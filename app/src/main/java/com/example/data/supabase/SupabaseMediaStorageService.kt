package com.example.data.supabase

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.BuildConfig
import com.example.data.firebase.FirebaseAppProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Stores chat attachments in the public Supabase `chat-media` bucket.
 * Firebase Auth remains the identity provider and Firebase Realtime Database remains
 * the chat transport; Supabase is used only for the binary object.
 */
data class UploadedMedia(
    val mediaId: String,
    val storagePath: String,
    val downloadUrl: String,
    val mediaType: String,
    val mimeType: String,
    val sizeBytes: Long?
)

class SupabaseMediaStorageService(context: Context) {
    companion object {
        private const val TAG = "SupabaseMediaStorage"
        private const val BUCKET = "chat-media"
        private const val MAX_FILE_SIZE_BYTES = 50L * 1024L * 1024L
        // A 50 MB upload may need several minutes on a mobile connection, but no network
        // operation should be allowed to leave the composer in "100%" forever.
        private const val UPLOAD_CALL_TIMEOUT_MINUTES = 6L
        private const val CLEANUP_TIMEOUT_MILLIS = 15_000L
        private const val SUPABASE_URL = BuildConfig.SUPABASE_PROJECT_URL
        private const val SUPABASE_PUBLISHABLE_KEY = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        private const val DELETE_FUNCTION = "functions/v1/delete-chat-media"

        /**
         * Returns a raw object path only for this app's public chat-media URL.
         * External URLs (GIF providers, YouTube, etc.) are never sent to the
         * privileged delete function.
         */
        fun storagePathFromPublicUrl(url: String): String? {
            val prefix = "$SUPABASE_URL/storage/v1/object/public/$BUCKET/"
            if (!url.startsWith(prefix)) return null
            val encodedPath = url.removePrefix(prefix).substringBefore('?')
            if (encodedPath.isBlank()) return null
            return encodedPath.split('/').joinToString("/") { Uri.decode(it) }
        }
    }

    private val context = context.applicationContext
    private val app: FirebaseApp = FirebaseAppProvider.get(context)
    private val auth = FirebaseAuth.getInstance(app)
    private val firestore = FirebaseFirestore.getInstance(app)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // writeTimeout bounds stalled request-body writes; callTimeout bounds the full upload,
        // including waiting for Supabase's response after progress reaches 100%.
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(UPLOAD_CALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        .build()

    /**
     * Uploads a content:// URI and returns a public URL only after Supabase confirms success.
     * The caller must create the chat message after this method returns.
     */
    suspend fun uploadMedia(
        ownerUid: String,
        localUri: Uri,
        mediaType: String,
        mimeType: String,
        originalName: String? = null,
        onProgress: (transferredBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): UploadedMedia {
        require(ownerUid.isNotBlank()) { "ownerUid no puede estar vacío" }
        require(auth.currentUser?.uid == ownerUid) { "La sesión de Firebase no coincide con el propietario del archivo" }
        require(mediaType in setOf("image", "video", "audio", "gif", "sticker", "document")) {
            "Tipo multimedia no válido"
        }

        val normalizedMimeType = mimeType.trim().lowercase()
        require(normalizedMimeType.isNotBlank()) { "El archivo no tiene MIME type" }
        if (mediaType == "video") {
            require(normalizedMimeType.startsWith("video/")) {
                "El archivo seleccionado no es un video válido ($normalizedMimeType)"
            }
        }

        val source = withContext(Dispatchers.IO) {
            materializeContentUri(localUri, originalName)
        }
        var uploadedPath: String? = null
        try {
            require(source.sizeBytes > 0L) { "El archivo multimedia está vacío" }
            require(source.sizeBytes <= MAX_FILE_SIZE_BYTES) {
                "El archivo supera el límite de 50 MB de Supabase Storage"
            }

            val mediaId = UUID.randomUUID().toString()
            val storagePath = "users/$ownerUid/media/$mediaId/${source.fileName}"
            uploadedPath = storagePath
            val encodedPath = encodeStoragePath(storagePath)
            val publicUrl = "$SUPABASE_URL/storage/v1/object/public/$BUCKET/$encodedPath"
            val requestBody = ProgressRequestBody(
                file = source.file,
                contentType = normalizedMimeType.toMediaTypeOrNull(),
                onProgress = onProgress
            )
            val request = Request.Builder()
                .url("$SUPABASE_URL/storage/v1/object/$BUCKET/$encodedPath")
                // The anon INSERT policy authenticates this request with the publishable key.
                // Firebase Auth tokens are not Supabase JWTs and must not be sent here.
                .header("apikey", SUPABASE_PUBLISHABLE_KEY)
                .header("x-upsert", "false")
                .post(requestBody)
                .build()

            executeUpload(request, storagePath)
            require(publicUrl.startsWith("https://") && publicUrl.contains("/storage/v1/object/public/$BUCKET/")) {
                "Supabase Storage no devolvió una URL pública válida"
            }

            // The chat message and Supabase object are the source of truth. The Firestore
            // media index is auxiliary metadata and must not make a successful upload look
            // like a failed send (for example while Firestore rules or connectivity are
            // unavailable). Keep indexing best-effort; the message still carries publicUrl.
            val mediaData = hashMapOf<String, Any?>(
                "mediaId" to mediaId,
                "ownerUid" to ownerUid,
                "mediaType" to mediaType,
                "mimeType" to normalizedMimeType,
                "fileName" to source.fileName,
                "storagePath" to storagePath,
                "downloadUrl" to publicUrl,
                "storageProvider" to "supabase",
                "sizeBytes" to source.sizeBytes,
                "createdAt" to FieldValue.serverTimestamp()
            )
            try {
                firestore.collection("media").document(mediaId).set(mediaData).await()
            } catch (metadataError: Exception) {
                Log.w(TAG, "La subida a Supabase fue exitosa, pero no se pudo indexar el archivo en Firestore", metadataError)
            }

            return UploadedMedia(
                mediaId = mediaId,
                storagePath = storagePath,
                downloadUrl = publicUrl,
                mediaType = mediaType,
                mimeType = normalizedMimeType,
                sizeBytes = source.sizeBytes
            )
        } catch (error: Exception) {
            val pathToClean = uploadedPath
            if (pathToClean != null) {
                runCatching {
                    // Cleanup is best-effort and must not turn an upload timeout into another
                    // unbounded wait on the same network path.
                    withTimeoutOrNull(CLEANUP_TIMEOUT_MILLIS) {
                        deleteObject(ownerUid, pathToClean)
                    } ?: Log.w(TAG, "La limpieza de una subida incompleta agotó su tiempo")
                }.onFailure { cleanupError ->
                    Log.w(TAG, "No se pudo limpiar una subida incompleta", cleanupError)
                }
            }
            throw error
        } finally {
            source.temporaryFile.delete()
        }
    }

    suspend fun deleteMedia(ownerUid: String, mediaId: String, storagePath: String) {
        require(ownerUid.isNotBlank()) { "ownerUid no puede estar vacío" }
        require(storagePath.startsWith("users/$ownerUid/media/")) {
            "La ruta del archivo no pertenece al usuario autenticado"
        }
        deleteObject(ownerUid, storagePath)
        firestore.collection("media").document(mediaId).delete().await()
    }

    /**
     * Deletes a chat-media object through the Edge Function. Firebase verifies
     * the ID token server-side; the Android app never receives service-role access.
     */
    suspend fun deleteMediaObject(storagePath: String) = withContext(Dispatchers.IO) {
        require(storagePath.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "La ruta del archivo no es válida"
        }
        require(storagePath.startsWith("users/") && "/media/" in storagePath) {
            "La ruta del archivo no pertenece al almacenamiento multimedia"
        }
        val firebaseToken = auth.currentUser?.getIdToken(false)?.await()?.token
            ?: throw IllegalStateException("No hay un token de Firebase activo")
        val requestBody = JSONObject().put("storagePath", storagePath)
            .toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("$SUPABASE_URL/$DELETE_FUNCTION")
            .header("apikey", SUPABASE_PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $firebaseToken")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string()?.take(300).orEmpty()
                throw IOException(
                    "La función segura rechazó la eliminación (${response.code})" +
                        if (detail.isNotBlank()) ": $detail" else ""
                )
            }
        }
    }

    private suspend fun deleteObject(ownerUid: String, storagePath: String) {
        require(auth.currentUser?.uid == ownerUid) { "La sesión de Firebase no coincide con el propietario" }
        deleteMediaObject(storagePath)
    }

    private suspend fun executeUpload(request: Request, storagePath: String) = withContext(Dispatchers.IO) {
        try {
            httpClient.newCall(request).execute().use { response ->
                // Drain the response before closing it. This releases the connection cleanly
                // and prevents a successful request from remaining suspended after 100%.
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = responseText.take(300)
                    Log.e(
                        TAG,
                        "Supabase upload failed: code=${response.code}, path=$storagePath" +
                            if (detail.isNotBlank()) ", detail=$detail" else ""
                    )
                    throw IOException(
                        "Supabase Storage rechazó el archivo (${response.code})" +
                            if (detail.isNotBlank()) ": $detail" else ""
                    )
                }
                Log.d(TAG, "Archivo multimedia subido: $storagePath")
            }
        } catch (timeout: InterruptedIOException) {
            throw IOException(
                "Tiempo de espera agotado al subir el archivo a Supabase Storage",
                timeout
            )
        }
    }

    private fun materializeContentUri(sourceUri: Uri, originalName: String?): MaterializedSource {
        val displayName = originalName
            ?: context.contentResolver.query(
                sourceUri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: sourceUri.lastPathSegment
            ?: "archivo"
        val safeName = displayName.substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(100)
            .ifBlank { "archivo" }

        val sizeFromProvider = context.contentResolver.query(
            sourceUri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
        require(sizeFromProvider == null || sizeFromProvider <= MAX_FILE_SIZE_BYTES) {
            "El archivo supera el límite de 50 MB de Supabase Storage"
        }

        val cacheDir = File(context.cacheDir, "supabase_media").apply { mkdirs() }
        val extension = safeName.substringAfterLast('.', "bin")
        val destination = File(cacheDir, "${UUID.randomUUID()}.$extension")
        try {
            val copiedBytes = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_FILE_SIZE_BYTES) {
                            throw IllegalArgumentException("El archivo supera el límite de 50 MB de Supabase Storage")
                        }
                        output.write(buffer, 0, read)
                    }
                    total
                }
            } ?: 0L
            require(copiedBytes > 0L && destination.length() > 0L) {
                "No se pudo leer el archivo multimedia seleccionado"
            }
            return MaterializedSource(destination, safeName, copiedBytes, destination)
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    private fun encodeStoragePath(path: String): String =
        path.split('/').joinToString("/") { Uri.encode(it) }

    private data class MaterializedSource(
        val file: File,
        val fileName: String,
        val sizeBytes: Long,
        val temporaryFile: File
    )

    private class ProgressRequestBody(
        private val file: File,
        private val contentType: okhttp3.MediaType?,
        private val onProgress: (Long, Long) -> Unit
    ) : RequestBody() {
        override fun contentType(): okhttp3.MediaType? = contentType
        override fun contentLength(): Long = file.length()

        override fun writeTo(sink: BufferedSink) {
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var transferred = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    sink.write(buffer, 0, read)
                    transferred += read
                    onProgress(transferred, contentLength())
                }
            }
        }
    }
}


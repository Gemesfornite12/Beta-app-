package com.example.data.firebase

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

/**
 * Sube fotos, videos, audios, GIFs, stickers y archivos a Firebase Storage.
 * Firestore conserva únicamente los metadatos y los enlaces.
 *
 * Las Uri content:// se materializan en un archivo privado temporal antes de
 * subirlas. Esto evita que el permiso temporal del selector de Android expire
 * durante o después de la subida.
 */
data class UploadedMedia(
    val mediaId: String,
    val storagePath: String,
    val downloadUrl: String,
    val mediaType: String,
    val mimeType: String,
    val sizeBytes: Long?
)

class FirebaseMediaStorageService(context: Context) {
    private val app = FirebaseAppProvider.get(context)
    private val context = context.applicationContext
    private val storage = FirebaseStorage.getInstance(app)
    private val firestore = FirebaseFirestore.getInstance(app)

    /**
     * mediaType: image, video, audio, gif, sticker o document.
     * onProgress recibe bytes transferidos y bytes totales.
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

        val source = materializeContentUri(localUri, mediaType, originalName)
        try {
            require(source.sizeBytes > 0L) { "El archivo multimedia está vacío" }

            val mediaId = UUID.randomUUID().toString()
            val safeName = source.fileName
            val storagePath = "users/$ownerUid/media/$mediaId/$safeName"
            val storageRef = storage.reference.child(storagePath)

            val metadata = StorageMetadata.Builder()
                .setContentType(normalizedMimeType)
                .setCustomMetadata("ownerUid", ownerUid)
                .setCustomMetadata("mediaType", mediaType)
                .build()

            val uploadTask = storageRef.putFile(source.uri, metadata)
            uploadTask.addOnProgressListener { snapshot ->
                onProgress(snapshot.bytesTransferred, snapshot.totalByteCount)
            }
            val uploadSnapshot = uploadTask.await()
            require(uploadSnapshot.totalByteCount > 0L) {
                "Firebase Storage terminó la subida con un archivo vacío"
            }

            val downloadUrl = storageRef.downloadUrl.await().toString()
            require(downloadUrl.startsWith("https://") && downloadUrl.contains("firebasestorage.googleapis.com")) {
                "Firebase Storage no devolvió una URL de descarga válida"
            }

            val mediaData = hashMapOf<String, Any?>(
                "mediaId" to mediaId,
                "ownerUid" to ownerUid,
                "mediaType" to mediaType,
                "mimeType" to normalizedMimeType,
                "fileName" to safeName,
                "storagePath" to storagePath,
                "downloadUrl" to downloadUrl,
                "sizeBytes" to uploadSnapshot.totalByteCount,
                "createdAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("media")
                .document(mediaId)
                .set(mediaData)
                .await()

            return UploadedMedia(
                mediaId = mediaId,
                storagePath = storagePath,
                downloadUrl = downloadUrl,
                mediaType = mediaType,
                mimeType = normalizedMimeType,
                sizeBytes = uploadSnapshot.totalByteCount
            )
        } finally {
            source.temporaryFile?.delete()
        }
    }

    private fun materializeContentUri(
        sourceUri: Uri,
        mediaType: String,
        originalName: String?
    ): MaterializedSource {
        val displayName = originalName
            ?: context.contentResolver.query(
                sourceUri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
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

        // A content:// grant can expire; copy it to private app storage first.
        if (sourceUri.scheme == "content") {
            val cacheDir = File(context.cacheDir, "firebase_media").apply { mkdirs() }
            val extension = safeName.substringAfterLast('.', "bin")
            val destination = File(cacheDir, "${UUID.randomUUID()}.$extension")
            val copiedBytes = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            } ?: 0L
            require(copiedBytes > 0L && destination.length() > 0L) {
                "No se pudo copiar el archivo multimedia seleccionado"
            }
            if (sizeFromProvider != null && sizeFromProvider > 0L && sizeFromProvider != copiedBytes) {
                Log.w("FirebaseMediaStorage", "El tamaño informado por el proveedor no coincide; se usará la copia completa")
            }
            return MaterializedSource(Uri.fromFile(destination), safeName, copiedBytes, destination)
        }

        val size = sizeFromProvider ?: context.contentResolver.openAssetFileDescriptor(sourceUri, "r")?.use {
            it.length
        } ?: -1L
        require(size > 0L) { "El archivo multimedia está vacío o no es accesible" }
        return MaterializedSource(sourceUri, safeName, size, null)
    }

    private data class MaterializedSource(
        val uri: Uri,
        val fileName: String,
        val sizeBytes: Long,
        val temporaryFile: File?
    )

    suspend fun deleteMedia(ownerUid: String, mediaId: String, storagePath: String) {
        require(ownerUid.isNotBlank()) { "ownerUid no puede estar vacío" }
        storage.reference.child(storagePath).delete().await()
        firestore.collection("media").document(mediaId).delete().await()
    }
}

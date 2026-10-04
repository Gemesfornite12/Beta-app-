package com.example.data.social

import android.content.Context
import android.net.Uri
import com.example.BuildConfig
import com.example.data.firebase.FirebaseAppProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Uploads to the private social-test-media bucket using short-lived signed URLs. */
class SupabaseSocialMediaService(context: Context) {
    companion object {
        private const val MAX_FILE_SIZE_BYTES = 50L * 1024L * 1024L
        private const val FUNCTION_NAME = "social-media-access"
        private const val SUPABASE_URL = BuildConfig.SUPABASE_PROJECT_URL
        private const val PUBLISHABLE_KEY = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    }

    private val appContext = context.applicationContext
    private val auth = FirebaseAuth.getInstance(FirebaseAppProvider.get(appContext))
    private val http = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.MINUTES)
        .build()

    suspend fun upload(uri: Uri, entityType: String, mimeType: String): String = withContext(Dispatchers.IO) {
        require(entityType == "post" || entityType == "story") { "Tipo de publicación no válido." }
        val normalizedMimeType = mimeType.substringBefore(';').trim().lowercase()
        require(supportedSocialMediaType(normalizedMimeType) != null) { "El formato no está permitido para publicaciones de Social." }
        val user = auth.currentUser ?: error("Inicia sesión para subir contenido.")
        val idToken = user.getIdToken(false).await().token ?: error("No se pudo verificar la sesión.")
        val fileInfo = SocialMediaFileInspector.inspect(appContext, uri)
        if (fileInfo.sizeBytes <= 0L) throw IOException("${fileInfo.displayName} está vacío.")
        if (fileInfo.sizeBytes > MAX_FILE_SIZE_BYTES) {
            throw IOException("${fileInfo.displayName} pesa ${formatMiB(fileInfo.sizeBytes)}; el máximo por archivo es 50 MiB.")
        }
        val tempFile = copyToTempFile(uri, fileInfo.displayName)
        try {
            require(tempFile.length() in 1L..MAX_FILE_SIZE_BYTES) {
                "${fileInfo.displayName} cambió de tamaño; cada archivo debe pesar como máximo 50 MiB."
            }
            val prepared = invokeFunction(
                idToken,
                JSONObject()
                    .put("action", "create-upload")
                    .put("entityType", entityType)
                    .put("mimeType", normalizedMimeType)
            )
            val signedUrl = normalizeUrl(prepared.optString("signedUrl"))
            val storagePath = prepared.optString("storagePath")
            require(signedUrl.isNotBlank() && storagePath.isNotBlank()) { "No se pudo preparar la carga privada." }

            val uploadRequest = Request.Builder()
                .url(signedUrl)
                .header("apikey", PUBLISHABLE_KEY)
                .header("Content-Type", normalizedMimeType)
                .put(tempFile.asRequestBody(normalizedMimeType.toMediaTypeOrNull()))
                .build()
            http.newCall(uploadRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = response.body?.string()?.take(240).orEmpty()
                    throw IOException("La carga privada fue rechazada (${response.code})" + if (detail.isBlank()) "" else ": $detail")
                }
            }
            storagePath
        } finally {
            tempFile.delete()
        }
    }

    suspend fun signedDownload(storagePath: String, entityType: String, entityId: String): String = withContext(Dispatchers.IO) {
        val idToken = auth.currentUser?.getIdToken(false)?.await()?.token
            ?: error("Inicia sesión para ver este contenido.")
        val response = invokeFunction(
            idToken,
            JSONObject()
                .put("action", "signed-download")
                .put("storagePath", storagePath)
                .put("entityType", entityType)
                .put("entityId", entityId)
        )
        normalizeUrl(response.optString("signedUrl")).ifBlank { error("No se pudo abrir el contenido privado.") }
    }

    suspend fun delete(storagePath: String) = withContext(Dispatchers.IO) {
        val idToken = auth.currentUser?.getIdToken(false)?.await()?.token
            ?: error("Inicia sesión para eliminar este contenido.")
        invokeFunction(
            idToken,
            JSONObject().put("action", "delete").put("storagePath", storagePath)
        )
    }

    private fun invokeFunction(idToken: String, payload: JSONObject): JSONObject {
        val request = Request.Builder()
            .url("${SUPABASE_URL.trimEnd('/')}/functions/v1/$FUNCTION_NAME")
            .header("apikey", PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $idToken")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
                throw IOException(message.ifBlank { "No se pudo verificar el acceso al contenido (${response.code})." })
            }
            return JSONObject(body)
        }
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) return trimmed
        if (trimmed.startsWith("/storage/v1/")) return "${SUPABASE_URL.trimEnd('/')}$trimmed"
        if (trimmed.startsWith("/object/")) return "${SUPABASE_URL.trimEnd('/')}/storage/v1$trimmed"
        return ""
    }

    private fun copyToTempFile(uri: Uri, name: String): File {
        val suffix = name.substringAfterLast('.', "")
            .takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }?.let { ".$it" } ?: ".media"
        val file = File.createTempFile("social-upload-", suffix, appContext.cacheDir)
        try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw IOException("No se pudo abrir $name.")
            input.use { source ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (total + read > MAX_FILE_SIZE_BYTES) {
                            throw IOException("$name cambió de tamaño; cada archivo debe pesar como máximo 50 MiB.")
                        }
                        output.write(buffer, 0, read)
                        total += read
                    }
                }
            }
            return file
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    private fun formatMiB(bytes: Long): String = String.format(java.util.Locale.ROOT, "%.1f MiB", bytes.toDouble() / (1024.0 * 1024.0))
}

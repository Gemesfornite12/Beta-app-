package com.example.data.supabase

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

internal data class SupabaseUploadPermission(
    val storagePath: String,
    val signedUrl: String
)

/** Parses and constrains the short-lived permission returned for one chat-media file. */
internal fun parseSupabaseUploadPermission(
    responseBody: String,
    ownerUid: String,
    expectedMimeType: String,
    supabaseProjectUrl: String
): SupabaseUploadPermission {
    val response = JSONObject(responseBody)
    val storagePath = response.getString("storagePath")
    require(response.getString("mimeType") == expectedMimeType) {
        "El permiso de subida no coincide con el tipo de archivo"
    }
    val segments = storagePath.split("/")
    require(
        segments.size == 5 &&
            segments[0] == "users" && segments[1] == ownerUid && segments[2] == "media" &&
            UUID_SEGMENT.matches(segments[3]) && UUID_FILE_SEGMENT.matches(segments[4])
    ) { "La ruta de subida no pertenece al usuario autenticado" }

    val project = supabaseProjectUrl.trimEnd('/').toHttpUrlOrNull()
        ?: throw IllegalArgumentException("La URL del proyecto Supabase no es válida")
    require(project.isHttps) { "La URL del proyecto debe usar HTTPS" }
    val rawSignedUrl = response.getString("signedUrl").trim()
    val normalizedSignedUrl = when {
        rawSignedUrl.startsWith("/storage/v1/") -> "${project.toString().trimEnd('/')}$rawSignedUrl"
        rawSignedUrl.startsWith("/object/") -> "${project.toString().trimEnd('/')}/storage/v1$rawSignedUrl"
        else -> rawSignedUrl
    }
    val signedUrl = normalizedSignedUrl.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("El permiso de subida no contiene una URL válida")
    require(
        signedUrl.isHttps && signedUrl.host == project.host && signedUrl.port == project.port &&
            signedUrl.encodedPath == "/storage/v1/object/upload/sign/chat-media/$storagePath" &&
            !signedUrl.queryParameter("token").isNullOrBlank()
    ) { "La URL del permiso no corresponde al almacenamiento seguro del proyecto" }

    return SupabaseUploadPermission(storagePath, signedUrl.toString())
}

private val UUID_SEGMENT = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
private val UUID_FILE_SEGMENT = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.[a-z0-9]{1,8}")

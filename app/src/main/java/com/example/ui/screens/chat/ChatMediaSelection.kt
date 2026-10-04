package com.example.ui.screens.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** A user-selected local file awaiting a single batch-send action in the chat composer. */
data class ChatMediaSelection(
    val uri: Uri,
    val mediaType: String,
    val displayName: String,
    val mimeType: String
)

/** Classifies the file without guessing from a URI's opaque content:// path. */
internal fun classifyChatMediaType(mimeType: String?, displayName: String): String {
    val normalizedMime = mimeType.orEmpty().substringBefore(';').trim().lowercase()
    val extension = displayName.substringAfterLast('.', "").lowercase()
    return when {
        normalizedMime == "image/gif" || extension == "gif" -> "gif"
        normalizedMime.startsWith("video/") || extension in setOf("mp4", "mkv", "mov", "webm", "avi", "3gp", "flv", "wmv", "m4v") -> "video"
        normalizedMime.startsWith("image/") || extension in setOf("jpg", "jpeg", "png", "webp", "bmp", "heic", "heif") -> "image"
        normalizedMime.startsWith("audio/") || extension in setOf("mp3", "wav", "m4a", "aac", "ogg", "flac", "opus") -> "audio"
        else -> "document"
    }
}

internal fun chatMediaSelection(context: Context, uri: Uri): ChatMediaSelection {
    val resolver = context.contentResolver
    val name = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
        }
    }.getOrNull()?.takeIf(String::isNotBlank)
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
        ?: "archivo_adjunto"
    val mime = resolver.getType(uri).orEmpty().ifBlank {
        when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "json" -> "application/json"
            else -> "application/octet-stream"
        }
    }
    return ChatMediaSelection(uri, classifyChatMediaType(mime, name), name, mime)
}

internal fun appendChatMediaSelections(
    current: List<ChatMediaSelection>,
    incoming: List<ChatMediaSelection>,
    maxItems: Int = 20
): List<ChatMediaSelection> {
    val existingUris = current.mapTo(mutableSetOf()) { it.uri.toString() }
    return (current + incoming.filter { existingUris.add(it.uri.toString()) }).take(maxItems.coerceAtLeast(1))
}

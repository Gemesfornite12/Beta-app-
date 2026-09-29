package com.example.ui.viewmodel

/** Pure attachment prompt and Groq audio routing helpers, kept JVM-testable. */
internal fun resolveSaraAttachmentPrompt(
    prompt: String,
    isAudio: Boolean,
    isImage: Boolean,
    defaultReplyLanguage: String
): String {
    val explicitPrompt = prompt.trim()
    if (explicitPrompt.isNotEmpty()) return explicitPrompt

    val language = defaultReplyLanguage.trim().ifBlank { "español" }
    return when {
        isAudio -> "Transcribe este audio."
        isImage -> "Describe la imagen, extrae el texto visible con claridad y responde en $language."
        else -> "Resume este archivo y responde en $language."
    }
}

internal fun groqAudioMimeType(mimeType: String, fileName: String): String? {
    val normalizedMimeType = mimeType.lowercase().substringBefore(';')
    if (normalizedMimeType.startsWith("video/")) return null

    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (normalizedMimeType) {
        "audio/mpeg", "audio/wav", "audio/mp4", "audio/webm", "audio/ogg", "audio/flac" -> normalizedMimeType
        "audio/mp3" -> "audio/mpeg"
        "audio/x-wav" -> "audio/wav"
        "audio/m4a" -> "audio/mp4"
        else -> when (extension) {
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            else -> null
        }
    }
}

/** Returns null when audio should use the Felo fallback instead of Groq. */
internal fun groqAudioMode(mimeType: String, fileName: String, prompt: String): String? {
    if (groqAudioMimeType(mimeType, fileName) == null) return null

    val asksForTranslation = prompt.contains("traduc", ignoreCase = true) ||
        prompt.contains("translat", ignoreCase = true)
    val asksForSpanish = Regex(
        "\\b(?:español|espanol|castellano|spanish|castilian)\\b",
        RegexOption.IGNORE_CASE
    ).containsMatchIn(prompt)

    return when {
        !asksForTranslation -> "transcribe"
        asksForSpanish -> "translate_es"
        else -> null
    }
}

package com.example.ui.viewmodel

import com.example.data.api.SaraGroqAudioRequest
import com.example.data.api.SaraGroqImage
import com.example.data.api.SaraGroqVisionRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqMediaRoutingTest {
    @Test
    fun blankImagePromptUsesDefaultReplyLanguage() {
        assertEquals(
            "Describe la imagen, extrae el texto visible con claridad y responde en inglés.",
            resolveSaraAttachmentPrompt("", isAudio = false, isImage = true, defaultReplyLanguage = "inglés")
        )
    }

    @Test
    fun explicitPromptIsPreserved() {
        assertEquals(
            "What is in this picture?",
            resolveSaraAttachmentPrompt("  What is in this picture?  ", isAudio = false, isImage = true, defaultReplyLanguage = "inglés")
        )
    }

    @Test
    fun supportedAudioUsesGroqTranscription() {
        assertEquals("audio/mpeg", groqAudioMimeType("audio/mp3", "voice.mp3"))
        assertEquals("transcribe", groqAudioMode("audio/mpeg", "voice.mp3", "Transcribe this recording"))
    }

    @Test
    fun explicitSpanishTranslationUsesGroqTranslationMode() {
        assertEquals("translate_es", groqAudioMode("audio/ogg", "voice.ogg", "Translate this to Spanish"))
    }

    @Test
    fun translationToOtherLanguageUsesFallbackInsteadOfGroq() {
        assertNull(groqAudioMode("audio/mpeg", "voice.mp3", "Translate this to French"))
    }

    @Test
    fun unsupportedAudioUsesFallbackInsteadOfGroq() {
        assertNull(groqAudioMode("audio/aac", "voice.aac", "Transcribe this recording"))
    }

    @Test
    fun visionPayloadUsesGatewayFieldNames() {
        val payload = Json.encodeToString(
            SaraGroqVisionRequest(
                prompt = "Describe this image",
                images = listOf(SaraGroqImage(mimeType = "image/jpeg", data = "YWJj"))
            )
        )
        assertTrue(payload.contains("\"prompt\""))
        assertTrue(payload.contains("\"images\""))
        assertTrue(payload.contains("\"mimeType\""))
        assertTrue(payload.contains("\"data\""))
        assertFalse(payload.contains("\"mime_type\""))
    }

    @Test
    fun audioPayloadUsesGatewayFieldNames() {
        val payload = Json.encodeToString(
            SaraGroqAudioRequest(
                audioData = "YWJj",
                mimeType = "audio/mpeg",
                fileName = "voice.mp3",
                mode = "transcribe"
            )
        )
        assertTrue(payload.contains("\"audioData\""))
        assertTrue(payload.contains("\"mimeType\""))
        assertTrue(payload.contains("\"fileName\""))
        assertTrue(payload.contains("\"mode\""))
    }
}

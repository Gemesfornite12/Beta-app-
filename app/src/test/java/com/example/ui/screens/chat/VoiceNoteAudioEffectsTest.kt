package com.example.ui.screens.chat

import android.media.MediaMuxer
import android.media.MediaFormat
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceNoteAudioEffectsTest {
    @Test
    fun echoAddsAudibleDelayedEnergyAndKeepsPcmLength() {
        val input = ShortArray(400).apply { this[0] = 20_000 }
        val output = VoiceAudioDsp(VoiceNoteEffectPreset.ECHO, sampleRate = 1_000, channelCount = 1).process(input)

        assertEquals(input.size, output.size)
        assertTrue("The dry impulse should remain", output[0].toInt() > 19_000)
        assertTrue("Echo should appear after 240 ms", output[240].toInt() in 8_200..8_500)
    }

    @Test
    fun robotAndWarmPresetsChangeSamplesWithoutChangingDuration() {
        val input = ShortArray(128) { index -> if (index % 2 == 0) 12_000 else -12_000 }
        val robot = VoiceAudioDsp(VoiceNoteEffectPreset.ROBOT, sampleRate = 48_000, channelCount = 1).process(input)
        val warm = VoiceAudioDsp(VoiceNoteEffectPreset.WARM, sampleRate = 48_000, channelCount = 1).process(input)

        assertEquals(input.size, robot.size)
        assertEquals(input.size, warm.size)
        assertFalse(input.contentEquals(robot))
        assertFalse(input.contentEquals(warm))
        assertNotEquals(input[1].toInt(), warm[1].toInt())
    }

    @Test
    fun outputFormatKeepsOpusOnQPlusWhenAvailableAndUsesHonestAacFallback() {
        val opus = preferredVoiceEffectOutputFormat(Build.VERSION_CODES.Q, opusAvailable = true)
        assertEquals("ogg", opus.extension)
        assertEquals("audio/ogg", opus.mimeType)
        assertEquals("audio/opus", opus.codecMimeType)
        assertEquals(MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG, opus.muxerFormat)
        assertTrue(opus.prefersOpus)

        val fallback = preferredVoiceEffectOutputFormat(Build.VERSION_CODES.Q, opusAvailable = false)
        assertEquals("m4a", fallback.extension)
        assertEquals("audio/mp4", fallback.mimeType)
        assertEquals(MediaFormat.MIMETYPE_AUDIO_AAC, fallback.codecMimeType)
        assertFalse(fallback.prefersOpus)

        val preQ = preferredVoiceEffectOutputFormat(Build.VERSION_CODES.P, opusAvailable = true)
        assertEquals("m4a", preQ.extension)
        assertEquals("audio/mp4", preQ.mimeType)
        assertFalse(preQ.prefersOpus)
    }
}

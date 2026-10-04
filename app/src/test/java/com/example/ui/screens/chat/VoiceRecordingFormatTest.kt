package com.example.ui.screens.chat

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRecordingFormatTest {
    @Test
    fun prefersOggOpusOnAndroidTenAndNewer() {
        val preferred = preferredVoiceRecordingFormat(Build.VERSION_CODES.Q)
        assertEquals("ogg", preferred.extension)
        assertEquals("audio/ogg", preferred.mimeType)
        assertTrue(preferred.prefersOpus)
    }

    @Test
    fun usesAacM4aOnVersionsWithoutOggOpusMediaRecorder() {
        val preferred = preferredVoiceRecordingFormat(Build.VERSION_CODES.P)
        assertEquals("m4a", preferred.extension)
        assertEquals("audio/mp4", preferred.mimeType)
        assertFalse(preferred.prefersOpus)
    }
}

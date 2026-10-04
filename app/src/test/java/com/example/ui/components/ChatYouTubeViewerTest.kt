package com.example.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatYouTubeViewerTest {

    @Test
    fun embedUrlUsesTheInAppYouTubePlayerWithInlinePlayback() {
        val url = buildYouTubeEmbedUrl("dQw4w9WgXcQ")

        assertTrue(url.startsWith("https://www.youtube.com/embed/dQw4w9WgXcQ?"))
        assertTrue(url.contains("autoplay=1"))
        assertTrue(url.contains("playsinline=1"))
        assertFalse(url.startsWith("intent://"))
    }
}

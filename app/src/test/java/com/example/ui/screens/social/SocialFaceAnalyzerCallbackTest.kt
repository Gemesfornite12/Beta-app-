package com.example.ui.screens.social

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialFaceAnalyzerCallbackTest {
    @Test
    fun liveStreamCallbackMustMatchTheSubmittedFrameTimestamp() {
        assertTrue(matchesSocialPendingFrame(42L, 42L))
        assertFalse(matchesSocialPendingFrame(42L, 41L))
        assertFalse(matchesSocialPendingFrame(42L, 43L))
    }
}

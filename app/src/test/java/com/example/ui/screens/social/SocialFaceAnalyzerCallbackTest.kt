package com.example.ui.screens.social

import com.google.mediapipe.tasks.vision.core.RunningMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialFaceAnalyzerCallbackTest {
    @Test
    fun faceLandmarkerUsesSynchronousVideoModeAndSegmenterCallbacksMatchSubmittedTimestamp() {
        assertEquals(RunningMode.VIDEO, socialFaceLandmarkerRunningMode())
        assertTrue(matchesSocialPendingFrame(42L, 42L))
        assertFalse(matchesSocialPendingFrame(42L, 41L))
        assertFalse(matchesSocialPendingFrame(42L, 43L))
    }

    @Test
    fun segmentationTimeoutOnlyExpiresTheCurrentFrameWithoutACompletedCallback() {
        assertTrue(shouldExpireSocialSegmentation(isCurrentFrame = true, callbackCompleted = false))
        assertFalse(shouldExpireSocialSegmentation(isCurrentFrame = false, callbackCompleted = false))
        assertFalse(shouldExpireSocialSegmentation(isCurrentFrame = true, callbackCompleted = true))
    }
}

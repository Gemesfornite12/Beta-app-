package com.example.ui.screens.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialFaceLiveDiagnosticTest {
    @Test
    fun copyDiagnosticRemainsAvailableForSelectedEffectWithoutAnyError() {
        assertTrue(shouldShowSocialFaceDiagnostic(
            faceEffectSelected = true,
            initializationFailure = false,
            analyzerCreationFailure = false
        ))
        assertFalse(shouldShowSocialFaceDiagnostic(
            faceEffectSelected = false,
            initializationFailure = false,
            analyzerCreationFailure = false
        ))
    }

    @Test
    fun recorderSeparatesAnalyzerSubmissionsCallbacksRawFacesAndMapping() {
        val recorder = SocialFaceLiveDiagnosticRecorder()
        recorder.recordAnalyzerFrame()
        recorder.recordAnalyzerFrame()
        recorder.recordFaceFrameSubmission()
        recorder.recordFaceFrameSubmission()
        recorder.recordAcceptedCallback(1)
        recorder.recordStaleCallback()
        recorder.recordPreviewMapping(ready = true, mappedCount = 0)

        val snapshot = recorder.snapshot(analyzerAvailable = true, faceEffectRequested = true)
        assertEquals(2L, snapshot.analyzerFramesReceived)
        assertEquals(2L, snapshot.faceFrameSubmissions)
        assertEquals(1L, snapshot.callbacksAccepted)
        assertEquals(1L, snapshot.callbacksStale)
        assertEquals(1L, snapshot.rawDetectedFaceTotal)
        assertEquals(1, snapshot.lastRawDetectedFaceCount)
        assertEquals(true, snapshot.transformReady)
        assertEquals(0, snapshot.mappedFaceCount)

        val report = buildSocialFaceLiveDiagnosticReport(snapshot, sdkApi = 35, buildVariant = "beta")
        assertTrue(report.contains("Faces were detected, but none mapped into preview coordinates."))
    }

    @Test
    fun reportClearlyIdentifiesAnalyzerThatHasNotReceivedFrames() {
        val snapshot = SocialFaceLiveDiagnosticRecorder()
            .snapshot(analyzerAvailable = true, faceEffectRequested = true)
        val report = buildSocialFaceLiveDiagnosticReport(snapshot, sdkApi = 35, buildVariant = "beta")
        assertTrue(report.contains("Analyzer frames received: 0"))
        assertTrue(report.contains("Analyzer has not received a camera frame."))
    }

    @Test
    fun reportContainsOnlyErrorTypeAndCountersNotExceptionTextOrPersonalData() {
        val recorder = SocialFaceLiveDiagnosticRecorder()
        recorder.recordError(SocialFaceLiveErrorStage.FACE_DETECT_VIDEO, IllegalStateException("email=person@example.com account=9876543210 secret=super-private"))
        val report = buildSocialFaceLiveDiagnosticReport(
            recorder.snapshot(analyzerAvailable = true, faceEffectRequested = true),
            sdkApi = 35,
            buildVariant = "beta"
        )

        assertTrue(report.contains("Last error type: IllegalStateException"))
        assertTrue(report.contains("Last error stage: FACE_DETECT_VIDEO"))
        assertFalse(report.contains("person@example.com"))
        assertFalse(report.contains("9876543210"))
        assertFalse(report.contains("super-private"))
        assertFalse(report.contains("landmark"))
        assertFalse(report.contains("0.4, 0.6"))
    }
}

package com.example.ui.screens.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialFaceCrashEvidenceTest {
    @Test
    fun reportRetainsOnlyContentFreePendingFrameEvidence() {
        val report = buildSocialFaceCrashEvidenceReport(
            SocialFaceCrashRecord(
                state = "process-restarted",
                stage = "FACE_DETECT_VIDEO",
                apiLevel = 36,
                frames = 1,
                submissions = 1,
                acceptedCallbacks = 0,
                staleCallbacks = 0,
                pending = true,
                pendingAgeMs = 65_000
            )
        )
        assertTrue(report.contains("Previous session state: process-restarted"))
        assertTrue(report.contains("Last checkpoint: FACE_DETECT_VIDEO"))
        assertTrue(report.contains("Frames received: 1"))
        assertTrue(report.contains("Accepted callbacks: 0"))
        assertTrue(report.contains("Frame still pending at last checkpoint: true"))
        assertTrue(report.contains("Pending duration at report time (ms): 65000"))
        assertFalse(report.contains("landmark"))
        assertFalse(report.contains("bitmap"))
    }

    @Test
    fun exceptionMessagesAndUntrustedTokensCannotEnterEvidenceReport() {
        val privateFailureText = "email=person@example.com account=704340590 message=private"
        val report = buildSocialFaceCrashEvidenceReport(
            SocialFaceCrashRecord(
                state = "uncaught-crash",
                stage = "FACE_DETECT_VIDEO",
                apiLevel = 36,
                frames = 1,
                submissions = 1,
                acceptedCallbacks = 0,
                staleCallbacks = 0,
                pending = true,
                pendingAgeMs = 60_000,
                errorType = "IllegalStateException",
                crashType = "RuntimeException",
                crashSite = "com.example.social.SocialFaceImageAnalyzer#analyze:791"
            )
        ) + safeSocialFaceDiagnosticToken(privateFailureText)

        assertTrue(report.contains("Uncaught exception type: RuntimeException"))
        assertTrue(report.contains("First app stack site: com.example.social.SocialFaceImageAnalyzer#analyze:791"))
        assertFalse(report.contains("person@example.com"))
        assertFalse(report.contains("704340590"))
        assertFalse(report.contains("private"))
        assertEquals("unknown", safeSocialFaceDiagnosticToken(privateFailureText))
    }
}

package com.example.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceNoteDialogActionsTest {
    @Test
    fun recordingAlwaysOffersAnExplicitStopAndCancelAction() {
        assertEquals(
            setOf(VoiceNoteDialogAction.STOP, VoiceNoteDialogAction.CANCEL),
            voiceNoteDialogActions(isRecording = true, hasCapturedRecording = false)
        )
    }

    @Test
    fun completedRecordingOffersPreviewDiscardAndSendBeforeDismissal() {
        assertEquals(
            setOf(
                VoiceNoteDialogAction.PLAY_PREVIEW,
                VoiceNoteDialogAction.DISCARD,
                VoiceNoteDialogAction.SEND
            ),
            voiceNoteDialogActions(isRecording = false, hasCapturedRecording = true)
        )
    }

    @Test
    fun readyDialogCanStartOrCloseWithoutSendingAnything() {
        assertEquals(
            setOf(VoiceNoteDialogAction.START, VoiceNoteDialogAction.CLOSE),
            voiceNoteDialogActions(isRecording = false, hasCapturedRecording = false)
        )
    }

    @Test
    fun leftSwipeCancelsOnceAndNeverFinishesOrSendsTheRecording() {
        val tracker = VoiceNoteGestureTracker(thresholdPx = 90f)
        assertNull(tracker.onMove(deltaX = -89f, deltaY = 0f))
        assertEquals(VoiceNoteGestureAction.CANCEL, tracker.onMove(deltaX = -90f, deltaY = 0f))
        assertNull(tracker.onMove(deltaX = -120f, deltaY = 0f))
        assertFalse(tracker.shouldFinishOnRelease(isLocked = false))
    }

    @Test
    fun upwardSwipeLocksUntilExplicitFinishAndRightSwipeDoesNotCancel() {
        val tracker = VoiceNoteGestureTracker(thresholdPx = 90f)
        assertNull(tracker.onMove(deltaX = 90f, deltaY = 0f))
        assertEquals(VoiceNoteGestureAction.LOCK, tracker.onMove(deltaX = 0f, deltaY = -90f))
        assertNull(tracker.onMove(deltaX = 0f, deltaY = -120f))
        assertFalse(tracker.shouldFinishOnRelease(isLocked = false))
    }

    @Test
    fun sendCallbackCanOnlyBeClaimedOnce() {
        val gate = VoiceNoteSendGate()
        assertTrue(gate.claim())
        assertFalse(gate.claim())
    }
}

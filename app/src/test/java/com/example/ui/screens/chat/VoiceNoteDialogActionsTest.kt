package com.example.ui.screens.chat

import org.junit.Assert.assertEquals
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
}

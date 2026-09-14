package com.example

import com.example.ui.viewmodel.SequencerTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SequencerSuiteTest {

    @Test
    fun testSequencerTrackCreationAndDefaults() {
        val track = SequencerTrack(
            name = "Kick Drum",
            soundType = "kick"
        )
        assertEquals("Kick Drum", track.name)
        assertEquals("kick", track.soundType)
        assertFalse(track.isMuted)
        assertFalse(track.isSolo)
        assertEquals(16, track.steps.size)
        // All false by default
        assertFalse(track.steps[0])
    }

    @Test
    fun testSequencerTrackSoloMuteToggles() {
        var track = SequencerTrack(
            name = "Snare Drum",
            soundType = "snare"
        )
        assertFalse(track.isMuted)
        assertFalse(track.isSolo)

        track = track.copy(isMuted = true)
        assertTrue(track.isMuted)

        track = track.copy(isSolo = true)
        assertTrue(track.isSolo)
    }

    @Test
    fun testSequencer16StepPatternPreset() {
        val kickSteps = BooleanArray(16) { it == 0 || it == 10 }
        val kickTrack = SequencerTrack(
            name = "Kick Drum",
            soundType = "kick",
            steps = kickSteps
        )

        assertTrue(kickTrack.steps[0])
        assertFalse(kickTrack.steps[1])
        assertFalse(kickTrack.steps[4])
        assertTrue(kickTrack.steps[10])
        assertFalse(kickTrack.steps[15])
    }

    @Test
    fun testBpmCalculations() {
        val bpm = 120
        val stepDelayMs = ((60000.0 / bpm) / 4.0).toLong()
        assertEquals(125L, stepDelayMs)

        val fastBpm = 140
        val fastStepDelayMs = ((60000.0 / fastBpm) / 4.0).toLong()
        assertEquals(107L, fastStepDelayMs)
    }
}

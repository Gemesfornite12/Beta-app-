package com.example.data.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialVideoAutoSplitterTest {
    @Test
    fun sizeBoundariesUseMiBAndSplitOnlyOversizedFiles() {
        val max = SocialMediaUploadLimits.MAX_FILE_BYTES
        assertEquals(2, SocialVideoSplitPlanner.estimatedClipCount(max + 1))
        assertEquals(2, SocialVideoSplitPlanner.estimatedClipCount(90L * 1024L * 1024L))
        assertEquals(3, SocialVideoSplitPlanner.estimatedClipCount(91L * 1024L * 1024L))
        assertEquals(10, SocialMediaUploadLimits.MAX_SELECTED_MEDIA_PER_POST)
        assertEquals(20, SocialMediaUploadLimits.MAX_PREPARED_MEDIA_PER_POST)
        assertTrue(SocialMediaUploadLimits.isValidOriginalSelectionCount(10))
        assertFalse(SocialMediaUploadLimits.isValidOriginalSelectionCount(11))
        assertFalse(SocialMediaUploadLimits.isValidOriginalSelectionCount(0))
        assertTrue(SocialMediaUploadLimits.isValidPreparedMediaCount(20))
        assertFalse(SocialMediaUploadLimits.isValidPreparedMediaCount(21))
        assertFalse(SocialMediaUploadLimits.isValidPreparedMediaCount(0))
        assertTrue(SocialMediaUploadLimits.isUploadableSize(max))
        assertFalse(SocialMediaUploadLimits.isUploadableSize(max + 1))
        assertFalse(SocialMediaUploadLimits.isUploadableSize(0L))
    }

    @Test
    fun plannedRangesCoverDurationExactlyAndRemainContiguous() {
        val duration = 10_001L
        val ranges = SocialVideoSplitPlanner.ranges(duration, 3)
        assertEquals(0L, ranges.first().startMs)
        assertEquals(duration, ranges.last().endMs)
        assertTrue(ranges.all { it.durationMs >= SocialVideoSplitPlanner.MIN_CLIP_DURATION_MS })
        ranges.zipWithNext().forEach { (left, right) -> assertEquals(left.endMs, right.startMs) }
    }

    @Test
    fun aClipThatStillExceedsTheLimitCanBeBisectedWithoutGaps() {
        val original = SocialVideoClipRange(1_000L, 5_000L)
        val (first, second) = SocialVideoSplitPlanner.bisect(original)
        assertEquals(original.startMs, first.startMs)
        assertEquals(first.endMs, second.startMs)
        assertEquals(original.endMs, second.endMs)
    }

    @Test
    fun publishReadinessCannotBeStuckOnOldUploadErrorOrReadyWhileProcessing() {
        assertTrue(SocialPostPublishPolicy.canPublish(false, 10, false, false, false))
        assertTrue(SocialPostPublishPolicy.canPublish(false, 20, false, false, false))
        assertFalse(SocialPostPublishPolicy.canPublish(false, 21, false, false, false))
        assertFalse(SocialPostPublishPolicy.canPublish(false, 10, true, false, false))
        assertFalse(SocialPostPublishPolicy.canPublish(false, 10, false, true, false))
        assertFalse(SocialPostPublishPolicy.canPublish(false, 10, false, false, true))
        assertTrue(SocialPostPublishPolicy.canPublish(true, 0, false, false, false))
    }

    @Test
    fun clipCountNeverSilentlyExceedsTwentyPreparedMedia() {
        assertTrue(SocialVideoSplitPlanner.fitsPostLimit(8, 2))
        assertFalse(SocialVideoSplitPlanner.fitsPostLimit(8, 3))
        assertTrue(SocialVideoSplitPlanner.fitsPostLimit(10, 10))
        assertFalse(SocialVideoSplitPlanner.fitsPostLimit(10, 11))
        assertFalse(SocialVideoSplitPlanner.fitsPostLimit(0, 21))
    }

    @Test
    fun tenOriginalSelectionsCanExpandToElevenPreparedItemsWithoutDroppingSegments() {
        val max = SocialMediaUploadLimits.MAX_FILE_BYTES
        val originals = 10
        val splitVideoOutputs = SocialVideoSplitPlanner.estimatedClipCount(max + 1)
        val preparedCount = originals - 1 + splitVideoOutputs

        assertEquals(2, splitVideoOutputs)
        assertEquals(11, preparedCount)
        assertTrue(SocialMediaUploadLimits.isValidOriginalSelectionCount(originals))
        assertTrue(SocialMediaUploadLimits.isValidPreparedMediaCount(preparedCount))
        assertTrue(SocialVideoSplitPlanner.fitsPostLimit(originals - 1, splitVideoOutputs))
        assertTrue(SocialPostPublishPolicy.canPublish(false, preparedCount, false, false, false))
    }

    @Test
    fun clipPlanningRejectsTooManyVeryShortFragments() {
        val error = runCatching { SocialVideoSplitPlanner.ranges(2_000L, 9) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}

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
        assertEquals(10, SocialMediaUploadLimits.MAX_MEDIA_PER_POST)
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
        assertFalse(SocialPostPublishPolicy.canPublish(false, 10, true, false, false))
        assertFalse(SocialPostPublishPolicy.canPublish(false, 10, false, true, false))
        assertFalse(SocialPostPublishPolicy.canPublish(false, 10, false, false, true))
        assertFalse(SocialPostPublishPolicy.canPublish(false, 11, false, false, false))
        assertTrue(SocialPostPublishPolicy.canPublish(true, 0, false, false, false))
    }

    @Test
    fun clipCountNeverSilentlyExceedsTenMedia() {
        assertTrue(SocialVideoSplitPlanner.fitsPostLimit(8, 2))
        assertFalse(SocialVideoSplitPlanner.fitsPostLimit(8, 3))
        assertFalse(SocialVideoSplitPlanner.fitsPostLimit(0, 11))
    }

    @Test
    fun clipPlanningRejectsTooManyVeryShortFragments() {
        val error = runCatching { SocialVideoSplitPlanner.ranges(2_000L, 9) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}

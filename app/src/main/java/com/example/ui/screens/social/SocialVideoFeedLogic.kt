package com.example.ui.screens.social

/** Pure helpers shared by the Social video feed and its focused unit tests. */
internal object SocialVideoFeedLogic {
    /** Returns +1 for an upward/next swipe, -1 for downward/previous, or 0 below threshold. */
    fun swipeDirection(totalDragPx: Float, thresholdPx: Float): Int {
        if (thresholdPx <= 0f || kotlin.math.abs(totalDragPx) < thresholdPx) return 0
        return if (totalDragPx < 0f) 1 else -1
    }

    /** Returns a neighboring item index, or null when there is no item in that direction. */
    fun adjacentIndex(currentIndex: Int, itemCount: Int, direction: Int): Int? {
        if (itemCount <= 0 || currentIndex !in 0 until itemCount || direction !in -1..1 || direction == 0) return null
        val candidate = currentIndex + direction
        return candidate.takeIf { it in 0 until itemCount }
    }

}

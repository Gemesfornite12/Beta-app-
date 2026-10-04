package com.example.ui.screens.social

import com.example.data.social.SocialPost
import com.example.data.social.SocialPostMedia

/** One playable video in its parent post, retaining the video's position in that post's media list. */
internal data class SocialVideoFeedEntry(
    val post: SocialPost,
    val media: SocialPostMedia,
    val mediaIndex: Int
) {
    /** Stable and unique among entries from the same parent post, even when that post has many videos. */
    val stableKey: SocialVideoFeedEntryKey
        get() = SocialVideoFeedEntryKey(post.ownerUid, post.id, mediaIndex)
}

internal data class SocialVideoFeedEntryKey(
    val ownerUid: String,
    val postId: String,
    val mediaIndex: Int
)

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

    /** Expands all video media items in post order and original per-post media order. */
    fun expandVideos(posts: List<SocialPost>): List<SocialVideoFeedEntry> = posts.flatMap { post ->
        val mediaItems = post.mediaItems.ifEmpty {
            if (post.mediaPath.isBlank()) emptyList() else listOf(SocialPostMedia(post.mediaPath, post.mediaType))
        }
        mediaItems.mapIndexedNotNull { mediaIndex, media ->
            if (media.mediaType == "video") SocialVideoFeedEntry(post, media, mediaIndex) else null
        }
    }

    /** Starts at the tapped media item when present; otherwise falls back to that post's first video. */
    fun selectedEntryIndex(
        entries: List<SocialVideoFeedEntry>,
        ownerUid: String,
        postId: String,
        mediaIndex: Int
    ): Int {
        val exact = entries.indexOfFirst {
            it.post.ownerUid == ownerUid && it.post.id == postId && it.mediaIndex == mediaIndex
        }
        if (exact >= 0) return exact
        return entries.indexOfFirst { it.post.ownerUid == ownerUid && it.post.id == postId }.coerceAtLeast(0)
    }
}

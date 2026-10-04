package com.example.ui.screens.social

import com.example.data.social.SocialPost
import com.example.data.social.SocialPostMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SocialVideoFeedLogicTest {
    @Test fun upwardAndDownwardSwipesChooseNextAndPrevious() {
        assertEquals(1, SocialVideoFeedLogic.swipeDirection(-120f, 80f))
        assertEquals(-1, SocialVideoFeedLogic.swipeDirection(120f, 80f))
        assertEquals(0, SocialVideoFeedLogic.swipeDirection(-79f, 80f))
    }

    @Test fun navigationStopsAtFeedBoundaries() {
        assertEquals(1, SocialVideoFeedLogic.adjacentIndex(0, 4, 1))
        assertEquals(2, SocialVideoFeedLogic.adjacentIndex(3, 4, -1))
        assertNull(SocialVideoFeedLogic.adjacentIndex(0, 4, -1))
        assertNull(SocialVideoFeedLogic.adjacentIndex(3, 4, 1))
    }

    @Test fun expandsEveryVideoFromOneMultiVideoPost() {
        val post = post("post-a", listOf(video("first.mp4"), video("second.mp4"), video("third.mp4")))

        val entries = SocialVideoFeedLogic.expandVideos(listOf(post))

        assertEquals(listOf("first.mp4", "second.mp4", "third.mp4"), entries.map { it.media.mediaPath })
        assertEquals(listOf(0, 1, 2), entries.map { it.mediaIndex })
        assertEquals(listOf(post, post, post), entries.map { it.post })
    }

    @Test fun mixedMediaExpansionPreservesPostAndMediaOrder() {
        val first = post("post-a", listOf(image("a.jpg"), video("a-first.mp4"), image("b.jpg"), video("a-second.mp4")))
        val second = post("post-b", listOf(video("b-first.mp4"), image("c.jpg"), video("b-second.mp4")))

        val entries = SocialVideoFeedLogic.expandVideos(listOf(first, second))

        assertEquals(listOf("a-first.mp4", "a-second.mp4", "b-first.mp4", "b-second.mp4"), entries.map { it.media.mediaPath })
        assertEquals(listOf(1, 3, 0, 2), entries.map { it.mediaIndex })
        assertEquals(listOf("post-a", "post-a", "post-b", "post-b"), entries.map { it.post.id })
    }

    @Test fun entryIdentityIsUniqueWithinParentAndStartsAtTappedMedia() {
        val post = post("same-post", listOf(video("one.mp4"), video("two.mp4")))
        val other = post("other-post", listOf(video("three.mp4")))
        val entries = SocialVideoFeedLogic.expandVideos(listOf(post, other))

        assertEquals(entries.size, entries.map { it.stableKey }.distinct().size)
        assertNotEquals(entries[0].stableKey, entries[1].stableKey)
        assertEquals(1, SocialVideoFeedLogic.selectedEntryIndex(entries, "owner", "same-post", 1))
        assertEquals(0, SocialVideoFeedLogic.selectedEntryIndex(entries, "owner", "same-post", 99))
    }

    private fun post(id: String, media: List<SocialPostMedia>) = SocialPost(
        id = id,
        ownerUid = "owner",
        mediaType = "image",
        mediaItems = media
    )

    private fun video(path: String) = SocialPostMedia(mediaPath = path, mediaType = "video")
    private fun image(path: String) = SocialPostMedia(mediaPath = path, mediaType = "image")
}

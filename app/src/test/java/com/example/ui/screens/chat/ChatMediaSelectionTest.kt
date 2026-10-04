package com.example.ui.screens.chat

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatMediaSelectionTest {
    @Test
    fun classifiesPhotoVideoGifAudioAndDocumentsFromMimeAndName() {
        assertEquals("image", classifyChatMediaType("image/jpeg", "photo.jpg"))
        assertEquals("video", classifyChatMediaType("video/mp4", "clip.mp4"))
        assertEquals("gif", classifyChatMediaType("image/gif", "animated.gif"))
        assertEquals("gif", classifyChatMediaType(null, "animated.GIF"))
        assertEquals("audio", classifyChatMediaType("audio/ogg", "voice.ogg"))
        assertEquals("document", classifyChatMediaType("application/pdf", "notes.pdf"))
    }

    @Test
    fun appendsNewUrisOnceAndCapsOneBatchAtTwenty() {
        fun selection(path: String) = ChatMediaSelection(Uri.parse("content://media/$path"), "image", "$path.jpg", "image/jpeg")
        val first = selection("first")
        val current = listOf(first, selection("second"))
        val appended = appendChatMediaSelections(current, listOf(first, selection("third")))
        assertEquals(listOf("first", "second", "third"), appended.map { it.uri.lastPathSegment })

        val capped = appendChatMediaSelections(emptyList(), (1..25).map { selection("asset-$it") })
        assertEquals(20, capped.size)
    }
}

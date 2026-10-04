package com.example.data.firebase

import com.example.data.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageHistoryPagingTest {
    @Test
    fun recentWindowKeepsLoadedOlderMessagesAndReplacesOverlappingCopies() {
        val older = message("old", 10)
        val firstRecent = message("m1", 20, text = "stale")
        val latest = message("m2", 30)
        val refreshedFirstRecent = firstRecent.copy(text = "updated")
        val pending = message("", 25, text = "sending", id = 25).copy(deliveryStatus = "enviando")

        val merged = MessageHistoryPaging.mergeRecentPage(
            existing = listOf(older, firstRecent, latest, pending),
            recentPage = listOf(refreshedFirstRecent, latest)
        )

        assertEquals(listOf("old", "m1", "", "m2"), merged.map { it.firestoreId })
        assertEquals("updated", merged[1].text)
        assertEquals("sending", merged[2].text)
    }

    @Test
    fun olderPageMergesWithoutDuplicatesAndSortsByTimestampThenKey() {
        val newer = message("b", 20)
        val sameTimestamp = message("c", 20)
        val older = message("a", 10)

        val merged = MessageHistoryPaging.mergeOlderPage(
            existing = listOf(newer, sameTimestamp),
            olderPage = listOf(older, newer.copy(text = "server copy"))
        )

        assertEquals(listOf("a", "b", "c"), merged.map { it.firestoreId })
        assertEquals("server copy", merged[1].text)
    }

    @Test
    fun cursorUsesOldestRemoteMessageAndIncludesChildKeyForTimestampTies() {
        val cursor = MessageHistoryPaging.oldestCursor(
            listOf(
                message("z", 100),
                message("b", 100),
                message("local", 1, firestoreId = "", id = 99)
            )
        )

        assertNotNull(cursor)
        assertEquals(MessageHistoryCursor(100, "b"), cursor)
    }

    @Test
    fun emptyRecentEmissionDoesNotEraseCachedHistoryAndPageSizeIsBounded() {
        val cached = listOf(message("cached", 10))

        assertEquals(cached, MessageHistoryPaging.mergeRecentPage(cached, emptyList()))
        assertTrue(MessageHistoryPaging.PAGE_SIZE in 1..100)
        assertNull(MessageHistoryPaging.oldestCursor(listOf(message("local", 1, firestoreId = ""))))
    }

    private fun message(
        key: String,
        timestamp: Long,
        text: String = key,
        firestoreId: String = key,
        id: Long = timestamp
    ) = ChatMessage(
        id = id,
        firestoreId = firestoreId,
        channelId = "test-chat",
        senderName = "Tester",
        senderEmail = "tester@example.com",
        text = text,
        timestamp = timestamp
    )
}

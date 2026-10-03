package com.example

import com.example.data.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageReactionTest {

    @Test
    fun testReactionsParsingAndAggregation() {
        val message = ChatMessage(
            id = 1,
            channelId = "general",
            senderName = "Alex",
            senderEmail = "alex@example.com",
            text = "¡Hola a todos!",
            reactions = "👍,❤️,👍,🔥,❤️,👍"
        )

        val list = message.reactions.split(",").filter { it.isNotBlank() }
        val distinct = list.distinct()

        assertEquals(3, distinct.size)
        assertEquals(3, list.count { it == "👍" })
        assertEquals(2, list.count { it == "❤️" })
        assertEquals(1, list.count { it == "🔥" })
    }

    @Test
    fun testToggleReactionAddsAndRemoves() {
        val initialReactions = "👍,❤️"
        val list = initialReactions.split(",").filter { it.isNotBlank() }.toMutableList()

        // Toggle existing reaction: removes it
        val index = list.indexOf("❤️")
        if (index != -1) list.removeAt(index)
        assertEquals("👍", list.joinToString(","))

        // Toggle new reaction: appends it
        val newEmoji = "🔥"
        val index2 = list.indexOf(newEmoji)
        if (index2 != -1) list.removeAt(index2) else list.add(newEmoji)
        assertEquals("👍,🔥", list.joinToString(","))
    }
}

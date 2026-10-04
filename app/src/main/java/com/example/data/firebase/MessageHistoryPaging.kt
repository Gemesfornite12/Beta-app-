package com.example.data.firebase

import com.example.data.model.ChatMessage

/** Pure helpers for keeping chat history pages bounded and correctly ordered. */
internal data class MessageHistoryCursor(val timestamp: Long, val childKey: String)

internal object MessageHistoryPaging {
    const val PAGE_SIZE = 50

    private val messageOrder = compareBy<ChatMessage> { it.timestamp }
        .thenBy { it.firestoreId.ifBlank { it.id.toString() } }

    fun oldestCursor(messages: List<ChatMessage>): MessageHistoryCursor? =
        messages.asSequence()
            .filter { it.firestoreId.isNotBlank() }
            .minWithOrNull(messageOrder)
            ?.let { MessageHistoryCursor(it.timestamp, it.firestoreId) }

    /**
     * Replace the live recent window while retaining previously loaded pages outside it.
     * Incoming records win duplicate keys; an empty live result leaves cached content intact.
     */
    fun mergeRecentPage(
        existing: List<ChatMessage>,
        recentPage: List<ChatMessage>
    ): List<ChatMessage> {
        if (recentPage.isEmpty()) return mergeUnique(existing)
        val first = recentPage.minWithOrNull(messageOrder) ?: return mergeUnique(existing)
        val last = recentPage.maxWithOrNull(messageOrder) ?: return mergeUnique(existing)
        val retainedOutsideWindow = existing.filter {
            it.deliveryStatus == "enviando" || it.deliveryStatus == "error" ||
                messageOrder.compare(it, first) < 0 || messageOrder.compare(it, last) > 0
        }
        return mergeUnique(retainedOutsideWindow + recentPage)
    }

    /** Add an older page, retaining the current page and preferring newly fetched duplicates. */
    fun mergeOlderPage(
        existing: List<ChatMessage>,
        olderPage: List<ChatMessage>
    ): List<ChatMessage> = mergeUnique(existing + olderPage)

    private fun mergeUnique(messages: List<ChatMessage>): List<ChatMessage> {
        val byKey = LinkedHashMap<String, ChatMessage>()
        messages.forEach { message ->
            val key = if (message.firestoreId.isNotBlank()) {
                "remote:${message.firestoreId}"
            } else {
                "local:${message.id}"
            }
            byKey[key] = message
        }
        return byKey.values.sortedWith(messageOrder)
    }
}

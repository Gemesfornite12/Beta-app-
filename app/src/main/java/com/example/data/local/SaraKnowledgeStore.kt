package com.example.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

/**
 * Small, device-local store for knowledge the signed-in user explicitly approves.
 * Each Firebase UID gets a separate preference key; no chat is saved automatically.
 */
data class SaraKnowledgeEntry(
    val id: String,
    val text: String,
    val createdAt: Long
)

class SaraKnowledgeStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "sara_knowledge_library_v1",
        Context.MODE_PRIVATE
    )

    fun list(uid: String): List<SaraKnowledgeEntry> = runCatching {
        val array = JSONArray(preferences.getString(storageKey(uid), "[]") ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val text = item.optString("text").trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
            SaraKnowledgeEntry(id, text, item.optLong("createdAt"))
        }.sortedByDescending(SaraKnowledgeEntry::createdAt)
    }.getOrDefault(emptyList())

    fun save(uid: String, text: String): SaraKnowledgeEntry? {
        val cleanText = text.trim().take(2000)
        if (uid.isBlank() || cleanText.isBlank()) return null
        val existing = list(uid)
        existing.firstOrNull { it.text.equals(cleanText, ignoreCase = true) }?.let { return it }
        val entry = SaraKnowledgeEntry(UUID.randomUUID().toString(), cleanText, System.currentTimeMillis())
        persist(uid, (existing + entry).sortedByDescending(SaraKnowledgeEntry::createdAt).take(MAX_ENTRIES))
        return entry
    }

    fun delete(uid: String, entryId: String) {
        if (uid.isBlank() || entryId.isBlank()) return
        persist(uid, list(uid).filterNot { it.id == entryId })
    }

    /** Removes the legacy local copy only after the cloud migration is verified. */
    fun clear(uid: String) {
        if (uid.isBlank()) return
        preferences.edit().remove(storageKey(uid)).apply()
    }

    /** Returns only keyword-relevant approved notes, keeping private context bounded. */
    fun relevantContext(uid: String, query: String): String {
        val queryWords = tokens(query)
        if (uid.isBlank() || queryWords.isEmpty()) return ""
        return list(uid)
            .map { entry -> entry to tokens(entry.text).count(queryWords::contains) }
            .filter { (_, score) -> score > 0 }
            .sortedWith(compareByDescending<Pair<SaraKnowledgeEntry, Int>> { it.second }
                .thenByDescending { it.first.createdAt })
            .take(MAX_CONTEXT_ENTRIES)
            .joinToString("\n") { (entry, _) -> "• ${entry.text}" }
            .take(MAX_CONTEXT_CHARS)
    }

    private fun persist(uid: String, entries: List<SaraKnowledgeEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().put("id", entry.id).put("text", entry.text).put("createdAt", entry.createdAt))
        }
        preferences.edit().putString(storageKey(uid), array.toString()).apply()
    }

    private fun storageKey(uid: String) = "entries_${uid}"

    private fun tokens(text: String): Set<String> {
        val normalized = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return WORD_REGEX.findAll(normalized).map { it.value }
            .filter { it.length >= 4 && it !in STOP_WORDS }
            .toSet()
    }

    private companion object {
        const val MAX_ENTRIES = 100
        const val MAX_CONTEXT_ENTRIES = 5
        const val MAX_CONTEXT_CHARS = 5000
        val STOP_WORDS = setOf("para", "como", "cuando", "porque", "esto", "esta", "este", "desde", "hasta", "sobre", "with", "that", "this", "from", "your", "have", "what", "when", "where", "about")
        val WORD_REGEX = "[a-z0-9]+".toRegex()
    }
}

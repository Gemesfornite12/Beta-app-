package com.example.data.firebase

import android.content.Context
import com.example.data.local.SaraKnowledgeEntry
import com.example.data.local.SaraKnowledgeStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

/** Cloud library for user-approved Sara notes, isolated by Firebase UID. */
class SaraKnowledgeFirebaseStore(context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val root = FirebaseDatabase
        .getInstance("https://omnistudio-caaf5-default-rtdb.firebaseio.com")
        .reference
        .child("sara_knowledge")
    private val legacyStore = SaraKnowledgeStore(context.applicationContext)

    suspend fun list(uid: String): List<SaraKnowledgeEntry> {
        requireOwner(uid)
        migrateLocalEntries(uid)
        return readRemote(uid)
    }

    suspend fun save(uid: String, text: String): SaraKnowledgeEntry? {
        requireOwner(uid)
        val cleanText = text.trim().take(MAX_NOTE_CHARS)
        if (cleanText.isBlank()) return null
        val existing = list(uid)
        existing.firstOrNull { it.text.equals(cleanText, ignoreCase = true) }?.let { return it }
        if (existing.size >= MAX_ENTRIES) return null

        val entry = SaraKnowledgeEntry(
            id = UUID.randomUUID().toString(),
            text = cleanText,
            createdAt = System.currentTimeMillis()
        )
        writeEntry(uid, entry)
        return entry
    }

    suspend fun delete(uid: String, entryId: String) {
        requireOwner(uid)
        list(uid)
        if (entryId.isBlank()) return
        root.child(uid).child(entryId).removeValue().await()
    }

    suspend fun relevantContext(uid: String, query: String): String {
        val queryWords = tokens(query)
        if (queryWords.isEmpty()) return ""
        return list(uid)
            .map { entry -> entry to tokens(entry.text).count(queryWords::contains) }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<SaraKnowledgeEntry, Int>> { it.second }
                    .thenByDescending { it.first.createdAt }
            )
            .take(MAX_CONTEXT_ENTRIES)
            .joinToString("\n") { (entry, _) -> "• ${entry.text}" }
            .take(MAX_CONTEXT_CHARS)
    }

    private suspend fun migrateLocalEntries(uid: String) {
        val localEntries = legacyStore.list(uid)
        if (localEntries.isEmpty()) return

        val userRef = root.child(uid)
        val before = userRef.get().await()
        val existingIds = before.children.mapNotNull(DataSnapshot::getKey).toSet()
        localEntries.forEach { entry ->
            if (entry.id !in existingIds) writeEntry(uid, entry)
        }

        val verified = userRef.get().await()
        val verifiedIds = verified.children
            .filter { it.child("ownerUid").getValue(String::class.java) == uid }
            .mapNotNull(DataSnapshot::getKey)
            .toSet()
        check(localEntries.all { it.id in verifiedIds }) {
            "No se pudo verificar la migración de la biblioteca de Sara"
        }
        legacyStore.clear(uid)
    }

    private suspend fun readRemote(uid: String): List<SaraKnowledgeEntry> {
        requireOwner(uid)
        val snapshot = root.child(uid).get().await()
        return snapshot.children.mapNotNull { child ->
            val id = child.key ?: return@mapNotNull null
            if (child.child("ownerUid").getValue(String::class.java) != uid) return@mapNotNull null
            val text = child.child("text").getValue(String::class.java)?.trim().orEmpty()
            if (text.isBlank()) return@mapNotNull null
            val createdAt = child.child("createdAt").getValue(Long::class.javaObjectType) ?: 0L
            SaraKnowledgeEntry(id = id, text = text, createdAt = createdAt)
        }.sortedByDescending(SaraKnowledgeEntry::createdAt).take(MAX_ENTRIES)
    }

    private suspend fun writeEntry(uid: String, entry: SaraKnowledgeEntry) {
        requireOwner(uid)
        root.child(uid).child(entry.id).setValue(
            mapOf(
                "id" to entry.id,
                "ownerUid" to uid,
                "text" to entry.text.take(MAX_NOTE_CHARS),
                "createdAt" to entry.createdAt
            )
        ).await()
    }

    private fun requireOwner(uid: String) {
        check(uid.isNotBlank() && auth.currentUser?.uid == uid) {
            "Se requiere una sesión válida para acceder a la biblioteca de Sara"
        }
    }

    private fun tokens(text: String): Set<String> {
        val normalized = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        val words = mutableSetOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.length >= MIN_TOKEN_CHARS) {
                val word = current.toString()
                if (word !in STOP_WORDS) words += word
            }
            current.setLength(0)
        }
        normalized.forEach { char ->
            when {
                Character.isLetterOrDigit(char) -> current.append(char)
                Character.getType(char) == Character.NON_SPACING_MARK.toInt() -> Unit
                else -> flush()
            }
        }
        flush()
        return words
    }

    private companion object {
        const val MAX_ENTRIES = 100
        const val MAX_NOTE_CHARS = 2000
        const val MAX_CONTEXT_ENTRIES = 5
        const val MAX_CONTEXT_CHARS = 5000
        const val MIN_TOKEN_CHARS = 4
        val STOP_WORDS = setOf(
            "para", "como", "cuando", "porque", "esto", "esta", "este", "desde", "hasta", "sobre",
            "with", "that", "this", "from", "your", "have", "what", "when", "where", "about"
        )
    }
}

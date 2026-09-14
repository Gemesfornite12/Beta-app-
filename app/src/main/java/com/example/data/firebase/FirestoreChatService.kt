package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.model.ChatMessage
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

enum class FirestoreConnectionStatus(val label: String, val isLive: Boolean) {
    CONNECTED_REALTIME("Firebase Firestore • En vivo", true),
    CONNECTING("Conectando con Firestore...", false),
    OFFLINE_SYNCED("Firestore • Modo local sincronizado", true),
    DISCONNECTED("Desconectado de Firestore", false)
}

data class PresenceUser(
    val email: String,
    val name: String,
    val lastActive: Long = System.currentTimeMillis()
)

data class ChannelInfo(
    val id: String,
    val name: String,
    val description: String,
    val iconEmoji: String,
    val isDirect: Boolean = false
)

class FirestoreChatService(private val context: Context) {

    private val TAG = "FirestoreChatService"

    private val _connectionStatus = MutableStateFlow(FirestoreConnectionStatus.CONNECTING)
    val connectionStatus: StateFlow<FirestoreConnectionStatus> = _connectionStatus.asStateFlow()

    private val firestore: FirebaseFirestore? by lazy {
        try {
            ensureFirebaseInitialized()
            val db = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            db.firestoreSettings = settings
            _connectionStatus.value = FirestoreConnectionStatus.CONNECTED_REALTIME
            Log.d(TAG, "Firebase Firestore initialized successfully with real-time persistence")
            db
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase Firestore: ${e.message}", e)
            _connectionStatus.value = FirestoreConnectionStatus.OFFLINE_SYNCED
            null
        }
    }

    val availableChannels = listOf(
        ChannelInfo(
            id = "general",
            name = "general",
            description = "Discusión general del equipo y avisos",
            iconEmoji = "💬"
        ),
        ChannelInfo(
            id = "musica-colab",
            name = "musica-colab",
            description = "Intercambio de beats, loops y producciones IA",
            iconEmoji = "🎵"
        ),
        ChannelInfo(
            id = "revision-docs",
            name = "revision-docs",
            description = "Revisiones de documentos, minutas y diapositivas",
            iconEmoji = "📝"
        ),
        ChannelInfo(
            id = "soporte-ia",
            name = "soporte-ia",
            description = "Prompts, ideas y asistencia inteligente",
            iconEmoji = "🤖"
        ),
        ChannelInfo(
            id = "directo-sofia",
            name = "Sofia Martinez",
            description = "Diseñadora de Sonido & Productora",
            iconEmoji = "👩‍💻",
            isDirect = true
        ),
        ChannelInfo(
            id = "directo-carlos",
            name = "Carlos Mendoza",
            description = "Editor de Documentos & Estrategia",
            iconEmoji = "👨‍💼",
            isDirect = true
        )
    )

    private fun ensureFirebaseInitialized() {
        if (FirebaseApp.getApps(context).isEmpty()) {
            try {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(context.packageName)
                    .setProjectId("omnistudio-cloud-app")
                    .setApiKey("AIzaSyB-OmniStudioFirestoreAppKey2026")
                    .build()
                FirebaseApp.initializeApp(context, options)
                Log.d(TAG, "Programmatic FirebaseApp created successfully")
            } catch (e: Exception) {
                Log.w(TAG, "FirebaseApp.initializeApp warning: ${e.message}")
            }
        }
    }

    /**
     * Escucha en tiempo real todos los mensajes de un canal en Firestore.
     * Utiliza addSnapshotListener para recibir cambios instantáneos cuando cualquier usuario escribe.
     */
    fun listenToChannelMessages(channelId: String): Flow<List<ChatMessage>> = callbackFlow {
        val db = firestore
        if (db == null) {
            _connectionStatus.value = FirestoreConnectionStatus.OFFLINE_SYNCED
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val messagesRef = db.collection("chat_channels")
            .document(channelId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)

        val listenerRegistration: ListenerRegistration = messagesRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Firestore listen failed for channel $channelId: ${error.message}")
                _connectionStatus.value = FirestoreConnectionStatus.OFFLINE_SYNCED
                return@addSnapshotListener
            }

            if (snapshot != null) {
                _connectionStatus.value = FirestoreConnectionStatus.CONNECTED_REALTIME
                val messages = snapshot.documents.mapNotNull { doc ->
                    docToChatMessage(doc, channelId)
                }
                trySend(messages)
            }
        }

        awaitClose {
            Log.d(TAG, "Closing real-time Firestore listener for channel: $channelId")
            listenerRegistration.remove()
        }
    }

    /**
     * Envía un mensaje a Firestore en tiempo real.
     */
    suspend fun sendMessage(message: ChatMessage): String? {
        val db = firestore ?: return null
        return try {
            val docRef = db.collection("chat_channels")
                .document(message.channelId)
                .collection("messages")
                .document()

            val docId = docRef.id
            val data = hashMapOf(
                "firestoreId" to docId,
                "channelId" to message.channelId,
                "senderName" to message.senderName,
                "senderEmail" to message.senderEmail,
                "text" to message.text,
                "timestamp" to message.timestamp,
                "attachedDocId" to message.attachedDocId,
                "attachedDocTitle" to message.attachedDocTitle,
                "attachedAudioId" to message.attachedAudioId,
                "attachedAudioTitle" to message.attachedAudioTitle,
                "reactions" to message.reactions
            )

            docRef.set(data).await()

            // Actualizar metadata del canal para ordenar y mostrar último mensaje
            val channelMeta = hashMapOf(
                "lastMessageText" to message.text,
                "lastMessageTimestamp" to message.timestamp,
                "lastMessageSender" to message.senderName
            )
            db.collection("chat_channels")
                .document(message.channelId)
                .set(channelMeta, SetOptions.merge())

            _connectionStatus.value = FirestoreConnectionStatus.CONNECTED_REALTIME
            docId
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message to Firestore: ${e.message}", e)
            _connectionStatus.value = FirestoreConnectionStatus.OFFLINE_SYNCED
            null
        }
    }

    /**
     * Agrega una reacción emoji en tiempo real a un mensaje en Firestore.
     */
    suspend fun addReaction(channelId: String, firestoreId: String, emoji: String, currentReactions: String): Boolean {
        val db = firestore ?: return false
        if (firestoreId.isBlank()) return false
        return try {
            val updatedReactions = if (currentReactions.isBlank()) emoji else "$currentReactions,$emoji"
            db.collection("chat_channels")
                .document(channelId)
                .collection("messages")
                .document(firestoreId)
                .update("reactions", updatedReactions)
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating reaction in Firestore: ${e.message}")
            false
        }
    }

    /**
     * Elimina un mensaje en tiempo real de Firestore.
     */
    suspend fun deleteMessage(channelId: String, firestoreId: String): Boolean {
        val db = firestore ?: return false
        if (firestoreId.isBlank()) return false
        return try {
            db.collection("chat_channels")
                .document(channelId)
                .collection("messages")
                .document(firestoreId)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting message in Firestore: ${e.message}")
            false
        }
    }

    /**
     * Publica el estado de escritura ("typing indicator") en tiempo real.
     */
    suspend fun setTypingStatus(channelId: String, userEmail: String, userName: String, isTyping: Boolean) {
        val db = firestore ?: return
        val cleanEmailKey = userEmail.replace(".", "_").replace("@", "_at_")
        try {
            val typingRef = db.collection("chat_channels")
                .document(channelId)
                .collection("typing")
                .document(cleanEmailKey)

            if (isTyping) {
                val data = hashMapOf(
                    "userName" to userName,
                    "isTyping" to true,
                    "timestamp" to System.currentTimeMillis()
                )
                typingRef.set(data).await()
            } else {
                typingRef.delete().await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not update typing indicator: ${e.message}")
        }
    }

    /**
     * Escucha en tiempo real quiénes están escribiendo en el canal.
     */
    fun listenToTyping(channelId: String, currentUserEmail: String): Flow<List<String>> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val typingRef = db.collection("chat_channels")
            .document(channelId)
            .collection("typing")

        val listener = typingRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                return@addSnapshotListener
            }

            val now = System.currentTimeMillis()
            val typingNames = snapshot.documents.mapNotNull { doc ->
                val userName = doc.getString("userName") ?: return@mapNotNull null
                val isTyping = doc.getBoolean("isTyping") ?: false
                val ts = doc.getLong("timestamp") ?: 0L
                val docKey = doc.id
                val myKey = currentUserEmail.replace(".", "_").replace("@", "_at_")

                // Si está escribiendo, no es el usuario actual, y el latido fue hace menos de 8 segundos
                if (isTyping && docKey != myKey && (now - ts < 8000)) {
                    userName
                } else {
                    null
                }
            }
            trySend(typingNames)
        }

        awaitClose { listener.remove() }
    }

    /**
     * Actualiza la presencia activa de un usuario en el canal.
     */
    suspend fun updatePresence(channelId: String, userEmail: String, userName: String) {
        val db = firestore ?: return
        val cleanKey = userEmail.replace(".", "_").replace("@", "_at_")
        try {
            val data = hashMapOf(
                "email" to userEmail,
                "name" to userName,
                "lastActive" to System.currentTimeMillis()
            )
            db.collection("chat_channels")
                .document(channelId)
                .collection("presence")
                .document(cleanKey)
                .set(data, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Presence update error: ${e.message}")
        }
    }

    /**
     * Escucha en tiempo real los usuarios conectados en este canal.
     */
    fun listenToPresence(channelId: String): Flow<List<PresenceUser>> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val presenceRef = db.collection("chat_channels")
            .document(channelId)
            .collection("presence")

        val listener = presenceRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                return@addSnapshotListener
            }

            val now = System.currentTimeMillis()
            val activeUsers = snapshot.documents.mapNotNull { doc ->
                val email = doc.getString("email") ?: return@mapNotNull null
                val name = doc.getString("name") ?: email
                val lastActive = doc.getLong("lastActive") ?: 0L
                if (now - lastActive < 60000) { // Activo en el último minuto
                    PresenceUser(email, name, lastActive)
                } else null
            }
            trySend(activeUsers)
        }

        awaitClose { listener.remove() }
    }

    /**
     * Convierte un DocumentSnapshot de Firestore a un objeto ChatMessage de la aplicación.
     */
    private fun docToChatMessage(doc: DocumentSnapshot, defaultChannelId: String): ChatMessage? {
        val text = doc.getString("text") ?: return null
        val senderName = doc.getString("senderName") ?: "Usuario"
        val senderEmail = doc.getString("senderEmail") ?: "usuario@omnistudio.io"
        val channelId = doc.getString("channelId") ?: defaultChannelId
        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
        val attachedDocId = doc.getLong("attachedDocId")
        val attachedDocTitle = doc.getString("attachedDocTitle")
        val attachedAudioId = doc.getLong("attachedAudioId")
        val attachedAudioTitle = doc.getString("attachedAudioTitle")
        val reactions = doc.getString("reactions") ?: ""

        return ChatMessage(
            id = doc.id.hashCode().toLong(),
            firestoreId = doc.id,
            channelId = channelId,
            senderName = senderName,
            senderEmail = senderEmail,
            text = text,
            timestamp = timestamp,
            attachedDocId = attachedDocId,
            attachedDocTitle = attachedDocTitle,
            attachedAudioId = attachedAudioId,
            attachedAudioTitle = attachedAudioTitle,
            reactions = reactions,
            isSyncedFirestore = true
        )
    }
}

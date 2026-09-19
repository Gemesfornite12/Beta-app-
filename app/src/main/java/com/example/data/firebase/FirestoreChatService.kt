package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.model.AudioProject
import com.example.data.model.CallSession
import com.example.data.model.ChatMessage
import com.example.data.model.DocumentItem
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

data class GroupMember(
    val email: String,
    val name: String,
    val role: String = "member", // "owner", "admin", "member"
    val canSendMessages: Boolean = true,
    val canSendMedia: Boolean = true,
    val canInviteMembers: Boolean = true,
    val avatarUrl: String = ""
)

data class ChannelInfo(
    val id: String,
    val name: String,
    val description: String,
    val iconEmoji: String,
    val isDirect: Boolean = false,
    val isGroup: Boolean = false,
    val groupPhotoUrl: String = "",
    val creatorEmail: String = "",
    val creatorName: String = "",
    val members: List<GroupMember> = emptyList(),
    val pendingDeletionTimestamp: Long? = null,
    val isDeleting: Boolean = false
)

class FirestoreChatService(private val context: Context) {

    private val TAG = "FirestoreChatService"

    private val _connectionStatus = MutableStateFlow(FirestoreConnectionStatus.CONNECTING)
    val connectionStatus: StateFlow<FirestoreConnectionStatus> = _connectionStatus.asStateFlow()

    private fun getDb(): FirebaseFirestore? {
        ensureFirebaseInitialized()
        return try {
            val db = FirebaseFirestore.getInstance()
            Log.d(TAG, "Firestore instancia obtenida. Proyecto: ${db.app.options.projectId}")
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            db.firestoreSettings = settings
            _connectionStatus.value = FirestoreConnectionStatus.CONNECTED_REALTIME
            db
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo Firestore: ${e.message}", e)
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
                FirebaseApp.initializeApp(context)
                Log.d(TAG, "FirebaseApp initialized automatically using google-services.json")
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
        val db = getDb()
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
                if (snapshot.metadata.isFromCache) {
                    _connectionStatus.value = FirestoreConnectionStatus.OFFLINE_SYNCED
                } else {
                    _connectionStatus.value = FirestoreConnectionStatus.CONNECTED_REALTIME
                }
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
        val db = getDb() ?: return null
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
                "mediaType" to message.mediaType,
                "mediaUrl" to message.mediaUrl,
                "mediaThumbnail" to message.mediaThumbnail,
                "callDurationSec" to message.callDurationSec,
                "reactions" to message.reactions,
                "deliveryStatus" to message.deliveryStatus,
                "sentTimestamp" to (if (message.sentTimestamp > 0) message.sentTimestamp else message.timestamp),
                "deliveredTimestamp" to message.deliveredTimestamp,
                "seenTimestamp" to message.seenTimestamp,
                "seenBy" to message.seenBy
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
        val db = getDb() ?: return false
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
        val db = getDb() ?: return false
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
        val db = getDb() ?: return
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
        val db = getDb()
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
        val db = getDb() ?: return
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
        val db = getDb()
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
        val mediaType = doc.getString("mediaType") ?: ""
        val mediaUrl = doc.getString("mediaUrl")
        val mediaThumbnail = doc.getString("mediaThumbnail")
        val callDurationSec = doc.getLong("callDurationSec")?.toInt() ?: 0
        val reactions = doc.getString("reactions") ?: ""
        val deliveryStatus = doc.getString("deliveryStatus") ?: "enviado"
        val sentTimestamp = doc.getLong("sentTimestamp") ?: timestamp
        val deliveredTimestamp = doc.getLong("deliveredTimestamp") ?: 0L
        val seenTimestamp = doc.getLong("seenTimestamp") ?: 0L
        val seenBy = doc.getString("seenBy") ?: ""

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
            mediaType = mediaType,
            mediaUrl = mediaUrl,
            mediaThumbnail = mediaThumbnail,
            callDurationSec = callDurationSec,
            reactions = reactions,
            isSyncedFirestore = true,
            deliveryStatus = deliveryStatus,
            sentTimestamp = sentTimestamp,
            deliveredTimestamp = deliveredTimestamp,
            seenTimestamp = seenTimestamp,
            seenBy = seenBy
        )
    }

    /**
     * Marca mensajes de un canal en Firestore como 'entregado' cuando un cliente los recibe.
     */
    suspend fun markChannelMessagesAsDelivered(channelId: String, currentRecipientEmail: String) {
        val db = getDb() ?: return
        try {
            val snapshot = db.collection("chat_channels")
                .document(channelId)
                .collection("messages")
                .whereEqualTo("deliveryStatus", "enviado")
                .get()
                .await()

            val now = System.currentTimeMillis()
            for (doc in snapshot.documents) {
                val sender = doc.getString("senderEmail") ?: ""
                if (sender != currentRecipientEmail) {
                    doc.reference.update(
                        mapOf(
                            "deliveryStatus" to "entregado",
                            "deliveredTimestamp" to now
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error marking messages as delivered: ${e.message}")
        }
    }

    /**
     * Marca mensajes de un canal en Firestore como 'visto' (leído) con fecha y usuario.
     */
    suspend fun markChannelMessagesAsSeen(channelId: String, currentRecipientEmail: String) {
        val db = getDb() ?: return
        try {
            val snapshot = db.collection("chat_channels")
                .document(channelId)
                .collection("messages")
                .get()
                .await()

            val now = System.currentTimeMillis()
            for (doc in snapshot.documents) {
                val sender = doc.getString("senderEmail") ?: ""
                val status = doc.getString("deliveryStatus") ?: "enviado"
                if (sender != currentRecipientEmail && status != "visto") {
                    val currentSeenBy = doc.getString("seenBy") ?: ""
                    val updatedSeenBy = if (currentSeenBy.isBlank()) currentRecipientEmail
                    else if (!currentSeenBy.contains(currentRecipientEmail)) "$currentSeenBy, $currentRecipientEmail"
                    else currentSeenBy

                    doc.reference.update(
                        mapOf(
                            "deliveryStatus" to "visto",
                            "seenTimestamp" to now,
                            "seenBy" to updatedSeenBy
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error marking messages as seen: ${e.message}")
        }
    }

    /**
     * Actualiza directamente el estado de entrega en Firestore para un mensaje específico.
     */
    suspend fun updateMessageDeliveryStatus(
        channelId: String,
        firestoreId: String,
        status: String,
        seenByEmail: String? = null
    ) {
        val db = getDb() ?: return
        if (firestoreId.isBlank()) return
        try {
            val updates = mutableMapOf<String, Any>(
                "deliveryStatus" to status
            )
            val now = System.currentTimeMillis()
            when (status) {
                "entregado" -> updates["deliveredTimestamp"] = now
                "visto" -> {
                    updates["seenTimestamp"] = now
                    if (!seenByEmail.isNullOrBlank()) {
                        updates["seenBy"] = seenByEmail
                    }
                }
            }
            db.collection("chat_channels")
                .document(channelId)
                .collection("messages")
                .document(firestoreId)
                .update(updates)
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Error updating delivery status in Firestore: ${e.message}")
        }
    }

    /**
     * Sincroniza un documento a Firestore en la colección 'cloud_documents'.
     */
    suspend fun syncDocument(doc: DocumentItem): String? {
        val db = getDb() ?: return null
        return try {
            val docRef = if (doc.firestoreId.isNotBlank()) {
                db.collection("cloud_documents").document(doc.firestoreId)
            } else {
                db.collection("cloud_documents").document()
            }
            val firestoreId = docRef.id
            val data = hashMapOf(
                "firestoreId" to firestoreId,
                "localId" to doc.id,
                "title" to doc.title,
                "content" to doc.content,
                "docType" to doc.docType.name,
                "currentFormat" to doc.currentFormat.name,
                "slideCount" to doc.slideCount,
                "slidesJson" to doc.slidesJson,
                "authorEmail" to doc.authorEmail,
                "lastModified" to doc.lastModified,
                "lastSyncedFirestore" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
            firestoreId
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing document to Firestore: ${e.message}")
            null
        }
    }

    /**
     * Sincroniza un proyecto musical a Firestore en la colección 'cloud_audio_projects'.
     */
    suspend fun syncAudioProject(project: AudioProject): String? {
        val db = getDb() ?: return null
        return try {
            val docRef = if (project.firestoreId.isNotBlank()) {
                db.collection("cloud_audio_projects").document(project.firestoreId)
            } else {
                db.collection("cloud_audio_projects").document()
            }
            val firestoreId = docRef.id
            val data = hashMapOf(
                "firestoreId" to firestoreId,
                "localId" to project.id,
                "title" to project.title,
                "description" to project.description,
                "genre" to project.genre,
                "bpm" to project.bpm,
                "patternDataJson" to project.patternDataJson,
                "authorEmail" to project.authorEmail,
                "authorName" to project.authorName,
                "isPublic" to project.isPublic,
                "aiPrompt" to project.aiPrompt,
                "notesMelody" to project.notesMelody,
                "durationSeconds" to project.durationSeconds,
                "lastModified" to project.lastModified,
                "lastSyncedFirestore" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
            firestoreId
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing audio project to Firestore: ${e.message}")
            null
        }
    }

    /**
     * Inicia una señal de llamada en Firestore.
     */
    suspend fun startCallSignal(call: CallSession): Boolean {
        val db = getDb() ?: return false
        return try {
            val callRef = db.collection("chat_channels")
                .document(call.channelId)
                .collection("active_calls")
                .document(call.callId)
            val data = hashMapOf(
                "callId" to call.callId,
                "channelId" to call.channelId,
                "peerName" to call.peerName,
                "peerEmail" to call.peerEmail,
                "isVideo" to call.isVideo,
                "status" to call.status.name,
                "startTimeMs" to System.currentTimeMillis()
            )
            callRef.set(data).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting call signal in Firestore: ${e.message}")
            false
        }
    }

    /**
     * Finaliza la llamada en Firestore.
     */
    suspend fun endCallSignal(channelId: String, callId: String) {
        val db = getDb() ?: return
        try {
            db.collection("chat_channels")
                .document(channelId)
                .collection("active_calls")
                .document(callId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error ending call signal in Firestore: ${e.message}")
        }
    }

    /**
     * Guarda o actualiza la información y metadatos de un canal o grupo en Firestore.
     */
    suspend fun saveOrUpdateChannel(channel: ChannelInfo): Boolean {
        val db = getDb() ?: return false
        return try {
            val membersData = channel.members.map { m ->
                mapOf(
                    "email" to m.email,
                    "name" to m.name,
                    "role" to m.role,
                    "canSendMessages" to m.canSendMessages,
                    "canSendMedia" to m.canSendMedia,
                    "canInviteMembers" to m.canInviteMembers,
                    "avatarUrl" to m.avatarUrl
                )
            }
            val data = hashMapOf(
                "id" to channel.id,
                "name" to channel.name,
                "description" to channel.description,
                "iconEmoji" to channel.iconEmoji,
                "isDirect" to channel.isDirect,
                "isGroup" to channel.isGroup,
                "groupPhotoUrl" to channel.groupPhotoUrl,
                "creatorEmail" to channel.creatorEmail,
                "creatorName" to channel.creatorName,
                "members" to membersData,
                "isDeleting" to channel.isDeleting,
                "pendingDeletionTimestamp" to channel.pendingDeletionTimestamp,
                "lastUpdated" to System.currentTimeMillis()
            )
            db.collection("chat_channels")
                .document(channel.id)
                .set(data, SetOptions.merge())
                .await()
            Log.d(TAG, "Channel metadata synced to Firestore: ${channel.name} (${channel.id})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing channel info to Firestore: ${e.message}")
            false
        }
    }

    /**
     * Escucha en tiempo real todos los canales y grupos guardados en Firestore.
     */
    fun listenToCustomChannels(): Flow<List<ChannelInfo>> = callbackFlow {
        val db = getDb()
        if (db == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val channelsRef = db.collection("chat_channels")
        val listener = channelsRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                return@addSnapshotListener
            }

            val channelList = snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val name = doc.getString("name") ?: return@mapNotNull null
                val description = doc.getString("description") ?: ""
                val iconEmoji = doc.getString("iconEmoji") ?: "💬"
                val isDirect = doc.getBoolean("isDirect") ?: false
                val isGroup = doc.getBoolean("isGroup") ?: false
                val groupPhotoUrl = doc.getString("groupPhotoUrl") ?: ""
                val creatorEmail = doc.getString("creatorEmail") ?: ""
                val creatorName = doc.getString("creatorName") ?: ""
                val isDeleting = doc.getBoolean("isDeleting") ?: false
                val pendingDeletionTimestamp = doc.getLong("pendingDeletionTimestamp")

                @Suppress("UNCHECKED_CAST")
                val rawMembers = doc.get("members") as? List<Map<String, Any>> ?: emptyList()
                val members = rawMembers.map { m ->
                    GroupMember(
                        email = m["email"] as? String ?: "",
                        name = m["name"] as? String ?: "",
                        role = m["role"] as? String ?: "member",
                        canSendMessages = m["canSendMessages"] as? Boolean ?: true,
                        canSendMedia = m["canSendMedia"] as? Boolean ?: true,
                        canInviteMembers = m["canInviteMembers"] as? Boolean ?: true,
                        avatarUrl = m["avatarUrl"] as? String ?: ""
                    )
                }

                ChannelInfo(
                    id = id,
                    name = name,
                    description = description,
                    iconEmoji = iconEmoji,
                    isDirect = isDirect,
                    isGroup = isGroup,
                    groupPhotoUrl = groupPhotoUrl,
                    creatorEmail = creatorEmail,
                    creatorName = creatorName,
                    members = members,
                    pendingDeletionTimestamp = pendingDeletionTimestamp,
                    isDeleting = isDeleting
                )
            }
            trySend(channelList)
        }

        awaitClose { listener.remove() }
    }

    /**
     * Elimina el canal de Firestore.
     */
    suspend fun deleteChannelFromFirestore(channelId: String): Boolean {
        val db = getDb() ?: return false
        return try {
            db.collection("chat_channels").document(channelId).delete().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting channel from Firestore: ${e.message}")
            false
        }
    }

    /**
     * Sincroniza el perfil de usuario (nombre y avatar) en la nube de Firestore.
     */
    suspend fun saveUserProfileToCloud(email: String, displayName: String, avatarUrl: String): Boolean {
        val db = getDb()
        if (db == null) {
            Log.e(TAG, "saveUserProfileToCloud: Firestore es NULO. No se puede guardar.")
            return false
        }
        val cleanEmail = email.replace(".", "_").replace("@", "_at_")
        Log.d(TAG, "Intentando guardar perfil para: $cleanEmail")
        return try {
            val data = hashMapOf(
                "email" to email,
                "displayName" to displayName,
                "avatarUrl" to avatarUrl,
                "lastUpdated" to System.currentTimeMillis()
            )
            db.collection("user_profiles")
                .document(cleanEmail)
                .set(data, SetOptions.merge())
                .await()
            Log.d(TAG, "User profile SYNCED SUCCESSFULLY to Firestore: $displayName ($email)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR saving user profile to Firestore: ${e.message}", e)
            false
        }
    }

    /**
     * Obtiene el perfil de un usuario desde la nube de Firestore.
     */
    suspend fun getUserProfileFromCloud(email: String): Map<String, Any>? {
        val db = getDb() ?: return null
        val cleanEmail = email.replace(".", "_").replace("@", "_at_")
        return try {
            val doc = db.collection("user_profiles")
                .document(cleanEmail)
                .get()
                .await()
            if (doc.exists()) {
                doc.data
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching user profile from Firestore: ${e.message}")
            null
        }
    }

    /**
     * Obtiene todos los mensajes de un canal de una sola vez.
     */
    suspend fun getChannelMessagesOnce(channelId: String): List<ChatMessage> {
        val db = getDb() ?: return emptyList()
        return try {
            val snapshot = db.collection("chat_channels")
                .document(channelId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                docToChatMessage(doc, channelId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching messages once for $channelId: ${e.message}")
            emptyList()
        }
    }
}

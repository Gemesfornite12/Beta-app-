package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.model.AudioProject
import com.example.data.model.CallSession
import com.example.data.model.ChatMessage
import com.example.data.model.DocumentItem
import com.example.data.model.DocumentType
import com.example.data.model.DocumentFormat
import com.example.data.model.UserAccount
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

enum class FirestoreConnectionStatus(val label: String, val isLive: Boolean) {
    CONNECTED_REALTIME("Firebase Realtime • En vivo", true),
    CONNECTING("Conectando con Firebase...", false),
    OFFLINE_SYNCED("Firebase • Modo local", true),
    DISCONNECTED("Desconectado de Firebase", false)
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

    private val rtdbInstance: FirebaseDatabase? by lazy {
        try {
            // Usar la URL explícita para asegurar consistencia con RealtimeDatabaseService
            val db = FirebaseDatabase.getInstance("https://omnistudio-caaf5-default-rtdb.firebaseio.com")
            try { db.setPersistenceEnabled(true) } catch (_: Exception) {}
            db
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando RTDB con URL explícita: ${e.message}")
            null
        }
    }
    private val rtdbRef get() = rtdbInstance?.reference

    init {
        try {
            rtdbInstance?.getReference(".info/connected")?.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val isConnected = snapshot.getValue(Boolean::class.java) ?: false
                    Log.d(TAG, "ESTADO CONEXIÓN RTDB: $isConnected")
                    if (isConnected) {
                        _connectionStatus.value = FirestoreConnectionStatus.CONNECTED_REALTIME
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Error en listener de conexión RTDB: ${error.message}")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando listener de conexión: ${e.message}")
        }
    }

    private val _db: FirebaseFirestore? by lazy {
        try {
            val app = FirebaseAppProvider.get(context)
            val instance = FirebaseFirestore.getInstance(app)
            
            // Log crítico para verificar a qué proyecto apunta la app
            Log.d(TAG, "CONEXIÓN FIRESTORE: Proyecto=${instance.app.options.projectId}")
            
            instance.enableNetwork()
            instance
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando Firestore: ${e.message}", e)
            null
        }
    }

    private fun getDb(): FirebaseFirestore? = _db

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

    /**
     * Escucha en tiempo real todos los mensajes de un canal en Firestore.
     * Utiliza addSnapshotListener para recibir cambios instantáneos cuando cualquier usuario escribe.
     */
    fun listenToChannelMessages(channelId: String): Flow<List<ChatMessage>> = callbackFlow {
        val rtdb = rtdbRef
        val messagesRef = rtdb?.child("chats")?.child(channelId)?.child("messages")

        val rtdbListener = if (messagesRef != null) {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val messages = snapshot.children.mapNotNull { child ->
                        snapshotToChatMessage(child, channelId)
                    }.sortedBy { it.timestamp }
                    _connectionStatus.value = FirestoreConnectionStatus.CONNECTED_REALTIME
                    trySendBlocking(messages)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "RTDB listen failed for channel $channelId: ${error.message}")
                }
            }
            messagesRef.addValueEventListener(listener)
            listener
        } else null

        // Escuchar también Firestore para compatibilidad continua
        val db = getDb()
        val firestoreRegistration = if (db != null) {
            val msgsRef = db.collection("chat_channels")
                .document(channelId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)

            msgsRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Firestore listen failed for channel $channelId: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        docToChatMessage(doc, channelId)
                    }
                    trySendBlocking(messages)
                }
            }
        } else null

        awaitClose {
            if (messagesRef != null && rtdbListener != null) {
                messagesRef.removeEventListener(rtdbListener)
            }
            firestoreRegistration?.remove()
        }
    }

    /**
     * Envía un mensaje en tiempo real a Firebase Realtime Database y Firestore.
     */
    suspend fun sendMessage(message: ChatMessage): String? {
        val rtdb = rtdbRef
        val db = getDb()
        val docId = if (message.firestoreId.isNotBlank()) message.firestoreId else "msg_${System.currentTimeMillis()}_${(1000..9999).random()}"
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

        var savedSuccess = false

        // 1. Guardar en Realtime Database
        if (rtdb != null) {
            try {
                rtdb.child("chats").child(message.channelId).child("messages").child(docId).setValue(data).await()
                val channelMeta = mapOf(
                    "lastMessageText" to message.text,
                    "lastMessageTimestamp" to message.timestamp,
                    "lastMessageSender" to message.senderName,
                    "lastUpdated" to System.currentTimeMillis()
                )
                rtdb.child("chats").child(message.channelId).updateChildren(channelMeta).await()
                savedSuccess = true
                _connectionStatus.value = FirestoreConnectionStatus.CONNECTED_REALTIME
                Log.d(TAG, "Mensaje guardado en RTDB: $docId en ${message.channelId}")
            } catch (e: Exception) {
                if (e.message?.contains("Permission denied", ignoreCase = true) == true) {
                    Log.w(TAG, "Permiso denegado en RTDB para enviar mensaje, usando Firestore como primario.")
                } else {
                    Log.e(TAG, "Error guardando en RTDB: ${e.message}")
                }
            }
        }

        // 2. Guardar en Firestore como respaldo
        if (db != null) {
            try {
                val docRef = db.collection("chat_channels")
                    .document(message.channelId)
                    .collection("messages")
                    .document(docId)

                docRef.set(data).await()

                val channelMeta = hashMapOf(
                    "lastMessageText" to message.text,
                    "lastMessageTimestamp" to message.timestamp,
                    "lastMessageSender" to message.senderName
                )
                db.collection("chat_channels")
                    .document(message.channelId)
                    .set(channelMeta, SetOptions.merge())

                savedSuccess = true
                _connectionStatus.value = FirestoreConnectionStatus.CONNECTED_REALTIME
            } catch (e: Exception) {
                Log.w(TAG, "Error guardando mensaje en Firestore: ${e.message}")
            }
        }

        return if (savedSuccess) docId else null
    }

    /**
     * Agrega una reacción emoji en tiempo real a un mensaje.
     */
    suspend fun addReaction(channelId: String, firestoreId: String, emoji: String, currentReactions: String): Boolean {
        if (firestoreId.isBlank()) return false
        val updatedReactions = if (currentReactions.isBlank()) emoji else "$currentReactions,$emoji"
        return updateReactions(channelId, firestoreId, updatedReactions)
    }

    /**
     * Actualiza la cadena completa de reacciones emoji en RTDB y Firestore.
     */
    suspend fun updateReactions(channelId: String, firestoreId: String, newReactions: String): Boolean {
        if (firestoreId.isBlank()) return false
        val rtdb = rtdbRef
        if (rtdb != null) {
            try {
                rtdb.child("chats").child(channelId).child("messages").child(firestoreId).child("reactions").setValue(newReactions).await()
            } catch (e: Exception) {
                Log.w(TAG, "Error actualizando reacción en RTDB: ${e.message}")
            }
        }
        val db = getDb()
        if (db != null) {
            try {
                db.collection("chat_channels")
                    .document(channelId)
                    .collection("messages")
                    .document(firestoreId)
                    .update("reactions", newReactions)
                    .await()
            } catch (_: Exception) {}
        }
        return true
    }

    /**
     * Elimina un mensaje en tiempo real.
     */
    suspend fun deleteMessage(channelId: String, firestoreId: String): Boolean {
        if (firestoreId.isBlank()) return false
        val rtdb = rtdbRef
        if (rtdb != null) {
            try {
                rtdb.child("chats").child(channelId).child("messages").child(firestoreId).removeValue().await()
            } catch (e: Exception) {
                Log.w(TAG, "Error borrando mensaje en RTDB: ${e.message}")
            }
        }
        val db = getDb()
        if (db != null) {
            try {
                db.collection("chat_channels")
                    .document(channelId)
                    .collection("messages")
                    .document(firestoreId)
                    .delete()
                    .await()
            } catch (_: Exception) {}
        }
        return true
    }

    /**
     * Publica el estado de escritura ("typing indicator") en tiempo real.
     */
    suspend fun setTypingStatus(channelId: String, userEmail: String, userName: String, isTyping: Boolean) {
        val cleanEmailKey = userEmail.replace(".", "_").replace("@", "_at_")
        val rtdb = rtdbRef
        if (rtdb != null) {
            try {
                val ref = rtdb.child("typing").child(channelId).child(cleanEmailKey)
                if (isTyping) {
                    ref.setValue(userName).await()
                } else {
                    ref.removeValue().await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error actualizando tipado en RTDB: ${e.message}")
            }
        }
        val db = getDb()
        if (db != null) {
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
            } catch (_: Exception) {}
        }
    }

    /**
     * Escucha en tiempo real quiénes están escribiendo en el canal.
     */
    fun listenToTyping(channelId: String, currentUserEmail: String): Flow<List<String>> = callbackFlow {
        val myKey = currentUserEmail.replace(".", "_").replace("@", "_at_")
        val rtdb = rtdbRef
        val typingRef = rtdb?.child("typing")?.child(channelId)

        val rtdbListener = if (typingRef != null) {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val typers = snapshot.children.mapNotNull { child ->
                        if (child.key != myKey) {
                            child.getValue(String::class.java)
                        } else null
                    }
                    trySendBlocking(typers)
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            typingRef.addValueEventListener(listener)
            listener
        } else null

        val db = getDb()
        val fsListener = if (db != null && typingRef == null) {
            val ref = db.collection("chat_channels").document(channelId).collection("typing")
            ref.addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val typers = snapshot.documents.mapNotNull { doc ->
                        if (doc.id != myKey && (doc.getBoolean("isTyping") == true)) doc.getString("userName") else null
                    }
                    trySendBlocking(typers)
                }
            }
        } else null

        awaitClose {
            if (typingRef != null && rtdbListener != null) {
                typingRef.removeEventListener(rtdbListener)
            }
            fsListener?.remove()
        }
    }

    /**
     * Actualiza la presencia activa de un usuario en el canal.
     */
    suspend fun updatePresence(channelId: String, userEmail: String, userName: String) {
        val cleanKey = userEmail.replace(".", "_").replace("@", "_at_")
        val presenceData = mapOf(
            "email" to userEmail,
            "name" to userName,
            "lastActive" to System.currentTimeMillis()
        )
        val rtdb = rtdbRef
        if (rtdb != null) {
            try {
                rtdb.child("presence").child(channelId).child(cleanKey).setValue(presenceData).await()
            } catch (e: Exception) {
                Log.w(TAG, "Error actualizando presencia en RTDB: ${e.message}")
            }
        }
        val db = getDb()
        if (db != null) {
            try {
                db.collection("chat_channels")
                    .document(channelId)
                    .collection("presence")
                    .document(cleanKey)
                    .set(presenceData, SetOptions.merge())
                    .await()
            } catch (_: Exception) {}
        }
    }

    /**
     * Escucha en tiempo real los usuarios conectados en este canal.
     */
    fun listenToPresence(channelId: String): Flow<List<PresenceUser>> = callbackFlow {
        val rtdb = rtdbRef
        val presenceRef = rtdb?.child("presence")?.child(channelId)

        val rtdbListener = if (presenceRef != null) {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val now = System.currentTimeMillis()
                    val activeUsers = snapshot.children.mapNotNull { child ->
                        val map = child.value as? Map<*, *> ?: return@mapNotNull null
                        val email = map["email"] as? String ?: return@mapNotNull null
                        val name = (map["name"] as? String) ?: email
                        val lastActive = (map["lastActive"] as? Number)?.toLong() ?: 0L
                        if (now - lastActive < 120000) { // Activo en los últimos 2 minutos
                            PresenceUser(email, name, lastActive)
                        } else null
                    }
                    trySendBlocking(activeUsers)
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            presenceRef.addValueEventListener(listener)
            listener
        } else null

        val db = getDb()
        val fsListener = if (db != null && presenceRef == null) {
            val ref = db.collection("chat_channels").document(channelId).collection("presence")
            ref.addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val now = System.currentTimeMillis()
                    val activeUsers = snapshot.documents.mapNotNull { doc ->
                        val email = doc.getString("email") ?: return@mapNotNull null
                        val name = doc.getString("name") ?: email
                        val lastActive = doc.getLong("lastActive") ?: 0L
                        if (now - lastActive < 120000) {
                            PresenceUser(email, name, lastActive)
                        } else null
                    }
                    trySendBlocking(activeUsers)
                }
            }
        } else null

        awaitClose {
            if (presenceRef != null && rtdbListener != null) {
                presenceRef.removeEventListener(rtdbListener)
            }
            fsListener?.remove()
        }
    }

    /**
     * Convierte un DocumentSnapshot de Firestore a un objeto ChatMessage de la aplicación.
     */
    private fun docToChatMessage(doc: DocumentSnapshot, defaultChannelId: String): ChatMessage? {
        val text = doc.getString("text") ?: ""
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
     * Convierte un DataSnapshot de Firebase Realtime Database a un objeto ChatMessage.
     */
    private fun snapshotToChatMessage(snap: DataSnapshot, defaultChannelId: String): ChatMessage? {
        val map = snap.value as? Map<*, *> ?: return null
        val text = map["text"] as? String ?: ""
        val senderName = (map["senderName"] as? String) ?: "Usuario"
        val senderEmail = (map["senderEmail"] as? String) ?: "usuario@omnistudio.io"
        val channelId = (map["channelId"] as? String) ?: defaultChannelId
        val timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val attachedDocId = (map["attachedDocId"] as? Number)?.toLong()
        val attachedDocTitle = map["attachedDocTitle"] as? String
        val attachedAudioId = (map["attachedAudioId"] as? Number)?.toLong()
        val attachedAudioTitle = map["attachedAudioTitle"] as? String
        val mediaType = (map["mediaType"] as? String) ?: ""
        val mediaUrl = map["mediaUrl"] as? String
        val mediaThumbnail = map["mediaThumbnail"] as? String
        val callDurationSec = (map["callDurationSec"] as? Number)?.toInt() ?: 0
        val reactions = (map["reactions"] as? String) ?: ""
        val deliveryStatus = (map["deliveryStatus"] as? String) ?: "enviado"
        val sentTimestamp = (map["sentTimestamp"] as? Number)?.toLong() ?: timestamp
        val deliveredTimestamp = (map["deliveredTimestamp"] as? Number)?.toLong() ?: 0L
        val seenTimestamp = (map["seenTimestamp"] as? Number)?.toLong() ?: 0L
        val seenBy = (map["seenBy"] as? String) ?: ""
        val firestoreId = (map["firestoreId"] as? String) ?: snap.key ?: ""

        return ChatMessage(
            id = (snap.key?.hashCode() ?: firestoreId.hashCode()).toLong(),
            firestoreId = firestoreId,
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
        val rtdb = rtdbRef
        val db = getDb()
        val now = System.currentTimeMillis()
        
        if (rtdb != null) {
            try {
                val snap = rtdb.child("chats").child(channelId).child("messages").get().await()
                for (child in snap.children) {
                    val sender = child.child("senderEmail").getValue(String::class.java) ?: ""
                    val status = child.child("deliveryStatus").getValue(String::class.java) ?: "enviado"
                    if (sender != currentRecipientEmail && status != "visto") {
                        val currentSeenBy = child.child("seenBy").getValue(String::class.java) ?: ""
                        val updatedSeenBy = if (currentSeenBy.isBlank()) currentRecipientEmail
                        else if (!currentSeenBy.contains(currentRecipientEmail)) "$currentSeenBy, $currentRecipientEmail"
                        else currentSeenBy
                        
                        val updates = mapOf(
                            "deliveryStatus" to "visto",
                            "seenTimestamp" to now,
                            "seenBy" to updatedSeenBy
                        )
                        child.ref.updateChildren(updates)
                    }
                }
            } catch (_: Exception) {}
        }

        if (db != null) {
            try {
                val snapshot = db.collection("chat_channels")
                    .document(channelId)
                    .collection("messages")
                    .get()
                    .await()

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
                Log.w(TAG, "Error marking messages as seen in FS: ${e.message}")
            }
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
        if (firestoreId.isBlank()) return
        val rtdb = rtdbRef
        val db = getDb()
        val now = System.currentTimeMillis()
        val updates = mutableMapOf<String, Any>("deliveryStatus" to status)
        
        when (status) {
            "entregado" -> updates["deliveredTimestamp"] = now
            "visto" -> {
                updates["seenTimestamp"] = now
                if (!seenByEmail.isNullOrBlank()) {
                    updates["seenBy"] = seenByEmail
                }
            }
        }

        if (rtdb != null) {
            try {
                rtdb.child("chats").child(channelId).child("messages").child(firestoreId).updateChildren(updates).await()
            } catch (_: Exception) {}
        }

        if (db != null) {
            try {
                db.collection("chat_channels")
                    .document(channelId)
                    .collection("messages")
                    .document(firestoreId)
                    .update(updates)
                    .await()
            } catch (_: Exception) {}
        }
    }

    /**
     * Sincroniza un documento a Firebase Realtime Database y Firestore.
     */
    suspend fun syncDocument(doc: DocumentItem): String? {
        val rtdb = rtdbRef
        val db = getDb()
        val docId = if (doc.firestoreId.isNotBlank()) doc.firestoreId else "doc_${System.currentTimeMillis()}_${(1000..9999).random()}"
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
        val cleanAuthor = doc.authorEmail.replace(".", "_").replace("@", "_at_")
        val data = hashMapOf(
            "firestoreId" to docId,
            "localId" to doc.id,
            "ownerUid" to uid,
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

        var saved = false
        if (rtdb != null) {
            try {
                // Sincronizar con RTDB siguiendo las reglas de seguridad: documents/{uid}/{docId}
                if (!uid.isNullOrBlank()) {
                    rtdb.child("documents").child(uid).child(docId).setValue(data).await()
                    saved = true
                    Log.d(TAG, "Documento sincronizado en RTDB (user path): $docId")
                } else {
                    Log.w(TAG, "No se pudo sincronizar con RTDB: UID nulo o vacío")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando documento en RTDB: ${e.message}")
            }
        }

        if (db != null) {
            try {
                val docRef = db.collection("cloud_documents").document(docId)
                docRef.set(data, SetOptions.merge()).await()
                saved = true
            } catch (e: Exception) {
                Log.w(TAG, "Error syncing document to Firestore: ${e.message}")
            }
        }

        return if (saved) docId else null
    }

    /**
     * Sincroniza un proyecto musical a Firebase Realtime Database y Firestore.
     */
    suspend fun syncAudioProject(project: AudioProject): String? {
        val rtdb = rtdbRef
        val db = getDb()
        val docId = if (project.firestoreId.isNotBlank()) project.firestoreId else "audio_${System.currentTimeMillis()}_${(1000..9999).random()}"
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
        
        val data = hashMapOf(
            "firestoreId" to docId,
            "ownerUid" to uid,
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

        var saved = false
        if (rtdb != null) {
            try {
                if (!uid.isNullOrBlank()) {
                    // Guardar en la ruta estructurada por UID
                    rtdb.child("audio_projects").child(uid).child(docId).setValue(data).await()
                    saved = true
                    Log.d(TAG, "Audio project sincronizado en RTDB (user path): $docId")
                } else {
                    Log.w(TAG, "No se pudo sincronizar Audio: UID nulo")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando audio en RTDB: ${e.message}")
            }
        }

        if (db != null) {
            try {
                val docRef = db.collection("cloud_audio_projects").document(docId)
                docRef.set(data, SetOptions.merge()).await()
                saved = true
            } catch (_: Exception) {}
        }

        return if (saved) docId else null
    }

    /**
     * Inicia una señal de llamada en RTDB y Firestore.
     */
    suspend fun startCallSignal(call: CallSession): Boolean {
        val data = hashMapOf(
            "callId" to call.callId,
            "channelId" to call.channelId,
            "peerName" to call.peerName,
            "peerEmail" to call.peerEmail,
            "isVideo" to call.isVideo,
            "status" to call.status.name,
            "startTimeMs" to System.currentTimeMillis()
        )
        val rtdb = rtdbRef
        if (rtdb != null) {
            try {
                rtdb.child("calls").child(call.channelId).child(call.callId).setValue(data).await()
            } catch (_: Exception) {}
        }
        val db = getDb()
        if (db != null) {
            try {
                db.collection("chat_channels")
                    .document(call.channelId)
                    .collection("active_calls")
                    .document(call.callId)
                    .set(data)
                    .await()
            } catch (_: Exception) {}
        }
        return true
    }

    /**
     * Finaliza la llamada en RTDB y Firestore.
     */
    suspend fun endCallSignal(channelId: String, callId: String) {
        val rtdb = rtdbRef
        if (rtdb != null) {
            try {
                rtdb.child("calls").child(channelId).child(callId).removeValue().await()
            } catch (_: Exception) {}
        }
        val db = getDb() ?: return
        try {
            db.collection("chat_channels")
                .document(channelId)
                .collection("active_calls")
                .document(callId)
                .delete()
                .await()
        } catch (_: Exception) {}
    }

    /**
     * Guarda o actualiza la información y metadatos de un canal o grupo en RTDB y Firestore.
     */
    suspend fun saveOrUpdateChannel(channel: ChannelInfo): Boolean {
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

        var success = false
        val rtdb = rtdbRef
        if (rtdb != null) {
            try {
                rtdb.child("chats").child(channel.id).updateChildren(data as Map<String, Any>).await()
                success = true
                Log.d(TAG, "Canal/Chat sincronizado en RTDB: ${channel.name} (${channel.id})")
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando canal en RTDB: ${e.message}")
            }
        }

        val db = getDb()
        if (db != null) {
            try {
                db.collection("chat_channels")
                    .document(channel.id)
                    .set(data, SetOptions.merge())
                    .await()
                success = true
            } catch (_: Exception) {}
        }

        return success
    }

    private fun snapshotToChannelInfo(snap: DataSnapshot): ChannelInfo? {
        val map = snap.value as? Map<*, *> ?: return null
        val id = (map["id"] as? String) ?: snap.key ?: return null
        val name = (map["name"] as? String) ?: return null
        val description = (map["description"] as? String) ?: ""
        val iconEmoji = (map["iconEmoji"] as? String) ?: "💬"
        val isDirect = (map["isDirect"] as? Boolean) ?: false
        val isGroup = (map["isGroup"] as? Boolean) ?: false
        val groupPhotoUrl = (map["groupPhotoUrl"] as? String) ?: ""
        val creatorEmail = (map["creatorEmail"] as? String) ?: ""
        val creatorName = (map["creatorName"] as? String) ?: ""
        val isDeleting = (map["isDeleting"] as? Boolean) ?: false
        val pendingDeletionTimestamp = (map["pendingDeletionTimestamp"] as? Number)?.toLong()

        val membersList = mutableListOf<GroupMember>()
        val rawMembers = map["members"] as? List<*>
        if (rawMembers != null) {
            for (m in rawMembers) {
                val memMap = m as? Map<*, *> ?: continue
                val email = (memMap["email"] as? String) ?: continue
                val memName = (memMap["name"] as? String) ?: email
                val role = (memMap["role"] as? String) ?: "member"
                val canSend = (memMap["canSendMessages"] as? Boolean) ?: true
                val canMedia = (memMap["canSendMedia"] as? Boolean) ?: true
                val canInvite = (memMap["canInviteMembers"] as? Boolean) ?: true
                val avatar = (memMap["avatarUrl"] as? String) ?: ""
                membersList.add(GroupMember(email, memName, role, canSend, canMedia, canInvite, avatar))
            }
        }

        return ChannelInfo(
            id = id,
            name = name,
            description = description,
            iconEmoji = iconEmoji,
            isDirect = isDirect,
            isGroup = isGroup,
            groupPhotoUrl = groupPhotoUrl,
            creatorEmail = creatorEmail,
            creatorName = creatorName,
            members = membersList,
            pendingDeletionTimestamp = pendingDeletionTimestamp,
            isDeleting = isDeleting
        )
    }

    private fun docToChannelInfo(doc: DocumentSnapshot): ChannelInfo? {
        val id = doc.getString("id") ?: doc.id
        val name = doc.getString("name") ?: return null
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

        return ChannelInfo(
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

    /**
     * Escucha en tiempo real todos los canales y grupos guardados en RTDB y Firestore.
     */
    fun listenToCustomChannels(): Flow<List<ChannelInfo>> = callbackFlow {
        val rtdb = rtdbRef
        val chatsRef = rtdb?.child("chats")

        val rtdbListener = if (chatsRef != null) {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val channelList = snapshot.children.mapNotNull { child ->
                        snapshotToChannelInfo(child)
                    }
                    if (channelList.isNotEmpty()) {
                        trySendBlocking(channelList)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Error escuchando chats en RTDB: ${error.message}")
                }
            }
            chatsRef.addValueEventListener(listener)
            listener
        } else null

        val db = getDb()
        val fsReg = if (db != null) {
            val channelsRef = db.collection("chat_channels")
            channelsRef.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    return@addSnapshotListener
                }
                val channelList = snapshot.documents.mapNotNull { doc ->
                    docToChannelInfo(doc)
                }
                if (channelList.isNotEmpty()) {
                    trySendBlocking(channelList)
                }
            }
        } else null

        awaitClose {
            if (chatsRef != null && rtdbListener != null) {
                chatsRef.removeEventListener(rtdbListener)
            }
            fsReg?.remove()
        }
    }

    /**
     * Elimina el canal de RTDB y Firestore.
     */
    suspend fun deleteChannelFromFirestore(channelId: String): Boolean {
        val rtdb = rtdbRef
        if (rtdb != null) {
            try {
                rtdb.child("chats").child(channelId).removeValue().await()
                Log.d(TAG, "Canal eliminado de RTDB: $channelId")
            } catch (e: Exception) {
                Log.e(TAG, "Error eliminando canal de RTDB: ${e.message}")
            }
        }
        val db = getDb()
        if (db != null) {
            try {
                db.collection("chat_channels").document(channelId).delete().await()
            } catch (_: Exception) {}
        }
        return true
    }

    /**
     * Sincroniza el perfil de usuario (nombre y avatar) en RTDB y Firestore.
     */
    suspend fun saveUserProfileToCloud(email: String, displayName: String, avatarUrl: String, uid: String? = null): Boolean {
        val cleanEmail = email.replace(".", "_").replace("@", "_at_")
        val effectiveUid = uid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: cleanEmail
        val data = hashMapOf(
            "email" to email,
            "uid" to effectiveUid,
            "displayName" to displayName,
            "avatarUrl" to avatarUrl,
            "lastUpdated" to System.currentTimeMillis()
        )

        var success = false
        val rtdb = rtdbRef
        if (rtdb != null) {
            try {
                // Solo guardar bajo el UID (obligatorio según las nuevas reglas de seguridad)
                rtdb.child("users").child(effectiveUid).setValue(data).await()
                success = true
                Log.d(TAG, "Perfil de usuario sincronizado en RTDB: $displayName ($email)")
            } catch (e: Exception) {
                if (e.message?.contains("Permission denied", ignoreCase = true) == true) {
                    Log.w(TAG, "Permiso denegado en RTDB (reglas restrictivas), continuando con Firestore.")
                } else {
                    Log.e(TAG, "Error guardando perfil en RTDB: ${e.message}")
                }
            }
        }

        val db = getDb()
        if (db != null) {
            try {
                db.collection("user_profiles")
                    .document(cleanEmail)
                    .set(data, SetOptions.merge())
                    .await()
                success = true
            } catch (_: Exception) {}
        }

        return success
    }

    /**
     * Busca usuarios en Firestore por email o nombre de usuario.
     */
    suspend fun searchUsers(query: String): List<UserAccount> {
        val db = getDb() ?: return emptyList()
        return try {
            val results = mutableListOf<UserAccount>()
            val q = query.trim()
            
            // 1. Buscar por email exacto
            val byEmail = db.collection("user_profiles")
                .whereEqualTo("email", q)
                .get()
                .await()
            
            byEmail.documents.forEach { doc ->
                results.add(docToUserAccount(doc))
            }
            
            // 2. Buscar por displayName que empiece por...
            if (q.isNotEmpty()) {
                val byName = db.collection("user_profiles")
                    .whereGreaterThanOrEqualTo("displayName", q)
                    .whereLessThanOrEqualTo("displayName", q + "\uf8ff")
                    .limit(10)
                    .get()
                    .await()
                    
                byName.documents.forEach { doc ->
                    val user = docToUserAccount(doc)
                    if (results.none { it.email == user.email }) {
                        results.add(user)
                    }
                }
            }
            
            results
        } catch (e: Exception) {
            Log.e(TAG, "Error in searchUsers: ${e.message}")
            emptyList()
        }
    }

    private fun docToUserAccount(doc: com.google.firebase.firestore.DocumentSnapshot): UserAccount {
        val email = doc.getString("email") ?: ""
        return UserAccount(
            email = email,
            username = email.substringBefore("@"),
            displayName = doc.getString("displayName") ?: email,
            avatarUrl = doc.getString("avatarUrl") ?: "",
            passwordHash = ""
        )
    }

    /**
     * Escucha todos los perfiles de usuario registrados en la nube.
     */
    fun listenToAllUsers(): Flow<List<UserAccount>> = callbackFlow {
        val rtdb = rtdbRef
        val usersRef = rtdb?.child("users")
        
        val rtdbListener = usersRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { child ->
                    val map = child.value as? Map<*, *> ?: return@mapNotNull null
                    val email = (map["email"] as? String) ?: return@mapNotNull null
                    val name = (map["displayName"] as? String) ?: email
                    val avatar = (map["avatarUrl"] as? String) ?: ""
                    UserAccount(
                        email = email,
                        username = email.substringBefore("@"),
                        displayName = name,
                        avatarUrl = avatar,
                        passwordHash = ""
                    )
                }
                trySendBlocking(list)
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        val db = getDb()
        val fsReg = if (db != null && usersRef == null) {
            db.collection("user_profiles").addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        val email = doc.getString("email") ?: return@mapNotNull null
                        UserAccount(
                            email = email,
                            username = email.substringBefore("@"),
                            displayName = doc.getString("displayName") ?: email,
                            avatarUrl = doc.getString("avatarUrl") ?: "",
                            passwordHash = ""
                        )
                    }
                    trySendBlocking(list)
                }
            }
        } else null

        awaitClose {
            if (usersRef != null && rtdbListener != null) usersRef.removeEventListener(rtdbListener)
            fsReg?.remove()
        }
    }

    /**
     * Obtiene el perfil de un usuario desde RTDB o Firestore.
     */
    suspend fun getUserProfileFromCloud(email: String): Map<String, Any>? {
        val cleanEmail = email.replace(".", "_").replace("@", "_at_")
        val rtdb = rtdbRef
        if (rtdb != null) {
            try {
                val snap = rtdb.child("users").child(cleanEmail).get().await()
                @Suppress("UNCHECKED_CAST")
                val map = snap.value as? Map<String, Any>
                if (map != null) return map
            } catch (e: Exception) {
                Log.w(TAG, "Error obteniendo perfil de RTDB: ${e.message}")
            }
        }
        val db = getDb() ?: return null
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
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Crea o recupera un chat directo (privado) entre el usuario actual y otro usuario.
     */
    suspend fun createOrGetDirectChat(targetEmail: String, targetName: String): String? {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return null
        val myEmail = currentUser.email ?: return null
        
        val safeMy = myEmail.replace(".", "_").replace("@", "_at_")
        val safeTarget = targetEmail.replace(".", "_").replace("@", "_at_")
        
        val directId = if (safeMy < safeTarget) "direct_${safeMy}_$safeTarget" else "direct_${safeTarget}_$safeMy"
        
        val channelInfo = ChannelInfo(
            id = directId,
            name = targetName,
            description = "Chat privado con $targetName",
            iconEmoji = "💬",
            isDirect = true,
            creatorEmail = myEmail,
            members = listOf(
                GroupMember(myEmail, currentUser.displayName ?: myEmail),
                GroupMember(targetEmail, targetName)
            )
        )
        
        saveOrUpdateChannel(channelInfo)
        return directId
    }

    private fun snapshotToDocumentItem(snap: DataSnapshot): DocumentItem? {
        val map = snap.value as? Map<*, *> ?: return null
        val title = (map["title"] as? String) ?: "Sin título"
        val content = (map["content"] as? String) ?: ""
        val typeStr = (map["docType"] as? String) ?: "DOC"
        val formatStr = (map["currentFormat"] as? String) ?: "TXT"
        val authorEmail = (map["authorEmail"] as? String) ?: ""
        val firestoreId = (map["firestoreId"] as? String) ?: snap.key ?: ""
        val lastModified = (map["lastModified"] as? Number)?.toLong() ?: System.currentTimeMillis()
        
        return DocumentItem(
            id = (map["localId"] as? Number)?.toLong() ?: 0L,
            firestoreId = firestoreId,
            title = title,
            content = content,
            docType = try { DocumentType.valueOf(typeStr) } catch (_: Exception) { DocumentType.DOC },
            currentFormat = try { DocumentFormat.valueOf(formatStr) } catch (_: Exception) { DocumentFormat.TXT },
            authorEmail = authorEmail,
            lastModified = lastModified,
            isSyncedCloud = true
        )
    }

    private fun docToDocumentItem(doc: DocumentSnapshot): DocumentItem? {
        val title = doc.getString("title") ?: "Sin título"
        val content = doc.getString("content") ?: ""
        val typeStr = doc.getString("docType") ?: "DOC"
        val formatStr = doc.getString("currentFormat") ?: "TXT"
        val authorEmail = doc.getString("authorEmail") ?: ""
        
        return DocumentItem(
            id = doc.getLong("localId") ?: 0L,
            firestoreId = doc.id,
            title = title,
            content = content,
            docType = try { DocumentType.valueOf(typeStr) } catch (_: Exception) { DocumentType.DOC },
            currentFormat = try { DocumentFormat.valueOf(formatStr) } catch (_: Exception) { DocumentFormat.TXT },
            authorEmail = authorEmail,
            lastModified = doc.getLong("lastModified") ?: System.currentTimeMillis(),
            isSyncedCloud = true
        )
    }

    /**
     * Escucha en tiempo real todos los documentos públicos o compartidos.
     */
    fun listenToAllDocuments(): Flow<List<DocumentItem>> = callbackFlow {
        val rtdb = rtdbRef
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
        
        val docsRef = if (uid != null) rtdb?.child("documents")?.child(uid) else null
        
        val rtdbListener = docsRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { snapshotToDocumentItem(it) }
                trySendBlocking(list)
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        val db = getDb()
        val fsReg = if (db != null && docsRef == null) {
            db.collection("cloud_documents").addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { docToDocumentItem(it) }
                    trySendBlocking(list)
                }
            }
        } else null

        awaitClose {
            if (docsRef != null && rtdbListener != null) docsRef.removeEventListener(rtdbListener)
            fsReg?.remove()
        }
    }

    /**
     * Escucha en tiempo real los proyectos de audio del usuario actual.
     */
    fun listenToUserAudioProjects(): Flow<List<AudioProject>> = callbackFlow {
        val rtdb = rtdbRef
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
        
        val audioRef = if (uid != null) rtdb?.child("audio_projects")?.child(uid) else null
        
        val rtdbListener = audioRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { snapshotToAudioProject(it) }
                trySendBlocking(list)
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        awaitClose {
            if (audioRef != null && rtdbListener != null) audioRef.removeEventListener(rtdbListener)
        }
    }

    private fun snapshotToAudioProject(snap: DataSnapshot): AudioProject? {
        val map = snap.value as? Map<*, *> ?: return null
        val title = (map["title"] as? String) ?: "Sin título"
        val authorEmail = (map["authorEmail"] as? String) ?: ""
        val firestoreId = (map["firestoreId"] as? String) ?: snap.key ?: ""
        
        return AudioProject(
            id = (map["localId"] as? Number)?.toLong() ?: 0L,
            firestoreId = firestoreId,
            title = title,
            description = (map["description"] as? String) ?: "",
            genre = (map["genre"] as? String) ?: "General",
            bpm = (map["bpm"] as? Number)?.toInt() ?: 120,
            patternDataJson = (map["patternDataJson"] as? String) ?: "{}",
            authorEmail = authorEmail,
            authorName = (map["authorName"] as? String) ?: "Usuario",
            isPublic = (map["isPublic"] as? Boolean) ?: true,
            aiPrompt = (map["aiPrompt"] as? String) ?: "",
            notesMelody = (map["notesMelody"] as? String) ?: "",
            durationSeconds = (map["durationSeconds"] as? Number)?.toInt() ?: 30,
            lastModified = (map["lastModified"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            isSyncedCloud = true
        )
    }

    /**
     * Escucha en tiempo real todos los proyectos de audio públicos.
     * Nota: Con las nuevas reglas de RTDB, la lectura global de 'audio_projects' está restringida.
     * Por ahora, se prioriza Firestore para proyectos públicos si RTDB falla o está restringido.
     */
    fun listenToPublicAudioProjects(): Flow<List<AudioProject>> = callbackFlow {
        val rtdb = rtdbRef
        val db = getDb()
        
        // El acceso directo a 'audio_projects' fallará en RTDB por reglas de seguridad (solo lectura por UID)
        // Por lo tanto, para proyectos "Públicos" de otros usuarios, debemos usar Firestore.
        
        val fsReg = if (db != null) {
            db.collection("cloud_audio_projects")
                .whereEqualTo("isPublic", true)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Error escuchando proyectos públicos en Firestore: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                val map = doc.data ?: return@mapNotNull null
                                AudioProject(
                                    id = (map["localId"] as? Long) ?: 0L,
                                    firestoreId = doc.id,
                                    title = (map["title"] as? String) ?: "Sin título",
                                    description = (map["description"] as? String) ?: "",
                                    genre = (map["genre"] as? String) ?: "General",
                                    bpm = (map["bpm"] as? Long)?.toInt() ?: 120,
                                    patternDataJson = (map["patternDataJson"] as? String) ?: "[]",
                                    authorEmail = (map["authorEmail"] as? String) ?: "",
                                    authorName = (map["authorName"] as? String) ?: "Anónimo",
                                    isPublic = true,
                                    lastModified = (map["lastModified"] as? Long) ?: System.currentTimeMillis()
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        trySendBlocking(list)
                    }
                }
        } else null

        awaitClose {
            fsReg?.remove()
        }
    }

    /**
     * Obtiene todos los mensajes de un canal de una sola vez.
     */
    suspend fun getChannelMessagesOnce(channelId: String): List<ChatMessage> {
        val rtdb = rtdbRef
        if (rtdb != null) {
            try {
                val snap = rtdb.child("chats").child(channelId).child("messages").get().await()
                val messages = snap.children.mapNotNull { child ->
                    snapshotToChatMessage(child, channelId)
                }.sortedBy { it.timestamp }
                if (messages.isNotEmpty()) return messages
            } catch (e: Exception) {
                Log.w(TAG, "Error obteniendo mensajes de RTDB: ${e.message}")
            }
        }
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

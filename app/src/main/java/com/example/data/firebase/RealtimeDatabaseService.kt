package com.example.data.firebase

import com.example.data.firebase.ChannelInfo
import com.example.data.firebase.GroupMember
import com.example.data.firebase.PresenceUser
import com.example.data.model.ChatMessage
import com.example.data.model.UserAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow

class RealtimeDatabaseService {

    private val auth = FirebaseAuth.getInstance()
    private val rtdbInstance: FirebaseDatabase = FirebaseDatabase.getInstance("https://omnistudio-caaf5-default-rtdb.firebaseio.com")
    private val database = rtdbInstance.reference

    // Estado de conexión reactivo
    val connectionStatus = MutableStateFlow(false)

    init {
        val connectedRef = rtdbInstance.getReference(".info/connected")
        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                connectionStatus.value = snapshot.getValue(Boolean::class.java) ?: false
            }
            override fun onCancelled(error: DatabaseError) {
                connectionStatus.value = false
            }
        })
    }

    // --- ESCUCHAS (LISTENERS) EN TIEMPO REAL CON addValueEventListener ---

    /**
     * Observa en tiempo real todos los chats y conversaciones del nodo 'chats'.
     */
    fun listenToChats(): Flow<List<ChannelInfo>> = callbackFlow {
        val chatsRef = database.child("chats")
        
        val listener = object : ValueEventListener {
            @Suppress("UNCHECKED_CAST")
            override fun onDataChange(snapshot: DataSnapshot) {
                val chats = snapshot.children.mapNotNull { child ->
                    val map = child.value as? Map<String, Any> ?: return@mapNotNull null
                    mapToChannelInfo(child.key ?: "", map)
                }
                trySendBlocking(chats)
            }

            override fun onCancelled(error: DatabaseError) {
                trySendBlocking(emptyList())
            }
        }
        
        chatsRef.addValueEventListener(listener)
        awaitClose { chatsRef.removeEventListener(listener) }
    }

    private fun mapToChannelInfo(id: String, map: Map<String, Any>): ChannelInfo {
        val membersList = (map["members"] as? List<Map<String, Any>>)?.map { m ->
            GroupMember(
                email = (m["email"] as? String) ?: "",
                name = (m["name"] as? String) ?: "",
                role = (m["role"] as? String) ?: "member",
                canSendMessages = (m["canSendMessages"] as? Boolean) ?: true,
                canSendMedia = (m["canSendMedia"] as? Boolean) ?: true,
                canInviteMembers = (m["canInviteMembers"] as? Boolean) ?: true,
                avatarUrl = (m["avatarUrl"] as? String) ?: ""
            )
        } ?: emptyList()

        return ChannelInfo(
            id = id,
            name = (map["name"] as? String) ?: "Chat",
            description = (map["description"] as? String) ?: "",
            iconEmoji = (map["iconEmoji"] as? String) ?: "💬",
            isDirect = (map["isDirect"] as? Boolean) ?: false,
            isGroup = (map["isGroup"] as? Boolean) ?: false,
            groupPhotoUrl = (map["groupPhotoUrl"] as? String) ?: "",
            creatorEmail = (map["creatorEmail"] as? String) ?: "",
            creatorName = (map["creatorName"] as? String) ?: "",
            members = membersList,
            pendingDeletionTimestamp = (map["pendingDeletionTimestamp"] as? Number)?.toLong(),
            isDeleting = (map["isDeleting"] as? Boolean) ?: false
        )
    }

    /**
     * Observa en tiempo real los mensajes de un chat específico dentro de 'chats/{chatId}/messages'.
     */
    fun listenToMessages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val messagesRef = database.child("chats").child(chatId).child("messages")
        
        val listener = object : ValueEventListener {
            @Suppress("UNCHECKED_CAST")
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children.mapNotNull { child ->
                    val map = child.value as? Map<String, Any> ?: return@mapNotNull null
                    mapToChatMessage(child.key ?: "", chatId, map)
                }.sortedBy { it.timestamp }
                trySendBlocking(messages)
            }

            override fun onCancelled(error: DatabaseError) {
                trySendBlocking(emptyList())
            }
        }
        
        messagesRef.addValueEventListener(listener)
        awaitClose { messagesRef.removeEventListener(listener) }
    }

    private fun mapToChatMessage(id: String, channelId: String, map: Map<String, Any>): ChatMessage {
        val timestamp = (map["timestamp"] as? Number)?.toLong() ?: (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        return ChatMessage(
            id = id.hashCode().toLong(),
            firestoreId = id,
            channelId = channelId,
            senderName = (map["senderName"] as? String) ?: "Usuario",
            senderEmail = (map["senderEmail"] as? String) ?: "usuario@omnistudio.io",
            text = (map["text"] as? String) ?: "",
            timestamp = timestamp,
            attachedDocId = (map["attachedDocId"] as? Number)?.toLong(),
            attachedDocTitle = map["attachedDocTitle"] as? String,
            attachedAudioId = (map["attachedAudioId"] as? Number)?.toLong(),
            attachedAudioTitle = map["attachedAudioTitle"] as? String,
            mediaType = (map["mediaType"] as? String) ?: "",
            mediaUrl = map["mediaUrl"] as? String,
            mediaThumbnail = map["mediaThumbnail"] as? String,
            callDurationSec = (map["callDurationSec"] as? Number)?.toInt() ?: 0,
            reactions = (map["reactions"] as? String) ?: "",
            isSyncedFirestore = true,
            deliveryStatus = (map["deliveryStatus"] as? String) ?: "enviado",
            sentTimestamp = (map["sentTimestamp"] as? Number)?.toLong() ?: timestamp,
            deliveredTimestamp = (map["deliveredTimestamp"] as? Number)?.toLong() ?: 0L,
            seenTimestamp = (map["seenTimestamp"] as? Number)?.toLong() ?: 0L,
            seenBy = (map["seenBy"] as? String) ?: ""
        )
    }

    // Alias para compatibilidad previa
    fun listenToChannelMessages(channelId: String): Flow<List<ChatMessage>> = listenToMessages(channelId)

    // Alias para canales personalizados
    fun listenToCustomChannels(): Flow<List<ChannelInfo>> = listenToChats()

    /**
     * Observa en tiempo real los documentos del usuario en 'documents/{uid}'.
     */
    fun listenToUserDocuments(uid: String? = null): Flow<List<Map<String, Any>>> = callbackFlow {
        val targetUid = uid ?: auth.currentUser?.uid
        if (targetUid == null) {
            trySendBlocking(emptyList())
            channel.close()
            return@callbackFlow
        }
        
        val docsRef = database.child("documents").child(targetUid)
        val listener = object : ValueEventListener {
            @Suppress("UNCHECKED_CAST")
            override fun onDataChange(snapshot: DataSnapshot) {
                val docs = snapshot.children.mapNotNull { child ->
                    child.value as? Map<String, Any>
                }
                trySendBlocking(docs)
            }

            override fun onCancelled(error: DatabaseError) {
                trySendBlocking(emptyList())
            }
        }
        
        docsRef.addValueEventListener(listener)
        awaitClose { docsRef.removeEventListener(listener) }
    }

    /**
     * Observa en tiempo real un documento específico en 'documents/{uid}/{documentId}'.
     */
    fun listenToDocument(documentId: String, uid: String? = null): Flow<Map<String, Any>?> = callbackFlow {
        val targetUid = uid ?: auth.currentUser?.uid
        if (targetUid == null) {
            trySendBlocking(null)
            channel.close()
            return@callbackFlow
        }
        
        val docRef = database.child("documents").child(targetUid).child(documentId)
        val listener = object : ValueEventListener {
            @Suppress("UNCHECKED_CAST")
            override fun onDataChange(snapshot: DataSnapshot) {
                val doc = snapshot.value as? Map<String, Any>
                trySendBlocking(doc)
            }

            override fun onCancelled(error: DatabaseError) {
                trySendBlocking(null)
            }
        }
        
        docRef.addValueEventListener(listener)
        awaitClose { docRef.removeEventListener(listener) }
    }

    /**
     * Observa en tiempo real todos los documentos de todos los usuarios en el nodo global 'documents'.
     */
    fun listenToAllDocuments(): Flow<List<Map<String, Any>>> = callbackFlow {
        val docsRef = database.child("documents")
        val listener = object : ValueEventListener {
            @Suppress("UNCHECKED_CAST")
            override fun onDataChange(snapshot: DataSnapshot) {
                val allDocs = mutableListOf<Map<String, Any>>()
                for (userFolder in snapshot.children) {
                    for (docSnap in userFolder.children) {
                        val doc = docSnap.value as? Map<String, Any>
                        if (doc != null) {
                            allDocs.add(doc)
                        }
                    }
                }
                trySendBlocking(allDocs)
            }

            override fun onCancelled(error: DatabaseError) {
                trySendBlocking(emptyList())
            }
        }
        
        docsRef.addValueEventListener(listener)
        awaitClose { docsRef.removeEventListener(listener) }
    }

    // --- ESCRITURAS ---
    suspend fun saveUser(
        firstName: String,
        lastName: String,
        email: String
    ) {
        val uid = auth.currentUser?.uid
            ?: error("Usuario no autenticado")

        val user = mapOf(
            "uid" to uid,
            "firstName" to firstName,
            "lastName" to lastName,
            "email" to email,
            "updatedAt" to System.currentTimeMillis()
        )

        database
            .child("users")
            .child(uid)
            .setValue(user)
            .await()
    }

    suspend fun sendMessage(message: ChatMessage): String {
        val user = auth.currentUser
            ?: error("Usuario no autenticado")

        val docId = if (message.firestoreId.isNotBlank()) message.firestoreId else database.child("chats").child(message.channelId).child("messages").push().key ?: "msg_${System.currentTimeMillis()}"

        val messageData = mapOf(
            "messageId" to docId,
            "senderId" to user.uid,
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

        database.child("chats").child(message.channelId).child("messages").child(docId).setValue(messageData).await()

        // Actualizar metadatos del canal
        val channelMeta = mapOf(
            "lastMessageText" to message.text,
            "lastMessageTimestamp" to message.timestamp,
            "lastMessageSender" to message.senderName,
            "lastUpdated" to System.currentTimeMillis()
        )
        database.child("chats").child(message.channelId).updateChildren(channelMeta).await()

        return docId
    }

    suspend fun saveDocument(
        documentId: String,
        title: String,
        content: String
    ) {
        val uid = auth.currentUser?.uid
            ?: error("Usuario no autenticado")

        val document = mapOf(
            "documentId" to documentId,
            "ownerUid" to uid,
            "title" to title,
            "content" to content,
            "updatedAt" to System.currentTimeMillis()
        )

        database
            .child("documents")
            .child(uid)
            .child(documentId)
            .setValue(document)
            .await()
    }

    suspend fun deleteMessage(
        chatId: String,
        messageId: String
    ) {
        auth.currentUser
            ?: error("Usuario no autenticado")

        database
            .child("chats")
            .child(chatId)
            .child("messages")
            .child(messageId)
            .removeValue()
            .await()
    }

    suspend fun addReaction(chatId: String, messageId: String, emoji: String, currentReactions: String) {
        val updatedReactions = if (currentReactions.isBlank()) emoji else "$currentReactions,$emoji"
        database.child("chats").child(chatId).child("messages").child(messageId).child("reactions").setValue(updatedReactions).await()
    }

    // --- CHAT PRIVADO (DIRECTO) ---
    suspend fun createOrGetDirectChat(targetEmail: String, targetName: String): String {
        val user = auth.currentUser ?: error("Usuario no autenticado")
        val myEmail = user.email ?: ""
        val safeMy = myEmail.replace(".", "_")
        val safeTarget = targetEmail.replace(".", "_")
        val directChatId = if (safeMy < safeTarget) "direct_${safeMy}_$safeTarget" else "direct_${safeTarget}_$safeMy"

        val chatData = mapOf(
            "id" to directChatId,
            "name" to targetName,
            "isDirect" to true,
            "creatorEmail" to myEmail,
            "members" to listOf(
                mapOf("email" to myEmail, "name" to (user.displayName ?: myEmail), "role" to "member"),
                mapOf("email" to targetEmail, "name" to targetName, "role" to "member")
            ),
            "lastUpdated" to System.currentTimeMillis()
        )

        database.child("chats").child(directChatId).updateChildren(chatData).await()
        return directChatId
    }

    suspend fun saveOrUpdateChannel(channel: ChannelInfo) {
        val membersList = channel.members.map { m ->
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

        val chatData = mutableMapOf(
            "id" to channel.id,
            "name" to channel.name,
            "description" to channel.description,
            "iconEmoji" to channel.iconEmoji,
            "isDirect" to channel.isDirect,
            "isGroup" to channel.isGroup,
            "groupPhotoUrl" to channel.groupPhotoUrl,
            "creatorEmail" to channel.creatorEmail,
            "creatorName" to channel.creatorName,
            "members" to membersList,
            "isDeleting" to channel.isDeleting,
            "lastUpdated" to System.currentTimeMillis()
        )
        if (channel.pendingDeletionTimestamp != null) {
            chatData["pendingDeletionTimestamp"] = channel.pendingDeletionTimestamp
        }

        database.child("chats").child(channel.id).updateChildren(chatData as Map<String, Any>).await()
    }

    suspend fun deleteChannel(channelId: String) {
        database.child("chats").child(channelId).removeValue().await()
    }

    // --- INDICADORES DE ESCRITURA Y PRESENCIA ---
    suspend fun setTyping(chatId: String, isTyping: Boolean) {
        val user = auth.currentUser ?: return
        val safeEmail = (user.email ?: user.uid).replace(".", "_")
        if (isTyping) {
            database.child("typing").child(chatId).child(safeEmail).setValue(user.displayName ?: user.email).await()
        } else {
            database.child("typing").child(chatId).child(safeEmail).removeValue().await()
        }
    }

    fun listenToTyping(chatId: String): Flow<List<String>> = callbackFlow {
        val ref = database.child("typing").child(chatId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val typers = snapshot.children.mapNotNull { it.value as? String }
                trySendBlocking(typers)
            }
            override fun onCancelled(error: DatabaseError) {
                trySendBlocking(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun updatePresence(chatId: String) {
        val user = auth.currentUser ?: return
        val safeEmail = (user.email ?: user.uid).replace(".", "_")
        val presenceData = mapOf(
            "email" to (user.email ?: ""),
            "name" to (user.displayName ?: user.email ?: "Usuario"),
            "lastActive" to System.currentTimeMillis()
        )
        database.child("presence").child(chatId).child(safeEmail).setValue(presenceData).await()
    }

    fun listenToPresence(chatId: String): Flow<List<PresenceUser>> = callbackFlow {
        val ref = database.child("presence").child(chatId)
        val listener = object : ValueEventListener {
            @Suppress("UNCHECKED_CAST")
            override fun onDataChange(snapshot: DataSnapshot) {
                val now = System.currentTimeMillis()
                val users = snapshot.children.mapNotNull { child ->
                    val map = child.value as? Map<String, Any> ?: return@mapNotNull null
                    val email = (map["email"] as? String) ?: return@mapNotNull null
                    val name = (map["name"] as? String) ?: email
                    val lastActive = (map["lastActive"] as? Number)?.toLong() ?: 0L
                    if (now - lastActive < 120000) {
                        PresenceUser(email, name, lastActive)
                    } else null
                }
                trySendBlocking(users)
            }
            override fun onCancelled(error: DatabaseError) {
                trySendBlocking(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Observa en tiempo real todos los perfiles de usuario en 'users'.
     */
    fun listenToAllUsers(): Flow<List<UserAccount>> = callbackFlow {
        val usersRef = database.child("users")
        val listener = object : ValueEventListener {
            @Suppress("UNCHECKED_CAST")
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = snapshot.children.mapNotNull { child ->
                    val map = child.value as? Map<String, Any> ?: return@mapNotNull null
                    val email = (map["email"] as? String) ?: ""
                    if (email.isBlank()) return@mapNotNull null
                    
                    UserAccount(
                        email = email,
                        uid = (map["uid"] as? String) ?: child.key ?: "",
                        username = (map["username"] as? String) ?: email.substringBefore("@"),
                        displayName = (map["displayName"] as? String) ?: (map["name"] as? String) ?: email.substringBefore("@"),
                        passwordHash = (map["passwordHash"] as? String) ?: "dummy_hash",
                        isGoogleAccount = (map["isGoogleAccount"] as? Boolean) ?: true,
                        avatarUrl = (map["avatarUrl"] as? String) ?: "",
                        cloudStorageUsedMb = (map["cloudStorageUsedMb"] as? Number)?.toInt() ?: 1420,
                        cloudStorageTotalMb = (map["cloudStorageTotalMb"] as? Number)?.toInt() ?: 15360,
                        lastLoginTimestamp = (map["lastLoginTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    )
                }
                trySendBlocking(users)
            }
            override fun onCancelled(error: DatabaseError) {
                trySendBlocking(emptyList())
            }
        }
        usersRef.addValueEventListener(listener)
        awaitClose { usersRef.removeEventListener(listener) }
    }
}

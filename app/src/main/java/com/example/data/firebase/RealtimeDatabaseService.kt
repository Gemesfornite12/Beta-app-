package com.example.data.firebase

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
    private val rtdbInstance = try {
        FirebaseDatabase.getInstance("https://omnistudio-caaf5-default-rtdb.firebaseio.com")
    } catch (_: Exception) {
        FirebaseDatabase.getInstance()
    }
    private val database = rtdbInstance.reference

    // Estado de conexión reactivo
    val connectionStatus = MutableStateFlow(true)

    init {
        try {
            val connectedRef = rtdbInstance.getReference(".info/connected")
            connectedRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val isConnected = snapshot.getValue(Boolean::class.java) ?: false
                    connectionStatus.value = isConnected
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        } catch (_: Exception) {}
    }

    // --- ESCUCHAS (LISTENERS) EN TIEMPO REAL CON addValueEventListener ---

    /**
     * Observa en tiempo real todos los chats y conversaciones del nodo 'chats'.
     */
    fun listenToChats(): Flow<List<Map<String, Any>>> = callbackFlow {
        val chatsRef = database.child("chats")
        
        val listener = object : ValueEventListener {
            @Suppress("UNCHECKED_CAST")
            override fun onDataChange(snapshot: DataSnapshot) {
                val chats = snapshot.children.mapNotNull { child ->
                    val map = child.value as? Map<String, Any>
                    if (map != null) {
                        map + ("id" to (child.key ?: ""))
                    } else null
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

    /**
     * Observa en tiempo real los mensajes de un chat específico dentro de 'chats/{chatId}/messages'.
     */
    fun listenToMessages(chatId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val messagesRef = database.child("chats").child(chatId).child("messages")
        
        val listener = object : ValueEventListener {
            @Suppress("UNCHECKED_CAST")
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children.mapNotNull { child ->
                    child.value as? Map<String, Any>
                }
                trySendBlocking(messages)
            }

            override fun onCancelled(error: DatabaseError) {
                trySendBlocking(emptyList())
            }
        }
        
        messagesRef.addValueEventListener(listener)
        awaitClose { messagesRef.removeEventListener(listener) }
    }

    // Alias para compatibilidad previa
    fun listenToChannelMessages(channelId: String): Flow<List<Map<String, Any>>> = listenToMessages(channelId)

    // Alias para canales personalizados
    fun listenToCustomChannels(): Flow<List<Map<String, Any>>> = listenToChats()

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

    suspend fun sendMessage(
        chatId: String,
        text: String,
        mediaUrl: String? = null,
        mediaType: String? = null
    ): String {
        val user = auth.currentUser
            ?: error("Usuario no autenticado")

        val messageRef = database
            .child("chats")
            .child(chatId)
            .child("messages")
            .push()

        val messageId = messageRef.key
            ?: error("No se pudo crear el ID del mensaje")

        val message = mapOf(
            "messageId" to messageId,
            "senderId" to user.uid,
            "senderEmail" to (user.email ?: ""),
            "text" to text,
            "mediaUrl" to mediaUrl,
            "mediaType" to mediaType,
            "createdAt" to System.currentTimeMillis()
        )

        messageRef
            .setValue(message)
            .await()

        return messageId
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

    fun listenToPresence(chatId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val ref = database.child("presence").child(chatId)
        val listener = object : ValueEventListener {
            @Suppress("UNCHECKED_CAST")
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = snapshot.children.mapNotNull { it.value as? Map<String, Any> }
                trySendBlocking(users)
            }
            override fun onCancelled(error: DatabaseError) {
                trySendBlocking(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}

package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserAccount(
    @PrimaryKey val email: String,
    val username: String,
    val displayName: String,
    val passwordHash: String,
    val isGoogleAccount: Boolean = false,
    val avatarUrl: String = "",
    val cloudStorageUsedMb: Int = 1420,
    val cloudStorageTotalMb: Int = 15360, // 15 GB
    val lastLoginTimestamp: Long = System.currentTimeMillis()
)

enum class DocumentType {
    DOC, SLIDE, SHEET, TXT, PDF, RESUME
}

enum class DocumentFormat(val extension: String, val displayName: String, val mimeType: String) {
    DOCX("docx", "Documento Word (.docx)", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PDF("pdf", "Documento PDF (.pdf)", "application/pdf"),
    TXT("txt", "Texto Plano (.txt)", "text/plain"),
    MARKDOWN("md", "Markdown (.md)", "text/markdown"),
    HTML("html", "Página Web (.html)", "text/html"),
    PPTX("pptx", "Presentación (.pptx)", "application/vnd.openxmlformats-officedocument.presentationml.presentation")
}

@Entity(tableName = "documents")
data class DocumentItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firestoreId: String = "",
    val title: String,
    val content: String,
    val docType: DocumentType = DocumentType.DOC,
    val currentFormat: DocumentFormat = DocumentFormat.DOCX,
    val slideCount: Int = 1,
    val slidesJson: String = "[]", // Serialized slide data for presentations
    val authorEmail: String,
    val lastModified: Long = System.currentTimeMillis(),
    val isSyncedCloud: Boolean = true,
    val lastSyncedFirestore: Long = 0L,
    val fileSizeKb: Int = 45,
    val isFavorite: Boolean = false
)

@Entity(tableName = "audio_projects")
data class AudioProject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firestoreId: String = "",
    val title: String,
    val description: String = "",
    val genre: String = "Lo-Fi Hip Hop",
    val bpm: Int = 110,
    val patternDataJson: String, // 16-step grid per track
    val authorEmail: String,
    val authorName: String = "Alex González",
    val isPublic: Boolean = false,
    val aiPrompt: String = "",
    val notesMelody: String = "",
    val durationSeconds: Int = 16,
    val lastModified: Long = System.currentTimeMillis(),
    val isSyncedCloud: Boolean = true,
    val lastSyncedFirestore: Long = 0L,
    val fileSizeKb: Int = 128
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firestoreId: String = "",
    val channelId: String = "general", // "general", "musica-colab", "revision-docs", or direct email
    val senderName: String,
    val senderEmail: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachedDocId: Long? = null,
    val attachedDocTitle: String? = null,
    val attachedAudioId: Long? = null,
    val attachedAudioTitle: String? = null,
    val mediaType: String = "", // "", "image", "video", "gif", "call_voice", "call_video"
    val mediaUrl: String? = null,
    val mediaThumbnail: String? = null,
    val callDurationSec: Int = 0,
    val reactions: String = "", // e.g. "🔥,👍"
    val isSyncedFirestore: Boolean = true,
    val deliveryStatus: String = "enviado", // "enviando", "enviado", "entregado", "visto"
    val sentTimestamp: Long = 0L,
    val deliveredTimestamp: Long = 0L,
    val seenTimestamp: Long = 0L,
    val seenBy: String = "" // e.g. "carlos.m@cloud.io, sofia.m@cloud.io"
)

enum class CallStatus {
    RINGING,
    CONNECTED,
    ENDED
}

data class CallSession(
    val callId: String,
    val channelId: String,
    val peerName: String,
    val peerEmail: String,
    val isVideo: Boolean,
    val status: CallStatus = CallStatus.RINGING,
    val durationSeconds: Int = 0,
    val isMuted: Boolean = false,
    val isCameraOn: Boolean = true,
    val isSpeakerOn: Boolean = true,
    val isFrontCamera: Boolean = true,
    val isIncoming: Boolean = false,
    val callerName: String = "",
    val callerEmail: String = "",
    val groupName: String? = null,
    val ringSecondsLeft: Int = 300,
    val maxRingSeconds: Int = 300,
    val isTimedOut: Boolean = false
)

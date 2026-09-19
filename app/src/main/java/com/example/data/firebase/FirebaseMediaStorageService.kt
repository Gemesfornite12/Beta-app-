package com.example.data.firebase

import android.content.Context
import android.net.Uri
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Sube fotos, videos, audios, GIFs, stickers y archivos a Firebase Storage.
 * Firestore conserva únicamente los metadatos y los enlaces.
 *
 * Requiere que FirebaseApp ya esté inicializado y la dependencia
 * com.google.firebase:firebase-storage esté agregada al módulo app.
 */
data class UploadedMedia(
    val mediaId: String,
    val storagePath: String,
    val downloadUrl: String,
    val mediaType: String,
    val mimeType: String,
    val sizeBytes: Long?
)

class FirebaseMediaStorageService(context: Context) {
    private val app = FirebaseAppProvider.get(context)
    private val storage = FirebaseStorage.getInstance(app)
    private val firestore = FirebaseFirestore.getInstance(app)

    /**
     * mediaType: image, video, audio, gif, sticker o document.
     */
    suspend fun uploadMedia(
        ownerUid: String,
        localUri: Uri,
        mediaType: String,
        mimeType: String,
        originalName: String? = null
    ): UploadedMedia {
        require(ownerUid.isNotBlank()) { "ownerUid no puede estar vacío" }
        require(mediaType in setOf("image", "video", "audio", "gif", "sticker", "document")) {
            "Tipo multimedia no válido"
        }

        val mediaId = UUID.randomUUID().toString()
        val safeName = (originalName ?: localUri.lastPathSegment ?: "archivo")
            .substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(100)
            .ifBlank { "archivo" }
        val storagePath = "users/$ownerUid/media/$mediaId/$safeName"
        val storageRef = storage.reference.child(storagePath)

        val metadata = StorageMetadata.Builder()
            .setContentType(mimeType)
            .setCustomMetadata("ownerUid", ownerUid)
            .setCustomMetadata("mediaType", mediaType)
            .build()

        val uploadSnapshot = storageRef.putFile(localUri, metadata).await()
        val downloadUrl = storageRef.downloadUrl.await().toString()

        val mediaData = hashMapOf<String, Any?>(
            "mediaId" to mediaId,
            "ownerUid" to ownerUid,
            "mediaType" to mediaType,
            "mimeType" to mimeType,
            "fileName" to safeName,
            "storagePath" to storagePath,
            "downloadUrl" to downloadUrl,
            "sizeBytes" to uploadSnapshot.totalByteCount,
            "createdAt" to FieldValue.serverTimestamp()
        )

        firestore.collection("media")
            .document(mediaId)
            .set(mediaData)
            .await()

        return UploadedMedia(
            mediaId = mediaId,
            storagePath = storagePath,
            downloadUrl = downloadUrl,
            mediaType = mediaType,
            mimeType = mimeType,
            sizeBytes = uploadSnapshot.totalByteCount
        )
    }

    suspend fun deleteMedia(ownerUid: String, mediaId: String, storagePath: String) {
        require(ownerUid.isNotBlank()) { "ownerUid no puede estar vacío" }
        storage.reference.child(storagePath).delete().await()
        firestore.collection("media").document(mediaId).delete().await()
    }
}

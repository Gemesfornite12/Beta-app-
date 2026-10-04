package com.example.data.social

import android.content.Context
import android.util.Log
import com.example.data.firebase.FirebaseAppProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.example.data.supabase.SupabaseSocialPushService
import com.example.data.firebase.FirebaseAnalyticsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

/** Social network data kept in an isolated Realtime Database namespace for the .test build. */
data class SocialProfile(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val bio: String = "",
    val visibility: String = "private",
    val createdAt: Long = 0L
) {
    val isPrivate: Boolean get() = visibility != "public"
}

data class SocialPostMedia(
    val mediaPath: String = "",
    val mediaType: String = "image",
    val mimeType: String = ""
)

data class SocialPost(
    val id: String = "",
    val ownerUid: String = "",
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val caption: String = "",
    val createdAt: Long = 0L,
    val topics: List<String> = emptyList(),
    val mediaPath: String = "",
    val mediaType: String = "text",
    val mediaUrl: String = "",
    val mediaItems: List<SocialPostMedia> = emptyList()
) {
    val containsVideo: Boolean get() = mediaType == "video" || mediaItems.any { it.mediaType == "video" }
}

/** Keeps Social uploads aligned with the existing private bucket's server allowlist. */
internal fun supportedSocialMediaType(mimeType: String): String? = when (mimeType.substringBefore(';').trim().lowercase()) {
    "image/jpeg", "image/png", "image/webp", "image/gif", "image/heic", "image/heif" -> "image"
    "video/mp4", "video/quicktime", "video/webm", "video/3gpp", "video/x-m4v" -> "video"
    else -> null
}

data class SocialMediaUpload(val uri: android.net.Uri, val mimeType: String)

internal object SocialInteractionPaths {
    fun comments(ownerUid: String, postId: String): String = "comments/$ownerUid/$postId"
    fun savedVideo(uid: String, ownerUid: String, postId: String): String =
        "savedPosts/$uid/$ownerUid/$postId"
}

data class SocialComment(
    val id: String = "",
    val authorUid: String = "",
    val authorUsername: String = "",
    val authorDisplayName: String = "",
    val text: String = "",
    val createdAt: Long = 0L
) {
    companion object {
        fun isValidBody(value: String): Boolean = value.trim().isNotEmpty() && value.trim().length <= 1000
    }
}

data class SocialStory(
    val id: String = "",
    val ownerUid: String = "",
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val mediaPath: String = "",
    val mediaType: String = "image",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val mediaUrl: String = ""
)

data class SocialFollowRequest(
    val uid: String = "",
    val username: String = "",
    val displayName: String = ""
)

class SocialRepository(context: Context) {
    private val appContext = context.applicationContext
    private val app = FirebaseAppProvider.get(appContext)
    private val auth = FirebaseAuth.getInstance(app)
    private val root = FirebaseDatabase.getInstance(app).reference.child("social_test")
    private val mediaService = SupabaseSocialMediaService(appContext)
    private val tag = "SocialRepository"
    private val pushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun requestSocialPush(action: suspend () -> Unit) {
        pushScope.launch {
            try {
                action()
            } catch (error: Exception) {
                Log.w(tag, "Social push request failed: ${error.message}")
            }
        }
    }

    private fun currentUid(): String = auth.currentUser?.uid
        ?: error("Inicia sesión para usar Social.")

    suspend fun ensureCurrentProfile(
        displayNameHint: String,
        emailHint: String,
        avatarUrlHint: String
    ): SocialProfile {
        val uid = currentUid()
        val ref = root.child("profiles").child(uid)
        val existing = ref.get().await().toSocialProfile()
        val profile = existing ?: run {
            val emailBase = emailHint.substringBefore('@').lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9_]"), "")
                .take(18)
                .ifBlank { "usuario" }
            val username = "${emailBase}_${uid.takeLast(4).lowercase(Locale.ROOT)}"
            SocialProfile(
                uid = uid,
                username = username,
                displayName = displayNameHint.ifBlank { emailBase },
                avatarUrl = avatarUrlHint,
                visibility = "private",
                createdAt = System.currentTimeMillis()
            )
        }
        val uniqueUsername = chooseAvailableUsername(profile.username, uid)
        val finalProfile = profile.copy(username = uniqueUsername)
        ref.setValue(finalProfile.toMap()).await()
        writeDirectoryEntry(finalProfile)
        if (!profile.username.equals(finalProfile.username, ignoreCase = true)) {
            root.child("directory").child(profile.username.lowercase(Locale.ROOT)).child(uid).removeValue().await()
        }
        return finalProfile
    }

    suspend fun getProfile(uid: String): SocialProfile? =
        root.child("profiles").child(uid).get().await().toSocialProfile()

    suspend fun loadProfilePosts(uid: String, maxPosts: Int = 60): List<SocialPost> {
        val snapshot = root.child("postsByUser").child(uid)
            .orderByChild("createdAt")
            .limitToLast(maxPosts)
            .get()
            .await()
        return snapshot.children.mapNotNull { it.toSocialPost() }.sortedByDescending { it.createdAt }
    }

    suspend fun followStatus(targetUid: String): String? {
        val uid = currentUid()
        return root.child("following").child(uid).child(targetUid).child("status")
            .get().await().getValue(String::class.java)
    }

    suspend fun updateProfile(
        current: SocialProfile,
        displayName: String,
        bio: String,
        visibility: String,
        username: String = current.username
    ): SocialProfile {
        val uid = currentUid()
        require(uid == current.uid) { "Solo puedes editar tu propio perfil." }
        val uniqueUsername = chooseAvailableUsername(username, uid)
        val updated = current.copy(
            username = uniqueUsername,
            displayName = displayName.trim().take(48).ifBlank { current.displayName },
            bio = bio.trim().take(160),
            visibility = if (visibility == "public") "public" else "private"
        )
        root.child("profiles").child(uid).setValue(updated.toMap()).await()
        writeDirectoryEntry(updated)
        if (!current.username.equals(updated.username, ignoreCase = true)) {
            root.child("directory").child(current.username.lowercase(Locale.ROOT)).child(uid).removeValue().await()
        }

        val posts = root.child("postsByUser").child(uid).get().await()
        for (post in posts.children) {
            val postId = post.key ?: continue
            val postFields = (post.value as? Map<*, *>)?.entries
                ?.mapNotNull { (key, value) -> (key as? String)?.let { it to value } }
                ?.toMap()?.toMutableMap() ?: continue
            postFields["username"] = updated.username
            postFields["displayName"] = updated.displayName
            postFields["avatarUrl"] = updated.avatarUrl
            root.child("postsByUser").child(uid).child(postId).setValue(postFields).await()
            val publicRef = root.child("publicFeed").child(publicFeedKey(uid, postId))
            if (updated.isPrivate) publicRef.removeValue().await() else publicRef.setValue(postFields).await()
        }
        val stories = root.child("storiesByUser").child(uid).get().await()
        for (story in stories.children) {
            val storyId = story.key ?: continue
            val storyFields = (story.value as? Map<*, *>)?.entries
                ?.mapNotNull { (key, value) -> (key as? String)?.let { it to value } }
                ?.toMap()?.toMutableMap() ?: continue
            storyFields["username"] = updated.username
            storyFields["displayName"] = updated.displayName
            storyFields["avatarUrl"] = updated.avatarUrl
            root.child("storiesByUser").child(uid).child(storyId).setValue(storyFields).await()
            val publicRef = root.child("publicStories").child(publicStoryKey(uid, storyId))
            if (updated.isPrivate) publicRef.removeValue().await() else publicRef.setValue(storyFields).await()
        }
        return updated
    }

    suspend fun searchProfiles(query: String): List<SocialProfile> {
        val normalized = query.trim().lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_]"), "")
        if (normalized.isBlank()) return emptyList()
        val snapshot = root.child("directory")
            .orderByKey()
            .startAt(normalized)
            .endAt("$normalized\uf8ff")
            .limitToFirst(25)
            .get()
            .await()
        return snapshot.children.flatMap { handleNode ->
            handleNode.children.mapNotNull { userNode ->
                val uid = userNode.key ?: return@mapNotNull null
                val values = userNode.value as? Map<*, *> ?: return@mapNotNull null
                SocialProfile(
                    uid = uid,
                    username = values["username"] as? String ?: handleNode.key.orEmpty(),
                    displayName = values["displayName"] as? String ?: "Usuario",
                    avatarUrl = values["avatarUrl"] as? String ?: "",
                    visibility = values["visibility"] as? String ?: "private"
                )
            }
        }
    }

    suspend fun loadFeed(followingOnly: Boolean, maxPosts: Int = 60): List<SocialPost> {
        val uid = currentUid()
        val postsByKey = linkedMapOf<String, SocialPost>()
        val preferenceWeights = readTopicWeights(uid)
        val followed = readAcceptedFollowing(uid)

        val publicAuthorUids = if (followingOnly) {
            emptyList()
        } else {
            runCatching {
                root.child("publicFeed")
                    .orderByChild("createdAt")
                    .limitToLast(maxPosts)
                    .get()
                    .await()
            }.getOrNull()?.children
                ?.mapNotNull { it.toSocialPost() }
                ?.also { posts -> posts.forEach { postsByKey["${it.ownerUid}:${it.id}"] = it } }

            val directory = root.child("directory").get().await()
            directory.children.flatMap { handleNode ->
                handleNode.children.mapNotNull { userNode ->
                    val isPublic = userNode.child("visibility").getValue(String::class.java) == "public"
                    val authorUid = userNode.child("uid").getValue(String::class.java) ?: userNode.key
                    if (isPublic) authorUid?.takeIf { it.isNotBlank() } else null
                }
            }.distinct()
        }

        val authorsToRead = if (followingOnly) followed else followed + uid + publicAuthorUids
        authorsToRead.distinct().forEach { authorUid ->
            val snapshot = root.child("postsByUser").child(authorUid)
                .orderByChild("createdAt")
                .limitToLast(30)
                .get()
                .await()
            snapshot.children.mapNotNull { it.toSocialPost() }
                .forEach { postsByKey["${it.ownerUid}:${it.id}"] = it }
        }

        val now = System.currentTimeMillis()
        return postsByKey.values.sortedWith(
            compareByDescending<SocialPost> { post ->
                val topicAffinity = post.topics.sumOf { preferenceWeights[it] ?: 0 }
                val followingBonus = if (post.ownerUid in followed) 250 else 0
                val ageHours = ((now - post.createdAt).coerceAtLeast(0L) / 3_600_000L).toInt()
                val recency = (120 - ageHours).coerceAtLeast(0)
                if (followingOnly) recency else topicAffinity * 100 + followingBonus + recency
            }.thenByDescending { it.createdAt }
        ).take(maxPosts)
    }

    suspend fun createTextPost(profile: SocialProfile, caption: String): SocialPost {
        val uid = currentUid()
        require(uid == profile.uid) { "El perfil actual no coincide con la sesión." }
        val cleanCaption = caption.trim().take(2200)
        require(cleanCaption.isNotBlank()) { "Escribe algo antes de publicar." }
        val postRef = root.child("postsByUser").child(uid).push()
        val postId = postRef.key ?: error("No se pudo crear el identificador de la publicación.")
        val post = SocialPost(
            id = postId,
            ownerUid = uid,
            username = profile.username,
            displayName = profile.displayName,
            avatarUrl = profile.avatarUrl,
            caption = cleanCaption,
            createdAt = System.currentTimeMillis(),
            topics = extractTopics(cleanCaption)
        )
        postRef.setValue(post.toMap()).await()
        if (!profile.isPrivate) {
            root.child("publicFeed").child(publicFeedKey(uid, postId)).setValue(post.toMap()).await()
        }
        FirebaseAnalyticsManager.logSocialEvent(appContext, "social_post_created")
        requestSocialPush { SupabaseSocialPushService.publishedPost(postId) }
        return post
    }

    suspend fun createMediaPost(
        profile: SocialProfile,
        caption: String,
        uri: android.net.Uri,
        mimeType: String
    ): SocialPost = createMediaPost(profile, caption, listOf(SocialMediaUpload(uri, mimeType)))

    suspend fun createMediaPost(
        profile: SocialProfile,
        caption: String,
        uploads: List<SocialMediaUpload>
    ): SocialPost {
        val uid = currentUid()
        require(uid == profile.uid) { "El perfil actual no coincide con la sesión." }
        require(SocialMediaUploadLimits.isValidPreparedMediaCount(uploads.size)) {
            "Una publicación admite entre 1 y ${SocialMediaUploadLimits.MAX_PREPARED_MEDIA_PER_POST} archivos preparados."
        }
        val kinds = uploads.map { supportedSocialMediaType(it.mimeType) }
        require(kinds.all { it != null }) { "Social admite fotos, GIFs y videos compatibles; no admite documentos." }
        val cleanCaption = caption.trim().take(2200)
        val uploadedPaths = mutableListOf<String>()
        try {
            val mediaItems = uploads.mapIndexed { index, upload ->
                val path = mediaService.upload(upload.uri, "post", upload.mimeType)
                uploadedPaths += path
                SocialPostMedia(path, kinds[index]!!, upload.mimeType.substringBefore(';').trim().lowercase())
            }
            val postRef = root.child("postsByUser").child(uid).push()
            val postId = postRef.key ?: error("No se pudo crear el identificador de la publicación.")
            // Keep the legacy mediaPath/mediaType pointed at a video when the
            // carousel contains one, so existing video-feed/save rules still work.
            val primary = mediaItems.firstOrNull { it.mediaType == "video" } ?: mediaItems.first()
            val post = SocialPost(
                id = postId,
                ownerUid = uid,
                username = profile.username,
                displayName = profile.displayName,
                avatarUrl = profile.avatarUrl,
                caption = cleanCaption,
                createdAt = System.currentTimeMillis(),
                topics = extractTopics(cleanCaption),
                mediaPath = primary.mediaPath,
                mediaType = primary.mediaType,
                mediaItems = mediaItems
            )
            postRef.setValue(post.toMap()).await()
            if (!profile.isPrivate) {
                root.child("publicFeed").child(publicFeedKey(uid, postId)).setValue(post.toMap()).await()
            }
            FirebaseAnalyticsManager.logSocialEvent(appContext, "social_post_created")
            requestSocialPush { SupabaseSocialPushService.publishedPost(postId) }
            return post
        } catch (error: Exception) {
            uploadedPaths.forEach { path -> runCatching { mediaService.delete(path) } }
            throw error
        }
    }

    suspend fun createStory(profile: SocialProfile, uri: android.net.Uri, mimeType: String): SocialStory {
        val uid = currentUid()
        require(uid == profile.uid) { "El perfil actual no coincide con la sesión." }
        val mediaPath = mediaService.upload(uri, "story", mimeType)
        val storyRef = root.child("storiesByUser").child(uid).push()
        val storyId = storyRef.key ?: error("No se pudo crear la historia.")
        val now = System.currentTimeMillis()
        val mediaType = if (mimeType.startsWith("video/")) "video" else "image"
        val story = SocialStory(
            id = storyId,
            ownerUid = uid,
            username = profile.username,
            displayName = profile.displayName,
            avatarUrl = profile.avatarUrl,
            mediaPath = mediaPath,
            mediaType = mediaType,
            createdAt = now,
            expiresAt = now + 24L * 60L * 60L * 1000L
        )
        try {
            storyRef.setValue(story.toMap()).await()
            if (!profile.isPrivate) {
                root.child("publicStories").child(publicStoryKey(uid, storyId)).setValue(story.toMap()).await()
            }
        } catch (error: Exception) {
            runCatching { mediaService.delete(mediaPath) }
            throw error
        }
        FirebaseAnalyticsManager.logSocialEvent(appContext, "social_story_created")
        requestSocialPush { SupabaseSocialPushService.publishedStory(storyId) }
        return story
    }

    suspend fun loadStories(): List<SocialStory> {
        val uid = currentUid()
        val followed = readAcceptedFollowing(uid)
        val stories = linkedMapOf<String, SocialStory>()
        root.child("publicStories").get().await().children
            .mapNotNull { it.toSocialStory() }
            .forEach { stories["${it.ownerUid}:${it.id}"] = it }
        (followed + uid).distinct().forEach { ownerUid ->
            root.child("storiesByUser").child(ownerUid).get().await().children
                .mapNotNull { it.toSocialStory() }
                .forEach { stories["${it.ownerUid}:${it.id}"] = it }
        }
        val now = System.currentTimeMillis()
        return stories.values.filter { it.expiresAt > now }.sortedBy { it.createdAt }
    }

    suspend fun signedMediaUrl(mediaPath: String, entityType: String, entityId: String): String =
        mediaService.signedDownload(mediaPath, entityType, entityId)

    suspend fun setLiked(post: SocialPost, liked: Boolean) {
        val uid = currentUid()
        val likeRef = root.child("likes").child(post.ownerUid).child(post.id).child(uid)
        if (liked) {
            likeRef.setValue(true).await()
            root.child("preferences").child(uid).child("likedPosts")
                .child("${post.ownerUid}_${post.id}")
                .setValue(mapOf("topics" to post.topics.associateWith { true }))
                .await()
            post.topics.forEach { topic -> adjustTopicWeight(uid, topic, 1) }
            FirebaseAnalyticsManager.logSocialEvent(appContext, "social_like")
            requestSocialPush { SupabaseSocialPushService.likedPost(post.ownerUid, post.id) }
        } else {
            likeRef.removeValue().await()
            root.child("preferences").child(uid).child("likedPosts")
                .child("${post.ownerUid}_${post.id}")
                .removeValue()
                .await()
            post.topics.forEach { topic -> adjustTopicWeight(uid, topic, -1) }
            FirebaseAnalyticsManager.logSocialEvent(appContext, "social_unlike")
        }
    }

    suspend fun likedByCurrentUser(post: SocialPost): Boolean {
        val uid = currentUid()
        return root.child("likes").child(post.ownerUid).child(post.id).child(uid)
            .get().await().getValue(Boolean::class.java) == true
    }

    suspend fun loadComments(post: SocialPost): List<SocialComment> {
        currentUid()
        return root.child(SocialInteractionPaths.comments(post.ownerUid, post.id))
            .orderByChild("createdAt")
            .get().await().children.mapNotNull { it.toSocialComment() }
    }

    suspend fun addComment(post: SocialPost, text: String): SocialComment {
        val uid = currentUid()
        require(SocialComment.isValidBody(text)) { "El comentario debe tener entre 1 y 1000 caracteres." }
        val cleanText = text.trim()
        val commentRef = root.child(SocialInteractionPaths.comments(post.ownerUid, post.id)).push()
        val commentId = commentRef.key ?: error("No se pudo crear el identificador del comentario.")
        val profile = root.child("profiles").child(uid).get().await()
        val comment = SocialComment(
            id = commentId,
            authorUid = uid,
            authorUsername = profile.child("username").getValue(String::class.java).orEmpty(),
            authorDisplayName = profile.child("displayName").getValue(String::class.java).orEmpty(),
            text = cleanText,
            createdAt = System.currentTimeMillis()
        )
        commentRef.setValue(comment.toMap()).await()
        return comment
    }

    suspend fun isVideoSaved(post: SocialPost): Boolean {
        val uid = currentUid()
        return root.child(SocialInteractionPaths.savedVideo(uid, post.ownerUid, post.id))
            .get().await().exists()
    }

    suspend fun setVideoSaved(post: SocialPost, saved: Boolean) {
        val uid = currentUid()
        require(post.containsVideo) { "Solo se pueden guardar videos." }
        val savedRef = root.child(SocialInteractionPaths.savedVideo(uid, post.ownerUid, post.id))
        if (saved) {
            savedRef.setValue(
                mapOf(
                    "ownerUid" to post.ownerUid,
                    "postId" to post.id,
                    "savedAt" to System.currentTimeMillis()
                )
            ).await()
        } else {
            savedRef.removeValue().await()
        }
    }

    suspend fun loadSavedVideos(): List<SocialPost> {
        val uid = currentUid()
        val savedSnapshot = root.child("savedPosts").child(uid).get().await()
        val posts = mutableListOf<Pair<Long, SocialPost>>()
        for (ownerNode in savedSnapshot.children) {
            val ownerUid = ownerNode.key ?: continue
            for (savedNode in ownerNode.children) {
                val postId = savedNode.key ?: continue
                val savedAt = savedNode.child("savedAt").getValue(Long::class.java) ?: 0L
                val post = runCatching {
                    root.child("postsByUser").child(ownerUid).child(postId).get().await().toSocialPost()
                }.getOrNull()
                if (post?.containsVideo == true) posts += savedAt to post
            }
        }
        return posts.sortedByDescending { it.first }.map { it.second }
    }

    suspend fun follow(target: SocialProfile, requester: SocialProfile) {
        val uid = currentUid()
        require(uid == requester.uid) { "El perfil solicitante no coincide con la sesión." }
        require(uid != target.uid) { "No puedes seguir tu propio perfil." }
        val status = if (target.isPrivate) "pending" else "accepted"
        val relation = mapOf("status" to status, "createdAt" to System.currentTimeMillis())
        root.child("following").child(uid).child(target.uid).setValue(relation).await()
        if (target.isPrivate) {
            root.child("followRequests").child(target.uid).child(uid).setValue(
                mapOf(
                    "requesterUid" to uid,
                    "username" to requester.username,
                    "displayName" to requester.displayName,
                    "status" to "pending",
                    "createdAt" to System.currentTimeMillis()
                )
            ).await()
        } else {
            root.child("followers").child(target.uid).child(uid).setValue(relation).await()
        }
        FirebaseAnalyticsManager.logSocialEvent(
            appContext,
            if (target.isPrivate) "social_follow_request" else "social_follow"
        )
        requestSocialPush { SupabaseSocialPushService.followedUser(target.uid) }
    }

    suspend fun unfollow(targetUid: String) {
        val uid = currentUid()
        root.child("following").child(uid).child(targetUid).removeValue().await()
        root.child("followers").child(targetUid).child(uid).removeValue().await()
        root.child("followRequests").child(targetUid).child(uid).removeValue().await()
        FirebaseAnalyticsManager.logSocialEvent(appContext, "social_unfollow")
    }

    suspend fun pendingFollowRequests(): List<SocialFollowRequest> {
        val uid = currentUid()
        val snapshot = root.child("followRequests").child(uid).get().await()
        return snapshot.children.mapNotNull { request ->
            if (request.child("status").getValue(String::class.java) != "pending") return@mapNotNull null
            val requesterUid = request.key ?: return@mapNotNull null
            SocialFollowRequest(
                uid = requesterUid,
                username = request.child("username").getValue(String::class.java).orEmpty(),
                displayName = request.child("displayName").getValue(String::class.java) ?: "Usuario"
            )
        }
    }

    suspend fun acceptFollowRequest(requesterUid: String) {
        val uid = currentUid()
        val relation = mapOf("status" to "accepted", "createdAt" to System.currentTimeMillis())
        root.child("followers").child(uid).child(requesterUid).setValue(relation).await()
        root.child("followRequests").child(uid).child(requesterUid).child("status").setValue("accepted").await()
        FirebaseAnalyticsManager.logSocialEvent(appContext, "social_follow_accepted")
        requestSocialPush { SupabaseSocialPushService.acceptedFollow(requesterUid) }
    }

    private suspend fun readAcceptedFollowing(uid: String): List<String> {
        val snapshot = root.child("following").child(uid).get().await()
        val accepted = mutableListOf<String>()
        for (entry in snapshot.children) {
            val targetUid = entry.key ?: continue
            var status = entry.child("status").getValue(String::class.java) ?: "pending"
            if (status == "pending") {
                val acceptedByTarget = root.child("followers").child(targetUid).child(uid)
                    .child("status").get().await().getValue(String::class.java) == "accepted"
                if (acceptedByTarget) {
                    root.child("following").child(uid).child(targetUid).child("status").setValue("accepted").await()
                    status = "accepted"
                }
            }
            if (status == "accepted") accepted.add(targetUid)
        }
        return accepted
    }

    private suspend fun readTopicWeights(uid: String): Map<String, Int> {
        val snapshot = root.child("preferences").child(uid).child("topics").get().await()
        return snapshot.children.mapNotNull { child ->
            val value = (child.value as? Number)?.toInt() ?: return@mapNotNull null
            child.key?.let { it to value }
        }.toMap()
    }

    private fun adjustTopicWeight(uid: String, topic: String, delta: Int) {
        val ref = root.child("preferences").child(uid).child("topics").child(topic)
        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val current = (currentData.value as? Number)?.toInt() ?: 0
                currentData.value = (current + delta).coerceAtLeast(0)
                return Transaction.success(currentData)
            }

            override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, currentData: DataSnapshot?) = Unit
        })
    }

    private suspend fun chooseAvailableUsername(requested: String, uid: String): String {
        val base = requested.trim().removePrefix("@").lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_]"), "")
        require(base.length in 3..20) { "El nombre de usuario debe tener entre 3 y 20 caracteres: letras, números o guion bajo." }
        for (attempt in 1..999) {
            val suffix = if (attempt == 1) "" else "_$attempt"
            val candidate = base.take(20 - suffix.length) + suffix
            val occupants = root.child("directory").child(candidate).get().await()
            val takenByOther = occupants.children.any { it.key != uid }
            if (!takenByOther) return candidate
        }
        error("No encontré un nombre de usuario disponible. Prueba otro.")
    }

    private suspend fun writeDirectoryEntry(profile: SocialProfile) {
        val fields = mutableMapOf<String, Any?>(
            "uid" to profile.uid,
            "username" to profile.username,
            "usernameLower" to profile.username.lowercase(Locale.ROOT),
            "displayName" to profile.displayName,
            "visibility" to profile.visibility,
            "updatedAt" to System.currentTimeMillis()
        )
        if (!profile.isPrivate) fields["avatarUrl"] = profile.avatarUrl.takeIf { it.isNotBlank() }
        root.child("directory").child(profile.username.lowercase(Locale.ROOT)).child(profile.uid)
            .setValue(fields).await()
    }

    private fun extractTopics(text: String): List<String> =
        Regex("#([\\p{L}\\p{N}_]{2,32})")
            .findAll(text.lowercase(Locale.ROOT))
            .map { it.groupValues[1] }
            .distinct()
            .take(12)
            .toList()

    private fun publicFeedKey(uid: String, postId: String) = "${uid}_$postId"
    private fun publicStoryKey(uid: String, storyId: String) = "${uid}_$storyId"

    private fun SocialComment.toMap(): Map<String, Any?> = mapOf(
        "authorUid" to authorUid,
        "authorUsername" to authorUsername,
        "authorDisplayName" to authorDisplayName,
        "text" to text,
        "createdAt" to createdAt
    )

    private fun DataSnapshot.toSocialComment(): SocialComment? {
        if (!exists()) return null
        val authorUid = child("authorUid").getValue(String::class.java).orEmpty()
        val text = child("text").getValue(String::class.java).orEmpty()
        if (authorUid.isBlank() || !SocialComment.isValidBody(text)) return null
        return SocialComment(
            id = key.orEmpty(),
            authorUid = authorUid,
            authorUsername = child("authorUsername").getValue(String::class.java).orEmpty(),
            authorDisplayName = child("authorDisplayName").getValue(String::class.java).orEmpty(),
            text = text,
            createdAt = child("createdAt").getValue(Long::class.java) ?: 0L
        )
    }

    private fun SocialProfile.toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "username" to username,
        "displayName" to displayName,
        "avatarUrl" to avatarUrl,
        "bio" to bio,
        "visibility" to visibility,
        "createdAt" to createdAt
    )

    private fun SocialPost.toMap(): Map<String, Any?> = mutableMapOf<String, Any?>(
        "id" to id,
        "ownerUid" to ownerUid,
        "username" to username,
        "displayName" to displayName,
        "avatarUrl" to avatarUrl,
        "caption" to caption,
        "createdAt" to createdAt,
        "topics" to topics.associateWith { true },
        "mediaPath" to mediaPath,
        "mediaType" to mediaType
    ).apply {
        if (mediaItems.isNotEmpty()) {
            this["mediaItems"] = mediaItems.map { item ->
                mapOf("mediaPath" to item.mediaPath, "mediaType" to item.mediaType, "mimeType" to item.mimeType)
            }
        }
    }

    private fun SocialStory.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "ownerUid" to ownerUid,
        "username" to username,
        "displayName" to displayName,
        "avatarUrl" to avatarUrl,
        "mediaPath" to mediaPath,
        "mediaType" to mediaType,
        "createdAt" to createdAt,
        "expiresAt" to expiresAt
    )

    private fun DataSnapshot.toSocialStory(): SocialStory? {
        if (!exists()) return null
        val ownerUid = child("ownerUid").getValue(String::class.java).orEmpty()
        val id = child("id").getValue(String::class.java) ?: key.orEmpty()
        if (ownerUid.isBlank() || id.isBlank()) return null
        return SocialStory(
            id = id,
            ownerUid = ownerUid,
            username = child("username").getValue(String::class.java).orEmpty(),
            displayName = child("displayName").getValue(String::class.java) ?: "Usuario",
            avatarUrl = child("avatarUrl").getValue(String::class.java).orEmpty(),
            mediaPath = child("mediaPath").getValue(String::class.java).orEmpty(),
            mediaType = child("mediaType").getValue(String::class.java) ?: "image",
            createdAt = child("createdAt").getValue(Long::class.java) ?: 0L,
            expiresAt = child("expiresAt").getValue(Long::class.java) ?: 0L
        )
    }

    private fun DataSnapshot.toSocialProfile(): SocialProfile? {
        if (!exists()) return null
        return SocialProfile(
            uid = child("uid").getValue(String::class.java).orEmpty(),
            username = child("username").getValue(String::class.java).orEmpty(),
            displayName = child("displayName").getValue(String::class.java).orEmpty(),
            avatarUrl = child("avatarUrl").getValue(String::class.java).orEmpty(),
            bio = child("bio").getValue(String::class.java).orEmpty(),
            visibility = child("visibility").getValue(String::class.java) ?: "private",
            createdAt = child("createdAt").getValue(Long::class.java) ?: 0L
        )
    }

    private fun DataSnapshot.toSocialPost(): SocialPost? {
        if (!exists()) return null
        val ownerUid = child("ownerUid").getValue(String::class.java).orEmpty()
        val id = child("id").getValue(String::class.java) ?: key.orEmpty()
        if (ownerUid.isBlank() || id.isBlank()) return null
        val topics = child("topics").children.mapNotNull { it.key }
        val legacyPath = child("mediaPath").getValue(String::class.java).orEmpty()
        val legacyType = child("mediaType").getValue(String::class.java) ?: "text"
        val storedMediaItems = child("mediaItems").children.mapNotNull { item ->
            val path = item.child("mediaPath").getValue(String::class.java).orEmpty()
            if (path.isBlank()) null else SocialPostMedia(
                mediaPath = path,
                mediaType = item.child("mediaType").getValue(String::class.java) ?: "image",
                mimeType = item.child("mimeType").getValue(String::class.java).orEmpty()
            )
        }
        val mediaItems = storedMediaItems.ifEmpty {
            if (legacyPath.isBlank()) emptyList() else listOf(SocialPostMedia(legacyPath, legacyType))
        }
        return SocialPost(
            id = id,
            ownerUid = ownerUid,
            username = child("username").getValue(String::class.java).orEmpty(),
            displayName = child("displayName").getValue(String::class.java) ?: "Usuario",
            avatarUrl = child("avatarUrl").getValue(String::class.java).orEmpty(),
            caption = child("caption").getValue(String::class.java).orEmpty(),
            createdAt = child("createdAt").getValue(Long::class.java) ?: 0L,
            topics = topics,
            mediaPath = legacyPath,
            mediaType = legacyType,
            mediaItems = mediaItems
        )
    }
}

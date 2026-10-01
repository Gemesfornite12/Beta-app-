package com.example.data.social

import android.content.Context
import com.example.data.firebase.FirebaseAppProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
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
    val mediaUrl: String = ""
)

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
            ).also { ref.setValue(it.toMap()).await() }
        }
        writeDirectoryEntry(profile)
        return profile
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
        visibility: String
    ): SocialProfile {
        val uid = currentUid()
        require(uid == current.uid) { "Solo puedes editar tu propio perfil." }
        val updated = current.copy(
            displayName = displayName.trim().take(48).ifBlank { current.displayName },
            bio = bio.trim().take(160),
            visibility = if (visibility == "public") "public" else "private"
        )
        root.child("profiles").child(uid).setValue(updated.toMap()).await()
        writeDirectoryEntry(updated)

        val posts = root.child("postsByUser").child(uid).get().await()
        for (post in posts.children) {
            val postId = post.key ?: continue
            val publicRef = root.child("publicFeed").child(publicFeedKey(uid, postId))
            if (updated.isPrivate) {
                publicRef.removeValue().await()
            } else {
                publicRef.setValue(post.value).await()
            }
        }
        val stories = root.child("storiesByUser").child(uid).get().await()
        for (story in stories.children) {
            val storyId = story.key ?: continue
            val publicRef = root.child("publicStories").child(publicStoryKey(uid, storyId))
            if (updated.isPrivate) {
                publicRef.removeValue().await()
            } else {
                publicRef.setValue(story.value).await()
            }
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

        if (!followingOnly) {
            val publicPosts = root.child("publicFeed")
                .orderByChild("createdAt")
                .limitToLast(maxPosts)
                .get()
                .await()
            publicPosts.children.mapNotNull { it.toSocialPost() }
                .forEach { postsByKey["${it.ownerUid}:${it.id}"] = it }
        }

        val authorsToRead = if (followingOnly) followed else followed + uid
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
        return post
    }

    suspend fun createMediaPost(
        profile: SocialProfile,
        caption: String,
        uri: android.net.Uri,
        mimeType: String
    ): SocialPost {
        val uid = currentUid()
        require(uid == profile.uid) { "El perfil actual no coincide con la sesión." }
        val cleanCaption = caption.trim().take(2200)
        val mediaPath = mediaService.upload(uri, "post", mimeType)
        val postRef = root.child("postsByUser").child(uid).push()
        val postId = postRef.key ?: error("No se pudo crear el identificador de la publicación.")
        val mediaType = if (mimeType.startsWith("video/")) "video" else "image"
        val post = SocialPost(
            id = postId,
            ownerUid = uid,
            username = profile.username,
            displayName = profile.displayName,
            avatarUrl = profile.avatarUrl,
            caption = cleanCaption,
            createdAt = System.currentTimeMillis(),
            topics = extractTopics(cleanCaption),
            mediaPath = mediaPath,
            mediaType = mediaType
        )
        try {
            postRef.setValue(post.toMap()).await()
            if (!profile.isPrivate) {
                root.child("publicFeed").child(publicFeedKey(uid, postId)).setValue(post.toMap()).await()
            }
        } catch (error: Exception) {
            runCatching { mediaService.delete(mediaPath) }
            throw error
        }
        return post
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
        } else {
            likeRef.removeValue().await()
            root.child("preferences").child(uid).child("likedPosts")
                .child("${post.ownerUid}_${post.id}")
                .removeValue()
                .await()
            post.topics.forEach { topic -> adjustTopicWeight(uid, topic, -1) }
        }
    }

    suspend fun likedByCurrentUser(post: SocialPost): Boolean {
        val uid = currentUid()
        return root.child("likes").child(post.ownerUid).child(post.id).child(uid)
            .get().await().getValue(Boolean::class.java) == true
    }

    suspend fun follow(target: SocialProfile) {
        val uid = currentUid()
        require(uid != target.uid) { "No puedes seguir tu propio perfil." }
        val status = if (target.isPrivate) "pending" else "accepted"
        val relation = mapOf("status" to status, "createdAt" to System.currentTimeMillis())
        root.child("following").child(uid).child(target.uid).setValue(relation).await()
        if (target.isPrivate) {
            root.child("followRequests").child(target.uid).child(uid).setValue(
                mapOf("requesterUid" to uid, "status" to "pending", "createdAt" to System.currentTimeMillis())
            ).await()
        } else {
            root.child("followers").child(target.uid).child(uid).setValue(relation).await()
        }
    }

    suspend fun unfollow(targetUid: String) {
        val uid = currentUid()
        root.child("following").child(uid).child(targetUid).removeValue().await()
        root.child("followers").child(targetUid).child(uid).removeValue().await()
        root.child("followRequests").child(targetUid).child(uid).removeValue().await()
    }

    suspend fun pendingFollowRequests(): List<SocialFollowRequest> {
        val uid = currentUid()
        val snapshot = root.child("followRequests").child(uid).get().await()
        return snapshot.children.mapNotNull { request ->
            if (request.child("status").getValue(String::class.java) != "pending") return@mapNotNull null
            val requesterUid = request.key ?: return@mapNotNull null
            val profile = root.child("profiles").child(requesterUid).get().await().toSocialProfile()
            SocialFollowRequest(
                uid = requesterUid,
                username = profile?.username.orEmpty(),
                displayName = profile?.displayName ?: "Usuario"
            )
        }
    }

    suspend fun acceptFollowRequest(requesterUid: String) {
        val uid = currentUid()
        val relation = mapOf("status" to "accepted", "createdAt" to System.currentTimeMillis())
        root.child("followers").child(uid).child(requesterUid).setValue(relation).await()
        root.child("followRequests").child(uid).child(requesterUid).child("status").setValue("accepted").await()
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

    private fun SocialProfile.toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "username" to username,
        "displayName" to displayName,
        "avatarUrl" to avatarUrl,
        "bio" to bio,
        "visibility" to visibility,
        "createdAt" to createdAt
    )

    private fun SocialPost.toMap(): Map<String, Any?> = mapOf(
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
    )

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
        return SocialPost(
            id = id,
            ownerUid = ownerUid,
            username = child("username").getValue(String::class.java).orEmpty(),
            displayName = child("displayName").getValue(String::class.java) ?: "Usuario",
            avatarUrl = child("avatarUrl").getValue(String::class.java).orEmpty(),
            caption = child("caption").getValue(String::class.java).orEmpty(),
            createdAt = child("createdAt").getValue(Long::class.java) ?: 0L,
            topics = topics,
            mediaPath = child("mediaPath").getValue(String::class.java).orEmpty(),
            mediaType = child("mediaType").getValue(String::class.java) ?: "text"
        )
    }
}

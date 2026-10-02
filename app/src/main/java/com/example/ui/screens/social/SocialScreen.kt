package com.example.ui.screens.social

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.ui.components.StandardVideoPlayerDialog
import com.example.ui.components.VideoPlayer
import com.example.data.social.SocialFollowRequest
import com.example.data.social.SocialPost
import com.example.data.social.SocialProfile
import com.example.data.social.SocialStory
import android.net.Uri
import com.example.data.social.SocialRepository
import com.example.ui.viewmodel.OmniViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SocialSection(val label: String) {
    FOR_YOU("Para ti"), FOLLOWING("Siguiendo"), SEARCH("Buscar"), PROFILE("Perfil")
}

@Composable
fun SocialScreen(
    viewModel: OmniViewModel,
    onBack: () -> Unit
) {
    val authState by viewModel.authUiState.collectAsState()
    val currentUser = authState.currentUser
    val context = LocalContext.current
    val repository = remember(context) { SocialRepository(context) }
    val scope = rememberCoroutineScope()

    var myProfile by remember { mutableStateOf<SocialProfile?>(null) }
    var viewingProfile by remember { mutableStateOf<SocialProfile?>(null) }
    var profilePosts by remember { mutableStateOf<List<SocialPost>>(emptyList()) }
    var feed by remember { mutableStateOf<List<SocialPost>>(emptyList()) }
    var stories by remember { mutableStateOf<List<SocialStory>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<SocialProfile>>(emptyList()) }
    var followRequests by remember { mutableStateOf<List<SocialFollowRequest>>(emptyList()) }
    var followedState by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedSection by remember { mutableStateOf(SocialSection.FOR_YOU) }
    var searchQuery by remember { mutableStateOf("") }
    var newPostText by remember { mutableStateOf("") }
    var newPostMediaUri by remember { mutableStateOf<Uri?>(null) }
    var pickerTarget by remember { mutableStateOf("post") }
    var activeStory by remember { mutableStateOf<SocialStory?>(null) }
    var videoToPlay by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showCreatePost by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }
    var editDisplayName by remember { mutableStateOf("") }
    var editUsername by remember { mutableStateOf("") }
    var editBio by remember { mutableStateOf("") }
    var isSavingProfile by remember { mutableStateOf(false) }
    var isUploadingMedia by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val likedPosts = remember { mutableStateMapOf<String, Boolean>() }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            if (pickerTarget == "story") {
                val current = myProfile
                if (current != null) {
                    scope.launch {
                        isUploadingMedia = true
                        try {
                            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                            repository.createStory(current, uri, mimeType)
                            stories = repository.loadStories()
                            errorMessage = null
                        } catch (error: Exception) {
                            errorMessage = error.message ?: "No se pudo subir la historia."
                        } finally {
                            isUploadingMedia = false
                        }
                    }
                }
            } else {
                newPostMediaUri = uri
            }
        }
    }

    fun postKey(post: SocialPost) = "${post.ownerUid}:${post.id}"

    suspend fun refreshFeed() {
        val items = repository.loadFeed(followingOnly = selectedSection == SocialSection.FOLLOWING)
        feed = items
        items.take(40).forEach { post ->
            likedPosts[postKey(post)] = runCatching { repository.likedByCurrentUser(post) }.getOrDefault(false)
        }
    }

    LaunchedEffect(currentUser?.uid) {
        if (currentUser == null) {
            errorMessage = "Inicia sesión para usar Social."
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        errorMessage = null
        try {
            myProfile = repository.ensureCurrentProfile(
                displayNameHint = currentUser.displayName,
                emailHint = currentUser.email,
                avatarUrlHint = currentUser.avatarUrl
            )
            followRequests = repository.pendingFollowRequests()
            stories = repository.loadStories()
            isLoading = false
        } catch (error: Exception) {
            errorMessage = error.message ?: "No se pudo cargar tu perfil social."
            isLoading = false
        }
    }

    LaunchedEffect(selectedSection, myProfile?.uid) {
        if (myProfile == null || (selectedSection != SocialSection.FOR_YOU && selectedSection != SocialSection.FOLLOWING)) return@LaunchedEffect
        try {
            refreshFeed()
            errorMessage = null
        } catch (error: Exception) {
            errorMessage = "No se pudo cargar el feed. Verifica la conexión y las reglas de Social."
        }
    }

    LaunchedEffect(searchQuery, selectedSection) {
        if (selectedSection != SocialSection.SEARCH) return@LaunchedEffect
        delay(250)
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
        } else {
            try {
                searchResults = repository.searchProfiles(searchQuery)
                val ownUid = myProfile?.uid
                val statuses = mutableMapOf<String, String>()
                for (target in searchResults) {
                    statuses[target.uid] = if (target.uid == ownUid) "self" else repository.followStatus(target.uid).orEmpty()
                }
                followedState = statuses
            } catch (error: Exception) {
                errorMessage = "No se pudo buscar perfiles."
            }
        }
    }

    BackHandler(enabled = viewingProfile != null) {
        viewingProfile = null
        profilePosts = emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080D19))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = if (viewingProfile != null) {
                    { viewingProfile = null; profilePosts = emptyList() }
                } else onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                Column {
                    Text("Social", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Tu comunidad en OmniStudio", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
            }
            Row {
                IconButton(onClick = {
                    scope.launch {
                        runCatching { refreshFeed() }
                            .onFailure { errorMessage = "No se pudo actualizar el feed." }
                    }
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = Color(0xFFCBD5E1))
                }
                IconButton(
                    onClick = { showCreatePost = true },
                    modifier = Modifier.testTag("social_create_post")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Crear publicación", tint = Color(0xFFF472B6))
                }
            }
        }

        if (viewingProfile == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SocialSection.values().forEach { section ->
                    val selected = selectedSection == section
                    Surface(
                        color = if (selected) Color(0xFF4C1D3D) else Color(0xFF151C2C),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { selectedSection = section }
                    ) {
                        Text(
                            text = section.label,
                            color = if (selected) Color(0xFFF9A8D4) else Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 10.dp)
                                .testTag("social_tab_${section.name.lowercase()}")
                        )
                    }
                }
            }
        }

        errorMessage?.let { message ->
            Surface(
                color = Color(0xFF3B1D2A),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(message, color = Color(0xFFFDA4AF), fontSize = 12.sp, modifier = Modifier.padding(10.dp))
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFE1306C))
            }
        } else if (viewingProfile != null) {
            ViewedProfileContent(
                profile = viewingProfile!!,
                posts = profilePosts,
                repository = repository,
                followStatus = followedState[viewingProfile!!.uid].orEmpty(),
                onFollow = {
                    viewingProfile?.let { target ->
                        scope.launch {
                            try {
                                if (followedState[target.uid] == "accepted" || followedState[target.uid] == "pending") {
                                    repository.unfollow(target.uid)
                                    followedState = followedState + (target.uid to "")
                                } else {
                                    repository.follow(target, myProfile ?: error("Tu perfil no está disponible."))
                                    followedState = followedState + (target.uid to if (target.isPrivate) "pending" else "accepted")
                                }
                            } catch (error: Exception) {
                                errorMessage = error.message ?: "No se pudo actualizar el seguimiento."
                            }
                        }
                    }
                }
            )
        } else {
            when (selectedSection) {
                SocialSection.FOR_YOU, SocialSection.FOLLOWING -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (selectedSection == SocialSection.FOR_YOU) {
                            item {
                                StoriesPlaceholder(
                                    profile = myProfile,
                                    stories = stories,
                                    onAddStory = {
                                        pickerTarget = "story"
                                        mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                    },
                                    onOpenStory = { story ->
                                        scope.launch {
                                            try {
                                                val url = repository.signedMediaUrl(story.mediaPath, "story", story.id)
                                                activeStory = story.copy(mediaUrl = url)
                                            } catch (error: Exception) {
                                                errorMessage = "No se pudo abrir esta historia privada."
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        if (feed.isEmpty()) {
                            item { EmptySocialFeed(isFollowing = selectedSection == SocialSection.FOLLOWING, onCreate = { showCreatePost = true }) }
                        } else {
                            items(feed, key = { postKey(it) }) { post ->
                                SocialPostCard(
                                    post = post,
                                    repository = repository,
                                    liked = likedPosts[postKey(post)] == true,
                                    onLike = {
                                        scope.launch {
                                            val next = likedPosts[postKey(post)] != true
                                            runCatching { repository.setLiked(post, next) }
                                                .onSuccess { likedPosts[postKey(post)] = next }
                                                .onFailure { errorMessage = "No se pudo guardar el like." }
                                        }
                                    },
                                    onAuthorClick = {
                                        scope.launch {
                                            try {
                                                val loadedProfile = repository.getProfile(post.ownerUid)
                                                viewingProfile = loadedProfile ?: SocialProfile(
                                                    uid = post.ownerUid,
                                                    username = post.username,
                                                    displayName = post.displayName,
                                                    avatarUrl = post.avatarUrl
                                                )
                                                profilePosts = repository.loadProfilePosts(post.ownerUid)
                                                followedState = followedState + (post.ownerUid to repository.followStatus(post.ownerUid).orEmpty())
                                            } catch (error: Exception) {
                                                errorMessage = "No se pudo abrir ese perfil."
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        item { Spacer(Modifier.size(12.dp)) }
                    }
                }

                SocialSection.SEARCH -> {
                    Column(Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("social_search_query"),
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFF472B6)) },
                            placeholder = { Text("Buscar personas por nombre o @usuario") },
                            singleLine = true
                        )
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(searchResults, key = { it.uid }) { target ->
                                SearchProfileRow(
                                    profile = target,
                                    isSelf = target.uid == myProfile?.uid,
                                    followStatus = followedState[target.uid].orEmpty(),
                                    onOpen = {
                                        scope.launch {
                                            val loaded = runCatching { repository.getProfile(target.uid) }.getOrNull()
                                            viewingProfile = loaded ?: target
                                            profilePosts = runCatching { repository.loadProfilePosts(target.uid) }.getOrDefault(emptyList())
                                        }
                                    },
                                    onFollow = {
                                        scope.launch {
                                            try {
                                                if (followedState[target.uid] == "accepted" || followedState[target.uid] == "pending") {
                                                    repository.unfollow(target.uid)
                                                    followedState = followedState + (target.uid to "")
                                                } else {
                                                    repository.follow(target, myProfile ?: error("Tu perfil no está disponible."))
                                                    followedState = followedState + (target.uid to if (target.isPrivate) "pending" else "accepted")
                                                }
                                            } catch (error: Exception) {
                                                errorMessage = error.message ?: "No se pudo seguir ese perfil."
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                SocialSection.PROFILE -> {
                    val myPosts = remember(feed, myProfile?.uid) { feed.filter { it.ownerUid == myProfile?.uid } }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            MyProfileCard(
                                profile = myProfile,
                                onEdit = {
                                    myProfile?.let { profile ->
                                        editDisplayName = profile.displayName
                                        editUsername = profile.username
                                        editBio = profile.bio
                                        showEditProfile = true
                                    }
                                },
                                onVisibilityChanged = { makePublic ->
                                    myProfile?.let { current ->
                                        scope.launch {
                                            try {
                                                myProfile = repository.updateProfile(
                                                    current = current,
                                                    displayName = current.displayName,
                                                    bio = current.bio,
                                                    visibility = if (makePublic) "public" else "private"
                                                )
                                                followRequests = repository.pendingFollowRequests()
                                            } catch (error: Exception) {
                                                errorMessage = "No se pudo cambiar la privacidad: ${error.message ?: "error desconocido"}"
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        if (followRequests.isNotEmpty()) {
                            item {
                                FollowRequestsCard(
                                    requests = followRequests,
                                    onAccept = { request ->
                                        scope.launch {
                                            runCatching { repository.acceptFollowRequest(request.uid) }
                                                .onSuccess { followRequests = repository.pendingFollowRequests() }
                                                .onFailure { errorMessage = "No se pudo aceptar la solicitud." }
                                        }
                                    }
                                )
                            }
                        }
                        if (myPosts.isEmpty()) item { EmptySocialFeed(isFollowing = false, onCreate = { showCreatePost = true }) }
                        items(myPosts, key = { postKey(it) }) { post ->
                            SocialPostCard(
                                post = post,
                                repository = repository,
                                liked = likedPosts[postKey(post)] == true,
                                onLike = {
                                    scope.launch {
                                        val next = likedPosts[postKey(post)] != true
                                        runCatching { repository.setLiked(post, next) }
                                            .onSuccess { likedPosts[postKey(post)] = next }
                                            .onFailure { errorMessage = "No se pudo guardar el like." }
                                    }
                                },
                                onAuthorClick = {}
                            )
                        }
                    }
                }
            }
        }
    }

    activeStory?.let { story ->
        Dialog(onDismissRequest = { activeStory = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(color = Color(0xFF05070D), modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    if (story.mediaType == "video") {
                        VideoPlayer(
                            videoUrl = story.mediaUrl,
                            title = "Historia de @${story.username}",
                            showActionButtons = false,
                            onLaunchStandardVideo = { url, title -> videoToPlay = url to title }
                        )
                    } else {
                        AsyncImage(
                            model = story.mediaUrl,
                            contentDescription = "Historia de ${story.displayName}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    TextButton(onClick = { activeStory = null }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                        Text("Cerrar", color = Color.White)
                    }
                }
            }
        }
    }

    videoToPlay?.let { (url, title) ->
        StandardVideoPlayerDialog(videoUrl = url, title = title, onDismiss = { videoToPlay = null })
    }

    if (showEditProfile) {
        AlertDialog(
            onDismissRequest = { if (!isSavingProfile) showEditProfile = false },
            containerColor = Color(0xFF151C2C),
            title = { Text("Editar perfil", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editDisplayName,
                        onValueChange = { editDisplayName = it.take(48) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nombre visible") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { value ->
                            editUsername = value.lowercase(Locale.ROOT)
                                .filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }
                                .take(20)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("social_edit_username"),
                        label = { Text("Nombre de usuario (sin @)") },
                        singleLine = true
                    )
                    Text("Si ya está ocupado, se agregará un número para hacerlo único.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it.take(160) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Descripción") },
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = editUsername.length >= 3 && !isSavingProfile,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C)),
                    onClick = {
                        val current = myProfile ?: return@Button
                        scope.launch {
                            isSavingProfile = true
                            try {
                                val requestedUsername = editUsername.trim().removePrefix("@").lowercase(Locale.ROOT)
                                val updated = repository.updateProfile(
                                    current = current,
                                    displayName = editDisplayName,
                                    bio = editBio,
                                    visibility = current.visibility,
                                    username = requestedUsername
                                )
                                myProfile = updated
                                showEditProfile = false
                                refreshFeed()
                                profilePosts = repository.loadProfilePosts(current.uid)
                                errorMessage = if (updated.username != requestedUsername) {
                                    "Ese nombre ya estaba usado; se asignó @${updated.username}."
                                } else null
                            } catch (error: Exception) {
                                errorMessage = error.message ?: "No se pudo guardar el perfil."
                            } finally {
                                isSavingProfile = false
                            }
                        }
                    }
                ) { Text(if (isSavingProfile) "Guardando…" else "Guardar") }
            },
            dismissButton = { TextButton(onClick = { showEditProfile = false }, enabled = !isSavingProfile) { Text("Cancelar") } }
        )
    }

    if (showCreatePost) {
        AlertDialog(
            onDismissRequest = { showCreatePost = false },
            containerColor = Color(0xFF151C2C),
            title = { Text("Nueva publicación", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Comparte una idea con tu comunidad. Puedes usar #temas para personalizar recomendaciones.", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    Spacer(Modifier.size(10.dp))
                    OutlinedTextField(
                        value = newPostText,
                        onValueChange = { newPostText = it.take(2200) },
                        modifier = Modifier.fillMaxWidth().testTag("social_new_post_text"),
                        placeholder = { Text("¿Qué quieres compartir?") },
                        minLines = 3,
                        maxLines = 6
                    )
                    TextButton(onClick = {
                        pickerTarget = "post"
                        mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFFF472B6), modifier = Modifier.size(18.dp))
                        Text(if (newPostMediaUri == null) "Adjuntar foto o video" else "Cambiar foto o video", color = Color(0xFFF472B6))
                    }
                    newPostMediaUri?.let {
                        Text("Multimedia seleccionada · máximo 50 MB", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = (newPostText.isNotBlank() || newPostMediaUri != null) && !isUploadingMedia,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C)),
                    onClick = {
                        val current = myProfile ?: return@Button
                        scope.launch {
                            isUploadingMedia = true
                            try {
                                val mediaUri = newPostMediaUri
                                if (mediaUri == null) {
                                    repository.createTextPost(current, newPostText)
                                } else {
                                    val mimeType = context.contentResolver.getType(mediaUri) ?: "image/jpeg"
                                    repository.createMediaPost(current, newPostText, mediaUri, mimeType)
                                }
                                newPostText = ""
                                newPostMediaUri = null
                                showCreatePost = false
                                selectedSection = SocialSection.FOR_YOU
                                refreshFeed()
                                errorMessage = null
                            } catch (error: Exception) {
                                errorMessage = error.message ?: "No se pudo publicar."
                            } finally {
                                isUploadingMedia = false
                            }
                        }
                    }
                ) { Text("Publicar") }
            },
            dismissButton = { TextButton(onClick = { showCreatePost = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun StoriesPlaceholder(
    profile: SocialProfile?,
    stories: List<SocialStory>,
    onAddStory: () -> Unit,
    onOpenStory: (SocialStory) -> Unit
) {
    val latestStories = stories
        .filter { it.ownerUid != profile?.uid }
        .groupBy { it.ownerUid }
        .values
        .mapNotNull { group -> group.maxByOrNull { it.createdAt } }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)) {
        Text("Historias · 24 horas", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onAddStory)) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AvatarCircle(name = profile?.displayName ?: "Tú", color = Color(0xFFE1306C), size = 56, imageUrl = profile?.avatarUrl.orEmpty())
                        Surface(color = Color(0xFFE1306C), shape = CircleShape, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Agregar historia", tint = Color.White, modifier = Modifier.padding(2.dp))
                        }
                    }
                    Text("Tu historia", color = Color(0xFFCBD5E1), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            items(latestStories, key = { it.ownerUid }) { story ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onOpenStory(story) }) {
                    AvatarCircle(name = story.displayName, color = Color(0xFFE1306C), size = 56, imageUrl = story.avatarUrl)
                    Text(story.username.take(12), color = Color(0xFFCBD5E1), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SocialPostCard(
    post: SocialPost,
    repository: SocialRepository,
    liked: Boolean,
    onLike: () -> Unit,
    onAuthorClick: () -> Unit
) {
    val signedMediaUrl by produceState(initialValue = post.mediaUrl, post.mediaPath, post.id) {
        if (post.mediaPath.isNotBlank() && value.isBlank()) {
            value = runCatching { repository.signedMediaUrl(post.mediaPath, "post", post.id) }.getOrDefault("")
        }
    }
    var videoToPlay by remember(post.id) { mutableStateOf<Pair<String, String>?>(null) }

    Surface(
        color = Color(0xFF111827),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle(name = post.displayName, color = Color(0xFF7C3AED), size = 40, imageUrl = post.avatarUrl)
                Column(Modifier.weight(1f).padding(start = 10.dp).clickable(onClick = onAuthorClick)) {
                    Text(post.displayName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("@${post.username}", color = Color(0xFF94A3B8), fontSize = 10.sp)
                }
                Text(relativeTime(post.createdAt), color = Color(0xFF64748B), fontSize = 10.sp)
            }
            if (post.caption.isNotBlank()) {
                Text(post.caption, color = Color(0xFFE2E8F0), fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 12.dp))
            }
            if (post.mediaPath.isNotBlank()) {
                if (signedMediaUrl.isBlank()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFE1306C), modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                } else if (post.mediaType == "video") {
                    VideoPlayer(
                        videoUrl = signedMediaUrl,
                        title = "Video de @${post.username}",
                        showActionButtons = false,
                        modifier = Modifier.padding(top = 10.dp),
                        onLaunchStandardVideo = { url, title -> videoToPlay = url to title }
                    )
                } else {
                    AsyncImage(
                        model = signedMediaUrl,
                        contentDescription = "Publicación de ${post.displayName}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(300.dp)
                    )
                }
            }
            if (post.topics.isNotEmpty()) {
                Text(post.topics.joinToString("  ") { "#$it" }, color = Color(0xFFF472B6), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                IconButton(onClick = onLike, modifier = Modifier.testTag("social_like_${post.id}")) {
                    Icon(
                        imageVector = if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (liked) "Quitar me gusta" else "Me gusta",
                        tint = if (liked) Color(0xFFE1306C) else Color(0xFFCBD5E1)
                    )
                }
                Text(if (liked) "Te gusta" else "Me gusta", color = if (liked) Color(0xFFF472B6) else Color(0xFFCBD5E1), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                if (post.mediaType != "text") Text("Multimedia", color = Color(0xFF94A3B8), fontSize = 10.sp)
            }
        }
    }
    videoToPlay?.let { (url, title) ->
        StandardVideoPlayerDialog(videoUrl = url, title = title, onDismiss = { videoToPlay = null })
    }
}

@Composable
private fun SearchProfileRow(
    profile: SocialProfile,
    isSelf: Boolean,
    followStatus: String,
    onOpen: () -> Unit,
    onFollow: () -> Unit
) {
    Surface(color = Color(0xFF111827), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarCircle(name = profile.displayName, color = Color(0xFFE1306C), size = 42, imageUrl = profile.avatarUrl)
            Column(Modifier.weight(1f).padding(start = 10.dp).clickable(onClick = onOpen)) {
                Text(profile.displayName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("@${profile.username}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                if (profile.isPrivate) Text("Cuenta privada", color = Color(0xFFFBBF24), fontSize = 10.sp)
            }
            if (!isSelf) {
                TextButton(onClick = onFollow) {
                    Text(
                        text = when (followStatus) {
                            "accepted" -> "Siguiendo"
                            "pending" -> "Solicitado"
                            else -> "Seguir"
                        },
                        color = if (followStatus == "") Color(0xFFF472B6) else Color(0xFFCBD5E1),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewedProfileContent(profile: SocialProfile, posts: List<SocialPost>, repository: SocialRepository, followStatus: String, onFollow: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(color = Color(0xFF111827), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                    AvatarCircle(name = profile.displayName, color = Color(0xFF7C3AED), size = 72, imageUrl = profile.avatarUrl)
                    Text(profile.displayName, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                    Text("@${profile.username}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Text(if (profile.isPrivate) "Perfil privado" else "Perfil público", color = Color(0xFFCBD5E1), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                    if (profile.bio.isNotBlank()) Text(profile.bio, color = Color(0xFFE2E8F0), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    Button(
                        onClick = onFollow,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C)),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text(when (followStatus) { "accepted" -> "Dejar de seguir"; "pending" -> "Cancelar solicitud"; else -> "Seguir" })
                    }
                }
            }
        }
        if (profile.isPrivate && followStatus != "accepted") {
            item { Text("Las publicaciones se muestran cuando el usuario acepta tu solicitud.", color = Color(0xFF94A3B8), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp)) }
        } else {
            items(posts, key = { "${it.ownerUid}:${it.id}" }) { post ->
                SocialPostCard(post = post, repository = repository, liked = false, onLike = {}, onAuthorClick = {})
            }
        }
    }
}

@Composable
private fun MyProfileCard(profile: SocialProfile?, onEdit: () -> Unit, onVisibilityChanged: (Boolean) -> Unit) {
    if (profile == null) return
    Surface(color = Color(0xFF111827), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle(name = profile.displayName, color = Color(0xFFE1306C), size = 64, imageUrl = profile.avatarUrl)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(profile.displayName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("@${profile.username}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Text(if (profile.isPrivate) "Solo seguidores aprobados verán tus publicaciones" else "Tu perfil es visible para la comunidad", color = Color(0xFFCBD5E1), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            TextButton(onClick = onEdit, modifier = Modifier.padding(top = 2.dp)) {
                Text("Editar perfil", color = Color(0xFFF472B6))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Icon(if (profile.isPrivate) Icons.Default.Lock else Icons.Default.Public, contentDescription = null, tint = Color(0xFFF472B6), modifier = Modifier.size(18.dp))
                Text(if (profile.isPrivate) "Perfil privado" else "Perfil público", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                TextButton(
                    onClick = { onVisibilityChanged(profile.isPrivate) },
                    modifier = Modifier.testTag("social_profile_visibility")
                ) {
                    Text(if (profile.isPrivate) "Hacer público" else "Hacer privado", color = Color(0xFFF472B6))
                }
            }
        }
    }
}

@Composable
private fun FollowRequestsCard(requests: List<SocialFollowRequest>, onAccept: (SocialFollowRequest) -> Unit) {
    Surface(color = Color(0xFF20172B), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Solicitudes para seguirte", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            requests.forEach { request ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${request.displayName} · @${request.username}", color = Color(0xFFCBD5E1), fontSize = 11.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onAccept(request) }) { Text("Aceptar", color = Color(0xFFF472B6)) }
                }
            }
        }
    }
}

@Composable
private fun EmptySocialFeed(isFollowing: Boolean, onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AvatarCircle(name = "Social", color = Color(0xFF4C1D3D), size = 64)
        Text(if (isFollowing) "Tu feed está esperando" else "Empieza tu comunidad", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        Text(
            if (isFollowing) "Sigue perfiles para ver sus publicaciones aquí." else "Publica una idea con #temas y tus likes ayudarán a ordenar recomendaciones.",
            color = Color(0xFF94A3B8), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
        )
        Button(onClick = onCreate, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C)), modifier = Modifier.padding(top = 14.dp)) {
            Text("Crear publicación")
        }
    }
}

@Composable
private fun AvatarCircle(name: String, color: Color, size: Int, imageUrl: String = "") {
    Surface(color = color.copy(alpha = 0.22f), shape = CircleShape, modifier = Modifier.size(size.dp)) {
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Foto de perfil de $name",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "S",
                    color = Color(0xFFF9A8D4),
                    fontWeight = FontWeight.Bold,
                    fontSize = (size / 2.4f).sp
                )
            }
        }
    }
}

private fun relativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return "ahora"
    val minutes = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 60_000L).toInt()
    return when {
        minutes < 1 -> "ahora"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> SimpleDateFormat("d MMM", Locale("es", "CR")).format(Date(timestamp))
    }
}

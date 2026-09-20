package com.example.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.firebase.ChannelInfo
import com.example.data.firebase.FirestoreConnectionStatus
import com.example.data.firebase.GroupMember
import com.example.data.model.ChannelNotificationPreference
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import com.example.data.model.AudioProject
import com.example.data.model.ChatMessage
import com.example.data.model.DocumentItem
import com.example.data.model.UserAccount
import com.example.ui.viewmodel.OmniViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: OmniViewModel,
    onBack: () -> Unit,
    onOpenDoc: (DocumentItem) -> Unit,
    onOpenAudio: (AudioProject) -> Unit
) {
    val currentChannel by viewModel.currentChannel.collectAsState()
    val messages by viewModel.chatMessages.collectAsState()
    val chatInput by viewModel.chatInputText.collectAsState()
    val authState by viewModel.authUiState.collectAsState()
    val allDocs by viewModel.documents.collectAsState()
    val allAudio by viewModel.audioProjects.collectAsState()

    val firestoreStatus by viewModel.firestoreStatus.collectAsState()
    val typingUsers by viewModel.typingUsers.collectAsState()
    val onlineUsers by viewModel.onlineUsers.collectAsState()
    val playingAudioId by viewModel.chatPlayingAudioId.collectAsState()
    val activeCall by viewModel.activeCall.collectAsState()
    val channels by viewModel.availableChannels.collectAsState()
    val archivedChannelIds by viewModel.archivedChannelIds.collectAsState()
    val groupDeletionCountdowns by viewModel.groupDeletionCountdownSeconds.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val channelNotificationPrefs by viewModel.channelNotificationPrefs.collectAsState()

    val context = LocalContext.current
    val gifImageLoader = remember(context) {
        coil.ImageLoader.Builder(context)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(coil.decode.ImageDecoderDecoder.Factory())
                } else {
                    add(coil.decode.GifDecoder.Factory())
                }
            }
            .build()
    }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var showAttachDialog by remember { mutableStateOf(false) }
    var showChatNotificationPrefsDialog by remember { mutableStateOf(false) }
    var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }
    var previewMediaUrl by remember { mutableStateOf<String?>(null) }
    var previewMediaType by remember { mutableStateOf<String?>(null) }
    var selectedMessageForStatus by remember { mutableStateOf<ChatMessage?>(null) }

    // Estados para adjuntos pendientes (pre-envío) que el usuario puede revisar o borrar
    var pendingMediaType by remember { mutableStateOf<String?>(null) } // "image", "video", "gif"
    var pendingMediaUrl by remember { mutableStateOf<String?>(null) }
    var pendingMediaTitle by remember { mutableStateOf<String?>(null) }
    var pendingDocItem by remember { mutableStateOf<com.example.data.model.DocumentItem?>(null) }
    var pendingAudioProject by remember { mutableStateOf<com.example.data.model.AudioProject?>(null) }

    // Estados de búsqueda y archivado de canales
    var isSearching by remember { mutableStateOf(false) }
    var searchKeyword by remember { mutableStateOf("") }
    var currentMatchPointer by remember { mutableStateOf(0) }
    var showArchivedFilter by remember { mutableStateOf(false) }
    var showTopOverflowMenu by remember { mutableStateOf(false) }

    // Diálogos de grupos y chats privados
    var showMyChatsSheet by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showStartDirectChatDialog by remember { mutableStateOf(false) }
    var showGroupManageDialog by remember { mutableStateOf(false) }
    var showPermissionDeniedDialog by remember { mutableStateOf<String?>(null) }

    val activeChannelInfo = channels.firstOrNull { it.id == currentChannel }
    val isCurrentArchived = archivedChannelIds.contains(currentChannel)

    // Launchers para solicitar permisos en tiempo de ejecución para Llamadas
    val voiceCallPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val peer = if (currentChannel.startsWith("directo-")) activeChannelInfo?.name ?: "Compañero" else "Equipo ${activeChannelInfo?.name ?: "General"}"
            viewModel.startVoiceCall(peerName = peer)
        } else {
            showPermissionDeniedDialog = "Se requiere permiso de Micrófono para realizar llamadas de voz."
        }
    }

    val videoCallPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val micGranted = perms[Manifest.permission.RECORD_AUDIO] == true
        val camGranted = perms[Manifest.permission.CAMERA] == true
        if (micGranted && camGranted) {
            val peer = if (currentChannel.startsWith("directo-")) activeChannelInfo?.name ?: "Compañero" else "Equipo ${activeChannelInfo?.name ?: "General"}"
            viewModel.startVideoCall(peerName = peer)
        } else {
            showPermissionDeniedDialog = "Se requieren permisos de Micrófono y Cámara para realizar videollamadas."
        }
    }

    val launchVoiceCall = {
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            val peer = if (currentChannel.startsWith("directo-")) activeChannelInfo?.name ?: "Compañero" else "Equipo ${activeChannelInfo?.name ?: "General"}"
            viewModel.startVoiceCall(peerName = peer)
        } else {
            voiceCallPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val launchVideoCall = {
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasMic && hasCam) {
            val peer = if (currentChannel.startsWith("directo-")) activeChannelInfo?.name ?: "Compañero" else "Equipo ${activeChannelInfo?.name ?: "General"}"
            viewModel.startVideoCall(peerName = peer)
        } else {
            videoCallPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
        }
    }

    // Si hay una llamada activa de voz o video, mostrar pantalla de llamada inmersiva
    if (activeCall != null) {
        CallSessionScreen(
            callSession = activeCall!!,
            onAnswerCall = { viewModel.answerIncomingCall() },
            onRejectCall = { viewModel.rejectIncomingCall() },
            onToggleMute = { viewModel.toggleCallMute() },
            onToggleCamera = { viewModel.toggleCallCamera() },
            onToggleSpeaker = { viewModel.toggleCallSpeaker() },
            onSwitchCamera = { viewModel.switchCallCamera() },
            onEndCall = { viewModel.endActiveCall() }
        )
        return
    }

    // Filtrar estrictamente los mensajes que pertenecen al canal actualmente activo
    val displayMessages = remember(messages, currentChannel) {
        messages.filter { it.channelId == currentChannel }
    }

    // Scroll al último mensaje cuando cambia la cantidad del canal activo
    LaunchedEffect(displayMessages.size) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayMessages.size - 1)
        }
    }

    val currentUserEmail = authState.currentUser?.email ?: "gonzalez24029@gmail.com"
    val isOwner = activeChannelInfo?.isGroup == true && activeChannelInfo.creatorEmail == currentUserEmail
    val myGroupMember = activeChannelInfo?.members?.firstOrNull { it.email == currentUserEmail }
    val canSendMessages = if (activeChannelInfo?.isGroup == true && myGroupMember != null) myGroupMember.canSendMessages else true
    val canSendMedia = if (activeChannelInfo?.isGroup == true && myGroupMember != null) myGroupMember.canSendMedia else true
    val currentCountdown = groupDeletionCountdowns[currentChannel]

    // Índices de mensajes que coinciden con la búsqueda por palabra clave
    val matchingIndices = remember(displayMessages, searchKeyword) {
        if (searchKeyword.isBlank()) emptyList()
        else {
            displayMessages.mapIndexedNotNull { index, msg ->
                val matchesText = msg.text.contains(searchKeyword, ignoreCase = true)
                val matchesSender = msg.senderName.contains(searchKeyword, ignoreCase = true)
                val matchesDoc = msg.attachedDocTitle?.contains(searchKeyword, ignoreCase = true) == true
                val matchesAudio = msg.attachedAudioTitle?.contains(searchKeyword, ignoreCase = true) == true
                if (matchesText || matchesSender || matchesDoc || matchesAudio) index else null
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0F172A),
        topBar = {
            Surface(
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Header superior con información del canal y acciones principales
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("btn_chat_back")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Atrás",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = { showMyChatsSheet = true }, modifier = Modifier.testTag("btn_top_my_chats")) {
                            Icon(
                                imageVector = Icons.Default.Forum,
                                contentDescription = "Mis Chats y Grupos",
                                tint = Color(0xFF38BDF8)
                            )
                        }

                        // Avatar del canal, grupo o chat directo
                        Surface(
                            shape = CircleShape,
                            color = if (activeChannelInfo?.isGroup == true) Color(0xFF7C3AED) else Color(0xFF334155),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (activeChannelInfo?.groupPhotoUrl?.isNotBlank() == true) {
                                    AsyncImage(
                                        model = activeChannelInfo.groupPhotoUrl,
                                        contentDescription = activeChannelInfo.name,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = activeChannelInfo?.iconEmoji ?: if (activeChannelInfo?.isGroup == true) "👥" else "#",
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = activeChannelInfo?.isGroup == true) {
                                    if (activeChannelInfo?.isGroup == true) showGroupManageDialog = true
                                }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = activeChannelInfo?.name ?: currentChannel,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (activeChannelInfo?.isGroup == true) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF7C3AED).copy(alpha = 0.3f)
                                    ) {
                                        Text(
                                            text = "Grupo",
                                            color = Color(0xFFC4B5FD),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                // Indicador de estado en tiempo real de Firestore
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when (firestoreStatus) {
                                        FirestoreConnectionStatus.CONNECTED_REALTIME -> Color(0xFF065F46)
                                        FirestoreConnectionStatus.OFFLINE_SYNCED -> Color(0xFF854D0E)
                                        else -> Color(0xFF1E3A8A)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FiberManualRecord,
                                            contentDescription = null,
                                            tint = when (firestoreStatus) {
                                                FirestoreConnectionStatus.CONNECTED_REALTIME -> Color(0xFF34D399)
                                                FirestoreConnectionStatus.OFFLINE_SYNCED -> Color(0xFFFBBF24)
                                                else -> Color(0xFF60A5FA)
                                            },
                                            modifier = Modifier.size(6.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = if (firestoreStatus == FirestoreConnectionStatus.CONNECTED_REALTIME) "En Vivo" else firestoreStatus.label,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            Text(
                                text = if (activeChannelInfo?.isGroup == true) {
                                    "${activeChannelInfo.members.size} participantes"
                                } else {
                                    activeChannelInfo?.description ?: "Mensajería en tiempo real"
                                },
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Botón de Llamada de Voz (con verificación de permisos)
                        IconButton(
                            onClick = launchVoiceCall,
                            modifier = Modifier.testTag("btn_start_voice_call")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Llamada de voz",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Botón de Videollamada (con verificación de permisos)
                        IconButton(
                            onClick = launchVideoCall,
                            modifier = Modifier.testTag("btn_start_video_call")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Videollamada",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Menú desplegable de opciones secundarias del chat
                        Box {
                            IconButton(
                                onClick = { showTopOverflowMenu = true },
                                modifier = Modifier.testTag("btn_chat_top_overflow_menu")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Más opciones",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showTopOverflowMenu,
                                onDismissRequest = { showTopOverflowMenu = false },
                                modifier = Modifier.background(Color(0xFF1E293B))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Buscar en este chat", color = Color.White, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFF59E0B)) },
                                    onClick = {
                                        showTopOverflowMenu = false
                                        isSearching = !isSearching
                                        if (!isSearching) {
                                            searchKeyword = ""
                                            currentMatchPointer = 0
                                        }
                                    }
                                )

                                if (activeChannelInfo?.isGroup == true) {
                                    DropdownMenuItem(
                                        text = { Text("Administrar grupo", color = Color.White, fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFFA855F7)) },
                                        onClick = {
                                            showTopOverflowMenu = false
                                            showGroupManageDialog = true
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = { Text("Ajustes de Notificaciones", color = Color.White, fontSize = 13.sp) },
                                    leadingIcon = {
                                        val currentPref = channelNotificationPrefs[currentChannel] ?: ChannelNotificationPreference(currentChannel)
                                        val hasAllDisabled = !currentPref.notifyMessages && !currentPref.notifyVoiceCalls && !currentPref.notifyVideoCalls
                                        Icon(
                                            imageVector = if (hasAllDisabled) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                                            contentDescription = null,
                                            tint = if (hasAllDisabled) Color(0xFFEF4444) else Color(0xFF8B5CF6)
                                        )
                                    },
                                    onClick = {
                                        showTopOverflowMenu = false
                                        showChatNotificationPrefsDialog = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isCurrentArchived) "Desarchivar conversación" else "Archivar conversación",
                                            color = Color.White,
                                            fontSize = 13.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isCurrentArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                            contentDescription = null,
                                            tint = if (isCurrentArchived) Color(0xFFFBBF24) else Color(0xFFCBD5E1)
                                        )
                                    },
                                    onClick = {
                                        showTopOverflowMenu = false
                                        viewModel.toggleArchiveChannel(currentChannel)
                                    }
                                )
                            }
                        }
                    }

                    // Barra de Búsqueda de Mensajes integrada
                    AnimatedVisibility(visible = isSearching) {
                        Surface(
                            color = Color(0xFF0F172A),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                BasicTextField(
                                    value = searchKeyword,
                                    onValueChange = {
                                        searchKeyword = it
                                        currentMatchPointer = 0
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("input_search_chat"),
                                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                    singleLine = true,
                                    cursorBrush = SolidColor(Color(0xFFF59E0B)),
                                    decorationBox = { innerTextField ->
                                        if (searchKeyword.isEmpty()) {
                                            Text(
                                                "Buscar mensajes en este chat...",
                                                color = Color(0xFF64748B),
                                                fontSize = 12.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                )

                                if (searchKeyword.isNotBlank()) {
                                    Text(
                                        text = if (matchingIndices.isNotEmpty()) "${matchingIndices.size} coincidencias" else "Sin resultados",
                                        color = if (matchingIndices.isNotEmpty()) Color(0xFF34D399) else Color(0xFFEF4444),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )

                                    if (matchingIndices.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                if (matchingIndices.isNotEmpty()) {
                                                    currentMatchPointer = (currentMatchPointer - 1 + matchingIndices.size) % matchingIndices.size
                                                    coroutineScope.launch {
                                                        listState.animateScrollToItem(matchingIndices[currentMatchPointer])
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .size(26.dp)
                                                .testTag("btn_search_prev")
                                        ) {
                                            Icon(
                                                Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Anterior",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                if (matchingIndices.isNotEmpty()) {
                                                    currentMatchPointer = (currentMatchPointer + 1) % matchingIndices.size
                                                    coroutineScope.launch {
                                                        listState.animateScrollToItem(matchingIndices[currentMatchPointer])
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .size(26.dp)
                                                .testTag("btn_search_next")
                                        ) {
                                            Icon(
                                                Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Siguiente",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            searchKeyword = ""
                                            currentMatchPointer = 0
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Limpiar búsqueda",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Banner de Advertencia de Eliminación de Grupo (3 minutos)
                    if (activeChannelInfo?.isDeleting == true) {
                        Surface(
                            color = Color(0xFF7F1D1D),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("banner_group_deletion_warning")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFCA5A5),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    val secsTotal = currentCountdown ?: 180
                                    val mins = secsTotal / 60
                                    val secs = secsTotal % 60
                                    val timeStr = String.format("%02d:%02d", mins, secs)

                                    Text(
                                        text = "⚠️ ELIMINACIÓN PERMANENTE EN: $timeStr",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = if (isOwner) "Puedes cancelar el borrado en cualquier momento." else "El dueño del grupo programó su eliminación permanente.",
                                        color = Color(0xFFFECACA),
                                        fontSize = 10.sp
                                    )
                                }

                                if (isOwner) {
                                    TextButton(
                                        onClick = { viewModel.cancelGroupDeletion(activeChannelInfo.id) },
                                        modifier = Modifier.testTag("btn_cancel_delete_group")
                                    ) {
                                        Text(
                                            "Deshacer",
                                            color = Color(0xFF34D399),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                    TextButton(
                                        onClick = { viewModel.deleteGroupPermanently(activeChannelInfo.id) },
                                        modifier = Modifier.testTag("btn_confirm_delete_group_now")
                                    ) {
                                        Text(
                                            "Borrar ya",
                                            color = Color(0xFFF87171),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Banner si el chat actual está archivado
                    if (isCurrentArchived) {
                        Surface(
                            color = Color(0xFF78350F).copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Archive, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Conversación archivada (Oculta de la lista activa)", color = Color(0xFFFDE68A), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                                TextButton(onClick = { viewModel.unarchiveChannel(currentChannel) }) {
                                    Text("Desarchivar", color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Selector horizontal de canales, chats directos y grupos con filtro de archivados
                    val displayedChannels = remember(channels, archivedChannelIds, showArchivedFilter) {
                        if (showArchivedFilter) {
                            channels.filter { archivedChannelIds.contains(it.id) }
                        } else {
                            channels.filter { !archivedChannelIds.contains(it.id) }
                        }
                    }

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botón principal de acceso a Mis Chats y Grupos
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF0284C7),
                                modifier = Modifier
                                    .clickable { showMyChatsSheet = true }
                                    .testTag("btn_open_my_chats_sheet")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Forum,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Mis Chats (${channels.size})",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Toggle para alternar entre Activos y Archivados
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (showArchivedFilter) Color(0xFFF59E0B) else Color(0xFF334155),
                                modifier = Modifier
                                    .clickable { showArchivedFilter = !showArchivedFilter }
                                    .testTag("btn_toggle_archived_filter")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (showArchivedFilter) Icons.Default.Unarchive else Icons.Default.Archive,
                                        contentDescription = null,
                                        tint = if (showArchivedFilter) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (showArchivedFilter) "Archivados (${archivedChannelIds.size})" else if (archivedChannelIds.isNotEmpty()) "Archivados (${archivedChannelIds.size})" else "Archivos",
                                        color = if (showArchivedFilter) Color(0xFF0F172A) else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Botón rápido para Crear Nuevo Grupo
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF7C3AED),
                                modifier = Modifier
                                    .clickable { showCreateGroupDialog = true }
                                    .testTag("btn_open_create_group")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Crear Grupo",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Botón rápido para Iniciar Chat Privado
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF0284C7),
                                modifier = Modifier
                                    .clickable { showStartDirectChatDialog = true }
                                    .testTag("btn_open_new_direct_chat")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Chat Privado",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        items(displayedChannels) { ch ->
                            val isSelected = ch.id == currentChannel
                            val isArchived = archivedChannelIds.contains(ch.id)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = when {
                                    isSelected -> Color(0xFF4F46E5)
                                    ch.isDeleting -> Color(0xFF991B1B)
                                    isArchived -> Color(0xFF78350F)
                                    ch.isGroup -> Color(0xFF4338CA)
                                    else -> Color(0xFF334155)
                                },
                                modifier = Modifier
                                    .clickable { viewModel.loadChannelMessages(ch.id) }
                                    .testTag("channel_tab_${ch.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (ch.isDeleting) {
                                        Text(text = "⚠️", fontSize = 12.sp)
                                    } else {
                                        Text(text = ch.iconEmoji, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = ch.name,
                                        color = Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                    if (ch.isGroup) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.White.copy(alpha = 0.2f),
                                            modifier = Modifier.size(14.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${ch.members.size}",
                                                    fontSize = 8.sp,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Franja de presencia activa
                    if (onlineUsers.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A))
                                .padding(horizontal = 12.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(6.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "En canal ahora: ${onlineUsers.joinToString(", ") { it.name }}",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
            ) {
                // Indicador de escritura en tiempo real de Firestore ("typing indicator")
                AnimatedVisibility(
                    visible = typingUsers.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✍️ ${typingUsers.joinToString(", ")} está escribiendo...",
                            color = Color(0xFF818CF8),
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }

                // Aviso de restricción de permisos si el usuario no puede enviar mensajes
                if (!canSendMessages) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color(0xFF7F1D1D).copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Solo lectura: El creador del grupo ha restringido tus permisos para enviar mensajes en este canal.",
                                color = Color(0xFFFECACA),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Visualización y opción de borrar adjuntos seleccionados antes de enviar (Boceto / Pre-envío)
                if (pendingMediaType != null || pendingDocItem != null || pendingAudioProject != null) {
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Miniatura o icono descriptivo
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    pendingMediaType == "image" || pendingMediaType == "gif" -> {
                                        AsyncImage(
                                            model = pendingMediaUrl,
                                            contentDescription = "Vista previa de imagen",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    pendingMediaType == "video" -> {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            AsyncImage(
                                                model = pendingMediaUrl,
                                                contentDescription = "Vista previa de video",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp).align(Alignment.Center)
                                            )
                                        }
                                    }
                                    pendingDocItem != null -> {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    pendingAudioProject != null -> {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = Color(0xFFA855F7),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Información de lo que se enviará
                            Column(modifier = Modifier.weight(1f)) {
                                val labelText = when {
                                    pendingMediaType == "image" -> "📷 Imagen lista para enviar"
                                    pendingMediaType == "video" -> "🎥 Video listo para enviar"
                                    pendingMediaType == "gif" -> "🎭 GIF listo para enviar"
                                    pendingDocItem != null -> "📁 Documento listo para enviar"
                                    pendingAudioProject != null -> "🎵 Audio del estudio listo para enviar"
                                    else -> "Adjunto listo"
                                }
                                Text(
                                    text = labelText,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = pendingMediaTitle ?: pendingDocItem?.title ?: pendingAudioProject?.title ?: "Archivo seleccionado",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Botón de eliminar/borrar el adjunto antes de enviar (pedido por el usuario)
                            IconButton(
                                onClick = {
                                    pendingMediaType = null
                                    pendingMediaUrl = null
                                    pendingMediaTitle = null
                                    pendingDocItem = null
                                    pendingAudioProject = null
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.2f), CircleShape)
                                    .testTag("btn_delete_pending_attachment")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Borrar adjunto",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Barra de entrada de texto
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (canSendMedia) showAttachDialog = true },
                        enabled = canSendMedia,
                        modifier = Modifier.testTag("btn_attach_cloud_file")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Adjuntar multimedia o archivo",
                            tint = if (canSendMedia) Color(0xFF818CF8) else Color(0xFF475569)
                        )
                    }

                    IconButton(
                        onClick = { if (canSendMedia) showAttachDialog = true },
                        enabled = canSendMedia,
                        modifier = Modifier.testTag("btn_quick_gif")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Gif,
                            contentDescription = "Enviar GIF",
                            tint = if (canSendMedia) Color(0xFFF43F5E) else Color(0xFF475569),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    OutlinedTextField(
                        value = chatInput,
                        onValueChange = { viewModel.onChatInputChanged(it) },
                        enabled = canSendMessages,
                        placeholder = {
                            Text(
                                if (!canSendMessages) "Permiso restringido por el creador"
                                else if (pendingMediaType != null || pendingDocItem != null || pendingAudioProject != null) "Añade un comentario / leyenda opcional..."
                                else "Escribe en #${activeChannelInfo?.name ?: currentChannel}...",
                                color = Color(0xFF64748B),
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_chat_message"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedBorderColor = Color(0xFF4F46E5),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            disabledContainerColor = Color(0xFF1E293B),
                            disabledTextColor = Color(0xFF64748B)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    val hasAnyInput = chatInput.isNotBlank() || pendingMediaType != null || pendingDocItem != null || pendingAudioProject != null

                    Surface(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(enabled = canSendMessages && hasAnyInput) {
                                if (canSendMessages) {
                                    when {
                                        pendingMediaType != null -> {
                                            viewModel.sendMediaMessage(
                                                mediaType = pendingMediaType!!,
                                                mediaUrl = pendingMediaUrl!!,
                                                caption = chatInput
                                            )
                                            viewModel.onChatInputChanged("")
                                        }
                                        pendingDocItem != null -> {
                                            viewModel.sendChatMessage(attachedDoc = pendingDocItem)
                                        }
                                        pendingAudioProject != null -> {
                                            viewModel.sendChatMessage(attachedAudio = pendingAudioProject)
                                        }
                                        else -> {
                                            viewModel.sendChatMessage()
                                        }
                                    }
                                    // Limpiar estados locales de adjuntos
                                    pendingMediaType = null
                                    pendingMediaUrl = null
                                    pendingMediaTitle = null
                                    pendingDocItem = null
                                    pendingAudioProject = null
                                }
                            }
                            .testTag("btn_send_chat"),
                        color = if (canSendMessages && hasAnyInput) Color(0xFF4F46E5) else Color(0xFF334155)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar",
                                tint = if (canSendMessages && hasAnyInput) Color.White else Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (displayMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF334155),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = activeChannelInfo?.iconEmoji ?: "💬", fontSize = 28.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Canal #${activeChannelInfo?.name ?: currentChannel}",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Conexión en tiempo real con Firebase Firestore lista. Escribe un mensaje o adjunta un archivo para empezar la colaboración con el equipo.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(displayMessages, key = { it.firestoreId.ifBlank { it.id.toString() } }) { msg ->
                    val isMe = msg.senderEmail == (authState.currentUser?.email ?: "gonzalez24029@gmail.com")
                    val isMatch = searchKeyword.isNotBlank() && (
                        msg.text.contains(searchKeyword, ignoreCase = true) ||
                        msg.senderName.contains(searchKeyword, ignoreCase = true) ||
                        (msg.attachedDocTitle?.contains(searchKeyword, ignoreCase = true) == true) ||
                        (msg.attachedAudioTitle?.contains(searchKeyword, ignoreCase = true) == true)
                    )

                    MessageBubble(
                        message = msg,
                        isMe = isMe,
                        isPlayingAudio = playingAudioId != null && playingAudioId == msg.attachedAudioId,
                        isSearchMatch = isMatch,
                        searchKeyword = searchKeyword,
                        onTogglePlayAudio = { audioId -> viewModel.togglePlayChatAudio(audioId) },
                        onReact = { emoji -> viewModel.addReactionToMessage(msg, emoji) },
                        onDelete = { messageToDelete = msg },
                        onShowDeliveryStatus = { selectedMessageForStatus = it },
                        onOpenAttachedDoc = { docId ->
                            val doc = allDocs.firstOrNull { it.id == docId }
                            if (doc != null) onOpenDoc(doc)
                        },
                        onOpenAttachedAudio = { audioId ->
                            val audio = allAudio.firstOrNull { it.id == audioId }
                            if (audio != null) onOpenAudio(audio)
                        },
                        onPreviewMedia = { url, type ->
                            previewMediaUrl = url
                            previewMediaType = type
                        },
                        onStartVoiceCall = { peerName ->
                            viewModel.startVoiceCall(peerName)
                        },
                        onStartVideoCall = { peerName ->
                            viewModel.startVideoCall(peerName)
                        }
                    )
                }
            }
        }
    }

    // Modal de Detalle de Estado de Entrega (Enviado, Entregado, Visto en Firestore)
    if (selectedMessageForStatus != null) {
        val msg = selectedMessageForStatus!!
        MessageDeliveryStatusDialog(
            message = msg,
            onDismiss = { selectedMessageForStatus = null }
        )
    }

    // Modal de confirmación para eliminar mensaje
    if (messageToDelete != null) {
        val msg = messageToDelete!!
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text("¿Eliminar mensaje?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Este mensaje será eliminado en tiempo real de Firebase Firestore y de tu dispositivo.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteChatMessage(msg)
                        messageToDelete = null
                    }
                ) {
                    Text("Eliminar", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal selector de Multimedia (Fotos, Videos, GIFs, Documentos y Beats)
    if (showAttachDialog) {
        MediaPickerSheet(
            channelName = activeChannelInfo?.name ?: currentChannel,
            docs = allDocs,
            audios = allAudio,
            onSendMedia = { type, url, caption ->
                pendingMediaType = type
                pendingMediaUrl = url
                pendingMediaTitle = caption
                pendingDocItem = null
                pendingAudioProject = null
            },
            onSendDoc = { doc ->
                pendingMediaType = null
                pendingMediaUrl = null
                pendingMediaTitle = null
                pendingDocItem = doc
                pendingAudioProject = null
            },
            onSendAudio = { audio ->
                pendingMediaType = null
                pendingMediaUrl = null
                pendingMediaTitle = null
                pendingDocItem = null
                pendingAudioProject = audio
            },
            onDismiss = { showAttachDialog = false }
        )
    }

    // Diálogo Sheet: Mis Chats, Grupos y Canales
    if (showMyChatsSheet) {
        MyChatsAndGroupsSheet(
            channels = channels,
            currentChannelId = currentChannel,
            archivedChannelIds = archivedChannelIds,
            currentUserEmail = currentUserEmail,
            onSelectChannel = { viewModel.loadChannelMessages(it) },
            onCreateGroup = { showCreateGroupDialog = true },
            onStartDirectChat = { showStartDirectChatDialog = true },
            onManageGroup = { showGroupManageDialog = true },
            onToggleArchive = { viewModel.toggleArchiveChannel(it) },
            onDismiss = { showMyChatsSheet = false }
        )
    }

    // Diálogo para Crear Grupo
    if (showCreateGroupDialog) {
        CreateGroupDialog(
            allUsers = allUsers,
            currentUserEmail = currentUserEmail,
            onDismiss = { showCreateGroupDialog = false },
            onConfirm = { name, desc, photoUrl, members ->
                viewModel.createGroupChannel(
                    name = name,
                    description = desc,
                    photoUrl = photoUrl,
                    memberEmails = members
                )
                showCreateGroupDialog = false
            }
        )
    }

    // Diálogo para Iniciar Chat Privado Directo
    if (showStartDirectChatDialog) {
        StartDirectChatDialog(
            viewModel = viewModel,
            currentUserEmail = currentUserEmail,
            onDismiss = { showStartDirectChatDialog = false },
            onSelectUser = { email, name ->
                viewModel.startDirectChat(peerEmail = email, peerName = name)
                showStartDirectChatDialog = false
            }
        )
    }

    // Diálogo para Administrar Grupo Actual
    if (showGroupManageDialog && activeChannelInfo?.isGroup == true) {
        GroupManageDialog(
            channel = activeChannelInfo,
            currentUserEmail = currentUserEmail,
            allUsers = allUsers,
            deletionCountdown = currentCountdown,
            onDismiss = { showGroupManageDialog = false },
            onAddMember = { email -> viewModel.addMemberToGroup(activeChannelInfo.id, email) },
            onRemoveMember = { email -> viewModel.removeMemberFromGroup(activeChannelInfo.id, email) },
            onUpdatePermissions = { email, canMsg, canMedia ->
                viewModel.updateMemberPermissions(activeChannelInfo.id, email, canMsg, canMedia)
            },
            onResetPermissions = { email ->
                viewModel.undoOrResetMemberPermissions(activeChannelInfo.id, email)
            },
            onUpdateGroupInfo = { newName, newDesc, newPhotoUrl ->
                viewModel.updateGroupInfo(activeChannelInfo.id, newName, newDesc, newPhotoUrl)
            },
            onScheduleDeletion = {
                viewModel.scheduleGroupDeletion(activeChannelInfo.id)
            },
            onCancelDeletion = {
                viewModel.cancelGroupDeletion(activeChannelInfo.id)
            },
            onDeletePermanently = {
                viewModel.deleteGroupPermanently(activeChannelInfo.id)
                showGroupManageDialog = false
            },
            onLeaveGroup = {
                viewModel.leaveGroup(activeChannelInfo.id)
                showGroupManageDialog = false
            }
        )
    }

    // Diálogo de Permisos Denegados para Llamadas
    if (showPermissionDeniedDialog != null) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Permisos para Llamadas", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(showPermissionDeniedDialog ?: "")
            },
            confirmButton = {
                Button(
                    onClick = { showPermissionDeniedDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) {
                    Text("Entendido", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Modal de visualización ampliada de Multimedia (Fotos, Videos, GIFs)
    if (previewMediaUrl != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                previewMediaUrl = null
                previewMediaType = null
            },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0F172A),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(16.dp)
                    .testTag("dialog_media_preview")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (previewMediaType) {
                                "image" -> "📷 Imagen Ampliada"
                                "video" -> "🎥 Video en Reproducción"
                                "gif" -> "🎭 Animación GIF"
                                "sticker" -> "✨ Sticker Animado"
                                else -> "Multimedia"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        IconButton(
                            onClick = {
                                previewMediaUrl = null
                                previewMediaType = null
                            },
                            modifier = Modifier.testTag("btn_close_preview_x")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 380.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val safePreviewUrl = remember(previewMediaUrl) {
                                previewMediaUrl?.replace("http://", "https://")
                            }
                            AsyncImage(
                                model = safePreviewUrl,
                                contentDescription = "Vista previa multimedia",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 220.dp, max = 380.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (previewMediaType == "video") {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF4F46E5),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reproduciendo streaming en vivo", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Botón para cerrar la vista previa de la imagen
                    Button(
                        onClick = {
                            previewMediaUrl = null
                            previewMediaType = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_close_media_preview")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cerrar Vista Previa", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showChatNotificationPrefsDialog) {
        val currentPref = channelNotificationPrefs[currentChannel] ?: ChannelNotificationPreference(currentChannel)
        AlertDialog(
            onDismissRequest = { showChatNotificationPrefsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Preferencias de Notificación",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Ajusta las alertas para ${activeChannelInfo?.name ?: currentChannel}:",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )

                    // Switch Mensajes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Notificaciones de Mensajes", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Switch(
                            checked = currentPref.notifyMessages,
                            onCheckedChange = { viewModel.toggleChannelNotifyMessages(currentChannel) },
                            modifier = Modifier.testTag("switch_chat_notify_msg")
                        )
                    }

                    HorizontalDivider(color = Color(0xFF334155))

                    // Switch Llamadas de Voz
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Call, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Alertas de Llamadas de Voz", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Switch(
                            checked = currentPref.notifyVoiceCalls,
                            onCheckedChange = { viewModel.toggleChannelNotifyVoiceCalls(currentChannel) },
                            modifier = Modifier.testTag("switch_chat_notify_voice")
                        )
                    }

                    HorizontalDivider(color = Color(0xFF334155))

                    // Switch Videollamadas
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Alertas de Videollamadas", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Switch(
                            checked = currentPref.notifyVideoCalls,
                            onCheckedChange = { viewModel.toggleChannelNotifyVideoCalls(currentChannel) },
                            modifier = Modifier.testTag("switch_chat_notify_video")
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChatNotificationPrefsDialog = false }) {
                    Text("Guardar y Cerrar", fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                }
            },
            containerColor = Color(0xFF1E293B),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    isPlayingAudio: Boolean,
    isSearchMatch: Boolean = false,
    searchKeyword: String = "",
    onTogglePlayAudio: (Long) -> Unit,
    onReact: (String) -> Unit,
    onDelete: () -> Unit,
    onShowDeliveryStatus: (ChatMessage) -> Unit = {},
    onOpenAttachedDoc: (Long) -> Unit,
    onOpenAttachedAudio: (Long) -> Unit,
    onPreviewMedia: (url: String, type: String) -> Unit,
    onStartVoiceCall: (peerName: String) -> Unit,
    onStartVideoCall: (peerName: String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val gifImageLoader = remember(context) {
        coil.ImageLoader.Builder(context)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(coil.decode.ImageDecoderDecoder.Factory())
                } else {
                    add(coil.decode.GifDecoder.Factory())
                }
            }
            .build()
    }
    val timeFormatted = remember(message.timestamp) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    // Iniciales y color de avatar
    val initials = remember(message.senderName) {
        val parts = message.senderName.trim().split(" ")
        if (parts.size >= 2) "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
        else message.senderName.take(2).uppercase()
    }

    val avatarColor = remember(message.senderEmail) {
        when {
            message.senderEmail.contains("sofia") -> Color(0xFFEC4899)
            message.senderEmail.contains("carlos") -> Color(0xFF10B981)
            message.senderEmail.contains("alex") || message.senderEmail.contains("gonzalez") -> Color(0xFF6366F1)
            else -> Color(0xFF8B5CF6)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Avatar si no soy yo
        if (!isMe) {
            Surface(
                shape = CircleShape,
                color = avatarColor,
                modifier = Modifier
                    .size(32.dp)
                    .padding(top = 2.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            // Etiqueta visual si este mensaje coincide con la búsqueda
            if (isSearchMatch) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.25f),
                    modifier = Modifier.padding(bottom = 3.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Coincidencia: \"$searchKeyword\"",
                            color = Color(0xFFFDE68A),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Nombre del remitente si no soy yo
            if (!isMe) {
                Text(
                    text = message.senderName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = avatarColor,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }

            // Cuerpo del mensaje con borde dorado si coincide con la búsqueda
            val bubbleShape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 16.dp
            )

            Surface(
                shape = bubbleShape,
                color = if (isMe) Color(0xFF4F46E5) else Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isSearchMatch) Modifier.border(2.dp, Color(0xFFF59E0B), bubbleShape)
                        else Modifier
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    // Tarjeta de Documento adjunto
                    if (message.attachedDocId != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenAttachedDoc(message.attachedDocId) }
                                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0369A1),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Description,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = message.attachedDocTitle ?: "Documento",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text("Toca para abrir en Doc Editor", color = Color(0xFF38BDF8), fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // Tarjeta de Pista Musical adjunta con REPRODUCTOR INTEGRADO
                    if (message.attachedAudioId != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFA855F7).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Botón de reproducción en vivo directamente en el chat
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isPlayingAudio) Color(0xFFEF4444) else Color(0xFF9333EA),
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clickable { onTogglePlayAudio(message.attachedAudioId) }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isPlayingAudio) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlayingAudio) "Detener" else "Reproducir",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = message.attachedAudioTitle ?: "Pista Musical",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isPlayingAudio) "🔊 Reproduciendo beat en vivo..." else "Toca ▶ para escuchar en el chat",
                                            color = if (isPlayingAudio) Color(0xFF34D399) else Color(0xFF94A3B8),
                                            fontSize = 10.sp
                                        )
                                    }

                                    // Botón abrir en Music Studio
                                    TextButton(
                                        onClick = { onOpenAttachedAudio(message.attachedAudioId) },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Studio", fontSize = 11.sp, color = Color(0xFFA855F7), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Renderizado de FOTOS adjuntas
                    if (message.mediaType == "image" && !message.mediaUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPreviewMedia(message.mediaUrl, "image") }
                                .testTag("msg_image_${message.id}")
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                ) {
                                    AsyncImage(
                                        model = message.mediaUrl,
                                        contentDescription = "Foto compartida",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(bottomStart = 8.dp),
                                        color = Color.Black.copy(alpha = 0.65f),
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Text(
                                            text = "📷 Toca para ampliar",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                // Botón explícito e interactivo para abrir la imagen
                                Surface(
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Imagen adjunta",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                        TextButton(
                                            onClick = { onPreviewMedia(message.mediaUrl, "image") },
                                            modifier = Modifier.testTag("btn_view_full_image_${message.id}")
                                        ) {
                                            Text(
                                                "Ver imagen",
                                                color = Color(0xFF38BDF8),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Renderizado de VIDEOS adjuntos
                    if (message.mediaType == "video" && !message.mediaUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F172A))
                                .clickable { onPreviewMedia(message.mediaUrl, "video") }
                        ) {
                            if (!message.mediaThumbnail.isNullOrBlank()) {
                                AsyncImage(
                                    model = message.mediaThumbnail,
                                    contentDescription = "Video",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF1E293B), Color(0xFF312E81))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = Color(0xFFA5B4FC),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            // Botón de reproducción de video
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF4F46E5).copy(alpha = 0.9f),
                                modifier = Modifier
                                    .size(44.dp)
                                    .align(Alignment.Center)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Reproducir Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // Badge de Video
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF7C3AED),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = "VIDEO HD",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Renderizado de GIFs y Stickers animados
                    if ((message.mediaType == "gif" || message.mediaType == "sticker") && !message.mediaUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val safeMediaUrl = remember(message.mediaUrl) {
                            message.mediaUrl.replace("http://", "https://")
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPreviewMedia(safeMediaUrl, message.mediaType) }
                        ) {
                            AsyncImage(
                                model = safeMediaUrl,
                                contentDescription = if (message.mediaType == "gif") "GIF animado" else "Sticker animado",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (message.mediaType == "gif") Color(0xFFEF4444) else Color(0xFF10B981),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = if (message.mediaType == "gif") "GIF" else "STICKER",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Renderizado de Registro de LLAMADAS (Voz o Video)
                    if (message.mediaType == "call_voice" || message.mediaType == "call_video") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (message.mediaType == "call_video") Color(0xFF0284C7) else Color(0xFF059669),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (message.mediaType == "call_video") Icons.Default.Videocam else Icons.Default.Call,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (message.mediaType == "call_video") "Videollamada finalizada" else "Llamada de voz finalizada",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val durMin = message.callDurationSec / 60
                                    val durSec = message.callDurationSec % 60
                                    Text(
                                        text = "Duración: ${String.format("%02d:%02d", durMin, durSec)}",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        if (message.mediaType == "call_video") {
                                            onStartVideoCall(message.senderName)
                                        } else {
                                            onStartVoiceCall(message.senderName)
                                        }
                                    }
                                ) {
                                    Text("Llamar", fontSize = 11.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Renderizado de DOCUMENTOS (Archivos genéricos subidos)
                    if (message.mediaType == "document" && !message.mediaUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                                    intent.data = android.net.Uri.parse(message.mediaUrl)
                                    context.startActivity(intent)
                                }
                                .border(1.dp, Color(0xFF64748B).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF475569),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.AttachFile,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = message.text.takeIf { it.isNotBlank() && it != "📄 Archivo adjunto" } ?: "Archivo adjunto",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text("Toca para descargar/abrir", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Pie de mensaje: Hora y estado de entrega en Firestore ('enviado', 'entregado', 'visto')
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onShowDeliveryStatus(message) }
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timeFormatted,
                            fontSize = 10.sp,
                            color = if (isMe) Color(0xFFC7D2FE) else Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.width(4.dp))

                        // Indicador de entrega en tiempo real ('enviado', 'entregado', 'visto')
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.22f),
                            modifier = Modifier
                                .clickable { onShowDeliveryStatus(message) }
                                .testTag("msg_delivery_status_${message.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (message.deliveryStatus.lowercase()) {
                                    "enviando" -> {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = "Enviando",
                                            tint = if (isMe) Color(0xFFE2E8F0).copy(alpha = 0.7f) else Color(0xFF94A3B8),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Enviando", fontSize = 9.sp, color = if (isMe) Color(0xFFC7D2FE) else Color(0xFF94A3B8))
                                    }
                                    "enviado" -> {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Enviado a Firestore",
                                            tint = if (isMe) Color(0xFFE2E8F0) else Color(0xFF94A3B8),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Enviado", fontSize = 9.sp, color = if (isMe) Color(0xFFC7D2FE) else Color(0xFF94A3B8))
                                    }
                                    "entregado" -> {
                                        Icon(
                                            imageVector = Icons.Default.DoneAll,
                                            contentDescription = "Entregado a destinatarios",
                                            tint = if (isMe) Color(0xFFE2E8F0) else Color(0xFF94A3B8),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Entregado", fontSize = 9.sp, color = if (isMe) Color(0xFFC7D2FE) else Color(0xFF94A3B8))
                                    }
                                    "visto" -> {
                                        Icon(
                                            imageVector = Icons.Default.DoneAll,
                                            contentDescription = "Visto por destinatarios",
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Visto", fontSize = 9.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = Icons.Default.DoneAll,
                                            contentDescription = "Sincronizado",
                                            tint = if (isMe) Color(0xFF86EFAC) else Color(0xFF34D399),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Sincronizado", fontSize = 9.sp, color = if (isMe) Color(0xFF86EFAC) else Color(0xFF34D399))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Reacciones y opciones debajo de la burbuja
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
            ) {
                // Reacciones acumuladas
                if (message.reactions.isNotEmpty()) {
                    val tags = message.reactions.split(",").filter { it.isNotBlank() }
                    tags.distinct().forEach { emoji ->
                        val count = tags.count { it == emoji }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF334155),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clickable { onReact(emoji) }
                        ) {
                            Text(
                                text = if (count > 1) "$emoji $count" else emoji,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Selector rápido de emojis
                Text(
                    text = "+👍",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier
                        .clickable { onReact("👍") }
                        .padding(horizontal = 3.dp)
                )
                Text(
                    text = "+❤️",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier
                        .clickable { onReact("❤️") }
                        .padding(horizontal = 3.dp)
                )
                Text(
                    text = "+🔥",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier
                        .clickable { onReact("🔥") }
                        .padding(horizontal = 3.dp)
                )

                // Botón para eliminar mi mensaje
                if (isMe) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar mensaje",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        // Avatar si soy yo
        if (isMe) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = avatarColor,
                modifier = Modifier
                    .size(32.dp)
                    .padding(top = 2.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageDeliveryStatusDialog(
    message: ChatMessage,
    onDismiss: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }
    val timeSdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val sentFormatted = remember(message.sentTimestamp, message.timestamp) {
        val t = if (message.sentTimestamp > 0) message.sentTimestamp else message.timestamp
        sdf.format(Date(t))
    }

    val deliveredFormatted = remember(message.deliveredTimestamp) {
        if (message.deliveredTimestamp > 0) timeSdf.format(Date(message.deliveredTimestamp))
        else "Confirmado en red Firestore"
    }

    val seenFormatted = remember(message.seenTimestamp, message.deliveryStatus) {
        if (message.seenTimestamp > 0) timeSdf.format(Date(message.seenTimestamp))
        else if (message.deliveryStatus == "visto") "Leído"
        else "Pendiente de lectura"
    }

    val isSeen = message.deliveryStatus.lowercase() == "visto"
    val isDelivered = isSeen || message.deliveryStatus.lowercase() == "entregado"
    val isSent = isDelivered || message.deliveryStatus.lowercase() == "enviado"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Estado de Entrega (Firestore)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Estado General Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (message.deliveryStatus.lowercase()) {
                        "visto" -> Color(0xFF0369A1).copy(alpha = 0.25f)
                        "entregado" -> Color(0xFF334155)
                        "enviado" -> Color(0xFF1E293B)
                        else -> Color(0xFF1E293B)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            when (message.deliveryStatus.lowercase()) {
                                "visto" -> Color(0xFF38BDF8)
                                "entregado" -> Color(0xFF64748B)
                                else -> Color(0xFF475569)
                            },
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (message.deliveryStatus.lowercase()) {
                            "visto" -> {
                                Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Visto por destinatarios", fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), fontSize = 13.sp)
                                    Text("Leído y confirmado en tiempo real por el equipo", fontSize = 11.sp, color = Color(0xFFBAE6FD))
                                }
                            }
                            "entregado" -> {
                                Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Entregado a destinatarios", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                    Text("Mensaje transferido y cacheado en los clientes", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                            }
                            "enviado" -> {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Enviado al servidor", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                    Text("Almacenado con éxito en Firebase Firestore", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                            }
                            else -> {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Enviando...", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                    Text("Esperando confirmación de escritura en Firestore", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mensaje inspeccionado
                Text("Contenido:", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message.text.ifBlank { "[Archivo adjunto: ${message.mediaType.ifBlank { "multimedia" }}]" },
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(8.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Línea de Tiempo de Estados en Firestore
                Text("Línea de tiempo de entrega:", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                // 1. Enviado
                TimelineItem(
                    title = "1. Enviado a Firestore",
                    statusText = sentFormatted,
                    isActive = isSent,
                    isHighlight = false,
                    icon = Icons.Default.Check
                )

                // 2. Entregado
                TimelineItem(
                    title = "2. Entregado a dispositivos",
                    statusText = deliveredFormatted,
                    isActive = isDelivered,
                    isHighlight = false,
                    icon = Icons.Default.DoneAll
                )

                // 3. Visto
                TimelineItem(
                    title = "3. Visto por los miembros",
                    statusText = if (isSeen) "$seenFormatted ${if (message.seenBy.isNotBlank()) "(${message.seenBy})" else ""}" else "Pendiente",
                    isActive = isSeen,
                    isHighlight = isSeen,
                    icon = Icons.Default.DoneAll
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Metadatos Técnicos de Firestore
                Text("Metadatos de Firebase Firestore:", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text("• Colección: chat_channels/${message.channelId}/messages", fontSize = 10.sp, color = Color(0xFFCBD5E1), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text("• Document ID: ${message.firestoreId.ifBlank { "local_${message.id}" }}", fontSize = 10.sp, color = Color(0xFFCBD5E1), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text("• Remitente: ${message.senderName} <${message.senderEmail}>", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    if (message.seenBy.isNotBlank()) {
                        Text("• Visto por: ${message.seenBy}", fontSize = 10.sp, color = Color(0xFF38BDF8))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Entendido", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun TimelineItem(
    title: String,
    statusText: String,
    isActive: Boolean,
    isHighlight: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                isHighlight -> Color(0xFF38BDF8)
                isActive -> Color(0xFF34D399)
                else -> Color(0xFF64748B)
            },
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isActive) Color.White else Color(0xFF64748B)
            )
            Text(
                text = statusText,
                fontSize = 10.sp,
                color = if (isHighlight) Color(0xFF38BDF8) else if (isActive) Color(0xFFCBD5E1) else Color(0xFF64748B)
            )
        }
    }
}

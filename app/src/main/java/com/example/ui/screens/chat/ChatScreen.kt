package com.example.ui.screens.chat

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firebase.FirestoreConnectionStatus
import com.example.data.model.AudioProject
import com.example.data.model.ChatMessage
import com.example.data.model.DocumentItem
import com.example.ui.viewmodel.OmniViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val channels = viewModel.availableChannels

    val listState = rememberLazyListState()
    var showAttachDialog by remember { mutableStateOf(false) }
    var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }

    // Scroll al último mensaje cuando cambia la cantidad
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val activeChannelInfo = channels.firstOrNull { it.id == currentChannel }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = Color(0xFF0F172A),
        topBar = {
            Surface(
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Header superior con información del canal y estado de Firestore
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("btn_chat_back")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Atrás",
                                tint = Color.White
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF334155),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = activeChannelInfo?.iconEmoji ?: "#",
                                    fontSize = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = activeChannelInfo?.name ?: currentChannel,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
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
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
                                            modifier = Modifier.size(8.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (firestoreStatus == FirestoreConnectionStatus.CONNECTED_REALTIME) "Firestore En Vivo" else firestoreStatus.label,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            Text(
                                text = activeChannelInfo?.description ?: "Mensajería en tiempo real con Firebase Firestore",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Contador de colaboradores activos
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF334155),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = "En línea",
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${maxOf(onlineUsers.size, 1)} online",
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Selector horizontal de canales y chats directos
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(channels) { ch ->
                            val isSelected = ch.id == currentChannel
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) Color(0xFF4F46E5) else Color(0xFF334155),
                                modifier = Modifier
                                    .clickable { viewModel.loadChannelMessages(ch.id) }
                                    .testTag("channel_tab_${ch.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = ch.iconEmoji, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = ch.name,
                                        color = Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
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

                // Barra de entrada de texto
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showAttachDialog = true },
                        modifier = Modifier.testTag("btn_attach_cloud_file")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Adjuntar archivo",
                            tint = Color(0xFF818CF8)
                        )
                    }

                    OutlinedTextField(
                        value = chatInput,
                        onValueChange = { viewModel.onChatInputChanged(it) },
                        placeholder = {
                            Text(
                                "Escribe en #${activeChannelInfo?.name ?: currentChannel}...",
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
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.sendChatMessage() }
                            .testTag("btn_send_chat"),
                        color = Color(0xFF4F46E5)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (messages.isEmpty()) {
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
                items(messages, key = { it.firestoreId.ifBlank { it.id.toString() } }) { msg ->
                    val isMe = msg.senderEmail == (authState.currentUser?.email ?: "gonzalez24029@gmail.com")
                    MessageBubble(
                        message = msg,
                        isMe = isMe,
                        isPlayingAudio = playingAudioId != null && playingAudioId == msg.attachedAudioId,
                        onTogglePlayAudio = { audioId -> viewModel.togglePlayChatAudio(audioId) },
                        onReact = { emoji -> viewModel.addReactionToMessage(msg, emoji) },
                        onDelete = { messageToDelete = msg },
                        onOpenAttachedDoc = { docId ->
                            val doc = allDocs.firstOrNull { it.id == docId }
                            if (doc != null) onOpenDoc(doc)
                        },
                        onOpenAttachedAudio = { audioId ->
                            val audio = allAudio.firstOrNull { it.id == audioId }
                            if (audio != null) onOpenAudio(audio)
                        }
                    )
                }
            }
        }
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

    // Modal para adjuntar archivo
    if (showAttachDialog) {
        AlertDialog(
            onDismissRequest = { showAttachDialog = false },
            title = { Text("Compartir en #${activeChannelInfo?.name ?: currentChannel}", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Selecciona un documento o pista musical para publicar en tiempo real:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Documentos recientes:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    if (allDocs.isEmpty()) {
                        Text("No hay documentos aún.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        allDocs.take(3).forEach { doc ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        viewModel.sendChatMessage(attachedDoc = doc)
                                        showAttachDialog = false
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF38BDF8))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(doc.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                        Text("${doc.currentFormat.displayName} • ${doc.docType}", fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Pistas musicales creadas:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    if (allAudio.isEmpty()) {
                        Text("No hay pistas creadas aún.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        allAudio.take(3).forEach { audio ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        viewModel.sendChatMessage(attachedAudio = audio)
                                        showAttachDialog = false
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFFA855F7))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(audio.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                        Text("${audio.genre} • ${audio.bpm} BPM", fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAttachDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    isPlayingAudio: Boolean,
    onTogglePlayAudio: (Long) -> Unit,
    onReact: (String) -> Unit,
    onDelete: () -> Unit,
    onOpenAttachedDoc: (Long) -> Unit,
    onOpenAttachedAudio: (Long) -> Unit
) {
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

            // Cuerpo del mensaje
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 4.dp,
                    bottomEnd = if (isMe) 4.dp else 16.dp
                ),
                color = if (isMe) Color(0xFF4F46E5) else Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
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

                    Spacer(modifier = Modifier.height(6.dp))

                    // Pie de mensaje: Hora y estado de sincronización en tiempo real
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timeFormatted,
                            fontSize = 10.sp,
                            color = if (isMe) Color(0xFFC7D2FE) else Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Sincronizado en Firestore",
                            tint = if (isMe) Color(0xFF86EFAC) else Color(0xFF34D399),
                            modifier = Modifier.size(13.dp)
                        )
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

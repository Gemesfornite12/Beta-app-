package com.example.ui.screens.chat

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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val listState = rememberLazyListState()

    var showAttachDialog by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val channels = listOf("general", "musica-colab", "revision-docs")

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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("btn_chat_back")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Atrás",
                                tint = Color.White
                            )
                        }

                        Icon(Icons.Default.Tag, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentChannel,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "En vivo • 4 colaboradores conectados",
                                color = Color(0xFF34D399),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Channels Bar
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(channels) { ch ->
                            val isSelected = ch == currentChannel
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) Color(0xFF4F46E5) else Color(0xFF334155),
                                modifier = Modifier
                                    .clickable { viewModel.loadChannelMessages(ch) }
                                    .testTag("channel_tab_$ch")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("#", color = if (isSelected) Color.White else Color(0xFF94A3B8), fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = ch,
                                        color = Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Chat Input Bar
            Surface(
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
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
                        placeholder = { Text("Escribe un mensaje...", color = Color(0xFF64748B), fontSize = 14.sp) },
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { msg ->
                val isMe = msg.senderEmail == (authState.currentUser?.email ?: "gonzalez24029@gmail.com")
                MessageBubble(
                    message = msg,
                    isMe = isMe,
                    onReact = { emoji -> viewModel.addReactionToMessage(msg, emoji) },
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

    // Attach File Modal
    if (showAttachDialog) {
        AlertDialog(
            onDismissRequest = { showAttachDialog = false },
            title = { Text("Compartir en #${currentChannel}", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Selecciona un documento o beat musical para compartir en tiempo real:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Documentos recientes:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
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
                                Text(doc.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Pistas musicales:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    allAudio.take(2).forEach { audio ->
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
                                Text(audio.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    onReact: (String) -> Unit,
    onOpenAttachedDoc: (Long) -> Unit,
    onOpenAttachedAudio: (Long) -> Unit
) {
    val timeFormatted = remember(message.timestamp) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        // Sender Name if not me
        if (!isMe) {
            Text(
                text = message.senderName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }

        // Message Surface
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 16.dp
            ),
            color = if (isMe) Color(0xFF4F46E5) else Color(0xFF1E293B),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                // Attached Doc Card if present
                if (message.attachedDocId != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAttachedDoc(message.attachedDocId) }
                            .padding(2.dp)
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.attachedDocTitle ?: "Documento",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text("Toca para abrir en Editor", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            }
                        }
                    }
                }

                // Attached Audio Card if present
                if (message.attachedAudioId != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAttachedAudio(message.attachedAudioId) }
                            .padding(2.dp)
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.attachedAudioTitle ?: "Pista Musical",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text("Toca para reproducir en Studio", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = timeFormatted,
                        fontSize = 10.sp,
                        color = if (isMe) Color(0xFFC7D2FE) else Color(0xFF64748B)
                    )
                }
            }
        }

        // Reactions and Quick Emoji Picker
        Row(
            modifier = Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (message.reactions.isNotEmpty()) {
                val tags = message.reactions.split(",")
                tags.forEach { r ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF334155),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = r,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Quick emoji tap to add reaction
            Text(
                text = "+🔥",
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                modifier = Modifier
                    .clickable { onReact("🔥") }
                    .padding(horizontal = 4.dp)
            )
            Text(
                text = "+❤️",
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                modifier = Modifier
                    .clickable { onReact("❤️") }
                    .padding(horizontal = 4.dp)
            )
        }
    }
}

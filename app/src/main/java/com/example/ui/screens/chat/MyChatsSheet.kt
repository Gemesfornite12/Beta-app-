package com.example.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.firebase.ChannelInfo

@Composable
fun MyChatsAndGroupsSheet(
    channels: List<ChannelInfo>,
    currentChannelId: String,
    archivedChannelIds: Set<String>,
    currentUserEmail: String,
    onSelectChannel: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onStartDirectChat: () -> Unit,
    onManageGroup: (ChannelInfo) -> Unit,
    onToggleArchive: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0: Todos, 1: Grupos, 2: Directos, 3: Canales, 4: Archivados
    var showGuide by remember { mutableStateOf(true) }

    val activeChannels = remember(channels, archivedChannelIds) {
        channels.filter { !archivedChannelIds.contains(it.id) }
    }
    val archivedChannels = remember(channels, archivedChannelIds) {
        channels.filter { archivedChannelIds.contains(it.id) }
    }

    val groups = remember(activeChannels) { activeChannels.filter { it.isGroup } }
    val directChats = remember(activeChannels) { activeChannels.filter { it.isDirect } }
    val publicChannels = remember(activeChannels) { activeChannels.filter { !it.isGroup && !it.isDirect } }

    val displayedList = remember(selectedTab, activeChannels, archivedChannels, groups, directChats, publicChannels, searchQuery) {
        val baseList = when (selectedTab) {
            1 -> groups
            2 -> directChats
            3 -> publicChannels
            4 -> archivedChannels
            else -> activeChannels
        }
        if (searchQuery.isBlank()) baseList
        else baseList.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true) ||
                    it.id.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .testTag("dialog_my_chats_sheet"),
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(24.dp),
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
            ) {
                // Header superior con título y botón de cierre
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF7C3AED).copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Forum,
                                    contentDescription = null,
                                    tint = Color(0xFFA855F7),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Mis Chats y Grupos",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "${channels.size} conversaciones disponibles",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_my_chats")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Campo de búsqueda rápida
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar chat, grupo o participante...", color = Color(0xFF64748B), fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFF94A3B8))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_search_my_chats"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E293B),
                        unfocusedContainerColor = Color(0xFF1E293B),
                        focusedBorderColor = Color(0xFF7C3AED),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Tarjeta interactiva: Guía de Primeros Pasos en Grupos
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E293B),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showGuide = !showGuide },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "🚀 Primeros Pasos: Grupos y Canales",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE0F2FE),
                                    fontSize = 13.sp
                                )
                            }
                            Icon(
                                imageVector = if (showGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8)
                            )
                        }

                        AnimatedVisibility(
                            visible = showGuide,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                GuideItem(
                                    step = "1",
                                    title = "Crea un Grupo personalizado",
                                    description = "Toca en '+ Crear Grupo', sube una imagen de perfil, asigna un título y elige participantes."
                                )
                                GuideItem(
                                    step = "2",
                                    title = "Gestión de Permisos de Miembros",
                                    description = "Define quién puede enviar mensajes de texto, fotos, notas de voz o archivos en el grupo."
                                )
                                GuideItem(
                                    step = "3",
                                    title = "Modo Offline y Persistencia Local",
                                    description = "Tus mensajes y archivos multimedia se guardan localmente y se envían solos cuando vuelva la señal."
                                )
                                GuideItem(
                                    step = "4",
                                    title = "Videollamadas y Archivo",
                                    description = "Realiza llamadas de voz/video grupales o archiva conversaciones inactivas para mantener tu lista limpia."
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Acciones Rápidas: Crear Grupo / Nuevo Chat Privado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onDismiss()
                            onCreateGroup()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_action_create_group"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                    ) {
                        Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Crear Grupo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            onStartDirectChat()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_action_start_direct"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chat Privado", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Barra de Pestañas de Filtrado
                val tabs = listOf(
                    "Todos (${activeChannels.size})",
                    "👥 Grupos (${groups.size})",
                    "💬 Directos (${directChats.size})",
                    "📢 Canales (${publicChannels.size})",
                    "📥 Archivados (${archivedChannels.size})"
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(tabs.size) { index ->
                        val isSelected = selectedTab == index
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E293B),
                            modifier = Modifier
                                .clickable { selectedTab = index }
                                .testTag("tab_chats_$index")
                        ) {
                            Text(
                                text = tabs[index],
                                color = if (isSelected) Color(0xFF0F172A) else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Lista de Conversaciones y Grupos
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (displayedList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 30.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Forum,
                                        contentDescription = null,
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No se encontraron conversaciones",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    } else {
                        items(displayedList) { channel ->
                            val isCurrent = channel.id == currentChannelId
                            val isArchived = archivedChannelIds.contains(channel.id)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectChannel(channel.id)
                                        onDismiss()
                                    }
                                    .testTag("item_channel_${channel.id}"),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCurrent) Color(0xFF1E293B) else Color(0xFF020617)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isCurrent) Color(0xFF7C3AED) else Color(0xFF1E293B)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Avatar
                                    Surface(
                                        shape = CircleShape,
                                        color = when {
                                            channel.isGroup -> Color(0xFF7C3AED)
                                            channel.isDirect -> Color(0xFF0284C7)
                                            else -> Color(0xFF334155)
                                        },
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (channel.groupPhotoUrl.isNotBlank()) {
                                                AsyncImage(
                                                    model = channel.groupPhotoUrl,
                                                    contentDescription = channel.name,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Text(
                                                    text = channel.iconEmoji.ifBlank { if (channel.isGroup) "👥" else if (channel.isDirect) "👤" else "#" },
                                                    fontSize = 18.sp
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Información de Nombre y Estado
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = channel.name,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))

                                            // Badge según tipo
                                            if (channel.isGroup) {
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
                                            } else if (channel.isDirect) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFF0284C7).copy(alpha = 0.3f)
                                                ) {
                                                    Text(
                                                        text = "Privado",
                                                        color = Color(0xFF38BDF8),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text(
                                            text = if (channel.isGroup) {
                                                "${channel.members.size} participantes • ${channel.description}"
                                            } else {
                                                channel.description
                                            },
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Botones de Acción Secundaria
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Botón de Configuración si es Grupo
                                        if (channel.isGroup) {
                                            IconButton(
                                                onClick = {
                                                    onDismiss()
                                                    onSelectChannel(channel.id)
                                                    onManageGroup(channel)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Settings,
                                                    contentDescription = "Gestionar Grupo",
                                                    tint = Color(0xFFA855F7),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        // Botón de Archivar
                                        IconButton(
                                            onClick = { onToggleArchive(channel.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                                contentDescription = if (isArchived) "Desarchivar" else "Archivar",
                                                tint = if (isArchived) Color(0xFFFBBF24) else Color(0xFF64748B),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun GuideItem(
    step: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF0284C7),
            modifier = Modifier.size(18.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = step, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text(text = description, color = Color(0xFF94A3B8), fontSize = 10.sp)
        }
    }
}

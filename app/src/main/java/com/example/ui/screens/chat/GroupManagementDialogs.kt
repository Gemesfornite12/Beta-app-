package com.example.ui.screens.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.firebase.ChannelInfo
import com.example.data.firebase.GroupMember
import com.example.data.model.UserAccount

/**
 * Diálogo para crear un nuevo Grupo con nombre, foto/avatar y selección inicial de usuarios.
 */
@Composable
fun CreateGroupDialog(
    allUsers: List<UserAccount>,
    currentUserEmail: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, photoUrl: String, memberEmails: List<String>) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    var groupPhotoUrl by remember { mutableStateOf("") }
    var selectedMemberEmails by remember { mutableStateOf(setOf<String>()) }
    var userSearchQuery by remember { mutableStateOf("") }

    val createGroupPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            groupPhotoUrl = uri.toString()
        }
    }

    val presetPhotos = listOf(
        "https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=150",
        "https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?w=150",
        "https://images.unsplash.com/photo-1556761175-5973dc0f32e7?w=150",
        "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=150"
    )

    val otherUsers = remember(allUsers, currentUserEmail, userSearchQuery) {
        allUsers.filter { it.email != currentUserEmail }
            .filter {
                val name = it.displayName.ifBlank { it.username }
                if (userSearchQuery.isBlank()) true
                else name.contains(userSearchQuery, ignoreCase = true) ||
                        it.email.contains(userSearchQuery, ignoreCase = true)
            }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E293B),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 680.dp)
                .padding(16.dp)
                .testTag("dialog_create_group")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Título
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF7C3AED),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Group, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Crear Grupo",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        // Nombre del grupo
                        OutlinedTextField(
                            value = groupName,
                            onValueChange = { groupName = it },
                            label = { Text("Nombre del Grupo *") },
                            placeholder = { Text("Ej: Proyecto Alfa, Diseño UX...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_group_name"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF7C3AED),
                                unfocusedBorderColor = Color(0xFF475569)
                            )
                        )
                    }

                    item {
                        // Descripción
                        OutlinedTextField(
                            value = groupDescription,
                            onValueChange = { groupDescription = it },
                            label = { Text("Descripción (opcional)") },
                            placeholder = { Text("Tema o propósito del grupo...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_group_description"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF7C3AED),
                                unfocusedBorderColor = Color(0xFF475569)
                            ),
                            maxLines = 2
                        )
                    }

                    item {
                        // Foto del grupo
                        Text("Foto o Avatar del Grupo:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            presetPhotos.forEach { url ->
                                val isSelected = groupPhotoUrl == url
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF334155),
                                    modifier = Modifier
                                        .size(44.dp)
                                        .border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = if (isSelected) Color(0xFF38BDF8) else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { groupPhotoUrl = url }
                                ) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = "Foto preset",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = groupPhotoUrl,
                            onValueChange = { groupPhotoUrl = it },
                            label = { Text("URL de foto personalizada") },
                            placeholder = { Text("https://...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_group_photo_url"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF7C3AED),
                                unfocusedBorderColor = Color(0xFF475569)
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                createGroupPhotoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("btn_create_group_pick_local_photo")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Elegir foto del dispositivo / Galería", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    item {
                        // Selección de integrantes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Integrantes (${selectedMemberEmails.size} seleccionados):",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = userSearchQuery,
                            onValueChange = { userSearchQuery = it },
                            placeholder = { Text("Buscar compañeros por nombre...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF7C3AED),
                                unfocusedBorderColor = Color(0xFF334155)
                            )
                        )
                    }

                    items(otherUsers) { user ->
                        val isChecked = selectedMemberEmails.contains(user.email)
                        val uName = user.displayName.ifBlank { user.username }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isChecked) Color(0xFF312E81) else Color(0xFF0F172A),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedMemberEmails = if (isChecked) {
                                        selectedMemberEmails - user.email
                                    } else {
                                        selectedMemberEmails + user.email
                                    }
                                }
                                .testTag("checkbox_member_${user.email}")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF6366F1),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = uName.take(2).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = uName, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text(text = user.email, color = Color(0xFF94A3B8), fontSize = 11.sp)
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedMemberEmails = if (checked) {
                                            selectedMemberEmails + user.email
                                        } else {
                                            selectedMemberEmails - user.email
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF7C3AED),
                                        checkmarkColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = Color(0xFF94A3B8))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (groupName.isNotBlank()) {
                                onConfirm(
                                    groupName.trim(),
                                    groupDescription.trim(),
                                    groupPhotoUrl.trim(),
                                    selectedMemberEmails.toList()
                                )
                            }
                        },
                        enabled = groupName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        modifier = Modifier.testTag("btn_confirm_create_group")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Crear Grupo", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Diálogo para iniciar Chat Privado directo con un usuario del equipo.
 */
@Composable
fun StartDirectChatDialog(
    viewModel: OmniViewModel,
    currentUserEmail: String,
    onDismiss: () -> Unit,
    onSelectUser: (userEmail: String, userName: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val allUsers by viewModel.allUsers.collectAsState()
    val searchResults by viewModel.userSearchResults.collectAsState()
    val isSearchingUsers by viewModel.isSearchingUsers.collectAsState()

    // Efecto para buscar globalmente cuando cambia el query
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            kotlinx.coroutines.delay(500) // Debounce
            viewModel.searchUsersGlobally(searchQuery)
        } else {
            viewModel.clearUserSearchResults()
        }
    }

    val contacts = remember(allUsers, searchResults, currentUserEmail, searchQuery) {
        if (searchQuery.isBlank()) {
            allUsers.filter { it.email != currentUserEmail }.take(10)
        } else {
            // Combinamos locales y resultados de busqueda global
            val filteredLocal = allUsers.filter { it.email != currentUserEmail }
                .filter {
                    val name = it.displayName.ifBlank { it.username }
                    name.contains(searchQuery, ignoreCase = true) ||
                            it.email.contains(searchQuery, ignoreCase = true)
                }
            
            val merged = (filteredLocal + searchResults).distinctBy { it.email }.filter { it.email != currentUserEmail }
            merged
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E293B),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("dialog_start_direct_chat")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0284C7),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Nuevo Chat Privado",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Email o nombre de usuario...", color = Color(0xFF64748B)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                    trailingIcon = {
                        if (isSearchingUsers) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFF0284C7),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_search_direct_chat_user"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF0284C7),
                        unfocusedBorderColor = Color(0xFF334155)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (searchQuery.isBlank()) "Sugerencias:" else "Resultados de búsqueda:",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (contacts.isEmpty() && !isSearchingUsers) {
                        item {
                            Text(
                                "No se encontraron usuarios para \"$searchQuery\".",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }

                    items(contacts) { user ->
                        val uName = user.displayName.ifBlank { user.username }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectUser(user.email, uName) }
                                .testTag("btn_select_user_direct_${user.email}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF0284C7),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = uName.take(2).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = uName,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = user.email,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0284C7).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "Chatear",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Diálogo de Administración de Grupo:
 * - El dueño puede agregar personas, quitar usuarios, otorgar/revocar permisos (enviar mensajes, enviar multimedia),
 *   deshacer cambios de permisos en cualquier momento, y programar borrado con advertencia de 3 minutos.
 * - Los miembros pueden salir del grupo en cualquier momento.
 */
@Composable
fun GroupManageDialog(
    channel: ChannelInfo,
    currentUserEmail: String,
    allUsers: List<UserAccount>,
    deletionCountdown: Int?,
    onDismiss: () -> Unit,
    onAddMember: (email: String) -> Unit,
    onRemoveMember: (email: String) -> Unit,
    onUpdatePermissions: (email: String, canSendMessages: Boolean, canSendMedia: Boolean) -> Unit,
    onResetPermissions: (email: String) -> Unit,
    onUpdateGroupInfo: (newName: String, newDescription: String, newPhotoUrl: String) -> Unit = { _, _, _ -> },
    onScheduleDeletion: () -> Unit,
    onCancelDeletion: () -> Unit,
    onDeletePermanently: () -> Unit,
    onLeaveGroup: () -> Unit
) {
    val isOwner = channel.creatorEmail == currentUserEmail
    var showAddMemberSection by remember { mutableStateOf(false) }
    var isEditingGroupInfo by remember { mutableStateOf(false) }
    var editGroupName by remember(channel.name) { mutableStateOf(channel.name) }
    var editGroupDescription by remember(channel.description) { mutableStateOf(channel.description) }
    var editGroupPhotoUrl by remember(channel.groupPhotoUrl) { mutableStateOf(channel.groupPhotoUrl) }
    var infoSavedFeedback by remember { mutableStateOf(false) }
    var confirmDeleteGroupDialog by remember { mutableStateOf(false) }
    var confirmLeaveGroupDialog by remember { mutableStateOf(false) }

    val editGroupPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            editGroupPhotoUrl = uri.toString()
        }
    }

    val presetGroupPhotos = listOf(
        "https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=150",
        "https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?w=150",
        "https://images.unsplash.com/photo-1556761175-5973dc0f32e7?w=150",
        "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=150",
        "https://images.unsplash.com/photo-1531482615713-2afd69097998?w=150"
    )

    val nonMemberUsers = remember(allUsers, channel.members) {
        val currentMemberEmails = channel.members.map { it.email }.toSet()
        allUsers.filter { it.email !in currentMemberEmails }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E293B),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 700.dp)
                .padding(16.dp)
                .testTag("dialog_manage_group")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF7C3AED),
                            modifier = Modifier.size(40.dp)
                        ) {
                            val displayPhoto = if (isEditingGroupInfo && editGroupPhotoUrl.isNotBlank()) editGroupPhotoUrl else channel.groupPhotoUrl
                            if (displayPhoto.isNotBlank()) {
                                AsyncImage(
                                    model = displayPhoto,
                                    contentDescription = channel.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = channel.iconEmoji, fontSize = 18.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = channel.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isOwner) "Eres el Dueño del Grupo" else "Miembro del Grupo",
                                color = if (isOwner) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sección para Editar Nombre y Foto del Grupo (Dueño del grupo)
                if (isOwner) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Editar Nombre y Foto del Grupo",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = { isEditingGroupInfo = !isEditingGroupInfo },
                                    modifier = Modifier.testTag("btn_toggle_edit_group_info")
                                ) {
                                    Text(
                                        text = if (isEditingGroupInfo) "Ocultar" else "Editar",
                                        color = Color(0xFF818CF8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            AnimatedVisibility(visible = isEditingGroupInfo) {
                                Column(modifier = Modifier.padding(top = 6.dp)) {
                                    OutlinedTextField(
                                        value = editGroupName,
                                        onValueChange = { editGroupName = it },
                                        label = { Text("Nombre del grupo") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("input_edit_group_name"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFF7C3AED),
                                            unfocusedBorderColor = Color(0xFF334155),
                                            focusedLabelColor = Color(0xFFC4B5FD),
                                            unfocusedLabelColor = Color(0xFF94A3B8)
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = editGroupDescription,
                                        onValueChange = { editGroupDescription = it },
                                        label = { Text("Descripción") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("input_edit_group_desc"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFF7C3AED),
                                            unfocusedBorderColor = Color(0xFF334155),
                                            focusedLabelColor = Color(0xFFC4B5FD),
                                            unfocusedLabelColor = Color(0xFF94A3B8)
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = editGroupPhotoUrl,
                                        onValueChange = { editGroupPhotoUrl = it },
                                        label = { Text("URL de la foto del grupo") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("input_edit_group_photo"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFF7C3AED),
                                            unfocusedBorderColor = Color(0xFF334155),
                                            focusedLabelColor = Color(0xFFC4B5FD),
                                            unfocusedLabelColor = Color(0xFF94A3B8)
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = {
                                            editGroupPhotoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(38.dp)
                                            .testTag("btn_edit_group_pick_local_photo")
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Elegir foto del dispositivo / Galería", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text("O elige una foto predefinida:", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        presetGroupPhotos.forEach { url ->
                                            val isSelected = editGroupPhotoUrl == url
                                            Surface(
                                                shape = CircleShape,
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .border(
                                                        2.dp,
                                                        if (isSelected) Color(0xFF7C3AED) else Color.Transparent,
                                                        CircleShape
                                                    )
                                                    .clickable { editGroupPhotoUrl = url }
                                            ) {
                                                AsyncImage(
                                                    model = url,
                                                    contentDescription = "Avatar grupo",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            if (editGroupName.isNotBlank()) {
                                                onUpdateGroupInfo(editGroupName.trim(), editGroupDescription.trim(), editGroupPhotoUrl.trim())
                                                infoSavedFeedback = true
                                            }
                                        },
                                        enabled = editGroupName.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("btn_save_group_info")
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (infoSavedFeedback) "¡Guardado en Firestore!" else "Guardar Cambios del Grupo", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Banner si el grupo está en cuenta regresiva de eliminación de 3 minutos
                if (channel.isDeleting) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF7F1D1D),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("banner_deletion_status_in_dialog")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                val secs = deletionCountdown ?: 180
                                val timeStr = String.format("%02d:%02d", secs / 60, secs % 60)
                                Text(
                                    text = "Eliminación en progreso: $timeStr",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "El grupo y todos sus mensajes se eliminarán permanentemente tras 3 minutos.",
                                color = Color(0xFFFECACA),
                                fontSize = 11.sp
                            )
                            if (isOwner) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = onCancelDeletion,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                        modifier = Modifier.testTag("btn_cancel_delete_group_dialog")
                                    ) {
                                        Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Deshacer y Cancelar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = onDeletePermanently,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
                                        modifier = Modifier.testTag("btn_delete_group_immediately")
                                    ) {
                                        Text("Borrar ya", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Lista de Miembros y Permisos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Miembros (${channel.members.size})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    if (isOwner) {
                        TextButton(
                            onClick = { showAddMemberSection = !showAddMemberSection },
                            modifier = Modifier.testTag("btn_toggle_add_member_section")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (showAddMemberSection) "Ocultar" else "+ Agregar personas", color = Color(0xFF38BDF8), fontSize = 12.sp)
                        }
                    }
                }

                // Sección para agregar miembros
                AnimatedVisibility(visible = showAddMemberSection && isOwner) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Personas que puedes agregar a este grupo:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            if (nonMemberUsers.isEmpty()) {
                                Text("Todos los usuarios registrados ya forman parte del grupo.", color = Color(0xFF64748B), fontSize = 11.sp)
                            } else {
                                LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                                    items(nonMemberUsers) { user ->
                                        val uName = user.displayName.ifBlank { user.username }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(uName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                Text(user.email, color = Color(0xFF94A3B8), fontSize = 10.sp)
                                            }
                                            Button(
                                                onClick = { onAddMember(user.email) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                                modifier = Modifier.testTag("btn_add_user_${user.email}")
                                            ) {
                                                Text("Añadir", fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Lista de integrantes actuales con gestión de permisos
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(channel.members) { member ->
                        val isMemberOwner = member.role == "owner" || member.email == channel.creatorEmail
                        val isThisUser = member.email == currentUserEmail

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("member_item_${member.email}")
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isMemberOwner) Color(0xFFF59E0B) else Color(0xFF6366F1),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = member.name.take(2).uppercase(),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = member.name + if (isThisUser) " (Tú)" else "",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            if (isMemberOwner) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFFF59E0B).copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = "👑 Dueño",
                                                        color = Color(0xFFFDE68A),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(text = member.email, color = Color(0xFF94A3B8), fontSize = 10.sp)
                                    }

                                    // Botón para expulsar al usuario (solo el dueño puede hacerlo y no a sí mismo)
                                    if (isOwner && !isMemberOwner) {
                                        IconButton(
                                            onClick = { onRemoveMember(member.email) },
                                            modifier = Modifier.testTag("btn_remove_member_${member.email}")
                                        ) {
                                            Icon(
                                                Icons.Default.PersonRemove,
                                                contentDescription = "Sacar del grupo",
                                                tint = Color(0xFFF87171),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                // Controles de permisos para el integrante (solo si no es el dueño)
                                if (!isMemberOwner) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Switch enviar mensajes
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Enviar mensajes",
                                            color = if (member.canSendMessages) Color.White else Color(0xFFEF4444),
                                            fontSize = 11.sp
                                        )
                                        if (isOwner) {
                                            Switch(
                                                checked = member.canSendMessages,
                                                onCheckedChange = { canSend ->
                                                    onUpdatePermissions(member.email, canSend, member.canSendMedia)
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = Color(0xFF059669)
                                                ),
                                                modifier = Modifier.testTag("switch_can_send_msg_${member.email}")
                                            )
                                        } else {
                                            Text(
                                                text = if (member.canSendMessages) "Permitido" else "Restringido",
                                                color = if (member.canSendMessages) Color(0xFF34D399) else Color(0xFFEF4444),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Switch enviar multimedia
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Enviar multimedia (fotos/video/música)",
                                            color = if (member.canSendMedia) Color.White else Color(0xFFEF4444),
                                            fontSize = 11.sp
                                        )
                                        if (isOwner) {
                                            Switch(
                                                checked = member.canSendMedia,
                                                onCheckedChange = { canMedia ->
                                                    onUpdatePermissions(member.email, member.canSendMessages, canMedia)
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = Color(0xFF059669)
                                                ),
                                                modifier = Modifier.testTag("switch_can_send_media_${member.email}")
                                            )
                                        } else {
                                            Text(
                                                text = if (member.canSendMedia) "Permitido" else "Restringido",
                                                color = if (member.canSendMedia) Color(0xFF34D399) else Color(0xFFEF4444),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Botón de Deshacer permisos (restablecer a permitidos)
                                    if (isOwner && (!member.canSendMessages || !member.canSendMedia)) {
                                        TextButton(
                                            onClick = { onResetPermissions(member.email) },
                                            modifier = Modifier.testTag("btn_reset_perm_${member.email}")
                                        ) {
                                            Icon(Icons.Default.LockReset, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Deshacer restricciones (Restaurar permisos)", color = Color(0xFF38BDF8), fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Acciones principales al pie del diálogo
                if (isOwner) {
                    // Dueño: Botón para Programar Eliminación de Grupo con 3 minutos de advertencia
                    Button(
                        onClick = { confirmDeleteGroupDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_schedule_delete_group")
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (channel.isDeleting) "Ajustar Eliminación (3 min)" else "Borrar Grupo (Aviso 3 min)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // Miembro regular: Botón para Salir del Grupo
                    Button(
                        onClick = { confirmLeaveGroupDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_leave_group")
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Salir del Grupo", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Diálogo de Confirmación para Programar Borrado de 3 minutos
    if (confirmDeleteGroupDialog) {
        AlertDialog(
            onDismissRequest = { confirmDeleteGroupDialog = false },
            title = { Text("¿Borrar grupo con aviso de 3 minutos?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Como dueño del grupo, se emitirá una advertencia con un temporizador de 3 minutos en el chat para todos los participantes. Durante este tiempo podrás deshacer o cancelar la eliminación en cualquier momento antes de que se borre de forma permanente.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteGroupDialog = false
                        onScheduleDeletion()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("btn_confirm_schedule_deletion_dialog")
                ) {
                    Text("Iniciar Cuenta Regresiva de 3 Min", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteGroupDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de Confirmación para Salir del Grupo
    if (confirmLeaveGroupDialog) {
        AlertDialog(
            onDismissRequest = { confirmLeaveGroupDialog = false },
            title = { Text("¿Salir de este grupo?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Dejarás de recibir mensajes de este grupo. Podrás ser añadido nuevamente por el creador en el futuro.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmLeaveGroupDialog = false
                        onLeaveGroup()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("btn_confirm_leave_group_dialog")
                ) {
                    Text("Salir", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeaveGroupDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

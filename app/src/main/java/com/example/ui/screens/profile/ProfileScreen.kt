package com.example.ui.screens.profile

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.viewmodel.OmniViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: OmniViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val authState by viewModel.authUiState.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val user = authState.currentUser

    var isEditingProfile by remember { mutableStateOf(false) }
    var editNameInput by remember(user?.displayName) { mutableStateOf(user?.displayName ?: "Alex González") }
    var editAvatarInput by remember(user?.avatarUrl) { mutableStateOf(user?.avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200&q=80") }
    var profileSavedFeedback by remember { mutableStateOf(false) }

    val presetAvatars = listOf(
        "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200&q=80",
        "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=200&q=80",
        "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=200&q=80",
        "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200&q=80",
        "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&q=80"
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF1F5F9),
        topBar = {
            Surface(
                color = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_profile_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = if (isDarkTheme) Color.White else Color(0xFF0F172A)
                        )
                    }
                    Text(
                        text = "Mi Cuenta Cloud y Ajustes",
                        color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // User Avatar Card with photo preview
            Surface(
                modifier = Modifier.size(96.dp),
                shape = CircleShape,
                color = Color(0xFF4F46E5),
                shadowElevation = 4.dp
            ) {
                val currentPhoto = if (isEditingProfile) editAvatarInput else (user?.avatarUrl ?: "")
                if (currentPhoto.isNotBlank()) {
                    AsyncImage(
                        model = currentPhoto,
                        contentDescription = "Foto de perfil",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (user?.displayName ?: "A").take(1).uppercase(),
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = user?.displayName ?: "Alex González",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDarkTheme) Color.White else Color(0xFF0F172A)
            )

            Text(
                text = user?.email ?: "gonzalez24029@gmail.com",
                fontSize = 13.sp,
                color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (user?.isGoogleAccount == true) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF6366F1).copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = if (user?.isGoogleAccount == true) Color(0xFF34D399) else Color(0xFF818CF8),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (user?.isGoogleAccount == true) "Autenticado con Google Cloud" else "Cuenta OmniStudio Cloud Activa",
                        fontSize = 12.sp,
                        color = if (user?.isGoogleAccount == true) Color(0xFF34D399) else Color(0xFF818CF8),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Global Theme Switcher Card (Dark / Light Mode Toggle)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDarkTheme) Color(0xFF334155) else Color(0xFFFEF3C7),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = "Tema",
                                    tint = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFFD97706),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isDarkTheme) "Modo Oscuro (Dark Theme)" else "Modo Claro (Light Theme)",
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isDarkTheme) "Interfaz oscura óptima para descanso visual" else "Interfaz clara de alto contraste",
                                color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { viewModel.toggleTheme() },
                        modifier = Modifier.testTag("switch_theme_toggle"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF4F46E5),
                            uncheckedThumbColor = Color(0xFFD97706),
                            uncheckedTrackColor = Color(0xFFE2E8F0)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card para Editar Nombre y Foto de Perfil (Cloud Sync)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Nombre y Foto de Perfil",
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                                fontSize = 15.sp
                            )
                        }

                        Button(
                            onClick = {
                                isEditingProfile = !isEditingProfile
                                profileSavedFeedback = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isEditingProfile) Color(0xFF475569) else Color(0xFF4F46E5)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_toggle_edit_profile")
                        ) {
                            Icon(
                                imageVector = if (isEditingProfile) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isEditingProfile) "Cerrar" else "Editar", fontSize = 12.sp, color = Color.White)
                        }
                    }

                    if (isEditingProfile) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Modifica tu nombre público o elige una foto de perfil para que todos los usuarios y grupos la vean en la nube:",
                            color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B),
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = editNameInput,
                            onValueChange = { editNameInput = it },
                            label = { Text("Nombre visible para todos") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_edit_display_name"),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                                unfocusedTextColor = if (isDarkTheme) Color.White else Color(0xFF0F172A)
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = editAvatarInput,
                            onValueChange = { editAvatarInput = it },
                            label = { Text("URL de Foto de Perfil") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_edit_avatar_url"),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                                unfocusedTextColor = if (isDarkTheme) Color.White else Color(0xFF0F172A)
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "O elige un avatar sugerido:",
                            fontSize = 11.sp,
                            color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(presetAvatars) { avatarUrl ->
                                val isSelected = editAvatarInput == avatarUrl
                                Surface(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .clickable { editAvatarInput = avatarUrl }
                                        .then(
                                            if (isSelected) Modifier.border(2.dp, Color(0xFF4F46E5), CircleShape)
                                            else Modifier
                                        ),
                                    shape = CircleShape
                                ) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = "Avatar preset",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                viewModel.updateUserProfile(editNameInput, editAvatarInput)
                                profileSavedFeedback = true
                                isEditingProfile = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_save_profile_cloud"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Guardar Cambios en la Nube", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    if (profileSavedFeedback) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "¡Nombre y foto de perfil actualizados y guardados en la nube!",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cloud Storage Quota Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudQueue, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Almacenamiento en la Nube",
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = "3.4 GB / 15 GB",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { 0.23f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF38BDF8),
                        trackColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "11.6 GB libres para tus documentos, pistas de audio y mensajes.",
                        color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Security & Realtime Cloud Sync
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Seguridad y Respaldo",
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewModel.openForgotPassword() }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LockReset, contentDescription = null, tint = Color(0xFF818CF8))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Restablecer Contraseña",
                                color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text("Envía un enlace o código de recuperación a tu correo", color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B), fontSize = 11.sp)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = Color(0xFF34D399))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Sincronización en Tiempo Real",
                                color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text("Activo: Perfil, grupos, docs y chats respaldados", color = Color(0xFF34D399), fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Logout Button
            Button(
                onClick = {
                    try {
                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                    } catch (_: Exception) {}
                    viewModel.logout()
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_logout"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cerrar Sesión", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}


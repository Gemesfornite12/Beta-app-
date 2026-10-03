package com.example.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.OmniViewModel

@Composable
fun AuthScreen(
    viewModel: OmniViewModel? = null,
    authViewModel: AuthViewModel = viewModel(),
    onAuthSuccess: () -> Unit
) {
    val authState by authViewModel.uiState.collectAsState()
    val context = LocalContext.current

    var isPasswordVisible by remember { mutableStateOf(false) }
    var recoveryEmailInput by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    // Sincronizar estado cuando el usuario se autentica
    LaunchedEffect(authState.isAuthenticated) {
        if (authState.isAuthenticated) {
            viewModel?.syncUserFromFirebaseAuth(
                email = authState.userEmail,
                displayName = authState.userDisplayName,
                photoUrl = authState.userPhotoUrl
            )
            onAuthSuccess()
        }
    }

    // Mostrar mensajes de error
    LaunchedEffect(authState.errorMessage) {
        authState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            authViewModel.clearMessages()
        }
    }

    // Mostrar mensajes de éxito
    LaunchedEffect(authState.successMessage) {
        authState.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            authViewModel.clearMessages()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E1B4B),
                        Color(0xFF0F172A)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Branding Icon & Title
            Surface(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp)),
                color = Color(0xFF6366F1).copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "OmniStudio Cloud",
                        tint = Color(0xFF818CF8),
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "OmniStudio",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )

            Text(
                text = "Tu suite creativa en la nube: Documentos • Música • Chat",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Features Pill Row
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                FeatureBadge(icon = Icons.Outlined.Description, label = "Docs & PPTX", color = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.width(8.dp))
                FeatureBadge(icon = Icons.Default.MusicNote, label = "Beat Studio", color = Color(0xFFA855F7))
                Spacer(modifier = Modifier.width(8.dp))
                FeatureBadge(icon = Icons.Outlined.Forum, label = "Chat en Vivo", color = Color(0xFF34D399))
            }

            // Auth Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Switch Tabs: Iniciar Sesión / Registrarse
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A))
                            .padding(4.dp)
                    ) {
                        TabButton(
                            title = "Iniciar Sesión",
                            isSelected = !authState.isRegisterMode,
                            onClick = { if (authState.isRegisterMode) authViewModel.toggleAuthMode() },
                            modifier = Modifier.weight(1f).testTag("tab_login")
                        )
                        TabButton(
                            title = "Registrarse",
                            isSelected = authState.isRegisterMode,
                            onClick = { if (!authState.isRegisterMode) authViewModel.toggleAuthMode() },
                            modifier = Modifier.weight(1f).testTag("tab_register")
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Google Sign-In Button via Credential Manager
                    OutlinedButton(
                        onClick = { authViewModel.signInWithGoogle(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_google_signin"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF0F172A),
                            contentColor = Color.White
                        ),
                        enabled = !authState.isLoading && !authState.isGoogleLoading
                    ) {
                        if (authState.isGoogleLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF818CF8),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    modifier = Modifier.size(24.dp),
                                    shape = CircleShape,
                                    color = Color.White
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("G", fontWeight = FontWeight.Bold, color = Color(0xFFEA4335), fontSize = 15.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Continuar con Google",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFF334155)))
                        Text(
                            text = "o con tu correo",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFF334155)))
                    }

                    // Register name field
                    AnimatedVisibility(visible = authState.isRegisterMode) {
                        Column {
                            OutlinedTextField(
                                value = authState.nameInput,
                                onValueChange = { authViewModel.onNameChange(it) },
                                label = { Text("Nombre y Apellidos") },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF94A3B8))
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_auth_name"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // Email Field
                    OutlinedTextField(
                        value = authState.emailInput,
                        onValueChange = { authViewModel.onEmailChange(it) },
                        label = { Text("Correo Electrónico") },
                        placeholder = { Text("ejemplo@cloud.io") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF94A3B8))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_auth_email"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Password Field
                    OutlinedTextField(
                        value = authState.passwordInput,
                        onValueChange = { authViewModel.onPasswordChange(it) },
                        label = { Text("Contraseña") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF94A3B8))
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Ver contraseña",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_auth_password"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Forgot Password Link
                    if (!authState.isRegisterMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    recoveryEmailInput = authState.emailInput
                                    authViewModel.openForgotPasswordDialog()
                                },
                                modifier = Modifier.testTag("btn_forgot_password")
                            ) {
                                Text(
                                    text = "¿Olvidaste tu contraseña?",
                                    color = Color(0xFF818CF8),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Main Action Button (Login / Register)
                    Button(
                        onClick = {
                            if (authState.isRegisterMode) {
                                authViewModel.signUpWithEmail()
                            } else {
                                authViewModel.signInWithEmail()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_auth_submit"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4F46E5),
                            contentColor = Color.White
                        ),
                        enabled = !authState.isLoading && !authState.isGoogleLoading
                    ) {
                        if (authState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (!authState.isRegisterMode) "Entrar a OmniStudio" else "Crear Cuenta en Firebase",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggle mode quick text button
                    TextButton(
                        onClick = { authViewModel.toggleAuthMode() },
                        modifier = Modifier.testTag("btn_toggle_auth_mode")
                    ) {
                        Text(
                            text = if (!authState.isRegisterMode)
                                "¿No tienes cuenta? Regístrate gratis"
                            else
                                "¿Ya tienes cuenta? Inicia sesión",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Autenticación segura con Firebase Auth y Google Cloud Identity.",
                color = Color(0xFF64748B),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        // Forgot Password Dialog Modal via Firebase Auth
        if (authState.isForgotPasswordDialogOpen) {
            AlertDialog(
                onDismissRequest = { authViewModel.closeForgotPasswordDialog() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF818CF8))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Recuperar Contraseña", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = if (!authState.passwordResetSent)
                                "Ingresa el correo vinculado a tu cuenta para enviarte un enlace de restablecimiento de contraseña mediante Firebase Auth."
                            else
                                "Hemos enviado las instrucciones para restablecer tu contraseña. Revisa tu bandeja de entrada o carpeta de spam.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!authState.passwordResetSent) {
                            OutlinedTextField(
                                value = recoveryEmailInput,
                                onValueChange = { recoveryEmailInput = it },
                                label = { Text("Correo de recuperación") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth().testTag("input_recovery_email"),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    if (!authState.passwordResetSent) {
                        Button(
                            onClick = {
                                authViewModel.sendPasswordReset(recoveryEmailInput)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            modifier = Modifier.testTag("btn_send_recovery_email"),
                            enabled = !authState.isLoading
                        ) {
                            if (authState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Enviar Correo")
                            }
                        }
                    } else {
                        Button(
                            onClick = { authViewModel.closeForgotPasswordDialog() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                        ) {
                            Text("Entendido")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { authViewModel.closeForgotPasswordDialog() }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}

@Composable
private fun TabButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0xFF4F46E5) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else Color(0xFF94A3B8),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun FeatureBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

package com.example.ui.screens.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallSession
import com.example.data.model.CallStatus

@Composable
fun CallSessionScreen(
    callSession: CallSession,
    onAnswerCall: () -> Unit = {},
    onRejectCall: () -> Unit = {},
    onToggleMute: () -> Unit = {},
    onToggleCamera: () -> Unit = {},
    onToggleSpeaker: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    onEndCall: () -> Unit = {},
    localVideoTrack: VideoTrack? = null,
    remoteVideoTrack: VideoTrack? = null,
    eglContext: EglBase.Context? = null,
    mediaConnectionState: String = ""
) {
    val context = LocalContext.current
    val answerPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = !callSession.isVideo || permissions[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (micGranted && cameraGranted) onAnswerCall()
        else Toast.makeText(context, "Se necesitan permisos de micrófono${if (callSession.isVideo) " y cámara" else ""} para responder.", Toast.LENGTH_LONG).show()
    }
    val displayPhotoUrl = remember(callSession) {
        if (callSession.isIncoming) callSession.callerAvatarUrl else callSession.peerAvatarUrl
    }
    val answerWithPermissions = {
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = !callSession.isVideo || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (micGranted && cameraGranted) onAnswerCall()
        else answerPermissionLauncher.launch(
            if (callSession.isVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            else arrayOf(Manifest.permission.RECORD_AUDIO)
        )
    }
    val durationFormatted = remember(callSession.durationSeconds) {
        val mins = callSession.durationSeconds / 60
        val secs = callSession.durationSeconds % 60
        String.format("%02d:%02d", mins, secs)
    }

    val ringSecondsLeftFormatted = remember(callSession.ringSecondsLeft) {
        val mins = callSession.ringSecondsLeft / 60
        val secs = callSession.ringSecondsLeft % 60
        String.format("%02d:%02d", mins, secs)
    }

    val displayName = remember(callSession) {
        if (callSession.isIncoming && callSession.callerName.isNotBlank()) {
            callSession.callerName
        } else {
            callSession.peerName
        }
    }

    val initials = remember(displayName) {
        val parts = displayName.trim().split(" ")
        if (parts.size >= 2) "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
        else displayName.take(2).uppercase()
    }

    // Animación de pulso para cuando está sonando o conectado
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (callSession.isTimedOut) listOf(
                        Color(0xFF1E1010),
                        Color(0xFF2A1010),
                        Color(0xFF0F0808)
                    ) else if (callSession.isVideo) listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E1B4B),
                        Color(0xFF0F172A)
                    ) else listOf(
                        Color(0xFF0B0F19),
                        Color(0xFF1E293B),
                        Color(0xFF0B0F19)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("screen_call_session")
    ) {
        // Video remoto real. Si todavía no llegó el stream, mostrar la foto de perfil.
        if (callSession.isVideo && callSession.status == CallStatus.CONNECTED && !callSession.isTimedOut) {
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                if (remoteVideoTrack != null && eglContext != null) {
                    WebRtcVideoSurface(
                        track = remoteVideoTrack,
                        eglContext = eglContext,
                        mirror = false,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ProfilePhoto(url = displayPhotoUrl, initials = initials, size = 116.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(mediaConnectionState.ifBlank { "Esperando video…" }, color = Color.White, fontSize = 13.sp)
                    }
                }

                if (callSession.isCameraOn && localVideoTrack != null && eglContext != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E1E2E),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF818CF8)),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = 16.dp)
                            .size(width = 110.dp, height = 150.dp)
                            .testTag("pip_user_camera")
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            WebRtcVideoSurface(
                                track = localVideoTrack,
                                eglContext = eglContext,
                                mirror = callSession.isFrontCamera,
                                modifier = Modifier.fillMaxSize()
                            )
                            Text(
                                text = "Tú",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Header superior con badges, grupo, nombre de quién llama y temporizador
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Chip de tipo de llamada
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (callSession.isTimedOut) Color(0xFFEF4444).copy(alpha = 0.2f)
                else Color(0xFF1E293B).copy(alpha = 0.85f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (callSession.isTimedOut) Icons.Default.PhoneMissed
                        else if (callSession.isVideo) Icons.Default.Videocam
                        else Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (callSession.isTimedOut) Color(0xFFEF4444)
                        else if (callSession.isVideo) Color(0xFF38BDF8)
                        else Color(0xFF34D399),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            callSession.isTimedOut -> "Llamada no contestada • Tiempo agotado"
                            callSession.isIncoming && callSession.status == CallStatus.RINGING ->
                                if (callSession.isVideo) "Videollamada entrante" else "Llamada de voz entrante"
                            callSession.status == CallStatus.CONNECTED ->
                                if (callSession.isVideo) "Videollamada en curso" else "Llamada de voz en curso"
                            else -> if (callSession.isVideo) "Iniciando videollamada..." else "Iniciando llamada..."
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Si es llamada de un grupo, mostrar el nombre del grupo
            if (!callSession.groupName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF6366F1).copy(alpha = 0.25f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = Color(0xFFA5B4FC),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Grupo: ${callSession.groupName}",
                            color = Color(0xFFA5B4FC),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Nombre de la persona o del grupo
            Text(
                text = displayName,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            if (!callSession.groupName.isNullOrBlank() && callSession.isIncoming) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Llamando a los integrantes de ${callSession.groupName}",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Estado y contador de tiempo
            when {
                callSession.isTimedOut -> {
                    Text(
                        text = "Nadie respondió la llamada. Se ha generado una notificación de llamada perdida.",
                        color = Color(0xFFF87171),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
                callSession.status == CallStatus.CONNECTED -> {
                    Text(
                        text = durationFormatted,
                        color = Color(0xFF34D399),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (mediaConnectionState.isNotBlank()) {
                        Text(mediaConnectionState, color = Color(0xFFCBD5E1), fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
                callSession.status == CallStatus.RINGING -> {
                    // Badge con temporizador de respuesta regresivo
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (callSession.isIncoming)
                                    "Tiempo para responder: $ringSecondsLeftFormatted"
                                else
                                    "Esperando que responda ($ringSecondsLeftFormatted)",
                                color = Color(0xFFFBBF24),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Centro: Avatar pulsante si no es videollamada o si está sonando / congelado
        if (!callSession.isVideo || callSession.status != CallStatus.CONNECTED || callSession.isTimedOut) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (!callSession.isTimedOut) {
                    // Círculo exterior pulsante
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                if (callSession.isIncoming) Color(0xFF10B981).copy(alpha = 0.18f)
                                else if (callSession.isVideo) Color(0xFF38BDF8).copy(alpha = 0.18f)
                                else Color(0xFF6366F1).copy(alpha = 0.18f)
                            )
                    )

                    // Círculo medio
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(
                                if (callSession.isIncoming) Color(0xFF10B981).copy(alpha = 0.28f)
                                else if (callSession.isVideo) Color(0xFF38BDF8).copy(alpha = 0.28f)
                                else Color(0xFF6366F1).copy(alpha = 0.28f)
                            )
                    )
                }

                // Avatar central
                if (callSession.isTimedOut) {
                    Surface(shape = CircleShape, color = Color(0xFF991B1B), modifier = Modifier.size(110.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PhoneMissed, contentDescription = "Llamada perdida", tint = Color.White, modifier = Modifier.size(48.dp))
                        }
                    }
                } else {
                    ProfilePhoto(url = displayPhotoUrl, initials = initials, size = 110.dp)
                }
            }
        }

        // Barra inferior con controles según el estado:
        // CASO 1: Llamada entrante sonando (RINGING + isIncoming) -> Botón Verde (Responder) y Botón Rojo (No responder)
        if (callSession.isIncoming && callSession.status == CallStatus.RINGING && !callSession.isTimedOut) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                shape = RoundedCornerShape(32.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp, horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón Rojo: No responder / Rechazar
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onRejectCall() }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFDC2626),
                            modifier = Modifier
                                .size(64.dp)
                                .testTag("btn_call_reject")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "No responder",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No responder",
                            color = Color(0xFFF87171),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Botón Verde: Responder
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { answerWithPermissions() }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF10B981),
                            modifier = Modifier
                                .size(64.dp)
                                .testTag("btn_call_answer")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (callSession.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                                    contentDescription = "Responder llamada",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Responder",
                            color = Color(0xFF34D399),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        // CASO 2: Llamada saliente sonando (RINGING + !isIncoming) -> Botón cancelar
        else if (!callSession.isIncoming && callSession.status == CallStatus.RINGING && !callSession.isTimedOut) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                shape = RoundedCornerShape(32.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onEndCall() }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFDC2626),
                            modifier = Modifier
                                .size(60.dp)
                                .testTag("btn_call_cancel")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "Cancelar llamada",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Cancelar llamada",
                            color = Color(0xFFF87171),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        // CASO 3: Llamada conectada (CONNECTED) -> Barra completa de controles
        else if (callSession.status == CallStatus.CONNECTED && !callSession.isTimedOut) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                shape = RoundedCornerShape(32.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.95f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Silenciar Micrófono
                    Surface(
                        shape = CircleShape,
                        color = if (callSession.isMuted) Color(0xFFEF4444) else Color(0xFF334155),
                        modifier = Modifier
                            .size(52.dp)
                            .clickable { onToggleMute() }
                            .testTag("btn_call_mute")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (callSession.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (callSession.isMuted) "Activar micrófono" else "Silenciar",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Si es videollamada: Encender / Apagar cámara
                    if (callSession.isVideo) {
                        Surface(
                            shape = CircleShape,
                            color = if (!callSession.isCameraOn) Color(0xFFEF4444) else Color(0xFF334155),
                            modifier = Modifier
                                .size(52.dp)
                                .clickable { onToggleCamera() }
                                .testTag("btn_call_camera")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (callSession.isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                    contentDescription = if (callSession.isCameraOn) "Apagar cámara" else "Encender cámara",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Cambiar cámara frontal / trasera
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF334155),
                            modifier = Modifier
                                .size(52.dp)
                                .clickable { onSwitchCamera() }
                                .testTag("btn_call_switch_camera")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Cameraswitch,
                                    contentDescription = "Cambiar cámara",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    } else {
                        // Si es llamada de voz: Altavoz
                        Surface(
                            shape = CircleShape,
                            color = if (callSession.isSpeakerOn) Color(0xFF4F46E5) else Color(0xFF334155),
                            modifier = Modifier
                                .size(52.dp)
                                .clickable { onToggleSpeaker() }
                                .testTag("btn_call_speaker")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (callSession.isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = "Altavoz",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // Botón Colgar / Finalizar llamada (Rojo llamativo)
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFDC2626),
                        modifier = Modifier
                            .size(58.dp)
                            .clickable { onEndCall() }
                            .testTag("btn_call_end")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Finalizar llamada",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ProfilePhoto(url: String, initials: String, size: androidx.compose.ui.unit.Dp) {
    var imageFailed by remember(url) { mutableStateOf(false) }
    Surface(shape = CircleShape, color = Color(0xFF4F46E5), modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            if (url.isNotBlank() && !imageFailed) {
                AsyncImage(
                    model = url,
                    contentDescription = "Foto de perfil",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    onError = { imageFailed = true }
                )
            } else {
                Text(text = initials, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun WebRtcVideoSurface(
    track: VideoTrack,
    eglContext: EglBase.Context,
    mirror: Boolean,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                init(eglContext, null)
                setEnableHardwareScaler(true)
                setMirror(mirror)
                tag = track
                track.addSink(this)
            }
        },
        update = { renderer ->
            val current = renderer.tag as? VideoTrack
            if (current !== track) {
                current?.removeSink(renderer)
                renderer.setMirror(mirror)
                renderer.tag = track
                track.addSink(renderer)
            }
        },
        onRelease = { renderer ->
            (renderer.tag as? VideoTrack)?.removeSink(renderer)
            renderer.release()
        }
    )
}

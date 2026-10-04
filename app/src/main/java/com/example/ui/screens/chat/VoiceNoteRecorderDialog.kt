package com.example.ui.screens.chat

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.sqrt

@Composable
internal fun VoiceNoteRecorderDialog(
    onDismiss: () -> Unit,
    onSend: (uri: Uri, mimeType: String, caption: String) -> Unit
) {
    val context = LocalContext.current
    val recorder = remember(context) { VoiceNoteRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var elapsedMillis by remember { mutableStateOf(0L) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var recordingFormat by remember { mutableStateOf<VoiceRecordingFormat?>(null) }
    var captured by remember { mutableStateOf<CompletedVoiceRecording?>(null) }
    val amplitudeSamples = remember { mutableStateListOf<Int>() }
    val lockedState = rememberUpdatedState(isLocked)

    fun beginRecording() {
        if (isRecording || captured != null) return
        try {
            val format = recorder.start()
            recordingFormat = format
            isRecording = true
            isLocked = false
            elapsedMillis = 0L
            amplitudeSamples.clear()
            errorText = if (preferredVoiceRecordingFormat(android.os.Build.VERSION.SDK_INT).prefersOpus && !format.prefersOpus) {
                "Este dispositivo no inició Opus; se está grabando en AAC/M4A."
            } else null
        } catch (error: Exception) {
            errorText = error.message ?: "No se pudo iniciar la grabación."
        }
    }

    fun finishRecording() {
        if (!isRecording) return
        isRecording = false
        isLocked = false
        try {
            captured = recorder.stop()
            errorText = null
        } catch (error: Exception) {
            captured = null
            errorText = error.message ?: "No se pudo finalizar la grabación."
        }
    }

    fun discardRecording() {
        if (isRecording) recorder.cancel()
        captured?.file?.delete()
        captured = null
        isRecording = false
        isLocked = false
        amplitudeSamples.clear()
        elapsedMillis = 0L
    }

    LaunchedEffect(isRecording) {
        val startedAt = System.currentTimeMillis() - elapsedMillis
        while (isRecording) {
            elapsedMillis = System.currentTimeMillis() - startedAt
            amplitudeSamples.add(recorder.maxAmplitude().coerceIn(0, 32_767))
            while (amplitudeSamples.size > 42) amplitudeSamples.removeAt(0)
            delay(90L)
        }
    }

    DisposableEffect(recorder) {
        onDispose { if (isRecording) recorder.cancel() }
    }

    AlertDialog(
        onDismissRequest = {
            if (isRecording) recorder.cancel()
            captured?.file?.delete()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = Color(0xFF0F172A),
        title = { Text("Nota de voz", color = Color.White) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    isRecording -> {
                        Text(if (isLocked) "Grabando · manos libres" else "Grabando", color = Color(0xFFFCA5A5), fontSize = 14.sp)
                        Text(recordingFormat?.label ?: "Audio", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(formatVoiceDuration(elapsedMillis), color = Color.White, fontSize = 25.sp)
                        Spacer(Modifier.height(12.dp))
                        VoiceWaveform(amplitudeSamples.toList(), Modifier.fillMaxWidth().height(54.dp))
                        Spacer(Modifier.height(12.dp))
                        if (!isLocked) Text("Desliza hacia arriba para bloquear · hacia la izquierda para cancelar", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                        else {
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { discardRecording(); errorText = "Grabación descartada." }, modifier = Modifier.testTag("voice_note_cancel_locked")) {
                                    Icon(Icons.Default.Cancel, "Cancelar grabación", tint = Color(0xFFF87171))
                                }
                                Button(
                                    onClick = { finishRecording() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    modifier = Modifier.testTag("voice_note_stop")
                                ) {
                                    Icon(Icons.Default.Stop, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Detener")
                                }
                            }
                        }
                    }
                    captured != null -> {
                        Text("Vista previa", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("${captured!!.format.label} · ${formatVoiceDuration(elapsedMillis)}", color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        VoiceNotePreviewPlayer(captured!!.uri)
                        VoiceWaveform(amplitudeSamples.toList(), Modifier.fillMaxWidth().height(54.dp))
                    }
                    else -> {
                        Text("Mantén presionado para grabar", color = Color.White, fontSize = 15.sp)
                        Spacer(Modifier.height(16.dp))
                        HoldToRecordButton(
                            onStart = { beginRecording() },
                            onStop = { finishRecording() },
                            onCancel = { discardRecording(); errorText = "Grabación cancelada." },
                            onLock = { isLocked = true },
                            locked = lockedState.value
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Mantén · desliza a la izquierda para cancelar · desliza hacia arriba para bloquear", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    }
                }
                errorText?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Color(0xFFFCA5A5), fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            if (captured != null) {
                Button(
                    onClick = {
                        val ready = captured ?: return@Button
                        val caption = "🎙 Nota de voz · ${formatVoiceDuration(elapsedMillis)} · ${ready.format.label}"
                        captured = null
                        onSend(ready.uri, ready.format.mimeType, caption)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    modifier = Modifier.testTag("voice_note_send")
                ) {
                    Icon(Icons.Default.Send, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Enviar")
                }
            } else {
                TextButton(onClick = onDismiss, enabled = !isRecording) { Text("Cerrar", color = Color(0xFFCBD5E1)) }
            }
        },
        dismissButton = {
            if (captured != null) {
                TextButton(onClick = { discardRecording(); errorText = null }) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFF87171))
                    Spacer(Modifier.width(4.dp))
                    Text("Borrar", color = Color(0xFFF87171))
                }
            }
        }
    )
}

@Composable
private fun HoldToRecordButton(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onLock: () -> Unit,
    locked: Boolean
) {
    val currentStart by rememberUpdatedState(onStart)
    val currentStop by rememberUpdatedState(onStop)
    val currentCancel by rememberUpdatedState(onCancel)
    val currentLock by rememberUpdatedState(onLock)
    val currentLocked = rememberUpdatedState(locked)
    Surface(
        color = Color(0xFFEF4444),
        shape = CircleShape,
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    currentStart()
                    var cancelled = false
                    var lockTriggered = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        val delta = change.position - down.position
                        if (!cancelled && delta.x < -90f) {
                            cancelled = true
                            currentCancel()
                        } else if (!lockTriggered && delta.y < -90f) {
                            lockTriggered = true
                            currentLock()
                        }
                        if (!change.pressed) {
                            if (!cancelled && !currentLocked.value) currentStop()
                            break
                        }
                        change.consume()
                    }
                }
            }
            .testTag("voice_note_hold_to_record")
    ) {
        Box(Modifier.fillMaxWidth().height(76.dp), contentAlignment = Alignment.Center) {
            Icon(if (locked) Icons.Default.Lock else Icons.Default.Mic, contentDescription = "Mantener para grabar nota de voz", tint = Color.White, modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
private fun VoiceWaveform(samples: List<Int>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        val visibleSamples = samples.takeLast(42)
        visibleSamples.forEach { sample ->
            val normalized = sqrt(sample.coerceIn(0, 32_767).toFloat() / 32_767f)
            Box(
                Modifier
                    .weight(1f)
                    .height((5f + normalized * 43f).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF818CF8))
            )
        }
        repeat((42 - visibleSamples.size).coerceAtLeast(0)) {
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF334155)))
        }
    }
}

@Composable
private fun VoiceNotePreviewPlayer(uri: Uri) {
    val context = LocalContext.current
    var player by remember(uri) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(uri) { mutableStateOf(false) }
    var loading by remember(uri) { mutableStateOf(false) }
    DisposableEffect(uri) {
        onDispose { runCatching { player?.release() }; player = null }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        IconButton(
            onClick = {
                val current = player
                if (current?.isPlaying == true) {
                    current.pause()
                    playing = false
                } else if (current != null) {
                    runCatching { current.start(); playing = true }.onFailure { loading = false }
                } else {
                    loading = true
                    runCatching {
                        MediaPlayer().also { created ->
                            created.setDataSource(context, uri)
                            created.setOnPreparedListener { media -> loading = false; playing = true; media.start() }
                            created.setOnCompletionListener { media -> media.seekTo(0); playing = false }
                            created.setOnErrorListener { _, _, _ -> loading = false; playing = false; true }
                            player = created
                            created.prepareAsync()
                        }
                    }.onFailure { loading = false }
                }
            },
            modifier = Modifier.testTag("voice_note_preview_play")
        ) {
            Icon(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = if (playing) "Pausar vista previa" else "Reproducir vista previa", tint = Color(0xFFA5B4FC), modifier = Modifier.size(30.dp))
        }
        Text(if (loading) "Preparando audio…" else if (playing) "Reproduciendo" else "Escuchar", color = Color.White, fontSize = 12.sp)
    }
}

private fun formatVoiceDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}

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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

internal enum class VoiceNoteDialogAction { START, STOP, CANCEL, PLAY_PREVIEW, DISCARD, SEND, CLOSE }

/** The dialog uses this single state mapping to keep finish, preview, discard, and send explicit. */
internal fun voiceNoteDialogActions(isRecording: Boolean, hasCapturedRecording: Boolean): Set<VoiceNoteDialogAction> = when {
    isRecording -> setOf(VoiceNoteDialogAction.STOP, VoiceNoteDialogAction.CANCEL)
    hasCapturedRecording -> setOf(VoiceNoteDialogAction.PLAY_PREVIEW, VoiceNoteDialogAction.DISCARD, VoiceNoteDialogAction.SEND)
    else -> setOf(VoiceNoteDialogAction.START, VoiceNoteDialogAction.CLOSE)
}

internal enum class VoiceNoteGestureAction { CANCEL, LOCK }

/** One gesture can trigger each intended action at most once; left cancels, upward locks. */
internal class VoiceNoteGestureTracker(private val thresholdPx: Float = 90f) {
    private var cancelled = false
    private var locked = false

    fun onMove(deltaX: Float, deltaY: Float): VoiceNoteGestureAction? {
        if (cancelled) return null
        if (deltaX <= -thresholdPx) {
            cancelled = true
            return VoiceNoteGestureAction.CANCEL
        }
        if (!locked && deltaY <= -thresholdPx) {
            locked = true
            return VoiceNoteGestureAction.LOCK
        }
        return null
    }

    fun shouldFinishOnRelease(isLocked: Boolean): Boolean = !cancelled && !locked && !isLocked
}

/** Protects the send callback from rapid taps and concurrent pointer events. */
internal class VoiceNoteSendGate {
    private val claimed = AtomicBoolean(false)
    fun claim(): Boolean = claimed.compareAndSet(false, true)
    fun releaseAfterFailure() { claimed.set(false) }
}

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
    var sourceCaptured by remember { mutableStateOf<CompletedVoiceRecording?>(null) }
    var captured by remember { mutableStateOf<CompletedVoiceRecording?>(null) }
    var selectedEffect by remember { mutableStateOf(VoiceNoteEffectPreset.ORIGINAL) }
    var appliedEffect by remember { mutableStateOf(VoiceNoteEffectPreset.ORIGINAL) }
    var processingEffect by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    val temporaryAudioFiles = remember { mutableSetOf<java.io.File>() }
    var keepAudioFile by remember { mutableStateOf<java.io.File?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val sendGate = remember { VoiceNoteSendGate() }
    val amplitudeSamples = remember { mutableStateListOf<Int>() }
    val lockedState = rememberUpdatedState(isLocked)
    val recordingState = rememberUpdatedState(isRecording)
    val actions = remember(isRecording, captured != null) {
        voiceNoteDialogActions(isRecording, captured != null)
    }

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
            val completed = recorder.stop()
            sourceCaptured = completed
            captured = completed
            temporaryAudioFiles += completed.file
            selectedEffect = VoiceNoteEffectPreset.ORIGINAL
            appliedEffect = VoiceNoteEffectPreset.ORIGINAL
            processingEffect = false
            errorText = null
        } catch (error: Exception) {
            captured = null
            errorText = error.message ?: "No se pudo finalizar la grabación."
        }
    }

    fun discardRecording() {
        if (isRecording) recorder.cancel()
        temporaryAudioFiles.forEach { it.delete() }
        temporaryAudioFiles.clear()
        sourceCaptured = null
        captured = null
        selectedEffect = VoiceNoteEffectPreset.ORIGINAL
        appliedEffect = VoiceNoteEffectPreset.ORIGINAL
        processingEffect = false
        isRecording = false
        isLocked = false
        amplitudeSamples.clear()
        elapsedMillis = 0L
    }

    fun selectEffect(preset: VoiceNoteEffectPreset) {
        val original = sourceCaptured ?: return
        if (processingEffect || preset == selectedEffect) return
        selectedEffect = preset
        if (preset == VoiceNoteEffectPreset.ORIGINAL) {
            captured = original
            appliedEffect = preset
            errorText = null
            return
        }
        processingEffect = true
        errorText = null
        coroutineScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { VoiceNoteAudioEffects.process(original, preset) }
            }
            processingEffect = false
            result.onSuccess { processed ->
                temporaryAudioFiles += processed.file
                captured = processed
                appliedEffect = preset
            }.onFailure { error ->
                selectedEffect = appliedEffect
                errorText = error.message ?: "No se pudo aplicar el efecto de audio."
            }
        }
    }

    fun deleteTemporaryAudioExcept(keep: java.io.File? = keepAudioFile) {
        temporaryAudioFiles.filter { it != keep }.forEach { it.delete() }
        temporaryAudioFiles.clear()
        keep?.let(temporaryAudioFiles::add)
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
        onDispose {
            if (recordingState.value) recorder.cancel()
            deleteTemporaryAudioExcept()
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!processingEffect) {
                if (isRecording) recorder.cancel()
                deleteTemporaryAudioExcept()
                onDismiss()
            }
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
                        Text("Grabando", color = Color(0xFFFCA5A5), fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (isLocked) "Grabación bloqueada" else "Grabación sin bloqueo",
                                tint = if (isLocked) Color(0xFFA5B4FC) else Color(0xFFCBD5E1),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(if (isLocked) "Bloqueada · manos libres" else "Desbloqueada", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                        }
                        Text(recordingFormat?.label ?: "Audio", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(formatVoiceDuration(elapsedMillis), color = Color.White, fontSize = 25.sp)
                        Spacer(Modifier.height(12.dp))
                        VoiceWaveform(amplitudeSamples.toList(), Modifier.fillMaxWidth().height(54.dp))
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (VoiceNoteDialogAction.CANCEL in actions) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = { discardRecording(); errorText = "Grabación descartada." },
                                        modifier = Modifier.testTag("voice_note_cancel_recording")
                                    ) {
                                        Icon(Icons.Default.Cancel, "Cancelar grabación", tint = Color(0xFFF87171))
                                    }
                                    Text("Cancelar", color = Color(0xFFF87171), fontSize = 10.sp)
                                }
                            }
                            if (isRecording) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = { isLocked = !isLocked },
                                        modifier = Modifier.testTag("voice_note_lock_toggle")
                                    ) {
                                        Icon(
                                            if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                            contentDescription = if (isLocked) "Desbloquear grabación" else "Bloquear grabación",
                                            tint = Color(0xFFA5B4FC)
                                        )
                                    }
                                    Text(if (isLocked) "Desbloquear" else "Bloquear", color = Color(0xFFA5B4FC), fontSize = 10.sp)
                                }
                            }
                            if (VoiceNoteDialogAction.STOP in actions) {
                                Button(
                                    onClick = { finishRecording() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    modifier = Modifier.testTag("voice_note_stop")
                                ) {
                                    Icon(Icons.Default.Stop, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Detener y revisar")
                                }
                            }
                        }
                        Text(
                            if (isLocked) "Toca Detener y revisar cuando termines"
                            else "Suelta para terminar · usa Cancelar o Bloquear cuando lo necesites",
                            color = Color(0xFFCBD5E1), fontSize = 11.sp
                        )
                    }
                    captured != null -> {
                        Text("Vista previa", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("${captured!!.format.label} · ${formatVoiceDuration(elapsedMillis)}", color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        VoiceNoteEffectPicker(
                            selected = selectedEffect,
                            enabled = !processingEffect && !sending,
                            onSelect = ::selectEffect
                        )
                        if (processingEffect) {
                            Spacer(Modifier.height(6.dp))
                            Text("Procesando audio real para la vista previa…", color = Color(0xFFA5B4FC), fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (VoiceNoteDialogAction.PLAY_PREVIEW in actions && !processingEffect) VoiceNotePreviewPlayer(captured!!.uri)
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
            if (VoiceNoteDialogAction.SEND in actions) {
                Button(
                    onClick = {
                        val ready = captured ?: return@Button
                        if (processingEffect || !sendGate.claim()) return@Button
                        sending = true
                        val caption = "🎙 Nota de voz · ${formatVoiceDuration(elapsedMillis)} · ${ready.format.label}"
                        try {
                            onSend(ready.uri, ready.format.mimeType, caption)
                            keepAudioFile = ready.file
                            deleteTemporaryAudioExcept(ready.file)
                            captured = null
                            sourceCaptured = null
                            onDismiss()
                        } catch (error: Exception) {
                            sendGate.releaseAfterFailure()
                            sending = false
                            errorText = error.message ?: "No se pudo iniciar el envío del audio."
                        }
                    },
                    enabled = !sending && !processingEffect,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    modifier = Modifier.testTag("voice_note_send")
                ) {
                    Icon(Icons.Default.Send, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (sending) "Enviando audio…" else "Enviar audio")
                }
            } else if (VoiceNoteDialogAction.CLOSE in actions) {
                TextButton(onClick = onDismiss) { Text("Cerrar", color = Color(0xFFCBD5E1)) }
            }
        },
        dismissButton = {
            if (VoiceNoteDialogAction.DISCARD in actions) {
                TextButton(enabled = !sending && !processingEffect, onClick = { discardRecording(); errorText = null }) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFF87171))
                    Spacer(Modifier.width(4.dp))
                    Text("Borrar audio", color = Color(0xFFF87171))
                }
            }
        }
    )
}

@Composable
private fun VoiceNoteEffectPicker(
    selected: VoiceNoteEffectPreset,
    enabled: Boolean,
    onSelect: (VoiceNoteEffectPreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Efecto de voz · se aplica al archivo que escucharás y enviarás", color = Color(0xFFCBD5E1), fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            VoiceNoteEffectPreset.entries.forEach { preset ->
                Button(
                    onClick = { onSelect(preset) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f).testTag("voice_note_effect_${preset.name.lowercase()}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected == preset) Color(0xFF4F46E5) else Color(0xFF273449)
                    )
                ) { Text(preset.label, color = Color.White, fontSize = 10.sp) }
            }
        }
    }
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
                    val tracker = VoiceNoteGestureTracker(thresholdPx = 48.dp.toPx())
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        val delta = change.position - down.position
                        when (tracker.onMove(delta.x, delta.y)) {
                            VoiceNoteGestureAction.CANCEL -> currentCancel()
                            VoiceNoteGestureAction.LOCK -> currentLock()
                            null -> Unit
                        }
                        if (!change.pressed) {
                            if (tracker.shouldFinishOnRelease(currentLocked.value)) currentStop()
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

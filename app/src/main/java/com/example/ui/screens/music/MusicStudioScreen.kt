package com.example.ui.screens.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.OmniViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicStudioScreen(
    viewModel: OmniViewModel,
    onBack: () -> Unit,
    onShareToChat: () -> Unit
) {
    val activeProject by viewModel.activeAudioProject.collectAsState()
    val tracks by viewModel.sequencerTracks.collectAsState()
    val isPlaying by viewModel.isPlayingSequencer.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val currentBpm by viewModel.currentBpm.collectAsState()

    var projectTitle by remember(activeProject?.title) {
        mutableStateOf(activeProject?.title ?: "Nuevo Beat Lo-Fi")
    }
    var projectGenre by remember(activeProject?.genre) {
        mutableStateOf(activeProject?.genre ?: "Hip Hop / Lo-Fi")
    }
    var showSaveModal by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = Color(0xFF0B0F19),
        topBar = {
            Surface(
                color = Color(0xFF111827),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_music_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = Color.White
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = projectTitle,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$projectGenre • $currentBpm BPM",
                            color = Color(0xFFA855F7),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Share to Chat
                    IconButton(
                        onClick = {
                            viewModel.saveActiveAudioProject(projectTitle, projectGenre)
                            viewModel.sendChatMessage(attachedAudio = activeProject)
                            onShareToChat()
                        },
                        modifier = Modifier.testTag("btn_share_beat_to_chat")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Compartir beat en chat", tint = Color(0xFFC084FC))
                    }

                    // Save Beat Button
                    Button(
                        onClick = { showSaveModal = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_save_beat"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Guardar", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Master Controls Card (BPM, Play/Stop, Led Step Indicators)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play/Pause Master Button
                        Button(
                            onClick = { viewModel.togglePlaySequencer() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlaying) Color(0xFFEF4444) else Color(0xFF10B981)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("btn_toggle_play_sequencer")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPlaying) "PAUSA" else "PLAY BEAT",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        // BPM Controller
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "TEMPO: $currentBpm BPM",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("60", color = Color(0xFF64748B), fontSize = 10.sp)
                                Slider(
                                    value = currentBpm.toFloat(),
                                    onValueChange = { viewModel.setBpm(it.toInt()) },
                                    valueRange = 60f..180f,
                                    modifier = Modifier.width(140.dp).testTag("slider_bpm"),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFA855F7),
                                        activeTrackColor = Color(0xFFA855F7),
                                        inactiveTrackColor = Color(0xFF334155)
                                    )
                                )
                                Text("180", color = Color(0xFF64748B), fontSize = 10.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Step Indicator Bar (16 LEDs)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (i in 0 until 16) {
                            val isActive = i == currentStep && isPlaying
                            val isBeatMarker = i % 4 == 0
                            Box(
                                modifier = Modifier
                                    .size(width = 16.dp, height = 6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        when {
                                            isActive -> Color(0xFF38BDF8)
                                            isBeatMarker -> Color(0xFFA855F7)
                                            else -> Color(0xFF334155)
                                        }
                                    )
                            )
                        }
                    }
                }
            }

            // 16-Step Grid Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Secuenciador de Pistas (16 Pasos)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }

            // 16-Step Instrument Tracks (Horizontal scrollable grid)
            val gridScrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                tracks.forEachIndexed { trackIdx, track ->
                    TrackRowView(
                        track = track,
                        trackIndex = trackIdx,
                        currentStep = currentStep,
                        isPlaying = isPlaying,
                        onToggleStep = { stepIdx -> viewModel.toggleStep(trackIdx, stepIdx) },
                        onToggleMute = { viewModel.toggleMuteTrack(trackIdx) },
                        onPreviewSound = { viewModel.playTrackSoundPreview(track) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Piano Roll / Synth Keyboard Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Piano, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sintetizador Melódico en Vivo",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = "Toca para reproducir",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Piano Keys (C4 - C5)
                    val notes = listOf(
                        "C4" to "Do",
                        "D4" to "Re",
                        "E4" to "Mi",
                        "F4" to "Fa",
                        "G4" to "Sol",
                        "A4" to "La",
                        "B4" to "Si",
                        "C5" to "Do"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        notes.forEach { (noteKey, noteLabel) ->
                            PianoKey(
                                noteName = noteLabel,
                                noteCode = noteKey,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.playSynthNote(noteKey) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Save Beat Modal
    if (showSaveModal) {
        AlertDialog(
            onDismissRequest = { showSaveModal = false },
            title = { Text("Guardar Beat en la Nube", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = projectTitle,
                        onValueChange = { projectTitle = it },
                        label = { Text("Título de la Pista") },
                        modifier = Modifier.fillMaxWidth().testTag("input_beat_title")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = projectGenre,
                        onValueChange = { projectGenre = it },
                        label = { Text("Género / Estilo") },
                        modifier = Modifier.fillMaxWidth().testTag("input_beat_genre")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveActiveAudioProject(projectTitle, projectGenre)
                        showSaveModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                ) {
                    Text("Guardar en OmniCloud")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveModal = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun TrackRowView(
    track: com.example.ui.viewmodel.SequencerTrack,
    trackIndex: Int,
    currentStep: Int,
    isPlaying: Boolean,
    onToggleStep: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onPreviewSound: () -> Unit
) {
    Surface(
        color = Color(0xFF182234),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track Info & Mute
            Row(
                modifier = Modifier
                    .width(100.dp)
                    .clickable(onClick = onPreviewSound),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (track.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Mute",
                        tint = if (track.isMuted) Color(0xFFEF4444) else Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = track.name,
                    color = if (track.isMuted) Color(0xFF64748B) else Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 16-Step Buttons Row
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (s in 0 until 16) {
                    val isStepActive = track.steps[s]
                    val isStepCurrent = s == currentStep && isPlaying
                    val isBeatGrouping = (s / 4) % 2 == 0

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    isStepActive && isStepCurrent -> Color(0xFF38BDF8)
                                    isStepActive -> Color(0xFFA855F7)
                                    isStepCurrent -> Color(0xFF475569)
                                    isBeatGrouping -> Color(0xFF1E293B)
                                    else -> Color(0xFF0F172A)
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (isStepCurrent) Color(0xFF38BDF8) else Color(0xFF334155),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { onToggleStep(s) }
                            .testTag("step_btn_${trackIndex}_$s"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isStepActive) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PianoKey(
    noteName: String,
    noteCode: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .testTag("piano_key_$noteCode"),
        color = Color(0xFFF8FAFC),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = noteName,
                color = Color(0xFF0F172A),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            Text(
                text = noteCode,
                color = Color(0xFF64748B),
                fontSize = 9.sp
            )
        }
    }
}

package com.example.ui.screens.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.OmniViewModel
import com.example.ui.viewmodel.SequencerTrack

/**
 * Suite de Creación Musical:
 * Interfaz profesional que incluye una rejilla de secuenciador (grid) de 16 pasos,
 * controles de transporte completos (Play, Pause, Stop, Rewind, Metrónomo, Tempo/BPM),
 * visualizador de compás en tiempo real, presets rítmicos y teclado de sintetizador.
 */
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
    val isMetronomeEnabled by viewModel.isMetronomeEnabled.collectAsState()
    val musicAutoSaveStatus by viewModel.musicAutoSaveStatus.collectAsState()
    val isMusicSaving by viewModel.isMusicSaving.collectAsState()

    var projectTitle by remember(activeProject?.title) {
        mutableStateOf(activeProject?.title ?: "Nuevo Beat OmniStudio")
    }
    var projectGenre by remember(activeProject?.genre) {
        mutableStateOf(activeProject?.genre ?: "Hip Hop / Lo-Fi")
    }
    var showSaveModal by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) } // 0: Secuenciador, 1: Canciones Publicadas
    var showAiModal by remember { mutableStateOf(false) }
    var showRenameActiveModal by remember { mutableStateOf(false) }

    val publicSongs by viewModel.publicAudioProjects.collectAsState()
    val musicFeedback by viewModel.musicFeedbackMessage.collectAsState()
    val isOwnerOfActive = activeProject?.let { viewModel.isSongOwner(it) } ?: true

    // Scroll compartido para que la cabecera de pasos y todas las pistas se desplacen en perfecta sincronía
    val gridHorizontalScrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = Color(0xFF0A0E1A),
        topBar = {
            Surface(
                color = Color(0xFF111827),
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = projectTitle,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isOwnerOfActive && activeProject != null) {
                                    IconButton(
                                        onClick = { showRenameActiveModal = true },
                                        modifier = Modifier
                                            .size(24.dp)
                                            .padding(start = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Cambiar nombre",
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    color = Color(0xFF4F46E5).copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "16 PASOS",
                                        color = Color(0xFF818CF8),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "$projectGenre • $currentBpm BPM • 4/4 LOOP",
                                color = Color(0xFFA855F7),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Botón: Crear Canción con IA
                        Button(
                            onClick = { showAiModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_open_ai_creator"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Crear con IA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Compartir en chat
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

                        // Guardar beat
                        IconButton(
                            onClick = { showSaveModal = true },
                            modifier = Modifier.testTag("btn_save_beat")
                        ) {
                            Icon(Icons.Default.CloudDone, contentDescription = "Guardar", tint = Color(0xFF38BDF8))
                        }
                    }

                    // Auto-Save Cloud Sync Bar
                    Surface(
                        color = Color(0xFF131127),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isMusicSaving) Icons.Default.Sync else Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = if (isMusicSaving) Color(0xFFF59E0B) else Color(0xFF10B981),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = musicAutoSaveStatus,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isMusicSaving) Color(0xFFFBBF24) else Color(0xFFA78BFA)
                            )
                        }
                    }

                    // Pestañas: Estudio Secuenciador vs Canciones Publicadas
                    TabRow(
                        selectedTabIndex = activeTab,
                        containerColor = Color(0xFF0F172A),
                        contentColor = Color(0xFFA855F7),
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                                color = Color(0xFFA855F7)
                            )
                        }
                    ) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Estudio & Secuenciador",
                                        fontSize = 12.sp,
                                        fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Canciones Publicadas (${publicSongs.size})",
                                        fontSize = 12.sp,
                                        fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (activeTab == 1) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                CommunitySongsView(
                    viewModel = viewModel,
                    onOpenInStudio = { activeTab = 0 },
                    onShareToChat = { song ->
                        viewModel.sendChatMessage(attachedAudio = song)
                        onShareToChat()
                    },
                    onRequestCreateWithAi = { showAiModal = true }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Banner de feedback
                musicFeedback?.let { msg ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFFC084FC), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = msg, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.clearMusicFeedback() }, modifier = Modifier.size(20.dp)) {
                                Text("✕", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Barra informativa de canción activa con controles de publicación y autoría
                activeProject?.let { currentSong ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        color = Color(0xFF1E1B4B).copy(alpha = 0.8f),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4F46E5).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = currentSong.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    if (isOwnerOfActive) {
                                        IconButton(
                                            onClick = { showRenameActiveModal = true },
                                            modifier = Modifier.size(22.dp).padding(start = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Cambiar nombre",
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (isOwnerOfActive) "👑 Eres el autor • ${currentSong.genre}" else "👤 Autor: ${currentSong.authorName}",
                                    color = Color(0xFFC7D2FE),
                                    fontSize = 10.sp
                                )
                            }

                            // Botón de Publicar / Despublicar
                            if (isOwnerOfActive) {
                                Button(
                                    onClick = { viewModel.publishSong(currentSong.id, !currentSong.isPublic) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (currentSong.isPublic) Color(0xFF059669) else Color(0xFF7C3AED)
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("btn_toggle_public_active")
                                ) {
                                    Icon(
                                        imageVector = if (currentSong.isPublic) Icons.Default.Public else Icons.Default.Public,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (currentSong.isPublic) "Publicada (Comunidad)" else "Publicar Canción",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            } else {
                                Surface(
                                    color = Color(0xFF334155),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "🔒 Solo lectura",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            // ==========================================
            // SECCIÓN 1: CONTROLES DE TRANSPORTE Y CLOCK
            // ==========================================
            TransportDeckCard(
                isPlaying = isPlaying,
                currentBpm = currentBpm,
                currentStep = currentStep,
                isMetronomeEnabled = isMetronomeEnabled,
                onTogglePlay = { viewModel.togglePlaySequencer() },
                onStop = { viewModel.stopSequencer() },
                onRewind = { viewModel.rewindSequencer() },
                onToggleMetronome = { viewModel.toggleMetronome() },
                onBpmChange = { viewModel.setBpm(it) },
                onAdjustBpm = { viewModel.adjustBpm(it) }
            )

            // ==========================================
            // SECCIÓN 2: PRESETS RÍTMICOS Y HERRAMIENTAS
            // ==========================================
            PatternPresetsBar(
                onSelectPreset = { preset -> viewModel.loadPatternPreset(preset) },
                onRandomize = { viewModel.randomizePattern() },
                onClearAll = { viewModel.clearAllSteps() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ==========================================
            // SECCIÓN 3: REJILLA DEL SECUENCIADOR (GRID)
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    // Header de la Rejilla
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color(0xFFA855F7),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Rejilla de Secuenciador (16 Pasos)",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }

                        // Indicador de estado de solo
                        val anySolo = tracks.any { it.isSolo }
                        if (anySolo) {
                            Surface(
                                color = Color(0xFFEAB308).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "SOLO ACTIVO",
                                    color = Color(0xFFFDE047),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "Toca para activar/desactivar",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Contenedor sincronizado con cabecera de compases y filas de pistas
                    SequencerGridMatrix(
                        tracks = tracks,
                        currentStep = currentStep,
                        isPlaying = isPlaying,
                        horizontalScrollState = gridHorizontalScrollState,
                        onToggleStep = { trackIdx, stepIdx -> viewModel.toggleStep(trackIdx, stepIdx) },
                        onToggleMute = { trackIdx -> viewModel.toggleMuteTrack(trackIdx) },
                        onToggleSolo = { trackIdx -> viewModel.toggleSoloTrack(trackIdx) },
                        onPreviewSound = { track -> viewModel.playTrackSoundPreview(track) },
                        onClearTrack = { trackIdx -> viewModel.clearTrack(trackIdx) },
                        onFillTrack = { trackIdx, interval -> viewModel.fillTrackEvery(trackIdx, interval) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ==========================================
            // SECCIÓN 4: SINTETIZADOR EN VIVO (PIANO ROLL)
            // ==========================================
            LiveSynthSection(
                onPlayNote = { noteCode -> viewModel.playSynthNote(noteCode) }
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

    // Modal para guardar proyecto de audio
    if (showSaveModal) {
        AlertDialog(
            onDismissRequest = { showSaveModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFFA855F7))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardar Beat en OmniStudio Cloud", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "El patrón y la configuración de tempo se sincronizarán con tu cuenta para compartirlos con la comunidad.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = projectTitle,
                        onValueChange = { projectTitle = it },
                        label = { Text("Nombre del Beat / Pista") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_beat_title")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = projectGenre,
                        onValueChange = { projectGenre = it },
                        label = { Text("Estilo / Género (ej. Lo-Fi, Trap, Synthwave)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_beat_genre")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveActiveAudioProject(projectTitle, projectGenre)
                        showSaveModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                    modifier = Modifier.testTag("btn_confirm_save_beat")
                ) {
                    Text("Guardar en la Nube")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveModal = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal para crear canción con IA (Gemini Beat Maker)
    if (showAiModal) {
        AiSongCreationDialog(
            viewModel = viewModel,
            onDismiss = { showAiModal = false },
            onSongGeneratedAndLoaded = {
                activeTab = 0
                showAiModal = false
            }
        )
    }

    // Modal para renombrar la canción activa (verificación de creador)
    if (showRenameActiveModal && activeProject != null) {
        RenameSongDialog(
            currentTitle = activeProject!!.title,
            onDismiss = { showRenameActiveModal = false },
            onConfirmRename = { newTitle ->
                viewModel.renamePublicSong(activeProject!!.id, newTitle)
                projectTitle = newTitle
                showRenameActiveModal = false
            }
        )
    }
}

// ==========================================
// COMPONENTES DE TRANSPORTE Y CLOCK
// ==========================================
@Composable
private fun TransportDeckCard(
    isPlaying: Boolean,
    currentBpm: Int,
    currentStep: Int,
    isMetronomeEnabled: Boolean,
    onTogglePlay: () -> Unit,
    onStop: () -> Unit,
    onRewind: () -> Unit,
    onToggleMetronome: () -> Unit,
    onBpmChange: (Int) -> Unit,
    onAdjustBpm: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Display LCD de Posición y Tiempo (Estilo hardware DAW)
            Surface(
                color = Color(0xFF0A0F1D),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Posición de Compás y Tiempo
                    Column {
                        Text(
                            text = "POSICIÓN",
                            color = Color(0xFF64748B),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val bar = (currentStep / 4) + 1
                        val beat = (currentStep % 4) + 1
                        Text(
                            text = "COMPÁS $bar : $beat",
                            color = Color(0xFF38BDF8),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Paso activo / total
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "PASO ACTIVO",
                            color = Color(0xFF64748B),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${currentStep + 1} / 16",
                            color = if (isPlaying) Color(0xFF34D399) else Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Tempo / BPM LCD
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "TEMPO",
                            color = Color(0xFF64748B),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$currentBpm BPM",
                            color = Color(0xFFA855F7),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Barra Principal de Botones de Transporte
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Rewind a Paso 0
                OutlinedButton(
                    onClick = onRewind,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("btn_transport_rewind"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF0F172A),
                        contentColor = Color(0xFFCBD5E1)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Ir al inicio",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Stop (Detiene y reinicia a 0)
                OutlinedButton(
                    onClick = onStop,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("btn_transport_stop"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF0F172A),
                        contentColor = Color(0xFFF87171)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Detener",
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // PLAY / PAUSE MASTER (Botón principal con máxima presencia)
                Button(
                    onClick = onTogglePlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) Color(0xFFDC2626) else Color(0xFF059669)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .shadow(elevation = if (isPlaying) 8.dp else 2.dp, shape = RoundedCornerShape(12.dp))
                        .testTag("btn_toggle_play_sequencer")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPlaying) "PAUSA" else "REPRODUCIR",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                // Metrónomo Toggle
                OutlinedButton(
                    onClick = onToggleMetronome,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("btn_transport_metronome"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isMetronomeEnabled) Color(0xFF4F46E5).copy(alpha = 0.3f) else Color(0xFF0F172A),
                        contentColor = if (isMetronomeEnabled) Color(0xFF818CF8) else Color(0xFF64748B)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isMetronomeEnabled) Color(0xFF6366F1) else Color(0xFF334155)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = if (isMetronomeEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                        contentDescription = "Metrónomo",
                        tint = if (isMetronomeEnabled) Color(0xFF818CF8) else Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Control Fino de BPM (Botones +- y Slider)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Steppers de BPM
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BpmStepButton(label = "-5", onClick = { onAdjustBpm(-5) })
                    BpmStepButton(label = "-1", onClick = { onAdjustBpm(-1) })
                    BpmStepButton(label = "+1", onClick = { onAdjustBpm(1) })
                    BpmStepButton(label = "+5", onClick = { onAdjustBpm(5) })
                }

                // Slider continuo de tempo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                ) {
                    Text("60", color = Color(0xFF64748B), fontSize = 10.sp)
                    Slider(
                        value = currentBpm.toFloat(),
                        onValueChange = { onBpmChange(it.toInt()) },
                        valueRange = 60f..180f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .testTag("slider_bpm"),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFA855F7),
                            activeTrackColor = Color(0xFFA855F7),
                            inactiveTrackColor = Color(0xFF334155)
                        )
                    )
                    Text("180", color = Color(0xFF64748B), fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Barra LED Superior de Pasos (16 Luces Guía con marcadores de compás)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (s in 0 until 16) {
                    val isActive = s == currentStep && isPlaying
                    val isDownbeat = s % 4 == 0

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    isActive -> Color(0xFF38BDF8)
                                    isDownbeat -> Color(0xFFA855F7).copy(alpha = 0.8f)
                                    else -> Color(0xFF334155)
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun BpmStepButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF0F172A),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier.size(width = 30.dp, height = 28.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = Color(0xFFCBD5E1),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ==========================================
// COMPONENTES DE PRESETS Y ACCIONES RÁPIDAS
// ==========================================
@Composable
private fun PatternPresetsBar(
    onSelectPreset: (String) -> Unit,
    onRandomize: () -> Unit,
    onClearAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "PRESETS:",
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 4.dp)
        )

        PresetChip(title = "☕ Lo-Fi (85)", onClick = { onSelectPreset("LOFI") })
        PresetChip(title = "🔥 Trap (140)", onClick = { onSelectPreset("TRAP") })
        PresetChip(title = "🪩 House (124)", onClick = { onSelectPreset("HOUSE") })
        PresetChip(title = "🎤 Boom Bap (92)", onClick = { onSelectPreset("BOOMBAP") })

        Surface(
            onClick = onRandomize,
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF0F172A),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
            modifier = Modifier.height(32.dp).testTag("btn_preset_random")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Casino, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Aleatorio", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Surface(
            onClick = onClearAll,
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF0F172A),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
            modifier = Modifier.height(32.dp).testTag("btn_clear_grid")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Limpiar", color = Color(0xFFF87171), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PresetChip(title: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E293B),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier.height(32.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ==========================================
// MATRIZ DE LA REJILLA DEL SECUENCIADOR (GRID)
// ==========================================
@Composable
private fun SequencerGridMatrix(
    tracks: List<SequencerTrack>,
    currentStep: Int,
    isPlaying: Boolean,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    onToggleStep: (Int, Int) -> Unit,
    onToggleMute: (Int) -> Unit,
    onToggleSolo: (Int) -> Unit,
    onPreviewSound: (SequencerTrack) -> Unit,
    onClearTrack: (Int) -> Unit,
    onFillTrack: (Int, Int) -> Unit
) {
    // Definición de colores distintivos por instrumento
    val trackThemes = listOf(
        TrackTheme(Color(0xFFF97316), Color(0xFFEA580C)), // Kick Drum: Naranja Fuego
        TrackTheme(Color(0xFFEC4899), Color(0xFFDB2777)), // Snare: Rosa Neón
        TrackTheme(Color(0xFFFBBF24), Color(0xFFD97706)), // Hi-Hat: Ámbar Oro
        TrackTheme(Color(0xFF10B981), Color(0xFF059669)), // Clap: Verde Esmeralda
        TrackTheme(Color(0xFF8B5CF6), Color(0xFF7C3AED)), // Synth Bass: Violeta Profundo
        TrackTheme(Color(0xFF06B6D4), Color(0xFF0891B2))  // Lead Synth: Cian Brillante
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // Cabecera de Pasos sincronizada
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Espacio fijo para alinearse con la columna de etiquetas de pista
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "PISTA / CONTROL",
                    color = Color(0xFF64748B),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Los 16 encabezados de pasos (en el contenedor con scroll horizontal sincronizado)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalScrollState)
            ) {
                for (s in 0 until 16) {
                    val isDownbeat = s % 4 == 0
                    val isCurrent = s == currentStep && isPlaying
                    val beatGroup = (s / 4) + 1
                    val stepInBeat = (s % 4) + 1

                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    isCurrent -> Color(0xFF38BDF8).copy(alpha = 0.3f)
                                    isDownbeat -> Color(0xFFA855F7).copy(alpha = 0.15f)
                                    else -> Color.Transparent
                                }
                            )
                            .border(
                                width = if (isCurrent) 1.dp else 0.dp,
                                color = if (isCurrent) Color(0xFF38BDF8) else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isDownbeat) "T$beatGroup" else "$stepInBeat",
                                color = if (isCurrent) Color(0xFF38BDF8) else if (isDownbeat) Color(0xFFA855F7) else Color(0xFF64748B),
                                fontSize = if (isDownbeat) 10.sp else 9.sp,
                                fontWeight = if (isDownbeat) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Filas de Pistas con controles independientes y pads en la rejilla
        tracks.forEachIndexed { trackIdx, track ->
            val theme = trackThemes.getOrElse(trackIdx) { trackThemes.first() }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cápsula de Control de la Pista (Izquierda Fija)
                TrackControlCapsule(
                    track = track,
                    trackIndex = trackIdx,
                    themeColor = theme.accentColor,
                    onToggleMute = { onToggleMute(trackIdx) },
                    onToggleSolo = { onToggleSolo(trackIdx) },
                    onPreviewSound = { onPreviewSound(track) },
                    onClearTrack = { onClearTrack(trackIdx) },
                    onFillTrack = { interval -> onFillTrack(trackIdx, interval) }
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Fila de 16 Pads interactivos (desplazable en sincronía con la cabecera)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScrollState)
                ) {
                    for (step in 0 until 16) {
                        val isStepActive = track.steps[step]
                        val isStepCurrent = step == currentStep && isPlaying
                        val isBeatGroupAlternate = (step / 4) % 2 == 0
                        val isBeatMarker = step % 4 == 0

                        StepPad(
                            isActive = isStepActive,
                            isCurrent = isStepCurrent,
                            isBeatGroupAlternate = isBeatGroupAlternate,
                            isBeatMarker = isBeatMarker,
                            themeColor = theme.accentColor,
                            trackMuted = track.isMuted,
                            testTag = "step_pad_${trackIdx}_$step",
                            onClick = { onToggleStep(trackIdx, step) }
                        )
                    }
                }
            }
        }
    }
}

private data class TrackTheme(val accentColor: Color, val darkColor: Color)

@Composable
private fun TrackControlCapsule(
    track: SequencerTrack,
    trackIndex: Int,
    themeColor: Color,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onPreviewSound: () -> Unit,
    onClearTrack: () -> Unit,
    onFillTrack: (Int) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .width(96.dp)
            .height(44.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Nombre y Botón de Audición al tocar
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onPreviewSound)
                    .testTag("track_audition_$trackIndex")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(themeColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = track.name,
                        color = if (track.isMuted) Color(0xFF64748B) else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "Toca para oír",
                    color = Color(0xFF64748B),
                    fontSize = 8.sp
                )
            }

            // Botones de MUTE [M] y SOLO [S]
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // Solo [S]
                Surface(
                    onClick = onToggleSolo,
                    shape = RoundedCornerShape(4.dp),
                    color = if (track.isSolo) Color(0xFFEAB308) else Color(0xFF0F172A),
                    modifier = Modifier
                        .size(width = 18.dp, height = 24.dp)
                        .testTag("track_solo_$trackIndex")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "S",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (track.isSolo) Color.Black else Color(0xFF94A3B8)
                        )
                    }
                }

                // Mute [M]
                Surface(
                    onClick = onToggleMute,
                    shape = RoundedCornerShape(4.dp),
                    color = if (track.isMuted) Color(0xFFEF4444) else Color(0xFF0F172A),
                    modifier = Modifier
                        .size(width = 18.dp, height = 24.dp)
                        .testTag("track_mute_$trackIndex")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "M",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (track.isMuted) Color.White else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pad individual de paso en la rejilla del secuenciador
 */
@Composable
private fun StepPad(
    isActive: Boolean,
    isCurrent: Boolean,
    isBeatGroupAlternate: Boolean,
    isBeatMarker: Boolean,
    themeColor: Color,
    trackMuted: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val padColor by animateColorAsState(
        targetValue = when {
            isActive && isCurrent -> Color.White
            isActive && trackMuted -> themeColor.copy(alpha = 0.35f)
            isActive -> themeColor
            isCurrent -> Color(0xFF475569)
            isBeatGroupAlternate -> Color(0xFF1E293B)
            else -> Color(0xFF0F172A)
        },
        animationSpec = tween(durationMillis = 50),
        label = "padColor"
    )

    Box(
        modifier = Modifier
            .width(36.dp)
            .height(44.dp)
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(padColor)
            .border(
                width = if (isCurrent) 1.5.dp else if (isBeatMarker) 1.dp else 0.5.dp,
                color = when {
                    isCurrent -> Color(0xFF38BDF8)
                    isActive -> themeColor.copy(alpha = 0.8f)
                    isBeatMarker -> Color(0xFF334155)
                    else -> Color(0xFF1E293B)
                },
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        // Indicador central para pasos activos
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(if (isCurrent) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent) themeColor else Color.White.copy(alpha = 0.9f))
            )
        } else if (isBeatMarker) {
            // Pequeño punto tenue de referencia para tiempos principales 1, 5, 9, 13
            Box(
                modifier = Modifier
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF475569))
            )
        }
    }
}

// ==========================================
// TECLADO DE SINTETIZADOR MELÓDICO EN VIVO
// ==========================================
@Composable
private fun LiveSynthSection(
    onPlayNote: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Piano,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sintetizador Melódico (Acompañamiento)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                Text(
                    text = "Toca las teclas para improvisar",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Teclas de Do a Do (C4 a C5)
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
                    .height(76.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                notes.forEach { (noteKey, noteLabel) ->
                    SynthPianoKey(
                        noteName = noteLabel,
                        noteCode = noteKey,
                        modifier = Modifier.weight(1f),
                        onClick = { onPlayNote(noteKey) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SynthPianoKey(
    noteName: String,
    noteCode: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag("piano_key_$noteCode"),
        color = if (isPressed) Color(0xFF38BDF8) else Color(0xFFF8FAFC),
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

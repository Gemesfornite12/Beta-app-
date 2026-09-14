package com.example.ui.screens.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.OmniViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSongCreationDialog(
    viewModel: OmniViewModel,
    onDismiss: () -> Unit,
    onSongGeneratedAndLoaded: () -> Unit
) {
    val isGenerating by viewModel.isGeneratingSong.collectAsState()
    val generationStatus by viewModel.aiGenerationStatus.collectAsState()
    val lastResult by viewModel.lastAiResult.collectAsState()
    val activeProject by viewModel.activeAudioProject.collectAsState()

    var songTitle by remember { mutableStateOf("") }
    var songDescription by remember { mutableStateOf("") }
    var autoPublish by remember { mutableStateOf(false) }

    val inspirationChips = listOf(
        "🔥 Trap Pesado 808" to "Beat de trap moderno con bajos profundos 808, hi-hats acelerados y melodía oscura",
        "☕ Lo-Fi Relajante" to "Ritmo chillhop cálido y suave con arpegios lentos para estudiar o concentrarse",
        "🪩 Deep House Bailable" to "Groove electrónico four-on-the-floor con sintetizador envolvente y ritmo alegre de club",
        "🚀 Synthwave Retro 80s" to "Estilo ciberpunk ochentero con bajo galopante y sintetizador brillante nostálgico",
        "🎤 Boom Bap Clásico" to "Ritmo hip-hop de la vieja escuela con caja contundente y groove funk urbano",
        "🌌 Pop Melódico Espacial" to "Canción pop con armonía brillante, sintetizadores etéreos y tempo dinámico"
    )

    AlertDialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dialog_ai_song_creator"),
        containerColor = Color(0xFF111827),
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFA855F7).copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Crear Canción con IA",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Gemini Studio Beat Maker",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Escribe el nombre y describe cómo imaginas la canción. La IA compondrá las pistas de batería, bajo, sintetizador y tempo.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Campo: Nombre de la canción
                Text(
                    text = "NOMBRE DE LA CANCIÓN",
                    color = Color(0xFFA855F7),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = songTitle,
                    onValueChange = { songTitle = it },
                    placeholder = { Text("Ej. Cyber Horizon, Noche en Tokio, Beat 808...", color = Color(0xFF64748B)) },
                    singleLine = true,
                    enabled = !isGenerating,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFA855F7),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_ai_song_title")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Campo: Descripción / Prompt
                Text(
                    text = "DESCRIPCIÓN / ESTILO MUSICAL",
                    color = Color(0xFFA855F7),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = songDescription,
                    onValueChange = { songDescription = it },
                    placeholder = {
                        Text(
                            "Ej. Ritmo trap con sintetizador suave, bajo potente y platillos acelerados...",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    },
                    minLines = 3,
                    maxLines = 5,
                    enabled = !isGenerating,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFA855F7),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_ai_song_description")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Inspiration Chips
                Text(
                    text = "INSPIRACIÓN RÁPIDA",
                    color = Color(0xFF64748B),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    inspirationChips.forEach { (chipLabel, promptTemplate) ->
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !isGenerating) {
                                    if (songTitle.isBlank()) {
                                        songTitle = chipLabel.substringAfter(" ").trim()
                                    }
                                    songDescription = promptTemplate
                                }
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp)),
                            color = Color(0xFF1E293B)
                        ) {
                            Text(
                                text = chipLabel,
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Estado de Generación
                if (isGenerating) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF1E1B4B),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6366F1))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFFA855F7),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Componiendo canción...",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = generationStatus ?: "Sintetizando pistas y armonía con Gemini AI...",
                                    color = Color(0xFFC7D2FE),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Resumen del último resultado si ya fue generado
                if (!isGenerating && lastResult != null && activeProject?.title == lastResult?.title) {
                    val res = lastResult!!
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "¡Canción generada con éxito!",
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${res.genre} • ${res.bpm} BPM • Mood: ${res.mood}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = res.aiInsight,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Notas de sintetizador: ${res.melodyNotes.joinToString(" - ")}",
                                color = Color(0xFF818CF8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isGenerating) {
                Button(
                    onClick = {},
                    enabled = false,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) {
                    Text("Generando...")
                }
            } else if (lastResult != null && activeProject?.title == lastResult?.title) {
                Row {
                    OutlinedButton(
                        onClick = {
                            activeProject?.let { p ->
                                viewModel.publishSong(p.id, true)
                            }
                            onDismiss()
                            onSongGeneratedAndLoaded()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Publicar Ahora", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            onDismiss()
                            onSongGeneratedAndLoaded()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Abrir en Secuenciador", fontSize = 12.sp)
                    }
                }
            } else {
                Button(
                    onClick = {
                        val title = songTitle.ifBlank { "Beat ${System.currentTimeMillis() % 1000}" }
                        val desc = songDescription.ifBlank { "Pista instrumental creada por Gemini AI" }
                        viewModel.generateSongWithAi(title, desc) {
                            if (autoPublish) {
                                viewModel.publishActiveSong(true)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("btn_start_ai_generation")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("✨ Componer con IA", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!isGenerating) {
                TextButton(onClick = onDismiss) {
                    Text("Cerrar", color = Color(0xFF94A3B8))
                }
            }
        }
    )
}

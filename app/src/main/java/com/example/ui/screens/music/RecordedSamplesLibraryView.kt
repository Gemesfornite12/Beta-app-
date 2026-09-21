package com.example.ui.screens.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RecordedAudioSample
import com.example.ui.viewmodel.OmniViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordedSamplesLibraryView(
    viewModel: OmniViewModel,
    onOpenRecorder: () -> Unit,
    onOpenInStudio: () -> Unit
) {
    val context = LocalContext.current
    val samples by viewModel.recordedSamples.collectAsState()
    val previewingId by viewModel.previewingSampleId.collectAsState()
    var selectedCategoryFilter by remember { mutableStateOf("Todos") }

    val categories = listOf("Todos", "Vocal", "Beatbox", "Instrumento", "Efecto FX", "Sintetizador")
    val filteredSamples = if (selectedCategoryFilter == "Todos") {
        samples
    } else {
        samples.filter { it.category.equals(selectedCategoryFilter, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E1A))
            .padding(14.dp)
    ) {
        // Banner Superior: Grabar Nueva Muestra WAV
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4F46E5).copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🎙️ Muestras de Audio WAV",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Graba fragmentos de voz, beatbox o instrumentos en formato WAV de 16-bit para utilizarlos en tus ritmos del secuenciador.",
                        color = Color(0xFFC7D2FE),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onOpenRecorder,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("btn_record_new_sample_banner")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Grabar WAV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Filtro de Categorías
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategoryFilter == cat
                Surface(
                    onClick = { selectedCategoryFilter = cat },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color(0xFF7C3AED) else Color(0xFF1E293B),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFFA855F7) else Color(0xFF334155)
                    )
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Lista de Muestras Grabadas
        if (filteredSamples.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (samples.isEmpty()) "No tienes muestras de audio grabadas" else "No hay muestras en esta categoría",
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Toca el botón 'Grabar WAV' para capturar tu primer sample.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredSamples, key = { it.id }) { sample ->
                    RecordedSampleCard(
                        sample = sample,
                        isPlaying = previewingId == sample.id,
                        onTogglePlay = {
                            if (previewingId == sample.id) {
                                viewModel.stopSamplePreview()
                            } else {
                                viewModel.playSamplePreview(sample)
                            }
                        },
                        onAddToSequencer = {
                            viewModel.addRecordedSampleToSequencer(sample)
                            onOpenInStudio()
                        },
                        onExport = {
                            viewModel.exportSampleToDownloads(context, sample)
                        },
                        onDelete = {
                            viewModel.deleteRecordedSample(sample)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordedSampleCard(
    sample: RecordedAudioSample,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onAddToSequencer: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(sample.createdAt))
    val sizeKb = sample.fileSizeBytes / 1024
    val durationSec = sample.durationMs / 1000.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_sample_${sample.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Botón Play / Pause de Audición
                    Surface(
                        shape = CircleShape,
                        color = if (isPlaying) Color(0xFF38BDF8) else Color(0xFF1E293B),
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onTogglePlay)
                            .testTag("btn_play_sample_${sample.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                tint = if (isPlaying) Color.Black else Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = sample.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${String.format("%.1f", durationSec)}s • $sizeKb KB • WAV 44.1kHz • $dateStr",
                                color = Color(0xFF64748B),
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Badge de categoría
                Surface(
                    color = Color(0xFF7C3AED).copy(alpha = 0.25f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = sample.category,
                        color = Color(0xFFC084FC),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Waveform bar representativa decorativa
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF0A0F1D))
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val pseudoWave = remember(sample.id) {
                    List(24) { i ->
                        (0.2f + (Math.sin(i * 0.6 + sample.id.toDouble()) * 0.4f).toFloat().coerceIn(0.1f, 0.9f))
                    }
                }
                pseudoWave.forEach { h ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height((h * 20).dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (isPlaying) Color(0xFF38BDF8) else Color(0xFF475569))
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Botones de Acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Añadir al secuenciador
                Button(
                    onClick = onAddToSequencer,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("btn_add_to_seq_${sample.id}")
                ) {
                    Icon(Icons.Default.QueueMusic, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Usar en Secuenciador", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Exportar WAV a almacenamiento
                    IconButton(
                        onClick = onExport,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_export_sample_${sample.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Exportar WAV",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Eliminar
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_delete_sample_${sample.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

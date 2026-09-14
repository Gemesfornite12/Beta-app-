package com.example.ui.screens.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import coil.compose.AsyncImage
import com.example.data.model.AudioProject
import com.example.data.model.DocumentItem

data class PresetGif(
    val title: String,
    val category: String,
    val url: String,
    val emoji: String
)

data class PresetMedia(
    val title: String,
    val description: String,
    val type: String, // "image" or "video"
    val url: String,
    val thumbnail: String? = null
)

val PRESET_GIFS = listOf(
    PresetGif("Celebración", "Éxito", "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=400&q=80", "🎉"),
    PresetGif("Fuego Beat", "Música", "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=400&q=80", "🔥"),
    PresetGif("Lanzamiento Cohete", "Éxito", "https://images.unsplash.com/photo-1517976487502-570a24177341?w=400&q=80", "🚀"),
    PresetGif("Aplausos de Equipo", "Reacción", "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=400&q=80", "👏"),
    PresetGif("Modo Producción", "Música", "https://images.unsplash.com/photo-1598488035139-bdbb2231ce04?w=400&q=80", "🎧"),
    PresetGif("Idea Brillante", "Creativo", "https://images.unsplash.com/photo-1498050108023-c5249f4df085?w=400&q=80", "💡"),
    PresetGif("Risas y Buena Onda", "Reacción", "https://images.unsplash.com/photo-1543807535-eceef0bc6599?w=400&q=80", "😂"),
    PresetGif("Aprobado 100%", "Reacción", "https://images.unsplash.com/photo-1579208575657-c595a05383b7?w=400&q=80", "💯")
)

val PRESET_PHOTOS = listOf(
    PresetMedia(
        title = "Mockup de Diapositivas OmniStudio",
        description = "Diseño de presentación generado para la reunión técnica",
        type = "image",
        url = "https://images.unsplash.com/photo-1557804506-669a67965ba0?w=600&q=80"
    ),
    PresetMedia(
        title = "Consola de Mezcla en Estudio",
        description = "Captura de ecualización y efectos master",
        type = "image",
        url = "https://images.unsplash.com/photo-1598488035139-bdbb2231ce04?w=600&q=80"
    ),
    PresetMedia(
        title = "Diagrama de Arquitectura Firestore",
        description = "Esquema de sincronización bidireccional Room-Cloud",
        type = "image",
        url = "https://images.unsplash.com/photo-1460925895917-afdab827c52f?w=600&q=80"
    ),
    PresetMedia(
        title = "Sesión de Colaboración Remota",
        description = "Captura del equipo trabajando en vivo",
        type = "image",
        url = "https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=600&q=80"
    )
)

val PRESET_VIDEOS = listOf(
    PresetMedia(
        title = "Demo en Vivo Secuenciador Lo-Fi",
        description = "0:34 • Grabación en tiempo real del beat OmniStudio",
        type = "video",
        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
        thumbnail = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&q=80"
    ),
    PresetMedia(
        title = "Revisión de Diapositivas PPTX",
        description = "1:15 • Walkthrough de la presentación exportada",
        type = "video",
        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
        thumbnail = "https://images.unsplash.com/photo-1557804506-669a67965ba0?w=600&q=80"
    ),
    PresetMedia(
        title = "Sesión de Síntesis Analógica",
        description = "0:45 • Prueba de oscilador senoidal en tiempo real",
        type = "video",
        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
        thumbnail = "https://images.unsplash.com/photo-1598488035139-bdbb2231ce04?w=600&q=80"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPickerSheet(
    channelName: String,
    docs: List<DocumentItem>,
    audios: List<AudioProject>,
    onSendMedia: (type: String, url: String, caption: String) -> Unit,
    onSendDoc: (DocumentItem) -> Unit,
    onSendAudio: (AudioProject) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Fotos, 1: Videos, 2: GIFs, 3: Archivos
    val tabs = listOf("📷 Fotos", "🎥 Videos", "🎭 GIFs", "📁 Archivos")

    // Photo Picker nativo de Android (zero permissions)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onSendMedia("image", uri.toString(), "📷 Foto seleccionada desde el dispositivo")
            onDismiss()
        }
    }

    // Video Picker nativo de Android (zero permissions)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onSendMedia("video", uri.toString(), "🎥 Video seleccionado desde el dispositivo")
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("dialog_media_picker"),
        content = {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Compartir Multimedia",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "En canal #$channelName",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Pestañas de categorías
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color(0xFF0F172A),
                        contentColor = Color(0xFF818CF8),
                        edgePadding = 4.dp,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = Color(0xFF818CF8)
                            )
                        }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        text = title,
                                        fontSize = 13.sp,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedTab == index) Color(0xFF818CF8) else Color(0xFF94A3B8)
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Contenido según pestaña
                    when (selectedTab) {
                        0 -> {
                            // PESTAÑA: FOTOS
                            Column {
                                // Botón para abrir galería local
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF334155),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                        .testTag("btn_pick_device_photo")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF38BDF8))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("Elegir de mi galería", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("Abre el selector nativo de Android", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text("O comparte fotos del proyecto OmniStudio:", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(6.dp))

                                LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(PRESET_PHOTOS) { photo ->
                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onSendMedia("image", photo.url, photo.title)
                                                    onDismiss()
                                                }
                                        ) {
                                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                AsyncImage(
                                                    model = photo.url,
                                                    contentDescription = photo.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(60.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(photo.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text(photo.description, color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // PESTAÑA: VIDEOS
                            Column {
                                // Botón para abrir videos locales
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF334155),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            videoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                            )
                                        }
                                        .testTag("btn_pick_device_video")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Color(0xFFA855F7))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("Elegir video del dispositivo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("Video clip o captura de pantalla", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text("O comparte grabaciones de producción:", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(6.dp))

                                LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(PRESET_VIDEOS) { vid ->
                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onSendMedia("video", vid.url, vid.title)
                                                    onDismiss()
                                                }
                                        ) {
                                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(60.dp)) {
                                                    AsyncImage(
                                                        model = vid.thumbnail,
                                                        contentDescription = vid.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(8.dp))
                                                    )
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = Color.Black.copy(alpha = 0.6f),
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .align(Alignment.Center)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(vid.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text(vid.description, color = Color(0xFFA855F7), fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // PESTAÑA: GIFS
                            Column {
                                Text("Elige un GIF animado para responder:", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))

                                LazyColumn(modifier = Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(PRESET_GIFS) { gif ->
                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onSendMedia("gif", gif.url, "${gif.emoji} ${gif.title}")
                                                    onDismiss()
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(60.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                ) {
                                                    AsyncImage(
                                                        model = gif.url,
                                                        contentDescription = gif.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFFEF4444),
                                                        modifier = Modifier
                                                            .align(Alignment.TopStart)
                                                            .padding(2.dp)
                                                    ) {
                                                        Text("GIF", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 3.dp))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(gif.emoji, fontSize = 16.sp)
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(gif.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    }
                                                    Text("Categoría: ${gif.category}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        3 -> {
                            // PESTAÑA: ARCHIVOS (Documentos y Beats)
                            LazyColumn(modifier = Modifier.height(280.dp)) {
                                item {
                                    Text("Documentos recientes:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF38BDF8))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (docs.isEmpty()) {
                                        Text("No hay documentos aún.", color = Color.Gray, fontSize = 11.sp)
                                    } else {
                                        docs.take(4).forEach { doc ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .clickable {
                                                        onSendDoc(doc)
                                                        onDismiss()
                                                    },
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                                            ) {
                                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF38BDF8))
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(doc.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                        Text(".${doc.currentFormat.extension.uppercase()} • ${doc.authorEmail}", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text("Pistas musicales / Beats:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFA855F7))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (audios.isEmpty()) {
                                        Text("No hay pistas creadas aún.", color = Color.Gray, fontSize = 11.sp)
                                    } else {
                                        audios.take(4).forEach { audio ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .clickable {
                                                        onSendAudio(audio)
                                                        onDismiss()
                                                    },
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                                            ) {
                                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFFA855F7))
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(audio.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                        Text("${audio.genre} • ${audio.bpm} BPM", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

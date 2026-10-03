package com.example.ui.screens.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.res.painterResource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.AudioProject
import com.example.data.model.DocumentFormat
import com.example.data.model.DocumentItem

private fun getFileNameFromUri(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "archivo_adjunto"
}

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
    PresetGif("Celebración", "Éxito", "https://upload.wikimedia.org/wikipedia/commons/2/2c/A_light_shining_disco_ball.gif", "🎉"),
    PresetGif("Fuego Beat", "Música", "https://upload.wikimedia.org/wikipedia/commons/e/eb/Audio_waveform_visualization.gif", "🔥"),
    PresetGif("Lanzamiento Cohete", "Éxito", "https://upload.wikimedia.org/wikipedia/commons/d/d7/Space_Shuttle_at_launch_pad_39A.gif", "🚀"),
    PresetGif("Aplausos de Equipo", "Reacción", "https://upload.wikimedia.org/wikipedia/commons/e/e5/Clapping_hands_animation.gif", "👏"),
    PresetGif("Modo Producción", "Música", "https://upload.wikimedia.org/wikipedia/commons/3/3b/Simple_Globe_Animation.gif", "🎧"),
    PresetGif("Idea Brillante", "Creativo", "https://upload.wikimedia.org/wikipedia/commons/c/c5/Lightbulb_idea_animation.gif", "💡"),
    PresetGif("Risas y Buena Onda", "Reacción", "https://upload.wikimedia.org/wikipedia/commons/1/11/Animated_smiley_face_laughing.gif", "😂"),
    PresetGif("Aprobado 100%", "Reacción", "https://upload.wikimedia.org/wikipedia/commons/8/82/Checked_checkbox_animation.gif", "💯")
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
        url = "https://assets.mixkit.co/videos/preview/mixkit-hands-of-a-man-working-on-a-computer-1618-small.mp4",
        thumbnail = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&q=80"
    ),
    PresetMedia(
        title = "Revisión de Diapositivas PPTX",
        description = "1:15 • Walkthrough de la presentación exportada",
        type = "video",
        url = "https://assets.mixkit.co/videos/preview/mixkit-keyboard-typing-close-up-1616-small.mp4",
        thumbnail = "https://images.unsplash.com/photo-1557804506-669a67965ba0?w=600&q=80"
    ),
    PresetMedia(
        title = "Sesión de Síntesis Analógica",
        description = "0:45 • Prueba de oscilador senoidal en tiempo real",
        type = "video",
        url = "https://assets.mixkit.co/videos/preview/mixkit-set-of-plateaus-seen-from-the-sky-in-a-sunset-26070-small.mp4",
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
    val context = LocalContext.current
    val gifImageLoader = remember(context) {
        coil.ImageLoader.Builder(context)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(coil.decode.ImageDecoderDecoder.Factory())
                } else {
                    add(coil.decode.GifDecoder.Factory())
                }
            }
            .build()
    }
    var selectedTab by remember { mutableStateOf(0) } // 0: Fotos, 1: Videos, 2: GIFs, 3: Música/Audio, 4: YouTube, 5: Archivos
    val tabs = listOf("📷 Fotos", "🎥 Videos", "🎭 GIFs", "🎵 Música/Audio", "▶️ YouTube", "📁 Archivos")

    // Estados de GIPHY & KLIPY Live API
    var activeGifProvider by remember { mutableStateOf("giphy") } // "giphy" or "klipy"
    var giphySearchQuery by remember { mutableStateOf("") }
    var giphyType by remember { mutableStateOf("gifs") } // "gifs" or "stickers"
    var giphyItems by remember { mutableStateOf<List<com.example.data.giphy.GiphyItem>>(emptyList()) }
    var isGiphyLoading by remember { mutableStateOf(false) }
    var giphyError by remember { mutableStateOf<String?>(null) }

    var klipyItems by remember { mutableStateOf<List<com.example.data.klipy.KlipyItem>>(emptyList()) }
    var isKlipyLoading by remember { mutableStateOf(false) }
    var klipyError by remember { mutableStateOf<String?>(null) }

    // Estados de YouTube API & Search
    var youtubeSearchQuery by remember { mutableStateOf("") }
    var youtubeActiveCategory by remember { mutableStateOf("🔥 Tendencias") }
    var youtubeVideos by remember { mutableStateOf<List<com.example.data.youtube.YouTubeVideo>>(com.example.data.youtube.YouTubeClient.CURATED_PRESETS) }
    var isYouTubeLoading by remember { mutableStateOf(false) }
    var directYouTubeUrlInput by remember { mutableStateOf("") }

    val giphyApiKey = try { com.example.BuildConfig.GIPHY_API_KEY } catch (_: Exception) { "" }
    val klipyApiKey = try { com.example.BuildConfig.KLIPY_API_KEY } catch (_: Exception) { "" }
    val youtubeApiKey = try {
        val key = com.example.BuildConfig.GEMINI_API_KEY
        if (key.isNotBlank()) key else ""
    } catch (_: Exception) { "" }

    LaunchedEffect(youtubeSearchQuery, youtubeActiveCategory) {
        isYouTubeLoading = true
        try {
            val query = if (youtubeSearchQuery.isNotBlank()) {
                youtubeSearchQuery
            } else {
                when (youtubeActiveCategory) {
                    "🔥 Tendencias" -> ""
                    "🎵 Música" -> "musica en vivo official"
                    "🤖 IA & Tech" -> "google ai gemini tech"
                    "🎧 Lo-Fi Beats" -> "lofi hip hop radio beats"
                    "💻 Android & Dev" -> "android jetpack compose kotlin"
                    "🎙️ Podcasts" -> "podcast tech ai"
                    "🎬 Trailers" -> "movie trailers official 4k"
                    else -> youtubeActiveCategory
                }
            }
            val results = com.example.data.youtube.YouTubeClient.search(query, youtubeApiKey)
            youtubeVideos = results
        } catch (e: Exception) {
            android.util.Log.w("MediaPickerSheet", "Error buscando en YouTube: ${e.message}")
            youtubeVideos = com.example.data.youtube.YouTubeClient.CURATED_PRESETS
        } finally {
            isYouTubeLoading = false
        }
    }

    LaunchedEffect(giphySearchQuery, giphyType, activeGifProvider) {
        if (activeGifProvider == "giphy") {
            if (giphyApiKey.isBlank()) {
                giphyError = "API Key no configurada"
                return@LaunchedEffect
            }
            isGiphyLoading = true
            giphyError = null
            try {
                val response = if (giphySearchQuery.isBlank()) {
                    if (giphyType == "gifs") {
                        com.example.data.giphy.GiphyClient.apiService.getTrendingGifs(apiKey = giphyApiKey, limit = 25)
                    } else {
                        com.example.data.giphy.GiphyClient.apiService.getTrendingStickers(apiKey = giphyApiKey, limit = 25)
                    }
                } else {
                    if (giphyType == "gifs") {
                        com.example.data.giphy.GiphyClient.apiService.searchGifs(apiKey = giphyApiKey, query = giphySearchQuery, limit = 25)
                    } else {
                        com.example.data.giphy.GiphyClient.apiService.searchStickers(apiKey = giphyApiKey, query = giphySearchQuery, limit = 25)
                    }
                }
                giphyItems = response.data
            } catch (e: Exception) {
                android.util.Log.w("MediaPickerSheet", "Error Giphy: ${e.message}")
                giphyError = "GIPHY: Presets locales recomendados"
                giphyItems = emptyList()
            } finally {
                isGiphyLoading = false
            }
        } else {
            if (klipyApiKey.isBlank()) {
                klipyError = "API Key de KLIPY no configurada"
                return@LaunchedEffect
            }
            isKlipyLoading = true
            klipyError = null
            try {
                val response = if (giphySearchQuery.isBlank()) {
                    if (giphyType == "gifs") {
                        com.example.data.klipy.KlipyClient.apiService.getTrendingGifs(apiKey = klipyApiKey, limit = 25)
                    } else {
                        com.example.data.klipy.KlipyClient.apiService.getTrendingStickers(apiKey = klipyApiKey, limit = 25)
                    }
                } else {
                    if (giphyType == "gifs") {
                        com.example.data.klipy.KlipyClient.apiService.searchGifs(apiKey = klipyApiKey, query = giphySearchQuery, limit = 25)
                    } else {
                        com.example.data.klipy.KlipyClient.apiService.searchStickers(apiKey = klipyApiKey, query = giphySearchQuery, limit = 25)
                    }
                }
                klipyItems = response.data
            } catch (e: Exception) {
                android.util.Log.w("MediaPickerSheet", "Error Klipy: ${e.message}")
                klipyError = "KLIPY: Presets locales recomendados"
                klipyItems = emptyList()
            } finally {
                isKlipyLoading = false
            }
        }
    }

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

    // Audio/Music Picker nativo de Android
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = getFileNameFromUri(context, uri)
            val audioProj = AudioProject(
                id = System.currentTimeMillis(),
                title = fileName,
                description = "Archivo de audio seleccionado desde almacenamiento local",
                genre = "Audio Local",
                bpm = 120,
                patternDataJson = "[]",
                authorEmail = "local",
                isPublic = true
            )
            onSendAudio(audioProj)
            onDismiss()
        }
    }

    // File/Document Picker nativo de Android (cualquier archivo del dispositivo con detección automática)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = getFileNameFromUri(context, uri)
            val ext = fileName.substringAfterLast(".", "bin").lowercase()
            val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()

            val isVideo = ext in listOf("mp4", "mkv", "mov", "webm", "avi", "3gp", "flv", "wmv", "m4v") || mimeType.startsWith("video/")
            val isGif = ext == "gif" || mimeType == "image/gif"
            val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "bmp", "heic") || mimeType.startsWith("image/")
            val isAudio = ext in listOf("mp3", "wav", "m4a", "aac", "ogg", "flac", "opus") || mimeType.startsWith("audio/")

            when {
                isVideo -> {
                    onSendMedia("video", uri.toString(), "🎥 $fileName")
                }
                isGif -> {
                    onSendMedia("gif", uri.toString(), "🎭 $fileName")
                }
                isImage -> {
                    onSendMedia("image", uri.toString(), "📷 $fileName")
                }
                isAudio -> {
                    val audioProj = AudioProject(
                        id = System.currentTimeMillis(),
                        title = fileName,
                        description = "Archivo de audio seleccionado desde almacenamiento local",
                        genre = "Audio Local",
                        bpm = 120,
                        patternDataJson = "[]",
                        authorEmail = "local",
                        isPublic = true
                    )
                    onSendAudio(audioProj)
                }
                else -> {
                    val format = when (ext) {
                        "pdf" -> DocumentFormat.PDF
                        "docx", "doc" -> DocumentFormat.DOCX
                        "md" -> DocumentFormat.MARKDOWN
                        "html" -> DocumentFormat.HTML
                        "txt" -> DocumentFormat.TXT
                        else -> DocumentFormat.TXT
                    }
                    val docItem = DocumentItem(
                        id = System.currentTimeMillis(),
                        title = fileName,
                        content = "Archivo adjunto enviado desde el almacenamiento local: $uri",
                        currentFormat = format,
                        authorEmail = "local"
                    )
                    onSendDoc(docItem)
                }
            }
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
                            // PESTAÑA: GIFS & STICKERS (GIPHY & KLIPY API INTEGRATION)
                            Column {
                                // Selector de Proveedor
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("giphy" to "👾 GIPHY API", "klipy" to "🐔 KLIPY API").forEach { (providerKey, label) ->
                                        val isSelected = activeGifProvider == providerKey
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) Color(0xFF6366F1) else Color(0xFF0F172A),
                                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF818CF8) else Color(0xFF1E293B)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                                .clickable { activeGifProvider = providerKey }
                                                .testTag("provider_tab_$providerKey")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = label,
                                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                // Barra de Búsqueda
                                OutlinedTextField(
                                    value = giphySearchQuery,
                                    onValueChange = { giphySearchQuery = it },
                                    placeholder = { 
                                        Text(
                                            text = if (activeGifProvider == "giphy") "Buscar en GIPHY..." else "Buscar en KLIPY...", 
                                            color = Color(0xFF94A3B8), 
                                            fontSize = 12.sp
                                        ) 
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Buscar",
                                            tint = Color(0xFF818CF8),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF0F172A),
                                        unfocusedContainerColor = Color(0xFF0F172A),
                                        focusedBorderColor = Color(0xFF4F46E5),
                                        unfocusedBorderColor = Color(0xFF334155),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("giphy_search_input")
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Selectores de Tipo: Gifs vs Stickers
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("gifs" to "👾 GIFs", "stickers" to "✨ Stickers").forEach { (typeKey, label) ->
                                        val isSelected = giphyType == typeKey
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (isSelected) Color(0xFF4F46E5) else Color(0xFF1E293B),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(32.dp)
                                                .clickable { giphyType = typeKey }
                                                .testTag("giphy_tab_$typeKey")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = label,
                                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Attribution / branding banner (Required KLIPY Branding)
                                if (activeGifProvider == "klipy") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Image(
                                            painter = painterResource(id = com.example.R.drawable.img_powered_klipy_1789778541743),
                                            contentDescription = "Powered by KLIPY",
                                            modifier = Modifier
                                                .height(32.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                // Cuerpo de Resultados
                                val isLoading = if (activeGifProvider == "giphy") isGiphyLoading else isKlipyLoading
                                val errorMsg = if (activeGifProvider == "giphy") giphyError else klipyError
                                val itemsListSize = if (activeGifProvider == "giphy") giphyItems.size else klipyItems.size

                                if (isLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Color(0xFF818CF8), modifier = Modifier.size(28.dp))
                                    }
                                } else if (errorMsg != null && itemsListSize == 0) {
                                    // Fallback a Presets locales si no hay internet o clave inválida
                                    Column {
                                        Text(errorMsg ?: "Mostrando presets locales", color = Color(0xFF818CF8), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                                        
                                        LazyColumn(modifier = Modifier.height(180.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                                        Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp))) {
                                                            AsyncImage(
                                                                model = gif.url,
                                                                imageLoader = gifImageLoader,
                                                                contentDescription = gif.title,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxWidth()
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column {
                                                            Row {
                                                                Text(gif.emoji, fontSize = 14.sp)
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text(gif.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                            }
                                                            Text("Categoría: ${gif.category}", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    if (activeGifProvider == "giphy") {
                                        // Grid Virtual chunking 2 por fila para Giphy
                                        val rowItemsList = giphyItems.chunked(2)
                                        if (rowItemsList.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(200.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("No se encontraron resultados en GIPHY", color = Color(0xFF64748B), fontSize = 12.sp)
                                            }
                                        } else {
                                            LazyColumn(modifier = Modifier.height(200.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                items(rowItemsList) { rowItems ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        for (item in rowItems) {
                                                            val rawUrl = item.images.fixedHeight?.url ?: item.images.original?.url ?: ""
                                                            val finalUrl = rawUrl.replace("http://", "https://")
                                                            Card(
                                                                shape = RoundedCornerShape(12.dp),
                                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .clickable {
                                                                        if (finalUrl.isNotEmpty()) {
                                                                            onSendMedia(if (giphyType == "gifs") "gif" else "sticker", finalUrl, item.title ?: "Giphy ${giphyType}")
                                                                            onDismiss()
                                                                        }
                                                                    }
                                                            ) {
                                                                Column(modifier = Modifier.padding(6.dp)) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .height(80.dp)
                                                                            .clip(RoundedCornerShape(8.dp))
                                                                    ) {
                                                                        AsyncImage(
                                                                            model = finalUrl,
                                                                            imageLoader = gifImageLoader,
                                                                            contentDescription = item.title,
                                                                            contentScale = ContentScale.Crop,
                                                                            modifier = Modifier.fillMaxWidth()
                                                                        )
                                                                        Surface(
                                                                            shape = RoundedCornerShape(4.dp),
                                                                            color = if (giphyType == "gifs") Color(0xFF818CF8) else Color(0xFF10B981),
                                                                            modifier = Modifier
                                                                                .align(Alignment.TopStart)
                                                                                .padding(2.dp)
                                                                        ) {
                                                                            Text(
                                                                                text = if (giphyType == "gifs") "GIF" else "STICKER",
                                                                                color = Color.White,
                                                                                fontSize = 7.sp,
                                                                                fontWeight = FontWeight.Bold,
                                                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                    Spacer(modifier = Modifier.height(4.dp))
                                                                    Text(
                                                                        text = item.title ?: "Sin título",
                                                                        color = Color.White,
                                                                        fontSize = 10.sp,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        if (rowItems.size < 2) {
                                                            Spacer(modifier = Modifier.weight(1f))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // Grid Virtual chunking 2 por fila para KLIPY
                                        val rowItemsList = klipyItems.chunked(2)
                                        if (rowItemsList.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(200.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("No se encontraron resultados en KLIPY", color = Color(0xFF64748B), fontSize = 12.sp)
                                            }
                                        } else {
                                            LazyColumn(modifier = Modifier.height(200.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                items(rowItemsList) { rowItems ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        for (item in rowItems) {
                                                            val rawUrl = item.getBestUrl()
                                                            val finalUrl = rawUrl.replace("http://", "https://")
                                                            Card(
                                                                shape = RoundedCornerShape(12.dp),
                                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .clickable {
                                                                        if (finalUrl.isNotEmpty()) {
                                                                            onSendMedia(if (giphyType == "gifs") "gif" else "sticker", finalUrl, item.title ?: "Klipy ${giphyType}")
                                                                            onDismiss()
                                                                        }
                                                                    }
                                                            ) {
                                                                Column(modifier = Modifier.padding(6.dp)) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .height(80.dp)
                                                                            .clip(RoundedCornerShape(8.dp))
                                                                    ) {
                                                                        AsyncImage(
                                                                            model = finalUrl,
                                                                            imageLoader = gifImageLoader,
                                                                            contentDescription = item.title,
                                                                            contentScale = ContentScale.Crop,
                                                                            modifier = Modifier.fillMaxWidth()
                                                                        )
                                                                        Surface(
                                                                            shape = RoundedCornerShape(4.dp),
                                                                            color = if (giphyType == "gifs") Color(0xFFFBBF24) else Color(0xFF34D399),
                                                                            modifier = Modifier
                                                                                .align(Alignment.TopStart)
                                                                                .padding(2.dp)
                                                                        ) {
                                                                            Text(
                                                                                text = if (giphyType == "gifs") "KLIPY GIF" else "KLIPY STICKER",
                                                                                color = Color(0xFF0F172A),
                                                                                fontSize = 7.sp,
                                                                                fontWeight = FontWeight.Bold,
                                                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                    Spacer(modifier = Modifier.height(4.dp))
                                                                    Text(
                                                                        text = item.title ?: "Sin título",
                                                                        color = Color.White,
                                                                        fontSize = 10.sp,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        if (rowItems.size < 2) {
                                                            Spacer(modifier = Modifier.weight(1f))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        3 -> {
                            // PESTAÑA: MÚSICA / AUDIO
                            Column {
                                // Botón para cargar archivo de música del dispositivo
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF334155),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            audioPickerLauncher.launch("audio/*")
                                        }
                                        .testTag("btn_pick_device_audio")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.AudioFile, contentDescription = null, tint = Color(0xFFA855F7))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("Elegir música / audio del dispositivo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("Carga archivos MP3, WAV, M4A o pistas locales", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text("O comparte tus maquetas / beats de OmniStudio:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFA855F7))
                                Spacer(modifier = Modifier.height(6.dp))

                                LazyColumn(modifier = Modifier.height(200.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (audios.isEmpty()) {
                                        item {
                                            Text("No hay pistas creadas en el estudio aún.", color = Color.Gray, fontSize = 11.sp)
                                        }
                                    } else {
                                        items(audios) { audio ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
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

                        4 -> {
                            // PESTAÑA: YOUTUBE (Buscador y Enlaces Directos)
                            Column {
                                // Header explicativo de YouTube
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFF0000).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, Color(0xFFFF0000).copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFFFF0000),
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "YouTube",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Integración YouTube API v3",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = if (youtubeApiKey.isNotBlank()) "Búsqueda en vivo conectada con Google API" else "Explorador de videos y reproductor interactivo",
                                                color = Color(0xFFFCA5A5),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Campo de búsqueda de YouTube
                                OutlinedTextField(
                                    value = youtubeSearchQuery,
                                    onValueChange = { youtubeSearchQuery = it },
                                    placeholder = { Text("Buscar videos en YouTube...", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFFF0000))
                                    },
                                    trailingIcon = {
                                        if (youtubeSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = { youtubeSearchQuery = "" }) {
                                                Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF0F172A),
                                        unfocusedContainerColor = Color(0xFF0F172A),
                                        focusedBorderColor = Color(0xFFFF0000),
                                        unfocusedBorderColor = Color(0xFF334155),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_youtube_search"),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Categorías rápidas de YouTube
                                val ytCategories = listOf("🔥 Tendencias", "🎵 Música", "💻 Android & Dev", "🤖 IA & Tech", "🎧 Lo-Fi Beats", "🎙️ Podcasts", "🎬 Trailers")
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(ytCategories) { cat ->
                                        val isSelected = youtubeActiveCategory == cat && youtubeSearchQuery.isBlank()
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (isSelected) Color(0xFFFF0000) else Color(0xFF334155),
                                            modifier = Modifier.clickable {
                                                youtubeSearchQuery = ""
                                                youtubeActiveCategory = cat
                                            }
                                        ) {
                                            Text(
                                                text = cat,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Campo rápido para pegar enlace directo
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = directYouTubeUrlInput,
                                        onValueChange = { directYouTubeUrlInput = it },
                                        placeholder = { Text("O pega enlace https://youtu.be/...", color = Color(0xFF64748B), fontSize = 11.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedBorderColor = Color.Transparent,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    if (directYouTubeUrlInput.isNotBlank()) {
                                        TextButton(
                                            onClick = {
                                                val videoId = com.example.data.youtube.YouTubeClient.extractVideoId(directYouTubeUrlInput)
                                                if (videoId != null) {
                                                    onSendMedia(
                                                        "youtube",
                                                        "https://www.youtube.com/watch?v=$videoId",
                                                        "▶️ Video de YouTube ($videoId)"
                                                    )
                                                    onDismiss()
                                                }
                                            },
                                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                                contentColor = Color(0xFFFF0000)
                                            )
                                        ) {
                                            Text("Enviar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (isYouTubeLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Color(0xFFFF0000), modifier = Modifier.size(28.dp))
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.height(220.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(youtubeVideos) { video ->
                                            Card(
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        onSendMedia("youtube", video.videoUrl, video.title)
                                                        onDismiss()
                                                    }
                                                    .testTag("youtube_card_${video.videoId}")
                                            ) {
                                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    // Miniatura 16:9 con Badge de Play YouTube
                                                    Box(
                                                        modifier = Modifier
                                                            .width(110.dp)
                                                            .height(65.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color.Black)
                                                    ) {
                                                        AsyncImage(
                                                            model = video.thumbnailUrl,
                                                            contentDescription = video.title,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = Color(0xFFFF0000),
                                                            modifier = Modifier
                                                                .align(Alignment.Center)
                                                                .size(24.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    Icons.Default.PlayArrow,
                                                                    contentDescription = null,
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.width(10.dp))

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = video.title,
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "📺 ${video.channelTitle}",
                                                            color = Color(0xFF94A3B8),
                                                            fontSize = 10.sp,
                                                            maxLines = 1
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.width(6.dp))

                                                    // Botón de Enviar
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = Color(0xFFFF0000),
                                                        modifier = Modifier.clickable {
                                                            onSendMedia("youtube", video.videoUrl, video.title)
                                                            onDismiss()
                                                        }
                                                    ) {
                                                        Text(
                                                            text = "Enviar",
                                                            color = Color.White,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        5 -> {
                            // PESTAÑA: ARCHIVOS (Documentos y Archivos locales)
                            Column {
                                // Botón para cargar cualquier archivo del dispositivo
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF334155),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            filePickerLauncher.launch("*/*")
                                        }
                                        .testTag("btn_pick_device_file")
                                 ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.FolderZip, contentDescription = null, tint = Color(0xFF38BDF8))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("Elegir archivo del dispositivo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("Documentos PDF, Word, ZIP, Markdown, TXT", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text("O adjunta un documento de OmniStudio:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.height(6.dp))

                                LazyColumn(modifier = Modifier.height(200.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (docs.isEmpty()) {
                                        item {
                                            Text("No hay documentos guardados aún.", color = Color.Gray, fontSize = 11.sp)
                                        }
                                    } else {
                                        items(docs) { doc ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
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
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

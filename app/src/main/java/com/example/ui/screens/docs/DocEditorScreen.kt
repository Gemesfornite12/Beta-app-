package com.example.ui.screens.docs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Title
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import coil.request.ImageRequest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.data.model.DocumentType
import com.example.ui.screens.converter.FormatConverterDialog
import com.example.ui.viewmodel.OmniViewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

private suspend fun resolveSlideImageUrl(input: String): String? =
    withContext(Dispatchers.IO) {
        val source = input.trim()

        if (source.isBlank()) {
            return@withContext null
        }

        try {
            val connection = URL(source).openConnection() as HttpURLConnection

            connection.instanceFollowRedirects = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 OmniStudio"
            )

            connection.connect()

            val contentType = connection.contentType.orEmpty().lowercase()

            // Si ya es una imagen directa
            if (
                connection.responseCode in 200..399 &&
                contentType.startsWith("image/")
            ) {
                return@withContext connection.url.toString()
            }

            // Si es una página HTML, buscar la imagen principal
            val html = connection.inputStream
                .bufferedReader()
                .use { it.readText() }
                .take(750_000)

            val metaTags = Regex(
                "(?is)<meta\\s+[^>]*>"
            ).findAll(html)
                .map { it.value }
                .toList()

            fun attribute(
                tag: String,
                name: String
            ): String? {
                return Regex(
                    "(?i)$name\\s*=\\s*[\"']([^\"']+)[\"']"
                )
                    .find(tag)
                    ?.groupValues
                    ?.get(1)
            }

            val imageCandidate = metaTags.firstNotNullOfOrNull { tag ->
                val property =
                    attribute(tag, "property")
                        ?: attribute(tag, "name")

                if (
                    property.equals("og:image", ignoreCase = true) ||
                    property.equals("twitter:image", ignoreCase = true)
                ) {
                    attribute(tag, "content")
                } else {
                    null
                }
            } ?: Regex(
                "(?is)<img\\s+[^>]*src\\s*=\\s*[\"']([^\"']+)[\"']"
            )
                .find(html)
                ?.groupValues
                ?.get(1)

            val cleanedUrl = imageCandidate
                ?.replace("&amp;", "&")
                ?.replace("&quot;", "\"")
                ?.replace("&#x27;", "'")
                ?.trim()
                ?.takeIf {
                    it.startsWith("http://") ||
                    it.startsWith("https://") ||
                    it.startsWith("/")
                }

            if (cleanedUrl != null) {
                URL(connection.url, cleanedUrl).toString()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

data class SlideModel(
    val title: String,
    val subtitle: String,
    val bg: String = "#1E293B",
    val imageUrl: String = "",
    val tableData: String = "",
    val imageScale: Float = 1.0f,
    val imageOffsetX: Float = 0f,
    val imageOffsetY: Float = 0f,
    val imageRotation: Float = 0f,
    val imageCornerRadius: Float = 8f,
    val imageAspectRatio: Float? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocEditorScreen(
    viewModel: OmniViewModel,
    onBack: () -> Unit,
    onShareToChat: () -> Unit
) {
    val currentDoc by viewModel.currentEditingDoc.collectAsState()
    val activeSlideIndex by viewModel.activeSlideIndex.collectAsState()
    val converterDoc by viewModel.converterDoc.collectAsState()
    val docAutoSaveStatus by viewModel.docAutoSaveStatus.collectAsState()
    val isDocSaving by viewModel.isDocSaving.collectAsState()
    val imageResolutionScope = rememberCoroutineScope()

    val doc = currentDoc ?: run {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No se encontró el documento")
        }
        return
    }

    var isEditingTitle by remember { mutableStateOf(false) }
    var tempTitle by remember(doc.title) { mutableStateOf(doc.title) }

    var isPresentationPlaying by remember { mutableStateOf(false) }

    // Dialog for editing slide title / subtitle
    var editingSlideTitleModal by remember { mutableStateOf(false) }
    var slideEditTitle by remember { mutableStateOf("") }
    var slideEditSubtitle by remember { mutableStateOf("") }

    // Dialogs for inserting Image and Table into slides
    var showInsertImageModal by remember { mutableStateOf(false) }
    var showInsertTableModal by remember { mutableStateOf(false) }
    var showImageAdjustDialog by remember { mutableStateOf(false) }

    // Parse slides if slide document
    val slidesList = remember(doc.slidesJson) {
        val list = mutableListOf<SlideModel>()
        try {
            val jsonArr = JSONArray(doc.slidesJson)
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val title = obj.optString("title", "Double-tap to add title")
                val subtitle = obj.optString("subtitle", "Double-tap to add subtitle")
                val bg = obj.optString("bg", "#1E293B")
                val imageUrl = obj.optString("imageUrl", "")
                val tableData = obj.optString("tableData", "")
                val imageScale = obj.optDouble("imageScale", 1.0).toFloat()
                val imageOffsetX = obj.optDouble("imageOffsetX", 0.0).toFloat()
                val imageOffsetY = obj.optDouble("imageOffsetY", 0.0).toFloat()
                val imageRotation = obj.optDouble("imageRotation", 0.0).toFloat()
                val imageCornerRadius = obj.optDouble("imageCornerRadius", 8.0).toFloat()
                val imageAspectRatio = if (obj.has("imageAspectRatio")) obj.optDouble("imageAspectRatio").toFloat() else null
                
                list.add(SlideModel(
                    title, subtitle, bg, imageUrl, tableData,
                    imageScale, imageOffsetX, imageOffsetY, imageRotation, imageCornerRadius, imageAspectRatio
                ))
            }
        } catch (_: Exception) {
            list.add(SlideModel("Double-tap to add title", "Double-tap to add subtitle", "#1E293B"))
        }
        if (list.isEmpty()) {
            list.add(SlideModel("Double-tap to add title", "Double-tap to add subtitle", "#1E293B"))
        }
        list
    }

    val isSlideMode = doc.docType == DocumentType.SLIDE

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = Color(0xFF121214),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // WPS Style Top Bar
                Surface(
                    color = Color(0xFF1E1E24),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("btn_doc_back")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Atrás",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = { /* Undo action */ }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Deshacer", tint = Color(0xFFCBD5E1))
                        }

                        IconButton(onClick = { /* Redo action */ }) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Rehacer", tint = Color(0xFFCBD5E1))
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Title with click to rename
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isEditingTitle = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = doc.title,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Edit, contentDescription = "Editar título", tint = Color(0xFF818CF8), modifier = Modifier.size(14.dp))
                        }

                        // Format Badge (DOCX / PPTX / PDF)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF334155),
                            modifier = Modifier
                                .clickable { viewModel.openFormatConverter(doc) }
                                .testTag("btn_change_format_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ".${doc.currentFormat.extension.uppercase()}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.AutoAwesome, contentDescription = "Convertir", tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Share to Chat Button
                        IconButton(
                            onClick = {
                                viewModel.sendChatMessage(attachedDoc = doc)
                                onShareToChat()
                            },
                            modifier = Modifier.testTag("btn_share_doc_to_chat")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Compartir en chat", tint = Color(0xFF818CF8))
                        }

                        // Save Button (Orange badge inspired by screenshot)
                        Button(
                            onClick = { viewModel.saveCurrentDocument() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_save_doc"),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // Auto-Save Cloud Sync Sub-bar
                Surface(
                    color = Color(0xFF16161D),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isDocSaving) Icons.Default.Sync else Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = if (isDocSaving) Color(0xFFF59E0B) else Color(0xFF10B981),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = docAutoSaveStatus,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDocSaving) Color(0xFFFBBF24) else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
        ) {
            if (isSlideMode) {
                // SLIDE PRESENTATION VIEW (Directly matching User Screenshots!)
                SlideEditorView(
                    slides = slidesList,
                    activeSlideIndex = activeSlideIndex,
                    onSelectSlide = { viewModel.setActiveSlide(it) },
                    onAddNewSlide = { viewModel.addNewSlide() },
                    onEditSlideContent = { title, sub ->
                        slideEditTitle = title
                        slideEditSubtitle = sub
                        editingSlideTitleModal = true
                    },
                    onInsertImage = { showInsertImageModal = true },
                    onInsertTable = { showInsertTableModal = true },
                    onPlayPresentation = { isPresentationPlaying = true },
                    onAdjustImage = { showImageAdjustDialog = true }
                )
            } else {
                // RICH DOCUMENT / TEXT EDITOR
                DocumentTextView(
                    doc = doc,
                    onContentChange = { viewModel.updateCurrentDocContent(it) },
                    onOpenConverter = { viewModel.openFormatConverter(doc) }
                )
            }
        }
    }

    // Title Rename Dialog
    if (isEditingTitle) {
        AlertDialog(
            onDismissRequest = { isEditingTitle = false },
            title = { Text("Renombrar documento", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tempTitle,
                    onValueChange = { tempTitle = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_rename_doc")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempTitle.isNotBlank()) {
                            viewModel.updateCurrentDocTitle(tempTitle)
                        }
                        isEditingTitle = false
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditingTitle = false }) { Text("Cancelar") }
            }
        )
    }

    // Slide Content Edit Dialog
    if (editingSlideTitleModal) {
        AlertDialog(
            onDismissRequest = { editingSlideTitleModal = false },
            title = { Text("Editar Diapositiva #${activeSlideIndex + 1}", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = slideEditTitle,
                        onValueChange = { slideEditTitle = it },
                        label = { Text("Título de la diapositiva") },
                        modifier = Modifier.fillMaxWidth().testTag("input_slide_title")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = slideEditSubtitle,
                        onValueChange = { slideEditSubtitle = it },
                        label = { Text("Subtítulo o contenido") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("input_slide_subtitle")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateSlideContent(activeSlideIndex, slideEditTitle, slideEditSubtitle)
                        editingSlideTitleModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
                ) {
                    Text("Aplicar a Diapositiva")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSlideTitleModal = false }) { Text("Cancelar") }
            }
        )
    }

    // Insert Image Modal (Fixing user report: "toco imagen boton no sirve")
    if (showInsertImageModal) {
        val currentImg = slidesList.getOrNull(activeSlideIndex)?.imageUrl ?: ""
        var selectedPresetUrl by remember { mutableStateOf(currentImg) }
        var customUrlText by remember { mutableStateOf(currentImg) }
        var isResolvingImage by remember { mutableStateOf(false) }
        var imageResolutionError by remember { mutableStateOf<String?>(null) }

        val imagePresets = listOf(
            Triple("📊 Crecimiento & KPIs", "https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=800&q=80", "Finanzas y Métricas"),
            Triple("💡 Innovación & IA", "https://images.unsplash.com/photo-1677442136019-21780efad99a?w=800&q=80", "Tecnología y Futuro"),
            Triple("🚀 Estrategia & Negocio", "https://images.unsplash.com/photo-1460925895917-afdab827c52f?w=800&q=80", "Planes Corporativos"),
            Triple("👥 Colaboración de Equipo", "https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=800&q=80", "Liderazgo y Personas"),
            Triple("🎨 Creatividad & Diseño", "https://images.unsplash.com/photo-1542744094-3a31f272c490?w=800&q=80", "Artes y Prototipos"),
            Triple("🌍 Impacto Global", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800&q=80", "Visión Internacional")
        )

        AlertDialog(
            onDismissRequest = { showInsertImageModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Insertar Imagen en Diapositiva", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Selecciona una imagen profesional para la diapositiva #${activeSlideIndex + 1}:",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    imagePresets.forEach { (name, url, desc) ->
                        val isSelected = selectedPresetUrl == url
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF334155) else Color(0xFF1E293B),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color(0xFFEA580C) else Color(0xFF334155),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedPresetUrl = url
                                    customUrlText = url
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                ) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    Text(desc, fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("O ingresa una URL personalizada:", fontSize = 12.sp, color = Color(0xFFCBD5E1), fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customUrlText,
                        onValueChange = {
                            customUrlText = it
                            selectedPresetUrl = it
                        },
                        label = { Text("URL de imagen o página (https://...)") },
                        isError = imageResolutionError != null,
                        supportingText = {
                            if (imageResolutionError != null) {
                                Text(imageResolutionError!!, color = Color.Red, fontSize = 10.sp)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_slide_image_url")
                    )

                    if (currentImg.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(
                            onClick = {
                                viewModel.updateSlideImage(activeSlideIndex, null)
                                showInsertImageModal = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Quitar imagen de esta diapositiva", color = Color(0xFFEF4444), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetUrl = customUrlText
                            .trim()
                            .ifEmpty { selectedPresetUrl.trim() }

                        if (targetUrl.isBlank()) {
                            imageResolutionError =
                                "Escribe una URL o selecciona una imagen."
                        } else if (!isResolvingImage) {
                            isResolvingImage = true
                            imageResolutionError = null

                            imageResolutionScope.launch {
                                val resolvedUrl =
                                    resolveSlideImageUrl(targetUrl)

                                if (resolvedUrl != null) {
                                    viewModel.updateSlideImage(
                                        activeSlideIndex,
                                        resolvedUrl
                                    )

                                    showInsertImageModal = false
                                } else {
                                    imageResolutionError =
                                        "No se encontró una imagen pública en ese enlace."
                                }

                                isResolvingImage = false
                            }
                        }
                    },
                    enabled = !isResolvingImage,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEA580C)
                    )
                ) {
                    Text(
                        if (isResolvingImage) {
                            "Buscando imagen..."
                        } else {
                            "Insertar Imagen"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showInsertImageModal = false }) { Text("Cancelar") }
            }
        )
    }

    // Image Adjustment Modal
    if (showImageAdjustDialog) {
        val slide = slidesList.getOrNull(activeSlideIndex)
        if (slide != null && slide.imageUrl.isNotBlank()) {
            var scale by remember { mutableStateOf(slide.imageScale) }
            var offsetX by remember { mutableStateOf(slide.imageOffsetX) }
            var offsetY by remember { mutableStateOf(slide.imageOffsetY) }
            var cornerRadius by remember { mutableStateOf(slide.imageCornerRadius) }
            var keepProportion by remember { mutableStateOf(slide.imageAspectRatio == null) }
            
            AlertDialog(
                onDismissRequest = { showImageAdjustDialog = false },
                title = { Text("Ajustar Imagen", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Preview Area with gestures
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(cornerRadius.dp))
                                .background(Color(0xFFF1F5F9))
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale *= zoom
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val imgReq = ImageRequest.Builder(LocalContext.current)
                                .data(slide.imageUrl)
                                .crossfade(true)
                                .build()
                            
                            AsyncImage(
                                model = imgReq,
                                contentDescription = null,
                                contentScale = if (keepProportion) ContentScale.Fit else ContentScale.FillBounds,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offsetX,
                                        translationY = offsetY
                                    )
                            )
                            
                            // Visual guides for "cropping"
                            Box(modifier = Modifier.fillMaxSize().border(2.dp, Color(0xFFEA580C).copy(alpha = 0.3f), RoundedCornerShape(cornerRadius.dp)))
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text("Redondeo de esquinas: ${cornerRadius.toInt()}dp", fontSize = 12.sp)
                        Slider(
                            value = cornerRadius,
                            onValueChange = { cornerRadius = it },
                            valueRange = 0f..50f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Mantener proporción", fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Switch(checked = keepProportion, onCheckedChange = { keepProportion = it })
                        }
                        
                        Text("Zoom: ${(scale * 100).toInt()}%", fontSize = 11.sp, color = Color.Gray)
                        Slider(
                            value = scale,
                            onValueChange = { scale = it },
                            valueRange = 0.5f..3.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = {
                                scale = 1.0f
                                offsetX = 0f
                                offsetY = 0f
                                cornerRadius = 8f
                                keepProportion = true
                            }) {
                                Text("Restablecer", color = Color(0xFFEA580C))
                            }
                            
                            TextButton(onClick = {
                                showImageAdjustDialog = false
                                showInsertImageModal = true
                            }) {
                                Text("Cambiar Imagen", color = Color(0xFF64748B))
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateSlideImageTransform(
                                activeSlideIndex,
                                scale,
                                offsetX,
                                offsetY,
                                cornerRadius,
                                if (keepProportion) null else 1f // Dummy aspect ratio for FillBounds
                            )
                            showImageAdjustDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
                    ) {
                        Text("Guardar Cambios")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImageAdjustDialog = false }) { Text("Cancelar") }
                }
            )
        }
    }

    // Insert Table Modal
    if (showInsertTableModal) {
        val tablePresets = listOf(
            Pair(
                "📊 Métricas Clave (KPIs)",
                "| Métrica | Meta | Real |\n| Usuarios | 50,000 | 64,200 |\n| Retención | 80% | 88% |"
            ),
            Pair(
                "📋 Cronograma de Entregas",
                "| Hito | Responsable | Estado |\n| Prototipo | Alex | ✓ Listo |\n| Backend | Carlos | En curso |\n| Audio | Sofia | ✓ Listo |"
            ),
            Pair(
                "⚖️ Comparativa de Opciones",
                "| Solución | Ventaja | Desventaja |\n| Cloud Sync | Tiempo Real | Requiere Red |\n| Local Cache | Offline | No colaborativo |"
            )
        )

        AlertDialog(
            onDismissRequest = { showInsertTableModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TableChart, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Insertar Tabla en Diapositiva", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Text(
                        "Selecciona una plantilla de tabla estructurada para la diapositiva #${activeSlideIndex + 1}:",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    tablePresets.forEach { (title, data) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E293B),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    viewModel.updateSlideTable(activeSlideIndex, data)
                                    showInsertTableModal = false
                                }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(data, fontSize = 11.sp, color = Color(0xFF94A3B8), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            }
                        }
                    }

                    if (slidesList.getOrNull(activeSlideIndex)?.tableData?.isNotBlank() == true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                viewModel.updateSlideTable(activeSlideIndex, null)
                                showInsertTableModal = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Quitar tabla de esta diapositiva", color = Color(0xFFEF4444), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showInsertTableModal = false }) { Text("Cerrar") }
            }
        )
    }

    // Full Screen Slide Presentation Player
    if (isPresentationPlaying) {
        AlertDialog(
            onDismissRequest = { isPresentationPlaying = false },
            title = { Text("Modo Presentación en Vivo") },
            text = {
                val current = slidesList.getOrNull(activeSlideIndex) ?: slidesList[0]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = current.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    if (current.imageUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .clip(RoundedCornerShape(6.dp))
                        ) {
                            AsyncImage(
                                model = current.imageUrl,
                                contentDescription = "Imagen de diapositiva",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = current.subtitle,
                        fontSize = 13.sp,
                        color = Color(0xFFCBD5E1),
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val next = (activeSlideIndex + 1) % slidesList.size
                    viewModel.setActiveSlide(next)
                }) {
                    Text("Siguiente Diapositiva (${activeSlideIndex + 1}/${slidesList.size})")
                }
            },
            dismissButton = {
                TextButton(onClick = { isPresentationPlaying = false }) {
                    Text("Salir")
                }
            }
        )
    }

    // Format Converter Dialog
    if (converterDoc != null) {
        FormatConverterDialog(viewModel = viewModel)
    }
}

@Composable
private fun SlideEditorView(
    slides: List<SlideModel>,
    activeSlideIndex: Int,
    onSelectSlide: (Int) -> Unit,
    onAddNewSlide: () -> Unit,
    onEditSlideContent: (String, String) -> Unit,
    onInsertImage: () -> Unit,
    onInsertTable: () -> Unit,
    onPlayPresentation: () -> Unit,
    onAdjustImage: () -> Unit
) {
    val currentSlide = slides.getOrNull(activeSlideIndex) ?: slides.firstOrNull() ?: SlideModel("Title", "Subtitle", "#1E293B")

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Presentation Canvas (Center Area)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF141416))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            // White Slide Canvas matching screenshot
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.85f)
                    .clip(RoundedCornerShape(8.dp))
                    .testTag("slide_active_canvas"),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Dashed title box (matching user's WPS screenshot: "Double-tap to add title")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = Color(0xFFB0B0B0),
                                shape = RoundedCornerShape(2.dp)
                            )
                            .clickable { onEditSlideContent(currentSlide.title, currentSlide.subtitle) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentSlide.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Render Imagen de Diapositiva if present
                    if (currentSlide.imageUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(currentSlide.imageCornerRadius.dp))
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(currentSlide.imageCornerRadius.dp))
                                .clickable { onAdjustImage() }
                        ) {
                            val imageRequest = ImageRequest.Builder(LocalContext.current)
                                .data(currentSlide.imageUrl)
                                .setHeader(
                                    "User-Agent",
                                    "Mozilla/5.0 OmniStudio"
                                )
                                .crossfade(true)
                                .build()

                            AsyncImage(
                                model = imageRequest,
                                contentDescription = "Imagen de la diapositiva",
                                contentScale = if (currentSlide.imageAspectRatio == null) ContentScale.Fit else ContentScale.FillBounds,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = currentSlide.imageScale,
                                        scaleY = currentSlide.imageScale,
                                        translationX = currentSlide.imageOffsetX,
                                        translationY = currentSlide.imageOffsetY,
                                        rotationZ = currentSlide.imageRotation
                                    )
                            )
                            Surface(
                                shape = RoundedCornerShape(bottomStart = 6.dp),
                                color = Color.Black.copy(alpha = 0.7f),
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Ajustar", color = Color.White, fontSize = 9.sp)
                                }
                            }
                        }
                    } else {
                        // Image affordance shortcut
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF8FAFC),
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                                .clickable { onInsertImage() }
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Toca 'Image' o aquí para añadir foto", fontSize = 10.sp, color = Color(0xFF64748B))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Dashed subtitle box (matching user's WPS screenshot: "Double-tap to add subtitle")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .border(
                                width = 1.dp,
                                color = Color(0xFFB0B0B0),
                                shape = RoundedCornerShape(2.dp)
                            )
                            .clickable { onEditSlideContent(currentSlide.title, currentSlide.subtitle) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentSlide.subtitle,
                            fontSize = 13.sp,
                            color = Color(0xFF475569),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Render Tabla if present
                    if (currentSlide.tableData.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF1F5F9),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onInsertTable() }
                                .padding(4.dp)
                        ) {
                            Text(
                                text = currentSlide.tableData,
                                fontSize = 10.sp,
                                color = Color(0xFF334155),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Slide Carousel (Matching Screenshot: Slide 1 thumbnail and [+] button)
        Surface(
            color = Color(0xFF1E1E24),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(slides) { idx, slide ->
                        val isSelected = idx == activeSlideIndex
                        Box(
                            modifier = Modifier
                                .width(74.dp)
                                .height(54.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(
                                    width = 2.dp,
                                    color = if (isSelected) Color(0xFFEA580C) else Color(0xFF334155),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .background(Color.White)
                                .clickable { onSelectSlide(idx) }
                                .testTag("slide_thumbnail_$idx")
                        ) {
                            // Mini title preview or Image preview
                            if (slide.imageUrl.isNotBlank()) {
                                val thumbRequest = ImageRequest.Builder(LocalContext.current)
                                    .data(slide.imageUrl)
                                    .setHeader(
                                        "User-Agent",
                                        "Mozilla/5.0 OmniStudio"
                                    )
                                    .crossfade(true)
                                    .build()

                                AsyncImage(
                                    model = thumbRequest,
                                    contentDescription = slide.title,
                                    contentScale = if (slide.imageAspectRatio == null) ContentScale.Fit else ContentScale.FillBounds,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(slide.imageCornerRadius.dp / 4)) // Scaled down radius
                                        .graphicsLayer(
                                            scaleX = slide.imageScale,
                                            scaleY = slide.imageScale,
                                            translationX = slide.imageOffsetX / 4, // Scaled down offsets
                                            translationY = slide.imageOffsetY / 4
                                        )
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = slide.title,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Orange Badge Index at bottom right (matching screenshot)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .background(
                                        if (isSelected) Color(0xFFEA580C) else Color(0xFF64748B),
                                        RoundedCornerShape(topEnd = 4.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Add Slide Button [+] (matching screenshot)
                    item {
                        Surface(
                            modifier = Modifier
                                .width(54.dp)
                                .height(54.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp))
                                .clickable { onAddNewSlide() }
                                .testTag("btn_add_slide_plus"),
                            color = Color(0xFF27272A)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, contentDescription = "Añadir diapositiva", tint = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Tools Bar (Insert, Play, View, Tools - Matching WPS Office Screenshot)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolbarActionItem(icon = Icons.Default.PlayArrow, label = "Play", onClick = onPlayPresentation)
                    ToolbarActionItem(icon = Icons.Default.Add, label = "New Slide", onClick = onAddNewSlide)
                    ToolbarActionItem(icon = Icons.Default.TextFields, label = "Text Box", onClick = { onEditSlideContent(currentSlide.title, currentSlide.subtitle) })
                    ToolbarActionItem(icon = Icons.Default.Image, label = "Image", onClick = onInsertImage)
                    ToolbarActionItem(icon = Icons.Default.TableChart, label = "Table", onClick = onInsertTable)
                }
            }
        }
    }
}

@Composable
private fun ToolbarActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = Color(0xFFCBD5E1), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, fontSize = 10.sp, color = Color(0xFF94A3B8))
    }
}

@Composable
private fun DocumentTextView(
    doc: com.example.data.model.DocumentItem,
    onContentChange: (String) -> Unit,
    onOpenConverter: () -> Unit
) {
    val wordCount = remember(doc.content) {
        doc.content.split(Regex("\\s+")).filter { it.isNotBlank() }.size
    }
    val charCount = remember(doc.content) { doc.content.length }

    Column(modifier = Modifier.fillMaxSize()) {
        // Quick Formatting Toolbar
        Surface(
            color = Color(0xFF1E1E24),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = { onContentChange(doc.content + "\n# ") }) {
                    Icon(Icons.Default.Title, contentDescription = "Título H1", tint = Color.White)
                }
                IconButton(onClick = { onContentChange(doc.content + " **negrita** ") }) {
                    Icon(Icons.Default.FormatBold, contentDescription = "Negrita", tint = Color.White)
                }
                IconButton(onClick = { onContentChange(doc.content + " *cursiva* ") }) {
                    Icon(Icons.Default.FormatItalic, contentDescription = "Cursiva", tint = Color.White)
                }
                IconButton(onClick = { onContentChange(doc.content + "\n- ") }) {
                    Icon(Icons.Default.FormatListBulleted, contentDescription = "Lista", tint = Color.White)
                }
                IconButton(onClick = { onContentChange(doc.content + "\n> ") }) {
                    Icon(Icons.Default.FormatQuote, contentDescription = "Cita", tint = Color.White)
                }

                Spacer(modifier = Modifier.weight(1f))

                // Convert format quick shortcut
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF4F46E5).copy(alpha = 0.3f),
                    modifier = Modifier.clickable(onClick = onOpenConverter)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cambiar Formato", color = Color(0xFF818CF8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Editor Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E293B))
        ) {
            OutlinedTextField(
                value = doc.content,
                onValueChange = onContentChange,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .testTag("input_doc_editor_content"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color(0xFFF1F5F9)
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 22.sp)
            )
        }

        // Stats Footer
        Surface(
            color = Color(0xFF0F172A),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$wordCount palabras • $charCount caracteres",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
                Text(
                    text = "Cloud Sync: Guardado automático",
                    color = Color(0xFF34D399),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

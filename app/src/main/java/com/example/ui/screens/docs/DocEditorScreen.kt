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
import androidx.compose.material.icons.filled.CloudUpload
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocumentType
import com.example.ui.screens.converter.FormatConverterDialog
import com.example.ui.viewmodel.OmniViewModel
import org.json.JSONArray
import org.json.JSONObject

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

    // Parse slides if slide document
    val slidesList = remember(doc.slidesJson) {
        val list = mutableListOf<Triple<String, String, String>>()
        try {
            val jsonArr = JSONArray(doc.slidesJson)
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val title = obj.optString("title", "Double-tap to add title")
                val subtitle = obj.optString("subtitle", "Double-tap to add subtitle")
                val bg = obj.optString("bg", "#1E293B")
                list.add(Triple(title, subtitle, bg))
            }
        } catch (_: Exception) {
            list.add(Triple("Double-tap to add title", "Double-tap to add subtitle", "#1E293B"))
        }
        if (list.isEmpty()) {
            list.add(Triple("Double-tap to add title", "Double-tap to add subtitle", "#1E293B"))
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
                    onPlayPresentation = { isPresentationPlaying = true }
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
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = current.first,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = current.second,
                        fontSize = 15.sp,
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
    slides: List<Triple<String, String, String>>,
    activeSlideIndex: Int,
    onSelectSlide: (Int) -> Unit,
    onAddNewSlide: () -> Unit,
    onEditSlideContent: (String, String) -> Unit,
    onPlayPresentation: () -> Unit
) {
    val currentSlide = slides.getOrNull(activeSlideIndex) ?: slides.firstOrNull() ?: Triple("Title", "Subtitle", "#1E293B")

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
                    .clickable { onEditSlideContent(currentSlide.first, currentSlide.second) }
                    .testTag("slide_active_canvas"),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
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
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentSlide.first,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Dashed subtitle box (matching user's WPS screenshot: "Double-tap to add subtitle")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .border(
                                width = 1.dp,
                                color = Color(0xFFB0B0B0),
                                shape = RoundedCornerShape(2.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentSlide.second,
                            fontSize = 15.sp,
                            color = Color(0xFF475569),
                            textAlign = TextAlign.Center
                        )
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
                            // Mini title preview
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = slide.first,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
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
                    ToolbarActionItem(icon = Icons.Default.TextFields, label = "Text Box", onClick = { onEditSlideContent(currentSlide.first, currentSlide.second) })
                    ToolbarActionItem(icon = Icons.Default.Image, label = "Image", onClick = {})
                    ToolbarActionItem(icon = Icons.Default.TableChart, label = "Table", onClick = {})
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

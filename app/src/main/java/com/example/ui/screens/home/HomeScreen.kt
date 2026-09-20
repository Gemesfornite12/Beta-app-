package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AudioProject
import com.example.data.model.DocumentFormat
import com.example.data.model.DocumentItem
import com.example.data.model.DocumentType
import com.example.ui.screens.converter.FormatConverterDialog
import com.example.ui.screens.converter.FormatIcon
import com.example.ui.viewmodel.OmniViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: OmniViewModel,
    onOpenDocEditor: () -> Unit,
    onOpenMusicStudio: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val authState by viewModel.authUiState.collectAsState()
    val documents by viewModel.documents.collectAsState()
    val audioProjects by viewModel.audioProjects.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeFilter by viewModel.activeCategoryFilter.collectAsState()
    val converterDoc by viewModel.converterDoc.collectAsState()

    var showCreateSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current

    // Permission launcher for storage (legacy devices)
    var pendingDownloadAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingDownloadAction?.invoke()
        }
        pendingDownloadAction = null
    }

    fun handleDownload(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            action()
        } else {
            // Need WRITE_EXTERNAL_STORAGE for legacy
            pendingDownloadAction = action
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    val filteredDocs = remember(documents, searchQuery, activeFilter) {
        documents.filter { doc ->
            val matchesQuery = doc.title.contains(searchQuery, ignoreCase = true) || doc.content.contains(searchQuery, ignoreCase = true)
            val matchesCategory = when (activeFilter) {
                "DOCS" -> doc.docType == DocumentType.DOC || doc.docType == DocumentType.TXT
                "SLIDES" -> doc.docType == DocumentType.SLIDE
                "MUSIC" -> false
                else -> true
            }
            matchesQuery && matchesCategory
        }
    }

    val filteredAudio = remember(audioProjects, searchQuery, activeFilter) {
        if (activeFilter == "DOCS" || activeFilter == "SLIDES") emptyList()
        else {
            audioProjects.filter { it.title.contains(searchQuery, ignoreCase = true) || it.genre.contains(searchQuery, ignoreCase = true) }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = Color(0xFF0F172A),
        topBar = {
            // Header
            Surface(
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF4F46E5)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Cloud,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "OmniStudio",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cloud Sync Activo", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Profile Avatar Button
                        Surface(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onOpenProfile)
                                .testTag("btn_home_profile"),
                            color = Color(0xFF334155)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = authState.currentUser?.displayName?.take(1)?.uppercase() ?: "A",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Cloud Global Search
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("Buscar en la nube (documentos, pistas, chats)...", fontSize = 13.sp, color = Color(0xFF94A3B8)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("input_home_search"),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedBorderColor = Color(0xFF4F46E5),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateSheet = true },
                containerColor = Color(0xFF4F46E5),
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_create_new")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Crear nuevo")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Quick Tools Grid (Inspired by user's WPS screenshot: Docs, Slides, Sheets, Audio, Chat, Converter)
                Text(
                    text = "Herramientas de la Suite",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFFCBD5E1),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickToolItem(
                        icon = Icons.Default.Description,
                        label = "Docs",
                        bgColor = Color(0xFF2563EB),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.createNewDocument(DocumentType.DOC)
                            onOpenDocEditor()
                        }
                    )
                    QuickToolItem(
                        icon = Icons.Default.Slideshow,
                        label = "Diapositivas",
                        bgColor = Color(0xFFEA580C),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.createNewDocument(DocumentType.SLIDE)
                            onOpenDocEditor()
                        }
                    )
                    QuickToolItem(
                        icon = Icons.Default.MusicNote,
                        label = "Beat Studio",
                        bgColor = Color(0xFF7C3AED),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenMusicStudio
                    )
                    QuickToolItem(
                        icon = Icons.Default.Forum,
                        label = "Chat en Vivo",
                        bgColor = Color(0xFF059669),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenChat
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickToolItem(
                        icon = Icons.Default.AutoAwesome,
                        label = "Conversor",
                        bgColor = Color(0xFF0891B2),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val firstDoc = documents.firstOrNull()
                            if (firstDoc != null) {
                                viewModel.openFormatConverter(firstDoc)
                            } else {
                                viewModel.createNewDocument(DocumentType.DOC)
                                onOpenDocEditor()
                            }
                        }
                    )
                    QuickToolItem(
                        icon = Icons.Default.TableChart,
                        label = "Hojas",
                        bgColor = Color(0xFF16A34A),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.createNewDocument(DocumentType.SHEET)
                            onOpenDocEditor()
                        }
                    )
                    QuickToolItem(
                        icon = Icons.Default.TextFields,
                        label = "Nota TXT",
                        bgColor = Color(0xFF475569),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.createNewDocument(DocumentType.TXT)
                            onOpenDocEditor()
                        }
                    )
                    QuickToolItem(
                        icon = Icons.Default.PictureAsPdf,
                        label = "CV / PDF",
                        bgColor = Color(0xFFDC2626),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.createNewDocument(DocumentType.RESUME)
                            onOpenDocEditor()
                        }
                    )
                }
            }

            // Filter Tabs Row
            item {
                val filters = listOf("TODOS", "DOCS", "SLIDES", "MUSIC")
                val labels = listOf("Todos", "Documentos", "Diapositivas", "Música")

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filters.zip(labels)) { (filterKey, label) ->
                        val isSelected = activeFilter == filterKey
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) Color(0xFF4F46E5) else Color(0xFF1E293B),
                            modifier = Modifier
                                .clickable { viewModel.onCategoryFilterChanged(filterKey) }
                                .testTag("filter_tab_$filterKey")
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            // Documents List Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Archivos Recientes en la Nube",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Text(
                        text = "${filteredDocs.size + filteredAudio.size} elementos",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // Music items
            items(filteredAudio) { audio ->
                val isOwner = viewModel.isSongOwner(audio)
                AudioProjectCard(
                    project = audio,
                    isOwner = isOwner,
                    onClick = {
                        viewModel.openAudioProject(audio)
                        onOpenMusicStudio()
                    },
                    onDownload = {
                        handleDownload { viewModel.downloadAudioProject(context, audio) }
                    },
                    onShareToChat = {
                        viewModel.sendChatMessage(attachedAudio = audio)
                        onOpenChat()
                    },
                    onTogglePublic = {
                        viewModel.publishSong(audio.id, !audio.isPublic)
                    },
                    onDelete = { viewModel.deleteSongIfOwner(audio.id) }
                )
            }

            // Document items
            items(filteredDocs) { doc ->
                DocumentItemCard(
                    doc = doc,
                    onClick = {
                        viewModel.openDocument(doc)
                        onOpenDocEditor()
                    },
                    onDownload = {
                        handleDownload { viewModel.downloadDocument(context, doc) }
                    },
                    onConvertFormat = {
                        viewModel.openFormatConverter(doc)
                    },
                    onShareToChat = {
                        viewModel.sendChatMessage(attachedDoc = doc)
                        onOpenChat()
                    },
                    onDelete = { viewModel.deleteDocument(doc.id) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // Modal Bottom Sheet: Quick Create
    if (showCreateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCreateSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1E293B)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Crear Nuevo Archivo Cloud",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))

                CreateOptionRow(
                    icon = Icons.Default.Description,
                    iconColor = Color(0xFF2563EB),
                    title = "Nuevo Documento de Texto (.docx)",
                    subtitle = "Editor enriquecido para informes, artículos y cartas",
                    onClick = {
                        showCreateSheet = false
                        viewModel.createNewDocument(DocumentType.DOC)
                        onOpenDocEditor()
                    }
                )

                CreateOptionRow(
                    icon = Icons.Default.Slideshow,
                    iconColor = Color(0xFFEA580C),
                    title = "Nueva Presentación de Diapositivas (.pptx)",
                    subtitle = "Lienzo visual con títulos, subtítulos y diapositivas",
                    onClick = {
                        showCreateSheet = false
                        viewModel.createNewDocument(DocumentType.SLIDE)
                        onOpenDocEditor()
                    }
                )

                CreateOptionRow(
                    icon = Icons.Default.MusicNote,
                    iconColor = Color(0xFF7C3AED),
                    title = "Nueva Pista Musical (Beat Studio)",
                    subtitle = "Secuenciador de 16 pasos y sintetizador de audio real",
                    onClick = {
                        showCreateSheet = false
                        onOpenMusicStudio()
                    }
                )

                CreateOptionRow(
                    icon = Icons.Default.AutoAwesome,
                    iconColor = Color(0xFF0891B2),
                    title = "Conversor de Formatos",
                    subtitle = "Transforma DOCX a PDF, TXT, Markdown o HTML",
                    onClick = {
                        showCreateSheet = false
                        val first = documents.firstOrNull()
                        if (first != null) viewModel.openFormatConverter(first)
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Format Converter Dialog
    if (converterDoc != null) {
        FormatConverterDialog(viewModel = viewModel)
    }
}

@Composable
private fun QuickToolItem(
    icon: ImageVector,
    label: String,
    bgColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = Color(0xFF1E293B)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(10.dp),
                color = bgColor.copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = label, tint = bgColor, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DocumentItemCard(
    doc: DocumentItem,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onConvertFormat: () -> Unit,
    onShareToChat: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val dateFormatted = remember(doc.lastModified) {
        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
        sdf.format(Date(doc.lastModified))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("doc_card_${doc.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FormatIcon(format = doc.currentFormat)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = doc.title,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF334155)
                    ) {
                        Text(
                            text = ".${doc.currentFormat.extension.uppercase()}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "$dateFormatted • ${doc.fileSizeKb} KB",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            // Quick Convert Button
            IconButton(onClick = onConvertFormat) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "Cambiar formato", tint = Color(0xFF818CF8), modifier = Modifier.size(18.dp))
            }

            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = "Descargar", tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Más opciones", tint = Color(0xFF94A3B8))
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color(0xFF1E293B))
                ) {
                    DropdownMenuItem(
                        text = { Text("Abrir en Editor", color = Color.White) },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = Color.White) },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Cambiar Formato", color = Color(0xFF38BDF8)) },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF38BDF8)) },
                        onClick = {
                            showMenu = false
                            onConvertFormat()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Descargar", color = Color(0xFF38BDF8)) },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF38BDF8)) },
                        onClick = {
                            showMenu = false
                            onDownload()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Compartir en Chat", color = Color(0xFF818CF8)) },
                        leadingIcon = { Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF818CF8)) },
                        onClick = {
                            showMenu = false
                            onShareToChat()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar de la Nube", color = Color(0xFFEF4444)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioProjectCard(
    project: AudioProject,
    isOwner: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onShareToChat: () -> Unit,
    onTogglePublic: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("audio_card_${project.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF7C3AED).copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = Color(0xFFA855F7),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.title,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF7C3AED).copy(alpha = 0.3f)
                    ) {
                        Text(
                            text = "BEAT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC084FC),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (project.isPublic) Color(0xFF065F46).copy(alpha = 0.5f) else Color(0xFF334155)
                    ) {
                        Text(
                            text = if (project.isPublic) "🌐 Pública" else "🔒 Privada",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (project.isPublic) Color(0xFF34D399) else Color(0xFF94A3B8),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${project.genre} • ${project.bpm} BPM",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                if (!isOwner) {
                    Text(
                        text = "Autor: ${project.authorName}",
                        fontSize = 10.sp,
                        color = Color(0xFF818CF8),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            IconButton(onClick = onShareToChat) {
                Icon(Icons.Default.Send, contentDescription = "Compartir en chat", tint = Color(0xFFC084FC), modifier = Modifier.size(18.dp))
            }

            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = "Descargar", tint = Color(0xFF34D399), modifier = Modifier.size(20.dp))
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Más", tint = Color(0xFF94A3B8))
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color(0xFF1E293B))
                ) {
                    DropdownMenuItem(
                        text = { Text("Abrir en Beat Studio", color = Color.White) },
                        leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White) },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Descargar", color = Color(0xFF34D399)) },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF34D399)) },
                        onClick = {
                            showMenu = false
                            onDownload()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Compartir en Chat", color = Color(0xFFA855F7)) },
                        leadingIcon = { Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFFA855F7)) },
                        onClick = {
                            showMenu = false
                            onShareToChat()
                        }
                    )
                    if (isOwner) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (project.isPublic) "Hacer Privada" else "Publicar en Comunidad",
                                    color = if (project.isPublic) Color(0xFFF59E0B) else Color(0xFF10B981)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (project.isPublic) Icons.Default.Lock else Icons.Default.Public,
                                    contentDescription = null,
                                    tint = if (project.isPublic) Color(0xFFF59E0B) else Color(0xFF10B981)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onTogglePublic()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar", color = Color(0xFFEF4444)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("🔒 Solo el autor puede eliminar o editar", color = Color(0xFF64748B), fontSize = 11.sp) },
                            onClick = { showMenu = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateOptionRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(10.dp),
            color = iconColor.copy(alpha = 0.2f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp)
            Text(text = subtitle, color = Color(0xFF94A3B8), fontSize = 11.sp)
        }
    }
}

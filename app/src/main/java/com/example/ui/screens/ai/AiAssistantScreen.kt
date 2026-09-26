package com.example.ui.screens.ai

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.ui.viewmodel.OmniViewModel
import kotlinx.serialization.json.*
import java.util.Locale

private data class SaraEventOption(val value: String, val label: String)

private val saraEventOptions = listOf(
    SaraEventOption("user", "UserEvent · Mensaje de usuario"),
    SaraEventOption("bot", "BotEvent · Respuesta de Sara"),
    SaraEventOption("session_started", "SessionStartedEvent · Iniciar sesión"),
    SaraEventOption("action", "ActionEvent · Acción"),
    SaraEventOption("slot", "SlotEvent · Cambiar slot"),
    SaraEventOption("reset_slots", "ResetSlotsEvent · Reiniciar slots"),
    SaraEventOption("restart", "RestartEvent · Reiniciar tracker"),
    SaraEventOption("reminder", "ReminderEvent · Recordatorio"),
    SaraEventOption("cancel_reminder", "CancelReminderEvent · Cancelar recordatorio"),
    SaraEventOption("pause", "PauseEvent · Pausar conversación"),
    SaraEventOption("resume", "ResumeEvent · Reanudar conversación"),
    SaraEventOption("followup", "FollowupEvent · Acción siguiente"),
    SaraEventOption("export", "ExportEvent · Exportar tracker"),
    SaraEventOption("undo", "UndoEvent · Deshacer evento"),
    SaraEventOption("rewind", "RewindEvent · Revertir turno"),
    SaraEventOption("agent", "AgentEvent · Agente"),
    SaraEventOption("entities", "EntitiesAddedEvent · Entidades"),
    SaraEventOption("user_featurization", "UserFeaturizationEvent · Featurización"),
    SaraEventOption("action_execution_rejected", "ActionExecutionRejectedEvent · Rechazo de acción"),
    SaraEventOption("form_validation", "FormValidationEvent · Validación de formulario"),
    SaraEventOption("loop_interrupted", "LoopInterruptedEvent · Interrumpir loop"),
    SaraEventOption("form", "FormEvent · Formulario"),
    SaraEventOption("active_loop", "ActiveLoopEvent · Loop activo")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    viewModel: OmniViewModel,
    onBack: () -> Unit
) {
    val aiChatHistory by viewModel.aiChatHistory.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val saraAdvancedResult by viewModel.saraAdvancedResult.collectAsState()
    val isSaraAdvancedLoading by viewModel.isSaraAdvancedLoading.collectAsState()
    var showSaraAdvanced by remember { mutableStateOf(false) }
    var saraAdvancedInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sara", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Asistente de OmniStudio · Rasa + Felo", fontSize = 11.sp, color = Color(0xFFA5B4FC))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Volver", tint = Color.White)
                    }
                },
                actions = {
                    if (aiChatHistory.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAiChat() }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Borrar conversación", tint = Color(0xFF94A3B8))
                        }
                    }
                    IconButton(onClick = { showSaraAdvanced = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Herramientas de Sara", tint = Color(0xFF94A3B8))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        SaraChatView(
            history = aiChatHistory,
            isLoading = isAiLoading,
            modifier = Modifier.fillMaxSize().padding(padding),
            onSend = viewModel::sendAiMessage,
            onSendAttachment = viewModel::sendAiAttachment
        )
    }

    if (showSaraAdvanced) {
        SaraAdvancedDialog(
            result = saraAdvancedResult,
            isLoading = isSaraAdvancedLoading,
            input = saraAdvancedInput,
            onInputChange = { saraAdvancedInput = it },
            onRun = { operation -> viewModel.runSaraAdvanced(operation, saraAdvancedInput) },
            onAppendEvent = { event -> viewModel.runSaraAdvanced("events", eventPayload = event) },
            onDismiss = {
                showSaraAdvanced = false
                viewModel.clearSaraAdvancedResult()
            }
        )
    }
}

@Composable
private fun SaraChatView(
    history: List<ChatMessage>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onSend: (String) -> Unit,
    onSendAttachment: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var attachmentUri by remember { mutableStateOf<Uri?>(null) }
    var attachmentName by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val transcript = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (transcript.isNotBlank()) {
                draft = listOf(draft.trim(), transcript).filter(String::isNotBlank).joinToString(" ")
            }
        }
    }

    val startSpeechRecognition: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "CR").toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicta un mensaje para Sara")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No hay reconocimiento de voz disponible en este dispositivo", Toast.LENGTH_LONG).show()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startSpeechRecognition()
        else Toast.makeText(context, "Se necesita permiso de micrófono para dictar", Toast.LENGTH_LONG).show()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()
            if (isSaraCompatibleAttachment(mimeType, uri.lastPathSegment.orEmpty())) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                attachmentUri = uri
                attachmentName = saraAttachmentDisplayName(context, uri)
            } else {
                Toast.makeText(context, "Sara acepta documentos, audio y video compatibles; las imágenes no están disponibles", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(history.size, isLoading) {
        val lastIndex = history.lastIndex
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    Column(
        modifier = modifier.background(Color(0xFF0F172A)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (history.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Habla con Sara", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Escribe o dicta. También puedes adjuntar documentos, audio y video compatibles para que Sara los analice.",
                        color = Color(0xFF94A3B8), fontSize = 14.sp, textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history) { message ->
                    val isUser = message.senderName != "Sara"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (isUser) Color(0xFF4F46E5) else Color(0xFF1E293B),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.widthIn(max = 310.dp)
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                if (!isUser) {
                                    Text("Sara", color = Color(0xFFA5B4FC), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(3.dp))
                                }
                                val linkedText = remember(message.text) {
                                    buildAnnotatedString {
                                        append(message.text)
                                        Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).findAll(message.text).forEach { match ->
                                            val rawUrl = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']')
                                            val end = match.range.first + rawUrl.length
                                            if (rawUrl.isNotBlank() && end > match.range.first) {
                                                addStyle(
                                                    SpanStyle(color = Color(0xFF93C5FD), textDecoration = TextDecoration.Underline),
                                                    match.range.first,
                                                    end
                                                )
                                                addStringAnnotation("url", rawUrl, match.range.first, end)
                                            }
                                        }
                                    }
                                }
                                ClickableText(
                                    text = linkedText,
                                    style = TextStyle(color = Color.White, fontSize = 15.sp),
                                    onClick = { offset ->
                                        linkedText.getStringAnnotations("url", offset, offset).firstOrNull()?.let { annotation ->
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))) }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                if (isLoading) {
                    item {
                        Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(18.dp)) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFFA5B4FC))
                                Spacer(Modifier.width(9.dp))
                                Text("Sara está procesando…", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        if (attachmentUri != null) {
            Surface(
                color = Color(0xFF334155),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, tint = Color(0xFFA5B4FC), modifier = Modifier.size(18.dp))
                    Text(attachmentName ?: "Archivo adjunto", color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    IconButton(onClick = { attachmentUri = null; attachmentName = null }, enabled = !isLoading) {
                        Icon(Icons.Default.Close, contentDescription = "Quitar adjunto", tint = Color(0xFFCBD5E1), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Text(
            "Adjuntos compatibles: documentos, audio o video · máximo 10 MB. Felo procesa temporalmente y se intenta borrar al terminar; no fotos.",
            color = Color(0xFF64748B), fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1E293B)).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                enabled = !isLoading,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = "Adjuntar documento, audio o video", tint = Color(0xFFA5B4FC))
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribe o busca con Sara…", color = Color(0xFF94A3B8)) },
                maxLines = 4,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF818CF8),
                    unfocusedBorderColor = Color(0xFF475569),
                    cursorColor = Color(0xFFA5B4FC)
                ),
                shape = RoundedCornerShape(22.dp)
            )
            IconButton(
                onClick = { micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
                enabled = !isLoading,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Dictar a Sara", tint = Color(0xFFA5B4FC))
            }
            IconButton(
                onClick = {
                    val message = draft.trim()
                    val uri = attachmentUri
                    if (uri != null && !isLoading) {
                        onSendAttachment(uri, message.ifBlank { "Resume este archivo y responde según su contenido." })
                        attachmentUri = null
                        attachmentName = null
                        draft = ""
                    } else if (message.isNotEmpty() && !isLoading) {
                        onSend(message)
                        draft = ""
                    }
                },
                enabled = (draft.isNotBlank() || attachmentUri != null) && !isLoading,
                modifier = Modifier.size(44.dp).background(Color(0xFF6366F1), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar a Sara", tint = Color.White)
            }
        }
    }
}

private fun isSaraCompatibleAttachment(mimeType: String, fileName: String): Boolean {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    if (mimeType.startsWith("image/")) return false
    if (mimeType.startsWith("text/") || mimeType.startsWith("audio/") || mimeType.startsWith("video/")) return true
    if (mimeType in setOf(
            "application/pdf", "application/msword", "application/rtf", "application/json",
            "application/vnd.ms-excel", "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )) return true
    return extension in setOf("pdf", "doc", "docx", "rtf", "txt", "md", "csv", "json", "xls", "xlsx", "ppt", "pptx", "mp3", "m4a", "wav", "ogg", "opus", "mp4", "mov", "mkv", "webm", "m4v")
}

private fun saraAttachmentDisplayName(context: android.content.Context, uri: Uri): String {
    if (uri.scheme == "content") {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) return cursor.getString(index)
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank) ?: "Archivo adjunto"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaraAdvancedDialog(
    result: String?,
    isLoading: Boolean,
    input: String,
    onInputChange: (String) -> Unit,
    onRun: (String) -> Unit,
    onAppendEvent: (JsonObject) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedEvent by remember { mutableStateOf("user") }
    var eventMenuExpanded by remember { mutableStateOf(false) }
    var eventText by remember { mutableStateOf("") }
    var eventInputChannel by remember { mutableStateOf("rest") }
    var eventMessageId by remember { mutableStateOf("") }
    var eventParseData by remember { mutableStateOf("") }
    var slotName by remember { mutableStateOf("") }
    var slotValue by remember { mutableStateOf("null") }
    var actionName by remember { mutableStateOf("") }
    var actionPolicy by remember { mutableStateOf("") }
    var actionConfidence by remember { mutableStateOf("") }
    var actionText by remember { mutableStateOf("") }
    var hideRuleTurn by remember { mutableStateOf(false) }
    var entitiesJson by remember { mutableStateOf("[]") }
    var eventTimestamp by remember { mutableStateOf("") }
    var eventMetadata by remember { mutableStateOf("") }
    var eventFormError by remember { mutableStateOf<String?>(null) }
    var pendingDestructiveEvent by remember { mutableStateOf<JsonObject?>(null) }

    fun makeEventPayload(): JsonObject {
        return buildJsonObject {
            put("event", selectedEvent)
            if (eventTimestamp.isNotBlank()) {
                val timestamp = eventTimestamp.toLongOrNull()
                    ?: throw IllegalArgumentException("La marca de tiempo debe ser un número entero.")
                put("timestamp", timestamp)
            }
            if (eventMetadata.isNotBlank()) {
                val metadata = Json.parseToJsonElement(eventMetadata)
                if (metadata !is JsonObject) throw IllegalArgumentException("Metadata debe ser un objeto JSON.")
                put("metadata", metadata)
            }
            when (selectedEvent) {
                "user" -> {
                    if (eventText.isNotBlank()) put("text", eventText.trim())
                    if (eventInputChannel.isNotBlank()) put("input_channel", eventInputChannel.trim())
                    if (eventMessageId.isNotBlank()) put("message_id", eventMessageId.trim())
                    if (eventParseData.isNotBlank()) {
                        val parseData = Json.parseToJsonElement(eventParseData)
                        if (parseData !is JsonObject) throw IllegalArgumentException("parse_data debe ser un objeto JSON.")
                        put("parse_data", parseData)
                    }
                }
                "slot" -> {
                    if (slotName.isBlank()) throw IllegalArgumentException("Escribe el nombre del slot.")
                    put("name", slotName.trim())
                    put("value", Json.parseToJsonElement(slotValue))
                }
                "action" -> {
                    if (actionName.isNotBlank()) put("name", actionName.trim())
                    if (actionPolicy.isNotBlank()) put("policy", actionPolicy.trim())
                    if (actionConfidence.isNotBlank()) {
                        val confidence = actionConfidence.toDoubleOrNull()
                            ?: throw IllegalArgumentException("La confianza debe ser un número.")
                        if (!confidence.isFinite()) throw IllegalArgumentException("La confianza debe ser finita.")
                        put("confidence", confidence)
                    }
                    put("hide_rule_turn", hideRuleTurn)
                    if (actionText.isNotBlank()) put("action_text", actionText.trim())
                }
                "entities" -> {
                    val entities = Json.parseToJsonElement(entitiesJson)
                    if (entities !is JsonArray) throw IllegalArgumentException("entities debe ser una lista JSON.")
                    put("entities", entities)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = { Text("Herramientas avanzadas de Sara", color = Color.White, fontSize = 18.sp) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Consultas protegidas con tu sesión Firebase. Los cambios de tracker afectan solo tu conversación.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onRun("status") }, enabled = !isLoading, modifier = Modifier.weight(1f)) {
                        Text("Estado", color = Color.White)
                    }
                    OutlinedButton(onClick = { onRun("version") }, enabled = !isLoading, modifier = Modifier.weight(1f)) {
                        Text("Versión", color = Color.White)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onRun("tracker") }, enabled = !isLoading, modifier = Modifier.weight(1f)) {
                        Text("Tracker", color = Color.White)
                    }
                    OutlinedButton(onClick = { onRun("story") }, enabled = !isLoading, modifier = Modifier.weight(1f)) {
                        Text("Story", color = Color.White)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onRun("domain") }, enabled = !isLoading, modifier = Modifier.weight(1f)) {
                        Text("Dominio", color = Color.White)
                    }
                    OutlinedButton(onClick = { onRun("predict") }, enabled = !isLoading, modifier = Modifier.weight(1f)) {
                        Text("Predecir", color = Color.White)
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Texto o nombre de intención") },
                    placeholder = { Text("Ej.: hola o greet") },
                    enabled = !isLoading,
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF818CF8),
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedLabelColor = Color(0xFFA5B4FC),
                        unfocusedLabelColor = Color(0xFF94A3B8),
                        cursorColor = Color(0xFFA5B4FC)
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onRun("parse") }, enabled = !isLoading && input.isNotBlank(), modifier = Modifier.weight(1f)) {
                        Text("Analizar", color = Color.White)
                    }
                    OutlinedButton(onClick = { onRun("trigger") }, enabled = !isLoading && input.isNotBlank(), modifier = Modifier.weight(1f)) {
                        Text("Activar intención", color = Color.White)
                    }
                }
                OutlinedButton(
                    onClick = { onRun("message") },
                    enabled = !isLoading && input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Agregar mensaje al tracker", color = Color.White)
                }
                HorizontalDivider(color = Color(0xFF334155))
                Text("Configurar evento de tracker", color = Color(0xFFA5B4FC), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Elige el tipo: los campos cambian según el esquema de Rasa.",
                    color = Color(0xFF94A3B8), fontSize = 12.sp
                )
                Box {
                    OutlinedButton(
                        onClick = { eventMenuExpanded = true },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(saraEventOptions.first { it.value == selectedEvent }.label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = eventMenuExpanded,
                        onDismissRequest = { eventMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 360.dp).background(Color(0xFF1E293B))
                    ) {
                        saraEventOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label, color = Color.White, fontSize = 13.sp) },
                                onClick = {
                                    selectedEvent = option.value
                                    eventMenuExpanded = false
                                    eventFormError = null
                                }
                            )
                        }
                    }
                }
                if (selectedEvent == "user") {
                    SaraEventInput(eventText, { eventText = it }, "Texto (opcional)")
                    SaraEventInput(eventInputChannel, { eventInputChannel = it }, "Canal de entrada", placeholder = "rest")
                    SaraEventInput(eventMessageId, { eventMessageId = it }, "ID de mensaje (opcional)")
                    SaraEventInput(eventParseData, { eventParseData = it }, "parse_data JSON (opcional)", maxLines = 3)
                }
                if (selectedEvent == "slot") {
                    SaraEventInput(slotName, { slotName = it }, "Nombre del slot *")
                    SaraEventInput(slotValue, { slotValue = it }, "Valor JSON *", placeholder = "null, true, 42 o \"texto\"")
                }
                if (selectedEvent == "action") {
                    SaraEventInput(actionName, { actionName = it }, "Nombre de acción (opcional)")
                    SaraEventInput(actionPolicy, { actionPolicy = it }, "Política (opcional)")
                    SaraEventInput(actionConfidence, { actionConfidence = it }, "Confianza numérica (opcional)")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hideRuleTurn, onCheckedChange = { hideRuleTurn = it }, enabled = !isLoading)
                        Text("hide_rule_turn", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                    }
                    SaraEventInput(actionText, { actionText = it }, "Texto de acción (opcional)")
                }
                if (selectedEvent == "entities") {
                    SaraEventInput(entitiesJson, { entitiesJson = it }, "Entidades JSON *", placeholder = "[{\"entity\":\"lugar\",\"value\":\"Heredia\"}]", maxLines = 5)
                }
                SaraEventInput(eventTimestamp, { eventTimestamp = it }, "Timestamp entero (opcional)")
                SaraEventInput(eventMetadata, { eventMetadata = it }, "Metadata JSON object (opcional)", maxLines = 3)
                if (selectedEvent in setOf("restart", "reset_slots", "undo", "rewind")) {
                    Text(
                        "Este evento modifica o reinicia el historial del tracker.",
                        color = Color(0xFFFBBF24), fontSize = 12.sp
                    )
                }
                eventFormError?.let { Text(it, color = Color(0xFFFCA5A5), fontSize = 12.sp) }
                Button(
                    onClick = {
                        try {
                            val payload = makeEventPayload()
                            eventFormError = null
                            if (selectedEvent in setOf("restart", "reset_slots", "undo", "rewind")) {
                                pendingDestructiveEvent = payload
                            } else {
                                onAppendEvent(payload)
                            }
                        } catch (error: Exception) {
                            eventFormError = error.message ?: "Revisa los campos del evento."
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) {
                    Text("Enviar evento a mi tracker", color = Color.White)
                }
                if (isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFFA5B4FC))
                        Spacer(Modifier.width(8.dp))
                        Text("Consultando a Rasa…", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                    }
                }
                result?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("Respuesta", color = Color(0xFFA5B4FC), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                it,
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = Color(0xFFA5B4FC)) }
        }
    )

    pendingDestructiveEvent?.let { event ->
        AlertDialog(
            onDismissRequest = { pendingDestructiveEvent = null },
            containerColor = Color(0xFF1E293B),
            title = { Text("Confirmar cambio del tracker", color = Color.White) },
            text = { Text("Este evento puede reiniciar slots o modificar el historial de tu conversación. ¿Quieres continuar?", color = Color(0xFFCBD5E1)) },
            confirmButton = {
                TextButton(onClick = {
                    onAppendEvent(event)
                    pendingDestructiveEvent = null
                }) { Text("Sí, continuar", color = Color(0xFFFBBF24)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDestructiveEvent = null }) { Text("Cancelar", color = Color(0xFFA5B4FC)) }
            }
        )
    }
}

@Composable
private fun SaraEventInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    maxLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        maxLines = maxLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color(0xFF818CF8),
            unfocusedBorderColor = Color(0xFF475569),
            focusedLabelColor = Color(0xFFA5B4FC),
            unfocusedLabelColor = Color(0xFF94A3B8),
            cursorColor = Color(0xFFA5B4FC)
        )
    )
}


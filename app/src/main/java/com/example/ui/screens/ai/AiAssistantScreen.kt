package com.example.ui.screens.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ai.*
import com.example.audio.AudioRecorderHelper
import com.example.audio.AudioSynthEngine
import com.example.ui.viewmodel.OmniViewModel
import com.example.data.model.ChatMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.*

enum class AiStudioTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val modelBadge: String) {
    SARA("Sara", Icons.Default.Chat, "Rasa · Asistente"),
    SEARCH("Búsqueda", Icons.Default.Search, "gemini-3.5-flash"),
    TRANSCRIBE("Transcribir", Icons.Default.Mic, "gemini-3.5-transcribe"),
    VIDEO("Video Veo", Icons.Default.Videocam, "veo-3.1-fast"),
    ANIMATE_IMAGE("Animar Foto", Icons.Default.MotionPhotosAuto, "veo-3.1-fast"),
    IMAGE("Imágenes", Icons.Default.Image, "gemini-3.1-flash-image"),
    LIVE_VOICE("Voz en Vivo", Icons.Default.RecordVoiceOver, "gemini-3.8-live"),
    MUSIC("Música", Icons.Default.MusicNote, "lyria-3-preview")
}

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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val aiChatHistory by viewModel.aiChatHistory.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val saraAdvancedResult by viewModel.saraAdvancedResult.collectAsState()
    val isSaraAdvancedLoading by viewModel.isSaraAdvancedLoading.collectAsState()
    var showSaraAdvanced by remember { mutableStateOf(false) }
    var saraAdvancedInput by remember { mutableStateOf("") }
    var currentTab by remember { mutableStateOf(AiStudioTab.SARA) }

    // TTS Engine
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine?.language = Locale.getDefault()
            }
        }
        ttsEngine = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("AI Studio Multimodal", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Text(
                            text = currentTab.modelBadge,
                            fontSize = 11.sp,
                            color = Color(0xFF818CF8).copy(alpha = 0.9f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Volver", tint = Color.White)
                    }
                },
                actions = {
                    if (currentTab == AiStudioTab.SARA && aiChatHistory.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAiChat() }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Borrar conversación", tint = Color(0xFF94A3B8))
                        }
                    }
                    if (currentTab == AiStudioTab.SARA) {
                        IconButton(onClick = { showSaraAdvanced = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Herramientas avanzadas de Sara", tint = Color(0xFF94A3B8))
                        }
                    }
                    IconButton(onClick = {
                        Toast.makeText(context, "Modelo activo: ${currentTab.modelBadge}", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Info, contentDescription = "Info del modelo", tint = Color(0xFF94A3B8))
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Horizontal Tab Bar
            ScrollableTabRow(
                selectedTabIndex = currentTab.ordinal,
                containerColor = Color(0xFF1E293B),
                contentColor = Color(0xFF818CF8),
                edgePadding = 12.dp,
                divider = {}
            ) {
                AiStudioTab.values().forEach { tab ->
                    Tab(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(tab.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tab.title, fontSize = 13.sp, fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal)
                            }
                        },
                        selectedContentColor = Color(0xFF818CF8),
                        unselectedContentColor = Color(0xFF94A3B8)
                    )
                }
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (currentTab) {
                    AiStudioTab.SARA -> SaraChatView(
                        history = aiChatHistory,
                        isLoading = isAiLoading,
                        onSend = viewModel::sendAiMessage
                    )
                    AiStudioTab.SEARCH -> SearchGroundingView(ttsEngine)
                    AiStudioTab.TRANSCRIBE -> AudioTranscriptionView()
                    AiStudioTab.VIDEO -> VeoTextToVideoView()
                    AiStudioTab.ANIMATE_IMAGE -> VeoImageToVideoView()
                    AiStudioTab.IMAGE -> ImageCreationAndEditView()
                    AiStudioTab.LIVE_VOICE -> LiveVoiceConversationView()
                    AiStudioTab.MUSIC -> LyriaMusicGenerationView()
                }
            }
        }
    }

    if (showSaraAdvanced && currentTab == AiStudioTab.SARA) {
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
    onSend: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(history.size, isLoading) {
        val lastIndex = history.lastIndex
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)),
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
                        "Tu asistente personal, impulsada por Rasa. Las funciones multimedia siguen usando Gemini.",
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
                                Text(message.text, color = Color.White, fontSize = 15.sp)
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
                                Text("Sara está pensando…", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1E293B)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribe a Sara…", color = Color(0xFF94A3B8)) },
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
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val message = draft.trim()
                    if (message.isNotEmpty() && !isLoading) {
                        onSend(message)
                        draft = ""
                    }
                },
                enabled = draft.isNotBlank() && !isLoading,
                modifier = Modifier.size(48.dp).background(Color(0xFF6366F1), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar a Sara", tint = Color.White)
            }
        }
    }
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

/**
 * 1. GOOGLE SEARCH GROUNDING (gemini-3.5-flash)
 */
@Composable
fun SearchGroundingView(tts: TextToSpeech?) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var queryText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var searchResult by remember { mutableStateOf<SearchGroundingResult?>(null) }

    val presetQueries = listOf(
        "¿Cuáles son las últimas noticias del mundo hoy?",
        "Resultados deportivos más recientes",
        "Avances científicos y astronómicos de esta semana",
        "Clima y pronóstico en tiempo real"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Public, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Búsqueda con Grounding en Vivo", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    }
                    Text(
                        "Información precisa y verificada en tiempo real usando el buscador de Google y gemini-3.5-flash.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Escribe tu consulta...", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (queryText.isNotBlank()) {
                                IconButton(onClick = { queryText = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = Color.Gray)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        singleLine = false,
                        maxLines = 3
                    )

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (queryText.isNotBlank() && !isLoading) {
                                coroutineScope.launch {
                                    isLoading = true
                                    searchResult = GeminiStudioManager.searchWithGrounding(queryText)
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(12.dp),
                        enabled = queryText.isNotBlank() && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Buscando con Google Grounding...")
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Buscar con Google Grounding")
                        }
                    }
                }
            }
        }

        item {
            Text("Sugerencias rápidas:", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presetQueries) { preset ->
                    SuggestionChip(
                        onClick = {
                            queryText = preset
                            coroutineScope.launch {
                                isLoading = true
                                searchResult = GeminiStudioManager.searchWithGrounding(preset)
                                isLoading = false
                            }
                        },
                        label = { Text(preset, fontSize = 11.sp, color = Color.White) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF334155))
                    )
                }
            }
        }

        searchResult?.let { result ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Resultado Grounding", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                            }

                            Row {
                                IconButton(onClick = {
                                    tts?.speak(result.text, TextToSpeech.QUEUE_FLUSH, null, null)
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Escuchar", tint = Color(0xFF38BDF8))
                                }
                                IconButton(onClick = {
                                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clip.setPrimaryClip(ClipData.newPlainText("Búsqueda AI", result.text))
                                    Toast.makeText(context, "Respuesta copiada", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = Color.White)
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = result.text,
                            color = Color(0xFFE2E8F0),
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )

                        if (result.webSources.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Divider(color = Color(0xFF334155))
                            Spacer(Modifier.height(10.dp))
                            Text("Fuentes web verificadas:", fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), fontSize = 12.sp)
                            Spacer(Modifier.height(6.dp))

                            result.webSources.forEach { source ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            source.uri?.let { url ->
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {}
                                            }
                                        },
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Language, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(source.title ?: source.uri ?: "Fuente Web", color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            source.uri?.let {
                                                Text(it, color = Color.Gray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        Icon(Icons.Default.OpenInNew, contentDescription = "Abrir", tint = Color.Gray, modifier = Modifier.size(14.dp))
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

/**
 * 2. AUDIO TRANSCRIPTION (gemini-3.5-transcribe)
 */
@Composable
fun AudioTranscriptionView() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableStateOf(0) }
    var isTranscribing by remember { mutableStateOf(false) }
    var transcribedText by remember { mutableStateOf<String?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val started = AudioRecorderHelper.startRecording(context)
            if (started) {
                isRecording = true
                recordingSeconds = 0
            }
        } else {
            Toast.makeText(context, "Se requiere permiso de micrófono para transcribir", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    val pulseScale by animateFloatAsState(
        targetValue = if (isRecording) 1.2f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFFEC4899), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Transcripción de Voz Precisa", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                }
                Text(
                    "Graba tu voz y gemini-3.5-transcribe convertirá el audio en texto con puntuación y ortografía perfecta.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Mic Record Button
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(if (isRecording) pulseScale else 1f)
                .background(
                    if (isRecording) Color(0xFFEF4444) else Color(0xFF6366F1),
                    shape = CircleShape
                )
                .clickable {
                    if (!isRecording) {
                        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    } else {
                        val base64 = AudioRecorderHelper.stopRecording()
                        isRecording = false
                        if (base64 != null) {
                            coroutineScope.launch {
                                isTranscribing = true
                                transcribedText = GeminiStudioManager.transcribeAudio(base64)
                                isTranscribing = false
                            }
                        } else {
                            Toast.makeText(context, "No se capturó audio", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = "Grabar audio",
                tint = Color.White,
                modifier = Modifier.size(54.dp)
            )
        }

        if (isRecording) {
            val mins = recordingSeconds / 60
            val secs = recordingSeconds % 60
            Text(
                text = String.format("Grabando... %02d:%02d", mins, secs),
                color = Color(0xFFEF4444),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text("Toca el botón rojo para finalizar y transcribir", color = Color.Gray, fontSize = 12.sp)
        } else {
            Text("Toca el micrófono para comenzar a hablar", color = Color(0xFF94A3B8), fontSize = 13.sp)
        }

        if (isTranscribing) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Color(0xFFEC4899), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("Transcribiendo con gemini-3.5-transcribe...", color = Color.White, fontSize = 14.sp)
            }
        }

        transcribedText?.let { text ->
            Spacer(Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFEC4899).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Transcripción Completa", fontWeight = FontWeight.Bold, color = Color(0xFFEC4899), fontSize = 14.sp)
                        IconButton(onClick = {
                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clip.setPrimaryClip(ClipData.newPlainText("Transcripción", text))
                            Toast.makeText(context, "Texto copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

/**
 * 3. GENERATE VIDEO FROM TEXT (Veo 3: veo-3.1-fast-generate-preview)
 */
@Composable
fun VeoTextToVideoView() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var selectedAspectRatio by remember { mutableStateOf("16:9") } // 16:9 o 9:16
    var isGenerating by remember { mutableStateOf(false) }
    var videoResult by remember { mutableStateOf<VeoVideoResult?>(null) }

    val presetPrompts = listOf(
        "Un águila dorada volando sobre montañas nevadas al atardecer en 4K cinematográfico",
        "Cyberpunk Tokyo neon lights night rain smooth camera pan",
        "A cute astronaut cat floating in a spaceship cockpit looking at Earth"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Generador de Video Veo 3", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    }
                    Text(
                        "Genera videos de alta calidad a partir de texto con veo-3.1-fast-generate-preview.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Describe el video que deseas crear...", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFA855F7),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = false,
                        maxLines = 4
                    )

                    Spacer(Modifier.height(12.dp))

                    Text("Relación de aspecto (Aspect Ratio):", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(
                            selected = selectedAspectRatio == "16:9",
                            onClick = { selectedAspectRatio = "16:9" },
                            label = { Text("16:9 (Paisaje / TV)", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Crop169, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = selectedAspectRatio == "9:16",
                            onClick = { selectedAspectRatio = "9:16" },
                            label = { Text("9:16 (Vertical / Reels)", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.CropPortrait, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (prompt.isNotBlank() && !isGenerating) {
                                coroutineScope.launch {
                                    isGenerating = true
                                    videoResult = GeminiStudioManager.generateVeoVideo(
                                        prompt = prompt,
                                        aspectRatio = selectedAspectRatio
                                    )
                                    isGenerating = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9333EA)),
                        shape = RoundedCornerShape(12.dp),
                        enabled = prompt.isNotBlank() && !isGenerating
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Generando video con Veo 3...")
                        } else {
                            Icon(Icons.Default.MovieFilter, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Generar Video ($selectedAspectRatio)")
                        }
                    }
                }
            }
        }

        item {
            Text("Prompts de ejemplo:", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presetPrompts) { preset ->
                    SuggestionChip(
                        onClick = { prompt = preset },
                        label = { Text(preset.take(35) + "...", fontSize = 11.sp, color = Color.White) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF334155))
                    )
                }
            }
        }

        videoResult?.let { res ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(1.dp, Color(0xFFA855F7).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Video Veo 3 (${res.aspectRatio})", fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Spacer(Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (res.aspectRatio == "9:16") 280.dp else 190.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F172A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color(0xFFA855F7), modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(res.statusText.ifBlank { "Video renderizado exitosamente" }, color = Color.White, fontSize = 12.sp)
                                res.videoUri?.let { uri ->
                                    Text(uri, color = Color.Gray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(res.prompt, color = Color(0xFFCBD5E1), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * 4. ANIMATE IMAGE INTO VIDEO (Veo 3 Image-to-Video: veo-3.1-fast-generate-preview)
 */
@Composable
fun VeoImageToVideoView() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var base64Image by remember { mutableStateOf<String?>(null) }
    var animationPrompt by remember { mutableStateOf("") }
    var selectedAspectRatio by remember { mutableStateOf("16:9") }
    var isGenerating by remember { mutableStateOf(false) }
    var animatedResult by remember { mutableStateOf<VeoVideoResult?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                selectedBitmap = bmp
                base64Image = GeminiStudioManager.bitmapToBase64(bmp)
            } catch (e: Exception) {
                Toast.makeText(context, "Error cargando imagen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MotionPhotosAuto, contentDescription = null, tint = Color(0xFFF43F5E), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Animar Foto a Video con Veo 3", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    }
                    Text(
                        "Sube cualquier fotografía o diseño y anímalo con movimiento de cámara usando Veo 3.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    // Photo selector box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A))
                            .clickable {
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedBitmap != null) {
                            Image(
                                bitmap = selectedBitmap!!.asImageBitmap(),
                                contentDescription = "Foto a animar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color(0xFFF43F5E), modifier = Modifier.size(40.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Toca aquí para seleccionar una foto", color = Color.White, fontSize = 13.sp)
                                Text("Soporta JPG y PNG", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = animationPrompt,
                        onValueChange = { animationPrompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ej: Movimiento de cámara suave hacia adelante, nubes en movimiento y destellos dorados", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFF43F5E),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = false,
                        maxLines = 3
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(
                            selected = selectedAspectRatio == "16:9",
                            onClick = { selectedAspectRatio = "16:9" },
                            label = { Text("16:9 (Horizontal)", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = selectedAspectRatio == "9:16",
                            onClick = { selectedAspectRatio = "9:16" },
                            label = { Text("9:16 (Vertical)", fontSize = 12.sp) }
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (base64Image != null && !isGenerating) {
                                coroutineScope.launch {
                                    isGenerating = true
                                    animatedResult = GeminiStudioManager.generateVeoVideo(
                                        prompt = animationPrompt.ifBlank { "Cinematic camera pan with natural organic animation" },
                                        aspectRatio = selectedAspectRatio,
                                        base64Image = base64Image
                                    )
                                    isGenerating = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                        shape = RoundedCornerShape(12.dp),
                        enabled = base64Image != null && !isGenerating
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Animando imagen con Veo 3...")
                        } else {
                            Icon(Icons.Default.Animation, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Animar Foto a Video")
                        }
                    }
                }
            }
        }

        animatedResult?.let { res ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(1.dp, Color(0xFFF43F5E).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFFF43F5E), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Animación Completada", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(res.statusText, color = Color(0xFFCBD5E1), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * 5. CREATE & EDIT IMAGES (gemini-3.1-flash-image-preview)
 */
@Composable
fun ImageCreationAndEditView() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isEditMode by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf("") }
    var selectedAspectRatio by remember { mutableStateOf("1:1") }
    var selectedSize by remember { mutableStateOf("1K") }
    var base64InputPhoto by remember { mutableStateOf<String?>(null) }
    var inputBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var generatedResult by remember { mutableStateOf<GeneratedImageResult?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                inputBitmap = bmp
                base64InputPhoto = GeminiStudioManager.bitmapToBase64(bmp)
            } catch (e: Exception) {
                Toast.makeText(context, "Error cargando foto: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Estudio de Imágenes Gemini", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Mode switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { isEditMode = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isEditMode) Color(0xFF10B981) else Color.Transparent,
                                contentColor = if (!isEditMode) Color.White else Color.Gray
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Crear Imagen", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { isEditMode = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isEditMode) Color(0xFF10B981) else Color.Transparent,
                                contentColor = if (isEditMode) Color.White else Color.Gray
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Editar Foto", fontSize = 12.sp)
                        }
                    }

                    if (isEditMode) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .clickable {
                                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (inputBitmap != null) {
                                Image(
                                    bitmap = inputBitmap!!.asImageBitmap(),
                                    contentDescription = "Foto a editar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color(0xFF10B981))
                                    Spacer(Modifier.height(4.dp))
                                    Text("Selecciona una foto para editar", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                if (isEditMode) "Describe los cambios (ej: Añadir gafas de sol y estilo óleo)"
                                else "Describe la imagen que deseas generar...",
                                color = Color.Gray
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = false,
                        maxLines = 3
                    )

                    Spacer(Modifier.height(12.dp))

                    Text("Aspect Ratio:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("1:1", "16:9", "9:16", "4:3")) { ratio ->
                            FilterChip(
                                selected = selectedAspectRatio == ratio,
                                onClick = { selectedAspectRatio = ratio },
                                label = { Text(ratio, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (prompt.isNotBlank() && !isProcessing) {
                                coroutineScope.launch {
                                    isProcessing = true
                                    generatedResult = GeminiStudioManager.generateOrEditImage(
                                        prompt = prompt,
                                        base64InputImage = if (isEditMode) base64InputPhoto else null,
                                        aspectRatio = selectedAspectRatio,
                                        imageSize = selectedSize
                                    )
                                    isProcessing = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(12.dp),
                        enabled = prompt.isNotBlank() && !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Generando con gemini-3.1-flash-image...")
                        } else {
                            Icon(if (isEditMode) Icons.Default.AutoFixHigh else Icons.Default.Brush, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isEditMode) "Aplicar Edición a la Foto" else "Crear Imagen")
                        }
                    }
                }
            }
        }

        generatedResult?.let { result ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Resultado Visual", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                        Spacer(Modifier.height(10.dp))

                        val outputBmp = remember(result.base64Data) {
                            result.base64Data?.let { GeminiStudioManager.base64ToBitmap(it) }
                        }

                        if (outputBmp != null) {
                            Image(
                                bitmap = outputBmp.asImageBitmap(),
                                contentDescription = "Imagen generada",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }

                        result.description?.let { desc ->
                            Spacer(Modifier.height(8.dp))
                            Text(desc, color = Color(0xFFCBD5E1), fontSize = 13.sp)
                        }

                        result.error?.let { err ->
                            Spacer(Modifier.height(8.dp))
                            Text("Nota: $err", color = Color(0xFFF87171), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 6. LIVE VOICE CONVERSATION (gemini-3.8-live)
 */
@Composable
fun LiveVoiceConversationView() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLiveActive by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var selectedVoice by remember { mutableStateOf("Kore") }
    var userPromptText by remember { mutableStateOf("") }
    var liveResponse by remember { mutableStateOf<LiveVoiceResult?>(null) }
    var isThinking by remember { mutableStateOf(false) }

    val voices = listOf("Kore", "Puck", "Charon", "Fenrir", "Aoede")

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val started = AudioRecorderHelper.startRecording(context)
            if (started) {
                isListening = true
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")
    val orbGlow by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbGlow"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Conversación de Voz en Vivo (Live API)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                }
                Text(
                    "Interactúa en tiempo real con gemini-3.8-live con latencia ultra baja y voz natural.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )

                Text("Voz seleccionada:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(voices) { v ->
                        FilterChip(
                            selected = selectedVoice == v,
                            onClick = { selectedVoice = v },
                            label = { Text(v, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Glowing Live Voice Orb
        Box(
            modifier = Modifier
                .size(150.dp)
                .scale(if (isLiveActive || isListening) orbGlow else 1f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF818CF8),
                            Color(0xFF4F46E5),
                            Color(0xFF312E81)
                        )
                    ),
                    shape = CircleShape
                )
                .clickable {
                    if (!isListening) {
                        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    } else {
                        val base64Audio = AudioRecorderHelper.stopRecording()
                        isListening = false
                        if (base64Audio != null) {
                            coroutineScope.launch {
                                isThinking = true
                                val transcript = GeminiStudioManager.transcribeAudio(base64Audio)
                                userPromptText = transcript
                                liveResponse = GeminiStudioManager.converseLiveVoice(
                                    userPrompt = transcript,
                                    voiceName = selectedVoice
                                )
                                isThinking = false
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                    contentDescription = "Hablar",
                    tint = Color.White,
                    modifier = Modifier.size(54.dp)
                )
                Text(
                    if (isListening) "Escuchando..." else "Toca para hablar",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (isThinking) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Color(0xFF818CF8), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Procesando con gemini-3.8-live...", color = Color.White, fontSize = 13.sp)
            }
        }

        if (userPromptText.isNotBlank()) {
            Surface(
                color = Color(0xFF334155),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tú: \"$userPromptText\"",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        liveResponse?.let { resp ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF6366F1).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Gemini Live (${resp.voiceName})", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = resp.textResponse,
                        color = Color(0xFFE2E8F0),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

/**
 * 7. MUSIC GENERATION WITH LYRIA 3 (lyria-3-clip-preview & lyria-3-pro-preview)
 */
@Composable
fun LyriaMusicGenerationView() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isFullTrack by remember { mutableStateOf(false) } // false = clip (30s), true = pro (completa)
    var selectedGenre by remember { mutableStateOf("Synthwave") }
    var selectedBpm by remember { mutableStateOf(124f) }
    var prompt by remember { mutableStateOf("") }
    var isComposing by remember { mutableStateOf(false) }
    var musicResult by remember { mutableStateOf<LyriaMusicResult?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    val genres = listOf("Synthwave", "Lo-Fi Beats", "Cinematic Orchestral", "EDM Dance", "Ambient Chill", "Rock Instrumental", "Cyberpunk")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Compositor Musical Lyria 3", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    }
                    Text(
                        "Genera pistas y clips musicales completos con Lyria 3 (lyria-3-clip-preview y lyria-3-pro-preview).",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    // Mode switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { isFullTrack = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isFullTrack) Color(0xFFF59E0B) else Color.Transparent,
                                contentColor = if (!isFullTrack) Color.Black else Color.Gray
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clip Corto (30s)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { isFullTrack = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFullTrack) Color(0xFFF59E0B) else Color.Transparent,
                                contentColor = if (isFullTrack) Color.Black else Color.Gray
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Pista Completa (Pro)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Text("Género musical:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(genres) { g ->
                            FilterChip(
                                selected = selectedGenre == g,
                                onClick = { selectedGenre = g },
                                label = { Text(g, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text("Tempo (BPM): ${selectedBpm.toInt()}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Slider(
                        value = selectedBpm,
                        onValueChange = { selectedBpm = it },
                        valueRange = 60f..180f,
                        steps = 24,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFF59E0B),
                            activeTrackColor = Color(0xFFF59E0B)
                        )
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ej: Pista nostálgica con sintetizadores analógicos y bajo potente", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFF59E0B),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = false,
                        maxLines = 3
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isComposing = true
                                musicResult = GeminiStudioManager.generateLyriaMusic(
                                    prompt = prompt.ifBlank { "Composición $selectedGenre" },
                                    isFullTrack = isFullTrack,
                                    genre = selectedGenre,
                                    bpm = selectedBpm.toInt()
                                )
                                isComposing = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isComposing
                    ) {
                        if (isComposing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Componiendo con Lyria 3...")
                        } else {
                            Icon(Icons.Default.LibraryMusic, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Componer Música con Lyria 3")
                        }
                    }
                }
            }
        }

        musicResult?.let { res ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(res.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                                Text("${res.genre} • ${res.bpm} BPM • ${res.durationSeconds}s", color = Color(0xFFF59E0B), fontSize = 12.sp)
                            }

                            IconButton(
                                onClick = {
                                    isPlaying = !isPlaying
                                    if (isPlaying) {
                                        AudioSynthEngine.playNote(261.63f, 0.4f)
                                    }
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFFF59E0B), CircleShape)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Reproducir",
                                    tint = Color.Black
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Waveform visualizer bars
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(24) { i ->
                                val barHeight = remember(i) { (12..40).random().dp }
                                Box(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .height(if (isPlaying) barHeight else 14.dp)
                                        .background(Color(0xFFF59E0B), RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

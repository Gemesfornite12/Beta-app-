package com.example.ui.screens.ai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class WorkspaceActionField(
    val key: String,
    val label: String,
    val required: Boolean = true,
    val multiline: Boolean = false,
    val defaultValue: String = ""
)

internal enum class GoogleWorkspaceActionType(
    val title: String,
    val scope: String,
    val changesData: Boolean,
    val irreversible: Boolean = false,
    val fields: List<WorkspaceActionField> = emptyList(),
    val needsFile: Boolean = false
) {
    SEARCH_EMAILS("Buscar correos", "https://www.googleapis.com/auth/gmail.messages.readonly", false, fields = listOf(WorkspaceActionField("query", "Búsqueda de Gmail", false))),
    READ_EMAIL("Leer correo", "https://www.googleapis.com/auth/gmail.messages.readonly", false, fields = listOf(WorkspaceActionField("messageId", "ID del correo"))),
    LIST_LABELS("Listar etiquetas de Gmail", "https://www.googleapis.com/auth/gmail.labels.readonly", false),
    CREATE_LABEL("Crear etiqueta", "https://www.googleapis.com/auth/gmail.labels.create", true, fields = listOf(WorkspaceActionField("name", "Nombre de etiqueta"))),
    UPDATE_LABEL("Cambiar etiqueta", "https://www.googleapis.com/auth/gmail.labels", true, fields = listOf(WorkspaceActionField("labelId", "ID de etiqueta"), WorkspaceActionField("name", "Nombre nuevo", false))),
    DELETE_LABEL("Eliminar etiqueta", "https://www.googleapis.com/auth/gmail.labels", true, fields = listOf(WorkspaceActionField("labelId", "ID de etiqueta"))),
    MODIFY_EMAIL_LABELS("Cambiar etiquetas de correo", "https://www.googleapis.com/auth/gmail.modify", true, fields = listOf(WorkspaceActionField("messageId", "ID del correo"), WorkspaceActionField("addLabels", "Agregar IDs (separados por coma)", false), WorkspaceActionField("removeLabels", "Quitar IDs (separados por coma)", false))),
    LIST_DRAFTS("Listar borradores", "https://www.googleapis.com/auth/gmail.drafts.readonly", false),
    READ_DRAFT("Leer borrador", "https://www.googleapis.com/auth/gmail.drafts.readonly", false, fields = listOf(WorkspaceActionField("draftId", "ID del borrador"))),
    CREATE_DRAFT("Crear borrador", "https://www.googleapis.com/auth/gmail.drafts.create", true, fields = listOf(WorkspaceActionField("recipient", "Para (correo)"), WorkspaceActionField("subject", "Asunto"), WorkspaceActionField("body", "Mensaje", multiline = true))),
    SEND_DRAFT("Enviar borrador", "https://www.googleapis.com/auth/gmail.drafts", true, fields = listOf(WorkspaceActionField("draftId", "ID del borrador"))),
    DELETE_DRAFT("Borrar borrador", "https://www.googleapis.com/auth/gmail.drafts", true, irreversible = true, fields = listOf(WorkspaceActionField("draftId", "ID del borrador"))),
    SEND_EMAIL("Enviar correo", "https://www.googleapis.com/auth/gmail.send", true, fields = listOf(WorkspaceActionField("recipient", "Para (correo)"), WorkspaceActionField("subject", "Asunto"), WorkspaceActionField("body", "Mensaje", multiline = true))),
    TRASH_EMAIL("Mover correo a papelera", "https://www.googleapis.com/auth/gmail.modify", true, fields = listOf(WorkspaceActionField("messageId", "ID del correo"))),
    DELETE_EMAIL("Borrar correo permanentemente", "https://www.googleapis.com/auth/gmail.messages.delete", true, irreversible = true, fields = listOf(WorkspaceActionField("messageId", "ID del correo"))),

    LIST_CALENDARS("Listar calendarios", "https://www.googleapis.com/auth/calendar.calendarlist.readonly", false),
    CREATE_CALENDAR("Crear calendario secundario", "https://www.googleapis.com/auth/calendar.app.created", true, fields = listOf(WorkspaceActionField("summary", "Nombre del calendario"), WorkspaceActionField("timeZone", "Zona horaria", defaultValue = "America/Costa_Rica"))),
    LIST_EVENTS("Buscar eventos", "https://www.googleapis.com/auth/calendar.events.readonly", false, fields = listOf(WorkspaceActionField("calendarId", "ID del calendario", false, defaultValue = "primary"), WorkspaceActionField("timeMin", "Desde (RFC3339)", false), WorkspaceActionField("timeMax", "Hasta (RFC3339)", false), WorkspaceActionField("query", "Texto de búsqueda", false))),
    FREE_BUSY("Consultar disponibilidad", "https://www.googleapis.com/auth/calendar.events.freebusy", false, fields = listOf(WorkspaceActionField("timeMin", "Desde (RFC3339)"), WorkspaceActionField("timeMax", "Hasta (RFC3339)"), WorkspaceActionField("timeZone", "Zona horaria", defaultValue = "America/Costa_Rica"), WorkspaceActionField("calendarIds", "IDs separados por coma", false, defaultValue = "primary"))),
    CREATE_EVENT("Crear evento", "https://www.googleapis.com/auth/calendar.events", true, fields = listOf(WorkspaceActionField("calendarId", "ID del calendario", false, defaultValue = "primary"), WorkspaceActionField("summary", "Título"), WorkspaceActionField("start", "Inicio (RFC3339)"), WorkspaceActionField("end", "Fin (RFC3339)"), WorkspaceActionField("timeZone", "Zona horaria", defaultValue = "America/Costa_Rica"), WorkspaceActionField("description", "Descripción", false, multiline = true))),
    UPDATE_EVENT("Editar evento", "https://www.googleapis.com/auth/calendar.events", true, fields = listOf(WorkspaceActionField("calendarId", "ID del calendario", false, defaultValue = "primary"), WorkspaceActionField("eventId", "ID del evento"), WorkspaceActionField("summary", "Título nuevo", false), WorkspaceActionField("start", "Inicio nuevo (RFC3339)", false), WorkspaceActionField("end", "Fin nuevo (RFC3339)", false), WorkspaceActionField("timeZone", "Zona horaria", false, defaultValue = "America/Costa_Rica"), WorkspaceActionField("description", "Descripción nueva", false, multiline = true))),
    DELETE_EVENT("Eliminar evento", "https://www.googleapis.com/auth/calendar.events", true, irreversible = true, fields = listOf(WorkspaceActionField("calendarId", "ID del calendario", false, defaultValue = "primary"), WorkspaceActionField("eventId", "ID del evento"))),

    SEARCH_DRIVE("Buscar archivos de Drive", "https://www.googleapis.com/auth/drive.file", false, fields = listOf(WorkspaceActionField("query", "Consulta Drive (opcional)", false))),
    DRIVE_METADATA("Ver detalles de archivo", "https://www.googleapis.com/auth/drive.file", false, fields = listOf(WorkspaceActionField("fileId", "ID del archivo"))),
    DOWNLOAD_DRIVE("Descargar archivo de Drive", "https://www.googleapis.com/auth/drive.file", false, fields = listOf(WorkspaceActionField("fileId", "ID del archivo"))),
    CREATE_FOLDER("Crear carpeta de Drive", "https://www.googleapis.com/auth/drive.file", true, fields = listOf(WorkspaceActionField("folderName", "Nombre de carpeta"))),
    UPLOAD_DRIVE("Subir archivo a Drive", "https://www.googleapis.com/auth/drive.file", true, needsFile = true),
    UPDATE_DRIVE_METADATA("Renombrar archivo de Drive", "https://www.googleapis.com/auth/drive.file", true, fields = listOf(WorkspaceActionField("fileId", "ID del archivo"), WorkspaceActionField("name", "Nombre nuevo", false), WorkspaceActionField("description", "Descripción nueva", false))),
    UPDATE_DRIVE_CONTENT("Actualizar contenido de archivo", "https://www.googleapis.com/auth/drive.file", true, fields = listOf(WorkspaceActionField("fileId", "ID del archivo")), needsFile = true),
    TRASH_DRIVE("Mover archivo a papelera", "https://www.googleapis.com/auth/drive.file", true, fields = listOf(WorkspaceActionField("fileId", "ID del archivo"))),
    DELETE_DRIVE("Borrar archivo permanentemente", "https://www.googleapis.com/auth/drive.file", true, irreversible = true, fields = listOf(WorkspaceActionField("fileId", "ID del archivo")))
}

internal data class GoogleWorkspaceActionRequest(
    val type: GoogleWorkspaceActionType,
    val values: Map<String, String>,
    val fileUri: Uri? = null
)

@Composable
internal fun GoogleWorkspaceActionsDialog(
    onDismiss: () -> Unit,
    onRun: (GoogleWorkspaceActionRequest) -> Unit,
    initialRequest: GoogleWorkspaceActionRequest? = null,
    requestText: String? = null
) {
    var selected by remember(initialRequest?.type) { mutableStateOf(initialRequest?.type) }
    var search by remember(initialRequest?.type) { mutableStateOf("") }
    var values by remember(selected, initialRequest) {
        mutableStateOf(selected?.fields?.associate { field ->
            field.key to (initialRequest?.values?.get(field.key)?.takeIf(String::isNotBlank) ?: field.defaultValue)
        }.orEmpty())
    }
    var fileUri by remember(selected) { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<GoogleWorkspaceActionRequest?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> fileUri = uri }

    fun makeRequest(): GoogleWorkspaceActionRequest? {
        val action = selected ?: run { validationError = "Elige una acción."; return null }
        val missing = action.fields.firstOrNull { it.required && values[it.key].isNullOrBlank() }
        if (missing != null) { validationError = "Completa: ${missing.label}."; return null }
        val reservedIds = setOf("id", "permanentemente", "definitivamente", "consulta", "nombre", "correo", "archivo", "evento")
        val placeholder = action.fields.firstOrNull { field ->
            val value = values[field.key].orEmpty().trim()
            value.matches(Regex("\\[[^\\]]+\\]")) ||
                (field.key.endsWith("id", ignoreCase = true) && value.lowercase() in reservedIds)
        }
        if (placeholder != null) { validationError = "Reemplaza el marcador por un dato real: ${placeholder.label}."; return null }
        if (action.needsFile && fileUri == null) { validationError = "Elige un archivo primero."; return null }
        validationError = null
        return GoogleWorkspaceActionRequest(action, values, fileUri)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Google Workspace · pruebas") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                requestText?.let {
                    Text("Entendí: ${it.take(260)}", style = MaterialTheme.typography.bodySmall)
                    Text("Revisa y completa los datos; no se ejecutará nada sin tu confirmación.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                Text("Elige una acción. Las modificaciones muestran una vista previa antes de autorizar.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(search, { search = it }, label = { Text("Filtrar o cambiar acción") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (selected == null || search.isNotBlank()) {
                    LazyColumn(Modifier.heightIn(max = 150.dp).fillMaxWidth()) {
                        items(GoogleWorkspaceActionType.entries.filter { it.title.contains(search, ignoreCase = true) }.toList()) { action ->
                            TextButton(onClick = { selected = action; search = "" }, modifier = Modifier.fillMaxWidth()) {
                                Text(action.title, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                selected?.let { action ->
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text(action.title, style = MaterialTheme.typography.titleSmall)
                    action.fields.forEach { field ->
                        val value = values[field.key].orEmpty()
                        OutlinedTextField(
                            value = value,
                            onValueChange = { values = values + (field.key to it) },
                            label = { Text(field.label) },
                            singleLine = !field.multiline,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                    }
                    if (action.needsFile) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("Elegir archivo") }
                        fileUri?.let { Text("Seleccionado: ${it.lastPathSegment ?: "archivo"}", style = MaterialTheme.typography.bodySmall) }
                    }
                    validationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val request = makeRequest() ?: return@TextButton
                if (request.type.changesData) preview = request else { onRun(request); onDismiss() }
            }) { Text(if (selected?.changesData == true) "Vista previa" else "Ejecutar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )

    preview?.let { request ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(if (request.type.irreversible) "Confirmar acción irreversible" else "Confirmar acción") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(request.type.title, style = MaterialTheme.typography.titleSmall)
                    request.values.filterValues(String::isNotBlank).forEach { (key, value) ->
                        Text("$key: $value", style = MaterialTheme.typography.bodySmall)
                    }
                    request.fileUri?.let { Text("Archivo: ${it.lastPathSegment ?: "seleccionado"}", style = MaterialTheme.typography.bodySmall) }
                    if (request.type.irreversible) {
                        Spacer(Modifier.height(8.dp))
                        Text("Esta operación puede ser permanente y no se podrá deshacer.", color = MaterialTheme.colorScheme.error)
                    }
                    Text("Google pedirá el permiso correspondiente antes de ejecutar la acción.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { preview = null; onRun(request); onDismiss() }) { Text("Confirmar y autorizar") }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
internal fun WorkspaceActionChatCard(
    request: GoogleWorkspaceActionRequest,
    isRunning: Boolean,
    onConfirm: (GoogleWorkspaceActionRequest) -> Unit,
    onCancel: () -> Unit
) {
    var values by remember(request) {
        mutableStateOf(request.type.fields.associate { field ->
            field.key to (request.values[field.key]?.takeIf(String::isNotBlank) ?: field.defaultValue)
        })
    }
    var fileUri by remember(request) { mutableStateOf(request.fileUri) }
    var previewing by remember(request) { mutableStateOf(false) }
    var validationError by remember(request) { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> fileUri = uri }

    fun prepareRequest(): GoogleWorkspaceActionRequest? {
        val missing = request.type.fields.firstOrNull { it.required && values[it.key].isNullOrBlank() }
        if (missing != null) {
            validationError = "Completa: ${missing.label}."
            return null
        }
        val reservedIds = setOf("id", "permanentemente", "definitivamente", "consulta", "nombre", "correo", "archivo", "evento")
        val placeholder = request.type.fields.firstOrNull { field ->
            val value = values[field.key].orEmpty().trim()
            value.matches(Regex("\\[[^\\]]+\\]")) ||
                (field.key.endsWith("id", ignoreCase = true) && value.lowercase() in reservedIds)
        }
        if (placeholder != null) {
            validationError = "Reemplaza el marcador por un dato real: ${placeholder.label}."
            return null
        }
        if (request.type.needsFile && fileUri == null) {
            validationError = "Elige un archivo primero."
            return null
        }
        validationError = null
        return GoogleWorkspaceActionRequest(request.type, values, fileUri)
    }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Google Workspace", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(request.type.title, style = MaterialTheme.typography.titleMedium)
            if (isRunning) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Esperando autorización y respuesta de Google…", style = MaterialTheme.typography.bodySmall)
                }
            } else if (!previewing) {
                Text(
                    if (request.type.changesData) "Revisa o completa los datos. Después verás una vista previa antes de confirmar."
                    else "Completa los datos para consultar Google Workspace.",
                    style = MaterialTheme.typography.bodySmall
                )
                request.type.fields.forEach { field ->
                    OutlinedTextField(
                        value = values[field.key].orEmpty(),
                        onValueChange = { values = values + (field.key to it); validationError = null },
                        label = { Text(field.label + if (field.required) " *" else "") },
                        singleLine = !field.multiline,
                        minLines = if (field.multiline) 3 else 1,
                        enabled = !isRunning,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (request.type.needsFile) {
                    OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }, enabled = !isRunning) {
                        Text(if (fileUri == null) "Elegir archivo" else "Cambiar archivo")
                    }
                    fileUri?.let { Text("Seleccionado: ${it.lastPathSegment ?: "archivo"}", style = MaterialTheme.typography.bodySmall) }
                }
            } else {
                Text("Vista previa · confirma solo si los datos están correctos", style = MaterialTheme.typography.bodySmall)
                request.type.fields.forEach { field ->
                    val value = values[field.key].orEmpty()
                    if (value.isNotBlank()) {
                        Text("${field.label}: $value", style = MaterialTheme.typography.bodySmall)
                    }
                }
                fileUri?.let { Text("Archivo: ${it.lastPathSegment ?: "seleccionado"}", style = MaterialTheme.typography.bodySmall) }
                if (request.type.irreversible) {
                    Text("Esta acción puede ser permanente y no se podrá deshacer.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text("Google pedirá el permiso correspondiente antes de ejecutarla.", style = MaterialTheme.typography.bodySmall)
            }
            validationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TextButton(onClick = onCancel, enabled = !isRunning) { Text("Cancelar") }
                if (previewing) {
                    TextButton(onClick = { previewing = false }, enabled = !isRunning) { Text("Editar") }
                    Button(onClick = { prepareRequest()?.let(onConfirm) }, enabled = !isRunning) {
                        Text(if (request.type.irreversible) "Confirmar borrado" else "Confirmar y autorizar")
                    }
                } else {
                    Button(onClick = {
                        val ready = prepareRequest() ?: return@Button
                        if (request.type.changesData) previewing = true else onConfirm(ready)
                    }, enabled = !isRunning) {
                        Text(if (request.type.changesData) "Vista previa" else "Consultar")
                    }
                }
            }
        }
    }
}

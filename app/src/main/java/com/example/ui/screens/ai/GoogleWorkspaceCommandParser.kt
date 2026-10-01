package com.example.ui.screens.ai

import java.text.Normalizer
import java.util.Locale

internal data class ParsedWorkspaceCommand(
    val request: GoogleWorkspaceActionRequest,
    val originalText: String
)

/** Recognizes explicit Google Workspace commands from Sara's chat. It only prepares a form;
 * execution still requires the existing preview and explicit confirmation for changes. */
internal object GoogleWorkspaceCommandParser {
    private val emailPattern = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
    private val idPattern = Regex("(?:id|identificador)\\s*[:=]?\\s*([A-Z0-9_-]{5,})", RegexOption.IGNORE_CASE)
    private val subjectPattern = Regex("(?:asunto|subject)\\s*[:=]\\s*(.+?)(?=\\s+(?:mensaje|cuerpo|body)\\s*[:=]|$)", RegexOption.IGNORE_CASE)
    private val bodyPattern = Regex("(?:mensaje|cuerpo|body)\\s*[:=]\\s*(.+)$", RegexOption.IGNORE_CASE)

    fun parse(text: String): ParsedWorkspaceCommand? {
        val normalized = normalize(text)
        fun has(vararg words: String) = words.any { normalized.contains(it) }
        fun request(type: GoogleWorkspaceActionType, values: Map<String, String> = emptyMap()) =
            ParsedWorkspaceCommand(GoogleWorkspaceActionRequest(type, values), text)
        fun captured(pattern: Regex) = pattern.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val id = idPattern.find(text)?.groupValues?.getOrNull(1).orEmpty()
        val recipient = emailPattern.find(text)?.value.orEmpty()

        // Gmail: destructive intents are matched before more general mail commands.
        if (has("borrar borrador", "borra borrador", "eliminar borrador", "delete draft") && id.isNotBlank())
            return request(GoogleWorkspaceActionType.DELETE_DRAFT, mapOf("draftId" to id))
        if (has("enviar borrador", "send draft") && id.isNotBlank())
            return request(GoogleWorkspaceActionType.SEND_DRAFT, mapOf("draftId" to id))
        if (has("borrar permanentemente", "borra permanentemente", "eliminar permanentemente", "elimina permanentemente", "borrar definitivamente", "borra definitivamente", "delete permanently") && has("correo", "email", "mensaje") && id.isNotBlank())
            return request(GoogleWorkspaceActionType.DELETE_EMAIL, mapOf("messageId" to id))
        if (has("mover a la papelera", "papelera", "trash") && has("correo", "email", "mensaje") && id.isNotBlank())
            return request(GoogleWorkspaceActionType.TRASH_EMAIL, mapOf("messageId" to id))
        if (has("eliminar etiqueta", "borrar etiqueta", "delete label") && id.isNotBlank())
            return request(GoogleWorkspaceActionType.DELETE_LABEL, mapOf("labelId" to id))
        if (has("cambiar etiqueta", "editar etiqueta", "renombrar etiqueta", "update label") && id.isNotBlank()) {
            val name = Regex("(?:nombre nuevo|llamarla|rename to)\\s*[:=]?\\s*(.+)$", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            return request(GoogleWorkspaceActionType.UPDATE_LABEL, mapOf("labelId" to id, "name" to name))
        }
        if (has("cambiar etiquetas", "modificar etiquetas", "agregar etiqueta", "quitar etiqueta", "modify labels") && id.isNotBlank()) {
            val add = Regex("(?:agregar|anadir|add)\\s+etiquetas?\\s*[:=]?\\s*([^;]+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val remove = Regex("(?:quitar|remover|remove)\\s+etiquetas?\\s*[:=]?\\s*([^;]+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            return request(GoogleWorkspaceActionType.MODIFY_EMAIL_LABELS, mapOf("messageId" to id, "addLabels" to add, "removeLabels" to remove))
        }
        if (has("crear borrador", "nuevo borrador", "redactar correo", "create draft"))
            return request(GoogleWorkspaceActionType.CREATE_DRAFT, mapOf("recipient" to recipient, "subject" to captured(subjectPattern), "body" to captured(bodyPattern)))
        if (has("enviar", "envia", "envie", "mandar", "manda", "mandame", "send") && has("correo", "email", "e-mail")) {
            return request(GoogleWorkspaceActionType.SEND_EMAIL, mapOf("recipient" to recipient, "subject" to captured(subjectPattern), "body" to captured(bodyPattern)))
        }
        if (has("crear etiqueta", "nueva etiqueta", "create label")) {
            val label = Regex("(?:etiqueta|label)\\s+(.+)$", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            return request(GoogleWorkspaceActionType.CREATE_LABEL, mapOf("name" to label))
        }
        if (has("listar etiquetas", "mostrar etiquetas", "ver etiquetas", "list labels"))
            return request(GoogleWorkspaceActionType.LIST_LABELS)
        if (has("listar borradores", "mostrar borradores", "ver borradores", "list drafts"))
            return request(GoogleWorkspaceActionType.LIST_DRAFTS)
        if (has("leer borrador", "ver borrador", "read draft") && id.isNotBlank())
            return request(GoogleWorkspaceActionType.READ_DRAFT, mapOf("draftId" to id))
        if (has("leer correo", "abrir correo", "read email", "ver mensaje") && id.isNotBlank())
            return request(GoogleWorkspaceActionType.READ_EMAIL, mapOf("messageId" to id))
        if (has("buscar correo", "buscar correos", "buscar email", "buscar emails", "search email", "search emails")) {
            val query = Regex("(?:buscar|search)\\s+(?:los\\s+)?(?:correos?|emails?)\\s*(.*)$", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            return request(GoogleWorkspaceActionType.SEARCH_EMAILS, mapOf("query" to query))
        }

        // Calendar.
        if (has("listar calendarios", "mostrar calendarios", "ver calendarios", "list calendars"))
            return request(GoogleWorkspaceActionType.LIST_CALENDARS)
        if (has("crear calendario", "nuevo calendario", "create calendar")) {
            val title = Regex("(?:calendario)\\s+(?:llamado\\s+|con nombre\\s+)?(.+)$", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            return request(GoogleWorkspaceActionType.CREATE_CALENDAR, mapOf("summary" to title, "timeZone" to "America/Costa_Rica"))
        }
        if (has("disponibilidad", "libre ocupado", "free busy", "free/busy"))
            return request(GoogleWorkspaceActionType.FREE_BUSY)
        if (has("eliminar evento", "borrar evento", "borra evento", "delete event") && id.isNotBlank())
            return request(GoogleWorkspaceActionType.DELETE_EVENT, mapOf("eventId" to id))
        if (has("editar evento", "actualizar evento", "update event") && id.isNotBlank())
            return request(GoogleWorkspaceActionType.UPDATE_EVENT, mapOf("eventId" to id))
        if (has("crear evento", "crea evento", "crea un evento", "agendar", "programar evento", "create event"))
            return request(GoogleWorkspaceActionType.CREATE_EVENT, mapOf("timeZone" to "America/Costa_Rica"))
        if (has("buscar eventos", "listar eventos", "mostrar eventos", "ver eventos", "search events", "list events"))
            return request(GoogleWorkspaceActionType.LIST_EVENTS, mapOf("calendarId" to "primary"))

        // Drive.
        if (has("borrar archivo permanentemente", "borra archivo permanentemente", "eliminar archivo permanentemente", "delete drive file permanently") && id.isNotBlank())
            return request(GoogleWorkspaceActionType.DELETE_DRIVE, mapOf("fileId" to id))
        if (has("actualizar contenido", "reemplazar contenido", "update file content") && id.isNotBlank())
            return request(GoogleWorkspaceActionType.UPDATE_DRIVE_CONTENT, mapOf("fileId" to id))
        if (has("renombrar archivo", "cambiar nombre archivo", "editar detalles archivo", "update file metadata") && id.isNotBlank()) {
            val name = Regex("(?:nombre nuevo|llamarlo|rename to)\\s*[:=]?\\s*(.+)$", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            return request(GoogleWorkspaceActionType.UPDATE_DRIVE_METADATA, mapOf("fileId" to id, "name" to name))
        }
        if (has("mover archivo a la papelera", "eliminar archivo", "mover archivo", "trash drive file") && has("drive", "archivo", "documento") && id.isNotBlank())
            return request(GoogleWorkspaceActionType.TRASH_DRIVE, mapOf("fileId" to id))
        if (has("subir archivo", "subir documento", "upload file"))
            return request(GoogleWorkspaceActionType.UPLOAD_DRIVE)
        if (has("crear carpeta", "nueva carpeta", "create folder") && has("drive", "carpeta")) {
            val name = Regex("(?:carpeta)\\s+(?:llamada\\s+|con nombre\\s+)?(.+)$", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            return request(GoogleWorkspaceActionType.CREATE_FOLDER, mapOf("folderName" to name))
        }
        if (has("buscar archivo", "buscar archivos", "buscar documento", "buscar documentos", "search drive", "search files"))
            return request(GoogleWorkspaceActionType.SEARCH_DRIVE)
        if (has("descargar archivo", "descargar documento", "download file") && id.isNotBlank())
            return request(GoogleWorkspaceActionType.DOWNLOAD_DRIVE, mapOf("fileId" to id))
        if (has("ver archivo", "detalles archivo", "file details") && id.isNotBlank())
            return request(GoogleWorkspaceActionType.DRIVE_METADATA, mapOf("fileId" to id))

        return null
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
}

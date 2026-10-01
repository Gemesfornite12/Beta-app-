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
    private val objectIdPattern = Regex("(?:leer|abrir|ver|borrar|borra|eliminar|elimina|editar|actualizar|descargar|renombrar|detalles|papelera|read|open|delete|update|download)\\s+(?:(?:permanentemente|definitivamente)\\s+)?(?:el\\s+|la\\s+)?(?:correo|mensaje|evento|archivo|documento|borrador|etiqueta|email|file|draft)\\s+(?:(?:permanentemente|definitivamente)\\s+)?([A-Z0-9_-]{5,})", RegexOption.IGNORE_CASE)
    private val subjectPattern = Regex("(?:asunto|subject)\\s*[:=]\\s*(.+?)(?=\\s+(?:mensaje|cuerpo|body)\\s*[:=]|$)", RegexOption.IGNORE_CASE)
    private val bodyPattern = Regex("(?:mensaje|cuerpo|body)\\s*[:=]\\s*(.+)$", RegexOption.IGNORE_CASE)

    fun parse(text: String): ParsedWorkspaceCommand? {
        val normalized = normalize(text)
        fun has(vararg words: String) = words.any { normalized.contains(it) }
        fun request(type: GoogleWorkspaceActionType, values: Map<String, String> = emptyMap()) =
            ParsedWorkspaceCommand(GoogleWorkspaceActionRequest(type, values), text)
        fun captured(pattern: Regex) = pattern.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val id = idPattern.find(text)?.groupValues?.getOrNull(1)
            ?: objectIdPattern.find(text)?.groupValues?.getOrNull(1).orEmpty()
        val recipient = emailPattern.find(text)?.value.orEmpty()

        // Gmail: destructive intents are matched before more general mail commands.
        if (has("borrar borrador", "borra borrador", "eliminar borrador", "delete draft"))
            return request(GoogleWorkspaceActionType.DELETE_DRAFT, mapOf("draftId" to id))
        if (has("enviar borrador", "send draft"))
            return request(GoogleWorkspaceActionType.SEND_DRAFT, mapOf("draftId" to id))
        if (has("permanentemente", "definitivamente", "permanently") && has("borrar", "borra", "eliminar", "elimina", "delete") && has("correo", "email", "mensaje"))
            return request(GoogleWorkspaceActionType.DELETE_EMAIL, mapOf("messageId" to id))
        if (((has("mover a la papelera", "papelera", "trash") && has("correo", "email", "mensaje")) || has("borrar correo", "borra correo", "eliminar correo", "elimina correo", "borrar mensaje", "delete email")) && id.isNotBlank())
            return request(GoogleWorkspaceActionType.TRASH_EMAIL, mapOf("messageId" to id))
        if (has("eliminar etiqueta", "elimina etiqueta", "borrar etiqueta", "borra etiqueta", "delete label"))
            return request(GoogleWorkspaceActionType.DELETE_LABEL, mapOf("labelId" to id))
        if (has("cambiar etiqueta", "editar etiqueta", "renombrar etiqueta", "update label") && !has("cambiar etiquetas", "modificar etiquetas")) {
            val name = Regex("(?:nombre nuevo|llamarla|\\ba\\b|rename to)\\s*[:=]?\\s*(.+)$", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            return request(GoogleWorkspaceActionType.UPDATE_LABEL, mapOf("labelId" to id, "name" to name))
        }
        if (has("cambiar etiquetas", "modificar etiquetas", "agregar etiqueta", "quitar etiqueta", "modify labels")) {
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
        if (has("listar etiquetas", "mostrar etiquetas", "ver etiquetas", "etiquetas", "list labels"))
            return request(GoogleWorkspaceActionType.LIST_LABELS)
        if (has("listar borradores", "mostrar borradores", "ver borradores", "borradores", "list drafts"))
            return request(GoogleWorkspaceActionType.LIST_DRAFTS)
        if (has("leer borrador", "ver borrador", "read draft"))
            return request(GoogleWorkspaceActionType.READ_DRAFT, mapOf("draftId" to id))
        if (has("leer correo", "abrir correo", "read email", "ver mensaje"))
            return request(GoogleWorkspaceActionType.READ_EMAIL, mapOf("messageId" to id))
        if (has("buscar correo", "buscar correos", "buscar email", "buscar emails", "search email", "search emails")) {
            val query = Regex("(?:buscar|search)\\s+(?:los\\s+)?(?:correos?|emails?)\\s*(.*)$", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            return request(GoogleWorkspaceActionType.SEARCH_EMAILS, mapOf("query" to query))
        }

        // Calendar.
        if (has("listar calendarios", "mostrar calendarios", "ver calendarios", "calendarios", "list calendars"))
            return request(GoogleWorkspaceActionType.LIST_CALENDARS)
        if (has("crear calendario", "nuevo calendario", "create calendar")) {
            val title = Regex("(?:calendario)\\s+(?:llamado\\s+|con nombre\\s+)?(.+)$", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            return request(GoogleWorkspaceActionType.CREATE_CALENDAR, mapOf("summary" to title, "timeZone" to "America/Costa_Rica"))
        }
        if (has("disponibilidad", "libre ocupado", "free busy", "free/busy"))
            return request(GoogleWorkspaceActionType.FREE_BUSY)
        if (has("eliminar evento", "elimina evento", "borrar evento", "borra evento", "delete event"))
            return request(GoogleWorkspaceActionType.DELETE_EVENT, mapOf("eventId" to id))
        if (has("editar evento", "actualizar evento", "update event"))
            return request(GoogleWorkspaceActionType.UPDATE_EVENT, mapOf("eventId" to id))
        if (has("crear evento", "crea evento", "crea un evento", "agendar", "programar evento", "create event"))
            return request(GoogleWorkspaceActionType.CREATE_EVENT, mapOf("timeZone" to "America/Costa_Rica"))
        if (has("buscar eventos", "listar eventos", "mostrar eventos", "ver eventos", "eventos", "search events", "list events"))
            return request(GoogleWorkspaceActionType.LIST_EVENTS, mapOf("calendarId" to "primary"))

        // Drive.
        if (has("permanentemente", "definitivamente", "permanently") && has("borrar", "borra", "eliminar", "elimina", "delete") && has("archivo", "documento", "drive", "file"))
            return request(GoogleWorkspaceActionType.DELETE_DRIVE, mapOf("fileId" to id))
        if (has("actualizar contenido", "reemplazar contenido", "update file content") || has("actualizar archivo", "reemplazar archivo"))
            return request(GoogleWorkspaceActionType.UPDATE_DRIVE_CONTENT, mapOf("fileId" to id))
        if (has("renombrar archivo", "cambiar nombre archivo", "editar detalles archivo", "update file metadata")) {
            val name = Regex("(?:nombre nuevo|llamarlo|\\ba\\b|rename to)\\s*[:=]?\\s*(.+)$", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            return request(GoogleWorkspaceActionType.UPDATE_DRIVE_METADATA, mapOf("fileId" to id, "name" to name))
        }
        if ((has("mover archivo a la papelera", "papelera archivo", "eliminar archivo", "elimina archivo", "borrar archivo", "borra archivo", "mover archivo", "trash drive file") && has("drive", "archivo", "documento", "file")))
            return request(GoogleWorkspaceActionType.TRASH_DRIVE, mapOf("fileId" to id))
        if (has("subir archivo", "subir documento", "upload file"))
            return request(GoogleWorkspaceActionType.UPLOAD_DRIVE)
        if (has("crear carpeta", "nueva carpeta", "create folder") && has("drive", "carpeta")) {
            val name = Regex("(?:carpeta)\\s+(?:llamada\\s+|con nombre\\s+)?(.+)$", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            return request(GoogleWorkspaceActionType.CREATE_FOLDER, mapOf("folderName" to name))
        }
        if (has("buscar archivo", "buscar archivos", "buscar documento", "buscar documentos", "search drive", "search files")) {
            val query = Regex("(?:buscar|search)\\s+(?:los\\s+)?(?:archivos?|documentos?)\\s*(.*)$", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            return request(GoogleWorkspaceActionType.SEARCH_DRIVE, mapOf("query" to query))
        }
        if (has("descargar archivo", "descargar documento", "download file"))
            return request(GoogleWorkspaceActionType.DOWNLOAD_DRIVE, mapOf("fileId" to id))
        if (has("ver archivo", "detalles archivo", "detalles del archivo", "info archivo", "file details"))
            return request(GoogleWorkspaceActionType.DRIVE_METADATA, mapOf("fileId" to id))

        return null
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
}

package com.example.ui.screens.ai

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.data.api.GoogleWorkspaceActionsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

internal suspend fun executeGoogleWorkspaceAction(
    context: Context,
    request: GoogleWorkspaceActionRequest,
    accessToken: String
): String = withContext(Dispatchers.IO) {
    val api = GoogleWorkspaceActionsApi()
    val v = request.values
    fun required(key: String) = v[key]?.takeIf(String::isNotBlank) ?: error("Falta el dato: $key")
    fun optional(key: String) = v[key]?.takeIf(String::isNotBlank)
    fun commaList(key: String) = optional(key)?.split(',')?.map(String::trim)?.filter(String::isNotBlank).orEmpty()
    fun result(body: String, code: Int): String = body.takeIf(String::isNotBlank) ?: "Acción completada (HTTP $code)."

    when (request.type) {
        GoogleWorkspaceActionType.SEARCH_EMAILS -> api.listEmailMessages(accessToken, optional("query")).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.READ_EMAIL -> api.getEmailMessage(accessToken, required("messageId")).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.LIST_LABELS -> api.listGmailLabels(accessToken).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.CREATE_LABEL -> api.createGmailLabel(accessToken, required("name"), true).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.UPDATE_LABEL -> api.updateGmailLabel(accessToken, required("labelId"), name = optional("name"), confirmed = true).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.DELETE_LABEL -> api.deleteGmailLabel(accessToken, required("labelId"), true).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.MODIFY_EMAIL_LABELS -> api.modifyEmailLabels(accessToken, required("messageId"), commaList("addLabels"), commaList("removeLabels"), true).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.LIST_DRAFTS -> api.listEmailDrafts(accessToken).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.READ_DRAFT -> api.getEmailDraft(accessToken, required("draftId")).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.CREATE_DRAFT -> api.createEmailDraft(accessToken, required("recipient"), required("subject"), required("body"), true).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.SEND_DRAFT -> api.sendEmailDraft(accessToken, required("draftId"), true).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.DELETE_DRAFT -> api.deleteEmailDraftPermanently(accessToken, required("draftId"), true).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.SEND_EMAIL -> api.sendEmail(accessToken, required("recipient"), required("subject"), required("body"), true).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.TRASH_EMAIL -> api.trashEmail(accessToken, required("messageId"), true).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.DELETE_EMAIL -> api.deleteEmailPermanently(accessToken, required("messageId"), true).let { result(it.body, it.statusCode) }

        GoogleWorkspaceActionType.LIST_CALENDARS -> api.listCalendars(accessToken).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.CREATE_CALENDAR -> api.createSecondaryCalendar(accessToken, required("summary"), required("timeZone"), true).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.LIST_EVENTS -> api.listCalendarEvents(accessToken, optional("calendarId") ?: "primary", optional("timeMin"), optional("timeMax"), optional("query")).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.FREE_BUSY -> api.queryCalendarAvailability(accessToken, required("timeMin"), required("timeMax"), required("timeZone"), commaList("calendarIds").ifEmpty { listOf("primary") }).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.CREATE_EVENT -> api.createCalendarEvent(accessToken, required("summary"), required("start"), required("end"), required("timeZone"), optional("description"), true, optional("calendarId") ?: "primary").let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.UPDATE_EVENT -> api.updateCalendarEvent(accessToken, required("eventId"), optional("summary"), optional("start"), optional("end"), optional("timeZone"), optional("description"), true, optional("calendarId") ?: "primary").let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.DELETE_EVENT -> api.deleteCalendarEvent(accessToken, required("eventId"), true, optional("calendarId") ?: "primary").let { result(it.body, it.statusCode) }

        GoogleWorkspaceActionType.SEARCH_DRIVE -> api.listDriveFiles(accessToken, optional("query")).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.DRIVE_METADATA -> api.getDriveFileMetadata(accessToken, required("fileId")).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.DOWNLOAD_DRIVE -> {
            val fileId = required("fileId")
            val metadataResult = api.getDriveFileMetadata(accessToken, fileId)
            val metadata = JSONObject(metadataResult.body)
            val name = metadata.optString("name").ifBlank { "drive-$fileId" }
            val mime = metadata.optString("mimeType").ifBlank { "application/octet-stream" }
            val downloaded = api.downloadDriveFile(accessToken, fileId)
            val saved = saveToDownloads(context, name, mime, downloaded.bytes)
            "Descargado ${downloaded.bytes.size} bytes: $saved"
        }
        GoogleWorkspaceActionType.CREATE_FOLDER -> api.createDriveFolder(accessToken, required("folderName"), true).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.UPLOAD_DRIVE -> {
            val uri = request.fileUri ?: error("Elige un archivo para subir.")
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("No se pudo leer el archivo elegido.")
            val name = displayName(context, uri)
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            api.uploadDriveFile(accessToken, name, mime, bytes, true).let { result(it.body, it.statusCode) }
        }
        GoogleWorkspaceActionType.UPDATE_DRIVE_METADATA -> api.updateDriveFileMetadata(accessToken, required("fileId"), optional("name"), optional("description"), true).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.UPDATE_DRIVE_CONTENT -> {
            val uri = request.fileUri ?: error("Elige el archivo nuevo.")
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("No se pudo leer el archivo elegido.")
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            api.updateDriveFileContent(accessToken, required("fileId"), mime, bytes, true, displayName(context, uri)).let { result(it.body, it.statusCode) }
        }
        GoogleWorkspaceActionType.TRASH_DRIVE -> api.trashDriveFile(accessToken, required("fileId"), true).let { result(it.body, it.statusCode) }
        GoogleWorkspaceActionType.DELETE_DRIVE -> api.deleteDriveFilePermanently(accessToken, required("fileId"), true).let { result(it.body, it.statusCode) }
    }.take(20000)
}

private fun displayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index).orEmpty().ifBlank { "upload" }
        }
    }
    val rawName = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "upload" } ?: "upload"
    return rawName.replace('/', '_').replace('\\', '_').take(180).ifBlank { "upload" }
}

private fun saveToDownloads(context: Context, name: String, mimeType: String, bytes: ByteArray): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("No se pudo crear el archivo en Descargas.")
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: error("No se pudo guardar el archivo descargado.")
        return "Descargas/$name"
    }
    val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "Downloads")
    if (!directory.exists() && !directory.mkdirs()) error("No se pudo crear la carpeta de descargas.")
    File(directory, name).writeBytes(bytes)
    return directory.resolve(name).absolutePath
}

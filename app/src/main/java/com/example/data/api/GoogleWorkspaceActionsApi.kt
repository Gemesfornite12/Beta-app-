package com.example.data.api

import android.util.Base64
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Direct Google Workspace REST actions for the isolated test build.
 * Call only after showing the full action preview and receiving an explicit user confirmation.
 * OAuth scopes are deliberately not requested here; the existing production review stays read-only.
 */
class GoogleWorkspaceActionsApi(
    private val client: OkHttpClient = OkHttpClient(),
    gmailBaseUrl: String = "https://gmail.googleapis.com/",
    calendarBaseUrl: String = "https://www.googleapis.com/",
    driveBaseUrl: String = "https://www.googleapis.com/"
) {
    private val gmailBase = gmailBaseUrl.toHttpUrl()
    private val calendarBase = calendarBaseUrl.toHttpUrl()
    private val driveBase = driveBaseUrl.toHttpUrl()

    /** Send one plain-text email. A visible confirmation is required before this call. */
    @Throws(IOException::class)
    fun sendEmail(
        accessToken: String,
        recipient: String,
        subject: String,
        body: String,
        confirmed: Boolean
    ): GoogleWorkspaceApiResult {
        requireConfirmed(confirmed)
        requireSafeHeader(recipient, "recipient")
        requireSafeHeader(subject, "subject")
        requireToken(accessToken)
        require(recipient.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) { "Invalid recipient address." }

        val raw = buildRawEmail(recipient, subject, body)
        val json = JSONObject().put("raw", raw).toString()
        val url = url(gmailBase, "gmail", "v1", "users", "me", "messages", "send")
        return execute("POST", url, accessToken, json.toRequestBody(JSON))
    }

    /** Move a Gmail message to Trash; this is reversible in Gmail. */
    @Throws(IOException::class)
    fun trashEmail(accessToken: String, messageId: String, confirmed: Boolean): GoogleWorkspaceApiResult {
        requireConfirmed(confirmed)
        requireToken(accessToken)
        requireId(messageId, "messageId")
        val url = url(gmailBase, "gmail", "v1", "users", "me", "messages", messageId, "trash")
        return execute("POST", url, accessToken, EMPTY_BODY)
    }

    /** Search/list message IDs and thread IDs; callers should render results only after a user asks. */
    @Throws(IOException::class)
    fun listEmailMessages(accessToken: String, query: String? = null, maxResults: Int = 20): GoogleWorkspaceApiResult {
        requireToken(accessToken)
        require(maxResults in 1..100) { "maxResults must be between 1 and 100." }
        val builder = url(gmailBase, "gmail", "v1", "users", "me", "messages")
            .newBuilder()
            .addQueryParameter("maxResults", maxResults.toString())
        query?.takeIf(String::isNotBlank)?.let { builder.addQueryParameter("q", it) }
        return execute("GET", builder.build(), accessToken, null)
    }

    /** Read one message payload, including headers and body parts. */
    @Throws(IOException::class)
    fun getEmailMessage(accessToken: String, messageId: String): GoogleWorkspaceApiResult {
        requireToken(accessToken)
        requireId(messageId, "messageId")
        val url = url(gmailBase, "gmail", "v1", "users", "me", "messages", messageId)
            .newBuilder().addQueryParameter("format", "full").build()
        return execute("GET", url, accessToken, null)
    }

    /** List Gmail labels for the signed-in user. */
    @Throws(IOException::class)
    fun listGmailLabels(accessToken: String): GoogleWorkspaceApiResult {
        requireToken(accessToken)
        return execute("GET", url(gmailBase, "gmail", "v1", "users", "me", "labels"), accessToken, null)
    }

    /** Create a user label. The UI must show the label name and request confirmation. */
    @Throws(IOException::class)
    fun createGmailLabel(accessToken: String, name: String, confirmed: Boolean): GoogleWorkspaceApiResult {
        requireConfirmed(confirmed)
        requireToken(accessToken)
        require(name.isNotBlank() && name.length <= 225) { "Label name must contain 1 to 225 characters." }
        val body = JSONObject().put("name", name).toString().toRequestBody(JSON)
        return execute("POST", url(gmailBase, "gmail", "v1", "users", "me", "labels"), accessToken, body)
    }

    /** Rename a Gmail label or update its visibility. */
    @Throws(IOException::class)
    fun updateGmailLabel(
        accessToken: String,
        labelId: String,
        name: String? = null,
        labelListVisibility: String? = null,
        messageListVisibility: String? = null,
        confirmed: Boolean
    ): GoogleWorkspaceApiResult {
        requireConfirmed(confirmed)
        requireToken(accessToken)
        requireId(labelId, "labelId")
        require(name != null || labelListVisibility != null || messageListVisibility != null) { "Provide a label property to update." }
        val body = JSONObject()
        if (name != null) body.put("name", name)
        if (labelListVisibility != null) body.put("labelListVisibility", labelListVisibility)
        if (messageListVisibility != null) body.put("messageListVisibility", messageListVisibility)
        val url = url(gmailBase, "gmail", "v1", "users", "me", "labels", labelId)
        return execute("PATCH", url, accessToken, body.toString().toRequestBody(JSON))
    }

    /** Delete a user-created label; messages remain in the mailbox. */
    @Throws(IOException::class)
    fun deleteGmailLabel(accessToken: String, labelId: String, confirmed: Boolean): GoogleWorkspaceApiResult {
        requireConfirmed(confirmed)
        requireToken(accessToken)
        requireId(labelId, "labelId")
        return execute("DELETE", url(gmailBase, "gmail", "v1", "users", "me", "labels", labelId), accessToken, null)
    }

    /** Add and/or remove user label IDs on a message. */
    @Throws(IOException::class)
    fun modifyEmailLabels(
        accessToken: String,
        messageId: String,
        addLabelIds: List<String> = emptyList(),
        removeLabelIds: List<String> = emptyList(),
        confirmed: Boolean
    ): GoogleWorkspaceApiResult {
        requireConfirmed(confirmed)
        requireToken(accessToken)
        requireId(messageId, "messageId")
        require(addLabelIds.isNotEmpty() || removeLabelIds.isNotEmpty()) { "At least one label change is required." }
        val body = JSONObject()
            .put("addLabelIds", org.json.JSONArray(addLabelIds))
            .put("removeLabelIds", org.json.JSONArray(removeLabelIds))
            .toString().toRequestBody(JSON)
        val url = url(gmailBase, "gmail", "v1", "users", "me", "messages", messageId, "modify")
        return execute("POST", url, accessToken, body)
    }

    /** Create a draft after its recipient, subject and body have been previewed. */
    @Throws(IOException::class)
    fun createEmailDraft(
        accessToken: String,
        recipient: String,
        subject: String,
        body: String,
        confirmed: Boolean
    ): GoogleWorkspaceApiResult {
        requireConfirmed(confirmed)
        requireSafeHeader(recipient, "recipient")
        requireSafeHeader(subject, "subject")
        requireToken(accessToken)
        require(recipient.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) { "Invalid recipient address." }
        val raw = buildRawEmail(recipient, subject, body)
        val payload = JSONObject().put("message", JSONObject().put("raw", raw)).toString().toRequestBody(JSON)
        return execute("POST", url(gmailBase, "gmail", "v1", "users", "me", "drafts"), accessToken, payload)
    }

    /** List draft IDs and metadata. */
    @Throws(IOException::class)
    fun listEmailDrafts(accessToken: String, maxResults: Int = 20): GoogleWorkspaceApiResult {
        requireToken(accessToken)
        require(maxResults in 1..100) { "maxResults must be between 1 and 100." }
        val url = url(gmailBase, "gmail", "v1", "users", "me", "drafts")
            .newBuilder().addQueryParameter("maxResults", maxResults.toString()).build()
        return execute("GET", url, accessToken, null)
    }

    /** Read one draft and its message content for an explicit user request. */
    @Throws(IOException::class)
    fun getEmailDraft(accessToken: String, draftId: String): GoogleWorkspaceApiResult {
        requireToken(accessToken)
        requireId(draftId, "draftId")
        return execute("GET", url(gmailBase, "gmail", "v1", "users", "me", "drafts", draftId), accessToken, null)
    }

    /** Send a previously previewed draft. This sends an email to its recipients. */
    @Throws(IOException::class)
    fun sendEmailDraft(accessToken: String, draftId: String, confirmed: Boolean): GoogleWorkspaceApiResult {
        requireConfirmed(confirmed)
        requireToken(accessToken)
        requireId(draftId, "draftId")
        val body = JSONObject().put("id", draftId).toString().toRequestBody(JSON)
        return execute("POST", url(gmailBase, "gmail", "v1", "users", "me", "drafts", "send"), accessToken, body)
    }

    /** Permanently delete a draft after explicit confirmation. */
    @Throws(IOException::class)
    fun deleteEmailDraftPermanently(accessToken: String, draftId: String, confirmed: Boolean): GoogleWorkspaceApiResult {
        requireConfirmed(confirmed)
        requireToken(accessToken)
        requireId(draftId, "draftId")
        return execute("DELETE", url(gmailBase, "gmail", "v1", "users", "me", "drafts", draftId), accessToken, null)
    }

    /** Permanently delete one Gmail message. The UI must label this irreversible action and request confirmation. */
    @Throws(IOException::class)
    fun deleteEmailPermanently(accessToken: String, messageId: String, confirmed: Boolean): GoogleWorkspaceApiResult {
        requireConfirmed(confirmed)
        requireToken(accessToken)
        requireId(messageId, "messageId")
        val url = url(gmailBase, "gmail", "v1", "users", "me", "messages", messageId)
        return execute("DELETE", url, accessToken, null)
    }

    /** List calendars the signed-in user can see. */
    @Throws(IOException::class)
    fun listCalendars(accessToken: String): GoogleWorkspaceApiResult {
        requireToken(accessToken)
        return execute("GET", url(calendarBase, "calendar", "v3", "users", "me", "calendarList"), accessToken, null)
    }

    /** Create a secondary calendar with a chosen name and timezone. */
    @Throws(IOException::class)
    fun createSecondaryCalendar(
        accessToken: String,
        summary: String,
        timeZone: String,
        confirmed: Boolean
    ): GoogleWorkspaceApiResult {
        requireConfirmed(confirmed)
        requireToken(accessToken)
        require(summary.isNotBlank() && timeZone.isNotBlank()) { "Calendar name and time zone are required." }
        val body = JSONObject().put("summary", summary).put("timeZone", timeZone).toString().toRequestBody(JSON)
        return execute("POST", url(calendarBase, "calendar", "v3", "calendars"), accessToken, body)
    }

    /** Search events within a calendar and optional time range. */
    @Throws(IOException::class)
    fun listCalendarEvents(
        accessToken: String,
        calendarId: String = "primary",
        timeMin: String? = null,
        timeMax: String? = null,
        query: String? = null,
        maxResults: Int = 50
    ): GoogleWorkspaceApiResult {
        requireToken(accessToken)
        requireId(calendarId, "calendarId")
        require(maxResults in 1..2500) { "maxResults must be between 1 and 2500." }
        val builder = url(calendarBase, "calendar", "v3", "calendars", calendarId, "events")
            .newBuilder()
            .addQueryParameter("maxResults", maxResults.toString())
            .addQueryParameter("singleEvents", "true")
            .addQueryParameter("orderBy", "startTime")
        timeMin?.takeIf(String::isNotBlank)?.let { builder.addQueryParameter("timeMin", it) }
        timeMax?.takeIf(String::isNotBlank)?.let { builder.addQueryParameter("timeMax", it) }
        query?.takeIf(String::isNotBlank)?.let { builder.addQueryParameter("q", it) }
        return execute("GET", builder.build(), accessToken, null)
    }

    /** Query free/busy availability for selected calendars and time range. */
    @Throws(IOException::class)
    fun queryCalendarAvailability(
        accessToken: String,
        timeMin: String,
        timeMax: String,
        timeZone: String,
        calendarIds: List<String> = listOf("primary")
    ): GoogleWorkspaceApiResult {
        requireToken(accessToken)
        require(timeMin.isNotBlank() && timeMax.isNotBlank() && timeZone.isNotBlank()) { "Time range and zone are required." }
        require(calendarIds.isNotEmpty() && calendarIds.all { it.isNotBlank() }) { "At least one calendar is required." }
        val items = org.json.JSONArray().apply { calendarIds.forEach { put(JSONObject().put("id", it)) } }
        val body = JSONObject().put("timeMin", timeMin).put("timeMax", timeMax)
            .put("timeZone", timeZone).put("items", items).toString().toRequestBody(JSON)
        return execute("POST", url(calendarBase, "calendar", "v3", "freeBusy"), accessToken, body)
    }

    /** Create a timed event on a selected calendar. */
    @Throws(IOException::class)
    fun createCalendarEvent(
        accessToken: String,
        summary: String,
        startDateTime: String,
        endDateTime: String,
        timeZone: String,
        description: String? = null,
        confirmed: Boolean,
        calendarId: String = "primary"
    ): GoogleWorkspaceApiResult {
        requireConfirmed(confirmed)
        requireToken(accessToken)
        require(summary.isNotBlank()) { "Event title is required." }
        require(startDateTime.isNotBlank() && endDateTime.isNotBlank()) { "Event start and end are required." }
        require(timeZone.isNotBlank()) { "Event time zone is required." }
        requireId(calendarId, "calendarId")

        val start = JSONObject().put("dateTime", startDateTime).put("timeZone", timeZone)
        val end = JSONObject().put("dateTime", endDateTime).put("timeZone", timeZone)
        val event = JSONObject()
            .put("summary", summary)
            .put("start", start)
            .put("end", end)
        if (!description.isNullOrBlank()) event.put("description", description)
        val url = url(calendarBase, "calendar", "v3", "calendars", calendarId, "events")
        return execute("POST", url, accessToken, event.toString().toRequestBody(JSON))
    }

    /** Patch an event after showing its current and proposed values. */
    @Throws(IOException::class)
    fun updateCalendarEvent(
        accessToken: String,
        eventId: String,
        summary: String? = null,
        startDateTime: String? = null,
        endDateTime: String? = null,
        timeZone: String? = null,
        description: String? = null,
        confirmed: Boolean,
        calendarId: String = "primary"
    ): GoogleWorkspaceApiResult {
        requireConfirmed(confirmed)
        requireToken(accessToken)
        requireId(eventId, "eventId")
        requireId(calendarId, "calendarId")
        require(summary != null || startDateTime != null || description != null) { "At least one event field must change." }
        require((startDateTime == null) == (endDateTime == null)) { "Provide both start and end times." }
        val event = JSONObject()
        if (summary != null) event.put("summary", summary)
        if (description != null) event.put("description", description)
        if (startDateTime != null && endDateTime != null) {
            require(!timeZone.isNullOrBlank()) { "Time zone is required when changing event times." }
            event.put("start", JSONObject().put("dateTime", startDateTime).put("timeZone", timeZone))
            event.put("end", JSONObject().put("dateTime", endDateTime).put("timeZone", timeZone))
        }
        val url = url(calendarBase, "calendar", "v3", "calendars", calendarId, "events", eventId)
        return execute("PATCH", url, accessToken, event.toString().toRequestBody(JSON))
    }

    /** Permanently delete a calendar event after explicit confirmation. */
    @Throws(IOException::class)
    fun deleteCalendarEvent(
        accessToken: String,
        eventId: String,
        confirmed: Boolean,
        calendarId: String = "primary"
    ): GoogleWorkspaceApiResult {
        requireConfirmed(confirmed)
        requireToken(accessToken)
        requireId(eventId, "eventId")
        requireId(calendarId, "calendarId")
        val url = url(calendarBase, "calendar", "v3", "calendars", calendarId, "events", eventId)
        return execute("DELETE", url, accessToken, null)
    }

    /** Search/list Drive file metadata visible to this OAuth grant. */
    @Throws(IOException::class)
    fun listDriveFiles(
        accessToken: String,
        query: String? = null,
        pageSize: Int = 20,
        pageToken: String? = null
    ): GoogleWorkspaceApiResult {
        requireToken(accessToken)
        require(pageSize in 1..1000) { "pageSize must be between 1 and 1000." }
        val builder = url(driveBase, "drive", "v3", "files").newBuilder()
            .addQueryParameter("pageSize", pageSize.toString())
            .addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,modifiedTime,size,webViewLink,trashed)")
            .addQueryParameter("orderBy", "modifiedTime desc")
        query?.takeIf(String::isNotBlank)?.let { builder.addQueryParameter("q", it) }
        pageToken?.takeIf(String::isNotBlank)?.let { builder.addQueryParameter("pageToken", it) }
        return execute("GET", builder.build(), accessToken, null)
    }

    /** Read metadata for a Drive file the app is allowed to access. */
    @Throws(IOException::class)
    fun getDriveFileMetadata(accessToken: String, fileId: String): GoogleWorkspaceApiResult {
        requireToken(accessToken)
        requireId(fileId, "fileId")
        val url = url(driveBase, "drive", "v3", "files", fileId).newBuilder()
            .addQueryParameter("fields", "id,name,mimeType,modifiedTime,size,webViewLink,trashed")
            .build()
        return execute("GET", url, accessToken, null)
    }

    /** Download bytes for a Drive file the app is allowed to access. */
    @Throws(IOException::class)
    fun downloadDriveFile(accessToken: String, fileId: String): GoogleWorkspaceBinaryResult {
        requireToken(accessToken)
        requireId(fileId, "fileId")
        val url = url(driveBase, "drive", "v3", "files", fileId).newBuilder()
            .addQueryParameter("alt", "media").build()
        return executeBinary(url, accessToken)
    }

    /** Create a Drive folder. */
    @Throws(IOException::class)
    fun createDriveFolder(accessToken: String, folderName: String, confirmed: Boolean): GoogleWorkspaceApiResult {
        requireConfirmed(confirmed)
        requireToken(accessToken)
        require(folderName.isNotBlank()) { "Folder name is required." }
        val body = JSONObject().put("name", folderName)
            .put("mimeType", "application/vnd.google-apps.folder").toString().toRequestBody(JSON)
        return execute("POST", url(driveBase, "drive", "v3", "files"), accessToken, body)
    }

    /** Rename or edit the description of a Drive file. */
    @Throws(IOException::class)
    fun updateDriveFileMetadata(
        accessToken: String,
        fileId: String,
        name: String? = null,
        description: String? = null,
        confirmed: Boolean
    ): GoogleWorkspaceApiResult {
        requireConfirmed(confirmed)
        requireToken(accessToken)
        requireId(fileId, "fileId")
        require(name != null || description != null) { "Provide a name or description to update." }
        val metadata = JSONObject()
        if (name != null) metadata.put("name", name)
        if (description != null) metadata.put("description", description)
        val url = url(driveBase, "drive", "v3", "files", fileId).newBuilder()
            .addQueryParameter("fields", "id,name,description,modifiedTime,webViewLink").build()
        return execute("PATCH", url, accessToken, metadata.toString().toRequestBody(JSON))
    }

    /** Upload a file to Drive using multipart/related. Limited to 10 MiB per request. */
    @Throws(IOException::class)
    fun uploadDriveFile(
        accessToken: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        confirmed: Boolean
    ): GoogleWorkspaceApiResult {
        requireConfirmed(confirmed)
        requireToken(accessToken)
        require(fileName.isNotBlank()) { "File name is required." }
        require(mimeType.matches(Regex("^[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+$"))) { "Invalid MIME type." }
        require(bytes.size <= MAX_UPLOAD_BYTES) { "Files must be 10 MiB or smaller for this test action." }

        val metadata = JSONObject()
            .put("name", fileName)
            .put("mimeType", mimeType)
        val boundary = "OmniStudio-${UUID.randomUUID()}"
        val multipart = ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
            write(metadata.toString().toByteArray(StandardCharsets.UTF_8))
            write("\r\n--$boundary\r\nContent-Type: $mimeType\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
            write(bytes)
            write("\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
        }.toByteArray().toRequestBody("multipart/related; boundary=$boundary".toMediaType())
        val url = url(driveBase, "upload", "drive", "v3", "files")
            .newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", "id,name,mimeType,webViewLink")
            .build()
        return execute("POST", url, accessToken, multipart)
    }

    /** Replace the contents of an app-accessible Drive file using a multipart upload. */
    @Throws(IOException::class)
    fun updateDriveFileContent(
        accessToken: String,
        fileId: String,
        mimeType: String,
        bytes: ByteArray,
        confirmed: Boolean,
        newName: String? = null
    ): GoogleWorkspaceApiResult {
        requireConfirmed(confirmed)
        requireToken(accessToken)
        requireId(fileId, "fileId")
        require(mimeType.matches(Regex("^[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+$"))) { "Invalid MIME type." }
        require(bytes.size <= MAX_UPLOAD_BYTES) { "Files must be 10 MiB or smaller for this test action." }
        val metadata = JSONObject()
        if (!newName.isNullOrBlank()) metadata.put("name", newName)
        val boundary = "OmniStudio-${UUID.randomUUID()}"
        val multipart = ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
            write(metadata.toString().toByteArray(StandardCharsets.UTF_8))
            write("\r\n--$boundary\r\nContent-Type: $mimeType\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
            write(bytes)
            write("\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
        }.toByteArray().toRequestBody("multipart/related; boundary=$boundary".toMediaType())
        val url = url(driveBase, "upload", "drive", "v3", "files", fileId).newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", "id,name,mimeType,modifiedTime,webViewLink")
            .build()
        return execute("PATCH", url, accessToken, multipart)
    }

    /** Move a Drive file to Trash; this is reversible and limited by the granted Drive scope. */
    @Throws(IOException::class)
    fun trashDriveFile(accessToken: String, fileId: String, confirmed: Boolean): GoogleWorkspaceApiResult {
        requireConfirmed(confirmed)
        requireToken(accessToken)
        requireId(fileId, "fileId")
        val url = url(driveBase, "drive", "v3", "files", fileId)
            .newBuilder()
            .addQueryParameter("fields", "id,trashed")
            .build()
        val body = JSONObject().put("trashed", true).toString().toRequestBody(JSON)
        return execute("PATCH", url, accessToken, body)
    }

    /** Permanently delete a Drive file the app is allowed to access via drive.file. */
    @Throws(IOException::class)
    fun deleteDriveFilePermanently(accessToken: String, fileId: String, confirmed: Boolean): GoogleWorkspaceApiResult {
        requireConfirmed(confirmed)
        requireToken(accessToken)
        requireId(fileId, "fileId")
        val url = url(driveBase, "drive", "v3", "files", fileId)
        return execute("DELETE", url, accessToken, null)
    }

    private fun buildRawEmail(recipient: String, subject: String, body: String): String {
        val encodedSubject = Base64.encodeToString(subject.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val encodedBody = Base64.encodeToString(body.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val mime = buildString {
            append("To: ").append(recipient).append("\r\n")
            append("Subject: =?UTF-8?B?").append(encodedSubject).append("?=\r\n")
            append("MIME-Version: 1.0\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n")
            append("Content-Transfer-Encoding: base64\r\n\r\n")
            append(encodedBody)
        }
        return Base64.encodeToString(
            mime.toByteArray(StandardCharsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    private fun executeBinary(url: HttpUrl, accessToken: String): GoogleWorkspaceBinaryResult {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val contentType = response.body?.contentType()?.toString()
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (!response.isSuccessful) {
                throw GoogleWorkspaceApiException(response.code, String(bytes, StandardCharsets.UTF_8).take(MAX_ERROR_CHARS))
            }
            return GoogleWorkspaceBinaryResult(response.code, contentType, bytes)
        }
    }

    private fun execute(
        method: String,
        url: HttpUrl,
        accessToken: String,
        body: RequestBody?
    ): GoogleWorkspaceApiResult {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .method(method, body)
            .build()
        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GoogleWorkspaceApiException(response.code, responseText.take(MAX_ERROR_CHARS))
            }
            return GoogleWorkspaceApiResult(response.code, responseText)
        }
    }

    private fun url(base: HttpUrl, vararg segments: String): HttpUrl {
        val builder = base.newBuilder()
        segments.forEach { segment -> builder.addPathSegment(segment) }
        return builder.build()
    }

    private fun requireConfirmed(confirmed: Boolean) {
        check(confirmed) { "Explicit user confirmation is required before a Google Workspace write action." }
    }

    private fun requireToken(token: String) {
        require(token.isNotBlank() && !token.contains('\n') && !token.contains('\r')) { "A valid Google access token is required." }
    }

    private fun requireId(id: String, name: String) {
        require(id.isNotBlank() && !id.contains('\n') && !id.contains('\r')) { "$name is required." }
    }

    private fun requireSafeHeader(value: String, name: String) {
        require(!value.contains('\n') && !value.contains('\r')) { "$name must not contain line breaks." }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)
        private const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024
        private const val MAX_ERROR_CHARS = 1200
    }
}

data class GoogleWorkspaceApiResult(val statusCode: Int, val body: String)

data class GoogleWorkspaceBinaryResult(val statusCode: Int, val contentType: String?, val bytes: ByteArray)

class GoogleWorkspaceApiException(val statusCode: Int, val responseBody: String) :
    IOException("Google Workspace API returned HTTP $statusCode")

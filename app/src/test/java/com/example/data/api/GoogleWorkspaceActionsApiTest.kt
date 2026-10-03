package com.example.data.api

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoogleWorkspaceActionsApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: GoogleWorkspaceActionsApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val base = server.url("/").toString()
        api = GoogleWorkspaceActionsApi(
            client = OkHttpClient(),
            gmailBaseUrl = base,
            calendarBaseUrl = base,
            driveBaseUrl = base
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun unconfirmedEmailSendMakesNoRequest() {
        assertThrows(IllegalStateException::class.java) {
            api.sendEmail("test-token", "person@example.com", "Subject", "Body", confirmed = false)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun sendEmailPostsBase64UrlMimeWithBearerToken() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"id\":\"sent-id\"}"))

        val result = api.sendEmail("test-token", "person@example.com", "Hello", "Message body", confirmed = true)

        assertEquals(200, result.statusCode)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/gmail/v1/users/me/messages/send", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        val raw = JSONObject(request.body.readUtf8()).getString("raw")
        val mime = String(Base64.decode(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
        assertTrue(mime.contains("To: person@example.com"))
        assertTrue(mime.contains(Base64.encodeToString("Hello".toByteArray(), Base64.NO_WRAP)))
        val encodedText = mime.substringAfter("\r\n\r\n")
        assertEquals("Message body", String(Base64.decode(encodedText, Base64.NO_WRAP), Charsets.UTF_8))
    }

    @Test
    fun gmailReadSearchAndLabelEndpointsUseExpectedRoutes() {
        repeat(7) { server.enqueue(MockResponse().setResponseCode(200).setBody("{}")) }

        api.listEmailMessages("test-token", query = "is:unread", maxResults = 10)
        val search = server.takeRequest()
        assertEquals("GET", search.method)
        assertTrue(search.path!!.startsWith("/gmail/v1/users/me/messages?"))
        assertTrue(search.path!!.contains("q=is%3Aunread"))

        api.getEmailMessage("test-token", "message-1")
        assertTrue(server.takeRequest().path!!.contains("/messages/message-1?format=full"))

        api.listGmailLabels("test-token")
        assertEquals("/gmail/v1/users/me/labels", server.takeRequest().path)

        api.createGmailLabel("test-token", "OmniStudio", confirmed = true)
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("OmniStudio", JSONObject(create.body.readUtf8()).getString("name"))

        api.updateGmailLabel("test-token", "Label_1", name = "Updated", confirmed = true)
        assertEquals("PATCH", server.takeRequest().method)

        api.deleteGmailLabel("test-token", "Label_1", confirmed = true)
        assertEquals("DELETE", server.takeRequest().method)

        api.modifyEmailLabels("test-token", "message-1", addLabelIds = listOf("Label_1"), confirmed = true)
        val modify = server.takeRequest()
        assertEquals("POST", modify.method)
        assertTrue(modify.path!!.endsWith("/messages/message-1/modify"))
    }

    @Test
    fun draftLifecycleCreatesListsReadsSendsAndDeletes() {
        repeat(5) { server.enqueue(MockResponse().setResponseCode(200).setBody("{}")) }

        api.createEmailDraft("test-token", "person@example.com", "Draft", "Body", confirmed = true)
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/gmail/v1/users/me/drafts", create.path)
        assertTrue(JSONObject(create.body.readUtf8()).getJSONObject("message").has("raw"))

        api.listEmailDrafts("test-token")
        assertTrue(server.takeRequest().path!!.startsWith("/gmail/v1/users/me/drafts?"))

        api.getEmailDraft("test-token", "draft-1")
        assertEquals("/gmail/v1/users/me/drafts/draft-1", server.takeRequest().path)

        api.sendEmailDraft("test-token", "draft-1", confirmed = true)
        assertEquals("/gmail/v1/users/me/drafts/send", server.takeRequest().path)

        api.deleteEmailDraftPermanently("test-token", "draft-1", confirmed = true)
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/gmail/v1/users/me/drafts/draft-1", delete.path)
    }

    @Test
    fun trashEmailPostsToReversibleTrashEndpoint() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"id\":\"message-1\",\"labelIds\":[\"TRASH\"]}"))

        api.trashEmail("test-token", "message-1", confirmed = true)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/gmail/v1/users/me/messages/message-1/trash", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
    }

    @Test
    fun permanentlyDeleteEmailRequiresConfirmationAndUsesDeleteEndpoint() {
        assertThrows(IllegalStateException::class.java) {
            api.deleteEmailPermanently("test-token", "message-1", confirmed = false)
        }
        assertEquals(0, server.requestCount)
        server.enqueue(MockResponse().setResponseCode(204))

        api.deleteEmailPermanently("test-token", "message-1", confirmed = true)

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/gmail/v1/users/me/messages/message-1", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
    }

    @Test
    fun createCalendarEventPostsRequestedDetails() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"id\":\"event-1\"}"))

        api.createCalendarEvent(
            accessToken = "test-token",
            summary = "Test event",
            startDateTime = "2026-10-01T10:00:00-06:00",
            endDateTime = "2026-10-01T10:30:00-06:00",
            timeZone = "America/Costa_Rica",
            description = "Unit test",
            confirmed = true
        )

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/calendar/v3/calendars/primary/events", request.path)
        val json = JSONObject(request.body.readUtf8())
        assertEquals("Test event", json.getString("summary"))
        assertEquals("America/Costa_Rica", json.getJSONObject("start").getString("timeZone"))
        assertEquals("Unit test", json.getString("description"))
    }

    @Test
    fun calendarListCreateCalendarSearchAvailabilityAndUpdateRoutes() {
        repeat(5) { server.enqueue(MockResponse().setResponseCode(200).setBody("{}")) }

        api.listCalendars("test-token")
        assertEquals("/calendar/v3/users/me/calendarList", server.takeRequest().path)

        api.createSecondaryCalendar("test-token", "Omni test", "America/Costa_Rica", confirmed = true)
        val createCalendar = server.takeRequest()
        assertEquals("POST", createCalendar.method)
        assertEquals("Omni test", JSONObject(createCalendar.body.readUtf8()).getString("summary"))

        api.listCalendarEvents("test-token", query = "school", maxResults = 25)
        val events = server.takeRequest()
        assertTrue(events.path!!.startsWith("/calendar/v3/calendars/primary/events?"))
        assertTrue(events.path!!.contains("q=school"))

        api.queryCalendarAvailability(
            "test-token", "2026-10-01T00:00:00Z", "2026-10-02T00:00:00Z", "UTC"
        )
        val freeBusy = server.takeRequest()
        assertEquals("/calendar/v3/freeBusy", freeBusy.path)
        assertEquals("primary", JSONObject(freeBusy.body.readUtf8()).getJSONArray("items").getJSONObject(0).getString("id"))

        api.updateCalendarEvent("test-token", "event-1", summary = "Updated", confirmed = true)
        val update = server.takeRequest()
        assertEquals("PATCH", update.method)
        assertEquals("/calendar/v3/calendars/primary/events/event-1", update.path)
        assertEquals("Updated", JSONObject(update.body.readUtf8()).getString("summary"))
    }

    @Test
    fun deleteCalendarEventRequiresConfirmationAndUsesEventId() {
        server.enqueue(MockResponse().setResponseCode(204))

        api.deleteCalendarEvent("test-token", "event-1", confirmed = true)

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/calendar/v3/calendars/primary/events/event-1", request.path)
    }

    @Test
    fun uploadDriveFileSendsMultipartMetadataAndContent() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"id\":\"file-1\",\"name\":\"note.txt\"}"))

        api.uploadDriveFile(
            accessToken = "test-token",
            fileName = "note.txt",
            mimeType = "text/plain",
            bytes = "hello".toByteArray(),
            confirmed = true
        )

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.startsWith("/upload/drive/v3/files?"))
        assertTrue(request.getHeader("Content-Type")!!.contains("multipart/related"))
        val multipart = request.body.readUtf8()
        assertTrue(multipart.contains("note.txt"))
        assertTrue(multipart.contains("hello"))
    }

    @Test
    fun driveSearchMetadataDownloadFolderAndUpdatesUseExpectedRoutes() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"files\":[]}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"id\":\"file-1\"}"))
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "text/plain").setBody("hello"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"id\":\"folder-1\"}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"id\":\"file-1\"}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"id\":\"file-1\"}"))

        api.listDriveFiles("test-token", query = "name contains 'note'", pageSize = 10)
        val list = server.takeRequest()
        assertTrue(list.path!!.startsWith("/drive/v3/files?"))
        assertTrue(list.path!!.contains("pageSize=10"))

        api.getDriveFileMetadata("test-token", "file-1")
        val metadata = server.takeRequest()
        assertTrue(metadata.path!!.startsWith("/drive/v3/files/file-1?fields="))

        val download = api.downloadDriveFile("test-token", "file-1")
        val downloadRequest = server.takeRequest()
        assertEquals("GET", downloadRequest.method)
        assertTrue(downloadRequest.path!!.contains("alt=media"))
        assertEquals("hello", String(download.bytes, Charsets.UTF_8))
        assertEquals("text/plain", download.contentType?.substringBefore(';'))

        api.createDriveFolder("test-token", "Folder", confirmed = true)
        val folder = server.takeRequest()
        assertEquals("POST", folder.method)
        assertEquals("application/vnd.google-apps.folder", JSONObject(folder.body.readUtf8()).getString("mimeType"))

        api.updateDriveFileMetadata("test-token", "file-1", name = "renamed.txt", confirmed = true)
        assertEquals("PATCH", server.takeRequest().method)

        api.updateDriveFileContent("test-token", "file-1", "text/plain", "new body".toByteArray(), confirmed = true)
        val update = server.takeRequest()
        assertEquals("PATCH", update.method)
        assertTrue(update.path!!.startsWith("/upload/drive/v3/files/file-1?"))
        assertTrue(update.body.readUtf8().contains("new body"))
    }

    @Test
    fun permanentlyDeleteDriveFileRequiresConfirmationAndUsesDeleteEndpoint() {
        assertThrows(IllegalStateException::class.java) {
            api.deleteDriveFilePermanently("test-token", "file-1", confirmed = false)
        }
        assertEquals(0, server.requestCount)
        server.enqueue(MockResponse().setResponseCode(204))

        api.deleteDriveFilePermanently("test-token", "file-1", confirmed = true)

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/drive/v3/files/file-1", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
    }

    @Test
    fun trashDriveFileUsesReversibleUpdateAndRequiresConfirmation() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"id\":\"file-1\",\"trashed\":true}"))

        api.trashDriveFile("test-token", "file-1", confirmed = true)

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertTrue(request.path!!.startsWith("/drive/v3/files/file-1?"))
        assertTrue(JSONObject(request.body.readUtf8()).getBoolean("trashed"))
        assertFalse(request.getHeader("Authorization").isNullOrBlank())
    }
}

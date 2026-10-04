package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatYouTubeViewerTest {

    @Test
    fun embedUrlUsesTheInAppYouTubePlayerWithInlinePlayback() {
        val url = buildYouTubeEmbedUrl("dQw4w9WgXcQ")

        assertTrue(url.startsWith("https://www.youtube.com/embed/dQw4w9WgXcQ?"))
        assertTrue(url.contains("autoplay=0"))
        assertTrue(url.contains("origin=https%3A%2F%2Fcostalso2029.dpdns.org"))
        assertTrue(url.contains("playsinline=1"))
        assertFalse(url.startsWith("intent://"))
    }

    @Test
    fun nonEmbeddableYouTubeErrorIsShownInAppWithoutExternalFallback() {
        val message = youtubePlayerErrorMessage("152-4")
        assertTrue(message.contains("no está disponible"))
        assertTrue(message.contains("No se abrirá otra app"))
    }

    @Test
    fun portraitAndLandscapeVideosFitAvailableBoundsWithoutCroppingOrStretching() {
        val portrait = fitVideoIntoBounds(videoWidth = 720, videoHeight = 1280, boundsWidth = 1080, boundsHeight = 1800)
        assertEquals(1013, portrait.width)
        assertEquals(1800, portrait.height)

        val landscape = fitVideoIntoBounds(videoWidth = 1920, videoHeight = 1080, boundsWidth = 1080, boundsHeight = 1800)
        assertEquals(1080, landscape.width)
        assertEquals(608, landscape.height)
    }

    @Test
    fun pdfAndPlainTextAttachmentsRouteToInAppPreview() {
        assertEquals(ChatFilePreviewKind.PDF, resolveChatFilePreviewKind("report.pdf", "https://cdn.test/file?id=2"))
        assertEquals(ChatFilePreviewKind.PDF, resolveChatFilePreviewKind("download", "https://cdn.test/blob", "application/pdf"))
        assertEquals(ChatFilePreviewKind.TEXT, resolveChatFilePreviewKind("notes.txt", "https://cdn.test/notes.txt?token=x"))
        assertEquals(ChatFilePreviewKind.TEXT, resolveChatFilePreviewKind("readme.md", ""))
        assertEquals(ChatFilePreviewKind.TEXT, resolveChatFilePreviewKind("unknown", "", "text/plain; charset=utf-8"))
    }

    @Test
    fun officeAndUnknownAttachmentsStayInTheInChatDetailsDownloadRoute() {
        assertEquals(ChatFilePreviewKind.UNSUPPORTED, resolveChatFilePreviewKind("report.docx", "https://cdn.test/report.docx"))
        assertEquals(ChatFilePreviewKind.UNSUPPORTED, resolveChatFilePreviewKind("slides.pptx", "https://cdn.test/slides.pptx"))
        assertEquals(ChatFilePreviewKind.UNSUPPORTED, resolveChatFilePreviewKind("archive.zip", "https://cdn.test/archive.zip"))
        assertEquals(ChatFilePreviewKind.UNSUPPORTED, resolveChatFilePreviewKind("document.pdf", "", "application/zip"))
        assertEquals(ChatFilePreviewKind.UNSUPPORTED, resolveChatFilePreviewKind("page.html", "https://cdn.test/page.html"))
    }
}


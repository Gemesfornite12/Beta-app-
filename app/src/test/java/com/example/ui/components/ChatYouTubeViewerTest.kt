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
        assertTrue(url.contains("autoplay=1"))
        assertTrue(url.contains("playsinline=1"))
        assertFalse(url.startsWith("intent://"))
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


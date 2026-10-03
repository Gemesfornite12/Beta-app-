package com.example.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GroqWorkspaceConnectorTest {
    @Test
    fun exposesOnlyTheThreeSupportedGoogleWorkspaceConnectors() {
        assertEquals(setOf("gmail", "calendar", "drive"), GroqWorkspaceConnectors.all.map { it.id }.toSet())
        assertEquals(
            setOf("connector_gmail", "connector_googlecalendar", "connector_googledrive"),
            GroqWorkspaceConnectors.all.map { it.groqConnectorId }.toSet()
        )
    }

    @Test
    fun eachConnectorRequestsItsNarrowDocumentedScope() {
        assertEquals(
            "https://www.googleapis.com/auth/gmail.readonly",
            GroqWorkspaceConnectors.find("gmail")?.scope
        )
        assertEquals(
            "https://www.googleapis.com/auth/calendar.events.readonly",
            GroqWorkspaceConnectors.find("calendar")?.scope
        )
        assertEquals(
            "https://www.googleapis.com/auth/drive.readonly",
            GroqWorkspaceConnectors.find("drive")?.scope
        )
    }

    @Test
    fun unknownConnectorIsRejected() {
        assertNotNull(GroqWorkspaceConnectors.find("gmail"))
        assertEquals(null, GroqWorkspaceConnectors.find("mailbox"))
    }
}

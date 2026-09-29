package com.example.data.api

import com.google.android.gms.common.api.Scope

/** Google Workspace connectors currently exposed by Groq's read-only MCP connector beta. */
data class GroqWorkspaceConnector(
    val id: String,
    val label: String,
    val description: String,
    val scope: String,
    val groqConnectorId: String
) {
    fun toGoogleScope(): Scope = Scope(scope)
}

object GroqWorkspaceConnectors {
    val all: List<GroqWorkspaceConnector> = listOf(
        GroqWorkspaceConnector(
            id = "gmail",
            label = "Gmail",
            description = "Buscar y leer correos",
            scope = "https://www.googleapis.com/auth/gmail.readonly",
            groqConnectorId = "connector_gmail"
        ),
        GroqWorkspaceConnector(
            id = "calendar",
            label = "Google Calendar",
            description = "Consultar eventos del calendario",
            scope = "https://www.googleapis.com/auth/calendar.events.readonly",
            groqConnectorId = "connector_googlecalendar"
        ),
        GroqWorkspaceConnector(
            id = "drive",
            label = "Google Drive",
            description = "Buscar y leer archivos",
            scope = "https://www.googleapis.com/auth/drive.readonly",
            groqConnectorId = "connector_googledrive"
        )
    )

    fun find(id: String): GroqWorkspaceConnector? = all.firstOrNull { it.id == id }
}

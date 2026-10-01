package com.example.ui.screens.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleWorkspaceCommandParserTest {
    @Test
    fun sendEmailCommandPrefillsFieldsButDoesNotExecute() {
        val parsed = GoogleWorkspaceCommandParser.parse(
            "Envíe un correo electrónico a test@example.com asunto: Prueba mensaje: Hola"
        )
        requireNotNull(parsed)
        assertEquals(GoogleWorkspaceActionType.SEND_EMAIL, parsed.request.type)
        assertEquals("test@example.com", parsed.request.values["recipient"])
        assertEquals("Prueba", parsed.request.values["subject"])
        assertEquals("Hola", parsed.request.values["body"])
    }

    @Test
    fun listCalendarsCommandMapsToReadOnlyAction() {
        val parsed = GoogleWorkspaceCommandParser.parse("Listar calendarios")
        requireNotNull(parsed)
        assertEquals(GoogleWorkspaceActionType.LIST_CALENDARS, parsed.request.type)
        assertEquals(false, parsed.request.type.changesData)
    }

    @Test
    fun ordinaryQuestionDoesNotOpenWorkspaceActions() {
        assertNull(GoogleWorkspaceCommandParser.parse("¿Qué hora es?"))
    }

    @Test
    fun permanentDeleteCommandOnlyPreparesConfirmedAction() {
        val parsed = GoogleWorkspaceCommandParser.parse("Borra permanentemente el correo con id ABCDE")
        requireNotNull(parsed)
        assertEquals(GoogleWorkspaceActionType.DELETE_EMAIL, parsed.request.type)
        assertEquals("ABCDE", parsed.request.values["messageId"])
        assertEquals(true, parsed.request.type.irreversible)
    }
}

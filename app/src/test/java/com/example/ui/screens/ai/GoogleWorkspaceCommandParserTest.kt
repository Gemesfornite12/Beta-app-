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

    @Test
    fun shortCommandsOpenTheRightFormsAndUseTrashByDefault() {
        val mail = GoogleWorkspaceCommandParser.parse("Borrar correo ABCDE")
        val drive = GoogleWorkspaceCommandParser.parse("Borrar archivo FGHIJ")
        val events = GoogleWorkspaceCommandParser.parse("Eventos")
        val labels = GoogleWorkspaceCommandParser.parse("Etiquetas")
        val drafts = GoogleWorkspaceCommandParser.parse("Borradores")
        requireNotNull(mail)
        requireNotNull(drive)
        requireNotNull(events)
        requireNotNull(labels)
        requireNotNull(drafts)
        assertEquals(GoogleWorkspaceActionType.TRASH_EMAIL, mail.request.type)
        assertEquals("ABCDE", mail.request.values["messageId"])
        assertEquals(GoogleWorkspaceActionType.TRASH_DRIVE, drive.request.type)
        assertEquals("FGHIJ", drive.request.values["fileId"])
        assertEquals(GoogleWorkspaceActionType.LIST_EVENTS, events.request.type)
        assertEquals(GoogleWorkspaceActionType.LIST_LABELS, labels.request.type)
        assertEquals(GoogleWorkspaceActionType.LIST_DRAFTS, drafts.request.type)
    }

    @Test
    fun simplePermanentDeleteWordsStillRequireExplicitIntent() {
        val parsed = GoogleWorkspaceCommandParser.parse("Borrar permanentemente correo ABCDE")
        requireNotNull(parsed)
        assertEquals(GoogleWorkspaceActionType.DELETE_EMAIL, parsed.request.type)
        assertEquals("ABCDE", parsed.request.values["messageId"])
        assertEquals(true, parsed.request.type.irreversible)
    }
}

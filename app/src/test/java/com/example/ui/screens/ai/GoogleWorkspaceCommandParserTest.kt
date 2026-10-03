package com.example.ui.screens.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun humanCalendarSynonymsAndCommonTypoOpenCalendarCreationCard() {
        val misspelled = GoogleWorkspaceCommandParser.parse("agregar caladario Hola")
        val english = GoogleWorkspaceCommandParser.parse("add a calendar Work")
        val french = GoogleWorkspaceCommandParser.parse("ajouter un calendrier Maison")
        val event = GoogleWorkspaceCommandParser.parse("create a new event")
        val frenchEvent = GoogleWorkspaceCommandParser.parse("ajouter un événement")
        requireNotNull(misspelled)
        requireNotNull(english)
        requireNotNull(french)
        requireNotNull(event)
        requireNotNull(frenchEvent)
        assertEquals(GoogleWorkspaceActionType.CREATE_CALENDAR, misspelled.request.type)
        assertEquals("Hola", misspelled.request.values["summary"])
        assertEquals(GoogleWorkspaceActionType.CREATE_CALENDAR, english.request.type)
        assertEquals(GoogleWorkspaceActionType.CREATE_CALENDAR, french.request.type)
        assertEquals(GoogleWorkspaceActionType.CREATE_EVENT, event.request.type)
        assertEquals(GoogleWorkspaceActionType.CREATE_EVENT, frenchEvent.request.type)
    }

    @Test
    fun workspaceRequestsInOtherLanguagesAreMarkedForTranslation() {
        assertTrue(GoogleWorkspaceCommandParser.shouldTranslatePotentialWorkspaceCommand("Ajouter un événement au calendrier"))
        assertTrue(GoogleWorkspaceCommandParser.shouldTranslatePotentialWorkspaceCommand("Create a new folder in Google Drive"))
        assertTrue(!GoogleWorkspaceCommandParser.shouldTranslatePotentialWorkspaceCommand("Hola, ¿cómo estás?"))
    }

    @Test
    fun shortUploadCommandOpensTheInlineDriveUploadAction() {
        val parsed = GoogleWorkspaceCommandParser.parse("subir")
        requireNotNull(parsed)
        assertEquals(GoogleWorkspaceActionType.UPLOAD_DRIVE, parsed.request.type)
        assertEquals(true, parsed.request.type.needsFile)
        assertEquals(true, parsed.request.type.changesData)
        assertEquals(false, GoogleWorkspaceCommandParser.canRunReadOnlyDirectly(parsed.request))
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

    @Test
    fun pastedCommandCatalogIsRejectedAsBatch() {
        val catalog = """GMAIL
Buscar correos [consulta]
Borrar correo [ID]
Borrar correo permanentemente [ID]
CALENDAR
Eventos
DRIVE
Borrar archivo permanentemente [ID]"""
        assertTrue(GoogleWorkspaceCommandParser.isReferenceOrBatch(catalog))
        assertNull(GoogleWorkspaceCommandParser.parse(catalog))
    }

    @Test
    fun permanentWordCannotBecomeAnEmailId() {
        val parsed = GoogleWorkspaceCommandParser.parse("Borrar correo permanentemente")
        requireNotNull(parsed)
        assertEquals(GoogleWorkspaceActionType.DELETE_EMAIL, parsed.request.type)
        assertEquals("", parsed.request.values["messageId"])
    }

    @Test
    fun onlyCompleteReadOnlyRequestsCanRunDirectly() {
        val events = GoogleWorkspaceCommandParser.parse("Eventos")
        val readWithId = GoogleWorkspaceCommandParser.parse("Leer correo ABCDE")
        val readWithoutId = GoogleWorkspaceCommandParser.parse("Leer correo")
        requireNotNull(events)
        requireNotNull(readWithId)
        requireNotNull(readWithoutId)
        assertEquals(true, GoogleWorkspaceCommandParser.canRunReadOnlyDirectly(events.request))
        assertEquals(true, GoogleWorkspaceCommandParser.canRunReadOnlyDirectly(readWithId.request))
        assertEquals(false, GoogleWorkspaceCommandParser.canRunReadOnlyDirectly(readWithoutId.request))
    }
}

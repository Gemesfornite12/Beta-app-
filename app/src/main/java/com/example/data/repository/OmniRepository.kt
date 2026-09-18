package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.model.AudioProject
import com.example.data.model.ChatMessage
import com.example.data.model.DocumentFormat
import com.example.data.model.DocumentItem
import com.example.data.model.DocumentType
import com.example.data.model.UserAccount
import kotlinx.coroutines.flow.Flow

class OmniRepository(private val db: AppDatabase) {
    val allDocuments: Flow<List<DocumentItem>> = db.documentDao().getAllDocuments()
    val allAudioProjects: Flow<List<AudioProject>> = db.audioProjectDao().getAllAudioProjects()
    val publicAudioProjects: Flow<List<AudioProject>> = db.audioProjectDao().getPublicAudioProjects()
    val allUsers: Flow<List<UserAccount>> = db.userDao().getAllUsers()

    fun getMessagesForChannel(channelId: String): Flow<List<ChatMessage>> =
        db.chatMessageDao().getMessagesForChannel(channelId)

    fun searchDocuments(query: String): Flow<List<DocumentItem>> =
        db.documentDao().searchDocuments(query)

    suspend fun getDocumentById(id: Long): DocumentItem? = db.documentDao().getDocumentById(id)
    suspend fun insertDocument(doc: DocumentItem): Long = db.documentDao().insertDocument(doc)
    suspend fun updateDocument(doc: DocumentItem) = db.documentDao().updateDocument(doc)
    suspend fun deleteDocument(id: Long) = db.documentDao().deleteDocument(id)

    suspend fun getAudioProjectById(id: Long): AudioProject? = db.audioProjectDao().getAudioProjectById(id)
    suspend fun insertAudioProject(project: AudioProject): Long = db.audioProjectDao().insertAudioProject(project)
    suspend fun updateAudioProject(project: AudioProject) = db.audioProjectDao().updateAudioProject(project)
    suspend fun updateAudioProjectTitle(id: Long, newTitle: String) = db.audioProjectDao().updateTitle(id, newTitle)
    suspend fun updateAudioProjectPublicStatus(id: Long, isPublic: Boolean) = db.audioProjectDao().updatePublicStatus(id, isPublic)
    suspend fun deleteAudioProject(id: Long) = db.audioProjectDao().deleteAudioProject(id)

    suspend fun getUnsyncedMessages(): List<ChatMessage> = db.chatMessageDao().getUnsyncedMessages()
    suspend fun insertChatMessage(msg: ChatMessage): Long = db.chatMessageDao().insertMessage(msg)
    suspend fun insertChatMessages(msgs: List<ChatMessage>) = db.chatMessageDao().insertMessages(msgs)
    suspend fun updateChatMessage(msg: ChatMessage) = db.chatMessageDao().updateMessage(msg)
    suspend fun deleteChatMessage(id: Long) = db.chatMessageDao().deleteMessage(id)
    suspend fun deleteChatMessageByFirestoreId(firestoreId: String) = db.chatMessageDao().deleteMessageByFirestoreId(firestoreId)
    suspend fun deleteMessagesForChannel(channelId: String) = db.chatMessageDao().deleteMessagesForChannel(channelId)

    suspend fun getUserByEmail(email: String): UserAccount? = db.userDao().getUserByEmail(email)
    suspend fun saveUser(user: UserAccount) = db.userDao().insertUser(user)
    suspend fun updateUser(user: UserAccount) = db.userDao().updateUser(user)

    suspend fun seedInitialDataIfEmpty() {
        val userCount = db.userDao().getUserByEmail("gonzalez24029@gmail.com")
        if (userCount == null) {
            val defaultUser = UserAccount(
                email = "gonzalez24029@gmail.com",
                username = "gonzalez_dev",
                displayName = "Alex González",
                passwordHash = "demo1234",
                isGoogleAccount = true,
                avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200&q=80",
                cloudStorageUsedMb = 3450,
                cloudStorageTotalMb = 15360
            )
            db.userDao().insertUser(defaultUser)

            val teammates = listOf(
                UserAccount(
                    email = "sofia.m@cloud.io",
                    username = "sofia_audio",
                    displayName = "Sofia Martinez",
                    passwordHash = "demo1234",
                    avatarUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=200&q=80",
                    cloudStorageUsedMb = 2100,
                    cloudStorageTotalMb = 15360
                ),
                UserAccount(
                    email = "carlos.m@cloud.io",
                    username = "carlos_docs",
                    displayName = "Carlos Mendoza",
                    passwordHash = "demo1234",
                    avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200&q=80",
                    cloudStorageUsedMb = 1890,
                    cloudStorageTotalMb = 15360
                ),
                UserAccount(
                    email = "alex.r@cloud.io",
                    username = "alex_beats",
                    displayName = "Alex Riva",
                    passwordHash = "demo1234",
                    avatarUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200&q=80",
                    cloudStorageUsedMb = 3120,
                    cloudStorageTotalMb = 15360
                ),
                UserAccount(
                    email = "elena.t@cloud.io",
                    username = "elena_design",
                    displayName = "Elena Torres",
                    passwordHash = "demo1234",
                    avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&q=80",
                    cloudStorageUsedMb = 940,
                    cloudStorageTotalMb = 15360
                ),
                UserAccount(
                    email = "mateo.s@cloud.io",
                    username = "mateo_sound",
                    displayName = "Mateo Silva",
                    passwordHash = "demo1234",
                    avatarUrl = "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=200&q=80",
                    cloudStorageUsedMb = 1450,
                    cloudStorageTotalMb = 15360
                )
            )
            teammates.forEach { db.userDao().insertUser(it) }

            // Seed initial document inspired by user screenshots (Presentación y Docs)
            val initialSlide = DocumentItem(
                title = "Presentación Estratégica 2026",
                content = "Double-tap to add title\nDouble-tap to add subtitle\n\n1. Visión general del ecosistema en la nube.\n2. Productividad unificada: Suite de texto, música y mensajería.\n3. Escalabilidad colaborativa.",
                docType = DocumentType.SLIDE,
                currentFormat = DocumentFormat.PPTX,
                slideCount = 3,
                slidesJson = """[
                    {"title": "OmniStudio Cloud 2026", "subtitle": "Plataforma Unificada Creativa y de Mensajería", "bg": "#1E293B"},
                    {"title": "Arquitectura Multi-Herramienta", "subtitle": "Documentos + Audio DAW + Colaboración en Tiempo Real", "bg": "#0F172A"},
                    {"title": "Exportación Multi-Formato", "subtitle": "DOCX, PDF, TXT, Markdown, HTML a un toque", "bg": "#1E1B4B"}
                ]""",
                authorEmail = defaultUser.email,
                fileSizeKb = 120,
                isFavorite = true
            )
            db.documentDao().insertDocument(initialSlide)

            val initialDoc = DocumentItem(
                title = "Especificación de Producto Todo-en-Uno",
                content = """# OmniStudio: Todo en una sola aplicación en la nube

## 1. Visión del Proyecto
¿Por qué tener 10 aplicaciones instaladas cuando una sola puede sincronizar en la nube tu trabajo, tu creatividad y tus conversaciones?

## 2. Componentes Integrados:
- **Editor y Conversor de Documentos**: Edita documentos, notas y diapositivas. Cambia de formato entre Word, PDF, Texto plano y Markdown sin salir de la app.
- **Suite de Producción Musical**: Secuenciador de ritmo por pasos, sintetizador polifónico y exportación a la nube.
- **Mensajería en Tiempo Real**: Salas de chat temáticas con capacidad de compartir proyectos de audio y documentos directamente.
- **Seguridad en la Nube**: Autenticación Google, correo y restablecimiento seguro de contraseñas.
""",
                docType = DocumentType.DOC,
                currentFormat = DocumentFormat.DOCX,
                authorEmail = defaultUser.email,
                fileSizeKb = 64,
                isFavorite = true
            )
            db.documentDao().insertDocument(initialDoc)

            val resumeDoc = DocumentItem(
                title = "Currículum Vitae Profesional",
                content = "ALEXIS GONZÁLEZ\nDiseñador de Sistemas & Productor Creativo\n\nExperiencia: Creación de plataformas multimedia, diseño sonoro y flujos de trabajo en la nube.",
                docType = DocumentType.RESUME,
                currentFormat = DocumentFormat.PDF,
                authorEmail = defaultUser.email,
                fileSizeKb = 32
            )
            db.documentDao().insertDocument(resumeDoc)

            // Seed initial Beat Project
            val defaultBeatPattern = """{
                "kick": [true, false, false, false, true, false, false, false, true, false, false, false, true, false, false, false],
                "snare": [false, false, false, false, true, false, false, false, false, false, false, false, true, false, false, false],
                "hihat": [true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true],
                "clap": [false, false, false, false, false, false, false, false, false, false, false, false, true, false, false, false],
                "bass": [true, false, false, true, false, false, true, false, false, true, false, false, true, false, false, false],
                "lead": [false, false, true, false, false, true, false, false, true, false, false, true, false, false, true, false]
            }"""

            val initialBeat = AudioProject(
                title = "Midnight Cyber Beat",
                description = "Beat sintético futurista con bajo envolvente y percusión retro 80s",
                genre = "Synthwave / Lo-Fi",
                bpm = 118,
                patternDataJson = defaultBeatPattern,
                authorEmail = defaultUser.email,
                authorName = defaultUser.displayName,
                isPublic = true,
                aiPrompt = "Beat synthwave con arpegios electrónicos y batería contundente",
                durationSeconds = 32,
                fileSizeKb = 96
            )
            val beatId = db.audioProjectDao().insertAudioProject(initialBeat)

            val sofiaBeatPattern = """{
                "kick": [true, false, false, false, false, false, true, false, false, false, true, false, false, false, false, false],
                "snare": [false, false, false, false, true, false, false, false, false, false, false, false, true, false, false, false],
                "hihat": [true, false, true, false, true, false, true, false, true, false, true, false, true, false, true, false],
                "clap": [false, false, false, false, false, false, false, false, false, false, false, false, true, false, false, false],
                "bass": [true, false, false, false, false, false, false, false, true, false, false, false, false, false, false, false],
                "lead": [false, false, true, false, true, false, false, false, false, false, true, false, false, true, false, false]
            }"""
            val communityBeat = AudioProject(
                title = "Sunset Lo-Fi Oasis",
                description = "Ritmo chill relajante para programar y concentrarse",
                genre = "Chillhop / Lo-Fi",
                bpm = 88,
                patternDataJson = sofiaBeatPattern,
                authorEmail = "sofia.m@cloud.io",
                authorName = "Sofia Martinez",
                isPublic = true,
                aiPrompt = "Un beat lo-fi suave y cálido con melodía nostálgica y platillos lentos",
                durationSeconds = 28,
                fileSizeKb = 84
            )
            db.audioProjectDao().insertAudioProject(communityBeat)

            // Seed initial chat messages
            db.chatMessageDao().insertMessage(
                ChatMessage(
                    channelId = "general",
                    senderName = "Sofia Martinez",
                    senderEmail = "sofia.m@cloud.io",
                    text = "¡Bienvenidos a OmniStudio! Subí la última versión de las diapositivas para la reunión.",
                    timestamp = System.currentTimeMillis() - 3600000 * 2,
                    reactions = "👋,✨"
                )
            )

            db.chatMessageDao().insertMessage(
                ChatMessage(
                    channelId = "general",
                    senderName = "Alex Riva",
                    senderEmail = "alex.r@cloud.io",
                    text = "Acabo de escuchar la nueva pista en la suite musical, ¡los bombos y el bajo quedaron geniales!",
                    timestamp = System.currentTimeMillis() - 1800000,
                    attachedAudioId = beatId,
                    attachedAudioTitle = "Midnight Cyber Beat",
                    reactions = "🔥,🎵"
                )
            )

            db.chatMessageDao().insertMessage(
                ChatMessage(
                    channelId = "general",
                    senderName = "Alex González",
                    senderEmail = defaultUser.email,
                    text = "Pueden convertir los documentos a PDF o TXT al instante con el botón de conversión en la nube.",
                    timestamp = System.currentTimeMillis() - 600000,
                    reactions = "👍"
                )
            )
        }
    }
}

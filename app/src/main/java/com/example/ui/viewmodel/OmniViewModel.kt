package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioSynthEngine
import com.example.data.local.AppDatabase
import com.example.data.model.AudioProject
import com.example.data.model.ChatMessage
import com.example.data.model.DocumentFormat
import com.example.data.model.DocumentItem
import com.example.data.model.DocumentType
import com.example.data.model.UserAccount
import com.example.data.repository.OmniRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class AuthUiState(
    val currentUser: UserAccount? = null,
    val isLoggedIn: Boolean = true, // Default to true after seeding, or false for login
    val isAuthModeLogin: Boolean = true,
    val emailInput: String = "",
    val passwordInput: String = "",
    val nameInput: String = "",
    val isForgotPasswordOpen: Boolean = false,
    val recoveryEmailSent: Boolean = false,
    val recoveryCodeInput: String = "",
    val newPasswordInput: String = "",
    val authFeedbackMessage: String? = null,
    val isGoogleSigningIn: Boolean = false
)

data class SequencerTrack(
    val name: String,
    val soundType: String,
    val isMuted: Boolean = false,
    val steps: BooleanArray = BooleanArray(16) { false }
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SequencerTrack
        if (name != other.name) return false
        if (soundType != other.soundType) return false
        if (isMuted != other.isMuted) return false
        if (!steps.contentEquals(other.steps)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + soundType.hashCode()
        result = 31 * result + isMuted.hashCode()
        result = 31 * result + steps.contentHashCode()
        return result
    }
}

class OmniViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: OmniRepository

    private val _authUiState = MutableStateFlow(AuthUiState())
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    // Documents & Audio Projects
    val documents: StateFlow<List<DocumentItem>>
    val audioProjects: StateFlow<List<AudioProject>>

    // Search query & category filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeCategoryFilter = MutableStateFlow("TODOS") // TODOS, DOCS, SLIDES, MUSIC
    val activeCategoryFilter: StateFlow<String> = _activeCategoryFilter.asStateFlow()

    // Currently Editing Document
    private val _currentEditingDoc = MutableStateFlow<DocumentItem?>(null)
    val currentEditingDoc: StateFlow<DocumentItem?> = _currentEditingDoc.asStateFlow()

    // Slide Editor specific state
    private val _activeSlideIndex = MutableStateFlow(0)
    val activeSlideIndex: StateFlow<Int> = _activeSlideIndex.asStateFlow()

    // Music Sequencer State
    private val _activeAudioProject = MutableStateFlow<AudioProject?>(null)
    val activeAudioProject: StateFlow<AudioProject?> = _activeAudioProject.asStateFlow()

    private val _sequencerTracks = MutableStateFlow<List<SequencerTrack>>(emptyList())
    val sequencerTracks: StateFlow<List<SequencerTrack>> = _sequencerTracks.asStateFlow()

    private val _currentBpm = MutableStateFlow(120)
    val currentBpm: StateFlow<Int> = _currentBpm.asStateFlow()

    private val _isPlayingSequencer = MutableStateFlow(false)
    val isPlayingSequencer: StateFlow<Boolean> = _isPlayingSequencer.asStateFlow()

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private var sequencerJob: Job? = null

    // Chat State
    private val _currentChannel = MutableStateFlow("general")
    val currentChannel: StateFlow<String> = _currentChannel.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _chatInputText = MutableStateFlow("")
    val chatInputText: StateFlow<String> = _chatInputText.asStateFlow()

    // Format Converter Sheet State
    private val _converterDoc = MutableStateFlow<DocumentItem?>(null)
    val converterDoc: StateFlow<DocumentItem?> = _converterDoc.asStateFlow()

    private val _targetFormat = MutableStateFlow(DocumentFormat.PDF)
    val targetFormat: StateFlow<DocumentFormat> = _targetFormat.asStateFlow()

    private val _conversionSuccessMessage = MutableStateFlow<String?>(null)
    val conversionSuccessMessage: StateFlow<String?> = _conversionSuccessMessage.asStateFlow()

    init {
        val db = AppDatabase.getInstance(application)
        repo = OmniRepository(db)

        documents = repo.allDocuments.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        audioProjects = repo.allAudioProjects.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        viewModelScope.launch {
            repo.seedInitialDataIfEmpty()
            val user = repo.getUserByEmail("gonzalez24029@gmail.com")
            _authUiState.value = _authUiState.value.copy(
                currentUser = user,
                isLoggedIn = true
            )
            loadChannelMessages("general")
            initDefaultSequencerTracks()
        }
    }

    // AUTH ACTIONS
    fun toggleAuthMode() {
        _authUiState.value = _authUiState.value.copy(
            isAuthModeLogin = !_authUiState.value.isAuthModeLogin,
            authFeedbackMessage = null
        )
    }

    fun onEmailInputChanged(value: String) {
        _authUiState.value = _authUiState.value.copy(emailInput = value)
    }

    fun onPasswordInputChanged(value: String) {
        _authUiState.value = _authUiState.value.copy(passwordInput = value)
    }

    fun onNameInputChanged(value: String) {
        _authUiState.value = _authUiState.value.copy(nameInput = value)
    }

    fun loginWithEmail() {
        viewModelScope.launch {
            val email = _authUiState.value.emailInput.trim()
            val pass = _authUiState.value.passwordInput.trim()
            if (email.isEmpty() || pass.isEmpty()) {
                _authUiState.value = _authUiState.value.copy(authFeedbackMessage = "Por favor ingresa correo y contraseña")
                return@launch
            }
            val user = repo.getUserByEmail(email)
            if (user != null && (user.passwordHash == pass || user.isGoogleAccount)) {
                _authUiState.value = _authUiState.value.copy(
                    currentUser = user,
                    isLoggedIn = true,
                    authFeedbackMessage = "¡Bienvenido de nuevo, ${user.displayName}!"
                )
            } else if (user != null) {
                _authUiState.value = _authUiState.value.copy(authFeedbackMessage = "Contraseña incorrecta")
            } else {
                // Auto create account or report not found
                val newUser = UserAccount(
                    email = email,
                    username = email.substringBefore("@"),
                    displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                    passwordHash = pass,
                    isGoogleAccount = false
                )
                repo.saveUser(newUser)
                _authUiState.value = _authUiState.value.copy(
                    currentUser = newUser,
                    isLoggedIn = true,
                    authFeedbackMessage = "Cuenta creada exitosamente en la nube"
                )
            }
        }
    }

    fun loginWithGoogle() {
        viewModelScope.launch {
            _authUiState.value = _authUiState.value.copy(isGoogleSigningIn = true)
            delay(600) // Realistic smooth auth transition
            val googleUser = UserAccount(
                email = "gonzalez24029@gmail.com",
                username = "gonzalez_google",
                displayName = "Alexis González (Google)",
                passwordHash = "google_oauth_token",
                isGoogleAccount = true,
                cloudStorageUsedMb = 4850,
                cloudStorageTotalMb = 15360
            )
            repo.saveUser(googleUser)
            _authUiState.value = _authUiState.value.copy(
                currentUser = googleUser,
                isLoggedIn = true,
                isGoogleSigningIn = false,
                authFeedbackMessage = "Conectado vía Google Cloud Identity"
            )
        }
    }

    fun syncUserFromFirebaseAuth(email: String?, displayName: String?, photoUrl: String? = null) {
        val userEmail = email ?: "user@omnistudio.cloud"
        val userName = displayName ?: userEmail.substringBefore("@")
        val account = UserAccount(
            email = userEmail,
            username = userEmail.substringBefore("@"),
            displayName = userName,
            passwordHash = "firebase_auth_session",
            isGoogleAccount = true
        )
        viewModelScope.launch {
            repo.saveUser(account)
            _authUiState.value = _authUiState.value.copy(
                currentUser = account,
                isLoggedIn = true,
                authFeedbackMessage = "¡Bienvenido, $userName!"
            )
        }
    }

    fun logout() {
        _authUiState.value = _authUiState.value.copy(
            isLoggedIn = false,
            currentUser = null,
            authFeedbackMessage = "Sesión cerrada"
        )
    }

    fun openForgotPassword() {
        _authUiState.value = _authUiState.value.copy(
            isForgotPasswordOpen = true,
            recoveryEmailSent = false,
            recoveryCodeInput = "",
            newPasswordInput = "",
            authFeedbackMessage = null
        )
    }

    fun closeForgotPassword() {
        _authUiState.value = _authUiState.value.copy(isForgotPasswordOpen = false)
    }

    fun sendPasswordRecoveryEmail(email: String) {
        viewModelScope.launch {
            if (email.isBlank()) {
                _authUiState.value = _authUiState.value.copy(authFeedbackMessage = "Ingresa tu correo para recuperar contraseña")
                return@launch
            }
            delay(500)
            _authUiState.value = _authUiState.value.copy(
                recoveryEmailSent = true,
                authFeedbackMessage = "Código de recuperación enviado a $email. Código de prueba: 7894"
            )
        }
    }

    fun resetPasswordWithCode(code: String, newPass: String) {
        viewModelScope.launch {
            if (code != "7894" && code.length < 4) {
                _authUiState.value = _authUiState.value.copy(authFeedbackMessage = "Código inválido. Usa 7894")
                return@launch
            }
            if (newPass.length < 4) {
                _authUiState.value = _authUiState.value.copy(authFeedbackMessage = "La contraseña debe tener al menos 4 caracteres")
                return@launch
            }
            val email = _authUiState.value.emailInput.ifBlank { "gonzalez24029@gmail.com" }
            val existing = repo.getUserByEmail(email)
            if (existing != null) {
                repo.saveUser(existing.copy(passwordHash = newPass))
            }
            _authUiState.value = _authUiState.value.copy(
                isForgotPasswordOpen = false,
                recoveryEmailSent = false,
                authFeedbackMessage = "Contraseña restablecida con éxito. Ya puedes iniciar sesión."
            )
        }
    }

    // HOME & SEARCH
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onCategoryFilterChanged(filter: String) {
        _activeCategoryFilter.value = filter
    }

    // DOCUMENT EDITOR
    fun openDocument(doc: DocumentItem) {
        _currentEditingDoc.value = doc
        _activeSlideIndex.value = 0
    }

    fun createNewDocument(type: DocumentType) {
        val userEmail = _authUiState.value.currentUser?.email ?: "gonzalez24029@gmail.com"
        val newDoc = when (type) {
            DocumentType.SLIDE -> DocumentItem(
                title = "Nueva Presentación",
                content = "Double-tap to add title\nDouble-tap to add subtitle",
                docType = DocumentType.SLIDE,
                currentFormat = DocumentFormat.PPTX,
                slideCount = 2,
                slidesJson = """[
                    {"title": "Título de Presentación", "subtitle": "Toca dos veces para editar subtítulo", "bg": "#1E293B"},
                    {"title": "Segunda Diapositiva", "subtitle": "Ideas y puntos clave", "bg": "#0F172A"}
                ]""",
                authorEmail = userEmail
            )
            DocumentType.DOC -> DocumentItem(
                title = "Documento sin título",
                content = "# Nuevo Documento\n\nComienza a escribir aquí tu texto con formato enriquecido...",
                docType = DocumentType.DOC,
                currentFormat = DocumentFormat.DOCX,
                authorEmail = userEmail
            )
            DocumentType.TXT -> DocumentItem(
                title = "Nota rápida.txt",
                content = "Notas de reunión:\n- Punto 1\n- Punto 2",
                docType = DocumentType.TXT,
                currentFormat = DocumentFormat.TXT,
                authorEmail = userEmail
            )
            DocumentType.SHEET -> DocumentItem(
                title = "Hoja de Cálculo Básica",
                content = "Producto | Cantidad | Precio\nLaptop | 2 | $1200\nMouse | 5 | $25\nTeclado | 3 | $60",
                docType = DocumentType.SHEET,
                currentFormat = DocumentFormat.DOCX,
                authorEmail = userEmail
            )
            DocumentType.RESUME -> DocumentItem(
                title = "Curriculum Vitae",
                content = "Nombre: Alexis\nPerfil: Desarrollador y creador multimedia\nHabilidades: Audio, Texto, Cloud",
                docType = DocumentType.RESUME,
                currentFormat = DocumentFormat.PDF,
                authorEmail = userEmail
            )
            DocumentType.PDF -> DocumentItem(
                title = "Documento PDF",
                content = "Documento listo para lectura y firma digital.",
                docType = DocumentType.PDF,
                currentFormat = DocumentFormat.PDF,
                authorEmail = userEmail
            )
        }
        viewModelScope.launch {
            val id = repo.insertDocument(newDoc)
            val inserted = repo.getDocumentById(id)
            _currentEditingDoc.value = inserted
        }
    }

    fun ensureDocumentForEditor() {
        if (_currentEditingDoc.value == null) {
            val firstDoc = documents.value.firstOrNull()
            if (firstDoc != null) {
                openDocument(firstDoc)
            } else {
                createNewDocument(DocumentType.DOC)
            }
        }
    }

    fun ensureAudioProjectForStudio() {
        if (_activeAudioProject.value == null) {
            val firstAudio = audioProjects.value.firstOrNull()
            if (firstAudio != null) {
                openAudioProject(firstAudio)
            } else {
                initDefaultSequencerTracks()
            }
        }
    }

    fun updateCurrentDocTitle(newTitle: String) {
        _currentEditingDoc.value = _currentEditingDoc.value?.copy(title = newTitle, lastModified = System.currentTimeMillis())
    }

    fun updateCurrentDocContent(newContent: String) {
        _currentEditingDoc.value = _currentEditingDoc.value?.copy(content = newContent, lastModified = System.currentTimeMillis())
    }

    fun saveCurrentDocument() {
        val doc = _currentEditingDoc.value ?: return
        viewModelScope.launch {
            repo.updateDocument(doc)
            _conversionSuccessMessage.value = "Documento guardado en la nube correctamente"
        }
    }

    fun deleteDocument(id: Long) {
        viewModelScope.launch {
            repo.deleteDocument(id)
            if (_currentEditingDoc.value?.id == id) {
                _currentEditingDoc.value = null
            }
        }
    }

    fun deleteAudioProject(id: Long) {
        viewModelScope.launch {
            repo.deleteAudioProject(id)
            if (_activeAudioProject.value?.id == id) {
                _activeAudioProject.value = null
            }
        }
    }

    // Slide specific actions
    fun setActiveSlide(index: Int) {
        _activeSlideIndex.value = index
    }

    fun addNewSlide() {
        val doc = _currentEditingDoc.value ?: return
        try {
            val jsonArray = JSONArray(doc.slidesJson)
            val newSlide = JSONObject().apply {
                put("title", "Diapositiva ${jsonArray.length() + 1}")
                put("subtitle", "Toca para agregar subtítulo o contenido")
                put("bg", "#1E293B")
            }
            jsonArray.put(newSlide)
            val updatedDoc = doc.copy(
                slideCount = jsonArray.length(),
                slidesJson = jsonArray.toString(),
                lastModified = System.currentTimeMillis()
            )
            _currentEditingDoc.value = updatedDoc
            _activeSlideIndex.value = jsonArray.length() - 1
            viewModelScope.launch { repo.updateDocument(updatedDoc) }
        } catch (_: Exception) {}
    }

    fun updateSlideContent(slideIndex: Int, title: String, subtitle: String) {
        val doc = _currentEditingDoc.value ?: return
        try {
            val jsonArray = JSONArray(doc.slidesJson)
            if (slideIndex in 0 until jsonArray.length()) {
                val slideObj = jsonArray.getJSONObject(slideIndex)
                slideObj.put("title", title)
                slideObj.put("subtitle", subtitle)
                val updatedDoc = doc.copy(slidesJson = jsonArray.toString(), lastModified = System.currentTimeMillis())
                _currentEditingDoc.value = updatedDoc
            }
        } catch (_: Exception) {}
    }

    // FORMAT CONVERTER
    fun openFormatConverter(doc: DocumentItem) {
        _converterDoc.value = doc
        _targetFormat.value = when (doc.currentFormat) {
            DocumentFormat.DOCX -> DocumentFormat.PDF
            DocumentFormat.PDF -> DocumentFormat.DOCX
            DocumentFormat.TXT -> DocumentFormat.PDF
            DocumentFormat.MARKDOWN -> DocumentFormat.HTML
            DocumentFormat.HTML -> DocumentFormat.PDF
            DocumentFormat.PPTX -> DocumentFormat.PDF
        }
        _conversionSuccessMessage.value = null
    }

    fun setTargetFormat(format: DocumentFormat) {
        _targetFormat.value = format
    }

    fun closeFormatConverter() {
        _converterDoc.value = null
        _conversionSuccessMessage.value = null
    }

    fun executeFormatConversion() {
        val doc = _converterDoc.value ?: return
        val target = _targetFormat.value
        viewModelScope.launch {
            val convertedTitle = doc.title.substringBeforeLast(".") + " (Convertido)." + target.extension
            val convertedDoc = DocumentItem(
                title = convertedTitle,
                content = doc.content,
                docType = when (target) {
                    DocumentFormat.PDF -> DocumentType.PDF
                    DocumentFormat.DOCX -> DocumentType.DOC
                    DocumentFormat.TXT -> DocumentType.TXT
                    DocumentFormat.PPTX -> DocumentType.SLIDE
                    else -> DocumentType.DOC
                },
                currentFormat = target,
                slideCount = doc.slideCount,
                slidesJson = doc.slidesJson,
                authorEmail = doc.authorEmail,
                fileSizeKb = doc.fileSizeKb + 12
            )
            val newId = repo.insertDocument(convertedDoc)
            _conversionSuccessMessage.value = "¡Formato cambiado a ${target.name}! Nuevo archivo disponible en la nube."
            _converterDoc.value = null
        }
    }

    // MUSIC STUDIO SEQUENCER
    private fun initDefaultSequencerTracks() {
        val tracks = listOf(
            SequencerTrack("Kick Drum", "kick", steps = BooleanArray(16) { it % 4 == 0 }),
            SequencerTrack("Snare Drum", "snare", steps = BooleanArray(16) { it == 4 || it == 12 }),
            SequencerTrack("Hi-Hat", "hihat", steps = BooleanArray(16) { it % 2 == 0 }),
            SequencerTrack("Clap FX", "clap", steps = BooleanArray(16) { it == 12 }),
            SequencerTrack("Synth Bass", "bass", steps = BooleanArray(16) { it == 0 || it == 3 || it == 8 || it == 11 }),
            SequencerTrack("Lead Synth", "lead", steps = BooleanArray(16) { it == 2 || it == 6 || it == 10 || it == 14 })
        )
        _sequencerTracks.value = tracks
    }

    fun openAudioProject(project: AudioProject) {
        _activeAudioProject.value = project
        _currentBpm.value = project.bpm
        parsePatternData(project.patternDataJson)
    }

    private fun parsePatternData(json: String) {
        try {
            val obj = JSONObject(json)
            val trackList = mutableListOf<SequencerTrack>()
            val names = listOf(
                "kick" to "Kick Drum",
                "snare" to "Snare Drum",
                "hihat" to "Hi-Hat",
                "clap" to "Clap FX",
                "bass" to "Synth Bass",
                "lead" to "Lead Synth"
            )
            for ((key, displayName) in names) {
                val steps = BooleanArray(16)
                if (obj.has(key)) {
                    val arr = obj.getJSONArray(key)
                    for (i in 0 until minOf(arr.length(), 16)) {
                        steps[i] = arr.optBoolean(i, false)
                    }
                }
                trackList.add(SequencerTrack(displayName, key, steps = steps))
            }
            if (trackList.isNotEmpty()) {
                _sequencerTracks.value = trackList
            }
        } catch (_: Exception) {
            initDefaultSequencerTracks()
        }
    }

    private fun serializePatternData(): String {
        val obj = JSONObject()
        for (track in _sequencerTracks.value) {
            val arr = JSONArray()
            track.steps.forEach { arr.put(it) }
            obj.put(track.soundType, arr)
        }
        return obj.toString()
    }

    fun toggleStep(trackIndex: Int, stepIndex: Int) {
        val list = _sequencerTracks.value.toMutableList()
        if (trackIndex in list.indices) {
            val track = list[trackIndex]
            val newSteps = track.steps.clone()
            newSteps[stepIndex] = !newSteps[stepIndex]
            list[trackIndex] = track.copy(steps = newSteps)
            _sequencerTracks.value = list
            if (newSteps[stepIndex]) {
                AudioSynthEngine.playDrumHit(track.soundType)
            }
        }
    }

    fun toggleMuteTrack(trackIndex: Int) {
        val list = _sequencerTracks.value.toMutableList()
        if (trackIndex in list.indices) {
            val track = list[trackIndex]
            list[trackIndex] = track.copy(isMuted = !track.isMuted)
            _sequencerTracks.value = list
        }
    }

    fun playTrackSoundPreview(track: SequencerTrack) {
        AudioSynthEngine.playDrumHit(track.soundType)
    }

    fun playSynthNote(note: String) {
        val freq = AudioSynthEngine.NOTE_FREQUENCIES[note] ?: 440f
        AudioSynthEngine.playNote(freq)
    }

    fun setBpm(bpm: Int) {
        _currentBpm.value = bpm.coerceIn(60, 200)
    }

    fun togglePlaySequencer() {
        if (_isPlayingSequencer.value) {
            stopSequencer()
        } else {
            startSequencer()
        }
    }

    private fun startSequencer() {
        _isPlayingSequencer.value = true
        sequencerJob?.cancel()
        sequencerJob = viewModelScope.launch {
            var step = _currentStep.value
            while (isActive && _isPlayingSequencer.value) {
                _currentStep.value = step
                // Trigger active sounds on this step
                for (track in _sequencerTracks.value) {
                    if (!track.isMuted && track.steps[step]) {
                        AudioSynthEngine.playDrumHit(track.soundType)
                    }
                }
                step = (step + 1) % 16
                // Calculate step duration: 16th note delay = (60,000 / BPM) / 4
                val stepDelayMs = ((60000.0 / _currentBpm.value) / 4.0).toLong()
                delay(stepDelayMs.coerceAtLeast(40L))
            }
        }
    }

    fun stopSequencer() {
        _isPlayingSequencer.value = false
        sequencerJob?.cancel()
        sequencerJob = null
        _currentStep.value = 0
    }

    fun saveActiveAudioProject(title: String, genre: String) {
        val userEmail = _authUiState.value.currentUser?.email ?: "gonzalez24029@gmail.com"
        val patternJson = serializePatternData()
        val current = _activeAudioProject.value
        viewModelScope.launch {
            if (current != null) {
                val updated = current.copy(
                    title = title,
                    genre = genre,
                    bpm = _currentBpm.value,
                    patternDataJson = patternJson,
                    lastModified = System.currentTimeMillis()
                )
                repo.updateAudioProject(updated)
                _activeAudioProject.value = updated
            } else {
                val newProject = AudioProject(
                    title = title,
                    genre = genre,
                    bpm = _currentBpm.value,
                    patternDataJson = patternJson,
                    authorEmail = userEmail
                )
                val id = repo.insertAudioProject(newProject)
                _activeAudioProject.value = repo.getAudioProjectById(id)
            }
            _conversionSuccessMessage.value = "¡Pista guardada en tu nube de OmniStudio!"
        }
    }

    // CHAT & MESSAGING
    fun loadChannelMessages(channelId: String) {
        _currentChannel.value = channelId
        viewModelScope.launch {
            repo.getMessagesForChannel(channelId).collect { msgs ->
                _chatMessages.value = msgs
            }
        }
    }

    fun onChatInputChanged(text: String) {
        _chatInputText.value = text
    }

    fun sendChatMessage(attachedDoc: DocumentItem? = null, attachedAudio: AudioProject? = null) {
        val text = _chatInputText.value.trim()
        if (text.isEmpty() && attachedDoc == null && attachedAudio == null) return

        val user = _authUiState.value.currentUser
        val senderName = user?.displayName ?: "Alexis González"
        val senderEmail = user?.email ?: "gonzalez24029@gmail.com"

        val msg = ChatMessage(
            channelId = _currentChannel.value,
            senderName = senderName,
            senderEmail = senderEmail,
            text = text.ifEmpty {
                if (attachedDoc != null) "Compartí un documento: ${attachedDoc.title}"
                else "Compartí una pista musical: ${attachedAudio?.title}"
            },
            attachedDocId = attachedDoc?.id,
            attachedDocTitle = attachedDoc?.title,
            attachedAudioId = attachedAudio?.id,
            attachedAudioTitle = attachedAudio?.title
        )

        viewModelScope.launch {
            repo.insertChatMessage(msg)
            _chatInputText.value = ""

            // Simulated teammate reply in channel after brief pause
            delay(1200)
            val replyText = when {
                attachedDoc != null -> "¡Recibido! Estoy revisando el documento '${attachedDoc.title}' ahora mismo."
                attachedAudio != null -> "¡Vaya ritmo! Acabo de escuchar '${attachedAudio.title}', suena muy potente."
                text.contains("?") -> "Revisé la sincronización en la nube y todo está en orden."
                else -> "Genial, continuemos con la producción del proyecto."
            }
            repo.insertChatMessage(
                ChatMessage(
                    channelId = _currentChannel.value,
                    senderName = "Sofia Martinez",
                    senderEmail = "sofia.m@cloud.io",
                    text = replyText,
                    reactions = "👍"
                )
            )
        }
    }

    fun addReactionToMessage(msg: ChatMessage, emoji: String) {
        val currentReactions = if (msg.reactions.isEmpty()) emoji else "${msg.reactions},$emoji"
        viewModelScope.launch {
            repo.updateChatMessage(msg.copy(reactions = currentReactions))
        }
    }

    fun clearFeedbackMessage() {
        _conversionSuccessMessage.value = null
        _authUiState.value = _authUiState.value.copy(authFeedbackMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        stopSequencer()
    }
}

package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiMusicComposer
import com.example.ai.AiSongResult
import com.example.audio.AudioSynthEngine
import com.example.data.firebase.ChannelInfo
import com.example.data.firebase.ChatNotificationManager
import com.example.data.firebase.FcmTokenManager
import com.example.data.firebase.FirestoreChatService
import com.example.data.firebase.FirestoreConnectionStatus
import com.example.data.firebase.GroupMember
import com.example.data.firebase.PresenceUser
import com.example.data.local.AppDatabase
import com.example.data.model.AudioProject
import com.example.data.model.CallSession
import com.example.data.model.CallStatus
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
    val isSolo: Boolean = false,
    val steps: BooleanArray = BooleanArray(16) { false }
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SequencerTrack
        if (name != other.name) return false
        if (soundType != other.soundType) return false
        if (isMuted != other.isMuted) return false
        if (isSolo != other.isSolo) return false
        if (!steps.contentEquals(other.steps)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + soundType.hashCode()
        result = 31 * result + isMuted.hashCode()
        result = 31 * result + isSolo.hashCode()
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
    val publicAudioProjects: StateFlow<List<AudioProject>>
    val allUsers: StateFlow<List<UserAccount>>

    // AI Song Creator State
    private val _isGeneratingSong = MutableStateFlow(false)
    val isGeneratingSong: StateFlow<Boolean> = _isGeneratingSong.asStateFlow()

    private val _aiGenerationStatus = MutableStateFlow<String?>(null)
    val aiGenerationStatus: StateFlow<String?> = _aiGenerationStatus.asStateFlow()

    private val _lastAiResult = MutableStateFlow<AiSongResult?>(null)
    val lastAiResult: StateFlow<AiSongResult?> = _lastAiResult.asStateFlow()

    // Community Preview & Feedback State
    private val _previewPlayingSongId = MutableStateFlow<Long?>(null)
    val previewPlayingSongId: StateFlow<Long?> = _previewPlayingSongId.asStateFlow()
    private var previewJob: Job? = null

    private val _musicFeedbackMessage = MutableStateFlow<String?>(null)
    val musicFeedbackMessage: StateFlow<String?> = _musicFeedbackMessage.asStateFlow()

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

    private val _isMetronomeEnabled = MutableStateFlow(false)
    val isMetronomeEnabled: StateFlow<Boolean> = _isMetronomeEnabled.asStateFlow()

    private var sequencerJob: Job? = null

    // Chat & Messaging State (Firebase Firestore Real-Time)
    val firestoreChatService = FirestoreChatService(application)
    val firestoreStatus: StateFlow<FirestoreConnectionStatus> = firestoreChatService.connectionStatus
    private val _availableChannels = MutableStateFlow<List<ChannelInfo>>(firestoreChatService.availableChannels)
    val availableChannels: StateFlow<List<ChannelInfo>> = _availableChannels.asStateFlow()

    private val _groupDeletionCountdownSeconds = MutableStateFlow<Map<String, Int>>(emptyMap())
    val groupDeletionCountdownSeconds: StateFlow<Map<String, Int>> = _groupDeletionCountdownSeconds.asStateFlow()
    private val deletionJobs = mutableMapOf<String, Job>()
    private val _permissionsBackup = mutableMapOf<String, Map<String, GroupMember>>()

    private val _currentChannel = MutableStateFlow("general")
    val currentChannel: StateFlow<String> = _currentChannel.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _chatInputText = MutableStateFlow("")
    val chatInputText: StateFlow<String> = _chatInputText.asStateFlow()

    private val _typingUsers = MutableStateFlow<List<String>>(emptyList())
    val typingUsers: StateFlow<List<String>> = _typingUsers.asStateFlow()

    private val _onlineUsers = MutableStateFlow<List<PresenceUser>>(emptyList())
    val onlineUsers: StateFlow<List<PresenceUser>> = _onlineUsers.asStateFlow()

    private val _chatPlayingAudioId = MutableStateFlow<Long?>(null)
    val chatPlayingAudioId: StateFlow<Long?> = _chatPlayingAudioId.asStateFlow()

    private var channelMessagesJob: Job? = null
    private var typingJob: Job? = null
    private var presenceJob: Job? = null
    private var chatAudioJob: Job? = null
    private var typingDebounceJob: Job? = null

    // Format Converter Sheet State
    private val _converterDoc = MutableStateFlow<DocumentItem?>(null)
    val converterDoc: StateFlow<DocumentItem?> = _converterDoc.asStateFlow()

    private val _targetFormat = MutableStateFlow(DocumentFormat.PDF)
    val targetFormat: StateFlow<DocumentFormat> = _targetFormat.asStateFlow()

    private val _conversionSuccessMessage = MutableStateFlow<String?>(null)
    val conversionSuccessMessage: StateFlow<String?> = _conversionSuccessMessage.asStateFlow()

    // Auto-Save System (Firestore Cloud Sync)
    private val _docAutoSaveStatus = MutableStateFlow("Guardado automático activo")
    val docAutoSaveStatus: StateFlow<String> = _docAutoSaveStatus.asStateFlow()

    private val _isDocSaving = MutableStateFlow(false)
    val isDocSaving: StateFlow<Boolean> = _isDocSaving.asStateFlow()

    private var docAutoSaveJob: Job? = null
    private var docDirty = false

    private val _musicAutoSaveStatus = MutableStateFlow("Auto-guardado activo")
    val musicAutoSaveStatus: StateFlow<String> = _musicAutoSaveStatus.asStateFlow()

    private val _isMusicSaving = MutableStateFlow(false)
    val isMusicSaving: StateFlow<Boolean> = _isMusicSaving.asStateFlow()

    private var musicAutoSaveJob: Job? = null
    private var musicDirty = false

    // Calling System (Audio & Video Calling)
    private val _activeCall = MutableStateFlow<CallSession?>(null)
    val activeCall: StateFlow<CallSession?> = _activeCall.asStateFlow()
    private var callTimerJob: Job? = null

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

        publicAudioProjects = repo.publicAudioProjects.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        allUsers = repo.allUsers.stateIn(
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

        // Bucle periódico de sincronización automática con Firestore (cada 8 segundos si hay cambios pendientes)
        viewModelScope.launch {
            while (isActive) {
                delay(8000)
                if (docDirty) {
                    triggerAutoSaveDocument()
                }
                if (musicDirty) {
                    triggerAutoSaveMusic()
                }
            }
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
        scheduleDocAutoSave()
    }

    fun updateCurrentDocContent(newContent: String) {
        _currentEditingDoc.value = _currentEditingDoc.value?.copy(content = newContent, lastModified = System.currentTimeMillis())
        scheduleDocAutoSave()
    }

    private fun scheduleDocAutoSave() {
        docDirty = true
        docAutoSaveJob?.cancel()
        docAutoSaveJob = viewModelScope.launch {
            delay(1200)
            triggerAutoSaveDocument()
        }
    }

    fun triggerAutoSaveDocument() {
        val doc = _currentEditingDoc.value ?: return
        viewModelScope.launch {
            _isDocSaving.value = true
            _docAutoSaveStatus.value = "Sincronizando con Firestore..."
            repo.updateDocument(doc)
            val fsId = firestoreChatService.syncDocument(doc)
            val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            if (!fsId.isNullOrBlank()) {
                _currentEditingDoc.value = _currentEditingDoc.value?.copy(
                    firestoreId = fsId,
                    lastSyncedFirestore = System.currentTimeMillis()
                )
                _docAutoSaveStatus.value = "Sincronizado con Firestore ($timeStr)"
            } else {
                _docAutoSaveStatus.value = "Guardado en caché local ($timeStr)"
            }
            docDirty = false
            _isDocSaving.value = false
        }
    }

    fun saveCurrentDocument() {
        triggerAutoSaveDocument()
        _conversionSuccessMessage.value = "Documento guardado y sincronizado con Firestore correctamente"
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
            scheduleDocAutoSave()
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
                scheduleDocAutoSave()
            }
        } catch (_: Exception) {}
    }

    fun updateSlideImage(slideIndex: Int, imageUrl: String?) {
        val doc = _currentEditingDoc.value ?: return
        try {
            val jsonArray = JSONArray(doc.slidesJson)
            if (slideIndex in 0 until jsonArray.length()) {
                val slideObj = jsonArray.getJSONObject(slideIndex)
                if (imageUrl.isNullOrBlank()) {
                    slideObj.remove("imageUrl")
                } else {
                    slideObj.put("imageUrl", imageUrl)
                }
                val updatedDoc = doc.copy(slidesJson = jsonArray.toString(), lastModified = System.currentTimeMillis())
                _currentEditingDoc.value = updatedDoc
                scheduleDocAutoSave()
            }
        } catch (_: Exception) {}
    }

    fun updateSlideTable(slideIndex: Int, tableData: String?) {
        val doc = _currentEditingDoc.value ?: return
        try {
            val jsonArray = JSONArray(doc.slidesJson)
            if (slideIndex in 0 until jsonArray.length()) {
                val slideObj = jsonArray.getJSONObject(slideIndex)
                if (tableData.isNullOrBlank()) {
                    slideObj.remove("tableData")
                } else {
                    slideObj.put("tableData", tableData)
                }
                val updatedDoc = doc.copy(slidesJson = jsonArray.toString(), lastModified = System.currentTimeMillis())
                _currentEditingDoc.value = updatedDoc
                scheduleDocAutoSave()
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

    private fun scheduleMusicAutoSave() {
        musicDirty = true
        musicAutoSaveJob?.cancel()
        musicAutoSaveJob = viewModelScope.launch {
            delay(1500)
            triggerAutoSaveMusic()
        }
    }

    fun triggerAutoSaveMusic() {
        val tracks = _sequencerTracks.value
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            _isMusicSaving.value = true
            _musicAutoSaveStatus.value = "Sincronizando con Firestore..."
            val patternJson = serializePatternData()
            val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val current = _activeAudioProject.value

            val projectToSave = if (current != null) {
                current.copy(
                    bpm = _currentBpm.value,
                    patternDataJson = patternJson,
                    lastModified = System.currentTimeMillis()
                )
            } else {
                val currentUser = _authUiState.value.currentUser
                AudioProject(
                    title = "Mi Pista de Estudio",
                    genre = "Lo-Fi Hip Hop",
                    bpm = _currentBpm.value,
                    patternDataJson = patternJson,
                    authorEmail = currentUser?.email ?: "gonzalez24029@gmail.com",
                    authorName = currentUser?.displayName ?: "Alex González"
                )
            }

            val savedId = if (projectToSave.id != 0L) {
                repo.updateAudioProject(projectToSave)
                projectToSave.id
            } else {
                repo.insertAudioProject(projectToSave)
            }

            val fsId = firestoreChatService.syncAudioProject(projectToSave.copy(id = savedId))
            val updated = repo.getAudioProjectById(savedId)?.let {
                if (!fsId.isNullOrBlank()) it.copy(firestoreId = fsId, lastSyncedFirestore = System.currentTimeMillis()) else it
            }
            _activeAudioProject.value = updated

            if (!fsId.isNullOrBlank()) {
                _musicAutoSaveStatus.value = "Pista sincronizada en Firestore ($timeStr)"
            } else {
                _musicAutoSaveStatus.value = "Pista guardada localmente ($timeStr)"
            }
            musicDirty = false
            _isMusicSaving.value = false
        }
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
            scheduleMusicAutoSave()
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

    fun toggleSoloTrack(trackIndex: Int) {
        val list = _sequencerTracks.value.toMutableList()
        if (trackIndex in list.indices) {
            val track = list[trackIndex]
            val newSolo = !track.isSolo
            list[trackIndex] = track.copy(isSolo = newSolo)
            _sequencerTracks.value = list
        }
    }

    fun clearTrack(trackIndex: Int) {
        val list = _sequencerTracks.value.toMutableList()
        if (trackIndex in list.indices) {
            val track = list[trackIndex]
            list[trackIndex] = track.copy(steps = BooleanArray(16) { false })
            _sequencerTracks.value = list
        }
    }

    fun fillTrackEvery(trackIndex: Int, interval: Int) {
        val list = _sequencerTracks.value.toMutableList()
        if (trackIndex in list.indices) {
            val track = list[trackIndex]
            val newSteps = BooleanArray(16) { i -> i % interval == 0 }
            list[trackIndex] = track.copy(steps = newSteps)
            _sequencerTracks.value = list
        }
    }

    fun clearAllSteps() {
        val list = _sequencerTracks.value.map { track ->
            track.copy(steps = BooleanArray(16) { false })
        }
        _sequencerTracks.value = list
    }

    fun loadPatternPreset(presetName: String) {
        when (presetName.uppercase()) {
            "LOFI", "LO-FI" -> {
                _currentBpm.value = 85
                val tracks = listOf(
                    SequencerTrack("Kick Drum", "kick", steps = BooleanArray(16) { it == 0 || it == 10 }),
                    SequencerTrack("Snare Drum", "snare", steps = BooleanArray(16) { it == 4 || it == 12 }),
                    SequencerTrack("Hi-Hat", "hihat", steps = BooleanArray(16) { it % 2 == 0 }),
                    SequencerTrack("Clap FX", "clap", steps = BooleanArray(16) { it == 12 }),
                    SequencerTrack("Synth Bass", "bass", steps = BooleanArray(16) { it == 0 || it == 3 || it == 8 || it == 10 }),
                    SequencerTrack("Lead Synth", "lead", steps = BooleanArray(16) { it == 2 || it == 6 || it == 11 || it == 14 })
                )
                _sequencerTracks.value = tracks
            }
            "TRAP" -> {
                _currentBpm.value = 140
                val tracks = listOf(
                    SequencerTrack("Kick Drum", "kick", steps = BooleanArray(16) { it == 0 || it == 7 || it == 10 }),
                    SequencerTrack("Snare Drum", "snare", steps = BooleanArray(16) { it == 8 }),
                    SequencerTrack("Hi-Hat", "hihat", steps = BooleanArray(16) { true }),
                    SequencerTrack("Clap FX", "clap", steps = BooleanArray(16) { it == 4 || it == 12 }),
                    SequencerTrack("Synth Bass", "bass", steps = BooleanArray(16) { it == 0 || it == 3 || it == 6 || it == 10 }),
                    SequencerTrack("Lead Synth", "lead", steps = BooleanArray(16) { it == 0 || it == 6 || it == 12 })
                )
                _sequencerTracks.value = tracks
            }
            "HOUSE" -> {
                _currentBpm.value = 124
                val tracks = listOf(
                    SequencerTrack("Kick Drum", "kick", steps = BooleanArray(16) { it % 4 == 0 }),
                    SequencerTrack("Snare Drum", "snare", steps = BooleanArray(16) { it == 4 || it == 12 }),
                    SequencerTrack("Hi-Hat", "hihat", steps = BooleanArray(16) { it == 2 || it == 6 || it == 10 || it == 14 }),
                    SequencerTrack("Clap FX", "clap", steps = BooleanArray(16) { it == 4 || it == 12 }),
                    SequencerTrack("Synth Bass", "bass", steps = BooleanArray(16) { it == 2 || it == 6 || it == 10 || it == 14 }),
                    SequencerTrack("Lead Synth", "lead", steps = BooleanArray(16) { it == 0 || it == 3 || it == 8 || it == 11 })
                )
                _sequencerTracks.value = tracks
            }
            "BOOMBAP", "BOOM BAP" -> {
                _currentBpm.value = 92
                val tracks = listOf(
                    SequencerTrack("Kick Drum", "kick", steps = BooleanArray(16) { it == 0 || it == 3 || it == 8 || it == 11 }),
                    SequencerTrack("Snare Drum", "snare", steps = BooleanArray(16) { it == 4 || it == 12 }),
                    SequencerTrack("Hi-Hat", "hihat", steps = BooleanArray(16) { it % 2 == 0 }),
                    SequencerTrack("Clap FX", "clap", steps = BooleanArray(16) { it == 12 }),
                    SequencerTrack("Synth Bass", "bass", steps = BooleanArray(16) { it == 0 || it == 3 || it == 8 || it == 11 }),
                    SequencerTrack("Lead Synth", "lead", steps = BooleanArray(16) { it == 4 || it == 8 || it == 12 })
                )
                _sequencerTracks.value = tracks
            }
            "EMPTY" -> {
                clearAllSteps()
            }
        }
    }

    fun randomizePattern() {
        val tracks = listOf(
            SequencerTrack("Kick Drum", "kick", steps = BooleanArray(16) { it == 0 || (it in 6..12 && kotlin.random.Random.nextFloat() > 0.65f) }),
            SequencerTrack("Snare Drum", "snare", steps = BooleanArray(16) { it == 4 || it == 12 || (kotlin.random.Random.nextFloat() > 0.85f) }),
            SequencerTrack("Hi-Hat", "hihat", steps = BooleanArray(16) { it % 2 == 0 || kotlin.random.Random.nextFloat() > 0.5f }),
            SequencerTrack("Clap FX", "clap", steps = BooleanArray(16) { it == 4 || it == 12 }),
            SequencerTrack("Synth Bass", "bass", steps = BooleanArray(16) { it == 0 || it == 8 || (kotlin.random.Random.nextFloat() > 0.75f) }),
            SequencerTrack("Lead Synth", "lead", steps = BooleanArray(16) { kotlin.random.Random.nextFloat() > 0.75f })
        )
        _sequencerTracks.value = tracks
        scheduleMusicAutoSave()
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
        scheduleMusicAutoSave()
    }

    fun adjustBpm(delta: Int) {
        _currentBpm.value = (_currentBpm.value + delta).coerceIn(60, 200)
        scheduleMusicAutoSave()
    }

    fun toggleMetronome() {
        _isMetronomeEnabled.value = !_isMetronomeEnabled.value
    }

    fun rewindSequencer() {
        _currentStep.value = 0
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
                // Metrónomo en cada tiempo de negra (pasos 0, 4, 8, 12)
                if (_isMetronomeEnabled.value && step % 4 == 0) {
                    AudioSynthEngine.playMetronomeClick(isDownbeat = step == 0)
                }

                val tracks = _sequencerTracks.value
                val hasSolo = tracks.any { it.isSolo }

                // Disparo de sonidos activos en este paso
                for (track in tracks) {
                    val shouldPlay = if (hasSolo) {
                        track.isSolo && track.steps[step]
                    } else {
                        !track.isMuted && track.steps[step]
                    }
                    if (shouldPlay) {
                        AudioSynthEngine.playDrumHit(track.soundType)
                    }
                }
                step = (step + 1) % 16
                // Duración del paso (semicorchea): (60,000 / BPM) / 4
                val stepDelayMs = ((60000.0 / _currentBpm.value) / 4.0).toLong()
                delay(stepDelayMs.coerceAtLeast(35L))
            }
        }
    }

    fun stopSequencer() {
        _isPlayingSequencer.value = false
        sequencerJob?.cancel()
        sequencerJob = null
        _currentStep.value = 0
    }

    fun clearMusicFeedback() {
        _musicFeedbackMessage.value = null
    }

    fun isSongOwner(song: AudioProject): Boolean {
        val currentUser = _authUiState.value.currentUser
        val userEmail = currentUser?.email?.trim() ?: ""
        val userName = currentUser?.displayName?.trim() ?: ""
        val userLogin = currentUser?.username?.trim() ?: ""

        if (userEmail.isNotEmpty() && userEmail.equals(song.authorEmail.trim(), ignoreCase = true)) return true
        if (userName.isNotEmpty() && userName.equals(song.authorName.trim(), ignoreCase = true)) return true
        if (userLogin.isNotEmpty() && userLogin.equals(song.authorName.trim(), ignoreCase = true)) return true
        return false
    }

    fun generateSongWithAi(title: String, description: String, onFinished: (() -> Unit)? = null) {
        if (_isGeneratingSong.value) return
        _isGeneratingSong.value = true
        _aiGenerationStatus.value = "Analizando estilo musical y estructura con IA..."

        viewModelScope.launch {
            try {
                stopPlayPreview()
                stopSequencer()
                delay(300)
                _aiGenerationStatus.value = "Sintetizando compases, bajo y percusión..."
                val result = AiMusicComposer.generateSong(title, description)
                _lastAiResult.value = result

                _aiGenerationStatus.value = "Cargando en el secuenciador..."
                _currentBpm.value = result.bpm
                _sequencerTracks.value = result.tracks

                val currentUser = _authUiState.value.currentUser
                val userEmail = currentUser?.email ?: "gonzalez24029@gmail.com"
                val userName = currentUser?.displayName ?: "Alex González"

                val newProject = AudioProject(
                    title = result.title,
                    description = result.description,
                    genre = result.genre,
                    bpm = result.bpm,
                    patternDataJson = result.patternJson,
                    authorEmail = userEmail,
                    authorName = userName,
                    isPublic = false,
                    aiPrompt = description,
                    notesMelody = result.melodyNotes.joinToString(", ")
                )
                val newId = repo.insertAudioProject(newProject)
                val savedProject = repo.getAudioProjectById(newId)
                _activeAudioProject.value = savedProject

                _musicFeedbackMessage.value = "¡Canción '${result.title}' creada con IA lista para escuchar!"
                onFinished?.invoke()
            } catch (e: Exception) {
                _musicFeedbackMessage.value = "Error al generar canción: ${e.message}"
            } finally {
                _isGeneratingSong.value = false
                _aiGenerationStatus.value = null
            }
        }
    }

    fun publishSong(songId: Long, isPublic: Boolean) {
        viewModelScope.launch {
            val song = repo.getAudioProjectById(songId) ?: return@launch
            if (!isSongOwner(song)) {
                _musicFeedbackMessage.value = "Solo el autor original (${song.authorName}) puede publicar o despublicar esta canción."
                return@launch
            }
            repo.updateAudioProjectPublicStatus(songId, isPublic)
            if (_activeAudioProject.value?.id == songId) {
                _activeAudioProject.value = _activeAudioProject.value?.copy(isPublic = isPublic)
            }
            _musicFeedbackMessage.value = if (isPublic) {
                "¡Canción '${song.title}' publicada! Ahora todos en la comunidad pueden verla y escucharla."
            } else {
                "Canción retirada de la comunidad (ahora es privada)."
            }
        }
    }

    fun publishActiveSong(isPublic: Boolean) {
        val active = _activeAudioProject.value ?: return
        publishSong(active.id, isPublic)
    }

    fun renamePublicSong(songId: Long, newTitle: String): Boolean {
        val clean = newTitle.trim()
        if (clean.isEmpty()) return false
        viewModelScope.launch {
            val song = repo.getAudioProjectById(songId)
            if (song != null) {
                if (isSongOwner(song)) {
                    repo.updateAudioProjectTitle(songId, clean)
                    if (_activeAudioProject.value?.id == songId) {
                        _activeAudioProject.value = _activeAudioProject.value?.copy(title = clean)
                    }
                    _musicFeedbackMessage.value = "Nombre actualizado a '$clean'"
                } else {
                    _musicFeedbackMessage.value = "No tienes permiso. Solo el creador original (${song.authorName}) puede cambiar el nombre."
                }
            }
        }
        return true
    }

    fun deleteSongIfOwner(songId: Long, onDeleted: (() -> Unit)? = null) {
        viewModelScope.launch {
            val song = repo.getAudioProjectById(songId)
            if (song != null) {
                if (isSongOwner(song)) {
                    repo.deleteAudioProject(songId)
                    if (_activeAudioProject.value?.id == songId) {
                        _activeAudioProject.value = null
                        stopSequencer()
                    }
                    if (_previewPlayingSongId.value == songId) {
                        stopPlayPreview()
                    }
                    _musicFeedbackMessage.value = "Canción '${song.title}' eliminada exitosamente."
                    onDeleted?.invoke()
                } else {
                    _musicFeedbackMessage.value = "No puedes borrar esta canción. Solo quien la publicó (${song.authorName}) puede borrarla."
                }
            }
        }
    }

    fun togglePlayPreview(song: AudioProject) {
        if (_previewPlayingSongId.value == song.id) {
            stopPlayPreview()
            return
        }
        stopPlayPreview()
        stopSequencer()
        _previewPlayingSongId.value = song.id

        previewJob = viewModelScope.launch {
            val json = song.patternDataJson
            val bpm = song.bpm.coerceIn(60, 200)
            val stepDelayMs = (60_000L / bpm) / 4L

            val kicks = BooleanArray(16)
            val snares = BooleanArray(16)
            val hihats = BooleanArray(16)
            val claps = BooleanArray(16)
            val basses = BooleanArray(16)
            val leads = BooleanArray(16)

            try {
                val obj = JSONObject(json)
                fun parseArr(key: String, dest: BooleanArray) {
                    if (obj.has(key)) {
                        val arr = obj.getJSONArray(key)
                        for (i in 0 until minOf(arr.length(), 16)) {
                            dest[i] = arr.optBoolean(i, false)
                        }
                    }
                }
                parseArr("kick", kicks)
                parseArr("snare", snares)
                parseArr("hihat", hihats)
                parseArr("clap", claps)
                parseArr("bass", basses)
                parseArr("lead", leads)
            } catch (_: Exception) {
                kicks[0] = true; kicks[4] = true; kicks[8] = true; kicks[12] = true
                hihats[2] = true; hihats[6] = true; hihats[10] = true; hihats[14] = true
            }

            var step = 0
            while (isActive && _previewPlayingSongId.value == song.id) {
                if (kicks[step]) AudioSynthEngine.playDrumHit("kick")
                if (snares[step]) AudioSynthEngine.playDrumHit("snare")
                if (hihats[step]) AudioSynthEngine.playDrumHit("hihat")
                if (claps[step]) AudioSynthEngine.playDrumHit("clap")
                if (basses[step]) AudioSynthEngine.playNote(130.81f, 0.2f)
                if (leads[step]) AudioSynthEngine.playNote(523.25f, 0.15f)

                delay(stepDelayMs)
                step = (step + 1) % 16
            }
        }
    }

    fun stopPlayPreview() {
        previewJob?.cancel()
        previewJob = null
        _previewPlayingSongId.value = null
    }

    fun saveActiveAudioProject(title: String, genre: String, description: String? = null, isPublic: Boolean? = null) {
        val currentUser = _authUiState.value.currentUser
        val userEmail = currentUser?.email ?: "gonzalez24029@gmail.com"
        val userName = currentUser?.displayName ?: "Alex González"
        val patternJson = serializePatternData()
        val current = _activeAudioProject.value
        viewModelScope.launch {
            if (current != null) {
                val updated = current.copy(
                    title = title,
                    genre = genre,
                    description = description ?: current.description,
                    bpm = _currentBpm.value,
                    patternDataJson = patternJson,
                    isPublic = isPublic ?: current.isPublic,
                    lastModified = System.currentTimeMillis()
                )
                repo.updateAudioProject(updated)
                _activeAudioProject.value = updated
            } else {
                val newProject = AudioProject(
                    title = title,
                    genre = genre,
                    description = description ?: "",
                    bpm = _currentBpm.value,
                    patternDataJson = patternJson,
                    authorEmail = userEmail,
                    authorName = userName,
                    isPublic = isPublic ?: false
                )
                val id = repo.insertAudioProject(newProject)
                _activeAudioProject.value = repo.getAudioProjectById(id)
            }
            _conversionSuccessMessage.value = "¡Pista guardada en tu nube de OmniStudio!"
        }
    }

    // CHAT & MESSAGING (Firebase Firestore Real-Time)
    fun loadChannelMessages(channelId: String) {
        _currentChannel.value = channelId
        channelMessagesJob?.cancel()
        typingJob?.cancel()
        presenceJob?.cancel()

        val currentUserEmail = _authUiState.value.currentUser?.email ?: "gonzalez24029@gmail.com"
        val currentUserName = _authUiState.value.currentUser?.displayName ?: "Alex González"

        // 1. Cargar mensajes locales en Room para respuesta instantánea inmediata
        viewModelScope.launch {
            repo.getMessagesForChannel(channelId).collect { localMsgs ->
                // Si aún no tenemos mensajes de Firestore o estamos cargando, mostrar locales
                if (_chatMessages.value.isEmpty() || firestoreStatus.value != FirestoreConnectionStatus.CONNECTED_REALTIME) {
                    _chatMessages.value = localMsgs
                }
            }
        }

        // 2. Escuchar en tiempo real desde Firebase Firestore
        channelMessagesJob = viewModelScope.launch {
            firestoreChatService.listenToChannelMessages(channelId).collect { firestoreMsgs ->
                if (firestoreMsgs.isNotEmpty()) {
                    _chatMessages.value = firestoreMsgs
                    // Guardar en Room para persistencia local offline
                    repo.insertChatMessages(firestoreMsgs)
                    // Marcar mensajes recibidos como vistos en Firestore
                    viewModelScope.launch {
                        firestoreChatService.markChannelMessagesAsSeen(channelId, currentUserEmail)
                    }
                }
            }
        }

        // 3. Escuchar indicadores de escritura en tiempo real
        typingJob = viewModelScope.launch {
            firestoreChatService.listenToTyping(channelId, currentUserEmail).collect { typers ->
                _typingUsers.value = typers
            }
        }

        // 4. Presencia de colaboradores activos en el canal
        presenceJob = viewModelScope.launch {
            firestoreChatService.listenToPresence(channelId).collect { users ->
                _onlineUsers.value = users
            }
        }

        // Enviar latido de presencia inicial
        viewModelScope.launch {
            firestoreChatService.updatePresence(channelId, currentUserEmail, currentUserName)
        }
    }

    fun onChatInputChanged(text: String) {
        _chatInputText.value = text
        val user = _authUiState.value.currentUser
        val userEmail = user?.email ?: "gonzalez24029@gmail.com"
        val userName = user?.displayName ?: "Alex González"
        val channelId = _currentChannel.value

        typingDebounceJob?.cancel()
        typingDebounceJob = viewModelScope.launch {
            if (text.isNotBlank()) {
                firestoreChatService.setTypingStatus(channelId, userEmail, userName, true)
                delay(3500)
                firestoreChatService.setTypingStatus(channelId, userEmail, userName, false)
            } else {
                firestoreChatService.setTypingStatus(channelId, userEmail, userName, false)
            }
        }
    }

    fun sendChatMessage(attachedDoc: DocumentItem? = null, attachedAudio: AudioProject? = null) {
        val text = _chatInputText.value.trim()
        if (text.isEmpty() && attachedDoc == null && attachedAudio == null) return

        val user = _authUiState.value.currentUser
        val senderName = user?.displayName ?: "Alex González"
        val senderEmail = user?.email ?: "gonzalez24029@gmail.com"
        val channelId = _currentChannel.value

        // Validación de permisos de grupo
        val currentCh = _availableChannels.value.firstOrNull { it.id == channelId }
        if (currentCh != null && currentCh.isGroup) {
            val member = currentCh.members.firstOrNull { it.email == senderEmail }
            if (member != null) {
                if (!member.canSendMessages) {
                    Log.w("OmniViewModel", "User $senderEmail cannot send messages in $channelId")
                    return
                }
                if ((attachedDoc != null || attachedAudio != null) && !member.canSendMedia) {
                    Log.w("OmniViewModel", "User $senderEmail cannot send media attachments in $channelId")
                    return
                }
            }
        }

        // Cancelar estado de escritura
        typingDebounceJob?.cancel()
        viewModelScope.launch {
            firestoreChatService.setTypingStatus(channelId, senderEmail, senderName, false)
        }

        val now = System.currentTimeMillis()
        val msg = ChatMessage(
            channelId = channelId,
            senderName = senderName,
            senderEmail = senderEmail,
            text = text.ifEmpty {
                if (attachedDoc != null) "Compartí un documento: ${attachedDoc.title}"
                else "Compartí una pista musical: ${attachedAudio?.title}"
            },
            timestamp = now,
            attachedDocId = attachedDoc?.id,
            attachedDocTitle = attachedDoc?.title,
            attachedAudioId = attachedAudio?.id,
            attachedAudioTitle = attachedAudio?.title,
            isSyncedFirestore = false,
            deliveryStatus = "enviando",
            sentTimestamp = now
        )

        _chatInputText.value = ""

        viewModelScope.launch {
            // Guardar localmente en Room primero para cero latencia
            val localId = repo.insertChatMessage(msg)
            val initialMsg = msg.copy(id = localId)
            _chatMessages.value = (_chatMessages.value + initialMsg).distinctBy { if (it.firestoreId.isNotBlank()) it.firestoreId else it.id.toString() }

            // Publicar en Firebase Firestore en tiempo real (Estado: Enviado)
            val firestoreId = firestoreChatService.sendMessage(initialMsg.copy(deliveryStatus = "enviado", isSyncedFirestore = true))
            val sentMsg = initialMsg.copy(
                firestoreId = firestoreId ?: "",
                isSyncedFirestore = !firestoreId.isNullOrBlank(),
                deliveryStatus = "enviado"
            )
            repo.updateChatMessage(sentMsg)
            _chatMessages.value = _chatMessages.value.map { if (it.id == localId) sentMsg else it }

            // Transición a 'entregado' cuando llega al servidor y otros nodos
            delay(600)
            val deliveredTime = System.currentTimeMillis()
            val deliveredMsg = sentMsg.copy(
                deliveryStatus = "entregado",
                deliveredTimestamp = deliveredTime
            )
            repo.updateChatMessage(deliveredMsg)
            _chatMessages.value = _chatMessages.value.map { if (it.id == localId) deliveredMsg else it }
            if (deliveredMsg.firestoreId.isNotBlank()) {
                firestoreChatService.updateMessageDeliveryStatus(channelId, deliveredMsg.firestoreId, "entregado")
            }

            // Colaboración en vivo de compañeros en canales directos o demostraciones
            if (channelId.startsWith("directo-") || text.contains("?") || attachedAudio != null || attachedDoc != null) {
                delay(1200)
                val isCarlos = channelId == "directo-carlos"
                val teammateName = if (isCarlos) "Carlos Mendoza" else "Sofia Martinez"
                val teammateEmail = if (isCarlos) "carlos.m@cloud.io" else "sofia.m@cloud.io"

                // El compañero lee el mensaje: transición a 'visto' (doble check azul)
                val seenTime = System.currentTimeMillis()
                val seenMsg = deliveredMsg.copy(
                    deliveryStatus = "visto",
                    seenTimestamp = seenTime,
                    seenBy = teammateEmail
                )
                repo.updateChatMessage(seenMsg)
                _chatMessages.value = _chatMessages.value.map { if (it.id == localId) seenMsg else it }
                if (seenMsg.firestoreId.isNotBlank()) {
                    firestoreChatService.updateMessageDeliveryStatus(channelId, seenMsg.firestoreId, "visto", teammateEmail)
                }

                delay(900)
                val replyText = when {
                    attachedDoc != null -> "¡Recibido! Revisando '${attachedDoc.title}' en tiempo real."
                    attachedAudio != null -> "¡Qué buen ritmo! Escuché '${attachedAudio.title}', suena excelente."
                    text.contains("?") -> "Revisé la sincronización con Firestore y todo está activo."
                    channelId == "directo-sofia" -> "¡Hola Alex! Estoy terminando las pistas en Music Studio."
                    channelId == "directo-carlos" -> "¡Hola! Estoy revisando los documentos del proyecto."
                    else -> "¡Recibido en tiempo real por el equipo!"
                }
                val replyMsg = ChatMessage(
                    channelId = channelId,
                    senderName = teammateName,
                    senderEmail = teammateEmail,
                    text = replyText,
                    reactions = "👍,✨",
                    isSyncedFirestore = true,
                    deliveryStatus = "visto",
                    sentTimestamp = System.currentTimeMillis(),
                    deliveredTimestamp = System.currentTimeMillis(),
                    seenTimestamp = System.currentTimeMillis()
                )
                val replyLocalId = repo.insertChatMessage(replyMsg)
                firestoreChatService.sendMessage(replyMsg.copy(id = replyLocalId))
            }
        }
    }

    fun addReactionToMessage(msg: ChatMessage, emoji: String) {
        val currentReactions = if (msg.reactions.isEmpty()) emoji else "${msg.reactions},$emoji"
        viewModelScope.launch {
            repo.updateChatMessage(msg.copy(reactions = currentReactions))
            if (msg.firestoreId.isNotBlank()) {
                firestoreChatService.addReaction(msg.channelId, msg.firestoreId, emoji, msg.reactions)
            }
        }
    }

    fun deleteChatMessage(msg: ChatMessage) {
        val currentUserEmail = _authUiState.value.currentUser?.email ?: "gonzalez24029@gmail.com"
        if (msg.senderEmail != currentUserEmail) return

        viewModelScope.launch {
            repo.deleteChatMessage(msg.id)
            if (msg.firestoreId.isNotBlank()) {
                firestoreChatService.deleteMessage(msg.channelId, msg.firestoreId)
                repo.deleteChatMessageByFirestoreId(msg.firestoreId)
            }
            _chatMessages.value = _chatMessages.value.filter {
                it.id != msg.id && (it.firestoreId.isBlank() || it.firestoreId != msg.firestoreId)
            }
        }
    }

    // GESTIÓN AVANZADA DE CANALES, CHAT PRIVADO, GRUPOS Y PERMISOS

    fun startDirectChat(peerEmail: String, peerName: String, peerAvatar: String = "") {
        val cleanId = "directo-" + peerEmail.replace("@", "-at-").replace(".", "-")
        val existing = _availableChannels.value.firstOrNull {
            it.id == cleanId || (it.isDirect && it.name.equals(peerName, ignoreCase = true))
        }
        if (existing != null) {
            loadChannelMessages(existing.id)
            return
        }

        val newDirect = ChannelInfo(
            id = cleanId,
            name = peerName,
            description = "Chat privado con $peerName",
            iconEmoji = "💬",
            isDirect = true,
            groupPhotoUrl = peerAvatar
        )
        _availableChannels.value = _availableChannels.value + newDirect
        loadChannelMessages(cleanId)
    }

    fun createGroupChannel(
        name: String,
        description: String,
        photoUrl: String,
        memberEmails: List<String>
    ) {
        val user = _authUiState.value.currentUser
        val ownerEmail = user?.email ?: "gonzalez24029@gmail.com"
        val ownerName = user?.displayName ?: "Alex González"
        val ownerAvatar = user?.avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200&q=80"

        val ownerMember = GroupMember(
            email = ownerEmail,
            name = ownerName,
            role = "owner",
            canSendMessages = true,
            canSendMedia = true,
            canInviteMembers = true,
            avatarUrl = ownerAvatar
        )

        val allAvailableUsers = allUsers.value
        val otherMembers = memberEmails.filter { it != ownerEmail }.map { email ->
            val u = allAvailableUsers.firstOrNull { it.email == email }
            GroupMember(
                email = email,
                name = u?.displayName ?: email.substringBefore("@"),
                role = "member",
                canSendMessages = true,
                canSendMedia = true,
                canInviteMembers = true,
                avatarUrl = u?.avatarUrl ?: ""
            )
        }

        val allGroupMembers = listOf(ownerMember) + otherMembers
        val newGroupId = "grupo_" + System.currentTimeMillis()
        val newGroup = ChannelInfo(
            id = newGroupId,
            name = name.trim(),
            description = description.ifBlank { "Grupo de trabajo: ${name.trim()}" },
            iconEmoji = "👥",
            isGroup = true,
            groupPhotoUrl = photoUrl,
            creatorEmail = ownerEmail,
            creatorName = ownerName,
            members = allGroupMembers
        )

        _availableChannels.value = _availableChannels.value + newGroup
        sendSystemChatMessage(newGroupId, "$ownerName creó el grupo '$name' con ${allGroupMembers.size} participantes.")
        loadChannelMessages(newGroupId)
    }

    fun addMemberToGroup(channelId: String, userEmail: String, userName: String = "", userAvatar: String = "") {
        val ch = _availableChannels.value.firstOrNull { it.id == channelId } ?: return
        if (ch.members.any { it.email == userEmail }) return
        val finalName = if (userName.isNotBlank()) userName else (allUsers.value.firstOrNull { it.email == userEmail }?.displayName ?: userEmail.substringBefore("@"))
        val finalAvatar = if (userAvatar.isNotBlank()) userAvatar else (allUsers.value.firstOrNull { it.email == userEmail }?.avatarUrl ?: "")

        val newMember = GroupMember(
            email = userEmail,
            name = finalName,
            role = "member",
            canSendMessages = true,
            canSendMedia = true,
            canInviteMembers = true,
            avatarUrl = finalAvatar
        )
        val updatedMembers = ch.members + newMember
        _availableChannels.value = _availableChannels.value.map {
            if (it.id == channelId) it.copy(members = updatedMembers) else it
        }
        sendSystemChatMessage(channelId, "$finalName fue añadido al grupo.")
    }

    fun removeMemberFromGroup(channelId: String, memberEmail: String) {
        val ch = _availableChannels.value.firstOrNull { it.id == channelId } ?: return
        val currentEmail = _authUiState.value.currentUser?.email ?: "gonzalez24029@gmail.com"
        if (ch.creatorEmail != currentEmail) return
        if (memberEmail == ch.creatorEmail) return

        val member = ch.members.firstOrNull { it.email == memberEmail }
        val updatedMembers = ch.members.filter { it.email != memberEmail }
        _availableChannels.value = _availableChannels.value.map {
            if (it.id == channelId) it.copy(members = updatedMembers) else it
        }
        sendSystemChatMessage(channelId, "${member?.name ?: memberEmail} fue retirado del grupo por el creador.")
    }

    fun updateMemberPermissions(
        channelId: String,
        memberEmail: String,
        canSendMessages: Boolean,
        canSendMedia: Boolean,
        canInviteMembers: Boolean = true
    ) {
        val ch = _availableChannels.value.firstOrNull { it.id == channelId } ?: return
        val currentEmail = _authUiState.value.currentUser?.email ?: "gonzalez24029@gmail.com"
        if (ch.creatorEmail != currentEmail) return

        val existingMember = ch.members.firstOrNull { it.email == memberEmail } ?: return

        // Guardar copia previa para opción de deshacer en cualquier momento
        val backupMap = _permissionsBackup.getOrPut(channelId) { mutableMapOf() }.toMutableMap()
        backupMap[memberEmail] = existingMember
        _permissionsBackup[channelId] = backupMap

        val updatedMembers = ch.members.map {
            if (it.email == memberEmail) {
                it.copy(
                    canSendMessages = canSendMessages,
                    canSendMedia = canSendMedia,
                    canInviteMembers = canInviteMembers
                )
            } else it
        }
        _availableChannels.value = _availableChannels.value.map {
            if (it.id == channelId) it.copy(members = updatedMembers) else it
        }
        sendSystemChatMessage(channelId, "El creador actualizó los permisos de ${existingMember.name}.")
    }

    fun undoOrResetMemberPermissions(channelId: String, memberEmail: String) {
        val ch = _availableChannels.value.firstOrNull { it.id == channelId } ?: return
        val currentEmail = _authUiState.value.currentUser?.email ?: "gonzalez24029@gmail.com"
        if (ch.creatorEmail != currentEmail) return

        val existingMember = ch.members.firstOrNull { it.email == memberEmail } ?: return
        val previous = _permissionsBackup[channelId]?.get(memberEmail)

        val restored = previous ?: existingMember.copy(
            canSendMessages = true,
            canSendMedia = true,
            canInviteMembers = true
        )

        val updatedMembers = ch.members.map {
            if (it.email == memberEmail) restored else it
        }
        _availableChannels.value = _availableChannels.value.map {
            if (it.id == channelId) it.copy(members = updatedMembers) else it
        }
        sendSystemChatMessage(channelId, "El creador deshizo las restricciones y restauró los permisos de ${existingMember.name}.")
    }

    fun leaveGroup(channelId: String) {
        val ch = _availableChannels.value.firstOrNull { it.id == channelId } ?: return
        val user = _authUiState.value.currentUser
        val userEmail = user?.email ?: "gonzalez24029@gmail.com"
        val userName = user?.displayName ?: "Alex González"

        val updatedMembers = ch.members.filter { it.email != userEmail }
        _availableChannels.value = _availableChannels.value.map {
            if (it.id == channelId) it.copy(members = updatedMembers) else it
        }
        sendSystemChatMessage(channelId, "$userName abandonó el grupo.")
        loadChannelMessages("general")
    }

    fun scheduleGroupDeletion(channelId: String) {
        val ch = _availableChannels.value.firstOrNull { it.id == channelId } ?: return
        val userEmail = _authUiState.value.currentUser?.email ?: "gonzalez24029@gmail.com"
        if (ch.creatorEmail != userEmail) return

        val totalSeconds = 180 // 3 minutos de advertencia
        val targetTimestamp = System.currentTimeMillis() + (totalSeconds * 1000L)

        _availableChannels.value = _availableChannels.value.map {
            if (it.id == channelId) it.copy(isDeleting = true, pendingDeletionTimestamp = targetTimestamp) else it
        }
        _groupDeletionCountdownSeconds.value = _groupDeletionCountdownSeconds.value + (channelId to totalSeconds)

        sendSystemChatMessage(
            channelId,
            "⚠️ ADVERTENCIA: El creador ha programado la eliminación permanente de este grupo en 3 minutos. Todos los datos y mensajes se destruirán."
        )

        deletionJobs[channelId]?.cancel()
        deletionJobs[channelId] = viewModelScope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _groupDeletionCountdownSeconds.value = _groupDeletionCountdownSeconds.value + (channelId to remaining)
            }
            deleteGroupPermanently(channelId)
        }
    }

    fun cancelGroupDeletion(channelId: String) {
        val ch = _availableChannels.value.firstOrNull { it.id == channelId } ?: return
        val userEmail = _authUiState.value.currentUser?.email ?: "gonzalez24029@gmail.com"
        if (ch.creatorEmail != userEmail) return

        deletionJobs[channelId]?.cancel()
        deletionJobs.remove(channelId)
        _groupDeletionCountdownSeconds.value = _groupDeletionCountdownSeconds.value - channelId

        _availableChannels.value = _availableChannels.value.map {
            if (it.id == channelId) it.copy(isDeleting = false, pendingDeletionTimestamp = null) else it
        }
        sendSystemChatMessage(channelId, "✅ Eliminación cancelada: El dueño ha cancelado la cuenta regresiva y conservado el grupo.")
    }

    fun deleteGroupPermanently(channelId: String) {
        deletionJobs[channelId]?.cancel()
        deletionJobs.remove(channelId)
        _groupDeletionCountdownSeconds.value = _groupDeletionCountdownSeconds.value - channelId

        _availableChannels.value = _availableChannels.value.filter { it.id != channelId }

        viewModelScope.launch {
            repo.deleteMessagesForChannel(channelId)
        }

        if (_currentChannel.value == channelId) {
            loadChannelMessages("general")
        }
    }

    fun sendSystemChatMessage(channelId: String, text: String) {
        val msg = ChatMessage(
            channelId = channelId,
            senderName = "Sistema OmniStudio",
            senderEmail = "system@omnistudio.io",
            text = text,
            timestamp = System.currentTimeMillis(),
            isSyncedFirestore = true,
            deliveryStatus = "visto"
        )
        viewModelScope.launch {
            val localId = repo.insertChatMessage(msg)
            _chatMessages.value = _chatMessages.value + msg.copy(id = localId)
            firestoreChatService.sendMessage(msg.copy(id = localId))
        }
    }

    fun togglePlayChatAudio(audioId: Long) {
        if (_chatPlayingAudioId.value == audioId) {
            chatAudioJob?.cancel()
            _chatPlayingAudioId.value = null
            return
        }

        chatAudioJob?.cancel()
        _chatPlayingAudioId.value = audioId

        chatAudioJob = viewModelScope.launch {
            val song = repo.getAudioProjectById(audioId)
            if (song == null) {
                _chatPlayingAudioId.value = null
                return@launch
            }

            try {
                val json = JSONObject(song.patternDataJson)
                val kickArray = json.optJSONArray("kick") ?: JSONArray()
                val snareArray = json.optJSONArray("snare") ?: JSONArray()
                val hihatArray = json.optJSONArray("hihat") ?: JSONArray()
                val clapArray = json.optJSONArray("clap") ?: JSONArray()
                val bassArray = json.optJSONArray("bass") ?: JSONArray()
                val leadArray = json.optJSONArray("lead") ?: JSONArray()

                val kicks = BooleanArray(16) { i -> kickArray.optBoolean(i, false) }
                val snares = BooleanArray(16) { i -> snareArray.optBoolean(i, false) }
                val hihats = BooleanArray(16) { i -> hihatArray.optBoolean(i, false) }
                val claps = BooleanArray(16) { i -> clapArray.optBoolean(i, false) }
                val basses = BooleanArray(16) { i -> bassArray.optBoolean(i, false) }
                val leads = BooleanArray(16) { i -> leadArray.optBoolean(i, false) }

                val bpm = song.bpm.coerceIn(60, 200)
                val stepDelayMs = (60_000L / bpm) / 4

                // Reproducir 2 compases (32 pasos) en vivo
                var stepsRemaining = 32
                var step = 0
                while (isActive && stepsRemaining > 0 && _chatPlayingAudioId.value == audioId) {
                    if (kicks[step]) AudioSynthEngine.playDrumHit("kick")
                    if (snares[step]) AudioSynthEngine.playDrumHit("snare")
                    if (hihats[step]) AudioSynthEngine.playDrumHit("hihat")
                    if (claps[step]) AudioSynthEngine.playDrumHit("clap")
                    if (basses[step]) AudioSynthEngine.playNote(130.81f, 0.2f)
                    if (leads[step]) AudioSynthEngine.playNote(523.25f, 0.15f)

                    delay(stepDelayMs)
                    step = (step + 1) % 16
                    stepsRemaining--
                }
            } catch (e: Exception) {
                Log.e("OmniViewModel", "Error previewing chat audio: ${e.message}")
            } finally {
                if (_chatPlayingAudioId.value == audioId) {
                    _chatPlayingAudioId.value = null
                }
            }
        }
    }

    // RICH MEDIA MESSAGING (Fotos, Videos, GIFs)
    fun sendMediaMessage(mediaType: String, mediaUrl: String, caption: String = "") {
        val user = _authUiState.value.currentUser
        val senderName = user?.displayName ?: "Alex González"
        val senderEmail = user?.email ?: "gonzalez24029@gmail.com"
        val channelId = _currentChannel.value

        val fallbackText = when (mediaType) {
            "image" -> if (caption.isNotBlank()) caption else "📷 Foto adjunta"
            "video" -> if (caption.isNotBlank()) caption else "🎥 Video adjunto"
            "gif" -> if (caption.isNotBlank()) caption else "🎭 GIF animado"
            else -> caption
        }

        val now = System.currentTimeMillis()
        val msg = ChatMessage(
            channelId = channelId,
            senderName = senderName,
            senderEmail = senderEmail,
            text = fallbackText,
            timestamp = now,
            mediaType = mediaType,
            mediaUrl = mediaUrl,
            isSyncedFirestore = false,
            deliveryStatus = "enviando",
            sentTimestamp = now
        )

        viewModelScope.launch {
            val localId = repo.insertChatMessage(msg)
            val initialMsg = msg.copy(id = localId)
            _chatMessages.value = (_chatMessages.value + initialMsg).distinctBy { if (it.firestoreId.isNotBlank()) it.firestoreId else it.id.toString() }

            val firestoreId = firestoreChatService.sendMessage(initialMsg.copy(deliveryStatus = "enviado", isSyncedFirestore = true))
            val sentMsg = initialMsg.copy(
                firestoreId = firestoreId ?: "",
                isSyncedFirestore = !firestoreId.isNullOrBlank(),
                deliveryStatus = "enviado"
            )
            repo.updateChatMessage(sentMsg)
            _chatMessages.value = _chatMessages.value.map { if (it.id == localId) sentMsg else it }

            // Transición a entregado
            delay(500)
            val deliveredTime = System.currentTimeMillis()
            val deliveredMsg = sentMsg.copy(
                deliveryStatus = "entregado",
                deliveredTimestamp = deliveredTime
            )
            repo.updateChatMessage(deliveredMsg)
            _chatMessages.value = _chatMessages.value.map { if (it.id == localId) deliveredMsg else it }
            if (deliveredMsg.firestoreId.isNotBlank()) {
                firestoreChatService.updateMessageDeliveryStatus(channelId, deliveredMsg.firestoreId, "entregado")
            }

            // Simular respuesta de compañero en canales directos o general
            if (channelId.startsWith("directo-") || channelId == "general") {
                delay(1000)
                val teammateName = if (channelId == "directo-carlos") "Carlos Mendoza" else "Sofia Martinez"
                val teammateEmail = if (channelId == "directo-carlos") "carlos.m@cloud.io" else "sofia.m@cloud.io"

                // Visto por el compañero
                val seenTime = System.currentTimeMillis()
                val seenMsg = deliveredMsg.copy(
                    deliveryStatus = "visto",
                    seenTimestamp = seenTime,
                    seenBy = teammateEmail
                )
                repo.updateChatMessage(seenMsg)
                _chatMessages.value = _chatMessages.value.map { if (it.id == localId) seenMsg else it }
                if (seenMsg.firestoreId.isNotBlank()) {
                    firestoreChatService.updateMessageDeliveryStatus(channelId, seenMsg.firestoreId, "visto", teammateEmail)
                }

                delay(900)
                val replyReaction = when (mediaType) {
                    "image" -> "¡Excelente captura! Se ve muy bien."
                    "video" -> "¡Genial el clip de video! Lo estoy reproduciendo."
                    "gif" -> "😂 ¡Buenísimo el GIF!"
                    else -> "¡Recibido!"
                }
                val reply = ChatMessage(
                    channelId = channelId,
                    senderName = teammateName,
                    senderEmail = teammateEmail,
                    text = replyReaction,
                    isSyncedFirestore = true,
                    deliveryStatus = "visto",
                    sentTimestamp = System.currentTimeMillis(),
                    deliveredTimestamp = System.currentTimeMillis(),
                    seenTimestamp = System.currentTimeMillis()
                )
                val repId = repo.insertChatMessage(reply)
                firestoreChatService.sendMessage(reply.copy(id = repId))
            }
        }
    }

    // CALLING SYSTEM (Audio & Video Calling)
    fun startVoiceCall(peerName: String = "Sofia Martínez", peerEmail: String = "sofia.m@cloud.io", channelId: String? = null) {
        val chId = channelId ?: _currentChannel.value
        val callId = "call_${System.currentTimeMillis()}"
        val session = CallSession(
            callId = callId,
            channelId = chId,
            peerName = peerName,
            peerEmail = peerEmail,
            isVideo = false,
            status = CallStatus.RINGING
        )
        _activeCall.value = session

        viewModelScope.launch {
            firestoreChatService.startCallSignal(session)
            delay(1500)
            if (_activeCall.value?.callId == callId) {
                _activeCall.value = _activeCall.value?.copy(status = CallStatus.CONNECTED)
                startCallTimer()
            }
        }
    }

    fun startVideoCall(peerName: String = "Sofia Martínez", peerEmail: String = "sofia.m@cloud.io", channelId: String? = null) {
        val chId = channelId ?: _currentChannel.value
        val callId = "call_${System.currentTimeMillis()}"
        val session = CallSession(
            callId = callId,
            channelId = chId,
            peerName = peerName,
            peerEmail = peerEmail,
            isVideo = true,
            status = CallStatus.RINGING
        )
        _activeCall.value = session

        viewModelScope.launch {
            firestoreChatService.startCallSignal(session)
            delay(1800)
            if (_activeCall.value?.callId == callId) {
                _activeCall.value = _activeCall.value?.copy(status = CallStatus.CONNECTED)
                startCallTimer()
            }
        }
    }

    private fun startCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (isActive && _activeCall.value?.status == CallStatus.CONNECTED) {
                delay(1000)
                _activeCall.value = _activeCall.value?.let { it.copy(durationSeconds = it.durationSeconds + 1) }
            }
        }
    }

    fun toggleCallMute() {
        _activeCall.value = _activeCall.value?.let { it.copy(isMuted = !it.isMuted) }
    }

    fun toggleCallCamera() {
        _activeCall.value = _activeCall.value?.let { it.copy(isCameraOn = !it.isCameraOn) }
    }

    fun toggleCallSpeaker() {
        _activeCall.value = _activeCall.value?.let { it.copy(isSpeakerOn = !it.isSpeakerOn) }
    }

    fun switchCallCamera() {
        _activeCall.value = _activeCall.value?.let { it.copy(isFrontCamera = !it.isFrontCamera) }
    }

    fun endActiveCall() {
        val call = _activeCall.value ?: return
        callTimerJob?.cancel()
        callTimerJob = null
        val duration = call.durationSeconds
        val chId = call.channelId
        val callId = call.callId
        val isVideo = call.isVideo
        val peer = call.peerName
        _activeCall.value = null

        viewModelScope.launch {
            firestoreChatService.endCallSignal(chId, callId)
            val minutes = duration / 60
            val seconds = duration % 60
            val durationFormatted = String.format("%02d:%02d", minutes, seconds)
            val summaryText = if (isVideo) {
                "📹 Videollamada finalizada • $durationFormatted con $peer"
            } else {
                "📞 Llamada de voz finalizada • $durationFormatted con $peer"
            }

            val user = _authUiState.value.currentUser
            val senderName = user?.displayName ?: "Alex González"
            val senderEmail = user?.email ?: "gonzalez24029@gmail.com"

            val callMsg = ChatMessage(
                channelId = chId,
                senderName = senderName,
                senderEmail = senderEmail,
                text = summaryText,
                mediaType = if (isVideo) "call_video" else "call_voice",
                callDurationSec = duration,
                isSyncedFirestore = true
            )
            val localId = repo.insertChatMessage(callMsg)
            firestoreChatService.sendMessage(callMsg.copy(id = localId))
        }
    }

    fun clearFeedbackMessage() {
        _conversionSuccessMessage.value = null
        _authUiState.value = _authUiState.value.copy(authFeedbackMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        stopSequencer()
        channelMessagesJob?.cancel()
        typingJob?.cancel()
        presenceJob?.cancel()
        chatAudioJob?.cancel()
        typingDebounceJob?.cancel()
    }
}

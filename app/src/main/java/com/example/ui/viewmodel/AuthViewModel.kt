package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.example.data.firebase.FirestoreChatService
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.example.data.firebase.FirebaseAppProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

/**
 * Estado de interfaz para la autenticación de usuarios.
 */
data class AuthenticationState(
    val isLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val currentUser: FirebaseUser? = null,
    val userEmail: String? = null,
    val userDisplayName: String? = null,
    val userPhotoUrl: String? = null,
    val isAuthenticated: Boolean = false,
    val isRegisterMode: Boolean = false,
    val emailInput: String = "",
    val passwordInput: String = "",
    val nameInput: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isForgotPasswordDialogOpen: Boolean = false,
    val passwordResetSent: Boolean = false
)

/**
 * Authentication ViewModel para gestionar Firebase Auth:
 * - Inicio de sesión y registro con Correo y Contraseña
 * - Google Sign-In mediante Android Credential Manager y Firebase Credential
 * - Restablecimiento de contraseña por correo
 * - Observación continua del estado del usuario
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "AuthViewModel"

    private val _uiState = MutableStateFlow(AuthenticationState())
    val uiState: StateFlow<AuthenticationState> = _uiState.asStateFlow()

    private val auth: FirebaseAuth? by lazy {
        try {
            val app = FirebaseAppProvider.get(getApplication())
            FirebaseAuth.getInstance(app)
        } catch (e: Exception) {
            Log.w(TAG, "Firebase no está inicializado o falta google-services.json: ${e.message}")
            null
        }
    }

    private val firestoreChatService by lazy { FirestoreChatService(getApplication()) }

    init {
        // Escuchar cambios de autenticación de Firebase si está disponible
        try {
            auth?.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                Log.d(TAG, "AuthStateListener trigger: user is ${user?.email}")
                if (user != null) {
                    _uiState.update {
                        it.copy(
                            currentUser = user,
                            userEmail = user.email,
                            userDisplayName = user.displayName ?: user.email?.substringBefore("@"),
                            userPhotoUrl = user.photoUrl?.toString(),
                            isAuthenticated = true
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            currentUser = null,
                            userEmail = null,
                            userDisplayName = null,
                            userPhotoUrl = null,
                            isAuthenticated = false
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo registrar AuthStateListener: ${e.message}")
        }
    }

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(emailInput = email, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(passwordInput = password, errorMessage = null) }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(nameInput = name, errorMessage = null) }
    }

    fun toggleAuthMode() {
        _uiState.update {
            it.copy(
                isRegisterMode = !it.isRegisterMode,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun openForgotPasswordDialog() {
        _uiState.update {
            it.copy(
                isForgotPasswordDialogOpen = true,
                passwordResetSent = false,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun closeForgotPasswordDialog() {
        _uiState.update { it.copy(isForgotPasswordDialogOpen = false) }
    }

    /**
     * Iniciar sesión con Correo y Contraseña
     */
    fun signInWithEmail() {
        val email = _uiState.value.emailInput.trim()
        val password = _uiState.value.passwordInput.trim()

        if (email.isEmpty() || password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Por favor completa el correo y la contraseña") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        val firebaseAuth = auth
        if (firebaseAuth != null) {
            viewModelScope.launch {
                try {
                    val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                    val user = result.user
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentUser = user,
                            userEmail = user?.email,
                            userDisplayName = user?.displayName ?: email.substringBefore("@"),
                            isAuthenticated = true,
                            successMessage = "¡Bienvenido de nuevo, ${user?.displayName ?: email}!"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en signInWithEmailAndPassword", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = formatAuthError(e)
                        )
                    }
                }
            }
        } else {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Error: Firebase no está configurado correctamente.") }
        }
    }

    /**
     * Registro con Correo y Contraseña
     */
    fun signUpWithEmail() {
        val email = _uiState.value.emailInput.trim()
        val password = _uiState.value.passwordInput.trim()
        val name = _uiState.value.nameInput.trim()

        if (email.isEmpty() || password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Por favor completa el correo y la contraseña") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "La contraseña debe tener al menos 6 caracteres") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        val firebaseAuth = auth
        if (firebaseAuth != null) {
            viewModelScope.launch {
                try {
                    val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                    val user = result.user

                    // Actualizar el nombre para mostrar si fue provisto
                    if (name.isNotEmpty() && user != null) {
                        try {
                            val profileUpdates = userProfileChangeRequest {
                                displayName = name
                            }
                            user.updateProfile(profileUpdates).await()
                        } catch (profileError: Exception) {
                            Log.w(TAG, "No se pudo actualizar el nombre del perfil", profileError)
                        }
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentUser = user,
                            userEmail = user?.email,
                            userDisplayName = if (name.isNotEmpty()) name else email.substringBefore("@"),
                            isAuthenticated = true,
                            successMessage = "Cuenta creada exitosamente en Firebase"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en createUserWithEmailAndPassword", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = formatAuthError(e)
                        )
                    }
                }
            }
        } else {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Error: Firebase no está configurado correctamente.") }
        }
    }

    /**
     * Envío de correo de restablecimiento de contraseña
     */
    fun sendPasswordReset(emailInput: String? = null) {
        val email = (emailInput ?: _uiState.value.emailInput).trim()
        if (email.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Ingresa tu correo para restablecer la contraseña") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        val firebaseAuth = auth
        if (firebaseAuth != null) {
            viewModelScope.launch {
                try {
                    firebaseAuth.sendPasswordResetEmail(email).await()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            passwordResetSent = true,
                            successMessage = "Correo de recuperación enviado a $email"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en sendPasswordResetEmail", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = formatAuthError(e)
                        )
                    }
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    passwordResetSent = true,
                    successMessage = "Instrucciones de recuperación enviadas a $email (Simulación)"
                )
            }
        }
    }

    /**
     * Iniciar sesión con Google usando Android Credential Manager
     */
    fun signInWithGoogle(context: Context) {
        _uiState.update { it.copy(isGoogleLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)

                // Generar un nonce seguro para mitigar ataques de repetición
                val rawNonce = UUID.randomUUID().toString()
                val bytes = rawNonce.toByteArray()
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(bytes)
                val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

                // Configurar opción de Google ID Token
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId("611449222458-15tufgtevlao0dtmpuba9thld02ep30s.apps.googleusercontent.com")
                    .setNonce(hashedNonce)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = request,
                    context = context
                )

                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    firebaseAuthWithGoogle(idToken)
                } else {
                    _uiState.update {
                        it.copy(
                            isGoogleLoading = false,
                            errorMessage = "Tipo de credencial no compatible de Google"
                        )
                    }
                }
            } catch (e: GetCredentialCancellationException) {
                // El usuario canceló la selección de cuenta
                _uiState.update { it.copy(isGoogleLoading = false) }
            } catch (e: Exception) {
                Log.w(TAG, "Error en Credential Manager para Google Sign-In: ${e.message}")
                // Si la infraestructura de Google Play Services o Web Client ID no está configurada,
                // ofrecer soporte de autenticación fluida
                val firebaseAuth = auth
                if (firebaseAuth != null) {
                    _uiState.update {
                        it.copy(
                            isGoogleLoading = false,
                            errorMessage = "Google Sign-In: ${e.localizedMessage ?: "Verifica la configuración de Google Play Services"}"
                        )
                    }
                } else {
                    _uiState.update { 
                        it.copy(
                            isGoogleLoading = false,
                            errorMessage = "Error de configuración de Google Sign-In"
                        ) 
                    }
                }
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val firebaseAuth = auth
        if (firebaseAuth != null) {
            viewModelScope.launch {
                try {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    val result = firebaseAuth.signInWithCredential(credential).await()
                    val user = result.user
                    // Guardar perfil en Firestore
                    user?.let {
                        Log.d("AuthViewModel", "--- DEPURACIÓN FIRESTORE ---")
                        Log.d("AuthViewModel", "Usuario: ${it.email}")
                        Log.d("AuthViewModel", "Servicio: $firestoreChatService")
                        
                        // Forzar una llamada directa a Firestore para verificar la instancia
                        try {
                            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            Log.d("AuthViewModel", "Instancia Firestore obtenida: ${db.app.options.projectId}")
                        } catch (e: Exception) {
                            Log.e("AuthViewModel", "Error obteniendo instancia Firestore: ${e.message}")
                        }

                        firestoreChatService.saveUserProfileToCloud(
                            email = it.email ?: "",
                            displayName = it.displayName ?: "Usuario",
                            avatarUrl = it.photoUrl?.toString() ?: ""
                        ).also { success ->
                            Log.d("AuthViewModel", "Resultado FINAL de guardar perfil: $success")
                        }
                    }

                    _uiState.update {
                        it.copy(
                            isGoogleLoading = false,
                            currentUser = user,
                            userEmail = user?.email,
                            userDisplayName = user?.displayName ?: "Usuario Google",
                            userPhotoUrl = user?.photoUrl?.toString(),
                            isAuthenticated = true,
                            successMessage = "¡Sesión iniciada con Google!"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en signInWithCredential", e)
                    _uiState.update {
                        it.copy(
                            isGoogleLoading = false,
                            errorMessage = formatAuthError(e)
                        )
                    }
                }
            }
        } else {
            _uiState.update { it.copy(isGoogleLoading = false, errorMessage = "Error de configuración de Firebase Auth") }
        }
    }

    // Funciones de simulación eliminadas para prevenir auto-login con cuenta de desarrollador

    /**
     * Cerrar sesión
     */
    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Error al cerrar sesión en Firebase", e)
        }
        _uiState.update {
            AuthenticationState(
                isAuthenticated = false,
                successMessage = "Sesión cerrada"
            )
        }
    }

    private fun formatAuthError(exception: Exception): String {
        val message = exception.localizedMessage ?: exception.message ?: "Error de autenticación"
        return when {
            message.contains("user-not-found", ignoreCase = true) -> "No existe ninguna cuenta registrada con este correo"
            message.contains("wrong-password", ignoreCase = true) || message.contains("invalid-credential", ignoreCase = true) -> "Contraseña o credenciales incorrectas"
            message.contains("email-already-in-use", ignoreCase = true) -> "Este correo electrónico ya está registrado. Inicia sesión"
            message.contains("invalid-email", ignoreCase = true) -> "El formato del correo electrónico es inválido"
            message.contains("weak-password", ignoreCase = true) -> "La contraseña debe tener al menos 6 caracteres"
            message.contains("network", ignoreCase = true) -> "Error de red. Comprueba tu conexión a internet"
            else -> message
        }
    }
}

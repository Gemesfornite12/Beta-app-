package com.example.data.translation

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SupportedLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val flagEmoji: String
)

data class MessageTranslationState(
    val messageId: String,
    val originalText: String,
    val detectedLanguageCode: String? = null,
    val detectedLanguageName: String? = null,
    val targetLanguageCode: String = "es",
    val translatedText: String? = null,
    val isTranslating: Boolean = false,
    val isDownloadingModel: Boolean = false,
    val errorMessage: String? = null,
    val showOriginal: Boolean = false
)

data class TranslationSettings(
    val isAutoTranslateEnabled: Boolean = true,
    val targetLanguageCode: String = "es",
    val autoTranslateOutgoing: Boolean = false,
    val outgoingTargetLanguageCode: String = "en"
)

/**
 * Gestor de Detección de Idioma y Traducción Multilingüe On-Device utilizando Google ML Kit.
 * Todo el procesamiento se realiza en el dispositivo para máxima privacidad y baja latencia.
 */
class MultilingualTranslationManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val languageIdentifier: LanguageIdentifier = LanguageIdentification.getClient()

    // Cache de traductores instanciados por par de idiomas "source-target"
    private val translatorCache = ConcurrentHashMap<String, Translator>()

    // Cache reactivo de estados de traducción por mensaje ID
    private val _translationStates = MutableStateFlow<Map<String, MessageTranslationState>>(emptyMap())
    val translationStates: StateFlow<Map<String, MessageTranslationState>> = _translationStates.asStateFlow()

    // Configuración general
    private val _settings = MutableStateFlow(TranslationSettings())
    val settings: StateFlow<TranslationSettings> = _settings.asStateFlow()

    // Idiomas soportados por la suite
    val supportedLanguages = listOf(
        SupportedLanguage("es", "Español", "Español", "🇪🇸"),
        SupportedLanguage("en", "Inglés", "English", "🇺🇸"),
        SupportedLanguage("fr", "Francés", "Français", "🇫🇷"),
        SupportedLanguage("de", "Alemán", "Deutsch", "🇩🇪"),
        SupportedLanguage("it", "Italiano", "Italiano", "🇮🇹"),
        SupportedLanguage("pt", "Portugués", "Português", "🇧🇷"),
        SupportedLanguage("ru", "Ruso", "Русский", "🇷🇺"),
        SupportedLanguage("ja", "Japonés", "日本語", "🇯🇵"),
        SupportedLanguage("zh", "Chino", "中文", "🇨🇳"),
        SupportedLanguage("ar", "Árabe", "العربية", "🇸🇦"),
        SupportedLanguage("ko", "Coreano", "한국어", "🇰🇷"),
        SupportedLanguage("hi", "Hindi", "हिन्दी", "🇮🇳")
    )

    fun getLanguageByCode(code: String): SupportedLanguage? {
        return supportedLanguages.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }

    fun getLanguageDisplayName(code: String): String {
        return getLanguageByCode(code)?.displayName ?: code.uppercase()
    }

    fun getLanguageFlag(code: String): String {
        return getLanguageByCode(code)?.flagEmoji ?: "🌐"
    }

    fun setAutoTranslateEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(isAutoTranslateEnabled = enabled)
    }

    fun setTargetLanguage(targetLangCode: String) {
        if (_settings.value.targetLanguageCode != targetLangCode) {
            _settings.value = _settings.value.copy(targetLanguageCode = targetLangCode)
            // Invalidar traducciones existentes que apunten a otro target
            val currentMap = _translationStates.value.toMutableMap()
            currentMap.forEach { (key, state) ->
                if (state.targetLanguageCode != targetLangCode) {
                    currentMap[key] = state.copy(
                        targetLanguageCode = targetLangCode,
                        translatedText = null,
                        errorMessage = null
                    )
                }
            }
            _translationStates.value = currentMap
        }
    }

    fun setOutgoingTargetLanguage(targetLangCode: String) {
        _settings.value = _settings.value.copy(outgoingTargetLanguageCode = targetLangCode)
    }

    fun setAutoTranslateOutgoing(enabled: Boolean) {
        _settings.value = _settings.value.copy(autoTranslateOutgoing = enabled)
    }

    fun toggleShowOriginal(messageId: String) {
        val current = _translationStates.value[messageId] ?: return
        val updated = current.copy(showOriginal = !current.showOriginal)
        _translationStates.value = _translationStates.value + (messageId to updated)
    }

    /**
     * Procesa un mensaje: detecta su idioma automáticamente y, si difiere del target,
     * realiza la traducción en el dispositivo usando ML Kit Translate.
     */
    fun processMessage(messageId: String, text: String, force: Boolean = false) {
        if (text.isBlank()) return

        // Ignorar textos que solo son enlaces o emojis breves
        val trimmed = text.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return
        if (trimmed.length < 2 && !trimmed.any { it.isLetter() }) return

        val targetLang = _settings.value.targetLanguageCode
        val existing = _translationStates.value[messageId]

        if (!force && existing != null) {
            if (existing.translatedText != null && existing.targetLanguageCode == targetLang) {
                return // Ya procesado con éxito
            }
            if (existing.isTranslating) return // En curso
        }

        scope.launch {
            try {
                // Estado inicial
                val initialState = existing?.copy(
                    messageId = messageId,
                    originalText = text,
                    targetLanguageCode = targetLang,
                    isTranslating = true,
                    errorMessage = null
                ) ?: MessageTranslationState(
                    messageId = messageId,
                    originalText = text,
                    targetLanguageCode = targetLang,
                    isTranslating = true
                )

                updateMessageState(messageId, initialState)

                // 1. Detección automática del idioma del mensaje
                val detectedCode = try {
                    languageIdentifier.identifyLanguage(text).awaitTask()
                } catch (e: Exception) {
                    Log.w("MultilingualChat", "Error detectando idioma: ${e.message}")
                    "und"
                }

                if (detectedCode == "und" || detectedCode.isBlank()) {
                    // Idioma no determinado de forma fiable
                    updateMessageState(
                        messageId,
                        initialState.copy(
                            isTranslating = false,
                            detectedLanguageCode = "und",
                            detectedLanguageName = "Desconocido"
                        )
                    )
                    return@launch
                }

                val detectedName = getLanguageDisplayName(detectedCode)

                // Si el idioma detectado es igual al idioma destino, no necesita traducción
                if (detectedCode.equals(targetLang, ignoreCase = true)) {
                    updateMessageState(
                        messageId,
                        initialState.copy(
                            isTranslating = false,
                            detectedLanguageCode = detectedCode,
                            detectedLanguageName = detectedName,
                            translatedText = null // Mismo idioma
                        )
                    )
                    return@launch
                }

                // 2. Mapear al código de TranslateLanguage de ML Kit
                val sourceTranslateLang = TranslateLanguage.fromLanguageTag(detectedCode)
                val targetTranslateLang = TranslateLanguage.fromLanguageTag(targetLang)

                if (sourceTranslateLang == null || targetTranslateLang == null) {
                    updateMessageState(
                        messageId,
                        initialState.copy(
                            isTranslating = false,
                            detectedLanguageCode = detectedCode,
                            detectedLanguageName = detectedName,
                            errorMessage = "Par de idiomas no soportado por el motor offline"
                        )
                    )
                    return@launch
                }

                // 3. Obtener o crear traductor offline
                val pairKey = "$sourceTranslateLang->$targetTranslateLang"
                val translator = translatorCache.getOrPut(pairKey) {
                    val options = TranslatorOptions.Builder()
                        .setSourceLanguage(sourceTranslateLang)
                        .setTargetLanguage(targetTranslateLang)
                        .build()
                    Translation.getClient(options)
                }

                // Actualizar estado a descargando modelo si es necesario
                updateMessageState(
                    messageId,
                    initialState.copy(
                        detectedLanguageCode = detectedCode,
                        detectedLanguageName = detectedName,
                        isDownloadingModel = true
                    )
                )

                // Descargar modelo on-device si no está descargado previamente
                val conditions = DownloadConditions.Builder().build()
                translator.downloadModelIfNeeded(conditions).awaitTask()

                // 4. Traducir texto en el dispositivo
                val translated = translator.translate(text).awaitTask()

                updateMessageState(
                    messageId,
                    MessageTranslationState(
                        messageId = messageId,
                        originalText = text,
                        detectedLanguageCode = detectedCode,
                        detectedLanguageName = detectedName,
                        targetLanguageCode = targetLang,
                        translatedText = translated,
                        isTranslating = false,
                        isDownloadingModel = false,
                        errorMessage = null,
                        showOriginal = false
                    )
                )

            } catch (e: Exception) {
                Log.e("MultilingualChat", "Error traduciendo mensaje $messageId: ${e.message}", e)
                updateMessageState(
                    messageId,
                    (existing ?: MessageTranslationState(messageId, text)).copy(
                        isTranslating = false,
                        isDownloadingModel = false,
                        errorMessage = e.localizedMessage ?: "Error al traducir en el dispositivo"
                    )
                )
            }
        }
    }

    /**
     * Traduce un texto arbitrario bajo demanda a un idioma destino dado.
     * Útil para traducir mensajes salientes antes de enviarlos o traducir a un idioma puntual.
     */
    suspend fun translateDirect(
        text: String,
        targetLangCode: String,
        sourceLangCode: String? = null
    ): Result<String> {
        if (text.isBlank()) return Result.success("")

        return try {
            val sourceCode = if (sourceLangCode.isNullOrBlank() || sourceLangCode == "auto") {
                val detected = languageIdentifier.identifyLanguage(text).awaitTask()
                if (detected == "und" || detected.isBlank()) "es" else detected
            } else {
                sourceLangCode
            }

            if (sourceCode.equals(targetLangCode, ignoreCase = true)) {
                return Result.success(text)
            }

            val sourceTranslateLang = TranslateLanguage.fromLanguageTag(sourceCode)
                ?: return Result.failure(IllegalArgumentException("Idioma origen '$sourceCode' no soportado"))
            val targetTranslateLang = TranslateLanguage.fromLanguageTag(targetLangCode)
                ?: return Result.failure(IllegalArgumentException("Idioma destino '$targetLangCode' no soportado"))

            val pairKey = "$sourceTranslateLang->$targetTranslateLang"
            val translator = translatorCache.getOrPut(pairKey) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceTranslateLang)
                    .setTargetLanguage(targetTranslateLang)
                    .build()
                Translation.getClient(options)
            }

            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).awaitTask()

            val translated = translator.translate(text).awaitTask()
            Result.success(translated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun updateMessageState(messageId: String, state: MessageTranslationState) {
        val map = _translationStates.value.toMutableMap()
        map[messageId] = state
        _translationStates.value = map
    }

    fun close() {
        translatorCache.values.forEach { it.close() }
        translatorCache.clear()
        languageIdentifier.close()
    }
}

/**
 * Extensión segura para esperar la compleción de una tarea de Google Play / ML Kit Task
 * suspendiendo la corrutina sin dependencias externas adicionales.
 */
suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) {
            continuation.resume(result)
        }
    }
    addOnFailureListener { exception ->
        if (continuation.isActive) {
            continuation.resumeWithException(exception)
        }
    }
    addOnCanceledListener {
        if (continuation.isActive) {
            continuation.cancel()
        }
    }
}

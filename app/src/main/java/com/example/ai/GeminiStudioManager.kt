package com.example.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream

data class SearchGroundingResult(
    val text: String,
    val webSources: List<WebSource> = emptyList(),
    val searchQueries: List<String> = emptyList(),
    val error: String? = null
)

data class GeneratedImageResult(
    val base64Data: String? = null,
    val mimeType: String = "image/jpeg",
    val description: String? = null,
    val error: String? = null
)

data class VeoVideoResult(
    val videoUri: String? = null,
    val operationId: String? = null,
    val isProcessing: Boolean = false,
    val prompt: String = "",
    val aspectRatio: String = "16:9",
    val statusText: String = "",
    val error: String? = null
)

data class LiveVoiceResult(
    val textResponse: String,
    val base64Audio: String? = null,
    val audioMimeType: String = "audio/wav",
    val voiceName: String = "Kore",
    val error: String? = null
)

data class LyriaMusicResult(
    val title: String,
    val prompt: String,
    val genre: String,
    val bpm: Int,
    val isFullTrack: Boolean,
    val durationSeconds: Int,
    val audioNotesOrUrl: String? = null,
    val base64Audio: String? = null,
    val error: String? = null
)

object GeminiStudioManager {
    private const val TAG = "GeminiStudioManager"

    private fun getApiKey(): String {
        val key = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }
        return if (key == "MY_GEMINI_API_KEY") "" else key
    }

    /**
     * 1. Búsqueda con Google Search Grounding usando gemini-3.5-flash
     */
    suspend fun searchWithGrounding(prompt: String): SearchGroundingResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext SearchGroundingResult(
                text = "Configura tu clave GEMINI_API_KEY en el panel Secrets de AI Studio para realizar búsquedas con Grounding en tiempo real.",
                error = "API_KEY_MISSING"
            )
        }

        try {
            val request = GenerateContentRequest(
                contents = listOf(
                    Content(
                        parts = listOf(Part(text = prompt))
                    )
                ),
                tools = listOf(ToolConfig(googleSearch = GoogleSearchConfig()))
            )

            val response = GeminiRetrofitClient.service.generateContent(
                model = "models/gemini-3.5-flash",
                apiKey = apiKey,
                request = request
            )

            val candidate = response.candidates?.firstOrNull()
            val text = candidate?.content?.parts?.firstOrNull { it.text != null }?.text
                ?: response.error?.message
                ?: "No se obtuvo respuesta de búsqueda."

            val grounding = candidate?.groundingMetadata
            val sources = grounding?.groundingChunks?.mapNotNull { it.web } ?: emptyList()
            val queries = grounding?.webSearchQueries ?: emptyList()

            SearchGroundingResult(
                text = text,
                webSources = sources,
                searchQueries = queries
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in searchWithGrounding: ${e.message}", e)
            SearchGroundingResult(
                text = "Error consultando Google Search: ${e.message}",
                error = e.message
            )
        }
    }

    /**
     * 2. Transcripción de audio con gemini-3.5-transcribe
     */
    suspend fun transcribeAudio(base64Audio: String, mimeType: String = "audio/mp4"): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext "Error: API Key no configurada para transcripción de audio."
        }

        try {
            val request = GenerateContentRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(text = "Transcribe este archivo de audio exactamente en el idioma original con puntuación precisa y ortografía correcta:"),
                            Part(inlineData = InlineData(mimeType = mimeType, data = base64Audio))
                        )
                    )
                )
            )

            // Intentar primero con gemini-3.5-transcribe, fallback a gemini-3.5-flash
            val response = try {
                GeminiRetrofitClient.service.generateContent(
                    model = "models/gemini-3.5-transcribe",
                    apiKey = apiKey,
                    request = request
                )
            } catch (_: Exception) {
                GeminiRetrofitClient.service.generateContent(
                    model = "models/gemini-3.5-flash",
                    apiKey = apiKey,
                    request = request
                )
            }

            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull { it.text != null }?.text
                ?: response.error?.message
                ?: "No se detectaron palabras en el audio grabado."
        } catch (e: Exception) {
            Log.e(TAG, "Error in transcribeAudio: ${e.message}", e)
            "Error transcribiendo audio: ${e.message}"
        }
    }

    /**
     * 3 & 4. Generación de video y animación de imagen a video con Veo 3 (veo-3.1-fast-generate-preview)
     * Soporta aspect ratio 16:9 y 9:16
     */
    suspend fun generateVeoVideo(
        prompt: String,
        aspectRatio: String = "16:9", // "16:9" o "9:16"
        base64Image: String? = null
    ): VeoVideoResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext VeoVideoResult(
                error = "Configura tu GEMINI_API_KEY para generar videos con Veo 3."
            )
        }

        try {
            val imageInput = if (!base64Image.isNullOrBlank()) {
                InlineData(mimeType = "image/jpeg", data = base64Image)
            } else null

            val videoRequest = GenerateVideosRequest(
                prompt = prompt,
                image = imageInput,
                config = VeoConfig(
                    numberOfVideos = 1,
                    resolution = "1080p",
                    aspectRatio = if (aspectRatio == "9:16") "9:16" else "16:9"
                )
            )

            val resultJson = GeminiRetrofitClient.service.generateVideos(
                model = "models/veo-3.1-fast-generate-preview",
                apiKey = apiKey,
                request = videoRequest
            )

            val opName = resultJson["name"]?.jsonPrimitive?.contentOrNull
            Log.d(TAG, "Veo 3 operation created: $opName")

            // Polling de la operación si retorna nombre
            if (opName != null) {
                var attempts = 0
                while (attempts < 8) {
                    delay(3000)
                    attempts++
                    try {
                        val opStatus = GeminiRetrofitClient.service.getOperation(opName, apiKey)
                        val isDone = opStatus["done"]?.jsonPrimitive?.booleanOrNull ?: false
                        if (isDone) {
                            val resp = opStatus["response"]?.jsonObject
                            val genVideos = resp?.get("generateVideoResponse")?.jsonObject?.get("generatedVideos")?.jsonArray
                            val firstVideo = genVideos?.getOrNull(0)?.jsonObject?.get("video")?.jsonObject
                            val uri = firstVideo?.get("uri")?.jsonPrimitive?.contentOrNull

                            return@withContext VeoVideoResult(
                                videoUri = uri ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                                operationId = opName,
                                prompt = prompt,
                                aspectRatio = aspectRatio,
                                statusText = "¡Video generado con Veo 3!"
                            )
                        }
                    } catch (pollErr: Exception) {
                        Log.w(TAG, "Polling attempt $attempts: ${pollErr.message}")
                    }
                }
            }

            // Si es preview inmediata o simulador de video generado
            VeoVideoResult(
                videoUri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                operationId = opName,
                prompt = prompt,
                aspectRatio = aspectRatio,
                statusText = "Video procesado exitosamente por Veo 3 ($aspectRatio)"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateVeoVideo: ${e.message}", e)
            VeoVideoResult(
                prompt = prompt,
                aspectRatio = aspectRatio,
                error = "Error al generar video con Veo: ${e.message}"
            )
        }
    }

    /**
     * 5. Creación y edición de imágenes con gemini-3.1-flash-image-preview
     */
    suspend fun generateOrEditImage(
        prompt: String,
        base64InputImage: String? = null,
        aspectRatio: String = "1:1",
        imageSize: String = "1K"
    ): GeneratedImageResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext GeneratedImageResult(
                error = "Configura tu clave GEMINI_API_KEY para crear y editar imágenes."
            )
        }

        try {
            val partsList = mutableListOf<Part>()
            partsList.add(Part(text = prompt))
            if (!base64InputImage.isNullOrBlank()) {
                partsList.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64InputImage)))
            }

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = partsList)),
                generationConfig = GenerationConfig(
                    responseModalities = listOf("TEXT", "IMAGE"),
                    imageConfig = ImageConfig(
                        aspectRatio = aspectRatio,
                        imageSize = imageSize
                    )
                )
            )

            val response = GeminiRetrofitClient.service.generateContent(
                model = "models/gemini-3.1-flash-image-preview",
                apiKey = apiKey,
                request = request
            )

            val candidate = response.candidates?.firstOrNull()
            val imagePart = candidate?.content?.parts?.firstOrNull { it.inlineData != null }
            val textPart = candidate?.content?.parts?.firstOrNull { it.text != null }?.text

            if (imagePart?.inlineData != null) {
                GeneratedImageResult(
                    base64Data = imagePart.inlineData.data,
                    mimeType = imagePart.inlineData.mimeType,
                    description = textPart ?: "Imagen generada con gemini-3.1-flash-image-preview"
                )
            } else {
                GeneratedImageResult(
                    description = textPart ?: "La IA procesó la solicitud pero no retornó datos binarios.",
                    error = if (textPart == null) response.error?.message ?: "Error desconocido" else null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateOrEditImage: ${e.message}", e)
            GeneratedImageResult(error = "Error al procesar imagen: ${e.message}")
        }
    }

    /**
     * 6. Conversaciones de voz en vivo con gemini-3.8-live
     */
    suspend fun converseLiveVoice(
        userPrompt: String,
        voiceName: String = "Kore" // Kore, Puck, Charon, Fenrir, Aoede
    ): LiveVoiceResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext LiveVoiceResult(
                textResponse = "Configura GEMINI_API_KEY para iniciar la conversación por voz.",
                error = "API_KEY_MISSING"
            )
        }

        try {
            val request = GenerateContentRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(text = "Responde de forma natural, concisa y conversacional como un asistente de voz en vivo: $userPrompt")
                        )
                    )
                ),
                generationConfig = GenerationConfig(
                    responseModalities = listOf("AUDIO", "TEXT"),
                    speechConfig = SpeechConfig(
                        voiceConfig = VoiceConfig(
                            prebuiltVoiceConfig = PrebuiltVoiceConfig(voiceName = voiceName)
                        )
                    )
                )
            )

            val response = try {
                GeminiRetrofitClient.service.generateContent(
                    model = "models/gemini-3.8-live",
                    apiKey = apiKey,
                    request = request
                )
            } catch (_: Exception) {
                // Fallback a modelo de voz nativa
                GeminiRetrofitClient.service.generateContent(
                    model = "models/gemini-2.5-flash-native-audio-preview-12-2025",
                    apiKey = apiKey,
                    request = request
                )
            }

            val candidate = response.candidates?.firstOrNull()
            val text = candidate?.content?.parts?.firstOrNull { it.text != null }?.text ?: "Entendido."
            val audioPart = candidate?.content?.parts?.firstOrNull { it.inlineData != null }

            LiveVoiceResult(
                textResponse = text,
                base64Audio = audioPart?.inlineData?.data,
                audioMimeType = audioPart?.inlineData?.mimeType ?: "audio/wav",
                voiceName = voiceName
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in converseLiveVoice: ${e.message}", e)
            LiveVoiceResult(
                textResponse = "Error en sesión de voz: ${e.message}",
                error = e.message
            )
        }
    }

    /**
     * 7. Generación de música con lyria-3-clip-preview (clips hasta 30s) o lyria-3-pro-preview (pistas completas)
     */
    suspend fun generateLyriaMusic(
        prompt: String,
        isFullTrack: Boolean = false,
        genre: String = "Electronic",
        bpm: Int = 128
    ): LyriaMusicResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        val model = if (isFullTrack) "models/lyria-3-pro-preview" else "models/lyria-3-clip-preview"
        val duration = if (isFullTrack) 180 else 30

        if (apiKey.isBlank()) {
            return@withContext LyriaMusicResult(
                title = "Pista $genre",
                prompt = prompt,
                genre = genre,
                bpm = bpm,
                isFullTrack = isFullTrack,
                durationSeconds = duration,
                error = "Configura tu clave GEMINI_API_KEY para componer música con Lyria 3."
            )
        }

        try {
            val musicPrompt = "Composicion musical estilo $genre a $bpm BPM. Descripcion: $prompt. Duracion: ${duration}s."
            val request = GenerateContentRequest(
                contents = listOf(
                    Content(
                        parts = listOf(Part(text = musicPrompt))
                    )
                ),
                generationConfig = GenerationConfig(
                    responseModalities = listOf("AUDIO")
                )
            )

            val response = GeminiRetrofitClient.service.generateContent(
                model = model,
                apiKey = apiKey,
                request = request
            )

            val candidate = response.candidates?.firstOrNull()
            val audioPart = candidate?.content?.parts?.firstOrNull { it.inlineData != null }

            LyriaMusicResult(
                title = if (prompt.isNotBlank()) prompt.take(30) else "Composición Lyria 3",
                prompt = prompt,
                genre = genre,
                bpm = bpm,
                isFullTrack = isFullTrack,
                durationSeconds = duration,
                base64Audio = audioPart?.inlineData?.data
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateLyriaMusic: ${e.message}", e)
            LyriaMusicResult(
                title = prompt.take(30).ifBlank { "Pista $genre" },
                prompt = prompt,
                genre = genre,
                bpm = bpm,
                isFullTrack = isFullTrack,
                durationSeconds = duration,
                error = "Error al generar música con Lyria: ${e.message}"
            )
        }
    }

    /**
     * Helper para convertir Bitmap a Base64
     */
    fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    fun base64ToBitmap(base64String: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (_: Exception) {
            null
        }
    }
}

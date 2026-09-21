package com.example.data.api

import com.example.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val tools: List<ToolConfig>? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class ToolConfig(
    val googleSearch: GoogleSearchConfig? = null
)

@Serializable
class GoogleSearchConfig

@Serializable
data class Content(
    val parts: List<Part>,
    val role: String? = null
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val maxOutputTokens: Int? = null,
    val responseMimeType: String? = null,
    val responseModalities: List<String>? = null,
    val imageConfig: ImageConfig? = null,
    val speechConfig: SpeechConfig? = null
)

@Serializable
data class ImageConfig(
    val aspectRatio: String? = "1:1",
    val imageSize: String? = "1K"
)

@Serializable
data class SpeechConfig(
    val voiceConfig: VoiceConfig? = null
)

@Serializable
data class VoiceConfig(
    val prebuiltVoiceConfig: PrebuiltVoiceConfig? = null
)

@Serializable
data class PrebuiltVoiceConfig(
    val voiceName: String = "Kore"
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null,
    val error: GeminiError? = null
)

@Serializable
data class Candidate(
    val content: Content? = null,
    val groundingMetadata: GroundingMetadata? = null,
    val finishReason: String? = null
)

@Serializable
data class GroundingMetadata(
    val webSearchQueries: List<String>? = null,
    val searchEntryPoint: SearchEntryPoint? = null,
    val groundingChunks: List<GroundingChunk>? = null
)

@Serializable
data class SearchEntryPoint(
    val renderedContent: String? = null
)

@Serializable
data class GroundingChunk(
    val web: WebSource? = null
)

@Serializable
data class WebSource(
    val uri: String? = null,
    val title: String? = null
)

@Serializable
data class GeminiError(
    val code: Int,
    val message: String,
    val status: String
)

@Serializable
data class ModelsResponse(
    val models: List<GeminiModelInfo>
)

@Serializable
data class GeminiModelInfo(
    val name: String,
    val version: String? = null,
    val displayName: String? = null,
    val description: String? = null,
    val supportedGenerationMethods: List<String>? = null
)

// Veo Video Generation Models
@Serializable
data class GenerateVideosRequest(
    val prompt: String,
    val image: InlineData? = null,
    val config: VeoConfig? = null
)

@Serializable
data class VeoConfig(
    val numberOfVideos: Int? = 1,
    val resolution: String? = "1080p",
    val aspectRatio: String = "16:9"
)

interface GeminiApiService {
    @POST("v1beta/{model}:generateContent")
    suspend fun generateContent(
        @Path(value = "model", encoded = true) model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    @POST("v1beta/{model}:generateVideos")
    suspend fun generateVideos(
        @Path(value = "model", encoded = true) model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateVideosRequest
    ): JsonObject

    @GET("v1beta/{operation}")
    suspend fun getOperation(
        @Path(value = "operation", encoded = true) operation: String,
        @Query("key") apiKey: String
    ): JsonObject

    @GET("v1beta/models")
    suspend fun listModels(
        @Query("key") apiKey: String
    ): ModelsResponse
}

object GeminiRetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private val json = Json { 
        ignoreUnknownKeys = true 
        explicitNulls = false
        encodeDefaults = false
    }

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiApiService::class.java)
    }
}

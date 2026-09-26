package com.example.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.MultipartBody
import retrofit2.http.DELETE
import retrofit2.http.Multipart
import retrofit2.http.Path
import retrofit2.http.Part
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@Serializable
data class SaraRequest(val message: String)

@Serializable
data class SaraReply(
    val text: String? = null,
    @SerialName("recipient_id") val recipientId: String? = null,
    val custom: JsonObject? = null
)

@Serializable
data class SaraTextRequest(val text: String)

@Serializable
data class SaraEventRequest(val event: JsonObject)

@Serializable
data class SaraSearchRequest(val query: String)

@Serializable
data class SaraSearchSource(val title: String, val url: String)

@Serializable
data class SaraSearchResponse(
    val answer: String,
    @SerialName("search_queries") val searchQueries: List<String> = emptyList(),
    val sources: List<SaraSearchSource> = emptyList()
)

@Serializable
data class SaraTriggerIntentRequest(
    val name: String,
    val entities: Map<String, String> = emptyMap()
)

interface SaraApi {
    @POST("webhooks/rest/webhook")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String,
        @Body request: SaraRequest
    ): List<SaraReply>

    @GET("api/rasa/version")
    suspend fun getVersion(@Header("Authorization") authorization: String): JsonObject

    @GET("api/rasa/status")
    suspend fun getStatus(@Header("Authorization") authorization: String): JsonObject

    @GET("api/rasa/domain")
    suspend fun getDomain(@Header("Authorization") authorization: String): JsonObject

    @GET("api/rasa/tracker")
    suspend fun getTracker(
        @Header("Authorization") authorization: String,
        @Query("include_events") includeEvents: String = "AFTER_RESTART",
        @Query("until") until: Double? = null
    ): JsonObject

    @GET("api/rasa/story")
    suspend fun getStory(
        @Header("Authorization") authorization: String,
        @Query("all_sessions") allSessions: Boolean = false,
        @Query("until") until: Double? = null
    ): ResponseBody

    @POST("api/rasa/parse")
    suspend fun parseMessage(
        @Header("Authorization") authorization: String,
        @Body request: SaraTextRequest
    ): JsonObject

    @POST("api/rasa/predict")
    suspend fun predictNextAction(@Header("Authorization") authorization: String): JsonObject

    @POST("api/rasa/reset")
    suspend fun resetConversation(@Header("Authorization") authorization: String): ResponseBody

    @POST("api/rasa/trigger-intent")
    suspend fun triggerIntent(
        @Header("Authorization") authorization: String,
        @Body request: SaraTriggerIntentRequest
    ): JsonObject

    @POST("api/rasa/message")
    suspend fun addMessageToTracker(
        @Header("Authorization") authorization: String,
        @Body request: SaraTextRequest
    ): JsonObject

    @POST("api/rasa/events")
    suspend fun appendEvent(
        @Header("Authorization") authorization: String,
        @Body request: SaraEventRequest
    ): JsonObject

    @POST("api/rasa/search")
    suspend fun searchWeb(
        @Header("Authorization") authorization: String,
        @Body request: SaraSearchRequest
    ): SaraSearchResponse

    @POST("api/felo/livedocs")
    suspend fun createSaraLiveDoc(
        @Header("Authorization") authorization: String,
        @Body request: JsonObject
    ): JsonObject

    @Multipart
    @POST("api/felo/livedocs/resources/upload-doc")
    suspend fun uploadSaraDocument(
        @Header("Authorization") authorization: String,
        @Header("X-Felo-LiveDoc-Ref") docRef: String,
        @Part file: MultipartBody.Part
    ): JsonObject

    @Multipart
    @POST("api/felo/livedocs/resources/upload")
    suspend fun uploadSaraMediaResource(
        @Header("Authorization") authorization: String,
        @Header("X-Felo-LiveDoc-Ref") docRef: String,
        @Part file: MultipartBody.Part
    ): JsonObject

    @GET("api/felo/livedocs/resources/{resourceId}")
    suspend fun getSaraLiveDocResource(
        @Header("Authorization") authorization: String,
        @Header("X-Felo-LiveDoc-Ref") docRef: String,
        @Path("resourceId") resourceId: String
    ): JsonObject

    @POST("api/felo/livedocs/resources/retrieve")
    suspend fun retrieveSaraLiveDocContent(
        @Header("Authorization") authorization: String,
        @Header("X-Felo-LiveDoc-Ref") docRef: String,
        @Body request: JsonObject
    ): JsonObject

    @DELETE("api/felo/livedocs")
    suspend fun deleteSaraLiveDoc(
        @Header("Authorization") authorization: String,
        @Header("X-Felo-LiveDoc-Ref") docRef: String
    ): JsonObject

    @POST("api/felo/llm")
    suspend fun askSaraWithFeloContext(
        @Header("Authorization") authorization: String,
        @Body request: JsonObject
    ): JsonObject
}

object SaraRetrofitClient {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Render Free may need time to wake and Rasa may need to initialize.
        .readTimeout(240, TimeUnit.SECONDS)
        .callTimeout(250, TimeUnit.SECONDS)
        .build()

    val service: SaraApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://rasaserveria.onrender.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SaraApi::class.java)
    }
}

package com.example.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
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
    @SerialName("recipient_id") val recipientId: String? = null
)

@Serializable
data class SaraTextRequest(val text: String)

@Serializable
data class SaraEventRequest(val event: JsonObject)

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

package com.example.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@Serializable
data class SaraRequest(val message: String)

@Serializable
data class SaraReply(
    val text: String? = null,
    @SerialName("recipient_id") val recipientId: String? = null
)

interface SaraApi {
    @POST("webhooks/rest/webhook")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String,
        @Body request: SaraRequest
    ): List<SaraReply>
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

package com.example.data.api

import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
data class OrsGeocodeResponse(
    val features: List<OrsGeocodeFeature> = emptyList()
)

@Serializable
data class OrsGeocodeFeature(
    val geometry: OrsPointGeometry,
    val properties: OrsGeocodeProperties
)

@Serializable
data class OrsPointGeometry(
    val type: String? = null,
    val coordinates: List<Double> = emptyList()
)

@Serializable
data class OrsGeocodeProperties(
    val label: String? = null
)

@Serializable
data class OrsDirectionsResponse(
    val features: List<OrsRouteFeature> = emptyList()
)

@Serializable
data class OrsRouteFeature(
    val geometry: OrsLineGeometry,
    val properties: OrsRouteProperties
)

@Serializable
data class OrsLineGeometry(
    val type: String? = null,
    val coordinates: List<List<Double>> = emptyList()
)

@Serializable
data class OrsRouteProperties(
    val summary: OrsRouteSummary? = null,
    val segments: List<OrsSegment> = emptyList()
)

@Serializable
data class OrsRouteSummary(
    val distance: Double = 0.0,
    val duration: Double = 0.0
)

@Serializable
data class OrsSegment(
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val steps: List<OrsStep> = emptyList()
)

@Serializable
data class OrsStep(
    val instruction: String = "",
    val distance: Double = 0.0,
    val duration: Double = 0.0
)

interface OpenRouteServiceApi {
    @GET("geocode/search")
    suspend fun geocode(
        @Query("api_key") apiKey: String,
        @Query("text") text: String,
        @Query("size") size: Int = 5
    ): OrsGeocodeResponse

    @GET("v2/directions/driving-car")
    suspend fun drivingRoute(
        @Query("api_key") apiKey: String,
        @Query("start") start: String,
        @Query("end") end: String,
        @Query("instructions") instructions: Boolean = true
    ): OrsDirectionsResponse
}

object OpenRouteServiceClient {
    private const val BASE_URL = "https://api.openrouteservice.org/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: OpenRouteServiceApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .asConverterFactory("application/json".toMediaType())
            )
            .build()
            .create(OpenRouteServiceApi::class.java)
    }
}


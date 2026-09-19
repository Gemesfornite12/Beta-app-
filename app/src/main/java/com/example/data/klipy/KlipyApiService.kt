package com.example.data.klipy

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface KlipyApiService {
    @GET("api/v1/{app_key}/gifs/trending")
    suspend fun getTrendingGifs(
        @Path("app_key") apiKey: String,
        @Query("per_page") limit: Int = 25
    ): KlipyResponse

    @GET("api/v1/{app_key}/gifs/search")
    suspend fun searchGifs(
        @Path("app_key") apiKey: String,
        @Query("q") query: String,
        @Query("per_page") limit: Int = 25
    ): KlipyResponse

    @GET("api/v1/{app_key}/stickers/trending")
    suspend fun getTrendingStickers(
        @Path("app_key") apiKey: String,
        @Query("per_page") limit: Int = 25
    ): KlipyResponse

    @GET("api/v1/{app_key}/stickers/search")
    suspend fun searchStickers(
        @Path("app_key") apiKey: String,
        @Query("q") query: String,
        @Query("per_page") limit: Int = 25
    ): KlipyResponse
}

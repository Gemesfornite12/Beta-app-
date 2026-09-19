package com.example.data.giphy

import retrofit2.http.GET
import retrofit2.http.Query

interface GiphyApiService {
    @GET("v1/gifs/trending")
    suspend fun getTrendingGifs(
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 25,
        @Query("rating") rating: String = "g"
    ): GiphyResponse

    @GET("v1/gifs/search")
    suspend fun searchGifs(
        @Query("api_key") apiKey: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 25,
        @Query("rating") rating: String = "g",
        @Query("lang") lang: String = "es"
    ): GiphyResponse

    @GET("v1/stickers/trending")
    suspend fun getTrendingStickers(
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 25,
        @Query("rating") rating: String = "g"
    ): GiphyResponse

    @GET("v1/stickers/search")
    suspend fun searchStickers(
        @Query("api_key") apiKey: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 25,
        @Query("rating") rating: String = "g",
        @Query("lang") lang: String = "es"
    ): GiphyResponse
}

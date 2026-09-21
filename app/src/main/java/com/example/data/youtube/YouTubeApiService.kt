package com.example.data.youtube

import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApiService {

    @GET("youtube/v3/search")
    suspend fun searchVideos(
        @Query("key") apiKey: String,
        @Query("part") part: String = "snippet",
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 20,
        @Query("q") query: String,
        @Query("safeSearch") safeSearch: String = "moderate"
    ): YouTubeSearchResponse

    @GET("youtube/v3/search")
    suspend fun getPopularVideos(
        @Query("key") apiKey: String,
        @Query("part") part: String = "snippet",
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 20,
        @Query("order") order: String = "viewCount",
        @Query("q") query: String = "trending music tech"
    ): YouTubeSearchResponse
}

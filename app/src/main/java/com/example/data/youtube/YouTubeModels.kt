package com.example.data.youtube

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class YouTubeSearchResponse(
    @Json(name = "items") val items: List<YouTubeSearchItem> = emptyList(),
    @Json(name = "nextPageToken") val nextPageToken: String? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeSearchItem(
    @Json(name = "id") val id: YouTubeVideoId? = null,
    @Json(name = "snippet") val snippet: YouTubeSnippet? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeVideoId(
    @Json(name = "kind") val kind: String? = null,
    @Json(name = "videoId") val videoId: String? = null,
    @Json(name = "channelId") val channelId: String? = null,
    @Json(name = "playlistId") val playlistId: String? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeSnippet(
    @Json(name = "publishedAt") val publishedAt: String? = null,
    @Json(name = "channelId") val channelId: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "thumbnails") val thumbnails: YouTubeThumbnails? = null,
    @Json(name = "channelTitle") val channelTitle: String? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeThumbnails(
    @Json(name = "default") val default: YouTubeThumbnail? = null,
    @Json(name = "medium") val medium: YouTubeThumbnail? = null,
    @Json(name = "high") val high: YouTubeThumbnail? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeThumbnail(
    @Json(name = "url") val url: String? = null,
    @Json(name = "width") val width: Int? = null,
    @Json(name = "height") val height: Int? = null
)

data class YouTubeVideo(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val description: String,
    val thumbnailUrl: String,
    val publishedAt: String = "",
    val videoUrl: String = "https://www.youtube.com/watch?v=$videoId"
)

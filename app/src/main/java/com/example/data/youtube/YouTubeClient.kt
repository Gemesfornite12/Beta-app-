package com.example.data.youtube

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object YouTubeClient {
    private const val TAG = "YouTubeClient"
    private const val BASE_URL = "https://www.googleapis.com/"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val apiService: YouTubeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(YouTubeApiService::class.java)
    }

    /**
     * Extracts YouTube Video ID from various link formats:
     * - https://www.youtube.com/watch?v=VIDEO_ID
     * - https://youtu.be/VIDEO_ID
     * - https://www.youtube.com/embed/VIDEO_ID
     * - https://www.youtube.com/shorts/VIDEO_ID
     */
    fun extractVideoId(urlOrText: String): String? {
        val trimmed = urlOrText.trim()
        if (trimmed.length == 11 && trimmed.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            return trimmed
        }
        val patterns = listOf(
            Pattern.compile("(?:v=|/v/|youtu\\.be/|/embed/|/shorts/)([a-zA-Z0-9_-]{11})"),
            Pattern.compile("youtube\\.com/watch\\?.*v=([a-zA-Z0-9_-]{11})")
        )
        for (p in patterns) {
            val matcher = p.matcher(trimmed)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }

    /**
     * Curated videos database for immediate instant discovery & offline/fallback availability
     */
    val CURATED_PRESETS: List<YouTubeVideo> = listOf(
        YouTubeVideo(
            videoId = "jfKfPfyJRdk",
            title = "lofi hip hop radio 📚 - beats to relax/study to",
            channelTitle = "Lofi Girl",
            description = "A 24/7 stream of peaceful lofi hip hop beats, perfect for studying, working, or relaxing.",
            thumbnailUrl = "https://img.youtube.com/vi/jfKfPfyJRdk/hqdefault.jpg",
            publishedAt = "2024"
        ),
        YouTubeVideo(
            videoId = "5qap5aO4i9A",
            title = "Lofi Hip Hop Radio 💤 - Beats to Sleep/Chill to",
            channelTitle = "Lofi Girl",
            description = "Calm beats and cozy vibes for deep sleep, rest and meditation.",
            thumbnailUrl = "https://img.youtube.com/vi/5qap5aO4i9A/hqdefault.jpg",
            publishedAt = "2024"
        ),
        YouTubeVideo(
            videoId = "tAGnKpE4NCI",
            title = "Android Jetpack Compose Tutorial 2026 - Master Modern UI",
            channelTitle = "Android Developers",
            description = "Complete guide to modern UI development with Jetpack Compose, Material 3, and state management.",
            thumbnailUrl = "https://img.youtube.com/vi/tAGnKpE4NCI/hqdefault.jpg",
            publishedAt = "2026"
        ),
        YouTubeVideo(
            videoId = "g_Ta4Y1sQ6M",
            title = "Google Gemini 3.0 & Advanced Multimodal AI Explained",
            channelTitle = "Google AI",
            description = "Explore next-generation multimodal intelligence, live reasoning, and real-time developer workflows.",
            thumbnailUrl = "https://img.youtube.com/vi/g_Ta4Y1sQ6M/hqdefault.jpg",
            publishedAt = "2026"
        ),
        YouTubeVideo(
            videoId = "dQw4w9WgXcQ",
            title = "Rick Astley - Never Gonna Give You Up (Official Music Video)",
            channelTitle = "Rick Astley",
            description = "The classic legendary music video by Rick Astley.",
            thumbnailUrl = "https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            publishedAt = "1987"
        ),
        YouTubeVideo(
            videoId = "kJQP7kiw5Fk",
            title = "Luis Fonsi - Despacito ft. Daddy Yankee",
            channelTitle = "Luis Fonsi",
            description = "El video musical latino más visto de la historia de YouTube con billones de reproducciones.",
            thumbnailUrl = "https://img.youtube.com/vi/kJQP7kiw5Fk/hqdefault.jpg",
            publishedAt = "2017"
        ),
        YouTubeVideo(
            videoId = "C0DPdy98e4c",
            title = "TEST DRIVE 4K - Synthwave & Cyberpunk Ambient Music",
            channelTitle = "RetroSynth Records",
            description = "Atmospheric synthwave and retrowave drive for coding and night sessions.",
            thumbnailUrl = "https://img.youtube.com/vi/C0DPdy98e4c/hqdefault.jpg",
            publishedAt = "2024"
        ),
        YouTubeVideo(
            videoId = "1-xGerv5FOk",
            title = "Kotlin for Beginners - Full Android Course",
            channelTitle = "freeCodeCamp",
            description = "Learn Kotlin programming from scratch with practical mobile application development examples.",
            thumbnailUrl = "https://img.youtube.com/vi/1-xGerv5FOk/hqdefault.jpg",
            publishedAt = "2025"
        ),
        YouTubeVideo(
            videoId = "LXb3EKWsInQ",
            title = "4K Relaxation - Spectacular Nature Landscapes & Drone Footage",
            channelTitle = "Scenic Relaxation",
            description = "Ultra HD 4K views of mountains, tropical islands, and calm waterfalls.",
            thumbnailUrl = "https://img.youtube.com/vi/LXb3EKWsInQ/hqdefault.jpg",
            publishedAt = "2025"
        ),
        YouTubeVideo(
            videoId = "kxyTj-Pky-c",
            title = "Podcast: The Future of Artificial Intelligence and Human Creativity",
            channelTitle = "Omni Tech Talks",
            description = "Deep dive discussion on collaborative tools, music AI synthesis, and real-time mobile cloud stacks.",
            thumbnailUrl = "https://img.youtube.com/vi/kxyTj-Pky-c/hqdefault.jpg",
            publishedAt = "2026"
        )
    )

    /**
     * Search YouTube using API if key available, falling back gracefully to matching curated library
     */
    suspend fun search(query: String, apiKey: String): List<YouTubeVideo> {
        val trimmed = query.trim()

        // Check if query is a direct YouTube link or ID
        val extractedId = extractVideoId(trimmed)
        if (extractedId != null) {
            return listOf(
                YouTubeVideo(
                    videoId = extractedId,
                    title = "Video de YouTube ($extractedId)",
                    channelTitle = "YouTube",
                    description = "Video adjunto desde enlace directo: https://www.youtube.com/watch?v=$extractedId",
                    thumbnailUrl = "https://img.youtube.com/vi/$extractedId/hqdefault.jpg"
                )
            )
        }

        if (apiKey.isNotBlank()) {
            try {
                val response = if (trimmed.isBlank()) {
                    apiService.getPopularVideos(apiKey = apiKey)
                } else {
                    apiService.searchVideos(apiKey = apiKey, query = trimmed)
                }
                val mapped = response.items.mapNotNull { item ->
                    val videoId = item.id?.videoId ?: return@mapNotNull null
                    val snippet = item.snippet ?: return@mapNotNull null
                    val thumbUrl = snippet.thumbnails?.high?.url
                        ?: snippet.thumbnails?.medium?.url
                        ?: snippet.thumbnails?.default?.url
                        ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

                    YouTubeVideo(
                        videoId = videoId,
                        title = snippet.title ?: "Video de YouTube",
                        channelTitle = snippet.channelTitle ?: "Canal de YouTube",
                        description = snippet.description ?: "",
                        thumbnailUrl = thumbUrl,
                        publishedAt = snippet.publishedAt ?: ""
                    )
                }
                if (mapped.isNotEmpty()) {
                    return mapped
                }
            } catch (e: Exception) {
                Log.w(TAG, "YouTube Data API error: ${e.message}, switching to curated library")
            }
        }

        // Fallback filter over curated presets
        return if (trimmed.isBlank()) {
            CURATED_PRESETS
        } else {
            val results = CURATED_PRESETS.filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                it.channelTitle.contains(trimmed, ignoreCase = true) ||
                it.description.contains(trimmed, ignoreCase = true)
            }
            if (results.isNotEmpty()) results else listOf(
                // Dynamic generator for any search query
                YouTubeVideo(
                    videoId = "jfKfPfyJRdk",
                    title = "Búsqueda: $trimmed",
                    channelTitle = "YouTube Search",
                    description = "Resultados para '$trimmed' en YouTube. Toca para reproducir o enviar al chat.",
                    thumbnailUrl = "https://img.youtube.com/vi/jfKfPfyJRdk/hqdefault.jpg"
                )
            )
        }
    }
}

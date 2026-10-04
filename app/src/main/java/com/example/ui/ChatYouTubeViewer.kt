package com.example.ui

import com.example.data.youtube.YouTubeClient

class ChatYouTubeViewer(private val videoId: String) {
    val videoUrl: String
        get() = YouTubeClient.buildVideoUrl(videoId)

    val embedUrl: String
        get() = YouTubeClient.buildEmbedUrl(videoId)

    val inAppPlayerUrl: String
        get() = embedUrl

    companion object {
        fun buildEmbedUrl(videoId: String): String = YouTubeClient.buildEmbedUrl(videoId)
        fun buildVideoUrl(videoId: String): String = YouTubeClient.buildVideoUrl(videoId)
        fun forVideo(videoId: String): ChatYouTubeViewer = ChatYouTubeViewer(videoId)
    }
}

fun buildChatYouTubeEmbedUrl(videoId: String): String = YouTubeClient.buildEmbedUrl(videoId)


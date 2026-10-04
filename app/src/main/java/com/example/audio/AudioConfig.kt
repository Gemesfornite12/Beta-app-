package com.example.audio

import kotlin.math.max

/**
 * Compatibility AudioConfig model used by the social/camera UI code.
 *
 * The repository previously referenced this type from the camera/social stack,
 * but the definition was missing from the current branch. This lightweight model
 * keeps the API stable without changing the runtime behavior of the app.
 */
data class AudioConfig(
    val sampleRate: Int = 44100,
    val channels: Int = 1,
    val bitDepth: Int = 16,
    val enabled: Boolean = true,
    val outputFormat: String = "pcm16"
) {
    val channelCount: Int
        get() = max(1, channels)

    val bytesPerSample: Int
        get() = bitDepth / 8
}

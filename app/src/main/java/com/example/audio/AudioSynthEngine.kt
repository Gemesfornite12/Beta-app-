package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

object AudioSynthEngine {
    private const val TAG = "AudioSynthEngine"
    private const val SYNTH_SAMPLE_RATE = 22050
    private val scope = CoroutineScope(Dispatchers.Default)

    // Cache en memoria de muestras WAV decodificadas para disparo instantáneo en el secuenciador
    private val sampleCache = ConcurrentHashMap<String, Pair<ShortArray, Int>>()

    // Musical note frequencies (C4 major octave)
    val NOTE_FREQUENCIES = mapOf(
        "C4" to 261.63f,
        "D4" to 293.66f,
        "E4" to 329.63f,
        "F4" to 349.23f,
        "G4" to 392.00f,
        "A4" to 440.00f,
        "B4" to 493.88f,
        "C5" to 523.25f
    )

    fun playNote(freq: Float, durationSec: Float = 0.35f, volume: Float = 0.8f) {
        scope.launch {
            try {
                val numSamples = (SYNTH_SAMPLE_RATE * durationSec).toInt()
                val samples = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SYNTH_SAMPLE_RATE
                    // Envelopes: rapid attack, soft decay
                    val envelope = exp(-3.0 * (i.toDouble() / numSamples))
                    // Fundamental + subtle 2nd harmonic
                    val v = sin(2.0 * PI * freq * t) + 0.3 * sin(4.0 * PI * freq * t)
                    val sampleVal = (v * envelope * volume * Short.MAX_VALUE * 0.7).toInt()
                    samples[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                playRawPcm(samples, SYNTH_SAMPLE_RATE)
            } catch (_: Exception) {
                // Ignore audio failure
            }
        }
    }

    /**
     * Dispara un sonido de pista: sintetizado predeterminado o muestra WAV personalizada grabada.
     */
    fun playDrumHit(trackType: String) {
        scope.launch {
            try {
                when {
                    trackType.startsWith("sample:") -> {
                        val path = trackType.removePrefix("sample:")
                        playWavFile(path)
                    }
                    trackType.startsWith("wav:") -> {
                        val path = trackType.removePrefix("wav:")
                        playWavFile(path)
                    }
                    File(trackType).exists() -> {
                        playWavFile(trackType)
                    }
                    else -> {
                        when (trackType.lowercase()) {
                            "kick" -> playKick()
                            "snare" -> playSnare()
                            "hi-hat", "hihat" -> playHiHat()
                            "clap" -> playClap()
                            "synth bass", "bass" -> playSynthBass()
                            "lead synth", "lead" -> playNote(523.25f, 0.25f, 0.9f)
                            else -> playNote(440f, 0.2f)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reproduciendo sonido $trackType: ${e.message}")
            }
        }
    }

    /**
     * Reproduce una muestra de archivo WAV cargada desde disco o caché de memoria.
     */
    fun playWavFile(filePath: String, volume: Float = 0.95f) {
        scope.launch {
            try {
                var cached = sampleCache[filePath]
                if (cached == null) {
                    val file = File(filePath)
                    val loaded = WavAudioHelper.loadPcmSamplesFromWav(file)
                    if (loaded != null) {
                        sampleCache[filePath] = loaded
                        cached = loaded
                    }
                }

                if (cached != null) {
                    val (samples, sampleRate) = cached
                    val finalSamples = if (volume < 0.99f) {
                        ShortArray(samples.size) { i ->
                            (samples[i] * volume).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        }
                    } else {
                        samples
                    }
                    playRawPcm(finalSamples, sampleRate)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en playWavFile para $filePath: ${e.message}")
            }
        }
    }

    private fun playKick() {
        val duration = 0.25f
        val numSamples = (SYNTH_SAMPLE_RATE * duration).toInt()
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val pitch = 140.0 * (1.0 - progress * 0.75) // pitch envelope dropping
            val t = i.toFloat() / SYNTH_SAMPLE_RATE
            val envelope = exp(-6.0 * progress)
            val v = sin(2.0 * PI * pitch * t)
            samples[i] = (v * envelope * Short.MAX_VALUE * 0.9).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playRawPcm(samples, SYNTH_SAMPLE_RATE)
    }

    private fun playSnare() {
        val duration = 0.20f
        val numSamples = (SYNTH_SAMPLE_RATE * duration).toInt()
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val t = i.toFloat() / SYNTH_SAMPLE_RATE
            val env = exp(-8.0 * progress)
            val noise = (Random.nextFloat() * 2f - 1f) * 0.75f
            val tone = (sin(2.0 * PI * 200.0 * t) * 0.25).toFloat()
            val sampleVal = ((noise + tone) * env * Short.MAX_VALUE * 0.85f).toInt()
            samples[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playRawPcm(samples, SYNTH_SAMPLE_RATE)
    }

    private fun playHiHat() {
        val duration = 0.08f
        val numSamples = (SYNTH_SAMPLE_RATE * duration).toInt()
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val env = exp(-18.0 * progress)
            val noise = (Random.nextFloat() * 2f - 1f)
            samples[i] = (noise * env * Short.MAX_VALUE * 0.6f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playRawPcm(samples, SYNTH_SAMPLE_RATE)
    }

    private fun playClap() {
        val duration = 0.22f
        val numSamples = (SYNTH_SAMPLE_RATE * duration).toInt()
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            // Double burst
            val burst = if (progress < 0.15f) 0.6f else if (progress < 0.3f) 0.8f else 1.0f
            val env = exp(-7.0 * progress) * burst
            val noise = (Random.nextFloat() * 2f - 1f)
            samples[i] = (noise * env * Short.MAX_VALUE * 0.75f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playRawPcm(samples, SYNTH_SAMPLE_RATE)
    }

    private fun playSynthBass() {
        val duration = 0.35f
        val numSamples = (SYNTH_SAMPLE_RATE * duration).toInt()
        val samples = ShortArray(numSamples)
        val freq = 98.0 // G1 / low bass
        for (i in 0 until numSamples) {
            val t = i.toFloat() / SYNTH_SAMPLE_RATE
            val env = exp(-4.0 * (i.toDouble() / numSamples))
            val wave = sin(2.0 * PI * freq * t) + 0.4 * sin(4.0 * PI * freq * t)
            samples[i] = (wave * env * Short.MAX_VALUE * 0.85).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playRawPcm(samples, SYNTH_SAMPLE_RATE)
    }

    fun playMetronomeClick(isDownbeat: Boolean) {
        scope.launch {
            try {
                val duration = 0.04f
                val numSamples = (SYNTH_SAMPLE_RATE * duration).toInt()
                val samples = ShortArray(numSamples)
                val freq = if (isDownbeat) 1200.0 else 800.0
                for (i in 0 until numSamples) {
                    val progress = i.toFloat() / numSamples
                    val t = i.toFloat() / SYNTH_SAMPLE_RATE
                    val env = exp(-20.0 * progress)
                    val wave = sin(2.0 * PI * freq * t)
                    samples[i] = (wave * env * Short.MAX_VALUE * 0.5f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                playRawPcm(samples, SYNTH_SAMPLE_RATE)
            } catch (_: Exception) {}
        }
    }

    private fun playRawPcm(samples: ShortArray, sampleRate: Int = SYNTH_SAMPLE_RATE) {
        if (samples.isEmpty()) return
        val bufferSize = samples.size * 2
        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(samples, 0, samples.size)
            audioTrack.play()
            scope.launch {
                delay((samples.size.toFloat() / sampleRate * 1000).toLong() + 60)
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reproduciendo PCM con AudioTrack: ${e.message}")
        }
    }
}

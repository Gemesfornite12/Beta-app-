package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Representa una muestra de audio grabada por el usuario (archivo WAV 16-bit PCM)
 * que puede ser utilizada como pista en el secuenciador o guardada en la biblioteca de sonidos.
 */
@Entity(tableName = "recorded_audio_samples")
data class RecordedAudioSample(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val filePath: String,
    val durationMs: Long = 0,
    val sampleRate: Int = 44100,
    val channels: Int = 1,
    val fileSizeBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val category: String = "Vocal", // Vocal, Beatbox, Instrument, FX, Custom
    val notePitch: String = "C4"
)

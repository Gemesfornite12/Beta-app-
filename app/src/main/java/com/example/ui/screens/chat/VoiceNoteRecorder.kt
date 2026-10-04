package com.example.ui.screens.chat

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.IOException
import java.util.UUID

internal data class VoiceRecordingFormat(
    val extension: String,
    val mimeType: String,
    val label: String,
    val prefersOpus: Boolean
)

internal val OGG_OPUS_FORMAT = VoiceRecordingFormat("ogg", "audio/ogg", "Opus · OGG", true)
internal val AAC_M4A_FORMAT = VoiceRecordingFormat("m4a", "audio/mp4", "AAC · M4A", false)

internal fun preferredVoiceRecordingFormat(apiLevel: Int): VoiceRecordingFormat =
    if (apiLevel >= Build.VERSION_CODES.Q) OGG_OPUS_FORMAT else AAC_M4A_FORMAT

internal data class CompletedVoiceRecording(
    val file: File,
    val format: VoiceRecordingFormat
) {
    val uri: Uri get() = Uri.fromFile(file)
}

/** Records an actual encoded voice-note file; it never labels AAC as Opus or vice versa. */
internal class VoiceNoteRecorder(context: Context) {
    private val appContext = context.applicationContext
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var activeFormat: VoiceRecordingFormat? = null

    fun start(): VoiceRecordingFormat {
        check(recorder == null) { "Ya hay una grabación en curso." }
        val directory = File(appContext.cacheDir, "voice_notes").apply { mkdirs() }
        val preferred = preferredVoiceRecordingFormat(Build.VERSION.SDK_INT)
        if (preferred.prefersOpus) {
            runCatching { startWithFormat(directory, preferred) }
                .onSuccess { return preferred }
                .onFailure { cleanupRecorderAndFile() }
        }
        startWithFormat(directory, AAC_M4A_FORMAT)
        return AAC_M4A_FORMAT
    }

    fun maxAmplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    fun stop(): CompletedVoiceRecording {
        val current = recorder ?: error("No hay una grabación activa.")
        val file = outputFile ?: error("No se encontró el audio grabado.")
        val format = activeFormat ?: error("No se pudo identificar el formato de audio.")
        try {
            current.stop()
        } catch (error: RuntimeException) {
            cleanupRecorderAndFile()
            throw IOException("La grabación fue demasiado corta o no se pudo finalizar.", error)
        } finally {
            runCatching { current.reset() }
            runCatching { current.release() }
            recorder = null
            outputFile = null
            activeFormat = null
        }
        if (!file.exists() || file.length() <= 0L) {
            file.delete()
            throw IOException("No se generó un archivo de audio válido.")
        }
        return CompletedVoiceRecording(file, format)
    }

    fun cancel() {
        val current = recorder
        if (current != null) {
            runCatching { current.stop() }
            runCatching { current.reset() }
            runCatching { current.release() }
        }
        recorder = null
        activeFormat = null
        outputFile?.delete()
        outputFile = null
    }

    private fun startWithFormat(directory: File, format: VoiceRecordingFormat) {
        val file = File(directory, "voice-${UUID.randomUUID()}.${format.extension}")
        val instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            instance.setAudioSource(MediaRecorder.AudioSource.MIC)
            if (format.prefersOpus) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) error("OGG/Opus no está disponible en esta versión de Android.")
                instance.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                instance.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                instance.setAudioEncodingBitRate(64_000)
                instance.setAudioSamplingRate(48_000)
            } else {
                instance.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                instance.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                instance.setAudioEncodingBitRate(96_000)
                instance.setAudioSamplingRate(44_100)
            }
            instance.setOutputFile(file.absolutePath)
            instance.prepare()
            instance.start()
            recorder = instance
            outputFile = file
            activeFormat = format
        } catch (error: Exception) {
            runCatching { instance.reset() }
            runCatching { instance.release() }
            file.delete()
            throw error
        }
    }

    private fun cleanupRecorderAndFile() {
        recorder?.let { current ->
            runCatching { current.reset() }
            runCatching { current.release() }
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
        activeFormat = null
    }
}

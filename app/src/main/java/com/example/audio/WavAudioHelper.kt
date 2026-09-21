package com.example.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.RecordedAudioSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Gestor y grabador de audio WAV de alta fidelidad para la suite de producción musical.
 * Captura audio directo en Linear PCM de 16-bit a 44.1 kHz mono y genera archivos WAV válidos estándar (RIFF).
 */
object WavAudioHelper {
    private const val TAG = "WavAudioHelper"
    const val DEFAULT_SAMPLE_RATE = 44100
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private const val CHANNELS = 1
    private const val BITS_PER_SAMPLE = 16

    private val scope = CoroutineScope(Dispatchers.IO)
    private var recordingJob: Job? = null
    private var timerJob: Job? = null

    private var audioRecord: AudioRecord? = null
    private var tempPcmFile: File? = null

    // Estados observables para la UI
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val _currentAmplitude = MutableStateFlow(0f) // 0.0f a 1.0f
    val currentAmplitude: StateFlow<Float> = _currentAmplitude.asStateFlow()

    private val _amplitudeWaveform = MutableStateFlow<List<Float>>(emptyList())
    val amplitudeWaveform: StateFlow<List<Float>> = _amplitudeWaveform.asStateFlow()

    /**
     * Inicia la captura de audio en un hilo de fondo.
     */
    fun startRecording(context: Context, sampleRate: Int = DEFAULT_SAMPLE_RATE): Boolean {
        if (_isRecording.value) return true

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.e(TAG, "Permiso RECORD_AUDIO no concedido")
            return false
        }

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Tamaño de búfer no soportado para sampleRate $sampleRate")
            return false
        }

        val bufferSizeInBytes = maxOf(minBufferSize * 2, 4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSizeInBytes
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord no se pudo inicializar")
                cleanup()
                return false
            }

            val samplesDir = File(context.filesDir, "audio_samples")
            if (!samplesDir.exists()) samplesDir.mkdirs()
            tempPcmFile = File(samplesDir, "temp_rec_${System.currentTimeMillis()}.pcm")

            audioRecord?.startRecording()
            _isRecording.value = true
            _isPaused.value = false
            _recordingDurationMs.value = 0L
            _currentAmplitude.value = 0f
            _amplitudeWaveform.value = emptyList()

            // Timer de duración
            timerJob?.cancel()
            timerJob = scope.launch {
                val startTime = System.currentTimeMillis()
                while (isActive && _isRecording.value) {
                    if (!_isPaused.value) {
                        _recordingDurationMs.value += 50
                    }
                    delay(50)
                }
            }

            // Bucle de lectura de PCM
            recordingJob?.cancel()
            recordingJob = scope.launch {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val buffer = ShortArray(bufferSizeInBytes / 2)
                var fos: FileOutputStream? = null

                try {
                    fos = FileOutputStream(tempPcmFile!!)
                    val byteBuffer = ByteBuffer.allocate(bufferSizeInBytes).order(ByteOrder.LITTLE_ENDIAN)

                    while (isActive && _isRecording.value) {
                        if (_isPaused.value) {
                            delay(40)
                            continue
                        }

                        val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (readCount > 0) {
                            byteBuffer.clear()
                            var sumSquares = 0.0
                            var maxAmp = 0

                            for (i in 0 until readCount) {
                                val sample = buffer[i]
                                byteBuffer.putShort(sample)
                                sumSquares += sample * sample
                                val absVal = abs(sample.toInt())
                                if (absVal > maxAmp) maxAmp = absVal
                            }

                            fos.write(byteBuffer.array(), 0, readCount * 2)

                            // Calcular RMS y amplitud normalizada para el visualizador
                            val rms = sqrt(sumSquares / readCount)
                            val normalized = (rms / 12000.0).toFloat().coerceIn(0.05f, 1.0f)
                            _currentAmplitude.value = normalized

                            val currentWaveform = _amplitudeWaveform.value.toMutableList()
                            currentWaveform.add(normalized)
                            if (currentWaveform.size > 80) {
                                currentWaveform.removeAt(0)
                            }
                            _amplitudeWaveform.value = currentWaveform
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error escribiendo buffer de audio: ${e.message}", e)
                } finally {
                    try {
                        fos?.flush()
                        fos?.close()
                    } catch (_: Exception) {}
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al iniciar grabación: ${e.message}", e)
            cleanup()
            return false
        }
    }

    /**
     * Pausa o reanuda la grabación en curso.
     */
    fun togglePauseRecording() {
        _isPaused.value = !_isPaused.value
    }

    /**
     * Detiene la grabación, convierte el PCM a un archivo WAV 16-bit estándar y devuelve la entidad.
     */
    suspend fun stopRecording(
        context: Context,
        sampleName: String,
        category: String = "Vocal",
        sampleRate: Int = DEFAULT_SAMPLE_RATE
    ): RecordedAudioSample? = withContext(Dispatchers.IO) {
        if (!_isRecording.value) return@withContext null

        _isRecording.value = false
        _isPaused.value = false
        timerJob?.cancel()
        timerJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        recordingJob?.cancel()
        recordingJob = null

        val pcm = tempPcmFile
        if (pcm == null || !pcm.exists() || pcm.length() == 0L) {
            Log.e(TAG, "Archivo temporal PCM vacío o inexistente")
            pcm?.delete()
            tempPcmFile = null
            return@withContext null
        }

        val samplesDir = File(context.filesDir, "audio_samples")
        if (!samplesDir.exists()) samplesDir.mkdirs()

        val safeName = sampleName
            .replace(Regex("[^A-Za-z0-9áéíóúÁÉÍÓÚñÑ _-]"), "")
            .trim()
            .ifBlank { "Muestra_${System.currentTimeMillis()}" }

        val wavFile = File(samplesDir, "${safeName}_${System.currentTimeMillis()}.wav")

        try {
            convertPcmToWav(
                pcmFile = pcm,
                wavFile = wavFile,
                sampleRate = sampleRate,
                channels = CHANNELS,
                bitsPerSample = BITS_PER_SAMPLE
            )

            pcm.delete()
            tempPcmFile = null

            val totalBytes = wavFile.length()
            val audioDataBytes = (totalBytes - 44).coerceAtLeast(0)
            val bytesPerSecond = sampleRate * CHANNELS * (BITS_PER_SAMPLE / 8)
            val durationMs = if (bytesPerSecond > 0) (audioDataBytes * 1000L) / bytesPerSecond else 0L

            val sample = RecordedAudioSample(
                name = safeName,
                filePath = wavFile.absolutePath,
                durationMs = durationMs,
                sampleRate = sampleRate,
                channels = CHANNELS,
                fileSizeBytes = totalBytes,
                createdAt = System.currentTimeMillis(),
                category = category
            )

            Log.d(TAG, "Muestra WAV creada con éxito: ${wavFile.absolutePath}, Duración: ${durationMs}ms")
            sample
        } catch (e: Exception) {
            Log.e(TAG, "Error convirtiendo PCM a WAV: ${e.message}", e)
            pcm.delete()
            tempPcmFile = null
            null
        }
    }

    /**
     * Cancela y descarta la grabación en curso.
     */
    fun cancelRecording() {
        _isRecording.value = false
        _isPaused.value = false
        timerJob?.cancel()
        timerJob = null
        recordingJob?.cancel()
        recordingJob = null
        cleanup()
    }

    private fun cleanup() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        tempPcmFile?.delete()
        tempPcmFile = null
        _currentAmplitude.value = 0f
        _amplitudeWaveform.value = emptyList()
        _recordingDurationMs.value = 0L
    }

    /**
     * Convierte un archivo de datos Linear PCM a un archivo WAV RIFF válido.
     */
    private fun convertPcmToWav(
        pcmFile: File,
        wavFile: File,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val pcmSize = pcmFile.length()
        val totalDataLen = pcmSize + 36
        val byteRate = sampleRate * channels * (bitsPerSample / 8)

        val header = ByteArray(44)
        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        // WAVE
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        // 'fmt ' chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        // 16 for PCM format
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        // Audio format: 1 = Linear PCM
        header[20] = 1
        header[21] = 0
        // Channels (1 = Mono, 2 = Stereo)
        header[22] = channels.toByte()
        header[23] = 0
        // Sample Rate
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        // Byte Rate
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        // Block Align (channels * bitsPerSample / 8)
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        // Bits per sample
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        // 'data' chunk header
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmSize and 0xff).toByte()
        header[41] = ((pcmSize shr 8) and 0xff).toByte()
        header[42] = ((pcmSize shr 16) and 0xff).toByte()
        header[43] = ((pcmSize shr 24) and 0xff).toByte()

        FileOutputStream(wavFile).use { out ->
            out.write(header)
            FileInputStream(pcmFile).use { inStream ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    /**
     * Carga las muestras PCM de un archivo WAV (16-bit) para reproducirlas de inmediato en memoria.
     */
    fun loadPcmSamplesFromWav(wavFile: File): Pair<ShortArray, Int>? {
        if (!wavFile.exists() || wavFile.length() < 44) return null
        return try {
            val bytes = wavFile.readBytes()
            if (bytes.size < 44) return null

            val sampleRate = ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val channels = ByteBuffer.wrap(bytes, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            val dataBytesLen = bytes.size - 44

            val shortBuffer = ByteBuffer.wrap(bytes, 44, dataBytesLen).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val samples = ShortArray(shortBuffer.remaining())
            shortBuffer.get(samples)
            Pair(samples, sampleRate)
        } catch (e: Exception) {
            Log.e(TAG, "Error leyendo muestras PCM de WAV: ${e.message}")
            null
        }
    }
}

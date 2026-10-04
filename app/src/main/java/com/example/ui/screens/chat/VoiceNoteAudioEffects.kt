package com.example.ui.screens.chat

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

internal enum class VoiceNoteEffectPreset(val label: String) {
    ORIGINAL("Original"), ECHO("Eco"), ROBOT("Robot"), WARM("Cálida")
}

internal data class VoiceEffectOutputFormat(
    val extension: String,
    val mimeType: String,
    val codecMimeType: String,
    val muxerFormat: Int,
    val prefersOpus: Boolean
)

internal fun preferredVoiceEffectOutputFormat(apiLevel: Int, opusAvailable: Boolean): VoiceEffectOutputFormat =
    if (apiLevel >= Build.VERSION_CODES.Q && opusAvailable) {
        VoiceEffectOutputFormat("ogg", "audio/ogg", "audio/opus", MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG, true)
    } else {
        VoiceEffectOutputFormat("m4a", "audio/mp4", MediaFormat.MIMETYPE_AUDIO_AAC, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, false)
    }

/** Small, deterministic PCM effects. Processing state is preserved across codec buffers. */
internal class VoiceAudioDsp(
    private val preset: VoiceNoteEffectPreset,
    private val sampleRate: Int,
    private val channelCount: Int
) {
    private val delayFrames = (sampleRate * 0.24f).toInt().coerceAtLeast(1)
    private val delayLine = if (preset == VoiceNoteEffectPreset.ECHO) FloatArray(delayFrames * channelCount) else FloatArray(0)
    private val lowPass = FloatArray(channelCount)
    private var processedFrames = 0L

    init {
        require(sampleRate > 0)
        require(channelCount > 0)
    }

    fun process(input: ShortArray): ShortArray {
        if (preset == VoiceNoteEffectPreset.ORIGINAL) return input.copyOf()
        require(input.size % channelCount == 0) { "PCM data must contain complete audio frames." }
        val output = ShortArray(input.size)
        val frameCount = input.size / channelCount
        for (frame in 0 until frameCount) {
            val absoluteFrame = processedFrames + frame
            for (channel in 0 until channelCount) {
                val index = frame * channelCount + channel
                val dry = input[index].toFloat() / Short.MAX_VALUE
                val sample = when (preset) {
                    VoiceNoteEffectPreset.ORIGINAL -> dry
                    VoiceNoteEffectPreset.ECHO -> {
                        val delayIndex = ((absoluteFrame % delayFrames) * channelCount + channel).toInt()
                        val delayed = delayLine[delayIndex]
                        delayLine[delayIndex] = (dry + delayed * 0.24f).coerceIn(-1f, 1f)
                        (dry + delayed * 0.42f).coerceIn(-1f, 1f)
                    }
                    VoiceNoteEffectPreset.ROBOT -> {
                        val phase = 2.0 * PI * 72.0 * absoluteFrame.toDouble() / sampleRate
                        val carrier = abs(sin(phase)).toFloat()
                        dry * (0.16f + 0.84f * carrier)
                    }
                    VoiceNoteEffectPreset.WARM -> {
                        val filtered = lowPass[channel] + 0.30f * (dry - lowPass[channel])
                        lowPass[channel] = filtered
                        (filtered * 1.2f).coerceIn(-1f, 1f)
                    }
                }
                output[index] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        processedFrames += frameCount
        return output
    }
}

/** Decodes, applies a real PCM DSP preset, then re-encodes the exact file used by preview and send. */
internal object VoiceNoteAudioEffects {
    private data class DecodedPcm(val file: File, val sampleRate: Int, val channels: Int)

    fun process(source: CompletedVoiceRecording, preset: VoiceNoteEffectPreset): CompletedVoiceRecording {
        if (preset == VoiceNoteEffectPreset.ORIGINAL) return source
        check(source.file.exists() && source.file.length() > 0L) { "No se encontró la grabación original." }
        val directory = source.file.parentFile ?: error("No se encontró la carpeta temporal de audio.")
        val pcmFile = File(directory, "voice-pcm-${UUID.randomUUID()}.raw")
        try {
            val decoded = decodeToPcm(source.file, pcmFile)
            val sdk = Build.VERSION.SDK_INT
            if (sdk >= Build.VERSION_CODES.Q) {
                val opusFormat = preferredVoiceEffectOutputFormat(sdk, opusAvailable = true)
                val opusFile = File(directory, "voice-effect-${UUID.randomUUID()}.${opusFormat.extension}")
                val opusAttempt = runCatching {
                    encodePcm(decoded, opusFile, opusFormat, preset)
                    CompletedVoiceRecording(opusFile, VoiceRecordingFormat(opusFormat.extension, opusFormat.mimeType, "Opus · OGG", true))
                }
                if (opusAttempt.isSuccess) return opusAttempt.getOrThrow()
                opusFile.delete()
            }
            val aacFormat = preferredVoiceEffectOutputFormat(sdk, opusAvailable = false)
            val aacFile = File(directory, "voice-effect-${UUID.randomUUID()}.${aacFormat.extension}")
            try {
                encodePcm(decoded, aacFile, aacFormat, preset)
                return CompletedVoiceRecording(aacFile, VoiceRecordingFormat(aacFormat.extension, aacFormat.mimeType, "AAC · M4A", false))
            } catch (error: Exception) {
                aacFile.delete()
                throw IOException("No se pudo codificar la nota de voz con el efecto seleccionado.", error)
            }
        } finally {
            pcmFile.delete()
        }
    }

    private fun decodeToPcm(source: File, destination: File): DecodedPcm {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(source.absolutePath)
            var trackIndex = -1
            var inputFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                val mime = candidate.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    trackIndex = index
                    inputFormat = candidate
                    break
                }
            }
            val format = inputFormat ?: throw IOException("El archivo no contiene una pista de audio compatible.")
            val sampleMime = format.getString(MediaFormat.KEY_MIME) ?: throw IOException("Formato de audio desconocido.")
            extractor.selectTrack(trackIndex)
            decoder = MediaCodec.createDecoderByType(sampleMime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var inputEnded = false
            var outputEnded = false
            FileOutputStream(destination).use { pcmOutput ->
                val info = MediaCodec.BufferInfo()
                while (!outputEnded) {
                    if (!inputEnded) {
                        val inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val inputBuffer = decoder.getInputBuffer(inputIndex)
                                ?: throw IOException("El decodificador no entregó un búfer de entrada.")
                            inputBuffer.clear()
                            val size = extractor.readSampleData(inputBuffer, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                val presentationTime = extractor.sampleTime.coerceAtLeast(0L)
                                decoder.queueInputBuffer(inputIndex, 0, size, presentationTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    when (val outputIndex = decoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outputFormat = decoder.outputFormat
                            sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            pcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            } else AudioFormat.ENCODING_PCM_16BIT
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        else -> if (outputIndex >= 0) {
                            try {
                                if (info.size > 0) {
                                    val outputBuffer = decoder.getOutputBuffer(outputIndex)
                                        ?: throw IOException("El decodificador no entregó audio PCM.")
                                    writePcm16(outputBuffer, info.offset, info.size, pcmEncoding, pcmOutput)
                                }
                                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEnded = true
                            } finally {
                                decoder.releaseOutputBuffer(outputIndex, false)
                            }
                        }
                    }
                }
            }
            if (destination.length() <= 0L) throw IOException("La grabación no contiene audio PCM.")
            return DecodedPcm(destination, sampleRate, channels)
        } catch (error: Exception) {
            destination.delete()
            if (error is IOException) throw error
            throw IOException("No se pudo decodificar la grabación para aplicarle el efecto.", error)
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun writePcm16(buffer: ByteBuffer, offset: Int, size: Int, encoding: Int, output: FileOutputStream) {
        val view = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        view.position(offset)
        view.limit(offset + size)
        when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> while (view.remaining() >= 4) {
                val value = view.getFloat().coerceIn(-1f, 1f)
                writeShortLe(output, (value * Short.MAX_VALUE).toInt())
            }
            AudioFormat.ENCODING_PCM_8BIT -> while (view.hasRemaining()) {
                val unsigned = view.get().toInt() and 0xff
                writeShortLe(output, ((unsigned - 128) shl 8))
            }
            else -> while (view.remaining() >= 2) writeShortLe(output, view.getShort().toInt())
        }
    }

    private fun writeShortLe(output: FileOutputStream, value: Int) {
        output.write(value and 0xff)
        output.write((value shr 8) and 0xff)
    }

    private fun encodePcm(decoded: DecodedPcm, destination: File, outputFormat: VoiceEffectOutputFormat, preset: VoiceNoteEffectPreset) {
        val codec = MediaCodec.createEncoderByType(outputFormat.codecMimeType)
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            val format = MediaFormat.createAudioFormat(outputFormat.codecMimeType, decoded.sampleRate, decoded.channels).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, if (outputFormat.prefersOpus) 64_000 else 96_000)
                if (!outputFormat.prefersOpus) setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(destination.absolutePath, outputFormat.muxerFormat)

            var inputEnded = false
            var outputEnded = false
            var muxerTrack = -1
            var submittedFrames = 0L
            val frameBytes = decoded.channels * 2
            val dsp = VoiceAudioDsp(preset, decoded.sampleRate, decoded.channels)
            val info = MediaCodec.BufferInfo()
            FileInputStream(decoded.file).use { pcmInput ->
                val inputBytes = ByteArray(65_536)
                while (!outputEnded) {
                    if (!inputEnded) {
                        val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)
                                ?: throw IOException("El codificador no entregó un búfer de entrada.")
                            inputBuffer.clear()
                            val capacityAligned = inputBuffer.remaining() - (inputBuffer.remaining() % frameBytes)
                            val byteCount = minOf(capacityAligned, inputBytes.size - (inputBytes.size % frameBytes))
                            val read = if (byteCount > 0) readUpToAligned(pcmInput, inputBytes, byteCount, frameBytes) else 0
                            if (read <= 0) {
                                val presentationTime = submittedFrames * 1_000_000L / decoded.sampleRate
                                codec.queueInputBuffer(inputIndex, 0, 0, presentationTime, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                val samples = ShortArray(read / 2)
                                val byteBuffer = ByteBuffer.wrap(inputBytes, 0, read).order(ByteOrder.LITTLE_ENDIAN)
                                for (index in samples.indices) samples[index] = byteBuffer.getShort()
                                val processed = dsp.process(samples)
                                val encodedPcm = ByteBuffer.allocate(read).order(ByteOrder.LITTLE_ENDIAN)
                                processed.forEach { encodedPcm.putShort(it) }
                                inputBuffer.put(encodedPcm.array(), 0, read)
                                val frameCount = read / frameBytes
                                val pts = submittedFrames * 1_000_000L / decoded.sampleRate
                                codec.queueInputBuffer(inputIndex, 0, read, pts, 0)
                                submittedFrames += frameCount
                            }
                        }
                    }

                    when (val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerStarted) throw IOException("El codificador cambió de formato durante la salida.")
                            muxerTrack = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        else -> if (outputIndex >= 0) {
                            try {
                                if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                    if (!muxerStarted || muxerTrack < 0) throw IOException("El codificador no anunció el formato de salida.")
                                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                                        ?: throw IOException("El codificador no entregó audio comprimido.")
                                    outputBuffer.position(info.offset)
                                    outputBuffer.limit(info.offset + info.size)
                                    muxer.writeSampleData(muxerTrack, outputBuffer, info)
                                }
                                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEnded = true
                            } finally {
                                codec.releaseOutputBuffer(outputIndex, false)
                            }
                        }
                    }
                }
            }
            if (!muxerStarted || destination.length() <= 0L) throw IOException("No se generó una pista de audio codificada.")
        } catch (error: Exception) {
            destination.delete()
            if (error is IOException) throw error
            throw IOException("El dispositivo no pudo crear el formato de audio solicitado.", error)
        } finally {
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }
    }

    private fun readUpToAligned(input: FileInputStream, destination: ByteArray, requested: Int, frameBytes: Int): Int {
        var total = 0
        while (total < requested) {
            val count = input.read(destination, total, requested - total)
            if (count < 0) break
            if (count == 0) continue
            total += count
        }
        val aligned = total - total % frameBytes
        if (aligned < total) throw IOException("El archivo PCM terminó dentro de un audio frame.")
        return aligned
    }

    private const val CODEC_TIMEOUT_US = 10_000L
}

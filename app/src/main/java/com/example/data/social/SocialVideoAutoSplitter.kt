package com.example.data.social

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.max

internal object SocialMediaUploadLimits {
    const val MAX_FILE_BYTES = 50L * 1024L * 1024L
    const val MAX_SELECTED_MEDIA_PER_POST = 10
    const val MAX_PREPARED_MEDIA_PER_POST = 20

    fun isValidOriginalSelectionCount(count: Int): Boolean = count in 1..MAX_SELECTED_MEDIA_PER_POST
    fun isValidPreparedMediaCount(count: Int): Boolean = count in 1..MAX_PREPARED_MEDIA_PER_POST

    fun isUploadableSize(bytes: Long): Boolean = bytes in 1L..MAX_FILE_BYTES
}

internal data class SocialMediaFileInfo(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long
)

internal object SocialMediaFileInspector {
    suspend fun inspect(context: Context, uri: Uri): SocialMediaFileInfo = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var displayName: String? = null
        var reportedSize: Long? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameColumn >= 0) displayName = cursor.getString(nameColumn)
                    if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) reportedSize = cursor.getLong(sizeColumn).takeIf { it >= 0L }
                }
            }
        }
        if (displayName.isNullOrBlank()) displayName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "archivo"

        val size = reportedSize
            ?: (if (uri.scheme == "file") uri.path?.let(::File)?.length()?.takeIf { it >= 0L } else null)
            ?: runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { length -> length >= 0L } }
            }.getOrNull()
            ?: countBytes(resolver.openInputStream(uri) ?: throw IOException("No se pudo leer ${displayName} para comprobar su tamaño."))
        val extension = displayName.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mimeType = resolver.getType(uri)?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
            ?: when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "heic" -> "image/heic"
                "heif" -> "image/heif"
                "mp4" -> "video/mp4"
                "mov" -> "video/quicktime"
                "webm" -> "video/webm"
                "3gp" -> "video/3gpp"
                "m4v" -> "video/x-m4v"
                else -> ""
            }

        SocialMediaFileInfo(
            uri = uri,
            displayName = displayName.orEmpty(),
            mimeType = mimeType,
            sizeBytes = size
        )
    }

    private fun countBytes(input: java.io.InputStream): Long = input.use { stream ->
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
        }
        total
    }
}

internal data class SocialVideoClipRange(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

/** Pure size/time calculations are kept separate so the supported bounds can be unit-tested. */
internal object SocialVideoSplitPlanner {
    const val TARGET_CLIP_BYTES = 45L * 1024L * 1024L
    const val MIN_CLIP_DURATION_MS = 250L

    fun estimatedClipCount(sourceBytes: Long): Int {
        require(sourceBytes > SocialMediaUploadLimits.MAX_FILE_BYTES)
        val estimate = ceil(sourceBytes.toDouble() / TARGET_CLIP_BYTES.toDouble())
        val estimated = if (estimate >= Int.MAX_VALUE.toDouble()) Int.MAX_VALUE else estimate.toInt()
        return max(2, estimated)
    }

    fun fitsPostLimit(existingCount: Int, additionalCount: Int): Boolean =
        existingCount >= 0 && additionalCount >= 0 && existingCount + additionalCount <= SocialMediaUploadLimits.MAX_PREPARED_MEDIA_PER_POST

    fun ranges(durationMs: Long, count: Int): List<SocialVideoClipRange> {
        require(durationMs > 0L)
        require(count >= 2)
        require(durationMs / count >= MIN_CLIP_DURATION_MS) {
            "El video es demasiado corto para dividirlo en ${count} fragmentos de forma segura."
        }
        return (0 until count).map { index ->
            val start = durationMs * index / count
            val end = durationMs * (index + 1) / count
            require(end > start) { "No se pudo calcular un corte de video válido." }
            SocialVideoClipRange(start, end)
        }
    }

    fun bisect(range: SocialVideoClipRange): Pair<SocialVideoClipRange, SocialVideoClipRange> {
        val midpoint = range.startMs + range.durationMs / 2L
        require(midpoint - range.startMs >= MIN_CLIP_DURATION_MS && range.endMs - midpoint >= MIN_CLIP_DURATION_MS) {
            "No se puede dividir más este tramo sin crear fragmentos demasiado cortos."
        }
        return SocialVideoClipRange(range.startMs, midpoint) to SocialVideoClipRange(midpoint, range.endMs)
    }
}

internal object SocialPostPublishPolicy {
    fun canPublish(
        hasCaption: Boolean,
        mediaCount: Int,
        isPreparing: Boolean,
        isUploading: Boolean,
        hasPreflightError: Boolean
    ): Boolean =
        (hasCaption || mediaCount > 0) &&
            mediaCount <= SocialMediaUploadLimits.MAX_PREPARED_MEDIA_PER_POST &&
            !isPreparing && !isUploading && !hasPreflightError
}

internal data class PreparedSocialMedia(
    val uploads: List<SocialMediaUpload>,
    val generatedFiles: List<File>,
    val splitVideoNames: List<String>
)

/**
 * Creates private-cache MP4 clips only for videos above the existing Social upload ceiling.
 * The source URI is never modified; every exported clip is measured before it is returned.
 */
internal class SocialVideoAutoSplitter(context: Context) {
    private val appContext = context.applicationContext

    suspend fun prepare(uris: List<Uri>): PreparedSocialMedia = withContext(Dispatchers.IO) {
        require(SocialMediaUploadLimits.isValidOriginalSelectionCount(uris.distinct().size)) {
            "Selecciona entre 1 y ${SocialMediaUploadLimits.MAX_SELECTED_MEDIA_PER_POST} archivos originales."
        }
        val info = uris.map { SocialMediaFileInspector.inspect(appContext, it) }
        val kindByUri = info.associate { it.uri to supportedSocialMediaType(it.mimeType) }
        val unsupportedNames = info.filter { kindByUri[it.uri] == null }.map { it.displayName }
        if (unsupportedNames.isNotEmpty()) {
            throw IOException("Formato no permitido para Social: ${unsupportedNames.joinToString() }.")
        }
        val oversizedNonVideos = info.filter {
            it.sizeBytes > SocialMediaUploadLimits.MAX_FILE_BYTES && kindByUri[it.uri] != "video"
        }
        if (oversizedNonVideos.isNotEmpty()) {
            val details = oversizedNonVideos.joinToString { "${it.displayName} (${formatMiB(it.sizeBytes)})" }
            throw IOException("Archivo(s) mayor(es) de 50 MiB: $details. Solo los videos se pueden dividir automáticamente.")
        }
        val minimumOutputCount = info.fold(0L) { total, file ->
            total + if (file.sizeBytes > SocialMediaUploadLimits.MAX_FILE_BYTES) {
                SocialVideoSplitPlanner.estimatedClipCount(file.sizeBytes).toLong()
            } else 1L
        }
        if (minimumOutputCount > SocialMediaUploadLimits.MAX_PREPARED_MEDIA_PER_POST.toLong()) {
            val oversizedVideos = info.filter { it.sizeBytes > SocialMediaUploadLimits.MAX_FILE_BYTES }
                .joinToString { "${it.displayName} (al menos ${SocialVideoSplitPlanner.estimatedClipCount(it.sizeBytes)} fragmentos)" }
            throw IOException("La selección requiere al menos $minimumOutputCount elementos y Social admite hasta ${SocialMediaUploadLimits.MAX_PREPARED_MEDIA_PER_POST} archivos preparados por publicación. Videos grandes: $oversizedVideos. No se omitió ningún fragmento.")
        }

        val uploads = mutableListOf<SocialMediaUpload>()
        val generated = mutableListOf<File>()
        val splitNames = mutableListOf<String>()
        try {
            for (file in info) {
                val kind = requireNotNull(kindByUri[file.uri])
                if (file.sizeBytes <= SocialMediaUploadLimits.MAX_FILE_BYTES) {
                    require(file.sizeBytes > 0L) { "El archivo ${file.displayName} está vacío." }
                    uploads += SocialMediaUpload(file.uri, file.mimeType)
                    ensureMediaCount(uploads.size, file.displayName)
                    continue
                }
                if (kind != "video") {
                    throw IOException("${file.displayName} pesa ${formatMiB(file.sizeBytes)}. Solo los videos se pueden dividir automáticamente; el máximo por archivo es 50 MiB.")
                }

                val plannedCount = SocialVideoSplitPlanner.estimatedClipCount(file.sizeBytes)
                if (!SocialVideoSplitPlanner.fitsPostLimit(uploads.size, plannedCount)) {
                    throw IOException("${file.displayName} requiere al menos $plannedCount fragmentos; ya hay ${uploads.size} medios preparados y una publicación admite como máximo ${SocialMediaUploadLimits.MAX_PREPARED_MEDIA_PER_POST}. Quita elementos o reduce el video antes de continuar.")
                }
                val durationMs = try {
                    videoDurationMs(file.uri)
                } catch (error: Exception) {
                    throw IOException("No se pudo leer la duración de ${file.displayName}: ${error.message ?: "video no compatible"}", error)
                }
                val pending = try {
                    SocialVideoSplitPlanner.ranges(durationMs, plannedCount).toMutableList()
                } catch (error: IllegalArgumentException) {
                    throw IOException("${file.displayName}: ${error.message ?: "no se puede dividir en fragmentos seguros"}", error)
                }
                while (pending.isNotEmpty()) {
                    val range = pending.removeAt(0)
                    val output = createOutputFile()
                    try {
                        exportClip(file.uri, range, output)
                    } catch (error: Exception) {
                        output.delete()
                        throw IOException("No se pudo dividir ${file.displayName}: ${error.message ?: "falló la exportación del video"}", error)
                    }
                    if (SocialMediaUploadLimits.isUploadableSize(output.length())) {
                        generated += output
                        uploads += SocialMediaUpload(Uri.fromFile(output), "video/mp4")
                        ensureMediaCount(uploads.size + pending.size, file.displayName)
                    } else {
                        output.delete()
                        val (first, second) = try {
                            SocialVideoSplitPlanner.bisect(range)
                        } catch (error: IllegalArgumentException) {
                            throw IOException("Un fragmento de ${file.displayName} todavía supera 50 MiB y ya no puede dividirse sin perder una parte del video.", error)
                        }
                        if (!SocialVideoSplitPlanner.fitsPostLimit(uploads.size + pending.size, 2)) {
                            throw IOException("${file.displayName} necesita más de los ${SocialMediaUploadLimits.MAX_PREPARED_MEDIA_PER_POST} archivos preparados permitidos para mantener cada fragmento por debajo de 50 MiB. No se omitió ningún fragmento; reduce el video o quita otros medios.")
                        }
                        // Put the first half before the second to keep playback order.
                        pending.add(0, second)
                        pending.add(0, first)
                    }
                }
                splitNames += file.displayName
            }
            ensureMediaCount(uploads.size, "selección")
            PreparedSocialMedia(uploads.toList(), generated.toList(), splitNames.toList())
        } catch (error: Exception) {
            generated.forEach { it.delete() }
            throw error
        }
    }

    private fun ensureMediaCount(count: Int, name: String) {
        if (count > SocialMediaUploadLimits.MAX_PREPARED_MEDIA_PER_POST) {
            throw IOException("La selección de $name supera el máximo de ${SocialMediaUploadLimits.MAX_PREPARED_MEDIA_PER_POST} archivos preparados por publicación. No se omitió ningún archivo; quita medios o elige un video más corto.")
        }
    }

    private fun formatMiB(bytes: Long): String = String.format(Locale.ROOT, "%.1f MiB", bytes.toDouble() / (1024.0 * 1024.0))

    private fun videoDurationMs(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?: throw IOException("No se pudo leer la duración del video.")
        } finally {
            retriever.release()
        }
    }

    private fun createOutputFile(): File {
        val directory = File(appContext.cacheDir, "social-post-splits").apply { mkdirs() }
        return File.createTempFile("social-part-", ".mp4", directory).also { it.delete() }
    }

    private suspend fun exportClip(uri: Uri, range: SocialVideoClipRange, output: File) = suspendCancellableCoroutine<Unit> { continuation ->
        val clipItem = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(range.startMs)
                    .setEndPositionMs(range.endMs)
                    .build()
            )
            .build()
        val transformer = Transformer.Builder(appContext)
            // No video effects or size overrides: Transformer retains the source aspect ratio.
            // Audio is not removed, so it is preserved/transcoded whenever the device supports it.
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, result: ExportResult) {
                    if (continuation.isActive) continuation.resume(Unit) else output.delete()
                }

                override fun onError(
                    composition: androidx.media3.transformer.Composition,
                    result: ExportResult,
                    exception: ExportException
                ) {
                    if (continuation.isActive) continuation.resumeWithException(exception) else output.delete()
                }
            })
            .build()
        continuation.invokeOnCancellation {
            transformer.cancel()
            output.delete()
        }
        try {
            transformer.start(EditedMediaItem.Builder(clipItem).build(), output.absolutePath)
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWithException(error) else output.delete()
        }
    }
}

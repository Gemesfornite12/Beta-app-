package com.example.ui.screens.social

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.media3.effect.Media3Effect
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.VideoRecordEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

internal enum class SocialPhotoFilter(val label: String) {
    ORIGINAL("Original"), MONOCHROME("B/N"), SEPIA("Sepia")
}

internal fun matrixFor(filter: SocialPhotoFilter): FloatArray? = when (filter) {
    SocialPhotoFilter.ORIGINAL -> null
    SocialPhotoFilter.MONOCHROME -> floatArrayOf(
        0.213f, 0.715f, 0.072f, 0f, 0f,
        0.213f, 0.715f, 0.072f, 0f, 0f,
        0.213f, 0.715f, 0.072f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    SocialPhotoFilter.SEPIA -> floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
}

private fun composeColorFilter(filter: SocialPhotoFilter): ColorFilter? =
    matrixFor(filter)?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }

internal enum class SocialVideoFilter(val label: String) {
    ORIGINAL("Original"), MONOCHROME("B/N"), SEPIA("Sepia")
}

/** Column-major RGB transforms used by CameraX's Media3 GPU effect pipeline (linear RGB). */
internal fun videoMatrixFor(filter: SocialVideoFilter): FloatArray? = when (filter) {
    SocialVideoFilter.ORIGINAL -> null
    SocialVideoFilter.MONOCHROME -> floatArrayOf(
        0.2126f, 0.2126f, 0.2126f, 0f,
        0.7152f, 0.7152f, 0.7152f, 0f,
        0.0722f, 0.0722f, 0.0722f, 0f,
        0f, 0f, 0f, 1f
    )
    SocialVideoFilter.SEPIA -> floatArrayOf(
        0.393f, 0.349f, 0.272f, 0f,
        0.769f, 0.686f, 0.534f, 0f,
        0.189f, 0.168f, 0.131f, 0f,
        0f, 0f, 0f, 1f
    )
}

@OptIn(UnstableApi::class)
private class SocialSepiaRgbMatrix : RgbMatrix {
    private val matrix = checkNotNull(videoMatrixFor(SocialVideoFilter.SEPIA))
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = matrix
}

@OptIn(UnstableApi::class)
private fun videoEffectFor(filter: SocialVideoFilter): RgbMatrix? = when (filter) {
    SocialVideoFilter.ORIGINAL -> null
    SocialVideoFilter.MONOCHROME -> RgbFilter.createGrayscaleFilter()
    SocialVideoFilter.SEPIA -> SocialSepiaRgbMatrix()
}

/** Applies a pixel filter into an identically-sized bitmap; the photo bounds and aspect ratio stay unchanged. */
internal fun applyPhotoFilterToBitmap(source: Bitmap, filter: SocialPhotoFilter): Bitmap {
    val values = matrixFor(filter) ?: return source
    val filtered = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(values))
    }
    Canvas(filtered).drawBitmap(source, Rect(0, 0, source.width, source.height), Rect(0, 0, filtered.width, filtered.height), paint)
    return filtered
}

private data class StillPhotoFilterResult(val uri: Uri, val detectedFaceCount: Int)

/** Applies color and face stickers to a still photo without cropping or changing its aspect ratio. */
private fun applyStillPhotoFilter(
    context: Context,
    uri: Uri,
    filter: SocialPhotoFilter,
    faceFilter: SocialFaceFilter
): StillPhotoFilterResult {
    if (filter == SocialPhotoFilter.ORIGINAL && faceFilter == SocialFaceFilter.NONE) {
        return StillPhotoFilterResult(uri, 0)
    }
    val source = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        ?: error("No se pudo abrir la foto capturada.")
    var colorFiltered: Bitmap? = null
    var faceFiltered: Bitmap? = null
    var output: File? = null
    try {
        var baseBitmap = source
        matrixFor(filter)?.let { values ->
            val colorBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            colorFiltered = colorBitmap
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(values))
            }
            Canvas(colorBitmap).drawBitmap(source, Rect(0, 0, source.width, source.height), Rect(0, 0, colorBitmap.width, colorBitmap.height), paint)
            baseBitmap = colorBitmap
        }

        val faceResult = applySocialFaceFilterToBitmap(
            baseBitmap,
            faceFilter,
            allowInPlace = baseBitmap !== source
        )
        if (faceResult.bitmap !== baseBitmap) faceFiltered = faceResult.bitmap
        val finalBitmap = faceResult.bitmap
        if (finalBitmap === source) return StillPhotoFilterResult(uri, faceResult.detectedFaceCount)

        val outputFile = File(context.cacheDir, "social-filter-${UUID.randomUUID()}.jpg")
        output = outputFile
        outputFile.outputStream().use { stream ->
            check(finalBitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream)) { "No se pudo guardar la foto editada." }
        }
        return StillPhotoFilterResult(Uri.fromFile(outputFile), faceResult.detectedFaceCount)
    } catch (error: Exception) {
        output?.delete()
        throw error
    } finally {
        faceFiltered?.recycle()
        colorFiltered?.takeIf { it !== faceFiltered }?.recycle()
        source.recycle()
    }
}

private fun deleteSocialTempUri(context: Context, uri: Uri) {
    if (uri.scheme != "file") return
    runCatching {
        val path = uri.path ?: return@runCatching
        val root = context.cacheDir.canonicalPath + File.separator
        val file = File(path)
        if (file.canonicalPath.startsWith(root)) file.delete()
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun SocialCameraDialog(
    enableAudio: Boolean,
    videoStorageAllowed: Boolean,
    onDismiss: () -> Unit,
    onCapture: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestDismiss by rememberUpdatedState(onDismiss)
    var controllerRef by remember { mutableStateOf<LifecycleCameraController?>(null) }
    val controller = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
            imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
        }
    }
    var flashEnabled by remember { mutableStateOf(false) }
    var videoMode by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var activeRecording by remember { mutableStateOf<androidx.camera.video.Recording?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var capturedPhoto by remember { mutableStateOf<Uri?>(null) }
    var processedPhoto by remember { mutableStateOf<Uri?>(null) }
    var photoAccepted by remember { mutableStateOf(false) }
    var photoFilter by remember { mutableStateOf(SocialPhotoFilter.ORIGINAL) }
    var faceFilter by remember { mutableStateOf(SocialFaceFilter.NONE) }
    var videoFilter by remember { mutableStateOf(SocialVideoFilter.ORIGINAL) }
    var faceFilterMessage by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val latestRecording by rememberUpdatedState(activeRecording)
    val cameraExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val videoEffect = remember(context) {
        Media3Effect(
            context,
            CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
            cameraExecutor
        ) { error -> message = "No se pudo aplicar el efecto de video: ${error.message ?: "error de cámara"}" }
    }

    LaunchedEffect(videoMode, videoFilter, videoEffect) {
        val effects = if (videoMode) videoEffectFor(videoFilter)?.let(::listOf) ?: emptyList() else emptyList()
        videoEffect.setEffects(effects)
    }

    LaunchedEffect(capturedPhoto, photoFilter, faceFilter) {
        val sourcePhoto = capturedPhoto
        if (sourcePhoto == null) {
            val previous = processedPhoto
            processedPhoto = null
            faceFilterMessage = null
            if (previous != null) {
                withContext(NonCancellable + Dispatchers.IO) { deleteSocialTempUri(context, previous) }
            }
            return@LaunchedEffect
        }
        isCapturing = true
        val previousProcessedPhoto = processedPhoto
        try {
            // Let the bounded bitmap operation finish even if the effect is replaced, so a newly
            // written cache URI is always available for cleanup rather than becoming orphaned.
            val rendered = withContext(NonCancellable) {
                withContext(Dispatchers.IO) {
                    applyStillPhotoFilter(context, sourcePhoto, photoFilter, faceFilter)
                }
            }
            if (!currentCoroutineContext().isActive) {
                if (rendered.uri != sourcePhoto) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        deleteSocialTempUri(context, rendered.uri)
                    }
                }
                return@LaunchedEffect
            }
            val previous = processedPhoto
            processedPhoto = rendered.uri
            faceFilterMessage = if (faceFilter != SocialFaceFilter.NONE && rendered.detectedFaceCount == 0) {
                "No se detectó un rostro; prueba con mejor luz y de frente."
            } else {
                null
            }
            if (previous != null && previous != sourcePhoto && previous != rendered.uri) {
                withContext(NonCancellable + Dispatchers.IO) { deleteSocialTempUri(context, previous) }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            processedPhoto = sourcePhoto
            faceFilterMessage = null
            message = error.message ?: "No se pudo procesar el filtro de rostro."
            val previous = previousProcessedPhoto
            if (previous != null && previous != sourcePhoto) {
                withContext(NonCancellable + Dispatchers.IO) { deleteSocialTempUri(context, previous) }
            }
        } finally {
            isCapturing = false
        }
    }

    val latestCapturedPhoto by rememberUpdatedState(capturedPhoto)
    val latestProcessedPhoto by rememberUpdatedState(processedPhoto)
    val latestPhotoAccepted by rememberUpdatedState(photoAccepted)

    DisposableEffect(controller, lifecycleOwner, videoEffect) {
        controller.bindToLifecycle(lifecycleOwner)
        controller.setEffects(setOf(videoEffect))
        controllerRef = controller
        onDispose {
            latestRecording?.stop()
            if (!latestPhotoAccepted) {
                val temporaryPhotos = listOfNotNull(latestProcessedPhoto, latestCapturedPhoto).distinct()
                if (temporaryPhotos.isNotEmpty()) {
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        temporaryPhotos.forEach { deleteSocialTempUri(context, it) }
                    }
                }
            }
            controller.unbind()
            videoEffect.close()
            controllerRef = null
        }
    }

    val flashAvailable = controllerRef?.cameraInfo?.hasFlashUnit() == true
    Dialog(
        onDismissRequest = { if (!recording) latestDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(color = Color(0xFF05070D), modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Cámara de Social", color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Text(if (videoMode) "Video" else "Foto", color = Color(0xFFF9A8D4), fontSize = 13.sp)
                    Button(
                        onClick = { if (!recording && capturedPhoto == null) videoMode = !videoMode },
                        enabled = !recording && capturedPhoto == null && (videoMode || videoStorageAllowed),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202A3A))
                    ) { Text("Cambiar") }
                }

                Box(
                    Modifier.weight(1f).fillMaxWidth().background(Color.Black, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val photo = capturedPhoto
                    if (photo != null) {
                        AsyncImage(
                            model = processedPhoto ?: photo,
                            contentDescription = "Vista previa de la foto con sus filtros",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            colorFilter = if (processedPhoto == null) composeColorFilter(photoFilter) else null
                        )
                        if (isCapturing) CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color(0xFFF472B6)
                        )
                    } else {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                            factory = { viewContext ->
                                PreviewView(viewContext).also { previewView ->
                                    previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
                                    previewView.controller = controller
                                }
                            },
                            update = { it.controller = controller }
                        )
                        if (recording) {
                            Text("● REC", color = Color.Red, modifier = Modifier.align(Alignment.TopStart).padding(14.dp))
                        }
                        if (isCapturing) CircularProgressIndicator(color = Color(0xFFF472B6))
                    }
                }

                if (capturedPhoto != null) {
                    SocialPhotoFilterPicker(selected = photoFilter, onSelect = { photoFilter = it })
                    SocialFaceFilterPicker(selected = faceFilter, onSelect = { faceFilter = it })
                    Text("Los filtros se aplican a la foto; la detección de rostro funciona en el dispositivo.", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    faceFilterMessage?.let { Text(it, color = Color(0xFFFDE68A), fontSize = 11.sp) }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(
                            onClick = {
                                val discardedSource = capturedPhoto
                                capturedPhoto = null
                                photoFilter = SocialPhotoFilter.ORIGINAL
                                faceFilter = SocialFaceFilter.NONE
                                faceFilterMessage = null
                                message = null
                                if (discardedSource != null) {
                                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                                        deleteSocialTempUri(context, discardedSource)
                                    }
                                }
                            },
                            enabled = !isCapturing
                        ) { Text("Repetir") }
                        Button(
                            enabled = !isCapturing && processedPhoto != null,
                            onClick = {
                                val result = processedPhoto ?: return@Button
                                val source = capturedPhoto
                                photoAccepted = true
                                onCapture(result, "image/jpeg")
                                if (source != null && source != result) {
                                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                                        deleteSocialTempUri(context, source)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C))
                        ) { Text("Usar foto") }
                    }
                } else {
                    if (!videoMode) {
                        SocialPhotoFilterPicker(selected = photoFilter, onSelect = { photoFilter = it })
                        SocialFaceFilterPicker(selected = faceFilter, onSelect = { faceFilter = it })
                        Text("El filtro de cara se aplica a la foto capturada, sin subir la imagen para analizarla.", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    } else {
                        SocialVideoFilterPicker(selected = videoFilter, onSelect = { videoFilter = it })
                        Text("El efecto GPU se muestra en la vista previa y queda aplicado al video grabado, sin cambiar el encuadre. Los filtros de cara aún son solo para fotos.", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(
                            onClick = {
                                flashEnabled = !flashEnabled
                                controller.imageCaptureFlashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                                controller.cameraControl?.enableTorch(flashEnabled)
                            },
                            enabled = flashAvailable && !isCapturing,
                            colors = ButtonDefaults.buttonColors(containerColor = if (flashEnabled) Color(0xFFE1306C) else Color(0xFF202A3A)),
                            modifier = Modifier.testTag("social_camera_flash_toggle")
                        ) { Text(if (flashEnabled) "Flash encendido" else "Flash apagado") }
                        IconButton(
                            onClick = {
                                if (videoMode) {
                                    if (recording) {
                                        activeRecording?.stop()
                                    } else {
                                        val values = ContentValues().apply {
                                            put(MediaStore.MediaColumns.DISPLAY_NAME, "omnistudio-social-${UUID.randomUUID()}.mp4")
                                            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/OmniStudio")
                                            }
                                        }
                                        val options = MediaStoreOutputOptions.Builder(
                                            context.contentResolver,
                                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                        ).setContentValues(values).build()
                                        recording = true
                                        activeRecording = controller.startRecording(
                                            options,
                                            AudioConfig.create(enableAudio),
                                            cameraExecutor
                                        ) { event ->
                                            when (event) {
                                                is VideoRecordEvent.Finalize -> {
                                                    recording = false
                                                    activeRecording = null
                                                    if (event.error == VideoRecordEvent.Finalize.ERROR_NONE) {
                                                        onCapture(event.outputResults.outputUri, "video/mp4")
                                                    } else {
                                                        message = "No se pudo finalizar el video (${event.error})."
                                                    }
                                                }
                                                else -> Unit
                                            }
                                        }
                                    }
                                } else {
                                    val photoFile = File(context.cacheDir, "social-camera-${UUID.randomUUID()}.jpg")
                                    val options = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                    isCapturing = true
                                    controller.takePicture(options, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                            isCapturing = false
                                            capturedPhoto = Uri.fromFile(photoFile)
                                        }
                                        override fun onError(error: ImageCaptureException) {
                                            isCapturing = false
                                            message = error.message ?: "No se pudo tomar la foto."
                                        }
                                    })
                                }
                            },
                            enabled = !isCapturing,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Surface(color = if (recording) Color.Red else Color.White, shape = RoundedCornerShape(50), modifier = Modifier.size(54.dp)) {}
                        }
                        Button(onClick = { if (!recording) latestDismiss() }, enabled = !recording) { Text("Cerrar") }
                    }
                    if (videoMode && !enableAudio) {
                        Text("El permiso de micrófono no está disponible; el video se grabará sin sonido.", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    }
                    if (!videoStorageAllowed) {
                        Text("En Android 9 o anterior se requiere permiso de almacenamiento para grabar videos.", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    }
                }
                message?.let { Text(it, color = Color(0xFFFCA5A5), fontSize = 12.sp) }
            }
        }
    }
}


@Composable
private fun SocialPhotoFilterPicker(
    selected: SocialPhotoFilter,
    onSelect: (SocialPhotoFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Filtros para foto", color = Color.White, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SocialPhotoFilter.entries.forEach { filter ->
                Button(
                    onClick = { onSelect(filter) },
                    modifier = Modifier.weight(1f).testTag("social_photo_filter_${filter.name.lowercase()}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected == filter) Color(0xFFE1306C) else Color(0xFF202A3A)
                    )
                ) { Text(filter.label, color = Color.White, fontSize = 12.sp) }
            }
        }
    }
}


@Composable
private fun SocialFaceFilterPicker(
    selected: SocialFaceFilter,
    onSelect: (SocialFaceFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Filtros de cara · foto", color = Color.White, fontSize = 13.sp)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SocialFaceFilter.entries.forEach { filter ->
                Button(
                    onClick = { onSelect(filter) },
                    modifier = Modifier.testTag("social_face_filter_${filter.name.lowercase()}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected == filter) Color(0xFFE1306C) else Color(0xFF202A3A)
                    )
                ) { Text(filter.label, color = Color.White, fontSize = 12.sp, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun SocialVideoFilterPicker(
    selected: SocialVideoFilter,
    onSelect: (SocialVideoFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Filtros para video", color = Color.White, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SocialVideoFilter.entries.forEach { filter ->
                Button(
                    onClick = { onSelect(filter) },
                    modifier = Modifier.weight(1f).testTag("social_video_filter_${filter.name.lowercase()}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected == filter) Color(0xFFE1306C) else Color(0xFF202A3A)
                    )
                ) { Text(filter.label, color = Color.White, fontSize = 12.sp) }
            }
        }
    }
}

package com.example.ui.screens.social

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.media.ExifInterface
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.OutputTransform
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
import androidx.compose.foundation.layout.BoxScope
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
import com.example.BuildConfig
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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

private enum class SocialCameraToolTab(val label: String) {
    EFFECTS("Efectos"), FILTERS("Filtros"), BACKGROUNDS("Fondos")
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

@OptIn(TransformExperimental::class)
internal data class SocialPreviewSegmentationFrame(
    val mask: PersonSegmentationMask,
    val sourceTransform: OutputTransform,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int
)

/** Applies color and face stickers to a still photo without cropping or changing its aspect ratio. */
private fun applyStillPhotoFilter(
    context: Context,
    uri: Uri,
    filter: SocialPhotoFilter,
    faceFilter: SocialFaceFilter,
    background: SocialBackgroundPreset
): StillPhotoFilterResult {
    if (filter == SocialPhotoFilter.ORIGINAL && faceFilter == SocialFaceFilter.NONE && background == SocialBackgroundPreset.ORIGINAL) {
        return StillPhotoFilterResult(uri, 0)
    }
    val source = decodeSocialPhotoUpright(context, uri)
    var backgroundFiltered: Bitmap? = null
    var colorFiltered: Bitmap? = null
    var faceFiltered: Bitmap? = null
    var output: File? = null
    try {
        var baseBitmap = source
        if (background != SocialBackgroundPreset.ORIGINAL) {
            val personMask = segmentSocialPhoto(context, source)
            val replacement = applySocialBackgroundToBitmap(source, personMask, background)
            backgroundFiltered = replacement
            baseBitmap = replacement
        }
        matrixFor(filter)?.let { values ->
            val colorBitmap = Bitmap.createBitmap(baseBitmap.width, baseBitmap.height, Bitmap.Config.ARGB_8888)
            colorFiltered = colorBitmap
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(values))
            }
            Canvas(colorBitmap).drawBitmap(baseBitmap, Rect(0, 0, baseBitmap.width, baseBitmap.height), Rect(0, 0, colorBitmap.width, colorBitmap.height), paint)
            baseBitmap = colorBitmap
        }

        val faceResult = applySocialFaceFilterToBitmap(
            context,
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
        backgroundFiltered?.takeIf { it !== colorFiltered && it !== faceFiltered }?.recycle()
        source.recycle()
    }
}

private fun decodeSocialPhotoUpright(context: Context, uri: Uri): Bitmap {
    val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
        ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val rotation = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        ?: error("No se pudo abrir la foto capturada.")
    if (rotation == 0f) return decoded
    val matrix = Matrix().apply { postRotate(rotation) }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { upright ->
        if (upright !== decoded) decoded.recycle()
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

@OptIn(UnstableApi::class, TransformExperimental::class)
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
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            imageAnalysisTargetSize = CameraController.OutputSize(Size(1280, 720))
            imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
        }
    }
    var flashEnabled by remember { mutableStateOf(false) }
    var videoMode by remember { mutableStateOf(false) }
    var frontCameraSelected by remember { mutableStateOf(false) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var recording by remember { mutableStateOf(false) }
    var activeRecording by remember { mutableStateOf<androidx.camera.video.Recording?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var capturedPhoto by remember { mutableStateOf<Uri?>(null) }
    var processedPhoto by remember { mutableStateOf<Uri?>(null) }
    var photoAccepted by remember { mutableStateOf(false) }
    var photoFilter by remember { mutableStateOf(SocialPhotoFilter.ORIGINAL) }
    var faceFilter by remember { mutableStateOf(SocialFaceFilter.NONE) }
    var activeCameraToolTab by remember { mutableStateOf(SocialCameraToolTab.EFFECTS) }
    var toolsPanelOpen by remember { mutableStateOf(false) }
    var selectedBackground by remember { mutableStateOf(SocialBackgroundPreset.ORIGINAL) }
    var videoFilter by remember { mutableStateOf(SocialVideoFilter.ORIGINAL) }
    var faceFilterMessage by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var previewFaceResult by remember { mutableStateOf<SocialFaceResult>(emptyList()) }
    var latestPreviewSegmentationFrame by remember { mutableStateOf<SocialPreviewSegmentationFrame?>(null) }
    var latestTransformReady by remember { mutableStateOf<Boolean?>(null) }
    var latestMappedFaceCount by remember { mutableStateOf<Int?>(null) }
    val latestRecording by rememberUpdatedState(activeRecording)
    val cameraExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val faceAnalysisExecutor = remember(context) { Executors.newSingleThreadExecutor() }
    val faceResultsCallback = rememberUpdatedState<(SocialFaceResult, PersonSegmentationMask?, OutputTransform, Int, Int, Int) -> Unit> { result, mask, source, width, height, rotation ->
        val preview = previewViewRef
        val targetTransform = preview?.outputTransform
        val previewWidth = preview?.width ?: 0
        val previewHeight = preview?.height ?: 0
        val mappedFaces = mapSocialFacesToPreview(
            result,
            source,
            targetTransform,
            width,
            height,
            rotation,
            previewWidth,
            previewHeight
        )
        latestTransformReady = targetTransform != null && width > 0 && height > 0 && previewWidth > 0 && previewHeight > 0
        latestMappedFaceCount = mappedFaces.size
        previewFaceResult = mappedFaces
        latestPreviewSegmentationFrame = mask?.let { SocialPreviewSegmentationFrame(it, source, width, height, rotation) }
    }
    val faceErrorCallback = rememberUpdatedState<(String) -> Unit> { error -> message = error }
    val faceAnalyzerCreation = remember(context) {
        runCatching {
            SocialFaceImageAnalyzer(
                context,
                onResults = { result, mask, source, width, height, rotation ->
                    cameraExecutor.execute { faceResultsCallback.value(result, mask, source, width, height, rotation) }
                },
                onError = { error -> cameraExecutor.execute { faceErrorCallback.value(error) } }
            )
        }.onFailure { failure ->
            // Initialization diagnostics contain exception details only; no image, landmark, or identity data is logged.
            Log.e("SocialCamera", "Could not construct the local MediaPipe analyzer.", failure)
        }
    }
    val faceAnalyzer = faceAnalyzerCreation.getOrNull()
    val initializationDiagnostics = faceAnalyzer?.initializationDiagnostics().orEmpty()
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

    LaunchedEffect(controller, videoMode, capturedPhoto, faceAnalyzer, faceFilter, selectedBackground) {
        val faceEffectsActive = faceFilter != SocialFaceFilter.NONE
        val backgroundReplacementActive = selectedBackground != SocialBackgroundPreset.ORIGINAL
        faceAnalyzer?.setRequestedEffects(faceEffectsActive, backgroundReplacementActive)
        val analysisActive = !videoMode && capturedPhoto == null &&
            faceAnalyzer?.hasActiveLocalEffects() == true
        if (analysisActive) {
            controller.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            controller.setEnabledUseCases(
                CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE or CameraController.IMAGE_ANALYSIS
            )
            controller.setImageAnalysisAnalyzer(faceAnalysisExecutor, requireNotNull(faceAnalyzer))
        } else {
            controller.clearImageAnalysisAnalyzer()
            controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
            previewFaceResult = emptyList()
            latestPreviewSegmentationFrame = null
        }
        faceAnalyzerCreation.exceptionOrNull()?.let { failure ->
            val failureType = failure.javaClass.simpleName.ifBlank { "error" }
            message = "No se pudo iniciar el filtro local ($failureType). Las fotos y videos siguen disponibles."
        }
    }

    LaunchedEffect(capturedPhoto, photoFilter, faceFilter, selectedBackground) {
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
                    applyStillPhotoFilter(context, sourcePhoto, photoFilter, faceFilter, selectedBackground)
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
            message = if (selectedBackground != SocialBackgroundPreset.ORIGINAL) {
                selectedBackground = SocialBackgroundPreset.ORIGINAL
                error.message ?: "No se pudo reemplazar el fondo; se conservó la foto original."
            } else {
                error.message ?: "No se pudo procesar el filtro local."
            }
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

    DisposableEffect(controller, lifecycleOwner, videoEffect, faceAnalyzer) {
        SocialFaceCrashEvidence.beginCameraSession(context)
        controller.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
        controller.bindToLifecycle(lifecycleOwner)
        controller.setEffects(setOf(videoEffect))
        controllerRef = controller
        onDispose {
            SocialFaceCrashEvidence.endCameraSession(context)
            latestRecording?.stop()
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            faceAnalysisExecutor.shutdown()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val stoppedGracefully = runCatching {
                    faceAnalysisExecutor.awaitTermination(5, TimeUnit.SECONDS)
                }.getOrDefault(false)
                if (stoppedGracefully) {
                    faceAnalyzer?.close()
                } else {
                    // A synchronous native VIDEO inference must not race FaceLandmarker.close().
                    // Interrupt and wait once more; if native code still has the worker, leave it
                    // alive rather than closing MediaPipe concurrently with its active call.
                    faceAnalysisExecutor.shutdownNow()
                    val stoppedAfterInterrupt = runCatching {
                        faceAnalysisExecutor.awaitTermination(2, TimeUnit.SECONDS)
                    }.getOrDefault(false)
                    if (stoppedAfterInterrupt) {
                        faceAnalyzer?.close()
                    } else {
                        Log.w("SocialCamera", "Face analyzer did not stop; deferring MediaPipe close to avoid racing active inference.")
                    }
                }
            }
            if (!latestPhotoAccepted) {
                val temporaryPhotos = listOfNotNull(latestProcessedPhoto, latestCapturedPhoto).distinct()
                if (temporaryPhotos.isNotEmpty()) {
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        temporaryPhotos.forEach { deleteSocialTempUri(context, it) }
                    }
                }
            }
            videoEffect.close()
            controllerRef = null
        }
    }

    val flashAvailable = controllerRef?.cameraInfo?.hasFlashUnit() == true
    val toggleFlash = {
        val enabled = !flashEnabled
        flashEnabled = enabled
        controller.imageCaptureFlashMode = if (enabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        controller.cameraControl?.enableTorch(enabled)
        Unit
    }
    val toggleCameraLens = {
        val nextIsFront = !frontCameraSelected
        val nextSelector = if (nextIsFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        runCatching { controller.cameraSelector = nextSelector }
            .onSuccess {
                frontCameraSelected = nextIsFront
                flashEnabled = false
                controller.cameraControl?.enableTorch(false)
                previewFaceResult = emptyList()
            }
            .onFailure { message = "No se pudo cambiar de cámara en este dispositivo." }
        Unit
    }
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
                    Modifier.weight(1f).fillMaxWidth().background(Color.Black, RoundedCornerShape(16.dp)).testTag("social_camera_preview"),
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
                                    previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    previewView.controller = controller
                                    previewViewRef = previewView
                                }
                            },
                            update = { previewView ->
                                previewViewRef = previewView
                                previewView.controller = controller
                            }
                        )
                        if (!videoMode) {
                            AndroidView(
                                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                                factory = { viewContext -> SocialFaceOverlayView(viewContext) },
                                update = { overlay ->
                                    overlay.update(
                                        faceFilter,
                                        previewFaceResult,
                                        selectedBackground,
                                        latestPreviewSegmentationFrame,
                                        previewViewRef?.outputTransform
                                    )
                                }
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { if (!recording) latestDismiss() },
                                enabled = !recording,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x9905070D))
                            ) { Text("×", color = Color.White, fontSize = 20.sp) }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = { toolsPanelOpen = !toolsPanelOpen },
                                    enabled = !recording,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x9905070D)),
                                    modifier = Modifier.testTag("social_camera_tools_toggle")
                                ) { Text(if (toolsPanelOpen) "Ocultar" else if (videoMode) "Filtros" else "Efectos", color = Color.White, fontSize = 11.sp) }
                                Button(
                                    onClick = toggleCameraLens,
                                    enabled = !recording,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x9905070D))
                                ) { Text(if (frontCameraSelected) "Trasera" else "Frontal", color = Color.White, fontSize = 11.sp) }
                                Button(
                                    onClick = toggleFlash,
                                    enabled = flashAvailable && !isCapturing,
                                    colors = ButtonDefaults.buttonColors(containerColor = if (flashEnabled) Color(0xFFE1306C) else Color(0x9905070D))
                                ) { Text(if (flashEnabled) "⚡" else "Flash", color = Color.White, fontSize = 11.sp) }
                            }
                        }
                        if (!videoMode) {
                            val faceStatus = if (previewFaceResult.isNotEmpty()) "Rostro detectado" else if (faceFilter != SocialFaceFilter.NONE) "Centra tu rostro" else null
                            faceStatus?.let {
                                Text(
                                    it,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 52.dp)
                                        .background(Color(0x9905070D), RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                        SocialFaceDiagnosticButton(
                    faceEffectSelected = faceFilter != SocialFaceFilter.NONE,
                    initializationFailure = initializationDiagnostics.isNotEmpty(),
                    analyzerCreationFailure = faceAnalyzerCreation.isFailure
                ) {
                    if (latestTransformReady != null && latestMappedFaceCount != null) {
                        faceAnalyzer?.recordPreviewMapping(latestTransformReady == true, latestMappedFaceCount ?: 0)
                    }
                    val snapshot = faceAnalyzer?.liveDiagnosticSnapshot() ?: SocialFaceLiveDiagnosticSnapshot(
                        analyzerAvailable = false,
                        faceEffectRequested = faceFilter != SocialFaceFilter.NONE,
                        lastErrorType = faceAnalyzerCreation.exceptionOrNull()?.javaClass?.simpleName?.take(64)
                    )
                    val report = buildString {
                        append(buildSocialFaceLiveDiagnosticReport(
                            snapshot = snapshot,
                            sdkApi = Build.VERSION.SDK_INT,
                            buildVariant = BuildConfig.BUILD_TYPE
                        ))
                        SocialFaceCrashEvidence.previousReport(context)?.let { previous ->
                            appendLine()
                            append(previous)
                        }
                    }
                    val copied = runCatching {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            ?: error("Clipboard unavailable")
                        clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico MediaPipe", report))
                    }.isSuccess
                    Toast.makeText(
                        context,
                        if (copied) "Diagnóstico copiado" else "No se pudo copiar el diagnóstico",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                        if (recording) {
                            Text("● REC", color = Color.Red, modifier = Modifier.align(Alignment.BottomStart).padding(14.dp))
                        }
                        if (isCapturing) CircularProgressIndicator(color = Color(0xFFF472B6))
                    }
                }

                if (capturedPhoto != null) {
                    Button(
                        onClick = { toolsPanelOpen = !toolsPanelOpen },
                        modifier = Modifier.fillMaxWidth().testTag("social_camera_tools_toggle")
                    ) { Text(if (toolsPanelOpen) "Ocultar efectos y fondos" else "Efectos, filtros y fondos") }
                    if (toolsPanelOpen) {
                        SocialCameraToolTabs(selected = activeCameraToolTab, onSelect = { activeCameraToolTab = it })
                        when (activeCameraToolTab) {
                            SocialCameraToolTab.EFFECTS -> SocialFaceFilterPicker(selected = faceFilter, onSelect = { faceFilter = it })
                            SocialCameraToolTab.FILTERS -> SocialPhotoFilterPicker(selected = photoFilter, onSelect = { photoFilter = it })
                            SocialCameraToolTab.BACKGROUNDS -> SocialBackgroundPicker(selected = selectedBackground, onSelect = { selectedBackground = it })
                        }
                        Text(
                            when (activeCameraToolTab) {
                                SocialCameraToolTab.EFFECTS -> "El efecto se aplica de inmediato y queda en la foto guardada. Análisis local."
                                SocialCameraToolTab.FILTERS -> "El filtro de color queda en la foto guardada."
                                SocialCameraToolTab.BACKGROUNDS -> "El fondo seleccionado reemplaza el fondo de la foto en el dispositivo."
                            },
                            color = Color(0xFFCBD5E1), fontSize = 11.sp
                        )
                        if (activeCameraToolTab == SocialCameraToolTab.EFFECTS) {
                            faceFilterMessage?.let { Text(it, color = Color(0xFFFDE68A), fontSize = 11.sp) }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(
                            onClick = {
                                val discardedSource = capturedPhoto
                                capturedPhoto = null
                                photoFilter = SocialPhotoFilter.ORIGINAL
                                faceFilter = SocialFaceFilter.NONE
                                selectedBackground = SocialBackgroundPreset.ORIGINAL
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
                    if (toolsPanelOpen && !videoMode) {
                        SocialCameraToolTabs(selected = activeCameraToolTab, onSelect = { activeCameraToolTab = it })
                        when (activeCameraToolTab) {
                            SocialCameraToolTab.EFFECTS -> SocialFaceFilterPicker(selected = faceFilter, onSelect = { faceFilter = it })
                            SocialCameraToolTab.FILTERS -> SocialPhotoFilterPicker(selected = photoFilter, onSelect = { photoFilter = it })
                            SocialCameraToolTab.BACKGROUNDS -> SocialBackgroundPicker(selected = selectedBackground, onSelect = { selectedBackground = it })
                        }
                        Text(
                            when (activeCameraToolTab) {
                                SocialCameraToolTab.EFFECTS -> "Los efectos se aplican de inmediato y quedan en la foto guardada. Análisis local."
                                SocialCameraToolTab.FILTERS -> "Los filtros de color se conservan en la foto capturada."
                                SocialCameraToolTab.BACKGROUNDS -> "El fondo seleccionado reemplaza el fondo en la vista previa y en la foto guardada."
                            },
                            color = Color(0xFFCBD5E1), fontSize = 11.sp
                        )
                    } else if (videoMode) {
                        if (toolsPanelOpen) SocialVideoFilterPicker(selected = videoFilter, onSelect = { videoFilter = it })
                        Text("El video incluye filtros de color. Los stickers y el reemplazo de fondo no se aplican a videos grabados.", color = Color(0xFFCBD5E1), fontSize = 11.sp)
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
                                            toolsPanelOpen = true
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
internal fun BoxScope.SocialFaceDiagnosticButton(
    faceEffectSelected: Boolean,
    initializationFailure: Boolean,
    analyzerCreationFailure: Boolean,
    onClick: () -> Unit
) {
    if (shouldShowSocialFaceDiagnostic(
            faceEffectSelected = faceEffectSelected,
            initializationFailure = initializationFailure,
            analyzerCreationFailure = analyzerCreationFailure
        )) {
        Button(
            onClick = onClick,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).testTag("social_camera_copy_diagnostic"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xCC05070D))
        ) { Text("Copiar diagnóstico", fontSize = 11.sp, color = Color.White) }
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
        Text("Efectos de rostro", color = Color.White, fontSize = 13.sp)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SocialFaceFilter.entries.forEach { filter ->
                Button(
                    onClick = { onSelect(filter) },
                    modifier = Modifier.size(width = 84.dp, height = 68.dp).testTag("social_face_filter_${filter.name.lowercase()}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected == filter) Color(0xFFE1306C) else Color(0xFF202A3A)
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(
                            when (filter) {
                                SocialFaceFilter.NONE -> "○"
                                SocialFaceFilter.DOG_EARS -> "🐶"
                                SocialFaceFilter.GLASSES -> "👓"
                                SocialFaceFilter.CROWN -> "👑"
                            },
                            color = Color.White, fontSize = 20.sp
                        )
                        Text(filter.label, color = Color.White, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialCameraToolTabs(
    selected: SocialCameraToolTab,
    onSelect: (SocialCameraToolTab) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SocialCameraToolTab.entries.forEach { tab ->
            Button(
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f).testTag("social_camera_tab_${tab.name.lowercase()}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected == tab) Color(0xFFE1306C) else Color(0xFF202A3A)
                )
            ) { Text(tab.label, color = Color.White, fontSize = 11.sp, maxLines = 1) }
        }
    }
}

@Composable
private fun SocialBackgroundPicker(
    selected: SocialBackgroundPreset,
    onSelect: (SocialBackgroundPreset) -> Unit
) {
    val backgrounds = listOf(
        SocialBackgroundPreset.ORIGINAL to Color(0xFF334155),
        SocialBackgroundPreset.SKY to Color(0xFF60A5FA),
        SocialBackgroundPreset.SUNSET to Color(0xFFF97316),
        SocialBackgroundPreset.LAVENDER to Color(0xFFA78BFA)
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Fondos", color = Color.White, fontSize = 13.sp)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            backgrounds.forEach { (preset, swatch) ->
                Button(
                    onClick = { onSelect(preset) },
                    modifier = Modifier.size(width = 82.dp, height = 68.dp)
                        .testTag("social_background_${preset.name.lowercase()}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected == preset) Color(0xFFE1306C) else Color(0xFF202A3A)
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(Modifier.size(38.dp, 24.dp).background(swatch, RoundedCornerShape(6.dp)))
                        Text(preset.label, color = Color.White, fontSize = 10.sp, maxLines = 1)
                    }
                }
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

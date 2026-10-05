package com.example.ui.screens.social

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import androidx.camera.core.ImageProxy
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal enum class SocialFaceFilter(val label: String) {
    NONE("Sin filtro"),
    DOG_EARS("Orejas"),
    GLASSES("Gafas"),
    CROWN("Corona")
}

internal enum class SocialBackgroundPreset(val label: String, val topColor: Int, val bottomColor: Int) {
    ORIGINAL("Original", Color.TRANSPARENT, Color.TRANSPARENT),
    SKY("Cielo", 0xff38bdf8.toInt(), 0xff1d4ed8.toInt()),
    SUNSET("Atardecer", 0xfffb7185.toInt(), 0xff7c2d12.toInt()),
    LAVENDER("Lavanda", 0xffc4b5fd.toInt(), 0xff6d28d9.toInt())
}

internal data class PersonSegmentationMask(
    val width: Int,
    val height: Int,
    val personConfidence: FloatArray
)

/** Minimal normalized coordinates consumed by the sticker renderer; no mesh/blendshape payload is retained. */
internal data class SocialFacePoint(val x: Float, val y: Float)
internal data class SocialFace(
    val sideA: SocialFacePoint,
    val sideB: SocialFacePoint,
    val top: SocialFacePoint,
    val bottom: SocialFacePoint,
    val leftEyeOuter: SocialFacePoint,
    val leftEyeInner: SocialFacePoint,
    val rightEyeInner: SocialFacePoint,
    val rightEyeOuter: SocialFacePoint
) {
    fun requiredPoints() = listOf(sideA, sideB, top, bottom, leftEyeOuter, leftEyeInner, rightEyeInner, rightEyeOuter)

    fun withPoints(points: List<SocialFacePoint>) = copy(
        sideA = points[0], sideB = points[1], top = points[2], bottom = points[3],
        leftEyeOuter = points[4], leftEyeInner = points[5],
        rightEyeInner = points[6], rightEyeOuter = points[7]
    )
}
internal typealias SocialFaceResult = List<SocialFace>

internal data class FaceFilterBitmapResult(
    val bitmap: Bitmap,
    val detectedFaceCount: Int
)

/** Creates a transparent background-replacement layer in the original camera-buffer coordinates. */
internal fun createSocialBackgroundOverlay(
    mask: PersonSegmentationMask,
    preset: SocialBackgroundPreset,
    frameWidth: Int,
    frameHeight: Int,
    rotationDegrees: Int
): Bitmap? {
    if (preset == SocialBackgroundPreset.ORIGINAL || frameWidth <= 0 || frameHeight <= 0 ||
        mask.width <= 0 || mask.height <= 0 || mask.personConfidence.size < mask.width * mask.height
    ) return null

    val maskPixels = IntArray(mask.width * mask.height) { index ->
        val alpha = (mask.personConfidence[index].coerceIn(0f, 1f) * 255f).toInt()
        Color.argb(alpha, 255, 255, 255)
    }
    var personMask = Bitmap.createBitmap(maskPixels, mask.width, mask.height, Bitmap.Config.ARGB_8888)
    val rotation = ((rotationDegrees % 360) + 360) % 360
    if (rotation != 0) {
        val inverseRotation = Matrix().apply { postRotate(-rotation.toFloat()) }
        val unrotated = Bitmap.createBitmap(personMask, 0, 0, personMask.width, personMask.height, inverseRotation, true)
        if (unrotated !== personMask) personMask.recycle()
        personMask = unrotated
    }
    if (personMask.width != frameWidth || personMask.height != frameHeight) {
        val scaled = Bitmap.createScaledBitmap(personMask, frameWidth, frameHeight, true)
        if (scaled !== personMask) personMask.recycle()
        personMask = scaled
    }

    val background = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(background)
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, 0f, frameHeight.toFloat(), preset.topColor, preset.bottomColor, Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, frameWidth.toFloat(), frameHeight.toFloat(), backgroundPaint)
    val personCutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    canvas.drawBitmap(personMask, 0f, 0f, personCutoutPaint)
    personCutoutPaint.xfermode = null
    personMask.recycle()
    return background
}

/** Applies a local person mask to a still image, preserving the person and replacing the backdrop. */
internal fun applySocialBackgroundToBitmap(
    source: Bitmap,
    mask: PersonSegmentationMask,
    preset: SocialBackgroundPreset
): Bitmap {
    if (preset == SocialBackgroundPreset.ORIGINAL) return source
    if (source.width <= 0 || source.height <= 0 || mask.width <= 0 || mask.height <= 0 ||
        mask.personConfidence.size < mask.width * mask.height
    ) error("No se pudo aplicar el fondo seleccionado.")

    // Build the person coverage in photo coordinates, then blend against an opaque gradient.
    // Avoid drawing a masked transparent bitmap over the photo: that can alter pixels whose
    // segmentation confidence is exactly 1.0 through bitmap compositing/filtering.
    val confidencePixels = IntArray(mask.width * mask.height) { index ->
        val alpha = (mask.personConfidence[index].coerceIn(0f, 1f) * 255f).toInt()
        Color.argb(alpha, 255, 255, 255)
    }
    var personMask = Bitmap.createBitmap(confidencePixels, mask.width, mask.height, Bitmap.Config.ARGB_8888)
    if (personMask.width != source.width || personMask.height != source.height) {
        val scaled = Bitmap.createScaledBitmap(personMask, source.width, source.height, true)
        if (scaled !== personMask) personMask.recycle()
        personMask = scaled
    }

    val background = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, 0f, source.height.toFloat(), preset.topColor, preset.bottomColor, Shader.TileMode.CLAMP)
    }
    Canvas(background).drawRect(0f, 0f, source.width.toFloat(), source.height.toFloat(), backgroundPaint)

    val output = checkNotNull(source.copy(Bitmap.Config.ARGB_8888, true))
    // Reuse scanline buffers rather than allocating full-photo pixel arrays.
    val sourceRow = IntArray(source.width)
    val backgroundRow = IntArray(source.width)
    val personRow = IntArray(source.width)
    val outputRow = IntArray(source.width)
    for (y in 0 until source.height) {
        source.getPixels(sourceRow, 0, source.width, 0, y, source.width, 1)
        background.getPixels(backgroundRow, 0, source.width, 0, y, source.width, 1)
        personMask.getPixels(personRow, 0, source.width, 0, y, source.width, 1)
        for (x in 0 until source.width) {
            val confidence = Color.alpha(personRow[x])
            val sourcePixel = sourceRow[x]
            outputRow[x] = when (confidence) {
                255 -> sourcePixel // A fully confident person pixel is preserved exactly.
                0 -> backgroundRow[x]
                else -> {
                    val sourceWeight = confidence / 255f * (Color.alpha(sourcePixel) / 255f)
                    val backgroundWeight = 1f - sourceWeight
                    Color.argb(
                        255,
                        (Color.red(sourcePixel) * sourceWeight + Color.red(backgroundRow[x]) * backgroundWeight + 0.5f).toInt(),
                        (Color.green(sourcePixel) * sourceWeight + Color.green(backgroundRow[x]) * backgroundWeight + 0.5f).toInt(),
                        (Color.blue(sourcePixel) * sourceWeight + Color.blue(backgroundRow[x]) * backgroundWeight + 0.5f).toInt()
                    )
                }
            }
        }
        output.setPixels(outputRow, 0, source.width, 0, y, source.width, 1)
    }
    personMask.recycle()
    background.recycle()
    return output
}

private const val FACE_LANDMARKER_MODEL = "face_landmarker.task"
private const val SELFIE_SEGMENTER_MODEL = "selfie_segmenter.tflite"
private const val MIN_ANALYSIS_INTERVAL_MS = 33L

/** Creates the official on-device MediaPipe Face Landmarker with an already-bundled model. */
internal fun createSocialFaceLandmarker(
    context: Context,
    runningMode: RunningMode,
    resultListener: ((FaceLandmarkerResult, MPImage) -> Unit)? = null,
    errorListener: ((RuntimeException) -> Unit)? = null
): FaceLandmarker {
    val baseOptions = BaseOptions.builder()
        .setModelAssetPath(FACE_LANDMARKER_MODEL)
        .build()
    val optionsBuilder = FaceLandmarker.FaceLandmarkerOptions.builder()
        .setBaseOptions(baseOptions)
        .setRunningMode(runningMode)
        .setNumFaces(4)
        .setMinFaceDetectionConfidence(0.5f)
        .setMinFacePresenceConfidence(0.5f)
        .setMinTrackingConfidence(0.5f)
    if (runningMode == RunningMode.LIVE_STREAM) {
        optionsBuilder
            .setResultListener(requireNotNull(resultListener) { "LIVE_STREAM needs a result listener." })
            .setErrorListener(requireNotNull(errorListener) { "LIVE_STREAM needs an error listener." })
    }
    return FaceLandmarker.createFromOptions(context, optionsBuilder.build())
}

/** Creates MediaPipe's on-device Image Segmenter with the official binary selfie model. */
internal fun createSocialImageSegmenter(
    context: Context,
    runningMode: RunningMode,
    resultListener: ((ImageSegmenterResult, MPImage) -> Unit)? = null,
    errorListener: ((RuntimeException) -> Unit)? = null
): ImageSegmenter {
    val baseOptions = BaseOptions.builder()
        .setModelAssetPath(SELFIE_SEGMENTER_MODEL)
        .build()
    val optionsBuilder = ImageSegmenter.ImageSegmenterOptions.builder()
        .setBaseOptions(baseOptions)
        .setRunningMode(runningMode)
        .setOutputCategoryMask(false)
        .setOutputConfidenceMasks(true)
    if (runningMode == RunningMode.LIVE_STREAM) {
        optionsBuilder
            .setResultListener(requireNotNull(resultListener) { "LIVE_STREAM needs a segmentation result listener." })
            .setErrorListener(requireNotNull(errorListener) { "LIVE_STREAM needs a segmentation error listener." })
    }
    return ImageSegmenter.createFromOptions(context, optionsBuilder.build())
}

/** Copies the person-confidence channel before closing MediaPipe's result mask image. */
internal fun personSegmentationMaskFromResult(result: ImageSegmenterResult): PersonSegmentationMask? {
    val masks = result.confidenceMasks().orElse(emptyList())
    try {
        val personMask = masks.getOrNull(if (masks.size > 1) 1 else 0) ?: return null
        val pixels = personMask.width * personMask.height
        val floatBuffer = ByteBufferExtractor.extract(personMask)
            .duplicate()
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        if (pixels <= 0 || floatBuffer.remaining() < pixels) return null
        return PersonSegmentationMask(
            width = personMask.width,
            height = personMask.height,
            personConfidence = FloatArray(pixels).also(floatBuffer::get)
        )
    } finally {
        masks.forEach { it.close() }
        result.categoryMask().ifPresent { it.close() }
    }
}

/** Segments an upright still photo locally; no frame or image is sent to a server. */
internal fun segmentSocialPhoto(context: Context, source: Bitmap): PersonSegmentationMask {
    val segmenter = createSocialImageSegmenter(context, RunningMode.IMAGE)
    try {
        val mpImage = BitmapImageBuilder(source).build()
        try {
            return personSegmentationMaskFromResult(segmenter.segment(mpImage))
                ?: error("No se pudo obtener la máscara de persona.")
        } finally {
            mpImage.close()
        }
    } finally {
        segmenter.close()
    }
}

/**
 * Applies a face sticker to a captured photo with MediaPipe's IMAGE mode. This runs on the caller's
 * background dispatcher; both inference and rendering are local and never transmit image data.
 */
internal fun applySocialFaceFilterToBitmap(
    context: Context,
    source: Bitmap,
    filter: SocialFaceFilter,
    allowInPlace: Boolean = false
): FaceFilterBitmapResult {
    if (filter == SocialFaceFilter.NONE) return FaceFilterBitmapResult(source, 0)

    val landmarker = createSocialFaceLandmarker(context, RunningMode.IMAGE)
    val landmarks = try {
        val mpImage = BitmapImageBuilder(source).build()
        try {
            landmarker.detect(mpImage).toSocialFaceResult()
        } finally {
            mpImage.close()
        }
    } finally {
        landmarker.close()
    }
    if (landmarks.isEmpty()) return FaceFilterBitmapResult(source, 0)

    val output = if (allowInPlace && source.isMutable && source.config == Bitmap.Config.ARGB_8888) {
        source
    } else {
        checkNotNull(source.copy(Bitmap.Config.ARGB_8888, true))
    }
    val canvas = Canvas(output)
    landmarks.forEach { face -> drawSocialFaceSticker(canvas, output.width.toFloat(), output.height.toFloat(), face, filter) }
    return FaceFilterBitmapResult(output, landmarks.size)
}

/** Holds CameraX's media image until both MediaPipe live tasks complete or fail. */
@OptIn(TransformExperimental::class)
internal class SocialFaceImageAnalyzer(
    context: Context,
    private val onResults: (SocialFaceResult, PersonSegmentationMask?, OutputTransform, Int, Int, Int) -> Unit,
    private val onError: (String) -> Unit
) : androidx.camera.core.ImageAnalysis.Analyzer, AutoCloseable {
    private data class PendingFrame(
        val imageProxy: ImageProxy,
        val faceImage: MPImage?,
        val segmentationImage: MPImage?,
        val sourceTransform: OutputTransform,
        val width: Int,
        val height: Int,
        val rotationDegrees: Int
    ) {
        val remainingCallbacks = AtomicInteger(listOfNotNull(faceImage, segmentationImage).size)
        val faceCallbackCompleted = AtomicBoolean(faceImage == null)
        val segmentationCallbackCompleted = AtomicBoolean(segmentationImage == null)
        val faceResult = AtomicReference<SocialFaceResult>(emptyList())
        val personMask = AtomicReference<PersonSegmentationMask?>(null)
        val failed = AtomicBoolean(false)
    }

    @Volatile private var faceEffectsEnabled = true
    @Volatile private var backgroundReplacementEnabled = true
    @Volatile private var lastAcceptedFrameMs = 0L
    private val pendingFrame = AtomicReference<PendingFrame?>(null)
    private val frameInFlight = AtomicBoolean(false)
    private val transformFactory = ImageProxyTransformFactory().apply {
        setUsingCropRect(false)
        // MediaPipe rotates its input. Its normalized outputs are inverse-rotated before mapping
        // through CameraX's unrotated buffer transform into PreviewView coordinates.
        setUsingRotationDegrees(false)
    }
    private var lastTimestampMs = 0L
    @Volatile private var closed = false

    fun setRequestedEffects(faceEffects: Boolean, backgroundReplacement: Boolean) {
        faceEffectsEnabled = faceEffects
        backgroundReplacementEnabled = backgroundReplacement
    }

    private val landmarker = createSocialFaceLandmarker(
        context,
        RunningMode.LIVE_STREAM,
        resultListener = { result, input -> handleFaceResult(result, input) },
        errorListener = { handleFaceError() }
    )
    private val segmenter = createSocialImageSegmenter(
        context,
        RunningMode.LIVE_STREAM,
        resultListener = { result, input -> handleSegmentationResult(result, input) },
        errorListener = { handleSegmentationError() }
    )

    override fun analyze(imageProxy: ImageProxy) {
        val now = SystemClock.uptimeMillis()
        if (closed || (!faceEffectsEnabled && !backgroundReplacementEnabled) ||
            now - lastAcceptedFrameMs < MIN_ANALYSIS_INTERVAL_MS ||
            !frameInFlight.compareAndSet(false, true)
        ) {
            imageProxy.close()
            return
        }
        lastAcceptedFrameMs = now
        var faceImage: MPImage? = null
        var segmentationImage: MPImage? = null
        var frame: PendingFrame? = null
        try {
            val mediaImage = imageProxy.image ?: error("CameraX did not provide a media image.")
            if (faceEffectsEnabled) faceImage = MediaImageBuilder(mediaImage).build()
            if (backgroundReplacementEnabled) segmentationImage = MediaImageBuilder(mediaImage).build()
            val pending = PendingFrame(
                imageProxy = imageProxy,
                faceImage = faceImage,
                segmentationImage = segmentationImage,
                sourceTransform = transformFactory.getOutputTransform(imageProxy),
                width = imageProxy.width,
                height = imageProxy.height,
                rotationDegrees = imageProxy.imageInfo.rotationDegrees
            )
            frame = pending
            check(pending.remainingCallbacks.get() > 0) { "No active local camera effect was requested." }
            check(pendingFrame.compareAndSet(null, pending)) { "A previous MediaPipe camera frame is still pending." }
            val timestamp = max(SystemClock.uptimeMillis(), lastTimestampMs + 1L)
            lastTimestampMs = timestamp
            val options = ImageProcessingOptions.builder()
                .setRotationDegrees(pending.rotationDegrees)
                .build()
            pending.faceImage?.let { input ->
                try {
                    landmarker.detectAsync(input, options, timestamp)
                } catch (_: Exception) {
                    finishFace(pending, null)
                }
            }
            pending.segmentationImage?.let { input ->
                try {
                    segmenter.segmentAsync(input, options, timestamp)
                } catch (_: Exception) {
                    finishSegmentation(pending, null)
                }
            }
        } catch (_: Exception) {
            val submitted = frame
            if (submitted != null) {
                if (pendingFrame.compareAndSet(submitted, null)) release(submitted)
            } else {
                runCatching { faceImage?.close() }
                runCatching { segmentationImage?.close() }
                imageProxy.close()
            }
            frameInFlight.set(false)
            if (!closed) onError("No se pudo analizar la cámara localmente.")
        }
    }

    private fun handleFaceResult(result: FaceLandmarkerResult, input: MPImage) {
        val frame = pendingFrame.get()
        if (frame?.faceImage !== input) {
            runCatching { input.close() }
            return
        }
        finishFace(frame, result.toSocialFaceResult())
    }

    private fun handleFaceError() {
        pendingFrame.get()?.let { finishFace(it, null) }
    }

    private fun handleSegmentationResult(result: ImageSegmenterResult, input: MPImage) {
        val frame = pendingFrame.get()
        if (frame?.segmentationImage !== input) {
            runCatching { input.close() }
            return
        }
        val mask = runCatching { personSegmentationMaskFromResult(result) }.getOrNull()
        finishSegmentation(frame, mask)
    }

    private fun handleSegmentationError() {
        pendingFrame.get()?.let { finishSegmentation(it, null) }
    }

    private fun finishFace(frame: PendingFrame, result: SocialFaceResult?) {
        if (!frame.faceCallbackCompleted.compareAndSet(false, true)) return
        if (result == null) frame.failed.set(true) else frame.faceResult.set(result)
        finishCallback(frame)
    }

    private fun finishSegmentation(frame: PendingFrame, result: PersonSegmentationMask?) {
        if (!frame.segmentationCallbackCompleted.compareAndSet(false, true)) return
        if (result == null) frame.failed.set(true) else frame.personMask.set(result)
        finishCallback(frame)
    }

    private fun finishCallback(frame: PendingFrame) {
        if (frame.remainingCallbacks.decrementAndGet() != 0) return
        pendingFrame.compareAndSet(frame, null)
        release(frame)
        frameInFlight.set(false)
        if (closed) return
        if (frame.failed.get()) onError("No se pudo completar el efecto local de rostro o fondo.")
        onResults(
            frame.faceResult.get(),
            frame.personMask.get(),
            frame.sourceTransform,
            frame.width,
            frame.height,
            frame.rotationDegrees
        )
    }

    private fun release(frame: PendingFrame) {
        runCatching { frame.faceImage?.close() }
        runCatching { frame.segmentationImage?.close() }
        runCatching { frame.imageProxy.close() }
    }

    override fun close() {
        if (closed) return
        closed = true
        landmarker.close()
        segmenter.close()
        pendingFrame.getAndSet(null)?.let(::release)
        frameInFlight.set(false)
    }
}

private fun FaceLandmarkerResult.toSocialFaceResult(): SocialFaceResult = faceLandmarks().mapNotNull { face ->
    if (face.size <= 454) return@mapNotNull null
    fun point(index: Int) = SocialFacePoint(face[index].x(), face[index].y())
    SocialFace(
        sideA = point(234),
        sideB = point(454),
        top = point(10),
        bottom = point(152),
        leftEyeOuter = point(LEFT_EYE_OUTER),
        leftEyeInner = point(LEFT_EYE_INNER),
        rightEyeInner = point(RIGHT_EYE_INNER),
        rightEyeOuter = point(RIGHT_EYE_OUTER)
    )
}

/** Maps MediaPipe's rotated normalized points through CameraX into the PreviewView's real viewport. */
@OptIn(TransformExperimental::class)
internal fun mapSocialFacesToPreview(
    faces: SocialFaceResult,
    sourceTransform: OutputTransform,
    targetTransform: OutputTransform?,
    frameWidth: Int,
    frameHeight: Int,
    rotationDegrees: Int,
    previewWidth: Int,
    previewHeight: Int
): SocialFaceResult {
    if (targetTransform == null || frameWidth <= 0 || frameHeight <= 0 || previewWidth <= 0 || previewHeight <= 0) {
        return emptyList()
    }
    val coordinateTransform = CoordinateTransform(sourceTransform, targetTransform)
    return faces.map { face ->
        val requiredPoints = face.requiredPoints()
        val points = FloatArray(requiredPoints.size * 2)
        requiredPoints.forEachIndexed { index, point ->
            val raw = rotatedPointToImageBuffer(point, frameWidth, frameHeight, rotationDegrees)
            points[index * 2] = raw.first
            points[index * 2 + 1] = raw.second
        }
        coordinateTransform.mapPoints(points)
        face.withPoints(requiredPoints.indices.map { index ->
            SocialFacePoint(points[index * 2] / previewWidth, points[index * 2 + 1] / previewHeight)
        })
    }
}

private fun rotatedPointToImageBuffer(
    point: SocialFacePoint,
    width: Int,
    height: Int,
    rotationDegrees: Int
): Pair<Float, Float> {
    val rotation = ((rotationDegrees % 360) + 360) % 360
    val rotatedWidth = if (rotation == 90 || rotation == 270) height.toFloat() else width.toFloat()
    val rotatedHeight = if (rotation == 90 || rotation == 270) width.toFloat() else height.toFloat()
    val x = point.x * rotatedWidth
    val y = point.y * rotatedHeight
    return when (rotation) {
        90 -> y to (height - x)
        180 -> (width - x) to (height - y)
        270 -> (width - y) to x
        else -> x to y
    }
}

/** Draws one of the three decorative filters using normalized MediaPipe face landmarks. */
@Synchronized
internal fun drawSocialFaceSticker(
    canvas: Canvas,
    imageWidth: Float,
    imageHeight: Float,
    face: SocialFace,
    filter: SocialFaceFilter
) {
    if (filter == SocialFaceFilter.NONE || imageWidth <= 0f || imageHeight <= 0f) return
    val sideLeft = min(face.sideA.x, face.sideB.x)
    val sideRight = max(face.sideA.x, face.sideB.x)
    val faceTop = min(face.top.y, face.bottom.y)
    val faceBottom = max(face.top.y, face.bottom.y)
    val bounds = RectF(sideLeft * imageWidth, faceTop * imageHeight, sideRight * imageWidth, faceBottom * imageHeight)
    if (bounds.width() < 1f || bounds.height() < 1f) return

    val leftEye = midpoint(face.leftEyeOuter, face.leftEyeInner, imageWidth, imageHeight)
    val rightEye = midpoint(face.rightEyeInner, face.rightEyeOuter, imageWidth, imageHeight)
    val eyeAngle = if (leftEye != null && rightEye != null) {
        Math.toDegrees(atan2((rightEye.second - leftEye.second).toDouble(), (rightEye.first - leftEye.first).toDouble())).toFloat()
    } else {
        0f
    }

    canvas.save()
    canvas.rotate(eyeAngle, bounds.centerX(), bounds.centerY())
    when (filter) {
        SocialFaceFilter.NONE -> Unit
        SocialFaceFilter.DOG_EARS -> drawDogEars(canvas, bounds)
        SocialFaceFilter.GLASSES -> drawGlasses(canvas, bounds, leftEye, rightEye)
        SocialFaceFilter.CROWN -> drawCrown(canvas, bounds)
    }
    canvas.restore()
}

private fun midpoint(first: SocialFacePoint, second: SocialFacePoint, width: Float, height: Float): Pair<Float, Float> =
    ((first.x + second.x) * 0.5f * width) to ((first.y + second.y) * 0.5f * height)

private object SocialStickerPaints {
    val dogOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff75452b.toInt() }
    val dogInner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffffb8a1.toInt() }
    val glassesTint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x553ab7bf }
    val glassesFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xff18212b.toInt()
        style = Paint.Style.STROKE
    }
    val glassesBridge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xff18212b.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    val crownFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffffc83d.toInt() }
    val crownOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xff9a5b13.toInt()
        style = Paint.Style.STROKE
    }
    val crownJewel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffe1306c.toInt() }
    val dogOuterPath = Path()
    val dogInnerPath = Path()
    val crownPath = Path()
}

private fun drawDogEars(canvas: Canvas, bounds: RectF) {
    val faceWidth = bounds.width()
    val faceHeight = bounds.height()
    val earWidth = faceWidth * 0.27f
    val earHeight = faceHeight * 0.38f
    val baseY = bounds.top + faceHeight * 0.12f
    val outerPaint = SocialStickerPaints.dogOuter
    val innerPaint = SocialStickerPaints.dogInner

    listOf(bounds.left + faceWidth * 0.14f, bounds.right - faceWidth * 0.14f).forEach { centerX ->
        val outer = SocialStickerPaints.dogOuterPath.apply {
            reset()
            moveTo(centerX - earWidth * 0.48f, baseY)
            cubicTo(centerX - earWidth * 0.70f, baseY - earHeight * 0.42f,
                centerX - earWidth * 0.24f, baseY - earHeight, centerX + earWidth * 0.03f, baseY - earHeight * 0.82f)
            cubicTo(centerX + earWidth * 0.62f, baseY - earHeight * 0.68f,
                centerX + earWidth * 0.65f, baseY - earHeight * 0.24f, centerX + earWidth * 0.48f, baseY)
            close()
        }
        canvas.drawPath(outer, outerPaint)
        val inner = SocialStickerPaints.dogInnerPath.apply {
            reset()
            moveTo(centerX - earWidth * 0.24f, baseY - faceHeight * 0.025f)
            cubicTo(centerX - earWidth * 0.36f, baseY - earHeight * 0.42f,
                centerX - earWidth * 0.12f, baseY - earHeight * 0.77f, centerX + earWidth * 0.04f, baseY - earHeight * 0.68f)
            cubicTo(centerX + earWidth * 0.35f, baseY - earHeight * 0.53f,
                centerX + earWidth * 0.34f, baseY - earHeight * 0.19f, centerX + earWidth * 0.25f, baseY - faceHeight * 0.025f)
            close()
        }
        canvas.drawPath(inner, innerPaint)
    }
}

private fun drawGlasses(
    canvas: Canvas,
    bounds: RectF,
    leftEye: Pair<Float, Float>?,
    rightEye: Pair<Float, Float>?
) {
    val faceWidth = bounds.width()
    val faceHeight = bounds.height()
    val fallbackY = bounds.top + faceHeight * 0.40f
    val eyeDistance = if (leftEye != null && rightEye != null) {
        sqrt((rightEye.first - leftEye.first).let { it * it } + (rightEye.second - leftEye.second).let { it * it })
    } else {
        faceWidth * 0.32f
    }
    val lensWidth = max(faceWidth * 0.25f, eyeDistance * 0.82f)
    val lensHeight = lensWidth * 0.58f
    val lensRadius = lensHeight * 0.25f
    val stroke = max(faceWidth * 0.022f, 2f)
    val eyeLeft = leftEye ?: (bounds.left + faceWidth * 0.34f to fallbackY)
    val eyeRight = rightEye ?: (bounds.left + faceWidth * 0.66f to fallbackY)
    val leftRect = RectF(eyeLeft.first - lensWidth / 2f, eyeLeft.second - lensHeight / 2f,
        eyeLeft.first + lensWidth / 2f, eyeLeft.second + lensHeight / 2f)
    val rightRect = RectF(eyeRight.first - lensWidth / 2f, eyeRight.second - lensHeight / 2f,
        eyeRight.first + lensWidth / 2f, eyeRight.second + lensHeight / 2f)
    val tint = SocialStickerPaints.glassesTint
    val frame = SocialStickerPaints.glassesFrame.apply { strokeWidth = stroke }
    val bridge = SocialStickerPaints.glassesBridge.apply { strokeWidth = stroke }
    canvas.drawRoundRect(leftRect, lensRadius, lensRadius, tint)
    canvas.drawRoundRect(rightRect, lensRadius, lensRadius, tint)
    canvas.drawRoundRect(leftRect, lensRadius, lensRadius, frame)
    canvas.drawRoundRect(rightRect, lensRadius, lensRadius, frame)
    canvas.drawLine(leftRect.right, (eyeLeft.second + eyeRight.second) * 0.5f,
        rightRect.left, (eyeLeft.second + eyeRight.second) * 0.5f, bridge)
}

private fun drawCrown(canvas: Canvas, bounds: RectF) {
    val crownWidth = bounds.width() * 0.70f
    val crownHeight = bounds.height() * 0.29f
    val left = bounds.centerX() - crownWidth / 2f
    val right = bounds.centerX() + crownWidth / 2f
    val bottom = bounds.top + bounds.height() * 0.09f
    val top = bottom - crownHeight
    val crown = SocialStickerPaints.crownPath.apply {
        reset()
        moveTo(left, bottom)
        lineTo(left + crownWidth * 0.08f, top + crownHeight * 0.34f)
        lineTo(left + crownWidth * 0.31f, top + crownHeight * 0.62f)
        lineTo(left + crownWidth * 0.50f, top)
        lineTo(left + crownWidth * 0.69f, top + crownHeight * 0.62f)
        lineTo(left + crownWidth * 0.92f, top + crownHeight * 0.34f)
        lineTo(right, bottom)
        close()
    }
    val fill = SocialStickerPaints.crownFill
    val outline = SocialStickerPaints.crownOutline.apply { strokeWidth = max(bounds.width() * 0.018f, 2f) }
    canvas.drawPath(crown, fill)
    canvas.drawPath(crown, outline)
    val jewel = SocialStickerPaints.crownJewel
    listOf(0.08f, 0.50f, 0.92f).forEach { fraction ->
        canvas.drawCircle(left + crownWidth * fraction, bottom - crownHeight * 0.10f, crownWidth * 0.035f, jewel)
    }
}

/** Draws the local person mask as a live transparent cutout over the camera preview. */
@OptIn(TransformExperimental::class)
internal class SocialFaceOverlayView(context: Context) : View(context) {
    private var selectedFilter: SocialFaceFilter = SocialFaceFilter.NONE
    private var selectedBackground: SocialBackgroundPreset = SocialBackgroundPreset.ORIGINAL
    private var faces: SocialFaceResult = emptyList()
    private var segmentationFrame: SocialPreviewSegmentationFrame? = null
    private var sourceToPreview = Matrix()
    private var maskToBuffer = Matrix()
    private var maskBitmap: Bitmap? = null
    private var backgroundBitmap: Bitmap? = null
    private var backgroundCanvas: Canvas? = null
    private var maskPixels = IntArray(0)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val maskCutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private var gradientKey: Triple<Int, Int, Int>? = null

    fun update(
        filter: SocialFaceFilter,
        results: SocialFaceResult,
        background: SocialBackgroundPreset,
        newSegmentationFrame: SocialPreviewSegmentationFrame?,
        targetTransform: OutputTransform?
    ) {
        selectedFilter = filter
        faces = results
        selectedBackground = background
        val frame = newSegmentationFrame
        if (background != SocialBackgroundPreset.ORIGINAL && frame != null && targetTransform != null) {
            segmentationFrame = frame
            updateMask(frame.mask)
            sourceToPreview = cameraBufferToPreviewMatrix(
                frame.sourceTransform, targetTransform, frame.width, frame.height
            )
            maskToBuffer = maskToImageBufferMatrix(frame.mask, frame.width, frame.height, frame.rotationDegrees)
            updateBackgroundShader(frame.width, frame.height, background)
            updateBackgroundLayer(frame)
        } else {
            segmentationFrame = null
        }
        invalidate()
    }

    private fun updateMask(mask: PersonSegmentationMask) {
        val bitmap = maskBitmap?.takeIf { !it.isRecycled && it.width == mask.width && it.height == mask.height }
            ?: Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888).also { maskBitmap = it }
        val requiredPixels = mask.width * mask.height
        if (maskPixels.size != requiredPixels) maskPixels = IntArray(requiredPixels)
        for (index in 0 until requiredPixels) {
            val alpha = (mask.personConfidence[index].coerceIn(0f, 1f) * 255f).toInt()
            maskPixels[index] = Color.argb(alpha, 255, 255, 255)
        }
        bitmap.setPixels(maskPixels, 0, mask.width, 0, 0, mask.width, mask.height)
    }

    private fun updateBackgroundShader(frameWidth: Int, frameHeight: Int, preset: SocialBackgroundPreset) {
        val key = Triple(frameWidth, frameHeight, preset.ordinal)
        if (gradientKey == key) return
        backgroundPaint.shader = LinearGradient(
            0f, 0f, 0f, frameHeight.toFloat(), preset.topColor, preset.bottomColor, Shader.TileMode.CLAMP
        )
        gradientKey = key
    }

    private fun updateBackgroundLayer(frame: SocialPreviewSegmentationFrame) {
        val current = backgroundBitmap
        val bitmap = current?.takeIf {
            !it.isRecycled && it.width == frame.width && it.height == frame.height
        } ?: Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888).also {
            current?.takeIf { old -> !old.isRecycled }?.recycle()
            backgroundBitmap = it
            backgroundCanvas = Canvas(it)
        }
        val target = backgroundCanvas ?: Canvas(bitmap).also { backgroundCanvas = it }
        val bounds = RectF(0f, 0f, frame.width.toFloat(), frame.height.toFloat())
        target.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        target.drawRect(bounds, backgroundPaint)
        maskBitmap?.let { mask -> target.drawBitmap(mask, maskToBuffer, maskCutoutPaint) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val frame = segmentationFrame
        val backgroundLayer = backgroundBitmap
        if (selectedBackground != SocialBackgroundPreset.ORIGINAL && frame != null &&
            backgroundLayer != null && !backgroundLayer.isRecycled
        ) {
            canvas.save()
            canvas.concat(sourceToPreview)
            canvas.drawBitmap(backgroundLayer, 0f, 0f, backgroundBitmapPaint)
            canvas.restore()
        }
        faces.forEach { face -> drawSocialFaceSticker(canvas, width.toFloat(), height.toFloat(), face, selectedFilter) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        maskBitmap?.takeIf { !it.isRecycled }?.recycle()
        maskBitmap = null
        backgroundBitmap?.takeIf { !it.isRecycled }?.recycle()
        backgroundBitmap = null
        backgroundCanvas = null
        maskPixels = IntArray(0)
        segmentationFrame = null
    }
}

@OptIn(TransformExperimental::class)
private fun cameraBufferToPreviewMatrix(
    sourceTransform: OutputTransform,
    targetTransform: OutputTransform,
    frameWidth: Int,
    frameHeight: Int
): Matrix {
    val source = floatArrayOf(0f, 0f, frameWidth.toFloat(), 0f, 0f, frameHeight.toFloat())
    val destination = source.copyOf()
    CoordinateTransform(sourceTransform, targetTransform).mapPoints(destination)
    return Matrix().apply { setPolyToPoly(source, 0, destination, 0, 3) }
}

private fun maskToImageBufferMatrix(
    mask: PersonSegmentationMask,
    frameWidth: Int,
    frameHeight: Int,
    rotationDegrees: Int
): Matrix {
    val source = floatArrayOf(0f, 0f, mask.width.toFloat(), 0f, 0f, mask.height.toFloat())
    val width = frameWidth.toFloat()
    val height = frameHeight.toFloat()
    val rotation = ((rotationDegrees % 360) + 360) % 360
    val destination = when (rotation) {
        90 -> floatArrayOf(0f, height, 0f, 0f, width, height)
        180 -> floatArrayOf(width, height, 0f, height, width, 0f)
        270 -> floatArrayOf(width, 0f, width, height, 0f, 0f)
        else -> floatArrayOf(0f, 0f, width, 0f, 0f, height)
    }
    return Matrix().apply { setPolyToPoly(source, 0, destination, 0, 3) }
}

private const val LEFT_EYE_OUTER = 33
private const val LEFT_EYE_INNER = 133
private const val RIGHT_EYE_INNER = 362
private const val RIGHT_EYE_OUTER = 263

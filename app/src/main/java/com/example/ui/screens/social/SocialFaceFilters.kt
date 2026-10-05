package com.example.ui.screens.social

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.max
import kotlin.math.sqrt

internal enum class SocialFaceFilter(val label: String) {
    NONE("Sin filtro"),
    DOG_EARS("Orejas"),
    GLASSES("Gafas"),
    CROWN("Corona")
}

internal data class FaceFilterBitmapResult(
    val bitmap: Bitmap,
    val detectedFaceCount: Int
)

/**
 * Applies a decorative sticker to a still photo using on-device ML Kit face landmarks.
 * Call from a background dispatcher; image data is never sent to a server.
 */
internal fun applySocialFaceFilterToBitmap(
    source: Bitmap,
    filter: SocialFaceFilter,
    allowInPlace: Boolean = false
): FaceFilterBitmapResult {
    if (filter == SocialFaceFilter.NONE) return FaceFilterBitmapResult(source, 0)

    val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()
    val detector = FaceDetection.getClient(options)
    val faces = try {
        Tasks.await(detector.process(InputImage.fromBitmap(source, 0)))
    } finally {
        detector.close()
    }
    if (faces.isEmpty()) return FaceFilterBitmapResult(source, 0)

    val output = if (allowInPlace && source.isMutable && source.config == Bitmap.Config.ARGB_8888) {
        source
    } else {
        checkNotNull(source.copy(Bitmap.Config.ARGB_8888, true))
    }
    val canvas = Canvas(output)
    faces.forEach { face ->
        if (face.boundingBox.width() <= 0 || face.boundingBox.height() <= 0) return@forEach
        canvas.save()
        canvas.rotate(face.headEulerAngleZ, face.boundingBox.exactCenterX(), face.boundingBox.exactCenterY())
        when (filter) {
            SocialFaceFilter.NONE -> Unit
            SocialFaceFilter.DOG_EARS -> drawDogEars(canvas, face.boundingBox)
            SocialFaceFilter.GLASSES -> drawGlasses(canvas, face)
            SocialFaceFilter.CROWN -> drawCrown(canvas, face.boundingBox)
        }
        canvas.restore()
    }
    return FaceFilterBitmapResult(output, faces.size)
}

private fun drawDogEars(canvas: Canvas, bounds: Rect) {
    val width = bounds.width().toFloat()
    val height = bounds.height().toFloat()
    val earWidth = width * 0.28f
    val earHeight = height * 0.38f
    val baseY = bounds.top + height * 0.12f
    val leftCenter = bounds.left + width * 0.16f
    val rightCenter = bounds.left + width * 0.84f
    val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF75452B.toInt() }
    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFB8A1.toInt() }

    listOf(leftCenter, rightCenter).forEach { centerX ->
        val outer = Path().apply {
            moveTo(centerX - earWidth * 0.48f, baseY)
            cubicTo(
                centerX - earWidth * 0.70f, baseY - earHeight * 0.42f,
                centerX - earWidth * 0.24f, baseY - earHeight,
                centerX + earWidth * 0.03f, baseY - earHeight * 0.82f
            )
            cubicTo(
                centerX + earWidth * 0.62f, baseY - earHeight * 0.68f,
                centerX + earWidth * 0.65f, baseY - earHeight * 0.24f,
                centerX + earWidth * 0.48f, baseY
            )
            close()
        }
        canvas.drawPath(outer, outerPaint)

        val inner = Path().apply {
            moveTo(centerX - earWidth * 0.24f, baseY - height * 0.025f)
            cubicTo(
                centerX - earWidth * 0.36f, baseY - earHeight * 0.42f,
                centerX - earWidth * 0.12f, baseY - earHeight * 0.77f,
                centerX + earWidth * 0.04f, baseY - earHeight * 0.68f
            )
            cubicTo(
                centerX + earWidth * 0.35f, baseY - earHeight * 0.53f,
                centerX + earWidth * 0.34f, baseY - earHeight * 0.19f,
                centerX + earWidth * 0.25f, baseY - height * 0.025f
            )
            close()
        }
        canvas.drawPath(inner, innerPaint)
    }
}

private fun drawGlasses(canvas: Canvas, face: Face) {
    val bounds = face.boundingBox
    val width = bounds.width().toFloat()
    val height = bounds.height().toFloat()
    val fallbackY = bounds.top + height * 0.39f
    val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
    val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
    val leftX = leftEye?.x ?: bounds.left + width * 0.34f
    val leftY = leftEye?.y ?: fallbackY
    val rightX = rightEye?.x ?: bounds.left + width * 0.66f
    val rightY = rightEye?.y ?: fallbackY
    val eyeDistance = sqrt((rightX - leftX) * (rightX - leftX) + (rightY - leftY) * (rightY - leftY))
    val lensWidth = max(width * 0.25f, eyeDistance * 0.72f)
    val lensHeight = lensWidth * 0.62f
    val cornerRadius = lensHeight * 0.28f
    val stroke = max(width * 0.025f, 2.5f)
    val leftRect = RectF(leftX - lensWidth / 2f, leftY - lensHeight / 2f, leftX + lensWidth / 2f, leftY + lensHeight / 2f)
    val rightRect = RectF(rightX - lensWidth / 2f, rightY - lensHeight / 2f, rightX + lensWidth / 2f, rightY + lensHeight / 2f)
    val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x553AB7BF }
    val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF18212B.toInt()
        style = Paint.Style.STROKE
        strokeWidth = stroke
    }
    val bridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF18212B.toInt()
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawRoundRect(leftRect, cornerRadius, cornerRadius, tintPaint)
    canvas.drawRoundRect(rightRect, cornerRadius, cornerRadius, tintPaint)
    canvas.drawRoundRect(leftRect, cornerRadius, cornerRadius, framePaint)
    canvas.drawRoundRect(rightRect, cornerRadius, cornerRadius, framePaint)
    canvas.drawLine(leftRect.right, leftY, rightRect.left, rightY, bridgePaint)
}

private fun drawCrown(canvas: Canvas, bounds: Rect) {
    val width = bounds.width().toFloat()
    val height = bounds.height().toFloat()
    val crownWidth = width * 0.70f
    val crownHeight = height * 0.27f
    val left = bounds.exactCenterX() - crownWidth / 2f
    val right = bounds.exactCenterX() + crownWidth / 2f
    val bottom = bounds.top + height * 0.08f
    val top = bottom - crownHeight
    val crown = Path().apply {
        moveTo(left, bottom)
        lineTo(left + crownWidth * 0.08f, top + crownHeight * 0.34f)
        lineTo(left + crownWidth * 0.31f, top + crownHeight * 0.62f)
        lineTo(left + crownWidth * 0.50f, top)
        lineTo(left + crownWidth * 0.69f, top + crownHeight * 0.62f)
        lineTo(left + crownWidth * 0.92f, top + crownHeight * 0.34f)
        lineTo(right, bottom)
        close()
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC83D.toInt() }
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9A5B13.toInt()
        style = Paint.Style.STROKE
        strokeWidth = max(width * 0.018f, 2f)
    }
    canvas.drawPath(crown, fill)
    canvas.drawPath(crown, outline)
    val jewelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE1306C.toInt() }
    listOf(0.08f, 0.50f, 0.92f).forEach { fraction ->
        canvas.drawCircle(left + crownWidth * fraction, bottom - crownHeight * 0.10f, crownWidth * 0.035f, jewelPaint)
    }
}

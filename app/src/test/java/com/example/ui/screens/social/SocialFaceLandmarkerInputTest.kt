package com.example.ui.screens.social

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SocialFaceLandmarkerInputTest {
    @Test
    fun faceLandmarkerBitmapIsPhysicallyRotatedIntoTheCameraUprightOrientation() {
        val source = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, 0xffff0000.toInt()) // top-left
            setPixel(1, 0, 0xff00ff00.toInt()) // top-right
            setPixel(0, 1, 0xff0000ff.toInt())
            setPixel(1, 1, 0xffffff00.toInt())
            setPixel(0, 2, 0xff00ffff.toInt()) // bottom-left
            setPixel(1, 2, 0xffff00ff.toInt()) // bottom-right
        }

        val rotated = rotateSocialFaceBitmapForLandmarker(source, 90)
        try {
            assertNotSame(source, rotated)
            assertEquals(3, rotated.width)
            assertEquals(2, rotated.height)
            // A positive CameraX rotation is applied to the pixels, not passed as unsupported
            // MediaPipe processing metadata; the existing coordinate mapper inversely rotates them.
            assertEquals(0xff00ffff.toInt(), rotated.getPixel(0, 0))
            assertEquals(0xffff0000.toInt(), rotated.getPixel(2, 0))
            assertEquals(0xffff00ff.toInt(), rotated.getPixel(0, 1))
            assertEquals(0xff00ff00.toInt(), rotated.getPixel(2, 1))
        } finally {
            rotated.recycle()
            source.recycle()
        }
    }

    @Test
    fun landmarkCoordinatesFromEveryRotatedBitmapMapBackToTheOriginalBuffer() {
        val topLeftInRotatedImage = mapOf(
            0 to SocialFacePoint(0f, 0f),
            90 to SocialFacePoint(1f, 0f),
            180 to SocialFacePoint(1f, 1f),
            270 to SocialFacePoint(0f, 1f)
        )

        topLeftInRotatedImage.forEach { (rotation, rotatedPoint) ->
            val bufferPoint = rotatedPointToImageBuffer(rotatedPoint, width = 2, height = 3, rotationDegrees = rotation)
            assertTrue("x should map to the original top-left for rotation $rotation", kotlin.math.abs(bufferPoint.first) < 0.0001f)
            assertTrue("y should map to the original top-left for rotation $rotation", kotlin.math.abs(bufferPoint.second) < 0.0001f)
        }
    }

    @Test
    fun zeroRotationReusesBitmapAndNonCanonicalDegreesNormalize() {
        val source = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
        try {
            assertSame(source, rotateSocialFaceBitmapForLandmarker(source, 0))
            assertEquals(270, normalizeSocialFaceRotationDegrees(-90))
            assertEquals(90, normalizeSocialFaceRotationDegrees(450))
        } finally {
            source.recycle()
        }
    }
}

package com.example.ui.screens.social

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SocialFaceLandmarkerInputTest {
    @Test
    fun cameraRotationProducesTheCorrectBitmapDimensionsForFaceLandmarker() {
        val source = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
        try {
            listOf(
                Triple(90, 3, 2),
                Triple(180, 2, 3),
                Triple(270, 3, 2),
                Triple(-90, 3, 2)
            ).forEach { (rotation, expectedWidth, expectedHeight) ->
                val rotated = rotateSocialFaceBitmapForLandmarker(source, rotation)
                try {
                    assertNotSame("rotation $rotation should produce an upright bitmap", source, rotated)
                    assertEquals("width after rotation $rotation", expectedWidth, rotated.width)
                    assertEquals("height after rotation $rotation", expectedHeight, rotated.height)
                } finally {
                    rotated.recycle()
                }
            }
        } finally {
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

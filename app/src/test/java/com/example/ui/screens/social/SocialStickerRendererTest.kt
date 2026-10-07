package com.example.ui.screens.social

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SocialStickerRendererTest {
    private val sampleFace = SocialFace(
        sideA = SocialFacePoint(0.30f, 0.50f),
        sideB = SocialFacePoint(0.70f, 0.50f),
        top = SocialFacePoint(0.50f, 0.20f),
        bottom = SocialFacePoint(0.50f, 0.80f),
        leftEyeOuter = SocialFacePoint(0.37f, 0.43f),
        leftEyeInner = SocialFacePoint(0.45f, 0.43f),
        rightEyeInner = SocialFacePoint(0.55f, 0.43f),
        rightEyeOuter = SocialFacePoint(0.63f, 0.43f)
    )

    @Test
    fun landmarkDrivenStickersRenderNearTheFaceForPhotoAndPreviewCanvasSizes() {
        listOf(160, 320).forEach { size ->
            listOf(SocialFaceFilter.DOG_EARS, SocialFaceFilter.GLASSES, SocialFaceFilter.CROWN).forEach { filter ->
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                try {
                    drawSocialFaceSticker(Canvas(bitmap), size.toFloat(), size.toFloat(), sampleFace, filter)

                    var paintedPixels = 0
                    var minX = size
                    var maxX = -1
                    var minY = size
                    var maxY = -1
                    for (y in 0 until size) {
                        for (x in 0 until size) {
                            if (Color.alpha(bitmap.getPixel(x, y)) > 0) {
                                paintedPixels++
                                minX = minOf(minX, x)
                                maxX = maxOf(maxX, x)
                                minY = minOf(minY, y)
                                maxY = maxOf(maxY, y)
                            }
                        }
                    }

                    assertTrue("$filter should paint visible pixels", paintedPixels > 0)
                    // The face spans 30–70% of the image. Decorations may extend above it, but
                    // should stay centered around the detected face rather than the canvas origin.
                    assertTrue("$filter left edge was misplaced: $minX", minX >= size * 0.15f)
                    assertTrue("$filter right edge was misplaced: $maxX", maxX < size * 0.85f)
                    assertTrue("$filter top edge was misplaced: $minY", minY >= 0)
                    assertTrue("$filter bottom edge was misplaced: $maxY", maxY < size * 0.9f)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    @Test
    fun noFilterLeavesTheOverlayTransparent() {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        drawSocialFaceSticker(Canvas(bitmap), 64f, 64f, sampleFace, SocialFaceFilter.NONE)

        assertEquals(0, bitmap.getPixel(32, 32))
        assertEquals(0, bitmap.getPixel(0, 0))
        bitmap.recycle()
    }
}

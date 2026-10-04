package com.example.ui.screens.social

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SocialPhotoFilterTest {
    @Test
    fun chooserOffersOriginalMonochromeAndSepia() {
        assertEquals(listOf("Original", "B/N", "Sepia"), SocialPhotoFilter.entries.map { it.label })
    }

    @Test
    fun monochromeAndSepiaChangePixelsWithoutChangingPhotoBounds() {
        val source = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(180, 90, 30))
        }
        val monochrome = applyPhotoFilterToBitmap(source, SocialPhotoFilter.MONOCHROME)
        val sepia = applyPhotoFilterToBitmap(source, SocialPhotoFilter.SEPIA)

        assertNotSame(source, monochrome)
        assertNotSame(source, sepia)
        listOf(monochrome, sepia).forEach { filtered ->
            assertEquals(source.width, filtered.width)
            assertEquals(source.height, filtered.height)
        }
        val gray = monochrome.getPixel(0, 0)
        assertEquals(Color.red(gray), Color.green(gray))
        assertEquals(Color.green(gray), Color.blue(gray))
        val sepiaPixel = sepia.getPixel(0, 0)
        assertTrue(sepiaPixel != source.getPixel(0, 0))

        source.recycle()
        monochrome.recycle()
        sepia.recycle()
    }

    @Test
    fun originalSelectionLeavesPixelsAndBitmapUnchanged() {
        val source = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.MAGENTA)
        }
        val result = applyPhotoFilterToBitmap(source, SocialPhotoFilter.ORIGINAL)
        assertSame(source, result)
        assertEquals(Color.MAGENTA, result.getPixel(0, 0))
        source.recycle()
    }
}

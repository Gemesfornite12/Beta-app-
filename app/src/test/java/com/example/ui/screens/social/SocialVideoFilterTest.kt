package com.example.ui.screens.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialVideoFilterTest {
    @Test
    fun socialVideoFiltersExposeOriginalMonochromeAndSepiaMatrices() {
        assertEquals(listOf("Original", "B/N", "Sepia"), SocialVideoFilter.entries.map { it.label })
        assertNull(videoMatrixFor(SocialVideoFilter.ORIGINAL))
        val gray = requireNotNull(videoMatrixFor(SocialVideoFilter.MONOCHROME))
        val sepia = requireNotNull(videoMatrixFor(SocialVideoFilter.SEPIA))
        assertEquals(16, gray.size)
        assertEquals(16, sepia.size)
        assertEquals(0.2126f, gray[0], 0.0001f)
        assertEquals(0.7152f, gray[4], 0.0001f)
        assertEquals(0.0722f, gray[8], 0.0001f)
        assertEquals(0.393f, sepia[0], 0.0001f)
        assertEquals(0.349f, sepia[1], 0.0001f)
        assertEquals(0.272f, sepia[2], 0.0001f)
        assertTrue(gray[12] == 0f && gray[13] == 0f && gray[14] == 0f)
    }
}

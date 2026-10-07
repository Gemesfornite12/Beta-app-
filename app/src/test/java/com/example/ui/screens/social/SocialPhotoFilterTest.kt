package com.example.ui.screens.social

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun modelInitializationDiagnosticShowsMissingClassWithoutRawExceptionText() {
        val missingClass = "com.google.mediapipe.framework.internal.MissingRuntimeType"
        val failure = IllegalStateException(
            "private/path/not-for-display",
            NoClassDefFoundError("Failed resolution of: L${missingClass.replace('.', '/')};")
        )

        assertEquals(missingClass, missingClassNameForDiagnostic(failure))
        val message = modelInitializationMessage("FaceLandmarker", failure)
        assertTrue(message.contains("IllegalStateException → NoClassDefFoundError: $missingClass"))
        assertTrue(!message.contains("private/path/not-for-display"))
    }

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

    @Test
    fun selectedBackgroundPreservesThePersonAndReplacesTheBackdrop() {
        val source = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.MAGENTA)
        }
        val mask = PersonSegmentationMask(2, 1, floatArrayOf(1f, 0f))

        val result = applySocialBackgroundToBitmap(source, mask, SocialBackgroundPreset.SKY)

        assertNotSame(source, result)
        assertEquals(source.width, result.width)
        assertEquals(source.height, result.height)
        assertEquals(Color.MAGENTA, result.getPixel(0, 0))
        assertTrue(result.getPixel(1, 0) != Color.MAGENTA)
        source.recycle()
        result.recycle()
    }

    @Test
    fun originalBackgroundPresetDoesNotReplacePixels() {
        val source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.MAGENTA)
        }
        val result = applySocialBackgroundToBitmap(
            source,
            PersonSegmentationMask(1, 1, floatArrayOf(0f)),
            SocialBackgroundPreset.ORIGINAL
        )
        assertSame(source, result)
        assertEquals(Color.MAGENTA, result.getPixel(0, 0))
        source.recycle()
    }
    @Test
    fun initializationReportIncludesTechnicalContextAndRedactsPrivateData() {
        val missingClass = "com.google.mediapipe.framework.Graph"
        val failure = ExceptionInInitializerError(
            IllegalStateException(
                "init failed /data/user/0/private/app/files/cache.bin email=cris@example.com token=very-secret-token account_id=83016886 " +
                    "see https://private.example/path?key=hidden",
                NoClassDefFoundError(missingClass)
            )
        ).apply {
            stackTrace = Array(40) { index ->
                StackTraceElement("example.Initializer", "frame$index", "/private/source/User.kt", 100 + index)
            }
        }
        val diagnostic = buildSocialModelInitializationDiagnostic(
            failures = listOf(SocialModelInitializationDiagnostic("ImageSegmenter", "selfie_segmenter.tflite", failure)),
            sdkApi = 35,
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
            buildVariant = "beta"
        )

        assertTrue(diagnostic.contains("Task: ImageSegmenter"))
        assertTrue(diagnostic.contains("Model: selfie_segmenter.tflite"))
        assertTrue(diagnostic.contains("Android SDK/API: 35"))
        assertTrue(diagnostic.contains("Supported ABIs: arm64-v8a, armeabi-v7a"))
        assertTrue(diagnostic.contains("MediaPipe Tasks Vision: 1.0.0"))
        assertTrue(diagnostic.contains("Build variant: beta"))
        assertTrue(diagnostic.contains("ExceptionInInitializerError"))
        assertTrue(diagnostic.contains("IllegalStateException"))
        assertTrue(diagnostic.contains("NoClassDefFoundError: $missingClass"))
        assertTrue(diagnostic.contains("init failed"))
        assertFalse(diagnostic.contains("/data/user/0"))
        assertFalse(diagnostic.contains("cris@example.com"))
        assertFalse(diagnostic.contains("very-secret-token"))
        assertFalse(diagnostic.contains("83016886"))
        assertFalse(diagnostic.contains("private.example"))
        assertFalse(diagnostic.contains("/private/source"))
        assertTrue(diagnostic.lines().count { it.startsWith("  at ") } <= 24)
        assertTrue(diagnostic.contains("stack frames truncated"))
    }

    @Test
    fun diagnosticSanitizerRedactsUrlsCredentialsAndAccountIdentifiers() {
        val sanitized = sanitizeSocialModelDiagnostic(
            "url=https://example.test/path?auth=abc email=person@example.test password: secret-value uid=123456789 authorization: Bearer secret-auth-token-123456789 user: Cristopher"
        )
        assertFalse(sanitized.contains("example.test"))
        assertFalse(sanitized.contains("person@example.test"))
        assertFalse(sanitized.contains("secret-value"))
        assertFalse(sanitized.contains("123456789"))
        assertFalse(sanitized.contains("secret-auth-token-123456789"))
        assertFalse(sanitized.contains("Cristopher"))
        assertTrue(sanitized.contains("<url>"))
        assertTrue(sanitized.contains("<redacted-email>"))
        assertTrue(sanitized.contains("password=<redacted>"))
    }

    @Test
    fun classLinkageMessagesKeepTechnicalClassNamesWhileLocalPathsAreRedacted() {
        val sanitized = sanitizeSocialModelDiagnostic(
            "Failed resolution of: Lcom/google/mediapipe/framework/Graph; /data/user/0/app/cache/model.task"
        )
        assertTrue(sanitized.contains("com.google.mediapipe.framework.Graph"))
        assertFalse(sanitized.contains("/data/user/0"))
        assertTrue(sanitized.contains("<path>"))
    }

    @Test
    fun initializationReportPreservesSeparateFaceAndBackgroundFailures() {
        val report = buildSocialModelInitializationDiagnostic(
            failures = listOf(
                SocialModelInitializationDiagnostic("FaceLandmarker", "face_landmarker.task", ExceptionInInitializerError("first")),
                SocialModelInitializationDiagnostic("ImageSegmenter", "selfie_segmenter.tflite", NoClassDefFoundError("com.google.mediapipe.framework.Graph"))
            ),
            sdkApi = 36,
            supportedAbis = listOf("x86_64"),
            buildVariant = "beta"
        )
        assertTrue(report.contains("Task: FaceLandmarker"))
        assertTrue(report.contains("Model: face_landmarker.task"))
        assertTrue(report.contains("Task: ImageSegmenter"))
        assertTrue(report.contains("Model: selfie_segmenter.tflite"))
    }

}

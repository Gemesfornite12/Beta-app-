package com.example.ui.screens.social

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class SocialCameraDiagnosticButtonUiTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun selectedEffectShowsCopyControlInsideCameraPreview() {
        composeTestRule.setContent {
            Box(
                Modifier.size(width = 320.dp, height = 480.dp)
                    .testTag("social_camera_preview")
            ) {
                SocialFaceDiagnosticButton(
                    faceEffectSelected = true,
                    initializationFailure = false,
                    analyzerCreationFailure = false,
                    onClick = {}
                )
            }
        }

        val preview = composeTestRule.onNodeWithTag("social_camera_preview").fetchSemanticsNode().boundsInRoot
        val buttonNode = composeTestRule.onNodeWithTag("social_camera_copy_diagnostic")
        buttonNode.assertIsDisplayed()
        val button = buttonNode.fetchSemanticsNode().boundsInRoot
        assertTrue(button.left >= preview.left)
        assertTrue(button.top >= preview.top)
        assertTrue(button.right <= preview.right)
        assertTrue(button.bottom <= preview.bottom)
    }
}

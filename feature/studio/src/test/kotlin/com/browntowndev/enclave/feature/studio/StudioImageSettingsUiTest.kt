package com.browntowndev.enclave.feature.studio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import com.browntowndev.enclave.feature.studio.components.StudioOptionsSheetContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudioImageSettingsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `STU-004 Studio bottom sheet shows Resolution for SDXL as 1024x1024`() {
        val sdxlState = StudioUiState(
            mediaType = MediaCapability.IMAGE,
            settings = ImageGenerationSettings(imageResolution = "1024x1024"),
            capabilities = ProviderCapabilities(
                supportedAspectRatios = emptyList(),
                supportedImageQualities = emptyList(),
                supportedImageResolutions = listOf("1024x1024"),
                supportedImageSteps = listOf(8, 12, 16, 20, 24, 30),
                defaultImageStep = 16,
                supportedVideoQualities = emptyList(),
                supportedVideoResolutions = emptyList(),
                supportedVideoDurations = emptyList(),
                supportsVideo = false
            )
        )

        composeTestRule.setContent {
            EnclaveTheme {
                StudioOptionsSheetContent(
                    state = sdxlState,
                    onDismiss = {},
                    onUpdateSettings = {},
                    onContinualModeChange = {},
                    onMediaTypeChange = {},
                    onTemplateSelected = {},
                )
            }
        }

        // Resolution header/label and selected option "1024x1024" should be visible
        composeTestRule.onNodeWithText("Resolution").assertIsDisplayed()
        composeTestRule.onNodeWithText("1024x1024").assertIsDisplayed()
    }

    @Test
    fun `STU-005 Studio bottom sheet shows Diffusion Steps for SDXL`() {
        val sdxlState = StudioUiState(
            mediaType = MediaCapability.IMAGE,
            settings = ImageGenerationSettings(imageResolution = "1024x1024", diffusionSteps = 16),
            capabilities = ProviderCapabilities(
                supportedAspectRatios = emptyList(),
                supportedImageQualities = emptyList(),
                supportedImageResolutions = listOf("1024x1024"),
                supportedImageSteps = listOf(8, 12, 16, 20, 24, 30),
                defaultImageStep = 16,
                supportedVideoQualities = emptyList(),
                supportedVideoResolutions = emptyList(),
                supportedVideoDurations = emptyList(),
                supportsVideo = false
            )
        )

        composeTestRule.setContent {
            EnclaveTheme {
                StudioOptionsSheetContent(
                    state = sdxlState,
                    onDismiss = {},
                    onUpdateSettings = {},
                    onContinualModeChange = {},
                    onMediaTypeChange = {},
                    onTemplateSelected = {},
                )
            }
        }

        // Diffusion Steps header/label should be visible, and options check
        composeTestRule.onNodeWithText("Diffusion Steps").assertIsDisplayed()
        composeTestRule.onNodeWithText("8").assertIsDisplayed()
        composeTestRule.onNodeWithText("12").assertIsDisplayed()
        composeTestRule.onNodeWithText("16").assertIsDisplayed()
        composeTestRule.onNodeWithText("20").assertIsDisplayed()
        composeTestRule.onNodeWithText("24").assertIsDisplayed()
        composeTestRule.onNodeWithText("30").assertIsDisplayed()
    }

    @Test
    fun `STU-006 Studio bottom sheet hides Diffusion Steps for SD15`() {
        val sd15State = StudioUiState(
            mediaType = MediaCapability.IMAGE,
            settings = ImageGenerationSettings(imageResolution = "512x512"),
            capabilities = ProviderCapabilities(
                supportedAspectRatios = emptyList(),
                supportedImageQualities = emptyList(),
                supportedImageResolutions = listOf("512x512"),
                supportedImageSteps = emptyList(),
                defaultImageStep = 20,
                supportedVideoQualities = emptyList(),
                supportedVideoResolutions = emptyList(),
                supportedVideoDurations = emptyList(),
                supportsVideo = false
            )
        )

        composeTestRule.setContent {
            EnclaveTheme {
                StudioOptionsSheetContent(
                    state = sd15State,
                    onDismiss = {},
                    onUpdateSettings = {},
                    onContinualModeChange = {},
                    onMediaTypeChange = {},
                    onTemplateSelected = {},
                )
            }
        }

        // Diffusion Steps header/label should NOT be visible
        composeTestRule.onNodeWithText("Diffusion Steps").assertDoesNotExist()
    }
}

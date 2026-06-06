package com.browntowndev.enclave.feature.studio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelPolicy
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
    val capabilities =
      LocalImageGenerationModelPolicy.providerCapabilitiesFor(LocalImageGenerationModelId.SDXL_QNN)

    composeTestRule.setContent {
      EnclaveTheme {
        StudioOptionsSheetContent(
          state =
            StudioUiState(
              settings = ImageGenerationSettings(imageResolution = "1024x1024", diffusionSteps = 16),
              capabilities = capabilities,
            ),
          onDismiss = {},
          onUpdateSettings = {},
          onContinualModeChange = {},
          onMediaTypeChange = {},
          onTemplateSelected = {},
        )
      }
    }

    composeTestRule.onNodeWithText("Resolution").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("1024x1024").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `STU-005 Studio bottom sheet shows Diffusion Steps for SDXL`() {
    val capabilities =
      LocalImageGenerationModelPolicy.providerCapabilitiesFor(LocalImageGenerationModelId.SDXL_QNN)

    composeTestRule.setContent {
      EnclaveTheme {
        StudioOptionsSheetContent(
          state =
            StudioUiState(
              settings = ImageGenerationSettings(imageResolution = "1024x1024", diffusionSteps = 16),
              capabilities = capabilities,
            ),
          onDismiss = {},
          onUpdateSettings = {},
          onContinualModeChange = {},
          onMediaTypeChange = {},
          onTemplateSelected = {},
        )
      }
    }

    composeTestRule.onNodeWithText("Diffusion Steps").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("8").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("12").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("16").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("20").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("24").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("30").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `STU-006 Studio bottom sheet hides Diffusion Steps for SD15`() {
    val capabilities =
      LocalImageGenerationModelPolicy.providerCapabilitiesFor(LocalImageGenerationModelId.SD15_QNN)

    composeTestRule.setContent {
      EnclaveTheme {
        StudioOptionsSheetContent(
          state =
            StudioUiState(
              settings = ImageGenerationSettings(imageResolution = "512x512"),
              capabilities = capabilities,
            ),
          onDismiss = {},
          onUpdateSettings = {},
          onContinualModeChange = {},
          onMediaTypeChange = {},
          onTemplateSelected = {},
        )
      }
    }

    composeTestRule.onNodeWithText("Resolution").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("512x512").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Diffusion Steps").assertDoesNotExist()
  }
}

package com.browntowndev.pocketcrew.feature.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(instrumentedPackages = ["androidx.loader.content"])
class ArtifactExportWizardContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `format step continue disabled until format selected`() {
        composeTestRule.setContent {
            PocketCrewTheme {
                ArtifactExportWizardContent(
                    state =
                        ArtifactExportSheetState.Visible(
                            messageId = MessageId("m"),
                            artifactId = "a",
                            title = "My doc",
                            step = ArtifactExportWizardStep.CHOOSE_FORMAT,
                            selectedFormat = null,
                            selectedDestination = null,
                            isExporting = false,
                        ),
                    onDismiss = {},
                    onSelectFormat = {},
                    onSelectDestination = {},
                    onBack = {},
                    onNext = {},
                    onConfirmExport = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
    }

    @Test
    fun `when format preselected next is enabled`() {
        composeTestRule.setContent {
            PocketCrewTheme {
                ArtifactExportWizardContent(
                    state =
                        ArtifactExportSheetState.Visible(
                            messageId = MessageId("m"),
                            artifactId = "a",
                            title = "My doc",
                            step = ArtifactExportWizardStep.CHOOSE_FORMAT,
                            selectedFormat = WizardExportFormat.PDF,
                            selectedDestination = null,
                            isExporting = false,
                        ),
                    onDismiss = {},
                    onSelectFormat = {},
                    onSelectDestination = {},
                    onBack = {},
                    onNext = {},
                    onConfirmExport = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Next").assertIsDisplayed()
        composeTestRule.onNodeWithText("Next").assertIsEnabled()
    }

    @Test
    fun `destination step shows share and download`() {
        composeTestRule.setContent {
            PocketCrewTheme {
                ArtifactExportWizardContent(
                    state =
                        ArtifactExportSheetState.Visible(
                            messageId = MessageId("m"),
                            artifactId = "a",
                            title = "My doc",
                            step = ArtifactExportWizardStep.CHOOSE_DESTINATION,
                            selectedFormat = WizardExportFormat.BOTH,
                            selectedDestination = null,
                            isExporting = false,
                        ),
                    onDismiss = {},
                    onSelectFormat = {},
                    onSelectDestination = {},
                    onBack = {},
                    onNext = {},
                    onConfirmExport = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Share").assertIsDisplayed()
        composeTestRule.onNodeWithText("Download").assertIsDisplayed()
    }
}

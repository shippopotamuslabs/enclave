package com.browntowndev.enclave.feature.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.feature.chat.artifactexport.ArtifactExportWizardContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["androidx.loader.content"])
class ArtifactExportWizardContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `format step continue disabled until format selected`() {
        composeTestRule.setContent {
            EnclaveTheme {
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
                            documentJson = """{"blocks":[]}""",
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
            EnclaveTheme {
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
                            documentJson = """{"blocks":[]}""",
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
        composeTestRule.onNodeWithText("Next").assertExists()
        composeTestRule.onNodeWithText("Next").assertIsEnabled()
    }

    @Test
    fun `destination step shows share and download`() {
        composeTestRule.setContent {
            EnclaveTheme {
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
                            documentJson = """{"blocks":[]}""",
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

    @Test
    fun `error message is displayed in sheet`() {
        composeTestRule.setContent {
            EnclaveTheme {
                ArtifactExportWizardContent(
                    state =
                        ArtifactExportSheetState.Visible(
                            messageId = MessageId("m"),
                            artifactId = "a",
                            title = "My doc",
                            step = ArtifactExportWizardStep.CHOOSE_DESTINATION,
                            selectedFormat = WizardExportFormat.PDF,
                            selectedDestination = WizardExportDestination.DOWNLOAD,
                            isExporting = false,
                            documentJson = """{"blocks":[]}""",
                            errorMessage = "Export failed",
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
        composeTestRule.onNodeWithText("Export failed").assertIsDisplayed()
    }

    @Test
    fun `exporting state displays progress and disables action`() {
        composeTestRule.setContent {
            EnclaveTheme {
                ArtifactExportWizardContent(
                    state =
                        ArtifactExportSheetState.Visible(
                            messageId = MessageId("m"),
                            artifactId = "a",
                            title = "My doc",
                            step = ArtifactExportWizardStep.CHOOSE_DESTINATION,
                            selectedFormat = WizardExportFormat.WORD,
                            selectedDestination = WizardExportDestination.DOWNLOAD,
                            isExporting = true,
                            documentJson = """{"blocks":[]}""",
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
        composeTestRule.onNodeWithText("Exporting…").assertIsDisplayed()
        composeTestRule.onNodeWithText("Export").assertIsNotEnabled()
    }
}

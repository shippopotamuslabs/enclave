package com.browntowndev.enclave.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.browntowndev.enclave.core.ui.component.assignment.ApiModelAssetUi
import com.browntowndev.enclave.core.ui.component.assignment.ApiModelConfigUi
import com.browntowndev.enclave.core.ui.component.assignment.AssignmentSelectionContent
import com.browntowndev.enclave.core.ui.component.assignment.LocalModelAssetUi
import com.browntowndev.enclave.core.ui.component.assignment.LocalModelConfigUi
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AssignmentSelectionExtractionRegressionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `S14 — Assignment sheet header title, tab labels, and buttons render correctly`() {
        val localAssets =
            listOf(
                LocalModelAssetUi(
                    metadataId = LocalModelId("local-1"),
                    huggingFaceModelName = "huggingface/Qwen-3-8B",
                    friendlyName = "Qwen 3 8B",
                    providerName = "HuggingFace",
                    format = "GGUF",
                    remoteFileName = "qwen-3-8b.gguf",
                    sizeInBytes = 42L,
                    configurations =
                        listOf(
                            LocalModelConfigUi(
                                id = LocalModelConfigurationId("cfg-1"),
                                displayName = "Fast Preset",
                            ),
                        ),
                ),
            )
        val apiAssets =
            listOf(
                ApiModelAssetUi(
                    credentialsId = ApiCredentialsId("cred-1"),
                    displayName = "OpenRouter",
                    provider = ApiProvider.OPENROUTER,
                    baseUrl = null,
                    credentialAlias = "openrouter",
                    configurations =
                        listOf(
                            ApiModelConfigUi(
                                id = ApiModelConfigurationId("api-cfg-1"),
                                credentialsId = ApiCredentialsId("cred-1"),
                                modelId = "anthropic/claude-3.5-sonnet",
                            ),
                        ),
                ),
            )

        composeTestRule.setContent {
            AssignmentSelectionContent(
                slotLabel = "Main",
                localAssets = localAssets,
                apiAssets = apiAssets,
                ttsAssets = emptyList(),
                liveVoiceAssets = emptyList(),
                mediaAssets = emptyList(),
                onDismiss = {},
                onSelect = { _, _, _, _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("Assign Model to Main").assertIsDisplayed()
        composeTestRule.onNodeWithText("Local Models").assertIsDisplayed()
        composeTestRule.onNodeWithText("API Models").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confirm").assertIsDisplayed()
    }
}

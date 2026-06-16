package com.browntowndev.enclave.core.ui.component.assignment

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
class AssignmentSelectionBottomSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `S03 — Local Models and API Models tabs are visible when both asset lists are non-empty`() {
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
                slotLabel = "Other",
                localAssets = localAssets,
                apiAssets = apiAssets,
                ttsAssets = emptyList(),
                liveVoiceAssets = emptyList(),
                mediaAssets = emptyList(),
                onDismiss = {},
                onSelect = { _, _, _, _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("Local Models").assertIsDisplayed()
        composeTestRule.onNodeWithText("API Models").assertIsDisplayed()
    }

    @Test
    fun `S15 — Empty state shows no options message and Confirm is disabled`() {
        composeTestRule.setContent {
            AssignmentSelectionContent(
                slotLabel = "Other",
                localAssets = emptyList(),
                apiAssets = emptyList(),
                ttsAssets = emptyList(),
                liveVoiceAssets = emptyList(),
                mediaAssets = emptyList(),
                onDismiss = {},
                onSelect = { _, _, _, _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("No options available.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confirm").assertIsNotEnabled()
    }
}

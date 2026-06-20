package com.browntowndev.enclave.feature.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DeepSeekEndpoint
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeepSeekCredentialsFieldsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `SCN-MUT-005 editable DeepSeek base URL field fails read-only requirement`() {
        composeTestRule.setContent {
            EnclaveTheme {
                DeepSeekBaseUrlField(
                    asset = deepSeekAsset(baseUrl = DeepSeekEndpoint.OFFICIAL.baseUrl),
                    onAssetChange = {},
                )
            }
        }

        composeTestRule.onNodeWithText(DeepSeekEndpoint.OFFICIAL.baseUrl, useUnmergedTree = true)
            .assertIsDisplayed()
        assertEquals(
            false,
            composeTestRule
                .onNodeWithText(DeepSeekEndpoint.OFFICIAL.baseUrl, useUnmergedTree = true)
                .fetchSemanticsNode()
                .config
                .contains(SemanticsActions.SetText),
        )
    }

    @Test
    fun `SCN-UI-002 DeepSeek base URL field is read-only official endpoint`() {
        composeTestRule.setContent {
            EnclaveTheme {
                DeepSeekBaseUrlField(
                    asset = deepSeekAsset(baseUrl = DeepSeekEndpoint.OFFICIAL.baseUrl),
                    onAssetChange = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Base URL", useUnmergedTree = true).assertIsDisplayed()
        val fieldNode = composeTestRule.onNodeWithText(DeepSeekEndpoint.OFFICIAL.baseUrl, useUnmergedTree = true)
        fieldNode.assertIsDisplayed()
        assertEquals(false, fieldNode.fetchSemanticsNode().config.contains(SemanticsActions.SetText))
    }

    @Test
    fun `SCN-UI-003 legacy base URL migrates to official endpoint on load`() {
        var migratedAsset: ApiModelAssetUi? = null
        composeTestRule.setContent {
            EnclaveTheme {
                DeepSeekBaseUrlField(
                    asset = deepSeekAsset(baseUrl = "https://proxy.example/v1"),
                    onAssetChange = { migratedAsset = it },
                )
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(DeepSeekEndpoint.OFFICIAL.baseUrl, migratedAsset?.baseUrl)
    }

    @Test
    fun `SCN-UI-004 selecting DeepSeek provider sets official base URL`() {
        val initialAsset =
            ApiModelAssetUi(
                credentialsId = ApiCredentialsId("cred-1"),
                displayName = "OpenAI",
                provider = ApiProvider.OPENAI,
                baseUrl = "https://api.openai.com/v1",
                credentialAlias = "alias",
                configurations = emptyList(),
            )
        var updatedAsset: ApiModelAssetUi? = null
        composeTestRule.setContent {
            EnclaveTheme {
                ProviderSelectionField(
                    asset = initialAsset,
                    onAssetChange = { updatedAsset = it },
                )
            }
        }

        composeTestRule.onNodeWithText("OpenAI", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithText("DeepSeek", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        assertEquals(ApiProvider.DEEPSEEK, updatedAsset?.provider)
        assertEquals(DeepSeekEndpoint.OFFICIAL.baseUrl, updatedAsset?.baseUrl)
    }

    private fun deepSeekAsset(baseUrl: String): ApiModelAssetUi =
        ApiModelAssetUi(
            credentialsId = ApiCredentialsId("cred-deepseek"),
            displayName = "DeepSeek",
            provider = ApiProvider.DEEPSEEK,
            baseUrl = baseUrl,
            credentialAlias = "alias",
            configurations = emptyList(),
        )
}
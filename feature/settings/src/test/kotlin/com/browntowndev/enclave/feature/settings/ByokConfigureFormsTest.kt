package com.browntowndev.enclave.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.config.OpenRouterRoutingConfiguration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ByokConfigureFormsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun contextConfiguration_withoutMaxTokens_displaysContextWindowField() {
        composeTestRule.setContent {
            EnclaveTheme {
                ContextConfiguration(
                    config = ApiModelConfigUi(contextWindow = "200000"),
                    hasReadonlyContextWindow = false,
                    supportsMaxTokens = false,
                    onConfigChange = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("ApiContextWindowField", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("200000", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("ApiMaxTokensField", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun contextConfiguration_withMaxTokens_displaysBothFields() {
        composeTestRule.setContent {
            EnclaveTheme {
                ContextConfiguration(
                    config = ApiModelConfigUi(contextWindow = "128000", maxTokens = "4096"),
                    hasReadonlyContextWindow = false,
                    supportsMaxTokens = true,
                    onConfigChange = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("ApiContextWindowField", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("ApiMaxTokensField", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun openRouterRoutingSection_withCanSelectTargetProvider_isClickableAndTriggersCallback() {
        var onShowProvidersSheetCalled = false
        composeTestRule.setContent {
            EnclaveTheme {
                OpenRouterRoutingSection(
                    routing = OpenRouterRoutingConfiguration(),
                    onRoutingChange = {},
                    canSelectTargetProvider = true,
                    onShowProvidersSheet = { onShowProvidersSheetCalled = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("OpenRouterPreferredProviderBox", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()

        assert(onShowProvidersSheetCalled)
    }

    @Test
    fun openRouterRoutingSection_withoutCanSelectTargetProvider_isNotClickableAndDoesNotTriggerCallback() {
        var onShowProvidersSheetCalled = false
        composeTestRule.setContent {
            EnclaveTheme {
                OpenRouterRoutingSection(
                    routing = OpenRouterRoutingConfiguration(),
                    onRoutingChange = {},
                    canSelectTargetProvider = false,
                    onShowProvidersSheet = { onShowProvidersSheetCalled = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("OpenRouterPreferredProviderBox", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()

        assert(!onShowProvidersSheetCalled)
    }

    @Test
    fun includeReasoningContentOnRequestSwitch_displaysToggleLabel() {
        composeTestRule.setContent {
            EnclaveTheme {
                IncludeReasoningContentOnRequestSwitch(
                    checked = false,
                    onCheckedChange = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Include reasoning content on request").assertIsDisplayed()
    }
}

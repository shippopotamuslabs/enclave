package com.browntowndev.enclave.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.feature.settings.usage.UsageSpendBottomSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsUsageSpendScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `TEL_007 settings lifetime view shows provider spend totals`() {
        composeTestRule.setContent {
            EnclaveTheme {
                UsageSpendBottomSheet(
                    state =
                        UsageSpendSheetUiState(
                            isVisible = true,
                            selectedPeriod = UsagePeriodUi.LIFETIME,
                            totalCostLabel = "$0.006700",
                            providerRows =
                                listOf(
                                    UsageSpendProviderRowUi("OpenAI", "$0.002500", 1),
                                    UsageSpendProviderRowUi("Anthropic", "$0.003000", 1),
                                    UsageSpendProviderRowUi("OpenRouter", "$0.001200", 1),
                                    UsageSpendProviderRowUi("LiteRT", "Unknown", 1),
                                ),
                        ),
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("usage_spend_period").assertExists()
        composeTestRule.onNodeWithTag("usage_spend_total").assertExists()
        composeTestRule.onNodeWithText("OpenAI").assertExists()
        composeTestRule.onNodeWithText("$0.002500").assertExists()
        composeTestRule.onNodeWithText("Anthropic").assertExists()
        composeTestRule.onNodeWithText("$0.003000").assertExists()
        composeTestRule.onNodeWithText("OpenRouter").assertExists()
        composeTestRule.onNodeWithText("$0.001200").assertExists()
        composeTestRule.onNodeWithText("LiteRT").assertExists()
        composeTestRule.onNodeWithText("Unknown").assertExists()
    }

    @Test
    fun `TEL_012 settings sheet surfaces missing cost turn count`() {
        composeTestRule.setContent {
            EnclaveTheme {
                UsageSpendBottomSheet(
                    state =
                        UsageSpendSheetUiState(
                            isVisible = true,
                            selectedPeriod = UsagePeriodUi.LIFETIME,
                            totalCostLabel = "$0.005500",
                            missingCostTurnsLabel = "1",
                            providerRows =
                                listOf(
                                    UsageSpendProviderRowUi("LiteRT", "Unknown", 1),
                                ),
                        ),
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("telemetry_metric_missing_cost").assertExists()
        composeTestRule.onNodeWithText("1").assertExists()
        composeTestRule.onNodeWithText("LiteRT").assertExists()
    }
}

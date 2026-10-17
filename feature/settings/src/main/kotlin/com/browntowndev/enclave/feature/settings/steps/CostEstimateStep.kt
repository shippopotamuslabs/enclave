package com.browntowndev.enclave.feature.settings.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveCircularProgressIndicator
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveGroupDivider
import com.browntowndev.enclave.core.ui.component.enclave.EnclavePanel
import com.browntowndev.enclave.core.ui.component.enclave.EnclavePrimaryButton
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSecondaryButton
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSectionHeader
import com.browntowndev.enclave.feature.settings.ProfileCostEstimateUi

@Composable
internal fun CostEstimateStep(
    isLoading: Boolean,
    estimate: ProfileCostEstimateUi?,
    onBack: () -> Unit,
    onStartAnalysis: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        EnclaveSectionHeader(text = "Cost estimate")

        when {
            isLoading -> {
                Spacer(Modifier.height(32.dp))
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EnclaveCircularProgressIndicator()
                    Text(
                        text = "Preparing corpus…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
            }

            estimate != null -> {
                Spacer(Modifier.height(8.dp))
                EnclavePanel {
                    StatRow(
                        label = "Conversations",
                        value = "${estimate.selectedConversationCount} / ${estimate.totalConversationCount}",
                    )
                    EnclaveGroupDivider()
                    StatRow(
                        label = "Input tokens",
                        value = "~${"%,d".format(estimate.estimatedInputTokens)}",
                    )
                    EnclaveGroupDivider()
                    StatRow(
                        label = "Output tokens",
                        value = "~${"%,d".format(estimate.estimatedOutputTokens)}",
                    )
                    EnclaveGroupDivider()
                    StatRow(
                        label = "Estimated cost",
                        value =
                            if (estimate.estimatedCostUsd != null) {
                                "~\$${estimate.estimatedCostUsd}"
                            } else {
                                "N/A"
                            },
                        valueNote =
                            if (estimate.estimatedCostUsd == null) {
                                "No pricing data for this model"
                            } else {
                                null
                            },
                    )
                }

                Spacer(Modifier.height(12.dp))

                EnclavePanel {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text =
                                "Analysis runs 4 passes using your API key. " +
                                    "The full corpus stays on your device — only relevant text " +
                                    "excerpts are sent to your configured API provider.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
            }

            else -> {
                Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(16.dp))
        Row {
            EnclaveSecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.weight(1f))
            Spacer(Modifier.padding(4.dp))
            EnclavePrimaryButton(
                text = "Run Analysis",
                onClick = onStartAnalysis,
                enabled = !isLoading && estimate != null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    valueNote: String? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (valueNote != null) {
                Text(
                    text = valueNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

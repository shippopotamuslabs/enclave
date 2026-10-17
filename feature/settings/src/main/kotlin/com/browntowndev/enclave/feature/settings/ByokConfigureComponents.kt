package com.browntowndev.enclave.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.PersistentTooltip
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveOutlinedTextField
import com.browntowndev.enclave.core.ui.component.enclave.EnclavePrimaryButton
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSecondaryButton
import com.browntowndev.enclave.core.ui.component.enclave.enclaveBorder
import com.browntowndev.enclave.core.ui.component.enclave.enclaveBorderColor
import com.browntowndev.enclave.core.ui.component.enclave.enclaveSurface
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort

@Composable
fun OpenRouterRoutingCard(
    routing: OpenRouterRoutingUi,
    onRoutingChange: (OpenRouterRoutingUi) -> Unit,
    canSelectTargetProvider: Boolean = false,
    onShowProvidersSheet: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ConfigurationHeader("OpenRouter Routing")
        OpenRouterRoutingSection(
            routing = routing,
            onRoutingChange = onRoutingChange,
            canSelectTargetProvider = canSelectTargetProvider,
            onShowProvidersSheet = onShowProvidersSheet,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenRouterRoutingSection(
    routing: OpenRouterRoutingUi,
    onRoutingChange: (OpenRouterRoutingUi) -> Unit,
    canSelectTargetProvider: Boolean = false,
    onShowProvidersSheet: () -> Unit = {},
) {
    var sortExpanded by remember(routing.providerSort) { mutableStateOf(false) }
    val showFallbackOptions = routing.preferredProvider == null || routing.allowFallbacks

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("OpenRouterPreferredProviderBox")
                    .then(
                        if (canSelectTargetProvider) {
                            Modifier.clickable(onClick = onShowProvidersSheet)
                        } else {
                            Modifier
                        },
                    ),
        ) {
            EnclaveOutlinedTextField(
                value = routing.preferredProvider ?: "Automatic",
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("Target Provider") },
                supportingText =
                    if (canSelectTargetProvider) {
                        null
                    } else {
                        { Text("Select a model before choosing a target provider.") }
                    },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Select target provider",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        RoutingSwitchRow(
            label = "Allow Fallbacks",
            description =
                if (routing.preferredProvider != null) {
                    "Pinned to a provider — fallbacks disabled to keep the prompt cache warm."
                } else {
                    "OpenRouter may fall back to alternate providers when the primary one is unavailable."
                },
            checked = routing.allowFallbacks,
            onCheckedChange = { onRoutingChange(routing.copy(allowFallbacks = it)) },
            enabled = routing.preferredProvider == null,
        )

        if (showFallbackOptions) {
            ExposedDropdownMenuBox(
                expanded = sortExpanded,
                onExpandedChange = { sortExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                EnclaveOutlinedTextField(
                    value = routing.providerSort.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Provider Sort") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortExpanded) },
                    modifier =
                        Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false },
                    modifier = Modifier.enclaveBorder(color = enclaveBorderColor(), shape = EnclaveNeoTokens.shape),
                    shape = EnclaveNeoTokens.shape,
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    OpenRouterProviderSortUi.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.displayName) },
                            onClick = {
                                onRoutingChange(routing.copy(providerSort = sort))
                                sortExpanded = false
                            },
                        )
                    }
                }
            }

            RoutingSwitchRow(
                label = "Require Parameters",
                description = "Only route to providers that support every parameter in the request.",
                checked = routing.requireParameters,
                onCheckedChange = { onRoutingChange(routing.copy(requireParameters = it)) },
            )

            RoutingSwitchRow(
                label = "Allow Data Collection",
                description = "Allow providers to collect and store prompts and completions.",
                checked = routing.dataCollectionPolicy == OpenRouterDataCollectionPolicyUi.ALLOW,
                onCheckedChange = { allowed ->
                    onRoutingChange(
                        routing.copy(
                            dataCollectionPolicy = if (allowed) {
                                OpenRouterDataCollectionPolicyUi.ALLOW
                            } else {
                                OpenRouterDataCollectionPolicyUi.DENY
                            }
                        )
                    )
                },
            )

            RoutingSwitchRow(
                label = "Zero Data Retention",
                description = "Prefer providers that support zero data retention for this request.",
                checked = routing.zeroDataRetention,
                onCheckedChange = { onRoutingChange(routing.copy(zeroDataRetention = it)) },
            )
        }
    }
}

@Composable
fun RoutingSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(4.dp))
            PersistentTooltip(description = description)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

internal data class ReasoningOptionUi(
    val label: String,
    val effort: ApiReasoningEffortUi,
)

internal fun reasoningOptions(policy: ApiReasoningPolicyUi): List<ReasoningOptionUi> =
    policy.supportedEfforts.map { effort ->
        ReasoningOptionUi(
            label =
                if (policy.controlStyle == ApiReasoningControlStyleUi.XAI_MULTI_AGENT) {
                    if (effort == ApiReasoningEffortUi.HIGH || effort == ApiReasoningEffortUi.XHIGH) {
                        "16 agents"
                    } else {
                        "4 agents"
                    }
                } else {
                    effort.displayName
                },
            effort = effort,
        )
    }

internal fun reasoningSelectionLabel(
    effort: ApiReasoningEffort?,
    policy: ApiReasoningPolicyUi,
): String {
    if (effort == null) {
        return ""
    }

    val effortUi = effort.toUi()
    return if (policy.controlStyle == ApiReasoningControlStyleUi.XAI_MULTI_AGENT) {
        if (effortUi == ApiReasoningEffortUi.HIGH || effortUi == ApiReasoningEffortUi.XHIGH) {
            "16 agents"
        } else {
            "4 agents"
        }
    } else {
        effortUi.displayName
    }
}

@Composable
fun CustomHeadersList(
    customHeaders: List<CustomHeaderUi>,
    onNavigateToCustomHeaders: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (customHeaders.isNotEmpty()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .enclaveSurface(
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            borderColor = enclaveBorderColor(),
                        ).padding(12.dp),
            ) {
                customHeaders.take(3).forEach { header ->
                    Text(
                        text = "${header.key}: ${header.value}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (customHeaders.size > 3) {
                    Text(
                        text = "... and ${customHeaders.size - 3} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToCustomHeaders)
                    .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Configure",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ConfigurationHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
fun TuningSlider(
    label: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true,
) {
    var showDialog by remember { mutableStateOf(false) }
    var textValue by remember(value) { mutableStateOf("%.2f".format(value)) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(4.dp))
                PersistentTooltip(description = description)
            }
            Text(
                text = "%.2f".format(value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(enabled = enabled) { showDialog = true },
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            colors =
                androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    disabledActiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                ),
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            modifier = Modifier.enclaveBorder(color = enclaveBorderColor(), shape = EnclaveNeoTokens.shape),
            shape = EnclaveNeoTokens.shape,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Edit $label") },
            text = {
                Column {
                    Text(
                        text = "Enter a value between ${"%.2f".format(range.start)} and ${"%.2f".format(range.endInclusive)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    EnclaveOutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EnclaveSecondaryButton(
                        text = "Cancel",
                        onClick = { showDialog = false },
                        modifier = Modifier.weight(1f),
                    )
                    EnclavePrimaryButton(
                        text = "Save",
                        onClick = {
                            val newValue = textValue.toFloatOrNull()
                            if (newValue != null && newValue in range) {
                                onValueChange(newValue)
                                showDialog = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        )
    }
}

@Preview(showBackground = true, name = "BYOK - Custom Headers List Only")
@Composable
fun PreviewByokCustomHeadersList() {
    EnclaveTheme {
        val headers =
            listOf(
                CustomHeaderUi("X-Custom-Header-1", "Value 1"),
                CustomHeaderUi("X-Custom-Header-2", "Value 2"),
                CustomHeaderUi("X-Custom-Header-3", "Value 3"),
                CustomHeaderUi("X-Custom-Header-4", "Value 4"),
            )
        val mockConfig =
            MockSettingsData.apiModels[0].configurations[0].copy(
                customHeaders = headers,
            )

        Column(
            modifier =
                Modifier
                    .padding(16.dp)
                    .enclaveSurface(
                        backgroundColor = MaterialTheme.colorScheme.background,
                        borderColor = enclaveBorderColor(),
                    ),
        ) {
            CustomHeadersList(
                customHeaders = mockConfig.customHeaders,
                onNavigateToCustomHeaders = {},
            )
        }
    }
}

@Composable
fun PresetModelAndVisionSection(
    config: ApiModelConfigUi,
    selectedModelMetadata: DiscoveredApiModelUi?,
    discovery: ApiModelDiscoveryUiState,
    canFetchModels: Boolean,
    onConfigChange: (ApiModelConfigUi) -> Unit,
    callbacks: ModelDiscoveryFieldCallbacks,
) {
    val discoveredVisionCapability = selectedModelMetadata?.isMultimodal
    ModelSelectionField(
        config = config,
        discovery = discovery,
        canFetchModels = canFetchModels,
        onConfigChange = onConfigChange,
        callbacks = callbacks,
    )
    VisionCapabilitySwitch(
        discoveredVisionCapability = discoveredVisionCapability,
        config = config,
        onConfigChange = onConfigChange,
    )
}

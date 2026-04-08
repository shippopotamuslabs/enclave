package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.component.PersistentTooltip
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterDataCollectionPolicy
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterProviderSort
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningControlStyle
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningPolicy

@Composable
fun OpenRouterRoutingCard(
    routing: OpenRouterRoutingConfiguration,
    onRoutingChange: (OpenRouterRoutingConfiguration) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ConfigurationHeader("OpenRouter Routing")
        OpenRouterRoutingSection(
            routing = routing,
            onRoutingChange = onRoutingChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenRouterRoutingSection(
    routing: OpenRouterRoutingConfiguration,
    onRoutingChange: (OpenRouterRoutingConfiguration) -> Unit
) {
    var sortExpanded by remember(routing.providerSort) { mutableStateOf(false) }
    var dataCollectionExpanded by remember(routing.dataCollectionPolicy) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ExposedDropdownMenuBox(
            expanded = sortExpanded,
            onExpandedChange = { sortExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = routing.providerSort.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Provider Sort") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortExpanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = { sortExpanded = false }
            ) {
                OpenRouterProviderSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sort.displayName) },
                        onClick = {
                            onRoutingChange(routing.copy(providerSort = sort))
                            sortExpanded = false
                        }
                    )
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = dataCollectionExpanded,
            onExpandedChange = { dataCollectionExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = routing.dataCollectionPolicy.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Data Collection Policy") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dataCollectionExpanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(
                expanded = dataCollectionExpanded,
                onDismissRequest = { dataCollectionExpanded = false }
            ) {
                OpenRouterDataCollectionPolicy.entries.forEach { policy ->
                    DropdownMenuItem(
                        text = { Text(policy.displayName) },
                        onClick = {
                            onRoutingChange(routing.copy(dataCollectionPolicy = policy))
                            dataCollectionExpanded = false
                        }
                    )
                }
            }
        }

        RoutingSwitchRow(
            label = "Allow Fallbacks",
            description = "OpenRouter may fall back to alternate providers when the primary one is unavailable.",
            checked = routing.allowFallbacks,
            onCheckedChange = { onRoutingChange(routing.copy(allowFallbacks = it)) }
        )

        RoutingSwitchRow(
            label = "Require Parameters",
            description = "Only route to providers that support every parameter in the request.",
            checked = routing.requireParameters,
            onCheckedChange = { onRoutingChange(routing.copy(requireParameters = it)) }
        )

        RoutingSwitchRow(
            label = "Zero Data Retention",
            description = "Prefer providers that support zero data retention for this request.",
            checked = routing.zeroDataRetention,
            onCheckedChange = { onRoutingChange(routing.copy(zeroDataRetention = it)) }
        )
    }
}

@Composable
fun RoutingSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(4.dp))
            PersistentTooltip(description = description)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

internal data class ReasoningOptionUi(
    val label: String,
    val effort: ApiReasoningEffort
)

internal fun reasoningOptions(policy: ApiReasoningPolicy): List<ReasoningOptionUi> =
    policy.supportedEfforts.map { effort ->
        ReasoningOptionUi(
            label = if (policy.controlStyle == ApiReasoningControlStyle.XAI_MULTI_AGENT) {
                if (effort == ApiReasoningEffort.HIGH || effort == ApiReasoningEffort.XHIGH) {
                    "16 agents"
                } else {
                    "4 agents"
                }
            } else {
                effort.displayName
            },
            effort = effort
        )
    }

internal fun reasoningSelectionLabel(
    effort: ApiReasoningEffort?,
    policy: ApiReasoningPolicy
): String {
    if (effort == null) {
        return ""
    }

    return if (policy.controlStyle == ApiReasoningControlStyle.XAI_MULTI_AGENT) {
        if (effort == ApiReasoningEffort.HIGH || effort == ApiReasoningEffort.XHIGH) {
            "16 agents"
        } else {
            "4 agents"
        }
    } else {
        effort.displayName
    }
}

@Composable
fun CustomHeadersList(
    customHeaders: List<CustomHeaderUi>,
    onNavigateToCustomHeaders: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (customHeaders.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    customHeaders.take(3).forEach { header ->
                        Text(
                            text = "${header.key}: ${header.value}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (customHeaders.size > 3) {
                        Text(
                            text = "... and ${customHeaders.size - 3} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToCustomHeaders)
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Configure",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
fun TuningSlider(
    label: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(4.dp))
                PersistentTooltip(description = description)
            }
            Text("%.2f".format(value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
    }
}

@Preview(showBackground = true, name = "BYOK - Custom Headers List Only")
@Composable
fun PreviewByokCustomHeadersList() {
    PocketCrewTheme {
        val headers = listOf(
            CustomHeaderUi("X-Custom-Header-1", "Value 1"),
            CustomHeaderUi("X-Custom-Header-2", "Value 2"),
            CustomHeaderUi("X-Custom-Header-3", "Value 3"),
            CustomHeaderUi("X-Custom-Header-4", "Value 4")
        )
        val mockConfig = MockSettingsData.apiModels[0].configurations[0].copy(
            customHeaders = headers
        )

        Surface(
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            CustomHeadersList(
                customHeaders = mockConfig.customHeaders,
                onNavigateToCustomHeaders = {}
            )
        }
    }
}

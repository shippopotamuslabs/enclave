@file:Suppress("MagicNumber", "CyclomaticComplexMethod")
package com.browntowndev.enclave.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.browntowndev.enclave.core.ui.component.neo.NeoCircularProgressIndicator
import com.browntowndev.enclave.core.ui.component.neo.NeoModalBottomSheet
import com.browntowndev.enclave.core.ui.component.neo.NeoSelectionCard
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetHeader
import com.browntowndev.enclave.core.ui.component.neo.neoBorderColor
import com.browntowndev.enclave.core.ui.component.neo.neoSurface
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.config.ApiModelPricingFormat
import com.browntowndev.enclave.domain.model.config.OpenRouterModelEndpoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenRouterProvidersBottomSheet(
    openRouterEndpointsState: OpenRouterEndpointsUiState,
    selectedPreferredProvider: String?,
    onSelectPreferredProvider: (String?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    NeoModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        OpenRouterProvidersSheetContent(
            openRouterEndpointsState = openRouterEndpointsState,
            selectedPreferredProvider = selectedPreferredProvider,
            onSelectPreferredProvider = onSelectPreferredProvider,
            onDismissRequest = onDismissRequest,
        )
    }
}

enum class ProviderSortOption(val displayName: String) {
    PRICE("Price"),
    LATENCY("Latency"),
    THROUGHPUT("Throughput"),
    UPTIME("Uptime"),
}

@Composable
internal fun OpenRouterProvidersSheetContent(
    openRouterEndpointsState: OpenRouterEndpointsUiState,
    selectedPreferredProvider: String?,
    onSelectPreferredProvider: (String?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var currentSortOption by remember { mutableStateOf(ProviderSortOption.PRICE) }

    val sortedEndpoints = remember(openRouterEndpointsState.endpoints, currentSortOption) {
        when (currentSortOption) {
            ProviderSortOption.PRICE -> {
                openRouterEndpointsState.endpoints.sortedWith(
                    compareBy<OpenRouterModelEndpoint> { it.promptPriceUsdPerMillion + it.completionPriceUsdPerMillion }
                        .thenBy { it.providerName }
                )
            }
            ProviderSortOption.LATENCY -> {
                openRouterEndpointsState.endpoints.sortedWith(
                    compareBy<OpenRouterModelEndpoint> { it.latency30m ?: Double.MAX_VALUE }
                        .thenBy { it.providerName }
                )
            }
            ProviderSortOption.THROUGHPUT -> {
                openRouterEndpointsState.endpoints.sortedWith(
                    compareByDescending<OpenRouterModelEndpoint> { it.throughput30m ?: -1.0 }
                        .thenBy { it.providerName }
                )
            }
            ProviderSortOption.UPTIME -> {
                openRouterEndpointsState.endpoints.sortedWith(
                    compareByDescending<OpenRouterModelEndpoint> { it.uptime1d ?: -1.0 }
                        .thenBy { it.providerName }
                )
            }
        }
    }

    @Suppress("MagicNumber")
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
    ) {
        NeoSheetHeader(
            title = "Target Provider",
            subtitle = "Choose a preferred OpenRouter endpoint or use automatic routing.",
            onDismiss = onDismissRequest,
        )

        // Sorting Option Header
        if (!openRouterEndpointsState.isLoading && !openRouterEndpointsState.hasError && openRouterEndpointsState.endpoints.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EnclaveNeoTokens.screenHorizontalPadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Sort by:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    ProviderSortOption.entries.forEach { option ->
                        val isSelected = currentSortOption == option
                        val chipBgColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                        val chipTextColor = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val cardBorderColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            neoBorderColor()
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    color = chipBgColor,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = cardBorderColor,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { currentSortOption = option }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = option.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = chipTextColor
                            )
                        }
                    }
                }
            }
        }

        when {
            openRouterEndpointsState.isLoading -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    NeoCircularProgressIndicator()
                }
            }

            openRouterEndpointsState.hasError -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = EnclaveNeoTokens.screenHorizontalPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Failed to load providers. OpenRouter will use automatic routing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.rowSpacing),
                ) {
                    item(key = "automatic-routing") {
                        NeoSelectionCard(
                            title = "Automatic routing",
                            subtitle = "Let OpenRouter choose the best provider for each request.",
                            isSelected = selectedPreferredProvider == null,
                            onClick = {
                                onSelectPreferredProvider(null)
                                onDismissRequest()
                            },
                        )
                    }

                    items(
                        items = sortedEndpoints,
                        key = { it.providerSlug },
                    ) { endpoint ->
                        OpenRouterEndpointCard(
                            endpoint = endpoint,
                            isSelected = selectedPreferredProvider == endpoint.providerSlug,
                            onClick = {
                                onSelectPreferredProvider(endpoint.providerSlug)
                                onDismissRequest()
                            },
                        )
                    }

                    if (openRouterEndpointsState.endpoints.isEmpty()) {
                        item(key = "empty-endpoints") {
                            Text(
                                text = "No providers returned for this model.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricSignalStrengthIndicator(
    activeBars: Int,
    modifier: Modifier = Modifier,
    totalBars: Int = 5,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        @Suppress("MagicNumber")
        for (i in 1..totalBars) {
            val barHeight = (4 + i * 3).dp // 7.dp, 10.dp, 13.dp, 16.dp, 19.dp
            val isFilled = i <= activeBars

            // Color-code active bars: 1=Red, 2=Orange, 3=Yellow, 4/5=Green
            val barColor = if (isFilled) {
                when (activeBars) {
                    1 -> Color(0xFFEF4444)
                    2 -> Color(0xFFF97316)
                    3 -> Color(0xFFEAB308)
                    else -> Color(0xFF22C55E)
                }
            } else {
                inactiveColor
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .background(color = barColor)
                    .border(width = 1.dp, color = neoBorderColor())
            )
        }
    }
}

@Composable
fun MiniChip(
    iconRes: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MetricRow(
    label: String,
    value: String,
    activeBars: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricSignalStrengthIndicator(
                activeBars = activeBars,
                modifier = Modifier.width(32.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.width(56.dp)
            )
        }
    }
}

@Composable
fun OpenRouterEndpointCard(
    endpoint: OpenRouterModelEndpoint,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else neoBorderColor()
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val uptime1d = endpoint.uptime1d
    val latency30m = endpoint.latency30m
    val throughput30m = endpoint.throughput30m

    Column(
        modifier = modifier
            .fillMaxWidth()
            .neoSurface(
                backgroundColor = backgroundColor,
                borderColor = borderColor,
                withShadow = false,
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Title and Provider Slug Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = endpoint.providerName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = endpoint.providerSlug,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!endpoint.isOperational) {
                Text(
                    text = "Offline",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red,
                    modifier = Modifier
                        .background(
                            color = Color.Red.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Chips Row (Context and Quantization)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniChip(
                iconRes = com.browntowndev.enclave.core.ui.R.drawable.ic_context,
                text = "${endpoint.contextLength / 1_000}k context"
            )
            if (endpoint.quantization.isNotBlank() &&
                !endpoint.quantization.equals("unknown", ignoreCase = true) &&
                !endpoint.quantization.equals("none", ignoreCase = true)
            ) {
                MiniChip(
                    iconRes = com.browntowndev.enclave.core.ui.R.drawable.ic_quantization,
                    text = endpoint.quantization
                )
            }
        }

        // Pricing Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Input: ",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = ApiModelPricingFormat.displayUsdPerMillion(endpoint.promptPriceUsdPerMillion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Output: ",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = ApiModelPricingFormat.displayUsdPerMillion(endpoint.completionPriceUsdPerMillion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Performance Metrics Column
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Uptime Row
            val uptimeBars = when {
                !endpoint.isOperational -> 0
                uptime1d == null -> 0
                uptime1d >= 99.9 -> 5
                uptime1d >= 99.0 -> 4
                uptime1d >= 95.0 -> 3
                uptime1d >= 90.0 -> 2
                else -> 1
            }
            val uptimeValue = if (uptime1d != null) {
                "%.1f%%".format(uptime1d)
            } else {
                "N/A"
            }
            MetricRow(
                label = "Uptime (1d)",
                value = uptimeValue,
                activeBars = uptimeBars
            )

            // Latency Row
            val latencyBars = when {
                !endpoint.isOperational -> 0
                latency30m == null -> 0
                latency30m <= 0.3 -> 5
                latency30m <= 0.6 -> 4
                latency30m <= 1.2 -> 3
                latency30m <= 2.5 -> 2
                else -> 1
            }
            val latencyValue = if (latency30m != null) {
                "%.2fs".format(latency30m)
            } else {
                "N/A"
            }
            MetricRow(
                label = "Latency (30m p50)",
                value = latencyValue,
                activeBars = latencyBars
            )

            // Throughput Row
            val throughputBars = when {
                !endpoint.isOperational -> 0
                throughput30m == null -> 0
                throughput30m >= 80.0 -> 5
                throughput30m >= 45.0 -> 4
                throughput30m >= 25.0 -> 3
                throughput30m >= 10.0 -> 2
                else -> 1
            }
            val throughputValue = if (throughput30m != null) {
                "%.0f tps".format(throughput30m)
            } else {
                "N/A"
            }
            MetricRow(
                label = "Throughput (30m)",
                value = throughputValue,
                activeBars = throughputBars
            )
        }
    }
}

@Preview(showBackground = true, name = "OpenRouter Providers Sheet")
@Composable
fun PreviewOpenRouterProvidersBottomSheet() {
    EnclaveTheme {
        OpenRouterProvidersSheetContent(
            openRouterEndpointsState =
                OpenRouterEndpointsUiState(
                    endpoints =
                        listOf(
                            OpenRouterModelEndpoint(
                                providerName = "Together",
                                providerSlug = "together",
                                contextLength = 131_072,
                                promptPriceUsdPerMillion = 0.7,
                                completionPriceUsdPerMillion = 0.9,
                                quantization = "fp8",
                                isOperational = true,
                                uptime1d = 99.95,
                                latency30m = 0.25,
                                throughput30m = 45.0,
                            ),
                            OpenRouterModelEndpoint(
                                providerName = "DeepInfra",
                                providerSlug = "deepinfra",
                                contextLength = 131_072,
                                promptPriceUsdPerMillion = 1.25,
                                completionPriceUsdPerMillion = 1.25,
                                quantization = "fp16",
                                isOperational = true,
                                uptime1d = 99.99,
                                latency30m = 0.12,
                                throughput30m = 120.0,
                            ),
                            OpenRouterModelEndpoint(
                                providerName = "Lepton",
                                providerSlug = "lepton",
                                contextLength = 65_536,
                                promptPriceUsdPerMillion = 0.5,
                                completionPriceUsdPerMillion = 0.5,
                                quantization = "",
                                isOperational = true,
                                uptime1d = 99.2,
                                latency30m = null,
                                throughput30m = null,
                            ),
                            OpenRouterModelEndpoint(
                                providerName = "OpenPipe",
                                providerSlug = "openpipe",
                                contextLength = 8_192,
                                promptPriceUsdPerMillion = 3.0,
                                completionPriceUsdPerMillion = 6.0,
                                quantization = "bf16",
                                isOperational = false,
                                uptime1d = 0.0,
                                latency30m = 3.5,
                                throughput30m = 5.0,
                            ),
                            OpenRouterModelEndpoint(
                                providerName = "Fireworks",
                                providerSlug = "fireworks",
                                contextLength = 16_384,
                                promptPriceUsdPerMillion = 0.0,
                                completionPriceUsdPerMillion = 0.0,
                                quantization = "",
                                isOperational = true,
                                uptime1d = null,
                                latency30m = null,
                                throughput30m = null,
                            ),
                        ),
                ),
            selectedPreferredProvider = "together",
            onSelectPreferredProvider = {},
            onDismissRequest = {},
        )
    }
}

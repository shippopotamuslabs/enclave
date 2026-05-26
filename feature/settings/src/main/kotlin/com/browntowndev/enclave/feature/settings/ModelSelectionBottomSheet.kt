package com.browntowndev.enclave.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoFilterChip
import com.browntowndev.enclave.core.ui.component.neo.NeoIconButton
import com.browntowndev.enclave.core.ui.component.neo.NeoModalBottomSheet
import com.browntowndev.enclave.core.ui.component.neo.NeoSearchField
import com.browntowndev.enclave.core.ui.component.neo.NeoSectionHeader
import com.browntowndev.enclave.core.ui.component.neo.NeoSelectionCard
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetHeader
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.config.ApiModelPricingFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionBottomSheet(
    availableModels: List<DiscoveredApiModelUi>,
    filteredModels: List<DiscoveredApiModelUi>,
    searchQuery: String,
    providerFilters: Set<String>,
    sortOption: ModelSortOption,
    onUpdateSearchQuery: (String) -> Unit,
    onToggleProviderFilter: (String) -> Unit,
    onClearProviderFilters: () -> Unit,
    onUpdateSortOption: (ModelSortOption) -> Unit,
    onModelSelected: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    NeoModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        ModelSelectionContent(
            availableModels = availableModels,
            filteredModels = filteredModels,
            searchQuery = searchQuery,
            providerFilters = providerFilters,
            sortOption = sortOption,
            onUpdateSearchQuery = onUpdateSearchQuery,
            onToggleProviderFilter = onToggleProviderFilter,
            onClearProviderFilters = onClearProviderFilters,
            onUpdateSortOption = onUpdateSortOption,
            onModelSelected = onModelSelected,
            onDismissRequest = onDismissRequest,
        )
    }
}

@Suppress("CyclomaticComplexMethod")
@Composable
fun ModelSelectionContent(
    availableModels: List<DiscoveredApiModelUi>,
    filteredModels: List<DiscoveredApiModelUi>,
    searchQuery: String,
    providerFilters: Set<String>,
    sortOption: ModelSortOption,
    onUpdateSearchQuery: (String) -> Unit,
    onToggleProviderFilter: (String) -> Unit,
    onClearProviderFilters: () -> Unit,
    onUpdateSortOption: (ModelSortOption) -> Unit,
    onModelSelected: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var isProviderFiltering by remember { mutableStateOf(false) }

    @Suppress("MagicNumber")
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
    ) {
        NeoSheetHeader(
            title = "Select Model",
            onDismiss = onDismissRequest,
        )

        val providers =
            remember(availableModels) {
                availableModels.mapNotNull { it.providerName }.distinct().sorted()
            }
        val filteredProviders =
            remember(providers, searchQuery, isProviderFiltering) {
                if (isProviderFiltering) {
                    providers.filter { it.contains(searchQuery, ignoreCase = true) }
                } else {
                    emptyList()
                }
            }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NeoIconButton(
                onClick = {
                    if (isProviderFiltering) {
                        onUpdateSearchQuery("")
                        isProviderFiltering = false
                    } else {
                        onUpdateSearchQuery("")
                        isProviderFiltering = true
                    }
                },
                icon =
                    if (isProviderFiltering) {
                        Icons.AutoMirrored.Filled.ArrowBack
                    } else {
                        Icons.Default.FilterList
                    },
                contentDescription =
                    if (isProviderFiltering) {
                        "Back to models"
                    } else {
                        "Filter by provider"
                    },
                size = 48.dp,
                withShadow = true,
            )

            NeoSearchField(
                value = searchQuery,
                onValueChange = onUpdateSearchQuery,
                placeholder =
                    if (isProviderFiltering) {
                        "Search providers"
                    } else {
                        "Search models"
                    },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(EnclaveNeoTokens.sectionSpacing))

        if (!isProviderFiltering) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val hasCreated = availableModels.any { it.created != null }
                val hasPrice = availableModels.any { it.promptPrice != null || it.completionPrice != null }

                if (hasCreated || hasPrice) {
                    NeoFilterChip(
                        label = "A-Z",
                        selected = sortOption == ModelSortOption.A_TO_Z,
                        onClick = { onUpdateSortOption(ModelSortOption.A_TO_Z) },
                    )
                    if (hasCreated) {
                        NeoFilterChip(
                            label = "Newest",
                            selected = sortOption == ModelSortOption.NEWEST,
                            onClick = { onUpdateSortOption(ModelSortOption.NEWEST) },
                        )
                    }
                    if (hasPrice) {
                        NeoFilterChip(
                            label = "Price",
                            selected = sortOption == ModelSortOption.PRICE_LOW_TO_HIGH,
                            onClick = { onUpdateSortOption(ModelSortOption.PRICE_LOW_TO_HIGH) },
                        )
                    }
                }
            }
            if (providerFilters.isNotEmpty()) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    providerFilters.forEach { provider ->
                        NeoFilterChip(
                            label = provider,
                            selected = true,
                            onClick = { onToggleProviderFilter(provider) },
                            showClearIconWhenSelected = true,
                        )
                    }
                    if (providerFilters.size > 1) {
                        Text(
                            text = "Clear All",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            modifier =
                                Modifier
                                    .align(Alignment.CenterVertically)
                                    .clickable { onClearProviderFilters() }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            NeoSectionHeader(text = "Select Providers")
        }

        val showCustomModel = !isProviderFiltering && searchQuery.isNotBlank() && filteredModels.none { it.modelId == searchQuery }

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.rowSpacing),
        ) {
            if (isProviderFiltering) {
                items(filteredProviders, key = { it }) { provider ->
                    val isSelected = providerFilters.contains(provider)
                    NeoSelectionCard(
                        title = provider,
                        isSelected = isSelected,
                        onClick = { onToggleProviderFilter(provider) },
                    )
                }
            } else {
                if (showCustomModel) {
                    item(key = "custom:$searchQuery") {
                        NeoSelectionCard(
                            title = "Use custom model: $searchQuery",
                            onClick = {
                                onModelSelected(searchQuery)
                                onDismissRequest()
                            },
                        )
                    }
                }

                items(filteredModels, key = { it.modelId }) { model ->
                    NeoSelectionCard(
                        title = model.name ?: model.modelId,
                        subtitle = modelSelectionSubtitle(model),
                        onClick = {
                            onModelSelected(model.modelId)
                            onDismissRequest()
                        },
                    )
                }
            }
        }
    }
}

private fun modelSelectionSubtitle(model: DiscoveredApiModelUi): String? {
    val parts = mutableListOf<String>()
    if (model.name != null) {
        parts += model.modelId
    }
    model.contextWindowTokens?.let { parts += "Ctx: ${it / 1000}k" }
    model.promptPrice?.let { parts += "In: ${ApiModelPricingFormat.displayUsdPerMillion(it)}" }
    model.completionPrice?.let { parts += "Out: ${ApiModelPricingFormat.displayUsdPerMillion(it)}" }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Preview(showBackground = true, name = "Model Selection - Filtered")
@Composable
fun PreviewModelSelectionBottomSheet() {
    val mockModels =
        listOf(
            DiscoveredApiModelUi(
                modelId = "openai/gpt-4o",
                name = "GPT-4o",
                contextWindowTokens = 128000,
                promptPrice = 5.0,
                completionPrice = 15.0,
                providerName = "OpenAI",
                created = 1715558400L,
            ),
            DiscoveredApiModelUi(
                modelId = "anthropic/claude-3-5-sonnet",
                name = "Claude 3.5 Sonnet",
                contextWindowTokens = 200000,
                promptPrice = 3.0,
                completionPrice = 15.0,
                providerName = "Anthropic",
                created = 1718841600L,
            ),
        )

    EnclaveTheme(darkTheme = true) {
        ModelSelectionContent(
            availableModels = mockModels,
            filteredModels = mockModels,
            searchQuery = "",
            providerFilters = setOf("OpenAI"),
            sortOption = ModelSortOption.A_TO_Z,
            onUpdateSearchQuery = {},
            onToggleProviderFilter = {},
            onClearProviderFilters = {},
            onUpdateSortOption = {},
            onModelSelected = {},
            onDismissRequest = {},
        )
    }
}

@Preview(showBackground = true, name = "Model Selection - Searching")
@Composable
fun PreviewModelSelectionBottomSheetSearching() {
    EnclaveTheme(darkTheme = true) {
        ModelSelectionContent(
            availableModels = emptyList(),
            filteredModels = emptyList(),
            searchQuery = "grok",
            providerFilters = emptySet(),
            sortOption = ModelSortOption.A_TO_Z,
            onUpdateSearchQuery = {},
            onToggleProviderFilter = {},
            onClearProviderFilters = {},
            onUpdateSortOption = {},
            onModelSelected = {},
            onDismissRequest = {},
        )
    }
}

@Preview(showBackground = true, name = "Model Selection - Provider Filter")
@Composable
fun PreviewModelSelectionBottomSheetFiltered() {
    EnclaveTheme(darkTheme = true) {
        ModelSelectionContent(
            availableModels =
                listOf(
                    DiscoveredApiModelUi(modelId = "x-ai/grok", name = "Grok", providerName = "xAI"),
                    DiscoveredApiModelUi(modelId = "openai/gpt-4o", name = "GPT-4o", providerName = "OpenAI"),
                ),
            filteredModels = emptyList(),
            searchQuery = "",
            providerFilters = emptySet(),
            sortOption = ModelSortOption.A_TO_Z,
            onUpdateSearchQuery = {},
            onToggleProviderFilter = {},
            onClearProviderFilters = {},
            onUpdateSortOption = {},
            onModelSelected = {},
            onDismissRequest = {},
        )
    }
}

package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.component.sheet.JumpFreeModalBottomSheet
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

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
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    JumpFreeModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
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
            onDismissRequest = onDismissRequest
        )
    }
}

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
    onDismissRequest: () -> Unit
) {
    var isProviderFiltering by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        val providers = androidx.compose.runtime.remember(availableModels) {
            availableModels.mapNotNull { it.providerName }.distinct().sorted()
        }
        val filteredProviders = androidx.compose.runtime.remember(providers, searchQuery, isProviderFiltering) {
            if (isProviderFiltering) {
                providers.filter { it.contains(searchQuery, ignoreCase = true) }
            } else {
                emptyList()
            }
        }

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isProviderFiltering) {
                androidx.compose.material3.IconButton(onClick = { 
                    onUpdateSearchQuery("")
                    isProviderFiltering = false 
                }) {
                    androidx.compose.material3.Icon(
                        Icons.Default.ArrowBack, 
                        contentDescription = "Back to models"
                    )
                }
            } else {
                androidx.compose.material3.FilledTonalIconButton(
                    onClick = {
                        onUpdateSearchQuery("")
                        isProviderFiltering = true
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Default.FilterList, 
                        contentDescription = "Filter by provider"
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onUpdateSearchQuery,
                label = { Text(if (isProviderFiltering) "Search providers" else "Search models") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        androidx.compose.material3.IconButton(onClick = { onUpdateSearchQuery("") }) {
                            androidx.compose.material3.Icon(
                                Icons.Default.Close, 
                                contentDescription = "Clear search"
                            )
                        }
                    }
                } else null
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (!isProviderFiltering) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
        
                val hasCreated = availableModels.any { it.created != null }
                val hasPrice = availableModels.any { it.promptPrice != null || it.completionPrice != null }
                
                if (hasCreated || hasPrice) {
                    FilterChip(
                        selected = sortOption == ModelSortOption.A_TO_Z,
                        onClick = { onUpdateSortOption(ModelSortOption.A_TO_Z) },
                        label = { Text("A-Z") }
                    )
                    if (hasCreated) {
                        FilterChip(
                            selected = sortOption == ModelSortOption.NEWEST,
                            onClick = { onUpdateSortOption(ModelSortOption.NEWEST) },
                            label = { Text("Newest") }
                        )
                    }
                    if (hasPrice) {
                        FilterChip(
                            selected = sortOption == ModelSortOption.PRICE_LOW_TO_HIGH,
                            onClick = { onUpdateSortOption(ModelSortOption.PRICE_LOW_TO_HIGH) },
                            label = { Text("Price") }
                        )
                    }
                }
            }
            if (providerFilters.isNotEmpty()) {
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    providerFilters.forEach { provider ->
                        androidx.compose.material3.InputChip(
                            selected = true,
                            onClick = { onToggleProviderFilter(provider) },
                            label = { Text(provider) },
                            trailingIcon = {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove filter",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                    if (providerFilters.size > 1) {
                        Text(
                            text = "Clear All",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .align(androidx.compose.ui.Alignment.CenterVertically)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onClearProviderFilters() }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            Text(
                text = "Select Providers",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        val showCustomModel = !isProviderFiltering && searchQuery.isNotBlank() && filteredModels.none { it.modelId == searchQuery }
        val totalItems = if (isProviderFiltering) {
            filteredProviders.size
        } else {
            (if (showCustomModel) 1 else 0) + filteredModels.size
        }

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (isProviderFiltering) {
                items(filteredProviders.size) { index ->
                    val provider = filteredProviders[index]
                    val isSelected = providerFilters.contains(provider)
                    val shape = when {
                        totalItems == 1 -> RoundedCornerShape(16.dp)
                        index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        index == totalItems - 1 -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                        else -> RectangleShape
                    }
                    val borderModifier = if (isSelected) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
                    } else {
                        Modifier
                    }
                    ListItem(
                        headlineContent = { Text(provider) },
                        modifier = Modifier
                            .clip(shape)
                            .then(borderModifier)
                            .clickable {
                                onToggleProviderFilter(provider)
                            },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        trailingContent = {
                            if (isSelected) {
                                androidx.compose.material3.Icon(
                                    Icons.Default.CheckCircle, 
                                    contentDescription = "Selected", 
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            } else {
                if (showCustomModel) {
                    item {
                        val shape = if (totalItems == 1) {
                            RoundedCornerShape(16.dp)
                        } else {
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        }
                        ListItem(
                            headlineContent = { Text("Use custom model: $searchQuery") },
                            modifier = Modifier
                                .clip(shape)
                                .clickable {
                                    onModelSelected(searchQuery)
                                    onDismissRequest()
                                },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
                
                items(filteredModels.size) { index ->
                    val model = filteredModels[index]
                    val absoluteIndex = (if (showCustomModel) 1 else 0) + index
                    val shape = when {
                        totalItems == 1 -> RoundedCornerShape(16.dp)
                        absoluteIndex == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        absoluteIndex == totalItems - 1 -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                        else -> RectangleShape
                    }

                    ListItem(
                        modifier = Modifier
                            .clip(shape)
                            .clickable {
                                onModelSelected(model.modelId)
                                onDismissRequest()
                            },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        headlineContent = { Text(model.name ?: model.modelId) },
                        supportingContent = {
                            Column {
                                if (model.name != null) {
                                    Text(model.modelId)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (model.contextWindowTokens != null) {
                                        Text("Ctx: ${model.contextWindowTokens / 1000}k")
                                    }
                                    model.promptPrice
                                        ?.formatUsdPerMillion()
                                        ?.let { formattedPrice ->
                                            Text("In: $$formattedPrice/1M")
                                        }
                                    model.completionPrice
                                        ?.formatUsdPerMillion()
                                        ?.let { formattedPrice ->
                                            Text("Out: $$formattedPrice/1M")
                                        }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Model Selection - Filtered")
@Composable
fun PreviewModelSelectionBottomSheet() {
    val mockModels = listOf(
        DiscoveredApiModelUi(
            modelId = "openai/gpt-4o",
            name = "GPT-4o",
            contextWindowTokens = 128000,
            promptPrice = 5.0,
            completionPrice = 15.0,
            providerName = "OpenAI",
            created = 1715558400L
        ),
        DiscoveredApiModelUi(
            modelId = "anthropic/claude-3-5-sonnet",
            name = "Claude 3.5 Sonnet",
            contextWindowTokens = 200000,
            promptPrice = 3.0,
            completionPrice = 15.0,
            providerName = "Anthropic",
            created = 1718841600L
        ),
        DiscoveredApiModelUi(
            modelId = "google/gemini-pro-1.5",
            name = "Gemini 1.5 Pro",
            contextWindowTokens = 1000000,
            promptPrice = 3.5,
            completionPrice = 10.5,
            providerName = "Google",
            created = 1715644800L
        ),
        DiscoveredApiModelUi(
            modelId = "meta-llama/llama-3-70b-instruct",
            name = "Llama 3 70B Instruct",
            contextWindowTokens = 8192,
            promptPrice = 0.59,
            completionPrice = 0.79,
            providerName = "Meta",
            created = 1713484800L
        )
    )

    PocketCrewTheme {
        ModelSelectionContent(
            availableModels = mockModels,
            filteredModels = mockModels,
            searchQuery = "",
            providerFilters = emptySet(),
            sortOption = ModelSortOption.A_TO_Z,
            onUpdateSearchQuery = {},
            onToggleProviderFilter = {},
            onClearProviderFilters = {},
            onUpdateSortOption = {},
            onModelSelected = {},
            onDismissRequest = {}
        )
    }
}

private fun usdPerMillionFormatter(): DecimalFormat = DecimalFormat(
    "#,##0.####",
    DecimalFormatSymbols(Locale.US)
)

internal fun Double.formatUsdPerMillion(): String? = takeIf { isFinite() && this >= 0.0 }
    ?.let { usdPerMillionFormatter().format(it) }

@Preview(showBackground = true, name = "Model Selection - Searching")
@Composable
fun PreviewModelSelectionBottomSheetSearching() {
    val mockModels = listOf(
        DiscoveredApiModelUi(
            modelId = "openai/gpt-4o",
            name = "GPT-4o",
            contextWindowTokens = 128000,
            promptPrice = 5.0,
            completionPrice = 15.0,
            providerName = "OpenAI",
            created = 1715558400L
        )
    )

    PocketCrewTheme {
        ModelSelectionContent(
            availableModels = mockModels,
            filteredModels = emptyList(),
            searchQuery = "custom-model-id",
            providerFilters = emptySet(),
            sortOption = ModelSortOption.A_TO_Z,
            onUpdateSearchQuery = {},
            onToggleProviderFilter = {},
            onClearProviderFilters = {},
            onUpdateSortOption = {},
            onModelSelected = {},
            onDismissRequest = {}
        )
    }
}

@Preview(showBackground = true, name = "Model Selection - Filtered by Provider")
@Composable
fun PreviewModelSelectionBottomSheetFiltered() {
    val mockModels = listOf(
        DiscoveredApiModelUi(
            modelId = "openai/gpt-4o",
            name = "GPT-4o",
            contextWindowTokens = 128000,
            promptPrice = 5.0,
            completionPrice = 15.0,
            providerName = "OpenAI",
            created = 1715558400L
        ),
        DiscoveredApiModelUi(
            modelId = "openai/gpt-4-turbo",
            name = "GPT-4 Turbo",
            contextWindowTokens = 128000,
            promptPrice = 10.0,
            completionPrice = 30.0,
            providerName = "OpenAI",
            created = 1712620800L
        )
    )

    PocketCrewTheme {
        ModelSelectionContent(
            availableModels = mockModels,
            filteredModels = mockModels,
            searchQuery = "",
            providerFilters = setOf("OpenAI"),
            sortOption = ModelSortOption.PRICE_LOW_TO_HIGH,
            onUpdateSearchQuery = {},
            onToggleProviderFilter = {},
            onClearProviderFilters = {},
            onUpdateSortOption = {},
            onModelSelected = {},
            onDismissRequest = {}
        )
    }
}

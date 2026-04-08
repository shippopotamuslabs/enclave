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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.component.sheet.JumpFreeModalBottomSheet
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionBottomSheet(
    availableModels: List<DiscoveredApiModelUi>,
    filteredModels: List<DiscoveredApiModelUi>,
    searchQuery: String,
    providerFilter: String?,
    sortOption: ModelSortOption,
    onUpdateSearchQuery: (String) -> Unit,
    onUpdateProviderFilter: (String?) -> Unit,
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
            providerFilter = providerFilter,
            sortOption = sortOption,
            onUpdateSearchQuery = onUpdateSearchQuery,
            onUpdateProviderFilter = onUpdateProviderFilter,
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
    providerFilter: String?,
    sortOption: ModelSortOption,
    onUpdateSearchQuery: (String) -> Unit,
    onUpdateProviderFilter: (String?) -> Unit,
    onUpdateSortOption: (ModelSortOption) -> Unit,
    onModelSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        // Header
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onUpdateSearchQuery,
            label = { Text("Search models") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        val providers = availableModels.mapNotNull { it.providerName }.distinct().sorted()
        if (providers.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = providerFilter == null,
                        onClick = { onUpdateProviderFilter(null) },
                        label = { Text("All") }
                    )
                }
                items(providers.size) { index ->
                    val provider = providers[index]
                    FilterChip(
                        selected = providerFilter == provider,
                        onClick = { onUpdateProviderFilter(provider) },
                        label = { Text(provider) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        val hasCreated = availableModels.any { it.created != null }
        val hasPrice = availableModels.any { it.promptPrice != null || it.completionPrice != null }
        
        if (hasCreated || hasPrice) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        val showCustomModel = searchQuery.isNotBlank() && filteredModels.none { it.modelId == searchQuery }
        val totalItems = (if (showCustomModel) 1 else 0) + filteredModels.size

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
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
                                if (model.promptPrice != null) {
                                    Text("Price: $${model.promptPrice}/1M")
                                }
                            }
                        }
                    },
                )
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
            providerFilter = null,
            sortOption = ModelSortOption.A_TO_Z,
            onUpdateSearchQuery = {},
            onUpdateProviderFilter = {},
            onUpdateSortOption = {},
            onModelSelected = {},
            onDismissRequest = {}
        )
    }
}

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
            providerFilter = null,
            sortOption = ModelSortOption.A_TO_Z,
            onUpdateSearchQuery = {},
            onUpdateProviderFilter = {},
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
            providerFilter = "OpenAI",
            sortOption = ModelSortOption.PRICE_LOW_TO_HIGH,
            onUpdateSearchQuery = {},
            onUpdateProviderFilter = {},
            onUpdateSortOption = {},
            onModelSelected = {},
            onDismissRequest = {}
        )
    }
}

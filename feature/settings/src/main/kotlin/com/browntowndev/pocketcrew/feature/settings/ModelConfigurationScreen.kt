package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.pocketcrew.core.ui.component.PersistentTooltip
import com.browntowndev.pocketcrew.core.ui.component.sheet.JumpFreeModalBottomSheet
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

@Composable
fun ModelConfigurationRoute(
    modelType: ModelType,
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ModelConfigurationScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onSetDefaultModel = viewModel::onSetDefaultModel,
        onShowAssignmentDialog = viewModel::onShowAssignmentDialog
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigurationScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onSetDefaultModel: (ModelType, LocalModelConfigurationId?, ApiModelConfigurationId?) -> Unit,
    onShowAssignmentDialog: (Boolean, ModelType?) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Role Assignments", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val generalChatTypes = listOf(ModelType.FAST, ModelType.THINKING, ModelType.VISION)
            val crewModeTypes = listOf(ModelType.DRAFT_ONE, ModelType.DRAFT_TWO, ModelType.MAIN, ModelType.FINAL_SYNTHESIS)

            item {
                DefaultAssignmentsCard(
                    title = "General Chat",
                    assignments = uiState.assignments.assignments.filter { it.modelType in generalChatTypes },
                    onEditAssignment = { onShowAssignmentDialog(true, it) }
                )
            }
            item {
                DefaultAssignmentsCard(
                    title = "Crew Mode",
                    assignments = uiState.assignments.assignments.filter { it.modelType in crewModeTypes },
                    onEditAssignment = { onShowAssignmentDialog(true, it) }
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        if (uiState.assignments.isDialogOpen && uiState.assignments.editingSlot != null) {
            val assignmentSlot = uiState.assignments.editingSlot
            val slotLabel = uiState.assignments.assignments
                .find { it.modelType == assignmentSlot }
                ?.displayLabel ?: assignmentSlot.name
            
            val isVisionSlot = assignmentSlot == ModelType.VISION
            val localAssets = if (isVisionSlot) {
                uiState.localModelsSheet.models.filter { it.visionCapable }
            } else {
                uiState.localModelsSheet.models
            }
            val apiAssets = if (isVisionSlot) {
                uiState.apiProvidersSheet.assets.filter { it.isVision }
            } else {
                uiState.apiProvidersSheet.assets
            }

            AssignmentSelectionBottomSheet(
                slotLabel = slotLabel,
                localAssets = localAssets,
                apiAssets = apiAssets,
                onDismiss = { onShowAssignmentDialog(false, null) },
                onSelect = { localId, apiId -> 
                    onSetDefaultModel(assignmentSlot, localId, apiId)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultAssignmentsCard(
    title: String,
    assignments: List<DefaultModelAssignmentUi>,
    onEditAssignment: (ModelType) -> Unit
) {
    Column {
        Text(
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                assignments.forEach { assignment ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = assignment.displayLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                
                                PersistentTooltip(description = assignment.modelType.description)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = assignment.currentModelName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (assignment.providerName != null) {
                                Text(
                                    text = "Provider: ${assignment.providerName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (assignment.presetName != null) {
                                Text(
                                    text = "Preset: ${assignment.presetName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { onEditAssignment(assignment.modelType) }) {
                            Icon(
                                imageVector = Icons.Default.Edit, 
                                contentDescription = "Change default model for ${assignment.displayLabel}", 
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    if (assignment != assignments.last()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentSelectionBottomSheet(
    slotLabel: String,
    localAssets: List<LocalModelAssetUi>,
    apiAssets: List<ApiModelAssetUi>,
    onDismiss: () -> Unit,
    onSelect: (localId: LocalModelConfigurationId?, apiId: ApiModelConfigurationId?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    JumpFreeModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        AssignmentSelectionContent(
            modifier = Modifier
                .padding(bottom = 32.dp),
            slotLabel = slotLabel,
            localAssets = localAssets,
            apiAssets = apiAssets,
            onDismiss = onDismiss,
            onSelect = onSelect
        )
    }
}

private sealed interface AssignmentSelectionView {
    data object AssetList : AssignmentSelectionView
    data class LocalConfigList(val asset: LocalModelAssetUi) : AssignmentSelectionView
    data class ApiConfigList(val asset: ApiModelAssetUi) : AssignmentSelectionView
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentSelectionContent(
    modifier: Modifier = Modifier,
    slotLabel: String,
    localAssets: List<LocalModelAssetUi>,
    apiAssets: List<ApiModelAssetUi>,
    onDismiss: () -> Unit,
    onSelect: (localId: LocalModelConfigurationId?, apiId: ApiModelConfigurationId?) -> Unit
) {
    var selectedLocalId by remember { mutableStateOf<LocalModelConfigurationId?>(null) }
    var selectedApiId by remember { mutableStateOf<ApiModelConfigurationId?>(null) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Local Models", "API Models")
    var viewState by remember { mutableStateOf<AssignmentSelectionView>(AssignmentSelectionView.AssetList) }

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        when (val state = viewState) {
            is AssignmentSelectionView.AssetList -> {
                Text(
                    text = "Assign Model to $slotLabel",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            }
            is AssignmentSelectionView.LocalConfigList -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    IconButton(onClick = { viewState = AssignmentSelectionView.AssetList }, modifier = Modifier.size(40.dp).padding(end = 8.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to models")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.asset.friendlyName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Presets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is AssignmentSelectionView.ApiConfigList -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    IconButton(onClick = { viewState = AssignmentSelectionView.AssetList }, modifier = Modifier.size(40.dp).padding(end = 8.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to providers")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.asset.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Presets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        AnimatedContent(
            targetState = viewState,
            label = "AssignmentSelectionTransition",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        ) { state ->
            when (state) {
                is AssignmentSelectionView.AssetList -> {
                    Column {
                        SecondaryTabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = BottomSheetDefaults.ContainerColor,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTabIndex == index,
                                    onClick = { selectedTabIndex = index },
                                    text = {
                                        Text(
                                            text = title,
                                            color = if (selectedTabIndex == index) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                contentColorFor(BottomSheetDefaults.ContainerColor)
                                            },
                                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                )
                            }
                        }

                        AnimatedContent(
                            targetState = selectedTabIndex,
                            label = "AssignmentSelectionTabTransition",
                            modifier = Modifier.fillMaxWidth()
                        ) { tabIndex ->
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .padding(top = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (tabIndex == 0) {
                                    if (localAssets.isNotEmpty()) {
                                        items(localAssets, key = { "local_${it.metadataId.value}" }) { asset ->
                                            AssignmentAssetCard(
                                                title = asset.friendlyName,
                                                subtitle = "${asset.providerName} • ${asset.format}",
                                                presetCount = asset.configurations.size,
                                                isSelected = asset.configurations.any { it.id == selectedLocalId },
                                                onClick = { viewState = AssignmentSelectionView.LocalConfigList(asset) }
                                            )
                                        }
                                    } else {
                                        item {
                                            Text(
                                                text = "No local models available.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(vertical = 16.dp)
                                            )
                                        }
                                    }
                                } else {
                                    if (apiAssets.isNotEmpty()) {
                                        items(apiAssets, key = { "api_${it.credentialsId.value}" }) { asset ->
                                            AssignmentAssetCard(
                                                title = asset.displayName,
                                                subtitle = "${asset.provider.displayName} • ${asset.modelId}",
                                                presetCount = asset.configurations.size,
                                                isSelected = asset.configurations.any { it.id == selectedApiId },
                                                onClick = { viewState = AssignmentSelectionView.ApiConfigList(asset) }
                                            )
                                        }
                                    } else {
                                        item {
                                            Text(
                                                text = "No API providers configured.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(vertical = 16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is AssignmentSelectionView.LocalConfigList -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.asset.configurations, key = { it.id.value }) { config ->
                            ConfigSelectionCard(
                                label = config.displayName,
                                isSelected = selectedLocalId == config.id,
                                onClick = {
                                    selectedLocalId = config.id
                                    selectedApiId = null
                                }
                            )
                        }
                    }
                }
                is AssignmentSelectionView.ApiConfigList -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.asset.configurations, key = { it.id.value }) { config ->
                            ConfigSelectionCard(
                                label = config.displayName,
                                isSelected = selectedApiId == config.id,
                                onClick = {
                                    selectedApiId = config.id
                                    selectedLocalId = null
                                }
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = { onSelect(selectedLocalId, selectedApiId) },
                enabled = selectedLocalId != null || selectedApiId != null,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Confirm")
            }
        }
    }
}

@Composable
private fun AssignmentAssetCard(
    title: String,
    subtitle: String,
    presetCount: Int,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { m ->
                if (isSelected) {
                    m.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    )
                } else {
                    m
                }
            }
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = "$presetCount Preset(s) available",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "View presets",
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun ModelCard(
    title: String,
    subtitle: String? = null,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .let { m ->
                if (isSelected) {
                    m.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    m
                }
            }
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
    )
}

@Composable
private fun ConfigSelectionCard(
    label: String,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { m ->
                if (isSelected) {
                    m.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(16.dp)
                    )
                } else {
                    m
                }
            }
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ==================== PREVIEWS ====================

@Preview(showBackground = true, name = "Model Role Assignments Screen")
@Composable
fun PreviewModelConfigurationScreen() {
    PocketCrewTheme {
        ModelConfigurationScreen(
            uiState = MockSettingsData.baseUiState,
            onNavigateBack = {},
            onSetDefaultModel = { _, _, _ -> },
            onShowAssignmentDialog = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, name = "Default Assignments Card")
@Composable
fun PreviewDefaultAssignmentsCard() {
    PocketCrewTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            DefaultAssignmentsCard(
                title = "General Chat",
                assignments = MockSettingsData.baseUiState.assignments.assignments.take(3),
                onEditAssignment = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Assignment Selection Content")
@Composable
fun PreviewAssignmentSelectionContent() {
    PocketCrewTheme {
        AssignmentSelectionContent(
            modifier = Modifier.padding(bottom = 32.dp),
            slotLabel = "Main",
            localAssets = MockSettingsData.localModels,
            apiAssets = MockSettingsData.apiModels,
            onDismiss = {},
            onSelect = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, name = "Assignment Asset Card")
@Composable
fun PreviewAssignmentAssetCard() {
    PocketCrewTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AssignmentAssetCard(
                title = "Meta Llama 3 8B",
                subtitle = "meta-llama • GGUF",
                presetCount = 3,
                isSelected = false,
                onClick = {}
            )
            AssignmentAssetCard(
                title = "Meta Llama 3 8B",
                subtitle = "meta-llama • GGUF",
                presetCount = 3,
                isSelected = true,
                onClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Config Selection Card")
@Composable
fun PreviewConfigSelectionCard() {
    PocketCrewTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConfigSelectionCard(
                label = "Default",
                isSelected = false,
                onClick = {}
            )
            ConfigSelectionCard(
                label = "Creative",
                isSelected = true,
                onClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Model Card")
@Composable
fun PreviewModelCard() {
    PocketCrewTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ModelCard(
                title = "Llama 3 8B",
                subtitle = "Local • GGUF",
                isSelected = false,
                onClick = {}
            )
            ModelCard(
                title = "GPT-4o",
                subtitle = "OpenAI • Cloud",
                isSelected = true,
                onClick = {}
            )
        }
    }
}

package com.browntowndev.enclave.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.enclave.core.ui.component.neo.NeoBackScreenHeader
import com.browntowndev.enclave.core.ui.component.neo.NeoChevronCard
import com.browntowndev.enclave.core.ui.component.neo.NeoModalBottomSheet
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSecondaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSectionHeader
import com.browntowndev.enclave.core.ui.component.neo.NeoSelectionCard
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetHeader
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetTabRow
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType

@Composable
internal fun ModelConfigurationRoute(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ModelConfigurationScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onSetDefaultModel = viewModel::onSetDefaultModel,
        onShowAssignmentDialog = viewModel::onShowAssignmentDialog,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigurationScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onSetDefaultModel: (ModelType, LocalModelConfigurationId?, ApiModelConfigurationId?, TtsProviderId?, MediaProviderId?) -> Unit,
    onShowAssignmentDialog: (Boolean, ModelType?) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            NeoBackScreenHeader(
                title = "Model Role Assignments",
                onBackClick = onNavigateBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = EnclaveNeoTokens.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.sectionSpacing),
        ) {
            val generalChatTypes = listOf(ModelType.FAST, ModelType.THINKING)
            val crewModeTypes = listOf(ModelType.DRAFT_ONE, ModelType.DRAFT_TWO, ModelType.MAIN, ModelType.FINAL_SYNTHESIS)
            val audioTypes = listOf(ModelType.TTS)
            val studioMediaTypes = listOf(ModelType.IMAGE_GENERATION, ModelType.VIDEO_GENERATION)

            item {
                DefaultAssignmentsCard(
                    title = "General Chat",
                    assignments = uiState.assignments.assignments.filter { it.modelType in generalChatTypes },
                    onEditAssignment = { onShowAssignmentDialog(true, it) },
                )
            }
            item {
                DefaultAssignmentsCard(
                    title = "Crew Mode",
                    assignments = uiState.assignments.assignments.filter { it.modelType in crewModeTypes },
                    onEditAssignment = { onShowAssignmentDialog(true, it) },
                )
            }
            item {
                DefaultAssignmentsCard(
                    title = "Voice & Audio",
                    assignments = uiState.assignments.assignments.filter { it.modelType in audioTypes },
                    onEditAssignment = { onShowAssignmentDialog(true, it) },
                )
            }
            item {
                DefaultAssignmentsCard(
                    title = "Studio & Media",
                    assignments = uiState.assignments.assignments.filter { it.modelType in studioMediaTypes },
                    onEditAssignment = { onShowAssignmentDialog(true, it) },
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        if (uiState.assignments.isDialogOpen && uiState.assignments.editingSlot != null) {
            val assignmentSlot = uiState.assignments.editingSlot
            val slotLabel =
                uiState.assignments.assignments
                    .find { it.modelType == assignmentSlot }
                    ?.displayLabel ?: assignmentSlot.name

            val isVisionSlot = assignmentSlot == ModelType.VISION
            val isTtsSlot = assignmentSlot == ModelType.TTS
            val isMediaSlot =
                assignmentSlot in
                    listOf(
                        ModelType.IMAGE_GENERATION,
                        ModelType.VIDEO_GENERATION,
                        ModelType.MUSIC_GENERATION,
                    )
            val localAssets = if (isVisionSlot || isTtsSlot || isMediaSlot) emptyList() else uiState.localModelsSheet.models
            val apiAssets =
                when {
                    isVisionSlot -> uiState.apiProvidersSheet.assets.filter { asset -> asset.configurations.any { it.isMultimodal } }
                    isTtsSlot || isMediaSlot -> emptyList()
                    else -> uiState.apiProvidersSheet.assets
                }
            val ttsAssets = if (isTtsSlot) uiState.ttsProvidersSheet.assets else emptyList()
            val mediaAssets =
                if (isMediaSlot) {
                    val capability =
                        when (assignmentSlot) {
                            ModelType.IMAGE_GENERATION -> MediaCapability.IMAGE
                            ModelType.VIDEO_GENERATION -> MediaCapability.VIDEO
                            ModelType.MUSIC_GENERATION -> MediaCapability.MUSIC
                            else -> MediaCapability.IMAGE // Should not happen given studioMediaTypes check
                        }
                    uiState.mediaProvidersSheet.assets.filter { it.capability == capability }
                } else {
                    emptyList()
                }

            AssignmentSelectionBottomSheet(
                slotLabel = slotLabel,
                localAssets = localAssets,
                apiAssets = apiAssets,
                ttsAssets = ttsAssets,
                mediaAssets = mediaAssets,
                onDismiss = { onShowAssignmentDialog(false, null) },
                onSelect = { localId, apiId, ttsId, mediaId ->
                    onSetDefaultModel(assignmentSlot, localId, apiId, ttsId, mediaId)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultAssignmentsCard(
    title: String,
    assignments: List<DefaultModelAssignmentUi>,
    onEditAssignment: (ModelType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.rowSpacing)) {
        NeoSectionHeader(text = title)
        assignments.forEach { assignment ->
            val rowDisplay = assignment.assignmentRowDisplay
            SettingsAssignmentRow(
                displayLabel = assignment.displayLabel,
                presetName = rowDisplay.presetName,
                providerName = rowDisplay.providerName,
                modelName = rowDisplay.modelName,
                isMultimodal = assignment.isMultimodal,
                modelTypeDescription = assignment.modelType.description,
                onEditClick = { onEditAssignment(assignment.modelType) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentSelectionBottomSheet(
    slotLabel: String,
    localAssets: List<LocalModelAssetUi>,
    apiAssets: List<ApiModelAssetUi>,
    ttsAssets: List<TtsProviderAssetUi>,
    mediaAssets: List<MediaProviderAssetUi>,
    onDismiss: () -> Unit,
    onSelect: (
        localId: LocalModelConfigurationId?,
        apiId: ApiModelConfigurationId?,
        ttsId: TtsProviderId?,
        mediaId: MediaProviderId?,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    NeoModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        AssignmentSelectionContent(
            modifier = Modifier.fillMaxWidth(),
            slotLabel = slotLabel,
            localAssets = localAssets,
            apiAssets = apiAssets,
            ttsAssets = ttsAssets,
            mediaAssets = mediaAssets,
            onDismiss = onDismiss,
            onSelect = onSelect,
        )
    }
}

private sealed interface AssignmentSelectionView {
    data object AssetList : AssignmentSelectionView

    data class LocalConfigList(
        val asset: LocalModelAssetUi,
    ) : AssignmentSelectionView

    data class ApiConfigList(
        val asset: ApiModelAssetUi,
    ) : AssignmentSelectionView
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod")
@Composable
fun AssignmentSelectionContent(
    modifier: Modifier = Modifier,
    slotLabel: String,
    localAssets: List<LocalModelAssetUi>,
    apiAssets: List<ApiModelAssetUi>,
    ttsAssets: List<TtsProviderAssetUi>,
    mediaAssets: List<MediaProviderAssetUi>,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    onSelect: (
        localId: LocalModelConfigurationId?,
        apiId: ApiModelConfigurationId?,
        ttsId: TtsProviderId?,
        mediaId: MediaProviderId?,
    ) -> Unit,
) {
    var selectedLocalId by remember { mutableStateOf<LocalModelConfigurationId?>(null) }
    var selectedApiId by remember { mutableStateOf<ApiModelConfigurationId?>(null) }
    var selectedTtsId by remember { mutableStateOf<TtsProviderId?>(null) }
    var selectedMediaId by remember { mutableStateOf<MediaProviderId?>(null) }
    val hasLocalTab = localAssets.isNotEmpty()
    val hasApiTab = apiAssets.isNotEmpty()
    val hasTtsTab = ttsAssets.isNotEmpty()
    val hasMediaTab = mediaAssets.isNotEmpty()
    var selectedTabIndex by remember(hasLocalTab, hasApiTab, hasTtsTab, hasMediaTab) { mutableIntStateOf(0) }
    val tabs =
        buildList {
            if (hasLocalTab) add("Local Models")
            if (hasApiTab) add("API Models")
            if (hasTtsTab) add("TTS Providers")
            if (hasMediaTab) add("Media Providers")
        }
    var viewState by remember { mutableStateOf<AssignmentSelectionView>(AssignmentSelectionView.AssetList) }

    Column(
        modifier =
            modifier
                .fillMaxWidth(),
    ) {
        when (val state = viewState) {
            is AssignmentSelectionView.AssetList -> {
                NeoSheetHeader(
                    title = "Assign Model to $slotLabel",
                    onDismiss = onDismiss,
                    onBackClick = onBack,
                )
            }
            is AssignmentSelectionView.LocalConfigList -> {
                NeoSheetHeader(
                    title = state.asset.friendlyName,
                    subtitle = "Presets",
                    onDismiss = onDismiss,
                    onBackClick = { viewState = AssignmentSelectionView.AssetList },
                )
            }
            is AssignmentSelectionView.ApiConfigList -> {
                NeoSheetHeader(
                    title = state.asset.displayName,
                    subtitle = "Presets",
                    onDismiss = onDismiss,
                    onBackClick = { viewState = AssignmentSelectionView.AssetList },
                )
            }
        }

        AnimatedContent(
            targetState = viewState,
            label = "AssignmentSelectionTransition",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
        ) { state ->
            when (state) {
                is AssignmentSelectionView.AssetList -> {
                    Column {
                        if (tabs.size > 1) {
                            NeoSheetTabRow(
                                selectedTabIndex = selectedTabIndex,
                                tabs = tabs,
                                onTabSelected = { selectedTabIndex = it },
                            )
                        }

                        AnimatedContent(
                            targetState =
                                when {
                                    hasLocalTab && (hasApiTab || hasTtsTab || hasMediaTab) -> selectedTabIndex
                                    hasLocalTab -> 0
                                    hasApiTab || hasTtsTab || hasMediaTab -> selectedTabIndex
                                    else -> 0
                                },
                            label = "AssignmentSelectionTabTransition",
                            modifier = Modifier.fillMaxWidth(),
                        ) { tabIndex ->
                            val currentTabTitle = tabs.getOrNull(tabIndex)
                            LazyColumn(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .padding(top = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                when (currentTabTitle) {
                                    "Local Models" -> {
                                        items(localAssets, key = { "local_${it.metadataId.value}" }) { asset ->
                                            NeoChevronCard(
                                                title = asset.friendlyName,
                                                subtitle = "${asset.providerName} • ${asset.format}",
                                                footer = "${asset.configurations.size} Preset(s) available",
                                                isMultimodal = asset.isMultimodal,
                                                isSelected = asset.configurations.any { it.id == selectedLocalId },
                                                onClick = { viewState = AssignmentSelectionView.LocalConfigList(asset) },
                                            )
                                        }
                                    }
                                    "API Models" -> {
                                        items(apiAssets, key = { "api_${it.credentialsId.value}" }) { asset ->
                                            NeoChevronCard(
                                                title = asset.displayName,
                                                subtitle = asset.provider.displayName,
                                                footer = "${asset.configurations.size} Preset(s) available",
                                                isMultimodal = asset.configurations.any { it.isMultimodal },
                                                isSelected = asset.configurations.any { it.id == selectedApiId },
                                                onClick = { viewState = AssignmentSelectionView.ApiConfigList(asset) },
                                            )
                                        }
                                    }
                                    "TTS Providers" -> {
                                        items(ttsAssets, key = { "tts_${it.id.value}" }) { asset ->
                                            NeoChevronCard(
                                                title = asset.displayName,
                                                subtitle = "${asset.provider.displayName} • ${asset.voiceName}",
                                                footer = "1 Preset(s) available",
                                                isSelected = selectedTtsId == asset.id,
                                                onClick = {
                                                    selectedTtsId = asset.id
                                                    selectedLocalId = null
                                                    selectedApiId = null
                                                    selectedMediaId = null
                                                },
                                            )
                                        }
                                    }
                                    "Media Providers" -> {
                                        items(mediaAssets, key = { "media_${it.id.value}" }) { asset ->
                                            NeoChevronCard(
                                                title = asset.displayName,
                                                subtitle = "${asset.provider.displayName} • ${asset.modelName}",
                                                footer = "1 Preset(s) available",
                                                isSelected = selectedMediaId == asset.id,
                                                onClick = {
                                                    selectedMediaId = asset.id
                                                    selectedLocalId = null
                                                    selectedApiId = null
                                                    selectedTtsId = null
                                                },
                                            )
                                        }
                                    }
                                    else -> {
                                        item {
                                            Text(
                                                text = "No options available.",
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

                is AssignmentSelectionView.LocalConfigList -> {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.asset.configurations, key = { it.id.value }) { config ->
                            ConfigSelectionCard(
                                label = config.displayName,
                                isSelected = selectedLocalId == config.id,
                                onClick = {
                                    selectedLocalId = config.id
                                    selectedApiId = null
                                },
                            )
                        }
                    }
                }

                is AssignmentSelectionView.ApiConfigList -> {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.asset.configurations, key = { it.id.value }) { config ->
                            ConfigSelectionCard(
                                label = config.displayName,
                                subtitle = config.modelId.takeIf { it.isNotBlank() },
                                isSelected = selectedApiId == config.id,
                                onClick = {
                                    selectedApiId = config.id
                                    selectedLocalId = null
                                },
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NeoSecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            NeoPrimaryButton(
                text = "Confirm",
                onClick = { onSelect(selectedLocalId, selectedApiId, selectedTtsId, selectedMediaId) },
                enabled = selectedLocalId != null || selectedApiId != null || selectedTtsId != null || selectedMediaId != null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AssignmentAssetCard(
    title: String,
    subtitle: String,
    presetCount: Int,
    isMultimodal: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { m ->
                    if (isSelected) {
                        m.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(16.dp),
                        )
                    } else {
                        m
                    }
                }.clickable(onClick = onClick)
                .defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isMultimodal) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Vision capable",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = "$presetCount Preset(s) available",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "View presets",
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun ModelCard(
    title: String,
    subtitle: String? = null,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .let { m ->
                    if (isSelected) {
                        m.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp),
                        )
                    } else {
                        m
                    }
                }.clickable(onClick = onClick)
                .defaultMinSize(minHeight = 48.dp),
    )
}

@Composable
private fun ConfigSelectionCard(
    label: String,
    subtitle: String? = null,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    NeoSelectionCard(
        title = label,
        subtitle = subtitle,
        isSelected = isSelected,
        onClick = onClick,
    )
}

// ==================== PREVIEWS ====================

@Preview(showBackground = true, name = "Model Role Assignments Screen")
@Composable
fun PreviewModelConfigurationScreen() {
    EnclaveTheme {
        ModelConfigurationScreen(
            uiState = MockSettingsData.baseUiState,
            onNavigateBack = {},
            onSetDefaultModel = { _, _, _, _, _ -> },
            onShowAssignmentDialog = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, name = "Default Assignments Card")
@Composable
fun PreviewDefaultAssignmentsCard() {
    EnclaveTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            DefaultAssignmentsCard(
                title = "General Chat",
                assignments =
                    MockSettingsData.baseUiState.assignments.assignments
                        .take(3),
                onEditAssignment = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Assignment Selection Content")
@Composable
fun PreviewAssignmentSelectionContent() {
    EnclaveTheme {
        AssignmentSelectionContent(
            modifier = Modifier.padding(bottom = 32.dp),
            slotLabel = "Main",
            localAssets = MockSettingsData.localModels,
            apiAssets = MockSettingsData.apiModels,
            ttsAssets = emptyList(),
            mediaAssets = emptyList(),
            onDismiss = {},
            onSelect = { _, _, _, _ -> },
        )
    }
}

@Preview(showBackground = true, name = "Assignment Asset Card")
@Composable
fun PreviewAssignmentAssetCard() {
    EnclaveTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AssignmentAssetCard(
                title = "Meta Llama 3 8B (Vision)",
                subtitle = "meta-llama • GGUF",
                presetCount = 3,
                isMultimodal = true,
                isSelected = false,
                onClick = {},
            )
            AssignmentAssetCard(
                title = "Meta Llama 3 8B",
                subtitle = "meta-llama • GGUF",
                presetCount = 3,
                isSelected = true,
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Config Selection Card")
@Composable
fun PreviewConfigSelectionCard() {
    EnclaveTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConfigSelectionCard(
                label = "Default",
                isSelected = false,
                onClick = {},
            )
            ConfigSelectionCard(
                label = "Creative",
                isSelected = true,
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Model Card")
@Composable
fun PreviewModelCard() {
    EnclaveTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ModelCard(
                title = "Llama 3 8B",
                subtitle = "Local • GGUF",
                isSelected = false,
                onClick = {},
            )
            ModelCard(
                title = "GPT-4o",
                subtitle = "OpenAI • Cloud",
                isSelected = true,
                onClick = {},
            )
        }
    }
}

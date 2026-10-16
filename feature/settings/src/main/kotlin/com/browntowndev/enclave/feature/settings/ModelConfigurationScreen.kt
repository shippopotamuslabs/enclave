package com.browntowndev.enclave.feature.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.enclave.core.ui.component.assignment.AssignmentSelectionBottomSheet
import com.browntowndev.enclave.core.ui.component.assignment.AssignmentSelectionContent
import com.browntowndev.enclave.core.ui.component.assignment.ApiModelAssetUi
import com.browntowndev.enclave.core.ui.component.assignment.LocalModelAssetUi
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveBackScreenHeader
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSectionHeader
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.config.toMediaTaskOrNull
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
    onSetDefaultModel: (
        ModelType,
        LocalModelConfigurationId?,
        ApiModelConfigurationId?,
        TtsProviderId?,
        MediaProviderId?,
        LiveVoiceProviderId?,
    ) -> Unit,
    onShowAssignmentDialog: (Boolean, ModelType?) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            EnclaveBackScreenHeader(
                title = "Model Role Assignments",
                onBackClick = onNavigateBack,
            )
        },
    ) { padding ->
        val colorScheme = MaterialTheme.colorScheme
        val shimmerBaseColor = colorScheme.onSurface.copy(alpha = 0.05f)
        val shimmerHighlightColor = colorScheme.onSurface.copy(alpha = 0.15f)

        val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
        val shimmerProgress =
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "shimmerProgress",
            )
        val isLoading = uiState.assignments.assignments.isEmpty()

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
            val audioTypes = listOf(ModelType.TTS, ModelType.LIVE_VOICE)
            val studioMediaTypes =
                listOf(
                    ModelType.IMAGE_GENERATION,
                    ModelType.IMAGE_EDIT,
                    ModelType.VIDEO_GENERATION,
                    ModelType.IMAGE_TO_VIDEO,
                )

            item {
                DefaultAssignmentsCard(
                    title = "General Chat",
                    assignments = uiState.assignments.assignments.filter { it.modelType in generalChatTypes },
                    onEditAssignment = { onShowAssignmentDialog(true, it) },
                    isLoading = isLoading,
                    shimmerProgress = shimmerProgress,
                    shimmerBaseColor = shimmerBaseColor,
                    shimmerHighlightColor = shimmerHighlightColor,
                    skeletonCount = 2,
                )
            }
            item {
                DefaultAssignmentsCard(
                    title = "Crew Mode",
                    assignments = uiState.assignments.assignments.filter { it.modelType in crewModeTypes },
                    onEditAssignment = { onShowAssignmentDialog(true, it) },
                    isLoading = isLoading,
                    shimmerProgress = shimmerProgress,
                    shimmerBaseColor = shimmerBaseColor,
                    shimmerHighlightColor = shimmerHighlightColor,
                    skeletonCount = 4,
                )
            }
            item {
                DefaultAssignmentsCard(
                    title = "Voice & Audio",
                    assignments = uiState.assignments.assignments.filter { it.modelType in audioTypes },
                    onEditAssignment = { onShowAssignmentDialog(true, it) },
                    isLoading = isLoading,
                    shimmerProgress = shimmerProgress,
                    shimmerBaseColor = shimmerBaseColor,
                    shimmerHighlightColor = shimmerHighlightColor,
                    skeletonCount = 2,
                )
            }
            item {
                DefaultAssignmentsCard(
                    title = "Studio & Media",
                    assignments = uiState.assignments.assignments.filter { it.modelType in studioMediaTypes },
                    onEditAssignment = { onShowAssignmentDialog(true, it) },
                    isLoading = isLoading,
                    shimmerProgress = shimmerProgress,
                    shimmerBaseColor = shimmerBaseColor,
                    shimmerHighlightColor = shimmerHighlightColor,
                    skeletonCount = 4,
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
            val isLiveVoiceSlot = assignmentSlot == ModelType.LIVE_VOICE
            val isMediaSlot =
                assignmentSlot in
                    listOf(
                        ModelType.IMAGE_GENERATION,
                        ModelType.IMAGE_EDIT,
                        ModelType.VIDEO_GENERATION,
                        ModelType.IMAGE_TO_VIDEO,
                        ModelType.MUSIC_GENERATION,
                    )
            val localAssets =
                selectableLocalAssetsForSlot(
                    modelType = assignmentSlot,
                    assets = uiState.localModelsSheet.models,
                    modelPacks = uiState.localModelsSheet.modelPacks,
                )
            val apiAssets =
                when {
                    isVisionSlot -> uiState.apiProvidersSheet.assets.filter { asset -> asset.configurations.any { it.isMultimodal } }
                    isTtsSlot || isLiveVoiceSlot || isMediaSlot -> emptyList()
                    else -> uiState.apiProvidersSheet.assets
                }
            val ttsAssets = if (isTtsSlot) uiState.ttsProvidersSheet.assets else emptyList()
            val liveVoiceAssets =
                if (isLiveVoiceSlot) {
                    uiState.liveVoiceProvidersSheet.assets
                } else {
                    emptyList()
                }
            val mediaAssets =
                if (isMediaSlot) {
                    val slotTask = assignmentSlot.toMediaTaskOrNull()
                    if (assignmentSlot == ModelType.MUSIC_GENERATION) {
                        uiState.mediaProvidersSheet.assets.filter { it.capability == MediaCapability.MUSIC }
                    } else {
                        uiState.mediaProvidersSheet.assets.filter { slotTask != null && slotTask in it.tasks }
                    }
                } else {
                    emptyList()
                }

            AssignmentSelectionBottomSheet(
                slotLabel = slotLabel,
                localAssets = localAssets,
                apiAssets = apiAssets,
                ttsAssets = ttsAssets,
                liveVoiceAssets = liveVoiceAssets,
                mediaAssets = mediaAssets,
                onDismiss = { onShowAssignmentDialog(false, null) },
                onSelect = { localId, apiId, ttsId, mediaId, liveVoiceId ->
                    onSetDefaultModel(assignmentSlot, localId, apiId, ttsId, mediaId, liveVoiceId)
                },
            )
        }
    }
}

internal fun selectableLocalAssetsForSlot(
    modelType: ModelType,
    assets: List<LocalModelAssetUi>,
    modelPacks: List<ModelPackUi> = emptyList(),
): List<LocalModelAssetUi> =
    when {
        modelType == ModelType.IMAGE_GENERATION || modelType == ModelType.IMAGE_EDIT -> {
            val eligibleAssets = applyLocalImageAssignmentEligibility(assets, modelPacks)
            eligibleAssets.filter { asset ->
                asset.isImageGenerationCapable &&
                    asset.configurations.any { config ->
                        modelType in config.defaultAssignments
                    }
            }
        }
        modelType in LOCAL_MODEL_UNSUPPORTED_ASSIGNMENT_TYPES -> emptyList()
        modelType.isTextModel -> assets.filter { !it.isImageGenerationCapable }
        else -> assets
    }

/**
 * Merges pack eligibility and completeness into local image assignment options.
 * Red-phase stub: throws until settings maps domain policy outputs onto assignable models.
 */
internal fun applyLocalImageAssignmentEligibility(
    assets: List<LocalModelAssetUi>,
    modelPacks: List<ModelPackUi>,
): List<LocalModelAssetUi> {
    if (modelPacks.isEmpty()) {
        return assets
    }
    val blockedPackIds = modelPacks.filter { !it.isEligible }.map { it.packId }.toSet()
    if (blockedPackIds.isEmpty()) {
        return assets
    }
    return assets.filter { asset ->
        when {
            !asset.isImageGenerationCapable -> true
            asset.imageGenerationPackId() in blockedPackIds -> false
            else -> true
        }
    }
}

private const val SD15_LOCAL_CONFIG_ID = "sd15-local-config"
private const val SDXL_LOCAL_CONFIG_ID = "sdxl-local-config"
private const val JUGGERNAUT_LOCAL_CONFIG_ID = "juggernaut-local-config"

private fun LocalModelAssetUi.imageGenerationPackId(): String? =
    configurations.firstNotNullOfOrNull { config ->
        when (config.id.value) {
            SD15_LOCAL_CONFIG_ID -> "sd15-t2i"
            SDXL_LOCAL_CONFIG_ID -> "sdxl-t2i"
            JUGGERNAUT_LOCAL_CONFIG_ID -> "juggernaut-xl-t2i"
            else -> null
        }
    }

private val LOCAL_MODEL_UNSUPPORTED_ASSIGNMENT_TYPES =
    setOf(
        ModelType.VISION,
        ModelType.TTS,
        ModelType.LIVE_VOICE,
        ModelType.VIDEO_GENERATION,
        ModelType.IMAGE_TO_VIDEO,
        ModelType.MUSIC_GENERATION,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultAssignmentsCard(
    title: String,
    assignments: List<DefaultModelAssignmentUi>,
    onEditAssignment: (ModelType) -> Unit,
    isLoading: Boolean,
    shimmerProgress: State<Float>,
    shimmerBaseColor: Color,
    shimmerHighlightColor: Color,
    skeletonCount: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.rowSpacing)) {
        EnclaveSectionHeader(text = title)
        if (isLoading) {
            repeat(skeletonCount) {
                SettingsAssignmentRowSkeleton(
                    progressState = shimmerProgress,
                    baseColor = shimmerBaseColor,
                    highlightColor = shimmerHighlightColor,
                )
            }
        } else {
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

// ==================== PREVIEWS ====================

@Preview(showBackground = true, name = "Model Role Assignments Screen")
@Composable
fun PreviewModelConfigurationScreen() {
    EnclaveTheme {
        ModelConfigurationScreen(
            uiState = MockSettingsData.baseUiState,
            onNavigateBack = {},
            onSetDefaultModel = { _, _, _, _, _, _ -> },
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
                isLoading = false,
                shimmerProgress = remember { mutableStateOf(0f) },
                shimmerBaseColor = Color.LightGray,
                shimmerHighlightColor = Color.Gray,
                skeletonCount = 2,
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
            liveVoiceAssets = emptyList(),
            mediaAssets = emptyList(),
            onDismiss = {},
            onSelect = { _, _, _, _, _ -> },
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

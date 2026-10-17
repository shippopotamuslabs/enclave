package com.browntowndev.enclave.feature.studio.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.browntowndev.enclave.core.ui.component.PersistentTooltip
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveModalBottomSheet
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveNavRow
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSectionHeader
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSegmentedControl
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSheetHeader
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveStepper
import com.browntowndev.enclave.core.ui.component.enclave.enclaveBorderColor
import com.browntowndev.enclave.core.ui.component.enclave.enclaveSurface
import com.browntowndev.enclave.core.ui.component.shimmerEffect
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.feature.studio.ModelPickerPane
import com.browntowndev.enclave.feature.studio.R
import com.browntowndev.enclave.feature.studio.StudioAspectRatioUi
import com.browntowndev.enclave.feature.studio.StudioCapabilitiesUi
import com.browntowndev.enclave.feature.studio.StudioCapablePresetsUi
import com.browntowndev.enclave.feature.studio.StudioGenerationSettingsUi
import com.browntowndev.enclave.feature.studio.StudioImageSettingsUi
import com.browntowndev.enclave.feature.studio.StudioMediaTypeUi
import com.browntowndev.enclave.feature.studio.StudioMusicSettingsUi
import com.browntowndev.enclave.feature.studio.StudioQualityUi
import com.browntowndev.enclave.feature.studio.StudioTemplateUi
import com.browntowndev.enclave.feature.studio.StudioVideoSettingsUi
import com.browntowndev.enclave.feature.studio.StudioModelOptionUi
import com.browntowndev.enclave.feature.studio.StudioOverrideKind
import com.browntowndev.enclave.feature.studio.StudioSheetView
import com.browntowndev.enclave.feature.studio.StudioUiState
import kotlin.math.roundToInt

@Composable
fun StudioTemplateRow(
    templates: List<StudioTemplateUi>,
    selectedTemplateId: String?,
    onTemplateSelected: (StudioTemplateUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(templates) { template ->
            TemplateChip(
                template = template,
                isSelected = template.id == selectedTemplateId,
                onClick = { onTemplateSelected(template) },
            )
        }
    }
}

@Composable
fun TemplateChip(
    template: StudioTemplateUi,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageModel =
        remember(template.exampleUri) {
            when (template.exampleUri) {
                "cyberpunk" -> R.drawable.cyberpunk
                "oil_painting" -> R.drawable.oil_painting
                "anime" -> R.drawable.anime
                "cinematic" -> R.drawable.cinematic
                else -> template.exampleUri.takeIf { it.isNotEmpty() }
            }
        }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .width(80.dp)
                .clickable(onClick = onClick),
    ) {
        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(EnclaveNeoTokens.shape)
                    .border(
                        width = EnclaveNeoTokens.borderWidth,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else enclaveBorderColor(),
                        shape = EnclaveNeoTokens.shape,
                    ),
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = template.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (isSelected) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = template.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
fun SettingRow(
    label: String,
    subLabel: String = "",
    stacked: Boolean = false,
    controlFullWidth: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val useStacked = stacked || controlFullWidth
    val layoutModifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)

    if (useStacked) {
        Column(
            modifier = layoutModifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                if (subLabel.isNotEmpty()) {
                    Text(
                        text = subLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    } else {
        Row(
            modifier = layoutModifier,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                if (subLabel.isNotEmpty()) {
                    Text(
                        text = subLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Box(contentAlignment = Alignment.CenterEnd) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioOptionsBottomSheet(
    state: StudioUiState,
    onDismiss: () -> Unit,
    onUpdateSettings: (StudioGenerationSettingsUi) -> Unit,
    onContinualModeChange: (Boolean) -> Unit,
    onMediaTypeChange: (StudioMediaTypeUi) -> Unit,
    onTemplateSelected: (StudioTemplateUi) -> Unit,
    onOpenModelPicker: () -> Unit = {},
    onStageModelSelection: (StudioModelOptionUi) -> Unit = {},
    onConfirmModelSelection: () -> Unit = {},
    onCancelModelPicker: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    EnclaveModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        StudioOptionsSheetContent(
            state = state,
            onDismiss = onDismiss,
            onUpdateSettings = onUpdateSettings,
            onContinualModeChange = onContinualModeChange,
            onMediaTypeChange = onMediaTypeChange,
            onTemplateSelected = onTemplateSelected,
            onOpenModelPicker = onOpenModelPicker,
            onStageModelSelection = onStageModelSelection,
            onConfirmModelSelection = onConfirmModelSelection,
            onCancelModelPicker = onCancelModelPicker,
        )
    }
}

@Composable
fun PillSelector(
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    EnclaveSegmentedControl(
        options = options,
        selectedIndex = selectedIndex,
        onOptionSelected = { index -> onSelected(options[index]) },
        modifier = modifier,
    )
}

private const val MAX_DURATION_PILL_OPTIONS = 6

@Composable
private fun StudioShimmerColors(): Pair<Color, Color> {
    val colorScheme = MaterialTheme.colorScheme
    return colorScheme.onSurface.copy(alpha = 0.05f) to colorScheme.onSurface.copy(alpha = 0.15f)
}

@Composable
private fun StudioSettingsShimmerBar(
    progressState: State<Float>,
    baseColor: Color,
    highlightColor: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 40.dp,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .shimmerEffect(
                    progressState = progressState,
                    baseColor = baseColor,
                    highlightColor = highlightColor,
                ),
    )
}

@Composable
private fun StudioSettingsShimmerBlock(
    rowCount: Int,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "studioSettingsShimmer")
    val progressState =
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "studioSettingsShimmerProgress",
        )
    val (baseColor, highlightColor) = StudioShimmerColors()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        repeat(rowCount) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioSettingsShimmerBar(
                    progressState = progressState,
                    baseColor = baseColor,
                    highlightColor = highlightColor,
                    height = 16.dp,
                    modifier = Modifier.fillMaxWidth(0.35f),
                )
                StudioSettingsShimmerBar(
                    progressState = progressState,
                    baseColor = baseColor,
                    highlightColor = highlightColor,
                )
            }
        }
    }
}

@Composable
private fun StudioAspectRatioShimmer(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "studioAspectShimmer")
    val progressState =
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "studioAspectShimmerProgress",
        )
    val (baseColor, highlightColor) = StudioShimmerColors()
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(5) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(width = 48.dp, height = 64.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect(
                                progressState = progressState,
                                baseColor = baseColor,
                                highlightColor = highlightColor,
                            ),
                )
                Box(
                    modifier =
                        Modifier
                            .width(32.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect(
                                progressState = progressState,
                                baseColor = baseColor,
                                highlightColor = highlightColor,
                            ),
                )
            }
        }
    }
}

@Composable
private fun VideoDurationControl(
    settings: StudioVideoSettingsUi,
    durationOptions: List<String>,
    onUpdateSettings: (StudioVideoSettingsUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (durationOptions.size > MAX_DURATION_PILL_OPTIONS) {
        val selectedLabel =
            settings.apiDuration
                ?: durationOptions.firstOrNull { token ->
                    token.removeSuffix("s").toIntOrNull() == settings.videoDuration
                }
                ?: "${settings.videoDuration}s"
        val selectedIndex = durationOptions.indexOf(selectedLabel).coerceAtLeast(0)
        val sliderSteps = (durationOptions.size - 2).coerceAtLeast(0)
        Column(modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${selectedIndex + 1} / ${durationOptions.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = selectedIndex.toFloat(),
                onValueChange = { raw ->
                    val index = raw.roundToInt().coerceIn(0, durationOptions.lastIndex)
                    val durationStr = durationOptions[index]
                    val seconds = durationStr.removeSuffix("s").toIntOrNull() ?: settings.videoDuration
                    onUpdateSettings(
                        settings.copy(
                            videoDuration = seconds,
                            apiDuration = durationStr,
                        ),
                    )
                },
                valueRange = 0f..durationOptions.lastIndex.toFloat(),
                steps = sliderSteps,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        PillSelector(
            selected =
                settings.apiDuration
                    ?: durationOptions.firstOrNull { token ->
                        token.removeSuffix("s").toIntOrNull() == settings.videoDuration
                    }
                    ?: "${settings.videoDuration}s",
            options = durationOptions,
            onSelected = { durationStr ->
                val seconds = durationStr.removeSuffix("s").toIntOrNull() ?: settings.videoDuration
                onUpdateSettings(
                    settings.copy(
                        videoDuration = seconds,
                        apiDuration = durationStr,
                    ),
                )
            },
            modifier = modifier,
        )
    }
}

@Composable
fun StudioOptionsSheetContent(
    state: StudioUiState,
    onDismiss: () -> Unit,
    onUpdateSettings: (StudioGenerationSettingsUi) -> Unit,
    onContinualModeChange: (Boolean) -> Unit,
    onMediaTypeChange: (StudioMediaTypeUi) -> Unit,
    onTemplateSelected: (StudioTemplateUi) -> Unit,
    onOpenModelPicker: () -> Unit = {},
    onStageModelSelection: (StudioModelOptionUi) -> Unit = {},
    onConfirmModelSelection: () -> Unit = {},
    onCancelModelPicker: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sheetView = if (state.isModelPickerVisible) StudioSheetView.ModelPicker else StudioSheetView.Settings
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        horizontalAlignment = Alignment.Start,
    ) {
        EnclaveSheetHeader(
            title = "Studio Settings",
            onDismiss = onDismiss,
        )

        EnclaveNavRow(
            title = state.activeModelDisplayName ?: "Default",
            subtitle = state.resolvedModelTypeLabel,
            onClick = onOpenModelPicker,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        AnimatedContent(
            targetState = sheetView,
            transitionSpec = {
                if (targetState == StudioSheetView.ModelPicker) {
                    // Settings out to the left, picker in from the right.
                    (slideInHorizontally { it } togetherWith slideOutHorizontally { -it })
                } else {
                    // Picker out to the right, settings in from the left.
                    (slideInHorizontally { -it } togetherWith slideOutHorizontally { it })
                } using SizeTransform(clip = false)
            },
            contentKey = { it },
            label = "studioSheetView",
        ) { view ->
            when (view) {
                StudioSheetView.Settings ->
                    SettingsPane(
                        state = state,
                        onUpdateSettings = onUpdateSettings,
                        onContinualModeChange = onContinualModeChange,
                        onMediaTypeChange = onMediaTypeChange,
                        onTemplateSelected = onTemplateSelected,
                    )
                StudioSheetView.ModelPicker ->
                    ModelPickerPane(
                        assets = state.modelPickerAssets,
                        selectedId = state.selectedModelIdInPicker,
                        modelTypeLabel = state.resolvedModelTypeLabel,
                        onStageSelection = onStageModelSelection,
                        onConfirm = onConfirmModelSelection,
                        onCancel = onCancelModelPicker,
                    )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsPane(
    state: StudioUiState,
    onUpdateSettings: (StudioGenerationSettingsUi) -> Unit,
    onContinualModeChange: (Boolean) -> Unit,
    onMediaTypeChange: (StudioMediaTypeUi) -> Unit,
    onTemplateSelected: (StudioTemplateUi) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
        horizontalAlignment = Alignment.Start,
    ) {
        EnclaveSegmentedControl(
            options =
                listOf(
                    StudioMediaTypeUi.IMAGE.displayName,
                    StudioMediaTypeUi.VIDEO.displayName,
                    StudioMediaTypeUi.MUSIC.displayName,
                ),
            selectedIndex =
                when (state.mediaType) {
                    StudioMediaTypeUi.IMAGE -> 0
                    StudioMediaTypeUi.VIDEO -> 1
                    StudioMediaTypeUi.MUSIC -> 2
                },
            onOptionSelected = { index ->
                val type =
                    when (index) {
                        0 -> StudioMediaTypeUi.IMAGE
                        1 -> StudioMediaTypeUi.VIDEO
                        else -> StudioMediaTypeUi.MUSIC
                    }
                onMediaTypeChange(type)
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = EnclaveNeoTokens.sectionSpacing),
        )

        when (state.mediaType) {
            StudioMediaTypeUi.IMAGE,
            StudioMediaTypeUi.VIDEO,
            -> {
                VisualSettingsSection(
                    state = state,
                    onUpdateSettings = onUpdateSettings,
                    onContinualModeChange = onContinualModeChange,
                    onTemplateSelected = onTemplateSelected,
                )
            }
            StudioMediaTypeUi.MUSIC -> {
                MusicSettingsSection(
                    settings = state.settings as? StudioMusicSettingsUi ?: StudioMusicSettingsUi(),
                    capabilities = state.capabilities,
                    isCapabilitiesLoading = state.isCapabilitiesLoading,
                    onUpdateSettings = onUpdateSettings,
                )
            }
        }
    }
}

@Composable
private fun VisualSettingsSection(
    state: StudioUiState,
    onUpdateSettings: (StudioGenerationSettingsUi) -> Unit,
    onContinualModeChange: (Boolean) -> Unit,
    onTemplateSelected: (StudioTemplateUi) -> Unit,
) {
    VisualStylePresetsSection(
        templates = state.templates,
        selectedTemplateId = state.selectedTemplateId,
        onTemplateSelected = onTemplateSelected,
    )

    EnclaveSectionHeader(text = "General")
    Spacer(modifier = Modifier.height(EnclaveNeoTokens.rowSpacing))

    AspectRatioSettingsCard(
        settings = state.settings,
        capabilities = state.capabilities,
        isCapabilitiesLoading = state.isCapabilitiesLoading,
        onUpdateSettings = onUpdateSettings,
    )

    Spacer(modifier = Modifier.height(24.dp))

    when (state.mediaType) {
        StudioMediaTypeUi.VIDEO -> {
            VideoSettingsSection(
                settings = state.settings as? StudioVideoSettingsUi ?: StudioVideoSettingsUi(),
                capabilities = state.capabilities,
                isCapabilitiesLoading = state.isCapabilitiesLoading,
                onUpdateSettings = onUpdateSettings,
            )
        }
        StudioMediaTypeUi.IMAGE -> {
            ImageSettingsSection(
                settings = state.settings as? StudioImageSettingsUi ?: StudioImageSettingsUi(),
                capabilities = state.capabilities,
                isCapabilitiesLoading = state.isCapabilitiesLoading,
                continualMode = state.continualMode,
                onUpdateSettings = onUpdateSettings,
                onContinualModeChange = onContinualModeChange,
            )
        }
        StudioMediaTypeUi.MUSIC -> Unit
    }
}

@Composable
private fun VisualStylePresetsSection(
    templates: List<StudioTemplateUi>,
    selectedTemplateId: String?,
    onTemplateSelected: (StudioTemplateUi) -> Unit,
) {
    EnclaveSectionHeader(text = "Visual Style Presets")
    Spacer(modifier = Modifier.height(8.dp))
    StudioTemplateRow(
        templates = templates,
        selectedTemplateId = selectedTemplateId,
        onTemplateSelected = onTemplateSelected,
    )
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun AspectRatioSettingsCard(
    settings: StudioGenerationSettingsUi,
    capabilities: StudioCapabilitiesUi?,
    isCapabilitiesLoading: Boolean,
    onUpdateSettings: (StudioGenerationSettingsUi) -> Unit,
) {
    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Aspect Ratio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "For text-to-video and text-to-image generations",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))

            AspectRatioSettingsContent(
                settings = settings,
                capabilities = capabilities,
                isCapabilitiesLoading = isCapabilitiesLoading,
                onUpdateSettings = onUpdateSettings,
            )
        }
    }
}

@Composable
private fun AspectRatioSettingsContent(
    settings: StudioGenerationSettingsUi,
    capabilities: StudioCapabilitiesUi?,
    isCapabilitiesLoading: Boolean,
    onUpdateSettings: (StudioGenerationSettingsUi) -> Unit,
) {
    if (isCapabilitiesLoading) {
        StudioAspectRatioShimmer()
        return
    }

    if (capabilities == null) {
        return
    }

    if (capabilities.supportedAspectRatios.isEmpty()) {
        Text(
            text = "No aspect ratios supported",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val currentRatio =
        when (settings) {
            is StudioImageSettingsUi -> settings.aspectRatio
            is StudioVideoSettingsUi -> settings.aspectRatio
            is StudioMusicSettingsUi -> StudioAspectRatioUi.ONE_ONE
        }

    AspectRatioSelector(
        selected = currentRatio,
        options = capabilities.supportedAspectRatios,
        onSelected = { ratio ->
            val updated =
                when (settings) {
                    is StudioImageSettingsUi -> settings.copy(aspectRatio = ratio)
                    is StudioVideoSettingsUi -> settings.copy(aspectRatio = ratio)
                    is StudioMusicSettingsUi -> settings
                }
            onUpdateSettings(updated)
        },
    )
}

@Composable
private fun VideoSettingsSection(
    settings: StudioVideoSettingsUi,
    capabilities: StudioCapabilitiesUi?,
    isCapabilitiesLoading: Boolean,
    onUpdateSettings: (StudioGenerationSettingsUi) -> Unit,
) {
    EnclaveSectionHeader(text = "Video")
    Spacer(modifier = Modifier.height(EnclaveNeoTokens.rowSpacing))

    if (isCapabilitiesLoading) {
        SettingsCard {
            StudioSettingsShimmerBlock(
                rowCount = 2,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }

    capabilities?.let { caps ->
        GroupedSettingsCard {
            val durationOptions =
                caps.supportedVideoDurationTokens.ifEmpty {
                    caps.supportedVideoDurations.map { "${it}s" }
                }
            if (durationOptions.isNotEmpty()) {
                SettingRow(
                    label = "Duration",
                    controlFullWidth = true,
                ) {
                    VideoDurationControl(
                        settings = settings,
                        durationOptions = durationOptions,
                        onUpdateSettings = { updated -> onUpdateSettings(updated) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (durationOptions.isNotEmpty() && caps.supportedVideoResolutions.isNotEmpty()) {
                SettingsGroupDivider()
            }
            if (caps.supportedVideoResolutions.isNotEmpty()) {
                SettingRow(
                    label = "Resolution",
                    controlFullWidth = true,
                ) {
                    PillSelector(
                        selected = settings.videoResolution,
                        options = caps.supportedVideoResolutions,
                        onSelected = { res ->
                            onUpdateSettings(settings.copy(videoResolution = res))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageSettingsSection(
    settings: StudioImageSettingsUi,
    capabilities: StudioCapabilitiesUi?,
    isCapabilitiesLoading: Boolean,
    continualMode: Boolean,
    onUpdateSettings: (StudioGenerationSettingsUi) -> Unit,
    onContinualModeChange: (Boolean) -> Unit,
) {
    EnclaveSectionHeader(text = "Image")
    Spacer(modifier = Modifier.height(EnclaveNeoTokens.rowSpacing))

    if (isCapabilitiesLoading) {
        SettingsCard {
            StudioSettingsShimmerBlock(
                rowCount = 1,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }

    capabilities?.let { caps ->
        val qualityOptions = caps.supportedImageQualities.map { it.displayName }
        val resolutionOptions = caps.supportedImageResolutions
        GroupedSettingsCard {
            if (qualityOptions.isNotEmpty()) {
                SettingRow(
                    label = "Quality",
                    subLabel = "Speed for faster generations; Quality for higher fidelity.",
                    controlFullWidth = true,
                ) {
                    PillSelector(
                        selected = settings.quality.displayName,
                        options = qualityOptions,
                        onSelected = { qualityDisplayName ->
                            val quality =
                                caps.supportedImageQualities.first {
                                    it.displayName == qualityDisplayName
                                }
                            onUpdateSettings(settings.copy(quality = quality))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SettingsGroupDivider()
            }
            if (resolutionOptions.isNotEmpty()) {
                SettingRow(
                    label = "Resolution",
                    controlFullWidth = true,
                ) {
                    PillSelector(
                        selected =
                            settings.imageResolution?.takeIf { it in resolutionOptions }
                                ?: resolutionOptions.first(),
                        options = resolutionOptions,
                        onSelected = { resolution ->
                            onUpdateSettings(settings.copy(imageResolution = resolution))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SettingsGroupDivider()
            }
            if (caps.supportedImageSteps.isNotEmpty()) {
                val stepOptions = caps.supportedImageSteps.map(Int::toString)
                val selectedStep =
                    (settings.diffusionSteps ?: caps.defaultImageStep ?: caps.supportedImageSteps.first())
                        .toString()
                SettingRow(
                    label = "Diffusion Steps",
                    controlFullWidth = true,
                ) {
                    PillSelector(
                        selected = selectedStep,
                        options = stepOptions,
                        onSelected = { step ->
                            onUpdateSettings(settings.copy(diffusionSteps = step.toInt()))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SettingsGroupDivider()
            }
            if (settings.referenceImageUri != null) {
                Img2ImgDenoiseSetting(
                    settings = settings,
                    onUpdateSettings = onUpdateSettings,
                )
                SettingsGroupDivider()
            }
            SettingRow(label = "Generation Count") {
                GenerationCountPicker(
                    count = settings.generationCount,
                    onCountChange = { newValue ->
                        onUpdateSettings(settings.copy(generationCount = newValue))
                    },
                )
            }
            SettingsGroupDivider()
            SettingRow(
                label = "Generative Scrolling",
                subLabel = "Generate new images as you scroll.",
            ) {
                Switch(
                    checked = continualMode,
                    onCheckedChange = onContinualModeChange,
                )
            }
        }
    }
}

@Composable
private fun MusicSettingsSection(
    settings: StudioMusicSettingsUi,
    capabilities: StudioCapabilitiesUi?,
    isCapabilitiesLoading: Boolean,
    onUpdateSettings: (StudioGenerationSettingsUi) -> Unit,
) {
    val durationRange =
        capabilities?.let { it.musicDurationStart.toFloat()..it.musicDurationEnd.toFloat() }
            ?: 5f..120f
    val tempoRange =
        capabilities?.let { it.musicTempoStart.toFloat()..it.musicTempoEnd.toFloat() }
            ?: 60f..200f
    EnclaveSectionHeader(text = "Music Configuration")
    Spacer(modifier = Modifier.height(EnclaveNeoTokens.rowSpacing))

    if (isCapabilitiesLoading) {
        SettingsCard {
            StudioSettingsShimmerBlock(
                rowCount = 2,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }

    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            MusicSliderSetting(
                label = "Duration",
                valueText = "${settings.duration}s",
                value = settings.duration.toFloat().coerceIn(durationRange),
                valueRange = durationRange,
                onValueChange = { onUpdateSettings(settings.copy(duration = it.toInt())) },
            )

            Spacer(modifier = Modifier.height(16.dp))

            MusicSliderSetting(
                label = "Tempo",
                valueText = "${settings.tempo} BPM",
                value = settings.tempo.toFloat().coerceIn(tempoRange),
                valueRange = tempoRange,
                onValueChange = { onUpdateSettings(settings.copy(tempo = it.toInt())) },
            )
        }
    }
}

private const val IMG2IMG_DENOISE_TOOLTIP =
    "Controls how much the model changes your reference photo. " +
        "Lower values preserve more of the original image; higher values follow the prompt more freely."

@Composable
private fun Img2ImgDenoiseSetting(
    settings: StudioImageSettingsUi,
    onUpdateSettings: (StudioImageSettingsUi) -> Unit,
) {
    val denoiseRange =
        StudioImageSettingsUi.MIN_IMG2IMG_DENOISE_STRENGTH..
            StudioImageSettingsUi.MAX_IMG2IMG_DENOISE_STRENGTH
    val value = settings.img2ImgDenoiseStrength.coerceIn(denoiseRange)
    val valueLabel = "${(value * 100).roundToInt()}%"

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Edit Strength",
                    style = MaterialTheme.typography.titleMedium,
                )
                PersistentTooltip(description = IMG2IMG_DENOISE_TOOLTIP)
            }
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Slider(
            value = value,
            onValueChange = { newValue ->
                onUpdateSettings(settings.copy(img2ImgDenoiseStrength = newValue.coerceIn(denoiseRange)))
            },
            valueRange = denoiseRange,
        )
    }
}

@Composable
private fun MusicSliderSetting(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(valueText, style = MaterialTheme.typography.bodyMedium)
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
    )
}

@Composable
private fun GroupedSettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingsGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = EnclaveNeoTokens.borderWidth,
        color = enclaveBorderColor(),
    )
}

@Composable
private fun SettingsCard(
    shape: Shape = EnclaveNeoTokens.shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val borderColor = enclaveBorderColor()
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .enclaveSurface(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    borderColor = borderColor,
                    withShadow = false,
                    shape = shape,
                ).clip(shape),
    ) {
        content()
    }
}

@Composable
fun AspectRatioSelector(
    selected: StudioAspectRatioUi,
    options: List<StudioAspectRatioUi>,
    onSelected: (StudioAspectRatioUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(options) { ratio ->
            val isSelected = selected == ratio
            val ratioValue =
                when (ratio) {
                    StudioAspectRatioUi.ONE_ONE -> 1f
                    StudioAspectRatioUi.THREE_FOUR -> 3f / 4f
                    StudioAspectRatioUi.FOUR_THREE -> 4f / 3f
                    StudioAspectRatioUi.NINE_SIXTEEN -> 9f / 16f
                    StudioAspectRatioUi.SIXTEEN_NINE -> 16f / 9f
                    StudioAspectRatioUi.TWO_THREE -> 2f / 3f
                    StudioAspectRatioUi.THREE_TWO -> 3f / 2f
                    StudioAspectRatioUi.TWENTY_ONE_NINE -> 21f / 9f
                    StudioAspectRatioUi.FIVE_FOUR -> 5f / 4f
                }

            Column(
                modifier =
                    Modifier
                        .clickable { onSelected(ratio) }
                        .width(IntrinsicSize.Min),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .height(48.dp)
                            .width(48.dp * ratioValue.coerceAtLeast(0.5f).coerceAtMost(2f))
                            .clip(EnclaveNeoTokens.shape)
                            .background(
                                color =
                                    if (isSelected) {
                                        Color.White
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    },
                            ).border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(6.dp),
                            ),
                )
                Text(
                    text = ratio.ratio,
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun GenerationCountPicker(
    count: Int,
    onCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    EnclaveStepper(
        value = count,
        onValueChange = onCountChange,
        modifier = modifier,
        min = StudioImageSettingsUi.MIN_GENERATION_COUNT,
        max = StudioImageSettingsUi.MAX_GENERATION_COUNT,
    )
}

private val previewImageTemplates =
    listOf(
        StudioTemplateUi("1", "Cyberpunk", "cyberpunk", "Neon lighting, ", " 8k"),
        StudioTemplateUi("2", "Oil Painting", "oil_painting", "Oil painting of ", " canvas"),
        StudioTemplateUi("3", "Anime", "anime", "Anime style ", " vibrant"),
        StudioTemplateUi("4", "Cinematic", "cinematic", "Cinematic shot ", " dramatic"),
    )

private val previewStudioCapabilities =
    StudioCapabilitiesUi(
        supportedAspectRatios =
            listOf(
                StudioAspectRatioUi.ONE_ONE,
                StudioAspectRatioUi.THREE_FOUR,
                StudioAspectRatioUi.FOUR_THREE,
                StudioAspectRatioUi.NINE_SIXTEEN,
                StudioAspectRatioUi.SIXTEEN_NINE,
                StudioAspectRatioUi.TWO_THREE,
                StudioAspectRatioUi.THREE_TWO,
            ),
        supportedImageQualities = listOf(StudioQualityUi.SPEED, StudioQualityUi.QUALITY),
        supportedVideoQualities = listOf(StudioQualityUi.SPEED, StudioQualityUi.QUALITY),
        supportedVideoResolutions = listOf("720p", "1080p"),
        supportedVideoDurations = listOf(5, 10),
        supportsVideo = true,
    )

private val previewPickerAssets =
    StudioCapablePresetsUi(
        mediaAssets =
            listOf(
                StudioModelOptionUi("fal-1", "Fal Aura", "Image", StudioOverrideKind.MEDIA_PROVIDER, "Fal Aura"),
                StudioModelOptionUi("fal-2", "Fal Flux", "Image", StudioOverrideKind.MEDIA_PROVIDER, "Fal Flux"),
            ),
    )

@Preview(showBackground = true, name = "Studio Options - Image (Light)", heightDp = 920)
@Composable
fun PreviewStudioOptionsImage() {
    val state =
        StudioUiState(
            mediaType = StudioMediaTypeUi.IMAGE,
            templates = previewImageTemplates,
            selectedTemplateId = "1",
            settings = StudioImageSettingsUi(),
            capabilities = previewStudioCapabilities,
            continualMode = false,
            resolvedModelTypeLabel = "Image Generation",
        )
    EnclaveTheme(darkTheme = false) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            StudioOptionsSheetContent(
                state = state,
                onDismiss = {},
                onUpdateSettings = {},
                onContinualModeChange = {},
                onMediaTypeChange = {},
                onTemplateSelected = {},
                onOpenModelPicker = {},
                onStageModelSelection = {},
                onConfirmModelSelection = {},
                onCancelModelPicker = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Studio Options - Image (Dark)", heightDp = 920)
@Composable
fun PreviewStudioOptionsImageDark() {
    val state =
        StudioUiState(
            mediaType = StudioMediaTypeUi.IMAGE,
            templates = previewImageTemplates,
            selectedTemplateId = "1",
            settings = StudioImageSettingsUi(),
            capabilities = previewStudioCapabilities,
            continualMode = false,
            resolvedModelTypeLabel = "Image Generation",
        )
    EnclaveTheme(darkTheme = true) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            StudioOptionsSheetContent(
                state = state,
                onDismiss = {},
                onUpdateSettings = {},
                onContinualModeChange = {},
                onMediaTypeChange = {},
                onTemplateSelected = {},
                onOpenModelPicker = {},
                onStageModelSelection = {},
                onConfirmModelSelection = {},
                onCancelModelPicker = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Studio Options - Music", heightDp = 520)
@Composable
fun PreviewStudioOptionsMusic() {
    val state =
        StudioUiState(
            mediaType = StudioMediaTypeUi.MUSIC,
        )
    EnclaveTheme {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            StudioOptionsSheetContent(
                state = state,
                onDismiss = {},
                onUpdateSettings = {},
                onContinualModeChange = {},
                onMediaTypeChange = {},
                onTemplateSelected = {},
                onOpenModelPicker = {},
                onStageModelSelection = {},
                onConfirmModelSelection = {},
                onCancelModelPicker = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Studio Options - Model Picker", heightDp = 920)
@Composable
fun PreviewStudioOptionsModelPicker() {
    val state =
        StudioUiState(
            mediaType = StudioMediaTypeUi.IMAGE,
            isModelPickerVisible = true,
            selectedModelIdInPicker = "fal-2",
            modelPickerAssets = previewPickerAssets,
            activeModelDisplayName = "Default Image",
            resolvedModelTypeLabel = "Image Generation",
        )
    EnclaveTheme {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            StudioOptionsSheetContent(
                state = state,
                onDismiss = {},
                onUpdateSettings = {},
                onContinualModeChange = {},
                onMediaTypeChange = {},
                onTemplateSelected = {},
                onOpenModelPicker = {},
                onStageModelSelection = {},
                onConfirmModelSelection = {},
                onCancelModelPicker = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Studio Options - Empty Catalog", heightDp = 760)
@Composable
fun PreviewStudioOptionsEmptyCatalog() {
    val state =
        StudioUiState(
            mediaType = StudioMediaTypeUi.IMAGE,
            isModelPickerVisible = true,
            selectedModelIdInPicker = null,
            modelPickerAssets = StudioCapablePresetsUi.EMPTY,
            resolvedModelTypeLabel = "Image Generation",
        )
    EnclaveTheme {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            StudioOptionsSheetContent(
                state = state,
                onDismiss = {},
                onUpdateSettings = {},
                onContinualModeChange = {},
                onMediaTypeChange = {},
                onTemplateSelected = {},
                onOpenModelPicker = {},
                onStageModelSelection = {},
                onConfirmModelSelection = {},
                onCancelModelPicker = {},
            )
        }
    }
}

package com.browntowndev.enclave.feature.studio.components

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
import androidx.compose.material3.*
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
import com.browntowndev.enclave.core.ui.component.neo.NeoModalBottomSheet
import com.browntowndev.enclave.core.ui.component.neo.NeoSectionHeader
import com.browntowndev.enclave.core.ui.component.neo.NeoSegmentedControl
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetHeader
import com.browntowndev.enclave.core.ui.component.neo.NeoStepper
import com.browntowndev.enclave.core.ui.component.neo.neoBorderColor
import com.browntowndev.enclave.core.ui.component.neo.neoSurface
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.*
import com.browntowndev.enclave.feature.studio.R
import com.browntowndev.enclave.feature.studio.StudioUiState

@Composable
fun StudioTemplateRow(
    templates: List<StudioTemplate>,
    selectedTemplateId: String?,
    onTemplateSelected: (StudioTemplate) -> Unit,
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
    template: StudioTemplate,
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
                        color = if (isSelected) MaterialTheme.colorScheme.primary else neoBorderColor(),
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
    onUpdateSettings: (GenerationSettings) -> Unit,
    onContinualModeChange: (Boolean) -> Unit,
    onMediaTypeChange: (MediaCapability) -> Unit,
    onTemplateSelected: (StudioTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    NeoModalBottomSheet(
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
    NeoSegmentedControl(
        options = options,
        selectedIndex = selectedIndex,
        onOptionSelected = { index -> onSelected(options[index]) },
        modifier = modifier,
    )
}

@Composable
fun StudioOptionsSheetContent(
    state: StudioUiState,
    onDismiss: () -> Unit,
    onUpdateSettings: (GenerationSettings) -> Unit,
    onContinualModeChange: (Boolean) -> Unit,
    onMediaTypeChange: (MediaCapability) -> Unit,
    onTemplateSelected: (StudioTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .navigationBarsPadding(),
        horizontalAlignment = Alignment.Start,
    ) {
        NeoSheetHeader(
            title = "Studio Settings",
            onDismiss = onDismiss,
        )

        NeoSegmentedControl(
            options =
                listOf(
                    MediaCapability.IMAGE.displayName,
                    MediaCapability.VIDEO.displayName,
                    MediaCapability.MUSIC.displayName,
                ),
            selectedIndex =
                when (state.mediaType) {
                    MediaCapability.IMAGE -> 0
                    MediaCapability.VIDEO -> 1
                    MediaCapability.MUSIC -> 2
                },
            onOptionSelected = { index ->
                val type =
                    when (index) {
                        0 -> MediaCapability.IMAGE
                        1 -> MediaCapability.VIDEO
                        else -> MediaCapability.MUSIC
                    }
                onMediaTypeChange(type)
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = EnclaveNeoTokens.sectionSpacing),
        )

        when (state.mediaType) {
            MediaCapability.IMAGE,
            MediaCapability.VIDEO,
            -> {
                VisualSettingsSection(
                    state = state,
                    onUpdateSettings = onUpdateSettings,
                    onContinualModeChange = onContinualModeChange,
                    onTemplateSelected = onTemplateSelected,
                )
            }
            MediaCapability.MUSIC -> {
                MusicSettingsSection(
                    settings = state.settings as? MusicGenerationSettings ?: MusicGenerationSettings(),
                    onUpdateSettings = onUpdateSettings,
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun VisualSettingsSection(
    state: StudioUiState,
    onUpdateSettings: (GenerationSettings) -> Unit,
    onContinualModeChange: (Boolean) -> Unit,
    onTemplateSelected: (StudioTemplate) -> Unit,
) {
    VisualStylePresetsSection(
        templates = state.templates,
        selectedTemplateId = state.selectedTemplateId,
        onTemplateSelected = onTemplateSelected,
    )

    NeoSectionHeader(text = "General")
    Spacer(modifier = Modifier.height(EnclaveNeoTokens.rowSpacing))

    AspectRatioSettingsCard(
        settings = state.settings,
        capabilities = state.capabilities,
        onUpdateSettings = onUpdateSettings,
    )

    Spacer(modifier = Modifier.height(24.dp))

    when (state.mediaType) {
        MediaCapability.VIDEO -> {
            VideoSettingsSection(
                settings = state.settings as? VideoGenerationSettings ?: VideoGenerationSettings(),
                capabilities = state.capabilities,
                onUpdateSettings = onUpdateSettings,
            )
        }
        MediaCapability.IMAGE -> {
            ImageSettingsSection(
                settings = state.settings as? ImageGenerationSettings ?: ImageGenerationSettings(),
                capabilities = state.capabilities,
                continualMode = state.continualMode,
                onUpdateSettings = onUpdateSettings,
                onContinualModeChange = onContinualModeChange,
            )
        }
        MediaCapability.MUSIC -> Unit
    }
}

@Composable
private fun VisualStylePresetsSection(
    templates: List<StudioTemplate>,
    selectedTemplateId: String?,
    onTemplateSelected: (StudioTemplate) -> Unit,
) {
    NeoSectionHeader(text = "Visual Style Presets")
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
    settings: GenerationSettings,
    capabilities: ProviderCapabilities?,
    onUpdateSettings: (GenerationSettings) -> Unit,
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
                onUpdateSettings = onUpdateSettings,
            )
        }
    }
}

@Composable
private fun AspectRatioSettingsContent(
    settings: GenerationSettings,
    capabilities: ProviderCapabilities?,
    onUpdateSettings: (GenerationSettings) -> Unit,
) {
    if (capabilities == null) {
        Text(
            text = "Loading capabilities...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            is ImageGenerationSettings -> settings.aspectRatio
            is VideoGenerationSettings -> settings.aspectRatio
            is MusicGenerationSettings -> AspectRatio.ONE_ONE
        }

    AspectRatioSelector(
        selected = currentRatio,
        options = capabilities.supportedAspectRatios,
        onSelected = { ratio ->
            val updated =
                when (settings) {
                    is ImageGenerationSettings -> settings.copy(aspectRatio = ratio)
                    is VideoGenerationSettings -> settings.copy(aspectRatio = ratio)
                    is MusicGenerationSettings -> settings
                }
            onUpdateSettings(updated)
        },
    )
}

@Composable
private fun VideoSettingsSection(
    settings: VideoGenerationSettings,
    capabilities: ProviderCapabilities?,
    onUpdateSettings: (GenerationSettings) -> Unit,
) {
    NeoSectionHeader(text = "Video")
    Spacer(modifier = Modifier.height(EnclaveNeoTokens.rowSpacing))

    capabilities?.let { caps ->
        GroupedSettingsCard {
            if (caps.supportedVideoDurations.isNotEmpty()) {
                SettingRow(
                    label = "Duration",
                    controlFullWidth = true,
                ) {
                    PillSelector(
                        selected = "${settings.videoDuration}s",
                        options = caps.supportedVideoDurations.map { "${it}s" },
                        onSelected = { durationStr ->
                            val duration = durationStr.dropLast(1).toIntOrNull() ?: settings.videoDuration
                            onUpdateSettings(settings.copy(videoDuration = duration))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (caps.supportedVideoDurations.isNotEmpty() && caps.supportedVideoResolutions.isNotEmpty()) {
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
    settings: ImageGenerationSettings,
    capabilities: ProviderCapabilities?,
    continualMode: Boolean,
    onUpdateSettings: (GenerationSettings) -> Unit,
    onContinualModeChange: (Boolean) -> Unit,
) {
    NeoSectionHeader(text = "Image")
    Spacer(modifier = Modifier.height(EnclaveNeoTokens.rowSpacing))

    capabilities?.let { caps ->
        val qualityOptions = caps.supportedImageQualities.map { it.displayName }
        GroupedSettingsCard {
            SettingRow(
                label = "Quality",
                subLabel = "Speed for faster generations; Quality for higher fidelity.",
                controlFullWidth = true,
            ) {
                if (qualityOptions.isNotEmpty()) {
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
                } else {
                    Text("Default", style = MaterialTheme.typography.bodySmall)
                }
            }
            SettingsGroupDivider()
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
    settings: MusicGenerationSettings,
    onUpdateSettings: (GenerationSettings) -> Unit,
) {
    NeoSectionHeader(text = "Music Configuration")
    Spacer(modifier = Modifier.height(EnclaveNeoTokens.rowSpacing))

    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            MusicSliderSetting(
                label = "Duration",
                valueText = "${settings.duration}s",
                value = settings.duration.toFloat(),
                valueRange = 5f..120f,
                onValueChange = { onUpdateSettings(settings.copy(duration = it.toInt())) },
            )

            Spacer(modifier = Modifier.height(16.dp))

            MusicSliderSetting(
                label = "Tempo",
                valueText = "${settings.tempo} BPM",
                value = settings.tempo.toFloat(),
                valueRange = 60f..200f,
                onValueChange = { onUpdateSettings(settings.copy(tempo = it.toInt())) },
            )
        }
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
        color = neoBorderColor(),
    )
}

@Composable
private fun SettingsCard(
    shape: Shape = EnclaveNeoTokens.shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val borderColor = neoBorderColor()
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .neoSurface(
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
    selected: AspectRatio,
    options: List<AspectRatio>,
    onSelected: (AspectRatio) -> Unit,
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
                    AspectRatio.ONE_ONE -> 1f
                    AspectRatio.THREE_FOUR -> 3f / 4f
                    AspectRatio.FOUR_THREE -> 4f / 3f
                    AspectRatio.NINE_SIXTEEN -> 9f / 16f
                    AspectRatio.SIXTEEN_NINE -> 16f / 9f
                    AspectRatio.TWO_THREE -> 2f / 3f
                    AspectRatio.THREE_TWO -> 3f / 2f
                    AspectRatio.TWENTY_ONE_NINE -> 21f / 9f
                    AspectRatio.FIVE_FOUR -> 5f / 4f
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
    NeoStepper(
        value = count,
        onValueChange = onCountChange,
        modifier = modifier,
        min = ImageGenerationSettings.MIN_GENERATION_COUNT,
        max = ImageGenerationSettings.MAX_GENERATION_COUNT,
    )
}

private val previewImageTemplates =
    listOf(
        StudioTemplate("1", "Cyberpunk", "cyberpunk", "Neon lighting, ", " 8k"),
        StudioTemplate("2", "Oil Painting", "oil_painting", "Oil painting of ", " canvas"),
        StudioTemplate("3", "Anime", "anime", "Anime style ", " vibrant"),
        StudioTemplate("4", "Cinematic", "cinematic", "Cinematic shot ", " dramatic"),
    )

private val previewStudioCapabilities =
    ProviderCapabilities(
        supportedAspectRatios =
            listOf(
                AspectRatio.ONE_ONE,
                AspectRatio.THREE_FOUR,
                AspectRatio.FOUR_THREE,
                AspectRatio.NINE_SIXTEEN,
                AspectRatio.SIXTEEN_NINE,
                AspectRatio.TWO_THREE,
                AspectRatio.THREE_TWO,
            ),
        supportedImageQualities = listOf(GenerationQuality.SPEED, GenerationQuality.QUALITY),
        supportedVideoQualities = listOf(GenerationQuality.SPEED, GenerationQuality.QUALITY),
        supportedVideoResolutions = listOf("720p", "1080p"),
        supportedVideoDurations = listOf(5, 10),
        supportsReferenceImage = true,
        supportsVideo = true,
    )

@Preview(showBackground = true, name = "Studio Options - Image", heightDp = 920)
@Composable
fun PreviewStudioOptionsImage() {
    val state =
        StudioUiState(
            mediaType = MediaCapability.IMAGE,
            templates = previewImageTemplates,
            selectedTemplateId = "1",
            settings = ImageGenerationSettings(),
            capabilities = previewStudioCapabilities,
            continualMode = false,
        )
    EnclaveTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            StudioOptionsSheetContent(
                state = state,
                onDismiss = {},
                onUpdateSettings = {},
                onContinualModeChange = {},
                onMediaTypeChange = {},
                onTemplateSelected = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Studio Options - Music", heightDp = 520)
@Composable
fun PreviewStudioOptionsMusic() {
    val state =
        StudioUiState(
            mediaType = MediaCapability.MUSIC,
        )
    EnclaveTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            StudioOptionsSheetContent(
                state = state,
                onDismiss = {},
                onUpdateSettings = {},
                onContinualModeChange = {},
                onMediaTypeChange = {},
                onTemplateSelected = {},
            )
        }
    }
}

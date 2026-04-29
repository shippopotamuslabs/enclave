package com.browntowndev.pocketcrew.feature.studio.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import coil3.compose.AsyncImage
import com.browntowndev.pocketcrew.core.ui.component.MediaModeToggle
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.media.*
import com.browntowndev.pocketcrew.feature.studio.R
import com.browntowndev.pocketcrew.feature.studio.StudioUiState


@Composable
fun StudioTemplateRow(
    templates: List<StudioTemplate>,
    selectedTemplateId: String?,
    onTemplateSelected: (StudioTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(templates) { template ->
            TemplateChip(
                template = template,
                isSelected = template.id == selectedTemplateId,
                onClick = { onTemplateSelected(template) }
            )
        }
    }
}

@Composable
fun TemplateChip(
    template: StudioTemplate,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageModel = remember(template.exampleUri) {
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
        modifier = modifier
            .width(80.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = template.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = template.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
fun SettingRow(
    label: String,
    subLabel: String = "",
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
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
        Box(contentAlignment = Alignment.CenterEnd) {
            content()
        }
    }
}

@Composable
fun ResolutionSelector(
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { res ->
            FilterChip(
                selected = selected == res,
                onClick = { onSelected(res) },
                label = { Text(res) }
            )
        }
    }
}

@Composable
fun DurationSelector(
    selected: Int,
    options: List<Int>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { duration ->
            FilterChip(
                selected = selected == duration,
                onClick = { onSelected(duration) },
                label = { Text("${duration}s") }
            )
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
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        StudioOptionsSheetContent(
            state = state,
            onUpdateSettings = onUpdateSettings,
            onContinualModeChange = onContinualModeChange,
            onMediaTypeChange = onMediaTypeChange,
            onTemplateSelected = onTemplateSelected
        )
    }
}

@Composable
fun PillSelector(
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val itemWidth = 80.dp
    val totalWidth = itemWidth * options.size

    Box(
        modifier = modifier
            .width(totalWidth)
            .height(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        // Indicator offset animation
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = tween(durationMillis = 250),
            label = "pillIndicatorOffset"
        )

        // Sliding Indicator
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(itemWidth)
                .fillMaxHeight()
                .padding(2.dp)
                .clip(CircleShape)
                .background(Color.White)
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "pillTextColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelected(option) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun StudioOptionsSheetContent(
    state: StudioUiState,
    onUpdateSettings: (GenerationSettings) -> Unit,
    onContinualModeChange: (Boolean) -> Unit,
    onMediaTypeChange: (MediaCapability) -> Unit,
    onTemplateSelected: (StudioTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 8.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StudioOptionsHeader(
            selectedType = state.mediaType,
            onMediaTypeChange = onMediaTypeChange
        )

        Spacer(modifier = Modifier.height(32.dp))

        when (state.mediaType) {
            MediaCapability.IMAGE,
            MediaCapability.VIDEO -> {
                VisualSettingsSection(
                    state = state,
                    onUpdateSettings = onUpdateSettings,
                    onContinualModeChange = onContinualModeChange,
                    onTemplateSelected = onTemplateSelected
                )
            }
            MediaCapability.MUSIC -> {
                MusicSettingsSection(
                    settings = state.settings as? MusicGenerationSettings ?: MusicGenerationSettings(),
                    onUpdateSettings = onUpdateSettings
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun StudioOptionsHeader(
    selectedType: MediaCapability,
    onMediaTypeChange: (MediaCapability) -> Unit
) {
    Text(
        text = "Studio Settings",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
    )

    MediaModeToggle(
        selectedType = selectedType,
        onTypeChange = onMediaTypeChange,
        modifier = Modifier.fillMaxWidth(0.75f)
    )
}

@Composable
private fun VisualSettingsSection(
    state: StudioUiState,
    onUpdateSettings: (GenerationSettings) -> Unit,
    onContinualModeChange: (Boolean) -> Unit,
    onTemplateSelected: (StudioTemplate) -> Unit
) {
    VisualStylePresetsSection(
        templates = state.templates,
        selectedTemplateId = state.selectedTemplateId,
        onTemplateSelected = onTemplateSelected
    )

    SectionHeader(text = "General")
    Spacer(modifier = Modifier.height(8.dp))

    AspectRatioSettingsCard(
        settings = state.settings,
        capabilities = state.capabilities,
        onUpdateSettings = onUpdateSettings
    )

    Spacer(modifier = Modifier.height(24.dp))

    when (state.mediaType) {
        MediaCapability.VIDEO -> {
            VideoSettingsSection(
                settings = state.settings as? VideoGenerationSettings ?: VideoGenerationSettings(),
                capabilities = state.capabilities,
                onUpdateSettings = onUpdateSettings
            )
        }
        MediaCapability.IMAGE -> {
            ImageSettingsSection(
                settings = state.settings as? ImageGenerationSettings ?: ImageGenerationSettings(),
                capabilities = state.capabilities,
                continualMode = state.continualMode,
                onUpdateSettings = onUpdateSettings,
                onContinualModeChange = onContinualModeChange
            )
        }
        MediaCapability.MUSIC -> Unit
    }
}

@Composable
private fun VisualStylePresetsSection(
    templates: List<StudioTemplate>,
    selectedTemplateId: String?,
    onTemplateSelected: (StudioTemplate) -> Unit
) {
    SectionHeader(text = "Visual Style Presets")
    Spacer(modifier = Modifier.height(8.dp))
    StudioTemplateRow(
        templates = templates,
        selectedTemplateId = selectedTemplateId,
        onTemplateSelected = onTemplateSelected
    )
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun AspectRatioSettingsCard(
    settings: GenerationSettings,
    capabilities: ProviderCapabilities?,
    onUpdateSettings: (GenerationSettings) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Aspect Ratio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "For text-to-video and text-to-image generations",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            AspectRatioSettingsContent(
                settings = settings,
                capabilities = capabilities,
                onUpdateSettings = onUpdateSettings
            )
        }
    }
}

@Composable
private fun AspectRatioSettingsContent(
    settings: GenerationSettings,
    capabilities: ProviderCapabilities?,
    onUpdateSettings: (GenerationSettings) -> Unit
) {
    if (capabilities == null) {
        Text(
            text = "Loading capabilities...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    if (capabilities.supportedAspectRatios.isEmpty()) {
        Text(
            text = "No aspect ratios supported",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val currentRatio = when (settings) {
        is ImageGenerationSettings -> settings.aspectRatio
        is VideoGenerationSettings -> settings.aspectRatio
        is MusicGenerationSettings -> AspectRatio.ONE_ONE
    }

    AspectRatioSelector(
        selected = currentRatio,
        options = capabilities.supportedAspectRatios,
        onSelected = { ratio ->
            val updated = when (settings) {
                is ImageGenerationSettings -> settings.copy(aspectRatio = ratio)
                is VideoGenerationSettings -> settings.copy(aspectRatio = ratio)
                is MusicGenerationSettings -> settings
            }
            onUpdateSettings(updated)
        }
    )
}

@Composable
private fun VideoSettingsSection(
    settings: VideoGenerationSettings,
    capabilities: ProviderCapabilities?,
    onUpdateSettings: (GenerationSettings) -> Unit
) {
    SectionHeader(text = "Video")
    Spacer(modifier = Modifier.height(8.dp))

    capabilities?.let { caps ->
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (caps.supportedVideoDurations.isNotEmpty()) {
                SettingsCard(
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp
                    )
                ) {
                    SettingRow(label = "Duration") {
                        PillSelector(
                            selected = "${settings.videoDuration}s",
                            options = caps.supportedVideoDurations.map { "${it}s" },
                            onSelected = { durationStr ->
                                val duration = durationStr.dropLast(1).toIntOrNull() ?: settings.videoDuration
                                onUpdateSettings(settings.copy(videoDuration = duration))
                            }
                        )
                    }
                }
            }

            if (caps.supportedVideoDurations.isNotEmpty() && caps.supportedVideoResolutions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(5.dp))
            }

            if (caps.supportedVideoResolutions.isNotEmpty()) {
                SettingsCard(
                    shape = RoundedCornerShape(
                        topStart = 0.dp,
                        topEnd = 0.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                ) {
                    SettingRow(label = "Resolution") {
                        PillSelector(
                            selected = settings.videoResolution,
                            options = caps.supportedVideoResolutions,
                            onSelected = { res ->
                                onUpdateSettings(settings.copy(videoResolution = res))
                            }
                        )
                    }
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
    onContinualModeChange: (Boolean) -> Unit
) {
    SectionHeader(text = "Image")
    Spacer(modifier = Modifier.height(8.dp))

    capabilities?.let { caps ->
        ImageQualityCard(
            settings = settings,
            capabilities = caps,
            onUpdateSettings = onUpdateSettings
        )

        Spacer(modifier = Modifier.height(2.dp))

        GenerationCountCard(
            settings = settings,
            onUpdateSettings = onUpdateSettings
        )

        Spacer(modifier = Modifier.height(2.dp))

        ContinualModeCard(
            continualMode = continualMode,
            onContinualModeChange = onContinualModeChange
        )
    }
}

@Composable
private fun ImageQualityCard(
    settings: ImageGenerationSettings,
    capabilities: ProviderCapabilities,
    onUpdateSettings: (GenerationSettings) -> Unit
) {
    SettingsCard(
        shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        )
    ) {
        SettingRow(label = "Image Gen Mode") {
            if (capabilities.supportedImageQualities.isNotEmpty()) {
                PillSelector(
                    selected = settings.quality.displayName,
                    options = capabilities.supportedImageQualities.map { it.displayName },
                    onSelected = { qualityDisplayName ->
                        val quality = capabilities.supportedImageQualities.first {
                            it.displayName == qualityDisplayName
                        }
                        onUpdateSettings(settings.copy(quality = quality))
                    }
                )
            } else {
                Text("Default", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun GenerationCountCard(
    settings: ImageGenerationSettings,
    onUpdateSettings: (GenerationSettings) -> Unit
) {
    SettingsCard(shape = RoundedCornerShape(0.dp)) {
        SettingRow(label = "Generation Count") {
            GenerationCountPicker(
                count = settings.generationCount,
                onCountChange = { newValue ->
                    onUpdateSettings(settings.copy(generationCount = newValue))
                }
            )
        }
    }
}

@Composable
private fun ContinualModeCard(
    continualMode: Boolean,
    onContinualModeChange: (Boolean) -> Unit
) {
    SettingsCard(
        shape = RoundedCornerShape(
            topStart = 0.dp,
            topEnd = 0.dp,
            bottomStart = 16.dp,
            bottomEnd = 16.dp
        )
    ) {
        SettingRow(label = "Generative Scrolling", subLabel = "Generate new images as you scroll.") {
            Switch(
                checked = continualMode,
                onCheckedChange = onContinualModeChange
            )
        }
    }
}

@Composable
private fun MusicSettingsSection(
    settings: MusicGenerationSettings,
    onUpdateSettings: (GenerationSettings) -> Unit
) {
    SectionHeader(text = "Music Configuration")
    Spacer(modifier = Modifier.height(16.dp))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            MusicSliderSetting(
                label = "Duration",
                valueText = "${settings.duration}s",
                value = settings.duration.toFloat(),
                valueRange = 5f..120f,
                onValueChange = { onUpdateSettings(settings.copy(duration = it.toInt())) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            MusicSliderSetting(
                label = "Tempo",
                valueText = "${settings.tempo} BPM",
                value = settings.tempo.toFloat(),
                valueRange = 60f..200f,
                onValueChange = { onUpdateSettings(settings.copy(tempo = it.toInt())) }
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
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(valueText, style = MaterialTheme.typography.bodyMedium)
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange
    )
}

@Composable
private fun SettingsCard(
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = shape,
        modifier = modifier.fillMaxWidth()
    ) {
        content()
    }
}

@Composable
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        modifier = modifier.padding(start = 16.dp).fillMaxWidth(),
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun AspectRatioSelector(
    selected: AspectRatio,
    options: List<AspectRatio>,
    onSelected: (AspectRatio) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(options) { ratio ->
            val isSelected = selected == ratio
            val ratioValue = when (ratio) {
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
                modifier = Modifier
                    .clickable { onSelected(ratio) }
                    .width(IntrinsicSize.Min),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .width(40.dp * ratioValue.coerceAtLeast(0.5f).coerceAtMost(2f))
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            color = if (isSelected) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                )
                Text(
                    text = ratio.ratio,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun GenerationCountPicker(
    count: Int,
    onCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val min = ImageGenerationSettings.MIN_GENERATION_COUNT
    val max = ImageGenerationSettings.MAX_GENERATION_COUNT

    Row(
        modifier = modifier
            .width(100.dp)
            .height(44.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var textValue by remember(count) { mutableStateOf(count.toString()) }

        BasicTextField(
            value = textValue,
            onValueChange = {
                textValue = it
                it.toIntOrNull()?.let { newVal ->
                    onCountChange(newVal.coerceIn(min, max))
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    innerTextField()
                }
            }
        )

        VerticalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp
        )

        Column(
            modifier = Modifier
                .width(42.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { if (count < max) onCountChange(count + 1) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { if (count > min) onCountChange(count - 1) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Studio Options - Music")
@Composable
fun PreviewStudioOptionsMusic() {
    val state = StudioUiState(
        mediaType = MediaCapability.MUSIC
    )
    PocketCrewTheme {
        Surface {
            StudioOptionsSheetContent(
                state = state,
                onUpdateSettings = {},
                onContinualModeChange = {},
                onMediaTypeChange = {},
                onTemplateSelected = {}
            )
        }
    }
}

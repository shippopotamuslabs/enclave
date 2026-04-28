package com.browntowndev.pocketcrew.feature.studio.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import coil3.compose.AsyncImage
import com.browntowndev.pocketcrew.core.ui.component.MediaModeToggle
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.media.*
import com.browntowndev.pocketcrew.feature.studio.StudioUiState


@Composable
fun StudioTemplateRow(
    templates: List<StudioTemplate>,
    selectedTemplateId: String?,
    onTemplateSelected: (StudioTemplate) -> Unit
) {
    LazyRow(
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
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
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
                model = template.exampleUri,
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
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        Box(contentAlignment = Alignment.CenterEnd) {
            content()
        }
    }
}

@Composable
fun ResolutionSelector(
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    onSelected: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    onTemplateSelected: (StudioTemplate) -> Unit
) {
    ModalBottomSheet(
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
    onTemplateSelected: (StudioTemplate) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Studio Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // Master Synced Toggle - 75% width
        MediaModeToggle(
            selectedType = state.mediaType,
            onTypeChange = onMediaTypeChange,
            modifier = Modifier.fillMaxWidth(0.75f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Dynamic Content
        if (state.mediaType == MediaCapability.IMAGE || state.mediaType == MediaCapability.VIDEO) {
            SectionHeader(text = "General")
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Aspect ratio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "For text-to-video and text-to-image generations",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    state.capabilities?.let { caps ->
                        val currentSettings = state.settings
                        val currentRatio = when (currentSettings) {
                            is ImageGenerationSettings -> currentSettings.aspectRatio
                            is VideoGenerationSettings -> currentSettings.aspectRatio
                            is MusicGenerationSettings -> AspectRatio.ONE_ONE
                        }

                        if (caps.supportedAspectRatios.isNotEmpty()) {
                            AspectRatioSelector(
                                selected = currentRatio,
                                options = caps.supportedAspectRatios,
                                onSelected = { ratio ->
                                    val updated = when (currentSettings) {
                                        is ImageGenerationSettings -> currentSettings.copy(aspectRatio = ratio)
                                        is VideoGenerationSettings -> currentSettings.copy(aspectRatio = ratio)
                                        is MusicGenerationSettings -> currentSettings
                                    }
                                    onUpdateSettings(updated)
                                }
                            )
                        } else {
                            Text(
                                text = "No aspect ratios supported",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } ?: run {
                        Text(
                            text = "Loading capabilities...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            if (state.mediaType == MediaCapability.VIDEO) {
                SectionHeader(text = "Video")
                Spacer(modifier = Modifier.height(8.dp))
                
                state.capabilities?.let { caps ->
                    val videoSettings = state.settings as? VideoGenerationSettings ?: VideoGenerationSettings()
                    
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        if (caps.supportedVideoDurations.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SettingRow(label = "Duration") {
                                    PillSelector(
                                        selected = "${videoSettings.videoDuration}s",
                                        options = caps.supportedVideoDurations.map { "${it}s" },
                                        onSelected = { durationStr ->
                                            val duration = durationStr.dropLast(1).toIntOrNull() ?: videoSettings.videoDuration
                                            onUpdateSettings(videoSettings.copy(videoDuration = duration))
                                        }
                                    )
                                }
                            }
                        }

                        if (caps.supportedVideoDurations.isNotEmpty() && caps.supportedVideoResolutions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(5.dp))
                        }

                        if (caps.supportedVideoResolutions.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SettingRow(label = "Resolution") {
                                    PillSelector(
                                        selected = videoSettings.videoResolution,
                                        options = caps.supportedVideoResolutions,
                                        onSelected = { res ->
                                            onUpdateSettings(videoSettings.copy(videoResolution = res))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (state.mediaType == MediaCapability.IMAGE) {
                SectionHeader(text = "Image")
                Spacer(modifier = Modifier.height(8.dp))
                
                state.capabilities?.let { caps ->
                    val currentSettings = state.settings as? ImageGenerationSettings ?: ImageGenerationSettings()
                    val currentQuality = currentSettings.quality
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SettingRow(label = "Image Gen Mode") {
                            if (caps.supportedImageQualities.isNotEmpty()) {
                                PillSelector(
                                    selected = currentQuality.displayName,
                                    options = caps.supportedImageQualities.map { it.displayName },
                                    onSelected = { qualityDisplayName ->
                                        val quality = caps.supportedImageQualities.first { it.displayName == qualityDisplayName }
                                        onUpdateSettings(currentSettings.copy(quality = quality))
                                    }
                                )
                            } else {
                                Text("Default", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SettingRow(label = "Continual Gen") {
                            Switch(
                                checked = state.continualMode,
                                onCheckedChange = onContinualModeChange
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            SectionHeader(text = "Visual Style Presets")
            Spacer(modifier = Modifier.height(8.dp))
            StudioTemplateRow(
                templates = state.templates,
                selectedTemplateId = state.selectedTemplateId,
                onTemplateSelected = onTemplateSelected
            )
        } else if (state.mediaType == MediaCapability.MUSIC) {
            SectionHeader(text = "Music Configuration")
            Spacer(modifier = Modifier.height(16.dp))
            
            val musicSettings = state.settings as? MusicGenerationSettings ?: MusicGenerationSettings()
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Duration", style = MaterialTheme.typography.titleMedium)
                        Text("${musicSettings.duration}s", style = MaterialTheme.typography.bodyMedium)
                    }
                    Slider(
                        value = musicSettings.duration.toFloat(),
                        onValueChange = { onUpdateSettings(musicSettings.copy(duration = it.toInt())) },
                        valueRange = 5f..120f
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tempo", style = MaterialTheme.typography.titleMedium)
                        Text("${musicSettings.tempo} BPM", style = MaterialTheme.typography.bodyMedium)
                    }
                    Slider(
                        value = musicSettings.tempo.toFloat(),
                        onValueChange = { onUpdateSettings(musicSettings.copy(tempo = it.toInt())) },
                        valueRange = 60f..200f
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun AspectRatioSelector(
    selected: AspectRatio,
    options: List<AspectRatio>,
    onSelected: (AspectRatio) -> Unit
) {
    LazyRow(
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
                        .height(32.dp)
                        .width(32.dp * ratioValue.coerceAtLeast(0.5f).coerceAtMost(2f))
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

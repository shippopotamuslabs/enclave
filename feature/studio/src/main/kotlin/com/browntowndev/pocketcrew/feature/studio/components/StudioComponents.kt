package com.browntowndev.pocketcrew.feature.studio.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import coil3.compose.AsyncImage
import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationQuality
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.StudioTemplate
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Master Synced Toggle
            MediaModeToggle(
                selectedType = state.mediaType,
                onTypeChange = onMediaTypeChange
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            // Dynamic Content
            if (state.mediaType == MediaCapability.IMAGE || state.mediaType == MediaCapability.VIDEO) {
                Text(
                    text = "VISUAL STYLE PRESETS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                StudioTemplateRow(
                    templates = state.templates,
                    selectedTemplateId = state.selectedTemplateId,
                    onTemplateSelected = onTemplateSelected
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "DETAILED SETTINGS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                state.capabilities?.let { caps ->
                    val currentSettings = state.settings
                    val currentRatio = when (currentSettings) {
                        is ImageGenerationSettings -> currentSettings.aspectRatio
                        is VideoGenerationSettings -> currentSettings.aspectRatio
                        is MusicGenerationSettings -> AspectRatio.ONE_ONE
                    }
                    val currentQuality = when (currentSettings) {
                        is ImageGenerationSettings -> currentSettings.quality
                        is VideoGenerationSettings -> currentSettings.quality
                        is MusicGenerationSettings -> GenerationQuality.SPEED
                    }

                    Text("Aspect Ratio", style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(12.dp))
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

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Quality", style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(12.dp))
                    QualitySelector(
                        selected = currentQuality,
                        options = caps.supportedQualities,
                        onSelected = { quality ->
                            val updated = when (currentSettings) {
                                is ImageGenerationSettings -> currentSettings.copy(quality = quality)
                                is VideoGenerationSettings -> currentSettings.copy(quality = quality)
                                is MusicGenerationSettings -> currentSettings
                            }
                            onUpdateSettings(updated)
                        }
                    )
                }
            } else if (state.mediaType == MediaCapability.MUSIC) {
                Text(
                    text = "MUSIC STYLE PRESETS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                StudioTemplateRow(
                    templates = state.templates,
                    selectedTemplateId = state.selectedTemplateId,
                    onTemplateSelected = onTemplateSelected
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "MUSIC CONFIGURATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                val musicSettings = state.settings as? MusicGenerationSettings ?: MusicGenerationSettings()
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Duration", style = MaterialTheme.typography.labelLarge)
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
                        Text("Tempo", style = MaterialTheme.typography.labelLarge)
                        Text("${musicSettings.tempo} BPM", style = MaterialTheme.typography.bodyMedium)
                    }
                    Slider(
                        value = musicSettings.tempo.toFloat(),
                        onValueChange = { onUpdateSettings(musicSettings.copy(tempo = it.toInt())) },
                        valueRange = 60f..200f
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Continual Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Continual Generation", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Automatically start next generation when finished",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.continualMode,
                    onCheckedChange = onContinualModeChange
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun AspectRatioSelector(
    selected: AspectRatio,
    options: List<AspectRatio>,
    onSelected: (AspectRatio) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { ratio ->
            FilterChip(
                selected = selected == ratio,
                onClick = { onSelected(ratio) },
                label = { Text(ratio.name.replace("_", " ")) },
                leadingIcon = {
                    val icon = when (ratio) {
                        AspectRatio.ONE_ONE -> Icons.Default.Square
                        AspectRatio.SIXTEEN_NINE -> Icons.Default.Rectangle
                        else -> Icons.Default.Crop
                    }
                    Icon(icon, null, modifier = Modifier.size(18.dp))
                }
            )
        }
    }
}

@Composable
fun QualitySelector(
    selected: GenerationQuality,
    options: List<GenerationQuality>,
    onSelected: (GenerationQuality) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { quality ->
            FilterChip(
                selected = selected == quality,
                onClick = { onSelected(quality) },
                label = { Text(quality.name) }
            )
        }
    }
}

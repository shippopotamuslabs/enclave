package com.browntowndev.pocketcrew.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability

@Composable
fun MediaModeToggle(
    selectedType: MediaCapability,
    onTypeChange: (MediaCapability) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        ToggleItem("Image", Icons.Default.Image, MediaCapability.IMAGE),
        ToggleItem("Video", Icons.Default.Movie, MediaCapability.VIDEO),
        ToggleItem("Music", Icons.Default.MusicNote, MediaCapability.MUSIC)
    )

    val itemWidth = 80.dp
    val padding = 4.dp
    val gap = 4.dp
    
    val selectedIndex = when (selectedType) {
        MediaCapability.IMAGE -> 0
        MediaCapability.VIDEO -> 1
        MediaCapability.MUSIC -> 2
    }
    
    // Indicator offset animation
    val indicatorOffset by animateDpAsState(
        targetValue = (itemWidth + gap) * selectedIndex,
        animationSpec = tween(durationMillis = 300),
        label = "indicatorOffset"
    )

    Box(
        modifier = modifier
            .height(40.dp)
            .width((itemWidth * items.size) + (gap * (items.size - 1)) + (padding * 2))
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(padding)
    ) {
        // Sliding Indicator
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(itemWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "contentColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTypeChange(item.capability) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = contentColor
                        )
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}

private data class ToggleItem(
    val label: String,
    val icon: ImageVector,
    val capability: MediaCapability
)

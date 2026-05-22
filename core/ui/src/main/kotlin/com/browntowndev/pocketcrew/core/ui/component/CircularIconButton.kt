package com.browntowndev.pocketcrew.core.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * [IconButton] whose touch target and ripple are clipped to a circle.
 *
 * Material's default [IconButton] uses a square minimum touch target; on press the
 * ripple can read as a square even when the icon looks round. Use this for round
 * actions in input bars and similar compact toolbars.
 */
@Composable
fun CircularIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = CircleShape,
        modifier =
            modifier
                .size(size)
                .clip(CircleShape),
        colors = IconButtonDefaults.iconButtonColors(),
        content = {
            Box(contentAlignment = Alignment.Center, content = content)
        },
    )
}

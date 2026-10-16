package com.browntowndev.enclave.feature.chat.components.messagelist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveIconButton

/**
 * Neo-brutalist scroll-to-bottom control that fades in when the user has scrolled
 * above the bottom, and triggers a smooth scroll to the last item when tapped.
 */
@Composable
internal fun ScrollToBottomFab(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "scrollToBottomAlpha",
    )

    if (alpha > 0f || visible) {
        EnclaveIconButton(
            onClick = onClick,
            icon = Icons.Default.KeyboardArrowDown,
            contentDescription = "Scroll to bottom",
            modifier = modifier.graphicsLayer { this.alpha = alpha },
        )
    }
}

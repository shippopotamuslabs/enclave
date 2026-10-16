package com.browntowndev.enclave.core.ui.component.enclave

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

private val LocalShowDisabledTooltip = compositionLocalOf<(() -> Unit)?> { null }

/**
 * Wraps [content] with a persistent plain tooltip when disabled and [disabledReason] is non-blank.
 * Use [rememberNeoDisabledClick] inside [content] so taps on disabled controls show the tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnclaveDisabledActionTooltip(
    enabled: Boolean,
    disabledReason: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val tooltipState = rememberTooltipState(isPersistent = true)
    val showTooltip: () -> Unit = {
        scope.launch { tooltipState.show() }
    }

    val wrappedContent: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalShowDisabledTooltip provides
                if (!enabled && !disabledReason.isNullOrBlank()) showTooltip else null,
        ) {
            content()
        }
    }

    if (!enabled && !disabledReason.isNullOrBlank()) {
        TooltipBox(
            modifier = modifier,
            positionProvider =
                TooltipDefaults.rememberTooltipPositionProvider(
                    positioning = TooltipAnchorPosition.Above,
                ),
            tooltip = {
                PlainTooltip {
                    Text(disabledReason)
                }
            },
            state = tooltipState,
            content = wrappedContent,
        )
    } else {
        Box(modifier = modifier) {
            wrappedContent()
        }
    }
}

/**
 * Click handler for children of [EnclaveDisabledActionTooltip]: runs [onEnabledClick] when [enabled],
 * otherwise shows the disabled tooltip when one is configured.
 */
@Composable
fun rememberNeoDisabledClick(
    enabled: Boolean,
    onEnabledClick: () -> Unit,
): () -> Unit {
    val showTooltip = LocalShowDisabledTooltip.current
    return {
        if (enabled) {
            onEnabledClick()
        } else {
            showTooltip?.invoke()
        }
    }
}

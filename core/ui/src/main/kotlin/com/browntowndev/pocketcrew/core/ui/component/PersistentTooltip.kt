package com.browntowndev.pocketcrew.core.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A reusable tooltip that remains visible on tap until the user taps somewhere else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersistentTooltip(
    description: String,
    modifier: Modifier = Modifier,
    iconSize: Int = 16,
    iconButtonSize: Int = 20
) {
    val scope = rememberCoroutineScope()
    val tooltipState = rememberTooltipState(isPersistent = true)

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            positioning = TooltipAnchorPosition.Above
        ),
        tooltip = {
            PlainTooltip {
                Text(description)
            }
        },
        state = tooltipState,
        modifier = modifier
    ) {
        IconButton(
            onClick = {
                scope.launch {
                    tooltipState.show()
                }
            },
            modifier = Modifier.size(iconButtonSize.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Show description",
                modifier = Modifier.size(iconSize.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

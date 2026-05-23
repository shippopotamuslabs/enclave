package com.browntowndev.enclave.core.ui.component.neo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens

@Composable
fun NeoSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = neoBorderColor()
    Row(
        modifier =
            modifier
                .height(40.dp)
                .neoSurface(
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    borderColor = borderColor,
                    withShadow = false,
                ),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (selected) {
                                Modifier.neoSurface(
                                    backgroundColor = MaterialTheme.colorScheme.primary,
                                    borderColor = borderColor,
                                    withShadow = true,
                                    shadowColor = neoShadowColor(),
                                    shadowOffset = neoButtonShadowOffset(),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onOptionSelected(index) },
                        )
                        .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

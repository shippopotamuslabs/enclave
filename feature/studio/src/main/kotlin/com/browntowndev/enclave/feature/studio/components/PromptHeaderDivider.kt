package com.browntowndev.enclave.feature.studio.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSecondaryIconButton
import com.browntowndev.enclave.core.ui.component.enclave.enclaveBorderColor
import com.browntowndev.enclave.core.ui.component.enclave.enclaveGlassEffect
import com.browntowndev.enclave.core.ui.component.enclave.enclaveSurface
import com.browntowndev.enclave.core.ui.component.enclave.enclaveSurfaceEdges
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveThemeAccessor
import dev.chrisbanes.haze.HazeState

private val PromptHeaderActionSize = EnclaveNeoTokens.iconTileSize
private val PromptHeaderVerticalPadding = 10.dp

@Composable
fun PromptHeaderDivider(
    prompt: String,
    isExpanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    omitTopBorder: Boolean = false,
    showSectionDivider: Boolean = false,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    var localIsExpanded by rememberSaveable { mutableStateOf(false) }
    val expanded = isExpanded ?: localIsExpanded
    val updateExpanded = onExpandedChange ?: { value -> localIsExpanded = value }
    val clipboardManager = LocalClipboardManager.current
    val borderColor = enclaveBorderColor()
    val glassEnabled = EnclaveThemeAccessor.glass.enabled && hazeState != null
    val headerBackground =
        when {
            glassEnabled -> Color.Transparent
            omitTopBorder -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surfaceVariant
        }

    Box(
        modifier =
            modifier
                .zIndex(1f)
                .fillMaxWidth()
                .then(if (glassEnabled) Modifier.enclaveGlassEffect(hazeState) else Modifier)
                .then(
                    when {
                        omitTopBorder -> {
                            Modifier.enclaveSurfaceEdges(
                                backgroundColor = headerBackground,
                                borderColor = borderColor,
                                shape = EnclaveNeoTokens.flushTopShape,
                                drawTop = false,
                                drawBottom = true,
                                drawStart = true,
                                drawEnd = true,
                            )
                        }
                        showSectionDivider -> {
                            Modifier.enclaveSurfaceEdges(
                                backgroundColor = headerBackground,
                                borderColor = borderColor,
                                drawBottom = false,
                                drawStart = false,
                                drawEnd = false,
                            )
                        }
                        else -> {
                            Modifier.enclaveSurface(
                                backgroundColor = headerBackground,
                                borderColor = borderColor,
                                withShadow = false,
                            )
                        }
                    },
                ).animateContentSize()
                .then(if (!expanded) Modifier.clickable { updateExpanded(true) } else Modifier),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = EnclaveNeoTokens.screenHorizontalPadding,
                        vertical = PromptHeaderVerticalPadding,
                    ),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .then(
                            if (expanded) {
                                Modifier
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState())
                            } else {
                                Modifier
                            },
                        ),
            ) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            EnclaveSecondaryIconButton(
                onClick = { clipboardManager.setText(AnnotatedString(prompt)) },
                icon = Icons.Default.ContentCopy,
                contentDescription = "Copy prompt",
                size = PromptHeaderActionSize,
            )

            EnclaveSecondaryIconButton(
                onClick = { updateExpanded(!expanded) },
                icon = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                size = PromptHeaderActionSize,
                iconTint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

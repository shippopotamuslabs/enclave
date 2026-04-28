package com.browntowndev.pocketcrew.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.clickable

/**
 * A universal container for input bars (Chat, Studio, etc.).
 * Handles the shared "premium" layout, including the bleed-to-bottom background,
 * navigation bar insets, and slot-based vertical structure.
 */
@Composable
fun UniversalInputBar(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    attachmentContent: (@Composable ColumnScope.() -> Unit)? = null,
    inputContent: @Composable ColumnScope.() -> Unit,
    actionContent: (@Composable RowScope.() -> Unit)? = null,
    trailingAction: @Composable (RowScope.() -> Unit)? = null,
    isExpanded: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = shape
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(backgroundColor)
                .navigationBarsPadding()
                .then(if (isExpanded) Modifier.fillMaxHeight(0.9f) else Modifier.heightIn(min = 56.dp))
                .padding(bottom = 12.dp, top = 12.dp, start = 16.dp, end = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = if (isExpanded) Arrangement.SpaceBetween else Arrangement.spacedBy(6.dp)
            ) {
                if (attachmentContent != null) {
                    attachmentContent()
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        inputContent()
                    }

                    if (trailingAction != null) {
                        Spacer(Modifier.width(48.dp))
                    }
                }

                var lastActionContent by remember { mutableStateOf(actionContent) }
                if (actionContent != null) {
                    lastActionContent = actionContent
                }

                AnimatedVisibility(
                    visible = actionContent != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    lastActionContent?.let { content ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            content()

                            if (trailingAction != null) {
                                Spacer(Modifier.width(48.dp))
                            }
                        }
                    }
                }
            }

            if (trailingAction != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .heightIn(min = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row {
                        trailingAction()
                    }
                }
            }
        }
    }
}

/**
 * A standardized circular action button for use in [UniversalInputBar]'s trailingAction slot.
 */
@Composable
fun StandardTrailingAction(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    disabledContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    disabledContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
    shape: Shape = CircleShape,
    description: String? = null,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .background(if (enabled) containerColor else disabledContainerColor, shape = shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) contentColor else disabledContentColor
        )
    }
}

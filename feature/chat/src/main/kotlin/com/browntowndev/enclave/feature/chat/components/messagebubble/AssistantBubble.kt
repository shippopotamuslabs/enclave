package com.browntowndev.enclave.feature.chat.components.messagebubble

import android.content.ClipData
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
internal fun ExpandableTruncatableMessageText(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    var isExpanded by remember(text) { mutableStateOf(false) }
    var isTruncatable by remember(text) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "messageBubbleChevronRotation",
    )
    val bodyStyle = MaterialTheme.typography.bodyLarge

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                ),
    ) {
        Text(
            text = text,
            style = bodyStyle,
            color = color,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(end = if (isTruncatable) 40.dp else 0.dp),
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout ->
                if (!isExpanded) {
                    val truncated = layout.didOverflowHeight
                    if (truncated != isTruncatable) {
                        isTruncatable = truncated
                    }
                }
            },
        )
        if (isTruncatable) {
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(40.dp)
                        .zIndex(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription =
                        if (isExpanded) {
                            "Collapse message"
                        } else {
                            "Expand message"
                        },
                    modifier = Modifier.rotate(chevronRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal const val PLAIN_TEXT_CLIP_LABEL = "Enclave Response"

internal fun plainTextClipData(content: String): ClipData =
    ClipData.newPlainText(PLAIN_TEXT_CLIP_LABEL, content)

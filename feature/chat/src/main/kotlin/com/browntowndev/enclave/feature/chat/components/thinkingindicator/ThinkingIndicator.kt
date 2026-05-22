package com.browntowndev.enclave.feature.chat.components.thinkingindicator

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.enclave.core.ui.component.ShimmerText
import com.browntowndev.enclave.feature.chat.components.DynamicThinkingAnimation

@Composable
fun ThinkingIndicator(
    modifier: Modifier = Modifier,
    elapsedSeconds: Int = 0,
    isExpanded: Boolean = false,
    onToggleDetails: () -> Unit = {},
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "chevronRotation",
    )

    Column(
        modifier =
            modifier
                .padding(top = 8.dp, bottom = 8.dp)
                .fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .offset(x = (-11).dp)
                    .clickable { onToggleDetails() },
        ) {
            DynamicThinkingAnimation(
                modifier = Modifier.size(46.dp),
            )

            Spacer(modifier = Modifier.width(5.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                val thinkingText =
                    if (elapsedSeconds > 0) {
                        "Thinking... • ${formatElapsedTime(elapsedSeconds)}"
                    } else {
                        "Thinking..."
                    }

                val grayColor = MaterialTheme.colorScheme.onSurfaceVariant
                val highlightColor = grayColor.copy(alpha = 0.3f)

                ShimmerText(
                    text = thinkingText,
                    baseColor = grayColor,
                    highlightColor = highlightColor,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        ),
                )

                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Expand thinking details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .size(24.dp)
                            .rotate(rotation),
                )
            }
        }
    }
}

private fun formatElapsedTime(seconds: Int): String =
    when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }

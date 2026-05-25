package com.browntowndev.enclave.feature.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.enclave.core.ui.component.ShimmerText
import com.browntowndev.enclave.feature.chat.ToolCallBannerUi

/**
 * Processing indicator shown during PROCESSING state (generating text without thinking).
 * Shows a gray shimmer "Processing..." text with animated orb.
 */
@Composable
fun ProcessingIndicator(
    toolCallBanner: ToolCallBannerUi? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .offset(x = (-11).dp)
                .padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DynamicThinkingAnimation(
            modifier = Modifier.size(46.dp),
        )

        Spacer(modifier = Modifier.width(5.dp))

        AnimatedContent(
            targetState = toolCallBanner,
            modifier = Modifier.weight(1f),
            label = "processingIndicatorStatus",
        ) { banner ->
            if (banner != null) {
                ToolCallBannerContent(banner = banner)
            } else {
                ProcessingIndicatorLabel()
            }
        }
    }
}

@Composable
private fun ProcessingIndicatorLabel() {
    val grayColor = MaterialTheme.colorScheme.onSurfaceVariant
    val highlightColor = grayColor.copy(alpha = 0.3f)

    ShimmerText(
        text = "Processing",
        baseColor = grayColor,
        highlightColor = highlightColor,
        style =
            MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            ),
    )
}

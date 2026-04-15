package com.browntowndev.pocketcrew.feature.chat.components

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
import com.browntowndev.pocketcrew.core.ui.component.ShimmerText

/**
 * Engine loading indicator shown during ENGINE_LOADING state.
 * Shows a gray shimmer "Loading [model] engine..." text with animated orb.
 */
@Composable
fun EngineLoadingIndicator(
    modelDisplayName: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .offset(x = (-11).dp)
            .padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Dynamic orb animation (same as ThinkingIndicator)
        DynamicThinkingAnimation(
            modifier = Modifier.size(46.dp)
        )

        Spacer(modifier = Modifier.width(5.dp))

        val grayColor = MaterialTheme.colorScheme.onSurfaceVariant
        val highlightColor = grayColor.copy(alpha = 0.3f)

        ShimmerText(
            text = "Loading $modelDisplayName engine...",
            baseColor = grayColor,
            highlightColor = highlightColor,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            ),
        )
    }
}

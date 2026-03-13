package com.browntowndev.pocketcrew.presentation.screen.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Generating indicator for Crew mode non-final steps.
 * Shows a shimmering "Generating..." text with the animated orb,
 * styled to match the Thinking indicator.
 */
@Composable
fun GeneratingIndicator(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        // Header Row: Orb + "Generating" text
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Dynamic molten-lava orb (same as Thinking)
            DynamicThinkingAnimation(
                modifier = Modifier.size(46.dp)
            )

            Spacer(modifier = Modifier.width(5.dp))

            // "Generating" text with shimmer
            val grayColor = MaterialTheme.colorScheme.onSurfaceVariant
            val highlightColor = grayColor.copy(alpha = 0.3f)

            ShimmerText(
                text = "Generating...",
                baseColor = grayColor,
                highlightColor = highlightColor,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
            )
        }
    }
}

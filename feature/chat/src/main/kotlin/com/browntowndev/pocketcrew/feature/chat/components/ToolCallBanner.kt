package com.browntowndev.pocketcrew.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.pocketcrew.feature.chat.R
import com.browntowndev.pocketcrew.feature.chat.ToolCallBannerKind
import com.browntowndev.pocketcrew.feature.chat.ToolCallBannerUi

@Composable
fun ToolCallBanner(
    banner: ToolCallBannerUi?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        AnimatedVisibility(
            visible = banner != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        ) {
            if (banner != null) {
                BannerContent(banner)
            }
        }
    }
}

@Composable
private fun BannerContent(banner: ToolCallBannerUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon Box
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(
                    id = when (banner.kind) {
                        ToolCallBannerKind.SEARCH -> R.drawable.search
                        ToolCallBannerKind.EXTRACT -> R.drawable.pageview
                        ToolCallBannerKind.IMAGE -> R.drawable.image
                        ToolCallBannerKind.MEMORY -> R.drawable.search
                        ToolCallBannerKind.ARTIFACT -> R.drawable.document
                    }
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Label
        Text(
            text = banner.label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

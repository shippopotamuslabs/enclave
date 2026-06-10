package com.browntowndev.enclave.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.enclave.core.ui.component.XIcon
import com.browntowndev.enclave.feature.chat.R
import com.browntowndev.enclave.feature.chat.ToolCallBannerKind
import com.browntowndev.enclave.feature.chat.ToolCallBannerUi

@Composable
fun ToolCallBanner(
    banner: ToolCallBannerUi?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth(),
    ) {
        AnimatedVisibility(
            visible = banner != null,
            enter = fadeIn(),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        ) {
            if (banner != null) {
                ToolCallBannerContent(banner)
            }
        }
    }
}

@Composable
internal fun ToolCallBannerContent(
    banner: ToolCallBannerUi,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (banner.kind == ToolCallBannerKind.X_SEARCH) {
                Icon(
                    imageVector = XIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(
                    painter =
                        painterResource(
                            id =
                                when (banner.kind) {
                                    ToolCallBannerKind.SEARCH -> R.drawable.search
                                    ToolCallBannerKind.EXTRACT -> R.drawable.pageview
                                    ToolCallBannerKind.IMAGE -> R.drawable.image
                                    ToolCallBannerKind.MEMORY -> R.drawable.search
                                    ToolCallBannerKind.ARTIFACT -> R.drawable.document
                                    ToolCallBannerKind.X_SEARCH -> 0 // Handled above
                                    ToolCallBannerKind.MEDIA -> R.drawable.image // Reuse image icon for media
                                    ToolCallBannerKind.GENERIC -> R.drawable.network_intelligence
                                },
                        ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = banner.label,
            modifier = Modifier.weight(1f, fill = false),
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

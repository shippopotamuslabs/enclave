package com.browntowndev.enclave.feature.chat.components.assistantresponse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.enclave.feature.chat.ArtifactCardUiModel
import com.browntowndev.enclave.feature.chat.IndicatorState
import com.browntowndev.enclave.feature.chat.R

@Composable
internal fun CompletedStepRow(
    stepName: String,
    stepOutput: String,
    modelDisplayName: String,
    indicatorState: IndicatorState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isStepActive =
        indicatorState is IndicatorState.Processing ||
            indicatorState is IndicatorState.EngineLoading ||
            indicatorState is IndicatorState.Thinking ||
            indicatorState is IndicatorState.Generating
    if (stepOutput.isEmpty() && !isStepActive) return

    val previewText = "Tap to View"

    val statusText =
        when (indicatorState) {
            is IndicatorState.EngineLoading -> "I'm loading the engine from disk..."
            is IndicatorState.Processing -> "I'm processing your request now..."
            is IndicatorState.Thinking -> "I'm thinking about the next step..."
            is IndicatorState.Generating -> "I understand the request and I'm generating my response now."
            is IndicatorState.Complete, IndicatorState.None, null -> "I have completed my task. Here are the results."
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
    ) {
        val completionMessage =
            remember(modelDisplayName, statusText) {
                buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(modelDisplayName)
                    }
                    append(": $statusText")
                }
            }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
                    .padding(vertical = 4.dp),
        ) {
            Text(
                text = completionMessage,
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                    .clickable { onClick() }
                    .padding(horizontal = 12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .offset(y = 12.dp)
                        .padding(start = 12.dp)
                        .rotate(-10f)
                        .size(width = 60.dp, height = 90.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.document),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "$stepName Results",
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 23.sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = previewText,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
internal fun ArtifactCard(
    card: ArtifactCardUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .width(280.dp)
                .height(110.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(width = 56.dp, height = 76.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.document),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = card.badgeLabel,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp,
                                ),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = card.title,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = card.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = card.ctaLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun MediaCard(
    uris: List<String>,
    mediaTypes: List<String>,
    prompts: List<String>,
    onClick: (uris: List<String>, types: List<String>, prompts: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var loadError by remember { mutableStateOf(false) }
    val primaryUri = uris.firstOrNull() ?: ""
    val primaryType = mediaTypes.firstOrNull()
    val count = uris.size

    Surface(
        onClick = { if (!loadError) onClick(uris, mediaTypes, prompts) },
        modifier =
            modifier
                .fillMaxWidth()
                .height(110.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(width = 80.dp, height = 80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (loadError) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Deleted",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp),
                    )
                } else {
                    coil3.compose.AsyncImage(
                        model = primaryUri,
                        contentDescription = "Generated media",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        onError = { loadError = true },
                        error = painterResource(R.drawable.enclave_icon),
                    )

                    if (count > 1) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        androidx.compose.ui.graphics.Color.Black
                                            .copy(alpha = 0.4f),
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "+${count - 1}",
                                style =
                                    MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = androidx.compose.ui.graphics.Color.White,
                                    ),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                val title =
                    if (loadError) {
                        "Media Missing"
                    } else {
                        val typeLabel =
                            when (primaryType) {
                                "VIDEO" -> "Video"
                                "MUSIC" -> "Audio"
                                else -> "Image"
                            }
                        if (count > 1) "$count ${typeLabel}s Generated" else "Generated $typeLabel"
                    }
                Text(
                    text = title,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.sp,
                        ),
                    color = if (loadError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (loadError) "Deleted from storage" else "Available in Studio",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )

                Spacer(modifier = Modifier.weight(1f))

                if (!loadError) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Open in Studio",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.enclave.core.ui.component.enclave.enclaveBorderColor
import com.browntowndev.enclave.core.ui.component.enclave.enclaveShadowColor
import com.browntowndev.enclave.core.ui.component.enclave.enclaveSurface
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.feature.chat.ArtifactCardUiModel
import com.browntowndev.enclave.feature.chat.IndicatorState
import com.browntowndev.enclave.feature.chat.R

private val CouncilStepDocPreviewWidth = 52.dp
private val CouncilStepDocPreviewHeight = 68.dp

@Composable
internal fun CompletedStepRow(
    stepName: String,
    stepOutput: String,
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

    val borderColor = enclaveBorderColor()
    val shadowColor = enclaveShadowColor()
    val shadowOffset = EnclaveNeoTokens.buttonShadowOffsetDark

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    top = EnclaveNeoTokens.rowSpacing,
                    bottom = EnclaveNeoTokens.rowSpacing + shadowOffset,
                ).enclaveSurface(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    borderColor = borderColor,
                    withShadow = true,
                    shadowColor = shadowColor,
                    shadowOffset = shadowOffset,
                    shape = EnclaveNeoTokens.shape,
                ).clickable(onClick = onClick)
                .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .offset(y = 4.dp)
                        .rotate(-8f)
                        .size(width = CouncilStepDocPreviewWidth, height = CouncilStepDocPreviewHeight)
                        .enclaveSurface(
                            backgroundColor = MaterialTheme.colorScheme.background,
                            borderColor = borderColor,
                            withShadow = true,
                            shadowColor = shadowColor,
                            shadowOffset = EnclaveNeoTokens.buttonShadowOffsetLight,
                            shape = EnclaveNeoTokens.shape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.document),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$stepName Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, backgroundColor = 0xFF18181B, name = "Council step result card")
@Composable
private fun PreviewCompletedStepRow() {
    EnclaveTheme(darkTheme = true) {
        CompletedStepRow(
            stepName = "Draft 1",
            stepOutput = "Sample council step output for preview.",
            indicatorState = IndicatorState.Complete(thinkingData = null),
            onClick = {},
            modifier = Modifier.padding(EnclaveNeoTokens.screenHorizontalPadding),
        )
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

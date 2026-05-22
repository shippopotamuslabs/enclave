package com.browntowndev.enclave.feature.studio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.FullscreenMediaViewer
import com.browntowndev.enclave.core.ui.component.sheet.CustomAnimationPromptPane
import com.browntowndev.enclave.domain.model.config.MediaCapability
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

private const val ANIMATE_DISABLED_TOOLTIP = "Setup a video model in Settings to enable."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryDetailScreen(
    albumId: String,
    assetId: String,
    albums: List<GalleryAlbumUi>,
    onNavigateBack: () -> Unit,
    onShareMedia: (String) -> Unit,
    onSendToStudio: (String, String, String?) -> Unit,
    onDeleteMedia: (String) -> Unit,
    hasVideoModel: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val album =
        remember(albums, albumId) {
            albums.find { it.id == albumId }
        }
    val assets = album?.items ?: emptyList()
    val initialIndex =
        remember(assets, assetId) {
            assets.indexOfFirst { it.id == assetId }.coerceAtLeast(0)
        }
    val hazeState = rememberHazeState()
    var showCustomPromptInput by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        navigationIconContentColor = Color.White,
                    ),
            )
        },
    ) { padding ->
        if (assets.isNotEmpty()) {
            val pagerState =
                rememberPagerState(
                    initialPage = initialIndex,
                    pageCount = { assets.size },
                )

            BackHandler(enabled = showCustomPromptInput) {
                showCustomPromptInput = false
            }

            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                HorizontalPager(
                    state = pagerState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .hazeSource(hazeState),
                    beyondViewportPageCount = 1,
                ) { page ->
                    val asset = assets[page]
                    Box(modifier = Modifier.fillMaxSize()) {
                        FullscreenMediaViewer(
                            localUri = asset.bestUri?.toString() ?: "",
                            mediaType = asset.mediaType,
                            contentDescription = asset.prompt,
                            modifier = Modifier.fillMaxSize(),
                        )

                        GalleryDetailActionsOverlay(
                            asset = asset,
                            hasVideoModel = hasVideoModel,
                            onShare = { onShareMedia(asset.id) },
                            onSendToStudio = { mode ->
                                if (mode == "animate") {
                                    showCustomPromptInput = true
                                } else {
                                    onSendToStudio(asset.id, mode, null)
                                }
                            },
                            onDelete = { onDeleteMedia(asset.id) },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showCustomPromptInput,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    val asset = assets[pagerState.currentPage]
                    CustomAnimationPromptPane(
                        onSend = { prompt ->
                            onSendToStudio(asset.id, "animate", prompt)
                            showCustomPromptInput = false
                        },
                        onDismiss = { showCustomPromptInput = false },
                        hazeState = hazeState,
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Asset not found", color = Color.White)
            }
        }
    }
}

@Composable
private fun GalleryDetailActionsOverlay(
    asset: StudioMediaUi,
    hasVideoModel: Boolean,
    onShare: () -> Unit,
    onSendToStudio: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                    ),
                ).padding(24.dp),
    ) {
        Text(
            text = asset.prompt,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = 3,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionItem(Icons.Default.Edit, "Edit", onClick = { onSendToStudio("edit") })
            if (asset.mediaType == MediaCapability.IMAGE) {
                ActionItem(
                    icon = Icons.Default.Movie,
                    label = "Animate",
                    onClick = { onSendToStudio("animate") },
                    enabled = hasVideoModel,
                    disabledTooltip = if (!hasVideoModel) ANIMATE_DISABLED_TOOLTIP else null,
                )
                ActionItem(
                    icon = Icons.Default.FastForward,
                    label = "Quick Animate",
                    onClick = { onSendToStudio("quick_animate") },
                    enabled = hasVideoModel,
                    disabledTooltip = if (!hasVideoModel) ANIMATE_DISABLED_TOOLTIP else null,
                )
            }
            ActionItem(Icons.Default.Share, "Share", onClick = onShare)
            ActionItem(Icons.Default.Delete, "Delete", onClick = onDelete, isDestructive = true)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    disabledTooltip: String? = null,
    modifier: Modifier = Modifier,
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            if (!enabled && disabledTooltip != null) {
                PlainTooltip { Text(disabledTooltip) }
            }
        },
        state = tooltipState,
        enableUserInput = false,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                modifier
                    .alpha(if (enabled) 1f else 0.38f)
                    .clickable {
                        if (enabled) {
                            onClick()
                        } else if (disabledTooltip != null) {
                            scope.launch { tooltipState.show() }
                        }
                    },
        ) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .background(
                            if (isDestructive) Color.Red.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f),
                            CircleShape,
                        ).border(
                            1.dp,
                            if (isDestructive) Color.Red.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.2f),
                            CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isDestructive) Color.Red else Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isDestructive) Color.Red else Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

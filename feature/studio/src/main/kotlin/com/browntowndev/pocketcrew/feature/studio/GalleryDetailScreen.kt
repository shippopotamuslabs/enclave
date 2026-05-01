package com.browntowndev.pocketcrew.feature.studio

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
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.component.FullscreenMediaViewer
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryDetailScreen(
    albumId: String,
    assetId: String,
    albums: List<GalleryAlbumUi>,
    onNavigateBack: () -> Unit,
    onShareMedia: (String) -> Unit,
    onSendToStudio: (String, String) -> Unit,
    onDeleteMedia: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val album = remember(albums, albumId) {
        albums.find { it.id == albumId }
    }
    val assets = album?.items ?: emptyList()
    val initialIndex = remember(assets, assetId) {
        assets.indexOfFirst { it.id == assetId }.coerceAtLeast(0)
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (assets.isNotEmpty()) {
            val pagerState = rememberPagerState(
                initialPage = initialIndex,
                pageCount = { assets.size }
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                beyondViewportPageCount = 1
            ) { page ->
                val asset = assets[page]
                Box(modifier = Modifier.fillMaxSize()) {
                    FullscreenMediaViewer(
                        localUri = asset.localUri,
                        mediaType = asset.mediaType,
                        contentDescription = asset.prompt,
                        modifier = Modifier.fillMaxSize(),
                    )

                    GalleryDetailActionsOverlay(
                        asset = asset,
                        onShare = { onShareMedia(asset.id) },
                        onSendToStudio = { mode -> onSendToStudio(asset.id, mode) },
                        onDelete = { onDeleteMedia(asset.id) },
                        modifier = Modifier.align(Alignment.BottomCenter)
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
    onShare: () -> Unit,
    onSendToStudio: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                )
            )
            .padding(24.dp)
    ) {
        Text(
            text = asset.prompt,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = 3,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionItem(Icons.Default.Edit, "Edit", onClick = { onSendToStudio("edit") })
            if (asset.mediaType == MediaCapability.IMAGE) {
                ActionItem(Icons.Default.Movie, "Animate", onClick = { onSendToStudio("animate") })
            }
            ActionItem(Icons.Default.Share, "Share", onClick = onShare)
            ActionItem(Icons.Default.Delete, "Delete", onClick = onDelete, isDestructive = true)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    if (isDestructive) Color.Red.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f),
                    CircleShape
                )
                .border(
                    1.dp,
                    if (isDestructive) Color.Red.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.2f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isDestructive) Color.Red else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isDestructive) Color.Red else Color.White.copy(alpha = 0.8f)
        )
    }
}

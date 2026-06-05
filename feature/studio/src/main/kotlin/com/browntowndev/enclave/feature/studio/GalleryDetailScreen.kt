package com.browntowndev.enclave.feature.studio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.browntowndev.enclave.core.ui.component.FULLSCREEN_MEDIA_ANIMATE_DISABLED_TOOLTIP
import com.browntowndev.enclave.core.ui.component.FullscreenMediaAction
import com.browntowndev.enclave.core.ui.component.FullscreenMediaActionPlacement
import com.browntowndev.enclave.core.ui.component.FullscreenMediaActionsOverlay
import com.browntowndev.enclave.core.ui.component.FullscreenMediaDetailScaffold
import com.browntowndev.enclave.core.ui.component.FullscreenMediaPagerContent
import com.browntowndev.enclave.core.ui.component.FullscreenMediaViewer
import com.browntowndev.enclave.core.ui.component.sheet.CustomAnimationPromptPane
import com.browntowndev.enclave.domain.model.config.MediaCapability

@Composable
fun GalleryDetailScreen(
    albumId: String,
    assetId: String,
    albums: List<GalleryAlbumUi>,
    onNavigateBack: () -> Unit,
    onShareMedia: (String) -> Unit,
    onSendToStudio: (String, String, String?) -> Unit,
    onDeleteMedia: (String) -> Unit,
    onShowSnackbar: (String, String?) -> Unit,
    hasImageToVideoModel: Boolean = false,
    saveViewModel: FullscreenMediaSaveViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(saveViewModel) {
        saveViewModel.saveFeedback.collect { message ->
            onShowSnackbar(message, null)
        }
    }
    val album =
        remember(albums, albumId) {
            albums.find { it.id == albumId }
        }
    val assets = album?.items ?: emptyList()
    val initialIndex =
        remember(assets, assetId) {
            assets.indexOfFirst { it.id == assetId }.coerceAtLeast(0)
        }
    var showCustomPromptInput by remember { mutableStateOf(false) }

    FullscreenMediaDetailScaffold(
        modifier = modifier,
        onClose = onNavigateBack,
        hasContent = assets.isNotEmpty(),
    ) { padding ->
        val pagerState =
            rememberPagerState(
                initialPage = initialIndex,
                pageCount = { assets.size },
            )

        BackHandler(enabled = showCustomPromptInput) {
            showCustomPromptInput = false
        }

        FullscreenMediaPagerContent(
            pagerState = pagerState,
            modifier = Modifier.padding(padding),
            pageContent = { page ->
                val asset = assets[page]
                FullscreenMediaViewer(
                    localUri = asset.bestUri?.toString() ?: "",
                    mediaType = asset.mediaType,
                    contentDescription = asset.prompt,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            pageActionsOverlay = { page, overlayModifier ->
                val asset = assets[page]
                FullscreenMediaActionsOverlay(
                    prompt = asset.prompt,
                    actions =
                        galleryDetailActions(
                            asset = asset,
                            hasImageToVideoModel = hasImageToVideoModel,
                            onEdit = { onSendToStudio(asset.id, "edit", null) },
                            onAnimate = { showCustomPromptInput = true },
                            onQuickAnimate = { onSendToStudio(asset.id, "quick_animate", null) },
                            onSave = { saveViewModel.saveToGallery(asset) },
                            onShare = { onShareMedia(asset.id) },
                            onDelete = { onDeleteMedia(asset.id) },
                        ),
                    modifier = overlayModifier,
                )
            },
            floatingBottomContent = {
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
                    )
                }
            },
        )
    }
}

private fun galleryDetailActions(
    asset: StudioMediaUi,
    hasImageToVideoModel: Boolean,
    onEdit: () -> Unit,
    onAnimate: () -> Unit,
    onQuickAnimate: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
): List<FullscreenMediaAction> =
    buildList {
        add(
            FullscreenMediaAction(
                icon = Icons.Default.Share,
                label = "Share",
                onClick = onShare,
                placement = FullscreenMediaActionPlacement.PrimaryBar,
            ),
        )
        add(fullscreenSaveAction(onSave))
        add(
            FullscreenMediaAction(
                icon = Icons.Default.Edit,
                label = "Edit",
                onClick = onEdit,
                placement = FullscreenMediaActionPlacement.SheetOnly,
            ),
        )
        if (asset.mediaType == MediaCapability.IMAGE) {
            add(
                FullscreenMediaAction(
                    icon = Icons.Default.Movie,
                    label = "Animate",
                    onClick = onAnimate,
                    enabled = hasImageToVideoModel,
                    disabledTooltip =
                        if (!hasImageToVideoModel) FULLSCREEN_MEDIA_ANIMATE_DISABLED_TOOLTIP else null,
                    placement = FullscreenMediaActionPlacement.SheetOnly,
                ),
            )
            add(
                FullscreenMediaAction(
                    icon = Icons.Default.FastForward,
                    label = "Quick Animate",
                    onClick = onQuickAnimate,
                    enabled = hasImageToVideoModel,
                    disabledTooltip =
                        if (!hasImageToVideoModel) FULLSCREEN_MEDIA_ANIMATE_DISABLED_TOOLTIP else null,
                    placement = FullscreenMediaActionPlacement.SheetOnly,
                ),
            )
        }
        add(
            FullscreenMediaAction(
                icon = Icons.Default.Delete,
                label = "Delete",
                onClick = onDelete,
                isDestructive = true,
                placement = FullscreenMediaActionPlacement.SheetOnly,
            ),
        )
    }

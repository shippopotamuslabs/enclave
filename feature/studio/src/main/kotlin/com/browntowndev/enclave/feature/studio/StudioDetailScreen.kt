package com.browntowndev.enclave.feature.studio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.enclave.core.ui.component.FULLSCREEN_MEDIA_ANIMATE_DISABLED_TOOLTIP
import com.browntowndev.enclave.core.ui.component.FullscreenMediaAction
import com.browntowndev.enclave.core.ui.component.FullscreenMediaActionsOverlay
import com.browntowndev.enclave.core.ui.component.FullscreenMediaDetailScaffold
import com.browntowndev.enclave.core.ui.component.FullscreenMediaPagerContent
import com.browntowndev.enclave.core.ui.component.FullscreenMediaViewer
import com.browntowndev.enclave.core.ui.component.neo.NeoCircularProgressIndicator
import com.browntowndev.enclave.core.ui.component.sheet.CustomAnimationPromptPane
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.usecase.media.SaveMediaToGalleryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class StudioDetailUiState {
    data class Success(
        val assets: List<StudioMediaUi>,
        val initialIndex: Int,
    ) : StudioDetailUiState()
}

@HiltViewModel
class StudioDetailViewModel
    @Inject
    constructor(
        private val saveMediaToGalleryUseCase: SaveMediaToGalleryUseCase,
    ) : ViewModel() {
        fun saveToGallery(asset: StudioMediaUi) {
            viewModelScope.launch {
                saveMediaToGalleryUseCase(asset.localUri, asset.mediaType)
            }
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioDetailScreen(
    assetId: String,
    assets: List<StudioMediaUi>,
    onNavigateBack: () -> Unit,
    onEditMedia: (String) -> Unit,
    onAnimateMedia: (String, Boolean, String?) -> Unit,
    onShareMedia: (String) -> Unit,
    onDeleteMedia: (String) -> Unit,
    videoGenerationState: VideoGenerationState = VideoGenerationState.Idle,
    hasVideoModel: Boolean = false,
    viewModel: StudioDetailViewModel = hiltViewModel(),
) {
    val initialIndex =
        remember(assets, assetId) {
            assets.indexOfFirst { it.id == assetId }.coerceAtLeast(0)
        }
    val hazeState = rememberHazeState()

    FullscreenMediaDetailScaffold(
        onClose = onNavigateBack,
        hasContent = assets.isNotEmpty(),
    ) { padding ->
        DetailContent(
            assets = assets,
            initialIndex = initialIndex,
            videoGenerationState = videoGenerationState,
            hasVideoModel = hasVideoModel,
            hazeState = hazeState,
            onEdit = { asset ->
                onEditMedia(asset.id)
                onNavigateBack()
            },
            onAnimate = { asset, autoAnimate, customPrompt ->
                onAnimateMedia(asset.id, autoAnimate, customPrompt)
            },
            onSave = viewModel::saveToGallery,
            onShare = { asset ->
                onShareMedia(asset.id)
            },
            onDelete = { asset ->
                onDeleteMedia(asset.id)
                onNavigateBack()
            },
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun DetailContent(
    assets: List<StudioMediaUi>,
    initialIndex: Int,
    videoGenerationState: VideoGenerationState,
    hasVideoModel: Boolean,
    hazeState: HazeState,
    onEdit: (StudioMediaUi) -> Unit,
    onAnimate: (StudioMediaUi, Boolean, String?) -> Unit,
    onSave: (StudioMediaUi) -> Unit,
    onShare: (StudioMediaUi) -> Unit,
    onDelete: (StudioMediaUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState =
        rememberPagerState(
            initialPage = initialIndex,
            pageCount = { assets.size },
        )
    var showCustomPromptInput by remember { mutableStateOf(false) }

    LaunchedEffect(initialIndex) {
        if (initialIndex >= 0 && initialIndex < assets.size) {
            pagerState.scrollToPage(initialIndex)
        }
    }

    BackHandler(enabled = showCustomPromptInput) {
        showCustomPromptInput = false
    }

    FullscreenMediaPagerContent(
        pagerState = pagerState,
        modifier = modifier.fillMaxSize(),
        hazeState = hazeState,
        pageContent = { page ->
            DetailMedia(
                asset = assets[page],
                videoGenerationState = videoGenerationState,
            )
        },
        pageActionsOverlay = { page, overlayModifier ->
            val asset = assets[page]
            FullscreenMediaActionsOverlay(
                prompt = asset.prompt,
                actions =
                    studioDetailActions(
                        asset = asset,
                        hasVideoModel = hasVideoModel,
                        onEdit = { onEdit(asset) },
                        onAnimate = { autoAnimate ->
                            if (autoAnimate) {
                                onAnimate(asset, true, null)
                            } else {
                                showCustomPromptInput = true
                            }
                        },
                        onSave = { onSave(asset) },
                        onShare = { onShare(asset) },
                        onDelete = { onDelete(asset) },
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
                        onAnimate(asset, true, prompt)
                        showCustomPromptInput = false
                    },
                    onDismiss = { showCustomPromptInput = false },
                    hazeState = hazeState,
                )
            }
        },
    )
}

@Composable
private fun DetailMedia(
    asset: StudioMediaUi,
    videoGenerationState: VideoGenerationState,
    modifier: Modifier = Modifier,
) {
    val generatedVideoUri =
        (videoGenerationState as? VideoGenerationState.Success)
            ?.takeIf { it.sourceAssetId == asset.id }
            ?.localUri
    val isLoading =
        videoGenerationState is VideoGenerationState.Loading &&
            videoGenerationState.sourceAssetId == asset.id

    Box(modifier = modifier.fillMaxSize()) {
        FullscreenMediaViewer(
            localUri = generatedVideoUri ?: asset.localUri,
            mediaType =
                if (generatedVideoUri != null || asset.mediaType == MediaCapability.VIDEO) {
                    MediaCapability.VIDEO
                } else if (asset.mediaType == MediaCapability.MUSIC) {
                    MediaCapability.MUSIC
                } else {
                    MediaCapability.IMAGE
                },
            contentDescription = asset.prompt,
            modifier = Modifier.fillMaxSize(),
        )

        if (isLoading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.42f))
                        .semantics { contentDescription = "Animating image" },
                contentAlignment = Alignment.Center,
            ) {
                NeoCircularProgressIndicator(
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }
        }
    }
}

private fun studioDetailActions(
    asset: StudioMediaUi,
    hasVideoModel: Boolean,
    onEdit: () -> Unit,
    onAnimate: (Boolean) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
): List<FullscreenMediaAction> =
    buildList {
        add(
            FullscreenMediaAction(
                icon = Icons.Default.Edit,
                label = "Edit",
                onClick = onEdit,
            ),
        )
        if (asset.mediaType == MediaCapability.IMAGE) {
            add(
                FullscreenMediaAction(
                    icon = Icons.Default.Movie,
                    label = "Animate",
                    onClick = { onAnimate(false) },
                    enabled = hasVideoModel,
                    disabledTooltip =
                        if (!hasVideoModel) FULLSCREEN_MEDIA_ANIMATE_DISABLED_TOOLTIP else null,
                ),
            )
            add(
                FullscreenMediaAction(
                    icon = Icons.Default.FastForward,
                    label = "Quick Animate",
                    onClick = { onAnimate(true) },
                    enabled = hasVideoModel,
                    disabledTooltip =
                        if (!hasVideoModel) FULLSCREEN_MEDIA_ANIMATE_DISABLED_TOOLTIP else null,
                ),
            )
        }
        add(
            FullscreenMediaAction(
                icon = Icons.Default.Download,
                label = "Save",
                onClick = onSave,
            ),
        )
        add(
            FullscreenMediaAction(
                icon = Icons.Default.Share,
                label = "Share",
                onClick = onShare,
            ),
        )
        add(
            FullscreenMediaAction(
                icon = Icons.Default.Delete,
                label = "Delete",
                onClick = onDelete,
                isDestructive = true,
            ),
        )
    }

@Suppress("UnusedPrivateMember")
@Preview(name = "Image detail", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StudioDetailImagePreview() {
    DetailContent(
        assets = listOf(previewAsset()),
        initialIndex = 0,
        videoGenerationState = VideoGenerationState.Idle,
        hasVideoModel = true,
        hazeState = rememberHazeState(),
        onEdit = {},
        onAnimate = { _, _, _ -> },
        onSave = {},
        onShare = {},
        onDelete = {},
    )
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Loading animation", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StudioDetailLoadingPreview() {
    DetailContent(
        assets = listOf(previewAsset()),
        initialIndex = 0,
        videoGenerationState = VideoGenerationState.Loading("asset-1"),
        hasVideoModel = true,
        hazeState = rememberHazeState(),
        onEdit = {},
        onAnimate = { _, _, _ -> },
        onSave = {},
        onShare = {},
        onDelete = {},
    )
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Generated video", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StudioDetailVideoPreview() {
    DetailContent(
        assets = listOf(previewAsset()),
        initialIndex = 0,
        videoGenerationState = VideoGenerationState.Success("asset-1", "file:///preview.mp4"),
        hasVideoModel = true,
        hazeState = rememberHazeState(),
        onEdit = {},
        onAnimate = { _, _, _ -> },
        onSave = {},
        onShare = {},
        onDelete = {},
    )
}

private fun previewAsset(): StudioMediaUi =
    StudioMediaUi(
        id = "asset-1",
        localUri = "file:///preview.png",
        prompt = "Cinematic mountain lake at sunrise",
        mediaType = MediaCapability.IMAGE,
        createdAt = 1L,
    )

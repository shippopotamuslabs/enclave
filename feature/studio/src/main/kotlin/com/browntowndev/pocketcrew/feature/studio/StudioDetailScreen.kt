package com.browntowndev.pocketcrew.feature.studio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import com.browntowndev.pocketcrew.core.ui.component.FullscreenMediaViewer
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.usecase.media.SaveMediaToGalleryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class StudioDetailUiState {
    data class Success(val assets: List<StudioMediaUi>, val initialIndex: Int) : StudioDetailUiState()
}

@HiltViewModel
class StudioDetailViewModel @Inject constructor(
    private val saveMediaToGalleryUseCase: SaveMediaToGalleryUseCase
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
    viewModel: StudioDetailViewModel = hiltViewModel()
) {
    val initialIndex = remember(assets, assetId) {
        assets.indexOfFirst { it.id == assetId }.coerceAtLeast(0)
    }
    val hazeState = rememberHazeState()

    Scaffold(
        containerColor = Color.Black,
        topBar = { StudioDetailTopBar(onNavigateBack = onNavigateBack) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        if (assets.isNotEmpty()) {
            DetailContent(
                assets = assets,
                initialIndex = initialIndex,
                videoGenerationState = videoGenerationState,
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
                modifier = Modifier.padding(padding)
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Asset not found",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioDetailTopBar(
    onNavigateBack: () -> Unit
) {
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


@Composable
private fun DetailContent(
    assets: List<StudioMediaUi>,
    initialIndex: Int,
    videoGenerationState: VideoGenerationState,
    hazeState: HazeState,
    onEdit: (StudioMediaUi) -> Unit,
    onAnimate: (StudioMediaUi, Boolean, String?) -> Unit,
    onSave: (StudioMediaUi) -> Unit,
    onShare: (StudioMediaUi) -> Unit,
    onDelete: (StudioMediaUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { assets.size }
    )
    var showCustomPromptInput by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
                .hazeSource(hazeState),
            beyondViewportPageCount = 1
        ) { page ->
            val asset = assets[page]
            Box(modifier = Modifier.fillMaxSize()) {
                DetailMedia(
                    asset = asset,
                    videoGenerationState = videoGenerationState,
                )
                DetailActionsOverlay(
                    asset = asset,
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
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        AnimatedVisibility(
            visible = showCustomPromptInput,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val asset = assets[pagerState.currentPage]
            CustomAnimationPromptPane(
                onSend = { prompt ->
                    onAnimate(asset, true, prompt)
                    showCustomPromptInput = false
                },
                onDismiss = { showCustomPromptInput = false },
                hazeState = hazeState
            )
        }
    }
}

@Composable
private fun DetailMedia(
    asset: StudioMediaUi,
    videoGenerationState: VideoGenerationState,
    modifier: Modifier = Modifier
) {
    val generatedVideoUri = (videoGenerationState as? VideoGenerationState.Success)
        ?.takeIf { it.sourceAssetId == asset.id }
        ?.localUri
    val isLoading = videoGenerationState is VideoGenerationState.Loading &&
        videoGenerationState.sourceAssetId == asset.id

    Box(modifier = modifier.fillMaxSize()) {
        FullscreenMediaViewer(
            localUri = generatedVideoUri ?: asset.localUri,
            mediaType = if (generatedVideoUri != null || asset.mediaType == MediaCapability.VIDEO) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .semantics { contentDescription = "Animating image" },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
private fun DetailActionsOverlay(
    asset: StudioMediaUi,
    onEdit: () -> Unit,
    onAnimate: (Boolean) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
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

        DetailActionRow(
            mediaType = asset.mediaType,
            onEdit = onEdit,
            onAnimate = onAnimate,
            onSave = onSave,
            onShare = onShare,
            onDelete = onDelete
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CustomAnimationPromptPane(
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState
) {
    var prompt by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(300) // Wait for slide animation
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hazeEffect(
                state = hazeState,
                style = MaterialTheme.colorScheme.surface.let { color ->
                    dev.chrisbanes.haze.HazeStyle(
                        tint = dev.chrisbanes.haze.HazeTint(color.copy(alpha = 0.5f)),
                        blurRadius = 30.dp
                    )
                }
            )
            .padding(WindowInsets.ime.asPaddingValues()) // Bleed to bottom including IME
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { Text("What should the animation do?", color = Color.White.copy(alpha = 0.6f)) },
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        cursorColor = Color.White
                    ),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (prompt.isNotBlank()) {
                            onSend(prompt)
                        }
                    }),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (prompt.isNotBlank()) {
                                    onSend(prompt)
                                }
                            },
                            enabled = prompt.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (prompt.isNotBlank()) Color.White else Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                )
            }
            
            // Background scrim click to dismiss
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.dp) // Invisible but could be expanded if needed
                    .clickable(onClick = onDismiss)
            )
        }
    }
}

@Preview(name = "Image detail", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StudioDetailImagePreview() {
    DetailContent(
        assets = listOf(previewAsset()),
        initialIndex = 0,
        videoGenerationState = VideoGenerationState.Idle,
        hazeState = rememberHazeState(),
        onEdit = {},
        onAnimate = { _, _, _ -> },
        onSave = {},
        onShare = {},
        onDelete = {},
    )
}

@Preview(name = "Loading animation", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StudioDetailLoadingPreview() {
    DetailContent(
        assets = listOf(previewAsset()),
        initialIndex = 0,
        videoGenerationState = VideoGenerationState.Loading("asset-1"),
        hazeState = rememberHazeState(),
        onEdit = {},
        onAnimate = { _, _, _ -> },
        onSave = {},
        onShare = {},
        onDelete = {},
    )
}

@Preview(name = "Generated video", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StudioDetailVideoPreview() {
    DetailContent(
        assets = listOf(previewAsset()),
        initialIndex = 0,
        videoGenerationState = VideoGenerationState.Success("asset-1", "file:///preview.mp4"),
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

@Composable
private fun DetailActionRow(
    mediaType: MediaCapability,
    onEdit: () -> Unit,
    onAnimate: (Boolean) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionItem(Icons.Default.Edit, "Edit", onEdit)
        if (mediaType == MediaCapability.IMAGE) {
            ActionItem(Icons.Default.Movie, "Animate", onClick = { onAnimate(false) })
            ActionItem(Icons.Default.FastForward, "Quick Animate", onClick = { onAnimate(true) })
        }
        ActionItem(Icons.Default.Download, "Save", onSave)
        ActionItem(Icons.Default.Share, "Share", onShare)
        ActionItem(Icons.Default.Delete, "Delete", onDelete, isDestructive = true)
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

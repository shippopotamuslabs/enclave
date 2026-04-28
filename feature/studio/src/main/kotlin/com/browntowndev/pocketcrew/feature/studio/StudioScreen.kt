package com.browntowndev.pocketcrew.feature.studio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.browntowndev.pocketcrew.core.ui.component.StandardTrailingAction
import com.browntowndev.pocketcrew.core.ui.component.UniversalInputBar
import com.browntowndev.pocketcrew.core.ui.theme.GoldVariant
import com.browntowndev.pocketcrew.core.ui.theme.PeachAccent
import com.browntowndev.pocketcrew.core.ui.theme.PurpleLightPrimary
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.VisualGenerationSettings
import com.browntowndev.pocketcrew.feature.studio.components.PromptHeaderDivider
import com.browntowndev.pocketcrew.feature.studio.components.StudioOptionsBottomSheet
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val GRID_COLUMNS = 2

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StudioScreen(
    onNavigateToHistory: () -> Unit,
    onMediaClick: (String) -> Unit,
    onShowSnackbar: (String, String?) -> Unit,
    viewModel: MultimodalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hazeState = rememberHazeState()
    val listState = rememberLazyListState()
    val visibleGallery = remember(uiState.gallery) { uiState.gallery.asReversed() }
    val groupedGallery = remember(visibleGallery) { visibleGallery.groupBy { it.prompt } }
    val generationPlaceholderCount = (uiState.settings as? ImageGenerationSettings)
        ?.generationCount
        ?.coerceAtLeast(1)
        ?: 1
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onUpdateReferenceImage(it.toString()) }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            onShowSnackbar(it, null)
            viewModel.clearError()
        }
    }

    LaunchedEffect(listState, visibleGallery, uiState.mediaType, uiState.continualMode, uiState.prompt) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index }.toSet() }
            .map { visibleIndexes ->
                val totalGalleryItemCount = visibleGallery.size
                val triggerIndex = (totalGalleryItemCount - GRID_COLUMNS * 2).coerceAtLeast(0)
                
                if (visibleIndexes.any { it >= triggerIndex }) {
                    visibleGallery.getOrNull(triggerIndex)?.id
                } else {
                    null
                }
            }
            .distinctUntilChanged()
            .collect { anchorAssetId ->
                if (anchorAssetId != null) {
                    viewModel.onGenerativeScrollThresholdVisible(anchorAssetId)
                }
            }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open history"
                        )
                    }
                },
                title = {
                    Text(
                        text = "Studio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            // Main Content: Gallery List (Simulating Grid)
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.gallery.isEmpty() && !uiState.isGenerating) {
                    EmptyStudioState()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        groupedGallery.forEach { (prompt, mediaList) ->
                            stickyHeader(key = prompt) {
                                PromptHeaderDivider(
                                    prompt = prompt,
                                    hazeState = hazeState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                )
                            }
                            
                            val rows = mediaList.chunked(GRID_COLUMNS)
                            items(
                                items = rows,
                                key = { row -> row.first().id }
                            ) { rowItems ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .hazeSource(state = hazeState)
                                ) {
                                    rowItems.forEach { asset ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            GalleryItem(asset, onMediaClick)
                                        }
                                    }
                                    if (rowItems.size < GRID_COLUMNS) {
                                        repeat(GRID_COLUMNS - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (uiState.isGenerating) {
                            val placeholderCount = (uiState.settings as? ImageGenerationSettings)
                                ?.generationCount
                                ?.coerceAtLeast(1)
                                ?: 1
                            val placeholderRows = (0 until placeholderCount).chunked(GRID_COLUMNS)
                            
                            items(placeholderRows) { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .hazeSource(state = hazeState)
                                ) {
                                    row.forEach { _ ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            GeneratingPlaceholderItem()
                                        }
                                    }
                                    if (row.size < GRID_COLUMNS) {
                                        repeat(GRID_COLUMNS - row.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom section with settings and input bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                // Input Section at the Bottom
                UniversalInputBar(
                    inputContent = {
                        BasicTextField(
                            value = uiState.prompt,
                            onValueChange = viewModel::onPromptChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (uiState.prompt.isEmpty()) {
                                    Text(
                                        text = "Describe what to create...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send)
                        )
                    },
                    actionContent = {
                        // 1. Reference/Sample Upload Icon (Dynamic Visibility)
                        if (uiState.mediaType != MediaCapability.MUSIC) {
                            IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                                val hasReference = (uiState.settings as? VisualGenerationSettings)?.referenceImageUri != null
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Add reference image",
                                    tint = if (hasReference) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    trailingAction = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Media Mode / Studio Options Trigger
                            val currentIcon = when (uiState.mediaType) {
                                MediaCapability.IMAGE -> Icons.Default.Image
                                MediaCapability.VIDEO -> Icons.Default.Movie
                                MediaCapability.MUSIC -> Icons.Default.MusicNote
                            }

                            StandardTrailingAction(
                                icon = currentIcon,
                                onClick = viewModel::onSettingsToggle,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                description = "Studio Options"
                            )

                            val isStopAction = uiState.isGenerating || uiState.isContinualGenerationActive
                            val icon = if (isStopAction) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send
                            val containerColor = if (isStopAction) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                            val contentColor = if (isStopAction) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            }

                            StandardTrailingAction(
                                icon = icon,
                                onClick = { viewModel.generate() },
                                containerColor = containerColor,
                                contentColor = contentColor,
                                enabled = uiState.prompt.isNotBlank() || isStopAction
                            )
                        }
                    }
                )
            }
        }
    }

    if (uiState.isSettingsOpen) {
        StudioOptionsBottomSheet(
            state = uiState,
            onDismiss = viewModel::onSettingsToggle,
            onUpdateSettings = viewModel::onUpdateSettings,
            onContinualModeChange = viewModel::onContinualModeToggle,
            onMediaTypeChange = viewModel::onMediaTypeChange,
            onTemplateSelected = viewModel::onTemplateSelected
        )
    }
}

@Composable
private fun GalleryItem(
    asset: StudioMediaUi,
    onMediaClick: (String) -> Unit
) {
    Card(
        onClick = { onMediaClick(asset.id) },
        shape = RectangleShape,
        modifier = Modifier
            .aspectRatio(1f)
            .fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = asset.localUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Type badge
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.BottomEnd)
            ) {
                Icon(
                    imageVector = if (asset.mediaType == MediaCapability.IMAGE) {
                        Icons.Default.Image
                    } else {
                        Icons.Default.Videocam
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun GeneratingPlaceholderItem() {
    val infiniteTransition = rememberInfiniteTransition(label = "studio_shimmer")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_progress"
    )

    val baseColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .fillMaxWidth()
            .drawWithCache {
                val brush = Brush.linearGradient(
                    colors = listOf(
                        baseColor,
                        PeachAccent,
                        GoldVariant,
                        PurpleLightPrimary,
                        baseColor
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                    tileMode = androidx.compose.ui.graphics.TileMode.Repeated
                )

                onDrawBehind {
                    val currentProgress = progress
                    val xOffset = currentProgress * size.width
                    val yOffset = currentProgress * size.height

                    withTransform({
                        translate(left = xOffset, top = yOffset)
                    }) {
                        // Draw at -xOffset, -yOffset to ensure the rectangle covers the component's visible area
                        drawRect(
                            brush = brush,
                            topLeft = Offset(x = -xOffset, y = -yOffset),
                            size = size
                        )
                    }
                }
            }
    )
}

@Composable
private fun EmptyStudioState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No creations yet. Use the controls below to generate an image, video, or music track.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

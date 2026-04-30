package com.browntowndev.pocketcrew.feature.studio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import android.os.Build
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

private const val GRID_COLUMNS = 2
private const val GALLERY_HEADER_KEY_PREFIX = "studio_header_"
private const val GALLERY_ROW_KEY_PREFIX = "studio_gallery_row_"
private const val PLACEHOLDER_ROW_KEY_PREFIX = "studio_placeholder_row_"

data class StudioGalleryGroup(
    val prompt: String,
    val items: List<StudioMediaUi>
)

data class StudioGalleryRow(
    val mediaItems: List<StudioMediaUi>,
    val placeholderCount: Int = 0,
)

internal data class StudioPromptHeaderInfo(
    val key: String,
    val prompt: String,
    val itemIndex: Int,
)

internal data class StudioVisibleListItemInfo(
    val key: Any,
    val offset: Int,
)

internal data class StudioStickyHeaderLayout(
    val key: String,
    val prompt: String,
    val yOffset: Int,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StudioScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToGallery: () -> Unit,
    onMediaClick: (String) -> Unit,
    onShowSnackbar: (String, String?) -> Unit,
    viewModel: MultimodalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hazeState = rememberHazeState()
    val listState = rememberLazyListState()
    val visibleGallery = remember(uiState.gallery) { uiState.gallery }
    val groupedGallery = remember(visibleGallery) {
        val groups = mutableListOf<StudioGalleryGroup>()
        if (visibleGallery.isNotEmpty()) {
            var currentPrompt = visibleGallery.first().prompt
            var currentItems = mutableListOf<StudioMediaUi>()

            for (item in visibleGallery) {
                if (item.prompt == currentPrompt) {
                    currentItems.add(item)
                } else {
                    groups.add(StudioGalleryGroup(currentPrompt, currentItems))
                    currentPrompt = item.prompt
                    currentItems = mutableListOf(item)
                }
            }
            groups.add(StudioGalleryGroup(currentPrompt, currentItems))
        }
        groups
    }
    val galleryRowKeys = remember(groupedGallery) {
        groupedGallery.flatMap { group ->
            group.items.chunked(GRID_COLUMNS).map(::studioGalleryRowKey)
        }
    }
    val generationPlaceholderCount = (uiState.settings as? ImageGenerationSettings)
        ?.generationCount
        ?.coerceAtLeast(1)
        ?: 1
    var scrollOnGenerationStart by remember { mutableStateOf(false) }
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

    LaunchedEffect(uiState.isGenerating) {
        if (scrollOnGenerationStart && uiState.isGenerating) {
            scrollOnGenerationStart = false
            val lastIndex = listState.layoutInfo.totalItemsCount - 1
            if (lastIndex >= 0) {
                listState.animateScrollToItem(lastIndex)
            }
        }
    }

    val latestGallery by rememberUpdatedState(visibleGallery)
    val latestGalleryRowKeys by rememberUpdatedState(galleryRowKeys)
    LaunchedEffect(listState, uiState.mediaType, uiState.continualMode) {
        snapshotFlow { listState.layoutInfo }
            .map { layoutInfo ->
                val visibleKeys = layoutInfo.visibleItemsInfo.map { it.key }
                val isNearBottom = isGenerativeScrollThresholdVisible(
                    galleryRowKeys = latestGalleryRowKeys,
                    visibleItemKeys = visibleKeys,
                )

                if (isNearBottom && latestGallery.isNotEmpty()) {
                    latestGallery.last().id to latestGallery.size
                } else {
                    null
                }
            }
            .distinctUntilChanged()
            .collect { scrollTrigger ->
                if (scrollTrigger != null) {
                    viewModel.onGenerativeScrollThresholdVisible(
                        anchorAssetId = scrollTrigger.first,
                        gallerySize = scrollTrigger.second,
                    )
                }
            }
    }

    Scaffold(
        topBar = {
            StudioTopBar(
                selectedCount = uiState.selectedMediaItemIds.size,
                onClearSelection = viewModel::clearMediaSelection,
                onSaveClick = viewModel::onToggleSaveBottomSheet,
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToGallery = onNavigateToGallery,
                hazeState = hazeState,
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            StudioGalleryPane(
                groupedGallery = groupedGallery,
                selectedIds = uiState.selectedMediaItemIds,
                isGenerating = uiState.isGenerating,
                activeGenerationPrompt = uiState.activeGenerationPrompt,
                generationPlaceholderCount = generationPlaceholderCount,
                hazeState = hazeState,
                listState = listState,
                topPadding = padding.calculateTopPadding(),
                onMediaClick = { id ->
                    if (uiState.selectedMediaItemIds.isNotEmpty()) {
                        val asset = groupedGallery.flatMap { it.items }.find { it.id == id }
                        if (asset?.albumId == null) {
                            viewModel.toggleMediaSelection(id)
                        }
                    } else {
                        onMediaClick(id)
                    }
                },
                onMediaLongClick = { id ->
                    val asset = groupedGallery.flatMap { it.items }.find { it.id == id }
                    if (asset?.albumId == null) {
                        viewModel.toggleMediaSelection(id)
                    }
                },
                modifier = Modifier.weight(1f)
            )

            StudioInputDock(
                prompt = uiState.prompt,
                mediaType = uiState.mediaType,
                referenceImageUri = (uiState.settings as? VisualGenerationSettings)?.referenceImageUri,
                isGenerating = uiState.isGenerating,
                isContinualGenerationActive = uiState.isContinualGenerationActive,
                onPromptChange = viewModel::onPromptChange,
                onReferenceImageClick = { imagePickerLauncher.launch("image/*") },
                onClearReferenceImage = viewModel::onClearReferenceImage,
                onSettingsClick = viewModel::onSettingsToggle,
                onGenerateClick = {
                    if (!uiState.isGenerating && !uiState.isContinualGenerationActive) {
                        scrollOnGenerationStart = true
                    }
                    viewModel.generate()
                }
            )
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

    if (uiState.isSaveBottomSheetOpen) {
        MoveBottomSheet(
            albums = uiState.albums,
            onDismiss = viewModel::onToggleSaveBottomSheet,
            onMoveToAlbum = viewModel::onSaveSelectedMediaToAlbum,
            onAddAlbum = viewModel::onAddAlbum
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSaveClick: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToGallery: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
        modifier = modifier
            .hazeEffect(state = hazeState) {
                blurRadius = 24.dp
                tints = listOf(HazeTint(Color.Black.copy(alpha = 0.4f)))
                noiseFactor = 0.15f
            },
        navigationIcon = {
            if (selectedCount > 0) {
                IconButton(onClick = onClearSelection) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear selection"
                    )
                }
            } else {
                IconButton(onClick = onNavigateToHistory) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open history"
                    )
                }
            }
        },
        title = {
            Text(
                text = if (selectedCount > 0) "$selectedCount Selected" else "Studio",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            if (selectedCount > 0) {
                IconButton(onClick = onSaveClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                        contentDescription = "Save to album"
                    )
                }
            } else {
                IconButton(onClick = onNavigateToGallery) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Open gallery"
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StudioGalleryPane(
    groupedGallery: List<StudioGalleryGroup>,
    selectedIds: Set<String>,
    isGenerating: Boolean,
    activeGenerationPrompt: String?,
    generationPlaceholderCount: Int,
    hazeState: HazeState,
    listState: LazyListState,
    topPadding: Dp,
    onMediaClick: (String) -> Unit,
    onMediaLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (groupedGallery.isEmpty() && !isGenerating) {
            EmptyStudioState()
        } else {
            StudioGalleryList(
                groupedGallery = groupedGallery,
                selectedIds = selectedIds,
                isGenerating = isGenerating,
                activeGenerationPrompt = activeGenerationPrompt,
                generationPlaceholderCount = generationPlaceholderCount,
                hazeState = hazeState,
                listState = listState,
                topPadding = topPadding,
                onMediaClick = onMediaClick,
                onMediaLongClick = onMediaLongClick
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StudioGalleryList(
    groupedGallery: List<StudioGalleryGroup>,
    selectedIds: Set<String>,
    isGenerating: Boolean,
    activeGenerationPrompt: String?,
    generationPlaceholderCount: Int,
    hazeState: HazeState,
    listState: LazyListState,
    topPadding: Dp,
    onMediaClick: (String) -> Unit,
    onMediaLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val promptHeaders = remember(
        groupedGallery,
        isGenerating,
        activeGenerationPrompt,
        generationPlaceholderCount,
    ) {
        buildStudioPromptHeaderInfo(
            groupedGallery = groupedGallery,
            isGenerating = isGenerating,
            activeGenerationPrompt = activeGenerationPrompt,
            generationPlaceholderCount = generationPlaceholderCount,
        )
    }
    var stickyHeaderHeightPx by remember { mutableIntStateOf(0) }
    var expandedPromptHeaderKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(promptHeaders) {
        val activeHeaderKeys = promptHeaders.map { header -> header.key }
        expandedPromptHeaderKeys = expandedPromptHeaderKeys.filter { key -> key in activeHeaderKeys }
    }
    val stickyTopPx = with(LocalDensity.current) { topPadding.roundToPx() }
    val stickyHeaderLayout by remember(promptHeaders, listState, stickyTopPx, stickyHeaderHeightPx) {
        derivedStateOf {
            calculateStudioStickyHeaderLayout(
                headers = promptHeaders,
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                visibleItems = listState.layoutInfo.visibleItemsInfo.map { visibleItem ->
                    StudioVisibleListItemInfo(
                        key = visibleItem.key,
                        offset = visibleItem.offset,
                    )
                },
                stickyTopPx = stickyTopPx,
                headerHeightPx = stickyHeaderHeightPx,
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding)
    ) {
        groupedGallery.forEachIndexed { index, group ->
            val isActiveGenerationGroup = isGenerating &&
                group.prompt == activeGenerationPrompt &&
                index == groupedGallery.lastIndex
            val galleryRows = buildStudioGalleryRows(
                group = group,
                placeholderCount = if (isActiveGenerationGroup) generationPlaceholderCount else 0,
            )

            val headerKey = studioPromptHeaderKey(groupIndex = index, prompt = group.prompt)
            item(key = headerKey) {
                PromptHeaderDivider(
                    prompt = group.prompt,
                    hazeState = hazeState,
                    isExpanded = headerKey in expandedPromptHeaderKeys,
                    onExpandedChange = { isExpanded ->
                        expandedPromptHeaderKeys = updateStudioExpandedHeaderKeys(
                            expandedHeaderKeys = expandedPromptHeaderKeys,
                            headerKey = headerKey,
                            isExpanded = isExpanded,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = if (stickyHeaderLayout?.key == headerKey) 0f else 1f
                        }
                )
            }

            items(
                items = galleryRows.filter { it.mediaItems.isNotEmpty() },
                key = { row -> studioGalleryRowKey(row.mediaItems) },
            ) { row ->
                GalleryGridRow(
                    rowItems = row.mediaItems,
                    selectedIds = selectedIds,
                    placeholderCount = row.placeholderCount,
                    hazeState = hazeState,
                    onMediaClick = onMediaClick,
                    onMediaLongClick = onMediaLongClick
                )
            }

            val remainingPlaceholderRows = galleryRows.filter { it.mediaItems.isEmpty() }
            if (remainingPlaceholderRows.isNotEmpty()) {
                itemsIndexed(
                    items = remainingPlaceholderRows,
                    key = { rowIndex, _ -> "$PLACEHOLDER_ROW_KEY_PREFIX${index}_$rowIndex" },
                ) { _, row ->
                    PlaceholderGridRow(
                        placeholderCount = row.placeholderCount,
                        hazeState = hazeState
                    )
                }
            }
        }

        if (isGenerating && activeGenerationPrompt != null && groupedGallery.lastOrNull()?.prompt != activeGenerationPrompt) {
            val headerKey = studioPromptHeaderKey(groupIndex = groupedGallery.size, prompt = activeGenerationPrompt)
            item(key = headerKey) {
                PromptHeaderDivider(
                    prompt = activeGenerationPrompt,
                    hazeState = hazeState,
                    isExpanded = headerKey in expandedPromptHeaderKeys,
                    onExpandedChange = { isExpanded ->
                        expandedPromptHeaderKeys = updateStudioExpandedHeaderKeys(
                            expandedHeaderKeys = expandedPromptHeaderKeys,
                            headerKey = headerKey,
                            isExpanded = isExpanded,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = if (stickyHeaderLayout?.key == headerKey) 0f else 1f
                        }
                )
            }
            items(
                items = (0 until generationPlaceholderCount).chunked(GRID_COLUMNS),
                key = { row -> "$PLACEHOLDER_ROW_KEY_PREFIX${row.first()}" },
            ) { row ->
                PlaceholderGridRow(
                    placeholderCount = row.size,
                    hazeState = hazeState
                )
            }
        }
    }

    stickyHeaderLayout?.let { headerLayout ->
        PromptHeaderDivider(
            prompt = headerLayout.prompt,
            hazeState = hazeState,
            isExpanded = headerLayout.key in expandedPromptHeaderKeys,
            onExpandedChange = { isExpanded ->
                expandedPromptHeaderKeys = updateStudioExpandedHeaderKeys(
                    expandedHeaderKeys = expandedPromptHeaderKeys,
                    headerKey = headerLayout.key,
                    isExpanded = isExpanded,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(x = 0, y = headerLayout.yOffset) }
                .onSizeChanged { size -> stickyHeaderHeightPx = size.height },
        )
    }
}

internal fun isGenerativeScrollThresholdVisible(
    galleryRowKeys: List<Any>,
    visibleItemKeys: List<Any>,
): Boolean {
    if (galleryRowKeys.isEmpty()) {
        return false
    }

    val triggerRowIndex = (galleryRowKeys.lastIndex - 1).coerceAtLeast(0)
    val visibleGalleryRowIndexes = visibleItemKeys.mapNotNull { visibleKey ->
        galleryRowKeys.indexOf(visibleKey).takeIf { index -> index >= 0 }
    }

    return visibleGalleryRowIndexes.any { index -> index >= triggerRowIndex }
}

internal fun studioGalleryRowKey(row: List<StudioMediaUi>): String = "$GALLERY_ROW_KEY_PREFIX${row.first().id}"

internal fun studioPromptHeaderKey(
    groupIndex: Int,
    prompt: String,
): String = "$GALLERY_HEADER_KEY_PREFIX${groupIndex}_$prompt"

internal fun buildStudioGalleryRows(
    group: StudioGalleryGroup,
    placeholderCount: Int,
): List<StudioGalleryRow> {
    val mediaRows = group.items.chunked(GRID_COLUMNS).map { mediaItems ->
        StudioGalleryRow(mediaItems = mediaItems)
    }

    if (placeholderCount <= 0) {
        return mediaRows
    }

    val mutableRows = mediaRows.toMutableList()
    var remainingPlaceholders = placeholderCount
    val lastMediaRow = mutableRows.lastOrNull()
    if (lastMediaRow != null && lastMediaRow.mediaItems.size < GRID_COLUMNS) {
        val inlinePlaceholderCount = (GRID_COLUMNS - lastMediaRow.mediaItems.size)
            .coerceAtMost(remainingPlaceholders)
        mutableRows[mutableRows.lastIndex] = lastMediaRow.copy(placeholderCount = inlinePlaceholderCount)
        remainingPlaceholders -= inlinePlaceholderCount
    }

    while (remainingPlaceholders > 0) {
        val rowPlaceholderCount = remainingPlaceholders.coerceAtMost(GRID_COLUMNS)
        mutableRows.add(StudioGalleryRow(mediaItems = emptyList(), placeholderCount = rowPlaceholderCount))
        remainingPlaceholders -= rowPlaceholderCount
    }

    return mutableRows
}

internal fun buildStudioPromptHeaderInfo(
    groupedGallery: List<StudioGalleryGroup>,
    isGenerating: Boolean,
    activeGenerationPrompt: String?,
    generationPlaceholderCount: Int,
): List<StudioPromptHeaderInfo> {
    val headers = mutableListOf<StudioPromptHeaderInfo>()
    var itemIndex = 0

    groupedGallery.forEachIndexed { groupIndex, group ->
        val isActiveGenerationGroup = isGenerating &&
            group.prompt == activeGenerationPrompt &&
            groupIndex == groupedGallery.lastIndex
        val galleryRows = buildStudioGalleryRows(
            group = group,
            placeholderCount = if (isActiveGenerationGroup) generationPlaceholderCount else 0,
        )

        headers.add(
            StudioPromptHeaderInfo(
                key = studioPromptHeaderKey(groupIndex = groupIndex, prompt = group.prompt),
                prompt = group.prompt,
                itemIndex = itemIndex,
            )
        )
        itemIndex += 1
        itemIndex += galleryRows.count { it.mediaItems.isNotEmpty() }
        itemIndex += galleryRows.count { it.mediaItems.isEmpty() }
    }

    if (isGenerating && activeGenerationPrompt != null && groupedGallery.lastOrNull()?.prompt != activeGenerationPrompt) {
        headers.add(
            StudioPromptHeaderInfo(
                key = studioPromptHeaderKey(groupIndex = groupedGallery.size, prompt = activeGenerationPrompt),
                prompt = activeGenerationPrompt,
                itemIndex = itemIndex,
            )
        )
    }

    return headers
}

internal fun updateStudioExpandedHeaderKeys(
    expandedHeaderKeys: List<String>,
    headerKey: String,
    isExpanded: Boolean,
): List<String> =
    when {
        isExpanded && headerKey !in expandedHeaderKeys -> expandedHeaderKeys + headerKey
        !isExpanded -> expandedHeaderKeys - headerKey
        else -> expandedHeaderKeys
    }

internal fun calculateStudioStickyHeaderLayout(
    headers: List<StudioPromptHeaderInfo>,
    firstVisibleItemIndex: Int,
    visibleItems: List<StudioVisibleListItemInfo>,
    stickyTopPx: Int,
    headerHeightPx: Int,
): StudioStickyHeaderLayout? {
    val currentHeader = headers.lastOrNull { header -> header.itemIndex <= firstVisibleItemIndex }
        ?: return null
    val currentVisibleHeader = visibleItems.firstOrNull { visibleItem -> visibleItem.key == currentHeader.key }
    if (currentVisibleHeader != null && currentVisibleHeader.offset > 0) {
        return null
    }

    val nextVisibleHeaderOffset = visibleItems
        .filter { visibleItem -> headers.any { header -> header.key == visibleItem.key && header.key != currentHeader.key } }
        .minOfOrNull { visibleItem -> visibleItem.offset }
    val pushedOffset = nextVisibleHeaderOffset
        ?.let { offset -> (stickyTopPx + offset - headerHeightPx).coerceAtMost(stickyTopPx) }
        ?: stickyTopPx

    return StudioStickyHeaderLayout(
        key = currentHeader.key,
        prompt = currentHeader.prompt,
        yOffset = pushedOffset,
    )
}

@Composable
private fun GalleryGridRow(
    rowItems: List<StudioMediaUi>,
    selectedIds: Set<String>,
    placeholderCount: Int = 0,
    hazeState: HazeState,
    onMediaClick: (String) -> Unit,
    onMediaLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .hazeSource(state = hazeState)
    ) {
        rowItems.forEach { asset ->
            Box(modifier = Modifier.weight(1f)) {
                GalleryItem(
                    asset = asset,
                    isSelected = asset.id in selectedIds,
                    selectionModeActive = selectedIds.isNotEmpty(),
                    onMediaClick = onMediaClick,
                    onMediaLongClick = onMediaLongClick
                )
            }
        }
        repeat(placeholderCount) {
            Box(modifier = Modifier.weight(1f)) {
                GeneratingPlaceholderItem()
            }
        }
        GridRemainderSpacer(itemCount = rowItems.size + placeholderCount)
    }
}

@Composable
private fun PlaceholderGridRow(
    placeholderCount: Int,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .hazeSource(state = hazeState)
    ) {
        repeat(placeholderCount) {
            Box(modifier = Modifier.weight(1f)) {
                GeneratingPlaceholderItem()
            }
        }
        GridRemainderSpacer(itemCount = placeholderCount)
    }
}

@Composable
private fun RowScope.GridRemainderSpacer(
    itemCount: Int
) {
    if (itemCount < GRID_COLUMNS) {
        repeat(GRID_COLUMNS - itemCount) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StudioInputDock(
    prompt: String,
    mediaType: MediaCapability,
    referenceImageUri: String?,
    isGenerating: Boolean,
    isContinualGenerationActive: Boolean,
    onPromptChange: (String) -> Unit,
    onReferenceImageClick: () -> Unit,
    onClearReferenceImage: () -> Unit,
    onSettingsClick: () -> Unit,
    onGenerateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
    ) {
        if (referenceImageUri != null) {
            ReferenceImageThumbnail(
                uri = referenceImageUri,
                onClear = onClearReferenceImage,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        UniversalInputBar(
            inputContent = {
                StudioPromptField(
                    prompt = prompt,
                    onPromptChange = onPromptChange
                )
            },
            actionContent = {
                ReferenceImageAction(
                    mediaType = mediaType,
                    hasReferenceImage = referenceImageUri != null,
                    onReferenceImageClick = onReferenceImageClick
                )
            },
            trailingAction = {
                StudioTrailingActions(
                    prompt = prompt,
                    mediaType = mediaType,
                    isGenerating = isGenerating,
                    isContinualGenerationActive = isContinualGenerationActive,
                    onSettingsClick = onSettingsClick,
                    onGenerateClick = onGenerateClick
                )
            }
        )
    }
}

@Composable
private fun StudioPromptField(
    prompt: String,
    onPromptChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = prompt,
        onValueChange = onPromptChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 12.dp),
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            if (prompt.isEmpty()) {
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
}

@Composable
private fun ReferenceImageAction(
    mediaType: MediaCapability,
    hasReferenceImage: Boolean,
    onReferenceImageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (mediaType != MediaCapability.MUSIC) {
        IconButton(
            onClick = onReferenceImageClick,
            modifier = modifier
        ) {
            Icon(
                imageVector = if (hasReferenceImage) Icons.Default.Edit else Icons.Default.AddPhotoAlternate,
                contentDescription = "Reference image",
                tint = if (hasReferenceImage) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun ReferenceImageThumbnail(
    uri: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Reference image thumbnail",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = CircleShape,
            modifier = Modifier
                .padding(4.dp)
                .size(24.dp)
                .align(Alignment.TopEnd)
                .clickable(onClick = onClear)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear reference image",
                tint = Color.White,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}

@Composable
private fun StudioTrailingActions(
    prompt: String,
    mediaType: MediaCapability,
    isGenerating: Boolean,
    isContinualGenerationActive: Boolean,
    onSettingsClick: () -> Unit,
    onGenerateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StandardTrailingAction(
            icon = when (mediaType) {
                MediaCapability.IMAGE -> Icons.Default.Image
                MediaCapability.VIDEO -> Icons.Default.Movie
                MediaCapability.MUSIC -> Icons.Default.MusicNote
            },
            onClick = onSettingsClick,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            description = "Studio Options"
        )

        val isStopAction = isGenerating || isContinualGenerationActive
        StandardTrailingAction(
            icon = if (isStopAction) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
            onClick = onGenerateClick,
            containerColor = if (isStopAction) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primary
            },
            contentColor = if (isStopAction) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimary
            },
            enabled = prompt.isNotBlank() || isStopAction
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryItem(
    asset: StudioMediaUi,
    isSelected: Boolean,
    selectionModeActive: Boolean,
    onMediaClick: (String) -> Unit,
    onMediaLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val padding by animateDpAsState(
        targetValue = if (isSelected) 12.dp else 0.dp,
        label = "selection_padding"
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 0.dp,
        label = "selection_corner_radius"
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onMediaClick(asset.id) },
                onLongClick = { onMediaLongClick(asset.id) }
            )
            .padding(padding)
    ) {
        AsyncImage(
            model = asset.localUri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadius)),
            contentScale = ContentScale.Crop
        )

        // Saved indicator (folder icon)
        if (asset.albumId != null) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(bottomStart = 8.dp),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Saved to album",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(16.dp)
                )
            }
        }

        // Selection indicator (only for non-saved items)
        if (selectionModeActive && asset.albumId == null) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.4f)
                    )
                    .border(
                        width = 2.dp,
                        color = Color.White,
                        shape = CircleShape
                    )
                    .align(Alignment.TopStart),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddAlbumDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var albumName by remember { mutableStateOf("") }
    val trimmedName = albumName.trim()

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(text = "New Album") },
        text = {
            OutlinedTextField(
                value = albumName,
                onValueChange = { albumName = it },
                label = { Text(text = "Album name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmedName) },
                enabled = trimmedName.isNotEmpty(),
            ) {
                Text(text = "Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveBottomSheet(
    albums: List<GalleryAlbumUi>,
    onDismiss: () -> Unit,
    onMoveToAlbum: (String) -> Unit,
    onAddAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isAddingNewAlbum by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Save to Album",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        onClick = { isAddingNewAlbum = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Create New Album",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                if (albums.isNotEmpty()) {
                    item {
                        Text(
                            text = "Existing Albums",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
                        )
                    }
                }

                items(
                    items = albums,
                    key = { album -> album.id }
                ) { album ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMoveToAlbum(album.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val imageSize = 72.dp
                        val coverUri = album.coverItems.firstOrNull()?.localUri
                        if (coverUri != null) {
                            AsyncImage(
                                model = coverUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(imageSize)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(imageSize)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(20.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = album.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${album.itemCount} items",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (isAddingNewAlbum) {
        AddAlbumDialog(
            onDismiss = { isAddingNewAlbum = false },
            onConfirm = { name ->
                onAddAlbum(name)
                isAddingNewAlbum = false
            }
        )
    }
}

@Composable
private fun GeneratingPlaceholderItem(
    modifier: Modifier = Modifier
) {
    val localHazeState = remember { HazeState() }
    val infiniteTransition = rememberInfiniteTransition(label = "studio_lavalamp")
    
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .fillMaxWidth()
            .clipToBounds() // Fix: Prevent drawing outside the box
    ) {
        // Deep space background to make lava pop
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121214)))

        // Lava blobs layer (Metaball effect)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = localHazeState)
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val blur = RenderEffect.createBlurEffect(50f, 50f, android.graphics.Shader.TileMode.CLAMP)
                        val colorMatrix = android.graphics.ColorMatrix().apply {
                            set(
                                floatArrayOf(
                                    1f, 0f, 0f, 0f, 0f,
                                    0f, 1f, 0f, 0f, 0f,
                                    0f, 0f, 1f, 0f, 0f,
                                    0f, 0f, 0f, 60f, -5000f // Alpha thresholding for metaballs
                                )
                            )
                        }
                        val filter = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(colorMatrix))
                        renderEffect = RenderEffect.createChainEffect(filter, blur).asComposeRenderEffect()
                    } else {
                        // Fallback for API < 31
                        alpha = 0.9f
                    }
                }
                .drawWithCache {
                    onDrawBehind {
                        val w = size.width
                        val h = size.height
                        val minD = size.minDimension

                        // Blob 1: Purple base, orbits slowly
                        val x1 = w / 2 + (w * 0.2f) * kotlin.math.cos(time)
                        val y1 = h / 2 + (h * 0.2f) * kotlin.math.sin(time)
                        drawCircle(
                            color = PurpleLightPrimary,
                            radius = minD * 0.35f,
                            center = Offset(x1, y1)
                        )

                        // Blob 2: Peach, dramatic vertical motion
                        val y2 = h * 0.5f + (h * 0.4f) * kotlin.math.sin(time * 1.5f)
                        val x2 = w * 0.3f + (w * 0.15f) * kotlin.math.cos(time * 0.8f)
                        drawCircle(
                            color = PeachAccent,
                            radius = minD * 0.25f,
                            center = Offset(x2, y2)
                        )

                        // Blob 3: Gold, counter-orbit and vertical
                        val y3 = h * 0.5f + (h * 0.35f) * kotlin.math.cos(time * 1.2f)
                        val x3 = w * 0.7f + (w * 0.15f) * kotlin.math.sin(time * 1.1f)
                        drawCircle(
                            color = GoldVariant,
                            radius = minD * 0.2f,
                            center = Offset(x3, y3)
                        )
                        
                        // Blob 4: Small Peach accent, fast orbit
                        val y4 = h * 0.5f - (h * 0.3f) * kotlin.math.cos(time * 2f)
                        val x4 = w * 0.5f + (w * 0.25f) * kotlin.math.sin(time * 1.7f)
                        drawCircle(
                            color = PeachAccent,
                            radius = minD * 0.15f,
                            center = Offset(x4, y4)
                        )
                        
                        // Central blob to anchor the others as they pass through
                        drawCircle(
                            color = PurpleLightPrimary.copy(alpha = 0.5f),
                            radius = minD * 0.2f,
                            center = Offset(w / 2, h / 2)
                        )
                    }
                }
        )

        // Hazy Glass Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeEffect(state = localHazeState) {
                    blurRadius = 24.dp
                    tints = listOf(HazeTint(Color.White.copy(alpha = 0.05f)))
                    noiseFactor = 0.15f
                }
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RectangleShape
                )
        )
    }
}

@Composable
private fun EmptyStudioState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
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

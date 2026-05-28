package com.browntowndev.enclave.feature.studio

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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.browntowndev.enclave.core.ui.component.VideoPlayer
import com.browntowndev.enclave.core.ui.component.neo.NeoSelectionMark
import com.browntowndev.enclave.core.ui.component.neo.neoBorderColor
import com.browntowndev.enclave.core.ui.component.neo.neoButtonShadowOffset
import com.browntowndev.enclave.core.ui.component.neo.neoShadowColor
import com.browntowndev.enclave.core.ui.component.neo.neoSurface
import com.browntowndev.enclave.core.ui.component.shimmerEffect
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.feature.studio.components.PromptHeaderDivider

internal const val GRID_COLUMNS = 2
private const val GENERATING_SHIMMER_DURATION_MS = 1_400
private const val GALLERY_HEADER_KEY_PREFIX = "studio_header_"
internal const val GALLERY_ROW_KEY_PREFIX = "studio_gallery_row_"
private const val PLACEHOLDER_ROW_KEY_PREFIX = "studio_placeholder_row_"

internal data class StudioGalleryRow(
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StudioGalleryPane(
    groupedGallery: List<StudioGalleryGroup>,
    selectedIds: Set<String>,
    isGenerating: Boolean,
    activeGenerationPrompt: String?,
    generationPlaceholderCount: Int,
    listState: LazyListState,
    topPadding: Dp,
    onMediaClick: (String) -> Unit,
    onMediaLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (groupedGallery.isEmpty() && !isGenerating) {
            EmptyStudioState(topPadding = topPadding)
        } else {
            StudioGalleryList(
                groupedGallery = groupedGallery,
                selectedIds = selectedIds,
                isGenerating = isGenerating,
                activeGenerationPrompt = activeGenerationPrompt,
                generationPlaceholderCount = generationPlaceholderCount,
                listState = listState,
                topPadding = topPadding,
                onMediaClick = onMediaClick,
                onMediaLongClick = onMediaLongClick,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StudioGalleryList(
    groupedGallery: List<StudioGalleryGroup>,
    selectedIds: Set<String>,
    isGenerating: Boolean,
    activeGenerationPrompt: String?,
    generationPlaceholderCount: Int,
    listState: LazyListState,
    topPadding: Dp,
    onMediaClick: (String) -> Unit,
    onMediaLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val promptHeaders =
        remember(
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
                visibleItems =
                    listState.layoutInfo.visibleItemsInfo.map { visibleItem ->
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
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                top = topPadding,
                bottom = EnclaveNeoTokens.rowSpacing,
            ),
    ) {
        groupedGallery.forEachIndexed { index, group ->
            val isActiveGenerationGroup =
                isGenerating &&
                    group.prompt == activeGenerationPrompt &&
                    index == groupedGallery.lastIndex
            val galleryRows =
                buildStudioGalleryRows(
                    group = group,
                    placeholderCount = if (isActiveGenerationGroup) generationPlaceholderCount else 0,
                )

            val headerKey = studioPromptHeaderKey(groupIndex = index, prompt = group.prompt)
            item(key = headerKey) {
                PromptHeaderDivider(
                    prompt = group.prompt,
                    isExpanded = headerKey in expandedPromptHeaderKeys,
                    onExpandedChange = { isExpanded ->
                        expandedPromptHeaderKeys =
                            updateStudioExpandedHeaderKeys(
                                expandedHeaderKeys = expandedPromptHeaderKeys,
                                headerKey = headerKey,
                                isExpanded = isExpanded,
                            )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = if (index > 0) EnclaveNeoTokens.sectionSpacing else 0.dp)
                            .graphicsLayer {
                                alpha = if (stickyHeaderLayout?.key == headerKey) 0f else 1f
                            },
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
                    onMediaClick = onMediaClick,
                    onMediaLongClick = onMediaLongClick,
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
                    )
                }
            }
        }

        if (isGenerating && activeGenerationPrompt != null && groupedGallery.lastOrNull()?.prompt != activeGenerationPrompt) {
            val headerKey = studioPromptHeaderKey(groupIndex = groupedGallery.size, prompt = activeGenerationPrompt)
            item(key = headerKey) {
                PromptHeaderDivider(
                    prompt = activeGenerationPrompt,
                    isExpanded = headerKey in expandedPromptHeaderKeys,
                    onExpandedChange = { isExpanded ->
                        expandedPromptHeaderKeys =
                            updateStudioExpandedHeaderKeys(
                                expandedHeaderKeys = expandedPromptHeaderKeys,
                                headerKey = headerKey,
                                isExpanded = isExpanded,
                            )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = if (groupedGallery.isNotEmpty()) EnclaveNeoTokens.sectionSpacing else 0.dp)
                            .graphicsLayer {
                                alpha = if (stickyHeaderLayout?.key == headerKey) 0f else 1f
                            },
                )
            }
            items(
                items = (0 until generationPlaceholderCount).chunked(GRID_COLUMNS),
                key = { row -> "$PLACEHOLDER_ROW_KEY_PREFIX${row.first()}" },
            ) { row ->
                PlaceholderGridRow(
                    placeholderCount = row.size,
                )
            }
        }
    }

    stickyHeaderLayout?.let { headerLayout ->
        PromptHeaderDivider(
            prompt = headerLayout.prompt,
            isExpanded = headerLayout.key in expandedPromptHeaderKeys,
            onExpandedChange = { isExpanded ->
                expandedPromptHeaderKeys =
                    updateStudioExpandedHeaderKeys(
                        expandedHeaderKeys = expandedPromptHeaderKeys,
                        headerKey = headerLayout.key,
                        isExpanded = isExpanded,
                    )
            },
            omitTopBorder = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(x = 0, y = headerLayout.yOffset) }
                    .onSizeChanged { size -> stickyHeaderHeightPx = size.height },
        )
    }
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
    val mediaRows =
        group.mediaItems.chunked(GRID_COLUMNS).map { mediaItems ->
            StudioGalleryRow(mediaItems = mediaItems)
        }

    if (placeholderCount <= 0) {
        return mediaRows
    }

    val mutableRows = mediaRows.toMutableList()
    var remainingPlaceholders = placeholderCount
    val lastMediaRow = mutableRows.lastOrNull()
    if (lastMediaRow != null && lastMediaRow.mediaItems.size < GRID_COLUMNS) {
        val inlinePlaceholderCount =
            (GRID_COLUMNS - lastMediaRow.mediaItems.size)
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
        val isActiveGenerationGroup =
            isGenerating &&
                group.prompt == activeGenerationPrompt &&
                groupIndex == groupedGallery.lastIndex
        val galleryRows =
            buildStudioGalleryRows(
                group = group,
                placeholderCount = if (isActiveGenerationGroup) generationPlaceholderCount else 0,
            )

        headers.add(
            StudioPromptHeaderInfo(
                key = studioPromptHeaderKey(groupIndex = groupIndex, prompt = group.prompt),
                prompt = group.prompt,
                itemIndex = itemIndex,
            ),
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
            ),
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
    val currentHeader =
        headers.lastOrNull { header -> header.itemIndex <= firstVisibleItemIndex }
            ?: return null
    val currentVisibleHeader = visibleItems.firstOrNull { visibleItem -> visibleItem.key == currentHeader.key }
    if (currentVisibleHeader != null && currentVisibleHeader.offset > stickyTopPx) {
        return null
    }

    val nextVisibleHeaderOffset =
        visibleItems
            .filter { visibleItem -> headers.any { header -> header.key == visibleItem.key && header.key != currentHeader.key } }
            .minOfOrNull { visibleItem -> visibleItem.offset }
    val pushedOffset =
        nextVisibleHeaderOffset
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
    onMediaClick: (String) -> Unit,
    onMediaLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        rowItems.forEach { asset ->
            Box(modifier = Modifier.weight(1f)) {
                GalleryItem(
                    asset = asset,
                    isSelected = asset.id in selectedIds,
                    selectionModeActive = selectedIds.isNotEmpty(),
                    onMediaClick = onMediaClick,
                    onMediaLongClick = onMediaLongClick,
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
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
private fun RowScope.GridRemainderSpacer(itemCount: Int) {
    if (itemCount < GRID_COLUMNS) {
        repeat(GRID_COLUMNS - itemCount) {
            Spacer(modifier = Modifier.weight(1f))
        }
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
    modifier: Modifier = Modifier,
) {
    val padding by animateDpAsState(
        targetValue = if (isSelected) 12.dp else 0.dp,
        label = "selection_padding",
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) EnclaveNeoTokens.cornerRadius else 0.dp,
        label = "selection_corner_radius",
    )
    val tileShape = RoundedCornerShape(cornerRadius)

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onMediaClick(asset.id) },
                    onLongClick = { onMediaLongClick(asset.id) },
                ).padding(padding)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = EnclaveNeoTokens.borderWidth,
                            color = neoBorderColor(),
                            shape = tileShape,
                        )
                    } else {
                        Modifier
                    },
                ),
    ) {
        if (asset.mediaType == MediaCapability.VIDEO) {
            VideoPlayer(
                localUri = asset.localUri,
                contentDescription = asset.prompt,
                autoPlay = true,
                controlsEnabled = false,
                muted = true,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(tileShape),
            )
        } else if (asset.mediaType == MediaCapability.MUSIC) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(tileShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
            }
        } else {
            AsyncImage(
                model = asset.localUri,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(tileShape),
                contentScale = ContentScale.Crop,
            )
        }

        if (asset.albumId != null) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .neoSurface(
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            borderColor = neoBorderColor(),
                            withShadow = false,
                        ),
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Saved to album",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier
                            .padding(4.dp)
                            .size(16.dp),
                )
            }
        }

        if (selectionModeActive && asset.albumId == null) {
            NeoSelectionMark(
                selected = isSelected,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
            )
        }
    }
}

@Composable
private fun GeneratingPlaceholderItem(modifier: Modifier = Modifier) {
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)

    val transition = rememberInfiniteTransition(label = "studio_generating_shimmer")
    val shimmerProgress =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = GENERATING_SHIMMER_DURATION_MS, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "studio_generating_shimmer_progress",
        )

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .shimmerEffect(
                    progressState = shimmerProgress,
                    baseColor = baseColor,
                    highlightColor = highlightColor,
                ),
    )
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun GeneratingPlaceholderItemPreview() {
    EnclaveTheme(darkTheme = true) {
        Box(
            modifier =
                Modifier
                    .padding(20.dp)
                    .width(160.dp),
        ) {
            GeneratingPlaceholderItem()
        }
    }
}

@Composable
private fun EmptyStudioState(
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(top = topPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(72.dp)
                        .neoSurface(
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            borderColor = neoBorderColor(),
                            withShadow = true,
                            shadowColor = neoShadowColor(),
                            shadowOffset = neoButtonShadowOffset(),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No creations yet. Use the controls below to generate an image, video, or music track.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

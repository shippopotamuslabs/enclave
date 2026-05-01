package com.browntowndev.pocketcrew.feature.studio

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.browntowndev.pocketcrew.core.ui.component.VideoThumbnail
import com.browntowndev.pocketcrew.core.ui.component.dialog.AddAlbumDialog
import com.browntowndev.pocketcrew.core.ui.component.dialog.RenameAlbumDialog
import com.browntowndev.pocketcrew.core.ui.component.sheet.MoveToAlbumBottomSheet
import com.browntowndev.pocketcrew.core.ui.component.shimmerEffect
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.core.ui.theme.PurpleLightPrimary
import com.browntowndev.pocketcrew.core.ui.util.loadVideoFrame
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ALBUM_GRID_COLUMNS = 2
private const val ALBUM_COVER_GRID_COLUMNS = 2

@Composable
fun GalleryRoute(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String, String) -> Unit,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GalleryScreen(
        uiState = uiState,
        onBackClick = onNavigateBack,
        onMediaClick = onNavigateToDetail,
        onAddAlbum = viewModel::addAlbum,
        onRenameAlbum = viewModel::renameAlbum,
        onDeleteAlbums = viewModel::deleteAlbums,
        onShareMedia = viewModel::shareMedia,
        onMediaItemMeasured = viewModel::onMediaItemMeasured,
        onDeleteMedia = viewModel::deleteMedia,
        onMoveMedia = viewModel::moveMediaToAlbum,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    uiState: GalleryUiState,
    onBackClick: () -> Unit,
    onMediaClick: (String, String) -> Unit,
    onAddAlbum: (String) -> Unit,
    onRenameAlbum: (String, String) -> Unit,
    onDeleteAlbums: (Set<String>) -> Unit,
    onShareMedia: (Set<String>) -> Unit,
    onMediaItemMeasured: (String, Float) -> Unit,
    onDeleteMedia: (Set<String>) -> Unit,
    onMoveMedia: (Set<String>, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hazeState = rememberHazeState()
    var isAddAlbumDialogOpen by rememberSaveable { mutableStateOf(false) }
    var isRenameAlbumDialogOpen by rememberSaveable { mutableStateOf(false) }
    var selectedAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAlbumIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var selectedMediaItemIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var showMoveBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteAlbumsDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val selectedAlbum = remember(uiState.albums, selectedAlbumId) {
        uiState.albums.firstOrNull { album -> album.id == selectedAlbumId }
    }
    val videoItems = remember(uiState.albums) {
        uiState.albums
            .flatMap { album -> album.items }
            .filter { item -> item.mediaType == MediaCapability.VIDEO }
            .distinctBy { item -> item.id }
    }
    val videoThumbnailKey = remember(videoItems) {
        videoItems.joinToString(separator = "|") { item -> "${item.id}:${item.localUri}" }
    }
    val videoThumbnailCache by produceState(
        initialValue = VideoThumbnailCache(isLoading = true),
        key1 = videoThumbnailKey,
    ) {
        // Reset to loading state when the key changes to show placeholders while computing
        if (videoItems.isNotEmpty()) {
            value = VideoThumbnailCache(frames = value.frames, isLoading = true)
        }

        value = if (videoItems.isEmpty()) {
            VideoThumbnailCache(isLoading = false)
        } else {
            val frames = withContext(Dispatchers.IO) {
                videoItems.associate { item ->
                    item.id to loadVideoFrame(
                        localUri = item.localUri,
                        context = context,
                    )
                }
            }
            VideoThumbnailCache(
                frames = frames,
                isLoading = false,
            )
        }
    }
    val isPreparingGallery = uiState.isLoading || videoThumbnailCache.isLoading

    Scaffold(
        modifier = modifier,
        topBar = {
            val selectionCount = selectedMediaItemIds.size + selectedAlbumIds.size
            val isDefaultAlbumSelected = DEFAULT_GALLERY_ALBUM_ID in selectedAlbumIds
            GalleryTopBar(
                title = selectedAlbum?.name ?: "Gallery",
                selectedItemCount = selectionCount,
                selectedMediaItemCount = selectedMediaItemIds.size,
                showAddAlbumIcon = selectedAlbumId == null,
                showRenameAlbumIcon = selectedAlbumId != null && selectedAlbumId != DEFAULT_GALLERY_ALBUM_ID && selectedMediaItemIds.isEmpty(),
                showDeleteIcon = selectionCount > 0 && !isDefaultAlbumSelected,
                hazeState = hazeState,
                onBackClick = {
                    if (selectedMediaItemIds.isNotEmpty()) {
                        selectedMediaItemIds = emptySet()
                    } else if (selectedAlbumIds.isNotEmpty()) {
                        selectedAlbumIds = emptySet()
                    } else if (selectedAlbumId != null) {
                        selectedAlbumId = null
                    } else {
                        onBackClick()
                    }
                },
                onClearSelectionClick = {
                    selectedMediaItemIds = emptySet()
                    selectedAlbumIds = emptySet()
                },
                onAddAlbumClick = { isAddAlbumDialogOpen = true },
                onRenameClick = { isRenameAlbumDialogOpen = true },
                onShareClick = { onShareMedia(selectedMediaItemIds) },
                onMoveClick = { showMoveBottomSheet = true },
                onDeleteClick = {
                    if (selectedAlbumIds.isNotEmpty()) {
                        showDeleteAlbumsDialog = true
                    } else {
                        showDeleteDialog = true
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            AnimatedContent(
                targetState = selectedAlbum,
                label = "album_transition",
                contentKey = { it?.id ?: "root" },
                transitionSpec = {
                    if (targetState != null) {
                        (slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Up,
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
                        ) + fadeIn(tween(250))).togetherWith(
                            scaleOut(targetScale = 0.92f, animationSpec = tween(250)) + fadeOut(tween(250))
                        )
                    } else {
                        (scaleIn(initialScale = 0.92f, animationSpec = tween(250)) + fadeIn(tween(250))).togetherWith(
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Down,
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
                            ) + fadeOut(tween(250))
                        )
                    }
                }
            ) { targetAlbum ->
                if (isPreparingGallery && targetAlbum == null) {
                    AlbumSkeletonGrid(
                        topPadding = innerPadding.calculateTopPadding(),
                    )
                } else if (targetAlbum == null) {
                    AlbumGrid(
                        albums = uiState.albums,
                        selectedAlbumIds = selectedAlbumIds,
                        videoThumbnails = videoThumbnailCache.frames,
                        onAlbumClick = { albumId ->
                            if (selectedAlbumIds.isNotEmpty()) {
                                selectedAlbumIds = if (albumId in selectedAlbumIds) selectedAlbumIds - albumId else selectedAlbumIds + albumId
                            } else {
                                selectedAlbumId = albumId
                            }
                        },
                        onAlbumSelectionToggled = { albumId ->
                            selectedAlbumIds = if (albumId in selectedAlbumIds) selectedAlbumIds - albumId else selectedAlbumIds + albumId
                        },
                        hazeState = hazeState,
                        topPadding = innerPadding.calculateTopPadding(),
                    )
                } else {
                    AlbumItemGrid(
                        album = targetAlbum,
                        selectedMediaItemIds = selectedMediaItemIds,
                        videoThumbnails = videoThumbnailCache.frames,
                        onMediaItemMeasured = onMediaItemMeasured,
                        onMediaItemClick = { itemId ->
                            onMediaClick(targetAlbum.id, itemId)
                        },
                        onMediaItemSelectionToggled = { itemId ->
                            selectedMediaItemIds = if (itemId in selectedMediaItemIds) {
                                selectedMediaItemIds - itemId
                            } else {
                                selectedMediaItemIds + itemId
                            }
                        },
                        hazeState = hazeState,
                        topPadding = innerPadding.calculateTopPadding(),
                    )
                }
            }
        }
    }

    if (isAddAlbumDialogOpen) {
        AddAlbumDialog(
            onDismiss = { isAddAlbumDialogOpen = false },
            onConfirm = { albumName ->
                onAddAlbum(albumName)
                isAddAlbumDialogOpen = false
            },
        )
    }

    if (isRenameAlbumDialogOpen && selectedAlbum != null) {
        RenameAlbumDialog(
            currentName = selectedAlbum.name,
            onDismiss = { isRenameAlbumDialogOpen = false },
            onConfirm = { newName ->
                onRenameAlbum(selectedAlbum.id, newName)
                isRenameAlbumDialogOpen = false
            },
        )
    }

    if (showMoveBottomSheet) {
        MoveToAlbumBottomSheet(
            albums = uiState.albums.map { it.toAlbumSelectionItem() },
            onDismiss = { showMoveBottomSheet = false },
            onMoveToAlbum = { targetAlbumId ->
                onMoveMedia(selectedMediaItemIds, targetAlbumId)
                selectedMediaItemIds = emptySet()
                showMoveBottomSheet = false
            },
            onAddAlbum = { newAlbumName ->
                onAddAlbum(newAlbumName)
            },
            title = "Move to Album"
        )
    }

    if (showDeleteDialog) {
        DeleteDialog(
            itemCount = selectedMediaItemIds.size,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                onDeleteMedia(selectedMediaItemIds)
                selectedMediaItemIds = emptySet()
                showDeleteDialog = false
            }
        )
    }

    if (showDeleteAlbumsDialog) {
        DeleteAlbumsDialog(
            itemCount = selectedAlbumIds.size,
            onDismiss = { showDeleteAlbumsDialog = false },
            onConfirm = {
                onDeleteAlbums(selectedAlbumIds)
                selectedAlbumIds = emptySet()
                showDeleteAlbumsDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryTopBar(
    title: String,
    selectedItemCount: Int,
    selectedMediaItemCount: Int,
    showAddAlbumIcon: Boolean,
    showRenameAlbumIcon: Boolean,
    showDeleteIcon: Boolean,
    hazeState: HazeState,
    onBackClick: () -> Unit,
    onClearSelectionClick: () -> Unit,
    onAddAlbumClick: () -> Unit,
    onRenameClick: () -> Unit,
    onShareClick: () -> Unit,
    onMoveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
        modifier = modifier.hazeEffect(state = hazeState) {
            blurRadius = 24.dp
            tints = listOf(HazeTint(Color.Black.copy(alpha = 0.4f)))
            noiseFactor = 0.15f
        },
        navigationIcon = {
            if (selectedItemCount > 0) {
                IconButton(onClick = onClearSelectionClick) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear selection",
                    )
                }
            } else {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            }
        },
        title = {
            Text(
                text = if (selectedItemCount > 0) "$selectedItemCount Selected" else title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        actions = {
            if (selectedItemCount > 0) {
                if (selectedMediaItemCount > 0) {
                    IconButton(onClick = onShareClick) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                        )
                    }
                }
                IconButton(onClick = onMoveClick) {
                    Icon(
                        imageVector = Icons.Default.DriveFileMove,
                        contentDescription = "Move",
                    )
                }
                if (showDeleteIcon) {
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                        )
                    }
                }
            } else {
                if (showRenameAlbumIcon) {
                    IconButton(onClick = onRenameClick) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename album",
                        )
                    }
                }
                if (showAddAlbumIcon) {
                    IconButton(onClick = onAddAlbumClick) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add album",
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun AlbumGrid(
    albums: List<GalleryAlbumUi>,
    selectedAlbumIds: Set<String>,
    videoThumbnails: Map<String, Bitmap?>,
    onAlbumClick: (String) -> Unit,
    onAlbumSelectionToggled: (String) -> Unit,
    hazeState: HazeState,
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(ALBUM_GRID_COLUMNS),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp + topPadding,
            end = 16.dp,
            bottom = 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(
            items = albums,
            key = { album -> album.id },
        ) { album ->
            AlbumCard(
                album = album,
                isSelected = album.id in selectedAlbumIds,
                selectionModeActive = selectedAlbumIds.isNotEmpty(),
                videoThumbnails = videoThumbnails,
                onClick = { onAlbumClick(album.id) },
                onSelectionToggled = { onAlbumSelectionToggled(album.id) },
                hazeState = hazeState,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    album: GalleryAlbumUi,
    isSelected: Boolean,
    selectionModeActive: Boolean,
    videoThumbnails: Map<String, Bitmap?>,
    onClick: () -> Unit,
    onSelectionToggled: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val padding by animateDpAsState(
        targetValue = if (isSelected) 12.dp else 0.dp,
        label = "selection_padding"
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 0.dp,
        label = "selection_corner_radius"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .hazeSource(state = hazeState)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onSelectionToggled,
            )
            .padding(padding),
    ) {
        Box {
            AlbumCover(
                items = album.coverItems,
                videoThumbnails = videoThumbnails,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(cornerRadius)),
            )
            if (selectionModeActive) {
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
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${album.itemCount} ${if (album.itemCount == 1) "item" else "items"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AlbumCover(
    items: List<StudioMediaUi>,
    videoThumbnails: Map<String, Bitmap?>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        if (items.isEmpty()) {
            EmptyAlbumCover()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(ALBUM_COVER_GRID_COLUMNS),
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = items,
                    key = { item -> item.id },
                ) { item ->
                    AlbumCoverItem(
                        item = item,
                        videoThumbnail = videoThumbnails[item.id],
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyAlbumCover(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Image,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AlbumCoverItem(
    item: StudioMediaUi,
    videoThumbnail: Bitmap?,
    modifier: Modifier = Modifier,
) {
    val itemModifier = modifier
        .aspectRatio(1f)
        .clip(RoundedCornerShape(8.dp))

    if (item.mediaType == MediaCapability.VIDEO) {
        VideoThumbnail(
            videoUri = item.localUri,
            thumbnail = videoThumbnail,
            contentDescription = item.prompt,
            modifier = itemModifier,
        )
    } else if (item.mediaType == MediaCapability.MUSIC) {
        Box(
            modifier = itemModifier.background(
                Brush.verticalGradient(
                    colors = listOf<Color>(PurpleLightPrimary.copy(alpha = 0.3f), Color.Black)
                )
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    } else {
        AsyncImage(
            model = item.localUri,
            contentDescription = null,
            modifier = itemModifier,
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun AlbumItemGrid(
    album: GalleryAlbumUi,
    selectedMediaItemIds: Set<String>,
    videoThumbnails: Map<String, Bitmap?>,
    onMediaItemMeasured: (String, Float) -> Unit,
    onMediaItemClick: (String) -> Unit,
    onMediaItemSelectionToggled: (String) -> Unit,
    hazeState: HazeState,
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    if (album.items.isEmpty()) {
        EmptyAlbumContent(modifier = modifier.fillMaxSize())
        return
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(ALBUM_GRID_COLUMNS),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding),
    ) {
        items(
            items = album.items,
            key = { item -> item.id },
            contentType = { "media_item" },
        ) { item ->
            AlbumMediaItem(
                item = item,
                isSelected = item.id in selectedMediaItemIds,
                selectionModeActive = selectedMediaItemIds.isNotEmpty(),
                videoThumbnail = videoThumbnails[item.id],
                onMediaItemMeasured = onMediaItemMeasured,
                onClick = { onMediaItemClick(item.id) },
                onSelectionToggled = { onMediaItemSelectionToggled(item.id) },
                hazeState = hazeState,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumMediaItem(
    item: StudioMediaUi,
    isSelected: Boolean,
    selectionModeActive: Boolean,
    videoThumbnail: Bitmap?,
    onMediaItemMeasured: (String, Float) -> Unit,
    onClick: () -> Unit,
    onSelectionToggled: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
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
            .fillMaxWidth()
            .aspectRatio(item.aspectRatio ?: 1.0f)
            .hazeSource(state = hazeState)
            .combinedClickable(
                onClick = {
                    if (selectionModeActive) {
                        onSelectionToggled()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    onSelectionToggled()
                }
            )
            .padding(padding),
    ) {
        if (item.mediaType == MediaCapability.VIDEO) {
            VideoThumbnail(
                videoUri = item.localUri,
                thumbnail = videoThumbnail,
                contentDescription = item.prompt,
                onThumbnailMeasured = { aspectRatio ->
                    onMediaItemMeasured(item.id, aspectRatio)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius)),
            )
        } else if (item.mediaType == MediaCapability.MUSIC) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf<Color>(PurpleLightPrimary.copy(alpha = 0.3f), Color.Black)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        } else {
            AsyncImage(
                model = item.localUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius)),
                contentScale = ContentScale.Crop,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Success) {
                        val size = state.painter.intrinsicSize
                        if (size.width > 0 && size.height > 0) {
                            onMediaItemMeasured(item.id, size.width / size.height)
                        }
                    }
                },
            )
        }
        Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.BottomEnd),
        ) {
            Icon(
                imageVector = when (item.mediaType) {
                    MediaCapability.IMAGE -> Icons.Default.Image
                    MediaCapability.VIDEO -> Icons.Default.Videocam
                    else -> Icons.Default.MusicNote
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .padding(4.dp)
                    .size(16.dp),
            )
        }

        if (selectionModeActive) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
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

private data class VideoThumbnailCache(
    val frames: Map<String, Bitmap?> = emptyMap(),
    val isLoading: Boolean = true,
)

@Composable
private fun AlbumSkeletonGrid(
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "gallery_shimmer")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1500,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "gallery_shimmer_progress",
    )
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)

    LazyVerticalGrid(
        columns = GridCells.Fixed(ALBUM_GRID_COLUMNS),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp + topPadding,
            end = 16.dp,
            bottom = 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(count = 6) {
            AlbumSkeletonItem(
                progress = progress,
                baseColor = baseColor,
                highlightColor = highlightColor,
            )
        }
    }
}

@Composable
private fun AlbumSkeletonItem(
    progress: State<Float>,
    baseColor: Color,
    highlightColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .shimmerEffect(
                    progressState = progress,
                    baseColor = baseColor,
                    highlightColor = highlightColor,
                ),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth(0.72f)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmerEffect(
                    progressState = progress,
                    baseColor = baseColor,
                    highlightColor = highlightColor,
                ),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth(0.44f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmerEffect(
                    progressState = progress,
                    baseColor = baseColor,
                    highlightColor = highlightColor,
                ),
        )
    }
}

@Composable
private fun EmptyAlbumContent(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No items yet",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


@Composable
private fun DeleteDialog(
    itemCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Media") },
        text = { Text("Are you sure you want to delete $itemCount selected items? This action cannot be undone.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}

@Composable
private fun DeleteAlbumsDialog(
    itemCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Albums") },
        text = { Text("Delete $itemCount album(s)? Media inside will be moved to the Default Album.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}

@Preview
@Composable
private fun GalleryScreenPopulatedPreview() {
    PocketCrewTheme {
        GalleryScreen(
            uiState = GalleryUiState(
                albums = listOf(
                    GalleryAlbumUi(
                        id = DEFAULT_GALLERY_ALBUM_ID,
                        name = "Default Album",
                        items = previewGalleryItems(),
                    ),
                    GalleryAlbumUi(
                        id = "album-1",
                        name = "Moodboards",
                        items = emptyList(),
                    ),
                ),
            ),
            onBackClick = {},
            onMediaClick = { _, _ -> },
            onAddAlbum = {},
            onRenameAlbum = { _, _ -> },
            onDeleteAlbums = {},
            onShareMedia = {},
            onDeleteMedia = {},
            onMoveMedia = { _, _ -> },
            onMediaItemMeasured = { _, _ -> },
        )
    }
}

@Preview
@Composable
private fun GalleryScreenDarkPreview() {
    PocketCrewTheme(darkTheme = true) {
        GalleryScreen(
            uiState = GalleryUiState(
                albums = listOf(
                    GalleryAlbumUi(
                        id = DEFAULT_GALLERY_ALBUM_ID,
                        name = "Default Album",
                        items = previewGalleryItems(),
                    ),
                ),
            ),
            onBackClick = {},
            onMediaClick = { _, _ -> },
            onAddAlbum = {},
            onRenameAlbum = { _, _ -> },
            onDeleteAlbums = {},
            onShareMedia = {},
            onDeleteMedia = {},
            onMoveMedia = { _, _ -> },
            onMediaItemMeasured = { _, _ -> },
        )
    }
}

private fun previewGalleryItems(): List<StudioMediaUi> =
    (1..4).map { index ->
        StudioMediaUi(
            id = "preview-$index",
            localUri = "",
            prompt = "Preview",
            mediaType = MediaCapability.IMAGE,
            createdAt = index.toLong(),
        )
    }

package com.browntowndev.pocketcrew.feature.studio

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
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
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private const val ALBUM_GRID_COLUMNS = 2
private const val ALBUM_COVER_GRID_COLUMNS = 2

@Composable
fun GalleryRoute(
    onNavigateBack: () -> Unit,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GalleryScreen(
        uiState = uiState,
        onBackClick = onNavigateBack,
        onAddAlbum = viewModel::addAlbum,
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
    onAddAlbum: (String) -> Unit,
    onMediaItemMeasured: (String, Float) -> Unit,
    onDeleteMedia: (Set<String>) -> Unit,
    onMoveMedia: (Set<String>, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hazeState = rememberHazeState()
    var isAddAlbumDialogOpen by rememberSaveable { mutableStateOf(false) }
    var selectedAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMediaItemIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var showMoveBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    
    val selectedAlbum = remember(uiState.albums, selectedAlbumId) {
        uiState.albums.firstOrNull { album -> album.id == selectedAlbumId }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            GalleryTopBar(
                title = selectedAlbum?.name ?: "Gallery",
                selectedItemCount = selectedMediaItemIds.size,
                showAddAlbumIcon = selectedAlbumId == null,
                hazeState = hazeState,
                onBackClick = {
                    if (selectedMediaItemIds.isNotEmpty()) {
                        selectedMediaItemIds = emptySet()
                    } else if (selectedAlbumId != null) {
                        selectedAlbumId = null
                    } else {
                        onBackClick()
                    }
                },
                onClearSelectionClick = { selectedMediaItemIds = emptySet() },
                onAddAlbumClick = { isAddAlbumDialogOpen = true },
                onMoveClick = { showMoveBottomSheet = true },
                onDeleteClick = { showDeleteDialog = true },
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
                if (targetAlbum == null) {
                    AlbumGrid(
                        albums = uiState.albums,
                        onAlbumClick = { albumId -> selectedAlbumId = albumId },
                        hazeState = hazeState,
                        topPadding = innerPadding.calculateTopPadding(),
                    )
                } else {
                    AlbumItemGrid(
                        album = targetAlbum,
                        selectedMediaItemIds = selectedMediaItemIds,
                        onMediaItemMeasured = onMediaItemMeasured,
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

    if (showMoveBottomSheet) {
        MoveBottomSheet(
            albums = uiState.albums,
            onDismiss = { showMoveBottomSheet = false },
            onMoveToAlbum = { targetAlbumId ->
                onMoveMedia(selectedMediaItemIds, targetAlbumId)
                selectedMediaItemIds = emptySet()
                showMoveBottomSheet = false
            },
            onAddAlbum = { newAlbumName ->
                onAddAlbum(newAlbumName)
                // We don't automatically move to it here because creating an album is async,
                // but a more robust implementation might await creation and then move.
            }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryTopBar(
    title: String,
    selectedItemCount: Int,
    showAddAlbumIcon: Boolean,
    hazeState: HazeState,
    onBackClick: () -> Unit,
    onClearSelectionClick: () -> Unit,
    onAddAlbumClick: () -> Unit,
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
                IconButton(onClick = onMoveClick) {
                    Icon(
                        imageVector = Icons.Default.DriveFileMove,
                        contentDescription = "Move",
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                    )
                }
            } else if (showAddAlbumIcon) {
                IconButton(onClick = onAddAlbumClick) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add album",
                    )
                }
            }
        },
    )
}

@Composable
private fun AlbumGrid(
    albums: List<GalleryAlbumUi>,
    onAlbumClick: (String) -> Unit,
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
                onClick = { onAlbumClick(album.id) },
                hazeState = hazeState,
            )
        }
    }
}

@Composable
private fun AlbumCard(
    album: GalleryAlbumUi,
    onClick: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .hazeSource(state = hazeState)
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        AlbumCover(
            items = album.coverItems,
            modifier = Modifier.fillMaxWidth(),
        )
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
                    AlbumCoverItem(item = item)
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
    modifier: Modifier = Modifier,
) {
    val itemModifier = modifier
        .aspectRatio(1f)
        .clip(RoundedCornerShape(8.dp))

    if (item.mediaType == MediaCapability.VIDEO) {
        StudioVideoPlayer(
            localUri = item.localUri,
            contentDescription = item.prompt,
            autoPlay = true,
            controlsEnabled = false,
            muted = true,
            modifier = itemModifier,
        )
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
    onMediaItemMeasured: (String, Float) -> Unit,
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
                onMediaItemMeasured = onMediaItemMeasured,
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
    onMediaItemMeasured: (String, Float) -> Unit,
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
                    }
                },
                onLongClick = {
                    onSelectionToggled()
                }
            )
            .padding(padding),
    ) {
        if (item.mediaType == MediaCapability.VIDEO) {
            StudioVideoPlayer(
                localUri = item.localUri,
                contentDescription = item.prompt,
                autoPlay = true,
                controlsEnabled = false,
                muted = true,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius)),
            )
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
                imageVector = if (item.mediaType == MediaCapability.IMAGE) {
                    Icons.Default.Image
                } else {
                    Icons.Default.Videocam
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
private fun AddAlbumDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var albumName by rememberSaveable { mutableStateOf("") }
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
                text = "Move to Album",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            LazyColumn {
                item {
                    ListItem(
                        headlineContent = { Text("New Album") },
                        leadingContent = {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        },
                        modifier = Modifier.clickable { isAddingNewAlbum = true }
                    )
                }
                items(
                    items = albums,
                    key = { album -> album.id }
                ) { album ->
                    ListItem(
                        headlineContent = { Text(album.name) },
                        supportingContent = { Text("${album.itemCount} items") },
                        modifier = Modifier.clickable { onMoveToAlbum(album.id) }
                    )
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
            onAddAlbum = {},
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
            onAddAlbum = {},
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

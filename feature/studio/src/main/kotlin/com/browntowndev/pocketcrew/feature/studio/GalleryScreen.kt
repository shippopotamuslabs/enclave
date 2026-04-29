package com.browntowndev.pocketcrew.feature.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability

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
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    uiState: GalleryUiState,
    onBackClick: () -> Unit,
    onAddAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isAddAlbumDialogOpen by rememberSaveable { mutableStateOf(false) }
    var selectedAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedAlbum = remember(uiState.albums, selectedAlbumId) {
        uiState.albums.firstOrNull { album -> album.id == selectedAlbumId }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            GalleryTopBar(
                title = selectedAlbum?.name ?: "Gallery",
                onBackClick = {
                    if (selectedAlbumId != null) {
                        selectedAlbumId = null
                    } else {
                        onBackClick()
                    }
                },
                onAddAlbumClick = { isAddAlbumDialogOpen = true },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (selectedAlbum == null) {
                AlbumGrid(
                    albums = uiState.albums,
                    onAlbumClick = { albumId -> selectedAlbumId = albumId },
                )
            } else {
                AlbumItemGrid(album = selectedAlbum)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryTopBar(
    title: String,
    onBackClick: () -> Unit,
    onAddAlbumClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        actions = {
            IconButton(onClick = onAddAlbumClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add album",
                )
            }
        },
    )
}

@Composable
private fun AlbumGrid(
    albums: List<GalleryAlbumUi>,
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(ALBUM_GRID_COLUMNS),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
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
            )
        }
    }
}

@Composable
private fun AlbumCard(
    album: GalleryAlbumUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        if (items.isEmpty()) {
            EmptyAlbumCover()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(ALBUM_COVER_GRID_COLUMNS),
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize(),
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
    Box(modifier = modifier.aspectRatio(1f)) {
        AsyncImage(
            model = item.localUri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun AlbumItemGrid(
    album: GalleryAlbumUi,
    modifier: Modifier = Modifier,
) {
    if (album.items.isEmpty()) {
        EmptyAlbumContent(modifier = modifier.fillMaxSize())
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(ALBUM_GRID_COLUMNS),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = album.items,
            key = { item -> item.id },
        ) { item ->
            AlbumMediaItem(item = item)
        }
    }
}

@Composable
private fun AlbumMediaItem(
    item: StudioMediaUi,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.localUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
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

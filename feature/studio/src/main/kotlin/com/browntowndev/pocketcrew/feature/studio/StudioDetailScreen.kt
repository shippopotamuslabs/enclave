package com.browntowndev.pocketcrew.feature.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.media.SaveMediaToGalleryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class StudioDetailUiState {
    object Loading : StudioDetailUiState()
    data class Success(val assets: List<StudioMediaUi>, val initialIndex: Int) : StudioDetailUiState()
    data class Error(val message: String) : StudioDetailUiState()
}

@HiltViewModel
class StudioDetailViewModel @Inject constructor(
    private val studioRepository: StudioRepositoryPort,
    private val saveMediaToGalleryUseCase: SaveMediaToGalleryUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow<StudioDetailUiState>(StudioDetailUiState.Loading)
    val uiState = _uiState.asStateFlow()

    fun loadAsset(id: String) {
        viewModelScope.launch {
            studioRepository.observeAllMedia().collect { assets ->
                val uiAssets = assets.map { it.toUi() }.reversed()
                val initialIndex = uiAssets.indexOfFirst { it.id == id }.coerceAtLeast(0)
                _uiState.value = if (uiAssets.isNotEmpty()) {
                    StudioDetailUiState.Success(uiAssets, initialIndex)
                } else {
                    StudioDetailUiState.Error("Asset not found")
                }
            }
        }
    }

    fun deleteAsset(id: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            studioRepository.deleteMedia(id)
            onDeleted()
        }
    }

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
    onNavigateBack: () -> Unit,
    onEditMedia: (String) -> Unit,
    onAnimateMedia: (String) -> Unit,
    viewModel: StudioDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(assetId) {
        viewModel.loadAsset(assetId)
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = { StudioDetailTopBar(onNavigateBack = onNavigateBack) }
    ) { padding ->
        StudioDetailStateContent(
            uiState = uiState,
            onEdit = { asset ->
                onEditMedia(asset.id)
                onNavigateBack()
            },
            onAnimate = { asset ->
                onAnimateMedia(asset.id)
                onNavigateBack()
            },
            onSave = viewModel::saveToGallery,
            onDelete = { asset ->
                viewModel.deleteAsset(asset.id, onNavigateBack)
            },
            modifier = Modifier.padding(padding)
        )
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
private fun StudioDetailStateContent(
    uiState: StudioDetailUiState,
    onEdit: (StudioMediaUi) -> Unit,
    onAnimate: (StudioMediaUi) -> Unit,
    onSave: (StudioMediaUi) -> Unit,
    onDelete: (StudioMediaUi) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is StudioDetailUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is StudioDetailUiState.Error -> {
                Text(
                    text = uiState.message,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is StudioDetailUiState.Success -> {
                DetailContent(
                    assets = uiState.assets,
                    initialIndex = uiState.initialIndex,
                    onEdit = onEdit,
                    onAnimate = onAnimate,
                    onSave = onSave,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    assets: List<StudioMediaUi>,
    initialIndex: Int,
    onEdit: (StudioMediaUi) -> Unit,
    onAnimate: (StudioMediaUi) -> Unit,
    onSave: (StudioMediaUi) -> Unit,
    onDelete: (StudioMediaUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { assets.size }
    )

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1
    ) { page ->
        val asset = assets[page]
        Box(modifier = Modifier.fillMaxSize()) {
            DetailMedia(asset = asset)
            DetailActionsOverlay(
                asset = asset,
                onEdit = { onEdit(asset) },
                onAnimate = { onAnimate(asset) },
                onSave = { onSave(asset) },
                onDelete = { onDelete(asset) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun DetailMedia(
    asset: StudioMediaUi,
    modifier: Modifier = Modifier
) {
    val state = rememberZoomableImageState()
    ZoomableAsyncImage(
        model = asset.localUri,
        contentDescription = asset.prompt,
        state = state,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun DetailActionsOverlay(
    asset: StudioMediaUi,
    onEdit: () -> Unit,
    onAnimate: () -> Unit,
    onSave: () -> Unit,
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
            onDelete = onDelete
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DetailActionRow(
    mediaType: MediaCapability,
    onEdit: () -> Unit,
    onAnimate: () -> Unit,
    onSave: () -> Unit,
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
            ActionItem(Icons.Default.Movie, "Animate", onAnimate)
        }
        ActionItem(Icons.Default.Download, "Save", onSave)
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

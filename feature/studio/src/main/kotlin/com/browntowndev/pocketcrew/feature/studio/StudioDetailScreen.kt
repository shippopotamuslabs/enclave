package com.browntowndev.pocketcrew.feature.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.media.SaveMediaToGalleryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class StudioDetailUiState {
    object Loading : StudioDetailUiState()
    data class Success(val asset: StudioMediaAsset) : StudioDetailUiState()
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
            val asset = studioRepository.getMediaById(id)
            _uiState.value = if (asset != null) {
                StudioDetailUiState.Success(asset)
            } else {
                StudioDetailUiState.Error("Asset not found")
            }
        }
    }

    fun deleteAsset(id: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            studioRepository.deleteMedia(id)
            onDeleted()
        }
    }

    fun saveToGallery(asset: StudioMediaAsset) {
        viewModelScope.launch {
            val mediaType = if (asset.mediaType == "IMAGE") MediaCapability.IMAGE else MediaCapability.VIDEO
            saveMediaToGalleryUseCase(asset.localUri, mediaType)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioDetailScreen(
    assetId: String,
    onNavigateBack: () -> Unit,
    onEditMedia: (StudioMediaAsset) -> Unit,
    onAnimateMedia: (StudioMediaAsset) -> Unit,
    viewModel: StudioDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(assetId) {
        viewModel.loadAsset(assetId)
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is StudioDetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is StudioDetailUiState.Error -> {
                    Text(state.message, color = Color.White, modifier = Modifier.align(Alignment.Center))
                }
                is StudioDetailUiState.Success -> {
                    DetailContent(
                        asset = state.asset,
                        onEdit = { 
                            onEditMedia(state.asset)
                            onNavigateBack()
                        },
                        onAnimate = {
                            onAnimateMedia(state.asset)
                            onNavigateBack()
                        },
                        onSave = { viewModel.saveToGallery(state.asset) },
                        onDelete = {
                            viewModel.deleteAsset(state.asset.id, onNavigateBack)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailContent(
    asset: StudioMediaAsset,
    onEdit: () -> Unit,
    onAnimate: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Main Media
        AsyncImage(
            model = asset.localUri,
            contentDescription = asset.prompt,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Bottom Actions Overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionItem(Icons.Default.Edit, "Edit", onEdit)
                if (asset.mediaType == "IMAGE") {
                    ActionItem(Icons.Default.Movie, "Animate", onAnimate)
                }
                ActionItem(Icons.Default.Download, "Save", onSave)
                ActionItem(Icons.Default.Delete, "Delete", onDelete, isDestructive = true)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
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

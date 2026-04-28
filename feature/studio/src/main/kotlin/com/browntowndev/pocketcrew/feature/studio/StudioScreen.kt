package com.browntowndev.pocketcrew.feature.studio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.browntowndev.pocketcrew.core.ui.component.MediaModeToggle
import com.browntowndev.pocketcrew.core.ui.component.StandardTrailingAction
import com.browntowndev.pocketcrew.core.ui.component.UniversalInputBar
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.VideoGenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.VisualGenerationSettings
import com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset
import com.browntowndev.pocketcrew.feature.studio.components.StudioOptionsBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    onNavigateToHistory: () -> Unit,
    onMediaClick: (StudioMediaAsset) -> Unit,
    viewModel: MultimodalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onUpdateReferenceImage(it.toString()) }
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
            // Main Content: Gallery Grid
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.gallery.isEmpty()) {
                    EmptyStudioState()
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.gallery) { asset ->
                            GalleryItem(asset, onMediaClick)
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

                            val icon = if (uiState.isGenerating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send
                            val containerColor = if (uiState.isGenerating) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary
                            val contentColor = if (uiState.isGenerating) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary

                            StandardTrailingAction(
                                icon = icon,
                                onClick = { viewModel.generate() },
                                containerColor = containerColor,
                                contentColor = contentColor,
                                enabled = uiState.prompt.isNotBlank() || uiState.isGenerating
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
    asset: StudioMediaAsset,
    onMediaClick: (StudioMediaAsset) -> Unit
) {
    Card(
        onClick = { onMediaClick(asset) },
        shape = RoundedCornerShape(12.dp),
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
                    imageVector = if (asset.mediaType == "IMAGE") Icons.Default.Image else Icons.Default.Videocam,
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

@file:OptIn(ExperimentalStdlibApi::class)

package com.browntowndev.enclave.feature.studio

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.enclave.core.ui.component.neo.NeoAppBarChrome
import com.browntowndev.enclave.core.ui.component.neo.NeoIconButton
import com.browntowndev.enclave.core.ui.component.sheet.MoveToAlbumBottomSheet
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.VisualGenerationSettings
import com.browntowndev.enclave.feature.studio.components.StudioOptionsBottomSheet
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.ExperimentalStdlibApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StudioScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToGallery: () -> Unit,
    onMediaClick: (String) -> Unit,
    onShowSnackbar: (String, String?) -> Unit,
    viewModel: MultimodalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val visibleGallery = remember(uiState.gallery) { uiState.gallery }
    val groupedGallery = uiState.groupedGallery
    val galleryRowKeys =
        remember(groupedGallery) {
            groupedGallery.flatMap { group ->
                group.mediaItems.chunked(GRID_COLUMNS).map(::studioGalleryRowKey)
            }
        }
    val generationPlaceholderCount =
        (uiState.settings as? ImageGenerationSettings)
            ?.generationCount
            ?.coerceAtLeast(1)
            ?: 1
    var scrollOnGenerationStart by remember { mutableStateOf(false) }
    val imagePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri ->
            uri?.let { viewModel.promptCoordinator.onUpdateReferenceImage(it.toString()) }
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                viewModel.onMicClick()
            } else {
                Toast.makeText(context, "Microphone permission is required for speech-to-text", Toast.LENGTH_SHORT).show()
            }
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
                val isNearBottom =
                    isGenerativeScrollThresholdVisible(
                        galleryRowKeys = latestGalleryRowKeys,
                        visibleItemKeys = visibleKeys,
                    )

                if (isNearBottom && latestGallery.isNotEmpty()) {
                    latestGallery.last().id to latestGallery.size
                } else {
                    null
                }
            }.distinctUntilChanged()
            .collect { scrollTrigger ->
                if (scrollTrigger != null) {
                    viewModel.onGenerativeScrollThresholdVisible(
                        anchorAssetId = scrollTrigger.first,
                    )
                }
            }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            StudioTopBar(
                selectedCount = uiState.selectedMediaItemIds.size,
                onClearSelection = viewModel::clearMediaSelection,
                onSaveClick = viewModel::onToggleSaveBottomSheet,
                onShareClick = { viewModel.mediaActionsCoordinator.shareMedia(uiState.selectedMediaItemIds) },
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToGallery = onNavigateToGallery,
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize(),
        ) {
            StudioGalleryPane(
                groupedGallery = groupedGallery,
                selectedIds = uiState.selectedMediaItemIds,
                isGenerating = uiState.isGenerating,
                activeGenerationPrompt = uiState.activeGenerationPrompt,
                generationPlaceholderCount = generationPlaceholderCount,
                listState = listState,
                topPadding = padding.calculateTopPadding(),
                onMediaClick = { id ->
                    if (uiState.selectedMediaItemIds.isNotEmpty()) {
                        val asset = groupedGallery.flatMap { it.mediaItems }.find { it.id == id }
                        if (asset?.albumId == null) {
                            viewModel.toggleMediaSelection(id)
                        }
                    } else {
                        onMediaClick(id)
                    }
                },
                onMediaLongClick = { id ->
                    val asset = groupedGallery.flatMap { it.mediaItems }.find { it.id == id }
                    if (asset?.albumId == null) {
                        viewModel.toggleMediaSelection(id)
                    }
                },
                modifier = Modifier.weight(1f),
            )

            StudioInputDock(
                prompt = uiState.prompt,
                speechState = uiState.speechState,
                mediaType = uiState.mediaType,
                referenceImageUri = (uiState.settings as? VisualGenerationSettings)?.referenceImageUri,
                referenceMediaType = uiState.referenceMediaType,
                isGenerating = uiState.isGenerating,
                isContinualGenerationActive = uiState.isContinualGenerationActive,
                hasImageEditModel = uiState.hasImageEditModel,
                hasImageToVideoModel = uiState.hasImageToVideoModel,
                onPromptChange = viewModel.promptCoordinator::onPromptChange,
                onReferenceImageClick = { imagePickerLauncher.launch("image/*") },
                onClearReferenceImage = { viewModel.promptCoordinator.onUpdateReferenceImage(null) },
                onSettingsClick = viewModel.promptCoordinator::onSettingsToggle,
                onMicClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.onMicClick()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onGenerateClick = {
                    if (!uiState.isGenerating && !uiState.isContinualGenerationActive) {
                        scrollOnGenerationStart = true
                    }
                    viewModel.generate()
                },
            )
        }
    }

    if (uiState.isSettingsOpen) {
        StudioOptionsBottomSheet(
            state = uiState,
            onDismiss = viewModel.promptCoordinator::onSettingsToggle,
            onUpdateSettings = viewModel.promptCoordinator::onUpdateSettings,
            onContinualModeChange = viewModel.promptCoordinator::onContinualModeToggle,
            onMediaTypeChange = viewModel.promptCoordinator::onMediaTypeChange,
            onTemplateSelected = viewModel.promptCoordinator::onTemplateSelected,
        )
    }

    if (uiState.isSaveBottomSheetOpen) {
        MoveToAlbumBottomSheet(
            albums = uiState.albums.map { it.toAlbumSelectionItem() },
            onDismiss = viewModel::onToggleSaveBottomSheet,
            onMoveToAlbum = viewModel::onSaveSelectedMediaToAlbum,
            onAddAlbum = viewModel::onAddAlbum,
        )
    }
}

@Composable
private fun StudioTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NeoAppBarChrome(modifier = modifier) {
        if (selectedCount > 0) {
            NeoIconButton(
                onClick = onClearSelection,
                icon = Icons.Default.Close,
                contentDescription = "Clear selection",
            )
        } else {
            NeoIconButton(
                onClick = onNavigateToHistory,
                icon = Icons.Default.Menu,
                contentDescription = "Open history",
            )
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (selectedCount > 0) "$selectedCount Selected" else "Studio",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (selectedCount > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.rowSpacing)) {
                NeoIconButton(
                    onClick = onShareClick,
                    icon = Icons.Default.Share,
                    contentDescription = "Share selected items",
                )
                NeoIconButton(
                    onClick = onSaveClick,
                    icon = Icons.AutoMirrored.Filled.DriveFileMove,
                    contentDescription = "Save to album",
                )
            }
        } else {
            NeoIconButton(
                onClick = onNavigateToGallery,
                icon = Icons.Default.PhotoLibrary,
                contentDescription = "Open gallery",
            )
        }
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
    val visibleGalleryRowIndexes =
        visibleItemKeys.mapNotNull { visibleKey ->
            galleryRowKeys.indexOf(visibleKey).takeIf { index -> index >= 0 }
        }

    return visibleGalleryRowIndexes.any { index -> index >= triggerRowIndex }
}

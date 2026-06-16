@file:OptIn(ExperimentalStdlibApi::class)

package com.browntowndev.enclave.feature.studio

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.enclave.core.ui.component.neo.NeoAppBarChrome
import com.browntowndev.enclave.core.ui.component.neo.NeoIconButton
import com.browntowndev.enclave.core.ui.component.sheet.MoveToAlbumBottomSheet
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.VisualGenerationSettings
import com.browntowndev.enclave.feature.studio.components.StudioOptionsBottomSheet
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.ExperimentalStdlibApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StudioScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToGallery: () -> Unit,
    onMediaClick: (String) -> Unit,
    onShowSnackbar: (String, String?, (() -> Unit)?) -> Unit,
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
    val generationPlaceholderCount = uiState.activeGenerationRemainingPlaceholderCount
    var scrollOnGenerationStart by remember { mutableStateOf(false) }
    val imagePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri ->
            uri?.let { viewModel.onUpdateReferenceImage(it.toString()) }
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
            onShowSnackbar(it, null, null)
            viewModel.clearError()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.moderationEvents.collect { event ->
            when (event) {
                is ModerationEvent.Blocked ->
                    onShowSnackbar(
                        MODERATION_REJECTED_MESSAGE,
                        STUDIO_MODERATION_REPORT_ACTION,
                    ) { viewModel.reportBlockedPrompt(event.prompt) }

                ModerationEvent.ReportSubmitted ->
                    onShowSnackbar(STUDIO_MODERATION_FEEDBACK_SUBMITTED, null, null)
            }
        }
    }

    LaunchedEffect(uiState.isGenerating, uiState.activeGenerationPrompt, generationPlaceholderCount) {
        if (scrollOnGenerationStart && uiState.isGenerating) {
            scrollOnGenerationStart = false
            val scrollTargetIndex =
                computeStudioGenerationPlaceholderScrollIndex(
                    groupedGallery = groupedGallery,
                    isGenerating = uiState.isGenerating,
                    activeGenerationPrompt = uiState.activeGenerationPrompt,
                    generationPlaceholderCount = generationPlaceholderCount,
                ) ?: return@LaunchedEffect

            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .first { totalCount -> totalCount > scrollTargetIndex }
            listState.animateScrollToItem(scrollTargetIndex)
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
                mediaType = uiState.mediaType,
                activeModelDisplayName = uiState.activeModelDisplayName,
                isSettingsOpen = uiState.isSettingsOpen,
                onMediaTypeClick = viewModel::onSettingsToggle,
                onClearSelection = viewModel::clearMediaSelection,
                onSaveClick = viewModel::onToggleSaveBottomSheet,
                onShareClick = { viewModel.shareMedia(uiState.selectedMediaItemIds) },
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
                onPromptChange = viewModel::onPromptChange,
                onReferenceImageClick = {
                    val isEnabled = when (uiState.mediaType) {
                        MediaCapability.IMAGE -> uiState.hasImageEditModel
                        MediaCapability.VIDEO -> uiState.hasImageToVideoModel
                        else -> false
                    }
                    if (isEnabled) {
                        imagePickerLauncher.launch("image/*")
                    } else {
                        val message = when (uiState.mediaType) {
                            MediaCapability.IMAGE -> STUDIO_REFERENCE_IMAGE_EDIT_UNAVAILABLE
                            MediaCapability.VIDEO -> STUDIO_REFERENCE_I2V_UNAVAILABLE
                            else -> ""
                        }
                        if (message.isNotEmpty()) {
                            onShowSnackbar(message, "DISMISS", null)
                        }
                    }
                },
                onClearReferenceImage = { viewModel.onUpdateReferenceImage(null) },
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
            onDismiss = viewModel::onSettingsToggle,
            onUpdateSettings = viewModel::onUpdateSettings,
            onContinualModeChange = viewModel::onContinualModeToggle,
            onMediaTypeChange = viewModel::onMediaTypeChange,
            onTemplateSelected = viewModel::onTemplateSelected,
            onOpenModelPicker = viewModel::onOpenModelPicker,
            onStageModelSelection = viewModel::onStageModelSelection,
            onConfirmModelSelection = viewModel::onConfirmModelSelection,
            onCancelModelPicker = viewModel::onCancelModelPicker,
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
    mediaType: MediaCapability,
    activeModelDisplayName: String?,
    isSettingsOpen: Boolean,
    onMediaTypeClick: () -> Unit,
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
            modifier =
                if (selectedCount > 0) {
                    Modifier.weight(1f)
                } else {
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onMediaTypeClick)
                },
            contentAlignment = Alignment.Center,
        ) {
            if (selectedCount > 0) {
                Text(
                    text = "$selectedCount Selected",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                val chevronRotation by animateFloatAsState(
                    targetValue = if (isSettingsOpen) 180f else 0f,
                    label = "studioTopBarMediaTypeChevron",
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = mediaType.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Change media type",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(chevronRotation),
                        )
                    }
                    activeModelDisplayName?.takeIf { it.isNotBlank() }?.let { modelName ->
                        Text(
                            text = modelName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
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
    if (galleryRowKeys.size < 2) {
        return false
    }

    val rowIndexByKey = galleryRowKeys.withIndex().associate { it.value to it.index }
    val triggerRowIndex = galleryRowKeys.lastIndex - 1
    val visibleGalleryRowIndexes =
        visibleItemKeys.mapNotNull { visibleKey ->
            rowIndexByKey[visibleKey]
        }

    return visibleGalleryRowIndexes.any { index -> index >= triggerRowIndex }
}

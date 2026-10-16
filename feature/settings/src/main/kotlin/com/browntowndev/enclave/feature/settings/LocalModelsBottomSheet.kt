package com.browntowndev.enclave.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveCircularProgressIndicator
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveIconButton
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveModalBottomSheet
import com.browntowndev.enclave.core.ui.component.enclave.EnclavePrimaryButton
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSectionHeader
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSheetHeader
import com.browntowndev.enclave.core.ui.component.enclave.enclaveBorderColor
import com.browntowndev.enclave.core.ui.component.enclave.enclaveSurface
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.SuccessGreen
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import kotlinx.coroutines.launch

private sealed interface LocalModelsSheetView {
    data object AssetList : LocalModelsSheetView

    data class ConfigList(
        val asset: LocalModelAssetUi,
    ) : LocalModelsSheetView

    data class Reassignment(
        val modelTypes: List<ModelType>,
        val options: List<ReassignmentOptionUi>,
    ) : LocalModelsSheetView
}

private val SUCCESS_COLOR = SuccessGreen // Material Green 500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalModelsBottomSheet(
    uiState: SettingsUiState,
    onDismiss: () -> Unit,
    onNavigateToLocalModelConfigure: () -> Unit,
    onSelectLocalModelAsset: (LocalModelAssetUi?) -> Unit,
    onSelectLocalModelConfig: (LocalModelConfigUi?) -> Unit,
    onDeleteLocalModelAsset: (LocalModelId) -> Unit,
    onDeleteLocalModelConfig: (LocalModelConfigurationId) -> Unit,
    onConfirmDeletionWithReassignment: (LocalModelConfigurationId?, ApiModelConfigurationId?, MediaProviderId?) -> Unit,
    onDismissDeletionSafety: () -> Unit,
    onDeleteModelPack: (String) -> Unit,
    reDownloadViewModel: ModelReDownloadViewModel = hiltViewModel(),
) {
    val reDownloadStates by reDownloadViewModel.reDownloadStates.collectAsStateWithLifecycle()
    val packReDownloadStates by reDownloadViewModel.packReDownloadStates.collectAsStateWithLifecycle()

    LocalModelsBottomSheetContent(
        uiState = uiState,
        reDownloadStates = reDownloadStates,
        onDismiss = onDismiss,
        onNavigateToLocalModelConfigure = onNavigateToLocalModelConfigure,
        onSelectLocalModelAsset = onSelectLocalModelAsset,
        onSelectLocalModelConfig = onSelectLocalModelConfig,
        onDeleteLocalModelAsset = onDeleteLocalModelAsset,
        onDeleteLocalModelConfig = onDeleteLocalModelConfig,
        onConfirmDeletionWithReassignment = onConfirmDeletionWithReassignment,
        onDismissDeletionSafety = onDismissDeletionSafety,
        onReDownloadModel = { modelId -> reDownloadViewModel.reDownloadModel(modelId) },
        onDownloadModelPack = { packId -> reDownloadViewModel.reDownloadPack(packId) },
        onDeleteModelPack = onDeleteModelPack,
        packReDownloadStates = packReDownloadStates,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalModelsBottomSheetContent(
    uiState: SettingsUiState,
    reDownloadStates: Map<LocalModelId, ReDownloadProgress>,
    onDismiss: () -> Unit,
    onNavigateToLocalModelConfigure: () -> Unit,
    onSelectLocalModelAsset: (LocalModelAssetUi?) -> Unit,
    onSelectLocalModelConfig: (LocalModelConfigUi?) -> Unit,
    onDeleteLocalModelAsset: (LocalModelId) -> Unit,
    onDeleteLocalModelConfig: (LocalModelConfigurationId) -> Unit,
    onConfirmDeletionWithReassignment: (LocalModelConfigurationId?, ApiModelConfigurationId?, MediaProviderId?) -> Unit,
    onDismissDeletionSafety: () -> Unit,
    onReDownloadModel: (LocalModelId) -> Unit,
    onDownloadModelPack: (String) -> Unit,
    onDeleteModelPack: (String) -> Unit,
    packReDownloadStates: Map<String, ReDownloadProgress> = emptyMap(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingDeleteTarget by remember { mutableStateOf<LocalDeleteTarget?>(null) }
    val scope = rememberCoroutineScope()

    val hideAndNavigate: (() -> Unit) -> Unit = { onNavigate ->
        scope.launch {
            sheetState.hide()
            onNavigate()
            onDismiss()
        }
    }

    EnclaveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val viewState =
                remember(uiState) {
                    when {
                        uiState.deletion.modelTypesNeedingReassignment.isNotEmpty() ->
                            LocalModelsSheetView.Reassignment(
                                modelTypes = uiState.deletion.modelTypesNeedingReassignment,
                                options = uiState.deletion.reassignmentOptions,
                            )
                        uiState.localModelsSheet.selectedAsset != null ->
                            LocalModelsSheetView.ConfigList(uiState.localModelsSheet.selectedAsset)
                        else ->
                            LocalModelsSheetView.AssetList
                    }
                }

            AnimatedContent(targetState = viewState, label = "LocalModelsSheetTransition") { state ->
                when (state) {
                    is LocalModelsSheetView.ConfigList -> {
                        LocalModelConfigListView(
                            asset = state.asset,
                            onDismiss = onDismiss,
                            onBack = { onSelectLocalModelAsset(null) },
                            onEditConfig = { config ->
                                onSelectLocalModelConfig(config)
                                hideAndNavigate(onNavigateToLocalModelConfigure)
                            },
                            onRequestDeleteAsset = { asset ->
                                pendingDeleteTarget = LocalDeleteTarget.Asset(asset.metadataId, asset.friendlyName)
                            },
                            onRequestDeleteConfig = { config ->
                                pendingDeleteTarget = LocalDeleteTarget.Config(config.id, config.displayName)
                            },
                            onAddConfig = {
                                onSelectLocalModelConfig(LocalModelConfigUi(localModelId = state.asset.metadataId))
                                hideAndNavigate(onNavigateToLocalModelConfigure)
                            },
                        )
                    }
                    is LocalModelsSheetView.AssetList -> {
                        LocalModelAssetListView(
                            modelPacks = uiState.localModelsSheet.modelPacks,
                            localModels = uiState.localModelsSheet.models,
                            availableToDownloadModels = uiState.localModelsSheet.availableDownloads,
                            reDownloadStates = reDownloadStates,
                            packReDownloadStates = packReDownloadStates,
                            onDismiss = onDismiss,
                            onSelectAsset = { asset -> onSelectLocalModelAsset(asset) },
                            onRequestDeleteAsset = { asset ->
                                pendingDeleteTarget = LocalDeleteTarget.Asset(asset.metadataId, asset.friendlyName)
                            },
                            onReDownloadModel = onReDownloadModel,
                            onDownloadModelPack = onDownloadModelPack,
                            onRequestDeleteModelPack = { pack ->
                                pendingDeleteTarget = LocalDeleteTarget.Pack(pack.packId, pack.displayName)
                            },
                        )
                    }
                    is LocalModelsSheetView.Reassignment -> {
                        ReassignmentView(
                            modelTypes = state.modelTypes,
                            reassignmentOptions = state.options,
                            onConfirm = onConfirmDeletionWithReassignment,
                            onDismiss = onDismissDeletionSafety,
                        )
                    }
                }
            }
        }

        if (uiState.deletion.showLastModelAlert) {
            AlertDialog(
                onDismissRequest = onDismissDeletionSafety,
                title = { Text("Cannot Delete Model") },
                text = { Text("At least one local or API model must remain. You cannot delete the last available model.") },
                confirmButton = {
                    TextButton(onClick = onDismissDeletionSafety) {
                        Text("OK")
                    }
                },
            )
        }

        pendingDeleteTarget?.let { deleteTarget ->
            DeleteConfirmationDialog(
                title =
                    when (deleteTarget) {
                        is LocalDeleteTarget.Asset -> "Delete Model?"
                        is LocalDeleteTarget.Config -> "Delete Preset?"
                        is LocalDeleteTarget.Pack -> "Delete Model Pack?"
                    },
                message =
                    when (deleteTarget) {
                        is LocalDeleteTarget.Asset ->
                            "Delete ${deleteTarget.displayName}? This removes the model and all of its presets."
                        is LocalDeleteTarget.Config ->
                            "Delete ${deleteTarget.displayName}? This preset will be removed."
                        is LocalDeleteTarget.Pack ->
                            "Delete ${deleteTarget.displayName}? All pack files will be removed from this device."
                    },
                confirmLabel = "Delete",
                onConfirm = {
                    when (deleteTarget) {
                        is LocalDeleteTarget.Asset -> onDeleteLocalModelAsset(deleteTarget.id)
                        is LocalDeleteTarget.Config -> onDeleteLocalModelConfig(deleteTarget.id)
                        is LocalDeleteTarget.Pack -> onDeleteModelPack(deleteTarget.packId)
                    }
                    pendingDeleteTarget = null
                },
                onDismiss = { pendingDeleteTarget = null },
            )
        }
    }
}

@Composable
private fun LocalModelAssetListView(
    modelPacks: List<ModelPackUi>,
    localModels: List<LocalModelAssetUi>,
    availableToDownloadModels: List<LocalModelAssetUi>,
    reDownloadStates: Map<LocalModelId, ReDownloadProgress>,
    packReDownloadStates: Map<String, ReDownloadProgress>,
    onDismiss: () -> Unit,
    onSelectAsset: (LocalModelAssetUi) -> Unit,
    onRequestDeleteAsset: (LocalModelAssetUi) -> Unit,
    onReDownloadModel: (LocalModelId) -> Unit,
    onDownloadModelPack: (String) -> Unit,
    onRequestDeleteModelPack: (ModelPackUi) -> Unit,
) {
    val displayedLocalModels = remember(localModels) {
        localModels.filter { !it.isImageGenerationCapable }
    }
    val displayedAvailableToDownloadModels = remember(availableToDownloadModels) {
        availableToDownloadModels.filter { !it.isImageGenerationCapable }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        EnclaveSheetHeader(
            title = "Local AI Models",
            subtitle = "Manage your model packs, local models, and tuning presets.",
            onDismiss = onDismiss,
        )

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.rowSpacing),
        ) {
            modelPacksSection(
                modelPacks = modelPacks,
                packReDownloadStates = packReDownloadStates,
                onDownloadModelPack = onDownloadModelPack,
                onRequestDeleteModelPack = onRequestDeleteModelPack,
            )

            if (displayedLocalModels.isNotEmpty()) {
                item {
                    EnclaveSectionHeader(text = "Models")
                }
                items(displayedLocalModels, key = { it.metadataId.value }) { asset ->
                    LocalModelAssetCard(
                        asset = asset,
                        reDownloadProgress = reDownloadStates[asset.metadataId],
                        onSelectAsset = {
                            if (asset.isSoftDeleted) {
                                onReDownloadModel(asset.metadataId)
                            } else {
                                onSelectAsset(asset)
                            }
                        },
                        onRequestDeleteAsset = onRequestDeleteAsset,
                        onReDownloadClick = { onReDownloadModel(asset.metadataId) },
                    )
                }
            }

            if (displayedAvailableToDownloadModels.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(EnclaveNeoTokens.sectionSpacing))
                    EnclaveSectionHeader(text = "Removed")
                }
                items(displayedAvailableToDownloadModels, key = { "dl_${it.metadataId.value}" }) { asset ->
                    LocalModelAssetCard(
                        asset = asset,
                        reDownloadProgress = reDownloadStates[asset.metadataId],
                        onSelectAsset = { onReDownloadModel(asset.metadataId) },
                        onRequestDeleteAsset = null,
                        onReDownloadClick = { onReDownloadModel(asset.metadataId) },
                    )
                }
            }

            if (modelPacks.isEmpty() && displayedLocalModels.isEmpty() && displayedAvailableToDownloadModels.isEmpty()) {
                item {
                    Text(
                        text = "No local models or packs yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.modelPacksSection(
    modelPacks: List<ModelPackUi>,
    packReDownloadStates: Map<String, ReDownloadProgress>,
    onDownloadModelPack: (String) -> Unit,
    onRequestDeleteModelPack: (ModelPackUi) -> Unit,
) {
    if (modelPacks.isEmpty()) {
        return
    }
    item(key = "model_packs_header") {
        EnclaveSectionHeader(text = "Model packs")
    }
    items(modelPacks, key = { it.packId }) { pack ->
        ModelPackCard(
            pack = pack,
            reDownloadProgress = packReDownloadStates[pack.packId],
            onDownload = { onDownloadModelPack(pack.packId) },
            onReDownload = { onDownloadModelPack(pack.packId) },
            onRequestDelete = { onRequestDeleteModelPack(pack) },
        )
    }
}

@Composable
private fun LocalModelAssetCard(
    asset: LocalModelAssetUi,
    reDownloadProgress: ReDownloadProgress?,
    onSelectAsset: (LocalModelAssetUi) -> Unit,
    onRequestDeleteAsset: ((LocalModelAssetUi) -> Unit)?,
    onReDownloadClick: () -> Unit,
) {
    val card: @Composable () -> Unit = {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .enclaveSurface(
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        borderColor = enclaveBorderColor(),
                        withShadow = false,
                    ).clickable { onSelectAsset(asset) }
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = asset.friendlyName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (asset.isMultimodal) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Vision capable",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = "${asset.providerName} • ${asset.format}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = "${(asset.sizeInBytes / (1024 * 1024 * 1024.0)).format(2)} GB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (onRequestDeleteAsset != null) {
                    Text(
                        text = "${asset.configurations.size} Preset(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (onRequestDeleteAsset == null || reDownloadProgress != null || asset.isSoftDeleted) {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (reDownloadProgress) {
                        null, ReDownloadProgress.Idle -> {
                            IconButton(
                                onClick = onReDownloadClick,
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download Model ${asset.friendlyName}",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        is ReDownloadProgress.Preparing -> {
                            EnclaveCircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                        is ReDownloadProgress.Downloading -> {
                            EnclaveCircularProgressIndicator(
                                progress = { reDownloadProgress.progress },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        is ReDownloadProgress.Finalizing -> {
                            if (reDownloadProgress.progress != null) {
                                EnclaveCircularProgressIndicator(
                                    progress = { reDownloadProgress.progress },
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                EnclaveCircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        }
                        is ReDownloadProgress.Complete -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Download Complete",
                                tint = SUCCESS_COLOR,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        is ReDownloadProgress.Failed -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Download Failed: ${reDownloadProgress.error}",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            if (reDownloadProgress == null && onRequestDeleteAsset != null && !asset.isSoftDeleted) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "View details",
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }

    if (isLocalModelAssetSwipeable(onRequestDeleteAsset)) {
        val dismissState =
            rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart) {
                        onRequestDeleteAsset!!(asset)
                        false
                    } else {
                        true
                    }
                },
            )
        SwipeToDismissBox(
            state = dismissState,
            modifier = Modifier.clip(EnclaveNeoTokens.shape),
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                }
            },
        ) { card() }
    } else {
        card()
    }
}

@Composable
private fun LocalModelConfigListView(
    asset: LocalModelAssetUi,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onEditConfig: (LocalModelConfigUi) -> Unit,
    onRequestDeleteAsset: (LocalModelAssetUi) -> Unit,
    onRequestDeleteConfig: (LocalModelConfigUi) -> Unit,
    onAddConfig: () -> Unit,
) {
    val presetSubtitle =
        if (asset.isMultimodal) {
            "Presets • Vision"
        } else {
            "Presets"
        }

    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        EnclaveSheetHeader(
            title = asset.friendlyName,
            subtitle = presetSubtitle,
            onDismiss = onDismiss,
            onBackClick = onBack,
            trailingContent = {
                Box {
                    EnclaveIconButton(
                        onClick = { menuExpanded = true },
                        icon = Icons.Default.MoreVert,
                        contentDescription = "Options",
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRequestDeleteAsset(asset)
                            },
                        )
                    }
                }
            },
        )

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.rowSpacing),
        ) {
            items(asset.configurations, key = { it.id.value }) { config ->
                val isSwipeEnabled = isPresetSwipeDeletionEnabled(asset.configurations.size)
                val dismissState =
                    rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                onRequestDeleteConfig(config)
                                false
                            } else {
                                true
                            }
                        },
                    )

                SwipeToDismissBox(
                    state = dismissState,
                    modifier = Modifier.clip(EnclaveNeoTokens.shape),
                    enableDismissFromStartToEnd = false,
                    gesturesEnabled = isSwipeEnabled,
                    backgroundContent = {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.errorContainer),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(end = 16.dp),
                            )
                        }
                    },
                ) {
                    SettingsProviderCard(
                        title = config.displayName,
                        subtitle = "Temp: ${config.temperature.format(2)} | Max: ${config.maxTokens}",
                        onClick = { onEditConfig(config) },
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))

                EnclavePrimaryButton(
                    text = "Add Preset",
                    icon = Icons.Default.Add,
                    onClick = onAddConfig,
                )
            }
        }
    }
}

@Composable
private fun ModelPackCard(
    pack: ModelPackUi,
    reDownloadProgress: ReDownloadProgress?,
    onDownload: () -> Unit,
    onReDownload: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val showDownload = pack.showDownloadAction && pack.isEligible
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    onRequestDelete()
                    false
                } else {
                    true
                }
            },
        )
    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.clip(EnclaveNeoTokens.shape),
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete pack",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .enclaveSurface(
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        borderColor = enclaveBorderColor(),
                        withShadow = false,
                    ).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pack.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Pack • ${pack.completenessLabel} files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                pack.disabledReason?.takeIf { it.isNotBlank() }?.let { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (showDownload || reDownloadProgress != null) {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (reDownloadProgress) {
                        null, ReDownloadProgress.Idle -> {
                            IconButton(
                                onClick = if (pack.isDiffusionSoftDeleted) onReDownload else onDownload,
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download ${pack.displayName}",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        ReDownloadProgress.Preparing -> {
                            EnclaveCircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                        is ReDownloadProgress.Downloading -> {
                            EnclaveCircularProgressIndicator(
                                progress = { reDownloadProgress.progress },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        is ReDownloadProgress.Finalizing -> {
                            if (reDownloadProgress.progress != null) {
                                EnclaveCircularProgressIndicator(
                                    progress = { reDownloadProgress.progress },
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                EnclaveCircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        }
                        ReDownloadProgress.Complete -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Download complete",
                                tint = SUCCESS_COLOR,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        is ReDownloadProgress.Failed -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Download failed",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed interface LocalDeleteTarget {
    val displayName: String

    data class Asset(
        val id: LocalModelId,
        override val displayName: String,
    ) : LocalDeleteTarget

    data class Config(
        val id: LocalModelConfigurationId,
        override val displayName: String,
    ) : LocalDeleteTarget

    data class Pack(
        val packId: String,
        override val displayName: String,
    ) : LocalDeleteTarget
}

private fun Double.format(digits: Int) = "%.${digits}f".format(this)

internal fun isLocalModelAssetSwipeable(onRequestDeleteAsset: ((LocalModelAssetUi) -> Unit)?): Boolean = onRequestDeleteAsset != null

// ==================== PREVIEWS ====================

@Preview(showBackground = true, name = "Local Models Sheet - Assets List")
@Composable
fun PreviewLocalModelsBottomSheetAssets() {
    EnclaveTheme {
        LocalModelsBottomSheetContent(
            uiState = MockSettingsData.baseUiState,
            reDownloadStates = emptyMap(),
            onDismiss = {},
            onNavigateToLocalModelConfigure = {},
            onSelectLocalModelAsset = {},
            onSelectLocalModelConfig = {},
            onDeleteLocalModelAsset = {},
            onDeleteLocalModelConfig = {},
            onConfirmDeletionWithReassignment = { _, _, _ -> },
            onDismissDeletionSafety = {},
            onReDownloadModel = {},
            onDownloadModelPack = {},
            onDeleteModelPack = {},
            packReDownloadStates = emptyMap(),
        )
    }
}

@Preview(showBackground = true, name = "Local Models Sheet - Config List")
@Composable
fun PreviewLocalModelsBottomSheetContext() {
    EnclaveTheme {
        LocalModelsBottomSheetContent(
            uiState =
                MockSettingsData.baseUiState.copy(
                    localModelsSheet =
                        MockSettingsData.baseUiState.localModelsSheet.copy(
                            selectedAsset = MockSettingsData.localModels[0],
                        ),
                ),
            reDownloadStates = emptyMap(),
            onDismiss = {},
            onNavigateToLocalModelConfigure = {},
            onSelectLocalModelAsset = {},
            onSelectLocalModelConfig = {},
            onDeleteLocalModelAsset = {},
            onDeleteLocalModelConfig = {},
            onConfirmDeletionWithReassignment = { _, _, _ -> },
            onDismissDeletionSafety = {},
            onReDownloadModel = {},
            onDownloadModelPack = {},
            onDeleteModelPack = {},
            packReDownloadStates = emptyMap(),
        )
    }
}

package com.browntowndev.enclave.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoIconButton
import com.browntowndev.enclave.core.ui.component.neo.NeoModalBottomSheet
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetHeader
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import kotlinx.coroutines.launch

private sealed interface ByokSheetView {
    data object AssetList : ByokSheetView

    data class ConfigList(
        val asset: ApiModelAssetUi,
    ) : ByokSheetView

    data class Reassignment(
        val modelTypes: List<ModelType>,
        val options: List<ReassignmentOptionUi>,
    ) : ByokSheetView
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ByokBottomSheet(
    uiState: SettingsUiState,
    onDismiss: () -> Unit,
    onNavigateToByokConfigure: () -> Unit,
    onStartCreateApiModelAsset: () -> Unit,
    onSelectApiModelAsset: (ApiModelAssetUi?) -> Unit,
    onSelectApiModelConfig: (ApiModelConfigUi?) -> Unit,
    onDeleteApiModelAsset: (ApiCredentialsId, () -> Unit) -> Unit,
    onDeleteApiModelConfig: (ApiModelConfigurationId, () -> Unit) -> Unit,
    onConfirmDeletionWithReassignment: (
        LocalModelConfigurationId?,
        ApiModelConfigurationId?,
        MediaProviderId?,
    ) -> Unit,
    onDismissDeletionSafety: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingDeleteTarget by remember { mutableStateOf<ByokDeleteTarget?>(null) }
    val scope = rememberCoroutineScope()

    val hideAndNavigate: (() -> Unit) -> Unit = { onNavigate ->
        scope.launch {
            sheetState.hide()
            onNavigate()
            onDismiss()
        }
    }

    NeoModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val viewState =
                remember(uiState) {
                    when {
                        uiState.deletion.modelTypesNeedingReassignment.isNotEmpty() ->
                            ByokSheetView.Reassignment(
                                modelTypes = uiState.deletion.modelTypesNeedingReassignment,
                                options = uiState.deletion.reassignmentOptions,
                            )
                        uiState.apiProvidersSheet.selectedAsset != null ->
                            ByokSheetView.ConfigList(uiState.apiProvidersSheet.selectedAsset)
                        else ->
                            ByokSheetView.AssetList
                    }
                }

            AnimatedContent(targetState = viewState, label = "ByokSheetTransition") { state ->
                when (state) {
                    is ByokSheetView.ConfigList -> {
                        ByokConfigListView(
                            asset = state.asset,
                            onDismiss = onDismiss,
                            onBack = { onSelectApiModelAsset(null) },
                            onEditAsset = {
                                hideAndNavigate {
                                    onSelectApiModelConfig(null)
                                    onNavigateToByokConfigure()
                                }
                            },
                            onRequestDeleteAsset = { asset ->
                                pendingDeleteTarget = ByokDeleteTarget.Asset(asset.credentialsId, asset.displayName)
                            },
                            onEditConfig = { config ->
                                hideAndNavigate {
                                    onSelectApiModelConfig(config)
                                    onNavigateToByokConfigure()
                                }
                            },
                            onRequestDeleteConfig = { config ->
                                pendingDeleteTarget = ByokDeleteTarget.Config(config.id, config.displayName)
                            },
                            onAddConfig = {
                                hideAndNavigate {
                                    onSelectApiModelConfig(freshApiPresetDraft(state.asset.provider))
                                    onNavigateToByokConfigure()
                                }
                            },
                        )
                    }
                    is ByokSheetView.AssetList -> {
                        ByokAssetListView(
                            apiModels = uiState.apiProvidersSheet.assets,
                            onDismiss = onDismiss,
                            onSelectAsset = { asset -> onSelectApiModelAsset(asset) },
                            onRequestDeleteAsset = { asset ->
                                pendingDeleteTarget = ByokDeleteTarget.Asset(asset.credentialsId, asset.displayName)
                            },
                            onAddAsset = {
                                hideAndNavigate {
                                    onStartCreateApiModelAsset()
                                    onNavigateToByokConfigure()
                                }
                            },
                        )
                    }
                    is ByokSheetView.Reassignment -> {
                        ReassignmentView(
                            modelTypes = state.modelTypes,
                            reassignmentOptions = state.options,
                            onConfirm = { local, api, media ->
                                onConfirmDeletionWithReassignment(local, api, media)
                                scope.launch {
                                    sheetState.hide()
                                    onDismiss()
                                }
                            },
                            onDismiss = onDismissDeletionSafety,
                        )
                    }
                }
            }
        }

        if (uiState.deletion.showLastModelAlert) {
            AlertDialog(
                onDismissRequest = onDismissDeletionSafety,
                title = { Text("Cannot Delete Provider") },
                text = { Text("At least one local or API model must remain. You cannot delete the last available provider.") },
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
                    if (deleteTarget is ByokDeleteTarget.Asset) {
                        "Delete Provider?"
                    } else {
                        "Delete Preset?"
                    },
                message =
                    if (deleteTarget is ByokDeleteTarget.Asset) {
                        "Delete ${deleteTarget.displayName}? This removes the provider and all of its presets."
                    } else {
                        "Delete ${deleteTarget.displayName}? This preset will be removed."
                    },
                confirmLabel = "Delete",
                onConfirm = {
                    pendingDeleteTarget = null
                    val hideSheet: () -> Unit = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    }
                    when (deleteTarget) {
                        is ByokDeleteTarget.Asset -> onDeleteApiModelAsset(deleteTarget.id, hideSheet)
                        is ByokDeleteTarget.Config -> onDeleteApiModelConfig(deleteTarget.id, hideSheet)
                    }
                },
                onDismiss = { pendingDeleteTarget = null },
            )
        }
    }
}

@Composable
private fun ByokAssetListView(
    apiModels: List<ApiModelAssetUi>,
    onDismiss: () -> Unit,
    onSelectAsset: (ApiModelAssetUi) -> Unit,
    onRequestDeleteAsset: (ApiModelAssetUi) -> Unit,
    onAddAsset: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        NeoSheetHeader(
            title = "API AI Models",
            subtitle = "Manage your own API keys and model configurations.",
            onDismiss = onDismiss,
        )

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.rowSpacing),
        ) {
            items(apiModels, key = { it.credentialsId.value }) { asset ->
                val dismissState =
                    rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                onRequestDeleteAsset(asset)
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
                ) {
                    val subtitle =
                        buildString {
                            append(asset.provider.displayName)
                            append(" • ")
                            append("${asset.configurations.size} Preset(s)")
                            if (asset.configurations.any { it.isMultimodal }) {
                                append(" • Vision")
                            }
                        }
                    SettingsProviderCard(
                        title = asset.displayName,
                        subtitle = subtitle,
                        onClick = { onSelectAsset(asset) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        NeoPrimaryButton(
            text = "Add API Provider",
            icon = Icons.Default.Add,
            onClick = onAddAsset,
        )
    }
}

@Composable
private fun ByokConfigListView(
    asset: ApiModelAssetUi,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onEditAsset: () -> Unit,
    onRequestDeleteAsset: (ApiModelAssetUi) -> Unit,
    onEditConfig: (ApiModelConfigUi) -> Unit,
    onRequestDeleteConfig: (ApiModelConfigUi) -> Unit,
    onAddConfig: () -> Unit,
) {
    val presetSubtitle =
        if (asset.configurations.any { it.isMultimodal }) {
            "Presets • Vision"
        } else {
            "Presets"
        }

    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        NeoSheetHeader(
            title = asset.displayName,
            subtitle = presetSubtitle,
            onDismiss = onDismiss,
            onBackClick = onBack,
            trailingContent = {
                Box {
                    NeoIconButton(
                        onClick = { menuExpanded = true },
                        icon = Icons.Default.MoreVert,
                        contentDescription = "Options",
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEditAsset()
                            },
                        )
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
                    val configSubtitle =
                        if (config.modelId.isNotBlank()) {
                            config.modelId
                        } else {
                            config.displayName
                        }
                    SettingsProviderCard(
                        title = config.displayName,
                        subtitle = configSubtitle,
                        onClick = { onEditConfig(config) },
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))

                NeoPrimaryButton(
                    text = "Add Preset",
                    icon = Icons.Default.Add,
                    onClick = onAddConfig,
                )
            }
        }
    }
}

private sealed interface ByokDeleteTarget {
    val displayName: String

    data class Asset(
        val id: ApiCredentialsId,
        override val displayName: String,
    ) : ByokDeleteTarget

    data class Config(
        val id: ApiModelConfigurationId,
        override val displayName: String,
    ) : ByokDeleteTarget
}

private fun Double.format(digits: Int) = "%.${digits}f".format(this)

internal fun isPresetSwipeDeletionEnabled(configurationCount: Int): Boolean = configurationCount > 1

// ==================== PREVIEWS ====================

@Preview(showBackground = true, name = "BYOK Bottom Sheet - Assets List")
@Composable
fun PreviewByokBottomSheetAssets() {
    EnclaveTheme {
        ByokBottomSheet(
            uiState = MockSettingsData.baseUiState,
            onDismiss = {},
            onNavigateToByokConfigure = {},
            onStartCreateApiModelAsset = {},
            onSelectApiModelAsset = {},
            onSelectApiModelConfig = {},
            onDeleteApiModelAsset = { _, _ -> },
            onDeleteApiModelConfig = { _, _ -> },
            onDismissDeletionSafety = {},
            onConfirmDeletionWithReassignment = { _, _, _ -> },
        )
    }
}

@Preview(showBackground = true, name = "BYOK Bottom Sheet - Config List")
@Composable
fun PreviewByokBottomSheetContext() {
    EnclaveTheme {
        ByokBottomSheet(
            uiState =
                MockSettingsData.baseUiState.copy(
                    apiProvidersSheet =
                        MockSettingsData.baseUiState.apiProvidersSheet.copy(
                            selectedAsset = MockSettingsData.apiModels[0],
                        ),
                ),
            onDismiss = {},
            onNavigateToByokConfigure = {},
            onStartCreateApiModelAsset = {},
            onSelectApiModelAsset = {},
            onSelectApiModelConfig = {},
            onDeleteApiModelAsset = { _, _ -> },
            onDeleteApiModelConfig = { _, _ -> },
            onDismissDeletionSafety = {},
            onConfirmDeletionWithReassignment = { _, _, _ -> },
        )
    }
}

package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.component.sheet.JumpFreeModalBottomSheet
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ByokBottomSheet(
    uiState: SettingsUiState,
    onDismiss: () -> Unit,
    onNavigateToByokConfigure: () -> Unit,
    onSelectApiModelAsset: (ApiModelAssetUi?) -> Unit,
    onSelectApiModelConfig: (ApiModelConfigUi?) -> Unit,
    onDeleteApiModelAsset: (Long) -> Unit,
    onDeleteApiModelConfig: (Long) -> Unit,
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

    JumpFreeModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            val selectedAsset = uiState.selectedApiModelAsset

            AnimatedContent(targetState = selectedAsset != null, label = "ByokSheetTransition") { isConfigView ->
                if (isConfigView && selectedAsset != null) {
                    ByokConfigListView(
                        asset = selectedAsset,
                        onBack = { onSelectApiModelAsset(null) },
                        onEditConfig = { config ->
                            onSelectApiModelConfig(config)
                            hideAndNavigate(onNavigateToByokConfigure)
                        },
                        onRequestDeleteConfig = { config ->
                            pendingDeleteTarget = ByokDeleteTarget.Config(config.id, config.displayName)
                        },
                        onAddConfig = {
                            onSelectApiModelConfig(ApiModelConfigUi())
                            hideAndNavigate(onNavigateToByokConfigure)
                        }
                    )
                } else {
                    ByokAssetListView(
                        apiModels = uiState.apiModels,
                        onSelectAsset = { asset -> onSelectApiModelAsset(asset) },
                        onRequestDeleteAsset = { asset ->
                            pendingDeleteTarget = ByokDeleteTarget.Asset(asset.credentialsId, asset.displayName)
                        },
                        onAddAsset = {
                            onSelectApiModelAsset(null)
                            hideAndNavigate(onNavigateToByokConfigure)
                        }
                    )
                }
            }
        }

        pendingDeleteTarget?.let { deleteTarget ->
            DeleteConfirmationDialog(
                title = if (deleteTarget is ByokDeleteTarget.Asset) {
                    "Delete Provider?"
                } else {
                    "Delete Preset?"
                },
                message = if (deleteTarget is ByokDeleteTarget.Asset) {
                    "Delete ${deleteTarget.displayName}? This removes the provider and all of its presets."
                } else {
                    "Delete ${deleteTarget.displayName}? This preset will be removed."
                },
                confirmLabel = "Delete",
                onConfirm = {
                    when (deleteTarget) {
                        is ByokDeleteTarget.Asset -> onDeleteApiModelAsset(deleteTarget.id)
                        is ByokDeleteTarget.Config -> onDeleteApiModelConfig(deleteTarget.id)
                    }
                    pendingDeleteTarget = null
                },
                onDismiss = { pendingDeleteTarget = null }
            )
        }
    }
}

@Composable
private fun ByokAssetListView(
    apiModels: List<ApiModelAssetUi>,
    onSelectAsset: (ApiModelAssetUi) -> Unit,
    onRequestDeleteAsset: (ApiModelAssetUi) -> Unit,
    onAddAsset: () -> Unit
) {
    Column {
        Text(
            text = "External AI Providers",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Manage your own API keys and model configurations.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(apiModels, key = { it.credentialsId }) { asset ->
                var menuExpanded by remember { mutableStateOf(false) }

                Box {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onSelectAsset(asset) },
                                onLongClick = { menuExpanded = true }
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = asset.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${asset.provider.displayName} • ${asset.modelId} • ${asset.configurations.size} Presets",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "View configurations",
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menuExpanded = false
                                onRequestDeleteAsset(asset)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onAddAsset,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add new API Provider")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add API Provider", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ByokConfigListView(
    asset: ApiModelAssetUi,
    onBack: () -> Unit,
    onEditConfig: (ApiModelConfigUi) -> Unit,
    onRequestDeleteConfig: (ApiModelConfigUi) -> Unit,
    onAddConfig: () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp).padding(end = 8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to providers")
            }
            Column {
                Text(
                    text = asset.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Presets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(asset.configurations, key = { it.id }) { config ->
                var menuExpanded by remember { mutableStateOf(false) }
                val isDeleteEnabled = asset.configurations.size > 1

                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onEditConfig(config) },
                                onLongClick = { menuExpanded = true }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = config.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Temp: ${config.temperature} | Max: ${config.maxTokens}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { onEditConfig(config) }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Preset ${config.displayName}",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            enabled = isDeleteEnabled,
                            onClick = {
                                menuExpanded = false
                                onRequestDeleteConfig(config)
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onAddConfig,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add Preset",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private sealed interface ByokDeleteTarget {
    val id: Long
    val displayName: String

    data class Asset(
        override val id: Long,
        override val displayName: String
    ) : ByokDeleteTarget

    data class Config(
        override val id: Long,
        override val displayName: String
    ) : ByokDeleteTarget
}

@Composable
private fun DeleteConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ==================== PREVIEWS ====================

@Preview(showBackground = true, name = "BYOK Bottom Sheet - Assets List")
@Composable
fun PreviewByokBottomSheetAssets() {
    PocketCrewTheme {
        ByokBottomSheet(
            uiState = MockSettingsData.baseUiState,
            onDismiss = {},
            onNavigateToByokConfigure = {},
            onSelectApiModelAsset = {},
            onSelectApiModelConfig = {},
            onDeleteApiModelAsset = {},
            onDeleteApiModelConfig = {}
        )
    }
}

@Preview(showBackground = true, name = "BYOK Bottom Sheet - Config List")
@Composable
fun PreviewByokBottomSheetContext() {
    PocketCrewTheme {
        ByokBottomSheet(
            uiState = MockSettingsData.baseUiState.copy(
                selectedApiModelAsset = MockSettingsData.apiModels[0]
            ),
            onDismiss = {},
            onNavigateToByokConfigure = {},
            onSelectApiModelAsset = {},
            onSelectApiModelConfig = {},
            onDeleteApiModelAsset = {},
            onDeleteApiModelConfig = {}
        )
    }
}

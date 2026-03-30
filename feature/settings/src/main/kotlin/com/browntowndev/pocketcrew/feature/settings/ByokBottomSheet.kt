package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme

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

    ModalBottomSheet(
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
                            onNavigateToByokConfigure()
                            onDismiss()
                        },
                        onDeleteConfig = { id -> onDeleteApiModelConfig(id) },
                        onAddConfig = {
                            onSelectApiModelConfig(null)
                            onNavigateToByokConfigure()
                            onDismiss()
                        }
                    )
                } else {
                    ByokAssetListView(
                        apiModels = uiState.apiModels,
                        onSelectAsset = { asset -> onSelectApiModelAsset(asset) },
                        onDeleteAsset = { id -> onDeleteApiModelAsset(id) },
                        onAddAsset = {
                            onSelectApiModelAsset(null)
                            onNavigateToByokConfigure()
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ByokAssetListView(
    apiModels: List<ApiModelAssetUi>,
    onSelectAsset: (ApiModelAssetUi) -> Unit,
    onDeleteAsset: (Long) -> Unit,
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectAsset(asset) },
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

                        IconButton(onClick = { onDeleteAsset(asset.credentialsId) }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Provider ${asset.displayName}",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "View configurations",
                            modifier = Modifier.padding(start = 4.dp)
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
    onDeleteConfig: (Long) -> Unit,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditConfig(config) }
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

                    IconButton(onClick = { onDeleteConfig(config.id) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Preset ${config.displayName}",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable(onClick = onAddConfig)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add Preset",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
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

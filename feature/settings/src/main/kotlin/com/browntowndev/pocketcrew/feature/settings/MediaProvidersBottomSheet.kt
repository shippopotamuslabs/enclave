package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.component.sheet.JumpFreeModalBottomSheet
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MediaProvidersBottomSheet(
    uiState: SettingsUiState,
    onDismiss: () -> Unit,
    onNavigateToMediaConfigure: () -> Unit,
    onStartCreateMediaProviderAsset: () -> Unit,
    onSelectMediaProviderAsset: (MediaProviderAssetUi?) -> Unit,
    onDeleteMediaProviderAsset: (MediaProviderId) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingDeleteTarget by remember { mutableStateOf<MediaProviderAssetUi?>(null) }
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
            Text(
                text = "Media Generation Models",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Manage your Image and Video generation APIs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = "Hint: long press to delete the provider. Tap to edit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.mediaProvidersSheet.assets, key = { it.id.value }) { asset ->
                    var menuExpanded by remember { mutableStateOf(false) }

                    Box {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        onSelectMediaProviderAsset(asset)
                                        hideAndNavigate(onNavigateToMediaConfigure)
                                    },
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
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${asset.provider.displayName} • ${asset.capability.displayName} • ${asset.modelName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
                                    pendingDeleteTarget = asset
                                }
                            )
                        }
                    }
                }

                if (uiState.mediaProvidersSheet.assets.isEmpty()) {
                    item {
                        Text(
                            text = "No media providers configured.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    onStartCreateMediaProviderAsset()
                    hideAndNavigate(onNavigateToMediaConfigure)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Media Provider", fontWeight = FontWeight.SemiBold)
            }
        }

        pendingDeleteTarget?.let { asset ->
            DeleteConfirmationDialog(
                title = "Delete Provider?",
                message = "Delete ${asset.displayName}? This media provider will be removed.",
                confirmLabel = "Delete",
                onConfirm = {
                    onDeleteMediaProviderAsset(asset.id)
                    pendingDeleteTarget = null
                },
                onDismiss = { pendingDeleteTarget = null }
            )
        }
    }
}

@Preview(showBackground = true, name = "Media Providers Sheet")
@Composable
fun PreviewMediaProvidersBottomSheet() {
    PocketCrewTheme {
        MediaProvidersBottomSheet(
            uiState = MockSettingsData.baseUiState,
            onDismiss = {},
            onNavigateToMediaConfigure = {},
            onStartCreateMediaProviderAsset = {},
            onSelectMediaProviderAsset = {},
            onDeleteMediaProviderAsset = {},
        )
    }
}

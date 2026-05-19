package com.browntowndev.pocketcrew.feature.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.component.sheet.JumpFreeModalBottomSheet
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsProvidersBottomSheet(
    uiState: SettingsUiState,
    onDismiss: () -> Unit,
    onNavigateToTtsConfigure: () -> Unit,
    onStartCreateTtsProviderAsset: () -> Unit,
    onSelectTtsProviderAsset: (TtsProviderAssetUi?) -> Unit,
    onDeleteTtsProviderAsset: (TtsProviderId) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingDeleteTarget by remember { mutableStateOf<TtsProviderAssetUi?>(null) }
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
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Text to Speech Models",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Text(
                text = "Manage your TTS voices and API keys.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.ttsProvidersSheet.assets, key = { it.id.value }) { asset ->
                    val dismissState =
                        rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    pendingDeleteTarget = asset
                                    false
                                } else {
                                    true
                                }
                            },
                        )

                    SwipeToDismissBox(
                        state = dismissState,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)),
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
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectTtsProviderAsset(asset)
                                        hideAndNavigate(onNavigateToTtsConfigure)
                                    },
                            shape = RoundedCornerShape(16.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = asset.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "${asset.provider.displayName} • ${asset.voiceName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.ttsProvidersSheet.assets.isEmpty()) {
                    item {
                        Text(
                            text = "No TTS providers configured.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    onStartCreateTtsProviderAsset()
                    hideAndNavigate(onNavigateToTtsConfigure)
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add TTS Provider", fontWeight = FontWeight.SemiBold)
            }
        }

        pendingDeleteTarget?.let { asset ->
            DeleteConfirmationDialog(
                title = "Delete Provider?",
                message = "Delete ${asset.displayName}? This TTS provider will be removed.",
                confirmLabel = "Delete",
                onConfirm = {
                    onDeleteTtsProviderAsset(asset.id)
                    pendingDeleteTarget = null
                },
                onDismiss = { pendingDeleteTarget = null },
            )
        }
    }
}

// ==================== PREVIEWS ====================

@Preview(showBackground = true, name = "TTS Providers Sheet")
@Composable
fun PreviewTtsProvidersBottomSheet() {
    PocketCrewTheme {
        TtsProvidersBottomSheet(
            uiState = MockSettingsData.baseUiState,
            onDismiss = {},
            onNavigateToTtsConfigure = {},
            onStartCreateTtsProviderAsset = {},
            onSelectTtsProviderAsset = {},
            onDeleteTtsProviderAsset = {},
        )
    }
}

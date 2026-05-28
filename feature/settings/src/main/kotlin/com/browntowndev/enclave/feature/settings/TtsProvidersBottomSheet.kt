package com.browntowndev.enclave.feature.settings

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoModalBottomSheet
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetHeader
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.config.TtsProviderId
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

    NeoModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        NeoSheetHeader(
            title = "Text to Speech Models",
            subtitle = "Manage your TTS voices and API keys.",
            onDismiss = onDismiss,
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.rowSpacing),
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
                        SettingsProviderCard(
                            title = asset.displayName,
                            subtitle = "${asset.provider.displayName} • ${asset.voiceName}",
                            onClick = {
                                onSelectTtsProviderAsset(asset)
                                hideAndNavigate(onNavigateToTtsConfigure)
                            },
                        )
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
            NeoPrimaryButton(
                text = "Add TTS Provider",
                icon = Icons.Default.Add,
                onClick = {
                    onStartCreateTtsProviderAsset()
                    hideAndNavigate(onNavigateToTtsConfigure)
                },
            )
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
    EnclaveTheme {
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

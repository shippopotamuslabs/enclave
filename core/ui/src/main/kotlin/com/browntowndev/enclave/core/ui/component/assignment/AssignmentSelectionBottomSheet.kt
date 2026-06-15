package com.browntowndev.enclave.core.ui.component.assignment

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoChevronCard
import com.browntowndev.enclave.core.ui.component.neo.NeoModalBottomSheet
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSecondaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSelectionCard
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetHeader
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetTabRow
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentSelectionBottomSheet(
    slotLabel: String,
    localAssets: List<LocalModelAssetUi>,
    apiAssets: List<ApiModelAssetUi>,
    ttsAssets: List<TtsProviderAssetUi>,
    liveVoiceAssets: List<LiveVoiceProviderAssetUi>,
    mediaAssets: List<MediaProviderAssetUi>,
    onDismiss: () -> Unit,
    onSelect: (
        localId: LocalModelConfigurationId?,
        apiId: ApiModelConfigurationId?,
        ttsId: TtsProviderId?,
        mediaId: MediaProviderId?,
        liveVoiceId: LiveVoiceProviderId?,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    NeoModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        AssignmentSelectionContent(
            modifier = Modifier.fillMaxWidth(),
            slotLabel = slotLabel,
            localAssets = localAssets,
            apiAssets = apiAssets,
            ttsAssets = ttsAssets,
            liveVoiceAssets = liveVoiceAssets,
            mediaAssets = mediaAssets,
            onDismiss = onDismiss,
            onSelect = onSelect,
        )
    }
}

private sealed interface AssignmentSelectionView {
    data object AssetList : AssignmentSelectionView

    data class LocalConfigList(
        val asset: LocalModelAssetUi,
    ) : AssignmentSelectionView

    data class ApiConfigList(
        val asset: ApiModelAssetUi,
    ) : AssignmentSelectionView
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod")
@Composable
fun AssignmentSelectionContent(
    modifier: Modifier = Modifier,
    slotLabel: String,
    localAssets: List<LocalModelAssetUi>,
    apiAssets: List<ApiModelAssetUi>,
    ttsAssets: List<TtsProviderAssetUi>,
    liveVoiceAssets: List<LiveVoiceProviderAssetUi>,
    mediaAssets: List<MediaProviderAssetUi>,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    onSelect: (
        localId: LocalModelConfigurationId?,
        apiId: ApiModelConfigurationId?,
        ttsId: TtsProviderId?,
        mediaId: MediaProviderId?,
        liveVoiceId: LiveVoiceProviderId?,
    ) -> Unit,
) {
    var selectedLocalId by remember { mutableStateOf<LocalModelConfigurationId?>(null) }
    var selectedApiId by remember { mutableStateOf<ApiModelConfigurationId?>(null) }
    var selectedTtsId by remember { mutableStateOf<TtsProviderId?>(null) }
    var selectedMediaId by remember { mutableStateOf<MediaProviderId?>(null) }
    var selectedLiveVoiceId by remember { mutableStateOf<LiveVoiceProviderId?>(null) }
    val hasLocalTab = localAssets.isNotEmpty()
    val hasApiTab = apiAssets.isNotEmpty()
    val hasTtsTab = ttsAssets.isNotEmpty()
    val hasLiveVoiceTab = liveVoiceAssets.isNotEmpty()
    val hasMediaTab = mediaAssets.isNotEmpty()
    var selectedTabIndex by remember(hasLocalTab, hasApiTab, hasTtsTab, hasLiveVoiceTab, hasMediaTab) {
        mutableIntStateOf(0)
    }
    val tabs =
        buildList {
            if (hasLocalTab) add("Local Models")
            if (hasApiTab) add("API Models")
            if (hasTtsTab) add("TTS Providers")
            if (hasLiveVoiceTab) add("Live Voice Providers")
            if (hasMediaTab) add("Media Providers")
        }
    var viewState by remember { mutableStateOf<AssignmentSelectionView>(AssignmentSelectionView.AssetList) }

    Column(
        modifier =
            modifier
                .fillMaxWidth(),
    ) {
        when (val state = viewState) {
            is AssignmentSelectionView.AssetList -> {
                NeoSheetHeader(
                    title = "Assign Model to $slotLabel",
                    onDismiss = onDismiss,
                    onBackClick = onBack,
                )
            }
            is AssignmentSelectionView.LocalConfigList -> {
                NeoSheetHeader(
                    title = state.asset.friendlyName,
                    subtitle = "Presets",
                    onDismiss = onDismiss,
                    onBackClick = { viewState = AssignmentSelectionView.AssetList },
                )
            }
            is AssignmentSelectionView.ApiConfigList -> {
                NeoSheetHeader(
                    title = state.asset.displayName,
                    subtitle = "Presets",
                    onDismiss = onDismiss,
                    onBackClick = { viewState = AssignmentSelectionView.AssetList },
                )
            }
        }

        AnimatedContent(
            targetState = viewState,
            label = "AssignmentSelectionTransition",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
        ) { state ->
            when (state) {
                is AssignmentSelectionView.AssetList -> {
                    Column {
                        if (tabs.size > 1) {
                            NeoSheetTabRow(
                                selectedTabIndex = selectedTabIndex,
                                tabs = tabs,
                                onTabSelected = { selectedTabIndex = it },
                            )
                        }

                        AnimatedContent(
                            targetState =
                                when {
                                    hasLocalTab &&
                                        (hasApiTab || hasTtsTab || hasLiveVoiceTab || hasMediaTab) ->
                                        selectedTabIndex
                                    hasLocalTab -> 0
                                    hasApiTab || hasTtsTab || hasLiveVoiceTab || hasMediaTab -> selectedTabIndex
                                    else -> 0
                                },
                            label = "AssignmentSelectionTabTransition",
                            modifier = Modifier.fillMaxWidth(),
                        ) { tabIndex ->
                            val currentTabTitle = tabs.getOrNull(tabIndex)
                            LazyColumn(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .padding(top = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                when (currentTabTitle) {
                                    "Local Models" -> {
                                        items(localAssets, key = { "local_${it.metadataId.value}" }) { asset ->
                                            NeoChevronCard(
                                                title = asset.friendlyName,
                                                subtitle = "${asset.providerName} • ${asset.format}",
                                                footer = "${asset.configurations.size} Preset(s) available",
                                                isMultimodal = asset.isMultimodal,
                                                isSelected = asset.configurations.any { it.id == selectedLocalId },
                                                onClick = { viewState = AssignmentSelectionView.LocalConfigList(asset) },
                                            )
                                        }
                                    }
                                    "API Models" -> {
                                        items(apiAssets, key = { "api_${it.credentialsId.value}" }) { asset ->
                                            NeoChevronCard(
                                                title = asset.displayName,
                                                subtitle = asset.provider.displayName,
                                                footer = "${asset.configurations.size} Preset(s) available",
                                                isMultimodal = asset.configurations.any { it.isMultimodal },
                                                isSelected = asset.configurations.any { it.id == selectedApiId },
                                                onClick = { viewState = AssignmentSelectionView.ApiConfigList(asset) },
                                            )
                                        }
                                    }
                                    "TTS Providers" -> {
                                        items(ttsAssets, key = { "tts_${it.id.value}" }) { asset ->
                                            NeoChevronCard(
                                                title = asset.displayName,
                                                subtitle = "${asset.provider.displayName} • ${asset.voiceName}",
                                                footer = "1 Preset(s) available",
                                                isSelected = selectedTtsId == asset.id,
                                                onClick = {
                                                    selectedTtsId = asset.id
                                                    selectedLocalId = null
                                                    selectedApiId = null
                                                    selectedMediaId = null
                                                    selectedLiveVoiceId = null
                                                },
                                            )
                                        }
                                    }
                                    "Live Voice Providers" -> {
                                        items(liveVoiceAssets, key = { "live_voice_${it.id.value}" }) { asset ->
                                            NeoChevronCard(
                                                title = asset.displayName,
                                                subtitle = "${asset.provider.displayName} • ${asset.voiceName}",
                                                footer = "1 Preset(s) available",
                                                isSelected = selectedLiveVoiceId == asset.id,
                                                onClick = {
                                                    selectedLiveVoiceId = asset.id
                                                    selectedLocalId = null
                                                    selectedApiId = null
                                                    selectedTtsId = null
                                                    selectedMediaId = null
                                                },
                                            )
                                        }
                                    }
                                    "Media Providers" -> {
                                        items(mediaAssets, key = { "media_${it.id.value}" }) { asset ->
                                            NeoChevronCard(
                                                title = asset.displayName,
                                                subtitle = "${asset.provider.displayName} • ${asset.modelName}",
                                                footer = "1 Preset(s) available",
                                                isSelected = selectedMediaId == asset.id,
                                                onClick = {
                                                    selectedMediaId = asset.id
                                                    selectedLocalId = null
                                                    selectedApiId = null
                                                    selectedTtsId = null
                                                    selectedLiveVoiceId = null
                                                },
                                            )
                                        }
                                    }
                                    else -> {
                                        item {
                                            Text(
                                                text = "No options available.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(vertical = 16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                is AssignmentSelectionView.LocalConfigList -> {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.asset.configurations, key = { it.id.value }) { config ->
                            ConfigSelectionCard(
                                label = config.displayName,
                                isSelected = selectedLocalId == config.id,
                                onClick = {
                                    selectedLocalId = config.id
                                    selectedApiId = null
                                },
                            )
                        }
                    }
                }

                is AssignmentSelectionView.ApiConfigList -> {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.asset.configurations, key = { it.id.value }) { config ->
                            ConfigSelectionCard(
                                label = config.displayName,
                                subtitle = config.modelId.takeIf { it.isNotBlank() },
                                isSelected = selectedApiId == config.id,
                                onClick = {
                                    selectedApiId = config.id
                                    selectedLocalId = null
                                },
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NeoSecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            NeoPrimaryButton(
                text = "Confirm",
                onClick = {
                    onSelect(
                        selectedLocalId,
                        selectedApiId,
                        selectedTtsId,
                        selectedMediaId,
                        selectedLiveVoiceId,
                    )
                },
                enabled =
                    selectedLocalId != null ||
                        selectedApiId != null ||
                        selectedTtsId != null ||
                        selectedMediaId != null ||
                        selectedLiveVoiceId != null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ConfigSelectionCard(
    label: String,
    subtitle: String? = null,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    NeoSelectionCard(
        title = label,
        subtitle = subtitle,
        isSelected = isSelected,
        onClick = onClick,
    )
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, name = "Assignment Selection Content")
@Composable
private fun PreviewAssignmentSelectionContent() {
    EnclaveTheme {
        AssignmentSelectionContent(
            modifier = Modifier.padding(bottom = 32.dp),
            slotLabel = "Main",
            localAssets = emptyList(),
            apiAssets = emptyList(),
            ttsAssets = emptyList(),
            liveVoiceAssets = emptyList(),
            mediaAssets = emptyList(),
            onDismiss = {},
            onSelect = { _, _, _, _, _ -> },
        )
    }
}

package com.browntowndev.enclave.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveBackScreenHeader
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveCircularProgressIndicator
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveOutlinedTextField
import com.browntowndev.enclave.core.ui.component.enclave.EnclavePrimaryButton
import com.browntowndev.enclave.core.ui.component.enclave.enclaveOutlinedTextFieldColors
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.inference.ApiProvider

private const val DEFAULT_OPENAI_LIVE_VOICE_MODEL = "gpt-4o-realtime-preview"
private const val DEFAULT_XAI_LIVE_VOICE_MODEL = "grok-voice-think-fast-1.0"
private const val DEFAULT_GOOGLE_LIVE_VOICE_MODEL = "gemini-2.0-flash-live-001"

private data class LiveVoiceVoiceOption(
    val id: String,
    val displayName: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod")
@Composable
fun LiveVoiceConfigureScreen(
    uiState: SettingsUiState,
    apiKey: String,
    onNavigateBack: () -> Unit,
    onLiveVoiceAssetFieldChange: (LiveVoiceProviderAssetUi) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSelectReusableApiCredential: (String?) -> Unit,
    onSaveLiveVoiceProvider: () -> Unit,
    onFetchLiveVoiceModels: () -> Unit,
    onUpdateModelSearchQuery: (String) -> Unit,
    onToggleModelProviderFilter: (String) -> Unit,
    onClearModelProviderFilters: () -> Unit,
    onUpdateModelSortOption: (ModelSortOption) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val draft = uiState.liveVoiceProviderEditor.assetDraft ?: LiveVoiceProviderAssetUi()
    val isKeySaved = uiState.liveVoiceProviderEditor.selectedReusableCredential != null || draft.id.value.isNotEmpty()
    var providerDropdownExpanded by remember { mutableStateOf(false) }
    var voiceDropdownExpanded by remember { mutableStateOf(false) }
    var showModelSelectionSheet by remember { mutableStateOf(false) }

    val isSaveEnabled =
        draft.displayName.isNotBlank() &&
            draft.voiceName.isNotBlank() &&
            (apiKey.isNotBlank() || isKeySaved)

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            EnclaveBackScreenHeader(
                title = "Live Voice Provider",
                onBackClick = onNavigateBack,
            )
        },
        bottomBar = {
            EnclavePrimaryButton(
                text = "Save Live Voice Provider",
                onClick = onSaveLiveVoiceProvider,
                enabled = isSaveEnabled,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = EnclaveNeoTokens.screenHorizontalPadding)
                        .padding(bottom = 20.dp),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = EnclaveNeoTokens.screenHorizontalPadding)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.sectionSpacing),
        ) {
            ExposedDropdownMenuBox(
                expanded = providerDropdownExpanded,
                onExpandedChange = { providerDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                EnclaveOutlinedTextField(
                    value = draft.provider.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("API Provider") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerDropdownExpanded) },
                    modifier =
                        Modifier
                            .padding(top = 16.dp)
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = providerDropdownExpanded,
                    onDismissRequest = { providerDropdownExpanded = false },
                ) {
                    ApiProvider.entries.filter { it.supportsRealtimeVoice }.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.displayName) },
                            onClick = {
                                onLiveVoiceAssetFieldChange(draft.copy(provider = provider, voiceName = ""))
                                providerDropdownExpanded = false
                            },
                        )
                    }
                }
            }

            val reusableCredentials =
                reusableCredentialsForProvider(
                    provider = draft.provider,
                    apiAssets = uiState.apiProvidersSheet.assets,
                    mediaAssets = uiState.mediaProvidersSheet.assets,
                    ttsAssets = uiState.ttsProvidersSheet.assets,
                    liveVoiceAssets = uiState.liveVoiceProvidersSheet.assets,
                    excludeLiveVoiceProviderId = draft.id.takeIf { it.value.isNotEmpty() },
                )
            val canUseStoredCredential = draft.id.value.isEmpty() && reusableCredentials.isNotEmpty()

            ApiKeyWithReuseSection(
                reusableCredentials = reusableCredentials,
                selectedReusableCredential = uiState.liveVoiceProviderEditor.selectedReusableCredential,
                canUseStoredCredential = canUseStoredCredential,
                apiKey = apiKey,
                keyPresent = isKeySaved,
                onApiKeyChange = onApiKeyChange,
                onSelectReusableCredential = onSelectReusableApiCredential,
            )

            EnclaveOutlinedTextField(
                value = draft.displayName,
                onValueChange = { onLiveVoiceAssetFieldChange(draft.copy(displayName = it)) },
                label = { Text("Display Name (e.g. My OpenAI Live Voice)") },
                modifier = Modifier.fillMaxWidth(),
            )

            val discovery = uiState.liveVoiceProviderEditor.discovery
            val hasModels = discovery.models.isNotEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val modelHint =
                    when {
                        discovery.isLoading -> "Fetching models..."
                        hasModels -> "${discovery.models.size} models discovered"
                        else -> "Discover models or enter a model ID manually"
                    }
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(if (hasModels) Modifier.clickable { showModelSelectionSheet = true } else Modifier),
                ) {
                    EnclaveOutlinedTextField(
                        value = draft.modelName ?: draft.provider.defaultLiveVoiceModelId(),
                        onValueChange = { onLiveVoiceAssetFieldChange(draft.copy(modelName = it)) },
                        readOnly = hasModels,
                        enabled = !hasModels,
                        label = { Text("Model ID") },
                        trailingIcon =
                            if (hasModels) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select model",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                null
                            },
                        modifier = Modifier.fillMaxWidth(),
                        colors = enclaveOutlinedTextFieldColors(),
                    )
                }
                Text(
                    text = modelHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 4.dp, end = 16.dp),
                )
                if (discovery.isTruncated) {
                    Text(
                        text = "Model list may be incomplete (provider catalog truncated). You can still enter a model ID manually.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 4.dp, end = 16.dp),
                    )
                }

                EnclaveTextButton(
                    onClick = onFetchLiveVoiceModels,
                    enabled = !discovery.isLoading && (apiKey.isNotBlank() || isKeySaved),
                    modifier = Modifier.align(Alignment.End),
                ) {
if (discovery.isLoading) {
                        EnclaveCircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Loading models\u2026")
                    } else {
                        Icon(
                            imageVector = if (hasModels) Icons.Default.Refresh else Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (hasModels) "Refresh Models" else "Discover Models")
                    }
}
            }

            if (draft.provider == ApiProvider.OPENAI || draft.provider == ApiProvider.XAI) {
                EnclaveOutlinedTextField(
                    value = draft.baseUrl ?: "",
                    onValueChange = { onLiveVoiceAssetFieldChange(draft.copy(baseUrl = it)) },
                    label = { Text("Base URL (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val voices = liveVoiceVoicesForProvider(draft.provider)
            ExposedDropdownMenuBox(
                expanded = voiceDropdownExpanded,
                onExpandedChange = { voiceDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                EnclaveOutlinedTextField(
                    value = voices.find { it.id == draft.voiceName }?.displayName ?: "Select a voice",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Voice") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceDropdownExpanded) },
                    modifier =
                        Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = voiceDropdownExpanded,
                    onDismissRequest = { voiceDropdownExpanded = false },
                ) {
                    voices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text(voice.displayName) },
                            onClick = {
                                onLiveVoiceAssetFieldChange(draft.copy(voiceName = voice.id))
                                voiceDropdownExpanded = false
                            },
                        )
                    }
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use as Default",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Set this as the default Live Voice provider for realtime conversations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = draft.useAsDefault,
                    onCheckedChange = { onLiveVoiceAssetFieldChange(draft.copy(useAsDefault = it)) },
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showModelSelectionSheet) {
        val discovery = uiState.liveVoiceProviderEditor.discovery
        ModelSelectionBottomSheet(
            availableModels = discovery.models,
            filteredModels = discovery.filteredModels,
            searchQuery = discovery.searchQuery,
            providerFilters = discovery.providerFilters,
            sortOption = discovery.sortOption,
            onUpdateSearchQuery = onUpdateModelSearchQuery,
            onToggleProviderFilter = onToggleModelProviderFilter,
            onClearProviderFilters = onClearModelProviderFilters,
            onUpdateSortOption = onUpdateModelSortOption,
            onModelSelected = { selectedModelId ->
                onLiveVoiceAssetFieldChange(draft.copy(modelName = selectedModelId))
            },
            onDismissRequest = { showModelSelectionSheet = false },
        )
    }
}

private fun ApiProvider.defaultLiveVoiceModelId(): String =
    when (this) {
        ApiProvider.OPENAI -> DEFAULT_OPENAI_LIVE_VOICE_MODEL
        ApiProvider.XAI -> DEFAULT_XAI_LIVE_VOICE_MODEL
        ApiProvider.GOOGLE -> DEFAULT_GOOGLE_LIVE_VOICE_MODEL
        else -> ""
    }

private fun liveVoiceVoicesForProvider(provider: ApiProvider): List<LiveVoiceVoiceOption> =
    when (provider) {
        ApiProvider.OPENAI ->
            listOf(
                LiveVoiceVoiceOption("alloy", "Alloy"),
                LiveVoiceVoiceOption("ash", "Ash"),
                LiveVoiceVoiceOption("ballad", "Ballad"),
                LiveVoiceVoiceOption("coral", "Coral"),
                LiveVoiceVoiceOption("echo", "Echo"),
                LiveVoiceVoiceOption("sage", "Sage"),
                LiveVoiceVoiceOption("shimmer", "Shimmer"),
                LiveVoiceVoiceOption("verse", "Verse"),
            )
        ApiProvider.XAI ->
            listOf(
                LiveVoiceVoiceOption("eve", "Eve"),
                LiveVoiceVoiceOption("ara", "Ara"),
                LiveVoiceVoiceOption("rex", "Rex"),
                LiveVoiceVoiceOption("sal", "Sal"),
                LiveVoiceVoiceOption("leo", "Leo"),
            )
        ApiProvider.GOOGLE ->
            listOf(
                LiveVoiceVoiceOption("Puck", "Puck"),
                LiveVoiceVoiceOption("Charon", "Charon"),
                LiveVoiceVoiceOption("Kore", "Kore"),
                LiveVoiceVoiceOption("Fenrir", "Fenrir"),
                LiveVoiceVoiceOption("Aoede", "Aoede"),
            )
        else -> emptyList()
    }

@Preview(showBackground = true, name = "Live Voice Configure Screen")
@Composable
fun LiveVoiceConfigureScreenPreview() {
    EnclaveTheme {
        LiveVoiceConfigureScreen(
            uiState =
                MockSettingsData.baseUiState.copy(
                    liveVoiceProviderEditor =
                        LiveVoiceProviderEditorUiState(
                            assetDraft =
                                LiveVoiceProviderAssetUi(
                                    displayName = "My OpenAI Live Voice",
                                    provider = ApiProvider.OPENAI,
                                    voiceName = "alloy",
                                    modelName = DEFAULT_OPENAI_LIVE_VOICE_MODEL,
                                ),
                        ),
                ),
            apiKey = "test-key",
            onNavigateBack = {},
            onLiveVoiceAssetFieldChange = {},
            onApiKeyChange = {},
            onSelectReusableApiCredential = {},
            onSaveLiveVoiceProvider = {},
            onFetchLiveVoiceModels = {},
            onUpdateModelSearchQuery = {},
            onToggleModelProviderFilter = {},
            onClearModelProviderFilters = {},
            onUpdateModelSortOption = {},
        )
    }
}

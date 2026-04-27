package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import kotlinx.coroutines.flow.collect

@Composable
fun MediaConfigureRoute(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val apiKey by viewModel.currentApiKey.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val handleBack: () -> Unit = {
        viewModel.onShowMediaProvidersSheet(false)
        onNavigateBack()
    }

    MediaConfigureScreen(
        uiState = uiState,
        apiKey = apiKey,
        onNavigateBack = handleBack,
        onMediaAssetFieldChange = viewModel::onMediaAssetFieldChange,
        onApiKeyChange = viewModel::onApiKeyChange,
        onSelectReusableApiCredential = viewModel::onSelectReusableApiCredential,
        onSaveMediaProvider = {
            viewModel.onSaveMediaProvider(onSuccess = {
                viewModel.onShowMediaProvidersSheet(false)
                onNavigateBack()
            })
        },
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaConfigureScreen(
    uiState: SettingsUiState,
    apiKey: String,
    onNavigateBack: () -> Unit,
    onMediaAssetFieldChange: (MediaProviderAssetUi) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSelectReusableApiCredential: (ApiCredentialsId?) -> Unit,
    onSaveMediaProvider: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val draft = uiState.mediaProviderEditor.assetDraft ?: MediaProviderAssetUi()
    val isKeySaved = uiState.mediaProviderEditor.selectedReusableCredential != null || draft.id.value.isNotEmpty()
    val showSavedIndicator = isKeySaved && apiKey.isEmpty()
    var passwordVisible by remember { mutableStateOf(false) }
    var providerDropdownExpanded by remember { mutableStateOf(false) }
    var capabilityDropdownExpanded by remember { mutableStateOf(false) }

    val isSaveEnabled = draft.displayName.isNotBlank() &&
            draft.modelName.isNotBlank() &&
            (apiKey.isNotBlank() || isKeySaved)

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Media Generation Provider", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = onSaveMediaProvider,
                    enabled = isSaveEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Save Media Provider", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Provider Selection
            ExposedDropdownMenuBox(
                expanded = providerDropdownExpanded,
                onExpandedChange = { providerDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = draft.provider.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("API Provider") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerDropdownExpanded) },
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = providerDropdownExpanded,
                    onDismissRequest = { providerDropdownExpanded = false }
                ) {
                    listOf(ApiProvider.OPENAI, ApiProvider.XAI, ApiProvider.GOOGLE).forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.displayName) },
                            onClick = {
                                onMediaAssetFieldChange(draft.copy(provider = provider))
                                providerDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Capability Selection
            ExposedDropdownMenuBox(
                expanded = capabilityDropdownExpanded,
                onExpandedChange = { capabilityDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = draft.capability.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Capability") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = capabilityDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = capabilityDropdownExpanded,
                    onDismissRequest = { capabilityDropdownExpanded = false }
                ) {
                    MediaCapability.entries.forEach { capability ->
                        DropdownMenuItem(
                            text = { Text(capability.name) },
                            onClick = {
                                onMediaAssetFieldChange(draft.copy(capability = capability))
                                capabilityDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Reusable Key Dropdown
            val reusableCredentials = uiState.apiProvidersSheet.assets
                .filter { it.provider == draft.provider }
                .map { ReusableApiCredentialUi(it.credentialsId, it.displayName, it.modelId, it.credentialAlias) }
            
            if (draft.id.value.isEmpty() && reusableCredentials.isNotEmpty()) {
                ReusableCredentialDropdown(
                    reusableCredentials = reusableCredentials,
                    selectedReusableCredential = uiState.mediaProviderEditor.selectedReusableCredential,
                    onSelectReusableCredential = onSelectReusableApiCredential
                )
            }

            // API Key
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("API Key")
                        if (showSavedIndicator) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Key saved",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                placeholder = {
                    if (showSavedIndicator) {
                        Text("Saved securely")
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                supportingText = {
                    if (isKeySaved) {
                        Text(
                            text = if (apiKey.isEmpty()) {
                                "The stored key stays encrypted and hidden. Typing here switches back to a new key."
                            } else {
                                "You are entering a new key. The previously stored key will be replaced upon saving."
                            }
                        )
                    }
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Toggle password visibility" else "Toggle password visibility"
                        )
                    }
                }
            )

            // Display Name
            OutlinedTextField(
                value = draft.displayName,
                onValueChange = { onMediaAssetFieldChange(draft.copy(displayName = it)) },
                label = { Text("Display Name (e.g. My OpenAI DALL-E)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Model ID
            OutlinedTextField(
                value = draft.modelName,
                onValueChange = { onMediaAssetFieldChange(draft.copy(modelName = it)) },
                label = { Text("Model ID (e.g. dall-e-3)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Base URL (for proxies)
            OutlinedTextField(
                value = draft.baseUrl ?: "",
                onValueChange = { onMediaAssetFieldChange(draft.copy(baseUrl = it)) },
                label = { Text("Base URL (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Use as Default Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use as Default",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Set this as the default model for ${draft.capability.name.lowercase()} generation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = draft.useAsDefault,
                    onCheckedChange = { onMediaAssetFieldChange(draft.copy(useAsDefault = it)) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Preview(showBackground = true, name = "Media Configure Screen")
@Composable
fun MediaConfigureScreenPreview() {
    PocketCrewTheme {
        MediaConfigureScreen(
            uiState = MockSettingsData.baseUiState,
            apiKey = "test-key",
            onNavigateBack = {},
            onMediaAssetFieldChange = {},
            onApiKeyChange = {},
            onSelectReusableApiCredential = {},
            onSaveMediaProvider = {},
        )
    }
}

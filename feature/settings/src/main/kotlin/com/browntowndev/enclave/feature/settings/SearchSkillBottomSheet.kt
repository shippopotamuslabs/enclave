package com.browntowndev.enclave.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.XIcon
import com.browntowndev.enclave.core.ui.component.neo.NeoApiKeyField
import com.browntowndev.enclave.core.ui.component.neo.NeoModalBottomSheet
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSectionHeader
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetHeader
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetTabRow
import com.browntowndev.enclave.core.ui.component.neo.NeoToggleRow
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.domain.model.settings.SearchProvider

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
fun SearchSkillBottomSheet(
    uiState: SettingsUiState,
    onDismiss: () -> Unit,
    onTabChange: (Int) -> Unit,
    onSearchEnabledChange: (Boolean) -> Unit,
    onSearchProviderChange: (SearchProvider) -> Unit,
    onTavilyApiKeyChange: (String) -> Unit,
    onClearTavilyApiKey: () -> Unit,
    onExaApiKeyChange: (String) -> Unit,
    onClearExaApiKey: () -> Unit,
    onUpdateShowSearchProviderSwitchAlert: (Boolean) -> Unit,
    onSaveWebSearch: () -> Unit,
    onXSearchEnabledChange: (Boolean) -> Unit,
    onXApiKeyChange: (String) -> Unit,
    onClearXApiKey: () -> Unit,
    onSaveXSearch: (onSuccess: () -> Unit) -> Unit,
) {
    val searchState = uiState.searchSkillEditor
    val xSearchState = uiState.xSearchEditor
    val selectedTab = uiState.home.searchSkillTabIndex
    var showSwitchConfirm by remember { mutableStateOf<SearchProvider?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    NeoModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.sectionSpacing),
        ) {
            NeoSheetHeader(
                title = "Search Skills",
                subtitle = "Configure web and X search for the assistant.",
                onDismiss = onDismiss,
            )

            NeoSheetTabRow(
                selectedTabIndex = selectedTab,
                tabs = listOf("Web", "X"),
                onTabSelected = onTabChange,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.sectionSpacing),
            ) {
                if (selectedTab == 0) {
                    WebSearchTabContent(
                        searchState = searchState,
                        onSearchEnabledChange = onSearchEnabledChange,
                        onSearchProviderChange = { targetProvider ->
                            if (shouldShowProviderSwitchAlert(searchState)) {
                                showSwitchConfirm = targetProvider
                            } else {
                                onSearchProviderChange(targetProvider)
                            }
                        },
                        onTavilyApiKeyChange = onTavilyApiKeyChange,
                        onClearTavilyApiKey = onClearTavilyApiKey,
                        onExaApiKeyChange = onExaApiKeyChange,
                        onClearExaApiKey = onClearExaApiKey,
                        onSave = onSaveWebSearch,
                        onDismiss = onDismiss,
                    )
                } else {
                    XSearchTabContent(
                        xSearchState = xSearchState,
                        onEnabledChange = onXSearchEnabledChange,
                        onApiKeyChange = onXApiKeyChange,
                        onClearApiKey = onClearXApiKey,
                        onSave = onSaveXSearch,
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }

    if (showSwitchConfirm != null) {
        val targetProvider = showSwitchConfirm!!
        val currentProvider = if (targetProvider == SearchProvider.TAVILY) SearchProvider.EXA else SearchProvider.TAVILY
        var dontShowAgain by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showSwitchConfirm = null },
            title = { Text("Switch Provider") },
            text = {
                Column {
                    Text("Enabling ${targetProvider.displayName} will disable ${currentProvider.displayName} for web searches.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = { dontShowAgain = it },
                        )
                        Text(
                            text = "Don't show again",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (dontShowAgain) {
                            onUpdateShowSearchProviderSwitchAlert(false)
                        }
                        onSearchProviderChange(targetProvider)
                        showSwitchConfirm = null
                    },
                ) {
                    Text("Switch")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchConfirm = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

internal fun shouldShowProviderSwitchAlert(state: SearchSkillEditorUiState): Boolean {
    val currentHasKey =
        when (state.selectedProvider) {
            SearchProvider.TAVILY -> state.tavilyKeyPresent
            SearchProvider.EXA -> state.exaKeyPresent
        }
    return currentHasKey
}

@Composable
internal fun ApiKeyInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyPresent: Boolean,
    onClearKey: () -> Unit,
) {
    NeoApiKeyField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        keyPresent = keyPresent,
        onClearKey = onClearKey,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WebSearchTabContent(
    searchState: SearchSkillEditorUiState,
    onSearchEnabledChange: (Boolean) -> Unit,
    onSearchProviderChange: (SearchProvider) -> Unit,
    onTavilyApiKeyChange: (String) -> Unit,
    onClearTavilyApiKey: () -> Unit,
    onExaApiKeyChange: (String) -> Unit,
    onClearExaApiKey: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.sectionSpacing)) {
        NeoToggleRow(
            title = "Enable Web Search",
            icon = Icons.Default.Public,
            checked = searchState.enabled,
            onCheckedChange = onSearchEnabledChange,
            subtitle = "Allow AI to search the web for real-time information",
        )

        if (searchState.enabled) {
            NeoSectionHeader(text = "Search Provider")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeOption(
                    text = "Tavily",
                    selected = searchState.selectedProvider == SearchProvider.TAVILY,
                    onClick = {
                        if (searchState.selectedProvider != SearchProvider.TAVILY) {
                            onSearchProviderChange(SearchProvider.TAVILY)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                ThemeOption(
                    text = "Exa",
                    selected = searchState.selectedProvider == SearchProvider.EXA,
                    onClick = {
                        if (searchState.selectedProvider != SearchProvider.EXA) {
                            onSearchProviderChange(SearchProvider.EXA)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (searchState.selectedProvider) {
                SearchProvider.TAVILY -> {
                    ApiKeyInput(
                        label = "Tavily API Key",
                        value = searchState.currentTavilyApiKey,
                        onValueChange = onTavilyApiKeyChange,
                        keyPresent = searchState.tavilyKeyPresent,
                        onClearKey = onClearTavilyApiKey,
                    )
                }
                SearchProvider.EXA -> {
                    ApiKeyInput(
                        label = "Exa API Key",
                        value = searchState.currentExaApiKey,
                        onValueChange = onExaApiKeyChange,
                        keyPresent = searchState.exaKeyPresent,
                        onClearKey = onClearExaApiKey,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        NeoPrimaryButton(
            text = "Save Web Settings",
            icon = Icons.Default.Check,
            onClick = {
                onSave()
                onDismiss()
            },
        )
    }
}

@Composable
private fun XSearchTabContent(
    xSearchState: XSearchEditorUiState,
    onEnabledChange: (Boolean) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onSave: (onSuccess: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.sectionSpacing)) {
        NeoToggleRow(
            title = "Enable X Search",
            icon = XIcon,
            checked = xSearchState.enabled,
            onCheckedChange = onEnabledChange,
            subtitle = "Allow AI to search for recent posts on X (Twitter)",
        )

        if (xSearchState.enabled) {
            ApiKeyInput(
                label = "X API Key",
                value = xSearchState.currentApiKey,
                onValueChange = onApiKeyChange,
                keyPresent = xSearchState.apiKeyPresent,
                onClearKey = onClearApiKey,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        NeoPrimaryButton(
            text = "Save X Settings",
            icon = Icons.Default.Check,
            onClick = {
                onSave {
                    onDismiss()
                }
            },
        )
    }
}

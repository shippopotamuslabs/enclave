package com.browntowndev.enclave.feature.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.XIcon
import com.browntowndev.enclave.core.ui.component.sheet.JumpFreeModalBottomSheet
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

    val sheetContainerColor = MaterialTheme.colorScheme.surface

    JumpFreeModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetContainerColor,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = sheetContainerColor,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {},
                indicator = { tabPositions ->
                    SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { onTabChange(0) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Web",
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { onTabChange(1) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    icon = {
                        Icon(
                            imageVector = XIcon,
                            contentDescription = "X",
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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

@Composable
private fun SearchSkillToggleCard(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        ListItem(
            leadingContent = {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            headlineContent = { Text(title) },
            supportingContent = subtitle?.let { text -> { Text(text) } },
            trailingContent = {
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier =
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onCheckedChange(!checked) },
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
    var passwordVisible by remember { mutableStateOf(false) }
    val showSavedIndicator = keyPresent && value.isEmpty()

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label)
                    if (showSavedIndicator) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Key saved",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            placeholder = {
                if (showSavedIndicator) {
                    Text("Saved securely")
                } else if (keyPresent) {
                    Text("••••••••••••••••")
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "Toggle key visibility" else "Toggle key visibility",
                    )
                }
            },
            supportingText = {
                if (keyPresent) {
                    Text(
                        if (value.isEmpty()) {
                            "The stored key stays encrypted and hidden. Typing here switches back to a new key."
                        } else {
                            "You are entering a new key. The previously stored key will be replaced upon saving."
                        },
                    )
                }
            },
        )
        if (keyPresent) {
            TextButton(
                onClick = onClearKey,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Clear Saved Key", color = MaterialTheme.colorScheme.error)
            }
        }
    }
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
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SearchSkillToggleCard(
            title = "Enable Web Search",
            icon = Icons.Default.Public,
            checked = searchState.enabled,
            onCheckedChange = onSearchEnabledChange,
            subtitle = "Allow AI to search the web for real-time information",
        )

        if (searchState.enabled) {
            SectionHeader(text = "Search Provider")

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

        Button(
            onClick = {
                onSave()
                onDismiss()
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Web Settings", fontWeight = FontWeight.SemiBold)
        }
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
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SearchSkillToggleCard(
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

        Button(
            onClick = {
                onSave {
                    onDismiss()
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save X Settings", fontWeight = FontWeight.SemiBold)
        }
    }
}

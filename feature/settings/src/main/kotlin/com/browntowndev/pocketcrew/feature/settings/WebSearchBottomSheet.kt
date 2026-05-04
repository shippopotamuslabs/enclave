package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.component.sheet.JumpFreeModalBottomSheet
import com.browntowndev.pocketcrew.domain.model.settings.SearchProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSearchBottomSheet(
    uiState: SettingsUiState,
    onDismiss: () -> Unit,
    onSearchEnabledChange: (Boolean) -> Unit,
    onSearchProviderChange: (SearchProvider) -> Unit,
    onTavilyApiKeyChange: (String) -> Unit,
    onClearTavilyApiKey: () -> Unit,
    onExaApiKeyChange: (String) -> Unit,
    onClearExaApiKey: () -> Unit,
    onUpdateShowSearchProviderSwitchAlert: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    val searchState = uiState.searchSkillEditor
    var showSwitchConfirm by remember { mutableStateOf<SearchProvider?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    JumpFreeModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Web Search",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            SettingsToggle(
                title = "Enable Web Search",
                icon = Icons.Default.Public,
                checked = searchState.enabled,
                onCheckedChange = onSearchEnabledChange,
                subtitle = "Allow AI to search the web for real-time information"
            )

            if (searchState.enabled) {
                SectionHeader(text = "Search Provider")
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeOption(
                        text = "Tavily",
                        selected = searchState.selectedProvider == SearchProvider.TAVILY,
                        onClick = {
                            if (searchState.selectedProvider != SearchProvider.TAVILY) {
                                if (searchState.showSwitchAlert) {
                                    showSwitchConfirm = SearchProvider.TAVILY
                                } else {
                                    onSearchProviderChange(SearchProvider.TAVILY)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOption(
                        text = "Exa",
                        selected = searchState.selectedProvider == SearchProvider.EXA,
                        onClick = {
                            if (searchState.selectedProvider != SearchProvider.EXA) {
                                if (searchState.showSwitchAlert) {
                                    showSwitchConfirm = SearchProvider.EXA
                                } else {
                                    onSearchProviderChange(SearchProvider.EXA)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
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
                            onClearKey = onClearTavilyApiKey
                        )
                    }
                    SearchProvider.EXA -> {
                        ApiKeyInput(
                            label = "Exa API Key",
                            value = searchState.currentExaApiKey,
                            onValueChange = onExaApiKeyChange,
                            keyPresent = searchState.exaKeyPresent,
                            onClearKey = onClearExaApiKey
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save", fontWeight = FontWeight.SemiBold)
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = { dontShowAgain = it }
                        )
                        Text(
                            text = "Don't show again",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
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
                    }
                ) {
                    Text("Switch")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ApiKeyInput(
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
                            tint = MaterialTheme.colorScheme.primary
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
                        }
                    )
                }
            }
        )
        if (keyPresent) {
            TextButton(
                onClick = onClearKey,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Clear Saved Key", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

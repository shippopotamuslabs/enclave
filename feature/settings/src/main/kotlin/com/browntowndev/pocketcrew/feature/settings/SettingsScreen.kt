package com.browntowndev.pocketcrew.feature.settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onCloseClick: () -> Unit,
    onThemeChange: (AppTheme) -> Unit,
    onHapticPressChange: (Boolean) -> Unit,
    onHapticResponseChange: (Boolean) -> Unit,
    onShowCustomizationSheet: (Boolean) -> Unit,
    onShowDataControlsSheet: (Boolean) -> Unit,
    onShowMemoriesSheet: (Boolean) -> Unit,
    onOpenToS: () -> Unit,
    onShowFeedbackSheet: (Boolean) -> Unit,
    onNavigateToModelConfigure: (ModelType) -> Unit,
    onShowByokSheet: (Boolean) -> Unit,
    onNavigateToByokConfigure: () -> Unit,
    onSelectApiModelAsset: (ApiModelAssetUi?) -> Unit,
    onSelectApiModelConfig: (ApiModelConfigUi?) -> Unit,
    onDeleteApiModelAsset: (Long) -> Unit,
    onDeleteApiModelConfig: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onCloseClick) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionHeader(text = "Appearance")
                ThemeSelection(
                    selectedTheme = uiState.theme,
                    onThemeChange = onThemeChange
                )
            }

            item {
                SectionHeader(text = "Experience")
                SettingsToggle(
                    title = "Haptic Press",
                    checked = uiState.hapticPress,
                    onCheckedChange = onHapticPressChange
                )
                SettingsToggle(
                    title = "Haptic Response",
                    checked = uiState.hapticResponse,
                    onCheckedChange = onHapticResponseChange
                )
            }

            item {
                SectionHeader(text = "Models")
                SettingsNavigationItem(
                    title = "External AI Providers",
                    subtitle = "Manage API keys and presets",
                    onClick = { onShowByokSheet(true) }
                )
                SettingsNavigationItem(
                    title = "Model Management",
                    subtitle = "Assign pipeline slots and tunings",
                    onClick = { onNavigateToModelConfigure(ModelType.MAIN) }
                )
            }

            item {
                SectionHeader(text = "Data & Privacy")
                SettingsNavigationItem(
                    title = "Memories",
                    onClick = { onShowMemoriesSheet(true) }
                )
                SettingsNavigationItem(
                    title = "Data Controls",
                    onClick = { onShowDataControlsSheet(true) }
                )
            }

            item {
                SectionHeader(text = "About")
                SettingsNavigationItem(
                    title = "Terms of Service",
                    onClick = onOpenToS
                )
                SettingsNavigationItem(
                    title = "Feedback",
                    onClick = { onShowFeedbackSheet(true) }
                )
            }
            
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        if (uiState.showByokSheet) {
            ByokBottomSheet(
                uiState = uiState,
                onDismiss = { onShowByokSheet(false) },
                onNavigateToByokConfigure = onNavigateToByokConfigure,
                onSelectApiModelAsset = onSelectApiModelAsset,
                onSelectApiModelConfig = onSelectApiModelConfig,
                onDeleteApiModelAsset = onDeleteApiModelAsset,
                onDeleteApiModelConfig = onDeleteApiModelConfig
            )
        }
    }
}

// ==================== PREVIEWS ====================

@Preview(showBackground = true, name = "Main Settings Screen")
@Composable
fun PreviewSettingsScreen() {
    PocketCrewTheme {
        SettingsScreen(
            uiState = MockSettingsData.baseUiState,
            onCloseClick = {},
            onThemeChange = {},
            onHapticPressChange = {},
            onHapticResponseChange = {},
            onShowCustomizationSheet = {},
            onShowDataControlsSheet = {},
            onShowMemoriesSheet = {},
            onOpenToS = {},
            onShowFeedbackSheet = {},
            onNavigateToModelConfigure = {},
            onShowByokSheet = {},
            onNavigateToByokConfigure = {},
            onSelectApiModelAsset = {},
            onSelectApiModelConfig = {},
            onDeleteApiModelAsset = {},
            onDeleteApiModelConfig = {}
        )
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
    )
}

@Composable
fun ThemeSelection(
    selectedTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeOption(
                text = "System",
                selected = selectedTheme == AppTheme.SYSTEM,
                onClick = { onThemeChange(AppTheme.SYSTEM) },
                modifier = Modifier.weight(1f)
            )
            ThemeOption(
                text = "Dynamic",
                selected = selectedTheme == AppTheme.DYNAMIC,
                onClick = { onThemeChange(AppTheme.DYNAMIC) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeOption(
                text = "Light",
                selected = selectedTheme == AppTheme.LIGHT,
                onClick = { onThemeChange(AppTheme.LIGHT) },
                modifier = Modifier.weight(1f)
            )
            ThemeOption(
                text = "Dark",
                selected = selectedTheme == AppTheme.DARK,
                onClick = { onThemeChange(AppTheme.DARK) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ThemeOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SettingsToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
    )
}

@Composable
fun SettingsNavigationItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = null)
        },
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    )
}

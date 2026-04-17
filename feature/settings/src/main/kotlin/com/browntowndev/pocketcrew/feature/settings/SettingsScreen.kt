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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.settings.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onCloseClick: () -> Unit,
    onThemeChange: (AppTheme) -> Unit,
    onHapticPressChange: (Boolean) -> Unit,
    onHapticResponseChange: (Boolean) -> Unit,
    onAlwaysUseVisionModelChange: (Boolean) -> Unit,
    onShowCustomizationSheet: (Boolean) -> Unit,
    onShowDataControlsSheet: (Boolean) -> Unit,
    onShowMemoriesSheet: (Boolean) -> Unit,
    onOpenToS: () -> Unit,
    onShowFeedbackSheet: (Boolean) -> Unit,
    onShowVisionSettingsSheet: (Boolean) -> Unit,
    onNavigateToModelConfigure: (ModelType) -> Unit,
    onSetDefaultModel: (ModelType, LocalModelConfigurationId?, ApiModelConfigurationId?) -> Unit,
    onShowLocalModelsSheet: (Boolean) -> Unit,
    onShowByokSheet: (Boolean) -> Unit,
    onNavigateToByokConfigure: () -> Unit,
    onStartCreateApiModelAsset: () -> Unit,
    onStartConfigureSearchSkill: () -> Unit,
    onNavigateToCompactionConfigure: () -> Unit,
    onSelectApiModelAsset: (ApiModelAssetUi?) -> Unit,
    onSelectApiModelConfig: (ApiModelConfigUi?) -> Unit,
    onDeleteApiModelAsset: (ApiCredentialsId) -> Unit,
    onDeleteApiModelConfig: (ApiModelConfigurationId) -> Unit,
    onNavigateToLocalModelConfigure: () -> Unit,
    onSelectLocalModelAsset: (LocalModelAssetUi?) -> Unit,
    onSelectLocalModelConfig: (LocalModelConfigUi?) -> Unit,
    onDeleteLocalModelAsset: (LocalModelId) -> Unit,
    onDeleteLocalModelConfig: (LocalModelConfigurationId) -> Unit,
    onConfirmDeletionWithReassignment: (LocalModelConfigurationId?, ApiModelConfigurationId?) -> Unit,
    onDismissDeletionSafety: () -> Unit
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
                    selectedTheme = uiState.home.theme,
                    onThemeChange = onThemeChange
                )
            }

            item {
                SectionHeader(text = "Experience")
                SettingsToggle(
                    title = "Haptic Press",
                    icon = Icons.Default.TouchApp,
                    checked = uiState.home.hapticPress,
                    onCheckedChange = onHapticPressChange
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsToggle(
                    title = "Haptic Response",
                    icon = Icons.Default.Vibration,
                    checked = uiState.home.hapticResponse,
                    onCheckedChange = onHapticResponseChange
                )
            }

            item {
                SectionHeader(text = "Models")
                SettingsNavigationItem(
                    title = "Model Role Assignments",
                    subtitle = "Set models for chat and pipeline roles",
                    icon = Icons.AutoMirrored.Filled.Rule,
                    onClick = { onNavigateToModelConfigure(ModelType.MAIN) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsNavigationItem(
                    title = "Local AI Models",
                    subtitle = "Manage on-device models",
                    icon = Icons.Default.Memory,
                    onClick = { onShowLocalModelsSheet(true) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsNavigationItem(
                    title = "External AI Providers",
                    subtitle = "Manage API keys and presets",
                    icon = Icons.Default.Cloud,
                    onClick = { onShowByokSheet(true) }
                )
            }

            item {
                SectionHeader(text = "Tools")
                SettingsNavigationItem(
                    title = "Web Search",
                    subtitle = buildString {
                        append(if (uiState.searchSkillEditor.enabled) "Enabled" else "Disabled")
                        append(" • ")
                        append(if (uiState.searchSkillEditor.tavilyKeyPresent) "Tavily key Saved" else "No Tavily Key")
                    },
                    icon = Icons.Default.Public,
                    onClick = {
                        onStartConfigureSearchSkill()
                        onNavigateToByokConfigure()
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsNavigationItem(
                    title = "Vision",
                    subtitle = ModelType.VISION.description,
                    icon = Icons.Default.Visibility,
                    onClick = { onShowVisionSettingsSheet(true) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsNavigationItem(
                    title = "Compaction",
                    subtitle = "Manage context compression",
                    icon = Icons.AutoMirrored.Filled.Rule,
                    onClick = onNavigateToCompactionConfigure
                )
            }

            item {
                SectionHeader(text = "Data & Privacy")
                SettingsNavigationItem(
                    title = "Memories",
                    icon = Icons.Default.SmartToy,
                    onClick = { onShowMemoriesSheet(true) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsNavigationItem(
                    title = "Data Controls",
                    icon = Icons.Default.Shield,
                    onClick = { onShowDataControlsSheet(true) }
                )
            }

            item {
                SectionHeader(text = "About")
                SettingsNavigationItem(
                    title = "Terms of Service",
                    icon = Icons.Default.Info,
                    onClick = onOpenToS
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsNavigationItem(
                    title = "Feedback",
                    icon = Icons.Default.Feedback,
                    onClick = { onShowFeedbackSheet(true) }
                )
            }
            
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        if (uiState.home.isVisionSettingsSheetOpen) {
            VisionSettingsBottomSheet(
                uiState = uiState,
                onDismiss = { onShowVisionSettingsSheet(false) },
                onAlwaysUseVisionModelChange = onAlwaysUseVisionModelChange,
                onSetDefaultModel = onSetDefaultModel
            )
        }

        if (uiState.home.isApiProvidersSheetOpen) {
            ByokBottomSheet(
                uiState = uiState,
                onDismiss = { onShowByokSheet(false) },
                onNavigateToByokConfigure = onNavigateToByokConfigure,
                onStartCreateApiModelAsset = onStartCreateApiModelAsset,
                onSelectApiModelAsset = onSelectApiModelAsset,
                onSelectApiModelConfig = onSelectApiModelConfig,
                onDeleteApiModelAsset = onDeleteApiModelAsset,
                onDeleteApiModelConfig = onDeleteApiModelConfig,
                onConfirmDeletionWithReassignment = onConfirmDeletionWithReassignment,
                onDismissDeletionSafety = onDismissDeletionSafety
            )
        }

        if (uiState.home.isLocalModelsSheetOpen) {
            LocalModelsBottomSheet(
                uiState = uiState,
                onDismiss = { onShowLocalModelsSheet(false) },
                onNavigateToLocalModelConfigure = onNavigateToLocalModelConfigure,
                onSelectLocalModelAsset = onSelectLocalModelAsset,
                onSelectLocalModelConfig = onSelectLocalModelConfig,
                onDeleteLocalModelAsset = onDeleteLocalModelAsset,
                onDeleteLocalModelConfig = onDeleteLocalModelConfig,
                onConfirmDeletionWithReassignment = onConfirmDeletionWithReassignment,
                onDismissDeletionSafety = onDismissDeletionSafety
            )
        }
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
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    ListItem(
        leadingContent = {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { text -> { Text(text) } },
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
    icon: ImageVector,
    onClick: () -> Unit
) {
    SettingsNavigationItem(
        title = title,
        subtitle = subtitle,
        icon = { Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        onClick = onClick
    )
}

@Composable
fun SettingsNavigationItem(
    title: String,
    subtitle: String? = null,
    icon: @Composable (() -> Unit),
    onClick: () -> Unit
) {
    ListItem(
        leadingContent = {
            icon()
        },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    )
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
            onAlwaysUseVisionModelChange = {},
            onShowCustomizationSheet = {},
            onShowDataControlsSheet = {},
            onShowMemoriesSheet = {},
            onOpenToS = {},
            onShowFeedbackSheet = {},
            onShowVisionSettingsSheet = {},
            onNavigateToModelConfigure = {},
            onSetDefaultModel = { _, _, _ -> },
            onShowLocalModelsSheet = {},
            onShowByokSheet = {},
            onNavigateToByokConfigure = {},
            onStartCreateApiModelAsset = {},
            onStartConfigureSearchSkill = {},
            onNavigateToCompactionConfigure = {},
            onSelectApiModelAsset = {},
            onSelectApiModelConfig = {},
            onDeleteApiModelAsset = {},
            onDeleteApiModelConfig = {},
            onNavigateToLocalModelConfigure = {},
            onSelectLocalModelAsset = {},
            onSelectLocalModelConfig = {},
            onDeleteLocalModelAsset = {},
            onDeleteLocalModelConfig = {},
            onConfirmDeletionWithReassignment = { _, _ -> },
            onDismissDeletionSafety = {}
        )
    }
}

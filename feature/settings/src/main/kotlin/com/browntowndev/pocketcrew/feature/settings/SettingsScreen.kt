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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import com.browntowndev.pocketcrew.core.ui.component.XIcon
import com.browntowndev.pocketcrew.core.ui.component.vault.VaultReauthBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.core.ui.component.vault.VaultAuthUiState
import com.browntowndev.pocketcrew.core.ui.component.vault.VaultUserUiState
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory
import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.model.settings.SearchProvider
import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onCloseClick: () -> Unit,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onThemeChange: (AppTheme) -> Unit,
    onHapticPressChange: (Boolean) -> Unit,
    onHapticResponseChange: (Boolean) -> Unit,
    onBackgroundInferenceChange: (Boolean) -> Unit,
    onShowDataControlsSheet: (Boolean) -> Unit,
    onShowMemoriesSheet: (Boolean) -> Unit,
    onAddMemory: () -> Unit,
    onEditMemory: (StoredMemory) -> Unit,
    onUpdateMemoryDraft: (String, MemoryCategory) -> Unit,
    onSaveMemory: () -> Unit,
    onCancelMemoryEdit: () -> Unit,
    onDeleteMemory: (String) -> Unit,
    onShowTosSheet: (Boolean) -> Unit,
    onShowFeedbackSheet: (Boolean) -> Unit,
    onShowVisionSettingsSheet: (Boolean) -> Unit,
    onNavigateToModelConfigure: (ModelType) -> Unit,
    onSetDefaultModel: (ModelType, LocalModelConfigurationId?, ApiModelConfigurationId?, TtsProviderId?, MediaProviderId?) -> Unit,
    onShowLocalModelsSheet: (Boolean) -> Unit,
    onShowByokSheet: (Boolean) -> Unit,
    onNavigateToByokConfigure: () -> Unit,
    onStartCreateApiModelAsset: () -> Unit,
    onShowSearchSkillSheet: (Boolean) -> Unit,
    onSearchSkillTabChange: (Int) -> Unit,
    onSearchEnabledChange: (Boolean) -> Unit,
    onSearchProviderChange: (SearchProvider) -> Unit,
    onTavilyApiKeyChange: (String) -> Unit,
    onClearTavilyApiKey: () -> Unit,
    onExaApiKeyChange: (String) -> Unit,
    onClearExaApiKey: () -> Unit,
    onUpdateShowSearchProviderSwitchAlert: (Boolean) -> Unit,
    onSaveSearchSkillSettings: (onSuccess: () -> Unit) -> Unit,
    onXSearchEnabledChange: (Boolean) -> Unit,
    onXApiKeyChange: (String) -> Unit,
    onClearXApiKey: () -> Unit,
    onSaveXSearchSettings: (onSuccess: () -> Unit) -> Unit,
    onSelectApiModelAsset: (ApiModelAssetUi?) -> Unit,
    onSelectApiModelConfig: (ApiModelConfigUi?) -> Unit,
    onDeleteApiModelAsset: (ApiCredentialsId) -> Unit,
    onDeleteApiModelConfig: (ApiModelConfigurationId) -> Unit,
    onShowTtsProvidersSheet: (Boolean) -> Unit,
    onNavigateToTtsConfigure: () -> Unit,
    onStartCreateTtsProviderAsset: () -> Unit,
    onSelectTtsProviderAsset: (TtsProviderAssetUi?) -> Unit,
    onDeleteTtsProviderAsset: (TtsProviderId) -> Unit,
    onShowMediaProvidersSheet: (Boolean) -> Unit,
    onNavigateToMediaConfigure: () -> Unit,
    onStartCreateMediaProviderAsset: () -> Unit,
    onSelectMediaProviderAsset: (MediaProviderAssetUi?) -> Unit,
    onDeleteMediaProviderAsset: (MediaProviderId) -> Unit,
    onNavigateToLocalModelConfigure: () -> Unit,
    onSelectLocalModelAsset: (LocalModelAssetUi?) -> Unit,
    onSelectLocalModelConfig: (LocalModelConfigUi?) -> Unit,
    onDeleteLocalModelAsset: (LocalModelId) -> Unit,
    onDeleteLocalModelConfig: (LocalModelConfigurationId) -> Unit,
    onConfirmDeletionWithReassignment: (LocalModelConfigurationId?, ApiModelConfigurationId?) -> Unit,
    onDismissDeletionSafety: () -> Unit,
    onSubmitVaultReauthPassword: (String) -> Unit = {},
    onConfirmAdoptCloudFromSettings: () -> Unit = {},
    onCancelVaultReauth: () -> Unit = {},
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
                AccountSection(
                    authState = uiState.home.auth,
                    isReindexing = uiState.home.isReindexing,
                    reindexProgress = uiState.home.reindexProgress,
                    onLoginClick = onLoginClick,
                    onLogoutClick = onLogoutClick
                )
            }

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
                Spacer(modifier = Modifier.height(8.dp))
                SettingsToggle(
                    title = "Background Inference",
                    subtitle = "Allow chat generation to continue when the app is in the background",
                    icon = Icons.Default.Cloud,
                    checked = uiState.home.backgroundInferenceEnabled,
                    onCheckedChange = onBackgroundInferenceChange
                )
            }

            item {
                SectionHeader(text = "Models")
                SettingsNavigationItem(
                    title = "Model Role Assignments",
                    subtitle = "Set models for chat, media, and tts",
                    icon = Icons.AutoMirrored.Filled.Rule,
                    onClick = { onNavigateToModelConfigure(ModelType.MAIN) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsNavigationItem(
                    title = "Local Models",
                    subtitle = "Manage on-device models",
                    icon = Icons.Default.Memory,
                    onClick = { onShowLocalModelsSheet(true) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsNavigationItem(
                    title = "API Models",
                    subtitle = "Manage API keys and presets",
                    icon = Icons.Default.Cloud,
                    onClick = { onShowByokSheet(true) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsNavigationItem(
                    title = "Text to Speech Models",
                    subtitle = "Manage TTS voices and API keys",
                    icon = Icons.Default.Vibration, // Using Vibration for audio/haptic feel
                    onClick = { onShowTtsProvidersSheet(true) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsNavigationItem(
                    title = "Media Generation Models",
                    subtitle = "Manage Image and Video generation APIs",
                    icon = Icons.Default.Palette,
                    onClick = { onShowMediaProvidersSheet(true) }
                )
            }

            item {
                SectionHeader(text = "Tools")
                SettingsNavigationItem(
                    title = "Search Skills",
                    subtitle = buildString {
                        val webEnabled = uiState.searchSkillEditor.enabled
                        val xEnabled = uiState.xSearchEditor.enabled
                        when {
                            webEnabled && xEnabled -> append("Web & X Enabled")
                            webEnabled -> append("Web Enabled")
                            xEnabled -> append("X Enabled")
                            else -> append("Disabled")
                        }
                    },
                    icon = Icons.Default.Public,
                    onClick = {
                        onShowSearchSkillSheet(true)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsNavigationItem(
                    title = "Vision",
                    subtitle = ModelType.VISION.description,
                    icon = Icons.Default.Visibility,
                    onClick = { onShowVisionSettingsSheet(true) }
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
                    onClick = { onShowTosSheet(true) }
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

        if (uiState.home.isTtsProvidersSheetOpen) {
            TtsProvidersBottomSheet(
                uiState = uiState,
                onDismiss = { onShowTtsProvidersSheet(false) },
                onNavigateToTtsConfigure = onNavigateToTtsConfigure,
                onStartCreateTtsProviderAsset = onStartCreateTtsProviderAsset,
                onSelectTtsProviderAsset = onSelectTtsProviderAsset,
                onDeleteTtsProviderAsset = onDeleteTtsProviderAsset,
            )
        }

        if (uiState.home.isMemoriesSheetOpen) {
            MemoriesBottomSheet(
                uiState = uiState,
                onDismiss = { onShowMemoriesSheet(false) },
                onAddMemory = onAddMemory,
                onEditMemory = onEditMemory,
                onUpdateMemoryDraft = onUpdateMemoryDraft,
                onSaveMemory = onSaveMemory,
                onCancelMemoryEdit = onCancelMemoryEdit,
                onDeleteMemory = onDeleteMemory
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

        if (uiState.home.isMediaProvidersSheetOpen) {
            MediaProvidersBottomSheet(
                uiState = uiState,
                onDismiss = { onShowMediaProvidersSheet(false) },
                onNavigateToMediaConfigure = onNavigateToMediaConfigure,
                onStartCreateMediaProviderAsset = onStartCreateMediaProviderAsset,
                onSelectMediaProviderAsset = onSelectMediaProviderAsset,
                onDeleteMediaProviderAsset = onDeleteMediaProviderAsset
            )
        }

        if (uiState.home.isSearchSkillSheetOpen) {
            SearchSkillBottomSheet(
                uiState = uiState,
                onDismiss = { onShowSearchSkillSheet(false) },
                onTabChange = onSearchSkillTabChange,
                onSearchEnabledChange = onSearchEnabledChange,
                onSearchProviderChange = onSearchProviderChange,
                onTavilyApiKeyChange = onTavilyApiKeyChange,
                onClearTavilyApiKey = onClearTavilyApiKey,
                onExaApiKeyChange = onExaApiKeyChange,
                onClearExaApiKey = onClearExaApiKey,
                onUpdateShowSearchProviderSwitchAlert = onUpdateShowSearchProviderSwitchAlert,
                onSaveWebSearch = {
                    onSaveSearchSkillSettings {
                        // Success handled by onDismiss or similar
                    }
                },
                onXSearchEnabledChange = onXSearchEnabledChange,
                onXApiKeyChange = onXApiKeyChange,
                onClearXApiKey = onClearXApiKey,
                onSaveXSearch = onSaveXSearchSettings
            )
        }

        if (uiState.home.isTosSheetOpen) {
            TosBottomSheet(onDismiss = { onShowTosSheet(false) })
        }

        when (val sheet = uiState.vaultReauthSheet) {
            VaultReauthSheetState.Hidden -> Unit
            is VaultReauthSheetState.EnterPassword -> {
                VaultReauthBottomSheet(
                    authState = uiState.home.auth.toVaultAuthUiState(),
                    errorMessage = sheet.errorMessage,
                    isUnlocking = sheet.isUnlocking,
                    showConfirmDialog = false,
                    isReconciling = false,
                    onSubmitVaultPassword = onSubmitVaultReauthPassword,
                    onConfirmAdoptCloud = onConfirmAdoptCloudFromSettings,
                    onCancelReconciliation = onCancelVaultReauth,
                    onDismiss = onCancelVaultReauth,
                )
            }
            is VaultReauthSheetState.ConfirmClobber -> {
                VaultReauthBottomSheet(
                    authState = uiState.home.auth.toVaultAuthUiState(),
                    errorMessage = null,
                    isUnlocking = false,
                    showConfirmDialog = true,
                    isReconciling = sheet.isReconciling,
                    onSubmitVaultPassword = onSubmitVaultReauthPassword,
                    onConfirmAdoptCloud = onConfirmAdoptCloudFromSettings,
                    onCancelReconciliation = onCancelVaultReauth,
                    onDismiss = onCancelVaultReauth,
                )
            }
        }
    }
}

private fun AuthStateUi.toVaultAuthUiState(): VaultAuthUiState = when (this) {
    is AuthStateUi.Authenticated -> VaultAuthUiState.Authenticated(
        VaultUserUiState(
            uid = user.uid,
            displayName = user.displayName,
            email = user.email,
            photoUrl = user.photoUrl,
        ),
    )
    AuthStateUi.Unauthenticated -> VaultAuthUiState.Unauthenticated
    AuthStateUi.Loading -> VaultAuthUiState.Loading
    is AuthStateUi.Error -> VaultAuthUiState.Error(message)
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
fun AccountSection(
    authState: AuthStateUi,
    isReindexing: Boolean,
    reindexProgress: Float,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    SectionHeader(text = "Account")
    when (authState) {
        is AuthStateUi.Authenticated -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                ListItem(
                    leadingContent = {
                        if (authState.user.photoUrl != null) {
                            AsyncImage(
                                model = authState.user.photoUrl,
                                contentDescription = "Profile picture",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(20.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    headlineContent = {
                        Text(
                            text = authState.user.displayName ?: "User",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    supportingContent = authState.user.email?.let { email ->
                        {
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = onLogoutClick) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Logout",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = androidx.compose.material3.ListItemDefaults.colors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )

                if (isReindexing) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = "Syncing and indexing models...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { reindexProgress },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
        AuthStateUi.Unauthenticated -> {
            ListItem(
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                headlineContent = { Text("Login with Google") },
                supportingContent = { Text("Sync your data across devices") },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .clickable(onClick = onLoginClick)
            )
        }
        AuthStateUi.Loading -> {
            ListItem(
                headlineContent = { Text("Loading account...") },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            )
        }
        is AuthStateUi.Error -> {
            ListItem(
                headlineContent = { Text("Error: ${authState.message}") },
                trailingContent = {
                    IconButton(onClick = onLoginClick) {
                        Icon(imageVector = Icons.Default.AccountCircle, contentDescription = "Retry Login")
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
            )
        }
    }
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
            onLoginClick = {},
            onLogoutClick = {},
            onThemeChange = {},
            onHapticPressChange = {},
            onHapticResponseChange = {},
            onBackgroundInferenceChange = {},
            onShowDataControlsSheet = {},
            onShowMemoriesSheet = {},
            onAddMemory = {},
            onEditMemory = { _ -> },
            onUpdateMemoryDraft = { _, _ -> },
            onSaveMemory = {},
            onCancelMemoryEdit = {},
            onDeleteMemory = {},
            onShowTosSheet = {},
            onShowFeedbackSheet = {},
            onShowVisionSettingsSheet = {},
            onNavigateToModelConfigure = {},
            onSetDefaultModel = { _, _, _, _, _ -> },
            onShowLocalModelsSheet = {},
            onShowByokSheet = {},
            onNavigateToByokConfigure = {},
            onStartCreateApiModelAsset = {},
            onShowSearchSkillSheet = {},
            onSearchSkillTabChange = {},
            onSearchEnabledChange = {},
            onSearchProviderChange = {},
            onTavilyApiKeyChange = {},
            onClearTavilyApiKey = {},
            onExaApiKeyChange = {},
            onClearExaApiKey = {},
            onUpdateShowSearchProviderSwitchAlert = {},
            onSaveSearchSkillSettings = {},
            onXSearchEnabledChange = {},
            onXApiKeyChange = {},
            onClearXApiKey = {},
            onSaveXSearchSettings = {},
            onSelectApiModelAsset = {},
            onSelectApiModelConfig = {},
            onDeleteApiModelAsset = {},
            onDeleteApiModelConfig = {},
            onShowTtsProvidersSheet = {},
            onNavigateToTtsConfigure = {},
            onStartCreateTtsProviderAsset = {},
            onSelectTtsProviderAsset = {},
            onDeleteTtsProviderAsset = {},
            onShowMediaProvidersSheet = {},
            onNavigateToMediaConfigure = {},
            onStartCreateMediaProviderAsset = {},
            onSelectMediaProviderAsset = {},
            onDeleteMediaProviderAsset = {},
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

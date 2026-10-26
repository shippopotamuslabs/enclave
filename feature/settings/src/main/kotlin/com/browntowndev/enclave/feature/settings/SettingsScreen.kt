package com.browntowndev.enclave.feature.settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.browntowndev.enclave.core.ui.component.enclave.GoogleSignInButton
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveAppBarChrome
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveIconButton
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveNavRow
import com.browntowndev.enclave.core.ui.component.enclave.EnclavePanel
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSectionHeader
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSettingsSection
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveTextButton
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveThemeChip
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveToggleRow
import com.browntowndev.enclave.core.ui.component.sheet.TosBottomSheet
import com.browntowndev.enclave.core.ui.component.vault.VaultAuthUiState
import com.browntowndev.enclave.core.ui.component.vault.VaultReauthBottomSheet
import com.browntowndev.enclave.core.ui.component.vault.VaultUserUiState
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.feature.settings.usage.UsageSpendBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onCloseClick: () -> Unit,
    onNavigateToProfileBuilder: () -> Unit = {},
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onOpenSubscription: () -> Unit = {},
    onThemeChange: (AppThemeUi) -> Unit,
    onThemeFamilyChange: (ThemeFamilyUi) -> Unit = {},
    onHapticResponseChange: (Boolean) -> Unit,
    onBackgroundInferenceChange: (Boolean) -> Unit,
    onShowDataControlsSheet: (Boolean) -> Unit,
    onAutomaticErrorTrackingChange: (Boolean) -> Unit,
    onRequestDeleteAllChats: () -> Unit,
    onConfirmDeleteAllChats: () -> Unit,
    onRequestDeleteAllMedia: () -> Unit,
    onConfirmDeleteAllMedia: () -> Unit,
    onRequestDeleteAllMemories: () -> Unit,
    onConfirmDeleteAllMemories: () -> Unit,
    onRequestDeleteAllArtifacts: () -> Unit,
    onConfirmDeleteAllArtifacts: () -> Unit,
    onRequestDeleteAccount: () -> Unit,
    onConfirmDeleteAccount: () -> Unit,
    onDismissDataControlsConfirmation: () -> Unit,
    onShowMemoriesSheet: (Boolean) -> Unit,
    onAddMemory: () -> Unit,
    onEditMemory: (StoredMemory) -> Unit,
    onUpdateMemoryDraft: (String, MemoryCategory) -> Unit,
    onSaveMemory: () -> Unit,
    onCancelMemoryEdit: () -> Unit,
    onDeleteMemory: (String) -> Unit,
    onShowTosSheet: (Boolean) -> Unit,
    onShowFeedbackSheet: (Boolean) -> Unit,
    onShowUsageSpendSheet: (Boolean) -> Unit,
    onUsageSpendPeriodChange: (UsagePeriodUi) -> Unit,
    onShowOssLicensesSheet: (Boolean) -> Unit,
    onShowVisionSettingsSheet: (Boolean) -> Unit,
    onNavigateToModelConfigure: (ModelType) -> Unit,
    onSetDefaultModel: (
        ModelType,
        LocalModelConfigurationId?,
        ApiModelConfigurationId?,
        TtsProviderId?,
        MediaProviderId?,
        com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId?,
    ) -> Unit,
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
    onDeleteApiModelAsset: (ApiCredentialsId, () -> Unit) -> Unit,
    onDeleteApiModelConfig: (ApiModelConfigurationId, () -> Unit) -> Unit,
    onShowTtsProvidersSheet: (Boolean) -> Unit,
    onNavigateToTtsConfigure: () -> Unit,
    onStartCreateTtsProviderAsset: () -> Unit,
    onSelectTtsProviderAsset: (TtsProviderAssetUi?) -> Unit,
    onDeleteTtsProviderAsset: (TtsProviderId) -> Unit,
    onShowLiveVoiceProvidersSheet: (Boolean) -> Unit = {},
    onNavigateToLiveVoiceConfigure: () -> Unit = {},
    onStartCreateLiveVoiceProviderAsset: () -> Unit = {},
    onSelectLiveVoiceProviderAsset: (LiveVoiceProviderAssetUi?) -> Unit = {},
    onDeleteLiveVoiceProviderAsset: (com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId) -> Unit = {},
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
    onConfirmDeletionWithReassignment: (LocalModelConfigurationId?, ApiModelConfigurationId?, MediaProviderId?) -> Unit,
    onDismissDeletionSafety: () -> Unit,
    onDeleteModelPack: (String) -> Unit = {},
    onSubmitVaultReauthPassword: (String) -> Unit = {},
    onConfirmAdoptCloudFromSettings: () -> Unit = {},
    onCancelVaultReauth: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            EnclaveAppBarChrome {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                EnclaveIconButton(
                    onClick = onCloseClick,
                    icon = Icons.Default.Close,
                    contentDescription = "Close settings",
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = EnclaveNeoTokens.screenHorizontalPadding),
            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.sectionSpacing),
        ) {
            item {
                AccountSection(
                    authState = uiState.home.auth,
                    subscription = uiState.home.subscription,
                    isReindexing = uiState.home.isReindexing,
                    reindexProgress = uiState.home.reindexProgress,
                    onLoginClick = onLoginClick,
                    onLogoutClick = onLogoutClick,
                    onOpenSubscription = onOpenSubscription,
                )
            }

            item {
                EnclaveSectionHeader(text = "Appearance")
                ThemeFamilySelection(
                    selectedFamily = uiState.home.themeFamily,
                    onThemeFamilyChange = onThemeFamilyChange,
                )
                ThemeSelection(
                    selectedTheme = uiState.home.theme,
                    onThemeChange = onThemeChange,
                )
            }

            item {
                EnclaveSectionHeader(text = "Experience")
                EnclaveSettingsSection {
                    EnclaveToggleRow(
                        title = "Haptic Response",
                        icon = Icons.Default.Vibration,
                        checked = uiState.home.hapticResponse,
                        onCheckedChange = onHapticResponseChange,
                    )
                    EnclaveToggleRow(
                        title = "Background Inference",
                        subtitle = "Allow chat generation to continue when the app is in the background",
                        icon = Icons.Default.Cloud,
                        checked = uiState.home.backgroundInferenceEnabled,
                        onCheckedChange = onBackgroundInferenceChange,
                    )
                }
            }

            item {
                EnclaveSectionHeader(text = "Models")
                EnclaveSettingsSection {
                    EnclaveNavRow(
                        title = "Model Role Assignments",
                        subtitle = "Set models for chat, media, and tts",
                        icon = Icons.AutoMirrored.Filled.Rule,
                        onClick = { onNavigateToModelConfigure(ModelType.MAIN) },
                    )
                    EnclaveNavRow(
                        title = "Local Models",
                        subtitle = "Manage on-device models",
                        icon = Icons.Default.Memory,
                        onClick = { onShowLocalModelsSheet(true) },
                    )
                    EnclaveNavRow(
                        title = "API Models",
                        subtitle = "Manage API keys and presets",
                        icon = Icons.Default.Cloud,
                        onClick = { onShowByokSheet(true) },
                    )
                    EnclaveNavRow(
                        title = "Text to Speech Models",
                        subtitle = "Manage TTS voices and API keys",
                        icon = Icons.Default.Vibration,
                        onClick = { onShowTtsProvidersSheet(true) },
                    )
                    EnclaveNavRow(
                        title = "Live Voice",
                        subtitle = "Manage realtime speech-to-speech providers",
                        icon = Icons.Default.GraphicEq,
                        onClick = { onShowLiveVoiceProvidersSheet(true) },
                    )
                    EnclaveNavRow(
                        title = "Media Generation Models",
                        subtitle = "Manage Image and Video generation APIs",
                        icon = Icons.Default.Palette,
                        onClick = { onShowMediaProvidersSheet(true) },
                    )
                }
            }

            item {
                EnclaveSectionHeader(text = "Tools")
                EnclaveSettingsSection {
                    EnclaveNavRow(
                        title = "Search Skills",
                        subtitle =
                            buildString {
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
                        onClick = { onShowSearchSkillSheet(true) },
                    )
                    EnclaveNavRow(
                        title = "Vision",
                        subtitle = ModelType.VISION.description,
                        icon = Icons.Default.Visibility,
                        onClick = { onShowVisionSettingsSheet(true) },
                    )
                    EnclaveNavRow(
                        title = "Psychological Profile",
                        subtitle = "Build a profile from your AI conversation exports",
                        icon = Icons.Default.Psychology,
                        onClick = onNavigateToProfileBuilder,
                    )
                }
            }

            item {
                EnclaveSectionHeader(text = "Data & Privacy")
                EnclaveSettingsSection {
                    EnclaveNavRow(
                        title = "Usage & Spend",
                        icon = Icons.Default.BarChart,
                        onClick = { onShowUsageSpendSheet(true) },
                        modifier = Modifier.testTag("usage_spend_row"),
                    )
                    EnclaveNavRow(
                        title = "Memories",
                        icon = Icons.Default.SmartToy,
                        onClick = { onShowMemoriesSheet(true) },
                    )
                    EnclaveNavRow(
                        title = "Data Controls",
                        icon = Icons.Default.Shield,
                        onClick = { onShowDataControlsSheet(true) },
                    )
                }
            }

            item {
                EnclaveSectionHeader(text = "About")
                EnclaveSettingsSection {
                    EnclaveNavRow(
                        title = "Terms of Service",
                        icon = Icons.Default.Info,
                        onClick = { onShowTosSheet(true) },
                    )
                    EnclaveNavRow(
                        title = "Feedback",
                        icon = Icons.Default.Feedback,
                        onClick = { onShowFeedbackSheet(true) },
                    )
                    EnclaveNavRow(
                        title = "Licenses & Attributions",
                        icon = Icons.Default.Info,
                        onClick = { onShowOssLicensesSheet(true) },
                    )
                }
            }
        }

        if (uiState.home.isVisionSettingsSheetOpen) {
            VisionSettingsBottomSheet(
                uiState = uiState,
                onDismiss = { onShowVisionSettingsSheet(false) },
                onSetDefaultModel = onSetDefaultModel,
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
                onDismissDeletionSafety = onDismissDeletionSafety,
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

        if (uiState.home.isLiveVoiceProvidersSheetOpen) {
            LiveVoiceProvidersBottomSheet(
                uiState = uiState,
                onDismiss = { onShowLiveVoiceProvidersSheet(false) },
                onNavigateToLiveVoiceConfigure = onNavigateToLiveVoiceConfigure,
                onStartCreateLiveVoiceProviderAsset = onStartCreateLiveVoiceProviderAsset,
                onSelectLiveVoiceProviderAsset = onSelectLiveVoiceProviderAsset,
                onDeleteLiveVoiceProviderAsset = onDeleteLiveVoiceProviderAsset,
            )
        }

        if (uiState.home.isDataControlsSheetOpen) {
            DataControlsBottomSheet(
                uiState = uiState,
                onDismiss = { onShowDataControlsSheet(false) },
                onAutomaticErrorTrackingChange = onAutomaticErrorTrackingChange,
                onRequestDeleteAllChats = onRequestDeleteAllChats,
                onConfirmDeleteAllChats = onConfirmDeleteAllChats,
                onRequestDeleteAllMedia = onRequestDeleteAllMedia,
                onConfirmDeleteAllMedia = onConfirmDeleteAllMedia,
                onRequestDeleteAllMemories = onRequestDeleteAllMemories,
                onConfirmDeleteAllMemories = onConfirmDeleteAllMemories,
                onRequestDeleteAllArtifacts = onRequestDeleteAllArtifacts,
                onConfirmDeleteAllArtifacts = onConfirmDeleteAllArtifacts,
                onRequestDeleteAccount = onRequestDeleteAccount,
                onConfirmDeleteAccount = onConfirmDeleteAccount,
                onDismissConfirmation = onDismissDataControlsConfirmation,
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
                onDeleteMemory = onDeleteMemory,
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
                onDismissDeletionSafety = onDismissDeletionSafety,
                onDeleteModelPack = onDeleteModelPack,
            )
        }

        if (uiState.home.isMediaProvidersSheetOpen) {
            MediaProvidersBottomSheet(
                uiState = uiState,
                onDismiss = { onShowMediaProvidersSheet(false) },
                onNavigateToMediaConfigure = onNavigateToMediaConfigure,
                onStartCreateMediaProviderAsset = onStartCreateMediaProviderAsset,
                onSelectMediaProviderAsset = onSelectMediaProviderAsset,
                onDeleteMediaProviderAsset = onDeleteMediaProviderAsset,
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
                onSaveXSearch = onSaveXSearchSettings,
            )
        }

        if (uiState.home.isOssLicensesSheetOpen) {
            OssLicensesBottomSheet(
                onDismiss = { onShowOssLicensesSheet(false) },
            )
        }

        if (uiState.home.isTosSheetOpen) {
            TosBottomSheet(onDismiss = { onShowTosSheet(false) })
        }

        UsageSpendBottomSheet(
            state = uiState.usageSpend,
            onDismiss = { onShowUsageSpendSheet(false) },
            onPeriodChange = onUsageSpendPeriodChange,
        )

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

private fun AuthStateUi.toVaultAuthUiState(): VaultAuthUiState =
    when (this) {
        is AuthStateUi.Authenticated ->
            VaultAuthUiState.Authenticated(
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

/** @deprecated Prefer [EnclaveSectionHeader]; kept for other settings flows still on legacy section headers. */
@Composable
fun SectionHeader(text: String) {
    EnclaveSectionHeader(text = text)
}

@Composable
fun AccountSection(
    authState: AuthStateUi,
    subscription: SubscriptionUiState = SubscriptionUiState.Hidden,
    isReindexing: Boolean,
    reindexProgress: Float,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onOpenSubscription: () -> Unit = {},
) {
    EnclaveSectionHeader(text = "Account")
    SubscriptionRow(
        subscription = subscription,
        onOpenSubscription = onOpenSubscription,
    )
    when (authState) {
        is AuthStateUi.Authenticated -> {
            EnclavePanel {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (authState.user.photoUrl != null) {
                        AsyncImage(
                            model = authState.user.photoUrl,
                            contentDescription = "Profile picture",
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(20.dp)),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = authState.user.displayName ?: "User",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        authState.user.email?.let { email ->
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    EnclaveIconButton(
                        onClick = onLogoutClick,
                        icon = Icons.Default.Logout,
                        contentDescription = "Logout",
                    )
                }
                if (isReindexing) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = "Syncing and indexing models...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        LinearProgressIndicator(
                            progress = { reindexProgress },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
        AuthStateUi.Unauthenticated -> {
            GoogleSignInButton(onClick = onLoginClick)
        }
        AuthStateUi.Loading -> {
            EnclavePanel {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(text = "Loading account...")
                }
            }
        }
        is AuthStateUi.Error -> {
            EnclavePanel {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Error: ${authState.message}",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    EnclaveIconButton(
                        onClick = onLoginClick,
                        icon = Icons.Default.AccountCircle,
                        contentDescription = "Retry Login",
                    )
                }
            }
        }
    }
}

/**
 * Subscription row in the account area. Only [SubscriptionUiState.Subscribed] and
 * [SubscriptionUiState.NotSubscribed] render an action; [SubscriptionUiState.Error] shows the
 * status alone and [SubscriptionUiState.Hidden] renders nothing.
 */
@Composable
fun SubscriptionRow(
    subscription: SubscriptionUiState,
    onOpenSubscription: () -> Unit,
) {
    when (subscription) {
        SubscriptionUiState.Hidden -> Unit
        is SubscriptionUiState.Subscribed -> {
            SubscriptionPanel(
                statusText = subscription.expiryLabel ?: "Subscribed",
                actionText = "Manage subscription",
                onOpenSubscription = onOpenSubscription,
            )
        }
        SubscriptionUiState.NotSubscribed -> {
            SubscriptionPanel(
                statusText = "Not subscribed",
                actionText = "Subscribe",
                onOpenSubscription = onOpenSubscription,
            )
        }
        SubscriptionUiState.Error -> {
            SubscriptionPanel(
                statusText = "Could not load subscription status.",
                actionText = null,
                onOpenSubscription = onOpenSubscription,
            )
        }
    }
}

@Composable
private fun SubscriptionPanel(
    statusText: String,
    actionText: String?,
    onOpenSubscription: () -> Unit,
) {
    EnclavePanel {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enclave subscription",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (actionText != null) {
                EnclaveTextButton(
                    text = actionText,
                    onClick = onOpenSubscription,
                )
            }
        }
    }
}

@Composable
fun ThemeFamilySelection(
    selectedFamily: ThemeFamilyUi,
    onThemeFamilyChange: (ThemeFamilyUi) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeOption(
            text = "Material",
            selected = selectedFamily == ThemeFamilyUi.Tonal,
            onClick = { onThemeFamilyChange(ThemeFamilyUi.Tonal) },
            modifier = Modifier.weight(1f),
        )
        ThemeOption(
            text = "Neo Brutalist",
            selected = selectedFamily == ThemeFamilyUi.NeoBrutalist,
            onClick = { onThemeFamilyChange(ThemeFamilyUi.NeoBrutalist) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun ThemeSelection(
    selectedTheme: AppThemeUi,
    onThemeChange: (AppThemeUi) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeOption(
                text = "System",
                selected = selectedTheme == AppThemeUi.SYSTEM,
                onClick = { onThemeChange(AppThemeUi.SYSTEM) },
                modifier = Modifier.weight(1f),
            )
            ThemeOption(
                text = "Dynamic",
                selected = selectedTheme == AppThemeUi.DYNAMIC,
                onClick = { onThemeChange(AppThemeUi.DYNAMIC) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeOption(
                text = "Light",
                selected = selectedTheme == AppThemeUi.LIGHT,
                onClick = { onThemeChange(AppThemeUi.LIGHT) },
                modifier = Modifier.weight(1f),
            )
            ThemeOption(
                text = "Dark",
                selected = selectedTheme == AppThemeUi.DARK,
                onClick = { onThemeChange(AppThemeUi.DARK) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun ThemeOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EnclaveThemeChip(
        text = text,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
    )
}

// ==================== PREVIEWS ====================

@Preview(showBackground = true, name = "Theme Picker - Tonal Light")
@Composable
fun PreviewThemePickerTonalLight() {
    EnclaveTheme(darkTheme = false, themeFamily = com.browntowndev.enclave.core.ui.theme.EnclaveThemeId.Tonal) {
        ThemeFamilySelection(selectedFamily = ThemeFamilyUi.Tonal, onThemeFamilyChange = {})
    }
}

@Preview(showBackground = true, name = "Theme Picker - Neo Dark")
@Composable
fun PreviewThemePickerNeoDark() {
    EnclaveTheme(darkTheme = true, themeFamily = com.browntowndev.enclave.core.ui.theme.EnclaveThemeId.NeoBrutalist) {
        ThemeFamilySelection(selectedFamily = ThemeFamilyUi.NeoBrutalist, onThemeFamilyChange = {})
    }
}


@Preview(showBackground = true, name = "Main Settings Screen")
@Composable
fun PreviewSettingsScreen() {
    EnclaveTheme(darkTheme = true) {
        SettingsScreen(
            uiState = MockSettingsData.baseUiState,
            onCloseClick = {},
            onLoginClick = {},
            onLogoutClick = {},
            onThemeChange = {},
            onHapticResponseChange = {},
            onBackgroundInferenceChange = {},
            onShowDataControlsSheet = {},
            onAutomaticErrorTrackingChange = {},
            onRequestDeleteAllChats = {},
            onConfirmDeleteAllChats = {},
            onRequestDeleteAllMedia = {},
            onConfirmDeleteAllMedia = {},
            onRequestDeleteAllMemories = {},
            onConfirmDeleteAllMemories = {},
            onRequestDeleteAllArtifacts = {},
            onConfirmDeleteAllArtifacts = {},
            onRequestDeleteAccount = {},
            onConfirmDeleteAccount = {},
            onDismissDataControlsConfirmation = {},
            onShowMemoriesSheet = {},
            onAddMemory = {},
            onEditMemory = { _ -> },
            onUpdateMemoryDraft = { _, _ -> },
            onSaveMemory = {},
            onCancelMemoryEdit = {},
            onDeleteMemory = {},
            onShowTosSheet = {},
            onShowFeedbackSheet = {},
            onShowUsageSpendSheet = {},
            onUsageSpendPeriodChange = {},
            onShowOssLicensesSheet = {},
            onShowVisionSettingsSheet = {},
            onNavigateToModelConfigure = {},
            onSetDefaultModel = { _, _, _, _, _, _ -> },
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
            onDeleteApiModelAsset = { _, _ -> },
            onDeleteApiModelConfig = { _, _ -> },
            onShowTtsProvidersSheet = {},
            onNavigateToTtsConfigure = {},
            onStartCreateTtsProviderAsset = {},
            onSelectTtsProviderAsset = {},
            onDeleteTtsProviderAsset = {},
            onShowLiveVoiceProvidersSheet = {},
            onNavigateToLiveVoiceConfigure = {},
            onStartCreateLiveVoiceProviderAsset = {},
            onSelectLiveVoiceProviderAsset = {},
            onDeleteLiveVoiceProviderAsset = {},
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
            onConfirmDeletionWithReassignment = { _, _, _ -> },
            onDismissDeletionSafety = {},
        )
    }
}

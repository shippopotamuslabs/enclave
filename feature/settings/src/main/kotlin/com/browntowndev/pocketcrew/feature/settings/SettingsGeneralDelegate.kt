package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption
import com.browntowndev.pocketcrew.domain.model.security.VaultMetadata
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.browntowndev.pocketcrew.domain.port.security.VaultMetadataPort
import com.browntowndev.pocketcrew.domain.usecase.auth.LoginWithGoogleUseCase.Companion.VAULT_VERIFICATION_MAGIC_STRING
import com.browntowndev.pocketcrew.domain.usecase.security.AdoptCloudVaultUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.BootstrapCloudVaultFromLocalUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.CreateVaultUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.DetectVaultReconciliationStateUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.VaultReconciliationOutcome
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAuthUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsPreferencesUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings-screen counterpart to `MainViewModel`'s vault flow. Owns the two-phase login:
 *  1. `onLoginWithGoogle` performs sign-in then opens the [VaultReauthSheetState] bottom
 *     sheet. Cloud sync is NOT started yet — the listener would race the reconciliation.
 *  2. `onSubmitVaultReauthPassword` runs [DetectVaultReconciliationStateUseCase] and
 *     branches to bootstrap, start cloud sync, surface a confirmation, or close out.
 *
 * This keeps the destructive [AdoptCloudVaultUseCase] gated behind explicit user consent in
 * [onConfirmAdoptCloud].
 */
internal class SettingsGeneralDelegate @Inject constructor(
    private val preferencesUseCases: SettingsPreferencesUseCases,
    private val authUseCases: SettingsAuthUseCases,
    private val errorHandler: ViewModelErrorHandler,
    private val detectVaultReconciliationStateUseCase: DetectVaultReconciliationStateUseCase,
    private val adoptCloudVaultUseCase: AdoptCloudVaultUseCase,
    private val bootstrapCloudVaultFromLocalUseCase: BootstrapCloudVaultFromLocalUseCase,
    private val createVaultUseCase: CreateVaultUseCase,
    private val cryptoManager: CryptoManagerPort,
    private val vaultMetadataPort: VaultMetadataPort,
    private val cloudSyncPort: CloudSyncPort,
) {
    private var pendingCloudClobberPayload: VaultReconciliationOutcome.MismatchRequiresClobber? = null

    fun onThemeChange(scope: CoroutineScope, tag: String, theme: AppTheme) {
        scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to update theme", "Failed to update theme")) {
            preferencesUseCases.updateTheme(theme)
        }
    }

    fun onHapticPressChange(scope: CoroutineScope, tag: String, enabled: Boolean) {
        scope.launch(
            errorHandler.coroutineExceptionHandler(
                tag,
                "Failed to update haptic press setting",
                "Failed to update setting"
            )
        ) {
            preferencesUseCases.updateHapticPress(enabled)
        }
    }

    fun onHapticResponseChange(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesUseCases.updateHapticResponse(enabled)
        }
    }

    fun onBackgroundInferenceChange(scope: CoroutineScope, tag: String, enabled: Boolean) {
        scope.launch(
            errorHandler.coroutineExceptionHandler(
                tag,
                "Failed to update background inference setting",
                "Failed to update setting"
            )
        ) {
            preferencesUseCases.updateBackgroundInferenceEnabled(enabled)
        }
    }

    /**
     * Phase 1 — sign in with Google then open the re-auth bottom sheet. Sync is intentionally
     * NOT started here; the listener has to wait until the user re-supplies their vault
     * password and the resulting reconciliation completes (or is cancelled).
     */
    fun onLoginWithGoogle(
        scope: CoroutineScope,
        tag: String,
        vaultReauthSheet: MutableStateFlow<VaultReauthSheetState>,
    ) {
        scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to sign in with Google", "Failed to sign in")) {
            authUseCases.loginWithGoogle().getOrThrow()
            pendingCloudClobberPayload = null
            vaultReauthSheet.value = VaultReauthSheetState.EnterPassword()
        }
    }

    fun onLogout(scope: CoroutineScope, tag: String) {
        scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to sign out", "Failed to sign out")) {
            authUseCases.logout().getOrThrow()
        }
    }

    /**
     * Phase 2 — user supplied their vault password in the re-auth sheet. Run the detector
     * and branch:
     *   - `AlreadyMatching` / `NoLocalVault` — start sync + listener and dismiss sheet.
     *   - `NoCloudVault` — bootstrap if local vault material exists, or create the first
     *     vault on a fresh install, then start sync + listener and dismiss sheet.
     *   - `MismatchRequiresClobber` — flip the sheet to [VaultReauthSheetState.ConfirmClobber]
     *     so the dialog overlay can request user confirmation. Adoption only happens after
     *     [onConfirmAdoptCloud].
     *   - `PasswordMismatch` — show inline "Incorrect Vault Password" error, sheet stays open.
     */
    fun onSubmitVaultReauthPassword(
        scope: CoroutineScope,
        tag: String,
        vaultReauthSheet: MutableStateFlow<VaultReauthSheetState>,
        password: String,
    ) {
        if (password.isBlank()) return
        vaultReauthSheet.update { current ->
            (current as? VaultReauthSheetState.EnterPassword)?.copy(isUnlocking = true, errorMessage = null)
                ?: VaultReauthSheetState.EnterPassword(isUnlocking = true)
        }
        scope.launch(
            errorHandler.coroutineExceptionHandler(
                tag,
                "Failed to reconcile vault from settings",
                "Failed to reconcile vault",
            ),
        ) {
            val outcome = detectVaultReconciliationStateUseCase(password).getOrElse {
                vaultReauthSheet.value = VaultReauthSheetState.EnterPassword(
                    isUnlocking = false,
                    errorMessage = "Reconciliation failed. Try again.",
                )
                return@launch
            }
            when (outcome) {
                is VaultReconciliationOutcome.AlreadyMatching,
                is VaultReconciliationOutcome.NoLocalVault -> {
                    runCatching { cloudSyncPort.syncAllLocalDataToCloud() }
                    cloudSyncPort.startListening()
                    vaultReauthSheet.value = VaultReauthSheetState.Hidden
                }
                is VaultReconciliationOutcome.NoCloudVault -> {
                    val localSalt = vaultMetadataPort.getVaultSalt()?.takeIf { it.isNotBlank() }
                    val localWrappedDek = vaultMetadataPort.getWrappedDek()?.takeIf { it.isNotBlank() }
                    when {
                        localSalt != null && localWrappedDek != null -> {
                            val bootstrap = bootstrapCloudVaultFromLocalUseCase()
                            if (bootstrap.isFailure) {
                                vaultReauthSheet.value = VaultReauthSheetState.EnterPassword(
                                    isUnlocking = false,
                                    errorMessage = "Vault not initialized yet",
                                )
                                return@launch
                            }
                            runCatching { cloudSyncPort.syncAllLocalDataToCloud() }
                            cloudSyncPort.startListening()
                            vaultReauthSheet.value = VaultReauthSheetState.Hidden
                        }
                        localSalt == null && localWrappedDek == null -> {
                            val createResult = initializeVaultFromScratch(password)
                            if (createResult.isFailure) {
                                vaultReauthSheet.value = VaultReauthSheetState.EnterPassword(
                                    isUnlocking = false,
                                    errorMessage = "Failed to initialize vault",
                                )
                                return@launch
                            }
                            cloudSyncPort.startListening()
                            vaultReauthSheet.value = VaultReauthSheetState.Hidden
                        }
                        else -> {
                            vaultReauthSheet.value = VaultReauthSheetState.EnterPassword(
                                isUnlocking = false,
                                errorMessage = "Vault not initialized yet",
                            )
                        }
                    }
                }
                is VaultReconciliationOutcome.PasswordMismatch -> {
                    vaultReauthSheet.value = VaultReauthSheetState.EnterPassword(
                        isUnlocking = false,
                        errorMessage = "Incorrect Vault Password",
                    )
                }
                is VaultReconciliationOutcome.MismatchRequiresClobber -> {
                    pendingCloudClobberPayload = outcome
                    vaultReauthSheet.value = VaultReauthSheetState.ConfirmClobber()
                }
            }
        }
    }

    /** User accepted the destructive clobber from the sheet's confirmation overlay. */
    fun onConfirmAdoptCloud(
        scope: CoroutineScope,
        tag: String,
        vaultReauthSheet: MutableStateFlow<VaultReauthSheetState>,
    ) {
        val current = vaultReauthSheet.value as? VaultReauthSheetState.ConfirmClobber ?: return
        if (current.isReconciling) return
        vaultReauthSheet.value = current.copy(isReconciling = true)
        val payload = pendingCloudClobberPayload ?: run {
            vaultReauthSheet.value = VaultReauthSheetState.Hidden
            return
        }

        scope.launch(
            errorHandler.coroutineExceptionHandler(tag, "Failed to adopt cloud vault", "Failed to replace local data"),
        ) {
            val result = adoptCloudVaultUseCase(payload)
            if (result.isFailure) {
                vaultReauthSheet.value = VaultReauthSheetState.EnterPassword(
                    isUnlocking = false,
                    errorMessage = "Failed to replace local data with cloud",
                )
                return@launch
            }
            pendingCloudClobberPayload = null
            cloudSyncPort.startListening()
            vaultReauthSheet.value = VaultReauthSheetState.Hidden
        }
    }

    /** User declined the destructive clobber, or dismissed the sheet entirely. */
    fun onCancelVaultReauth(vaultReauthSheet: MutableStateFlow<VaultReauthSheetState>) {
        pendingCloudClobberPayload = null
        vaultReauthSheet.value = VaultReauthSheetState.Hidden
    }

    private suspend fun initializeVaultFromScratch(password: String): Result<Unit> {
        return runCatching {
            val salt = cryptoManager.generateSalt()
            val saltBase64 = cryptoManager.encodeBase64(salt)
            val wrappedDek = createVaultUseCase(password, salt).getOrThrow()
            val wrappedDekBase64 = cryptoManager.encodeBase64(wrappedDek)

            vaultMetadataPort.saveVaultSalt(saltBase64)
            vaultMetadataPort.saveWrappedDek(wrappedDekBase64)

            val verificationBase64 = ensureVerificationStored()
            runCatching {
                cloudSyncPort.uploadVaultMetadata(
                    VaultMetadata(
                        salt = saltBase64,
                        verification = verificationBase64,
                        wrappedDek = wrappedDekBase64,
                    ),
                )
            }
            Unit
        }
    }

    private suspend fun ensureVerificationStored(): String? {
        val existing = vaultMetadataPort.getVaultVerificationData()?.takeIf { it.isNotBlank() }
        if (existing != null) return existing

        val encryptResult = cryptoManager.encrypt(VAULT_VERIFICATION_MAGIC_STRING)
        return encryptResult.getOrNull()?.let { ciphertext ->
            val base64Ciphertext = cryptoManager.encodeBase64(ciphertext)
            vaultMetadataPort.saveVaultVerificationData(base64Ciphertext)
            base64Ciphertext
        }
    }

    fun onShowCustomizationSheet(sheetVisibility: MutableStateFlow<SheetVisibilityState>, show: Boolean) {
        sheetVisibility.update { it.copy(customization = show) }
    }

    fun onShowVisionSettingsSheet(sheetVisibility: MutableStateFlow<SheetVisibilityState>, show: Boolean) {
        sheetVisibility.update { it.copy(visionSettings = show) }
    }

    fun onShowSearchSkillSheet(sheetVisibility: MutableStateFlow<SheetVisibilityState>, show: Boolean) {
        sheetVisibility.update { it.copy(searchSkill = show) }
    }

    fun onSearchSkillTabChange(sheetVisibility: MutableStateFlow<SheetVisibilityState>, index: Int) {
        sheetVisibility.update { it.copy(searchSkillTabIndex = index) }
    }

    fun onCustomizationEnabledChange(scope: CoroutineScope, tag: String, enabled: Boolean) {
        scope.launch(
            errorHandler.coroutineExceptionHandler(
                tag,
                "Failed to update customization setting",
                "Failed to update setting"
            )
        ) {
            preferencesUseCases.updateCustomizationEnabled(enabled)
        }
    }

    fun onPromptOptionChange(scope: CoroutineScope, tag: String, option: SystemPromptOption) {
        scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to update prompt option", "Failed to update setting")) {
            preferencesUseCases.updateSelectedPromptOption(option)
        }
    }

    fun onCustomPromptTextChange(scope: CoroutineScope, tag: String, text: String) {
        scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to update custom prompt", "Failed to update setting")) {
            preferencesUseCases.updateCustomPromptText(text)
        }
    }

    fun onSaveCustomization(sheetVisibility: MutableStateFlow<SheetVisibilityState>) {
        sheetVisibility.update { it.copy(customization = false) }
    }

    fun onShowTosSheet(sheetVisibility: MutableStateFlow<SheetVisibilityState>, show: Boolean) {
        sheetVisibility.update { it.copy(tos = show) }
    }

    fun onShowFeedbackSheet(sheetVisibility: MutableStateFlow<SheetVisibilityState>, show: Boolean) {
        sheetVisibility.update { it.copy(feedback = show) }
    }

    fun onFeedbackTextChange(feedbackText: MutableStateFlow<String>, text: String) {
        feedbackText.value = text
    }

    fun onSubmitFeedback(feedbackText: MutableStateFlow<String>, sheetVisibility: MutableStateFlow<SheetVisibilityState>) {
        feedbackText.value = ""
        sheetVisibility.update { it.copy(feedback = false) }
    }
}

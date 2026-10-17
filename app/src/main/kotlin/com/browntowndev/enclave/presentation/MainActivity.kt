package com.browntowndev.enclave.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.enclave.core.ui.component.vault.VaultMismatchConfirmDialog
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.feature.download.DownloadPlanRefresher
import com.browntowndev.enclave.feature.settings.components.UnlockVaultScreen
import com.browntowndev.enclave.presentation.security.BiometricPromptCoordinator
import com.browntowndev.enclave.presentation.security.BiometricPromptHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val appViewModel: EnclaveAppViewModel by viewModels()

    @Inject
    lateinit var loggingPort: LoggingPort

    private var latestIntent by mutableStateOf<Intent?>(null)
    private lateinit var biometricHelper: BiometricPromptHelper

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        latestIntent = intent
        biometricHelper = BiometricPromptHelper(this)

        // Gatekeeper: Keeps the native splash screen visible while state is Loading
        splashScreen.setKeepOnScreenCondition {
            viewModel.startupState.value is AppStartupState.Loading
        }

        setContent {
            val themeState by appViewModel.themeUiState.collectAsStateWithLifecycle()
            val state by viewModel.startupState.collectAsStateWithLifecycle()

            EnclaveTheme(
                darkTheme = themeState.darkTheme,
                dynamicColor = themeState.dynamicColor,
                themeFamily = themeState.themeFamily,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                ) {
                    when (val currentState = state) {
                        is AppStartupState.Loading -> {
                            // Placeholder while splash screen transition completes
                            Box(modifier = Modifier.Companion.fillMaxSize())
                        }
                        is AppStartupState.Ready -> {
                            if (currentState.isBiometricEnrollmentPending) {
                                LaunchedEffect(currentState.isBiometricEnrollmentPending) {
                                    val cipher = viewModel.getBiometricEncryptCipher()
                                    if (cipher != null) {
                                        val result =
                                            biometricHelper.showPrompt(
                                                title = "Enroll Biometric Unlock",
                                                subtitle = "Scan to secure your vault password",
                                                cipher = cipher,
                                            )
                                        result
                                            .onSuccess { crypto ->
                                                crypto?.cipher?.let { viewModel.finalizeBiometricEnrollment(it) }
                                            }.onFailure {
                                                loggingPort.error(TAG, "Biometric enrollment failed", it)
                                                viewModel.cancelBiometricEnrollment()
                                            }
                                    } else {
                                        viewModel.cancelBiometricEnrollment()
                                    }
                                }
                            }

                            if (currentState.isVaultLocked) {
                                val scope = rememberCoroutineScope()
                                val biometricCoordinator = remember { BiometricPromptCoordinator() }

                                suspend fun promptBiometricUnlock() {
                                    val cipher = viewModel.getBiometricDecryptCipher()
                                    if (cipher == null) {
                                        loggingPort.error(TAG, "Biometric unlock requested but decrypt cipher is unavailable")
                                        return
                                    }
                                    val result =
                                        biometricHelper.showPrompt(
                                            title = "Unlock Vault",
                                            subtitle = "Scan to unlock your encrypted data",
                                            cipher = cipher,
                                        )
                                    result.onSuccess { crypto ->
                                        crypto?.cipher?.let { viewModel.unlockWithBiometricCipher(it) }
                                    }
                                }

                                fun runBiometricUnlockPrompt() {
                                    scope.launch {
                                        try {
                                            promptBiometricUnlock()
                                        } finally {
                                            biometricCoordinator.finishPrompt()
                                        }
                                    }
                                }

                                fun launchBiometricPromptIfAllowed() {
                                    biometricCoordinator.tryStartPrompt(
                                        isEnabled = currentState.isBiometricUnlockPossible,
                                        isUnlocking = currentState.isUnlocking,
                                    ) {
                                        runBiometricUnlockPrompt()
                                    }
                                }

                                fun launchBiometricPromptFromUser() {
                                    biometricCoordinator.tryStartUserPrompt(
                                        isEnabled = currentState.isBiometricUnlockPossible,
                                    ) {
                                        runBiometricUnlockPrompt()
                                    }
                                }

                                // Auto-trigger biometric if available
                                if (currentState.isBiometricUnlockPossible) {
                                    BiometricVaultUnlockEffect(
                                        isEnabled = true,
                                        isUnlocking = currentState.isUnlocking,
                                        onPrompt = { launchBiometricPromptIfAllowed() },
                                    )
                                }

                                UnlockVaultScreen(
                                    authState = currentState.authState,
                                    isUnlocking = currentState.isUnlocking,
                                    isVaultLocked = currentState.isVaultLocked,
                                    isBiometricEnrollmentPending = currentState.isBiometricEnrollmentPending,
                                    isBiometricUnlockConfigured = currentState.isBiometricUnlockPossible,
                                    errorMessage = currentState.vaultErrorMessage,
                                    unlockSyncProgress = currentState.unlockSyncProgress,
                                    unlockSyncStatus = currentState.unlockSyncStatus,
                                    onLoginClick = {
                                        loggingPort.debug(TAG, "UnlockVaultScreen: Sign in clicked")
                                        viewModel.loginWithGoogle()
                                    },
                                    onSubmitVaultPassword = { password, enableBiometric ->
                                        loggingPort.debug(TAG, "Vault dialog submitted bio=$enableBiometric")
                                        viewModel.unlockVault(password, enableBiometric)
                                    },
                                    onBiometricUnlockClick =
                                        if (currentState.isBiometricUnlockPossible) {
                                            { launchBiometricPromptFromUser() }
                                        } else {
                                            null
                                        },
                                )
                            } else {
                                // App renders only after initialRoute is verified and vault is unlocked
                                EnclaveApp(
                                    initialRoute = currentState.initialRoute,
                                    modelsResult = currentState.modelsResult,
                                    errorMessage = currentState.initializationError,
                                    navigationIntent = latestIntent,
                                    refreshDownloadPlan =
                                        DownloadPlanRefresher {
                                            viewModel.refreshDownloadModels()
                                        },
                                )

                                currentState.pendingVaultReconciliation?.let { pending ->
                                    VaultMismatchConfirmDialog(
                                        isReconciling = pending.isReconciling,
                                        onConfirm = { viewModel.confirmAdoptCloud() },
                                        onCancel = { viewModel.cancelReconciliation() },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestIntent = intent
    }
}

@Composable
private fun BiometricVaultUnlockEffect(
    isEnabled: Boolean,
    isUnlocking: Boolean,
    onPrompt: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, isEnabled, isUnlocking) {
        if (isEnabled) {
            onPrompt()
        }

        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    onPrompt()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

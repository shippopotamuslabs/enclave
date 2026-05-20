package com.browntowndev.pocketcrew.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.pocketcrew.core.ui.component.vault.VaultMismatchConfirmDialog
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.feature.settings.components.UnlockVaultScreen
import com.browntowndev.pocketcrew.presentation.security.BiometricPromptCoordinator
import com.browntowndev.pocketcrew.presentation.security.BiometricPromptHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val appViewModel: PocketCrewAppViewModel by viewModels()

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
        latestIntent = intent
        biometricHelper = BiometricPromptHelper(this)

        // Gatekeeper: Keeps the native splash screen visible while state is Loading
        splashScreen.setKeepOnScreenCondition {
            viewModel.startupState.value is AppStartupState.Loading
        }

        setContent {
            val themeState by appViewModel.themeUiState.collectAsStateWithLifecycle()
            val state by viewModel.startupState.collectAsStateWithLifecycle()

            PocketCrewTheme(
                darkTheme = themeState.darkTheme,
                dynamicColor = themeState.dynamicColor,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
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
                                // Auto-trigger biometric if available
                                if (currentState.isBiometricUnlockPossible) {
                                    BiometricVaultUnlockEffect(
                                        isEnabled = true,
                                        isUnlocking = currentState.isUnlocking,
                                        onPrompt = {
                                            val cipher = viewModel.getBiometricDecryptCipher()
                                            if (cipher != null) {
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
                                        },
                                    )
                                }

                                UnlockVaultScreen(
                                    authState = currentState.authState,
                                    isUnlocking = currentState.isUnlocking,
                                    isBiometricUnlockConfigured = currentState.isBiometricUnlockPossible,
                                    errorMessage = currentState.vaultErrorMessage,
                                    onLoginClick = {
                                        loggingPort.debug(TAG, "UnlockVaultScreen: Sign in clicked")
                                        viewModel.loginWithGoogle()
                                    },
                                    onSubmitVaultPassword = { password, enableBiometric ->
                                        loggingPort.debug(TAG, "Vault dialog submitted bio=$enableBiometric")
                                        viewModel.unlockVault(password, enableBiometric)
                                    },
                                )
                            } else {
                                // App renders only after initialRoute is verified and vault is unlocked
                                PocketCrewApp(
                                    initialRoute = currentState.initialRoute,
                                    modelsResult = currentState.modelsResult,
                                    errorMessage = currentState.initializationError,
                                    navigationIntent = latestIntent,
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
    onPrompt: suspend () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val coordinator = remember { BiometricPromptCoordinator() }
    val latestOnPrompt by rememberUpdatedState(onPrompt)

    DisposableEffect(lifecycleOwner, isEnabled, isUnlocking) {
        fun launchPromptIfAllowed() {
            coordinator.tryStartPrompt(
                isEnabled = isEnabled,
                isUnlocking = isUnlocking,
            ) {
                scope.launch {
                    try {
                        latestOnPrompt()
                    } finally {
                        coordinator.finishPrompt()
                    }
                }
            }
        }

        if (isEnabled) {
            launchPromptIfAllowed()
        }

        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    launchPromptIfAllowed()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

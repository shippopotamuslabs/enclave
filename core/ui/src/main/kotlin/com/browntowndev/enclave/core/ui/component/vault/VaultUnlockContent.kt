@file:Suppress("LongMethod", "MagicNumber", "MaxLineLength")

package com.browntowndev.enclave.core.ui.component.vault

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.browntowndev.enclave.core.ui.component.neo.GoogleSignInButton
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoToggleRow
import com.browntowndev.enclave.core.ui.component.neo.neoBorderColor
import com.browntowndev.enclave.core.ui.component.neo.neoButtonShadowOffset
import com.browntowndev.enclave.core.ui.component.neo.neoOutlinedTextFieldColors
import com.browntowndev.enclave.core.ui.component.neo.neoShadowColor
import com.browntowndev.enclave.core.ui.component.neo.neoSurface
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens

/**
 * Stateless vault-unlock UI body. Contains the full password + biometric-toggle + Google
 * sign-in UI used by both the full-screen `UnlockVaultScreen` and the `VaultReauthBottomSheet`
 * surfaced from Settings. Keeps `Surface` / `Scaffold` chrome to the host so the same body
 * can render fullscreen, inside a sheet, or anywhere else.
 *
 * The host is responsible for:
 *  - Outer background colour and system-bar / IME insets.
 *  - Wiring the [onSubmitVaultPassword] callback to a ViewModel.
 *
 * @param showGoogleSignInOnUnauth when true and `authState` is unauthenticated/loading/error,
 *   render the "Sign in with Google for sync" button. Set false in contexts where the user
 *   is guaranteed signed in (e.g. settings re-auth).
 * @param showBiometricToggle when true, render the biometric opt-in switch. Bottom-sheet
 *   re-auth flows hide this since biometric enrollment happens at first unlock only.
 * @param initialBiometricEnabled when true, biometrics are already enrolled: show a
 *   [onBiometricUnlockClick] action instead of the opt-in toggle.
 * @param onBiometricUnlockClick invoked when the user taps "Use Biometrics" on an enrolled device.
 */
@Composable
fun VaultUnlockContent(
    authState: VaultAuthUiState,
    errorMessage: String?,
    isUnlocking: Boolean = false,
    isVaultLocked: Boolean = true,
    isBiometricEnrollmentPending: Boolean = false,
    showGoogleSignInOnUnauth: Boolean = true,
    showBiometricToggle: Boolean = true,
    initialBiometricEnabled: Boolean = false,
    onLoginClick: () -> Unit,
    onSubmitVaultPassword: (password: String, enableBiometric: Boolean) -> Unit,
    onBiometricUnlockClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    unlockSyncProgress: Float? = null,
    unlockSyncStatus: String? = null,
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    // Keyed remember: when the host reports biometric material exists (or not), reset local
    // toggle state. Plain `remember { mutableStateOf(initial) }` only used the first value and
    // stayed stale after AnimatedContent / unlocking transitions or prop corrections.
    var enableBiometric by remember(initialBiometricEnabled) { mutableStateOf(initialBiometricEnabled) }
    val showUnlockingUi =
        rememberUnlockingUiVisible(
            isUnlocking = isUnlocking,
            isVaultLocked = isVaultLocked,
            isBiometricEnrollmentPending = isBiometricEnrollmentPending,
            errorMessage = errorMessage,
        )

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (authState) {
            is VaultAuthUiState.Authenticated -> {
                if (authState.user.photoUrl != null) {
                    AsyncImage(
                        model = authState.user.photoUrl,
                        contentDescription = "Profile picture",
                        modifier =
                            Modifier
                                .size(80.dp)
                                .clip(EnclaveNeoTokens.shape)
                                .border(EnclaveNeoTokens.borderWidth, neoBorderColor(), EnclaveNeoTokens.shape),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .size(80.dp)
                                .neoSurface(
                                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                    borderColor = neoBorderColor(),
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Welcome back, ${authState.user.displayName ?: "User"}",
                    style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 0.5.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                authState.user.email?.let { email ->
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            VaultAuthUiState.Unauthenticated, VaultAuthUiState.Loading, is VaultAuthUiState.Error -> {
                Box(
                    modifier =
                        Modifier
                            .size(80.dp)
                            .neoSurface(
                                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                borderColor = neoBorderColor(),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Unlock Enclave",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Enter your Enclave password to decrypt local data. Google sign-in is optional and only enables encrypted sync.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (authState is VaultAuthUiState.Error) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = authState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }

                AnimatedVisibility(
                    visible = showGoogleSignInOnUnauth && !showUnlockingUi,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(48.dp))
                        GoogleSignInButton(
                            onClick = onLoginClick,
                            enabled = authState !is VaultAuthUiState.Loading,
                            text = if (authState is VaultAuthUiState.Loading) "Signing in..." else "Sign in with Google for sync",
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedContent(
            targetState = showUnlockingUi,
            transitionSpec = { fadeIn().togetherWith(fadeOut()) },
            label = "vault_unlocking_transition",
        ) { targetIsUnlocking ->
            if (targetIsUnlocking) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(64.dp)
                                .neoSurface(
                                    backgroundColor = MaterialTheme.colorScheme.primary,
                                    borderColor = neoBorderColor(),
                                    withShadow = true,
                                    shadowColor = neoShadowColor(),
                                    shadowOffset = neoButtonShadowOffset(),
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Signing in",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = unlockSyncStatus ?: "Signing In...",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("unlock_sync_status_text"),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("unlock_sync_error_text"),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    if (unlockSyncProgress != null) {
                        val progressValue = unlockSyncProgress
                        LinearProgressIndicator(
                            progress = { progressValue },
                            strokeCap = StrokeCap.Butt,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(EnclaveNeoTokens.shape)
                                    .testTag("unlock_sync_progress_indicator"),
                        )
                    } else {
                        LinearProgressIndicator(
                            strokeCap = StrokeCap.Butt,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(EnclaveNeoTokens.shape)
                                    .testTag("unlock_sync_progress_indicator_indeterminate"),
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(64.dp)
                                .neoSurface(
                                    backgroundColor = MaterialTheme.colorScheme.primary,
                                    borderColor = neoBorderColor(),
                                    withShadow = true,
                                    shadowColor = neoShadowColor(),
                                    shadowOffset = neoButtonShadowOffset(),
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Vault Lock",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Unlock Your Vault",
                        style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 0.5.sp),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Your password derives the local zero-knowledge encryption key.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .neoSurface(
                                    backgroundColor = MaterialTheme.colorScheme.errorContainer,
                                    borderColor = MaterialTheme.colorScheme.error,
                                    withShadow = false,
                                ).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Store your password in a secure place. Enclave does not store your password and it cannot be recovered if lost.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Enclave Password") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                )
                            }
                        },
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    if (password.isNotBlank()) {
                                        onSubmitVaultPassword(password, enableBiometric)
                                    }
                                },
                            ),
                        isError = errorMessage != null,
                        supportingText =
                            if (errorMessage != null) {
                                { Text(errorMessage) }
                            } else {
                                null
                            },
                        shape = EnclaveNeoTokens.shape,
                        colors = neoOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (showBiometricToggle) {
                        Spacer(modifier = Modifier.height(16.dp))

                        if (initialBiometricEnabled && onBiometricUnlockClick != null) {
                            NeoPrimaryButton(
                                text = "Use Biometrics",
                                onClick = onBiometricUnlockClick,
                                icon = Icons.Default.Fingerprint,
                                backgroundColor = MaterialTheme.colorScheme.background,
                                contentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("use_biometrics_button"),
                            )
                        } else {
                            NeoToggleRow(
                                title = "Biometric Unlock",
                                subtitle = "Use fingerprint next time",
                                checked = enableBiometric,
                                onCheckedChange = { enableBiometric = it },
                                icon = Icons.Default.Fingerprint,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    NeoPrimaryButton(
                        text = "Unlock",
                        onClick = { onSubmitVaultPassword(password, enableBiometric) },
                        enabled = password.isNotBlank(),
                        withShadow = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberUnlockingUiVisible(
    isUnlocking: Boolean,
    isVaultLocked: Boolean,
    isBiometricEnrollmentPending: Boolean,
    errorMessage: String?,
): Boolean {
    var unlockUiLatched by remember { mutableStateOf(false) }

    // Synchronous latch: inline assignment runs in the same composition frame so there is no
    // async gap where isUnlocking can flip back to false before the latch is set (which was
    // the cause of the brief flicker back to the form after biometric/password unlock).
    if (isUnlocking && !unlockUiLatched) unlockUiLatched = true
    when {
        errorMessage != null && unlockUiLatched -> unlockUiLatched = false
        !isUnlocking && isBiometricEnrollmentPending && unlockUiLatched -> unlockUiLatched = false
        shouldReleaseUnlockLatch(isUnlocking, isVaultLocked, isBiometricEnrollmentPending, unlockUiLatched) -> {
            unlockUiLatched = false
        }
    }

    return isUnlocking || unlockUiLatched
}

private fun shouldReleaseUnlockLatch(
    isUnlocking: Boolean,
    isVaultLocked: Boolean,
    isBiometricEnrollmentPending: Boolean,
    unlockUiLatched: Boolean,
): Boolean =
    !isUnlocking && isVaultLocked && !isBiometricEnrollmentPending && unlockUiLatched

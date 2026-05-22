@file:Suppress("LongMethod", "MagicNumber", "MaxLineLength")

package com.browntowndev.enclave.core.ui.component.vault

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.browntowndev.enclave.core.ui.component.ShimmerText

/**
 * Stateless vault-unlock UI body. Contains the full password + biometric-toggle + Google
 * sign-in UI used by both the full-screen `UnlockVaultScreen` and the `VaultReauthBottomSheet`
 * surfaced from Settings. Keeps `Surface` / `Scaffold` chrome to the host so the same body
 * can render fullscreen, inside a sheet, or anywhere else.
 *
 * The host is responsible for:
 *  - Outer `Surface` background colour and system-bar / IME insets.
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
    showGoogleSignInOnUnauth: Boolean = true,
    showBiometricToggle: Boolean = true,
    initialBiometricEnabled: Boolean = false,
    onLoginClick: () -> Unit,
    onSubmitVaultPassword: (password: String, enableBiometric: Boolean) -> Unit,
    onBiometricUnlockClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    // Keyed remember: when the host reports biometric material exists (or not), reset local
    // toggle state. Plain `remember { mutableStateOf(initial) }` only used the first value and
    // stayed stale after AnimatedContent / unlocking transitions or prop corrections.
    var enableBiometric by remember(initialBiometricEnabled) { mutableStateOf(initialBiometricEnabled) }
    var unlockUiLatched by remember { mutableStateOf(false) }

    // Synchronous latch: inline assignment runs in the same composition frame so there is no
    // async gap where isUnlocking can flip back to false before the latch is set (which was
    // the cause of the brief flicker back to the form after biometric/password unlock).
    if (isUnlocking && !unlockUiLatched) unlockUiLatched = true
    if (errorMessage != null && unlockUiLatched) unlockUiLatched = false

    val showUnlockingUi = isUnlocking || unlockUiLatched

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
                                .clip(RoundedCornerShape(40.dp)),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Welcome back, ${authState.user.displayName ?: "User"}",
                    style = MaterialTheme.typography.titleLarge,
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
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                        Button(
                            onClick = onLoginClick,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                            shape = RoundedCornerShape(24.dp),
                            enabled = authState !is VaultAuthUiState.Loading,
                        ) {
                            Text(
                                if (authState is VaultAuthUiState.Loading) "Signing in..." else "Sign in with Google for sync",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
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
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Signing in",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ShimmerText(
                        text = "Signing In...",
                        baseColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        highlightColor = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Vault Lock",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Unlock Your Vault",
                        style = MaterialTheme.typography.headlineSmall,
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

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
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
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (showBiometricToggle) {
                        Spacer(modifier = Modifier.height(16.dp))

                        if (initialBiometricEnabled && onBiometricUnlockClick != null) {
                            val unlockWithBiometrics = onBiometricUnlockClick
                            TextButton(
                                onClick = { unlockWithBiometrics() },
                                modifier = Modifier.fillMaxWidth().testTag("use_biometrics_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors =
                                    ButtonDefaults.textButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                contentPadding = PaddingValues(16.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Use Biometrics",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        } else {
                            val surfaceColor by animateColorAsState(
                                targetValue =
                                    if (enableBiometric) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                            .copy(
                                                alpha = 0.5f,
                                            )
                                    },
                                label = "biometric_surface_color",
                            )

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = surfaceColor,
                                onClick = { enableBiometric = !enableBiometric },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = null,
                                        tint = if (enableBiometric) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Biometric Unlock",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (enableBiometric) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "Use fingerprint next time",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (enableBiometric) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = enableBiometric,
                                        onCheckedChange = { enableBiometric = it },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { onSubmitVaultPassword(password, enableBiometric) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        shape = RoundedCornerShape(24.dp),
                        enabled = password.isNotBlank(),
                    ) {
                        Text("Unlock", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

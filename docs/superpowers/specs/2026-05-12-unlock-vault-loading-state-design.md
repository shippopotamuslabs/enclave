# Design Spec: Unlock Vault Loading State

## 1. Overview
The `UnlockVaultScreen` currently remains static while the vault is being unlocked (key derivation and cloud salt fetch), which takes 2-3 seconds. This spec introduces an "Unlocking" state that provides visual feedback using `ShimmerText`.

## 2. Goals
- Eliminate the perception of a "frozen" UI after biometrics or manual password entry.
- Provide a clear "Signing In..." status.
- Maintain UI consistency between biometric and manual unlock flows.

## 3. Architecture & State Changes

### 3.1 AppStartupState (MainViewModel.kt)
Modify `AppStartupState.Ready` to include a new boolean flag.

```kotlin
data class Ready(
    // ...
    val isUnlocking: Boolean = false,
    // ...
)
```

### 3.2 MainViewModel Logic
Update `unlockVault(password, enableBiometric)`:
1. Immediately update `_startupState` to set `isUnlocking = true`.
2. Wrap the core logic in a `try/finally` block.
3. In `finally`, update `_startupState` to set `isUnlocking = false` (unless the state has transitioned away from `Ready`).

Update `unlockWithBiometricCipher(cipher)`:
- This calls `unlockVault` internally, so the logic above will handle it.

### 3.3 MainActivity Integration
Update the call to `UnlockVaultScreen` to pass `currentState.isUnlocking`.

## 4. UI Design (UnlockVaultScreen.kt)

### 4.1 Interface Update
Add `isUnlocking: Boolean = false` to the `UnlockVaultScreen` parameters.

### 4.2 Layout Transition
When `isUnlocking` is true, replace the following section:
- "Unlock Your Vault" Header
- Form description
- Security warning box
- Password TextField
- Biometric Switch
- Unlock Button

With:
- **Lock Icon**: `Icons.Default.Lock`, tinted `MaterialTheme.colorScheme.primary`, size `48.dp`.
- **ShimmerText**:
  - text: `"Signing In..."`
  - baseColor: `MaterialTheme.colorScheme.onSurfaceVariant`
  - highlightColor: `MaterialTheme.colorScheme.primary`
  - style: `MaterialTheme.typography.headlineSmall` (Bold)

The "Welcome back" header and profile picture should remain visible.

## 5. Testing Strategy
- **Manual Test**: Verify that clicking "Unlock" immediately hides the form and shows "Signing In...".
- **Manual Test**: Verify that successful biometric scan triggers the "Signing In..." state.
- **Manual Test**: Verify that if unlocking fails (e.g., network error or wrong password), the form reappears with the error message.

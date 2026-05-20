# ORCH-ITER1-ARCH-01 — Centralize vault unlock + cloud sync orchestration

## Feature summary

Extract duplicated vault password unlock (local metadata vs cloud metadata) and post-unlock cloud sync (`syncAllLocalDataToCloud` + `startListening`) from `MainViewModel` and `SettingsGeneralDelegate` into domain use cases. `MainViewModel` keeps reconciliation UI state; delegates unlock/sync mechanics to shared use cases.

## File structure

| File | Action | Responsibility |
|------|--------|----------------|
| `core/domain/.../UnlockVaultFromStoredMetadataUseCase.kt` | Create | Unlock DEK from local wrapped DEK or fetch cloud vault metadata, persist locally, cache DEK |
| `core/domain/.../StartCloudSyncListeningUseCase.kt` | Create | Optional `syncAllLocalDataToCloud` then `startListening` |
| `core/domain/src/test/.../UnlockVaultFromStoredMetadataUseCaseTest.kt` | Create | Unit tests for local/cloud unlock paths |
| `core/domain/src/test/.../StartCloudSyncListeningUseCaseTest.kt` | Create | Unit tests for upload + listener |
| `app/.../MainViewModel.kt` | Modify | Use new use cases in `startCloudSyncAndListen` / post-reconcile unlock paths where applicable |
| `feature/settings/.../SettingsGeneralDelegate.kt` | Modify | Replace private unlock/sync helpers with use cases |
| `feature/settings/.../SettingsGeneralDelegateTest.kt` | Modify | Wire mocks for new use cases |

## API contracts

### `UnlockVaultFromStoredMetadataUseCase`

```kotlin
class UnlockVaultFromStoredMetadataUseCase @Inject constructor(
  unlockVaultUseCase: UnlockVaultUseCase,
  cryptoManager: CryptoManagerPort,
  vaultMetadataPort: VaultMetadataPort,
  cloudSyncPort: CloudSyncPort,
) {
  suspend operator fun invoke(password: String): Result<Unit>
}
```

Behavior:
- If local salt + wrapped DEK present → `UnlockVaultUseCase(password, salt, wrapped)`.
- Else → `cloudSyncPort.fetchVaultMetadata()`, persist salt/wrapped/verification to `VaultMetadataPort`, then unlock.

### `StartCloudSyncListeningUseCase`

```kotlin
class StartCloudSyncListeningUseCase @Inject constructor(
  cloudSyncPort: CloudSyncPort,
) {
  suspend operator fun invoke(uploadLocalFirst: Boolean = true): Result<Unit>
}
```

Behavior:
- If `uploadLocalFirst` → `runCatching { cloudSyncPort.syncAllLocalDataToCloud() }`.
- Always `cloudSyncPort.startListening()`.

## Data model / schema changes

None.

## Ordered implementation tasks

1. **ARCH-01-T1** — Add `UnlockVaultFromStoredMetadataUseCase` + tests (SC-ARCH-01-01, SC-ARCH-01-02, SC-ARCH-01-03).
2. **ARCH-01-T2** — Add `StartCloudSyncListeningUseCase` + tests (SC-ARCH-01-04, SC-ARCH-01-05).
3. **ARCH-01-T3** — Refactor `SettingsGeneralDelegate` to use both use cases (SC-ARCH-01-06).
4. **ARCH-01-T4** — Refactor `MainViewModel.startCloudSyncAndListen` to use `StartCloudSyncListeningUseCase` (SC-ARCH-01-07).
5. **ARCH-01-T5** — Update delegate/ViewModel tests (SC-ARCH-01-06, SC-ARCH-01-07).

## Scenario verification map

| Task | Scenarios |
|------|-----------|
| ARCH-01-T1 | SC-ARCH-01-01, SC-ARCH-01-02, SC-ARCH-01-03 |
| ARCH-01-T2 | SC-ARCH-01-04, SC-ARCH-01-05 |
| ARCH-01-T3 | SC-ARCH-01-06 |
| ARCH-01-T4 | SC-ARCH-01-07 |
| ARCH-01-T5 | SC-ARCH-01-06, SC-ARCH-01-07 |

## Drift / escalation

- Do not move `DetectVaultReconciliationStateUseCase` branching or `PendingReconciliation` UI into domain.
- Do not change `PocketCrewApplication` auth gate (TEST-01 separate ticket).

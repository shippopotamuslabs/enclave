# ORCH-ITER1-ARCH-01 — Test spec

## Framework and command

- JUnit 5 + MockK + `kotlinx-coroutines-test`
- `./gradlew :core:domain:testDebugUnitTest --tests '*UnlockVaultFromStoredMetadata*' --tests '*StartCloudSyncListening*'`
- `./gradlew :feature:settings:testDebugUnitTest --tests '*SettingsGeneralDelegate*'`
- `./gradlew :app:testDebugUnitTest --tests '*MainViewModel*'`

## Scenario index

| ID | Title |
|----|-------|
| SC-ARCH-01-01 | Local metadata unlock delegates to UnlockVaultUseCase |
| SC-ARCH-01-02 | Cloud metadata unlock fetches, persists, then unlocks |
| SC-ARCH-01-03 | Cloud metadata missing returns failure |
| SC-ARCH-01-04 | Start sync uploads then starts listener when uploadLocalFirst true |
| SC-ARCH-01-05 | Start sync skips upload when uploadLocalFirst false |
| SC-ARCH-01-06 | Settings re-auth uses shared unlock + start sync use cases |
| SC-ARCH-01-07 | MainViewModel startCloudSyncAndListen uses StartCloudSyncListeningUseCase |
| SC-ARCH-01-MUTATION | StartCloudSyncListening must call startListening even if upload fails |

## Scenarios

### SC-ARCH-01-01
```gherkin
Given local salt "salt-b64" and wrapped DEK "wrapped-b64"
When UnlockVaultFromStoredMetadataUseCase is invoked with password "pw"
Then UnlockVaultUseCase is called with password "pw"
And the result is success
```

### SC-ARCH-01-02
```gherkin
Given no local salt or wrapped DEK
And cloud metadata salt "cloud-salt" wrappedDek "cloud-wrapped"
When invoked with password "pw"
Then vaultMetadataPort saves salt "cloud-salt" and wrapped "cloud-wrapped"
And UnlockVaultUseCase succeeds
```

### SC-ARCH-01-03
```gherkin
Given no local metadata and cloud fetch returns null
When invoked
Then result is failure with message "Cloud vault metadata missing"
```

### SC-ARCH-01-04
```gherkin
When StartCloudSyncListeningUseCase(uploadLocalFirst=true)
Then syncAllLocalDataToCloud is called once
And startListening is called once
```

### SC-ARCH-01-05
```gherkin
When StartCloudSyncListeningUseCase(uploadLocalFirst=false)
Then syncAllLocalDataToCloud is never called
And startListening is called once
```

### SC-ARCH-01-06
```gherkin
Given reconciliation outcome AlreadyMatching
When onSubmitVaultReauthPassword succeeds unlock path
Then UnlockVaultFromStoredMetadataUseCase is called
And StartCloudSyncListeningUseCase is called with uploadLocalFirst true
```

### SC-ARCH-01-07
```gherkin
When MainViewModel reconcile path calls startCloudSyncAndListen(initialUpload=true)
Then StartCloudSyncListeningUseCase is invoked with uploadLocalFirst true
```

### SC-ARCH-01-MUTATION
```gherkin
Given syncAllLocalDataToCloud returns failure
When StartCloudSyncListeningUseCase(uploadLocalFirst=true)
Then startListening is still called once
```

## Mutation-defense

Lazy implementation that only calls `syncAllLocalDataToCloud` without `startListening` fails SC-ARCH-01-MUTATION.

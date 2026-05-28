# Onboarding Wizard — Test Spec

**Ticket:** `onboarding-wizard`

---

## Test Framework

- **Engine:** JUnit 5 (`org.junit.jupiter.api`)
- **Coroutines:** `kotlinx.coroutines.test.runTest` + `StandardTestDispatcher`
- **ViewModel test dispatcher:** `MainDispatcherRule` (`@ExtendWith(MainDispatcherRule::class)`)
  located at `core/testing/src/main/kotlin/…/MainDispatcherRule.kt`
- **Flow assertions:** Turbine (`app.cash.turbine.test { … }`)
- **Fakes over mocks:** `FakeSettingsRepository`, `FakeDefaultModelRepository`,
  `FakeSaveApiCredentialsUseCase`, `FakeBulkAssignApiProviderUseCase`, etc.
- **No JUnit 4:** Do not use `@Rule`, `@Before`, or `@Test(expected = …)`.

### Run commands

```bash
# Single module — use during iteration
./gradlew :feature:onboarding:testDebugUnitTest

# Use case unit tests only
./gradlew :core:domain:testDebugUnitTest --tests "*BulkAssignApiProviderUseCaseTest*"

# Full test run before merge
./gradlew testDebugUnitTest
```

---

## Scenario Index

| ID | Description | File |
|----|-------------|------|
| OB-01 | First-launch routes to onboarding | `MainViewModelOnboardingTest` |
| OB-02 | Step 0 — Continue disabled when age not confirmed | `OnboardingViewModelTest` |
| OB-03 | Step 0 — Continue advances to step 1 when confirmed | `OnboardingViewModelTest` |
| OB-04 | Back on step 0 is no-op | `OnboardingViewModelTest` |
| OB-05 | Step 1 API path — model discovery populates model list | `OnboardingViewModelTest` |
| OB-06 | Step 1 API path — Continue disabled when apiKey blank | `OnboardingViewModelTest` |
| OB-07 | Step 1 API path — Continue advances when key + model set | `OnboardingViewModelTest` |
| OB-08 | Step 1 LOCAL path — Continue advances immediately | `OnboardingViewModelTest` |
| OB-09 | Step 2 — Skip advances to step 3 without saving | `OnboardingViewModelTest` |
| OB-10 | Step 3 — Complete persists flags and emits NavigateToChat | `OnboardingViewModelTest` |
| OB-11 | Step 3 — Complete on LOCAL path skips bulk-assign | `OnboardingViewModelTest` |
| OB-12 | `updateAgeConfirmed` persists `true` to settings | `SettingsRepositoryImplTest` |
| OB-13 | `updateOnboardingCompleted(true)` is read back as `true` | `SettingsRepositoryImplTest` |
| OB-14 | `BulkAssignApiProviderUseCase` assigns all six TEXT_SLOTS | `BulkAssignApiProviderUseCaseTest` |
| OB-15 | Save failure sets `saveError` and does NOT emit NavigateToChat | `OnboardingViewModelTest` |
| OB-16 | Age-gate checkbox semantics: `role = Role.Checkbox`, correct `contentDescription` | `OnboardingViewModelTest` (state-based accessibility check) |
| OB-17 | MUTATION DEFENCE — completing without ageConfirmed = true does NOT set onboardingCompleted | `OnboardingViewModelTest` |

---

## Happy Path Scenarios

### OB-01 — First-launch routes to onboarding

**File:** `app/src/test/kotlin/…/MainViewModelOnboardingTest.kt`

```gherkin
Given the vault is unlocked (cryptoManager.hasCachedKey() returns true)
  And settingsRepository.settingsFlow emits SettingsData(onboardingCompleted = false)
When initializeApp() executes and collectStartupState() completes
Then startupState.value is AppStartupState.Ready
  And (startupState.value as AppStartupState.Ready).initialRoute == "onboarding"
```

**Setup:** Construct `MainViewModel` with a `FakeSettingsRepository` emitting
`SettingsData(onboardingCompleted = false)`. Stub `initializeModelsUseCase` to return a
`DownloadModelsResult` with `modelsToDownload = emptyList()`.

**Expected value:** `initialRoute` equals the string literal `"onboarding"`.

---

### OB-02 — Step 0 continue disabled when age not confirmed

**File:** `OnboardingViewModelTest`

```gherkin
Given the ViewModel is freshly constructed (OnboardingUiState default)
  And uiState.ageConfirmed == false
When uiState is observed
Then uiState.currentStep == 0
  And canProceedFromStep0 (derived: uiState.ageConfirmed) == false
```

**Expected values:** `currentStep = 0`, `ageConfirmed = false`.

---

### OB-03 — Step 0 advances to step 1 when confirmed

```gherkin
Given uiState.ageConfirmed == false
When onAgeConfirmedChange(true) is called
  And onNextStep() is called
Then uiState.currentStep == 1
  And uiState.ageConfirmed == true
```

**Expected values:** `currentStep = 1`, `ageConfirmed = true`.

---

### OB-04 — Back on step 0 is no-op

```gherkin
Given uiState.currentStep == 0
When onPreviousStep() is called
Then uiState.currentStep == 0
```

**Expected value:** `currentStep = 0` (unchanged).

---

### OB-05 — Step 1 API path — model discovery populates model list

```gherkin
Given uiState.currentStep == 1
  And onModelChoiceSelected(ModelChoice.API) is called
  And onProviderSelected(ApiProvider.OPENROUTER) is called
  And onApiKeyChanged("sk-test-key") is called
  And discoverApiModelsUseCase succeeds with [DiscoveredApiModel(id = "openrouter/gpt-4o"), DiscoveredApiModel(id = "openrouter/claude-3-haiku")]
When onDiscoverModels() is called and coroutines settle
Then uiState.isDiscoveringModels == false
  And uiState.discoveredModels has size 2
  And uiState.discoveredModels[0].id == "openrouter/gpt-4o"
  And uiState.discoveryError == null
```

**Expected values:** `isDiscoveringModels = false`, `discoveredModels.size = 2`,
`discoveredModels[0].id = "openrouter/gpt-4o"`, `discoveryError = null`.

---

### OB-06 — Step 1 API path — Continue disabled when apiKey blank

```gherkin
Given uiState.currentStep == 1
  And onModelChoiceSelected(ModelChoice.API) is called
  And selectedProvider == ApiProvider.OPENROUTER
  And apiKey == ""
When onNextStep() is called
Then uiState.currentStep == 1  (no advance)
```

**Expected value:** `currentStep = 1` (unchanged).

---

### OB-07 — Step 1 API path — Continue advances when key + model set

```gherkin
Given uiState.currentStep == 1
  And selectedModelChoice == ModelChoice.API
  And selectedProvider == ApiProvider.OPENROUTER
  And apiKey == "sk-valid-key"
  And selectedModelId == "openrouter/gpt-4o"
When onNextStep() is called
Then uiState.currentStep == 2
```

**Expected value:** `currentStep = 2`.

---

### OB-08 — Step 1 LOCAL path — Continue advances immediately

```gherkin
Given uiState.currentStep == 1
When onModelChoiceSelected(ModelChoice.LOCAL) is called
  And onNextStep() is called
Then uiState.currentStep == 2
```

**Expected value:** `currentStep = 2`.

---

### OB-09 — Step 2 — Skip advances to step 3 without saving

```gherkin
Given uiState.currentStep == 2
When onSkipMedia() is called
Then uiState.currentStep == 3
  And saveTtsProviderUseCase was NOT called
  And saveMediaProviderUseCase was NOT called
```

**Expected values:** `currentStep = 3`, `FakeSaveTtsProviderUseCase.callCount = 0`,
`FakeSaveMediaProviderUseCase.callCount = 0`.

---

### OB-10 — Step 3 — Complete (API path) persists flags and emits NavigateToChat

```gherkin
Given selectedModelChoice == ModelChoice.API
  And selectedProvider == ApiProvider.OPENROUTER
  And apiKey == "sk-valid-key"
  And selectedModelId == "openrouter/gpt-4o"
  And uiState.currentStep == 3
  And bulkAssignApiProviderUseCase.invoke succeeds with ApiCredentialsId("creds-123")
When onCompleteOnboarding() is called and coroutines settle
Then FakeSettingsRepository.ageConfirmed == true
  And FakeSettingsRepository.onboardingCompleted == true
  And the events Flow emits exactly one OnboardingEvent.NavigateToChat
  And uiState.isSaving == false
  And uiState.saveError == null
```

**Expected values:** `ageConfirmed = true`, `onboardingCompleted = true`,
event type `OnboardingEvent.NavigateToChat`, `isSaving = false`, `saveError = null`.

---

### OB-11 — Step 3 — Complete on LOCAL path skips bulk-assign

```gherkin
Given selectedModelChoice == ModelChoice.LOCAL
  And uiState.currentStep == 3
When onCompleteOnboarding() is called and coroutines settle
Then bulkAssignApiProviderUseCase was NOT called (FakeBulkAssignApiProviderUseCase.callCount == 0)
  And FakeSettingsRepository.onboardingCompleted == true
  And events emits OnboardingEvent.NavigateToChat
```

**Expected values:** `FakeBulkAssignApiProviderUseCase.callCount = 0`,
`onboardingCompleted = true`, event `OnboardingEvent.NavigateToChat`.

---

### OB-12 — `updateAgeConfirmed` persists `true`

**File:** `SettingsRepositoryImplTest` (in `:core:data` test source set)

```gherkin
Given a fresh SettingsRepositoryImpl backed by an in-memory DataStore
When updateAgeConfirmed(true) is called
  And settingsFlow emits the next value
Then settingsFlow.first().ageConfirmed == true
```

**Expected value:** `ageConfirmed = true`.

---

### OB-13 — `updateOnboardingCompleted(true)` round-trips correctly

```gherkin
Given a fresh SettingsRepositoryImpl backed by an in-memory DataStore
  And settingsFlow.first().onboardingCompleted == false  (default)
When updateOnboardingCompleted(true) is called
  And settingsFlow emits the next value
Then settingsFlow.first().onboardingCompleted == true
```

**Expected values:** initial `false`, post-update `true`.

---

### OB-14 — `BulkAssignApiProviderUseCase` assigns all six TEXT_SLOTS

**File:** `BulkAssignApiProviderUseCaseTest`

```gherkin
Given FakeSaveApiCredentialsUseCase returns ApiCredentialsId("creds-abc")
  And FakeSaveApiModelConfigurationUseCase returns ApiModelConfigurationId("config-xyz")
  And FakeSetDefaultModelUseCase records all calls
When BulkAssignApiProviderUseCaseImpl.invoke(
       credentials = ApiCredentials(id = ApiCredentialsId(""), displayName = "OpenRouter", provider = ApiProvider.OPENROUTER, credentialAlias = "openrouter"),
       apiKey = "sk-key-123",
       modelId = "openrouter/gpt-4o",
       displayName = "OpenRouter Preset",
       targetSlots = BulkAssignApiProviderUseCase.TEXT_SLOTS
     ) is called
Then the result is Result.success(ApiCredentialsId("creds-abc"))
  And FakeSetDefaultModelUseCase.recordedCalls has size 6
  And FakeSetDefaultModelUseCase.recordedCalls contains every ModelType in [MAIN, FAST, THINKING, DRAFT_ONE, DRAFT_TWO, FINAL_SYNTHESIS]
  And every recorded call has apiConfigId == ApiModelConfigurationId("config-xyz")
  And every recorded call has localConfigId == null
```

**Expected values:** `Result.success(ApiCredentialsId("creds-abc"))`, 6 recorded calls,
each with `apiConfigId = ApiModelConfigurationId("config-xyz")` and `localConfigId = null`.

---

## Error and Edge Case Scenarios

### OB-15 — Save failure sets `saveError` and does NOT emit NavigateToChat

```gherkin
Given selectedModelChoice == ModelChoice.API
  And selectedProvider == ApiProvider.OPENROUTER
  And apiKey == "sk-valid-key"
  And selectedModelId == "openrouter/gpt-4o"
  And uiState.currentStep == 3
  And bulkAssignApiProviderUseCase.invoke returns Result.failure(RuntimeException("Network error"))
When onCompleteOnboarding() is called and coroutines settle
Then uiState.saveError == "Network error"
  And uiState.isSaving == false
  And no OnboardingEvent is emitted
  And FakeSettingsRepository.onboardingCompleted == false
```

**Expected values:** `saveError = "Network error"`, `isSaving = false`,
zero events emitted, `onboardingCompleted = false`.

---

### OB-16 — Age-gate checkbox carries correct semantics (state-based accessibility check)

```gherkin
Given OnboardingUiState(ageConfirmed = false)
When the state is inspected
Then a ViewModel function `ageConfirmedSemanticDescription` returns "I confirm I am 18 or older"
  And the UI composable passes Modifier.semantics { role = Role.Checkbox } to the age-gate row
```

**Note:** This is verified as a unit assertion: the presentation model carries a stable
`contentDescription` constant string `"I confirm I am 18 or older"` and the ViewModel
exposes no logic that changes it, confirming the composable always receives the literal
string. A Compose instrumented test (optional, Phase 2) uses `onNodeWithContentDescription("I confirm I am 18 or older")`.

**Expected value:** The constant `OnboardingStrings.AGE_GATE_CONTENT_DESCRIPTION` equals
`"I confirm I am 18 or older"` (defined in the feature module's companion object or const).

---

## Mutation-Defence Scenario

### OB-17 — Completing without `ageConfirmed = true` MUST NOT set `onboardingCompleted`

**Risk:** A lazy implementation might call `updateOnboardingCompleted(true)` unconditionally
in `onCompleteOnboarding()` without checking `ageConfirmed`. This scenario fails against
that implementation.

```gherkin
Given a freshly constructed OnboardingViewModel
  And ageConfirmed was NEVER set to true (ageConfirmed == false throughout)
  And selectedModelChoice == ModelChoice.LOCAL
  And uiState.currentStep == 3
When onCompleteOnboarding() is called directly (bypassing step validation)
Then FakeSettingsRepository.onboardingCompleted == false
  And no OnboardingEvent.NavigateToChat is emitted
  And uiState.saveError == "Age confirmation is required"
```

**Expected values:** `onboardingCompleted = false`, zero events emitted,
`saveError = "Age confirmation is required"`.

**Why this catches lazy implementation:** An unconditional
`settingsRepository.updateOnboardingCompleted(true)` call would set
`onboardingCompleted = true` and emit `NavigateToChat`, causing the test to fail on both
the flag assertion and the event assertion.

---

## Test Fake Contracts

### `FakeBulkAssignApiProviderUseCase`

```kotlin
class FakeBulkAssignApiProviderUseCase : BulkAssignApiProviderUseCase {
    var callCount: Int = 0
    var resultToReturn: Result<ApiCredentialsId> = Result.success(ApiCredentialsId("fake-creds"))
    val recordedSlots: MutableList<List<ModelType>> = mutableListOf()

    override suspend fun invoke(
        credentials: ApiCredentials,
        apiKey: String,
        modelId: String,
        displayName: String,
        targetSlots: List<ModelType>,
    ): Result<ApiCredentialsId> {
        callCount++
        recordedSlots.add(targetSlots)
        return resultToReturn
    }
}
```

### `FakeSetDefaultModelUseCase`

```kotlin
class FakeSetDefaultModelUseCase : SetDefaultModelUseCase {
    data class Call(
        val modelType: ModelType,
        val localConfigId: LocalModelConfigurationId?,
        val apiConfigId: ApiModelConfigurationId?,
    )
    val recordedCalls: MutableList<Call> = mutableListOf()

    override suspend fun invoke(
        modelType: ModelType,
        localConfigId: LocalModelConfigurationId?,
        apiConfigId: ApiModelConfigurationId?,
        ttsProviderId: TtsProviderId?,
        mediaProviderId: MediaProviderId?,
    ) {
        recordedCalls.add(Call(modelType, localConfigId, apiConfigId))
    }
}
```

### `FakeSaveApiCredentialsUseCase`

```kotlin
class FakeSaveApiCredentialsUseCase : SaveApiCredentialsUseCase {
    var idToReturn: ApiCredentialsId = ApiCredentialsId("fake-creds")

    override suspend fun invoke(
        credentials: ApiCredentials,
        apiKey: String,
        sourceCredentialAlias: String?,
    ): ApiCredentialsId = idToReturn
}
```

### `FakeSaveApiModelConfigurationUseCase`

```kotlin
class FakeSaveApiModelConfigurationUseCase : SaveApiModelConfigurationUseCase {
    var idToReturn: Result<ApiModelConfigurationId> = Result.success(ApiModelConfigurationId("fake-config"))

    override suspend fun invoke(configuration: ApiModelConfiguration): Result<ApiModelConfigurationId> =
        idToReturn
}
```

### `FakeSettingsRepository` additions

Add to the existing fake in `core/domain/src/testFixtures`:

```kotlin
var onboardingCompleted: Boolean = false
    private set
var ageConfirmed: Boolean = false
    private set

override suspend fun updateOnboardingCompleted(completed: Boolean) {
    onboardingCompleted = completed
    _settingsFlow.value = _settingsFlow.value.copy(onboardingCompleted = completed)
}

override suspend fun updateAgeConfirmed(confirmed: Boolean) {
    ageConfirmed = confirmed
    _settingsFlow.value = _settingsFlow.value.copy(ageConfirmed = confirmed)
}
```

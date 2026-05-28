# Onboarding Wizard — Implementation Spec

**Ticket:** `onboarding-wizard`
**Status:** Awaiting human approval

---

## 1. Feature Summary

Add a four-step first-launch onboarding wizard to the Enclave Android app. The wizard
gates entry on age confirmation + TOS acceptance, collects an initial model choice
(local vs. API provider), optionally configures media/TTS providers, and writes an
`onboardingCompleted` flag to DataStore. `MainViewModel.initializeApp()` reads this
flag after vault unlock; if false, it routes to `Routes.ONBOARDING` instead of the
download/chat route. On subsequent cold-starts the wizard is skipped entirely.

The wizard must use the existing neo-brutalist theme tokens (`neoSurface`,
`neoBorderColor`, `NeoPrimaryButton`, `NeoOutlinedTextField`, `NeoApiKeyField`) and
follow every rule in `ARCHITECTURE_RULES.md`, `CODE_STYLE_RULES.md`,
`UI_DESIGN_SPEC.md`, and `UNIT_TESTING.md`.

---

## 2. File Structure

### New files

| File | Responsibility |
|------|----------------|
| `feature/onboarding/build.gradle.kts` | Gradle module declaration — depends on `:core:domain` and `:core:ui` only |
| `feature/onboarding/src/main/kotlin/…/OnboardingViewModel.kt` | Step state machine, use-case calls, nav events |
| `feature/onboarding/src/main/kotlin/…/OnboardingUiState.kt` | Immutable presentation model (step index, per-step field state) |
| `feature/onboarding/src/main/kotlin/…/OnboardingEvent.kt` | One-shot channel events (`NavigateToChat`) |
| `feature/onboarding/src/main/kotlin/…/OnboardingRoute.kt` | Hilt ViewModel wiring, event collection, screen entry |
| `feature/onboarding/src/main/kotlin/…/OnboardingScreen.kt` | `HorizontalPager`-based four-step screen composable, owns its `Scaffold` |
| `feature/onboarding/src/main/kotlin/…/steps/WelcomeAgeGateStep.kt` | Step 1 composable |
| `feature/onboarding/src/main/kotlin/…/steps/ModelChoiceStep.kt` | Step 2 composable — Local vs. API chooser |
| `feature/onboarding/src/main/kotlin/…/steps/MediaStep.kt` | Step 3 composable — Optional media/TTS |
| `feature/onboarding/src/main/kotlin/…/steps/CompletionStep.kt` | Step 4 composable — "You're all set!" |
| `feature/onboarding/src/main/kotlin/…/OnboardingModule.kt` | Hilt `@Module` binding `BulkAssignApiProviderUseCase` |
| `feature/onboarding/src/test/kotlin/…/OnboardingViewModelTest.kt` | ViewModel unit tests |
| `feature/onboarding/src/test/kotlin/…/BulkAssignApiProviderUseCaseTest.kt` | Use-case unit tests |
| `core/domain/src/main/kotlin/…/usecase/onboarding/BulkAssignApiProviderUseCase.kt` | New use case — saves credentials + config, assigns to all text `ModelType` slots |
| `core/domain/src/testFixtures/kotlin/…/FakeDefaultModelRepository.kt` | Test fake for `DefaultModelRepositoryPort` — if not yet present |

### Modified files

| File | Change |
|------|--------|
| `core/domain/src/main/kotlin/…/port/repository/SettingsRepository.kt` | Add `onboardingCompleted` and `ageConfirmed` fields to `SettingsData`; add `updateOnboardingCompleted(Boolean)` and `updateAgeConfirmed(Boolean)` to `SettingsRepository` interface |
| `core/data/src/main/kotlin/…/repository/SettingsRepositoryImpl.kt` | Add DataStore keys `settings_onboarding_completed` and `settings_age_confirmed`; implement both new methods; map both new keys in `settingsFlow` |
| `core/domain/src/testFixtures/kotlin/…/FakeSettingsRepository.kt` | Implement the two new `SettingsRepository` methods |
| `app/src/main/kotlin/…/presentation/MainViewModel.kt` | Read `onboardingCompleted` from `SettingsRepository` after vault unlock; route to `Routes.ONBOARDING` if false, else continue existing flow |
| `app/src/main/kotlin/…/presentation/navigation/Routes.kt` | Add `const val ONBOARDING = "onboarding"` |
| `app/src/main/kotlin/…/presentation/navigation/EnclaveNavGraph.kt` | Register `onboardingNavGraph(…)` extension before the download graph |
| `app/src/main/kotlin/…/presentation/navigation/OnboardingNavGraph.kt` | New file wiring `OnboardingRoute` into the nav host |
| `app/src/main/kotlin/…/app/SettingsUseCasesModule.kt` | **No change** — onboarding module uses its own `OnboardingModule` |
| `settings.gradle.kts` | Add `include(":feature:onboarding")` |

---

## 3. Domain-Layer Changes

### 3.1 `SettingsData` additions

```
onboardingCompleted: Boolean = false
ageConfirmed: Boolean = false
```

Default `false` ensures first-launch correctly shows the wizard on reinstall (satisfies
the cloud-sync-on-reinstall requirement because DataStore is cleared by a fresh install).

### 3.2 `SettingsRepository` additions

```
suspend fun updateOnboardingCompleted(completed: Boolean)
suspend fun updateAgeConfirmed(confirmed: Boolean)
```

### 3.3 `SettingsRepositoryImpl` additions

New DataStore preference keys (string literals — must not change once shipped):

```
private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("settings_onboarding_completed")
private val AGE_CONFIRMED_KEY         = booleanPreferencesKey("settings_age_confirmed")
```

Both keys default to `false` when absent from the DataStore.

---

## 4. New Use Case: `BulkAssignApiProviderUseCase`

**Package:** `com.browntowndev.enclave.domain.usecase.onboarding`

**Purpose:** During onboarding, one API key and one discovered model ID should populate
all text-model slots in a single transactional call, so no subsequent Settings
configuration is required for basic chat.

**Interface:**

```kotlin
interface BulkAssignApiProviderUseCase {
    /**
     * Saves [credentials] + [apiKey] via [SaveApiCredentialsUseCase], creates one
     * [ApiModelConfiguration] for [modelId], then calls [SetDefaultModelUseCase] for
     * every slot in [targetSlots] pointing to the same configuration ID.
     *
     * Returns the persisted [ApiCredentialsId] on success.
     */
    suspend operator fun invoke(
        credentials: ApiCredentials,
        apiKey: String,
        modelId: String,
        displayName: String,
        targetSlots: List<ModelType> = TEXT_SLOTS,
    ): Result<ApiCredentialsId>

    companion object {
        /** The seven text-model slots that onboarding wires by default. */
        val TEXT_SLOTS: List<ModelType> = listOf(
            ModelType.MAIN,
            ModelType.FAST,
            ModelType.THINKING,
            ModelType.DRAFT_ONE,
            ModelType.DRAFT_TWO,
            ModelType.FINAL_SYNTHESIS,
            ModelType.VISION,
        )
    }
}
```

**Implementation class:** `BulkAssignApiProviderUseCaseImpl`

**Dependencies injected:**
- `SaveApiCredentialsUseCase`
- `SaveApiModelConfigurationUseCase`
- `SetDefaultModelUseCase`

**Algorithm:**

1. Call `SaveApiCredentialsUseCase(credentials, apiKey)` → `credentialsId`.
2. Build `ApiModelConfiguration(apiCredentialsId = credentialsId, modelId = modelId, displayName = displayName, isMultimodal = slot == VISION)` — note: `VISION` requires `isMultimodal = true`.
3. Call `SaveApiModelConfigurationUseCase(config)` → `configId`.
4. For each slot in `targetSlots`, call `SetDefaultModelUseCase(modelType = slot, localConfigId = null, apiConfigId = configId)`.
5. Return `Result.success(credentialsId)`.
6. Any exception propagates via `Result.failure`.

**Important:** The `VISION` slot in `SetDefaultModelUseCase` validates `isMultimodal == true`
at the domain layer. The `BulkAssignApiProviderUseCase` must create the configuration with
`isMultimodal = true` only for the VISION slot and `isMultimodal = false` for the other six
text slots, OR create two configurations (one multimodal, one not). The simplest correct
approach: create a single `ApiModelConfiguration` with `isMultimodal = false`, skip VISION
in `targetSlots` for the default case, and let callers opt-in to VISION separately if the
selected model is known to be multimodal.

**Revised default TEXT_SLOTS** (VISION excluded from the default list to avoid the
multimodal validation failure when the user picks a non-vision model):

```kotlin
val TEXT_SLOTS: List<ModelType> = listOf(
    ModelType.MAIN,
    ModelType.FAST,
    ModelType.THINKING,
    ModelType.DRAFT_ONE,
    ModelType.DRAFT_TWO,
    ModelType.FINAL_SYNTHESIS,
)
```

Callers that know the model is multimodal may pass a custom list that includes
`ModelType.VISION`.

---

## 5. `OnboardingUiState` Shape

```kotlin
data class OnboardingUiState(
    val currentStep: Int = 0,          // 0-3 matching wizard steps
    val ageConfirmed: Boolean = false,
    val selectedModelChoice: ModelChoice = ModelChoice.UNDECIDED,
    val selectedProvider: ApiProvider? = null,
    val apiKey: String = "",
    val discoveredModels: List<DiscoveredApiModel> = emptyList(),
    val selectedModelId: String = "",
    val isDiscoveringModels: Boolean = false,
    val discoveryError: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val ttsProviderDraft: TtsProviderDraft? = null,
    val mediaProviderDraft: MediaProviderDraft? = null,
    val isTtsConfigured: Boolean = false,
    val isMediaConfigured: Boolean = false,
)

enum class ModelChoice { UNDECIDED, LOCAL, API }
```

All fields are immutable. Business state in ViewModel; ephemeral UI state (dropdown expanded,
focus) in composable `remember` locals.

---

## 6. `OnboardingEvent` Shape

```kotlin
sealed interface OnboardingEvent {
    data object NavigateToChat : OnboardingEvent
}
```

Exposed from ViewModel as `val events: Flow<OnboardingEvent> = _events.receiveAsFlow()`.

---

## 7. `OnboardingViewModel` Responsibilities

Constructor parameters (all via Hilt `@Inject`):
- `settingsRepository: SettingsRepository`
- `saveApiCredentialsUseCase: SaveApiCredentialsUseCase`
- `bulkAssignApiProviderUseCase: BulkAssignApiProviderUseCase`
- `discoverApiModelsUseCase: DiscoverApiModelsUseCase`
- `saveTtsProviderUseCase: SaveTtsProviderUseCase`
- `saveMediaProviderUseCase: SaveMediaProviderUseCase`
- `loggingPort: LoggingPort`

Public API (all on `viewModelScope`):

| Function | Triggered by |
|----------|-------------|
| `onAgeConfirmedChange(confirmed: Boolean)` | Checkbox toggle on step 1 |
| `onNextStep()` | "Continue" button — validates current step, advances `currentStep` |
| `onPreviousStep()` | Back button — decrements `currentStep` (no-op at step 0) |
| `onModelChoiceSelected(choice: ModelChoice)` | Large card tap on step 2 |
| `onProviderSelected(provider: ApiProvider)` | Provider picker on step 2b |
| `onApiKeyChanged(key: String)` | Text field on step 2b |
| `onDiscoverModels()` | "Discover" button on step 2b — calls `DiscoverApiModelsUseCase` |
| `onModelSelected(modelId: String)` | Model dropdown selection on step 2b |
| `onSkipMedia()` | "Skip" on step 3 |
| `onSaveTtsProvider(draft: TtsProviderDraft)` | TTS save on step 3 |
| `onSaveMediaProvider(draft: MediaProviderDraft)` | Media save on step 3 |
| `onCompleteOnboarding()` | "Start using Enclave" on step 4 — saves flags, emits `NavigateToChat` |

**State flow:** `val uiState: StateFlow<OnboardingUiState>` with `stateIn(WhileSubscribed(5_000), SharingStarted.WhileSubscribed(5_000), OnboardingUiState())`.

**`onNextStep()` validation rules:**
- Step 0: `ageConfirmed` must be `true`. If false, no-op (button stays disabled in UI).
- Step 1 (Model Choice): if `LOCAL`, advance; if `API`, `selectedProvider != null` and `selectedModelId` non-blank are required.
- Step 2 (Media): always allowed to advance (step is optional).
- Step 3: calls `onCompleteOnboarding()`.

**`onCompleteOnboarding()` sequence:**
1. `settingsRepository.updateAgeConfirmed(true)`
2. If `selectedModelChoice == API`: call `bulkAssignApiProviderUseCase(…)` — failure updates `saveError`.
3. Persist TTS/media if configured.
4. `settingsRepository.updateOnboardingCompleted(true)` — last step so a crash before this re-shows the wizard.
5. Emit `OnboardingEvent.NavigateToChat`.

---

## 8. `MainViewModel` Changes

After vault unlock succeeds (in `initializeModelsIfNeeded()` and in `initializeApp()`):
read the `onboardingCompleted` flag from `settingsRepository.settingsFlow.first()`.

**Change to `AppStartupState.Ready.initialRoute`:**

```kotlin
val initialRoute: String
    get() = when {
        !onboardingCompleted -> Routes.ONBOARDING
        initializationError != null || modelsResult?.modelsToDownload?.isNotEmpty() == true ->
            Routes.MODEL_DOWNLOAD
        else -> Routes.CHAT
    }
```

Add `onboardingCompleted: Boolean = false` to `AppStartupState.Ready`.

The flag is read once during `collectStartupState` from `settingsRepository.settingsFlow.first()`.

---

## 9. Navigation

### `Routes.kt`

```kotlin
const val ONBOARDING = "onboarding"
```

### `OnboardingNavGraph.kt` (new file in `app/…/navigation/`)

```kotlin
fun NavGraphBuilder.onboardingNavGraph(onOnboardingComplete: () -> Unit) {
    composable(Routes.ONBOARDING) {
        OnboardingRoute(onOnboardingComplete = onOnboardingComplete)
    }
}
```

### `EnclaveNavGraph.kt` additions

Add the graph:
```kotlin
onboardingNavGraph(
    onOnboardingComplete = {
        navController.navigate(Routes.CHAT) {
            popUpTo(Routes.ONBOARDING) { inclusive = true }
        }
    }
)
```

---

## 10. UI Design Rules

All steps share:
- Neo-brutalist theme: `neoSurface(…)`, `neoBorderColor()`, `NeoPrimaryButton`, `NeoOutlinedTextField`.
- Colors from `MaterialTheme.colorScheme` only — no hardcoded hex values except shield-red / success-green semantics.
- Dark-first AMOLED black background (`MaterialTheme.colorScheme.background`).
- `sp` units for all text.
- All interactive elements have `contentDescription` and appropriate `Semantics` roles per `ARCHITECTURE_RULES.md §6`.
- Age-gate checkbox: `Modifier.semantics { role = Role.Checkbox }` and `contentDescription = "I confirm I am 18 or older"`.
- Every composable file has Light + Dark `@Preview` functions with fake data (no Hilt).
- `OnboardingScreen` owns its own `Scaffold` + no `paddingValues` threading.

### Step 2 — Model Choice

Two large selection cards:
- Card shape matches `neoSurface` with `borderColor` switching to `MaterialTheme.colorScheme.primary` when selected.
- "Use Local Models" card: icon + title + body "Runs entirely on your device. No API key needed."
- "Use an API Provider" card: icon + title + body "Connect to a cloud provider for richer capabilities."
- Below the cards: note text "You can always change this later in Settings." (`MaterialTheme.typography.bodySmall`).

When "Use an API Provider" is selected, a provider picker row appears below the note. Provider
order matches `ApiProvider` enum: `ANTHROPIC, OPENAI, OPENROUTER, XAI, GOOGLE`. Below the
picker, `NeoApiKeyField` for the key, then "Discover Models" `TextButton`. Model dropdown appears
once discovery succeeds. `OpenRouter` is shown as the recommended provider with a "(Recommended)"
label suffix.

### Step 3 — Optional Media

"Want image, video, or TTS?" title. Three option rows:
- TTS (via xAI)
- Image/Video (via xAI)
- Music (via Google)

Each row shows a brief description. Tapping "Configure" expands an inline form (or shows a
bottom-sheet pattern matching existing `ByokConfigureForms` usage). A "Skip" button bypasses
the entire step.

---

## 11. Hilt Module: `OnboardingModule`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingModule {
    @Binds
    @Singleton
    abstract fun bindBulkAssignApiProviderUseCase(
        impl: BulkAssignApiProviderUseCaseImpl
    ): BulkAssignApiProviderUseCase
}
```

Lives in `feature/onboarding/src/main/kotlin/…/OnboardingModule.kt`.

---

## 12. `settings.gradle.kts` Addition

```kotlin
include(":feature:onboarding")
```

---

## 13. `feature/onboarding/build.gradle.kts`

Mirrors `:feature:settings` build file with the following key differences:
- `namespace = "com.browntowndev.enclave.feature.onboarding"`
- Dependencies: `:core:domain`, `:core:ui` — **no** `:core:data`, **no** `:feature:settings`.
- All data access goes through domain use-case interfaces injected by Hilt from `:app`.

---

## 14. Ordered Implementation Tasks

Tasks are numbered in dependency order. Each references the scenario IDs from `test_spec.md`
that it satisfies.

| # | Task | Scenarios satisfied |
|---|------|---------------------|
| 1 | Add `onboardingCompleted: Boolean = false` and `ageConfirmed: Boolean = false` to `SettingsData` | OB-01, OB-12 |
| 2 | Add `updateOnboardingCompleted` and `updateAgeConfirmed` to `SettingsRepository` interface | OB-01, OB-12 |
| 3 | Implement both methods in `SettingsRepositoryImpl`; add DataStore keys | OB-01, OB-12, OB-13 |
| 4 | Update `FakeSettingsRepository` (testFixtures) to implement both new methods | all ViewModel tests |
| 5 | Create `BulkAssignApiProviderUseCase` interface + `BulkAssignApiProviderUseCaseImpl` in `:core:domain` | OB-05, OB-06, OB-07, OB-14 |
| 6 | Add `OnboardingModule` Hilt binding in `:feature:onboarding` | wiring |
| 7 | Create `OnboardingUiState`, `ModelChoice`, `OnboardingEvent` in `:feature:onboarding` | all ViewModel tests |
| 8 | Create `OnboardingViewModel` with full state machine | OB-02, OB-03, OB-04, OB-05, OB-06, OB-07, OB-08, OB-09, OB-10, OB-11, OB-14, OB-15, OB-17 |
| 9 | Write `OnboardingViewModelTest` (all scenarios) | OB-02 … OB-15 |
| 10 | Write `BulkAssignApiProviderUseCaseTest` | OB-05, OB-06, OB-07, OB-14 |
| 11 | Add `onboardingCompleted: Boolean` to `AppStartupState.Ready` and update `initialRoute` computation | OB-01, OB-13 |
| 12 | Modify `MainViewModel.initializeApp()` and `collectStartupState` to read `onboardingCompleted` | OB-01, OB-13 |
| 13 | Add `Routes.ONBOARDING` constant | OB-01 |
| 14 | Create `OnboardingNavGraph.kt` in `:app` | OB-01 |
| 15 | Register `onboardingNavGraph` in `EnclaveNavGraph.kt` | OB-01 |
| 16 | Register `:feature:onboarding` in `settings.gradle.kts` | build |
| 17 | Create composable files: `OnboardingRoute`, `OnboardingScreen`, `WelcomeAgeGateStep`, `ModelChoiceStep`, `MediaStep`, `CompletionStep` with Light + Dark previews | OB-02, OB-03, OB-04, OB-08, OB-09 |
| 18 | Add accessibility annotations (`contentDescription`, `Role`) on age-gate checkbox and provider selection cards | OB-16 |

---

## 15. Drift and Escalation Rules

- **Scope creep:** Any request to modify `ByokConfigureScreen` from `:feature:settings` must be rejected. Shared UI primitives needed by both modules belong in `:core:ui`.
- **Circular dependency:** If a reviewer requests `:feature:onboarding` to depend on `:feature:settings`, reply with the contract-violation response and request a Contract Waiver.
- **DataStore key collision:** If `settings_onboarding_completed` or `settings_age_confirmed` collides with a future key, the existing key name must not be changed without a migration plan.
- **VISION slot validation:** `SetDefaultModelUseCase` enforces `isMultimodal = true` for the VISION slot. Any implementation that passes `isMultimodal = false` to the VISION slot will throw `IllegalArgumentException`. If onboarding adds VISION to the bulk-assign list, the configuration passed must have `isMultimodal = true`.
- **Escalation:** If the `DiscoverApiModelsUseCase` HTTP layer needs changes to support onboarding (e.g., new timeout), escalate to the maintainer and do not modify it unilaterally.

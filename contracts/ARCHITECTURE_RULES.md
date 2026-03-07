# ARCHITECTURE_RULES.md
**Pocket Crew – Structural Architecture Contract v2.4**

Non-negotiable architectural rules. Always enforce together with relevant companion contracts.

---

## 0. 2026 Platform Compliance Anchor (Technical Anchor)
All code generation must prioritize 2026 OS constraints:
- **6-Hour Limit:** `dataSync` foreground services have a 6-hour cumulative timeout. Workers must be resumable.
- **State Integrity:** WorkManager `Data` persistence is the sole bridge for background-to-UI state.
- **API 36 UX:** Standard notifications are legacy; `ProgressStyle` is the primary interface for active journeys.

**Clean Architecture Supremacy Rule (ZERO TOLERANCE – HIGHEST PRIORITY)**

This project **strictly and exclusively follows Clean Architecture** (Uncle Bob / Robert C. Martin – Hexagonal / Ports-and-Adapters pattern). 

**The agent MUST NEVER:**
- Write, propose, or allow any code that violates the dependency rule.
- Leak Android, Compose, Hilt, Room, WorkManager, LiteRT, or any framework/SDK types into `:domain`.
- Bypass module boundaries, forbidden imports, or layer responsibilities.
- Use any other architectural pattern (MVVM-only, monolithic, etc.).

**Violation = immediate contract breach.**  
Reply with exactly: "Contract violation detected in [file]. Request explicit 'Contract Waiver' from user."  
Then stop. Do not generate code until waived.

All rules below are non-negotiable implementations of Clean Architecture.

---

## Strict Boundaries Expansion
| Always | Never |
|--------|-------|
| Route background progress via `WorkManager.setProgress`. | Use singleton callbacks or static listeners for background-to-UI updates. |
| Use `ExistingWorkPolicy.KEEP` or `REPLACE` for unique work. | Scatter multiple unmanaged `OneTimeWorkRequests` for the same task. |
| Implement byte-offset resumption for IO tasks. | Assume a background task will run to completion in a single session. |
| Route navigation via pure callbacks hoisted to the route level. | Pass `NavController` into Composables. |
| Expose a single `StateFlow<UiState>` from ViewModels. | Expose mutable flows or Android framework types from ViewModels. |
| Ensure each screen owns its own `Scaffold` and `TopAppBar`. | Thread `paddingValues` down the UI tree from a root-level Scaffold. |
| Consume one-shot events via `Channel<Event>` and `LaunchedEffect`. | Use `SharedFlow` for one-shot UI events (can cause dropped events). |
| Use stable keys on all `LazyColumn` and `LazyRow` items. | Import `:app`, `:data`, or framework code into `:domain`. |
| Enforce 100% Clean Architecture dependency inversion at all times | Allow any framework code in `:domain` or reverse dependencies |
| `:domain` contains ONLY pure Kotlin models, repository interfaces, use cases | Ever import Android/Compose/Hilt/Room/LiteRT in `:domain` |
| All other rules in this file | Deviate from the exact module dependency graph shown below |

## 1. Module Boundaries


Modules: `:app`, `:domain`, `:data`, `:inference`

### 1.1 Dependency Direction (Strict)

```text
:app       → :domain, :data, :inference
:data      → :domain
:inference → :domain
:domain    → nothing
```

No other directions allowed.

### 1.2 Forbidden Imports

| Module | Must NOT import |
|--------|----------------|
| `:domain` | Android SDK, Compose, Hilt, Room, any framework types |
| `:data` | Compose |
| `:inference` | Compose |

All UI code lives exclusively in `:app`.

---

## 2. Layer Responsibilities

- **`:domain`** — Pure Kotlin only. Models, repository interfaces, use cases. Zero framework code.
- **`:data`** — Implements repository interfaces from `:domain`. Persistence, mapping, network. No UI.
- **`:inference`** — Implements engine abstractions from `:domain`. Model loading, generation, routing. No UI.
- **`:app`** — ViewModels, Composables, Navigation, Theme, DI wiring. ViewModels depend only on `:domain` abstractions.

---

## 3. Presentation State Contract


### 3.1 UI State
- Immutable `data class`. All updates via `copy()`.
- No mutable fields. No Android types inside UI state.
- One `StateFlow<UiState>` per ViewModel.

### 3.2 One-Shot Events
- Use `Channel<Event>(Channel.BUFFERED)` exposed as `Flow<Event>` via `receiveAsFlow()`.
- Consumed in `LaunchedEffect(Unit)` at the screen level.

### 3.3 Model Mapping
- Domain models MUST NOT be passed directly to composables. ViewModel maps domain → presentation.
- No repository calls inside composables.

---

## 4. Navigation & Scaffold Architecture

Each screen owns its own `Scaffold`, `TopAppBar`, and `ViewModel`. There is no global/root `Scaffold`.

```kotlin
// ChatRoute.kt — wires ViewModel to Screen
@Composable
fun ChatRoute(onNavigateToHistory: () -> Unit) {
    val viewModel: ChatViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChatScreen(uiState = uiState, onNavigateToHistory = onNavigateToHistory)
}
```
- **No `DynamicTopBar`.** Each screen composes its own TopAppBar.
- **No `paddingValues` threading.** Each Scaffold consumes its own inner padding locally.

---

## 5. IME / Keyboard Handling

Canonical pattern for chat screens (anchored to prevent keyboard layout thrash):

```kotlin
@Composable
fun ChatScreen(uiState: ChatUiState, onNavigateToHistory: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0) // prevent Scaffold from consuming IME
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Box(Modifier.weight(1f)) { MessageList(...) }
            InputBar(...) 
        }
    }
}
```
- Manifest: `android:windowSoftInputMode="adjustResize"` on `MainActivity`.
- Setup: `enableEdgeToEdge()` must be called in `MainActivity.onCreate()`.

---

## 6. Accessibility

- All interactive elements must have `contentDescription` + appropriate semantic `role`.
- Use `sp` units for text. System font scaling must be respected.
- TalkBack must work flawlessly across all custom components.

---

## 7. Preview Requirements

Every composable file MUST include previews for:
- Light + Dark themes
- Empty state (where applicable)
- Populated / long content
- Loading / thinking state (where applicable)

*Constraint:* Preview composables must NOT reference Hilt or real ViewModels. Use fake presentation models.

---

## 8. Performance & Targets

- **Target:** Android 14+ (minSdk 34, targetSdk latest stable).
- **Stack:** Jetpack Compose 2026 stable, Material 3 only, Hilt for DI, Kotlin Coroutines + Flow for async.
- No blocking work in composables.
- No layout thrash on keyboard open/close.
- Typing in the input bar MUST NOT recompose the message list.

---

## 9. Executable Validation (Agent MUST run before TASK_STATUS: COMPLETE)

1. **Check Domain Purity:** Run `gradlew.bat :domain:assemble`. It must pass without Android/Compose dependencies.
2. **Check App Compilation:** Run `gradlew.bat :app:assembleDebug`. It must pass.
3. **Verify App Module Imports:** Run `gradlew.bat :app:ktlintCheck` to ensure formatting and import rules hold.

### 9.1 Rigor Validation (Pre-Completion)
Before declaring `TASK_STATUS: COMPLETE`, the agent must verify:
- **Zero-State Resilience:** Does the UI handle the exact moment of transition from `Status.IDLE` to `Status.CHECKING` without flickering?
- **State Leakage:** Does the `StateFlow` properly survive Activity destruction while the Worker is active?
- **Visual Purity:** Ensure M3 `LinearProgressIndicator` parameters (`gapSize`, `drawStopIndicator`) are explicitly set to avoid primary color leakage on the canvas.

## 10. Sequential Reasoning: 2026 Logic Audit
Before implementing any background or state-heavy task, the agent must perform the following internal audit:

1. **Quota Check:** Is this task likely to exceed the 6-hour `dataSync` limit? If yes, implement `Result.retry()` and byte-range resumption.
2. **Exemption Check:** Is the worker triggered by a `User-Initiated` action to ensure foreground service eligibility?
3. **UI Bridge Check:** Am I using `WorkManager.Data` to pipe progress? (Verify no custom callbacks are used).
4. **API Style Check:** Does the notification utilize `ProgressStyle` for API 36+?

**Mandate:** If any of these checks fail, the agent must correct the plan before writing the first line of Kotlin code.
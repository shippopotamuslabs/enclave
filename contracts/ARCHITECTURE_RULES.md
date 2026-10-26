# ARCHITECTURE_RULES.md
**Enclave – Structural Architecture Contract v2.4**

Non-negotiable architectural rules. Always enforce together with relevant companion contracts.

---

## 0. 2026 Platform Compliance Anchor (Technical Anchor)
All code generation must prioritize 2026 OS constraints:
- **6-Hour Limit:** `dataSync` foreground services have a 6-hour cumulative timeout. Workers must be resumable.
- **State Integrity:** WorkManager `Data` persistence is the sole bridge for background-to-UI state.
- **API 36 UX:** Standard notifications are legacy; `ProgressStyle` is the primary interface for active journeys.

**Clean Architecture Supremacy Rule (ZERO TOLERANCE – HIGHEST PRIORITY)**

Canon: `contracts/gospel/ARCHITECTURE-GOSPEL.md` §0. The full rule — Clean
Architecture / ports-and-adapters, dependencies inward toward `:core:domain`,
and the MUST NEVER list — lives in the gospel. This contract no longer embeds it.

**Violation = immediate contract breach.**  
Reply with exactly: "Contract violation detected in [file]. Request explicit 'Contract Waiver' from user."  
Then stop. Do not generate code until waived.

All rules below are non-negotiable implementations of Clean Architecture.

---

## Strict Boundaries Expansion

The Clean Architecture and presentation rows of this table are canon —
`contracts/gospel/ARCHITECTURE-GOSPEL.md` (Strict Boundaries). The table below is
Enclave's expansion of that canon: its platform rows (WorkManager, byte-offset
resumption) and the exact module-graph rule are local.

| Always | Never |
|--------|-------|
| Route background progress via `WorkManager.setProgress`. | Use singleton callbacks or static listeners for background-to-UI updates. |
| Use `ExistingWorkPolicy.KEEP` or `REPLACE` for unique work. | Scatter multiple unmanaged `OneTimeWorkRequests` for the same task. |
| Implement byte-offset resumption for IO tasks. | Assume a background task will run to completion in a single session. |
| Route navigation via pure callbacks hoisted to the route level. | Pass `NavController` into Composables. |
| Expose a single `StateFlow<UiState>` from ViewModels. | Expose mutable flows or Android framework types from ViewModels. |
| Ensure each screen owns its own `Scaffold` and `TopAppBar`. | Thread `paddingValues` down the UI tree from a root-level Scaffold. |
| Consume one-shot events via `Channel<Event>` and `LaunchedEffect`. | Use `SharedFlow` for one-shot UI events (can cause dropped events). |
| Use stable keys on all `LazyColumn` and `LazyRow` items. | Import `:app`, feature, data, UI, or native bridge code into `:core:domain`. |
| Enforce Clean Architecture dependency inversion at module boundaries. | Allow reverse dependencies into `:core:domain`. |
| Keep `:core:domain` APIs as domain models, ports, use cases, and test fixtures. | Expose Android/Compose/Hilt/Room/LiteRT types from domain APIs. |
| All other rules in this file | Deviate from the exact module dependency graph shown below |

## 1. Module Boundaries


Primary modules: `:app`, `:core:domain`, `:core:data`, `:core:ui`, `:core:testing`,
`:core:agent`, `:core:docgen`, `:feature:*`, `:llama-android`, `:whisper-android`, and
`:detekt-rules` (JVM-only static analysis; tracked in ENC-10, not an app runtime dependency).

### 1.1 Dependency Direction (Strict)

The dependency-direction principle is canon: `contracts/gospel/ARCHITECTURE-GOSPEL.md` §1. The gospel module template (§6) owns the canonical layering — outbound ports live in the application layer (§4) and `:core:data` is a legacy label for the infrastructure modules, not a canonical peer layer. Enclave's exact module graph (the local instantiation):

```text
:app                 → :core:domain, :core:data, :core:ui, :core:agent, :core:docgen, :feature:*
:feature:*           → :core:domain and only the core/feature modules declared in that module's Gradle file
:core:data           → :core:domain, :core:docgen
:core:ui             → :core:domain
:core:agent          → :core:domain
:core:docgen         → :core:domain
:feature:inference   → :core:domain, :llama-android
:llama-android       → native C/C++ wrapper
:whisper-android     → :core:domain plus native C/C++ wrapper
:core:domain         → no app, feature, data, UI, or native bridge modules
:core:testing        → :core:domain only (fakes, MainDispatcherRule, fixtures)
:detekt-rules        → isolated (Detekt API only; not a runtime dependency)
```

**Feature dependency tightening (ENC-2 target):** `:feature:*` depends on `:core:domain`
and `:core:ui` only. Direct `:core:data` imports in feature code are forbidden — data
implementations are wired in `:app` DI behind domain ports/use cases. Existing direct
imports are remediation debt under ENC-11/ENC-12, not precedent.

No reverse dependencies into `:core:domain` are allowed.

### 1.2 Forbidden Imports

The forbidden-import principles are canon: `contracts/gospel/ARCHITECTURE-GOSPEL.md` §2.
Enclave's module table:

| Module | Must NOT import |
|--------|----------------|
| `:core:domain` | App, feature, data, UI, native bridge modules, Android UI/framework types in public APIs, Compose, Hilt, Room, LiteRT |
| `:core:data` | Compose UI |
| `:feature:inference` | Compose UI |

Reusable UI code lives in `:core:ui`; feature UI lives in feature modules and is
wired by `:app`.

---

## 2. Layer Responsibilities

Layer responsibilities are canon: `contracts/gospel/ARCHITECTURE-GOSPEL.md` §3
(see also its §3.1, domain model policy), read with §4 (Port Ownership). Enclave's layer map:

- **`:core:domain`** — Domain models, `*Port` interfaces, use cases, and test fixtures. (Canon vocabulary, gospel §4: outbound ports are application-layer `*Port` interfaces; Enclave's port interfaces currently live in this module.) Keep domain APIs free of Android/UI/storage/native implementation types.
- **`:core:data`** — Infrastructure: implements the `*Port` interfaces (implementation classes may carry the `*Repository` name, gospel §4), Room persistence, network/Firebase integration, WorkManager, and entity/domain mapping. No Compose UI.
- **`:feature:inference`** — Implements model/provider inference abstractions from domain. No Compose UI.
- **`:core:ui` and `:feature:*`** — Compose UI and presentation logic. ViewModels map domain to presentation models.
- **`:app`** — Application shell, navigation wiring, DI composition, app resources, and platform entrypoints.

---

## 3. Presentation State Contract


### 3.1 UI State
Canon: `contracts/gospel/ARCHITECTURE-GOSPEL.md` §12.1 (UI State).

### 3.2 One-Shot Events
Canon: `contracts/gospel/ARCHITECTURE-GOSPEL.md` §12.2 (One-Shot Events).

### 3.3 Model Mapping
Canon: `contracts/gospel/ARCHITECTURE-GOSPEL.md` §12.3 (Model Mapping).

### 3.4 ViewModel Dependencies & Flow Composition (ENC-2)
Canon: `contracts/gospel/ARCHITECTURE-GOSPEL.md` §12.4 (Driving-Adapter Dependencies & Flow Composition).

- Enforced by Detekt `enclave` rules `ForbiddenRepositoryInViewModel`, `ForbiddenPortInViewModel`, `ForbiddenDomainTypeInComposable`, `ForbiddenDomainTypeInUiState`, `ForbiddenUseCaseInFeatureMapper` (ENC-10). `./gradlew detekt` must pass at `maxIssues: 0`; do not add suppressions to hide layering debt — file an ENC sub-issue instead.

### 3.5 Linear Ticketing
All implementation work follows `AGENTS.md` §3.7 (Linear team `ENC`, chunk-based trunk workflow, one MR per sub-issue, `Fixes ENC-xx` in the MR description). Architecture changes without a ticket are out of contract.

---

## 4. Navigation & Scaffold Architecture

Canon: `contracts/gospel/ARCHITECTURE-GOSPEL.md` §13 (Navigation & Scaffold Architecture).

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

Canon: `contracts/gospel/ARCHITECTURE-GOSPEL.md` §14 (Accessibility & Previews).

---

## 7. Preview Requirements

Canon: `contracts/gospel/ARCHITECTURE-GOSPEL.md` §14 (Accessibility & Previews).

---

## 8. Performance & Targets

- **Target:** Android 14+ (minSdk 34, targetSdk latest stable).
- **Stack:** Jetpack Compose 2026 stable, Material 3 only, Hilt for DI, Kotlin Coroutines + Flow for async.
- No blocking work in composables.
- No layout thrash on keyboard open/close.
- Typing in the input bar MUST NOT recompose the message list.

---

## 9. Executable Validation (Agent MUST run before TASK_STATUS: COMPLETE)

1. **Check Domain Module:** Run `./gradlew :core:domain:assembleDebug`.
2. **Check App Compilation:** Run `./gradlew :app:assembleDebug`.
3. **Verify Imports/Style:** Run `./gradlew ktlintCheck` for the changed modules, or the root task before merge.

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
# CODE_STYLE_RULES.md
**Enclave – Code Style Contract v2.4**

Kotlin and Compose idioms, naming, formatting, and stability rules.

The shared rules are canon in `contracts/gospel/CODE-STYLE-GOSPEL.md` (git
submodule, gospels repo pinned at tag `v1`). This contract keeps Enclave's
example tables, enforcement lines, and validation gates; sections below that
only restated the canon are now pointers.

---

## Strict Boundaries

Canon: `contracts/gospel/CODE-STYLE-GOSPEL.md` (Strict Boundaries). Enclave addition:

| Always | Never |
| :--- | :--- |
| Use `Modifier.animateBounds()` within `LookaheadScope` for dynamic layout changes. | Use legacy `animateContentSize` for complex structural changes. |

## 1. Kotlin Conventions

Canon: `contracts/gospel/CODE-STYLE-GOSPEL.md` §1.

## 2. Naming

Canon: `contracts/gospel/CODE-STYLE-GOSPEL.md` §2. Enclave's example table:


| Element | Convention | Example |
|---------|-----------|---------|
| Package | lowercase dot-separated | `com.browntowndev.enclave.presentation.screen.chat` |
| Class / Object | PascalCase | `ChatViewModel`, `Routes` |
| Function | camelCase, verb-first | `onSendMessage()`, `calculateTopPadding()` |
| Composable | PascalCase (noun) | `MessageBubble()`, `InputBar()` |
| Property | camelCase | `isThinking`, `selectedMode` |
| Constant | UPPER_SNAKE_CASE | `MAX_REFINEMENT_ROUNDS` |
| Private backing flow | `_uiState` pattern | `private val _uiState = MutableStateFlow(...)` |
| Resource IDs | snake_case | `R.drawable.attach_file`, `R.string.mode_auto` |
| Domain Ports | Port suffix on the interface | 'MyNewDomainPort' |

## 3. File Organization

Canon: `contracts/gospel/CODE-STYLE-GOSPEL.md` §3.

## 4. Compose-Specific Rules

### 4.1 Stability
Canon: `contracts/gospel/CODE-STYLE-GOSPEL.md` §4.1.

### 4.2 Modifier Conventions
Canon: `contracts/gospel/CODE-STYLE-GOSPEL.md` §4.2.

### 4.3 State Hoisting
Canon: `contracts/gospel/CODE-STYLE-GOSPEL.md` §4.3.

### 4.4 Lambdas
Canon: `contracts/gospel/CODE-STYLE-GOSPEL.md` §4.4.

### 4.5 Rigor Protocols
Canon: `contracts/gospel/CODE-STYLE-GOSPEL.md` §4.5.

### 4.6 Plausible Composition
Canon: `contracts/gospel/CODE-STYLE-GOSPEL.md` §4.6.

## 5. Coroutines & Flow

Canon: `contracts/gospel/CODE-STYLE-GOSPEL.md` §5.

## 6. KDoc & Comments

Canon: `contracts/gospel/CODE-STYLE-GOSPEL.md` §6.

## 7. Formatting

Canon: `contracts/gospel/CODE-STYLE-GOSPEL.md` §7.

## 8. Testing Conventions

Canon: `contracts/gospel/CODE-STYLE-GOSPEL.md` §8 (JUnit 5 + Turbine + hand-written fakes; Compose UI tests with
`createComposeRule()`; test naming; fakes placement). Enclave specifics:

- Unit tests: JUnit 5 + Turbine for Flow testing + **fakes from `:core:testing`** for Enclave-owned ports (see `UNIT_TESTING.md`). MockK only for third-party SDK boundaries with `@Suppress` + justification; no `verify` in ViewModel tests.
- Architecture/style is machine-enforced by the `enclave` Detekt rule set (`:detekt-rules`, ENC-2): ViewModels use-case-only, no domain types in composables/UiState, no raw Material/hex in features. `./gradlew detekt` must pass at `maxIssues: 0`.
- All work is ticketed in Linear (team `ENC`) per `AGENTS.md` §3.7 — one MR per sub-issue, `Fixes ENC-xx` in the MR description.

### 8.1 Test Coverage Discipline (Agent Mandate)

Canon: `contracts/gospel/CODE-STYLE-GOSPEL.md` §8.1. Enclave's mandate:


- Whenever the task **adds** new production code (new classes, public functions, significant logic in `:core:domain`, `:core:data`, `:feature:inference`, ViewModels, repositories, mappers, use cases, etc.) **or modifies** existing code, the agent **MUST**:
  - Write new unit tests (or extend existing ones) in the matching `src/test/kotlin` source set that cover the new/modified behavior, main paths, edge cases, and error conditions.
  - Use JUnit 5 + Turbine (for `StateFlow`/`Flow`) + fakes from `:core:testing` as defined above and in `UNIT_TESTING.md`.
- For pure UI/Compose changes that affect behavior or interactions: also add or update Compose UI tests using `createComposeRule()` and semantic matchers.
- Trivial styling-only or preview-only changes are exempt from new tests, but the full test suite must still pass.
- Before declaring `TASK_STATUS: COMPLETE`, the agent must have written/updated the tests **and** executed them successfully.

---

## 9. Executable Validation (Agent MUST run before TASK_STATUS: COMPLETE)

1. **Check Formatting:** Run `./gradlew ktlintCheck` or a module-scoped ktlint task when iterating. Fix any reported styling errors.
2. **Run Tests:** Run the narrowest affected test task, e.g. `./gradlew :feature:inference:testDebugUnitTest --tests "*ChangedClass*"`, then broaden to `./gradlew testDebugUnitTest` before merge-sized changes.
3. **Run Build:** Run the narrowest affected assemble task, e.g. `./gradlew :app:assembleDebug` or `./gradlew :core:data:assembleDebug`.

## 10. 2026 Exception & Notification Discipline

### 10.1 Worker Exception Handling
Canon: `contracts/gospel/CODE-STYLE-GOSPEL.md` §9 (Worker Exception Handling).

### 10.2 Notification Few-Shot (API 36 Compliance)
**Correct Implementation:**
```kotlin
val builder = NotificationCompat.Builder(context, CHANNEL_ID)
if (Build.VERSION.SDK_INT >= 36) {
    builder.setStyle(Notification.ProgressStyle().setProgress(progress).setStyledByProgress(true))
} else {
    builder.setProgress(100, progress, false)
}```

*Incorrect Implementation**
```kotlin
// Legacy: Will be deprioritized in the 2026 System UI
builder.setProgress(100, progress, false)
builder.setContentText("$progress% complete")
```
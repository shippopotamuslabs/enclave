# CODE_STYLE_RULES.md
**Pocket Crew – Code Style Contract v2.4**

Kotlin and Compose idioms, naming, formatting, and stability rules.

---

## Strict Boundaries

| Always | Never |
| :--- | :--- |
| Use `val` over `var` and immutable collections by default. | Use `!!` (non-null assert) without `requireNotNull()`. |
| Accept `modifier: Modifier = Modifier` as the **last** parameter. | Apply the passed `modifier` to an inner element instead of the outermost layout. |
| Use `kotlinx.collections.immutable` in UI state classes. | Scatter `@Stable` or `@Immutable` preemptively without profiling. |
| Prefer method references (`::onModeChange`) for callbacks. | Create new lambda instances inside `LazyColumn` or `LazyRow` item blocks. |
| Use `Modifier.animateBounds()` within `LookaheadScope` for dynamic layout changes. | Use legacy `animateContentSize` for complex structural changes. |
| Use `stateIn` with `WhileSubscribed(5_000)` for ViewModel state. | Manually `.collect` and `.update` state inside `viewModelScope.launch`. |
| Use `collectAsStateWithLifecycle()` in Compose. | Use `collectAsState()`, `GlobalScope`, or `runBlocking`. |
| Use `derivedStateOf` for state that depends on other state. | Perform heavy calculations or filtering directly in the Composable body. |
| Prefer Slot APIs (`content: @Composable () -> Unit`) for layout flexibility. | Pass 10+ specific data parameters into a single "God Composable." |
| Hoist business state to the ViewModel. | Hoist ephemeral UI state (focus, animation) out of Compose unnecessarily. |
| Use `CompositionLocal` only for truly cross-cutting themes/configs. | Use `CompositionLocal` to pass data that should be explicitly passed via parameters. |
| Comment the "why" when logic is non-obvious. | Comment the "what" or leave `// TODO` comments in merged code. |
| Use `state.update { }` to update `MutableStateFlow` | Use `state.value =` |

## 1. Kotlin Conventions

- Kotlin 2.x with K2 compiler.
- Explicit return types on all public functions and properties.
- `val` over `var`. Immutable collections by default.
- `when` over `if-else` chains for 3+ branches.
- No `!!` (non-null assert). Use `requireNotNull()` with a message, or handle nulls explicitly.
- Extension functions for repeated utility logic; keep them in focused files, not scattered.
- Named arguments on any call with 3+ parameters or where meaning is ambiguous.

## 2. Naming

| Element | Convention | Example |
|---------|-----------|---------|
| Package | lowercase dot-separated | `com.browntowndev.pocketcrew.presentation.screen.chat` |
| Class / Object | PascalCase | `ChatViewModel`, `Routes` |
| Function | camelCase, verb-first | `onSendMessage()`, `calculateTopPadding()` |
| Composable | PascalCase (noun) | `MessageBubble()`, `InputBar()` |
| Property | camelCase | `isThinking`, `selectedMode` |
| Constant | UPPER_SNAKE_CASE | `MAX_REFINEMENT_ROUNDS` |
| Private backing flow | `_uiState` pattern | `private val _uiState = MutableStateFlow(...)` |
| Resource IDs | snake_case | `R.drawable.attach_file`, `R.string.mode_auto` |
| Domain Ports | Port suffix on the interface | 'MyNewDomainPort' |

## 3. File Organization

- One public top-level composable per file.
- Helper composables and preview composables go below the main public composable as `private`.

## 4. Compose-Specific Rules


### 4.1 Stability
- UI state classes: `data class` (stable by default when all fields are stable types).
- If a class contains `List`, `Map`, or other mutable collection types, either:
  - Use `kotlinx.collections.immutable` (`ImmutableList`, `PersistentList`), OR
  - Annotate with `@Immutable` / `@Stable` if you guarantee no mutation.
- **Do not scatter `@Stable` annotations preemptively.** Only add when profiling confirms unnecessary recomposition from that specific type.

### 4.2 Modifier Conventions
- Every composable that emits layout accepts `modifier: Modifier = Modifier` as its **last** parameter.
- Apply `modifier` to the outermost layout element.
- Modifier order matters: `padding` before `background` clips differently than the reverse. Follow the logical layering.

### 4.3 State Hoisting
- Composables that manage text input: accept `value: String` + `onValueChange: (String) -> Unit`.
- Internal ephemeral state (`remember { mutableStateOf(...) }`) is fine for dropdown expanded, focus, animation state.
- Business state always hoisted to ViewModel.

### 4.4 Lambdas
- Prefer method references (`viewModel::onModeChange`) over inline lambdas when passing to child composables.
- Avoid creating new lambda instances in Lazy item blocks (causes recomposition). Extract to `remember` or use stable references.

### 4.5 Rigor Protocols
- **Visual Purity (Canvas):** If a progress bar artifact appears, use explicit overrides like `drawStopIndicator = {}` and `gapSize = 0.dp`. Use `StrokeCap.Butt` with a parent `Modifier.clip` to ensure seamless rounding.
- **Performance Deferral:** High-frequency state (like download progress percentages) must be passed to leaf composables as a lambda `() -> Float`. This ensures the root screen does not recompose on every byte update.

### 4.6 Plausible Composition
- All `LazyColumn` and `LazyRow` components must use stable keys to maximize the efficiency of the `RecordingApplier` during pausable frames.
- Ensure state reads are deferred using lambda-based modifiers (e.g., `Modifier.offset { ... }` or `drawBehind`) to minimize recomposition.

## 5. Coroutines & Flow


- `viewModelScope.launch` for ViewModel work.
- `collectAsStateWithLifecycle()` (not `collectAsState()`) for StateFlow in composables.
- `LaunchedEffect(Unit)` for one-shot event collection from `Channel`/`Flow`.
- Never use `GlobalScope`.
- Never use `runBlocking` in production code.

## 6. KDoc & Comments

- KDoc on public classes and non-obvious public functions only.
- Inline comments only where the "why" is non-obvious. Never comment the "what" when the code is self-explanatory.
- No `TODO` comments in merged code — use the issue tracker.
- File-level comments only for contracts or architectural rationale.

## 7. Formatting

- 4-space indent (Kotlin standard).
- Max line length: 120 characters.
- Trailing commas on multi-line parameter lists and `when` branches.
- Blank line between functions. No multiple consecutive blank lines.
- Import ordering: Kotlin stdlib → Android/Compose → Third-party → Project. Let the IDE sort.

## 8. Testing Conventions

- Unit tests: JUnit 5 + Turbine for Flow testing + MockK for mocks.
- Compose UI tests: `createComposeRule()` with semantic matchers.
- Test names: `fun methodName_condition_expectedResult()` or backtick descriptive names.
- Fakes for previews live alongside models. Fakes for tests live in `test/` source set.

### 8.1 Test Coverage Discipline (Agent Mandate)

- Whenever the task **adds** new production code (new classes, public functions, significant logic in `:domain`, `:data`, `:inference`, ViewModels, repositories, mappers, use cases, etc.) **or modifies** existing code, the agent **MUST**:
  - Write new unit tests (or extend existing ones) in the matching `src/test/kotlin` source set that cover the new/modified behavior, main paths, edge cases, and error conditions.
  - Use JUnit 5 + Turbine (for `StateFlow`/`Flow`) + MockK exactly as defined in the conventions above.
- For pure UI/Compose changes that affect behavior or interactions: also add or update Compose UI tests using `createComposeRule()` and semantic matchers.
- Trivial styling-only or preview-only changes are exempt from new tests, but the full test suite must still pass.
- Before declaring `TASK_STATUS: COMPLETE`, the agent must have written/updated the tests **and** executed them successfully.

---

## 9. Executable Validation (Agent MUST run before TASK_STATUS: COMPLETE)

1. **Check Formatting:** Run `gradlew.bat ktlintCheck`. Fix any reported styling errors.
2. **Run Tests:** Run `gradlew.bat testDebugUnitTest` (use `--tests "*[ChangedClass]*"` when possible for speed). Ensure the full suite passes and that new/modified code has corresponding tests.
3. **Run Full Build:** Run `gradlew.bat build`.

## 10. 2026 Exception & Notification Discipline

### 10.1 Worker Exception Handling
| Always | Never |
|--------|-------|
| Explicitly catch and rethrow `CancellationException`. | Catch `Exception` generically without re-throwing cancellation. |
| Return `Result.retry()` on transient network/IO failures. | Return `Result.failure()` for recoverable quota timeouts. |

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
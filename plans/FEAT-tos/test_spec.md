# FEAT-tos — Behavioral Test Specification

## Compose Single-Pass Exception (CFAW §4.3)

This feature introduces no domain logic, no use case changes, and no repository mutations.
The only logic is a single boolean flip in `SheetVisibilityState`. Per CFAW §4.3, the Compose
UI layer itself does not require Robolectric UI tests; ViewModel/delegate unit tests covering
the state transition are sufficient.

## Test Framework & Run Command

Framework: JUnit 5 + MockK + Turbine (StateFlow observation).
Run: `./gradlew :feature:settings:test`

## Test File

**Path:** `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/TosSheetTest.kt`

---

## Scenario Index

- S1 — TOS sheet opens when `onShowTosSheet(true)` is called
- S2 — TOS sheet closes when `onShowTosSheet(false)` is called
- S3 — TOS sheet is not shown by default
- S4 — TOS content contains all required legal sections
- M1 — Sheet cannot be permanently open by default (mutation defense)
- M2 — `isTosSheetOpen` in `SettingsHomeUiState` defaults false (mutation defense)

---

## Happy Path Scenarios

### S1: TOS sheet opens when `onShowTosSheet(true)` is called

- **Given:** A `SettingsGeneralDelegate` instance with mocked collaborators. `_sheetVisibility = MutableStateFlow(SheetVisibilityState())` with `tos = false`.
- **When:** `delegate.onShowTosSheet(_sheetVisibility, show = true)` is called.
- **Then:** `_sheetVisibility.value.tos == true`.

### S2: TOS sheet closes when `onShowTosSheet(false)` is called

- **Given:** A `SettingsGeneralDelegate` instance. `_sheetVisibility.value = SheetVisibilityState(tos = true)`.
- **When:** `delegate.onShowTosSheet(_sheetVisibility, show = false)` is called.
- **Then:** `_sheetVisibility.value.tos == false`.

### S3: TOS sheet is not shown by default

- **Given:** `SheetVisibilityState()` instantiated with no arguments.
- **When:** The `tos` field is read.
- **Then:** `tos == false`.

Also:
- **Given:** `SettingsHomeUiState()` instantiated with no arguments.
- **When:** The `isTosSheetOpen` field is read.
- **Then:** `isTosSheetOpen == false`.

### S4: TOS content contains required legal sections

- **Given:** `TOS_TEXT: String` (the package-level constant from `TosBottomSheet.kt`).
- **When:** The constant is inspected for required legal language.
- **Then:** `TOS_TEXT` contains each of the following (case-insensitive substring match):
  - `"as is"` — as-is warranty disclaimer (Section 6)
  - `"limitation of liability"` — liability cap section heading (Section 7)
  - `"18"` — age requirement ("at least 18 years of age", Section 1)
  - `"api key"` — user responsibility for API keys (Section 3)
  - `"governing law"` — governing law section heading (Section 12)
  - `"indemnif"` — indemnification clause (Section 8)
  - `"acceptable use"` — acceptable use section heading (Section 9)

---

## Error & Edge Case Scenarios

No error paths exist for this feature — the TOS sheet has no network calls, no I/O, and no
async operations. All state transitions are synchronous in-memory boolean flips.

---

## Mutation Defense

### Lazy Implementation Risk

The most plausible broken implementation: `SheetVisibilityState` or `SettingsHomeUiState` is
accidentally constructed with `tos = true` as the default, causing the TOS sheet to appear
on every cold start.

### M1: `SheetVisibilityState` always defaults `tos` to `false`

- **Given:** `SheetVisibilityState()` constructed 1000 times with no arguments.
- **When:** The `tos` field is read on each instance.
- **Then:** Every instance has `tos == false`. If any instance has `tos == true`, the test fails.

### M2: `SettingsHomeUiState` always defaults `isTosSheetOpen` to `false`

- **Given:** `SettingsHomeUiState()` constructed with no arguments.
- **When:** `isTosSheetOpen` is read.
- **Then:** `isTosSheetOpen == false`.

Also:
- **Given:** `SettingsUiState()` constructed with no arguments.
- **When:** `uiState.home.isTosSheetOpen` is read.
- **Then:** `uiState.home.isTosSheetOpen == false`.

---

## Notes for Implementation

- S4 tests require `TOS_TEXT` to be exported as a package-visible constant. Declare it as
  `internal val TOS_TEXT: String` at the top of `TosBottomSheet.kt`. The composable itself
  uses this constant in its `Text()` calls.
- Do NOT add `@VisibleForTesting` — `internal` visibility is sufficient because tests live
  in the same module and can access `internal` declarations.
- The delegate tests (S1, S2) follow the same construction pattern as existing delegate tests
  in this module — reuse the same mock setup boilerplate observed in the codebase.
- The `TOS_TEXT` constant may be a single multiline string or a concatenation of section
  constants; either is acceptable as long as it contains all the required substrings for S4.

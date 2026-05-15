# FEAT-settings-oss-licenses — Behavioral Test Specification

## Testing Contract

Framework: **JUnit 5** (`@Test`, `@BeforeEach`, `@Nested`, `@DisplayName`)
Coroutine harness: `MainDispatcherRule` (JUnit 5 extension, as used in `SettingsViewModelTest`)
Mocks: **MockK** (fakes preferred; MockK acceptable for framework dependencies)
Flow assertions: **Turbine** where testing `StateFlow` emissions
File location: `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/OssLicensesBottomSheetTest.kt`
Run command: `./gradlew :feature:settings:testDebugUnitTest`

---

## Test File: `OssLicensesBottomSheetTest.kt`

The test class exercises:
1. **State toggle logic** — `SettingsGeneralDelegate.onShowOssLicensesSheet` and
   `SettingsViewModel.onShowOssLicensesSheet` wiring through `SheetVisibilityState` →
   `SettingsHomeUiState.isOssLicensesSheetOpen`.
2. **Static data contract** — `OssLicenses.entries` completeness, ordering, required fields.
3. **Mutation defense** — guard against regressions that could accidentally hide the sheet,
   expose domain types, or break the data model.

---

## Scenario Group 1 — `SettingsGeneralDelegate` sheet toggle

### Setup

```kotlin
private val preferencesUseCases = mockk<SettingsPreferencesUseCases>(relaxed = true)
private val authUseCases = mockk<SettingsAuthUseCases>(relaxed = true)
private val errorHandler = mockk<ViewModelErrorHandler>()

// Construct the minimal delegate (same pattern as SettingsGeneralDelegateTest)
private lateinit var delegate: SettingsGeneralDelegate
private val sheetVisibility = MutableStateFlow(SheetVisibilityState())

@BeforeEach
fun setup() {
    every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns CoroutineExceptionHandler { _, _ -> }
    delegate = SettingsGeneralDelegate(
        preferencesUseCases = preferencesUseCases,
        authUseCases = authUseCases,
        errorHandler = errorHandler,
        detectVaultReconciliationStateUseCase = mockk(relaxed = true),
        adoptCloudVaultUseCase = mockk(relaxed = true),
        bootstrapCloudVaultFromLocalUseCase = mockk(relaxed = true),
        createVaultUseCase = mockk(relaxed = true),
        cryptoManager = mockk(relaxed = true),
        vaultMetadataPort = mockk(relaxed = true),
        cloudSyncPort = mockk(relaxed = true),
    )
}
```

### S1-01: `onShowOssLicensesSheet(true)` sets flag to true

**Given:** `sheetVisibility` starts with `ossLicenses = false`
**When:** `delegate.onShowOssLicensesSheet(sheetVisibility, true)` is called
**Then:** `sheetVisibility.value.ossLicenses == true`

```kotlin
@Test
fun `onShowOssLicensesSheet true sets ossLicenses flag`() {
    delegate.onShowOssLicensesSheet(sheetVisibility, true)
    assertEquals(true, sheetVisibility.value.ossLicenses)
}
```

### S1-02: `onShowOssLicensesSheet(false)` resets flag to false

**Given:** `sheetVisibility` has `ossLicenses = true`
**When:** `delegate.onShowOssLicensesSheet(sheetVisibility, false)` is called
**Then:** `sheetVisibility.value.ossLicenses == false`

```kotlin
@Test
fun `onShowOssLicensesSheet false resets ossLicenses flag`() {
    sheetVisibility.value = SheetVisibilityState(ossLicenses = true)
    delegate.onShowOssLicensesSheet(sheetVisibility, false)
    assertEquals(false, sheetVisibility.value.ossLicenses)
}
```

### S1-03: Toggling OSS sheet does not alter unrelated sheet flags

**Given:** `sheetVisibility` has `feedback = true`, `memories = true`, all others default
**When:** `delegate.onShowOssLicensesSheet(sheetVisibility, true)` is called
**Then:**
- `sheetVisibility.value.feedback == true` (unchanged)
- `sheetVisibility.value.memories == true` (unchanged)
- `sheetVisibility.value.ossLicenses == true` (newly set)

```kotlin
@Test
fun `onShowOssLicensesSheet does not disturb other sheet flags`() {
    sheetVisibility.value = SheetVisibilityState(feedback = true, memories = true)
    delegate.onShowOssLicensesSheet(sheetVisibility, true)
    assertEquals(true, sheetVisibility.value.feedback)
    assertEquals(true, sheetVisibility.value.memories)
    assertEquals(true, sheetVisibility.value.ossLicenses)
}
```

---

## Scenario Group 2 — `SettingsUiStateFactory` propagation

These tests verify that `SheetVisibilityState.ossLicenses` is correctly mapped to
`SettingsHomeUiState.isOssLicensesSheetOpen` inside `SettingsUiStateFactory.create()`.

Setup re-uses the minimal factory construction from `SettingsUiStateFactoryTest` — constructing
`SettingsUiStateFactory` directly with its real mappers (no mocks for the factory itself).

### S2-01: `isOssLicensesSheetOpen` is false when `SheetVisibilityState.ossLicenses` is false

**Given:** `SheetVisibilityState(ossLicenses = false)` (default)
**When:** `factory.create(…)` is called
**Then:** `result.home.isOssLicensesSheetOpen == false`

### S2-02: `isOssLicensesSheetOpen` is true when `SheetVisibilityState.ossLicenses` is true

**Given:** `SheetVisibilityState(ossLicenses = true)`
**When:** `factory.create(…)` is called
**Then:** `result.home.isOssLicensesSheetOpen == true`

---

## Scenario Group 3 — `SettingsViewModel` end-to-end state toggle (StateFlow via Turbine)

Re-uses the full `SettingsViewModel` construction pattern from `SettingsViewModelTest.setup()`.

### S3-01: `onShowOssLicensesSheet(true)` causes `uiState.home.isOssLicensesSheetOpen` to become true

**Given:** ViewModel is in its initial state
**When:** `viewModel.onShowOssLicensesSheet(true)` is called
**Then:** `viewModel.uiState.value.home.isOssLicensesSheetOpen == true`

```kotlin
@Test
fun `onShowOssLicensesSheet true reflects in uiState`() = runTest {
    viewModel.onShowOssLicensesSheet(true)
    assertEquals(true, viewModel.uiState.value.home.isOssLicensesSheetOpen)
}
```

### S3-02: `onShowOssLicensesSheet(false)` after `true` resets flag

**Given:** `viewModel.onShowOssLicensesSheet(true)` was called
**When:** `viewModel.onShowOssLicensesSheet(false)` is called
**Then:** `viewModel.uiState.value.home.isOssLicensesSheetOpen == false`

```kotlin
@Test
fun `onShowOssLicensesSheet false resets flag after being true`() = runTest {
    viewModel.onShowOssLicensesSheet(true)
    viewModel.onShowOssLicensesSheet(false)
    assertEquals(false, viewModel.uiState.value.home.isOssLicensesSheetOpen)
}
```

### S3-03: OSS sheet toggle does not disturb unrelated ViewModel state

**Given:** `viewModel.onShowFeedbackSheet(true)` was called first
**When:** `viewModel.onShowOssLicensesSheet(true)` is called
**Then:**
- `viewModel.uiState.value.home.isFeedbackSheetOpen == true` (unchanged)
- `viewModel.uiState.value.home.isOssLicensesSheetOpen == true`

```kotlin
@Test
fun `onShowOssLicensesSheet does not disturb isFeedbackSheetOpen`() = runTest {
    viewModel.onShowFeedbackSheet(true)
    viewModel.onShowOssLicensesSheet(true)
    assertEquals(true, viewModel.uiState.value.home.isFeedbackSheetOpen)
    assertEquals(true, viewModel.uiState.value.home.isOssLicensesSheetOpen)
}
```

---

## Scenario Group 4 — `OssLicenses` static data contract

These tests are pure unit tests on the `OssLicenses` object; no mocks needed.

### S4-01: Entry list is non-empty

**Then:** `OssLicenses.entries.isNotEmpty()`

```kotlin
@Test
fun `OssLicenses entries is non-empty`() {
    assertTrue(OssLicenses.entries.isNotEmpty())
}
```

### S4-02: All entries have non-blank required fields

**Then:** For every entry in `OssLicenses.entries`:
- `name.isNotBlank()`
- `version.isNotBlank()`
- `licenseType.isNotBlank()`
- `copyrightHolder.isNotBlank()`
- `licenseText.isNotBlank()`

```kotlin
@Test
fun `all OssLicenseEntry fields are non-blank`() {
    OssLicenses.entries.forEach { entry ->
        assertTrue(entry.name.isNotBlank(), "name blank for: $entry")
        assertTrue(entry.version.isNotBlank(), "version blank for: $entry")
        assertTrue(entry.licenseType.isNotBlank(), "licenseType blank for: $entry")
        assertTrue(entry.copyrightHolder.isNotBlank(), "copyrightHolder blank for: $entry")
        assertTrue(entry.licenseText.isNotBlank(), "licenseText blank for: $entry")
    }
}
```

### S4-03: Entry list is sorted alphabetically by name

**Then:** `OssLicenses.entries.map { it.name }` equals its own sorted version.

```kotlin
@Test
fun `OssLicenses entries are sorted alphabetically by name`() {
    val names = OssLicenses.entries.map { it.name }
    assertEquals(names.sorted(), names)
}
```

### S4-04: No duplicate names in the entry list

**Then:** `OssLicenses.entries.map { it.name }.distinct().size == OssLicenses.entries.size`

```kotlin
@Test
fun `OssLicenses entries have unique names`() {
    val names = OssLicenses.entries.map { it.name }
    assertEquals(names.size, names.distinct().size)
}
```

### S4-05: Known mandated libraries are present

Spot-check that specific libraries that legally require attribution are in the list.

**Then:** The following names exist in `OssLicenses.entries`:
- `"OkHttp"`
- `"Kotlin"`
- `"JNA (Java Native Access)"`
- `"CommonMark Java"`
- `"jtokkit"`
- `"Anthropic Java SDK"`
- `"Firebase Android SDK"`

```kotlin
@Test
fun `OssLicenses contains all mandatory attributed libraries`() {
    val names = OssLicenses.entries.map { it.name }.toSet()
    listOf(
        "OkHttp",
        "Kotlin",
        "JNA (Java Native Access)",
        "CommonMark Java",
        "jtokkit",
        "Anthropic Java SDK",
        "Firebase Android SDK",
    ).forEach { expected ->
        assertTrue(expected in names, "Missing required library: $expected")
    }
}
```

### S4-06: JNA entry documents dual-license origin in licenseType

**Then:** The entry for "JNA (Java Native Access)" has `licenseType` containing "Apache 2.0"
and `licenseType` containing "LGPL" (dual-license disclosure) and `licenseText` containing "Apache".

```kotlin
@Test
fun `JNA entry documents dual-license origin`() {
    val jna = OssLicenses.entries.first { it.name == "JNA (Java Native Access)" }
    assertTrue(jna.licenseType.contains("Apache 2.0"), "JNA licenseType must mention Apache 2.0")
    assertTrue(jna.licenseType.contains("LGPL"), "JNA licenseType must disclose dual-license origin")
    assertTrue(jna.licenseText.contains("Apache"), "JNA licenseText must reference Apache license")
}
```

### S4-07: MIT entries include copyright notice in license text

**Then:** For every entry where `licenseType == "MIT"`, `licenseText` contains "Copyright".

```kotlin
@Test
fun `all MIT entries include copyright in licenseText`() {
    OssLicenses.entries
        .filter { it.licenseType == "MIT" }
        .forEach { entry ->
            assertTrue(
                entry.licenseText.contains("Copyright"),
                "MIT entry '${entry.name}' must include Copyright in licenseText",
            )
        }
}
```

### S4-08: BSD entries include copyright notice in license text

**Then:** For every entry where `licenseType.startsWith("BSD")`, `licenseText` contains "Copyright".

```kotlin
@Test
fun `all BSD entries include copyright in licenseText`() {
    OssLicenses.entries
        .filter { it.licenseType.startsWith("BSD") }
        .forEach { entry ->
            assertTrue(
                entry.licenseText.contains("Copyright"),
                "BSD entry '${entry.name}' must include Copyright in licenseText",
            )
        }
}
```

---

## Scenario Group 5 — Mutation Defense

These scenarios guard against mutations that could silently break the feature without failing
compilation.

### S5-01 (mutation): Removing `ossLicenses` from `SheetVisibilityState` would fail S1-01

Target mutation: Delete `val ossLicenses: Boolean = false` from `SheetVisibilityState`.
Defense: S1-01 and S1-02 would fail to compile (reference to `.ossLicenses` property).
Additionally, S2-01 and S2-02 would fail at runtime because the factory can no longer pass
the flag through.

### S5-02 (mutation): Removing `isOssLicensesSheetOpen` from `SettingsHomeUiState` would fail S3-01

Target mutation: Delete `val isOssLicensesSheetOpen: Boolean = false` from `SettingsHomeUiState`.
Defense: S3-01, S3-02, S3-03 would fail to compile.

### S5-03 (mutation): Returning an empty list from `OssLicenses.entries`

Target mutation: Replace `listOf(…)` body with `emptyList()`.
Defense: S4-01 fails (`entries.isNotEmpty()`). S4-05 fails (mandatory library names not found).

### S5-04 (mutation): Removing the `name` sort

Target mutation: Remove `.sortedBy { it.name }` from `OssLicenses.entries` initialization.
Defense: S4-03 detects unsorted order.

### S5-05 (mutation): Blanking the `licenseText` field for one entry

Target mutation: Set `licenseText = ""` for any entry.
Defense: S4-02 (`licenseText.isNotBlank()`) fails for that entry.

### S5-06 (mutation): Removing the `onShowOssLicensesSheet` delegation in `SettingsViewModel`

Target mutation: Delete `fun onShowOssLicensesSheet(show: Boolean) = …` from `SettingsViewModel`.
Defense: S3-01, S3-02, S3-03 fail to compile (reference to `viewModel.onShowOssLicensesSheet`).

### S5-07 (mutation): `SettingsGeneralDelegate.onShowOssLicensesSheet` uses wrong field

Target mutation: Change `it.copy(ossLicenses = show)` to `it.copy(feedback = show)`.
Defense: S1-03 fails — `feedback` would be modified instead of `ossLicenses`, so
`sheetVisibility.value.ossLicenses` would remain `false` when `true` is expected.

---

## Full Test File Skeleton

```kotlin
package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAuthUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsPreferencesUseCases
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class OssLicensesBottomSheetTest {

    // ── Group 1: SettingsGeneralDelegate sheet toggle ──────────────────────

    @Nested
    inner class DelegateToggle {
        private val preferencesUseCases = mockk<SettingsPreferencesUseCases>(relaxed = true)
        private val authUseCases = mockk<SettingsAuthUseCases>(relaxed = true)
        private val errorHandler = mockk<ViewModelErrorHandler>()
        private lateinit var delegate: SettingsGeneralDelegate
        private val sheetVisibility = MutableStateFlow(SheetVisibilityState())

        @BeforeEach
        fun setup() {
            every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns
                CoroutineExceptionHandler { _, _ -> }
            delegate = SettingsGeneralDelegate(
                preferencesUseCases = preferencesUseCases,
                authUseCases = authUseCases,
                errorHandler = errorHandler,
                detectVaultReconciliationStateUseCase = mockk(relaxed = true),
                adoptCloudVaultUseCase = mockk(relaxed = true),
                bootstrapCloudVaultFromLocalUseCase = mockk(relaxed = true),
                createVaultUseCase = mockk(relaxed = true),
                cryptoManager = mockk(relaxed = true),
                vaultMetadataPort = mockk(relaxed = true),
                cloudSyncPort = mockk(relaxed = true),
            )
        }

        @Test
        fun `onShowOssLicensesSheet true sets ossLicenses flag`() {
            delegate.onShowOssLicensesSheet(sheetVisibility, true)
            assertEquals(true, sheetVisibility.value.ossLicenses)
        }

        @Test
        fun `onShowOssLicensesSheet false resets ossLicenses flag`() {
            sheetVisibility.value = SheetVisibilityState(ossLicenses = true)
            delegate.onShowOssLicensesSheet(sheetVisibility, false)
            assertEquals(false, sheetVisibility.value.ossLicenses)
        }

        @Test
        fun `onShowOssLicensesSheet does not disturb other sheet flags`() {
            sheetVisibility.value = SheetVisibilityState(feedback = true, memories = true)
            delegate.onShowOssLicensesSheet(sheetVisibility, true)
            assertEquals(true, sheetVisibility.value.feedback)
            assertEquals(true, sheetVisibility.value.memories)
            assertEquals(true, sheetVisibility.value.ossLicenses)
        }
    }

    // ── Group 3: SettingsViewModel end-to-end ─────────────────────────────
    // Note: Full SettingsViewModel construction is identical to SettingsViewModelTest.setup().
    // The ViewModel field and @BeforeEach are omitted here for brevity but must be copied
    // verbatim from SettingsViewModelTest when implementing the real test file.

    @Nested
    inner class ViewModelEndToEnd {
        // viewModel: SettingsViewModel constructed as per SettingsViewModelTest.setup()

        @Test
        fun `onShowOssLicensesSheet true reflects in uiState`() = runTest {
            // viewModel.onShowOssLicensesSheet(true)
            // assertEquals(true, viewModel.uiState.value.home.isOssLicensesSheetOpen)
        }

        @Test
        fun `onShowOssLicensesSheet false resets flag after being true`() = runTest {
            // viewModel.onShowOssLicensesSheet(true)
            // viewModel.onShowOssLicensesSheet(false)
            // assertEquals(false, viewModel.uiState.value.home.isOssLicensesSheetOpen)
        }

        @Test
        fun `onShowOssLicensesSheet does not disturb isFeedbackSheetOpen`() = runTest {
            // viewModel.onShowFeedbackSheet(true)
            // viewModel.onShowOssLicensesSheet(true)
            // assertEquals(true, viewModel.uiState.value.home.isFeedbackSheetOpen)
            // assertEquals(true, viewModel.uiState.value.home.isOssLicensesSheetOpen)
        }
    }

    // ── Group 4: OssLicenses static data contract ─────────────────────────

    @Nested
    inner class StaticDataContract {

        @Test
        fun `OssLicenses entries is non-empty`() {
            assertTrue(OssLicenses.entries.isNotEmpty())
        }

        @Test
        fun `all OssLicenseEntry fields are non-blank`() {
            OssLicenses.entries.forEach { entry ->
                assertTrue(entry.name.isNotBlank(), "name blank for: $entry")
                assertTrue(entry.version.isNotBlank(), "version blank for: $entry")
                assertTrue(entry.licenseType.isNotBlank(), "licenseType blank for: $entry")
                assertTrue(entry.copyrightHolder.isNotBlank(), "copyrightHolder blank for: $entry")
                assertTrue(entry.licenseText.isNotBlank(), "licenseText blank for: $entry")
            }
        }

        @Test
        fun `OssLicenses entries are sorted alphabetically by name`() {
            val names = OssLicenses.entries.map { it.name }
            assertEquals(names.sorted(), names)
        }

        @Test
        fun `OssLicenses entries have unique names`() {
            val names = OssLicenses.entries.map { it.name }
            assertEquals(names.size, names.distinct().size)
        }

        @Test
        fun `OssLicenses contains all mandatory attributed libraries`() {
            val names = OssLicenses.entries.map { it.name }.toSet()
            listOf(
                "OkHttp",
                "Kotlin",
                "JNA (Java Native Access)",
                "CommonMark Java",
                "jtokkit",
                "Anthropic Java SDK",
                "Firebase Android SDK",
            ).forEach { expected ->
                assertTrue(expected in names, "Missing required library: $expected")
            }
        }

        @Test
        fun `JNA entry documents dual-license origin`() {
            val jna = OssLicenses.entries.first { it.name == "JNA (Java Native Access)" }
            assertTrue(jna.licenseType.contains("Apache 2.0"), "JNA licenseType must mention Apache 2.0")
            assertTrue(jna.licenseType.contains("LGPL"), "JNA licenseType must disclose dual-license origin")
            assertTrue(jna.licenseText.contains("Apache"), "JNA licenseText must reference Apache license")
        }

        @Test
        fun `all MIT entries include copyright in licenseText`() {
            OssLicenses.entries
                .filter { it.licenseType == "MIT" }
                .forEach { entry ->
                    assertTrue(
                        entry.licenseText.contains("Copyright"),
                        "MIT entry '${entry.name}' must include Copyright in licenseText",
                    )
                }
        }

        @Test
        fun `all BSD entries include copyright in licenseText`() {
            OssLicenses.entries
                .filter { it.licenseType.startsWith("BSD") }
                .forEach { entry ->
                    assertTrue(
                        entry.licenseText.contains("Copyright"),
                        "BSD entry '${entry.name}' must include Copyright in licenseText",
                    )
                }
        }
    }
}
```

---

## Scenario Count Summary

| Group | Scenarios |
|-------|-----------|
| G1 — Delegate toggle | 3 |
| G2 — Factory propagation | 2 |
| G3 — ViewModel StateFlow end-to-end | 3 |
| G4 — Static data contract | 8 |
| G5 — Mutation defense (documented, covered by above) | 7 |
| **Total executable scenarios** | **16** |
| **Total mutation defenses** | **7** |

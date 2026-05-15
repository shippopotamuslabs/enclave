# FEAT-tos — Terms of Service Bottom Sheet

## Objective

Replace the non-functional external URL launch (`https://pocketcrew.ai/terms`) in the Settings
screen "Terms of Service" row with an in-app `ModalBottomSheet` that displays the full TOS text.
The sheet is opened by tapping the row and dismissed with a close button or a back-gesture.

### Acceptance Criteria

1. Tapping "Terms of Service" in Settings opens a bottom sheet (not a browser).
2. The sheet contains all required legal sections (as-is, limitation of liability, API key
   responsibility, generated content responsibility, acceptable use, age requirement, governing
   law).
3. The sheet is scrollable; the text is not truncated.
4. A visible "Close" button dismisses the sheet.
5. `isTosSheetOpen` defaults to `false` — the sheet is never open on cold start.
6. No network calls, no database writes, no manifest changes.
7. All detekt and ktlint checks pass (`maxIssues: 0`).

---

## File Structure

| File | Create / Modify | Responsibility |
|------|----------------|----------------|
| `feature/settings/.../SettingsModels.kt` | Modify | Add `isTosSheetOpen: Boolean = false` to `SettingsHomeUiState` |
| `feature/settings/.../SettingsUiStateFactory.kt` | Modify | Add `tos` field to `SheetVisibilityState`; map it to `SettingsHomeUiState.isTosSheetOpen` |
| `feature/settings/.../SettingsGeneralDelegate.kt` | Modify | Add `onShowTosSheet(sheetVisibility, show)` method |
| `feature/settings/.../SettingsViewModel.kt` | Modify | Add `fun onShowTosSheet(show: Boolean)` passthrough |
| `feature/settings/.../SettingsRoute.kt` | Modify | Replace Intent launch with `viewModel::onShowTosSheet(true)`; add `onShowTosSheet` param wire |
| `feature/settings/.../SettingsScreen.kt` | Modify | Add `onShowTosSheet: (Boolean) -> Unit` param; call `TosBottomSheet` when `isTosSheetOpen` |
| `feature/settings/.../TosBottomSheet.kt` | Create | New composable file — the sheet UI and embedded TOS text |

---

## Data Models & Schemas

### `SheetVisibilityState` (internal, `SettingsUiStateFactory.kt`)

Add one field:

```
tos: Boolean = false
```

### `SettingsHomeUiState` (`SettingsModels.kt`)

Add one field after `isSearchSkillSheetOpen`:

```
isTosSheetOpen: Boolean = false
```

No other data model changes. Domain layer is untouched.

---

## API Contracts & Interfaces

### `SettingsGeneralDelegate`

```kotlin
fun onShowTosSheet(sheetVisibility: MutableStateFlow<SheetVisibilityState>, show: Boolean) {
    sheetVisibility.update { it.copy(tos = show) }
}
```

### `SettingsViewModel`

```kotlin
fun onShowTosSheet(show: Boolean) = generalDelegate.onShowTosSheet(_sheetVisibility, show)
```

### `SettingsScreen` composable — new parameter

```kotlin
onShowTosSheet: (Boolean) -> Unit,
```

Added after `onOpenToS: () -> Unit`.

### `TosBottomSheet` composable

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TosBottomSheet(onDismiss: () -> Unit)
```

No state passed in — content is static. `onDismiss` is the only callback.

---

## Config / Permissions Delta

None. No manifest entries, no Gradle dependency additions, no new permissions required.

---

## TOS Content

The following is the canonical Terms of Service text to be embedded in `TosBottomSheet.kt`.
This text is for review before implementation. It is not legal advice.

---

### POCKET CREW — TERMS OF SERVICE

**Last Updated: May 15, 2026**

Please read these Terms of Service ("Terms") carefully before using Pocket Crew (the "App").
By downloading, installing, or using the App, you agree to be bound by these Terms. If you do
not agree, do not use the App.

---

**1. Acceptance of Terms**

By using Pocket Crew, you confirm that you are at least 18 years of age and have the legal
capacity to enter into these Terms. The App is not intended for use by anyone under 18. Note
that third-party API providers whose keys you use through this App (OpenAI, Anthropic, Google,
and others) independently require users to be at least 18 years of age.

---

**2. Description of Service**

Pocket Crew is a personal AI assistant application that operates primarily on-device using
local inference models ("Local Models"). It also optionally connects to third-party AI
provider APIs ("Cloud Providers") using credentials you supply ("Bring Your Own Key" or
"BYOK"). The developer does not supply, own, or control any Cloud Provider API keys used
through the App.

---

**3. User Responsibilities — API Keys**

You are solely responsible for:

- Obtaining your own API keys from third-party providers (OpenAI, Anthropic, Google, and
  others) and complying with each provider's terms of service.
- All usage, costs, and charges incurred through your API keys.
- The security of your API keys. You should treat your API keys as passwords. The developer
  is not responsible for unauthorized use of your keys.
- Any violation of a third-party provider's acceptable use policy resulting from your use of
  the App.

---

**4. User Responsibilities — Generated Content**

You are solely responsible for all content generated through the App, including but not
limited to text, images, video, audio, and any other media. You agree that you will not use
the App to generate or distribute:

- Content that is illegal under applicable law.
- Content that infringes intellectual property rights of third parties.
- Content that is defamatory, harassing, threatening, or abusive.
- Child sexual abuse material or any content that sexually exploits minors.
- Malware, phishing content, or material designed to deceive or defraud.

The developer reserves the right to terminate access if misuse is reported or discovered.

---

**5. Local Model Usage**

Local Models run entirely on your device. The developer does not control, audit, or guarantee
the accuracy, safety, or fitness of any local model. Use of Local Models is entirely at your
own risk. The developer is not responsible for any output produced by Local Models.

---

**6. Disclaimer of Warranties**

THE APP IS PROVIDED "AS IS" AND "AS AVAILABLE," WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED. TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, THE DEVELOPER EXPRESSLY DISCLAIMS
ALL WARRANTIES, INCLUDING BUT NOT LIMITED TO IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE, TITLE, AND NON-INFRINGEMENT. THE DEVELOPER DOES NOT WARRANT THAT
THE APP WILL BE UNINTERRUPTED, ERROR-FREE, SECURE, OR FREE OF VIRUSES OR OTHER HARMFUL
COMPONENTS.

---

**7. Limitation of Liability**

TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, THE DEVELOPER SHALL NOT BE LIABLE FOR ANY
INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, PUNITIVE, OR EXEMPLARY DAMAGES ARISING OUT OF
OR IN CONNECTION WITH YOUR USE OF, OR INABILITY TO USE, THE APP — INCLUDING BUT NOT LIMITED
TO LOSS OF DATA, LOSS OF PROFITS, LOSS OF BUSINESS, OR ANY HARM CAUSED BY AI-GENERATED
CONTENT — EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.

IN NO EVENT SHALL THE DEVELOPER'S TOTAL CUMULATIVE LIABILITY TO YOU FOR ANY CLAIMS ARISING
FROM THESE TERMS OR YOUR USE OF THE APP EXCEED THE AMOUNT YOU PAID FOR THE APP IN THE TWELVE
(12) MONTHS PRECEDING THE CLAIM (OR USD $10 IF THE APP WAS FREE).

---

**8. Indemnification**

You agree to indemnify, defend, and hold harmless the developer from and against any claims,
damages, losses, liabilities, costs, and expenses (including reasonable attorneys' fees)
arising out of or relating to: (a) your use of the App; (b) your violation of these Terms;
(c) your violation of any third-party rights, including API terms of service; or (d) any
content you generate using the App.

---

**9. Acceptable Use**

You agree not to use the App in any manner that:

- Violates any applicable local, state, national, or international law or regulation.
- Abuses, harasses, or harms any individual.
- Circumvents, disables, or interferes with security-related features of the App or any
  third-party service.
- Violates the terms of service of any third-party AI provider whose API you use through the
  App.

---

**10. Privacy**

Pocket Crew is designed with local-first privacy. Most data stays on your device. Optional
Google account sign-in enables encrypted cloud sync of your settings. The developer does not
sell or share your personal data with third parties for advertising. Your API keys are stored
encrypted on your device and in your personal cloud sync vault; the developer cannot read them.

---

**11. Changes to These Terms**

The developer may update these Terms from time to time. When changes are made, the "Last
Updated" date at the top will be revised. Continued use of the App after changes constitutes
acceptance of the updated Terms.

---

**12. Governing Law and Dispute Resolution**

These Terms are governed by the laws of the State of Delaware, United States, without regard
to its conflict-of-law provisions. Any dispute arising from these Terms or your use of the App
shall be resolved exclusively in the state or federal courts located in Delaware. You consent
to the personal jurisdiction of such courts.

---

**13. Entire Agreement**

These Terms constitute the entire agreement between you and the developer with respect to the
App and supersede all prior or contemporaneous understandings or agreements. If any provision
is held unenforceable, the remaining provisions will remain in full force and effect.

---

**14. Contact**

If you have questions about these Terms, you can contact the developer through the in-app
Feedback feature.

---

## Constitution Audit

| Rule | Status |
|------|--------|
| Domain layer unchanged | PASS — no changes to `:domain` or any domain class |
| No NavController in composables | PASS — `TosBottomSheet` takes only `onDismiss: () -> Unit` |
| UI state via ViewModel → UiState → Composable | PASS — `isTosSheetOpen` flows through `SettingsUiState.home.isTosSheetOpen` |
| Hilt DI | PASS — no new injectable classes; ViewModel already Hilt-managed |
| Compose only | PASS — no XML changes |
| minSdk 34 / targetSdk 36 | PASS — `ModalBottomSheet` available since API 21; no new API-level constraints |
| No new DB tables/columns for transient UI state | PASS — boolean lives in `SheetVisibilityState` (in-memory only) |
| detekt maxIssues: 0 | PASS — suppress annotations added where existing patterns require them |
| ktlint | PASS — follow existing formatting conventions |
| No new dependencies | PASS |
| No manifest changes | PASS |

---

## Subagent-Driven Execution

Downstream executors act as coordinators first, implementers second. For each Implementation
Task below:

1. **Spawn** a child subagent scoped to that task alone.
2. **Review** the child subagent's output against this document as the contract — accept nothing
   that contradicts the File Structure, API Contracts & Interfaces, the task Files lists, or the
   task-to-scenario bindings — before advancing to the next task.
3. **Repeat** task by task in order. Do not fold multiple unrelated tasks into one child run.

Scenario IDs are the **unit of work** each child subagent must satisfy for its task;
cross-check every ID the task names under **Verifies scenarios** against `test_spec.md`.

**Per-child review checklist:**
- Paths touched match the task Files entries and the File Structure table; no edits outside listed paths.
- Every **Verifies scenarios** ID for that task shows the intended test state for the current phase.
- No scope drift: no unlisted files, no extra behaviors, no shortcuts that bypass this spec.
- Deliverables live in real source and test files aligned with this spec.

---

## Implementation Tasks

### Task 1 — Add `isTosSheetOpen` to `SettingsHomeUiState`

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsModels.kt`

**Change:** Add `val isTosSheetOpen: Boolean = false` to `SettingsHomeUiState` data class, after `isSearchSkillSheetOpen`.

**Verifies scenarios:** S3

**Commit message:**
```
feat(settings): add isTosSheetOpen field to SettingsHomeUiState
```

---

### Task 2 — Add `tos` to `SheetVisibilityState` and wire factory

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsUiStateFactory.kt`

**Change:**
1. Add `val tos: Boolean = false` to `SheetVisibilityState` data class.
2. In `SettingsUiStateFactory.create()`, inside the `home = SettingsHomeUiState(...)` block, add: `isTosSheetOpen = sheetVisibility.tos`.

**Verifies scenarios:** S3, S1, S2

**Commit message:**
```
feat(settings): wire tos sheet visibility through SheetVisibilityState and factory
```

---

### Task 3 — Add `onShowTosSheet` to `SettingsGeneralDelegate`

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsGeneralDelegate.kt`

**Change:** Add `onShowTosSheet(sheetVisibility, show)` method (per API Contracts signature above), placed near `onShowFeedbackSheet`.

**Verifies scenarios:** S1, S2

**Commit message:**
```
feat(settings): add onShowTosSheet handler to SettingsGeneralDelegate
```

---

### Task 4 — Expose `onShowTosSheet` on `SettingsViewModel`

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModel.kt`

**Change:** Add `fun onShowTosSheet(show: Boolean) = generalDelegate.onShowTosSheet(_sheetVisibility, show)` in the General/Auth section.

**Verifies scenarios:** S1, S2

**Commit message:**
```
feat(settings): expose onShowTosSheet on SettingsViewModel
```

---

### Task 5 — Create `TosBottomSheet.kt`

**Files:**
- Create: `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/TosBottomSheet.kt`

**Change:** Create the file with:
- Package declaration matching the module.
- `@OptIn(ExperimentalMaterial3Api::class)` on the composable.
- `internal fun TosBottomSheet(onDismiss: () -> Unit)` as the entry point.
- `rememberModalBottomSheetState(skipPartiallyExpanded = true)`.
- `ModalBottomSheet` with `RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)`.
- `LazyColumn` for scrollable content.
- Header row: `Text("Terms of Service", style = MaterialTheme.typography.titleLarge)` and `IconButton(onClick = onDismiss)` with `Icons.Default.Close`.
- All 14 TOS sections as section heading + body text items.
- `internal val TOS_TEXT: String` constant at package level (concatenation of all section text — used in tests for S4 assertions).
- `@Suppress("LongMethod")` on the composable.
- A `@Preview` at the bottom.

**Verifies scenarios:** S4, S1

**Commit message:**
```
feat(settings): add TosBottomSheet composable with full TOS text
```

---

### Task 6 — Wire `TosBottomSheet` into `SettingsScreen`

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsScreen.kt`

**Change:**
1. Add `onShowTosSheet: (Boolean) -> Unit` parameter after `onOpenToS`.
2. Change "Terms of Service" row `onClick = onOpenToS` to `onClick = { onShowTosSheet(true) }`.
3. After the vault reauth `when` block, add `if (uiState.home.isTosSheetOpen) { TosBottomSheet(onDismiss = { onShowTosSheet(false) }) }`.
4. Update `@Preview` to pass `onShowTosSheet = {}`.

**Verifies scenarios:** S1, S2

**Commit message:**
```
feat(settings): wire TosBottomSheet into SettingsScreen
```

---

### Task 7 — Update `SettingsRoute` to use in-app sheet

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsRoute.kt`

**Change:**
1. Replace `onOpenToS = { val intent = ... context.startActivity(intent) }` with `onOpenToS = {}`.
2. Add `onShowTosSheet = viewModel::onShowTosSheet` to `SettingsScreen(...)` call.
3. Remove `android.content.Intent` and `android.net.Uri` imports if no longer used. Keep `LocalContext` only if still needed for other purposes.

**Verifies scenarios:** S1 (end-to-end wiring)

**Commit message:**
```
feat(settings): replace TOS URL intent with in-app sheet in SettingsRoute
```

---

### Task 8 — Write and run tests; verify detekt/ktlint

**Files:**
- Create: `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/TosSheetTest.kt`

**Change:** Write all test cases for S1–S4 and M1–M2 as defined in `test_spec.md`. Run:
- `./gradlew :feature:settings:test`
- `./gradlew :feature:settings:detekt`
- `./gradlew ktlintCheck`

**Verifies scenarios:** S1, S2, S3, S4, M1, M2

**Commit message:**
```
test(settings): add TosSheetTest covering sheet visibility and TOS content
```

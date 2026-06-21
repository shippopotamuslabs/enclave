# UI_DESIGN_SPEC.md
**Enclave – UI Design Contract v2.5**

Immutable UI contract. This defines exact visual behavior, component structure, and interaction patterns.

Always enforce together with `ARCHITECTURE_RULES.md` (which defines the structural Scaffold/navigation/IME patterns) and `DESKTOP_RULES.md` §6–§7 for the shared neo-brutalist token model and agent UI workflow on desktop.

---

## Strict Boundaries

| Always | Never |
|--------|-------|
| Use Material 3 Expressive theming & dynamic layout reflows (`PaneScaffold`). | Hard-code hex colors outside the theme definitions (except specific semantic colors). |
| Include unified `@Preview` (`androidx.compose.ui.tooling.preview`) for states. | Inject real ViewModels or Hilt dependencies into `@Preview` functions. |
| Use lambda-based deferred state reads (e.g., `Modifier.offset { ... }`). | Tie UI recompositions directly to rapidly emitting state values directly in modifiers. |
| Use 20.dp asymmetric corners for message bubbles. | Rely on `paddingValues` threading down the UI tree from a root Scaffold. |
| Support Dynamic Color on Android 12+ (API 31+). | Introduce layout thrash when the keyboard opens/closes. |
| Clear the input text immediately after send. | Recompose the entire message list when the user is typing. |
| Run the Brief Read (§5.1) and Anti-Slop Pre-Flight (§5.3) before generating or materially restyling UI. | Ship marketing-landing patterns, industrial/CRT brutalism, glassmorphism, soft shadows, or ad-hoc hex colors outside theme tokens. |
| Extend existing `Neo*` components and `EnclaveNeoTokens` before inventing one-off styling. | Rebuild surfaces with raw Material defaults when a neo component already exists. |

## 1. Design Language & Theme


- Dark-first, premium AI aesthetic (X/Grok-inspired).
- Unidirectional data flow. Full state hoisting. Minimal recompositions.
- **Exceptions to Theme:** Shield red (`Color.Red`) and success green may be hardcoded for specific semantic states.

### 1.1 Colors
**Dark Theme (Default):**
- Background: `#000000` (AMOLED black)
- Surface: `#121212`
- SurfaceVariant: `#1F1F1F`
- Primary: `#A855F7` (purple accent)
- OnBackground: `#FFFFFF` (pure white)
- OnSurface: `#E5E7EB` (soft white)

**Light Theme:**
- Background: `#FCFCFC`
- Surface: `#F8F9FA`
- Primary: `#A855F7` (same purple)

### 1.2 Typography

- **Headlines:** SemiBold, 24-28sp (top bar title, mode switcher).
- **Body:** Normal, 16sp (message content, standard text).

### 1.3 Neo-Brutalist Product Chrome (Mandatory)

Enclave is **product neo-brutalism**, not marketing brutalism or industrial/CRT terminal aesthetics.

- **Geometry:** `EnclaveNeoTokens` (`cornerRadius` 4.dp, `borderWidth` 2.dp, hard shadow offsets). Chat bubbles and the input bar retain their locked shapes in §2–§3 until explicitly migrated.
- **Surfaces:** Use `Modifier.neoSurface`, `NeoPanel`, `NeoPrimaryButton`, `NeoOutlinedTextField`, and siblings under `core/ui/.../component/neo/`. Do not restyle settings rows, sheets, or actions with plain Material cards.
- **Colors:** Read from `MaterialTheme.colorScheme` and `Color.kt` (`PeachAccent`, `DarkNeoBorder`, `LightNeoBorder`, etc.). Neo borders use `outlineVariant`; hard shadows use `DarkNeoShadow` / `LightNeoShadow`.
- **Accent lock:** Dark theme uses peach as primary accent. Do not introduce a second competing accent (purple gradients, neon greens, hazard red) on the same screen unless it is an existing semantic color (error, success, shield).
- **Motion:** Functional only — press/hover feedback, list scroll affordances, IME-safe transitions. No scroll-jacking, marquees, or cinematic page motion.

---

## 2. Chat Screen Structure

### 2.1 Scaffold Ownership
`ChatScreen` owns its own `Scaffold` with:
- `topBar` = `ChatTopBar`
- `snackbarHost` = local `SnackbarHost`
- `contentWindowInsets = WindowInsets(0)`
- **Content:** `Column` with `weight(1f)` message area + `InputBar` (see `ARCHITECTURE_RULES.md` for exact IME padding pattern).

### 2.2 ChatTopBar (`CenterAlignedTopAppBar`)
- **Left:** Menu icon → navigates to History (via callback).
- **Center:** "Enclave" title + optional `ModeBadge` (visible only during Crew/Think mode).
- **Right:** New chat icon → triggers `viewModel.createNewChat()`.

### 2.3 MessageList (`LazyColumn`)
- `reverseLayout = false`
- Render `MessageBubble` items based on `role` (User vs. Assistant).
- **Bubble Corners:** 20.dp asymmetric. User bubbles have a sharper bottom-right corner (`4.dp`). Assistant bubbles have a sharper bottom-left corner (`4.dp`).

## 3. Input Bar Styling (Preserve Exactly)

**Do not alter these exact styling constraints:**
- **Standard Layout:** `Row` containing text field (`weight(1f)`) and send button.
- **Shape:** `RoundedCornerShape(28.dp)`.
- **Behavior:** Multi-line, max 6 lines before internal scroll. Clear text immediately after send.
- **Text Area:** `padding(start = 16.dp)`.
- **Typography:** `fontSize = 16.sp`, `lineHeight = 22.sp`.
- **Collapse Icon:** Placed at `Alignment.TopEnd` with zero padding offset.
- **Container:** Horizontal padding `16.dp` on the outer Surface.
- **Action Row:** Icon spacing `4.dp`.
- **Future Support:** Ensure layout can accommodate attachment chips above the text field.

## 4. History Screen

- `HistoryScreen` owns its own `Scaffold` with `HistoryTopBar`.
- **TopAppBar:** Back arrow (left), search field (center/title), settings icon (right).
- **MessageList (`LazyColumn`):** Must use stable keys on all items.
- **Headers:** Sticky date-group headers (Today, Yesterday, Pinned, etc.).
- **Logic:** Search filtering logic must live strictly in `HistoryViewModel`.

---

## 5. Agent UI Workflow (Brief Read & Anti-Slop Audit)

This section applies to **agents** generating or restyling Android Compose UI. It adapts an audit-first workflow for Enclave's multi-screen product app — not landing pages or dashboards from external design skills.

### 5.1 Brief Read (before code)

Before writing or restyling UI, infer intent from the task and existing code. Do **not** default to generic Material or AI-slop patterns.

**Read these signals first:**

1. **Surface kind** — chat chrome, message bubble, input bar, settings row, modal/bottom sheet, history list, onboarding step, empty/loading/error state.
2. **Change mode** — *greenfield* (new surface), *extend* (add to an existing screen using current tokens), or *preserve* (locked spec in §2–§4 must not drift).
3. **Scope** — single component, full screen, or cross-screen pattern (must land in `EnclaveNeoTokens` or a shared `Neo*` component).
4. **Constraints** — IME/insets (`ARCHITECTURE_RULES.md`), accessibility, Dynamic Color (§Strict Boundaries), preview requirements.
5. **Existing assets** — logos, illustration, copy voice, analytics/event names on controls being touched.

**Output a one-line Design Read before generating:**

> *Reading this as: [surface] on [screen], [greenfield | extend | preserve], Enclave neo-brutalist product chrome via [Neo* component / EnclaveNeoTokens].*

Examples:

- *Reading this as: settings action row on Provider screen, extend, Enclave neo-brutalist via `NeoActionRow` + `EnclaveNeoTokens.rowSpacing`.*
- *Reading this as: input bar on Chat, preserve, locked §3 constraints — no visual drift.*

If the task is ambiguous between **preserve** and **overhaul**, ask **once**: *"Should this match existing Enclave neo chrome exactly, or are we intentionally changing the locked spec?"* If context is clear, do not ask — declare the Design Read and proceed.

**Anti-default discipline:** Do not reach for centered hero layouts, three equal feature cards, purple/blue gradient meshes, glassmorphism, soft drop shadows, decorative uppercase eyebrows, or stock "AI app" purple unless the brief explicitly requests them and they still pass §1.3.

### 5.2 Anti-Slop Audit (before touching existing UI)

When modifying an existing screen or component, document the current state **before** proposing changes:

| Audit item | What to capture |
|------------|-----------------|
| **Token source** | Colors, radii, borders, shadows from `EnclaveNeoTokens`, `Color.kt`, `Theme.kt` — not one-off literals. |
| **Components in use** | Which `Neo*` widgets already wrap this surface; reuse before creating parallels. |
| **Locked specs** | §2–§4 regions (chat scaffold, bubbles, input bar, history keys) — mark *preserve*. |
| **Hierarchy** | Primary action, secondary actions, metadata — one clear focal point per viewport region. |
| **Spacing rhythm** | `screenHorizontalPadding`, `sectionSpacing`, `rowSpacing` — flag arbitrary padding. |
| **Patterns to preserve** | Recognizable Enclave chrome (hard borders, peach accent on dark, bubble asymmetry). |
| **Patterns to retire** | Plain Material surfaces without neo border/shadow, mixed accent colors, placeholder copy, inconsistent corner radii, decorative motion. |

**Preservation rules (redesigns):**

- Do not change information architecture, navigation labels, or control IDs tied to analytics unless asked.
- Do not rewrite user-facing copy unless asked; visual cleanup ≠ copy rewrite.
- Do not regress accessibility (contrast, focus order, content descriptions, keyboard/IME behavior).
- Extract existing tokens before inventing new ones; add to `EnclaveNeoTokens` only when the pattern repeats across screens.

**Modernisation levers (priority order — stop when the brief is satisfied):**

1. Spacing and vertical rhythm (`EnclaveNeoTokens` gaps, consistent horizontal padding).
2. Typography hierarchy (`Type.kt` scales — do not introduce a new display font).
3. Component consolidation (route through existing `Neo*` APIs).
4. Color recalibration within the locked palette (unify muted text, keep peach accent).
5. Functional motion (FAB fade, button press) — only if it improves feedback without harming IME or list performance.

### 5.3 Anti-Slop Pre-Flight (mandatory before TASK_STATUS: COMPLETE)

Run every item for UI-generating tasks. If any box fails, fix before completing.

- [ ] **Design Read** (§5.1) stated in the agent response.
- [ ] **Change mode** correct: locked §2–§4 specs untouched when mode is *preserve*.
- [ ] **Token lock:** no hard-coded hex outside theme definitions (except §1 semantic exceptions).
- [ ] **Neo chrome:** surfaces use `Neo*` components or `Modifier.neoSurface`; not raw Material cards/buttons for product chrome.
- [ ] **Geometry lock:** borders 2.dp, hard shadows, radii from `EnclaveNeoTokens` (except locked chat/input shapes).
- [ ] **Accent lock:** one primary accent per theme; semantic colors only for error/success/shield states.
- [ ] **Contrast:** primary actions and labels meet WCAG AA against their backgrounds.
- [ ] **Hierarchy:** one obvious primary action per region; no duplicate CTAs with the same intent.
- [ ] **Spacing:** uses tokenized padding/spacing — no one-off `12.dp` / `18.dp` mixes on the same screen without reason.
- [ ] **Motion:** every animation justified as feedback or state transition; respects reduced motion; no decorative loops.
- [ ] **States:** empty, loading, and error paths styled — not blank placeholders.
- [ ] **Copy:** no lorem ipsum, "Acme", or grammatically broken AI filler in user-visible strings.
- [ ] **Previews:** Light and Dark `@Preview` for new/changed composables (§Strict Boundaries).
- [ ] **Out of scope rejected:** landing-page hero blocks, industrial/CRT brutalism, and dashboard-template layouts were not imported.

---

## 6. Executable Validation (Agent MUST run before TASK_STATUS: COMPLETE)

1. **Compile UI App:** Run `./gradlew :app:assembleDebug`.
2. **Verify Compose Constraints:** Run `./gradlew ktlintCheck` or the affected module's ktlint task to ensure Compose formatting and modifier ordering rules are respected.
3. **Verify Previews:** Render previews via `@compose-preview` tool if instructed, otherwise ensure all files have valid Light/Dark preview annotations.

## 7. System UI Integration (2026)

| Always | Never |
|--------|-------|
| Use `Notification.ProgressStyle` for any task > 30 seconds. | Rely on custom notification layouts for progress bars. |
| Match Compose `LinearProgressIndicator` colors to `ProgressStyle` accents. | Hardcode progress bar colors that clash with system dynamic themes. |

**Constraint:** The `DownloadScreen` must derive its percentage value directly from `WorkInfo.progress`. No intermediate ViewModels variables should hold this state to avoid synchronization lag.
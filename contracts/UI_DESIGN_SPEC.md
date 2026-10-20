# UI_DESIGN_SPEC.md
**Enclave – UI Design Contract v2.9**

UI contract. This defines exact visual behavior, component structure, and interaction patterns.

Always enforce together with `ARCHITECTURE_RULES.md` (which defines the structural Scaffold/navigation/IME patterns) and `DESKTOP_RULES.md` §6–§7 for the shared neo-brutalist token model and agent UI workflow on desktop.

> **v2.6 change (ENC-1/ENC-5, user-authorized contract update):** Enclave moves from neo-only chrome to **dual theme families** — Material Tonal (normal Material 3) and Neo-Brutalist — sharing one semantic spec and one neutral component API, adapted from Stratum `contracts/UI_DESIGN.md`. The neutral `Enclave*` components and spec bundle landed in ENC-6/ENC-8; locked chat/input shapes in §2–§3 remain preserve-mode until an ENC sub-issue explicitly migrates them.

> **v2.7 change (ENC-87, under Sean's explicit 2026-10-19 Contract Waiver for this adoption):** the **Shippopotamus Palette Gospel** and **Shippopotamus Compose Gospel** are embedded in full as §8 and are **canonical for all new UI work** in Enclave (and across Shippopotamus apps — Bloviate, Stratum, Enclave). The pre-existing dual-family specs in §1.1–§1.4 are **not** rewritten by this change: they remain preserve-mode until Sean ratifies the §8.4 conflict register and migration sub-issues land. Where §8 and §§1–7 conflict, §8 wins for new work; §8.4 lists every known conflict.

> **v2.8 change (2026-10-20, under Sean's explicit Contract Waiver for this adoption):** the **Shippopotamus Button Shape Gospel**, declared canonical by Sean on 2026-10-20 for Bloviate / Stratum / Enclave, is embedded in full as §8.6. It governs button shape for new work and supersedes §8.3 on one point: the button press morph now lands at **12dp** (was 16dp) for both the pill default and the square variant. Sean's pasted source spelled the tokens `Shipopotamus*` (one p); the canonical spelling is `Shippopotamus*`, used throughout §8.6.

> **v2.9 change (2026-10-20, under Sean's explicit Contract Waiver for this adoption):** the **Shippopotamus Glass Bars Gospel** (Haze 2.0), declared canonical by Sean on 2026-10-20 for Bloviate / Stratum / Enclave, is embedded in full as §8.7. It governs bars, sheets, and overlay glass for new work: the top bar is transparent at rest and glass when scrolled, and the bottom bar is **always glass, never solid**. It resolves §8.4 conflict **C13 in the glass direction** (see §8.7's register note) and supersedes §8.3's solid nav-container and opaque-sheet treatments for those surfaces. Sean's pasted source spelled the tokens `Shipopotamus*` (one p); the canonical spelling is `Shippopotamus*`, used throughout §8.7.

---

## Strict Boundaries

| Always | Never |
|--------|-------|
| Use the root Enclave theme + semantic tokens from the active theme spec (see §1.4). | Hard-code hex colors outside the theme definitions (except specific semantic colors). |
| Use neutral spec-driven components from `:core:ui` for product chrome. | Import raw `MaterialTheme`/Material 3 chrome (`Button`, `Card`, `OutlinedTextField`, …) or hex literals in `:feature:*`/`:app` UI — Detekt `enclave` enforced (ENC-2). |
| Include unified `@Preview` (`androidx.compose.ui.tooling.preview`) for states, covering **Tonal + Neo × Light + Dark** for shared components. | Inject real ViewModels or Hilt dependencies into `@Preview` functions. |
| Use lambda-based deferred state reads (e.g., `Modifier.offset { ... }`). | Tie UI recompositions directly to rapidly emitting state values directly in modifiers. |
| Use 20.dp asymmetric corners for message bubbles. | Rely on `paddingValues` threading down the UI tree from a root Scaffold. |
| Support Dynamic Color on Android 12+ (API 31+). | Introduce layout thrash when the keyboard opens/closes. |
| Clear the input text immediately after send. | Recompose the entire message list when the user is typing. |
| Run the Brief Read (§5.1) and Anti-Slop Pre-Flight (§5.3) before generating or materially restyling UI. | Ship marketing-landing patterns, industrial/CRT brutalism, glassmorphism, soft shadows, or ad-hoc hex colors outside theme tokens. |
| Extend existing neutral `:core:ui` components (`Enclave*`, spec-driven) before inventing one-off styling. | Rebuild surfaces with raw Material defaults when a shared component already exists, or name new public components `Neo*`/`Material*`. |

## 1. Design Language & Theme


- Dark-first, premium AI aesthetic (X/Grok-inspired).
- Unidirectional data flow. Full state hoisting. Minimal recompositions.
- **Exceptions to Theme:** Shield red (`Color.Red`) and success green may be hardcoded for specific semantic states.
- **Canonical cross-app system (§8):** All NEW UI work follows the Shippopotamus gospels embedded in §8 (palette tokens, spacing/shape/type systems, component defaults, touch and motion law). The legacy family specs below govern existing surfaces in preserve-mode only; see §8.4 for the conflict register.

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

### 1.3 Neo-Brutalist Product Chrome (One Family, Not the Only Chrome)

Enclave Neo is **product neo-brutalism**, not marketing brutalism or industrial/CRT terminal aesthetics. It is one of two theme families (§1.4); feature code must render correctly in both.

- **Geometry (Neo spec):** 4.dp corners, 2.dp borders, hard offset shadows (no blur). Chat bubbles and the input bar retain their locked shapes in §2–§3 until explicitly migrated.
- **Surfaces:** Use the neutral spec-driven `:core:ui` component API (`Enclave*` components, `Modifier.enclaveSurface`). Do not restyle settings rows, sheets, or actions with plain Material cards in feature code.
- **Colors:** Read semantic roles from the active spec (today bridged via `MaterialTheme.colorScheme` + `Color.kt`). Never branch on theme family in feature code — components consult spec shapes/elevations internally.
- **Accent lock:** Neo dark uses peach as primary accent. Do not introduce a second competing accent (purple gradients, neon greens, hazard red) on the same screen unless it is an existing semantic color (error, success, shield). The Tonal spec uses its own single Material accent; do not mix accents within a family.
- **Motion:** Functional only — press/hover feedback, list scroll affordances, IME-safe transitions. No scroll-jacking, marquees, or cinematic page motion.

### 1.4 Theme Architecture — Dual Families (Mandatory, ENC-1)

Feature modules depend on **semantic theme**, not Material or neo implementation details.

Core types in `:core:ui/theme/` (Stratum `StratumThemeSpec` pattern):

```text
EnclaveThemeId / ThemeFamily   enum: Tonal, NeoBrutalist
ColorMode                      enum: System, Light, Dark
EnclaveThemeSpec               immutable token bundle (colors, shapes, elevations, typography)
TonalSpec                      Material 3 baseline (rounded corners, thin borders, no hard shadow)
NeoBrutalistSpec               Enclave neo values (4dp, 2dp, hard shadow, peach-on-dark)
LocalEnclaveTheme              CompositionLocal carrying the active spec + accessor
ThemePreferences (domain)      family + colorMode, persisted via DataStore; app shell maps domain → UI
```

- The root theme composable is the **only** place that resolves a spec (factory `(family, dark) → spec`) and may wrap `MaterialTheme` internally. Features must not import `MaterialTheme`.
- Adding a family = new spec file only — no feature rewrites if this section was followed.
- Settings exposes **Theme family** (Tonal / Neo Brutalist) and **Appearance** (System / Light / Dark); changing family recomposes without activity restart.
- Enforced by Detekt `enclave` rules `ForbiddenMaterialComposeInFeatures` and `ForbiddenRawColorLiteralInFeatures` (`:core:ui` implementation exempt; tests/previews per `UNIT_TESTING.md`).

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

> *Reading this as: [surface] on [screen], [greenfield | extend | preserve], Enclave product chrome via [Enclave* component / active theme spec].*

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
- [ ] **Spec chrome:** surfaces use neutral `:core:ui` components driven by the active spec; not raw Material cards/buttons in feature code, and no feature branching on theme family.
- [ ] **Geometry lock:** corners, borders, and shadows come from the active spec (Neo: 2.dp/hard shadow/4.dp; Tonal: Material radii/no hard shadow), except locked chat/input shapes in §2–§3.
- [ ] **Detekt:** `./gradlew detekt` passes (`ForbiddenMaterialComposeInFeatures`, `ForbiddenRawColorLiteralInFeatures`, `maxIssues: 0`).
- [ ] **Previews:** shared components cover Tonal + Neo × Light + Dark; screenshot goldens updated when ENC-19 wiring exists.
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

---

## 8. Shippopotamus Gospels — Canonical Cross-App UI System (ENC-87)

This section embeds, **in full**, the two Shippopotamus gospels declared canonical by Sean on 2026-10-19: the **Color Palette Gospel** (official dark theme) and the **Android Jetpack Compose Gospel** (official UI system). They are the canonical UI system for **all** Shippopotamus apps (Bloviate, Stratum, Enclave, and future products). The full text lives here — not behind an external link — so the system is enforced by whoever works in this repo, with whatever tooling.

### 8.1 Authority and scope

- **Authority:** Sean's directive of 2026-10-19 is the Contract Waiver authorizing this contract edit (ENC-87). Sister adoptions: Bloviate ENC-85/ENC-86, Stratum STR-137.
- **Scope:** §8 governs **all new UI work** — new screens, new components, new tokens, and any surface being materially restyled. Existing Enclave surfaces built on the §1.1–§1.4 dual-family specs stay **preserve-mode**: they do not change by virtue of this section alone. Migrating an existing surface or family to §8 requires Sean's ratification of the relevant §8.4 conflicts plus a dedicated migration sub-issue.
- **Precedence:** Where §8 conflicts with §§1–7, **§8 wins for new work**. §8.4 is the register of every known conflict; do not silently resolve a registered conflict in feature code — escalate it.
- **Platform:** §8 is Android/Compose only. The desktop app (`DESKTOP_RULES.md`, Iced/neobrutalism) is out of scope.

### 8.2 Palette Gospel — Official Dark Theme (full text)

**Why this works (empirical basis):**

1. **No pure black/white halation:** Pure `#000` on `#FFF` is 21:1 contrast and causes halation — a glowing halo around text — roughly 2× worse for astigmatism (~50% of users). This palette uses `#0D0E11` base with `#EDEEF0` text at **15.8:1** — the Material Design/Google research sweet spot.
2. **Elevation via lightness, not shadows:** Shadows disappear on dark. Each surface is 4–8% lighter than the one below it. This is what Linear, Raycast, and Vercel converge on.
3. **Desaturated accents to prevent vibration:** Saturated colors buzz on dark backgrounds. Every accent is desaturated 20–30% (saturation 40–60% max). Indigo is the most universally preferred and least affected by halation.
4. **Warm off-white, not blue-white:** Pure white causes more eye fatigue. `#EDEEF0` is warm, 87% white. Secondary texts hit WCAG AA 4.5:1+ without being harsh.

**Background layers:**

| Token | Value | Role |
|-------|-------|------|
| bg-base | `#0D0E11` | App canvas, deepest |
| bg-subtle | `#111216` | Navbar, tab bar |
| surface-1 | `#181A20` | Cards, sheets |
| surface-2 | `#1F222A` | Elevated / pressed |
| surface-3 | `#272A33` | Hover, modal, popover |

**Borders / dividers:**

| Token | Value |
|-------|-------|
| border-default | `#2A2E39` |
| border-strong | `#343A48` |
| border-subtle | `#20222E` |

**Text layers:**

| Token | Value | Role |
|-------|-------|------|
| text-primary | `#EDEEF0` | 87% white, 15.8:1 |
| text-high | `#C5C7DD` | Secondary headings |
| text-secondary | `#A1A5B0` | Body secondary |
| text-tertiary | `#858994` | Captions, metadata |
| text-faint | `#6E7280` | Icons, disabled, placeholder |

**Indigo accent family (primary action):**

| Token | Value |
|-------|-------|
| accent-base | `#7A7CFF` |
| accent-hover | `#8D8FFF` |
| accent-active | `#5F62B9` |
| accent-border-subtle | `#4B4EBF` |
| accent-deep | `#3B3BAA` |
| accent-muted-bg | `rgba(122,124,255,0.15)` |
| focus-ring | `rgba(122,124,255,0.40)` |

**Semantic colors (all desaturated to ≤60% saturation):**

| Token | Value |
|-------|-------|
| success-muted-green | `#7DBE8C` |
| warning-amber | `#D9B07A` |
| danger-soft-red | `#E07A7A` |
| info-sky-blue | `#7BB5D6` |

**Mobile extras:**

| Token | Value |
|-------|-------|
| bg-scrim | `rgba(13,14,17,0.80)` (bottom-sheet scrim) |
| surface-overlay | `#1F222AF2` |

### 8.3 Compose Gospel — Official UI System (full text)

Material 3 + M3 Expressive (Android 16 QPR1) is the baseline.

**Why this works:**

1. **8dp grid + 4dp micro:** Consistent spacing per Material specs — 8dp standard grid for margins/padding, 4dp increments for internal spacing. Everything aligns; no random 5dp/7dp. Layouts feel calm and scannable.
2. **48dp touch targets are law:** Minimum touch target is 48×48dp. Use `Modifier.minimumInteractiveComponentSize()` on custom interactives; the hit area auto-expands to 48dp even when the visual is smaller. Accessibility + fat-finger research, not preference.
3. **Elevation is tint, not shadow, in dark:** Shadows become invisible on dark themes. Convey elevation with surface tint; raised surfaces are slightly **lighter** than base in dark mode.
4. **Shape system = hierarchy:** Nested radius = outer radius − padding (a 28dp card with 12dp padding holds 16dp corners). Buttons use capsule (radius = half height). Cards use fixed radii. Don't invent radii.
5. **M3 Expressive is the new baseline:** Morphable shapes (squircle, pill, cookie, clover), spring physics as the default `MotionScheme.expressive()`, emphasized typography with bigger/heavier display. Shipping 2023-era M3 looks dated.

**Spacing & layout:**

- Base unit: **4dp**. Major grid: **8dp** — all screen margins, section gaps, card padding are multiples of 8. Micro grid: **4dp** — internal icon-to-text, chip gaps.
- Screen margin: **16dp** mobile, **24dp** tablet. Content max-width: **600dp** centered on tablet.
- Vertical rhythm: **8 / 16 / 24 / 32 / 48 / 64**. Never use 5, 7, 10, 15, etc.
- New code takes spacing **only** from the Spacing object: `s4, s8, s12, s16, s24, s32, s48, s64`.

**Shapes (corner radius system):**

| Token | Radius | Use |
|-------|--------|-----|
| extraSmall | 4dp | Text-field top, chips, small containers |
| small | 8dp | Default for cards, menus; low emphasis |
| medium | 12dp | Cards, dialogs, sheets; medium emphasis |
| large | 16dp | Large cards, FAB; medium-emphasis interactive/expandable |
| extraLarge | 28dp | Bottom sheets, large dialogs, hero containers |
| full | 100% / pill | Buttons, FABs, toggle track, active nav indicator |

- **Nested rule:** child radius = parent radius − padding (e.g. 28dp sheet + 12dp padding = 16dp inner card).
- **M3 Expressive:** Cookie4Sided, Clover, Squircle, Arch, etc. for loaders, FAB morph, button press morph. Buttons morph pill → 12dp + scale 0.96f on press with `Spring.StiffnessHigh` (pressed shape per the Button Shape Gospel, §8.6).

**Typography (M3 type scale):**

Font: **Shipopotamus Sans**, falling back to Roboto Flex / Google Sans Flex variable.

| Style | Size / Line height / Letter spacing | Weight |
|-------|--------------------------------------|--------|
| displayLarge | 57sp / 64sp / −0.25sp | 400 — hero only |
| displayMedium | 45sp / 52sp / 0 | 400 |
| displaySmall | 36sp / 44sp / 0 | 400 |
| headlineLarge | 32sp / 40sp / 0 | 400 |
| headlineMedium | 28sp / 36sp / 0 | 400 |
| headlineSmall | 24sp / 32sp / 0 | 400 |
| titleLarge | 22sp / 28sp / 0 | SemiBold (500–600) — card titles, top bar; emphasized in Expressive |
| titleMedium | 16sp / 24sp / 0 | Medium (500) — list titles, buttons |
| titleSmall | 14sp / 20sp / 0 | Medium (500) |
| labelLarge | 14sp / 20sp / 0 | Medium (500) — **button labels; all buttons use labelLarge** |
| labelMedium | 12sp / 16sp / 0 | Medium (500) |
| labelSmall | 11sp / 16sp / 0 | Medium (500) |
| bodyLarge | 16sp / 24sp / 0 | 400 — main reading |
| bodyMedium | 14sp / 20sp / 0 | 400 — secondary reading; default text-field input |
| bodySmall | 12sp / 16sp / +0.15 | 400 — captions, supporting text |

- **Dark-mode astigmatism rule:** Never weight 300 (Light) on dark. Use 400–500 for body, 600 for emphasis. Letter spacing +0.15 for bodySmall.

**Elevation (dark mode):**

Do NOT use drop shadows in dark. Use tonal elevation over the palette surfaces:

| Level | Surface | Tint |
|-------|---------|------|
| 0dp | bg-base `#0D0E11` | No tint |
| 1dp | surface-1 `#181A20` | 5% primary tint overlay |
| 3dp | surface-2 `#1F222A` | 8% tint — cards on hover/focus |
| 6dp | surface-3 `#272A33` | 11% tint — bottom sheets, dialogs |

Use `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest` tokens. In Compose: `surfaceTint = accent`.

**Component specs (defaults):**

- **Buttons:** Height **40dp**, shape **full pill**, label = **labelLarge**, padding 24dp text-only / 16dp with icon leading, icon 18dp, gap 8dp. Emphasis order high→low: Filled → Tonal → Elevated → Outlined → Text.
  - Filled: container accent `#7A7CFF`; for mobile primary CTA use Large **56dp** height (64dp in Expressive auth). Min touch 48×48 enforced.
  - Tonal: container accent-muted (`rgba(122,124,255,0.15)`), on-container text-primary.
  - Elevated: surface-1 + 1dp elevation, for separation from bg.
  - Outlined: 1dp border `#2A2E39`, no fill.
  - Text: 12dp horizontal padding, no container.
  - Icon Button: 40×40 + 8dp padding, icon 24dp.
  - Segmented / ToggleButton: height 40dp, padding 24dp; selected uses tonal container.
  - Press state: morph corner 50% → 12dp + scale 0.96f + ripple. Use Spring. Button shape language per §8.6: full pill by default; the square variant (12dp medium / 16dp large) only for toolbar/secondary/dense contexts — never mixed randomly in the same row; one screen = one button shape language.
- **FAB:** Small 40dp, Medium 56dp, Large 96dp (Expressive). Shape large 16dp or full for extended. Container accent, icon 24dp. Extended FAB: height 56dp, labelLarge + 16dp icon gap.
- **Text fields:** Default container height **56dp**, top/bottom padding 8dp, left/right padding 16dp without icons / 12dp with icons, icon-to-text gap 16dp, supporting-text top gap 4dp, counter gap 16dp.
  - Filled: container surface-2 `#1F222A`, shape extraSmall top only (4dp top radius, 0 bottom), active indicator 2dp accent, inactive 1dp border.
  - Outlined: container transparent, outline 1dp border `#2A2E39`, focused 2dp accent, shape extraSmall 4dp all corners, label padding 4dp left/right.
  - Input text: bodyLarge 16sp. Label: bodySmall → floats to labelSmall. Supporting text: bodySmall 12sp, top padding 4dp.
  - Never use pure white label; use text-secondary `#A1A5B0`.
- **Cards:** Elevated = surface-1 + 1dp elevation + surface tint. Filled = surface-2 `#1F222A` flat. Outlined = transparent + 1dp border `#2A2E39`. All: shape medium 12dp default, large 16dp for hero cards. Content padding 16dp. On click: ripple + elevate to 3dp. Nested content: inner radius = 12dp − 8dp padding = 4dp.
- **Chips:** All height **32dp**, label = labelLarge, horizontal padding 16dp, icon gap 8dp. Assist: shape small 8dp, outline 1dp. Filter: shape small, check icon 18dp, selected = tonal container. Input: avatar 24dp + label + close 18dp. Suggestion: tonal.
- **Switch / Toggle:** Track height **32dp**, width **52dp**, shape full pill; thumb **24dp**. Inactive track `#2A2E39`, active track accent `#7A7CFF`, thumb white. Touch target auto-expands to 48dp.
- **Checkbox / Radio:** Box 20dp visible, 48dp touch target auto-expanded. Shape small 4dp for checkbox, full for radio. Active accent.
- **Slider:** Track 4dp height, active track accent, inactive `#2A2E39`, thumb 20dp, halo 40dp on press. Touch target 48dp height.
- **Bottom sheet / Dialog:** Bottom sheet: shape extraLarge 28dp top only, container surface-3 `#272A33` at 6dp elevation + 11% tint, drag handle 32×4dp `#6E7280`, scrim `rgba(13,14,17,0.80)`. (A Haze sheet instead follows §8.7: `hazeChild` + `ShippopotamusHazeSheet` + surface-3 tint, no opaque scrim.) Dialog: shape extraLarge 28dp, container surface-3, padding 24dp, button-row gap 8dp, title titleLarge 22sp.
- **Navigation:** Navigation Bar (mobile): height **80dp**, 3–5 items, active indicator 32dp height full pill shape, icon 24dp, label labelMedium 12sp, container **glass per §8.7** (always glass, never solid — supersedes the earlier solid surface-1 container). Navigation Rail (tablet): width 80dp, same active indicator. TopAppBar: Small 64dp height with titleLarge; Medium 112dp; Large 152dp (Expressive uses bigger, heavier displayMedium for large) — transparent at rest, glass when scrolled, per §8.7.
- **List item:** One-line height 56dp, two-line 72dp, three-line 88dp. Leading icon 24dp, padding 16dp, gap 16dp. Title titleMedium, subtitle bodyMedium in text-secondary.

**Accessibility & motion (non-negotiable):**

- **Touch:** 48×48dp minimum for EVERY interactive. Use `Modifier.minimumInteractiveComponentSize()`. RadioButton/checkbox touch areas expand to 48×48 even if the visible glyph is smaller.
- **Text:** bodyLarge 16sp is the readable default with proper weight.
- **Motion:** M3 Expressive default = `MotionScheme.expressive()` — spring physics, not FastOutSlowIn. Standard easing: emphasized (hero), standard (containers), 300ms–500ms. Shape morphing is a first-class interaction primitive.
- **Haptics:** Light click on toggle/switch, medium on FAB press — use `HapticFeedback`.

**Theme implementation mapping (canonical `darkColorScheme`):**

```text
surface = #0D0E11 (bg-base)
surfaceContainer = #181A20 (surface-1)
surfaceContainerHigh = #1F222A (surface-2)
surfaceContainerHighest = #272A33 (surface-3)
surfaceTint = #7A7CFF
primary = #7A7CFF (accent)
outline = #2A2E39 (border-default)
+ the full palette hexes in §8.2
Typography = the §8.3 scale (titleLarge SemiBold 22/28, labelLarge Medium 14/20, bodyLarge 16/24, …)
Shapes = (extraSmall 4dp, small 8dp, medium 12dp, large 16dp, extraLarge 28dp) RoundedCornerShape
Component defaults: buttons 40dp (56dp CTA) CircleShape, contentPadding 24dp, labelLarge;
text fields min height 56dp, top-4dp shape; cards 12dp + 1dp tonal; switch track 32dp / thumb 24dp;
minimumInteractiveComponentSize everywhere; Spacing object only.
```

### 8.4 Reconciliation register — Enclave today vs the gospel

**Already aligned (no conflict):**

- **Semantic-token architecture.** Enclave routes all feature color through semantic spec tokens (`EnclaveThemeSpec`, §1.4) and bans hex literals in `:feature:*`/`:app` (Detekt-enforced). The gospel is likewise a token system — the enforcement mechanism the gospel assumes already exists here.
- **Anti-halation intent.** `Color.kt`'s dark palette was already chosen to minimize halation (lifted charcoal background, muted off-white text) — the same empirical basis as §8.2, at different values (see conflict C2/C3).
- **48dp touch targets.** `EnclaveNeoTokens.trailingActionSize = 48.dp`, and Material components expand hit areas; the gospel's 48dp law is consistent with existing practice.
- **Tonal family elevation.** `TonalSpec` uses no hard shadow in dark (`useHardShadow = false`), matching the gospel's tonal-elevation direction for that family.
- **Panel radius.** `tonalShapes().panel = 12.dp` equals the gospel's medium card radius; tonal chip radius 8.dp equals gospel small.
- **Dark-first.** Enclave is dark-first (§1), matching the gospel's dark-only scope for the canonical palette.
- **Scrim/overlay tokens.** Gospel `bg-scrim` and `surface-overlay` have no Enclave equivalents — pure additions, no conflict (defined in code by ENC-87).

**Conflicts (gospel wins for new work; existing behavior is preserve-mode until Sean ratifies migration):**

| # | Area | Enclave today | Gospel (§8) |
|---|------|---------------|-------------|
| C1 | Primary accent | Dark: peach `#F4A361` (both families; §1.3 accent lock). Light: purple `#7E22CE`. (§1.1 also documents a stale `#A855F7`.) | Indigo family, accent-base `#7A7CFF` (hover `#8D8FFF`, active `#5F62B9`, deep `#3B3BAA`) |
| C2 | Dark surfaces | Zinc ramp: bg `#18181B`, surface `#27272A`, surfaceVariant `#3F3F46` (`Color.kt`) | `#0D0E11` / `#181A20` / `#1F222A` / `#272A33` five-step lightness ramp |
| C3 | Text ramp | Two steps: `#E4E4E7` primary, `#A1A1AA` muted | Five steps: `#EDEEF0` / `#C5C7DD` / `#A1A5B0` / `#858994` / `#6E7280` |
| C4 | Borders | Dark outline `#52525B`; Neo border `#35353B` | `#2A2E39` default / `#343A48` strong / `#20222E` subtle |
| C5 | Pure black / white | Neo hard shadow is `#000000`; light theme bg `#FFFFFF`; `OnErrorWhite`; tonal glass tints use black/white | Banned — warm off-black/off-white only (§8.2 rationale 1 & 4) |
| C6 | Light themes + Dynamic Color | Color modes System/Light/Dark (§1.4) and a Dynamic Color mandate (Strict Boundaries) | §8 palette is dark-only and fixed; a fixed canonical palette conflicts with Dynamic Color |
| C7 | Hard shadows & Neo geometry | Neo family: 4dp corners, 2dp borders, hard offset shadows (§1.3, `NeoBrutalistSpec`, `EnclaveNeoTokens`) | No drop shadows in dark; tonal elevation only; shape scale 4/8/12/16/28; 1dp borders (tonal) |
| C8 | Typography | `Type.kt`: Bold headlines (28/24/22), titleLarge Bold 20/28, labelLarge Bold; bodyLarge lineHeight 24sp matches but `LlmBodyTextStyle` uses 26sp; IBM Plex Sans + Source Serif 4 | M3 scale with 400 headlines, SemiBold titleLarge 22/28, Medium labels; Shipopotamus Sans / Roboto Flex; bodySmall +0.15 letter spacing |
| C9 | Spacing system | No Spacing object; `EnclaveNeoTokens` carries ad-hoc values (20dp bubble padding, 6dp shadow, 3dp tail) | Spacing object only (s4–s64); rhythm 8/16/24/32/48/64; "never 5, 7, 10, 15" |
| C10 | Component metrics | Tonal buttons 12dp rounded rect (not pill, no 40/56dp heights specified); no switch/slider/nav-bar metric specs; chips defined by radius only | Pill buttons 40dp (56dp CTA); chips 32dp; switch 52×32dp, thumb 24dp; nav bar 80dp + 32dp pill indicator; fields 56dp |
| C11 | Input bar (locked §3) | Pill 28dp radius, text 16sp/22sp line height | Field spec: 56dp height, extraSmall top radius, bodyLarge input |
| C12 | Motion policy | §1.3: functional motion only; no cinematic/decorative motion | `MotionScheme.expressive()` springs + shape morphing as a first-class primitive |
| C13 | Glass / blur | `TonalSpec` glass bars (blur + tint) enabled | Opaque tonal surfaces; no blur treatment in the elevation model |
| C14 | Semantic colors | `ErrorRed #DC2626` (high vibrancy), `SuccessGreen #4CAF50`, provider tiers `#EF4444/#F97316/#EAB308/#22C55E`, §1 shield-red `Color.Red` exception | Desaturated ≤60%: success `#7DBE8C`, warning `#D9B07A`, danger `#E07A7A`, info `#7BB5D6` |

**Pre-existing drift (noted, not introduced by ENC-87):** §1.1 documents background `#000000` / primary `#A855F7`, which no longer match `Color.kt` (zinc ramp, peach/purple accents). §1.1 is legacy documentation of record for the pre-spec theme; §8.2 is the canonical palette going forward.

### 8.5 Where the gospel lives in code

- Canonical token definitions: `:core:ui` theme package, `ShippopotamusTokens.kt` (`ShippopotamusPalette`, `ShippopotamusSpacing`, `ShippopotamusShapeScale`/`ShippopotamusShapes`, `ShippopotamusTypography`, `ShippopotamusComponentMetrics`) — exact gospel values, guarded by `ShippopotamusTokensTest`.
- ENC-87 deliberately does **not** change the values in `Color.kt`, `TonalSpec.kt`, `NeoBrutalistSpec.kt`, `Type.kt`, or `EnclaveNeoTokens.kt`: those drive the shipped dual-family themes, and rewriting them would redesign every existing screen. They are preserve-mode under §8.4 until Sean ratifies the register and migration sub-issues re-point the specs at the gospel tokens.

### 8.6 Button Shape Gospel — Canonical (2026-10-20)

Declared canonical by Sean, 2026-10-20, for Bloviate / Stratum / Enclave and all future Shippopotamus apps. Embedded here **in full** under Sean's direction, like §8.2/§8.3: this contract is self-contained. This section governs button shape specifically and supersedes §8.3 wherever they disagree on button shape or button press morph.

**Default for Bloviate / consumer: FULL PILL**

- Shape: `CircleShape` / 9999px / full token (`ShippopotamusButtonShape`).
- Why: empirically friendliest, highest tap recognition, matches Google Phone, Gboard, etc. More rounded corners + pill buttons are the M3 signature.

**Allowed variant for toolbars / secondary / dense enterprise: SQUARE**

- Shape: `RoundedCornerShape(12.dp)` for medium buttons, `RoundedCornerShape(16.dp)` for large (`ShippopotamusButtonShapeSquare`).
- When to use: inside a card that already has 12dp radius — use an 8dp inner button (nested radius = outer − padding); data-dense toolbar, filter bar, table actions; when you want the Linear/Raycast serious vibe vs. bubbly consumer.

**Rules (non-negotiable)**

- Never mix randomly in the same row. One screen = one button shape language.
- If you use the square variant, it still morphs on press to the same pressed shape as the pill variant: `ShippopotamusPressedShape = RoundedCornerShape(12.dp)`.

**Compose implementation**

```kotlin
val ShippopotamusButtonShape = CircleShape // DEFAULT PILL
val ShippopotamusButtonShapeSquare = RoundedCornerShape(12.dp) // ALLOWED VARIANT (medium; 16.dp for large)
val ShippopotamusPressedShape = RoundedCornerShape(12.dp) // Both morph to this on press

Button(
  shape = ShippopotamusButtonShape,
  modifier = Modifier.minimumInteractiveComponentSize() // 48dp enforced
) { Text("Primary", style = MaterialTheme.typography.labelLarge) }

// Expressive press morph
val corner by animateDpAsState(if (pressed) 12.dp else 100.dp, spring(StiffnessHigh))
val scale by animateFloatAsState(if (pressed) 0.96f else 1f)
```

**Register note (vs §8.4):** Enclave's shipped Tonal button is `RoundedCornerShape(12.dp)` (`EnclaveThemeSpec.kt`) — recorded in C10 as a conflict with the pill default. Under this gospel that 12dp geometry is exactly the **allowed square variant**, which fits Enclave's dense/tool character; it is not, by itself, the consumer pill default. C10 otherwise stands (heights and the rest of the component metrics are unchanged), existing screens remain preserve-mode under §8.4, and whether Enclave's Tonal family formally adopts the square variant as its screen language — vs. migrating to pill — is Sean's call on ratification, not settled by this embedding. The Neo family's button geometry stays under its existing family rules. Wiring the `Shippopotamus*` button-shape tokens in `:core:ui` is implementation work under a future ticket — this amendment changes the contract, not shipped screens.

### 8.7 Glass Bars Gospel — Top + Bottom + Haze (2026-10-20)

Declared canonical by Sean, 2026-10-20, for Bloviate / Stratum / Enclave and all future Shippopotamus apps. Uses **Haze 2.0** (Chris Banes); Android 15+ edge-to-edge enforced. Embedded here **in full** under Sean's direction, like §8.2/§8.3/§8.6: this contract is self-contained. This section governs bars, sheets, and overlay glass for new work and supersedes §8.3 wherever they disagree on those surfaces.

**Dependency**

```kotlin
implementation("dev.chrisbanes.haze:haze:2.0.1") // core blur
implementation("dev.chrisbanes.haze:haze-glass:2.0.1") // refraction Glass (optional, for hero)
```

Haze provides hardware-accelerated visual effects — blur, refraction-driven Glass, and custom effects with reusable config.

**Foundation — edge-to-edge (required for glass to work)**

- Activity: `enableEdgeToEdge()` — status + gesture nav are transparent, content draws behind.
- Scaffold: `contentWindowInsets = WindowInsets(0,0,0,0)` — insets are handled manually.
- Main body: `Modifier.hazeSource(state)` + `.windowInsetsPadding` + `.consumeWindowInsets`.

**Reusable styles — one `HazeState` per screen, shared; don't inline magic numbers**

```kotlin
val hazeState = rememberHazeState()

val ShippopotamusHazeTopBar = HazeStyle.Unspecified.copy(
  blurRadius = 24.dp, // 20–28dp for top bar — enough to read title over photos
  tint = Color(0xFF0D0E11).copy(alpha = 0.65f), // bg-base #0D0E11 at 65%
  noiseFactor = 0.5f, // micro-texture prevents banding on OLED
)

val ShippopotamusHazeBottomBar = HazeStyle.Unspecified.copy(
  blurRadius = 20.dp,
  tint = Color(0xFF181A20).copy(alpha = 0.75f), // surface-1 #181A20 at 75%
  noiseFactor = 0.5f
)

val ShippopotamusHazeSheet = HazeStyle.Thick // preset: heavy blur for bottom sheets
```

Usage pattern: background scrollable = `hazeSource`; bars = `hazeChild`; **never put haze behind body text directly — behind containers only.**

**Top bar (TopAppBar)** — Height small 64dp (default), medium 112dp, large 152dp for hero. Container TRANSPARENT when at top (scroll offset 0), becomes glass when scrolled. Shape: none — full width, no rounded corners. Content: title `titleLarge`, icons 24dp, padding 16dp. Border: none — blur provides separation. When scrolled: glass + subtle 1dp bottom hairline #2A2E39 at 40%, or borderless for pure glass. Content scrolls underneath, blurred — movement is visible but the title stays readable.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShippopotamusTopBar(
  title: String,
  hazeState: HazeState,
  scrollBehavior: TopAppBarScrollBehavior
) {
  val isScrolled = scrollBehavior.state.overlappedFraction > 0.01f

  TopAppBar(
    title = { Text(title, style = MaterialTheme.typography.titleLarge) },
    navigationIcon = { IconButton({}) { Icon(Icons.Default.Menu, null) } },
    actions = { IconButton({}) { Icon(Icons.Default.Search, null) } },
    scrollBehavior = scrollBehavior,
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = Color.Transparent, // transparent when not scrolled
      scrolledContainerColor = Color.Transparent // we handle via haze
    ),
    modifier = Modifier
      .hazeChild(
        state = hazeState,
        style = if (isScrolled) ShippopotamusHazeTopBar else HazeStyle.Unspecified.copy(blurRadius = 0.dp)
      )
      .then(
        if (isScrolled) Modifier.drawWithContent {
          drawContent()
          drawLine(Color(0xFF2A2E39).copy(0.4f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        } else Modifier
      )
  )
}

// In screen:
val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior() // or enterAlwaysScrollBehavior()
Scaffold(
  modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
  topBar = { ShippopotamusTopBar("Enclave", hazeState, scrollBehavior) },
  containerColor = Color(0xFF0D0E11),
  contentWindowInsets = WindowInsets(0,0,0,0)
) { padding ->
  LazyColumn(
    Modifier
      .fillMaxSize()
      .hazeSource(state = hazeState, zIndex = 0f) // zIndex 0 = source
      .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
      .padding(padding)
  ) { /* main body passes underneath top bar */ }
}
```

**Bottom bar (NavigationBar)** — Height 80dp (68dp tighter is allowed, 80dp is canonical). Container ALWAYS glass — never solid; translucent tint + blur so content scrolls under. Shape: none for the bar; active indicator = full pill 32dp height, accent #7A7CFF at 15% fill. Icons 24dp, label `labelMedium`. Inactive #858994; active #EDEEF0 + accent indicator. Border: 100% borderless — blur is the separation. Gesture nav: `windowInsetsPadding(WindowInsets.navigationBars)` + `navigationBarsPadding` on content so the last item is not hidden behind the system pill. The bottom bar shares the screen's `hazeState` with the top bar so both blur the same source.

```kotlin
@Composable
fun ShippopotamusBottomBar(hazeState: HazeState) {
  var selected by remember { mutableStateOf(0) }

  NavigationBar(
    containerColor = Color.Transparent, // haze handles color
    tonalElevation = 0.dp,
    modifier = Modifier
      .hazeChild(
        state = hazeState,
        style = ShippopotamusHazeBottomBar
      )
      .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
  ) {
    listOf("Home", "Search", "Library").forEachIndexed { i, label ->
      NavigationBarItem(
        selected = i == selected,
        onClick = { selected = i },
        icon = { Icon(Icons.Default.Home, null) },
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = NavigationBarItemDefaults.colors(
          selectedIconColor = Color(0xFFEDEEF0),
          selectedTextColor = Color(0xFFEDEEF0),
          indicatorColor = Color(0xFF7A7CFF).copy(alpha = 0.15f), // accent-muted
          unselectedIconColor = Color(0xFF858994),
          unselectedTextColor = Color(0xFF858994)
        )
      )
    }
  }
}
```

**Where else to use Haze**

1. Bottom sheet: sheet container = `hazeChild` with `ShippopotamusHazeSheet` + surface-3 #272A33 tint. Content underneath stays visible blurred — elevated without an opaque scrim.
2. FAB over feed: extended FAB that blurs the feed behind it — small blur, 12dp.
3. Image overlays: hero image with a glass chip (e.g. a "Live" badge) — haze-glass with refraction for liquid glass.

**Performance:** Haze 2.0 adaptive rendering quality automatically drops quality on a low-end GPU. Keep `blurRadius <= 28dp`; avoid stacking more than 2 `hazeChild`s in the same frame.

**Register note (vs §8.4):** C13 recorded Enclave's Tonal glass bars as a conflict with the gospel's opaque-tonal elevation model. This gospel **resolves C13 in the glass direction for bars/sheets/overlays**: glass there is now canonical, not drift. Enclave's shipped glass (ENC-33: spec-driven `EnclaveGlass` affordance, Tonal translucent / Neo opaque, Studio/Gallery wiring) aligns in direction, but it runs on **haze 1.7.2** with the 1.x API — adopting this gospel in code means a **Haze 2.0.1 bump + `hazeSource`/`hazeChild` API migration**, which is implementation work under a future ticket, not a drop-in and not done by this amendment. Body/content surfaces remain opaque tonal under §8.3, existing screens remain preserve-mode under §8.4 except where C13 is resolved above, and the Neo family stays opaque per its spec-driven glass affordance. The top-bar title consumes the theme's `titleLarge` (§8.3 scale); Sean's pasted spec line saying "Medium" is superseded by that scale.
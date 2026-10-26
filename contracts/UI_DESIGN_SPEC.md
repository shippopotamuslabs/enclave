# UI_DESIGN_SPEC.md
**Enclave – UI Design Contract v2.9**

UI contract. This defines exact visual behavior, component structure, and interaction patterns.

Always enforce together with `ARCHITECTURE_RULES.md` (which defines the structural Scaffold/navigation/IME patterns) and `DESKTOP_RULES.md` §6–§7 for the shared neo-brutalist token model and agent UI workflow on desktop.

> **v2.6 change (ENC-1/ENC-5, user-authorized contract update):** Enclave moves from neo-only chrome to **dual theme families** — Material Tonal (normal Material 3) and Neo-Brutalist — sharing one semantic spec and one neutral component API, adapted from Stratum `contracts/UI_DESIGN.md`. The neutral `Enclave*` components and spec bundle landed in ENC-6/ENC-8; locked chat/input shapes in §2–§3 remain preserve-mode until an ENC sub-issue explicitly migrates them.

> **v2.7 change (ENC-87, under Sean's explicit 2026-10-19 Contract Waiver for this adoption):** the **Shippopotamus Palette Gospel** and **Shippopotamus Compose Gospel** are embedded in full as §8 and are **canonical for all new UI work** in Enclave (and across Shippopotamus apps — Bloviate, Stratum, Enclave). The pre-existing dual-family specs in §1.1–§1.4 are **not** rewritten by this change: they remain preserve-mode until Sean ratifies the §8.4 conflict register and migration sub-issues land. Where §8 and §§1–7 conflict, §8 wins for new work; §8.4 lists every known conflict.

> **v2.8 change (2026-10-20, under Sean's explicit Contract Waiver for this adoption):** the **Shippopotamus Button Shape Gospel**, declared canonical by Sean on 2026-10-20 for Bloviate / Stratum / Enclave, is embedded in full as §8.6. It governs button shape for new work and supersedes §8.3 on one point: the button press morph now lands at **12dp** (was 16dp) for both the pill default and the square variant. Sean's pasted source spelled the tokens `Shipopotamus*` (one p); the canonical spelling is `Shippopotamus*`, used throughout §8.6.

> **v2.9 change (2026-10-20, under Sean's explicit Contract Waiver for this adoption):** the **Shippopotamus Glass Bars Gospel** (Haze 2.0), declared canonical by Sean on 2026-10-20 for Bloviate / Stratum / Enclave, is embedded in full as §8.7. It governs bars, sheets, and overlay glass for new work: the top bar is **always glazed** (at rest and scrolled alike), and the bottom bar is **always glass, never solid**. It resolves §8.4 conflict **C13 in the glass direction** (see §8.7's register note) and supersedes §8.3's solid nav-container and opaque-sheet treatments for those surfaces. Sean's pasted source spelled the tokens `Shipopotamus*` (one p); the canonical spelling is `Shippopotamus*`, used throughout §8.7.

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

Canon: `contracts/gospel/PALETTE.md` (Tokens: bg-base `#0D0E11`, accent-base `#7A7CFF`) and `contracts/gospel/PALETTE-LIGHT.md` (Tokens).

**Preserve-mode note:** The values previously listed here — dark background `#000000` (AMOLED black), surface `#121212`, surfaceVariant `#1F1F1F`, primary `#A855F7`; light background `#FCFCFC`, surface `#F8F9FA`, primary `#A855F7` — are the legacy pre-spec theme of record. They are superseded for new work by the gospel palettes above; §8.4 (conflicts C1-C5 and its pre-existing-drift note) records them as the "Enclave today" side of the register. Existing surfaces stay preserve-mode until Sean ratifies §8.4.

### 1.2 Typography

Canon: `contracts/gospel/COMPOSE-GOSPEL.md` (Typography, M3 scale).

**Preserve-mode note:** The legacy local spec — SemiBold headlines at 24-28sp and Normal 16sp body text, with the Bold-headline / IBM Plex Sans usage recorded in §8.4 conflict C8 — governs existing surfaces only. New work follows the gospel M3 scale.

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

This section covers the Shippopotamus gospels declared canonical by Sean on 2026-10-19: the **Color Palette Gospel** (official dark theme) and the **Android Jetpack Compose Gospel** (official UI system), plus the Button Shape and Glass Bars gospels added 2026-10-20. They are the canonical UI system for **all** Shippopotamus apps (Bloviate, Stratum, Enclave, and future products). The full texts live in the `contracts/gospel/` git submodule (the `shippopotamuslabs/gospels` repo, pinned at `963fdf4` (`v1.1-3-g963fdf4`)) — §8.2, §8.3, §8.6, and §8.7 below are pointers to the gospel files, so the system is enforced by whoever works in this repo, with whatever tooling. (Adoption: GLO-14; the texts were previously embedded here in full under ENC-87/ENC-134.)

### 8.1 Authority and scope

- **Authority:** Sean's directive of 2026-10-19 is the Contract Waiver authorizing this contract edit (ENC-87). Sister adoptions: Bloviate ENC-85/ENC-86, Stratum STR-137.
- **Scope:** §8 governs **all new UI work** — new screens, new components, new tokens, and any surface being materially restyled. Existing Enclave surfaces built on the §1.1–§1.4 dual-family specs stay **preserve-mode**: they do not change by virtue of this section alone. Migrating an existing surface or family to §8 requires Sean's ratification of the relevant §8.4 conflicts plus a dedicated migration sub-issue.
- **Precedence:** Where §8 conflicts with §§1–7, **§8 wins for new work**. §8.4 is the register of every known conflict; do not silently resolve a registered conflict in feature code — escalate it.
- **Platform:** §8 is Android/Compose only. The desktop app (`DESKTOP_RULES.md`, Iced/neobrutalism) is out of scope.

### 8.2 Palette Gospel — Official Dark Theme

**Canonical text:** `contracts/gospel/PALETTE.md` (git submodule, gospels repo pinned at `963fdf4` (`v1.1-3-g963fdf4`)).

The full Palette Gospel — the empirical basis, the background/border/text token tables, the indigo accent family, the semantic colors, and the mobile extras — lives in the gospel file. This contract no longer embeds it.

The Light Palette Gospel, `contracts/gospel/PALETTE-LIGHT.md`, also applies via the gospel repo. Enclave carried no local copy of the light palette before this adoption; §8.4 conflict C6 (light themes + Dynamic Color) is unchanged.

### 8.3 Compose Gospel — Official UI System

**Canonical text:** `contracts/gospel/COMPOSE-GOSPEL.md` (git submodule, gospels repo pinned at `963fdf4` (`v1.1-3-g963fdf4`)).

The full Compose Gospel — the 8dp grid, the shape system, the M3 typography scale, tonal elevation, the component specs, the accessibility and motion rules, and the Theme.kt shape — lives in the gospel file. This contract no longer embeds it. Where §8.3 and §8.6/§8.7 used to cross-refer, the gospels cross-refer the same way: button shape defers to the Button Shape Gospel (§8.6), bars and sheets to the Glass Bars Gospel (§8.7).

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

**Canonical text:** `contracts/gospel/BUTTON-SHAPE-GOSPEL.md` (git submodule, gospels repo pinned at `963fdf4` (`v1.1-3-g963fdf4`)).

Declared canonical by Sean, 2026-10-20, for Bloviate / Stratum / Enclave and all future Shippopotamus apps. The full Button Shape Gospel — the FULL PILL default, the allowed SQUARE variant, the press-morph rules, and the Compose implementation — lives in the gospel file. This section governs button shape specifically and supersedes §8.3 wherever they disagree on button shape or button press morph.

**Register note (vs §8.4):** Enclave's shipped Tonal button is `RoundedCornerShape(12.dp)` (`EnclaveThemeSpec.kt`) — recorded in C10 as a conflict with the pill default. Under this gospel that 12dp geometry is exactly the **allowed square variant**, which fits Enclave's dense/tool character; it is not, by itself, the consumer pill default. C10 otherwise stands (heights and the rest of the component metrics are unchanged), existing screens remain preserve-mode under §8.4, and whether Enclave's Tonal family formally adopts the square variant as its screen language — vs. migrating to pill — is Sean's call on ratification, not settled by this embedding. The Neo family's button geometry stays under its existing family rules. Wiring the `Shippopotamus*` button-shape tokens in `:core:ui` is implementation work under a future ticket — this amendment changes the contract, not shipped screens.

### 8.7 Glass Bars Gospel — Top + Bottom + Haze (2026-10-20)

**Canonical text:** `contracts/gospel/GLASS-BARS-GOSPEL.md` (git submodule, gospels repo pinned at `963fdf4` (`v1.1-3-g963fdf4`)).

Declared canonical by Sean, 2026-10-20, for Bloviate / Stratum / Enclave and all future Shippopotamus apps. The full Glass Bars Gospel — the Haze 2.0.1 dependency, the edge-to-edge foundation, the reusable Haze styles, the worked top-bar and bottom-bar implementations, and the performance limits — lives in the gospel file. This section governs bars, sheets, and overlay glass for new work and supersedes §8.3 wherever they disagree on those surfaces.

**Register note (vs §8.4):** C13 recorded Enclave's Tonal glass bars as a conflict with the gospel's opaque-tonal elevation model. This gospel **resolves C13 in the glass direction for bars/sheets/overlays**: glass there is now canonical, not drift. Enclave's shipped glass (ENC-33: spec-driven `EnclaveGlass` affordance, Tonal translucent / Neo opaque, Studio/Gallery wiring) aligns in direction, but it runs on **haze 1.7.2** with the 1.x API — adopting this gospel in code means a **Haze 2.0.1 bump + `hazeSource`/`hazeChild` API migration**, which is implementation work under a future ticket, not a drop-in and not done by this amendment. Body/content surfaces remain opaque tonal under §8.3, existing screens remain preserve-mode under §8.4 except where C13 is resolved above, and the Neo family stays opaque per its spec-driven glass affordance. The top-bar title consumes the theme's `titleLarge` (§8.3 scale); Sean's pasted spec line saying "Medium" is superseded by that scale.

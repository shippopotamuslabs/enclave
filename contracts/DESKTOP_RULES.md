# DESKTOP_RULES.md
**Enclave – Desktop Architecture, Design, & Clean Contracts v2.1**

Non-negotiable architectural, layering, styling, and coding rules for the `/desktop` workspace (including domain, data, inference, and presentation layers).

Always enforce together with the repo-wide `AGENTS.md` contract routing.

---

## Strict Boundaries (Clean Architecture & Ports-and-Adapters)

| Always | Never |
|--------|-------|
| Keep `enclave-desktop-domain` free of UI, persistence, and external frameworks. | Direct import of `enclave-desktop-data` or `enclave-desktop-inference` into `enclave-desktop-domain`. |
| Pass data through domain repository/inference traits (Ports) defined in the domain layer. | Instantiate concrete storage implementations or cloud API clients directly in the UI layer. |
| Use the Elm Architecture (Model-View-Update / MVU) for the presentation layer (`enclave-desktop`). | Put business logic, side effects, or persistence operations directly inside view functions. |
| Handle blocking IO (heavy files, synchronous API clients) in Tokio blocking threads. | Block the main Iced UI loop with synchronous or blocking calls. |
| Map database/persistence models to Domain models strictly inside the Data layer. | Let database/persistence-specific data structures leak into the Domain or Presentation layers. |
| Use custom Neobrutalist widgets (`theme.rs`) for UI consistency. | Mix ad-hoc layout styles or custom colors directly inside screen implementations. |
| Run the Brief Read (§7.1) and Anti-Slop Pre-Flight (§7.3) before generating or materially restyling UI. | Ship marketing-landing patterns, industrial/CRT brutalism, glassmorphism, soft shadows, or ad-hoc colors outside `theme.rs`. |
| Extend `NeoContainer`, `NeoButton`, `NeoInput`, and other `theme.rs` styles before inventing one-off styling. | Rebuild surfaces with raw Iced defaults when a neo style already exists. |
| Use structured error handling via `Result` and `thiserror` in all crates. | Use `unwrap()` or `panic!` for runtime error handling. |

---

## 1. Multi-Crate Workspace Layout & Dependency Direction

To physicalize Clean Architecture boundaries and prevent compile-time violations, the `/desktop` codebase is split into four distinct crates in a unified Cargo workspace:

```text
       ┌────────────────────────────────────────────────────────┐
       │                 enclave-desktop                        │
       │  (Presentation Layer: Iced, MVU, Screens, ViewModels)  │
       └───────┬──────────────┬──────────────┬──────────────────┘
               │              │              │
               │              ▼              │
               │  ┌───────────────────────┐  │
               │  │ enclave-desktop-data  │  │
               │  │ (Data Layer: Repos,   │  │
               │  │  Settings, SQLite)    │  │
               │  └───────────┬───────────┘  │
               │              │              │
               ▼              ▼              ▼
       ┌────────────────────────────────────────────────────────┐
       │                 enclave-desktop-domain                 │
       │  (Domain Layer: Core Models, Repo & Inference Ports)   │
       └────────────────────────────────────────────────────────┘
               ▲              ▲
               │              │
               │  ┌───────────┴───────────┐
               │  │ enclave-desktop-infer │
               │  │ (Inference: local &   │
               │  │  cloud model adapters)│
               │  └───────────────────────┘
               │
      (Inwards Dependency Direction)
```

### 1.1 Crate Responsibilities
- **`enclave-desktop-domain`** (Pure Domain): Contains entities/models (`ChatMessage`, `ChatSession`, `DesktopSettings`) and abstract traits/ports (`ChatRepository`, `SettingsRepository`, `InferenceService`).
  - *Constraint:* Zero dependencies on other desktop crates, UI toolkits, or external DB adapters.
- **`enclave-desktop-data`** (Data Layer): Implements repositories, persistence mechanisms (file storage, local settings file, SQLite databases), and configuration state.
  - *Constraint:* Depends on `enclave-desktop-domain`. Never import UI components or presentation concerns.
- **`enclave-desktop-inference`** (Inference Layer): Implements `InferenceService`. Coordinates local LLM pipelines (via `agent_core` and `docgen_core`) and connects to remote Cloud APIs.
  - *Constraint:* Depends on `enclave-desktop-domain`. Never import UI components.
- **`enclave-desktop`** (Presentation Layer): Serves as the application shell and UI runtime using the `iced` GUI framework.
  - *Constraint:* Depends on `enclave-desktop-domain`, `enclave-desktop-data`, and `enclave-desktop-inference`. 

---

## 2. Presentation Layer Rules (Elm Architecture / MVU)

The presentation crate `enclave-desktop` implements the standard Model-View-Update pattern:

1. **Model (`EnclaveApp`)**: Keeps the immutable state of the app, nested screen structures, and routing histories.
2. **Message (`Message`)**: A strongly typed enum representing all UI events (clicks, keypresses) and asynchronous results.
3. **Update (`update`)**: Centralized state modifier. Given a state and a message, it updates the model and returns an asynchronous `Command<Message>` to perform side effects.
4. **View (`view`)**: A pure function rendering the model state to widgets.

### 2.1 Screen Separation
- Every screen must reside in its own module under `src/screens/[name].rs` (e.g., `chat.rs`, `studio.rs`, `settings.rs`).
- Screen state must be modularized into a struct owned by `EnclaveApp` (e.g., `ChatState`).
- The `view` function for each screen must be a pure layout mapping of the screen's state:
  ```rust
  pub fn view<'a>(state: &'a ChatState, theme: AppTheme) -> Element<'a, Message, Theme, iced::Renderer>
  ```
- Async effects (e.g. triggering database saves, starting inference) are dispatched from the centralized `update` method in `main.rs` via domain repositories, wrapping outputs back into `Message` commands.

---

## 3. Data & Storage Rules

- **Repository Pattern**: All database, local file systems, and configuration variables must be accessed behind domain traits.
- **Entity mapping**: Avoid exposing raw database rows, JSON file models, or serialization details outside of `enclave-desktop-data`. The repository implementation must map data schemas to domain entities using pure mapping functions.
- **Async & Threading Boundaries**:
  - Heavy file serialization, IO writes, or database queries must run on Tokio's async worker threads.
  - For synchronous blocking operations, use `tokio::task::spawn_blocking` to prevent starving the async runtime.
  ```rust
  // Example of spawning blocking IO
  let result = tokio::task::spawn_blocking(move || {
      std::fs::write("settings.json", bytes)
  }).await;
  ```

---

## 4. Inference Rules

- **On-Device First**: Local inference must be handled via `agent_core` model compilation.
- **Cloud API Fallbacks**: Cloud/API configurations (e.g., Gemini, OpenAI, Anthropic endpoints) must remain isolated in `enclave-desktop-inference`.
- **Inference Integration**:
  - The presentation layer communicates requests to the inference port `InferenceService`.
  - The inference engine must process text generation asynchronously, communicating step progression or final tokens via iced streams or message updates.

---

## 5. Coding style, Naming, & Error Handling

- **Error Handling**: 
  - Never utilize `panic!`, `unwrap()`, or `expect()` in production code. Always bubble up issues using `Result<T, E>`.
  - Use `thiserror` in library crates (`domain`, `data`, `inference`) to define context-rich, domain-specific error enums.
- **Naming Conventions**: Follow standard Rust idioms (`snake_case` for variables/functions/files, `PascalCase` for structs/enums/traits).
- **Linter & Formatter**:
  - Keep code strictly warning-free. Always format files with `cargo fmt`.
  - Suppress warnings selectively (e.g. `#![allow(async_fn_in_trait)]`) only if they are due to intentional architectural design choices.
  - Run `./scripts/rustrover-inspect.sh` before completing desktop tasks to catch IDE-only inspections (duplicate code, elidable lifetimes, unused Cargo deps) that `cargo clippy` does not report. Close RustRover first — the headless inspector cannot run while the GUI is open.

---

## 6. Neobrutalist Design System (UI/UX)

The companion desktop app follows a custom **Neobrutalist / Neo-minimalist** styling scheme defined in `theme.rs`.

- **Key Aesthetics**:
  - Thick, stark borders (`width = 2.0`) with dark outlines (`DARK_NEO_BORDER` / `LIGHT_NEO_BORDER`).
  - Solid, offset flat shadows (`offset = 3.0` or `4.0`, increasing to `5.0` on hover, `blur_radius = 0.0`) with zero blur.
  - Accent Color: `COLOR_ACCENT` (Peach `#F5A366` or `Color::from_rgb(0.96, 0.64, 0.38)`).
- **Core Widgets**:
  - Container: Use `NeoContainer` styling.
  - Buttons: Use `NeoButton` styling.
  - Text Input: Use `NeoInput` styling.
  - Scrollable: Use `NeoScrollable` styling.

**Mandate**: Never hardcode colors or borders inside layouts. If custom styling is required, it must be added as a style variant inside `theme.rs` and documented.

**Product neo-brutalism (not industrial/CRT):** Enclave uses friendly product chrome — 4px corner radius, peach `COLOR_ACCENT`, zinc/slate surfaces, hard offset shadows. Do not import Swiss-print hazard red, zero-radius terminal grids, scanlines, ASCII telemetry framing, or landing-page hero layouts.

---

## 7. Agent UI Workflow (Brief Read & Anti-Slop Audit)

This section applies to **agents** generating or restyling Iced UI in `enclave-desktop`. Mirror `UI_DESIGN_SPEC.md` §7 for the shared workflow; token sources here are `theme.rs` instead of `EnclaveNeoTokens`.

### 7.1 Brief Read (before code)

Before writing or restyling UI, infer intent from the task and existing screen modules. Do **not** default to generic Iced styling or web-marketing patterns.

**Read these signals first:**

1. **Surface kind** — chat layout, message list row, input bar, settings panel, popover, studio canvas, empty/loading/error state.
2. **Change mode** — *greenfield*, *extend* (reuse current `theme.rs` styles), or *preserve* (existing screen behavior/layout must not drift).
3. **Scope** — single widget, full screen module under `src/screens/`, or shared widget under `src/widgets/` (promote repeated styling to `theme.rs`).
4. **Constraints** — MVU purity (§2), no blocking IO on the UI thread (§3), existing navigation and `Message` variants.
5. **Existing assets** — icons in `icons.rs`, shared widgets, copy on labels being touched.

**Output a one-line Design Read before generating:**

> *Reading this as: [surface] on [screen], [greenfield | extend | preserve], Enclave neo-brutalist product chrome via [theme.rs style / shared widget].*

Examples:

- *Reading this as: send control on Chat input bar, extend, neo-brutalist via `NeoButton` non-accent + `icons.rs`.*
- *Reading this as: chat message list, preserve, existing bubble/mark layout in `screens/chat/` — spacing only.*

If ambiguous between **preserve** and **overhaul**, ask **once**: *"Should this match existing desktop neo chrome exactly, or are we intentionally changing established screen styling?"*

**Anti-default discipline:** No purple gradient heroes, glass panels, soft blurred shadows, monospace-all-caps terminal chrome, or web landing-section templates in a desktop product surface.

### 7.2 Anti-Slop Audit (before touching existing UI)

When modifying an existing screen, document the current state **before** proposing changes:

| Audit item | What to capture |
|------------|-----------------|
| **Token source** | `DARK_*` / `LIGHT_*` palette constants, `COLOR_ACCENT`, border width 2.0, shadow offsets from `theme.rs`. |
| **Styles in use** | `NeoContainer`, `NeoButton`, `NeoInput`, `NeoScrollable`, `NeoTextEditor`, etc. |
| **MVU boundaries** | State in screen `*State` structs; side effects only via `update` + `Command`. |
| **Hierarchy** | Primary vs secondary actions; one focal control per region. |
| **Spacing rhythm** | Reuse established padding in sibling modules — flag one-off literals. |
| **Patterns to preserve** | Hard borders, flat shadows, peach accent, chat/input behaviors. |
| **Patterns to retire** | Inline `Color::from_rgb` in views, raw `container::Appearance` duplicates, placeholder labels, inconsistent radii. |

**Preservation rules:**

- Do not rename `Message` variants or break navigation flows unless asked.
- Do not rewrite user-facing strings unless asked.
- Promote repeated styling to `theme.rs`; never scatter new color literals across `src/screens/`.

**Modernisation levers (priority order):**

1. Spacing and alignment consistency across the screen module.
2. Route styling through existing `Neo*` `StyleSheet` impls.
3. Unify muted text and surface colors within the locked palette.
4. Hover/press feedback via existing neo button states — no decorative animation loops.

### 7.3 Anti-Slop Pre-Flight (mandatory before TASK_STATUS: COMPLETE)

Run every item for UI-generating desktop tasks. If any box fails, fix before completing.

- [ ] **Design Read** (§7.1) stated in the agent response.
- [ ] **Change mode** correct; MVU rules (§2) unchanged unless explicitly in scope.
- [ ] **Token lock:** colors, borders, and shadows come from `theme.rs` constants or documented style structs.
- [ ] **Neo chrome:** uses `NeoContainer` / `NeoButton` / `NeoInput` / siblings — not ad-hoc container styling in screen files.
- [ ] **Geometry lock:** border width 2.0, `blur_radius = 0.0` shadows, radius 4.0 unless a documented exception exists.
- [ ] **Accent lock:** `COLOR_ACCENT` for primary actions; no second competing accent on the same view.
- [ ] **Contrast:** text and primary controls readable on dark and light themes.
- [ ] **Hierarchy:** one obvious primary action per region.
- [ ] **Motion:** hover/press feedback only; no continuous decorative animation.
- [ ] **States:** empty, loading, and error paths rendered — not blank panes.
- [ ] **Copy:** no placeholder lorem or broken AI filler in labels.
- [ ] **Tests:** layout/state invariants covered by `cargo test` where logic changed (§8 Executable Validation).
- [ ] **IDE lint:** `./scripts/rustrover-inspect.sh --scope rust-workspace --fail-on WARNING` passed (RustRover GUI closed).
- [ ] **Out of scope rejected:** landing-page layouts and industrial/CRT brutalism were not imported.

---

## 8. Executable Validation (Agent MUST run before TASK_STATUS: COMPLETE)

Whenever a desktop task is performed, the agent must run the following checks:
1. **Compilation Check**: `cargo check --workspace`
2. **Linting Check**: `cargo clippy --workspace --all-targets` (must contain zero warnings/errors)
3. **Format Check**: `cargo fmt --all -- --check`
4. **Unit Tests**: `cargo test --workspace`
5. **IDE Inspection Check**: `./scripts/rustrover-inspect.sh --scope rust-workspace --fail-on WARNING`

   RustRover headless inspect surfaces IDE-only linting (duplicate code, elidable lifetimes, unused Cargo deps, unnecessary qualifications) that Clippy does not emit. **Close the RustRover GUI before running** — the CLI inspector fails if an IDE window is already open on this project.

   ```bash
   # Default: desktop + agent_core + classifier_core + docgen_core
   ./scripts/rustrover-inspect.sh --scope rust-workspace --fail-on WARNING

   # Faster, desktop-only pass during UI work
   ./scripts/rustrover-inspect.sh --scope desktop --fail-on WARNING

   # Uncommitted changes only
   ./scripts/rustrover-inspect.sh --scope desktop --changes --fail-on WARNING
   ```

   Reports land in `build/rustrover-inspections/`. Fix findings or document why a warning is intentionally accepted before `TASK_STATUS: COMPLETE`.

---

## 9. UI Validation (Launch, Automate, Capture)

Visual validation on this Linux Mint (Cinnamon/X11) environment is a **secondary** signal. The
window title changes per active screen (`Enclave - Chat`, `Enclave - Studio`, …), Cinnamon does not
reliably foreground newly-launched windows, and the agent cannot reliably interpret screenshots it
captures. Treat screenshots as a *capture-for-the-user* path, not as something the agent "verifies."

**Structural/logic assertions belong in headless `cargo test` tests** (widget-tree reconciliation,
layout invariants, state transitions) — that is the primary, reliable validation path. Use the visual
flow below only for things tests cannot express (pixel placement, colors, eyeballing a new screen).

### 9.1 Preconditions (one-time)
- `DISPLAY=:0` is active in the shell.
- `xdotool` is installed for reliable focus + click automation:
  ```bash
  which xdotool || sudo apt-get install -y xdotool
  ```
- `wmctrl` is available for window discovery (pre-installed).

### 9.2 Launch (window is maximized automatically)
The app issues `iced::window::maximize` on startup (`main.rs` → `Application::new`), so every launch
is deterministic and fills the workspace. Launch it as a background task:
```bash
DISPLAY=:0 cargo run -p enclave-desktop &
```
Wait ~2–3 seconds for the window to appear and maximize before interacting.

### 9.3 Foreground the window (do NOT rely on the title)
Cinnamon may launch the window unfocused. Resolve the numeric window ID and activate **by ID** — the
ID is stable regardless of which screen is active, unlike the title:
```bash
WINID=$(wmctrl -l | awk '/Enclave/ {print $1; exit}')
wmctrl -i -a "$WINID"        # activate (switch desktop + raise + focus)
xdotool windowactivate "$WINID"
```

### 9.4 Drive the UI with xdotool
Prefer programmatic clicks over assuming a launch state. Resolve coordinates relative to the window:
```bash
# Example: click the "new chat" control in the top-right of the chat app bar.
xdotool windowactivate "$WINID"
eval "$(xdotool getwindowgeometry --shell "$WINID")"   # sets WIDTH, HEIGHT, X, Y
# Top-right of a maximized window: click near the right edge of the app bar.
xdotool mousemove $((X + WIDTH - 60)) $((Y + 40)) click 1
```
Wait ~1 second after each navigation for the transition animation to settle.

### 9.5 Capture a screenshot (for the user to eyeball)
Capture the now-focused window:
```bash
gnome-screenshot -w -f /absolute/path/to/screenshot.png
# or, targeted by window ID:
import -window "$WINID" /absolute/path/to/screenshot.png
```
Save screenshots to the current conversation's artifact directory:
`/home/sean/.gemini/antigravity/brain/08a4ef79-c3fc-4043-9206-c0c81ad1dbdb/` and **present them to
the user** for visual confirmation. Do not claim the screenshot has been "verified" unless the user
confirms it — the agent's image interpretation is unreliable.

### 9.6 Cleanup
Terminate the background app when validation is complete:
```bash
pkill -f "target/debug/enclave-desktop"
```


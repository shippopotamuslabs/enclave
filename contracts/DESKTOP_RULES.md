# DESKTOP_RULES.md
**Enclave – Desktop Architecture, Design, & Clean Contracts v2.0**

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

---

## 7. Executable Validation (Agent MUST run before TASK_STATUS: COMPLETE)

Whenever a desktop task is performed, the agent must run the following checks:
1. **Compilation Check**: `cargo check --workspace`
2. **Linting Check**: `cargo clippy --workspace --all-targets` (must contain zero warnings/errors)
3. **Format Check**: `cargo fmt --all -- --check`
4. **Unit Tests**: `cargo test --workspace`

---

## 8. Run App & Take Screenshots (UI Validation)

To validate visual UI layouts, screen flows, and component styles on this Linux Mint (Cinnamon/X11) environment:

1. **Launch the App**:
   Run the desktop application as a background task using:
   ```bash
   cargo run -p enclave-desktop
   ```
   *Note: Ensure `DISPLAY=:0` is set in the environment (verified as active in the shell).*

2. **Wait for Render**:
   Always wait 1-2 seconds after launching or navigating to a new screen before capturing the screen to allow the window/assets to fully render.

3. **Capture Screenshot**:
   Use either of the following pre-installed screenshot tools to capture the UI:
   - **GNOME Screenshot (Full Desktop)**:
     ```bash
     gnome-screenshot -f /absolute/path/to/screenshot.png
     ```
   - **GNOME Screenshot (Active Window Only)**:
     ```bash
     gnome-screenshot -w -f /absolute/path/to/screenshot.png
     ```
   - **ImageMagick Import (Targeting Enclave Window)**:
     ```bash
     import -window "Enclave - Chat" /absolute/path/to/screenshot.png
     ```
     *(Substitute window name based on current screen: "Enclave - Studio", "Enclave - Settings", "Enclave - Gallery")*

4. **Verify visually**:
   Save screenshots to the current conversation's artifact directory: `/home/sean/.gemini/antigravity/brain/08a4ef79-c3fc-4043-9206-c0c81ad1dbdb/` and present them to the user.


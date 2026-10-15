# Agent Routing Contract — Enclave

This is the root source of truth for agent-facing repo instructions. Agent-specific
files such as `CLAUDE.md` and `GEMINI.md` should only point here to avoid drift.
Never modify this file or any file in `/contracts/` without an explicit
**"Contract Waiver"** from the user.

---

## 1. Contract Routing (Read Before Writing Any Code)

Every task maps to one or more binding contracts in `/contracts/`. Read the relevant contract(s) before generating code or proposing architecture.

### Android Application Contracts

| Task type | Contract to read |
|-----------|-----------------|
| Module structure, dependency direction, layer responsibilities, navigation, Scaffold, IME handling, `WorkManager`, background tasks | `ARCHITECTURE_RULES.md` |
| Kotlin style, naming, Compose stability, modifier conventions, state hoisting, Flow/coroutines, testing conventions, test coverage mandate | `CODE_STYLE_RULES.md` |
| Room database, entities, DAOs, repository pattern, entity↔domain mapping, threading | `DATA_LAYER_RULES.md` |
| LiteRT inference, model loading, KV cache, thermal/memory watchdog, thinking pipeline, safety layer | `INFERENCE_RULES.md` |
| Colors, typography, Compose component structure, `InputBar`, `MessageList`, `ChatTopBar`, `HistoryScreen` | `UI_DESIGN_SPEC.md` |
| JUnit 5, Fakes vs Mocks, Robolectric, Turbine, State-based assertions, Test coverage | `UNIT_TESTING.md` |

### Desktop Application Contracts

| Task type | Contract to read |
|-----------|-----------------|
| Elm Architecture (MVU), styling rules (Neobrutalism), state patterns, commands, clippy, and rustfmt validations | `DESKTOP_RULES.md` |

When a task spans multiple domains, read all relevant contracts.

Before broad edits, first map the relevant subsystem, identify contracts and call
paths, then write a brief implementation plan. For large investigations, use
subagents if the harness supports them so exploration does not flood the main
context.

---

## 2. Repository Map

Enclave is a mixed Android monorepo, not a single-module app.

- `app/`: Android application shell, DI wiring, navigation entrypoints, app resources, signing, Firebase/Google services integration.
- `core/domain/`: domain models, ports, use cases, and test fixtures.
- `core/data/`: Room, repositories, Firebase/network persistence, WorkManager, entity/domain mapping, schemas.
- `core/ui/`: shared Compose UI components, themes, media/rendering helpers.
- `core/testing/`: shared hand-written fakes, `MainDispatcherRule`, test fixtures (see `contracts/UNIT_TESTING.md`).
- `detekt-rules/`: custom Detekt rules (`enclave` rule set) enforcing architecture/UI/testing contracts; JVM-only, not an app runtime dependency. Scaffold tracked in Linear ENC-10 — until it lands, the contract rules below are agent-enforced and must not be weakened to match existing violations.
- `core/agent/`: Android UniFFI/JNI bridge for Rust `third_party/agent_core`; read `core/agent/README.md` for bridge work.
- `core/docgen/`: Android UniFFI/JNI bridge for Rust `third_party/docgen_core`.
- `feature/*/`: feature modules for chat, settings, history, downloads, inference, studio, and background services.
- `desktop/`: Rust & Iced companion desktop application.
- `llama-android/` and `whisper-android/`: native Android library wrappers over C/C++ code in `third_party`.
- `functions/`: Firebase Cloud Functions TypeScript project.
- `third_party/agent_core/` and `third_party/docgen_core/`: repo-owned Rust native cores used to generate Kotlin bindings.
- `third_party/llama.cpp`, `third_party/whisper.cpp`, `third_party/kleidiai-*`, `third_party/OpenCL-*`, `third_party/Vulkan-*`: vendored/native dependency sources; avoid routine edits.
- `scripts/`: deterministic helper scripts for Rust binding generation and catalog checks.
- `config/detekt/`: Detekt static analysis config.
- `docs/superpowers/` and `plans/`: planning/spec artifacts, not runtime source.

Main toolchains: Gradle Kotlin DSL, Kotlin/Compose/Hilt/Room/WorkManager,
Rust/Cargo/UniFFI/Iced, CMake/Android NDK, and npm/TypeScript for Firebase functions.

---

## 3. Hard Rules (Non-Negotiable)

### 3.1 Android Clean Architecture — Zero Tolerance (Android Only)

- `:core:domain` contains domain models, ports, use cases, and test fixtures. Keep framework dependencies out of domain APIs.
- Dependency direction is inward toward `:core:domain`. Data, inference, UI, app, and feature modules may depend on domain; domain must not depend on app, feature, data, UI, or native bridge modules.
- Domain models are **never** passed directly to composables. ViewModels map domain → presentation. Presentation models (`*UiState`, `*UiEvent`, drafts) must not embed domain types.
- No `NavController` passed into composables. Navigation via pure callbacks hoisted to the route level.
- **ViewModels depend on use cases only** — never on `*Repository`/`*Port` interfaces or `:core:data` classes. Multi-source `Flow` composition lives in a domain interactor/use case; feature mappers take no use-case parameters; write paths go through use cases. Enforced by the `enclave` Detekt rule set (ENC-2) and `contracts/ARCHITECTURE_RULES.md` §3.4.
- Feature UI uses the neutral spec-driven component API and semantic theme tokens from `:core:ui` — no raw `MaterialTheme`/Material 3 chrome or hex literals in `:feature:*`/`:app` UI (ENC-1/ENC-2, `contracts/UI_DESIGN_SPEC.md`).

**Violation response:** Reply with exactly: `"Contract violation detected in [file]. Request explicit 'Contract Waiver' from user."` Then stop. Do not generate code.

### 3.2 Android Code — AndroJack Level 3 Protocol (Android Only)

For every Kotlin, XML, or Gradle code block generated:

1. **Pre-generation:** Call the appropriate `androjack` tool to ground the solution before writing code:
   - Compose UI tasks → `mcp_androjack_material3_expressive`
   - Dependency changes → `mcp_androjack_gradle_dependency_checker`
   - API-level questions → `mcp_androjack_android_api_level_check`
   - New components/APIs → `mcp_androjack_android_component_status`
   - Architecture questions → `mcp_androjack_architecture_reference`

2. **Post-generation:** Pass every generated code block through `mcp_androjack_android_code_validator` (minSdk 34, targetSdk 36).

3. **FAIL verdict:** Fix internally and re-run validator. Never output code that fails validation.

### 3.3 Test Coverage — Mandatory

Whenever production code is added or modified (new classes, public functions, logic in `:core:domain`, `:core:data`, `:feature:inference`, ViewModels, repositories, mappers, use cases, or desktop screens and native crates):
- **Android**: Write or extend unit tests in the matching `src/test/kotlin` source set using JUnit 5 + Turbine (for Flow) + **fakes from `:core:testing`** for Enclave-owned ports. MockK is allowed only for third-party SDK boundaries with an explicit `@Suppress` and one-line justification. Never `verify`/`coVerify` in ViewModel tests — assert on `UiState` (see `contracts/UNIT_TESTING.md`, ENC-4).
- **Desktop/Rust**: Write or extend cargo tests. Run using `cargo test --workspace`.
- Tests must pass before `TASK_STATUS: COMPLETE`.
- Trivial styling-only or preview-only changes are exempt (theme/preview matrix changes are not exempt — see `contracts/UI_DESIGN_SPEC.md`).

### 3.4 Schema Lock

**Never** invent new database tables or columns for transient UI state (e.g., `is_typing`, `is_loading`). Database schema changes require explicit developer authorization.

### 3.5 Inference — On-Device First (BYOK Optional)

Prioritize on-device inference via LiteRT 2.x/agent_core. Optional "BYOK" cloud providers (OpenAI, Anthropic, Google GenAI) are permitted as advanced user escape hatches, provided they remain isolated from core on-device logic.

### 3.6 Distribution Scope — No Google Play (Personal / Portfolio)

Enclave will **never** be released on Google Play (liability and Play AI-policy exposure). It is a personal project and portfolio item only.

- Do **not** add Play Console, Play App Signing for distribution, bundle-for-Play, Play service-account, or Firebase Test Lab robo/store lanes to CI or release docs. Signed release APKs are manual/local for personal sideload or portfolio demo only.
- CI is scoped accordingly (ENC-3): fast MR gates plus scheduled nightly confidence — not store-grade release automation. See §4 and `contracts/ARCHITECTURE_RULES.md`.
- Linear `Deployed` / `Partially Deployed` / `Released` states (§3.7) therefore mean manual sideload / personal-device distribution milestones on the **parent issue only**, never a Play rollout.

### 3.7 Linear Issue Tracking — Chunk-Based Trunk Workflow

Single source of truth for work items is the Linear **Enclave** team (key `ENC`, workspace Browntown Development: https://linear.app/browntown-development/team/ENC/all), not GitLab issues. Adapted from Stratum `AGENTS.md` §4.8 (team `STR`).

- **Chunk-based development:** one parent issue per feature; each chunk of work is a **sub-issue**; **one MR per sub-issue** — never batch multiple chunks into one MR.
- **Ticket first:** ensure you have a Linear ticket to work from via the Linear MCP (`save_issue`, `get_issue`, `list_issues`; see §7). If you do not know the ticket, ask the user. If told it does not exist, create one with a `type::` label (`type::feature`, `type::bug`, `type::chore`, `type::polish`). Once you have the ticket, put it **In Progress** (`save_issue` with `state: "In Progress"`), then start working.
- **Branch naming:** copy the git branch name from the Linear sub-issue (it contains the issue ID, e.g. `browntowndevelopment/enc-20-contracts-edit-agentsmd-linear-workflow-contracts`) — this auto-links the branch.
- **Every MR description MUST contain a closing magic word + the sub-issue ID**, e.g. `Fixes ENC-20`. Linear's MR automations move the issue to **In Review** when the MR opens (review = the user plus any AI reviewer job) and to **Merged** when the MR merges. A merge is NOT a deploy. `In Progress` is manual: the agent sets it when work starts.
- To link an MR without triggering the automatic status moves, use `Refs ENC-20` or `Contributes to ENC-20` instead of a closing magic word.
- Commit messages and MR comments do **not** link issues — only branch name, MR title, and MR description.
- **Merge vs deploy vs release:** `Merged` = merged to main (trunk). `Deployed` / `Partially Deployed` / `Released` live on the **parent issue only** and are manual (developer sideloads/distributes, then updates Linear or asks the agent to) — there is no Play pipeline or feature-flag webhook into Linear (§3.6). Sub-issues stop at `Merged`.
- Never close the parent manually while sub-issues are open.
- Current programme parents: ENC-1 (theme families), ENC-2 (Detekt + layering), ENC-3 (CI MR/nightly, no Play), ENC-4 (fakes/screenshot testing), ENC-5 (contracts/Linear adoption; docs chunk ENC-20).

---

## 4. Scoped Commands

Prefer the narrowest command that covers the changed area.

### Android Commands
- Android app compile: `./gradlew :app:assembleDebug`
- Single Android module compile: `./gradlew :core:data:assembleDebug` or `./gradlew :feature:chat:assembleDebug`
- Single Android module tests: `./gradlew :feature:inference:testDebugUnitTest`
- Full Android unit tests: `./gradlew testDebugUnitTest`
- Full style check: `./gradlew ktlintCheck`
- Full static analysis: `./gradlew detekt`
- Room/KSP smoke: `./gradlew :app:kspDebugKotlin`
- Agent binding regeneration: `./scripts/build-agent-bindings.sh`
- Docgen binding regeneration: `./scripts/build-docgen-bindings.sh`

### Desktop / Rust Commands
- Workspace check: `cargo check --workspace`
- Desktop app check: `cargo check -p enclave-desktop`
- Run desktop app: `cargo run -p enclave-desktop`
- Workspace tests: `cargo test --workspace`
- Desktop tests: `cargo test -p enclave-desktop`
- Rust lints: `cargo clippy --workspace --all-targets`
- Rust formatter check: `cargo fmt --all -- --check`

### Firebase Commands
- Firebase functions build: `npm --prefix functions run build`
- Firebase functions tests: `npm --prefix functions test`
- Firebase functions emulator: `npm --prefix functions run serve`

Before merge or a large cross-module change, use the CI-aligned checks for the active platform:
- **Android**: `./gradlew ktlintCheck`, `./gradlew detekt`, `./gradlew assembleDebug`, and `./gradlew testDebugUnitTest`.
- **Desktop**: `cargo fmt --all -- --check`, `cargo clippy --workspace --all-targets`, and `cargo test --workspace`.

CI target shape (ENC-3, personal/portfolio scope per §3.6): MR pipelines run only for MRs targeting the default branch, with independent jobs (`needs: []`) — combined static analysis, unit tests, debug + unsigned release (R8 gate) builds, changes-gated functions/Rust jobs, and changes-gated screenshot tests. Emulator/device and full re-verify jobs run on the scheduled nightly pipeline only (`allow_failure` until green, `interruptible: false`). No Play or Test Lab store lanes.

---

## 5. Context Noise And Generated Code

Do not inspect or edit these during normal tasks unless the task explicitly targets them:

- Gradle/build outputs: `build/`, `**/build/`, `.gradle/`, `.kotlin/`, `.cxx/`, `.externalNativeBuild/`.
- Rust/Cargo build outputs: `target/`, `**/target/`, `Cargo.lock` (unless dependency updates are explicitly required).
- Dependency caches and generated JS: `functions/node_modules/`, `functions/lib/`.
- Vendored native sources: `third_party/llama.cpp/`, `third_party/whisper.cpp/`, `third_party/kleidiai-*`, `third_party/OpenCL-*`, `third_party/Vulkan-*`.
- Binary/model/assets noise: `*.so`, `*.tflite`, `*.onnx`, `*.png`, `*.jpg`, `*.webp`, APK/AAB outputs, keystores.
- Planning/output logs unless requested: `test_output.txt`, `build_output.txt`, `lint_output.txt`, `plans/`.

Exception: generated UniFFI Kotlin bindings and checked-in native libraries are
part of the Android bridge deliverable when running the binding scripts. Inspect
or edit generated binding outputs only as part of that regeneration workflow.

---

## 6. Code Intelligence Setup

- Android/Kotlin: import the root Gradle project in **Android Studio Quail** (or IntelliJ). Keep Studio open on **Enclave** during agent sessions so `android studio` bridge commands work. Use JDK 17 and the checked-in Gradle wrapper.
- **Android CLI:** `android` 1.0+ on PATH (`android info`). Run `android init` once per machine; project skills live under `.agents/skills/`.
- Rust/Desktop: use **VS Code** with the **rust-analyzer** extension or JetBrains **RustRover**. Open the root of the workspace.
- Rust bridge crates & desktop: use `rust-analyzer` rooted at the root `Cargo.toml`.
- TypeScript functions: run `npm --prefix functions install` if dependencies are missing; TypeScript server should use `functions/tsconfig.json`.
- Native C/C++: Android Studio/CMake can generate compile commands under native build output folders; those outputs are intentionally ignored.

---

## 7. Tool Priority (MCP and Android CLI)

Before any Android/Kotlin task, check MCP tools **and** whether Android Studio bridge
commands are available.

### MCP servers

| MCP Server | When to use |
|------------|-------------|
| `androjack` | All Android/Kotlin validation, API research, dependency checks — **mandatory for all code generation** (Android only) |
| `mobile-mcp` | Install APKs, interact with a running emulator/device, take screenshots (Android only) |
| `google-developer-knowledge` / `firebase-mcp-server` | Official Android/Firebase/Google API documentation lookup |
| `github-mcp-server` | PR creation, branch management, code search across the repo |
| `linear` (Linear MCP, `mcp__linear.*`) | **All work-item tracking for Enclave (team `ENC`)** — `list_issues`/`get_issue` to find tickets, `save_issue` to create/update (including `In Progress`), `save_comment` for MR notes. Accessible in-session; no separate login step for agents. See §3.7. Use before starting work, not GitLab issues |
| `synthitect` | CFAW workflow phases when explicitly invoked by the user |

For Desktop tasks, use Cargo, Clippy, and Rustfmt CLI commands directly.

### Android CLI and Studio bridge (shell)

| Capability | Command family | When to use |
|------------|----------------|-------------|
| Studio connected | `android studio check` | Start of Compose UI, refactor, or navigation tasks |
| Symbol navigation | `android studio find-declaration`, `find-usages` | Cross-module Kotlin references (prefer over blind grep) |
| IDE inspections | `android studio analyze-file` | Before/after editing a Kotlin/Compose file |
| Resolved versions | `android studio version-lookup` | Dependency bumps, version skew debugging |
| Compose previews | `android studio render-compose-preview` | Visual check of `@Preview` composables — **requires debug build** (see below) |
| APK / deploy | `android describe`, `android run` | After `./gradlew :app:assembleDebug` |
| Device UI tree | `android layout` | Layout/debug on connected device or emulator |
| Android docs | `android docs search`, `android docs fetch` | Authoritative KB lookups |

**Project identifier:** `--project="Enclave"` when multiple projects could be open.

**Compose previews:** Build debug first (`./gradlew :feature:<module>:assembleDebug` or
`:app:assembleDebug`). Release artifacts do not satisfy preview rendering. If
`Missing ComposeViewAdapter` appears, open the preview in Studio once and rebuild debug.

**AndroJack vs Android CLI:** AndroJack validates **generated** code (§3.2). Android CLI
queries the **running IDE** and devices. Use both; neither replaces the other.

**Agent rule and recipes:** `.agents/rules/android-studio-cli.md`,
`.agents/skills/pocket-crew-studio-bridge/SKILL.md`.

If `android studio check` reports no running Studio, continue with Gradle/tests and note
that bridge commands were skipped.

---

## 8. Skills, Hooks, And Deterministic Checks

Keep reusable expertise out of root instructions. Good candidates for on-demand
skills or skill-like docs in this repo:

- Android Compose feature work against `ARCHITECTURE_RULES.md`, `CODE_STYLE_RULES.md`, `UI_DESIGN_SPEC.md`, and `UNIT_TESTING.md`; use **Android Studio bridge** per §7 and `pocket-crew-studio-bridge` skill.
- Room/schema changes against `DATA_LAYER_RULES.md`.
- Inference/provider changes against `INFERENCE_RULES.md`.
- UniFFI binding regeneration for `core/agent` and `core/docgen`.
- Firebase functions build/test/deploy preparation.
- Pre-merge review using CI-aligned Gradle, npm, and Cargo checks.

Use scripts or hooks for deterministic behavior. Existing deterministic checks
are Gradle tasks, npm scripts, Cargo tests, and scripts under `scripts/`. Do not
invent hook config unless the repo adds checked-in hook infrastructure.

Existing MCP config is `.cursor/mcp.json`, which configures `androjack`.
The **Linear MCP is available to agents in-session** (`mcp__linear.*` tools listed in §7) for the Enclave team `ENC` — use it for ticket-first workflow (§3.7); it is not configured in `.cursor/mcp.json`.
Android CLI is shell-based (not MCP); Studio bridge requires Quail + Gemini signed in.
Candidate future integrations, if maintainers choose to add them: Firebase project operations and deeper emulator automation beyond `android emulator` / `mobile-mcp`.

---

## 9. Executable Validation Before Completion

Before declaring `TASK_STATUS: COMPLETE`, run the narrowest relevant validations based on the target project.

### Android Validations (Android Tasks Only)
- **Architecture / Domain:** `./gradlew :core:domain:assembleDebug`
- **App Compilation:** `./gradlew :app:assembleDebug`
- **Data Layer:** `./gradlew :core:data:assembleDebug` + `./gradlew :app:kspDebugKotlin`
- **Inference Layer:** `./gradlew :feature:inference:assembleDebug`
- **Tests:** `./gradlew testDebugUnitTest`
- **Style:** `./gradlew ktlintCheck`
- **Static analysis:** `./gradlew detekt` (required — `maxIssues: 0`; must pass before completion)

**Note on Detekt:** Detekt runs per module. `./gradlew assembleDebug` (root) and `./gradlew detekt` analyze all modules. `:app:assembleDebug` alone only ran `:app:detekt` historically; the app module's `assemble*` tasks now also depend on every subproject `detekt` task. Still run `./gradlew detekt` explicitly after Kotlin changes — do not rely on compile-only checks.

### Desktop Validations (Desktop Tasks Only)
- **Compile Check:** `cargo check -p enclave-desktop`
- **Linter (Clippy):** `cargo clippy -p enclave-desktop --all-targets` (must pass with zero warnings or errors)
- **Code Formatter:** `cargo fmt --all -- --check`
- **Unit Tests:** `cargo test -p enclave-desktop`

Only run the checks relevant to what was changed during iteration, but run the
full affected unit test set for logic changes.

---

## 10. Maintenance

Repo maintainers own this file. Review every 3-6 months:

- Root and local agent instructions.
- `/contracts` accuracy against current module paths and Gradle commands.
- Skills or skill-like workflow docs.
- Hook/script recommendations.
- Ignore rules in `.geminiignore`, `.gitignore`, IDE settings, and any future tracked `.cursorignore`.
- LSP/code-intelligence setup.
- MCP/plugin notes.
- Stale model-specific workarounds in active tool-specific rules.

---

## 11. Autonomous Continuation Protocol

After any file edit is accepted or tool result returns:
- Continue to the next logical step without pausing for confirmation.
- End every response with exactly one of:
  - `TASK_STATUS: COMPLETE — Phase objectives achieved.`
  - `TASK_STATUS: PARTIAL — Completed: [list]. Remaining: [list]. Continuing.`

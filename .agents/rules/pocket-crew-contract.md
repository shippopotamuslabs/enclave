# Agent Routing Contract — Enclave

This is the root source of truth for agent-facing repo instructions. Agent-specific
files such as `CLAUDE.md` and `GEMINI.md` should only point here to avoid drift.
Never modify this file or any file in `/contracts/` without an explicit
**"Contract Waiver"** from the user.

---

## 1. Contract Routing (Read Before Writing Any Code)

Every task maps to one or more binding contracts in `/contracts/`. Read the relevant contract(s) before generating code or proposing architecture.

| Task type | Contract to read |
|-----------|-----------------|
| Module structure, dependency direction, layer responsibilities, navigation, Scaffold, IME handling, `WorkManager`, background tasks | `ARCHITECTURE_RULES.md` |
| Kotlin style, naming, Compose stability, modifier conventions, state hoisting, Flow/coroutines, testing conventions, test coverage mandate | `CODE_STYLE_RULES.md` |
| Room database, entities, DAOs, repository pattern, entity↔domain mapping, threading | `DATA_LAYER_RULES.md` |
| LiteRT inference, model loading, KV cache, thermal/memory watchdog, thinking pipeline, safety layer | `INFERENCE_RULES.md` |
| Colors, typography, Compose component structure, `InputBar`, `MessageList`, `ChatTopBar`, `HistoryScreen` | `UI_DESIGN_SPEC.md` |
| JUnit 5, Fakes vs Mocks, Robolectric, Turbine, State-based assertions, Test coverage | `UNIT_TESTING.md` |

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
- `core/agent/`: Android UniFFI/JNI bridge for Rust `third_party/agent_core`; read `core/agent/README.md` for bridge work.
- `core/docgen/`: Android UniFFI/JNI bridge for Rust `third_party/docgen_core`.
- `feature/*/`: feature modules for chat, settings, history, downloads, inference, studio, and background services.
- `llama-android/` and `whisper-android/`: native Android library wrappers over C/C++ code in `third_party`.
- `functions/`: Firebase Cloud Functions TypeScript project.
- `third_party/agent_core/` and `third_party/docgen_core/`: repo-owned Rust native cores used to generate Kotlin bindings.
- `third_party/llama.cpp`, `third_party/whisper.cpp`, `third_party/kleidiai-*`, `third_party/OpenCL-*`, `third_party/Vulkan-*`: vendored/native dependency sources; avoid routine edits.
- `scripts/`: deterministic helper scripts for Rust binding generation and catalog checks.
- `config/detekt/`: Detekt static analysis config.
- `docs/superpowers/` and `plans/`: planning/spec artifacts, not runtime source.

Main toolchains: Gradle Kotlin DSL, Kotlin/Compose/Hilt/Room/WorkManager,
Rust/Cargo/UniFFI, CMake/Android NDK, and npm/TypeScript for Firebase functions.

---

## 3. Hard Rules (Non-Negotiable)

### 3.1 Clean Architecture — Zero Tolerance

- `:core:domain` contains domain models, ports, use cases, and test fixtures. Keep framework dependencies out of domain APIs.
- Dependency direction is inward toward `:core:domain`. Data, inference, UI, app, and feature modules may depend on domain; domain must not depend on app, feature, data, UI, or native bridge modules.
- Domain models are **never** passed directly to composables. ViewModels map domain → presentation.
- No `NavController` passed into composables. Navigation via pure callbacks hoisted to the route level.

**Violation response:** Reply with exactly: `"Contract violation detected in [file]. Request explicit 'Contract Waiver' from user."` Then stop. Do not generate code.

### 3.2 Android Code — AndroJack Level 3 Protocol

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

Whenever production code is added or modified (new classes, public functions, logic in `:core:domain`, `:core:data`, `:feature:inference`, ViewModels, repositories, mappers, use cases):
- Write or extend unit tests in the matching `src/test/kotlin` source set.
- Use JUnit 5 + Turbine (for Flow) + MockK.
- Tests must pass before `TASK_STATUS: COMPLETE`.
- Trivial styling-only or preview-only changes are exempt.

### 3.4 Schema Lock

**Never** invent new database tables or columns for transient UI state (e.g., `is_typing`, `is_loading`). Database schema changes require explicit developer authorization.

### 3.5 Inference — On-Device First (BYOK Optional)

Prioritize on-device inference via LiteRT 2.x. Optional "BYOK" cloud providers (OpenAI, Anthropic, Google GenAI) are permitted in `:feature:inference` as advanced user escape hatches, provided they remain isolated from core on-device logic.

---

## 4. Scoped Commands

Prefer the narrowest command that covers the changed area.

- Android app compile: `./gradlew :app:assembleDebug`
- Single Android module compile: `./gradlew :core:data:assembleDebug` or `./gradlew :feature:chat:assembleDebug`
- Single Android module tests: `./gradlew :feature:inference:testDebugUnitTest`
- Full Android unit tests: `./gradlew testDebugUnitTest`
- Full style check: `./gradlew ktlintCheck`
- Full static analysis: `./gradlew detekt`
- Room/KSP smoke: `./gradlew :app:kspDebugKotlin`
- Rust agent core tests: `(cd third_party/agent_core && cargo test)`
- Agent binding regeneration: `./scripts/build-agent-bindings.sh`
- Docgen binding regeneration: `./scripts/build-docgen-bindings.sh`
- Firebase functions build: `npm --prefix functions run build`
- Firebase functions tests: `npm --prefix functions test`
- Firebase functions emulator: `npm --prefix functions run serve`

Before merge or a large cross-module change, use the CI-aligned checks:
`./gradlew ktlintCheck`, `./gradlew detekt`, `./gradlew assembleDebug`, and
`./gradlew testDebugUnitTest`. Run narrower checks during small iterations.

---

## 5. Context Noise And Generated Code

Do not inspect or edit these during normal tasks unless the task explicitly targets them:

- Gradle/build outputs: `build/`, `**/build/`, `.gradle/`, `.kotlin/`, `.cxx/`, `.externalNativeBuild/`.
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
- TypeScript functions: run `npm --prefix functions install` if dependencies are missing; TypeScript server should use `functions/tsconfig.json`.
- Rust bridge crates: use `rust-analyzer` rooted at `third_party/agent_core/Cargo.toml` or `third_party/docgen_core/Cargo.toml`.
- Native C/C++: Android Studio/CMake can generate compile commands under native build output folders; those outputs are intentionally ignored.

---

## 7. Tool Priority (MCP and Android CLI)

Before any Android/Kotlin task, check MCP tools **and** whether Android Studio bridge
commands are available.

### MCP servers

| MCP Server | When to use |
|------------|-------------|
| `androjack` | All Android/Kotlin validation, API research, dependency checks — **mandatory for all code generation** |
| `mobile-mcp` | Install APKs, interact with a running emulator/device, take screenshots |
| `google-developer-knowledge` / `firebase-mcp-server` | Official Android/Firebase/Google API documentation lookup |
| `github-mcp-server` | PR creation, branch management, code search across the repo |
| `synthitect` | CFAW workflow phases when explicitly invoked by the user |

Use MCP tools before ad-hoc web search. Prefer MCP over guessing APIs.

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
Android CLI is shell-based (not MCP); Studio bridge requires Quail + Gemini signed in.
Candidate future integrations, if maintainers choose to add them: GitHub issue
and PR access, Firebase project operations, and deeper emulator automation beyond
`android emulator` / `mobile-mcp`.

---

## 9. Executable Validation Before Completion

Before declaring `TASK_STATUS: COMPLETE`, run the narrowest relevant validations.
For large or cross-module changes, run the CI-aligned checks.

- **Architecture / Domain:** `./gradlew :core:domain:assembleDebug`
- **App Compilation:** `./gradlew :app:assembleDebug`
- **Data Layer:** `./gradlew :core:data:assembleDebug` + `./gradlew :app:kspDebugKotlin`
- **Inference Layer:** `./gradlew :feature:inference:assembleDebug`
- **Tests:** `./gradlew testDebugUnitTest`
- **Style:** `./gradlew ktlintCheck`
- **Static analysis:** `./gradlew detekt` (required — `maxIssues: 0`; must pass before completion)

**Note on Detekt:** Detekt runs per module. `./gradlew assembleDebug` (root) and `./gradlew detekt` analyze all modules. `:app:assembleDebug` alone only ran `:app:detekt` historically; the app module's `assemble*` tasks now also depend on every subproject `detekt` task. Still run `./gradlew detekt` explicitly after Kotlin changes — do not rely on compile-only checks.

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

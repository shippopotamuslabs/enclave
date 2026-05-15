# Agent Routing Contract — Pocket Crew

This file has **absolute priority** over all other instructions. Never modify it or any file in `/contracts/` without an explicit **"Contract Waiver"** from the user.

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

---

## 2. Hard Rules (Non-Negotiable)

### 2.1 Clean Architecture — Zero Tolerance

- `:domain` contains **only** pure Kotlin: models, repository interfaces, use cases. No Android, Compose, Hilt, Room, or any framework type.
- Dependency direction is strictly: `:app` → (`:domain`, `:data`, `:inference`), `:data` → `:domain`, `:inference` → `:domain`, `:domain` → nothing.
- Domain models are **never** passed directly to composables. ViewModels map domain → presentation.
- No `NavController` passed into composables. Navigation via pure callbacks hoisted to the route level.

**Violation response:** Reply with exactly: `"Contract violation detected in [file]. Request explicit 'Contract Waiver' from user."` Then stop. Do not generate code.

### 2.2 Android Code — AndroJack Level 3 Protocol

For every Kotlin, XML, or Gradle code block generated:

1. **Pre-generation:** Call the appropriate `androjack` tool to ground the solution before writing code:
   - Compose UI tasks → `mcp_androjack_material3_expressive`
   - Dependency changes → `mcp_androjack_gradle_dependency_checker`
   - API-level questions → `mcp_androjack_android_api_level_check`
   - New components/APIs → `mcp_androjack_android_component_status`
   - Architecture questions → `mcp_androjack_architecture_reference`

2. **Post-generation:** Pass every generated code block through `mcp_androjack_android_code_validator` (minSdk 34, targetSdk 36).

3. **FAIL verdict:** Fix internally and re-run validator. Never output code that fails validation.

### 2.3 Test Coverage — Mandatory

Whenever production code is added or modified (new classes, public functions, logic in `:domain`, `:data`, `:inference`, ViewModels, repositories, mappers, use cases):
- Write or extend unit tests in the matching `src/test/kotlin` source set.
- Use JUnit 5 + Turbine (for Flow) + MockK.
- Tests must pass before `TASK_STATUS: COMPLETE`.
- Trivial styling-only or preview-only changes are exempt.

### 2.4 Schema Lock

**Never** invent new database tables or columns for transient UI state (e.g., `is_typing`, `is_loading`). Database schema changes require explicit developer authorization.

### 2.5 Inference — On-Device First (BYOK Optional)

Prioritize on-device inference via LiteRT 2.x. Optional "BYOK" cloud providers (OpenAI, Anthropic, Google GenAI) are permitted in the `:inference` module as advanced user escape hatches, provided they remain isolated from core on-device logic.

---

## 3. MCP Tool Priority

Before any task, check available MCP tools:

| MCP Server | When to use |
|------------|-------------|
| `androjack` | All Android/Kotlin validation, API research, dependency checks — **mandatory for all code generation** |
| `mobile-mcp` | Install APKs, interact with a running emulator/device, take screenshots |
| `google-developer-knowledge` / `firebase-mcp-server` | Official Android/Firebase/Google API documentation lookup |
| `github-mcp-server` | PR creation, branch management, code search across the repo |
| `synthitect` | CFAW workflow phases when explicitly invoked by the user |

Use MCP tools before manual execution. Manual shell commands are a last resort.

---

## 4. Executable Validation Before Completion

Before declaring `TASK_STATUS: COMPLETE`, run the following validations to ensure zero regressions and style compliance:

- **Architecture / Domain:** `./gradlew :core:domain:assemble` (must pass with no Android/Compose deps)
- **App Compilation:** `./gradlew :app:assembleDebug`
- **Data Layer:** `./gradlew :core:data:assemble` + `./gradlew :app:kspDebugKotlin`
- **Inference Layer:** `./gradlew :feature:inference:assemble`
- **Tests:** `./gradlew test` (Runs the entire unit test suite across all modules)
- **Style:** `./gradlew ktlintCheck`
- **Static analysis:** `./gradlew detekt` (required — `maxIssues: 0`; must pass before completion)

**Note on Detekt:** Detekt runs per module. `./gradlew assembleDebug` (root) and `./gradlew detekt` analyze all modules. `:app:assembleDebug` alone only ran `:app:detekt` historically; the app module's `assemble*` tasks now also depend on every subproject `detekt` task. Still run `./gradlew detekt` explicitly after Kotlin changes — do not rely on compile-only checks.

Only run the checks relevant to what was changed, but **always** run the full test suite for any logic changes.

---

## 5. Autonomous Continuation Protocol

After any file edit is accepted or tool result returns:
- Continue to the next logical step without pausing for confirmation.
- End every response with exactly one of:
  - `TASK_STATUS: COMPLETE — Phase objectives achieved.`
  - `TASK_STATUS: PARTIAL — Completed: [list]. Remaining: [list]. Continuing.`

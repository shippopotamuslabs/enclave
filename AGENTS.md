---
name: Pocket Crew Master Agent
version: 5.0
description: Elite Android engineer using Compose/Kotlin. Strict contract-first + **TDD enforced (Red → Green → Refactor)**. Windows PowerShell only. Full autonomy after edit acceptance. Proactive Android docs search enabled. Tests BEFORE any production code mandatory.
tools:
  - shell: "ls"
  - gradle: "./gradlew build"
  - gradle: "./gradlew testDebugUnitTest"
  - gradle: "./gradlew ktlintCheck"
  - gradle: "./gradlew detekt"
  - adb: "adb devices"
  - mcp: "search_android_docs"
  - mcp: "fetch_page"
tags: [android, compose, clean-architecture, on-device, lite-rt, windows-ps, agp9, nav3, mcp-search, tdd-enforced]
---

# Pocket Crew – AGENTS.md
**Master Agent Contract v5.0 (March 2026) – API 36/Baklava Edition + Strict TDD + Knowledge Tooling**

This is the **single immutable entry-point**. Read this file first on every session and before every tool call.

## Contract Supremacy Rule (HIGHEST PRIORITY)
At session start and before EVERY tool call:
1. Read this CLAUDE.md
2. Read **ONLY** the exact contracts it specifies from the `/contracts/` directory
3. Treat every referenced contract as immutable ground truth
4. Any deviation → reply with exactly: "Contract violation detected in [file]. Request explicit 'Contract Waiver' from user."

## TDD Supremacy Rule (ZERO TOLERANCE – HIGHEST PRIORITY AFTER CONTRACT SUPREMACY)
**ALL work follows strict Test-Driven Development (Red → Green → Refactor). Plausibility reasoning is forbidden — only passing tests prove correctness.**

At session start and before **ANY** production code is written, edited, or proposed:
1. In `<think>` tags: Explicitly enumerate **EVERY** scenario, edge case, boundary, error path, state transition, and Android-specific behavior (configuration changes, process death, thermal/memory pressure, API 36 constraints, recomposition stability, dark mode, large fonts, etc.) impacted by the changes. Base this strictly on the task and loaded contracts.
2. **Red Phase (mandatory first step)**: Create or update unit test files **ONLY** (src/test/...). Use JUnit Jupiter 5 + mockk. Tests must follow AAA pattern, be descriptively named, and cover 100% of enumerated scenarios. Tests **must fail** against current code.
3. Execute targeted tests: `.\gradlew.bat testDebugUnitTest --tests "*AffectedClass*"` (or specific test class). Confirm and document failures in output.
4. **Green Phase**: **Only after** Red confirmation — implement **minimal** production code to make all tests pass.
5. **Refactor Phase**: Improve design, readability, and performance while keeping tests green. Re-run tests after every change.
6. Any production code edited before a successful Red phase → reply with exactly: "TDD Contract violation detected. Request explicit 'TDD Waiver' from user."

This rule eliminates all "it looks correct" churn. Tests are the sole source of truth.

## Available Tools
- PowerShell commands (Windows only)
- Gradle tasks (`.\gradlew.bat ktlintCheck`, `testDebugUnitTest`, `build`, `:module:assemble`, etc.)
- `adb.exe devices`
- **MCP Knowledge Tools (NEW & MANDATORY):**
  - `search_android_docs(query: str, max_results: int = 12, include_community: bool = True)` — searches official developer.android.com, codelabs, AOSP, Kotlin, GitHub, etc.
  - `fetch_page(url: str)` — returns clean Markdown of the page (perfect for code samples, tables, deprecation notes)

## Rigor Protocol (API 36 Optimized + TDD)
Before executing **any** tool call or writing **any** line of code, the agent **MUST**:
1. **Knowledge Check (Anti-Thrashing Rule):** If you are not 100% certain about an API signature, parameter order, deprecation status, Compose behavior, Material3 Expressive details, WorkManager/Data constraints, LiteRT usage, thermal/memory callbacks, notification ProgressStyle on API 36, or any other technical detail → **immediately call `search_android_docs`**.
2. **Proactive Search Mandate:** Default to searching early and often. Do **not** guess. Do **not** rely on stale internal knowledge. The explicit goal is to **minimize thrashing** and contract violations. Use the tool as much as possible.
3. **TDD Test Scenario Phase (NEW & MANDATORY):** After knowledge acquisition, enumerate every test scenario in `<think>` tags (see TDD Supremacy Rule). Then execute the Red Phase before touching any main source.
4. After search, call `fetch_page` on the top 2–3 most relevant official links and base all implementation decisions on the fetched content.
5. Continue with the original audit steps (Canvas, Lifecycle Trace, Process Boundaries, Invisible Constraint).
6. **Lint Enforcement:** Before any code is considered final, always run both ktlintCheck and detekt (on both main and test sources). Report unused variables, always-true/false conditions, VarCouldBeVal, ConstantConditionIf, UnnecessaryTemporaryInstantiation, and any "could be done better" rules from detekt.

## Contract Loading (ONLY these — nothing else)
Read and enforce **ONLY** the following from `/contracts/`:
- UI_DESIGN_SPEC.md
- ARCHITECTURE_RULES.md
- CODE_STYLE_RULES.md
- DATA_LAYER_RULES.md
- INFERENCE_RULES.md

(You may load multiple at once if relevant.)

## Global Thinking Discipline (MiniMax-M2.5 Native)
- ALL reasoning, planning, validation, analysis, and internal monologue MUST be wrapped exclusively in `<think></think>` tags.
- ZERO thinking text allowed outside these tags — not in responses, not in code blocks, not in File_Write content, not in tool calls.
- Native interleaved thinking is active — use it on every turn. **Test scenario enumeration is required in every `<think>` block.**

## Autonomous Continuation Protocol
When any file edit is accepted/applied or any tool result returns:
- Immediately resume at the next step in the **TDD cycle** (Red → Green → Refactor → Validate).
- Do not pause, do not ask for input, do not declare partial unless the entire current batch is done.
- End every response with exactly one line:
  `TASK_STATUS: COMPLETE — All tests passing, code green, validated.`
  or
  `TASK_STATUS: PARTIAL — Completed: [list]. Remaining: [list]. Continuing automatically.`

## Strict Boundaries
| Always                                                         | Never                                         |
|-------------------------------------|--------------------------------------------------------------------------|
| Hoist state to ViewModel                                       | Pass ViewModels into Compose UI trees         |
| Use `StateFlow` for UI state                                   | Use `var` without `remember`                  |
| Run `.\gradlew.bat ktlintCheck` and `.\gradlew.bat detekt`     | Use Unix commands (`ls`, `grep`, etc.)        |
| Use backslashes `\` in Windows paths                           | Use forward slashes `/` in file paths         |
| Use mockk for unit tests                                       | Use mockito for unit tests                    |
| Use JUnit Jupiter 5 for unit tests                             | Use JUnit 4 or other libraries for unit tests |
| **Write failing unit tests FIRST (Red Phase)**                 | **Write or edit any main source code before tests** |
| **Run tests via Gradle and confirm Red before any impl**       | Rely on code review, plausibility, or mental simulation for correctness |

## Workflow (Every Task – Strict TDD Enforced)
1. Parse — Read task + all relevant contracts from `/contracts/`
2. **Knowledge Acquisition** — Aggressively use `search_android_docs` + `fetch_page` for any unclear API or best practice
3. Plan — Decompose into files/changes. **Enforce strict Clean Architecture per ARCHITECTURE_RULES.md with zero tolerance.** In `<think>`: Exhaustively enumerate every test scenario.
4. **Red Phase (Tests First)**: Create/update unit tests covering 100% of scenarios. Run `.\gradlew.bat testDebugUnitTest --tests "*AffectedClass*"` and confirm failures.
5. **Green Phase**: Implement minimal production code to make ALL tests pass.
6. **Refactor Phase**: Optimize while keeping tests green. Re-run targeted tests.
7. Validate — Run `.\gradlew.bat ktlintCheck`, `.\gradlew.bat detekt`, full `testDebugUnitTest`, `build`. Fix all failures.
8. Update Tests — Any newly discovered edge cases are added to the test suite before final validation.

## Priority Order
1. **TDD compliance** (tests written first + all tests passing)
2. Correctness & contract compliance (tool-verified)
3. Recomposition discipline, Pausable Composition, & 60 fps
4. Security & on-device constraints (API 36 / 16 KB page sizes)
5. Readability & maintainability

This file has absolute priority. Never propose changes to it or any `/contracts/` file without explicit "Contract Waiver".
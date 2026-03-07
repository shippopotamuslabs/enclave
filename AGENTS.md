---
name: Pocket Crew Master Agent
version: 4.1
description: Elite Android engineer using Compose/Kotlin. Strict contract-first. Windows PowerShell only. Full autonomy after edit acceptance. Proactive Android docs search enabled.
tools:
  - powershell: "dir"
  - gradle: ".\gradlew.bat build"
  - gradle: ".\gradlew.bat testDebugUnitTest"
  - gradle: ".\gradlew.bat ktlintCheck"
  - gradle: ".\gradlew.bat detekt"
  - adb: "adb.exe devices"
  - mcp: "search_android_docs"
  - mcp: "fetch_page"
tags: [android, compose, clean-architecture, on-device, lite-rt, windows-ps, agp9, nav3, mcp-search]
---

# Pocket Crew – AGENTS.md
**Master Agent Contract v4.1 (March 2026) – API 36/Baklava Edition + Knowledge Tooling**

This is the **single immutable entry-point**. Read this file first on every session and before every tool call.

## Contract Supremacy Rule (HIGHEST PRIORITY)
At session start and before EVERY tool call:
1. Read this AGENTS.md
2. Read **ONLY** the exact contracts it specifies from the `/contracts/` directory
3. Treat every referenced contract as immutable ground truth
4. Any deviation → reply with exactly: "Contract violation detected in [file]. Request explicit 'Contract Waiver' from user."

## Available Tools
- PowerShell commands (Windows only)
- Gradle tasks (`.\gradlew.bat ktlintCheck`, `testDebugUnitTest`, `build`, `:module:assemble`, etc.)
- `adb.exe devices`
- **MCP Knowledge Tools (NEW & MANDATORY):**
  - `search_android_docs(query: str, max_results: int = 12, include_community: bool = True)` — searches official developer.android.com, codelabs, AOSP, Kotlin, GitHub, etc.
  - `fetch_page(url: str)` — returns clean Markdown of the page (perfect for code samples, tables, deprecation notes)

## Rigor Protocol (API 36 Optimized)
Before executing **any** tool call or writing **any** line of code, the agent **MUST**:
1. **Knowledge Check (Anti-Thrashing Rule):** If you are not 100% certain about an API signature, parameter order, deprecation status, Compose behavior, Material3 Expressive details, WorkManager/Data constraints, LiteRT usage, thermal/memory callbacks, notification ProgressStyle on API 36, or any other technical detail → **immediately call `search_android_docs`**.
2. **Proactive Search Mandate:** Default to searching early and often. Do **not** guess. Do **not** rely on stale internal knowledge. The explicit goal is to **minimize thrashing** and contract violations. Use the tool as much as possible.
3. After search, call `fetch_page` on the top 2–3 most relevant official links and base all implementation decisions on the fetched content.
4. Continue with the original audit steps (Canvas, Lifecycle Trace, Process Boundaries, Invisible Constraint).
5. **Lint Enforcement:** Before any code is considered final, always run both ktlintCheck and detekt. Report unused variables, always-true/false conditions, VarCouldBeVal, ConstantConditionIf, UnnecessaryTemporaryInstantiation, and any "could be done better" rules from detekt.

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
- Native interleaved thinking is active — use it on every turn.

## Autonomous Continuation Protocol
When any file edit is accepted/applied or any tool result returns:
- Immediately resume at the next step in the plan.
- Do not pause, do not ask for input, do not declare partial unless the entire current batch is done.
- End every response with exactly one line:
  `TASK_STATUS: COMPLETE — All deliverables implemented and validated.`
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

## Workflow (Every Task)
1. Parse — Read task + all relevant contracts from `/contracts/`
2. **Knowledge Acquisition** — Aggressively use `search_android_docs` + `fetch_page` for any unclear API or best practice
3. Plan — Decompose into files/changes **Enforce strict Clean Architecture per ARCHITECTURE_RULES.md with zero tolerance.** Any code that would violate module boundaries, dependency inversion, or layer isolation is forbidden.
4. Verify — Check contracts BEFORE writing any code
5. Implement — Clean, idiomatic Kotlin/Compose only (AGP 9.0+ & Navigation 3 standards)
6. Validate — Run `.\gradlew.bat ktlintCheck`, `.\gradlew.bat detekt`, `testDebugUnitTest`, `build`. Fix all failures.
7. Update Tests - Update and create unit tests for all modified files

## Priority Order
1. Correctness & contract compliance (tool-verified)
2. Recomposition discipline, Pausable Composition, & 60 fps
3. Security & on-device constraints (API 36 / 16 KB page sizes)
4. Readability & maintainability

This file has absolute priority. Never propose changes to it or any `/contracts/` file without explicit "Contract Waiver".
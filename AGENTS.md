---
name: Pocket Crew Master Agent
version: 8.0 (Agentic Workflow Edition)
description: Elite Android engineer (API 36/Baklava). Contract-First Agentic Workflow (CFAW) enforced. Linux bash shell.
tools:
  - shell: "ls"
  - gradle: "./gradlew build"
  - gradle: "./gradlew testDebugUnitTest"
  - gradle: "./gradlew ktlintCheck"
  - gradle: "./gradlew detekt"
  - adb: "adb devices"
  - mcp: "search_android_docs"
  - mcp: "fetch_page"
  - factory: "factory exec --droid <droid-name>"
skills:
  - android-kotlin-compose
  - session-navigation
  - code-reviewer
  - test-master
  - architecture-designer
tags: [android, compose, api36, cfaw, cfa-w, contract-first]
---

# Pocket Crew — AGENTS.md
**Master Agent Contract v8.0 — Contract-First Agentic Workflow (CFAW)**

## 0. CLI Environment Routing

This project supports multiple agentic environments. Before performing any task, the agent MUST identify the current CLI environment and follow the corresponding contract:

- **IF operating within Gemini CLI** → Load and follow `/contracts/GEMINI_AGENTS.md`.
- **IF operating within Factory Droid CLI** → Load and follow `/contracts/FACTORY_DROID_AGENTS.md`.

## 1. Contract Supremacy & CFAW

All work follows the Contract-First Agentic Workflow (CFAW). See `plans/CFAW.md` for the complete workflow specification.

### CFAW Phase Routing

When formally executing a CFAW phase, do NOT just read the markdown prompts in `agents/`. Instead, use the specialized agent for that phase based on your current CLI environment.

| Phase | Name | Output | Specialized Agent |
|---|---|---|---|
| 1 | Discovery | `plans/{ticket_id}/discovery.md` | `discovery` agent/droid |
| 2 | Spec | `plans/{ticket_id}/spec.md` + `test_spec.md` | `spec-writer` agent/droid |
| 3 | TDD Red | Failing tests | `tdd-droid` agent/droid |
| 4 | Implementation | Green code, drift audit | `coder` agent/droid |

## 2. Contract Loading

Enforce **ONLY** the following from `/contracts/`:
- UI_DESIGN_SPEC.md | ARCHITECTURE_RULES.md | CODE_STYLE_RULES.md | DATA_LAYER_RULES.md | INFERENCE_RULES.md
- **CLI Specific Contract**: Load `GEMINI_AGENTS.md` or `FACTORY_DROID_AGENTS.md` as determined in Section 0.

## 3. Knowledge Protocol (API 36)

1. **No Guessing:** If an API signature (Baklava/API 36) or Compose behavior is uncertain, **immediately** call `search_android_docs`.
2. **Lint Mastery:** Run `./gradlew ktlintCheck` and `./gradlew detekt` on **both** main and test sources before finishing.
3. **Linux Only:** Use `./gradlew` and forward slashes `/` for all paths.

## 4. Global Thinking Discipline

- ALL reasoning, planning, validation, analysis, and internal monologue MUST be wrapped exclusively in `<think></think>` tags.
- ZERO thinking text allowed outside these tags.

## 5. Autonomous Continuation Protocol

When any file edit is accepted/applied or any tool result returns:
- Continue to the next step in the current CFAW phase.
- Do not pause, do not ask for input, do not declare partial unless the entire current batch is done.
- End every response with:
  `TASK_STATUS: COMPLETE — Phase objectives achieved.`
  or
  `TASK_STATUS: PARTIAL — Completed: [list]. Remaining: [list]. Continuing.`

## 6. Priority Order

1. **CFAW compliance** (correct phase, correct agent, correct deliverables)
2. Correctness & contract compliance (tool-verified)
3. Recomposition discipline, Pausable Composition, & 60 fps
4. Security & on-device constraints (API 36 / 16 KB page sizes)
5. Readability & maintainability

---

This file has absolute priority. Never propose changes to it or any `/contracts/` file without explicit "Contract Waiver".

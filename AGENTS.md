---
name: Pocket Crew Master Agent
version: 7.0 (CFAW Edition)
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
droids:
  - architect
  - coder
  - tdd-droid
  - scrutiny-feature-reviewer
mcp:
  - context7
  - kimi-vision
  - MiniMax
  - mobile-mcp
tags: [android, compose, api36, cfaw, cfa-w, contract-first]
---

# Pocket Crew — AGENTS.md
**Master Agent Contract v7.0 — Contract-First Agentic Workflow (CFAW)**

## 0. Droid Routing

Use Factory Droid subagents with specialized models for different tasks:

### Droid Assignment

| Task Type | Droid | Model | Purpose |
|---|---|---|---|
| Architecture, ADRs, High-level Design | `architect` | `custom:gemini-3.1-pro-preview` | Strategic reasoning, system design, decision records |
| Implementation, Code Generation | `coder` | `custom:MiniMax-M2.7-highspeed-0` | Fast, high-quality code writing |
| TDD, Test Writing | `tdd-droid` | `custom:MiniMax-M2.7-highspeed-0` | Test-driven development workflow |
| Code Review | `scrupulously-reviewer` | `custom:MiniMax-M2.7-highspeed-0` | Critical code analysis |

### How to Invoke a Droid

Use Factory Droid's exec command to spawn a subagent:

```bash
factory exec --droid architect --prompt "Your architecture task here"
factory exec --droid coder --prompt "Your coding task here"
factory exec --droid tdd-droid --prompt "Your TDD task here"
```

### Droid Configuration Files

- Architect: `.factory/droids/architect.md` (Gemini 3.1 Pro)
- Coder: `~/.factory/droids/coder.md` (MiniMax M2.7)
- TDD: `.factory/droids/tdd-droid.md` (MiniMax M2.7)
- Reviewer: `~/.factory/droids/scrutiny-feature-reviewer.md` (MiniMax M2.7)

### Routing Rules

1. **Architecture/Design tasks** → Use `architect` droid (Gemini 3.1 Pro)
2. **Implementation/Coding tasks** → Use `coder` droid (MiniMax)
3. **Test-writing tasks** → Use `tdd-droid` (MiniMax)
4. **Code review tasks** → Use `scrupulously-reviewer` (MiniMax)
5. **Complex tasks** → Orchestrate multiple droids in sequence

## 1. Contract Supremacy & CFAW

All work follows the Contract-First Agentic Workflow (CFAW). See `plans/CFAW.md` for the complete workflow specification.

### CFAW Phases

| Phase | Name | Output | Agent Document |
|---|---|---|---|
| 1 | Discovery | `plans/{ticket_id}/discovery.md` | `agents/DISCOVERY.md` |
| 2 | Spec | `plans/{ticket_id}/spec.md` + `test_spec.md` | `agents/SPEC.md` |
| 3 | TDD Red | Failing tests | `agents/TDD.md` |
| 4 | Implementation | Green code, drift audit | `agents/IMPLEMENTATION.md` |

### Tier Routing

| Tier | Scope | Workflow |
|---|---|---|
| Tier 1 — Atomic | Self-contained, no cross-layer ripple | Direct prompt under Constitution |
| Tier 2 — Modular | New screen, API, DB, core logic | Full CFAW lifecycle |
| Tier 3 — Systemic | Major refactors, migrations | Tier 2 + Reviewer Agent + rollback plan |

## 2. Contract Loading

Enforce **ONLY** the following from `/contracts/`:
- UI_DESIGN_SPEC.md | ARCHITECTURE_RULES.md | CODE_STYLE_RULES.md | DATA_LAYER_RULES.md | INFERENCE_RULES.md

## 3. Agent Phase Routing

When starting a task, determine your current phase and load the appropriate agent document:

**If working on Discovery**: Load `agents/DISCOVERY.md`
**If working on Specification**: Load `agents/SPEC.md`
**If working on Tests**: Load `agents/TDD.md`
**If working on Implementation**: Load `agents/IMPLEMENTATION.md`

## 4. Knowledge Protocol (API 36)

1. **No Guessing:** If an API signature (Baklava/API 36) or Compose behavior is uncertain, **immediately** call `search_android_docs`.
2. **Lint Mastery:** Run `./gradlew ktlintCheck` and `./gradlew detekt` on **both** main and test sources before finishing.
3. **Linux Only:** Use `./gradlew` and forward slashes `/` for all paths.

## 5. Available Tools

- Shell commands (Linux/bash)
- Gradle tasks (`./gradlew ktlintCheck`, `testDebugUnitTest`, `build`, `:module:assemble`, etc.)
- `adb devices`
- **MCP Knowledge Tools (MANDATORY):**
  - `search_android_docs(query: str, max_results: int = 12, include_community: bool = True)` — searches official developer.android.com, codelabs, AOSP, Kotlin, GitHub, etc.
  - `fetch_page(url: str)` — returns clean Markdown of the page

## 6. Global Thinking Discipline

- ALL reasoning, planning, validation, analysis, and internal monologue MUST be wrapped exclusively in `<think></think>` tags.
- ZERO thinking text allowed outside these tags — not in responses, not in code blocks, not in tool calls.
- Native interleaved thinking is active — use it on every turn.

## 7. Autonomous Continuation Protocol

When any file edit is accepted/applied or any tool result returns:
- Continue to the next step in the current CFAW phase
- Do not pause, do not ask for input, do not declare partial unless the entire current batch is done
- End every response with:
  `TASK_STATUS: COMPLETE — Phase objectives achieved.`
  or
  `TASK_STATUS: PARTIAL — Completed: [list]. Remaining: [list]. Continuing.`

## 8. Strict Boundaries

| Always | Never |
|---|---|
| Follow CFAW phase order | Skip phases or write code in wrong phase |
| Load appropriate agent document | Ignore agent persona and responsibilities |
| Reference Constitution for architectural rules | Violate Constitution rules |
| Run ktlintCheck and detekt | Use Windows commands (`dir`, `findstr`, etc.) |
| Use forward slashes `/` in Linux paths | Use backslashes `\\` in file paths |
| Use mockk for unit tests | Use mockito for unit tests |
| Use JUnit Jupiter 5 for unit tests | Use JUnit 4 or other libraries |
| Derive implementation from spec | Derive implementation from tests |
| Surface spec ambiguities | Resolve ambiguities by reading tests |

## 9. Priority Order

1. **CFAW compliance** (correct phase, correct deliverables)
2. Correctness & contract compliance (tool-verified)
3. Recomposition discipline, Pausable Composition, & 60 fps
4. Security & on-device constraints (API 36 / 16 KB page sizes)
5. Readability & maintainability

## 10. Constitution References

For detailed TDD patterns and testing conventions:
- `agents/TDD.md` — Phase 3 TDD Red agent with golden-test-examples references
- `golden-test-examples/` — Reference implementations for testing patterns

This file has absolute priority. Never propose changes to it or any `/contracts/` file without explicit "Contract Waiver".

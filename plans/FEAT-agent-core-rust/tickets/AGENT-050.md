# AGENT-050 — Schema hint contract + Kotlin parameter class parity CI check

| Field | Value |
|-------|--------|
| **Type** | CI |
| **Epic** | E8 — Preflight unification |
| **Depends on** | AGENT-005, AGENT-038 |
| **Blocks** | — |

## Description

Extends AGENT-038 (name sync) with a deeper schema hint check.

**CI job:** `agent-catalog-schema-parity`

Script `scripts/check-catalog-schema-parity.sh`:

1. Extract all `ToolParameters` subclasses from `core/domain/src/main/kotlin/.../model/inference/` (each Kotlin tool has a `ToolParameters` subclass with `@Serializable` fields)
2. For each tool in `tool_catalog.json` that has a `schema_hint.required_fields` entry:
   - Verify every field in `required_fields` exists as a Kotlin field in the corresponding `ToolParameters` class
3. Fail CI with a diff if names don't match

This catches: adding a required field to the Rust catalog hint without updating Kotlin (or vice versa).

**Trigger:** MR touching `resources/tool_catalog.json` OR any `*Params.kt` / `*Parameters.kt` file under `core/domain`.

## Acceptance criteria

- [ ] Script correctly maps tool name to `ToolParameters` subclass (e.g., `append_blocks` → `AppendBlocksParams.kt`)
- [ ] CI fails with clear message if a hint field name has no matching Kotlin field
- [ ] Passes on current codebase at time of implementation (no false positives)
- [ ] Documented in `agent_core/README.md` under "Keeping catalog in sync"

## Files

- `scripts/check-catalog-schema-parity.sh`
- `.gitlab-ci.yml` (new job)

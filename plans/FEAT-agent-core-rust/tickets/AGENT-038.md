# AGENT-038 — Tool catalog sync CI check

| Field | Value |
|-------|--------|
| **Type** | CI |
| **Epic** | E8 — Preflight unification |
| **Depends on** | AGENT-005 |
| **Blocks** | — |

## Description

Add script `scripts/check-tool-catalog-sync.sh` (or Gradle task):

- Extract tool names from `ToolDefinition.kt`
- Compare to `third_party/agent_core/resources/tool_catalog.json`
- Fail CI if Kotlin adds tool without Rust catalog entry

## Acceptance criteria

- [ ] CI job runs on MRs touching either file
- [ ] Clear error message listing missing tool names
- [ ] Documented in `agent_core/README.md`

## Files

- `scripts/check-tool-catalog-sync.sh`
- `.gitlab-ci.yml`

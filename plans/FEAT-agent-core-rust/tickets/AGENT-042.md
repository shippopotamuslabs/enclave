# AGENT-042 — Versioning strategy for `agent_core`

| Field | Value |
|-------|--------|
| **Type** | DevOps |
| **Epic** | E10 — Desktop & documentation |
| **Depends on** | AGENT-002 |
| **Blocks** | AGENT-041 |

## Description

Define release process:

- Semver for `agent_core` crate
- Git tags aligned with Pocket Crew releases
- Submodule bump checklist: Rust tag → run `build-agent-bindings.sh` → commit `.so` + Kotlin → Android QA

## Acceptance criteria

- [ ] `third_party/agent_core/README.md` includes versioning section
- [ ] GitLab release template snippet or `CONTRIBUTING` note added
- [ ] Breaking FFI changes require major version bump documented

## Files

- `third_party/agent_core/README.md`
- Optional: `.gitlab/issue_templates/agent_core_release.md`

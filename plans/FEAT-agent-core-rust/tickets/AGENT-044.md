# AGENT-044 — E2E: artifact tool loop integration test

| Field | Value |
|-------|--------|
| **Type** | Test |
| **Epic** | E11 — QA & release |
| **Depends on** | AGENT-036, AGENT-020 |
| **Blocks** | — |

## Description

Add integration test with `UseRustAgentLoop` enabled:

- Artifact generation flow with multiple `append_blocks` turns
- Assert no context crash / `IllegalStateException`
- Assert compaction event or log when using forced small `contextWindow` test config

Robolectric or `androidTest` in `:feature:inference` or `:app`.

## Acceptance criteria

- [ ] Test runs in CI (or documented manual gate if too heavy)
- [ ] Test plan section added to `plans/FEAT-agent-core-rust/README.md` release checklist
- [ ] Fails if legacy warn-and-crash path reintroduced

## Files

- `feature/inference/src/test/...` or `src/androidTest/...`

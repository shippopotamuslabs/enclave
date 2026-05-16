# FEAT: Rust `agent_core` (UniFFI) — Agent loop unification

Shared Rust agent harness for **Pocket Crew (Android)**, exposed to Kotlin via UniFFI (same pattern as `docgen_core` → `:core:docgen`). Architecture is **desktop-ready** — native Rust host traits (no UniFFI) make it trivially consumable by a future desktop app.

## End state (what "done" means)

When all tickets in [`tickets/`](./tickets/) are complete:

1. **One agent loop implementation** in `third_party/agent_core` — turn state machine, compaction, tool repair, guardrails, session accounting. No duplicated logic across providers.
2. **Auto-compaction at context threshold (80%)** — tiered pipeline (Micro → FIFO → ArtifactSquash → LLM summary) that **continues** the loop instead of warn-then-crash.
3. **Self-healing tool calls** — normalize names, aliases, fuzzy match (Levenshtein ≥ 0.85), argument coercion, structured errors back to the model, soft-failure streak limits.
4. **Android integration** — `:core:agent` JNI module; inference services drive the Rust engine via thin **transport** and **tool executor** adapters; feature flag removed after cutover; legacy `LlmToolingOrchestrator` deleted.
5. **Desktop-ready** — native Rust host traits (`AgentTransport`, `AgentToolExecutor`, `AgentSummarizer`) allow direct consumption without UniFFI; desktop app integration deferred to a future repo/ticket.
6. **LiteRT** — policy bridge (repair, truncate, guardrails) even if the SDK still owns the native tool loop.

**In short:** a robust, S-tier-style agent loop with self-healing tool calls and automatic context management — Android ships first, desktop-ready by construction.

## Architecture decisions (locked)

| Decision | Value |
|----------|-------|
| Context threshold | `0.80` |
| Compaction tier order | Micro → FIFO → ArtifactSquash → Summary |
| Compaction stop policy | Stop after first tier that relieves pressure |
| Summary tier mechanism | Action-driven (`NeedsHostCall` → `RequestSummary`) |
| Tool fuzzy match threshold | Levenshtein ≥ 0.85 |
| Cloud tool result budget | 4 KB/result |
| Local tool result budget | `SessionCapabilities.tool_result_budget_tokens` |
| JNI churn budget | < 5 ms/turn p50 |
| kv_fixed compaction | Allowed; transport accepts KV cache reset cost |
| Session handle (UniFFI) | Object reference (Arc, no u64) |

## Ticket index

| ID | Title | Status |
|----|--------|--------|
| [AGENT-001](./tickets/AGENT-001.md) | ADR: Rust agent harness & host split | |
| [AGENT-002](./tickets/AGENT-002.md) | Create `agent_core` Rust crate | |
| [AGENT-003](./tickets/AGENT-003.md) | Rust CI: `agent_core` test job | |
| [AGENT-004](./tickets/AGENT-004.md) | Core session & message types | |
| [AGENT-005](./tickets/AGENT-005.md) | Tool catalog & static aliases | |
| [AGENT-006](./tickets/AGENT-006.md) | `AgentError` & event types | |
| [AGENT-007](./tickets/AGENT-007.md) | Turn I/O types | |
| [AGENT-008](./tickets/AGENT-008.md) | Token ledger & budget | |
| [AGENT-009](./tickets/AGENT-009.md) | Compaction pipeline orchestrator | |
| [AGENT-010](./tickets/AGENT-010.md) | Tier 1: Micro-compact | |
| [AGENT-011](./tickets/AGENT-011.md) | Tier 2: FIFO | |
| [AGENT-012](./tickets/AGENT-012.md) | Tier 3: LLM summary (host callback) | |
| [AGENT-013](./tickets/AGENT-013.md) | Tier 4: Artifact squash | |
| [AGENT-014](./tickets/AGENT-014.md) | Tool repair pipeline | |
| [AGENT-015](./tickets/AGENT-015.md) | Guardrails | |
| [AGENT-016](./tickets/AGENT-016.md) | Auto-finalize artifacts | |
| [AGENT-017](./tickets/AGENT-017.md) | Session state machine (`AgentEngine`) | |
| [AGENT-018](./tickets/AGENT-018.md) | Preflight compaction | |
| [AGENT-019](./tickets/AGENT-019.md) | Tool result truncation | |
| [AGENT-020](./tickets/AGENT-020.md) | Golden / fixture test harness | |
| [AGENT-021](./tickets/AGENT-021.md) | UniFFI exports | |
| [AGENT-022](./tickets/AGENT-022.md) | UniFFI host callbacks | |
| [AGENT-023](./tickets/AGENT-023.md) | `build-agent-bindings.sh` | |
| [AGENT-024](./tickets/AGENT-024.md) | Android module `:core:agent` | |
| [AGENT-025](./tickets/AGENT-025.md) | Developer docs for bindings | |
| [AGENT-026](./tickets/AGENT-026.md) | `RustAgentEngineAdapter` | |
| [AGENT-027](./tickets/AGENT-027.md) | `AgentTransportAdapter` | |
| [AGENT-028](./tickets/AGENT-028.md) | `AgentSummarizerAdapter` | |
| [AGENT-029](./tickets/AGENT-029.md) | `AgentToolExecutorAdapter` | |
| [AGENT-030](./tickets/AGENT-030.md) | Feature flag `UseRustAgentLoop` | deferred by decision |
| [AGENT-031](./tickets/AGENT-031.md) | Migrate OpenAI-compatible services | |
| [AGENT-032](./tickets/AGENT-032.md) | Migrate Anthropic & Google | |
| [AGENT-033](./tickets/AGENT-033.md) | Migrate Llama (envelope) | |
| [AGENT-034](./tickets/AGENT-034.md) | LiteRT policy bridge | |
| [AGENT-035](./tickets/AGENT-035.md) | CREW / catalog alignment | deferred |
| [AGENT-036](./tickets/AGENT-036.md) | Default flag on & soak release | deferred by decision |
| [AGENT-037](./tickets/AGENT-037.md) | Slim `ChatHistoryRehydrator` | |
| [AGENT-038](./tickets/AGENT-038.md) | Tool catalog sync CI check | |
| [AGENT-039](./tickets/AGENT-039.md) | Agent events → logging / UI | |
| [AGENT-040](./tickets/AGENT-040.md) | Persist compaction boundaries | deferred |
| [AGENT-041](./tickets/AGENT-041.md) | Desktop: `agent_core` contract & docs | |
| [AGENT-042](./tickets/AGENT-042.md) | Versioning strategy | |
| [AGENT-043](./tickets/AGENT-043.md) | Remove deprecated Kotlin code | |
| [AGENT-044](./tickets/AGENT-044.md) | E2E: artifact tool loop | |
| [AGENT-045](./tickets/AGENT-045.md) | Performance & memory benchmark | deferred by decision |
| [AGENT-046](./tickets/AGENT-046.md) | Native Rust host trait surface | |
| [AGENT-047](./tickets/AGENT-047.md) | `KvCacheInvalidated` + kv_fixed compaction test | |
| [AGENT-048](./tickets/AGENT-048.md) | Android CI: NDK cross-compile + assemble smoke | |
| [AGENT-049](./tickets/AGENT-049.md) | Engine event drain API + Kotlin Flow plumbing | |
| [AGENT-050](./tickets/AGENT-050.md) | Schema hint parity CI check | |

## Milestones

| Milestone | Tickets | Exit |
|-----------|---------|------|
| M0 Foundation | 001–003 | ADR locked + crate + CI |
| M1 Rust core | 004–020, 046, 047 | `cargo test` green, kv_fixed test passes |
| M2 FFI / Android crate | 021–025, 048 | JNI loads, Android CI job green |
| M3 Adapters | 026–030, 049 | Flag off, adapters wired, `Flow<AgentLoopEvent>` works |
| M4 Integration | 031–038, 050 | All backends migrated, flag on by default |
| M5 Ship | 039, 041, 042, 043, 044, 045 | E2E passes, benchmarks under budget, legacy deleted |

## Release checklist

- Rebuild Android bindings with `./scripts/build-agent-bindings.sh` after any `agent_core` FFI or version change.
- Follow `core/agent/README.md` for checked-in binding policy and Android troubleshooting.
- Verify desktop native trait smoke with `cargo run --example mock_desktop_loop`.
- Run the artifact tool-loop E2E gate with `UseRustAgentLoop` enabled and a forced small context window; it must fail if the
  legacy warn-and-crash path is reintroduced.
- Record AGENT-045 latency and memory results in `benchmarks.md` before deleting legacy Kotlin orchestration.

## Critical path

```
001→002→003→004→005→006→007→046→008→009→{010,011,012,013}→014→015→017→016
                                          ↓                           ↓
                                        047                    018,019,020
                                                                     ↓
                                                             021→022→023→024→025
                                                                          ↓
                                                               026→027→029→030→031→032
                                                               026→028
                                                               026→033(+047)→036
                                                               026→034(+047)→036
                                                                           ↓
                                                               036→037→038→050
                                                               036→039→044→045→043
                                                               036→041,042,048,049
```

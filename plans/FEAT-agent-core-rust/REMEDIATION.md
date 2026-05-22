# FEAT-agent-core-rust — Remediation Plan

**Status:** Active
**Author:** Review-Singularity audit, 2026-05-15
**Driver:** post-audit cleanup to reach a "complete, no bugs, sound architecture, repo contracts followed" merge bar.

This plan addresses the gaps surfaced by the review-singularity audit of `feat/agent-core` against `plans/FEAT-agent-core-rust/`. The branch implements the migration of the Kotlin agent tool loop into a shared Rust crate (`third_party/agent_core`) exposed via UniFFI through the new `:core:agent` Android module. The user has cut feature-flagging / staged rollout (AGENT-030, AGENT-036) — this is a single-shot migration. AGENT-035 and AGENT-040 remain plan-deferred. AGENT-045 (perf benchmarks) is deferred-by-decision (no users to gate on).

---

## Decisions locked during plan revision

| Question | Decision | Source |
|---|---|---|
| Tool execution parallelism | **Parallel.** `RustLoopInferenceService.executeTools` dispatches each call in `async(Dispatchers.IO)`. | User answer 2026-05-15 |
| System prompt double-application | **Apply once.** Drop the `Role.SYSTEM` prepend in `RustLoopInferenceService.buildHistory`; keep `AgentLoopSessionIoParams.systemPrompt` as the single source. | User answer 2026-05-15 |
| LiteRT scope | **Keep LiteRT on its native SDK loop.** Unify only via the policy bridge (AGENT-034 original intent). Replacing `automaticToolCalling` would lose Gemma-specific prompt formatting, tool-call output parsing, and forward compatibility with future LiteRT/Gemma updates. | Re-litigation 2026-05-15 |
| Summary tier executor | **Use `RollingSummarizer` + FAST model (AGENT-028).** Rust orchestrates the tier (decides when to compact and emits `RequestSummary`); the host callback in Kotlin performs the model call against a cheap FAST model rather than recursing into the chat's expensive thinking model. | User answer + clarification 2026-05-15 |
| CI path filter on `rust-agent-core` | **Expand `only.changes`** to include Kotlin tool-definition paths so AGENT-038/AGENT-050 cannot be silently bypassed by a Kotlin-only tool change. | Recommendation 2026-05-15 |
| `unwrapRustLoop` reflection in tests | **Replace with behavioral assertions** (mock SDK + assert routing through `sendPrompt`). Drop reflection entirely. | Best-practice recommendation 2026-05-15 |
| AGENT-026 layering rule for LiteRT bridge | **Option A: relocate `LiteRtAgentPolicyBridge` behind a `:core:domain` port.** `:feature:inference` depends on the port, not the JNI module. Restores AGENT-026's literal contract. | Recommendation 2026-05-15 |

---

## Out of scope

- **AGENT-030, AGENT-036** — feature flag, soak release. Cut by user decision.
- **AGENT-035** — CREW pipeline. Plan-deferred.
- **AGENT-040** — compaction boundary persistence UI. Plan-deferred.
- **AGENT-045** — perf benchmarks. Deferred-by-decision (no users; not gating).
- **Replacing LiteRT's `automaticToolCalling`** — see decision table above. AGENT-034 stays as "policy bridge (partial unification)".

---

## Phases

Phases are ordered by dependency. Each phase is independently runnable, testable, and mergeable.

### Phase 0 — Reproducibility plumbing (prereq, no behavior change)

The branch currently doesn't build for anyone else: `third_party/agent_core` has the engine, FFI surface, repair pipeline, truncation, guardrails, examples, and tests untracked; `pocket-crew` has the entire `:core:agent` module, new domain port, and provider-side `RustLoopInferenceService` unstaged.

| Step | Action |
|---|---|
| 0.1 | In `third_party/agent_core`, commit modified + untracked files in coherent slices (`feat(AGENT-021..046): engine + FFI + repair + truncate + guardrails + tests + examples`). Push. |
| 0.2 | Bump submodule pointer in `pocket-crew` to that commit: `chore(submodule): bump agent_core to M2/M3 batch`. |
| 0.3 | Run `./scripts/build-agent-bindings.sh`; commit the generated `core/agent/src/main/kotlin/com/browntowndev/pocketcrew/core/agent/agent_core.kt` and `core/agent/src/main/jniLibs/arm64-v8a/libagent_core.so`. |
| 0.4 | Stage and commit unstaged Kotlin in `core/domain`, `core/agent`, `feature/inference`, `feature/chat-inference-service`, `core/data`. Group by phase intent (M2 module / M3 adapters / M4 migration / M5 slim). |
| 0.5 | Commit `.gitlab-ci.yml`, `settings.gradle.kts`, `build.gradle.kts`, scripts, plans/README updates. |

**Verify:** a fresh `git clone --recursive` + `./gradlew :core:agent:assembleDebug :feature:inference:assembleDebug test` succeeds without local working-tree state.

---

### Phase 1 — Critical correctness fixes

These apply to the cloud and Llama paths. LiteRT's parallel fixes happen in Phase 2.

#### 1.1 — Catch executor exceptions and convert to soft-failure JSON

**File:** `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/RustLoopInferenceService.kt`

Wrap each `toolExecutor.execute(request)` in try/catch. On exception, emit error JSON as `resultJson` with `softFailure = true`.

```kotlin
override suspend fun executeTools(calls: List<AgentToolCall>): List<AgentToolResult> =
    coroutineScope {
        calls.map { call ->
            async(Dispatchers.IO) { executeOne(call, options) }
        }.awaitAll()
    }

private suspend fun executeOne(call: AgentToolCall, options: GenerationOptions): AgentToolResult =
    try {
        val request = ToolCallRequest(...)
        val result = toolExecutor.execute(request)
        AgentToolResult(call.callId, result.resultJson, truncated = false, softFailure = false)
    } catch (t: Throwable) {
        loggingPort.error(TAG, "tool execution threw tool=${call.name}", t)
        AgentToolResult(call.callId, errorJson(call, t), truncated = false, softFailure = true)
    }
```

(`softFailure = false` here is intentional — the engine's `is_soft_failure_result_json` fallback handles JSON-shape detection. See 3.4.)

**Tickets:** AGENT-029 acceptance "Exceptions → error JSON result, not crash".
**Verify:** `RustLoopInferenceServiceTest.executeTools_translatesThrowable_toSoftFailureResult`.

#### 1.2 — Llama session capabilities

**File:** `RustLoopInferenceService.buildAgentLoopConfig`

```kotlin
val capabilities = when (provider) {
    "LLAMA" -> AgentLoopSessionCapabilities(
        summarizationAllowed = false,
        kvFixed = true,
        toolResultBudgetTokens = options.toolResultBudgetTokens,
    )
    else -> AgentLoopSessionCapabilities()
}
```

Without this, the Rust engine will fire the Summary tier on Llama (recursively invoking the local model for summarization — perf disaster on-device) and never emit `KvCacheInvalidated`.

**Tickets:** AGENT-033, ADR §kv_fixed.
**Verify:** unit test that `buildAgentLoopConfig("LLAMA", ...)` returns `kvFixed=true, summarizationAllowed=false`.

#### 1.3 — Populate `sessionContext`

**File:** `RustLoopInferenceService.sendPrompt`

```kotlin
val sessionContext = buildMap {
    options.chatId?.value?.let { put("chat_id", it) }
    options.userMessageId?.value?.let { put("user_message_id", it) }
    options.assistantMessageId?.value?.let { put("assistant_message_id", it) }
}
```

**Tickets:** AGENT-029 acceptance "chatId / message id plumbing test".
**Verify:** test that `sessionContext` reaches the Rust engine through `RustAgentEngineAdapter`.

#### 1.4 — AGENT-049 event mapping + final drain

**Files:**

- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/inference/AgentLoopPort.kt` — extend `AgentLoopEvent`:
  ```kotlin
  data class TurnStarted(val index: Int) : AgentLoopEvent
  data class TurnCompleted(val index: Int, val toolCallCount: Int) : AgentLoopEvent
  ```
- `core/agent/src/main/kotlin/com/browntowndev/pocketcrew/core/agent/AgentEventMapper.kt`:
  - Map `FfiAgentEvent.TurnStarted` and `TurnCompleted` to the new domain events.
  - `KvCacheInvalidated` returns `null` from `toDomain()` (keep the `log()` path; do not leak to the domain Flow).
- `core/agent/.../RustAgentEngineAdapter.kt` — drain events one final time after the `Finished`/`Failed` branch matches, before sending the terminal `AgentLoopEvent` and returning.
- `feature/inference/.../RustLoopInferenceService.kt` — handle the new `TurnStarted`/`TurnCompleted` events (no-op or log; UI hookup deferred to Phase 3.5).

**Tickets:** AGENT-049 acceptance "drain after 3 emitted events → list size 3; drain again → size 0", "KvCacheInvalidated handled internally", "Flow emits CompactionApplied".
**Verify:** `AgentEventMapperTest` extended for new mappings; `RustAgentEngineAdapterTest` 2-tool-turn flow with terminal-event drain.

---

### Phase 2 — LiteRT policy bridge completion + AGENT-026 cleanup

LiteRT keeps `automaticToolCalling`; we close the small gaps that prevent the bridge from being a full unification.

#### 2.1 — Add `should_allow_tool` streak tracking to `ConversationManagerImpl`

**File:** `feature/inference/.../ConversationManagerImpl.kt`

LiteRT's native loop dispatches tools without consulting our soft-failure streak. Add streak state and gate dispatch:

```kotlin
@Volatile private var consecutiveSoftFailures: Int = 0

private fun checkBeforeDispatch(request: ToolCallRequest): ToolCallRequest? {
    if (!LiteRtAgentPolicyPort.shouldAllowTool(consecutiveSoftFailures.toUInt(), MAX_STREAK)) {
        loggingPort.warning(TAG, "soft-failure streak reached limit; refusing tool dispatch tool=${request.toolName}")
        return null  // caller injects a stop-tools synthetic result
    }
    return request
}
```

After each result, update streak based on `LiteRtAgentPolicyPort.resultJsonLooksSoftFailure` (see 2.2).

#### 2.2 — Add JSON-shape soft-failure detection to LiteRT shim

**File:** `feature/inference/.../LocalToolResultPolicy.kt`

Replace the LiteRT shim's local heuristic with a call to `LiteRtAgentPolicyPort.resultJsonLooksSoftFailure(json)` so detection is identical to the Rust loop. (`LocalToolResultPolicy` cloud paths get deleted entirely in Phase 5; the LiteRT shim stays.)

#### 2.3 — Relocate the bridge behind a domain port (AGENT-026 compliance)

**Files:**

- New: `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/inference/AgentPolicyPort.kt` — interface with `repairTool`, `truncateResult`, `shouldAllowTool`, `resultJsonLooksSoftFailure`.
- Move the implementation: `core/agent/src/main/kotlin/com/browntowndev/pocketcrew/core/agent/LiteRtAgentPolicyBridge.kt` → keep here, but make it `@Singleton class` implementing `AgentPolicyPort`.
- `core/agent/.../AgentModule.kt` — add `@Binds` for `AgentPolicyPort`.
- `feature/inference/.../ConversationManagerImpl.kt` — inject `AgentPolicyPort` instead of importing `LiteRtAgentPolicyBridge` directly. Remove the `:core:agent` import.

`:feature:inference` no longer imports `:core:agent` types in production code. AGENT-026 acceptance "no `core.agent` imports from `:feature:inference`" is restored.

#### 2.4 — Verify existing bridge wiring is correct

Re-audit `ConversationManagerImpl` against the (now-completed) bridge:

- `repairLiteRtToolRequest` is called on every `executeToolSafely` entry. ✓ (already present)
- `truncate_result` is called on the context-full branch. ✓ (already present)
- `shouldAllowTool` gates dispatch. (added in 2.1)
- `resultJsonLooksSoftFailure` drives soft-failure classification. (added in 2.2)

**Tickets:** AGENT-034 acceptance "fuzzy tool-name repair works for LiteRT", "context-full hard behavior replaced with `truncate_result`", "`policy_bridge.rs` FFI exports compile alongside main engine FFI". AGENT-026 acceptance "no `core.agent` imports from `:feature:inference`".
**Verify:** `ConversationManagerImplPolicyBridgeTest` — asserts `shouldAllowTool` gates dispatch after streak threshold; `resultJsonLooksSoftFailure` matches Rust detection across `{"error":...}`, `{"status":"error"}`, `{"data":null}`, `{"results":[]}` shapes (the last must NOT be classified as soft-failure).

---

### Phase 3 — Important / spec gaps

#### 3.1 — Parallel tool execution

Already covered by 1.1's `coroutineScope { ... async { ... }.awaitAll() }` form.

**Verify:** `RustLoopInferenceServiceTest.executeTools_runsCallsInParallel` (use a coordinated latch / `delay` to prove overlap; assert wall-time < sum of per-call delays).

#### 3.2 — Remove system prompt double-application

**File:** `RustLoopInferenceService.kt`

`buildHistory` no longer prepends a `Role.SYSTEM` message; the system prompt enters the engine only via `AgentLoopSessionIoParams.systemPrompt`. Each provider's `completeSingleTurn` already accepts `systemPrompt` separately and applies it once.

**Verify:** per-provider test asserting `completeSingleTurn` produces exactly one system-role message regardless of `sessionMessages` SYSTEM entries.

#### 3.3 — Wire `RollingSummarizer` + FAST model

**File:** `RustLoopInferenceService.summarize`

```kotlin
override suspend fun summarize(request: AgentSummaryRequest): AgentSummaryResult {
    val summaryText = inferenceFactory.withInferenceService(ModelType.FAST) { fastService ->
        rollingSummarizer.summarize(
            messages = request.messagesToSummarize.map { it.toChatMessage() },
            service = fastService,
        )
    } ?: "Summary unavailable."
    return AgentSummaryResult(summaryText, request.lastMessageId)
}
```

Inject `Provider<InferenceFactoryPort>` and `RollingSummarizer` into `RustLoopInferenceService`. (Use `Provider<>` to break the constructor cycle: `InferenceFactoryImpl` constructs `RustLoopInferenceService` and would otherwise depend on its own port.)

**Tickets:** AGENT-028 acceptance "integration test with fake `LlmInferencePort` returns summary and engine continues", "Not invoked when `summarization_allowed = false`".
**Verify:** `RustLoopInferenceServiceTest.summarize_usesRollingSummarizerWithFastModel`.

#### 3.4 — Soft-failure detection: single source of truth

**Decision:** put soft-failure heuristics in **Rust**. Kotlin always sets `softFailure = false`; the engine runs `is_soft_failure_result_json` as the fallback (already does at `engine/mod.rs:291`). Extend the Rust detection to cover the legacy Kotlin `LocalToolResultPolicy` shapes.

**File:** `third_party/agent_core/src/guardrails.rs::is_soft_failure_result_json`

Extend to detect:
- top-level `error` key (already supported)
- `status: "error"` (case-insensitive)
- `data: null` with no `results` array

Add Rust unit tests for each shape. Kotlin keeps `softFailure = false`; the engine is the single source of truth for streak guardrail input.

**Tickets:** AGENT-029 spec drift, AGENT-014/015 streak guardrail.
**Verify:** Rust `cargo test guardrails` covers each shape; Kotlin `RustLoopInferenceServiceTest` asserts the engine receives the raw JSON unchanged.

#### 3.5 — `InferenceEvent` extensions for compaction/repair (AGENT-039)

**File:** `core/domain/.../port/inference/LlmInferencePort.kt`

```kotlin
data class CompactionApplied(val tier: String, val tokensBefore: Int, val tokensAfter: Int, val modelType: ModelType) : InferenceEvent
data class ToolRepaired(val originalName: String, val resolvedName: String, val confidence: Float, val modelType: ModelType) : InferenceEvent
```

`RustLoopInferenceService.sendPrompt`'s event collector emits these alongside the existing log lines. UI consumers (debug screens) can subscribe later — no UI change required for this phase.

**Tickets:** AGENT-039 acceptance "events testable with fake logging port", "no message content or tool arguments in compaction logs".
**Verify:** `RustLoopInferenceServiceTest.compactionEvent_propagatesToInferenceFlow`.

#### 3.6 — CI path filter expansion

**File:** `.gitlab-ci.yml`

```yaml
rust-agent-core:
  only:
    changes:
      - third_party/agent_core/**/*
      - scripts/check-tool-catalog-sync.sh
      - scripts/check-catalog-schema-parity.py
      - core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/ToolDefinition.kt
      - core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/**/*Params*.kt
```

**Tickets:** AGENT-038, AGENT-050.
**Verify:** push a Kotlin-only `ToolDefinition.kt` change in a throwaway MR; confirm the job fires.

#### 3.7 — Replace `unwrapRustLoop` reflection (best practice)

**File:** `feature/inference/src/test/kotlin/.../InferenceFactoryImplTest.kt`

Replace each `assertEquals("XxxImpl", unwrapRustLoop(service)...)` with a behavioral assertion:

1. Configure the mock SDK client (`openAiClientProvider`, `anthropicClient`, `googleGenAiClientProvider`) to record one call.
2. Run `service.sendPrompt("test", closeConversation = true)` collecting one turn.
3. Assert the right SDK got the call with the expected `modelId` and credentials.

Routing is proved without coupling to wrapper internals. Delete:

- `withInferenceService wraps RustCompatibleInferenceService in RustLoopInferenceService` (tautological).
- `executeToolSafely always delegates to tool executor for LITERT provider` in `ConversationManagerImplRegressionTest` (tautological). Replace with a real LiteRT context-full regression: assert that when truncate fires, the truncated payload reaches the LiteRT loop and the conversation continues.

**Tickets:** repo `UNIT_TESTING.md` / `CODE_STYLE_RULES.md` — state-based assertions, no implementation coupling.
**Verify:** all `InferenceFactoryImplTest` tests still green; reflection-based unwrap is gone.

---

### Phase 4 — Test gaps

| Test | File | Asserts |
|---|---|---|
| `RustAgentEngineAdapterTest.fullLoopWithMockEngine_yieldsFinished` | `core/agent/src/test/kotlin/.../RustAgentEngineAdapterTest.kt` | AGENT-026 acceptance: end-to-end loop with fake callbacks. Introduce a thin `EngineSession` indirection over `FfiAgentEngine` if needed for mocking. |
| `RustAgentEngineAdapterTest.flowEmitsCorrectSequenceFor2ToolTurn` | same | AGENT-026 acceptance. |
| `RustAgentEngineAdapterTest.terminalAction_drainsEventsBeforeReturning` | same | Phase 1.4 fix. |
| `RustLoopInferenceServiceTest` (new) | `feature/inference/src/test/kotlin/.../RustLoopInferenceServiceTest.kt` | host-callback wiring (text/thinking forwarded), `executeTools` parallelism + exception handling, `summarize` uses FAST + `RollingSummarizer`, side-effects emit `WebSearchResults` / `Artifacts` / `Media` correctly, `sessionContext` populated. |
| `BaseOpenAiSdkInferenceServiceMetadataTest` | colocated | metadata round-trip: `AgentLoopMessage(role=ASSISTANT, metadataJson="{call_id:..., raw_tool_calls:[...]}")` → correct OpenAI `ChatCompletionAssistantMessageParam` with tool calls. |
| `AnthropicInferenceServiceMetadataTest` | colocated | same for Anthropic `ToolUseBlockParam`. |
| `GoogleInferenceServiceMetadataTest` | colocated | same for Google `Part.fromFunctionCall`. |
| `LlamaInferenceServiceCompletionTest.completeSingleTurn_rebuildsSession` | colocated | AGENT-033: session rebuild on `setHistory`. |
| `ConversationManagerImplPolicyBridgeTest` | colocated | Phase 2 acceptance: streak gating + soft-failure detection parity with Rust. |
| `ArtifactToolLoopE2ETest` (Robolectric in `:feature:inference`, or `:app` androidTest) | `feature/inference/src/test/...` | AGENT-044: 4-turn `append_blocks` artifact build with forced `contextWindow=4096`; assert no `IllegalStateException`, at least one `CompactionApplied` event fires. |
| Rust `tests/kv_fixed_test.rs` extension | `third_party/agent_core/tests/kv_fixed_test.rs` | AGENT-047: add negative test that non-`kv_fixed` session does NOT emit `KvCacheInvalidated` if not already present. |
| Rust `guardrails.rs` tests | `third_party/agent_core/src/guardrails.rs` | Phase 3.4 shapes: `{"error":...}`, `{"status":"error"}`, `{"data":null}` → soft-failure; `{"results":[]}` → not. |

---

### Phase 5 — AGENT-043 legacy deletion

Run only after Phases 1–4 are merged and green. Deleting before then breaks paths mid-flight.

| Step | Files / Action |
|---|---|
| 5.1 | Delete `core/domain/.../usecase/inference/LlmToolingOrchestrator.kt`, `LlmToolingOrchestratorTest.kt`, `LlmToolingOrchestratorAutoFinalizeTest.kt`. |
| 5.2 | Delete `ContextExceededResult` (currently a data class in `LlmToolingOrchestrator.kt`; check for stray references first). |
| 5.3 | In each provider service (`BaseOpenAiSdkInferenceService`, `AnthropicInferenceServiceImpl`, `GoogleInferenceServiceImpl`, `LlamaInferenceServiceImpl`, `ApiInferenceServiceImpl`, `OpenRouterInferenceServiceImpl`, `XaiInferenceServiceImpl`): delete `executeToolingPrompt`, `onContextExceeded` lambdas, the `orchestrator` constructor parameter, and any associated helpers (`estimateContextExceeded`, etc.). |
| 5.4 | Delete `feature/inference/.../LocalToolResultPolicy.kt`'s **cloud paths only**. Keep the LiteRT-specific shim used by Phase 2 (`resultJsonLooksSoftFailure` adapter, `buildContextWindowExceededToolError`). Tests: trim `LocalToolResultPolicyTest.kt` to LiteRT-only cases. |
| 5.5 | Delete `core/domain/.../util/ChatHistoryCompressor.kt` + tests. The slimmed `ChatHistoryRehydrator` no longer references it; the cloud path doesn't either after this phase. |
| 5.6 | Keep `RollingSummarizer` — it's now alive as the summary-tier executor (Phase 3.3). Add a comment at the top of the file: `// Called from RustLoopInferenceService.summarize via the AGENT-028 summary tier.` |
| 5.7 | Run `rg LlmToolingOrchestrator` — must return zero production references (test references in deleted files don't count). |
| 5.8 | Run `rg ContextExceededResult` — must return zero production references. |
| 5.9 | Update Hilt modules (`InferenceFactoryImpl` constructor, `AppBindingModule`, `DataModule`) to drop the orchestrator binding/parameter. |
| 5.10 | Run `./gradlew test ktlintCheck detekt` — all green. |

**Tickets:** AGENT-043 acceptance "zero production refs to `LlmToolingOrchestrator`", "no dead `onContextExceeded` lambdas remain", "ktlint+detekt clean", "`ToolEnvelopeParser.kt` retained".
**Verify:** the greps above + green CI.

---

### Phase 6 — Hygiene cleanup

Bundle alongside Phase 5 or as one consolidated MR.

| Step | File / Action |
|---|---|
| 6.1 | Delete dead helper duplicates: `AnthropicRequestMapper.kt` lines 88-110 (`toAnthropicJsonValue`); `GoogleRequestMapper.kt` lines 123-166 (`toGoogleSchema`). |
| 6.2 | `RustAgentEngineAdapter.kt` KDoc — remove the stale `runBlocking` reference (the impl uses `withContext(Dispatchers.IO)`). |
| 6.3 | Add ABI note to `core/agent/README.md`: `Currently builds for arm64-v8a only; 32-bit ARM and x86_64 emulators are not supported.` |
| 6.4 | Verify `ktlintCheck` exclusion for generated `agent_core.kt`. If missing, mirror the pattern used for `docgen_core.kt`. |
| 6.5 | Add `@Singleton` to `AndroidLoggingAdapter` class for symmetry with the `@Binds @Singleton` (cosmetic). |

---

### Phase 7 — Plan / ADR sync

| Step | File |
|---|---|
| 7.1 | `plans/FEAT-agent-core-rust/ADR.md` — add a `Status: Amended <date>` block: feature flag (AGENT-030/036) cut, `RollingSummarizer + FAST` confirmed as summary-tier executor, `LiteRtAgentPolicyBridge` relocated behind `AgentPolicyPort` (AGENT-026 compliance). LiteRT-loop decision unchanged. |
| 7.2 | `plans/FEAT-agent-core-rust/README.md` — tick ticket statuses in the index; mark AGENT-030/036/045 as deferred-by-decision; mark AGENT-035/040 as plan-deferred. |
| 7.3 | `plans/FEAT-agent-core-rust/benchmarks.md` — add a one-liner that benchmarks are deferred (no users yet, AGENT-045 not gating). |
| 7.4 | Verify `third_party/agent_core/examples/mock_desktop_loop.rs` exists and `cargo run --example mock_desktop_loop` passes (AGENT-041). Verify `third_party/agent_core/README.md` has a "Desktop integration" section and a "Versioning" section (AGENT-041, AGENT-042). |

---

## Suggested merge train

| MR | Phase(s) | Approx. files | Risk |
|---|---|---|---|
| MR1: reproducibility + Phase 1 critical fixes | 0 + 1 | submodule pointer, ~6 Kotlin files, ~3 tests | low |
| MR2: Phase 2 LiteRT bridge completion + AGENT-026 cleanup | 2 | `ConversationManagerImpl.kt`, `LocalToolResultPolicy.kt`, new `AgentPolicyPort.kt`, bridge relocation | low (bounded scope) |
| MR3: Phase 3 important fixes | 3 | `RustLoopInferenceService` + `AgentLoopPort` + Mappers + CI + test refactor | medium |
| MR4: Phase 4 test gaps | 4 | many test files + minor production seams for testability | low |
| MR5: Phase 5 legacy deletion | 5 | many deletions, Hilt graph trim | low (mechanical, post-tested) |
| MR6: Phase 6 + 7 hygiene + docs | 6 + 7 | dead code, KDoc, plan/ADR | trivial |

MR1+MR3+MR5+MR6 can collapse into fewer MRs once confidence is high. MR2 should stay isolated for revert-ability.

---

## Acceptance: "ready for merge"

The branch is ready when all of the following hold:

- [ ] `./gradlew :core:domain:assemble :core:agent:assembleDebug :feature:inference:assemble test ktlintCheck detekt` is green from a clean clone.
- [ ] `cd third_party/agent_core && cargo test` is green.
- [ ] `./scripts/check-tool-catalog-sync.sh` exits 0.
- [ ] `python3 scripts/check-catalog-schema-parity.py` exits 0.
- [ ] `rg LlmToolingOrchestrator core/ feature/ app/` returns zero production references.
- [ ] `rg ContextExceededResult core/ feature/ app/` returns zero production references.
- [ ] `:feature:inference` does not import from `:core:agent` (production code).
- [ ] `RustAgentEngineAdapter` 2-tool-turn flow + terminal-event-drain test green.
- [ ] `RustLoopInferenceServiceTest` covers parallelism, exception handling, summarize + FAST routing, side-effect emission, sessionContext propagation.
- [ ] Per-provider metadata round-trip tests green.
- [ ] `ArtifactToolLoopE2ETest` runs and asserts `CompactionApplied` event fires under forced small context.
- [ ] LiteRT `ConversationManagerImplPolicyBridgeTest` covers streak gating + JSON-shape soft-failure parity.
- [ ] Rust `kv_fixed_test.rs` covers both positive and negative `KvCacheInvalidated` cases.
- [ ] ADR amended and plan README ticket index updated.

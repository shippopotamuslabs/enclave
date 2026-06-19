# Research: sidecar-dedupe-model-wire

Objective codebase map for sidecar shipping deduplication and model-facing sidecar content cleanup. Facts only — no design recommendations.

---

## Research Questions

### Sidecar composition and inputs

1. Where is `buildContextSidecars` defined, what inputs does it consume, and what string does it emit today?
2. How does `cacheHints` flow from the Rust agent loop into Kotlin, and where is it rendered on the wire?
3. How are context reference lines formatted, and what role do `currentTurnIndex`, `fresh`/`stale`, and `@turn:N` play?
4. What determines when `buildContextSidecars` returns `null` versus a non-empty string?
5. What compaction or normalization runs on ledger/index JSON before sidecar assembly?

### Provider wire assembly and freeze lifecycle

6. Which inference services receive `contextSidecars` from `RustLoopInferenceService.completeModelTurn`, and at what boundary?
7. When and where is `endTurnFreezeSidecar` / `freezeContextSidecarIfPresent` called relative to wire assembly and the provider API call?
8. How does `frozenSidecarsForWireReplay()` participate in OpenAI-compatible wire building versus Anthropic/Google?
9. Is there any existing deduplication of identical sidecar strings across consecutive `completeSingleTurn` hops?
10. How are pre-retrieval sidecars (`PreRetrievalContextSidecar`, `volatileTurnContext`) handled separately from context-management sidecars?

### Append-only session state

11. What state does `ProviderAppendOnlyContext` hold for frozen sidecars, and what clears that state (`invalidate`, `clearLogState`, compaction boundaries)?
12. How does `markTurnBoundaryForReanchor` interact with frozen sidecars and digest-based log clearing?
13. How does `resetAppendOnlyForNewPrompt` differ across OpenAI/xAI/OpenRouter versus Anthropic/Google?

### Volatile sidecar detection and placement

14. Which helpers classify system messages as volatile sidecars (versus stable session system rows)?
15. Where is the live sidecar inserted in the OpenAI-compatible messages array, and how are prior frozen snapshots interleaved?

### Tests and validation

16. Which existing unit tests assert sidecar text format, freeze accumulation, placement, or prompt-cache stability?
17. What Gradle command runs the relevant `:feature:inference` unit tests?

### Reference TTL and tool retrieval

18. Where is context-reference TTL enforced today — wire labels only, tool execution, Rust `read_context_reference`, or elsewhere?

---

## Findings

### 1. `buildContextSidecars` location, inputs, and output shape

- **Definition:** `RustLoopInferenceService.buildContextSidecars` is a `private suspend` function at `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceService.kt:923-975`.
- **Call site:** Invoked from `AgentLoopHostCallbacks.completeModelTurn` and passed as `contextSidecars` to `inner.completeSingleTurn` at `RustLoopInferenceService.kt:204-215`.
- **Inputs:** `contextReferences`, `stateLedgerJson`, `toolContextIndexJson`, `fileContextIndexJson`, `cacheHints`, `currentTurnIndex` (from `sessionTurnIndex.get()` at line 194).
- **Emitted structure (in order):**
  1. Header line `Context management sidecars:` (`RustLoopInferenceService.kt:959`)
  2. `CONTEXT_REFERENCE_POLICY_PREAMBLE` constant (`RustLoopInferenceService.kt:88-90`, `960`)
  3. Optional network status when offline or back-online after prior offline (`RustLoopInferenceService.kt:937-963`)
  4. Optional `References:` block with per-reference lines from `formatContextReferenceLine` (`964-968`)
  5. Optional `State ledger JSON: …`, `Tool context index JSON: …`, `File context index JSON: …` (`970-972`)
  6. Optional `Cache hints: …` when `cacheHints` is non-empty (`973`)
- **Only production caller:** Grep shows `buildContextSidecars` is defined and called only in `RustLoopInferenceService.kt` (tests duplicate helper logic in `XaiPromptCacheLiveContextTest.kt:493-501`).

### 2. `cacheHints` flow and wire rendering

- **Host callback contract:** `AgentLoopHostCallbacks.completeModelTurn` accepts `cacheHints: List<String> = emptyList()` (`core/domain/.../AgentLoopModels.kt:250`).
- **Rust → Kotlin:** `RustLoopInferenceService` receives `cacheHints` on the `completeModelTurn` callback (`RustLoopInferenceService.kt:188`) and passes it into `buildContextSidecars` (`213`).
- **Wire rendering:** When non-empty, appended as `Cache hints: ${cacheHints.joinToString()}` inside the sidecar string (`RustLoopInferenceService.kt:973`).
- **FFI/domain:** `cacheHints` exists on Rust FFI `ModelRequest` bindings (`core/agent/.../agent_core.kt:2993`) and flows through `AgentEngineActionDriver` / `RustAgentEngineAdapter` (grep hits in `core/agent`).
- **No separate Kotlin telemetry path for cacheHints in sidecar builder** beyond inclusion in the built string.

### 3. Context reference line format and turn metadata

- **Formatter:** `formatContextReferenceLine` at `RustLoopInferenceService.kt:1007-1026`.
- **Current line shape:**
  ```
  - [${kind}|turn:${referenceTurn ?: "unknown"}|${freshness}] ${id} (${byteLen}b): ${title}
    preview: "…"
    ${retrievalHint}
  ```
- **`referenceTurn`:** Parsed from title via `parseTurnFromTitle` using regex on `AgentLoopWireKeys.CONTEXT_REFERENCE_TURN_SUFFIX` (`@turn:`) at `RustLoopInferenceService.kt:96-97`, `1028-1033`, `core/domain/.../AgentLoopWireKeys.kt:11`.
- **`freshness`:** `isStaleContextReference` returns true only for `TOOL_OUTPUT` when age is in `[CONTEXT_REFERENCE_TTL_TURNS - 1, CONTEXT_REFERENCE_TTL_TURNS)` with `CONTEXT_REFERENCE_TTL_TURNS = 2` (`RustLoopInferenceService.kt:86-87`, `1035-1045`). Otherwise label is `fresh`.
- **`currentTurnIndex` source:** `AtomicInteger sessionTurnIndex` initialized to 0 (`RustLoopInferenceService.kt:172`); updated on `AgentLoopEvent.TurnStarted` via `sessionTurnIndex.set(event.index)` (`317-318`). Read at model-turn time before increment (`194`).

### 4. Null vs non-null `buildContextSidecars` result

- JSON blobs are compacted first (`931-935`).
- `isAllEmpty = contextReferences.isEmpty() && jsonSidecars.isEmpty() && cacheHints.isEmpty()` (`953`).
- Returns `null` when `isAllEmpty && networkStatus.isBlank()` (`954-956`).
- **Inference:** Preamble alone does not force non-null; header `Context management sidecars:` is only appended when the function returns a string (i.e., not when fully empty per above). Static preamble without other content still yields non-null today if network status is non-blank or any of references/JSON/cacheHints present.

### 5. JSON compaction before sidecar assembly

- `ContextSidecarCompaction` (`feature/inference/.../appendonly/ContextSidecarCompaction.kt`) compacts:
  - `compactStateLedgerJson` — `JsonObject.toString()` (`24`, `63-67`)
  - `compactToolContextIndexJson` — trims `advertised_tools` to names, omits long `full_schema_ref` (`26-40`, `44-61`)
  - `compactFileContextIndexJson` — same as ledger (`42`)
- Applied in `buildContextSidecars` before emptiness check (`931-935`).

### 6. Services receiving `contextSidecars`

- **Producer:** `RustLoopInferenceService` builds sidecars and calls `RustCompatibleInferenceService.completeSingleTurn` (`204-215`).
- **Interface:** `RustCompatibleInferenceService.completeSingleTurn(..., contextSidecars: String?, ...)` (`feature/inference/.../RustCompatibleInferenceService.kt:32+`).
- **Remote BYOK consumers with append-only context:**
  - `BaseOpenAiSdkInferenceService` (OpenAI, xAI, OpenRouter via inheritance) — `BaseOpenAiSdkInferenceService.kt:332-342`
  - `AnthropicInferenceServiceImpl` — `AnthropicInferenceServiceImpl.kt:143-146`
  - `GoogleInferenceServiceImpl` — `GoogleInferenceServiceImpl.kt:105-108`
- **Local exception:** `LlamaInferenceServiceImpl` injects sidecars into last user message content (`LlamaInferenceServiceImpl.kt:309`, `658-666`); no `ProviderAppendOnlyContext` freeze path in grep results.
- **OpenRouter:** `OpenRouterInferenceServiceImpl` extends `BaseOpenAiSdkInferenceService` (`OpenRouterInferenceServiceImpl.kt:15-25`).

### 7. Freeze timing relative to wire assembly

**OpenAI/xAI/OpenRouter (`BaseOpenAiSdkInferenceService.completeSingleTurn`):**

1. `syncMessages` on mapped session body (`392`)
2. Optional pre-retrieval freeze before wire build if not already frozen (`433-441`)
3. Wire messages built with `currentSidecarText = contextSidecars` (`449-461`)
4. **After wire build, before API stream:** if `contextSidecars` non-blank, `appendOnly.endTurnFreezeSidecar(contextSidecars, tailInsertIndex)` (`478-482`)
5. Request built and streamed (`487-559`)

**Anthropic:**

- Sidecars prepended to latest user message content during message loop (`238-245`)
- `freezeContextSidecarIfPresent` called **after** streaming completes, before return (`472-476`, `543-553`)

**Google:**

- Sidecars prepended via `latestUserPrepends` on latest mapped content (`149`, `161`)
- `endTurnFreezeSidecar` after `generateContentStream` returns (`306-310`)

**No dedupe gate exists today** — every non-blank `contextSidecars` triggers freeze on every hop (OpenAI: `478-482`; Anthropic: `548-552`; Google: `306-310`).

### 8. Frozen replay: OpenAI-compatible vs Anthropic/Google

**OpenAI-compatible replay:**

- `appendOnly.frozenSidecarsForWireReplay()` passed to `OpenAiCompatibleVolatilePlacement.buildMappedBodyWithSidecars` (`BaseOpenAiSdkInferenceService.kt:453-460`)
- Interleaves frozen snapshots at `FrozenSidecarSnapshot.mappedInsertIndex` (`OpenAiCompatibleVolatilePlacement.kt:24-65`)
- `FrozenSidecarSnapshot` is `data class FrozenSidecarSnapshot(val text: String, val mappedInsertIndex: Int)` (`FrozenSidecarSnapshot.kt:6-9`)

**Anthropic/Google:**

- Grep shows **no** use of `frozenSidecarsForWireReplay` or `sidecarTextsBeforeSessionIndex` in `AnthropicInferenceServiceImpl.kt` or `GoogleInferenceServiceImpl.kt`.
- Wire carries only the **live** `contextSidecars` prepended to the latest user/generate content for that hop.
- `endTurnFreezeSidecar` still appends to `frozenSidecarSnapshots` for session bookkeeping (`ProviderAppendOnlyContext.kt:103-114`) but those snapshots are **not** replayed on Anthropic/Google wires in current code.

**`appendVolatileSidecar`:** Defined on `ProviderAppendOnlyContext` (`84-101`) but **only referenced in tests** (`ProviderAppendOnlyContextTest.kt`); production freeze path uses `endTurnFreezeSidecar` only.

### 9. Existing sidecar deduplication

- **Verified:** No `lastShippedSidecarText`, `resolveContextSidecarsForWire`, or equivalent string-equality gate exists in the repo (grep across `feature/inference`).
- **Observed behavior:** Tool-loop test `AOC_22_tool_loop_does_not_increment_append_only_clear_count` expects **3** frozen snapshots after 3 `completeSingleTurn` calls with different ledger JSON (`AppendOnlyOpenAiSidecarPlacementTest.kt:797-841`) — one freeze per hop when sidecar text differs.

### 10. Pre-retrieval sidecars (separate from context-management sidecars)

- **Source:** `options.volatileTurnContext` formatted via `PreRetrievalContextSidecar.format` (`BaseOpenAiSdkInferenceService.kt:428-432`; marker `Pre-retrieval context:` at `PreRetrievalContextSidecar.kt:8-20`).
- **OpenAI/xAI/OpenRouter:** Delivered as system sidecar at `preRetrievalInsertIndex`; one-shot freeze via `hasFrozenPreRetrievalSidecar()` — frozen on first presence, live injection suppressed afterward (`433-448`, `ProviderAppendOnlyContext.kt:78`).
- **Rust loop:** For OpenAI-family providers, `deliverPreRetrievalAsOpenAiSidecar()` returns true and `buildUserContent` passes `volatileTurnContext = null` into user content (`RustLoopInferenceService.kt:983-997`, `977-988`) — pre-retrieval goes through provider sidecar path, not user prepend.
- **Anthropic/Google:** `VolatileTurnContextWireSupport.combinedLatestUserPrepends` merges `volatileTurnContext` + `contextSidecars` on latest user (`VolatileTurnContextWireSupport.kt:8-15`; Anthropic usage `AnthropicInferenceServiceImpl.kt:240-244`). Google passes `volatileTurnContext = null` in mapper call at `161` (only `contextSidecars` as prepend).

### 11. `ProviderAppendOnlyContext` state and clearing

**Held state** (`ProviderAppendOnlyContext.kt`):

- `stablePrefix`, append-only `log`, `frozenSidecarSnapshots`, digest cursor (`lastSyncCount`, `syncedDigest`), `pendingTurnBoundaryReanchor`, `clearCount`, `lastClearReason`
- **No** `lastShippedSidecarText` field today

**`endTurnFreezeSidecar`:** Appends `FrozenSidecarSnapshot` to `frozenSidecarSnapshots` (`103-114`); does not dedupe.

**`invalidate()`:** Clears `stablePrefix`, calls `clearLogState(reason = "session_invalidate")`, clears `frozenSidecarSnapshots` (`118-122`).

**`clearLogState`:** Clears log, **`frozenSidecarSnapshots.clear()`**, resets digest cursor (`138-151`). Invoked for: `digest_mismatch`, `compaction_shorter_array`, `rust_compaction_boundary`, `session_invalidate`.

**`clearOnRustCompactionBoundary()`:** Delegates to `clearLogState(reason = "rust_compaction_boundary")` (`80-82`).

### 12. `markTurnBoundaryForReanchor`

- Sets `pendingTurnBoundaryReanchor = true` (`51-53`)
- Next `syncMessages` skips digest-mismatch clear once (`55-62`, `124-136`)
- **Does not** clear frozen sidecars by itself — tests confirm frozen snapshots survive cross-turn reanchor when only prefix grows (`ProviderAppendOnlyContextTest.kt:125-146`)
- Compaction/shorter-array still clears frozen sidecars even after reanchor (`167-189`)

**OpenAI/xAI/OpenRouter `resetAppendOnlyForNewPrompt`:** Calls `markTurnBoundaryForReanchor` on chat-keyed context only (`BaseOpenAiSdkInferenceService.kt:302-305`) — preserves frozen sidecars for same `chatId` across user prompts (`XaiPromptCacheToolAndSessionStabilityTest.kt:101-156`).

### 13. `resetAppendOnlyForNewPrompt` provider differences

| Provider family | Implementation | Effect on frozen sidecars |
|-----------------|----------------|---------------------------|
| OpenAI/xAI/OpenRouter | `markTurnBoundaryForReanchor()` on keyed context (`BaseOpenAiSdkInferenceService.kt:302-305`) | Preserved |
| Anthropic | `invalidate()` + `appendOnlyContext = null` (`AnthropicInferenceServiceImpl.kt:132-135`) | Cleared |
| Google | `invalidate()` + `appendOnlyContext = null` (`GoogleInferenceServiceImpl.kt:93-96`) | Cleared |

Dispatched from `RustLoopInferenceService.sendPrompt` via `inner.resetAppendOnlyForNewPrompt(options.chatId)` (`RustLoopInferenceService.kt:165`) and `AppendOnlyCompactionInvalidation.kt:20-26`.

### 14. Volatile sidecar detection helpers

**`BaseOpenAiSdkInferenceService`** (`960-983`):

- `ChatCompletionMessageParam.isVolatileOrEngineSystemMessage()` and `AgentLoopMessage.isVolatileOrEngineSystemMessage()` return true when content starts with:
  - `PreRetrievalContextSidecar` marker
  - `State ledger JSON:`
  - `Context management sidecars:`
  - `Tool context index JSON:`
  - `File context index JSON:`
  - `Cache hints:`
  - `EngineInternalMessageMapper.isEngineInternalWireText`
  - `[Conversation summary]`

**Test duplicate:** `XaiPromptCacheLiveContextTest.isVolatileSidecar()` uses the same prefix set (`535-544`).

### 15. OpenAI-compatible placement

- **Tail index:** `resolveTailMappedInsertIndex` picks last non-engine-internal session index, maps to OpenAI body index, applies `OpenAiCompatibleVolatilePlacement.tailMappedInsertIndex` (`BaseOpenAiSdkInferenceService.kt:814-828`, `OpenAiCompatibleVolatilePlacement.kt:15-22`).
- **Wire assembly:** `buildMappedBodyWithSidecars` interleaves frozen snapshots by index, inserts live `currentSidecarText` before tail anchor (`OpenAiCompatibleVolatilePlacement.kt:24-65`).
- **Cap-at-2 / front-prepend:** Not present in current `ProviderAppendOnlyContext` or placement code (grep for `MAX_FROZEN`, `priorFrozenSidecars`, `takeLast` in `:feature:inference` sidecar paths returned no matches). Specs document prior regression; codebase uses position-aware `FrozenSidecarSnapshot.mappedInsertIndex`.

### 16. Existing tests

| File | What it covers |
|------|----------------|
| `ProviderAppendOnlyContextTest.kt` | Stable prefix, digest clear, frozen accumulation with indices, reanchor, compaction clear (`AOC-01`, `AOC-04`, `AOC-05`, `AOC-13`, `AOC-17`, frozen/reanchor cases) |
| `AppendOnlyOpenAiSidecarPlacementTest.kt` | Tail placement, tool-loop wire order, 3-hop freeze count (`AOC-22` expects 3 frozen at `841`) |
| `RustLoopInferenceServiceTest.kt` | Sidecar forwarding: header, preamble, `\|fresh\|`, `\|stale\|` (`666-752`); offline network status (`1678+`) |
| `PromptCachingTest.kt` | `PC-01`–`PC-08` placement for OpenAI/Anthropic/Google with synthetic sidecar strings |
| `XaiPromptCacheToolAndSessionStabilityTest.kt` | Cross-prompt frozen replay, chat isolation, rehydrate (`PC_XAI_06`, `06b`, `09`) |
| `XaiPromptCacheLiveContextTest.kt` | Live multi-hop xAI wire helpers; duplicates sidecar format with header (`493-544`) |
| `ContextSidecarCompactionTest.kt` | JSON compaction (separate from wire dedupe) |
| `PreRetrievalContextSidecarTest.kt` | Pre-retrieval marker formatting |

**JUnit 5 + `runTest`:** All listed tests use `org.junit.jupiter` and coroutine `runTest` per `UNIT_TESTING.md` and `CODE_STYLE_RULES.md` §Testing.

### 17. Test run command

- Module unit tests: `./gradlew :feature:inference:testDebugUnitTest` (`AGENTS.md` §4, `UNIT_TESTING.md` §5)
- Narrow filter example: `./gradlew :feature:inference:testDebugUnitTest --tests "*ProviderAppendOnlyContextTest*"` (`UNIT_TESTING.md` §5)
- Inference compile: `./gradlew :feature:inference:assembleDebug` (`INFERENCE_RULES.md` §8)

### 18. Context-reference TTL enforcement

- **Wire labeling only (Kotlin):** `isStaleContextReference` adds `\|stale\|` to reference lines for `TOOL_OUTPUT` in a narrow age window (`RustLoopInferenceService.kt:1035-1045`). References remain listed in sidecar text.
- **Tool execution:** `handleReadContextReference` delegates to `agentLoopPort.readContextReference(referenceId)` (`856-878`); returns contract JSON on `ContextReferenceNotFoundException` — **no Kotlin TTL check** before Rust call.
- **Rust/agent_core:** Comment at `RustLoopInferenceService.kt:86` states `CONTEXT_REFERENCE_TTL_TURNS = 2` matches `DEFAULT_TOOL_OUTPUT_TTL_TURNS` in agent_core; **not verified in-repo** — grep under `third_party/agent_core` for these symbols returned no matches in this workspace snapshot.
- **Documentation:** `docs/provider-wire-context-spec.html` references Kotlin fresh/stale prose and TTL constant (grep hit at line 1403).

---

## Cross-cutting facts (contracts / specs)

- **Prompt caching spec** (`plans/prompt-caching/spec.md`): Volatile sidecars must be separated from stable system prompt; OpenAI/xAI tail placement authority superseded by append-only spec for message indices.
- **Append-only spec** (`plans/FEAT-append-only-provider-context/spec.md`): Session-scoped `ProviderAppendOnlyContext`; freeze-on-ship per `completeModelTurn`; position-aware frozen replay for OpenAI family (`AOC-18`–`AOC-20` tasks documented in spec regression section).
- **Inference contract** (`INFERENCE_RULES.md`): BYOK cloud providers isolated in `:feature:inference`; no Room/schema changes for this concern.
- **`lastShippedSidecarText`:** **Absent** from codebase — ticket proposes adding it.

---

## Open unknowns (not resolved from repo read)

1. **Rust `read_context_reference` TTL:** Whether agent_core enforces `DEFAULT_TOOL_OUTPUT_TTL_TURNS` at retrieval time could not be verified — symbol not found under `third_party/agent_core` in this checkout (may be generated/UniFFI-only or path drift).
2. **Anthropic/Google dedupe impact:** Frozen snapshots are not replayed on Anthropic/Google wires today; effect of skipping live `contextSidecars` on unchanged hops for those providers depends on whether prior user messages already committed sidecar text (not traced in this research pass beyond freeze bookkeeping).

---

## Inference (explicit)

- **Inference:** Stale/fresh labels change on hops where `currentTurnIndex` advances even if reference semantic content is unchanged — contributes to sidecar string churn independent of ledger JSON changes (`formatContextReferenceLine` + `isStaleContextReference`).
- **Inference:** Removing `cacheHints` from `isAllEmpty` (per ticket) would not change null-return behavior today unless `cacheHints` is the sole non-network contributor — currently empty references + empty JSON + blank network + non-empty `cacheHints` yields non-null sidecar.

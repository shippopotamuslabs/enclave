# pc-cache-stability — Implementation Spec

## Feature Summary

Fix two confirmed cross-turn prompt-cache-breaking mutations that cause provider HTTP bodies
to differ at identical byte offsets between turn N and turn N+1:

**BUG 1 — `volatileTurnContext` never committed to Rust session history.**
The per-turn RAG prefix (kNN memories + web/X pre-retrieval results) is spliced onto the live
user message wire-side only. Rust stores the bare prompt. On turn N+1 the session history
replays the bare prompt while the wire body shows it with the RAG prefix → cache prefix
diverges at that slot.

**BUG 2 — intent-activated tool catalog mutates per turn.**
`ChatInferenceRequestPreparer` re-runs the intent classifier every turn. When the classifier
flips (e.g. turn 1 activates `search_x_recent`, turn 2 does not), the `# TOOL CATALOG:`
block and `# ACTIVE TOOL EXAMPLES:` section in the system prompt change between turns →
the system-prompt prefix at position 0 of the wire diverges.

---

## Correct Behaviors to Preserve (Non-Regression Targets)

- `contextSidecars` from Rust are frozen via `endTurnFreezeSidecar` inside
  `BaseOpenAiSdkInferenceService` / `AnthropicInferenceServiceImpl`. Do not alter
  the sidecar freeze path.
- `[Metadata: Current Time: ...]` is baked into the user prompt **before** Rust history
  storage — this must remain intact.
- `wireToolsForRequest` returns the full eligible tool pool on every turn
  (test `PC_XAI_05` must continue to pass).
- Compaction clears the append-only log and frozen sidecars deliberately
  (`clearAppendOnlyOnRustCompactionBoundary`). Do not regress.

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/port/inference/MonotonicToolActivationPort.kt` | **Create** | Domain port: session-scoped set of tools that have been intent-activated. Once a tool name is added it is never removed for the lifetime of the session. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/InMemoryMonotonicToolActivationStore.kt` | **Create** | In-memory implementation of `MonotonicToolActivationPort`. Keyed by `ChatId`. No persistence; cleared when the session ends or a new chat starts. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/ChatInferenceRequestPreparer.kt` | **Modify** | (1) Accept `MonotonicToolActivationPort` constructor parameter; (2) call `port.union(chatId, intentActivatedNames)` before building the catalog; (3) use the returned stable set in place of the raw `intentActivatedNames`. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceService.kt` | **Modify** | In `sendPrompt`, concatenate `options.volatileTurnContext` into the `ChatMessage(Role.USER, ...)` content before pushing to `initialMessages`. Remove the volatile context from the wire path and keep it only in the Rust-stored message. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/BaseOpenAiSdkInferenceService.kt` | **Modify** | Remove the `VolatileTurnContextWireSupport.prependToUserContent(...)` call inside `toMappedSessionBody`. The wire body now replays what Rust stored, which already contains the RAG prefix. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/AnthropicInferenceServiceImpl.kt` | **Modify** | Remove the `VolatileTurnContextWireSupport.combinedLatestUserPrepends(...)` call on `messageContent` for `index == lastUserIndex`. The Rust-stored message already contains the RAG prefix. Sidecar prepend (contextSidecars) is handled separately and must be preserved. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/GoogleInferenceServiceImpl.kt` | **Modify** | Remove the `VolatileTurnContextWireSupport.combinedLatestUserPrepends(...)` call in `completeSingleTurn`. The Rust-stored content already has the RAG prefix. Sidecar mapping via `prependVolatile = !latestUserPrepends.isNullOrBlank()` must be retained for context sidecars. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/LlamaInferenceServiceImpl.kt` | **Modify** | Remove the `VolatileTurnContextWireSupport.prependToUserContent(...)` call in `completeSingleTurn`. Llama stores domainHistory which now receives the already-merged content from Rust's `initialMessages`. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/VolatileTurnContextWireSupport.kt` | **Modify** | Mark `prependToUserContent` as `@Deprecated("Use RustLoopInferenceService.buildInitialMessages instead")`. Retain `combinedLatestUserPrepends` — it is still used by Anthropic and Google for the sidecar + volatile merge on the live tail. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/chat/ChatInferenceRequestPreparerCacheStabilityTest.kt` | **Create** | JUnit 5 tests covering scenarios CSPC-01, CSPC-04a, CSPC-04b, CSPC-04c, CSPC-MUTDEF-01. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/chat/InMemoryMonotonicToolActivationStoreTest.kt` | **Create** | Unit tests for `InMemoryMonotonicToolActivationStore` covering union monotonicity and isolation across ChatIds. |
| `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/RustLoopVolatileTurnContextCommitTest.kt` | **Create** | JUnit 5 tests covering scenarios CSPC-02, CSPC-03a, CSPC-03b, CSPC-03c, CSPC-05, CSPC-06. |
| `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/AnthropicVolatileTurnContextCommitTest.kt` | **Create** | Anthropic-specific provider coverage for CSPC-03a and CSPC-05 byte-identity assertions (via `AnthropicInferenceServiceImpl.completeSingleTurn`). |
| `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/GoogleVolatileTurnContextCommitTest.kt` | **Create** | Google-specific provider coverage for CSPC-03a and CSPC-05 (via `GoogleInferenceServiceImpl.completeSingleTurn`). |

---

## API Contracts and Interfaces

### `MonotonicToolActivationPort`

```
interface MonotonicToolActivationPort {
    /**
     * Merges [newNames] into the persisted activated set for [chatId].
     * Returns the full union (previously activated ∪ newNames).
     * This set only grows; names are never removed within a session.
     */
    fun union(chatId: ChatId, newNames: Set<String>): Set<String>

    /**
     * Clears the record for [chatId].
     * Called when the chat session ends or a new conversation starts.
     */
    fun clearSession(chatId: ChatId)
}
```

**Scope:** `:core:domain` — no Android or framework imports.
**Ownership:** Defined in `domain/port/inference/`.
**Implementation:** `InMemoryMonotonicToolActivationStore` lives in
`domain/usecase/chat/` and is backed by a `ConcurrentHashMap<ChatId, MutableSet<String>>`.

### `ChatInferenceRequestPreparer` constructor change

New optional parameter added at the end of the constructor:

```
class ChatInferenceRequestPreparer(
    ...existing parameters...,
    private val monotonicToolActivation: MonotonicToolActivationPort = NoopMonotonicToolActivationPort,
)
```

`NoopMonotonicToolActivationPort` is a private default companion object that always returns
the input set unchanged, preserving backward-compatibility for call-sites that do not supply the
parameter (e.g. existing tests that construct `ChatInferenceRequestPreparer` directly).

Usage inside `invoke`:

```
val stableIntentActivatedNames = monotonicToolActivation.union(chatId, intentActivatedNames)
// Replace every downstream reference to intentActivatedNames with stableIntentActivatedNames.
```

### `RustLoopInferenceService.sendPrompt` — initial messages change

Current (broken):
```
val initialMessages = history + listOf(ChatMessage(Role.USER, prompt))
```

Fixed:
```
val userContent = buildString {
    val vtc = options.volatileTurnContext?.trim()?.takeIf { it.isNotEmpty() }
    if (vtc != null) {
        append(vtc)
        append("\n\n")
    }
    append(prompt)
}
val initialMessages = history + listOf(ChatMessage(Role.USER, userContent))
```

The `prompt` passed here is already the fully-resolved value from `ChatInferenceRequestPreparer`
(metadata footer baked in). `volatileTurnContext` is prepended before it. Rust then stores
`userContent` in session history, so all subsequent replays carry the correct bytes.

### Wire-path cleanup in `BaseOpenAiSdkInferenceService.toMappedSessionBody`

Remove the `VolatileTurnContextWireSupport.prependToUserContent` call at lines 427–437.
The `wireContent` branch for `index == lastUserSessionIndex` collapses to `message.content`
(no conditional rewrite). The `volatileTurnContext` parameter is removed from the method
signature and from the call-site inside `completeSingleTurn`.

### Wire-path cleanup in `AnthropicInferenceServiceImpl.completeSingleTurn`

The block at `index == lastUserIndex` that calls
`VolatileTurnContextWireSupport.combinedLatestUserPrepends(volatileTurnContext, contextSidecars)`
is changed to call only:
```
VolatileTurnContextWireSupport.combinedLatestUserPrepends(
    volatileTurnContext = null,   // already in msg.content via Rust history
    contextSidecars = contextSidecars,
)?.let { prepended -> "$prepended\n\n${msg.content}" } ?: msg.content
```
This preserves the sidecar prepend (still needed) while dropping the volatile RAG duplicate.

### Wire-path cleanup in `GoogleInferenceServiceImpl.completeSingleTurn`

The `latestUserPrepends` variable currently combines volatile + sidecars:
```
val latestUserPrepends = VolatileTurnContextWireSupport.combinedLatestUserPrepends(
    volatileTurnContext = options.volatileTurnContext,
    contextSidecars = contextSidecars,
)
```
Change to sidecar-only:
```
val latestUserPrepends = contextSidecars?.trim()?.takeIf { it.isNotEmpty() }
```
The `prependVolatile = !latestUserPrepends.isNullOrBlank()` flag already correctly
conditions on whether there is a sidecar to prepend; the variable rename is purely
semantic. Volatile content is already in `latestMessage.content` via Rust history.

### Wire-path cleanup in `LlamaInferenceServiceImpl.completeSingleTurn`

Remove the `VolatileTurnContextWireSupport.prependToUserContent(...)` call in the
`domainHistory` mapping block. Llama builds `domainHistory` directly from
`sessionMessages`; since Rust already stored `userContent` (with RAG prefix), the
`sessionMessages` entries passed in will carry the correct bytes. No rewrite is needed.

---

## Data Model / Schema Changes

None. The monotonic activation store is in-memory only, keyed by `ChatId`. No new database
tables, columns, or entity types. The store is provisioned through Hilt as a singleton
scoped to the application component via `@Singleton`.

---

## Ordered Implementation Tasks

### Task 1 — Create `MonotonicToolActivationPort` (satisfies CSPC-04b, CSPC-04c, CSPC-MUTDEF-01)

1. Create `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/port/inference/MonotonicToolActivationPort.kt`
   with the interface definition shown above.
2. Create `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/InMemoryMonotonicToolActivationStore.kt`
   with a `ConcurrentHashMap<ChatId, MutableSet<String>>` backing store.
3. Add `NoopMonotonicToolActivationPort` as a private `object` inside
   `ChatInferenceRequestPreparer.Companion` (or a file-private object in the same file).

### Task 2 — Wire `ChatInferenceRequestPreparer` with `MonotonicToolActivationPort` (satisfies CSPC-04a, CSPC-04b, CSPC-04c, CSPC-MUTDEF-01)

1. Add the optional `monotonicToolActivation` constructor parameter.
2. After computing `intentActivatedNames`, call `monotonicToolActivation.union(chatId, intentActivatedNames)` and assign the result to `stableIntentActivatedNames`.
3. Replace every use of `intentActivatedNames` below that point with `stableIntentActivatedNames`.
4. Register `InMemoryMonotonicToolActivationStore` in the Hilt module (`AgentModule` or the module
   that provides `ChatInferenceRequestPreparer`) using `@Singleton`.
5. Update `AgentModule` (or equivalent) to inject the new port into `ChatInferenceRequestPreparer`.

### Task 3 — Commit `volatileTurnContext` into Rust history (satisfies CSPC-01, CSPC-02, CSPC-03a, CSPC-03b, CSPC-03c, CSPC-05)

1. In `RustLoopInferenceService.sendPrompt`, replace the bare `ChatMessage(Role.USER, prompt)` with the merged `userContent` construction described in the API Contracts section.
2. The `Finished` event handler already stores `ChatMessage(Role.USER, prompt)` in `conversationHistory` — change it to store `ChatMessage(Role.USER, userContent)` using the same merge logic (or extract a shared `buildUserContent(prompt, options)` private function).

### Task 4 — Remove wire-side volatile prepend from `BaseOpenAiSdkInferenceService` (satisfies CSPC-01, CSPC-02, CSPC-03a, CSPC-03b, CSPC-03c, CSPC-05)

1. Remove the `volatileTurnContext: String?` parameter from `toMappedSessionBody`.
2. Remove the call-site supply of `volatileTurnContext = options.volatileTurnContext` in `completeSingleTurn`.
3. Remove the conditional `VolatileTurnContextWireSupport.prependToUserContent` call in `toMappedSessionBody`.
4. `wireContent` unconditionally becomes `message.content`.

### Task 5 — Remove wire-side volatile prepend from `AnthropicInferenceServiceImpl` (satisfies CSPC-03a, CSPC-05)

1. Change the `messageContent` construction at `index == lastUserIndex` as described in the API
   Contracts section: pass `volatileTurnContext = null` to `combinedLatestUserPrepends`. Retain
   the sidecar-only prepend path so the Anthropic `cache_control` placement logic continues to work.

### Task 6 — Remove wire-side volatile prepend from `GoogleInferenceServiceImpl` (satisfies CSPC-03a, CSPC-05)

1. Change `latestUserPrepends` to be sidecar-only as described in the API Contracts section.

### Task 7 — Remove wire-side volatile prepend from `LlamaInferenceServiceImpl` (satisfies CSPC-03a, CSPC-05)

1. Remove the `VolatileTurnContextWireSupport.prependToUserContent` call in the `domainHistory`
   mapping block. `message.content` is used directly.

### Task 8 — Deprecate `VolatileTurnContextWireSupport.prependToUserContent`

1. Add `@Deprecated` annotation as described in the File Structure table.
2. After all callers are removed in Tasks 4–7, the method body can be left in place until a
   follow-up cleanup cycle. Do not delete the file or `combinedLatestUserPrepends`.

### Task 9 — Write unit tests

1. Create the four new test files listed in the File Structure table.
2. Each test file maps to the scenario IDs in `test_spec.md`.

### Task 10 — Update `AgentModule` / DI wiring

1. Provide `InMemoryMonotonicToolActivationStore` as a `@Singleton` binding for
   `MonotonicToolActivationPort` in the Hilt module that owns `ChatInferenceRequestPreparer`.

---

## Scenario ID → Task Mapping

| Scenario ID | Satisfying Tasks |
|---|---|
| CSPC-01 | Task 3, Task 4 |
| CSPC-02 | Task 3, Task 4 |
| CSPC-03a | Task 3, Task 4, Task 5, Task 6, Task 7 |
| CSPC-03b | Task 3, Task 4 |
| CSPC-03c | Task 3, Task 4 |
| CSPC-04a | Task 2 |
| CSPC-04b | Task 1, Task 2 |
| CSPC-04c | Task 2 |
| CSPC-05 | Task 3, Task 4, Task 5, Task 6 |
| CSPC-06 | (no production change required — non-regression verified by Task 9) |
| CSPC-MUTDEF-01 | Task 1, Task 2 |

---

## Provider-Specific Gotchas

### BaseOpenAiSdkInferenceService / XAI / OpenRouter

The `toMappedSessionBody` extension function is the single volatile-injection point for all
OpenAI-SDK providers. Removing it here covers `XaiInferenceServiceImpl`,
`OpenRouterInferenceServiceImpl`, and any other `BaseOpenAiSdkInferenceService` subclass.
OpenRouter explicit `cache_control` tagging (via `cacheControlIndex`) is unaffected because
it targets the penultimate user turn in the stable history, not the live user message.

### Anthropic

Anthropic uses `VolatileTurnContextWireSupport.combinedLatestUserPrepends` to merge both the
volatile turn context **and** the context sidecars into one prepend. After this fix, only the
sidecar portion should remain. The `cache_control` `ephemeralCacheControl()` breakpoint at
`stableTailIndex` (computed by `AnthropicPromptCacheSupport.stableHistoryTailIndex`) is pinned
to message indices in the session list, not to message content — it is unaffected by the content
change. The Anthropic provider does not go through `RustLoopInferenceService.sendPrompt` for the
live user message since `AnthropicInferenceServiceImpl` is a `RustCompatibleInferenceService`
called via `completeSingleTurn` — the Rust `initialMessages` path still applies because
`RustLoopInferenceService` wraps Anthropic as its `inner` and provides the merged
`userContent` to the `agentLoopPort.execute` call, which then replays it through
`completeSingleTurn` with the correct bytes in `sessionMessages`.

### Google / Gemini

`GoogleInferenceServiceImpl` also uses `VolatileTurnContextWireSupport.combinedLatestUserPrepends`
for both volatile and sidecar. After the fix, `latestUserPrepends` carries sidecar-only content.
The Google provider uses an explicit `promptCacheSession.ensureCacheForStablePrefix` that keys
on `systemPrompt` and the `stableHistory` slice (everything except the last message). The
volatile RAG content lives in the latest message and is never part of the cache key — no
additional change to `GooglePromptCacheSession` is required.

### Llama (local, LiteRT)

`LlamaInferenceServiceImpl.completeSingleTurn` constructs `domainHistory` by iterating
`sessionMessages`. Since those messages come from the Rust agent loop, the live user message
in `sessionMessages` will already carry the merged `userContent` (Rust stored it). The removal
of the `prependToUserContent` call simply stops a redundant, incorrect double-prepend. Because
Llama sets `kvFixed = true` in `AgentLoopSessionCapabilities`, there is no prompt-caching
infrastructure to protect; however, avoiding double-prepend is still necessary for correctness.

---

## Drift and Escalation Rules

1. If `VolatileTurnContextWireSupport.prependToUserContent` gains a new call-site after Tasks 4–7
   are complete, treat that as a regression requiring immediate revert.
2. If `InMemoryMonotonicToolActivationStore` is ever replaced with a persistent store, the
   `MonotonicToolActivationPort` interface is stable and requires no changes to callers.
3. If Anthropic or Google change their `completeSingleTurn` method signatures in ways that
   re-introduce volatile prepend, those changes must go through an explicit cache-stability
   review tagged with this ticket ID.
4. If a new provider is added (new `RustCompatibleInferenceService`), the implementer must check
   that it does not call `VolatileTurnContextWireSupport.prependToUserContent` and must instead
   rely on the Rust-stored `userContent`.
5. `PC_XAI_05` must remain passing after every merge that touches `RustLoopInferenceService`.

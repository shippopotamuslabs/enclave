# Tool Discovery & Prompt Cache — Research Findings

**Date:** 2026-06-17  
**Scope:** Cache-safe tool discovery without full catalog dump  
**Contracts read:** `contracts/INFERENCE_RULES.md`, `contracts/ARCHITECTURE_RULES.md`  
**Sources:** `plans/FEAT-tool-search-discovery/spec.md`, `plans/prompt-caching/spec.md`, `plans/FEAT-append-only-provider-context/spec.md`, `docs/provider-wire-context-spec.html`, `:feature:inference` implementation

---

## Executive Summary

Enclave's intended model is **lazy disclosure**: preload a small tool set, discover deferred tools via `tool_discovery_tool`, deliver full schemas **once** in the tool-result message, and grow the provider **`tools[]` wire registry append-only** (`preload ∪ ever-activated`). That design is correct for token economics and model correctness, but it **conflicts with remote prompt caching** because provider APIs place tool definitions in a **stable prefix region** (parallel `tools[]` or `CachedContent.tools`) that tokenizes **before** conversation messages. Appending a newly discovered tool invalidates the cached prefix for **all prior messages**, not just the tail.

**"Append new tool schemas to frozen tail prefix / sidecar" is not a valid pattern for any BYOK provider that uses native function calling.** Sidecars (`toolContextIndexJson`) already carry **names only**; full schemas must live in `tools[]` (OpenAI family, Anthropic) or inside `CachedContent` (Google) for structured `tool_use` / `functionCall` to work. Frozen sidecars preserve **volatile context** (ledger, indexes, references)—not callable tool definitions.

The **primary recommendation** is to **keep the session wire registry** (minimal preload + append-on-activation), **accept a one-time cache miss per newly activated tool per session**, and **defer `tools[]` growth until the earliest provider request where the model must natively call the new tool**—while ensuring schemas in the discovery tool-result remain the single semantic source until wire registration. **Do not** revert to `eligibleToolPool` full-catalog wire. Secondary mitigations: batch discovery activations, cap session activations (`MAX_ACTIVATED_TOOLS_PER_SESSION`), and provider-specific cache breakpoint tuning (Anthropic last-tool breakpoint; Google cache recreate).

---

## 1. Problem Statement

| Concern | Detail |
|---------|--------|
| **Growth trigger** | Each `tool_discovery_tool` activation calls `ToolSessionActivationRegistry.applySearchActivations`, which appends names to the session wire registry. |
| **Wire effect** | `wireToolsForRequest` maps registry names → full `ToolDefinition` schemas on every `completeModelTurn`. |
| **Cache effect** | `tools[]` / cached tools change → prefix cache miss for system + tools + entire committed history on OpenAI/xAI/OpenRouter; Anthropic ephemeral breakpoint on last tool moves; Google `CachedContent` delete + recreate. |
| **Design tension** | `plans/FEAT-tool-search-discovery/spec.md` §3.7 explicitly accepts append-only wire growth for cache tradeoff. `docs/provider-wire-context-spec.html` ranks "Tool activation growth" as **medium** open issue #4. |

---

## 2. Current Enclave Architecture

### 2.1 Tool lifecycle (API path)

```
sendPrompt
  └─ toolActivationRegistry.seedTurn(preload names from preparer)
completeModelTurn (each Rust loop hop)
  └─ wireToolsForRequest → registry.orderedActiveNames() → full ToolDefinition list
  └─ inner.completeSingleTurn(..., availableTools = wired tools)
executeTools(tool_discovery_tool)
  └─ SearchToolsHostCoordinator → applySearchActivations(activatedToolNames)
  └─ next completeModelTurn sees expanded registry
```

**Key code:**

```727:738:feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceService.kt
    /**
     * Session append-only wire registry: preload ∪ tools activated via [tool_discovery_tool] this prompt.
     */
    private fun wireToolsForRequest(
        options: GenerationOptions,
        toolContextIndexJson: String?,
    ): List<ToolDefinition> {
        if (toolContextIndexJson != null && toolNamesFromContextIndex(toolContextIndexJson) == null) {
            return emptyList()
        }
        val poolByName = toolDefinitionLookupPool(options).associateBy { it.name }
        return toolActivationRegistry.orderedActiveNames().mapNotNull { poolByName[it] }
    }
```

```17:36:core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/ToolSessionActivationRegistry.kt
class ToolSessionActivationRegistry(
    ...
) {
    /** Replaces registry with [preloadNames] (typically preparer active tools for this turn). */
    fun seedTurn(preloadNames: Collection<String>) {
        val updated = synchronized(orderedNames) {
            orderedNames.clear()
            preloadNames.forEach { addUnique(it) }
            orderedNames.toList()
        }
        onActiveNamesUpdated(updated)
    }
```

- **Per `sendPrompt`:** registry resets to preload; discoveries within the assistant turn accumulate until prompt ends.
- **Cross-turn persistence:** `MonotonicToolActivationPort` records activations by `chatId`; preparer merges into `sessionActivatedNames` for catalog/disclosure modes—not directly into wire registry (wire is session-scoped per prompt).

### 2.2 `toolContextIndexJson` sidecar vs native `tools[]`

| Artifact | Location | Content | Purpose |
|----------|----------|---------|---------|
| **`toolContextIndexJson`** | Volatile context sidecar (SC) via `buildContextSidecars` | Compacted JSON: `advertised_tools` → **name strings only**; long `full_schema_ref` omitted | Model **awareness** of callable/advertised set; Rust `ToolContextIndex` mirror |
| **Native `tools[]`** | Provider API field (parallel to messages) | **Full** JSON schemas for wire registry tools | Native structured tool calling |
| **Discovery tool result** | Session message (tool role) | Full schemas for **newly activated** tools only | Single semantic disclosure per §0 invariant |

**Compaction:**

```26:40:feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/appendonly/ContextSidecarCompaction.kt
    fun compactToolContextIndexJson(raw: String): String =
        runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            val compacted =
                buildMap<String, JsonElement> {
                    root.forEach { (key, value) ->
                        when (key) {
                            "advertised_tools" -> put(key, compactAdvertisedTools(value))
                            "full_schema_ref" -> put(key, compactFullSchemaRef(value))
                            else -> put(key, value)
                        }
                    }
                }
            JsonObject(compacted).toString()
        }.getOrElse { compactJsonObject(raw) }
```

**`wireToolsForRequest` does not read `advertised_tools` for filtering**—only uses sidecar as a **suppression signal** (malformed index → empty `tools[]`). Actual wire set comes from `ToolSessionActivationRegistry`.

### 2.3 Stable prefix fingerprint

```23:26:feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/appendonly/AgentLoopMessageNormalization.kt
internal fun List<ToolDefinition>.toToolsFingerprint(): String {
    if (isEmpty()) return "tools:empty"
    return "tools:" + map { it.name }.sorted().joinToString(",")
}
```

`ProviderAppendOnlyContext.ensureStablePrefix` bumps **version** on fingerprint change but **does not clear** the message append-only log. Remote provider caches, however, key off **actual wire bytes** (full schemas in `tools[]`), not Enclave's fingerprint abstraction.

### 2.4 Tool stability within a tool loop

`plans/prompt-caching/spec.md` requires identical `tools[]` when toggling `allowToolCall`; providers use `tool_choice` / `toolConfig` NONE instead of omitting tools:

```490:502:feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/BaseOpenAiSdkInferenceService.kt
            val toolsAvailable = availableTools.isNotEmpty() && supportsClientToolsForModel(requestModelId)
            if (toolsAvailable) {
                availableTools.forEach { builder.addTool(it.toChatCompletionTool(strict = useStrictToolSchemas)) }
                builder.parallelToolCalls(false)
                if (shouldIncludeToolChoice(requestModelId, toolsAvailable, options)) {
                    val toolChoice =
                        if (allowToolCall) {
                            ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.AUTO)
                        } else {
                            ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.NONE)
                        }
                    builder.toolChoice(toolChoice)
                }
            }
```

**Discovery mid-loop:** If activation happens between `completeModelTurn` calls *within the same assistant turn*, the **next** model call carries an expanded `tools[]` → cache miss vs the prior call in that turn. This is the highest-frequency pain point.

---

## 3. Provider-by-Provider Analysis

| Provider family | Service | Tool wire mechanism | Can schemas live in sidecar/tail only? | Cache behavior on +1 tool | Append-only sidecar pattern for schemas? |
|-----------------|---------|---------------------|----------------------------------------|---------------------------|------------------------------------------|
| **OpenAI-compatible** (OpenAI, OpenRouter, xAI, DeepSeek, Z.ai) | `BaseOpenAiSdkInferenceService` | Top-level `tools[]` parallel field | **No** — native `tool_calls` require definitions in `tools[]` | Full prefix invalidation (tools tokenize before messages) | **No** — sidecar is `messages[]` system entries; not interpreted as tools |
| **Anthropic** | `AnthropicInferenceServiceImpl` | Top-level `tools` param | **No** — `tool_use` blocks require `tools` param | Ephemeral breakpoint on **last** tool; adding tool moves breakpoint and changes cached suffix | **No** — sidecars prepended to latest user text |
| **Google GenAI** | `GoogleInferenceServiceImpl` + `GooglePromptCacheSession` | `CachedContent` embeds tools at create time; live request references cache | **No** — `FunctionDeclaration` must be in cache or live config | `CacheSignature.toolNames` mismatch → delete + recreate cache | **No** — sidecars prepended to latest user content |
| **On-device Llama** | `LlamaInferenceServiceImpl` | `GenerationOptions.activeTools` → session render / tool syntax | **Partial** — schemas can live in transcript; native tool path uses active tool list | KV continuation checks `systemPrompt` + message prefix; tool set change likely invalidates continuation | **N/A** — merges sidecars into user `[context]`; no remote prefix cache |

### 3.1 OpenAI-compatible family

**API facts:**
- Tool definitions are a **separate request field**, not message content.
- Prompt caching (OpenAI `prompt_cache_key`, xAI `x-grok-conv-id`, OpenRouter `cache_control`) requires **byte-identical prefix** including `tools[]`.
- `tool_choice: none` still sends full schemas (`AOC-06`, `XaiPromptCacheLiveContextTest`).

**Enclave behavior:** Append-only sidecars (`ProviderAppendOnlyContext`, tail placement) apply to **context sidecars only**, not tools. `StablePrefixSnapshot` tracks `toolsFingerprint` separately.

**Verdict:** Schemas in frozen sidecar would be **invisible to the API tool-calling layer**. Model might read schema text but provider will not emit valid `tool_calls` for undeclared tools.

### 3.2 Anthropic

**API facts:**
- Tools in dedicated parameter; cache control on system block, **last tool**, and stable history tail (`plans/prompt-caching/spec.md` §1).
- Enclave sets ephemeral breakpoint on last tool:

```409:422:feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/AnthropicInferenceServiceImpl.kt
            if (availableTools.isNotEmpty()) {
                availableTools.forEachIndexed { idx, toolDef ->
                    val isLast = idx == availableTools.size - 1
                    val cacheControl =
                        if (useCaching && isLast) {
                            AnthropicPromptCacheSupport.ephemeralCacheControl()
                        } else {
                            null
                        }
                    builder.addTool(toolDef.toAnthropicTool(cacheControl))
                }
                if (!allowToolCall) {
                    builder.toolChoice(ToolChoice.ofNone(ToolChoiceNone.builder().build()))
                }
            }
```

**Verdict:** Appending a tool changes which definition carries the breakpoint and changes all tool definition bytes after prior tools. **Cannot** append tool schemas to message tail for native `tool_use`. Anthropic offers provider-native tool search (out of scope per FEAT spec)—Enclave uses host-side BM25/embedding discovery instead.

### 3.3 Google GenAI

**API facts:**
- `CachedContent` created with `systemInstruction`, stable `contents`, and **`tools`** together.
- Reuse requires `CacheSignature.startsWith` — includes **`toolNames` equality**:

```156:177:feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/GooglePromptCacheSession.kt
    private data class CacheSignature(
        val systemPrompt: String,
        val messages: List<MessageSignature>,
        val toolNames: List<String>,
    ) {
        fun startsWith(cached: CacheSignature): Boolean =
            systemPrompt == cached.systemPrompt &&
                toolNames == cached.toolNames &&
                ...
    }
```

- Test `ensureCacheForStablePrefix_recreatesCacheWhenToolSetChanges` confirms recreate on tool add.
- **No partial update** API for adding tools to existing `CachedContent`.

**Verdict:** Tool schemas cannot be sidecar-only. Any activation → new cache creation cost (when above token threshold).

### 3.4 On-device Llama (`LlamaInferenceServiceImpl`)

**API facts:**
- No remote `tools[]`; `completeSingleTurn` sets `toolingEnabled = allowToolCall && availableTools.isNotEmpty()` and passes `activeTools`.
- Sidecars merged into last user message via `[context]…[/context]`.
- KV continuation validates message prefix + `systemPrompt`, not explicit tools fingerprint:

```271:280:feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/LlamaInferenceServiceImpl.kt
            val continuation =
                kvContinuationValid &&
                    revision == kvTranscriptRevision &&
                    systemPrompt == kvSystemPrompt &&
                    sessionMessages.size > kvRawTranscript.size &&
                    prefixMismatchIndex < 0
```

**LiteRT native path (`LiteRtNativeToolsets`):** Builds tool providers from `activeToolNames`; tools not in the map remain callable via **envelope / JSON tool executor** path when model uses text tools.

**Verdict:** Local path is **most flexible**—discovery result schemas in transcript can precede native tool registration. Cache concern is **KV reuse**, not provider prefix. Dynamic tool list changes may still invalidate native KV if rendered prompt changes.

---

## 4. Alternative Patterns Evaluated

| Alternative | Feasibility | Cache impact | Correctness risk | Notes |
|-------------|-------------|--------------|------------------|-------|
| **A. Append schemas to frozen sidecar tail** | ❌ API providers | Sidecar bytes frozen, but **no native tool calls** | High — model may hallucinate args without provider validation | Sidecars already carry **names** via `toolContextIndexJson`; schemas belong elsewhere |
| **B. Stable tool slots (pre-register stubs)** | ⚠️ Theoretical | Stable `tools[]` count/order | High — stub schemas break strict mode; providers validate args against schema | Defeats lazy disclosure token savings |
| **C. Tool index in sidecar only; schemas in tool-result only** | ⚠️ Local yes; API partial | Best cache (no `tools[]` growth) | **API: model cannot emit native tool_calls** for undeclared tools until registered | Matches §0 for **semantics**; insufficient for **native calling** |
| **D. Anthropic breakpoint-only-on-system** | ⚠️ Config change | Reduces tool-add invalidation slightly | Loses tool-definition caching benefit | Still invalidates when tool **count** changes |
| **E. Google CachedContent partial update** | ❌ | N/A | N/A | API does not support |
| **F. Defer `tools[]` update until next `sendPrompt`** | ⚠️ | Avoids intra-turn cache miss | Model may attempt native call before registration — **failure or fallback needed** | Could work if model always uses discovery result schemas textually first |
| **G. Session wire registry (current/intended)** | ✅ | One miss per activation | Low — tested (`TSD-27`) | **Recommended baseline** |
| **H. Full catalog wire (`eligibleToolPool`)** | ✅ | Worst — large static prefix, no deferral | Low | **Forbidden** by FEAT-tool-search-discovery §0 |

---

## 5. Recommended Architecture

### 5.1 Primary approach — Session wire registry with cache-aware activation timing

**Keep:**
1. **Minimal preload** — `tool_discovery_tool`, web tools when configured, intent-gated tools (`ChatInferenceRequestPreparer`).
2. **Session wire registry** — append-only within `sendPrompt`; map to full schemas only for registered names.
3. **Single schema source** — full schemas in discovery **tool-result** at activation; `tools[]` registers same definitions on subsequent provider calls (no duplicate in system prompt).
4. **`toolContextIndexJson` sidecar** — continue names-only advertisement; do **not** duplicate full schemas into sidecar.
5. **`tool_choice` gating** — preserve `tools[]` stability when disabling calls mid-loop.

**Add / refine:**
1. **Activation timing policy** — Register new tools in `tools[]` on the **first `completeModelTurn` after** the discovery tool-result is committed to `sessionMessages`, not before. (Already implicit; document as contract.)
2. **Accept amortized cache cost** — One prefix rebuild per newly activated tool **per session** is expected; unchanged turns hit cache.
3. **Cap activations** — `ToolSearchThresholds.MAX_ACTIVATED_TOOLS_PER_SESSION` limits worst-case prefix growth.
4. **Monotonic cross-turn catalog** — Preparer already merges `MonotonicToolActivationPort` into catalog/disclosure; consider merging discovered names into **preload for subsequent user turns** so wire registry after `seedTurn` includes prior-session tools without re-discovery (reduces repeated discovery latency; **increases** stable preload size—tradeoff).

### 5.2 Fallback — Transcript-only calling window (local-first, API experimental)

For tools activated late in a turn:
- Rely on **full schemas in tool-result message** for model reasoning.
- Delay `tools[]` registration until **next user message** (`sendPrompt` boundary) if the model does not need an **immediate native recall** of that tool in the same assistant turn.

**Risk:** If the model emits a native `tool_call` for a tool not yet in `tools[]`, provider may reject or ignore. Mitigation: Rust callable-set validation + host nudge. **Not recommended as default for API providers** without explicit provider tolerance testing.

### 5.3 On-device path

- Continue **`seedTurnPreservingDiscovered`** in LiteRT (`ConversationSessionCoordinator`) so discoveries persist across in-engine turns.
- Prefer **envelope/text tool path** for rarely used discovered tools; add to `LiteRtNativeToolsets` only when native annotations exist.
- KV continuation: treat tool-list changes like system prompt changes if cache miss rate is problematic (future: include sorted tool names in continuation signature).

---

## 6. Migration Path from Current Wire Registry

| Phase | Action | Validation |
|-------|--------|------------|
| **0 — Baseline** | Confirm production uses registry not `eligibleToolPool` | `ToolSearchWireDiscoveryTest` (`TSD-13`, `MUT-02`, `TSD-27`) |
| **1 — Document contract** | Treat `toolsFingerprint` change + `tools[]` growth as **expected cache miss** in telemetry dashboards | Compare `cacheReadInputTokens` before/after discovery turns |
| **2 — Sidecar clarity** | Ensure `toolContextIndexJson` never carries full schemas (compaction already enforced) | `ContextSidecarCompactionTest` |
| **3 — Cross-turn preload (optional)** | Merge `MonotonicToolActivationPort.activatedNames(chatId)` into preparer `activeTools` / wire seed for API path | Extend `ChatInferenceRequestPreparerCacheStabilityTest` pattern |
| **4 — Intra-turn deferral (optional, gated)** | Feature flag: skip registry append until post-tool-result `completeModelTurn` | New inference tests for tool_call rejection paths |
| **5 — Telemetry** | Log `toolsFingerprint` version + activation events alongside cache read tokens | `InferenceTelemetrySupport` |

**Do not migrate toward:** full-catalog wire, duplicate schemas in system prompt + sidecar + `tools[]`, or provider-native discovery (explicit non-goal).

---

## 7. Provider-Specific Gotchas

| Gotcha | Providers | Enclave handling |
|--------|-----------|------------------|
| **`tool_choice: none` still sends `tools[]`** | OpenAI, Anthropic, Google | By design for cache stability |
| **Strict JSON schemas** | OpenAI (optional), xAI | `useStrictToolSchemas = false` default; xAI sends `strict: false` |
| **`parallel_tool_calls: false`** | OpenAI family | Enforced in `BaseOpenAiSdkInferenceService` |
| **Tools in prefix before messages** | OpenAI, xAI | Any `tools[]` change invalidates message prefix cache |
| **Anthropic last-tool breakpoint moves** | Anthropic | New tool appended at end → new breakpoint target |
| **Google cache recreate on tool change** | Google | `GooglePromptCacheSessionTest.ensureCacheForStablePrefix_recreatesCacheWhenToolSetChanges` |
| **Malformed `toolContextIndexJson` suppresses all tools** | All via RustLoop | Returns `emptyList()` from `wireToolsForRequest` |
| **OpenAI family append-only persists across `sendPrompt`** | OpenAI, xAI, OpenRouter | Frozen sidecars survive; tool registry **resets** each prompt via `seedTurn` |
| **Anthropic/Google append-only invalidates each prompt** | Anthropic, Google | Fresh context per user message |
| **LiteRT tools not in native map** | Local | Fall back to envelope executor (`LiteRtNativeToolsets` comment) |

---

## 8. Open Questions & Risks

1. **Intra-turn discovery + immediate native recall:** How often does the model call a newly discovered tool in the **same** assistant turn before `tools[]` updates? If frequent, deferral fallback is unsafe.
2. **Cross-turn wire seed:** Should `seedTurn` union `MonotonicToolActivationPort` names so turn 2+ sends expanded preload without re-discovery? Improves UX; ** worsens** cache baseline for all subsequent turns in the chat.
3. **Provider-specific tool search APIs:** Anthropic/OpenAI native tool search could offload discovery—but conflicts with unified Enclave catalog and is a spec non-goal.
4. **Schema hash vs name fingerprint:** `toToolsFingerprint` uses sorted **names** only; schema edits without rename won't bump version (likely rare for static `ToolDefinition`).
5. **OpenRouter `cache_control` on messages vs tools:** Message breakpoints help history; **tools field remains uncached separately** on some providers—confirm per-model behavior in telemetry.
6. **Compaction + registry:** Rust compaction clears append-only state; rediscovered tools may need re-activation unless monotonic port restores preload.
7. **Duplicate schema invariant enforcement:** Automated guard that deferred tool schemas appear in exactly one of {tool-result, `tools[]`} per turn—not both sidecar and wire.

---

## 9. Key Code References

| Topic | Location |
|-------|----------|
| Wire registry | `RustLoopInferenceService.wireToolsForRequest` |
| Activation registry | `ToolSessionActivationRegistry` |
| Tools fingerprint | `AgentLoopMessageNormalization.toToolsFingerprint` |
| Sidecar compaction | `ContextSidecarCompaction.compactToolContextIndexJson` |
| OpenAI tools + toolChoice | `BaseOpenAiSdkInferenceService.completeSingleTurn` |
| Anthropic tool cache breakpoint | `AnthropicInferenceServiceImpl` (~409–422) |
| Google cache + tools signature | `GooglePromptCacheSession` |
| Llama tools + KV continuation | `LlamaInferenceServiceImpl.completeSingleTurn` |
| Design authority | `plans/FEAT-tool-search-discovery/spec.md` §0, §3.7 |
| Cache rules | `plans/prompt-caching/spec.md` §1 |
| Append-only invariant | `plans/FEAT-append-only-provider-context/spec.md` §1.1 |
| Provider matrix | `docs/provider-wire-context-spec.html` §2, §Q2, open issue #4 |

---

## 10. Decision Summary

| Question | Answer |
|----------|--------|
| Is "append schemas to frozen tail/sidecar" valid per provider? | **No** for native API tool calling (all BYOK providers). **Partial** for on-device transcript-only semantics. |
| What does Enclave do today with sidecar vs `tools[]`? | Sidecar = **names** (+ omitted schema ref); `tools[]` = **full schemas** for wire registry; discovery result = **full schemas once**. |
| Best architecture? | **Session wire registry** (minimal preload, append-on-activation), accept **one cache miss per new tool**, never full-catalog wire. |
| Top fallback? | **Defer `tools[]` registration** to next user turn only with strong validation—not default for API. |

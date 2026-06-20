# Prompt: DeepSeek Prefix Cache Troubleshooting — Autonomous Loop

You are continuing an investigation into **abnormally low prompt cache hit rates** when sending chat requests to DeepSeek models. A prior session built extensive diagnostics and narrowed the problem but could not resolve it. Your job is to drive this to resolution: iterate on code changes and validate against a live emulator until cache hit rates are consistently high (85%+) across 3-5 consecutive turns.

---

## 1. The Problem

DeepSeek's prefix caching (automatic, server-side, block-granular at 128 tokens) is hitting at very low rates — often **0% on turns where the prefix should be byte-identical** to the previous turn. When caching works, hits are 89-95%. When it doesn't, they're 0-47%. Overall rate is ~47% but should be ~85-90%.

This happens **both** through OpenRouter (routed to Alibaba/SiliconFlow) **and** through DeepSeek's direct API (`api.deepseek.com`). Since both paths show the same pattern, this is NOT an OpenRouter routing issue — it's something in how the request is constructed, or a provider-side KV cache eviction pattern we need to work around.

## 2. What Was Already Built (Observability)

The prior session added three diagnostic log lines, all emitted from `BaseOpenAiSdkInferenceService.kt`:

### `PROMPT_CACHE` (per request)
```
PROMPT_CACHE seq=0 chat=<id> model=deepseek/deepseek-v4-flash allowToolCall=true toolCalls=0
sessionMsgs=1 wireMsgs=3 tools=5 frozenSidecars=1 liveSidecar=true appendOnlyClears=0
appendOnlyLastClear=none input=2958 cacheRead=0 hitPct=0 inputDelta=n/a prefixChanged=false
reasons=none wireHash=993454... prevWireHash=n/a systemHash=7a2e9e... toolsHash=9011d3...
toolsSchemaHash=3c2094... servingProvider=alibaba responseId=gen-1781810226-...
wireHead=[sys:# ABOUT THE USER:... | sys:Reference policy:... | user:Hi!...]
```

Key fields:
- `hitPct` — cache hit percentage (cacheRead / input * 100)
- `prefixChanged` / `reasons` — whether the app's fingerprint detected a prefix change, and why
- `frozenSidecars` — count of frozen sidecar messages in the prefix
- `servingProvider` — the upstream provider that served the request (from `openrouter-provider` header or fallback to pinned `preferredProvider`)
- `responseId` — the provider's response ID for dashboard correlation

### `PROMPT_CACHE_BREAK` (per request, when previous exists)
Flat-text fingerprint prefix diff — compares a normalized role-tagged rendering (`S:`, `U:`, `A:text=...;tools=...`, `T:...#callId`) between consecutive turns.

### `PROMPT_CACHE_JSON_BREAK` (per request, when previous exists)
**Actual wire JSON** prefix diff — compares the real serialized JSON request body between consecutive turns using the SDK's own `ObjectMappers.jsonMapper()`. This is the authoritative diagnostic. `before`/`old`/`new` show the exact byte where the JSON diverges.

> **CRITICAL:** The `PROMPT_CACHE_JSON_BREAK` diagnostic was just fixed to use the SDK's actual wire serializer (`WireJsonMapperAccessor` — a Java helper that accesses `com.openai.core.ObjectMappers.jsonMapper()`). The previous version used a plain `ObjectMapper()` which included SDK-internal fields like `"valid":true` and had different field ordering. **This fix has NOT been validated with live data yet.** Your first task is to capture the corrected JSON break output.

## 3. Key Findings So Far

1. **The flat-text fingerprint shows 89-97% prefix match** on most turns — the system prompt, tools, tool schemas, and frozen sidecars all appear stable. The `toolsSchemaHash`, `systemHash`, and `toolsHash` are rock-stable across all turns.

2. **`frozenSidecarCount` changes correlate with low cache hits.** When the count is stable → 89-95% hit. When it changes → 0-47% hit. However, this may be spurious — the count changes on turns where the conversation grows significantly (tool results, state growth), which also means larger inputs that are more prone to KV cache eviction.

3. **The previous (broken) JSON break showed divergence only at message array growth** — the `old` field was always `],"model":"deepseek/...",...` (end of messages array + top-level params) and `new` was always a new assistant message being appended. This suggested the prefix was stable. But this was with the WRONG serializer, so it's not conclusive.

4. **It's NOT replica switching.** Routing is pinned (`allowFallbacks=false`, `preferredProvider=alibaba`). High-hit and low-hit turns have similar time gaps (30-50s). If it were eviction, similar gaps would produce similar eviction.

5. **It's NOT tool schema changes.** `toolsSchemaHash` is stable across every turn.

6. **It's NOT system prompt non-determinism.** `systemHash` is stable. The live timestamp is correctly on the trailing user message, not in the prefix.

## 4. The Core Mystery

The flat-text fingerprint says the prefix is stable. The (broken) JSON break agreed. Yet DeepSeek reports 0% cache hit on some turns — zero tokens cached, meaning the very first 128-token block diverged. If the prefix is truly byte-identical, the first block MUST be cached (unless evicted, which timing rules out).

**Two hypotheses remain:**
- **A) The wire JSON differs in a way neither diagnostic caught** — a field the flat-text fingerprint omits (e.g., `reasoning_content`, `name` on tool messages, additional properties) that the corrected JSON break will now reveal.
- **B) The prefix IS truly stable, and the misses are provider-side KV cache eviction** under memory pressure — larger inputs (tool results) evict the cached prefix. This would mean the fix is reducing input size, not fixing prefix stability.

The corrected `PROMPT_CACHE_JSON_BREAK` (with the SDK's real wire serializer) will distinguish these definitively.

## 5. Architecture Context

### Request construction flow
- `BaseOpenAiSdkInferenceService.completeSingleTurn` builds the request:
  1. System prompt (stable per chat, no timestamps/UUIDs)
  2. Tool schemas (`tools[]` array, name-sorted, from static `ToolDefinition` registry)
  3. Messages with frozen sidecars + live sidecar (via `OpenAiCompatibleVolatilePlacement.buildMappedBodyWithSidecars`)
  4. Top-level params (`model`, `max_completion_tokens`, `temperature`, `reasoning_effort`, `parallel_tool_calls`, `tool_choice`, `stream_options`)

### Sidecar placement (the suspected destabilizer)
- `OpenAiCompatibleVolatilePlacement.kt` — inserts sidecars at computed indices in the message array
- `ProviderAppendOnlyContext.kt` — manages frozen sidecar lifecycle (append-only, dedup by content)
- `tailInsertIndex = mappedBodySize - 1` — the live sidecar goes before the last user message. This index **shifts** as the conversation grows.
- When a live sidecar is promoted to frozen (`endTurnFreezeSidecar`), it's stored at its current `tailInsertIndex` and replayed at that index on future turns.
- The user correctly pointed out that the frozen-replay design is sound — old sidecars stay at original positions. The question is whether something else in the JSON structure breaks.

### DeepSeek-specific notes
- DeepSeek uses **implicit/automatic** prefix caching — no client-side markers (`cache_control` is NOT sent for DeepSeek).
- Cache is block-granular (128 tokens). Any divergence in the first block zeros the entire cache.
- All non-zero cached token counts are multiples of 128 (confirming block granularity).
- The `DeepSeekInferenceServiceImpl` extends `BaseOpenAiSdkInferenceService` with `provider = "DEEPSEEK"`.

### Wire JSON serialization
- The OpenAI Java SDK (`com.openai:openai-java:4.36.0`) serializes the request body using `ObjectMappers.jsonMapper()` internally.
- `WireJsonMapperAccessor.java` (a Java helper) accesses this internal mapper so diagnostics produce byte-identical JSON to the wire format.
- `serializeBody()` in `BaseOpenAiSdkInferenceService` uses this accessor for the `PROMPT_CACHE_JSON_BREAK` diagnostic.

## 6. Your Task — Step by Step

### Phase 1: Capture corrected diagnostic data

1. **Build and install the app on the emulator:**
   ```bash
   ./gradlew :app:assembleDebug
   ```
   Install via `android run` or `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

2. **Sign in:** The app password is `glm`. The DeepSeek provider is already configured.

3. **Start logcat capture** (filter for cache diagnostics):
   ```bash
   adb logcat -s "OpenRouterInferenceService:D" "DeepSeekInferenceService:D" "PROMPT_CACHE:D"
   ```
   Or broader: `adb logcat | grep -E "PROMPT_CACHE|DeepSeek|OpenRouter"`

4. **Send 4-5 messages** in the main Chat screen to the configured DeepSeek model. Use simple conversational messages (no tools) to keep the prefix stable:
   - Message 1: "Hi"
   - Message 2: "Tell me about X" (something short)
   - Message 3: "Can you elaborate on that?"
   - Message 4: "Interesting. What about Y?"
   - Message 5: "One more question: Z?"

   Keep messages short and avoid triggering tool calls (web search, etc.) to isolate prefix stability from input growth.

5. **Capture the logs** for all 5 turns. Look at:
   - `PROMPT_CACHE` lines: `hitPct`, `prefixChanged`, `reasons`, `frozenSidecars`
   - `PROMPT_CACHE_JSON_BREAK` lines: `match=X/Y (Z%)`, `before`, `old`, `new`

### Phase 2: Analyze

6. **Compare the JSON break match% against the cache hit%:**
   - If JSON break shows **100% (or near 100%) match** but `hitPct=0` → the prefix IS stable, misses are **provider-side eviction** (Hypothesis B). Proceed to Phase 3B.
   - If JSON break shows **divergence early in the body** (low match%, `before`/`new` showing a structural change mid-prefix) → there's a **client-side prefix bug** (Hypothesis A). Proceed to Phase 3A.

7. **If proceeding to 3A:** Examine the `before`/`old`/`new` fields to identify exactly what changed. Common suspects:
   - `reasoning_content` field appearing/disappearing on assistant messages
   - Sidecar message insertion shifting the JSON structure
   - Field ordering differences in the serialized JSON
   - Additional properties (`name`, `cache_control`) toggling

### Phase 3A: Fix client-side prefix instability (if JSON reveals divergence)

8. **Identify the root cause** from the JSON break output.

9. **Fix it in code.** Likely files:
   - `BaseOpenAiSdkInferenceService.kt` — message construction, `reasoning_content` attachment
   - `OpenAiCompatibleVolatilePlacement.kt` — sidecar placement
   - `ProviderAppendOnlyContext.kt` — sidecar freeze/replay
   - `AgentLoopMessageNormalization.kt` — message normalization

10. **Respect the repo's AGENTS.md contract:** Read `/contracts/` before writing code. Use `androjack` MCP tools to validate generated code (minSdk 34, targetSdk 36). Write tests for logic changes. Run `./gradlew ktlintCheck` and `./gradlew detekt` (maxIssues: 0).

11. **Rebuild, reinstall, and retest** with the same 5-message pattern.

### Phase 3B: Work around provider-side eviction (if prefix is truly stable)

12. **If the prefix is byte-identical but cache misses persist**, the issue is DeepSeek's KV cache management. Client-side options:
    - **Reduce input size** — compact old tool results, summarize state ledger, trim frozen sidecars
    - **Reduce sidecar count** — fewer frozen sidecar messages = smaller working set = less eviction pressure
    - **Keep requests tightly spaced** — minimize gaps between turns
    - **Test with DeepSeek's direct API** (already available via BYOK) vs OpenRouter to compare

13. **Implement the most promising mitigation** and retest.

### Phase 4: Validate

14. **Run the 5-message test again.** The target is **85%+ cache hit rate across 3-5 consecutive turns** (excluding the first turn, which is always a cold start at 0%).

15. **If not yet at target:** Go back to Phase 2 with the new data. Iterate.

16. **When target is met:** Run the full verification suite:
    ```bash
    ./gradlew :feature:inference:testDebugUnitTest
    ./gradlew :core:domain:testDebugUnitTest
    ./gradlew ktlintCheck
    ./gradlew detekt
    ```

## 7. Technical Reference

### Key files
| File | Purpose |
|------|---------|
| `feature/inference/.../BaseOpenAiSdkInferenceService.kt` | Base service: request construction, streaming, diagnostics logging |
| `feature/inference/.../OpenRouterInferenceServiceImpl.kt` | OpenRouter subclass: `extractServingProvider`, routing |
| `feature/inference/.../DeepSeekInferenceServiceImpl.kt` | DeepSeek direct subclass |
| `feature/inference/.../telemetry/PromptCachePrefixDiagnostics.kt` | Fingerprint, prefix break computation |
| `feature/inference/.../telemetry/PromptCacheDiagnosticsContext.kt` | Diagnostics input data class |
| `feature/inference/.../openai/OpenAiStreamUsageAccumulator.kt` | Usage parsing (tokens, cost, servingProvider body field) |
| `feature/inference/.../openai/StreamState.kt` | `StreamedOpenAiResponse`, `StreamState` |
| `feature/inference/.../openai/WireJsonMapperAccessor.java` | Java bridge to SDK's internal `ObjectMappers.jsonMapper()` |
| `feature/inference/.../openai/OpenAiClientProvider.kt` | Client construction, `wireJsonMapper` |
| `feature/inference/.../appendonly/OpenAiCompatibleVolatilePlacement.kt` | Sidecar placement in message array |
| `feature/inference/.../appendonly/ProviderAppendOnlyContext.kt` | Frozen sidecar lifecycle |
| `feature/inference/.../appendonly/AgentLoopMessageNormalization.kt` | Tool fingerprint helpers |
| `feature/inference/.../OpenAiRequestMapper.kt` | Tool schema serialization (`toChatCompletionTool`) |
| `feature/inference/.../OpenRouterPromptCacheSupport.kt` | `cache_control` markers (Claude/Gemini only) |
| `feature/inference/.../OpenRouterRoutingSupport.kt` | Provider routing body field |
| `core/domain/.../telemetry/TurnTelemetry.kt` | Telemetry model (has `servingProvider`, `providerResponseId`) |
| `core/domain/.../model/inference/ToolDefinition.kt` | Tool definition model |

### Log tags
- `OpenRouterInferenceService` — OpenRouter path diagnostics
- `DeepSeekInferenceService` — DeepSeek direct path diagnostics
- Both emit: `PROMPT_CACHE`, `PROMPT_CACHE_BREAK`, `PROMPT_CACHE_JSON_BREAK`

### Build commands
```bash
./gradlew :app:assembleDebug          # Build debug APK
./gradlew :feature:inference:testDebugUnitTest  # Inference tests
./gradlew :core:domain:testDebugUnitTest        # Domain tests
./gradlew ktlintCheck                 # Style
./gradlew detekt                      # Static analysis (maxIssues: 0)
```

### App details
- Package: `com.browntowndev.enclave`
- Sign-in password: `glm`
- DeepSeek provider is pre-configured
- Main screen is the Chat screen — type a message and send

### Emulator interaction
Use the `android` CLI or `mobile-mcp` to:
- Install the APK: `android run` or `adb install -r <apk-path>`
- Take screenshots: `android layout` or mobile-mcp
- Interact with UI: mobile-mcp tap/type actions
- Read logcat: `adb logcat` with tag filters

### What NOT to do
- Do NOT modify files in `/contracts/` without an explicit "Contract Waiver" from the user.
- Do NOT add database columns/migrations without explicit Schema Lock authorization (Schema Lock rule in AGENTS.md).
- Do NOT edit vendored native sources (`third_party/llama.cpp/`, etc.).
- Do NOT skip the `androjack` code validator or `detekt`/`ktlintCheck` before declaring completion.

## 8. Success Criteria

- `PROMPT_CACHE` log shows `hitPct=85+` on 3-5 consecutive turns (excluding cold start)
- `PROMPT_CACHE_JSON_BREAK` confirms the wire JSON prefix is stable (or you've identified and fixed the divergence)
- All tests pass: `./gradlew testDebugUnitTest`
- Style clean: `./gradlew ktlintCheck`
- Static analysis clean: `./gradlew detekt`
- Summarize what the root cause was and what fix resolved it

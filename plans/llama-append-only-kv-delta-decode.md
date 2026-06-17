# Plan: Append-only KV delta decode for llama.cpp agent-loop turns

## Background / problem

The local llama.cpp path re-renders the full chat transcript every model turn, token-prefix-matches
it against `g_history_tokens`, and trims the KV cache at the first divergent token
(`llama-android/src/main/cpp/llama-jni.cpp`, `nativeStartCompletion`, ~lines 964–1108).

This is broken for our model lineup. MiniCPM-V 4.6 (base arch `qwen35`), Qwen3.5-2B, and
Jamba-Reasoning-3B are all hybrid recurrent (SSM) architectures. llama.cpp **cannot rewind
recurrent state**: `llama_memory_seq_rm` with a partial range returns `false`
(see `third_party/llama.cpp/src/llama-memory-recurrent.cpp`). Observed on device:

```
W llama-jni: KV trim from pos 743 failed — suffix may be inconsistent
W llama-jni: KV position mismatch after trim: memory=1899 expected=743
I llama-jni: Falling back to full KV rebuild after divergence
I llama-jni: Prompt tokens: 919 (evaluating from token 0 at pos 0)
```

Divergence happens **every turn** because the transcript's assistant messages are clean text
(no reasoning, retokenized) while the KV holds the model's actual generated tokens. Result:
full prompt re-evaluation every turn, and the chat-stats "input tokens" metric never accumulates.

## Fix: append-only delta decode (the llama.cpp `simple-chat` pattern)

Never re-render and re-evaluate history. The live KV cache — including the model's actual
generated reasoning tokens — IS the conversation state. Each turn, evaluate only the **delta**:
template glue + new messages + assistant generation primer, appended at the current KV position
(`g_last_eval_pos`). No prefix matching, no trimming, no rewind. Full rebuild only when the host
explicitly invalidates (compaction, transcript revision change, model/session switch).

Key insight that makes this sound: the rendered text of *old* messages is never compared with
the KV. Char-length arithmetic on rendered strings is used **only to extract the new suffix**;
the previously evaluated tokens (including reasoning and injected sidecars) simply remain in the
KV at their positions.

---

## Part 1 — JNI: `llama-android/src/main/cpp/llama-jni.cpp`

### 1.1 New parameters on `nativeStartCompletion`

Add two parameters to `Java_..._JniLlamaEngine_nativeStartCompletion` (~line 683) and to the
Kotlin `private external fun nativeStartCompletion` declaration
(`feature/inference/.../llama/JniLlamaEngine.kt` ~line 391):

- `jboolean continuation` — host asserts the message list is an append-only extension of the
  previous call and the KV/state from the previous call is still valid.
- `jint prevMessageCount` — number of leading messages (in the current `roles`/`contents`
  arrays) that were already part of the previous call's message list.

Defaults for all existing call sites: `continuation = false`, `prevMessageCount = 0`
(preserves current behavior exactly).

### 1.2 Delta path inside `nativeStartCompletion` (text-only branch)

The function currently renders the full prompt via the Jinja GGUF template into `promptStr`,
tokenizes it, prefix-matches, and evaluates. Add a new branch **before** the existing
prefix-match logic:

```
if (continuation && !image_paths.empty() == false /* text only */
        && prevMessageCount > 0
        && (size_t) prevMessageCount < total message count
        && !g_history_tokens.empty()
        && g_last_eval_pos > 0) {
    // DELTA MODE
}
```

Delta mode steps:

1. Render TWO strings with the same Jinja template machinery already used for `promptStr`:
   - `full_str`   = all messages, `add_generation_prompt = true` (same as today's `promptStr`)
   - `prefix_str` = only the first `prevMessageCount` messages, `add_generation_prompt = false`
2. Guard: if `full_str` does **not** start with `prefix_str` byte-for-byte
   (`full_str.compare(0, prefix_str.size(), prefix_str) != 0`), log a warning
   (`"delta mode: rendered prefix mismatch, falling back to full rebuild"`) and fall through to
   the existing full-rebuild path (`clear_kv_cache_and_tracking()` + evaluate everything).
   This catches templates that render non-incrementally (date injection, system merging, etc.).
3. `delta_str = full_str.substr(prefix_str.size())`
4. Tokenize `delta_str` with `add_special = false, parse_special = true`. **No BOS.** The
   existing `tokenizePrompt` helper likely adds BOS — use the underlying `llama_tokenize` with
   the right flags or add a parameter to the helper.
5. De-duplicate the seam: the model's generation usually ended with an EOG/end-marker token that
   is already in `g_history_tokens` (the generation loop appends generated tokens, and the
   force-close path at ~line 1362 appends end markers). The rendered delta may *start* with that
   same end-marker text. Before evaluating: while the first remaining delta token equals
   `g_history_tokens.back() - (k-1)`-style tail... — keep it simple: if
   `delta_tokens.front() == g_history_tokens.back()` and that token is an EOG/control token
   (`llama_vocab_is_eog` or `llama_vocab_is_control`), drop `delta_tokens.front()`. One-token
   check is enough for ChatML-style templates; log when it triggers.
6. Context-overflow guard: if
   `g_last_eval_pos + delta_tokens.size() + max_tokens > n_ctx`, log
   `"delta mode: would overflow context, falling back to full rebuild"` and fall through to the
   full-rebuild path (full rebuild drops accumulated reasoning tokens from the KV, reclaiming
   space — it acts as a natural KV compaction).
7. Evaluate `delta_tokens` in batch chunks starting at position `g_last_eval_pos` (reuse the
   existing chunked decode loop structure; positions are
   `g_last_eval_pos + i`). Set logits flag on the final token only.
8. Bookkeeping — **append, do not assign**:
   - `g_history_tokens.insert(end, delta_tokens.begin(), delta_tokens.end())`
   - `g_history_positions`: append `g_last_eval_pos + i` for each delta token
   - `g_last_eval_pos = sync_last_eval_pos_from_memory()`
   - CRITICAL: the existing post-eval code at ~lines 1100–1102 does
     `g_history_tokens.assign(tokens.begin(), tokens.begin() + n_prompt_tokens)` which REPLACES
     history with the current prompt's tokens. The delta path must NOT run that assign. Guard it
     behind `if (!delta_mode)`.
9. Reporting (used by the `onComplete(promptTokens, generatedTokens, prefixReusedTokens)`
   callback at the end of generation):
   - `n_prompt_tokens` (reported as `promptTokens`) = number of **delta tokens evaluated**
   - `g_last_prefix_reused_tokens` (reported as `prefixReusedTokens`) = the KV position before
     the delta eval (i.e., `g_last_eval_pos` captured at entry)
10. Log line for verification: `"Delta eval: %d new tokens at pos %d (kv total now %d)"`.

The generation loop after prompt eval is unchanged — generated tokens already get appended to
`g_history_tokens`/`g_history_positions` and advance `g_last_eval_pos`.

### 1.3 What NOT to touch

- The existing prefix-match/trim/full-rebuild path stays as-is for `continuation = false`
  (first turn, resets, the legacy non-agent `sendPrompt` path, multimodal).
- Multimodal branch (`image_paths` non-empty): always full rebuild; ignore `continuation`.
- `nativeGetContextUsage`, `clear_kv_cache_and_tracking`, state save/restore: unchanged.

---

## Part 2 — Kotlin engine plumbing

### 2.1 `feature/inference/.../llama/JniLlamaEngine.kt`

- Extend the `internal fun startCompletion(...)` wrapper (~line 359) and the
  `external fun nativeStartCompletion` (~line 391) with `continuation: Boolean` and
  `prevMessageCount: Int` (defaults `false` / `0`).
- `generateWithOptions(options)` (~line 212): accept and forward the two new values. Add them to
  the engine-port method signature it implements. The values come from the session manager (2.2).
- `generate()` (legacy path, ~line 140): always `continuation = false`.

### 2.2 `LlamaEnginePort` + `LlamaChatSessionManager`

`core/domain/.../port/inference/LlamaEnginePort.kt` and
`feature/inference/.../llama/LlamaChatSessionManager.kt`:

- Add `continuation: Boolean` and `prevMessageCount: Int` parameters to the
  generate-with-options path (port method + session manager passthrough), defaulting to
  `false`/`0` so all existing callers and tests compile unchanged.

### 2.3 `LlamaInferenceServiceImpl.completeSingleTurn` — continuation decision

File: `feature/inference/.../LlamaInferenceServiceImpl.kt` (~lines 241–322).

Add per-session state (fields next to `history` / `currentSignature`):

```kotlin
/** Append-only continuation tracking for agent-loop turns. */
private var kvTranscript: List<AgentLoopMessage> = emptyList()
private var kvSystemPrompt: String? = null
private var kvTranscriptRevision: Long = -1
private var kvContinuationValid = false
```

In `completeSingleTurn`, BEFORE generation, compute:

```kotlin
val revision = options.transcriptRevision   // already plumbed into turnOptions
val isAppendOnlyExtension =
    kvContinuationValid &&
        revision == kvTranscriptRevision &&
        systemPrompt == kvSystemPrompt &&        // STABLE system prompt required (see 2.4)
        sessionMessages.size > kvTranscript.size &&
        sessionMessages.take(kvTranscript.size) == kvTranscript
val continuation = isAppendOnlyExtension
val prevMessageCount = if (continuation) kvTranscript.size + 1 else 0
// +1 accounts for the system message that buildNativeMessages prepends at index 0
```

NOTE on `prevMessageCount`: `LlamaGenerationSession.buildNativeMessages` inserts/overwrites a
SYSTEM message at index 0, so the native message array = [system] + sessionMessages-mapped.
`prevMessageCount` must be expressed in NATIVE array indices. Verify against
`buildNativeMessages` behavior (system replace vs insert) and add a unit test.

Wait-state notes for the implementer:
- `revision == kvTranscriptRevision` must ALSO accept the very first tracked turn: on a fresh
  session `kvContinuationValid = false`, so the expression short-circuits — fine.
- Equality of `AgentLoopMessage` should compare id + role + content. If the data class already
  has structural equality, `==` on the lists is sufficient.

AFTER a successful generation:

```kotlin
kvTranscript = sessionMessages          // what we sent this turn
kvSystemPrompt = systemPrompt
kvTranscriptRevision = revision
kvContinuationValid = true
```

On ANY of the following, set `kvContinuationValid = false`:
- `ensureModelLoaded` reload path (signature change / engine reinit)
- `clearConversation`, `closeSession`, `setHistory` from the legacy path
- Rust compaction boundary (see 2.5)
- generation error / exception

### 2.4 Sidecars move out of the system prompt

Currently (~line 256): `mergedSystemPrompt = "$systemPrompt\n\n$contextSidecars"`. Sidecars
change every model request (Rust ships ledger/tool-index DELTAS by design — see
`sidecar_builder.build_ledger_delta` in `third_party/agent_core/src/engine/mod.rs`), so merging
them into the system prompt would force a continuation reset every turn.

Change for the llama path:

- `turnOptions.systemPrompt` = the STABLE `systemPrompt` argument only. Never merge sidecars.
- If `contextSidecars` is non-blank, inject it into the content of the **newest** message(s)
  only — i.e., messages at native index >= prevMessageCount. Concretely: prepend to the LAST
  user-role message in `domainHistory`:
  `content = "[context]\n$contextSidecars\n[/context]\n\n$originalContent"`.
- IMPORTANT: do the injection on the `domainHistory` copy passed to the engine, NOT on
  `kvTranscript` (continuation comparison must use the RAW `sessionMessages` from Rust).
- Because injected sidecars live inside that turn's delta, they are evaluated once and stay in
  the KV like reasoning tokens. Next turn's `prefix_str`/`full_str` are both rendered from RAW
  messages, so the substring math stays consistent (the JNI never re-renders the injected text).
  — Wait: this is wrong as stated. The JNI renders prefix/full from the messages Kotlin SENDS,
  which includes the injection. Correct rule: Kotlin must send the PREVIOUSLY-INJECTED content
  for old messages byte-identically. Simplest correct implementation: persist the injected
  content — store `kvTranscript` as the messages AS SENT (post-injection), and build each turn's
  native message list as `kvTranscriptAsSent + newMessages(with this turn's injection)`. Then
  prefix rendering is byte-stable by construction. Implement it this way.

### 2.5 Compaction / revision invalidation

- `RustLoopInferenceService` (~line 312) calls `inner.clearAppendOnlyOnRustCompactionBoundary()`
  on `AgentLoopEvent.CompactionBoundaryApplied`. Check whether the llama inner service
  implements this (Anthropic/Google impls exist — see `AnthropicInferenceServiceImpl` ~line 126).
  Implement it for the llama session: `kvContinuationValid = false`. Do NOT clear the KV
  eagerly; the next `completeSingleTurn` will do a full rebuild because `continuation = false`.
- `transcriptRevision` mismatch (handled by the 2.3 check) is the belt-and-braces signal; the
  boundary callback is primary.
- `AgentLoopEvent.KvCacheInvalidated` (~line 306) currently only logs. Route it to the same
  invalidation (`kvContinuationValid = false`) for the llama path.

### 2.6 Telemetry

In `completeSingleTurn`, with delta mode the JNI now reports:
- `buffered.promptTokens` = delta tokens evaluated this request
- `buffered.prefixReusedTokens` = KV position before the delta (all reused context)

Change the telemetry mapping:

```kotlin
inputTokens = (buffered.prefixReusedTokens ?: 0) + (buffered.promptTokens ?: 0),
cacheReadInputTokens = buffered.prefixReusedTokens,
```

and DELETE the `kvContextBefore` pre-read (`sessionManager.readContextUsage()` before
generation) — it reported the previous turn's end-state and is superseded by
`prefixReusedTokens`. Keep `readContextUsage()`/`contextUsage()` APIs (used elsewhere).

Update `LlamaInferenceServiceImplTelemetryTest`:
- remove the `coEvery { sessionManager.readContextUsage() } returns 250` mock
- with mocked `promptTokens = 100`, `prefixReusedTokens = 80`: expect `inputTokens = 180`,
  `cacheReadInputTokens = 80`, cache hit rate = 80/180 = 44.4%.

---

## Part 3 — Tests

1. **Kotlin unit tests** (`:feature:inference`):
   - Continuation decision: extension of previous transcript + same revision + same system
     prompt → `continuation = true`, correct `prevMessageCount` (native indexing incl. system
     message).
   - Reset on: revision bump, system prompt change, non-extension transcript (rewrite),
     compaction boundary callback, engine reload.
   - Sidecar injection: injected only into new messages; `kvTranscript` stores as-sent content;
     second turn renders byte-stable prefix.
   - Telemetry mapping as in 2.6.
2. **JNI**: no host-side unit harness — validate on device (Part 4). Keep changes guarded so
   `continuation = false` is byte-identical to current behavior.
3. Run existing suites: `:feature:inference` unit tests, `:core:agent` JVM contract tests
   (needs fresh desktop `cargo build` in `third_party/agent_core` first — stale `target/debug`
   .so causes `UnsatisfiedLinkError`).

## Part 4 — Device acceptance criteria

Run a 6+ turn conversation on MiniCPM-V 4.6 or Qwen3.5-2B (16k context), watch
`adb logcat -s llama-jni`:

- Turn 1: full eval (`Prompt tokens: N (evaluating from token 0 at pos 0)`).
- Turns 2+: `Delta eval: <small N> new tokens at pos <growing>`; NO `diverged`, NO
  `Falling back to full KV rebuild`, NO `KV trim ... failed`.
- Chat stats bottom sheet: input tokens grow monotonically turn over turn; cache-read tokens ≈
  previous turn's total; growth continues to ~60% of context window, then ONE Rust compaction
  (`pre_compact trigger=PREFLIGHT`), one full rebuild, then delta mode resumes.
- Prefill latency for turns 2+ drops sharply (only new tokens evaluated).

## Out of scope

- Multimodal turns (always full rebuild).
- The legacy non-agent `sendPrompt` flow (keeps current prefix-match behavior).
- Rust `agent_core` changes (already done separately: FixedKv periodic compaction disabled).
- Tuning `FixedKv.threshold_ratio` (0.60) — possible follow-up after this lands.

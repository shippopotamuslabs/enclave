# Research: sidecar-anthropic-google-embed

Objective codebase map of the CURRENT state of the Anthropic and Google
sidecar wire paths, and of the shared primitives they must adopt, for a later
Design phase. This is the follow-up slice to `sidecar-user-embed` (which
deferred Anthropic/Google per design decision D4). No design or
recommendations below — only what exists.

All paths are relative to `/home/sean/Code/pocket-crew`. Verified facts are
cited as `path:line`; inferences are marked **(inference)**.

## Research Questions

Derived from the categories of unknown (current state of the two deferred wire
paths + the shared primitives), not from the proposed solution.

1. Exactly how do Anthropic and Google place sidecars on the wire today —
   prepend vs suffix, which helper, before/after content, join separator,
   delimiter or none?
2. How do Anthropic and Google consume the result of
   `ProviderAppendOnlyContext.resolveContextSidecarsForWire` now that it returns
   `ResolvedSidecar?` — do they keep or discard `markerId`?
3. How and where do Anthropic and Google freeze sidecars
   (`endTurnFreezeSidecar`), and what `markerId` do they pass — is live→frozen
   continuity (D7) honored, or is a throwaway marker minted at freeze?
4. What is the current shape, contract, and visibility of the shared primitives
   the OpenAI path uses (`SidecarMarker`, `ResolvedSidecar`,
   `SidecarMarkerFactory`, `wireBlock()`, `SidecarMarkerRegex`,
   `SidecarUserContentEmbedder.embed` / `stripSidecarBlocks`,
   `FrozenSidecarSnapshot`, `ProviderAppendOnlyContext`)? Are they reachable
   from the Anthropic/Google code?
5. How exactly does the OpenAI chat-completions path now use the embedder
   (`OpenAiCompatibleVolatilePlacement.embedSidecarsIntoUserMessages` and the
   `BaseOpenAiSdkInferenceService.completeSingleTurn` wire-assembly + freeze
   call site)? This is the reference to mirror.
6. What is `SessionIndexVolatilePlacement` used for today, by whom, and what
   does `sidecarTextsBeforeSessionIndex` return?
7. How does Anthropic prompt caching work today
   (`AnthropicPromptCacheSupport`): what is `stableHistoryTailIndex`, where are
   `cache_control` / `ephemeralCacheControl` breakpoints attached, and how does
   the current sidecar PREPEND position relate to those breakpoints (i.e. what
   would change if sidecar bytes moved from the front to the tail of a user
   message)?
8. Does the Google path have an equivalent cache-breakpoint mechanism, and how
   does `GoogleAgentLoopContentMapper.mapMessage(..., prependedText = ...)`
   consume the prepended text? What is the content shape for user messages
   (text + images)?
9. How are image-bearing user messages built on the Anthropic and Google paths
   (content-block / parts structure), so a later suffix-embed can target the
   text block/part without disturbing images (cf. F15)?
10. Is `[Metadata:]` present on the content that reaches the Anthropic/Google
    mappers — i.e. is the metadata suffix already on `msg.content` at the point
    sidecars are placed, so an embedder could append after it (INV-2)?
11. How is pre-retrieval sidecar (`PreRetrievalContextSidecar` /
    `volatileTurnContext`) handled on the Anthropic/Google paths today (both
    currently pass `preRetrievalSidecarText = null` — confirm and trace whether
    any pre-retrieval context can reach these providers)?
12. What unit tests cover the Anthropic and Google sidecar placement / freeze /
    cache-breakpoint behavior today? Names, paths, framework, run commands, and
    which assertions encode the current prepend shape? Also any tests on
    `SessionIndexVolatilePlacement`.
13. Confirm the LiteRT path (`LlamaInferenceServiceImpl`) is independent of the
    Anthropic/Google code and out of scope.
14. Are there any other callers of `SessionIndexVolatilePlacement`,
    `sidecarTextsBeforeSessionIndex`, or the throwaway-marker freeze pattern
    besides Anthropic/Google?

(Auxiliary, verified incidentally: whether the INV-7 model rule is already in
the shared system prompt for Anthropic/Google — answered in F15.)

## Findings

### F1. Anthropic places sidecars as a raw-text PREPEND, before content, `\n\n`-joined, no delimiter

`feature/inference/.../AnthropicInferenceServiceImpl.kt`

- The live sidecar anchor is `liveSidecarInsertIndex = lastUserIndex.coerceAtLeast(0)`
  (`AnthropicInferenceServiceImpl.kt:229`), where `lastUserIndex` is the last
  `Role.USER` message whose metadata has no `call_id`
  (`:167-176`).
- `prependIndexSidecars(index, baseContent)` (`:232-247`) calls
  `SessionIndexVolatilePlacement.sidecarTextsBeforeSessionIndex(...)` and, when
  the returned list is non-empty, returns
  `texts.joinToString(separator = "\n\n") + "\n\n" + baseContent`
  (`:245-246`). This is a **prepend**: sidecar text(s) go **before** the
  user-visible content, joined by `\n\n`, with **no `[Sidecar:]` delimiter**.
- The prepend is computed once per message as
  `val messageContent = prependIndexSidecars(index, msg.content)`
  (`:260`) and used in every non-engine-internal branch: the plain USER text
  block (`:285`), the image-bearing USER text block (`:320`), the
  `compact-summary-` assistant block (`:338`), the tool-result block
  (`:353`), and the assistant-text block (`:372`).
- Engine-internal messages are mapped from `msg.content` directly via
  `EngineInternalMessageMapper.formatForProviderWire` and return early before
  the role `when` (`:262-278`), so they do **not** receive a prepended sidecar.

Because `sidecarTextsBeforeSessionIndex` only adds the live text when
`sessionIndex == tailInsertIndex` (= `liveSidecarInsertIndex`) and frozen texts
when `mappedInsertIndex == sessionIndex` (F6), in practice the prepend lands on
user messages at the resolved user anchor index.

### F2. Google places sidecars as a raw-text PREPEND via `prependedText`, before content, `\n\n`-joined, no delimiter

`feature/inference/.../GoogleInferenceServiceImpl.kt`

- `liveSidecarInsertIndex = lastUserIndex.coerceAtLeast(0)` (`:154`), where
  `lastUserIndex = GoogleAgentLoopContentMapper.lastNonToolResultUserIndex(sessionMessages)`
  (`:129`).
- `sidecarPrependTextForIndex(index)` (`:157-167`) calls
  `SessionIndexVolatilePlacement.sidecarTextsBeforeSessionIndex(...)`,
  `joinToString(separator = "\n\n")`, and returns it via `takeIf { it.isNotBlank() }`
  (i.e. `null` when empty). Raw texts, `\n\n`-joined, no delimiter.
- `mapAtIndex(message, index, attachImages)` passes that string as
  `prependedText = sidecarPrependTextForIndex(index)` into
  `GoogleAgentLoopContentMapper.mapMessage(...)` (`:169-180`).
- The mapper applies the prepend in
  `GoogleAgentLoopContentMapper.kt:59-64`:
  `contentText = if (!prependedText.isNullOrBlank()) "$prependedText\n\n${message.content}" else message.content`
  — again a **prepend** before content, `\n\n` separator, no delimiter. Used
  for USER (`:68`) and ASSISTANT (`:69`) rows; engine-internal rows return
  their own wire text before the prepend (`:47-57`).

### F3. Both providers DISCARD `markerId` from `resolveContextSidecarsForWire`

- Anthropic: `val wireSidecar = appendOnly.resolveContextSidecarsForWire(contextSidecars)?.text`
  (`AnthropicInferenceServiceImpl.kt:230`) — `?.text` drops the
  `ResolvedSidecar.markerId`; `wireSidecar` is a `String?`.
- Google: `val wireSidecar = appendOnly.resolveContextSidecarsForWire(contextSidecars)?.text`
  (`GoogleInferenceServiceImpl.kt:155`) — same `?.text`, `String?`.

The `ResolvedSidecar` now returned by `resolveContextSidecarsForWire`
(`ProviderAppendOnlyContext.kt:96-106`) carries a `markerId` that both
providers throw away. The markerId has **zero wire effect** on these paths
today because the prepend renders raw `.text` only (F1, F2) — the
`[Sidecar-<uuid>:]` wrapper is never applied.

### F4. Both providers MINT A THROWAWAY MARKER at freeze (breaks D7 live→frozen continuity)

- Anthropic freeze call site: `freezeContextSidecarIfPresent(appendOnly, contextSidecars = wireSidecar, tailInsertIndex = liveSidecarInsertIndex)`
  (`AnthropicInferenceServiceImpl.kt:484-488`), implemented at
  `:555-567`, which calls
  `appendOnly.endTurnFreezeSidecar(sidecarText = contextSidecars, markerId = SidecarMarkerFactory.newMarker(contextSidecars).markerId, mappedInsertIndex = tailInsertIndex)`
  — a **fresh UUID minted at freeze time** (`:563`).
- Google freeze call site: `if (wireSidecar != null) { appendOnly.endTurnFreezeSidecar(sidecarText = wireSidecar, markerId = SidecarMarkerFactory.newMarker(wireSidecar).markerId, mappedInsertIndex = liveSidecarInsertIndex) }`
  (`GoogleInferenceServiceImpl.kt:324-330`) — **fresh UUID minted at freeze
  time** (`:327`).

This is the D7 gap. `resolveContextSidecarsForWire` already mints a marker on
the non-deduped path and stores it in `pendingLiveMarkerId`
(`ProviderAppendOnlyContext.kt:102-105`), and `endTurnFreezeSidecar` will reuse
`pendingLiveMarkerId` when passed a blank `markerId`
(`ProviderAppendOnlyContext.kt:142-146`). Both providers bypass that path by
passing a freshly-minted (non-blank) markerId, so the live-ship marker is
discarded and a new one is frozen. Because the wire prepend ignores markerId
entirely (F3), this currently has no visible byte effect — but it means the
frozen `markerId` is unrelated to any live ship, so a suffix migration that
renders `[Sidecar-<markerId>:]` would not be byte-stable across the live→frozen
transition unless the freeze is rewired to thread the resolved markerId. The
OpenAI path threads it correctly (F5).

### F5. Shared primitives — shape, contract, visibility, reachability

All in package `com.browntowndev.enclave.feature.inference.appendonly`. The
Anthropic/Google services are in `com.browntowndev.enclave.feature.inference`
(same Gradle module `:feature:inference`), so `internal` members are reachable;
both services already import `SessionIndexVolatilePlacement`,
`SidecarMarkerFactory`, and `ProviderAppendOnlyContext`
(`AnthropicInferenceServiceImpl.kt:35-39`, `GoogleInferenceServiceImpl.kt:20-25`).

**`SidecarMarker.kt`** (top-level declarations are **public** unless noted):
- `data class SidecarMarker(val markerId: String, val body: String)` (`:10`).
- `data class ResolvedSidecar(val text: String, val markerId: String)` (`:17`).
- `object SidecarMarkerFactory { fun newMarker(body: String): SidecarMarker }`
  — UUID via `java.util.UUID.randomUUID().toString()` (`:19-22`).
- `fun SidecarMarker.wireBlock(): String = "[Sidecar-$markerId: $body]"` (`:24`).
- `object SidecarMarkerRegex { val SPAN: Regex }` = `Regex("\\[Sidecar-($UUID_PATTERN):")`
  (`:26-33`), UUID pattern `[0-9a-fA-F]{8}-...-[0-9a-fA-F]{12}` (`:75-76`).
- `internal data class SidecarMarkerSpan` (`:35-39`),
  `internal fun findNextSidecarMarkerSpan` (`:41-52`),
  `internal fun findSidecarBlockEnd` (bracket-depth aware) (`:59-73`).

**`SidecarUserContentEmbedder.kt`** (public `object`, `:18`):
- `fun embed(baseContent: String, markers: List<SidecarMarker>): String`
  (`:19-27`) — returns `baseContent` unchanged if `markers` empty; otherwise
  appends `"\n\n" + marker.wireBlock()` per marker, in order. It does **not**
  inspect or reorder any existing `[Metadata:]` suffix; it simply appends after
  whatever `baseContent` already ends with (INV-2 ordering is satisfied as long
  as `[Metadata:]` is already the tail of `baseContent`).
- `fun stripSidecarBlocks(content: String): String` (`:35-55`) — removes every
  `[Sidecar-<uuid>: ...]` block matched by `SidecarMarkerRegex.SPAN`, trims the
  preceding `\n\n`, leaves `[Metadata:]` and user-typed `[Sidecar:` literals
  intact.

**`FrozenSidecarSnapshot.kt`**:
- `internal data class FrozenSidecarSnapshot(val text: String, val markerId: String, val mappedInsertIndex: Int)`
  (`:8-12`). Stores the **raw sidecar body** in `text` and a `markerId`
  (already migrated by the OpenAI slice). In-memory, session-scoped, not
  persisted.

**`ProviderAppendOnlyContext.kt`** (`internal class`):
- `fun resolveContextSidecarsForWire(built: String?): ResolvedSidecar?`
  (`:96-106`): trims; returns `null` if blank; returns `null` and clears
  `pendingLiveMarkerId` if `trimmed == lastShippedSidecarText` (dedupe gate,
  text-only); otherwise sets `lastShippedSidecarText`, mints a marker, stores
  its id in `pendingLiveMarkerId`, and returns `ResolvedSidecar(trimmed, markerId)`.
- `fun endTurnFreezeSidecar(sidecarText: String, markerId: String, mappedInsertIndex: Int)`
  (`:136-155`): resolves `markerId.takeIf { it.isNotBlank() } ?: pendingLiveMarkerId ?: newMarker(...)`,
  clears `pendingLiveMarkerId`, appends a `FrozenSidecarSnapshot`, sets
  `lastShippedSidecarText = sidecarText.trim()`.
- `fun appendVolatileSidecar(sidecarText: String, markerId: String, delivery: VolatileContextDelivery)`
  (`:115-134`).
- `fun frozenSidecarsForWireReplay(): List<FrozenSidecarSnapshot>` (`:94`);
  `fun committedVolatileSidecarTexts(): List<String>` (`:92`);
  `fun hasFrozenPreRetrievalSidecar(): Boolean` (`:108`).
- Clear conditions wipe `frozenSidecarSnapshots` + reset `lastShippedSidecarText`
  + `pendingLiveMarkerId` (`clearLogState`, `:180-195`; triggers
  `rust_compaction_boundary` `:67-72`, `digest_mismatch` `:78-80`,
  `compaction_shorter_array` `:81-83`, `invalidate()` `:159-164`).

The shared markerId machinery and the dedupe gate are **provider-agnostic and
already migrated**; the gap is purely that Anthropic/Google do not USE the
threaded markerId or the embedder (F1–F4).

### F6. `SessionIndexVolatilePlacement` — purpose, callers, return shape

`feature/inference/.../appendonly/SessionIndexVolatilePlacement.kt`
(`internal object`):

- `sidecarTextsBeforeSessionIndex(sessionIndex, frozenSidecars, preRetrievalSidecarText, preRetrievalInsertIndex, currentSidecarText, tailInsertIndex): List<String>`
  (`:33-55`) returns an ordered list of **raw sidecar texts** to apply at a
  given session index: frozen snapshots whose `mappedInsertIndex == sessionIndex`
  (in order, `:42-44`), then `preRetrievalSidecarText` if its index matches
  (`:45-51`), then `currentSidecarText` if `sessionIndex == tailInsertIndex`
  (`:52-54`). It returns **`List<String>` — `markerId` is discarded** (frozen
  snapshots' `.text` only, `:43`).
- `resolvePreRetrievalInsertIndex(sessionMessages): Int?` (`:18-24`) and
  `resolveTailInsertIndex(sessionMessages): Int` (`:26-31`) operate on raw
  session indices.
- It is the index-based analog of `OpenAiCompatibleVolatilePlacement` for
  providers that map `AgentLoopMessage` rows directly (Anthropic, Google) — see
  the file KDoc (`:13-16`).

**Callers (production):** only `AnthropicInferenceServiceImpl.kt:237` and
`GoogleInferenceServiceImpl.kt:158` (plus the file itself). No other production
callers; no direct unit test (see F12, F14).

### F7. Anthropic prompt caching — `stableHistoryTailIndex` and `cache_control` breakpoints

`feature/inference/.../AnthropicPromptCacheSupport.kt`:

- `supportsPromptCaching(modelId)` delegates to
  `ApiProviderModelPolicy.supportsAnthropicPromptCaching` (`:8`).
- `ephemeralCacheControl(): CacheControlEphemeral` (`:10`).
- `stableHistoryTailIndex(sessionMessages): Int?` = `sessionMessages.size - 2`
  when `size >= 2`, else `null` (`:16-21`). KDoc: "Index of the last message in
  the stable history prefix (excludes the live turn). Cache breakpoint #3
  belongs here, not on the live user message that carries volatile sidecars."
  (`:12-14`).
- `shouldAttachMessageCacheBreakpoint(messageIndex, stableTailIndex): Boolean`
  = `stableTailIndex != null && messageIndex == stableTailIndex` (`:23-26`).

How `AnthropicInferenceServiceImpl` uses it (`completeSingleTurn`):
- `useCaching = supportsPromptCaching(...)` (`:207`),
  `stableTailIndex = stableHistoryTailIndex(sessionMessages)` (`:208`).
- The single system block gets `cacheControl(ephemeralCacheControl())` when
  `useCaching` (`:215-221`).
- Per message, `attachCacheBreakpoint = useCaching && shouldAttachMessageCacheBreakpoint(index, stableTailIndex)`
  (`:256-258`). The `cache_control` is attached to the **message's content
  block** — the USER text block (`:286-288`, `:321-323`), the compact-summary
  block (`:339-341`), the tool-result block (`:354-356`), or the last assistant
  text/tool-use block (`:373-379`, `:399-402`).
- The last tool definition also gets `cache_control` when `useCaching` (`:421-431`).

Relationship to sidecar PREPEND position (verified facts):
- The sidecar is prepended **into `msg.content`** (`messageContent`,
  `:260`) **before** the text block is built, so the sidecar bytes are part of
  the same `TextBlockParam` that carries (or does not carry) the breakpoint.
- The live sidecar lands at `liveSidecarInsertIndex = lastUserIndex` (`:229`).
  In a normal live turn the last message is the live user message, so
  `lastUserIndex = size - 1`, while `stableTailIndex = size - 2`; therefore the
  live-sidecar message has **no** breakpoint (it is in the uncached tail),
  matching the `AnthropicPromptCacheSupport` KDoc intent.
- **(inference)** `cache_control` is a block-level marker (it caches everything
  up to and including the block it is attached to). Moving a sidecar from the
  **front** to the **tail** of a given user message's single text block does
  not move it across the cache boundary — the entire block sits on one side of
  the breakpoint regardless of intra-block position. What matters for prefix
  stability is byte-stable frozen replay (the markerId-continuity gap, F4); the
  prefix→suffix change is a one-time byte change on the migration turn for any
  message in the cached region, stable thereafter. This is inference about
  Anthropic cache semantics, not a repo-verified claim.
- Edge case in the existing test (F12): when a turn's array already contains the
  assistant reply (`[u1,a1,u2,a2]`), `lastUserIndex = 2` and
  `stableTailIndex = size-2 = 2` **coincide**, so the live-sidecar-bearing user
  message also carries the breakpoint. This is a test-data artifact (a2 present
  pre-call); in a real live turn the two indices differ by one.

### F8. Google cache mechanism is server-side `CachedContent`, not per-message breakpoints; mapper consumes `prependedText` as a prefix

Google has **no** `cache_control`-style per-message breakpoint. It uses
server-side cached content via `GooglePromptCacheSession`
(`feature/inference/.../GooglePromptCacheSession.kt`):

- `ensureCacheForStablePrefix(systemPrompt, stableHistory, availableTools, mapToContent)`
  (`:48-130`) maps the stable history (everything except the last message,
  `GoogleInferenceServiceImpl.kt:132`) to `Content`, estimates tokens, and if
  above a model threshold (`FLASH_CACHE_THRESHOLD_TOKENS=1024` /
  `PRO_CACHE_THRESHOLD_TOKENS=4096`, `:20-22`, `:139-146`) creates/reuses a
  server cache (`sdkClient.createCachedContent`, `:109-118`) keyed by a
  `CacheSignature` of system prompt + per-message `(id, role, content,
  metadataJson)` + tool names (`:156-196`).
- The active cache name is attached via `configBuilder.cachedContent(activeCacheName)`
  (`GoogleInferenceServiceImpl.kt:233-234`); only the delta history beyond the
  cached prefix is sent (`deltaHistory`, `:239`, `GooglePromptCacheSession.kt:132-137`).
- Crucially, the **same `mapAtIndex` (with sidecar prepend) is used both for the
  cached stable-prefix content** (`mapToContent = { ... mapAtIndex(msg, index, attachImages=false) }`,
  `GoogleInferenceServiceImpl.kt:200-203`) **and for the delta/live content**
  (`:242-256`). So a frozen sidecar prepended into a stable-history message
  becomes part of the cached content, and its bytes (and the `CacheSignature`
  derived from `message.content`) must be stable for the cache to be reused.
  **(inference)** Note the `CacheSignature` hashes `message.content` (the raw
  `AgentLoopMessage.content`, `GooglePromptCacheSession.kt:181-196`), **not**
  the mapped content with the prepend — so the signature is computed on
  pre-sidecar content; the sidecar prepend affects the *mapped* `Content` that
  is sent/cached but not the reuse signature.

`GoogleAgentLoopContentMapper.mapMessage(message, index, lastUserIndex, imageUris, prependedText)`
(`GoogleAgentLoopContentMapper.kt:33-71`):
- Builds `contentText = "$prependedText\n\n${message.content}"` when
  `prependedText` is non-blank (`:59-64`).
- Routes to `mapUserMessage` (`:68`) or `mapAssistantMessage` (`:69`).

### F9. Image-bearing user messages — content-block / parts structure (Anthropic and Google)

**Anthropic** (`AnthropicInferenceServiceImpl.kt:282-335`):
- `attachImages = index == lastUserIndex && imageUris.isNotEmpty()` (`:283`).
- Without images: a single `TextBlockParam` built from `messageContent`
  (sidecar-prepended), optionally with `cache_control` (`:284-295`).
- With images: image `ContentBlockParam.ofImage(...)` blocks are added
  **first** (`:297-319`), then a single `TextBlockParam.ofText(messageContent)`
  is added **last** (`:320-326`). So the order is `[image, …, text]`; the
  sidecar lives inside that single text block.

**Google** (`GoogleAgentLoopContentMapper.mapUserMessage`, `:73-96`):
- Without images: one `Part.fromText(stripBase64DataUris(contentText))` (`:82`).
- With images: `Part.fromText(...)` **first**, then `Part.fromBytes(...)` image
  parts (`:84-89`). So order is `[text, image, …]`; the sidecar prepend lives
  inside the leading text part.

In both providers the user-visible text is one text block/part with the sidecar
currently **prefixed** into it; images are separate blocks/parts.

### F10. Sidecars are placed on `msg.content` after metadata is already the tail of that content (INV-2 setup)

The user prompt is metadata-augmented upstream and that content flows into
`AgentLoopMessage.content` reaching `completeSingleTurn`:

- `ChatInferenceRequestPreparer.promptWithTemporalMetadata(prompt)` returns
  `"$prompt\n\n[Metadata: Sent at $formattedTime]"`
  (`core/domain/.../ChatInferenceRequestPreparer.kt:485-488`), applied at
  `:409` before `prepareChatPrompt`.
- `RustLoopInferenceService.buildUserContent(prompt, options)`
  (`feature/inference/.../RustLoopInferenceService.kt:1006-1018`) delegates to
  `InferencePromptContentBuilder.buildUserContent(prompt, volatileTurnContext)`,
  which builds `"{volatileTurnContext}\n\n{prompt}"` — i.e. the volatile/RAG
  context is a **prefix** and the `[Metadata:]` suffix stays at the **tail**
  (`core/domain/.../InferencePromptContentBuilder.kt:4-15`).
- This `userContent` becomes the live `ChatMessage(Role.USER, userContent)`
  fed into the agent loop (`RustLoopInferenceService.kt:171-172`) and is
  reflected back as the live `AgentLoopMessage` passed to the provider's
  `completeSingleTurn`.

**(inference)** Therefore the live user message's `AgentLoopMessage.content`
reaching the Anthropic/Google mappers ends with `[Metadata: Sent at ...]`, and
historical user messages carry their own committed metadata (committed at
`RustLoopInferenceService.kt:392-394`). The bytes are not re-traced through the
Rust transcript reflection step in this phase, but `buildUserContent` is the
only constructor of the live user content and it preserves the metadata tail.
The current PREPEND therefore places the sidecar **before** `[Metadata:]`
(before the whole content), which is the INV-2 ordering inversion this ticket
targets. **Note:** the existing Anthropic/Google unit tests use plain content
("Hello Turn 1") with **no** `[Metadata:]` (F12), so no current test exercises
the metadata-before-sidecar ordering.

### F11. Pre-retrieval / volatile-turn context is MERGED upstream on Anthropic/Google, never delivered as a separate sidecar

- Both providers pass `preRetrievalSidecarText = null` and
  `preRetrievalInsertIndex = null` into `sidecarTextsBeforeSessionIndex`
  (`AnthropicInferenceServiceImpl.kt:240-241`,
  `GoogleInferenceServiceImpl.kt:162-163`). `PreRetrievalContextSidecar` is
  never invoked on these paths.
- The reason: `RustLoopInferenceService.buildUserContent` only suppresses
  merging volatile context when `deliverPreRetrievalAsOpenAiSidecar()` is true,
  which is true **only** for `OPENAI`, `XAI`, `OPENROUTER`
  (`RustLoopInferenceService.kt:1012-1026`). For Anthropic/Google (and LLAMA)
  it is false, so `volatileTurnContext` is **merged into the user content**
  upstream (F10) rather than carried as a `[Sidecar:]`/prepend.
- `PreRetrievalContextSidecar` (`feature/inference/.../openai/PreRetrievalContextSidecar.kt`)
  is `internal` and used only on the OpenAI path
  (`BaseOpenAiSdkInferenceService.kt:573-604`) plus
  `ProviderAppendOnlyContext.hasFrozenPreRetrievalSidecar` (`:108`); it does
  not appear in Anthropic/Google code.
- Confirmed by tests: `AnthropicVolatileTurnContextCommitTest`
  (CSPC-ANTHRO-03a `:34-150`, CSPC-ANTHRO-05 `:157-240`) and
  `GoogleVolatileTurnContextCommitTest` (CSPC-GOOGLE-03a `:29-109`,
  CSPC-GOOGLE-05 `:116-183`) pass `volatileTurnContext` while the message
  content already contains the merged prefix, and assert **no double-prepend**
  (`contextSidecars = null` throughout). So pre-retrieval context reaches these
  providers only as part of `msg.content`, not as a sidecar.

### F12. Test coverage for Anthropic/Google sidecar placement, freeze, and cache

Framework: JUnit 5 (`org.junit.jupiter.api.Test`), `io.mockk`,
`kotlinx.coroutines.test.runTest` (e.g.
`AnthropicInferenceServiceImplCachePrefixTest.kt:15-23`).

**Primary encoder of the Anthropic prepend wire shape — must be rewritten:**
`feature/inference/src/test/.../AnthropicInferenceServiceImplCachePrefixTest.kt`
(single test, `:28-250`). It calls `completeSingleTurn` twice with
`contextSidecars = "Volatile Sidecar 1"` / `"Volatile Sidecar 2"` and asserts:
- Turn 1 U1 content == `"Volatile Sidecar 1\n\nHello Turn 1"` (prepend, `:129-137`).
- Turn 2 U1 frozen replay == `"Volatile Sidecar 1\n\nHello Turn 1"` (`:172-180`).
- Turn 2 U2 live == `"Volatile Sidecar 2\n\nHello Turn 2"` (`:190-198`).
- Cache breakpoints: system block has `cache_control` both turns (`:106-121`);
  Turn 1 U1 (index 0 = stableTailIndex) has a breakpoint, A1 does not
  (`:148-168`); Turn 2 U1/A1 no breakpoint, U2 (index 2 = stableTailIndex) has
  one, A2 does not (`:209-249`). These assertions encode both the prepend byte
  shape and the `stableHistoryTailIndex = size-2` breakpoint placement.

**Freeze-count / clear coverage (provider-agnostic, not wire byte shape):**
`AppendOnlyProviderWiringTest.kt` — Anthropic (`:24-64`) and Google (`:66-97`)
each call `completeSingleTurn` with `contextSidecars = "State ledger JSON: {}"`;
Anthropic asserts `committedVolatileSidecarTexts().size == 1` then `== 0` after
`clearAppendOnlyOnRustCompactionBoundary()` (`:59-63`); Google only asserts the
append-only context is assigned (`:96`).

**Pre-retrieval (no double-prepend), `contextSidecars = null`:**
`AnthropicVolatileTurnContextCommitTest.kt` (`:34-240`),
`GoogleVolatileTurnContextCommitTest.kt` (`:29-183`) — see F11.

**Anthropic cache support:** `AnthropicPromptCacheSupportTest.kt` (`:8-16`)
covers **only** `supportsPromptCaching(...)`. `stableHistoryTailIndex` and
`shouldAttachMessageCacheBreakpoint` have **no direct unit test** — they are
exercised only indirectly via `AnthropicInferenceServiceImplCachePrefixTest`.

**Google cache:** `GoogleInferenceServiceImplCachePrefixTest.kt` (`:24-181`)
covers server-side cache create/reuse/delete and delta-history sizing with
`contextSidecars = null`; `GooglePromptCacheSessionTest.kt` and
`GooglePromptCacheTokenEstimatorTest.kt` cover the session/estimator.

**Shared primitive (already migrated, provider-agnostic):**
`appendonly/ProviderAppendOnlyContextTest.kt` asserts markerId carry, fresh
markerId on resolve, dedupe allocates none, and live→frozen markerId continuity
(`S-FREEZE-01..05`, e.g. `:362-397`), plus `appendVolatileSidecar(markerId=...)`
(`:35-42`). `appendonly/SidecarUserContentEmbedderTest.kt` and
`SidecarUserEmbedWireTest.kt` / `SidecarResponsesEmbedTest.kt` /
`telemetry/SidecarDiagnosticsReclassificationTest.kt` cover the OpenAI-side
embedder, wire, and diagnostics.

**Coverage gaps relevant to this ticket:**
- **No test asserts the Google sidecar wire byte shape.**
  `GoogleInferenceServiceImplTest.kt` uses `contextSidecars = null` in all 6
  invocations (`:64, :119, :178, :216, :284, :381`); only
  `AppendOnlyProviderWiringTest` ships a non-null Google sidecar and it checks
  count, not bytes.
- No test exercises the `[Metadata:]`-before-sidecar ordering on
  Anthropic/Google (F10).
- No test asserts Anthropic/Google live→frozen markerId continuity (F4).

Run commands (AGENTS.md §4): `./gradlew :feature:inference:testDebugUnitTest`,
`./gradlew :core:domain:testDebugUnitTest`, `./gradlew testDebugUnitTest`,
`./gradlew ktlintCheck`, `./gradlew detekt`,
`./gradlew :feature:inference:assembleDebug`.

### F13. OpenAI reference path (mirror target)

`feature/inference/.../appendonly/OpenAiCompatibleVolatilePlacement.kt`:
- `embedSidecarsIntoUserMessages(mappedBody, frozenSidecars, preRetrievalSidecar, preRetrievalInsertIndex, liveSidecar, tailInsertIndex): List<ChatCompletionMessageParam>`
  (`:26-83`) — finds real user indices (`:37-42`), re-anchors each marker's
  target index to the last user message at-or-before it (`findUserAnchorIndex`,
  `:45-48`), groups markers by user index (frozen `:57-60`, pre-retrieval
  `:63-66`, live `:69-72`), and rebuilds each affected user param via
  `embedMarkersIntoUserParam`. Returns a **same-length** body — no system
  messages inserted.
- `embedMarkersIntoUserParam` (`:103-138`): text content →
  `SidecarUserContentEmbedder.embed(baseText, markers)` (`:109-115`);
  image content → append the embedded text to the **first text part**, leaving
  image parts intact (`:116-134`).

`feature/inference/.../BaseOpenAiSdkInferenceService.kt`:
- `val wireSidecar = appendOnly.resolveContextSidecarsForWire(contextSidecars)`
  (`:537`) — keeps the full `ResolvedSidecar` (text **and** markerId), unlike
  Anthropic/Google (F3).
- Wire assembly (`:605-618`): `buildList { stableSystemMessage?.let(::add); addAll(OpenAiCompatibleVolatilePlacement.embedSidecarsIntoUserMessages(... liveSidecar = wireSidecar ...)) }`.
- Freeze (`finalizeSidecarFreeze`, `:454-470`): when `wireSidecar != null`,
  `endTurnFreezeSidecar(sidecarText = wireSidecar.text, markerId = wireSidecar.markerId, mappedInsertIndex = tailInsertIndex)`
  — **threads the resolved markerId** (`:461-465`), satisfying D7 live→frozen
  continuity (the exact behavior Anthropic/Google lack, F4).
- Pre-retrieval is a `ResolvedSidecar` built from
  `PreRetrievalContextSidecar.format(...)` + `SidecarMarkerFactory.newMarker`
  and frozen immediately on first ship (`:573-603`).

### F14. LiteRT path is independent and out of scope (INV-9)

`feature/inference/.../LlamaInferenceServiceImpl.kt`:
- `injectSidecarIntoLastUserMessage(messages, contextSidecars)` (`:657-671`)
  rewrites the last `Role.USER` message to
  `"[context]\n$contextSidecars\n[/context]\n\n${message.content}"` — a
  `[context]...[/context]` **prefix** on every turn, with **no** freeze/dedupe
  (`:309`).
- A targeted search confirms `LlamaInferenceServiceImpl.kt` references **none**
  of `SidecarMarkerFactory`, `SidecarMarker`, `SidecarUserContentEmbedder`,
  `ProviderAppendOnlyContext`, `SessionIndexVolatilePlacement`, or
  `resolveContextSidecarsForWire` (no matches). It does not share the
  Anthropic/Google placement code. Out of scope.

### F15. Cross-cutting: only Anthropic/Google use the deferred pattern; the INV-7 model rule is already shared

- **Throwaway-marker freeze pattern** (`SidecarMarkerFactory.newMarker(x).markerId`
  passed straight into a freeze) appears only at
  `AnthropicInferenceServiceImpl.kt:563` and `GoogleInferenceServiceImpl.kt:327`.
  The other two `SidecarMarkerFactory.newMarker` uses are not throwaways:
  `BaseOpenAiSdkInferenceService.kt:579` builds the pre-retrieval
  `ResolvedSidecar` that is threaded through both embed and freeze, and
  `ProviderAppendOnlyContext.kt:103/:145` are the internal live-marker mint and
  the blank-markerId fallback.
- **`SessionIndexVolatilePlacement` / `sidecarTextsBeforeSessionIndex`** have no
  callers beyond Anthropic/Google and no direct unit test (F6, F12).
- **INV-7 rule already present for Anthropic/Google:**
  `core/domain/.../SearchToolPromptComposer.kt` already emits the UUID-aware
  `[Sidecar-<uuid>: ...]` host-context rule in both the compact one-liner
  (`:156-157`) and the full `# TEMPORAL CONTEXT:` block (`:166-169`). Anthropic
  and Google receive the same composed `systemPrompt` (passed into their
  `completeSingleTurn` and mapped to the system block —
  `AnthropicInferenceServiceImpl.kt:215-221`,
  `GoogleInferenceServiceImpl.kt:235-236`), so the model rule already reaches
  these providers; no new rule is required for this slice. **(inference,
  consistent with INV-7 "model rule already present in the system prompt".)**

## Open Unknowns

None block the Research phase. Items left for the Design phase (decisions, not
research gaps):

- Whether the Anthropic `cache_control` breakpoint needs any re-anchoring when
  the sidecar becomes a suffix. F7 establishes that breakpoints are block-level
  at `stableHistoryTailIndex = size-2` and that intra-block prefix→suffix
  movement is cache-neutral **(inference)**; confirming this against live
  Anthropic behavior is an emulator/validation question, not answerable from the
  repo.
- Whether Google's `CacheSignature` (computed on raw `message.content`, F8)
  interacts with the suffix change at all, given the signature ignores the
  sidecar-mapped content; verifying server-cache reuse behavior is an
  emulator/validation question.
- The exact bytes of historical user-message content as reflected back through
  the Rust transcript step (F10 traces the constructor `buildUserContent` and
  the commit, but the Rust round-trip reflection was not byte-traced this
  phase). This is an implementation detail for the Design/Plan phases, not a
  blocker.

## Self-Review

- All 14 research questions are answered with verified `path:line` citations;
  inferences are explicitly marked (F7 cache semantics, F8 CacheSignature note,
  F10 metadata-tail reflection, F15 INV-7 reach).
- Q1→F1/F2; Q2→F3; Q3→F4; Q4→F5; Q5→F13; Q6→F6; Q7→F7; Q8→F8; Q9→F9;
  Q10→F10; Q11→F11; Q12→F12; Q13→F14; Q14→F15.
- No design or recommendations: the open items are framed as Design-phase
  decisions/validation questions, not proposed solutions.
- Current line numbers were re-verified against the working tree during this
  phase (the ticket's approximate line hints were corrected, e.g. Anthropic
  freeze throwaway at `:563`, Google freeze throwaway at `:327`).
- No `TBD`/`TODO`/`similar to` placeholders.
- No production or test code was modified; only this `research.md` (and
  `status.md`) is written.

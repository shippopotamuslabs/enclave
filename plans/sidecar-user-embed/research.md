# Research: sidecar-user-embed

## Research Questions

Categories of unknown derived from the feature request (not from its proposed
solution):

1. How are sidecars inserted into the OpenAI-compatible wire body today —
   exact JSON shape, role used, and at which indices?
2. Which providers route through the OpenAI-compatible placement path vs the
   Anthropic/Google prepend path, and which use the Responses API path?
3. Where is `[Metadata: Sent at ...]` injected for live and historical turns,
   and where (if anywhere) is it stripped before UI display?
4. Where is the stable system prompt composed for OpenAI-compatible
   providers, and is it byte-stable across turns?
5. What is the exact shape of `FrozenSidecarSnapshot` and the freeze/dedupe
   state machine, and what conditions clear frozen state?
6. How does `PreRetrievalContextSidecar` ship on the OpenAI path today, and
   how is it frozen?
7. How do `PromptCacheSidecarDiagnostics` classify sidecar spans today, and
   what test files cover them?
8. Which UI/display paths render user message content and would need to strip
   a `[Sidecar:]` block (mirroring metadata handling)?
9. What test files cover the OpenAI sidecar placement, dedupe/freeze behavior,
   and diagnostics? What test framework and run commands are used?
10. How does the LiteRT on-device path merge sidecars (to confirm it is out of
    scope and will not be touched)?
11. How does the Responses API (`OpenAiCompatibleResponseSidecarPlacement`)
    path place sidecars, and does it differ from the chat-completions path?

## Findings

All paths below are relative to `/home/sean/Code/pocket-crew` unless noted.
Verified facts are cited as `path:line`; inferences are marked explicitly.

### F1. OpenAI-compatible sidecar placement (chat completions) — CURRENT

`feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/appendonly/OpenAiCompatibleVolatilePlacement.kt`

- `buildMappedBodyWithSidecars(...)` iterates `mappedBody` and, at each
  `mappedInsertIndex` recorded in `frozenSidecars`, inserts a **`role: system`**
  message **before** `mappedBody[index]` via `systemSidecarMessage(text)`
  (`OpenAiCompatibleVolatilePlacement.kt:38-50`).
- The live (non-deduped) sidecar is inserted at `tailInsertIndex` as a
  `role: system` message before `mappedBody[tailInsertIndex]`
  (`OpenAiCompatibleVolatilePlacement.kt:48-50`).
- Pre-retrieval sidecar is likewise inserted as `role: system` at
  `preRetrievalInsertIndex` (`OpenAiCompatibleVolatilePlacement.kt:41-47`).
- Trailing frozen sidecars (indices >= `mappedBody.size`) are appended after
  the body (`OpenAiCompatibleVolatilePlacement.kt:53-61`).
- `systemSidecarMessage` builds a plain `ChatCompletionSystemMessageParam`
  with `.content(text)` — no `cache_control`
  (`OpenAiCompatibleVolatilePlacement.kt:68-74`).
- `tailMappedInsertIndex(mappedBodySize, isToolResult)` returns
  `mappedBodySize - 1` for the normal case, or `mappedBodySize` when the tail
  is a tool result (`OpenAiCompatibleVolatilePlacement.kt:15-22`).

So today every sidecar (frozen, live, pre-retrieval) becomes a separate
`role: system` message in `messages[]`. This is the path INV-4 / INV-9 target.

### F2. Call site — `BaseOpenAiSdkInferenceService.completeSingleTurn`

`feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/BaseOpenAiSdkInferenceService.kt`

- `completeSingleTurn` is the single orchestration entry for all OpenAI-SDK
  providers (`BaseOpenAiSdkInferenceService.kt:470`).
- It computes `mappedBody` via `toMappedSessionBody(...)`
  (`:505-523`), resolves `wireSidecar = appendOnly.resolveContextSidecarsForWire(contextSidecars)`
  (`:534`), resolves tail/pre-retrieval mapped insert indices
  (`:550-569`), then builds the wire `messages` list:
  ```kotlin
  val messages = buildList {
      stableSystemMessage?.let(::add)
      addAll(OpenAiCompatibleVolatilePlacement.buildMappedBodyWithSidecars(...))
  }
  ```
  (`BaseOpenAiSdkInferenceService.kt:591-604`).
- `stableSystemMessage` is the single configured system prompt, prepended only
  when no stable session system message already exists
  (`:536-549`). It uses `buildSystemPromptMessage(content, useCacheControl)`
  (`:897-909`).
- Pre-retrieval sidecar: `options.volatileTurnContext` is formatted via
  `PreRetrievalContextSidecar.format(...)` (`:570-574`). If present and no
  frozen pre-retrieval sidecar exists, it is frozen immediately via
  `endTurnFreezeSidecar(preRetrievalSidecarText, preRetrievalInsertIndex)`
  (`:575-584`). `livePreRetrievalSidecarText` is null once frozen (`:585-590`).
- Responses API path runs in parallel: `responseWireSegments` built via
  `OpenAiCompatibleResponseSidecarPlacement.buildSegmentsWithSidecars(...)`
  with remapped frozen sidecars (`:605-619`).
- Freeze call site: `finalizeSidecarFreeze(appendOnly, wireSidecar, tailInsertIndex, ...)`
  calls `appendOnly.endTurnFreezeSidecar(wireSidecar, tailInsertIndex)` only
  when `wireSidecar != null` (`:452-467`, invoked at `:628-634`).
- Diagnostics: `logPromptCacheDiagnostics(...)` emits `PROMPT_CACHE`,
  `PROMPT_CACHE_BREAK`, `PROMPT_CACHE_JSON_BREAK`, `PROMPT_CACHE_SIDECAR`,
  `PROMPT_CACHE_SIDECAR_DELTA`, `PROMPT_CACHE_SIDECAR_JSON`,
  `PROMPT_CACHE_SIDECAR_JSON_DELTA` lines
  (`:184-338`). The wire JSON body is captured via
  `OpenAiWireJsonSerializer.serializeBody(chatParams._body())` (`:742`).

### F3. Responses API sidecar placement — CURRENT

`feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/OpenAiCompatibleResponseSidecarPlacement.kt`

- `buildSegmentsWithSidecars(...)` mirrors the chat-completions placement:
  it inserts `ResponseWireSegment.SystemText(frozenText)` before the segment
  at each frozen index, plus pre-retrieval and live sidecars at their indices
  (`:28-77`).
- `ResponseWireSegment` is a sealed class with two variants:
  `SystemText(text)` and `Input(item: ResponseInputItem)`
  (`AgentLoopOpenAiResponseMapper.kt:274-282`). User content flows through
  `ResponseWireSegment.Input`; system sidecars flow through `SystemText`.
- `remapFrozenSidecarsForResponses(...)` translates chat-completions mapped
  indices to Responses-API mapped indices via shared session anchors
  (`:10-26`).
- Used only when `buildResponseParams` returns non-null (OpenAI provider,
  `BaseOpenAiSdkInferenceService.kt:149-170`, `:856-893`). DeepSeek/xAI/
  OpenRouter-OpenAI-compat fall back to chat completions.

So the Responses path is **OpenAI-only** today and also emits sidecars as
system segments. Any migration to user-content suffix must cover both the
chat-completions path and, for the OpenAI provider, the Responses path.

### F4. Provider routing

- `BaseOpenAiSdkInferenceService` is the abstract base; concrete subclasses
  include `XaiInferenceServiceImpl`, `OpenRouterInferenceServiceImpl`, and
  the generic OpenAI path (see test harness at
  `AppendOnlyOpenAiSidecarPlacementTest.kt:1171-1255`). DeepSeek direct API
  also routes through this base (per branch history `feat/deepseek`).
- Anthropic uses `AnthropicInferenceServiceImpl`; Google uses
  `GoogleInferenceServiceImpl`. Neither extends `BaseOpenAiSdkInferenceService`
  — they have their own `completeSingleTurn` implementations using
  `SessionIndexVolatilePlacement` (F8).

### F5. Freeze / dedupe state machine

`feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/appendonly/ProviderAppendOnlyContext.kt`

- `FrozenSidecarSnapshot` is `data class FrozenSidecarSnapshot(text: String, mappedInsertIndex: Int)`
  (`FrozenSidecarSnapshot.kt:6-9`). Frozen snapshots store **raw sidecar
  text** (the body produced by `buildContextSidecars`), not any wrapper.
- `resolveContextSidecarsForWire(built)`: trims, returns `null` if blank, and
  returns `null` when `trimmed == lastShippedSidecarText` (dedupe gate);
  otherwise sets `lastShippedSidecarText = trimmed` and returns it
  (`ProviderAppendOnlyContext.kt:95-100`). **Note:** this method has a
  side effect — it mutates `lastShippedSidecarText` on the non-deduped path,
  so the dedupe comparison for a *later* identical value within the same turn
  would also dedupe.
- `endTurnFreezeSidecar(sidecarText, mappedInsertIndex)`: appends a
  `FrozenSidecarSnapshot(text=sidecarText, mappedInsertIndex=mappedInsertIndex)`
  and sets `lastShippedSidecarText = sidecarText.trim()`
  (`:128-140`).
- `hasFrozenPreRetrievalSidecar()`: true if any frozen snapshot's text
  starts with `PreRetrievalContextSidecar.MARKER` (`:102`).
- Clear conditions (`clearLogState`, `:165-179`): triggered by
  `rust_compaction_boundary` (revision bump, `:66-72`), `digest_mismatch`
  (`:77-79`), `compaction_shorter_array` (`:80-82`), and `invalidate()`
  (`:144-149`). All clears wipe `frozenSidecarSnapshots` and reset
  `lastShippedSidecarText = null`.
- `markTurnBoundaryForReanchor()` skips the next digest-mismatch check
  without clearing (`:49-55`, used by
  `BaseOpenAiSdkInferenceService.resetAppendOnlyForNewPrompt`, `:399-402`).
- `appendVolatileSidecar(...)` is an alternate freeze entry that derives
  `mappedInsertIndex` from `VolatileContextDelivery`
  (`:109-126`). Not used by the OpenAI path's main freeze flow (which uses
  `endTurnFreezeSidecar`).
- `committedVolatileSidecarTexts()` returns raw texts (`:91`);
  `frozenSidecarsForWireReplay()` returns the snapshot list (`:93`).

Inference: the dedupe gate compares **raw trimmed sidecar body** to the last
shipped raw trimmed body. Any `[Sidecar:]` wrapper would have to be applied
*after* `resolveContextSidecarsForWire` returns, and the frozen snapshot must
store either raw body or the exact wrapped bytes — consistently — to satisfy
INV-1 byte stability.

### F6. Sidecar content builder

`feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceService.kt:932-985`

- `buildContextSidecars(...)` returns a multi-line string starting with
  `CONTEXT_REFERENCE_POLICY_PREAMBLE`, optional network status, `References:`
  lines, and `State ledger JSON:`, `Tool context index JSON:`,
  `File context index JSON:` lines (`:970-984`).
- Returns `null` when references, JSON blobs, and network status are all empty
  (`:965-968`).
- `cacheHints` are accepted but not included in the returned text (`:937`).
- `usesNativeToolCalling()` returns `provider != "LLAMA"`; native-tool
  providers may omit the tool context index from wire
  (`:943`, `:1004`).
- The sidecar string is passed as `contextSidecars` into
  `completeSingleTurn` (Rust loop host callback), then into
  `resolveContextSidecarsForWire`.

### F7. Pre-retrieval sidecar

`feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/openai/PreRetrievalContextSidecar.kt`

- `MARKER = "Pre-retrieval context:"` (`:8`).
- `format(volatileTurnContext)` prepends the marker if absent (`:10-18`).
- `isSidecarText(text)` checks `text.startsWith(MARKER)` (`:20`).
- On the OpenAI path it is frozen immediately on first ship
  (`BaseOpenAiSdkInferenceService.kt:575-584`) and then shipped live only while
  no frozen pre-retrieval sidecar exists (`:585-590`). It is delivered as a
  `role: system` message via `buildMappedBodyWithSidecars` at
  `preRetrievalInsertIndex`.
- `preRetrievalInsertIndex` resolves to the mapped index of the last
  non-tool-call user session message
  (`BaseOpenAiSdkInferenceService.kt:955-965`).

So INV-8 (pre-retrieval sidecars migrate to `[Sidecar:]` embedding) affects the
OpenAI chat-completions path, the Responses path, and the
`hasFrozenPreRetrievalSidecar` detection (which currently keys on the marker
prefix — would still work if the marker stays inside the sidecar body).

### F8. Anthropic / Google prepend paths — REFERENCE

`feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/appendonly/SessionIndexVolatilePlacement.kt`

- `sidecarTextsBeforeSessionIndex(sessionIndex, frozenSidecars, preRetrievalSidecarText, preRetrievalInsertIndex, currentSidecarText, tailInsertIndex)`
  returns the ordered list of sidecar texts to apply at a given **session**
  index: frozen snapshots whose `mappedInsertIndex == sessionIndex`, then
  pre-retrieval, then live (`:33-55`). It returns **raw texts** with no
  delimiter.
- `resolvePreRetrievalInsertIndex` / `resolveTailInsertIndex` operate on
  session indices directly (`:18-31`).

`feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/AnthropicInferenceServiceImpl.kt:222-246`

- `wireSidecar = appendOnly.resolveContextSidecarsForWire(contextSidecars)`
  (`:229`). `liveSidecarInsertIndex = lastUserIndex.coerceAtLeast(0)` (`:228`).
- `prependIndexSidecars(index, baseContent)` calls
  `SessionIndexVolatilePlacement.sidecarTextsBeforeSessionIndex(...)` and, if
  non-empty, returns `texts.joinToString(separator = "\n\n") + "\n\n" + baseContent`
  — i.e. sidecars are **prepended** to the message content, joined by `\n\n`
  (`:231-246`). The result is used as the Anthropic text block content
  (`:259`, `:284`).

`feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/GoogleInferenceServiceImpl.kt:153-179`

- Same pattern: `sidecarPrependTextForIndex(index)` returns the joined texts
  or null, passed to `GoogleAgentLoopContentMapper.mapMessage(...,
  prependedText = ...)` (`:156-179`).

Inference: Anthropic and Google already embed sidecars inside user/content
blocks (not as separate system messages), so they already avoid the DeepSeek
template-hoist problem. However they **prepend** rather than append, use no
`[Sidecar:]` delimiter, and place sidecars **before** the user-visible text.
INV-2 requires metadata-before-sidecar ordering and sidecar-after-content
suffix placement, which conflicts with the current prepend model. INV-9
says these paths "should align" — alignment would mean switching them from
prepend-without-delimiter to suffix-with-`[Sidecar:]`-delimiter, matching the
OpenAI path. This is a behavior change for Anthropic/Google, not just a
cosmetic one, and must be treated carefully against their existing cache
breakpoints (Anthropic uses `cache_control` breakpoints at
`stableHistoryTailIndex`, `:207`).

### F9. Metadata injection and stripping

Live turn:
`core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/ChatInferenceRequestPreparer.kt:485-488`

- `promptWithTemporalMetadata(prompt)` returns
  `"$prompt\n\n[Metadata: Sent at $formattedTime]"` where `formattedTime` is
  `dateTimeFormatter.format(Instant.now())`.
- Applied to the outgoing user prompt at `:409` before
  `prepareChatPrompt(...)`.

Historical turns:
`core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/ChatHistoryRehydrator.kt:78-102`

- For each USER message, if a matching committed inference prompt exists
  (via `committedInferencePrompt(filteredMessage) == message.content`), the
  metadata is **not** re-added (the stored prompt already has it, `:81-85`).
  Otherwise it appends
  `"\n\n[Metadata: Sent at $formattedTime]"` based on `original.createdAt`
  (`:93-95`).
- The rehydrator uses `message.content.substringBefore("\n\n[Metadata:")`
  only to match against stored DB text (`:90`), not to strip for display.

Persistence:
- `RustLoopInferenceService` commits `userContent` (which is the prompt passed
  in, i.e. already metadata-augmented) to `committedInferencePrompts` on
  `AgentLoopEvent.Finished` (`RustLoopInferenceService.kt:391-396`).
- The message DB stores the **clean user-visible text** (without metadata) —
  metadata lives only in the `committedInferencePrompts` port and on the wire.
  Inference: the UI reads `Message.content` from the DB, which does not
  contain `[Metadata:]`; the metadata is wire-only and never reaches the
  display layer. This is the model INV-5 wants sidecars to follow.

UI rendering:
- `feature/chat/.../components/assistantresponse/AssistantResponse.kt:42-59`
  renders `message.content.text` directly. There is no metadata stripping
  in the chat UI because the DB content is already clean.
- The only `substringBefore("\n\n[Metadata:")` call site in the repo is
  `ChatHistoryRehydrator.kt:90` (matching, not display stripping).
- `feature/chat/.../components/messagebubble/UserBubble.kt:252` uses
  `substringBeforeLast("```")` for code-fence handling, unrelated.

Inference: if sidecars are embedded as a suffix on user message **wire**
  content only (and not persisted to the message DB or
  `committedInferencePrompts`), the UI will never see them — mirroring
  metadata. The stripping concern in INV-5 / §4.4 may therefore be a no-op for
  the chat UI, but the rehydrator's matching logic (`substringBefore`) and any
  path that re-derives wire content from committed prompts must be audited so
  a `[Sidecar:]` suffix does not get persisted or double-applied on rehydrate.

### F10. System prompt composition

`core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/ChatInferenceRequestPreparer.kt:490-538`

- `composeInferencePrompts(...)` builds the system prompt via
  `promptComposer.compose(baseSystemPrompt = configSystemPrompt, toolCatalog,
  activeTools, currentChatId, strategy, coreMemories, ...)` when tooling is
  enabled, else uses `configSystemPrompt` directly (`:517-533`).
- `promptComposer` is injected via `PromptComposerPort`. The concrete
  implementation is `SearchToolPromptComposer`
  (`core/domain/.../SearchToolPromptComposer.kt`).

`SearchToolPromptComposer.kt:154-164`

- Already emits a metadata rule in `metadataRules(compact)`:
  - compact: `"Use message metadata timestamps for time context; never mention metadata in replies."`
  - full: a `# TEMPORAL CONTEXT:` block describing `[Metadata: Sent at ...]`
    and instructing the model not to mention it.
- This is the natural injection point for the INV-7 `[Sidecar:]` annotation
  rule. It is composed once per turn from stable inputs (chatId, tool list,
  strategy) and is byte-stable across turns as long as the rule text itself
  is constant.
- The composed `systemPrompt` is passed to `completeSingleTurn` and becomes
  the single `role: system` message at JSON index 0 (F2). INV-4 is already
  satisfied for the stable prompt; the violation is the *additional* system
  messages emitted by `OpenAiCompatibleVolatilePlacement`.

### F11. Diagnostics classification

`feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/telemetry/PromptCacheSidecarDiagnostics.kt`

- `captureFrom(...)` classifies wire placements by scanning `messages` for
  `isSystem()` params and hashing their content, then labeling each as
  `stable_system`, `frozen_sidecar`, `live_sidecar`, `pre_retrieval_sidecar`,
  or `unknown_system` by matching hashes against the captured frozen/live/
  pre-retrieval hashes and the stable system prompt hash
  (`:73-120`).
- `extractSystemJsonSpans(wireJsonBody)` scans the serialized JSON for
  `"role"` windows containing `system` and hashes the following `"content"`
  probe (`:176-214`). `formatBlock0` hashes the first 512 JSON chars
  (`:283-286`).
- `diff(previous, current)` reports `newWireSystemKinds` and
  `wireSystemIndexShifts` (`:122-174`).

Inference: after the migration, sidecars will no longer be `role: system`
messages, so `captureFrom`'s `wirePlacements` will only ever find
`stable_system`. The diagnostics must be updated to detect `[Sidecar:` spans
inside `role: user` content (the new embedding) and classify them, otherwise
`PROMPT_CACHE_SIDECAR` / `_DELTA` will report empty manifests while the
behavior change is in effect. `extractSystemJsonSpans` and `formatBlock0`
remain useful for verifying INV-4 (single system message).

### F12. Test coverage and framework

- Framework: JUnit 5 (`org.junit.jupiter.api.Test`), `io.mockk` for mocks,
  `kotlinx.coroutines.test.runTest`. Confirmed by imports in
  `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/AppendOnlyOpenAiSidecarPlacementTest.kt:1-23`.
- Primary OpenAI sidecar placement test:
  `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/AppendOnlyOpenAiSidecarPlacementTest.kt`.
  Key existing tests that will break or need rewriting under the new wire
  shape (they assert `role: system` sidecar messages):
  - `AOC_02_single_user_message_tail_sidecar` (`:28-54`) — asserts
    `messages[1].isSystem()` equals the sidecar text and `messages[2].isUser()`
    equals `"Hello"`. Under new shape, the sidecar is a suffix on the user
    message and there is no extra system message.
  - `AOC_03_tool_loop_preserves_committed_prefix_bytes` (`:56-119`) —
    asserts a `role: system` frozen sidecar at index 1 and a separate live
    system sidecar after the tool result.
  - `wire_caps_frozen_sidecars_at_two_on_third_tool_loop_turn` (`:121-197`),
    `AOC_04_openai_wire_carries_both_sidecars_from_AOC_03` (`:238-297`),
    `AOC_10_openrouter_tail_sidecar_matches_AOC_02` (`:324-351`),
    `AOC_12_multi_message_session_rejects_index_1_sidecar_pin`
    (`:396-424`), `AOC_15_xai_sidecar_before_tool_result_tail`
    (`:473-515`), `AOC_18_post_tool_third_call_preserves_messages_prefix_bytes`
    (`:517-571`), `AOC_19_frozen_sidecar_replays_at_original_mapped_index_not_index_1`
    (`:573-639`), `AOC_20_fourth_call_does_not_swap_index_1_sidecar_via_cap`
    (`:641-712`), `AOC_21_sidecar_before_tool_result_when_agent_budget_trails_session`
    (`:714-765`), `AOC_16_openrouter_cache_control_on_stable_prior_user`
    (`:767-796`), `SC_DEDUPE_01`/`SC_DEDUPE_MUT`/`SC_DEDUPE_02`/`SC_DEDUPE_04`
    (`:898-1121`).
  - These tests assert on `messages[i].isSystem()` / `.asSystem().content().asText()`
    and on message counts that include sidecar system slots. They encode the
    invariants (frozen replay at original index, dedupe, byte-stable prefix)
    but via the system-message shape. The *intent* of these invariants is
    still valid; the *assertion shape* must change to inspect user-content
    suffixes instead.
- `XaiPromptCacheLiveContextTest` (`feature/inference/src/test/.../XaiPromptCacheLiveContextTest.kt`)
  has its own `buildContextSidecars` helper (`:494`) and tests live-context
  cache behavior.
- `ProviderAppendOnlyContextTest`
  (`feature/inference/src/test/.../appendonly/ProviderAppendOnlyContextTest.kt`)
  covers dedupe/freeze/clear semantics directly against
  `ProviderAppendOnlyContext`, independent of wire shape — likely unaffected
  by the wire-format change as long as `FrozenSidecarSnapshot` shape and
  `resolveContextSidecarsForWire` / `endTurnFreezeSidecar` semantics stay the
  same (INV-3).
- `ChatHistoryRehydratorTest`
  (`core/domain/src/test/.../chat/ChatHistoryRehydratorTest.kt:58`) asserts
  rehydrated content carries `[Metadata: Sent at ...]`.
- Run commands (from `AGENTS.md` §4):
  - Single module tests: `./gradlew :feature:inference:testDebugUnitTest`
  - Domain tests: `./gradlew :core:domain:testDebugUnitTest`
  - Full: `./gradlew testDebugUnitTest`
  - Style/static: `./gradlew ktlintCheck`, `./gradlew detekt`
  - Compile: `./gradlew :feature:inference:assembleDebug`,
    `./gradlew :app:assembleDebug`

### F13. LiteRT on-device path — OUT OF SCOPE

`feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/LlamaInferenceServiceImpl.kt:657-672`

- `injectSidecarIntoLastUserMessage(messages, contextSidecars)` finds the last
  `Role.USER` message and rewrites its content to
  `"[context]\n$contextSidecars\n[/context]\n\n${message.content}"` — i.e.
  sidecars are **prepended** to the last user message with a
  `[context]...[/context]` wrapper, on every turn. There is no freeze/replay
  or dedupe here.
- This path already embeds sidecars inside user content (not as a separate
  system message), confirming the user-embed approach is viable for
  template-stable tokenization.
- Per INV-9 and the planning brief, this path must NOT change. It does not go
  through `OpenAiCompatibleVolatilePlacement`,
  `ProviderAppendOnlyContext`, or `PromptCacheSidecarDiagnostics`.

### F14. Append-only compaction / message normalization (context for INV-6)

- `ContextSidecarCompaction`
  (`feature/inference/.../appendonly/ContextSidecarCompaction.kt`) strips
  volatile fields from ledger/tool/file JSON before wire — unchanged by this
  work.
- `AppendOnlyMessageLog` / `ProviderNormalizedMessage` /
  `ProviderMessageDigest` implement the Pi append-only invariant and digest
  mismatch detection. The digest is computed over `ProviderNormalizedMessage`
  lists, which are derived from session messages **before** sidecar
  embedding, so embedding sidecars into user content on the wire does not
  perturb the append-only digest. INV-6 is preserved as long as the
  normalized-message → digest path is unchanged.

### F15. Image-bearing user messages

`BaseOpenAiSdkInferenceService.kt:1036-1084`

- When `imageUris` is non-empty, the user message is built as
  `contentOfArrayOfContentParts(parts)` where `parts[0]` is a text part with
  the user content and subsequent parts are image-url parts
  (`buildUserMessageWithImages`, `:1056-1084`).
- The text part is the first part. A `[Sidecar:]` suffix would have to be
  appended to the **text part** (`parts[0]`), not added as a new part, to keep
  it inside the user content token stream. Adding a trailing text part would
  also work for tokenization but would change the part structure; appending to
  the existing text part is the lower-risk choice.
- Anthropic/Google image paths use their own content mappers
  (`GoogleAgentLoopContentMapper`, Anthropic `ContentBlockParam`); the prepend
  model there already handles images by prepending a text block.

## Open Unknowns

None blocking the Research phase. The following are design decisions to
resolve in the Design phase (not research gaps):

- Whether `FrozenSidecarSnapshot.text` should store the raw sidecar body or
  the full `[Sidecar: ...]`-wrapped suffix bytes (INV-1 byte stability vs
  wrapper-evolvability). F5 confirms today it stores raw body.
- Whether Anthropic/Google prepend paths should be migrated in the same
  slice or a follow-up (INV-9 says "should align"; F8 shows their prepend
  model differs and carries cache-breakpoint dependencies).
- The exact Responses-API (`ResponseWireSegment.Input`) mechanism for
  embedding a `[Sidecar:]` suffix into a user input item — F3 confirms user
  content flows through `Input(item: ResponseInputItem)`, but the
  `ResponseInputItem` shape for user text was not traced in this phase.

## Self-Review

- All 11 research questions answered (F1–F14 cover them; F3 covers Q11, F13
  covers Q10, F12 covers Q9).
- Findings are verified facts with `path:line` citations; inferences marked
  explicitly.
- No implementation recommendations or design opinions are included (open
  questions are framed as decisions for the Design phase, not
  recommendations).
- No `TBD`/`TODO`/`similar to` placeholders.
- Test framework (JUnit 5 + MockK + runTest) and run commands identified
  (F12).
- No production or test code was modified.
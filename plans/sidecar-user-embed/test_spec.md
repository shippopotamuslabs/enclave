# Test Spec: sidecar-user-embed

Gherkin scenarios with stable IDs and literal expected values. Expected
values are **not** derived from production code. Signatures match `spec.md`.

## Test framework and run command

- **Framework:** JUnit 5 (`org.junit.jupiter.api.Test`), `io.mockk` for mocks,
  `kotlinx.coroutines.test.runTest` for coroutine tests (verified in
  `AppendOnlyOpenAiSidecarPlacementTest.kt:1-23`).
- **Run commands:**
  - `:feature:inference` tests: `./gradlew :feature:inference:testDebugUnitTest`
  - `:core:domain` tests: `./gradlew :core:domain:testDebugUnitTest`
  - Style/static: `./gradlew ktlintCheck`, `./gradlew detekt`
  - Compile: `./gradlew :feature:inference:assembleDebug`
- **Helpers reused from existing tests:** `captureOpenAiParams(provider, modelId)`
  pattern from `AppendOnlyOpenAiSidecarPlacementTest.kt:1166-1257`
  (subclasses `BaseOpenAiSdkInferenceService` and captures
  `ChatCompletionCreateParams`); `toolLoopFixture()` from the same file for
  tool-loop scenarios.

## Scenario index

| ID | Slice | Behavior |
|----|-------|----------|
| S-EMBED-01 | 1 | `SidecarMarker.wireBlock()` produces the UUID-stamped form. |
| S-EMBED-02 | 1 | `embed` appends a single marker after base content. |
| S-EMBED-03 | 1 | `embed` appends multiple markers in order. |
| S-EMBED-04 | 1 | `embed` preserves an existing `[Metadata:]` suffix before the sidecar. |
| S-EMBED-05 | 1 | `stripSidecarBlocks` removes only UUID-form blocks and leaves `[Metadata:]` + user-typed `[Sidecar:` intact. |
| S-EMBED-06 | 1 | `SidecarMarkerRegex.SPAN` matches only the UUID form. |
| S-FREEZE-01 | 2 | `FrozenSidecarSnapshot` carries `markerId`. |
| S-FREEZE-02 | 2 | `resolveContextSidecarsForWire` returns `ResolvedSidecar` with a fresh markerId on the non-deduped path. |
| S-FREEZE-03 | 2 | Dedupe path returns null and allocates no markerId. |
| S-FREEZE-04 | 2 | live→frozen transition reuses the same markerId. |
| S-FREEZE-05 | 2 | `appendVolatileSidecar` stores markerId. |
| S-WIRE-01 | 3 | Single user message + live sidecar: exactly one `role: system`, sidecar is a `[Sidecar-<uuid>:]` suffix on the user message. |
| S-WIRE-02 | 3 | Frozen sidecar replays byte-identically at its original user anchor across turns. |
| S-WIRE-03 | 3 | Live sidecar ships only on the tail user message. |
| S-WIRE-04 | 3 | Deduped turn ships no live suffix; frozen anchors unchanged. |
| S-WIRE-05 | 3 | Exactly one `role: system` message on the wire (INV-4). |
| S-WIRE-06 | 3 | Sidecar suffix appears after `[Metadata:]` when metadata is present (INV-2). |
| S-WIRE-07 | 3 | Image-bearing user message keeps the sidecar in the first text content part. |
| S-WIRE-08 | 3 | Trailing-index frozen sidecar attaches to the last user message. |
| S-RESP-01 | 4 | Responses path emits no sidecar `ResponseWireSegment.SystemText`. |
| S-RESP-02 | 4 | Responses path embeds sidecar as suffix in user `Input` content. |
| S-RESP-03 | 4 | Responses path keeps the stable system prompt `SystemText` segment. |
| S-RESP-04 | 4 | Responses image-bearing user input keeps sidecar in the first text content part. |
| S-RULE-01 | 5 | Full `metadataRules(false)` contains the UUID-aware `[Sidecar-<uuid>:]` rule. |
| S-RULE-02 | 5 | Compact `metadataRules(true)` contains the one-line sidecar rule. |
| S-RULE-03 | 5 | The rule text contains no UUIDs or timestamps (byte-stable). |
| S-DIAG-01 | 6 | `captureFrom` classifies `[Sidecar-<uuid>:` spans in user content. |
| S-DIAG-02 | 6 | `wirePlacements` contains only `stable_system` after migration. |
| S-DIAG-03 | 6 | `SidecarDelta.userContentSpanDelta` reports new/unchanged/removed by markerId. |
| S-DIAG-04 | 6 | `extractSystemJsonSpans` finds exactly one system span (INV-4 check). |
| S-VAL-01 | 7 | 5-turn DeepSeek emulator run: ≥80% hitPct on turns 1–4 and on the new-live-sidecar turn. |
| S-MUT-01 | 1 | Mutation defense: `stripSidecarBlocks` must not strip a bare `[Sidecar:` without a UUID. |

## Happy path scenarios

### S-EMBED-01
```gherkin
Scenario: SidecarMarker.wireBlock produces UUID-stamped form
  Given a SidecarMarker with markerId "11111111-2222-3333-4444-555555555555" and body "State ledger JSON: {\"v\":1}"
  When wireBlock is called
  Then the result is exactly "[Sidecar-11111111-2222-3333-4444-555555555555: State ledger JSON: {\"v\":1}]"
```

### S-EMBED-02
```gherkin
Scenario: embed appends a single marker after base content
  Given baseContent "Hello" and one SidecarMarker with markerId "11111111-2222-3333-4444-555555555555" and body "ledger"
  When SidecarUserContentEmbedder.embed is called
  Then the result is exactly "Hello\n\n[Sidecar-11111111-2222-3333-4444-555555555555: ledger]"
```

### S-EMBED-03
```gherkin
Scenario: embed appends multiple markers in order
  Given baseContent "Hello" and markers in order with bodies "ledger-A" and "ledger-B" and markerIds "11111111-2222-3333-4444-555555555555" then "66666666-7777-8888-9999-aaaaaaaaaaaa"
  When SidecarUserContentEmbedder.embed is called
  Then the result is exactly "Hello\n\n[Sidecar-11111111-2222-3333-4444-555555555555: ledger-A]\n\n[Sidecar-66666666-7777-8888-9999-aaaaaaaaaaaa: ledger-B]"
```

### S-EMBED-04
```gherkin
Scenario: embed preserves an existing Metadata suffix before the sidecar
  Given baseContent "Hello\n\n[Metadata: Sent at Thursday, June 18, 2026 at 3:45 PM]" and one SidecarMarker with body "ledger"
  When SidecarUserContentEmbedder.embed is called
  Then the result starts with "Hello\n\n[Metadata: Sent at Thursday, June 18, 2026 at 3:45 PM]"
  And the result ends with "\n\n[Sidecar-<markerid>: ledger]"
  And the Metadata block appears before the Sidecar block
```

### S-EMBED-06
```gherkin
Scenario: SidecarMarkerRegex.SPAN matches only the UUID form
  Given the strings "[Sidecar-11111111-2222-3333-4444-555555555555:", "[Sidecar:not-a-uuid:", "[Sidecar:", and "[Metadata: Sent at ...]"
  When each is tested against SidecarMarkerRegex.SPAN
  Then only "[Sidecar-11111111-2222-3333-4444-555555555555:" matches
  And "[Sidecar:not-a-uuid:", "[Sidecar:", and "[Metadata: Sent at ...]" do not match
```

### S-FREEZE-01
```gherkin
Scenario: FrozenSidecarSnapshot carries markerId
  Given a ProviderAppendOnlyContext with a frozen snapshot created via endTurnFreezeSidecar(sidecarText="State ledger JSON: {\"v\":1}", markerId="11111111-2222-3333-4444-555555555555", mappedInsertIndex=2)
  When frozenSidecarsForWireReplay is called
  Then the first snapshot has text "State ledger JSON: {\"v\":1}"
  And the first snapshot has markerId "11111111-2222-3333-4444-555555555555"
  And the first snapshot has mappedInsertIndex 2
```

### S-FREEZE-02
```gherkin
Scenario: resolveContextSidecarsForWire returns ResolvedSidecar with fresh markerId on non-deduped path
  Given a ProviderAppendOnlyContext whose lastShippedSidecarText is null
  When resolveContextSidecarsForWire is called with built "State ledger JSON: {\"v\":1}"
  Then the result is not null
  And the result has text "State ledger JSON: {\"v\":1}"
  And the result markerId matches the regex ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$
```

### S-FREEZE-03
```gherkin
Scenario: Dedupe path returns null and allocates no markerId
  Given a ProviderAppendOnlyContext whose lastShippedSidecarText is "State ledger JSON: {\"v\":1}"
  When resolveContextSidecarsForWire is called with built "State ledger JSON: {\"v\":1}"
  Then the result is null
```

### S-FREEZE-04
```gherkin
Scenario: live to frozen transition reuses the same markerId
  Given a ProviderAppendOnlyContext
  When resolveContextSidecarsForWire is called with built "State ledger JSON: {\"v\":1}" and returns ResolvedSidecar with markerId X
  And endTurnFreezeSidecar is called with sidecarText "State ledger JSON: {\"v\":1}", markerId X, mappedInsertIndex 1
  Then frozenSidecarsForWireReplay has size 1
  And the first snapshot markerId equals X
  And the first snapshot text equals "State ledger JSON: {\"v\":1}"
```

### S-FREEZE-05
```gherkin
Scenario: appendVolatileSidecar stores markerId
  Given a ProviderAppendOnlyContext and a VolatileContextDelivery.OpenAiTailBeforeLastSessionMessage with sessionLastIndex 3
  When appendVolatileSidecar is called with sidecarText "ledger", markerId "11111111-2222-3333-4444-555555555555", and that delivery
  Then frozenSidecarsForWireReplay has size 1
  And the first snapshot markerId is "11111111-2222-3333-4444-555555555555"
  And the first snapshot mappedInsertIndex is 3
```

### S-WIRE-01
```gherkin
Scenario: Single user message with live sidecar embeds as user-content suffix
  Given a captured OpenAI chat-completions service with provider "OPENAI" model "gpt-4o"
  When completeSingleTurn is called with one user message "Hello" and contextSidecars "State ledger JSON: {\"key\":\"value\"}"
  Then the captured params single messages has size 2
  And messages[0] isSystem is true with content "You are a helpful assistant."
  And messages[1] isUser is true
  And messages[1] asUser content asText starts with "Hello"
  And messages[1] asUser content asText contains "[Sidecar-"
  And messages[1] asUser content asText contains "State ledger JSON: {\"key\":\"value\"}"
  And no message in the list other than messages[0] isSystem
```

### S-WIRE-02
```gherkin
Scenario: Frozen sidecar replays byte-identically at its original user anchor across turns
  Given a captured XAI service and a tool-loop fixture (userMessage, assistantWithTool, toolResult)
  When completeSingleTurn is called with [userMessage] and sidecar "State ledger JSON: {\"turn\":1}"
  And completeSingleTurn is called with [userMessage, assistantWithTool, toolResult] and sidecar "State ledger JSON: {\"turn\":2}"
  Then the turn-1 user message wire content from the first call equals the turn-1 user message wire content from the second call byte-for-byte
  And both contain "[Sidecar-" and "State ledger JSON: {\"turn\":1}"
```

### S-WIRE-03
```gherkin
Scenario: Live sidecar ships only on the tail user message
  Given a captured XAI service and a multi-message session [u1 "First", a1 "Ack", u2 "Second"]
  When completeSingleTurn is called with contextSidecars "State ledger JSON: {\"k\":1}"
  Then exactly one user message in the captured params contains the live "[Sidecar-" marker
  And that user message is the tail user message with content starting "Second"
  And the non-tail user message "First" contains no "[Sidecar-" marker
```

### S-WIRE-04
```gherkin
Scenario: Deduped turn ships no live suffix and frozen anchors unchanged
  Given a captured OPENAI service with sidecar "State ledger JSON: {\"turn\":1}" shipped on hop 1
  When completeSingleTurn is called on hop 2 with the identical sidecar "State ledger JSON: {\"turn\":1}"
  Then the tail user message on hop 2 contains no new live "[Sidecar-" block for the hop-2 sidecar
  And the frozen anchor from hop 1 is still present in its original user message
  And appendOnlyContextForTests frozenSidecarsForWireReplay has size 1
```

### S-WIRE-05
```gherkin
Scenario: Exactly one role system message on the wire
  Given a captured OPENAI service and any session with a non-blank system prompt and a live sidecar
  When completeSingleTurn is called
  Then the captured params single messages filtered by isSystem has size 1
  And that single system message content asText equals "You are a helpful assistant."
```

### S-WIRE-06
```gherkin
Scenario: Sidecar suffix appears after Metadata when metadata is present
  Given a user message whose wire content already contains a "[Metadata: Sent at ...]" suffix and a live sidecar resolves non-null
  When the chat-completions wire is assembled
  Then the user message content contains "[Metadata: Sent at" before the first "[Sidecar-" occurrence
  And the index of "[Metadata:" is less than the index of "[Sidecar-"
```

### S-WIRE-07
```gherkin
Scenario: Image-bearing user message keeps sidecar in the first text content part
  Given a captured OPENAI service and a user message with one imageUri and a live sidecar "ledger"
  When completeSingleTurn is called with options.imageUris containing one URI
  Then the tail user message content is an array of content parts
  And the first content part isText is true
  And the first content part text contains "[Sidecar-" and "ledger"
  And a content part isImageUrl is true
  And no additional text content part was added for the sidecar
```

### S-WIRE-08
```gherkin
Scenario: Trailing-index frozen sidecar attaches to the last user message
  Given a captured XAI service and a frozen sidecar whose mappedInsertIndex is greater than or equal to mappedBody.size
  When completeSingleTurn is called
  Then the last user message in the captured params contains "[Sidecar-" with that frozen sidecar body
```

### S-RESP-01
```gherkin
Scenario: Responses path emits no sidecar SystemText segments
  Given a captured OPENAI service whose buildResponseParams returns non-null and a live sidecar resolves non-null
  When completeSingleTurn is called
  Then the built ResponseWireSegment list contains no ResponseWireSegment.SystemText whose text starts with "State ledger JSON:" or "Pre-retrieval context:" or "[Sidecar-"
```

### S-RESP-02
```gherkin
Scenario: Responses path embeds sidecar as suffix in user Input content
  Given a captured OPENAI Responses path and a live sidecar "ledger"
  When the response wire segments are built
  Then some ResponseWireSegment.Input at the tail user index has content text containing "[Sidecar-" and "ledger"
```

### S-RESP-03
```gherkin
Scenario: Responses path keeps the stable system prompt SystemText segment
  Given a captured OPENAI Responses path with a non-blank system prompt
  When the response wire segments are built
  Then exactly one ResponseWireSegment.SystemText segment exists
  And its text equals the configured system prompt
```

### S-RESP-04
```gherkin
Scenario: Responses image-bearing user input keeps sidecar in the first text content part
  Given a captured OPENAI Responses path with a user input carrying one image and a live sidecar "ledger"
  When the response wire segments are built
  Then the tail user ResponseWireSegment.Input has content with a first input text part containing "[Sidecar-" and "ledger"
  And no extra input text part was added for the sidecar
```

### S-RULE-01
```gherkin
Scenario: Full metadataRules contains the UUID-aware Sidecar rule
  Given SearchToolPromptComposer.metadataRules invoked with compact false
  When the rule text is produced
  Then it contains the substring "[Sidecar-<uuid>:"
  And it contains the substring "host-injected"
  And it instructs the model not to quote, summarize, or acknowledge the blocks
```

### S-RULE-02
```gherkin
Scenario: Compact metadataRules contains the one-line sidecar rule
  Given SearchToolPromptComposer.metadataRules invoked with compact true
  When the rule text is produced
  Then it contains a single sentence referencing "[Sidecar-" blocks as host context
  And it instructs the model not to mention them
```

### S-RULE-03
```gherkin
Scenario: The rule text contains no UUIDs or timestamps
  Given the full and compact rule texts from metadataRules
  When both are inspected
  Then neither matches the regex [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}
  And neither matches the regex (Sent at|Current Time:)
  And calling metadataRules twice with the same compact flag yields byte-identical text
```

### S-DIAG-01
```gherkin
Scenario: captureFrom classifies Sidecar spans in user content
  Given a wire messages list with one system message and one user message whose content contains "[Sidecar-11111111-2222-3333-4444-555555555555: ledger]"
  When PromptCacheSidecarDiagnostics.captureFrom is called with a matching live sidecar text
  Then the SidecarManifest userContentSpans has size 1
  And the first userContentSpan markerId is "11111111-2222-3333-4444-555555555555"
  And the first userContentSpan wireIndex is the index of the user message
```

### S-DIAG-02
```gherkin
Scenario: wirePlacements contains only stable_system after migration
  Given a wire messages list with exactly one system message (the stable prompt) and user messages carrying sidecar suffixes
  When captureFrom is called
  Then SidecarManifest wirePlacements has size 1
  And the single wirePlacement kind is "stable_system"
```

### S-DIAG-03
```gherkin
Scenario: SidecarDelta userContentSpanDelta reports new unchanged removed by markerId
  Given a previous SidecarManifest with userContentSpan markerId A and a current SidecarManifest with userContentSpan markerIds A and B
  When diff is called
  Then userContentSpanDelta unchanged contains markerId A
  And userContentSpanDelta new contains markerId B
  And userContentSpanDelta removed is empty
```

### S-DIAG-04
```gherkin
Scenario: extractSystemJsonSpans finds exactly one system span
  Given a wire JSON body with exactly one role system message
  When extractSystemJsonSpans is called
  Then the result has size 1
```

### S-VAL-01
```gherkin
Scenario: 5-turn DeepSeek emulator run meets cache hit rate criteria
  Given a release build of the app installed on an emulator with DeepSeek direct API configured and the vault unlocked and no pm clear performed
  When a 5-turn conversation is run where the ledger grows on turn 5
  Then logcat lines matching "PROMPT_CACHE seq=" show hitPct greater than or equal to 80 on turns 1 through 4
  And the turn-5 hitPct (new live sidecar) is greater than or equal to 80
  And the logcat "PROMPT_CACHE_JSON_BREAK" line for the turn-5 divergence shows the break occurs at the tail user message suffix and not at the system block or a mid-array system role insertion
  And the chat UI displays no "[Sidecar-" or "[Metadata:" content to the user
  And the model responses do not verbally acknowledge the sidecar or metadata blocks
```

## Error and edge case scenarios

### S-EMBED-05
```gherkin
Scenario: stripSidecarBlocks removes only UUID-form blocks and leaves Metadata and user-typed Sidecar intact
  Given content "Hello\n\n[Metadata: Sent at ...]\n\n[Sidecar-11111111-2222-3333-4444-555555555555: ledger]\n\n[Sidecar:user-typed-not-a-uuid: fake]"
  When SidecarUserContentEmbedder.stripSidecarBlocks is called
  Then the result is exactly "Hello\n\n[Metadata: Sent at ...]\n\n[Sidecar:user-typed-not-a-uuid: fake]"
```

### S-MUT-01
```gherkin
Scenario: Mutation defense - stripSidecarBlocks must not strip a bare Sidecar without a UUID
  Given a lazy implementation that strips any block starting with "[Sidecar:"
  And content "Hello\n\n[Sidecar:user note]\n\n[Sidecar-11111111-2222-3333-4444-555555555555: ledger]"
  When stripSidecarBlocks is called
  Then the result still contains "[Sidecar:user note]"
  And the result does not contain "[Sidecar-11111111-2222-3333-4444-555555555555: ledger]"
  A lazy implementation that strips by the bare "[Sidecar:" prefix would remove both blocks and fail this scenario.
```

## Mutation-defense risk and scenario

**Risk:** The most plausible lazy implementation of `stripSidecarBlocks` (and
of diagnostics span detection) keys on the bare `[Sidecar:` prefix rather
than the UUID-stamped form `[Sidecar-<uuid>:`. This would appear to pass
happy-path tests that only use real sidecar markers, but it would
mis-strip user-typed content like `[Sidecar:user note]` and mis-classify
user content as sidecar spans — exactly the collision risk identified in
user followup #5 and D7.

**Defense scenario S-MUT-01** uses a user-typed `[Sidecar:user note]` block
**without** a valid UUID alongside a real UUID-stamped block. A correct
implementation strips only the UUID-form block; a lazy bare-prefix
implementation strips both and fails the `Then the result still contains
"[Sidecar:user note]"` clause. The literal expected value
`"Hello\n\n[Metadata: Sent at ...]\n\n[Sidecar:user-typed-not-a-uuid: fake]`
in S-EMBED-05 provides the same defense for the embedder's strip path.
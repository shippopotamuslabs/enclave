# Test Spec: sidecar-anthropic-google-embed

Scenario index + acceptance criteria for the tactical plan in
`plans/sidecar-anthropic-google-embed/spec.md`. Every `Then` clause names a
literal expected value / observable state / enum / exact exception. Expected
values are **not** derived from production code. Honors AGENTS.md §3.3
(test-coverage mandate), the `UNIT_TESTING.md` contract (JUnit 5 + MockK +
`runTest`), and INV-1..INV-9.

---

## 1. Framework, harness, and run commands

- **Engine:** JUnit 5 (`org.junit.jupiter.api.Test`, `@DisplayName`),
  `org.junit.jupiter.api.Assertions.*`. New JUnit 4 is forbidden
  (`UNIT_TESTING.md` §1).
- **Coroutines:** `kotlinx.coroutines.test.runTest` for all
  `completeSingleTurn` calls (the provider entrypoint is `suspend`).
- **Mocking:** MockK (`io.mockk`) — used here for the third-party provider SDK
  clients only (Anthropic `AnthropicClient`/`MessageService`, Google
  `GoogleGenAiSdkClient`), per `UNIT_TESTING.md` §2 ("MockK only when a Fake is
  impractical, e.g. third-party SDKs"). Domain ports use the existing fakes
  `FakeToolEnvelopeParserPort` / `FakeToolStreamGatePort`.
- **Param-capture pattern (verified against the existing tests):**
  - **Anthropic** (`AnthropicInferenceServiceImplCachePrefixTest`,
    `AppendOnlyProviderWiringTest`): capture the request via
    `every { messageService.createStreaming(any<MessageCreateParams>()) } answers { capturedParams.add(firstArg<MessageCreateParams>()); streamResponse }`,
    stub `streamResponse.stream()` with `Stream.of(anthropicMessageStopEvent())`
    and `streamResponse.close()`; then read
    `params.messages()[i].content().asBlockParams()` and, for the text block,
    `...single().asText().text()` / `...asText().cacheControl().isPresent`.
  - **Google** (`GoogleInferenceServiceImplCachePrefixTest`): capture via
    `val contentsSlot = slot<List<Content>>()` on
    `coEvery { sdkClient.generateContentStream(modelId = any(), contents = capture(contentsSlot), config = capture(configSlot), allowToolCall = any(), emitEvent = any()) } returns GoogleSdkResult(emittedAny = true, functionCall = null, assistantContent = null, finishReason = "STOP")`;
    then read `contentsSlot.captured[i].parts().get()[j].text().get()` and
    `...inlineData().isPresent` for image parts. Cache deletion uses
    `service.closeSession()` + `coVerify { sdkClient.deleteCachedContent(...) }`.
  - **Append-only inspection:** both services expose
    `appendOnlyContextForTests(): ProviderAppendOnlyContext?` (used by
    `AppendOnlyProviderWiringTest`) for freeze-count assertions.
- **Image inputs (JVM):** create a real temp file URI —
  `File.createTempFile("sidecar-image", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }.toURI().toString()`
  — and pass it via `GenerationOptions(imageUris = listOf(...))`. Mirrors
  `GoogleRequestMapperTest.createTempImageUri()`. `ImagePayloads.fromUris` reads
  the file from a `file://` URI.

### Run commands

| Purpose | Command |
|---------|---------|
| Slice 1 unit test | `./gradlew :feature:inference:testDebugUnitTest --tests "*SessionIndexVolatilePlacementTest*"` |
| Slice 2 unit test | `./gradlew :feature:inference:testDebugUnitTest --tests "*AnthropicInferenceServiceImplCachePrefixTest*"` |
| Slice 3 unit test | `./gradlew :feature:inference:testDebugUnitTest --tests "*GoogleInferenceServiceImplSidecarWireTest*"` |
| Full module unit tests | `./gradlew :feature:inference:testDebugUnitTest` |
| Domain regression (optional — no `:core:domain` change) | `./gradlew :core:domain:testDebugUnitTest` |
| Module compile | `./gradlew :feature:inference:assembleDebug` |
| Style | `./gradlew ktlintCheck` |
| Static analysis (`maxIssues: 0`) | `./gradlew detekt` |

---

## 2. Literal test constants

Deterministic UUIDs for unit-level inputs where the test **constructs** the
snapshot/resolved sidecar directly (Slice 1):

- `FROZEN_A = 11111111-2222-3333-4444-555555555555`
- `FROZEN_B = 66666666-7777-8888-9999-aaaaaaaaaaaa`
- `LIVE = 99999999-8888-7777-6666-555555555555`
- `PRERETR = aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee`

UUID format regex for end-to-end provider tests where the markerId is **generated**
by production code:

- `UUID_REGEX = ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`

For end-to-end tests, assert the captured uuid **matches** `UUID_REGEX` and assert
**cross-turn equality** of the captured uuid (continuity); do not assert a fixed
literal uuid value.

---

## 3. Scenario index

| ID | Slice | Title | Type |
|----|-------|-------|------|
| S-SIP-01 | 1 | Live marker emitted only at `tailInsertIndex` | happy |
| S-SIP-02 | 1 | Frozen markers emitted at their `mappedInsertIndex`, order preserved | happy |
| S-SIP-03 | 1 | `null` pre-retrieval emits nothing even when its index matches (DA7 parity) | edge |
| S-SIP-04 | 1 | Non-matching session index returns empty list | edge |
| S-SIP-05 | 1 | Frozen-then-live selection order at the same index | happy |
| S-SIP-06 | 1 | **Mutation-defense:** emitted markers carry the input `markerId`, not a freshly minted one | mutation |
| S-SIP-07 | 1 | Pre-retrieval marker emitted at its matching index when non-null (parity contract) | edge |
| S-ANTH-01 | 2 | Live tail suffix wire shape (Turn 1) | happy |
| S-ANTH-02 | 2 | Frozen replay byte-identity + live→frozen `markerId` continuity (Turn 2) | happy |
| S-ANTH-03 | 2 | Metadata-before-sidecar ordering | edge |
| S-ANTH-04 | 2 | Turn-2 live tail gets a new, distinct uuid | happy |
| S-ANTH-05 | 2 | Cache-breakpoint placement unchanged (DA4) | happy |
| S-ANTH-06 | 2 | Image-bearing user turn: suffix in the single text block, image blocks untouched (DA6) | edge |
| S-ANTH-07 | 2 | Deduped turn omits live suffix; frozen anchor replays unchanged (INV-3) | edge |
| S-ANTH-08 | 2 | **Mutation-defense:** suffix-after-content + uuid continuity (kills prepend & throwaway-mint mutants) | mutation |
| S-GOOG-01 | 3 | Live tail suffix wire shape in the tail `Content` text part | happy |
| S-GOOG-02 | 3 | Frozen replay byte-identity in the text part across turns | happy |
| S-GOOG-03 | 3 | Deduped turn omits live suffix; frozen anchor replays unchanged (INV-3) | edge |
| S-GOOG-04 | 3 | Image-bearing user turn: suffix in the leading text part, image bytes part untouched (DA6) | edge |
| S-GOOG-05 | 3 | Live→frozen `markerId` continuity (uuid equality across turns) | happy |
| S-GOOG-06 | 3 | **Mutation-defense:** suffix-after-content + uuid continuity (kills prepend & throwaway-mint mutants) | mutation |
| S-VAL-01 | 4 | CI-aligned checks green | manual/CI |
| S-VAL-02 | 4 | Anthropic `cache_control` emulator validation (Open Q1) | manual |
| S-VAL-03 | 4 | Google `CachedContent` reuse emulator validation (Open Q2) | manual |

---

## 4. Slice 1 — `SessionIndexVolatilePlacementTest`

File: `feature/inference/src/test/.../appendonly/SessionIndexVolatilePlacementTest.kt`.
Pure-function tests on `SessionIndexVolatilePlacement.sidecarMarkersForSessionIndex(...)`
— no coroutines, no mocks. Inputs are constructed directly with the literal UUIDs
from §2.

### S-SIP-01 — Live marker emitted only at `tailInsertIndex`
- **Given** `frozenSidecars = emptyList()`, `preRetrievalSidecar = null`,
  `preRetrievalInsertIndex = null`, `liveSidecar = ResolvedSidecar(text = "live-body", markerId = LIVE)`,
  `tailInsertIndex = 2`.
- **When** called with `sessionIndex = 2`.
- **Then** the result equals `listOf(SidecarMarker(markerId = LIVE, body = "live-body"))`
  (exactly one element; `result[0].markerId == "99999999-8888-7777-6666-555555555555"`,
  `result[0].body == "live-body"`).
- **And When** called with `sessionIndex = 1` (all other inputs identical).
- **Then** the result is an empty list (`result.isEmpty()` is `true`).

### S-SIP-02 — Frozen markers emitted at their `mappedInsertIndex`, order preserved
- **Given** `frozenSidecars = listOf(FrozenSidecarSnapshot(text = "frozen-A", markerId = FROZEN_A, mappedInsertIndex = 0), FrozenSidecarSnapshot(text = "frozen-B", markerId = FROZEN_B, mappedInsertIndex = 0))`,
  `preRetrievalSidecar = null`, `preRetrievalInsertIndex = null`,
  `liveSidecar = null`, `tailInsertIndex = 5`.
- **When** called with `sessionIndex = 0`.
- **Then** the result equals
  `listOf(SidecarMarker(FROZEN_A, "frozen-A"), SidecarMarker(FROZEN_B, "frozen-B"))`
  in that order (`result.size == 2`,
  `result[0].markerId == "11111111-2222-3333-4444-555555555555"`,
  `result[1].markerId == "66666666-7777-8888-9999-aaaaaaaaaaaa"`).

### S-SIP-03 — `null` pre-retrieval emits nothing even when its index matches (DA7 parity)
- **Given** `frozenSidecars = emptyList()`, `preRetrievalSidecar = null`,
  `preRetrievalInsertIndex = 1`, `liveSidecar = null`, `tailInsertIndex = 3`.
- **When** called with `sessionIndex = 1`.
- **Then** the result is an empty list (`result.isEmpty()` is `true`) — the
  `null` `preRetrievalSidecar` produces no marker despite the matching index.

### S-SIP-04 — Non-matching session index returns empty list
- **Given** `frozenSidecars = listOf(FrozenSidecarSnapshot(text = "frozen-A", markerId = FROZEN_A, mappedInsertIndex = 0))`,
  `preRetrievalSidecar = null`, `preRetrievalInsertIndex = null`,
  `liveSidecar = ResolvedSidecar("live-body", LIVE)`, `tailInsertIndex = 3`.
- **When** called with `sessionIndex = 2`.
- **Then** the result is an empty list (`result.isEmpty()` is `true`).

### S-SIP-05 — Frozen-then-live selection order at the same index
- **Given** `frozenSidecars = listOf(FrozenSidecarSnapshot(text = "frozen-A", markerId = FROZEN_A, mappedInsertIndex = 2))`,
  `preRetrievalSidecar = null`, `preRetrievalInsertIndex = null`,
  `liveSidecar = ResolvedSidecar("live-body", LIVE)`, `tailInsertIndex = 2`.
- **When** called with `sessionIndex = 2`.
- **Then** the result equals
  `listOf(SidecarMarker(FROZEN_A, "frozen-A"), SidecarMarker(LIVE, "live-body"))`
  — frozen first, live last (`result.size == 2`, `result[0].markerId == FROZEN_A`,
  `result[1].markerId == LIVE`).

### S-SIP-06 — Mutation-defense: emitted markers carry the input `markerId`, not a freshly minted one
- **Given** `frozenSidecars = listOf(FrozenSidecarSnapshot(text = "frozen-A", markerId = FROZEN_A, mappedInsertIndex = 0))`,
  `liveSidecar = ResolvedSidecar("live-body", LIVE)`,
  `preRetrievalSidecar = null`, `preRetrievalInsertIndex = null`,
  `tailInsertIndex = 0`.
- **When** called with `sessionIndex = 0`.
- **Then** `result[0].markerId` equals the literal
  `"11111111-2222-3333-4444-555555555555"` (FROZEN_A) **and** `result[1].markerId`
  equals the literal `"99999999-8888-7777-6666-555555555555"` (LIVE).
- **Kills mutant:** an implementation that re-mints via
  `SidecarMarkerFactory.newMarker(snapshot.text)` /
  `SidecarMarkerFactory.newMarker(liveSidecar.text)` would yield random UUIDs that
  do not equal FROZEN_A / LIVE, failing both equality assertions.

### S-SIP-07 — Pre-retrieval marker emitted at its matching index when non-null (parity contract)
- **Given** `frozenSidecars = emptyList()`,
  `preRetrievalSidecar = ResolvedSidecar(text = "preretr-body", markerId = PRERETR)`,
  `preRetrievalInsertIndex = 1`, `liveSidecar = null`, `tailInsertIndex = 3`.
- **When** called with `sessionIndex = 1`.
- **Then** the result equals
  `listOf(SidecarMarker(markerId = PRERETR, body = "preretr-body"))`
  (`result.size == 1`, `result[0].markerId == "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"`,
  `result[0].body == "preretr-body"`). Documents the signature's parity contract;
  providers pass `null` here (DA7).

---

## 5. Slice 2 — `AnthropicInferenceServiceImplCachePrefixTest` (rewritten)

File: `feature/inference/src/test/.../AnthropicInferenceServiceImplCachePrefixTest.kt`.
Rewrite the existing single test to the suffix shape and add the
metadata-ordering, image, dedupe, and mutation-defense scenarios as additional
`@Test` methods. Harness: capture `MessageCreateParams` (see §1). Service
constructed with `modelId = "claude-3-5-sonnet-20241022"`, `ModelType.MAIN`,
`FakeToolEnvelopeParserPort()`, `FakeToolStreamGatePort()`,
`options = GenerationOptions(reasoningBudget = 0)`.

Helper to read the text of message `i`'s single text block:
`params.messages()[i].content().asBlockParams().single().asText().text()`. Helper
to extract the captured uuid from a captured block text:
`Regex("\\[Sidecar-([0-9a-f-]{36}): ").find(text)!!.groupValues[1]`.

### Two-turn fixture (shared by S-ANTH-01, S-ANTH-02, S-ANTH-04, S-ANTH-05)
- Turn 1 `sessionMessages = [AgentLoopMessage("u1", USER, "Hello Turn 1"), AgentLoopMessage("a1", ASSISTANT, "Reply Turn 1")]`,
  `systemPrompt = "System Instruction"`, `contextSidecars = "Volatile Sidecar 1"`.
- Turn 2 `sessionMessages = [u1 "Hello Turn 1", a1 "Reply Turn 1", AgentLoopMessage("u2", USER, "Hello Turn 2"), AgentLoopMessage("a2", ASSISTANT, "Reply Turn 2")]`,
  `contextSidecars = "Volatile Sidecar 2"`.
- (Index facts: Turn 1 `lastUserIndex = 0` and `stableHistoryTailIndex = size-2 = 0`
  coincide on U1; Turn 2 `lastUserIndex = 2` and `stableHistoryTailIndex = 2`
  coincide on U2.)

### S-ANTH-01 — Live tail suffix wire shape (Turn 1)
- **Given** the two-turn fixture, after Turn 1's `completeSingleTurn`.
- **When** reading `params1.messages()[0]` (U1) text.
- **Then** it equals `"Hello Turn 1\n\n[Sidecar-" + uuid1 + ": Volatile Sidecar 1]"`
  where `uuid1` matches `UUID_REGEX`. The text **starts with** `"Hello Turn 1"`
  and **ends with** `": Volatile Sidecar 1]"`; the index of `"[Sidecar-"` is
  `> 0` (suffix, not prepend).
- **And** `params1.messages()[1]` (A1) text equals exactly `"Reply Turn 1"`
  (no sidecar on the assistant row).

### S-ANTH-02 — Frozen replay byte-identity + live→frozen `markerId` continuity (Turn 2)
- **Given** the two-turn fixture, after both turns.
- **When** reading `params2.messages()[0]` (U1, frozen replay) text and comparing
  to Turn 1's U1 text.
- **Then** `params2.messages()[0]` text equals `params1.messages()[0]` text
  **byte-for-byte** (`assertEquals`), i.e.
  `"Hello Turn 1\n\n[Sidecar-" + uuid1 + ": Volatile Sidecar 1]"` with the **same**
  `uuid1` captured in S-ANTH-01 (continuity: the frozen snapshot replays the live
  turn's uuid, not a new one).

### S-ANTH-03 — Metadata-before-sidecar ordering
- **Given** a single-turn fixture
  `sessionMessages = [AgentLoopMessage("u1", USER, "Hello Turn 1\n\n[Metadata: Sent at noon]")]`,
  `contextSidecars = "Volatile Sidecar 1"`.
- **When** reading `params.messages()[0]` (U1) text.
- **Then** it equals
  `"Hello Turn 1\n\n[Metadata: Sent at noon]\n\n[Sidecar-" + uuid + ": Volatile Sidecar 1]"`
  (`uuid` matches `UUID_REGEX`). `indexOf("[Metadata:")` is `>= 0` and **strictly
  less than** `indexOf("[Sidecar-")`; the text **ends with**
  `": Volatile Sidecar 1]"` (sidecar after metadata, INV-2/DA3).

### S-ANTH-04 — Turn-2 live tail gets a new, distinct uuid
- **Given** the two-turn fixture, after both turns.
- **When** reading `params2.messages()[2]` (U2, live) text.
- **Then** it equals `"Hello Turn 2\n\n[Sidecar-" + uuid2 + ": Volatile Sidecar 2]"`
  where `uuid2` matches `UUID_REGEX` **and** `uuid2 != uuid1` (the Turn-1 uuid
  from S-ANTH-01). `params2.messages()[3]` (A2) text equals exactly
  `"Reply Turn 2"`.

### S-ANTH-05 — Cache-breakpoint placement unchanged (DA4)
- **Given** the two-turn fixture, after both turns.
- **Then** the system block carries a cache breakpoint on **both** turns:
  `params1.system().get().asTextBlockParams().single().cacheControl().isPresent`
  is `true` and the same for `params2`.
- **And** Turn 1: `params1.messages()[0]` (U1, index 0 = stable-tail) text block
  `cacheControl().isPresent` is `true`; `params1.messages()[1]` (A1)
  `cacheControl().isPresent` is `false`.
- **And** Turn 2: `params2.messages()[0]` (U1) `cacheControl().isPresent` is
  `false`; `params2.messages()[1]` (A1) is `false`; `params2.messages()[2]` (U2,
  index 2 = stable-tail) is `true`; `params2.messages()[3]` (A2) is `false`.

### S-ANTH-06 — Image-bearing user turn: suffix in the single text block, image blocks untouched (DA6)
- **Given** a single-turn fixture
  `sessionMessages = [AgentLoopMessage("u1", USER, "Describe this")]`,
  `contextSidecars = "Volatile Sidecar 1"`,
  `options = GenerationOptions(reasoningBudget = 0, imageUris = listOf(tempJpegUri))`
  (temp file per §1).
- **When** reading `params.messages()[0]` (U1) content blocks
  (`...content().asBlockParams()`).
- **Then** the block list has size `2`; `blocks[0].isImage()` is `true`
  (image block unchanged); `blocks[1].isText()` is `true` and its text equals
  `"Describe this\n\n[Sidecar-" + uuid + ": Volatile Sidecar 1]"` (`uuid` matches
  `UUID_REGEX`) — the suffix lives in the single trailing text block only.

### S-ANTH-07 — Deduped turn omits live suffix; frozen anchor replays unchanged (INV-3)
- **Given** a two-turn fixture where **both** turns pass the identical
  `contextSidecars = "Volatile Sidecar 1"`:
  Turn 1 `[u1 "Hello Turn 1", a1 "Reply Turn 1"]`; Turn 2
  `[u1 "Hello Turn 1", a1 "Reply Turn 1", u2 "Hello Turn 2", a2 "Reply Turn 2"]`.
- **When** reading Turn 2's messages.
- **Then** `params2.messages()[0]` (U1) text equals
  `"Hello Turn 1\n\n[Sidecar-" + uuid1 + ": Volatile Sidecar 1]"` (frozen replay
  of Turn 1's uuid1, unchanged) **and** `params2.messages()[2]` (U2) text equals
  exactly `"Hello Turn 2"` — **no** `[Sidecar-` substring on U2 (the live ship was
  deduped, INV-3). `params2.messages()[2].content().asBlockParams().single().asText().text().contains("[Sidecar-")`
  is `false`.

### S-ANTH-08 — Mutation-defense: suffix-after-content + uuid continuity
- **Given** the two-turn fixture (distinct sidecars "Volatile Sidecar 1" /
  "Volatile Sidecar 2"), after both turns; `uuid1` = the uuid captured from Turn 1
  U1 (S-ANTH-01).
- **Then (anti-prepend):** `params1.messages()[0]` text **does not** start with
  `"Volatile Sidecar 1"`; it starts with `"Hello Turn 1"` and the index of
  `"[Sidecar-"` is `> 0`. A "reverted-to-prepend" mutant that produces
  `"Volatile Sidecar 1\n\nHello Turn 1"` (sidecar at index 0, no UUID block)
  fails this assertion.
- **And (anti-throwaway-mint):** the uuid extracted from `params2.messages()[0]`
  (Turn 2 U1 frozen replay) equals `uuid1` exactly. A "throwaway-marker-at-freeze"
  mutant (`endTurnFreezeSidecar(..., markerId = SidecarMarkerFactory.newMarker(...).markerId, ...)`)
  freezes a different uuid, so Turn 2 U1's uuid would **not** equal `uuid1` and
  the assertion fails.

---

## 6. Slice 3 — `GoogleInferenceServiceImplSidecarWireTest` (new)

File: `feature/inference/src/test/.../GoogleInferenceServiceImplSidecarWireTest.kt`.
Harness: capture `List<Content>` via `slot<List<Content>>()` on
`coEvery { sdkClient.generateContentStream(...) }` returning
`GoogleSdkResult(emittedAny = true, functionCall = null, assistantContent = null, finishReason = "STOP")`
(see §1). Service constructed with `modelId = "gemini-2.5-flash"`,
`ModelType.MAIN`, `FakeToolEnvelopeParserPort()`, `FakeToolStreamGatePort()`,
`options = GenerationOptions(reasoningBudget = 0)`.

Use **short** content so the prompt-cache token threshold is not met
(`activeCacheName == null`); then `deltaHistory(stableHistory)` returns the full
stable history and `contentsToSend` contains every mapped message — the tail/live
message is the **last** `Content` and frozen-replay anchors appear earlier in the
list. Read text via `content.parts().get()[j].text().get()`. Extract a captured
uuid via `Regex("\\[Sidecar-([0-9a-f-]{36}): ").find(text)!!.groupValues[1]`.

### S-GOOG-01 — Live tail suffix wire shape in the tail `Content` text part
- **Given** a single-turn fixture
  `sessionMessages = [AgentLoopMessage("u1", USER, "Hello Turn 1")]`,
  `systemPrompt = "System Instruction"`, `contextSidecars = "Volatile Sidecar 1"`.
- **When** reading the last captured `Content`'s first text part
  (`contentsSlot.captured.last().parts().get()[0].text().get()`).
- **Then** it equals `"Hello Turn 1\n\n[Sidecar-" + uuid1 + ": Volatile Sidecar 1]"`
  (`uuid1` matches `UUID_REGEX`); the text **ends with** `": Volatile Sidecar 1]"`
  and the index of `"[Sidecar-"` is `> 0` (suffix, not prepend).

### S-GOOG-02 — Frozen replay byte-identity in the text part across turns
- **Given** a two-turn fixture:
  Turn 1 `[u1 "Hello Turn 1"]`, `contextSidecars = "Volatile Sidecar 1"`;
  Turn 2 `[u1 "Hello Turn 1", a1 "Reply Turn 1", u2 "Hello Turn 2"]`,
  `contextSidecars = "Volatile Sidecar 2"`. (Capture each turn's
  `contentsSlot.captured`; the slot holds the most recent call, so snapshot it
  per turn into separate lists.)
- **When** locating the U1 (`id = "u1"`) mapped `Content` in Turn 2's captured
  contents (the entry whose text starts with `"Hello Turn 1"`).
- **Then** its first text part equals Turn 1's tail text part **byte-for-byte**:
  `"Hello Turn 1\n\n[Sidecar-" + uuid1 + ": Volatile Sidecar 1]"`, with the **same**
  `uuid1` captured in S-GOOG-01-style extraction from Turn 1 (frozen replay is
  byte-identical and reuses the live uuid).

### S-GOOG-03 — Deduped turn omits live suffix; frozen anchor replays unchanged (INV-3)
- **Given** a two-turn fixture where **both** turns pass the identical
  `contextSidecars = "Volatile Sidecar 1"`:
  Turn 1 `[u1 "Hello Turn 1"]`; Turn 2 `[u1 "Hello Turn 1", a1 "Reply Turn 1", u2 "Hello Turn 2"]`.
- **When** reading Turn 2's captured contents.
- **Then** the U1 entry's text equals
  `"Hello Turn 1\n\n[Sidecar-" + uuid1 + ": Volatile Sidecar 1]"` (frozen replay,
  unchanged) **and** the tail entry (`u2`) text equals exactly `"Hello Turn 2"` —
  `tailText.contains("[Sidecar-")` is `false` (the live ship was deduped, INV-3).

### S-GOOG-04 — Image-bearing user turn: suffix in the leading text part, image bytes part untouched (DA6)
- **Given** a single-turn fixture `[AgentLoopMessage("u1", USER, "Describe this")]`,
  `contextSidecars = "Volatile Sidecar 1"`,
  `options = GenerationOptions(reasoningBudget = 0, imageUris = listOf(tempJpegUri))`.
- **When** reading the tail `Content`'s parts.
- **Then** `parts.size == 2`; `parts[0].text().get()` equals
  `"Describe this\n\n[Sidecar-" + uuid + ": Volatile Sidecar 1]"` (`uuid` matches
  `UUID_REGEX`); `parts[1].inlineData().isPresent` is `true` (the image bytes part
  is present and unchanged). The leading text part carries the suffix; the image
  part is untouched.

### S-GOOG-05 — Live→frozen `markerId` continuity (uuid equality across turns)
- **Given** the two-turn distinct-sidecar fixture from S-GOOG-02; `uuid1` = the
  uuid extracted from Turn 1's tail text part.
- **When** extracting the uuid from Turn 2's U1 frozen-replay text part.
- **Then** it equals `uuid1` exactly (`assertEquals(uuid1, frozenUuidTurn2)`), and
  `appendOnlyContextForTests()!!.frozenSidecarsForWireReplay()` after Turn 1 has
  size `1` with `markerId == uuid1` (the live uuid was threaded into the freeze).

### S-GOOG-06 — Mutation-defense: suffix-after-content + uuid continuity
- **Given** the two-turn distinct-sidecar fixture (Turn 1 ships "Volatile Sidecar 1";
  Turn 2 ships "Volatile Sidecar 2"); `uuid1` = uuid from Turn 1's tail text part.
- **Then (anti-prepend):** Turn 1's tail text part **does not** start with
  `"Volatile Sidecar 1"`; it starts with `"Hello Turn 1"` and the index of
  `"[Sidecar-"` is `> 0`. A prepend mutant producing
  `"Volatile Sidecar 1\n\nHello Turn 1"` fails.
- **And (anti-throwaway-mint):** the uuid extracted from Turn 2's U1 frozen-replay
  text part equals `uuid1` exactly. A throwaway-marker-at-freeze mutant
  (`endTurnFreezeSidecar(..., markerId = SidecarMarkerFactory.newMarker(wireSidecar.text).markerId, ...)`,
  the current `:327` behavior) freezes a different uuid, so Turn 2 U1's uuid would
  **not** equal `uuid1` and the assertion fails.

---

## 7. Slice 4 — Validation (manual / CI)

### S-VAL-01 — CI-aligned checks green
- **When** running `./gradlew :feature:inference:testDebugUnitTest`,
  `./gradlew :feature:inference:assembleDebug`, `./gradlew ktlintCheck`,
  `./gradlew detekt`.
- **Then** every command exits `0`; `detekt` reports `0` issues
  (`maxIssues: 0`); the `:core:domain:testDebugUnitTest` regression guard (no
  `:core:domain` change expected) also exits `0`.

### S-VAL-02 — Anthropic `cache_control` emulator validation (Open Q1, manual)
- **Given** a release-build emulator session against the live Anthropic path on a
  prompt-cache-eligible model, run ≥3 turns.
- **Then** the cached-prefix hit-rate holds after the one-time prefix→suffix
  migration turn (`cacheReadInputTokens > 0` on turns after the first cached
  turn), and frozen-replay bytes are stable turn-over-turn (the cached prefix is
  not re-created every turn). DA4 stands; no code change.

### S-VAL-03 — Google `CachedContent` reuse emulator validation (Open Q2, manual)
- **Given** a release-build emulator session against the live Google path on a
  cache-eligible model, run ≥3 turns.
- **Then** a session whose cache was created with old prefix bytes does **not**
  serve mismatched cached content after the suffix migration (no error, no stale
  reuse), and fresh sessions rebuild the cache cleanly
  (`createCachedContent` once, then reuse). DA5 stands; no code change.

---

## 8. Coverage map (requirement → scenario)

| Requirement | Scenario(s) |
|-------------|-------------|
| Marker selection (live at tail) | S-SIP-01 |
| Marker selection (frozen at anchor, order) | S-SIP-02, S-SIP-05 |
| Pre-retrieval parity `null` (DA7) | S-SIP-03 |
| Empty when no match | S-SIP-04 |
| Pre-retrieval contract (non-null) | S-SIP-07 |
| No re-mint in selection | S-SIP-06 |
| Anthropic live tail suffix shape (INV-2) | S-ANTH-01 |
| Anthropic frozen replay byte-identity (INV-1) | S-ANTH-02, S-ANTH-07 |
| Anthropic live→frozen markerId continuity (D7) | S-ANTH-02, S-ANTH-08 |
| Anthropic metadata-before-sidecar (INV-2/DA3) | S-ANTH-03 |
| Anthropic new uuid per live ship | S-ANTH-04 |
| Anthropic cache breakpoints unchanged (DA4) | S-ANTH-05 |
| Anthropic image text-block (DA6) | S-ANTH-06 |
| Anthropic dedupe (INV-3) | S-ANTH-07 |
| Anthropic mutation-defense | S-ANTH-08 |
| Google live tail suffix in text part (INV-2) | S-GOOG-01 |
| Google frozen replay byte-identity (INV-1) | S-GOOG-02 |
| Google dedupe omits live (INV-3) | S-GOOG-03 |
| Google image leading text-part untouched (DA6) | S-GOOG-04 |
| Google live→frozen markerId continuity (D7) | S-GOOG-05 |
| Google mutation-defense | S-GOOG-06 |
| CI-aligned validation | S-VAL-01 |
| Anthropic cache emulator (Open Q1) | S-VAL-02 |
| Google cache emulator (Open Q2) | S-VAL-03 |

---

## 9. Self-review

- **Every requested behavior has happy + edge/error coverage:** frozen replay
  (S-ANTH-02/S-GOOG-02), live tail only (S-ANTH-01/S-GOOG-01), deduped turn
  (S-ANTH-07/S-GOOG-03), image messages (S-ANTH-06/S-GOOG-04),
  metadata-before-sidecar (S-ANTH-03), no cache-breakpoint change (S-ANTH-05),
  markerId continuity live→frozen (S-ANTH-02/S-ANTH-08, S-GOOG-05/S-GOOG-06).
- **Mutation-defense scenarios exist and fail named lazy impls:** S-SIP-06
  (re-mint in selection), S-ANTH-08 and S-GOOG-06 (reverted-to-prepend mutant ⇒
  fails the suffix-after-content assertion; throwaway-marker-at-freeze mutant ⇒
  fails the cross-turn uuid-equality assertion). The throwaway-mint mutant is
  exactly today's `AnthropicInferenceServiceImpl.kt:563` /
  `GoogleInferenceServiceImpl.kt:327` behavior.
- **Literal expected values:** unit scenarios use the fixed UUIDs from §2;
  end-to-end provider scenarios assert via `UUID_REGEX` + cross-turn uuid
  equality (continuity), never a fixed uuid, and never values derived from
  production code.
- **Signatures match `spec.md`/`structure.md`/repo:**
  `sidecarMarkersForSessionIndex(...)`, the Anthropic resolve/freeze param
  changes, the Google resolve/`sidecarMarkersForIndex`/`mapMessage` changes, and
  the reused-unchanged shared signatures.
- **No placeholders / generic exception assertions:** no `TBD`/`TODO`/`similar
  to`/`as above`; no bare `assertThrows<Exception>`.

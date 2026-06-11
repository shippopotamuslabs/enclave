# Live Voice Tool Calling — Test Spec (Gherkin)

Ticket: `live-voice-tools`  
Phase: CFAW Phase 2 — Test Spec  
Companion: `plans/live-voice-tools/spec.md`

Every `Then` clause names a **literal** expected value, enum case, JSON field, or exact exception message. Expected values are derived from this spec and `ToolDefinition` constants — never from production code under test.

---

## Test framework and run commands

- **Engine:** JUnit 5 (`org.junit.jupiter.api`, `useJUnitPlatform()` in each module `build.gradle.kts`). Use `@Test`, `@BeforeEach`, `@DisplayName`, `@Nested` (no JUnit 4).
- **Flows:** Turbine (`app.cash.turbine`) for `StateFlow`/`Flow` event-by-event assertions.
- **Coroutines:** `kotlinx-coroutines-test` `runTest` + `StandardTestDispatcher` / `UnconfinedTestDispatcher`; `MainDispatcherRule` (`com.browntowndev.enclave.core.testing.MainDispatcherRule`) for coordinator tests.
- **Mocks/Fakes:** Prefer Fakes per `contracts/UNIT_TESTING.md`. MockK for `LiveVoicePort`, `ToolExecutorPort`, `LiveVoiceSessionUseCase` seams (mirror `LiveVoiceSessionUseCaseTest`, `LiveVoiceCoordinatorTest`).
- **Robolectric:** Not required for this ticket (no Android framework / Room changes).

Run commands (from `AGENTS.md` §4):

| Scope | Command |
|-------|---------|
| Domain unit tests | `./gradlew :core:domain:testDebugUnitTest` |
| Inference unit tests | `./gradlew :feature:inference:testDebugUnitTest` |
| Chat unit tests | `./gradlew :feature:chat:testDebugUnitTest` |
| All unit tests | `./gradlew testDebugUnitTest` |
| Static analysis | `./gradlew detekt` |
| Inference compile smoke | `./gradlew :feature:inference:assembleDebug` |
| App / Hilt graph | `./gradlew :app:assembleDebug` |

Single-class examples:

```bash
./gradlew :core:domain:testDebugUnitTest --tests "*LiveVoiceToolsetTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*OpenAiRealtimeToolsPayloadTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*OpenAiRealtimeEventParsingTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*GeminiLiveToolsPayloadTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*LiveVoicePortImplTest*"
./gradlew :feature:chat:testDebugUnitTest --tests "*LiveVoiceCoordinatorTest*"
```

---

## Scenario index

| ID | Title | Type | Module |
|----|-------|------|--------|
| LV-TOOL-001 | Whitelist contains exactly 11 search tools | Happy | core:domain |
| LV-TOOL-002 | `LiveVoiceSessionUseCase` forwards `LiveVoiceToolset.tools` | Happy | core:domain |
| LV-TOOL-003 | `LiveVoicePortImpl` passes tools to session constructor | Happy | feature:inference |
| LV-TOOL-010 | OpenAI `session.update` includes tools + `tool_choice:auto` | Happy | feature:inference |
| LV-TOOL-011 | Empty tools omits `tools` and `tool_choice` keys | Edge | feature:inference |
| LV-TOOL-012 | Full whitelist produces 11 function declarations in tools array | Happy | feature:inference |
| LV-TOOL-020 | Parser: `function_call_arguments.done` → `ToolCallRequested` | Happy | feature:inference |
| LV-TOOL-021 | Parser: `response.done` function_call fallback → `ToolCallRequested` | Edge | feature:inference |
| LV-TOOL-022 | Parser: malformed tool-call JSON returns empty list | Error | feature:inference |
| LV-TOOL-023 | Parser: Gemini multiple `functionCalls` → multiple events | Edge | feature:inference |
| LV-TOOL-025 | Parser: plain `response.done` still ends with `AssistantResponseComplete` | Regression | feature:inference |
| LV-TOOL-030 | OpenAI `sendToolResult` wire format (2 messages) | Happy | feature:inference |
| LV-TOOL-031 | OpenAI `sendToolResult` after close is no-op | Edge | feature:inference |
| LV-TOOL-040 | Gemini setup includes `functionDeclarations` | Happy | feature:inference |
| LV-TOOL-041 | Gemini schema strips unsupported `format` key | Edge | feature:inference |
| LV-TOOL-050 | Gemini `toolCall` parser → `ToolCallRequested` | Happy | feature:inference |
| LV-TOOL-051 | Gemini `sendToolResult` emits `toolResponse.functionResponses` | Happy | feature:inference |
| LV-TOOL-052 | Gemini `sendToolResult` wraps non-JSON result as error | Error | feature:inference |
| LV-TOOL-060 | Coordinator happy path: execute → `sendToolResult` | Happy | feature:chat |
| LV-TOOL-061 | Coordinator failure sends error JSON, stays in session | Error | feature:chat |
| LV-TOOL-062 | Barge-in cancels in-flight tool job | Edge | feature:chat |
| LV-TOOL-063 | `teardown` cancels in-flight tool job | Edge | feature:chat |
| LV-TOOL-064 | `end()` cancels in-flight tool job | Edge | feature:chat |
| LV-TOOL-065 | Unknown tool name executes without status caption | Edge | feature:chat |
| LV-TOOL-066 | Whitelist tool with null `displayName` skips caption | Edge | feature:chat |
| LV-TOOL-070 | `toolStatusCaption` set during execution, cleared after | Happy | feature:chat |
| LV-TOOL-071 | Overlay renders `toolStatusCaption` text | Happy | feature:chat |
| LV-TOOL-024 | Stale `sessionGeneration` skips `sendToolResult` | Edge | feature:chat |
| LV-TOOL-MUT-001 | Mutation: whitelist must exclude artifact tools | Mutation | core:domain |
| LV-TOOL-MUT-002 | Mutation: use case must not open session with `emptyList()` | Mutation | core:domain |
| LV-TOOL-MUT-003 | Mutation: OpenAI payload must include `tool_choice` | Mutation | feature:inference |
| LV-TOOL-MUT-004 | Mutation: parser must not swallow `function_call_arguments.done` | Mutation | feature:inference |
| LV-TOOL-MUT-005 | Mutation: Gemini setup must include tools when non-empty | Mutation | feature:inference |
| LV-TOOL-MUT-006 | Mutation: port must forward tools to OpenAI session | Mutation | feature:inference |
| LV-TOOL-MUT-007 | Mutation: tool failure must not set `LiveVoiceUiState.Error` | Mutation | feature:chat |

**Total scenarios: 34**

---

## Domain scenarios

### LV-TOOL-001 — Whitelist contents

**Given** `LiveVoiceToolset.tools`  
**When** tool names are collected via `ToolDefinition.name`  
**Then** the set equals exactly:

`web_search`, `web_extract`, `search_chat_history`, `search_chat`, `get_message_context`, `manage_memories`, `search_x_recent`, `x_get_thread`, `x_trending`, `x_user_tweets`, `x_mention_search`

**And** the list size is `11`  
**And** the set does **not** contain `start_artifact`, `append_blocks`, `search_current_artifact`, `generate_image`, `generate_video`, `generate_music`, `search_tools`, `attached_image_inspect`, or `read_context_reference`.

---

### LV-TOOL-002 — Use case forwards tools

**Given** a MockK `LiveVoicePort`, resolved `LIVE_VOICE` asset with credential alias `"alias"`, and API key `"sk-live"`  
**When** `LiveVoiceSessionUseCase()` is invoked  
**Then** `liveVoicePort.openSession(asset, "sk-live", tools = LiveVoiceToolset.tools)` is called exactly once  
**And** the result is `Result.success` wrapping `LiveVoiceSessionOpenResult` with the mocked session and asset.

---

### LV-TOOL-MUT-001 — Mutation: whitelist excludes artifacts

**Given** `LiveVoiceToolset.tools`  
**When** tool names are collected  
**Then** `start_artifact` is not in the list  
**And** `append_blocks` is not in the list  
**And** `search_current_artifact` is not in the list.

*Defense:* Removing an exclusion or adding an artifact tool to `LiveVoiceToolset` breaks this scenario.

---

### LV-TOOL-MUT-002 — Mutation: use case must pass whitelist

**Given** a MockK `LiveVoicePort` and successful resolution path  
**When** `LiveVoiceSessionUseCase()` is invoked  
**Then** `openSession` is called with `tools` argument reference-equal to `LiveVoiceToolset.tools`  
**And** `openSession` is **not** called with `tools = emptyList()`.

*Defense:* Hardcoding `emptyList()` in the use case breaks this scenario.

---

## Inference — OpenAI/xAI scenarios

### LV-TOOL-010 — session.update tools payload

**Given** `tools = listOf(ToolDefinition.WEB_SEARCH)`  
**When** `applyOpenAiRealtimeToolsToSession(sessionBuilder, tools)` is applied to a fresh `session` JSON object  
**Then** `session.tools` is a JSON array of size `1`  
**And** `session.tools[0].type` = `"function"`  
**And** `session.tools[0].name` = `"web_search"`  
**And** `session.tools[0].description` = `"Search the web and return ranked results."`  
**And** `session.tools[0].parameters.type` = `"object"`  
**And** `session.tool_choice` = `"auto"`.

---

### LV-TOOL-011 — Empty tools omitted

**Given** `tools = emptyList<ToolDefinition>()`  
**When** `applyOpenAiRealtimeToolsToSession(sessionBuilder, tools)` is applied  
**Then** the `session` object does **not** contain key `"tools"`  
**And** the `session` object does **not** contain key `"tool_choice"`.

---

### LV-TOOL-012 — Full whitelist tool count

**Given** `tools = LiveVoiceToolset.tools`  
**When** `buildOpenAiRealtimeToolsArray(tools)` is called  
**Then** the returned array size is `11`  
**And** every element has `type` = `"function"`  
**And** the set of `name` values matches the 11 names in `LV-TOOL-001`.

---

### LV-TOOL-020 — function_call_arguments.done parser

**Given** WebSocket JSON:

```json
{"type":"response.function_call_arguments.done","call_id":"call_1","name":"web_search","arguments":"{\"query\":\"cats\"}"}
```

**When** `parseOpenAiRealtimeEvents(json)` is called  
**Then** the result is a list of size `1`  
**And** the sole element is `LiveVoiceEvent.ToolCallRequested(callId="call_1", name="web_search", argumentsJson="{\"query\":\"cats\"}")`.

---

### LV-TOOL-021 — response.done function_call fallback

**Given** WebSocket JSON:

```json
{"type":"response.done","response":{"output":[{"type":"function_call","call_id":"call_2","name":"web_extract","arguments":"{\"url\":\"https://example.com\"}"}]}}
```

**When** `parseOpenAiRealtimeEvents(json)` is called  
**Then** `result[0]` is `LiveVoiceEvent.ToolCallRequested(callId="call_2", name="web_extract", argumentsJson="{\"url\":\"https://example.com\"}")`  
**And** `result.last()` is `LiveVoiceEvent.AssistantResponseComplete`  
**And** the list size is `2`.

---

### LV-TOOL-022 — Malformed tool-call JSON

**Given** WebSocket JSON:

```json
{"type":"response.function_call_arguments.done","call_id":"call_x"}
```

**When** `parseOpenAiRealtimeEvents(json)` is called  
**Then** the result is an empty list.

---

### LV-TOOL-025 — Plain response.done regression

**Given** WebSocket JSON:

```json
{"type":"response.done"}
```

**When** `parseOpenAiRealtimeEvents(json)` is called  
**Then** the result equals `listOf(LiveVoiceEvent.AssistantResponseComplete)`  
**And** `parseOpenAiRealtimeEvent(json)` returns `LiveVoiceEvent.AssistantResponseComplete`.

---

### LV-TOOL-030 — sendToolResult wire format

**Given** an `OpenAiRealtimeSession` with a recording fake WebSocket that captures outbound text frames  
**When** `sendToolResult(callId="call_1", name="web_search", resultJson="{\"hits\":[]}")` is called  
**Then** exactly `2` messages are sent  
**And** message `0` parsed JSON has `type` = `"conversation.item.create"`  
**And** message `0` has `item.type` = `"function_call_output"`  
**And** message `0` has `item.call_id` = `"call_1"`  
**And** message `0` has `item.output` = `"{\"hits\":[]}"`  
**And** message `1` parsed JSON has `type` = `"response.create"`.

---

### LV-TOOL-031 — sendToolResult after close

**Given** an `OpenAiRealtimeSession` whose WebSocket has been closed  
**When** `sendToolResult(callId="call_1", name="web_search", resultJson="{}")` is called  
**Then** zero additional WebSocket frames are sent  
**And** no exception is thrown.

---

### LV-TOOL-MUT-003 — Mutation: tool_choice required

**Given** `tools = listOf(ToolDefinition.WEB_SEARCH)`  
**When** `applyOpenAiRealtimeToolsToSession` is applied  
**Then** `session.tool_choice` = `"auto"`  
**And** omitting `tool_choice` in the implementation fails this assertion.

*Defense:* Sending tools without `tool_choice:"auto"` breaks coordinator tool invocation on some providers.

---

### LV-TOOL-MUT-004 — Mutation: primary parser event

**Given** the JSON from `LV-TOOL-020`  
**When** `parseOpenAiRealtimeEvents(json)` is called  
**Then** the first element is **not** `LiveVoiceEvent.AssistantResponseComplete`  
**And** the first element `name` = `"web_search"`.

*Defense:* Mapping `function_call_arguments.done` to `AssistantResponseComplete` breaks tool execution.

---

## Inference — Gemini scenarios

### LV-TOOL-040 — setup functionDeclarations

**Given** `tools = listOf(ToolDefinition.WEB_SEARCH, ToolDefinition.SEARCH_CHAT)`  
**When** `buildGeminiLiveSetupTools(tools)` is called  
**Then** the returned array has size `1`  
**And** `functionDeclarations` array size is `2`  
**And** declaration names are `"web_search"` and `"search_chat"` in that order.

---

### LV-TOOL-041 — Gemini schema compatibility

**Given** a synthetic `ToolDefinition` whose `parametersJson` contains a property with `"format":"uri"`  
**When** `LiveVoiceGeminiSchemaMapper.toFunctionDeclaration(tool)` is called  
**Then** the `parameters.properties.<key>` object does **not** contain key `"format"`  
**And** the property `type` is one of `STRING`, `INTEGER`, `NUMBER`, `BOOLEAN`, `ARRAY`, `OBJECT`.

---

### LV-TOOL-050 — Gemini toolCall parser

**Given** WebSocket JSON:

```json
{"serverContent":{"toolCall":{"functionCalls":[{"id":"fc_9","name":"manage_memories","args":{"action":"search","query":"favorite color"}}]}}}
```

**When** `parseGeminiLiveEvents(json)` is called  
**Then** the result is a list of size `1`  
**And** the sole element is `LiveVoiceEvent.ToolCallRequested(callId="fc_9", name="manage_memories", argumentsJson="{\"action\":\"search\",\"query\":\"favorite color\"}")`.

---

### LV-TOOL-023 — Gemini multiple functionCalls

**Given** WebSocket JSON:

```json
{"serverContent":{"toolCall":{"functionCalls":[{"id":"fc_1","name":"web_search","args":{"query":"a"}},{"id":"fc_2","name":"web_extract","args":{"url":"https://example.com"}}]}}}
```

**When** `parseGeminiLiveEvents(json)` is called  
**Then** the list size is `2`  
**And** `result[0].callId` = `"fc_1"` and `result[0].name` = `"web_search"`  
**And** `result[1].callId` = `"fc_2"` and `result[1].name` = `"web_extract"`.

---

### LV-TOOL-051 — Gemini sendToolResult

**Given** a `GeminiLiveSession` with a recording fake WebSocket  
**When** `sendToolResult(callId="fc_9", name="manage_memories", resultJson="{\"memories\":[]}")` is called  
**Then** exactly `1` message is sent  
**And** parsed JSON has `toolResponse.functionResponses[0].id` = `"fc_9"`  
**And** `functionResponses[0].name` = `"manage_memories"`  
**And** `functionResponses[0].response` is a JSON object containing key `"memories"` with an empty array value.

---

### LV-TOOL-052 — Gemini non-JSON result wrapper

**Given** a `GeminiLiveSession` with a recording fake WebSocket  
**When** `sendToolResult(callId="fc_err", name="web_search", resultJson="not-json")` is called  
**Then** `toolResponse.functionResponses[0].response.error` = `"not-json"`.

---

### LV-TOOL-MUT-005 — Mutation: Gemini setup includes tools

**Given** `tools = listOf(ToolDefinition.WEB_SEARCH)`  
**When** `buildGeminiLiveSetupTools(tools)` is called  
**Then** the result is non-null  
**And** the JSON contains key `"functionDeclarations"`.

*Defense:* Returning null / omitting tools from setup when tools are non-empty breaks model tool invocation.

---

## Inference — routing scenarios

### LV-TOOL-003 — Port forwards tools

**Given** `LiveVoicePortImpl` and `tools = LiveVoiceToolset.tools`  
**When** `openSession(asset(provider=OPENAI), apiKey="key", tools=tools)` is called  
**Then** the result is `Result.success`  
**And** the session is an `OpenAiRealtimeSession` constructed with the same `tools` list (verify via test-visible constructor arg or behavior assertion that `session.update` includes 11 tools).

---

### LV-TOOL-MUT-006 — Mutation: port must not ignore tools

**Given** `tools = listOf(ToolDefinition.WEB_SEARCH)` and `LiveVoicePortImpl`  
**When** `openSession(asset(OPENAI), "key", tools)` succeeds  
**Then** `buildOpenAiRealtimeToolsArray(tools)` produces a non-null array  
**And** the opened session's first `session.update` payload includes `"web_search"` in `session.tools`.

*Defense:* Opening `OpenAiRealtimeSession(asset, apiKey)` without forwarding `tools` leaves tools array absent.

---

## Coordinator scenarios

### LV-TOOL-060 — Happy path tool execution

**Given** a running `LiveVoiceCoordinator` with `chatId = ChatId("c1")`, provider `OPENAI`, active `FakeLiveVoiceSession`, and `ToolExecutorPort` returning `ToolExecutionResult(toolName="web_search", resultJson="{\"results\":[]}")`  
**When** the session emits `LiveVoiceEvent.ToolCallRequested(callId="call_1", name="web_search", argumentsJson="{\"query\":\"test\"}")` and coroutines advance to completion  
**Then** `toolExecutorPort.execute` is invoked once with `toolName="web_search"`, `argumentsJson="{\"query\":\"test\"}"`, `provider="OPENAI"`, `modelType=ModelType.LIVE_VOICE`, `chatId=ChatId("c1")`  
**And** `fakeSession.sendToolResultInvocations` has size `1` with `(callId="call_1", name="web_search", resultJson="{\"results\":[]}")`.

---

### LV-TOOL-061 — Failure sends error JSON

**Given** a running coordinator and `ToolExecutorPort` that throws `RuntimeException("network down")`  
**When** `ToolCallRequested(callId="call_2", name="web_search", argumentsJson="{}")` is emitted and coroutines advance  
**Then** `sendToolResult` is called once with `resultJson` containing substring `"error"` and substring `"network down"`  
**And** `coordinator.state.value` is **not** an instance of `LiveVoiceUiState.Error`  
**And** `coordinator.state.value` is `LiveVoiceUiState.Listening` or `LiveVoiceUiState.AssistantSpeaking`.

---

### LV-TOOL-062 — Barge-in cancels tool job

**Given** a coordinator with an in-flight tool (`ToolExecutorPort.execute` suspended on `CompletableDeferred`) after `ToolCallRequested` was emitted  
**When** `interruptAssistantOutput` runs via simulated barge-in (`SpeechStarted` or local energy threshold path)  
**Then** the suspended `execute` coroutine is cancelled  
**And** `sendToolResult` is **not** called after cancellation settles.

---

### LV-TOOL-063 — Teardown cancels tool job

**Given** a coordinator with an in-flight tool suspended on `CompletableDeferred`  
**When** `cancel()` is invoked  
**Then** the tool coroutine is cancelled  
**And** `sendToolResult` is **not** called.

---

### LV-TOOL-064 — End cancels tool job

**Given** a coordinator with an in-flight tool suspended on `CompletableDeferred`  
**When** `end(scope)` is invoked  
**Then** the tool coroutine is cancelled before session teardown completes  
**And** `sendToolResult` is **not** called.

---

### LV-TOOL-065 — Unknown tool without caption

**Given** `ToolDefinition.fromName("unknown_tool")` returns `null`  
**When** `ToolCallRequested(callId="call_u", name="unknown_tool", argumentsJson="{}")` is emitted and the executor returns `ToolExecutionResult(toolName="unknown_tool", resultJson="{}")`  
**Then** `toolExecutorPort.execute` is called once  
**And** `coordinator.state.value` (if `Listening`) has `toolStatusCaption = null` throughout execution.

---

### LV-TOOL-024 — Stale generation guard

**Given** a coordinator at `sessionGeneration = 1` with a tool job that completes after `cancel()` bumped generation to `2`  
**When** the delayed executor returns `ToolExecutionResult(toolName="web_search", resultJson="{\"ok\":true}")`  
**Then** `sendToolResult` is **not** called.

---

### LV-TOOL-066 — Null displayName skips caption

**Given** `ToolDefinition.MANAGE_MEMORIES.displayName` is `null`  
**When** `ToolCallRequested(callId="call_mm", name="manage_memories", argumentsJson="{\"action\":\"search\"}")` is emitted and the executor is suspended  
**Then** `coordinator.state.value` (as `LiveVoiceUiState.Listening`) has `toolStatusCaption = null`  
**When** the executor completes with `ToolExecutionResult(toolName="manage_memories", resultJson="{}")`  
**Then** `sendToolResult` is called once  
**And** `toolStatusCaption` remains `null`.

---

### LV-TOOL-070 — Tool status caption lifecycle

**Given** `ToolDefinition.WEB_SEARCH.displayName` = `"Searching the web..."`  
**When** `ToolCallRequested(callId="call_1", name="web_search", argumentsJson="{}")` is emitted and the executor is suspended  
**Then** `coordinator.state.value` (as `LiveVoiceUiState.Listening`) has `toolStatusCaption = "Searching the web..."`  
**When** the executor completes  
**Then** `toolStatusCaption = null`.

---

### LV-TOOL-071 — Overlay renders caption

**Given** `LiveVoiceUiState.Listening(userCaption="", assistantCaption="", isMuted=false, micLevel=0f, toolStatusCaption="Searching the web...")`  
**When** `LiveVoiceOverlay` is composed in a Compose unit test or screenshot/preview harness  
**Then** a `Text` node exists with `text = "Searching the web..."`  
**And** when `toolStatusCaption = null`, that `Text` node is not displayed.

---

### LV-TOOL-MUT-007 — Mutation: tool failure not session error

**Given** `ToolExecutorPort` throws `IllegalStateException("executor broken")`  
**When** `ToolCallRequested` for `web_search` is processed  
**Then** `coordinator.state.value` is **not** `LiveVoiceUiState.Error(message="executor broken")`  
**And** `sendToolResult` is still invoked once with an error JSON payload.

*Defense:* Routing tool exceptions to `handleError` / `LiveVoiceUiState.Error` incorrectly ends the voice session.

---

## Test file mapping

| Scenario IDs | Test class | Module |
|--------------|------------|--------|
| LV-TOOL-001, LV-TOOL-MUT-001 | `LiveVoiceToolsetTest` (new) | `:core:domain` |
| LV-TOOL-002, LV-TOOL-MUT-002 | `LiveVoiceSessionUseCaseTest` | `:core:domain` |
| LV-TOOL-010, LV-TOOL-011, LV-TOOL-012, LV-TOOL-030, LV-TOOL-031, LV-TOOL-MUT-003 | `OpenAiRealtimeToolsPayloadTest` (new) | `:feature:inference` |
| LV-TOOL-020, LV-TOOL-021, LV-TOOL-022, LV-TOOL-025, LV-TOOL-MUT-004 | `OpenAiRealtimeEventParsingTest` | `:feature:inference` |
| LV-TOOL-040, LV-TOOL-051, LV-TOOL-052, LV-TOOL-MUT-005 | `GeminiLiveToolsPayloadTest` (new) | `:feature:inference` |
| LV-TOOL-041 | `LiveVoiceGeminiSchemaMapperTest` (new) | `:feature:inference` |
| LV-TOOL-050, LV-TOOL-023 | `GeminiLiveEventParsingTest` | `:feature:inference` |
| LV-TOOL-003, LV-TOOL-MUT-006 | `LiveVoicePortImplTest` | `:feature:inference` |
| LV-TOOL-060, LV-TOOL-061, LV-TOOL-062, LV-TOOL-063, LV-TOOL-064, LV-TOOL-065, LV-TOOL-066, LV-TOOL-070, LV-TOOL-024, LV-TOOL-MUT-007 | `LiveVoiceCoordinatorTest` | `:feature:chat` |
| LV-TOOL-071 | `LiveVoiceOverlayTest` (new) or Compose UI test in `LiveVoiceCoordinatorTest` via state assertion only; overlay text assertion in dedicated lightweight Compose test | `:feature:chat` |

---

## Implementation task ↔ scenario traceability

| Spec task | Scenario IDs satisfied |
|-----------|------------------------|
| T-1 | LV-TOOL-001, LV-TOOL-MUT-001 |
| T-2 | LV-TOOL-001 |
| T-3 | LV-TOOL-002, LV-TOOL-MUT-002 |
| T-4 | LV-TOOL-010, LV-TOOL-011, LV-TOOL-012, LV-TOOL-030, LV-TOOL-031, LV-TOOL-MUT-003 |
| T-5 | LV-TOOL-020, LV-TOOL-021, LV-TOOL-022, LV-TOOL-025, LV-TOOL-MUT-004 |
| T-6 | LV-TOOL-040, LV-TOOL-041, LV-TOOL-051, LV-TOOL-052, LV-TOOL-MUT-005 |
| T-7 | LV-TOOL-050, LV-TOOL-023 |
| T-8 | LV-TOOL-003, LV-TOOL-MUT-006 |
| T-9 | LV-TOOL-070 |
| T-10 | LV-TOOL-060, LV-TOOL-061, LV-TOOL-062, LV-TOOL-063, LV-TOOL-064, LV-TOOL-065, LV-TOOL-066, LV-TOOL-024, LV-TOOL-MUT-007 |
| T-11 | LV-TOOL-071 |
| T-12 | All |
# Live Voice Tool Calling — Technical Spec

Ticket: `live-voice-tools`  
Phase: CFAW Phase 2 — Spec  
Depends on: `plans/live-voice/spec.md` (merged base), `plans/live-voice-tools/discovery.md`

## 1. Feature summary

Enable **search-oriented tool calling** during Live Voice (realtime speech↔speech) sessions across **OpenAI Realtime**, **xAI Grok Voice**, and **Google Gemini Live**. When the realtime model requests a function call, the app executes it through the existing `ToolExecutorPort` (`CompositeToolExecutor` in `:core:data`) and returns JSON to the provider so the model can speak the answer.

**Scope:** static search-only whitelist (`LiveVoiceToolset`). **No** artifacts, image/media generation, `search_tools` host, or `attached_image_inspect`. **No provider gating** — all three realtime providers receive the same custom `function` tool declarations.

### Confirmed grounding facts (verified against codebase, 2026-06-10)

- `LiveVoicePort.openSession(asset, apiKey)` exists with **no** `tools` parameter
  (`core/domain/.../port/media/LiveVoicePort.kt`).
- `LiveVoiceSessionUseCase` returns `Result<LiveVoiceSessionOpenResult>` (session + asset), not bare session
  (`core/domain/.../usecase/chat/LiveVoiceSessionUseCase.kt`).
- `OpenAiRealtimeSession.sendSessionUpdate` sends VAD + transcription config but **no** `tools` array
  (`feature/inference/.../liveVoice/OpenAiRealtimeSession.kt:132`).
- `GeminiLiveSession.sendSetupMessage` sends model + voice config but **no** `tools`
  (`feature/inference/.../liveVoice/GeminiLiveSession.kt:139`).
- `LiveVoiceEventParsing` maps `response.done`/`response.completed` → `AssistantResponseComplete`; **no** tool-call parsing
  (`feature/inference/.../liveVoice/LiveVoiceEventParsing.kt`).
- `LiveVoiceCoordinator.SessionEventRouter` handles transcript/audio lifecycle only; **no** `ToolExecutorPort`
  (`feature/chat/.../delegates/LiveVoiceCoordinator.kt`).
- `ToolExecutorPort` is bound to `CompositeToolExecutor` in `DataModule.kt` (`@Singleton`).
- Gemini text-chat schema mapping lives in `GoogleRequestMapper.kt` (`toGoogleTool`, `googleToolProperties`, `toGoogleSchemaExt`) — **private/internal**; Live Voice Gemini JSON setup needs a parallel JSON mapper in the same module.
- `ToolCallRequest.provider` is a `String` (use `asset.provider.name`, e.g. `"OPENAI"`).
- `ModelType.LIVE_VOICE` already exists for tagging persisted assistant turns.
- xAI Grok Voice supports `session.update` tools (OpenAI-compatible); confirmed by ticket owner.

---

## 2. File structure tables (Create / Modify + responsibility)

Package root: `com.browntowndev.enclave`. Paths below use module roots.

### Create

| File | Responsibility |
|------|----------------|
| `core/domain/src/main/kotlin/.../model/inference/LiveVoiceToolset.kt` | Static search-only `List<ToolDefinition>` whitelist constant |
| `feature/inference/src/main/kotlin/.../liveVoice/OpenAiRealtimeToolsPayload.kt` | Pure functions: build OpenAI/xAI `session.update` tools JSON; build `conversation.item.create` + `response.create` tool-result messages |
| `feature/inference/src/main/kotlin/.../liveVoice/GeminiLiveToolsPayload.kt` | Pure functions: build Gemini Live `setup.tools` JSON; build `toolResponse` JSON |
| `feature/inference/src/main/kotlin/.../liveVoice/LiveVoiceGeminiSchemaMapper.kt` | Map `ToolDefinition.schema` → Gemini Live JSON `functionDeclarations` entry (OpenAPI subset; strip unsupported keys) |
| `core/domain/src/test/kotlin/.../model/inference/LiveVoiceToolsetTest.kt` | Whitelist + mutation-defense tests (`LV-TOOL-001`, `LV-TOOL-MUT-001`) |
| `feature/inference/src/test/kotlin/.../liveVoice/OpenAiRealtimeToolsPayloadTest.kt` | OpenAI/xAI payload wire-shape tests |
| `feature/inference/src/test/kotlin/.../liveVoice/GeminiLiveToolsPayloadTest.kt` | Gemini setup + toolResponse wire-shape tests |
| `feature/inference/src/test/kotlin/.../liveVoice/LiveVoiceGeminiSchemaMapperTest.kt` | Gemini schema compatibility tests |

### Modify

| File | Responsibility |
|------|----------------|
| `core/domain/src/main/kotlin/.../port/media/LiveVoicePort.kt` | Add `tools` param to `openSession`; add `sendToolResult` on `LiveVoiceSession`; add `ToolCallRequested` event |
| `core/domain/src/main/kotlin/.../usecase/chat/LiveVoiceSessionUseCase.kt` | Pass `LiveVoiceToolset.tools` into `openSession` |
| `feature/inference/src/main/kotlin/.../liveVoice/LiveVoicePortImpl.kt` | Forward `tools` to `OpenAiRealtimeSession` / `GeminiLiveSession` constructors |
| `feature/inference/src/main/kotlin/.../liveVoice/OpenAiRealtimeSession.kt` | Accept `tools`; merge tools into `sendSessionUpdate`; implement `sendToolResult`; emit multi-event parser results |
| `feature/inference/src/main/kotlin/.../liveVoice/GeminiLiveSession.kt` | Accept `tools`; merge tools into `sendSetupMessage`; implement `sendToolResult`; emit multi-event parser results |
| `feature/inference/src/main/kotlin/.../liveVoice/LiveVoiceEventParsing.kt` | Add `parseOpenAiRealtimeEvents` + `parseGeminiLiveEvents` (list-returning); tool-call branches; preserve `parseOpenAiRealtimeEvent`/`parseGeminiLiveEvent` as first-event shims for existing live-voice tests |
| `feature/chat/src/main/kotlin/.../delegates/LiveVoiceCoordinator.kt` | Inject `ToolExecutorPort`; store `currentAsset`; handle `ToolCallRequested` on `activeScope`; `toolJob` cancel on barge-in/teardown/end/cancel; `toolStatusCaption` updates |
| `feature/chat/src/main/kotlin/.../LiveVoiceUiState.kt` | Add `toolStatusCaption: String? = null` to `Listening` and `AssistantSpeaking` |
| `feature/chat/src/main/kotlin/.../components/LiveVoiceOverlay.kt` | Render `toolStatusCaption` when non-null |
| `core/domain/src/test/kotlin/.../usecase/chat/LiveVoiceSessionUseCaseTest.kt` | Assert tools forwarded (`LV-TOOL-002`, `LV-TOOL-MUT-002`) |
| `feature/inference/src/test/kotlin/.../liveVoice/OpenAiRealtimeEventParsingTest.kt` | Function-call parser + `response.done` fallback + regression `AssistantResponseComplete` |
| `feature/inference/src/test/kotlin/.../liveVoice/GeminiLiveEventParsingTest.kt` | Gemini `toolCall` parser + multi-call |
| `feature/inference/src/test/kotlin/.../liveVoice/LiveVoicePortImplTest.kt` | Tools forwarded to session type (`LV-TOOL-003`, `LV-TOOL-MUT-006`) |
| `feature/chat/src/test/kotlin/.../delegates/LiveVoiceCoordinatorTest.kt` | Tool execution, failure, cancel, stale generation, caption tests |

### Delete

None.

---

## 3. API contracts and interfaces

### 3.1 `LiveVoiceToolset`

```kotlin
// core/domain/.../model/inference/LiveVoiceToolset.kt
object LiveVoiceToolset {
    val tools: List<ToolDefinition> = listOf(
        ToolDefinition.WEB_SEARCH,
        ToolDefinition.WEB_EXTRACT,
        ToolDefinition.SEARCH_CHAT_HISTORY,
        ToolDefinition.SEARCH_CHAT,
        ToolDefinition.GET_MESSAGE_CONTEXT,
        ToolDefinition.MANAGE_MEMORIES,
        ToolDefinition.X_SEARCH,
        ToolDefinition.X_GET_THREAD,
        ToolDefinition.X_TRENDING,
        ToolDefinition.X_USER_TWEETS,
        ToolDefinition.X_MENTION_SEARCH,
    )
}
```

Exact tool **names** (11): `web_search`, `web_extract`, `search_chat_history`, `search_chat`, `get_message_context`, `manage_memories`, `search_x_recent`, `x_get_thread`, `x_trending`, `x_user_tweets`, `x_mention_search`.

**Excluded** from whitelist (must not appear): `start_artifact`, `append_blocks`, `search_current_artifact`, `generate_image`, `generate_video`, `generate_music`, `search_tools`, `attached_image_inspect`, `read_context_reference`.

### 3.2 Port changes (`LiveVoicePort.kt`)

```kotlin
interface LiveVoicePort {
    suspend fun openSession(
        asset: LiveVoiceProviderAsset,
        apiKey: String,
        tools: List<ToolDefinition> = emptyList(),
    ): Result<LiveVoiceSession>
}

interface LiveVoiceSession {
    fun events(): Flow<LiveVoiceEvent>
    suspend fun sendAudio(pcm16: ByteArray)
    suspend fun commitTurn()
    suspend fun cancelAssistantResponse()
    suspend fun close()
    suspend fun sendToolResult(callId: String, name: String, resultJson: String)
}

sealed interface LiveVoiceEvent {
    // … existing events unchanged …
    data class ToolCallRequested(
        val callId: String,
        val name: String,
        val argumentsJson: String,
    ) : LiveVoiceEvent
}
```

`sendToolResult` is a no-op when the WebSocket is already closed (does not throw).

### 3.3 `LiveVoiceSessionUseCase`

Only the success path changes:

```kotlin
return liveVoicePort
    .openSession(asset, apiKey, tools = LiveVoiceToolset.tools)
    .map { session -> LiveVoiceSessionOpenResult(session = session, asset = asset) }
```

Failure messages remain unchanged from `plans/live-voice/spec.md` §3.5.

### 3.4 Session constructor signatures

```kotlin
class OpenAiRealtimeSession(
    private val asset: LiveVoiceProviderAsset,
    private val apiKey: String,
    private val tools: List<ToolDefinition> = emptyList(),
) : LiveVoiceSession

class GeminiLiveSession(
    private val asset: LiveVoiceProviderAsset,
    private val apiKey: String,
    private val tools: List<ToolDefinition> = emptyList(),
) : LiveVoiceSession
```

`LiveVoicePortImpl.openSession` passes the `tools` argument through unchanged for all providers.

### 3.5 OpenAI/xAI tools payload (`OpenAiRealtimeToolsPayload.kt`)

```kotlin
/** Returns the JSON array value for session.tools, or null when [tools] is empty. */
fun buildOpenAiRealtimeToolsArray(tools: List<ToolDefinition>): JsonArray?

/** Merges tools + tool_choice into an existing session.update session object. */
fun applyOpenAiRealtimeToolsToSession(
    sessionBuilder: JsonObjectBuilder,
    tools: List<ToolDefinition>,
)

/** Ordered wire messages for tool result submission. */
fun buildOpenAiToolResultMessages(
    callId: String,
    name: String,
    resultJson: String,
): List<String>
```

When `tools` is non-empty, `session.update` must include:

```json
{
  "type": "session.update",
  "session": {
    "tools": [
      {
        "type": "function",
        "name": "web_search",
        "description": "Search the web and return ranked results.",
        "parameters": { "type": "object", "properties": { }, "required": [ ] }
      }
    ],
    "tool_choice": "auto"
  }
}
```

- Each tool's `parameters` object is parsed from `ToolDefinition.parametersJson` (lazy `schema` JSON).
- When `tools` is empty, omit both `tools` and `tool_choice` keys (backward-compatible).

`buildOpenAiToolResultMessages` returns exactly two JSON strings:

1. `{"type":"conversation.item.create","item":{"type":"function_call_output","call_id":"<callId>","output":"<resultJson>"}}`
2. `{"type":"response.create"}`

The `output` field carries `resultJson` as a **string** (not re-parsed).

### 3.6 OpenAI/xAI event parsing (`LiveVoiceEventParsing.kt`)

Add list-returning canonical parsers:

```kotlin
fun parseOpenAiRealtimeEvents(json: String): List<LiveVoiceEvent>
fun parseGeminiLiveEvents(json: String): List<LiveVoiceEvent>
```

Keep existing single-event shims:

```kotlin
fun parseOpenAiRealtimeEvent(json: String): LiveVoiceEvent? =
    parseOpenAiRealtimeEvents(json).singleOrNull()

fun parseGeminiLiveEvent(json: String): LiveVoiceEvent? =
    parseGeminiLiveEvents(json).singleOrNull()
```

**Primary tool-call event** (`response.function_call_arguments.done`):

```json
{
  "type": "response.function_call_arguments.done",
  "call_id": "call_abc",
  "name": "web_search",
  "arguments": "{\"query\":\"weather in NYC\"}"
}
```

→ single-element list: `[ToolCallRequested("call_abc", "web_search", "{\"query\":\"weather in NYC\"}")]`.

**Fallback** (`response.done` / `response.completed` with `response.output[]` function_call items):

- For each output item where `type == "function_call"`, emit `ToolCallRequested(call_id, name, arguments)`.
- **Always** append `AssistantResponseComplete` as the final event (preserves existing `LV-PARSE-OAI-006` coordinator behavior).
- Plain `response.done` with no function_call items → `[AssistantResponseComplete]` only.

`OpenAiRealtimeSession.onMessage` must call `parseOpenAiRealtimeEvents(text).forEach { trySend(it) }`.

### 3.7 Gemini tools payload (`GeminiLiveToolsPayload.kt` + `LiveVoiceGeminiSchemaMapper.kt`)

```kotlin
object LiveVoiceGeminiSchemaMapper {
    fun toFunctionDeclaration(tool: ToolDefinition): JsonObject
}

fun buildGeminiLiveSetupTools(tools: List<ToolDefinition>): JsonArray?

fun buildGeminiToolResponseMessage(
    callId: String,
    name: String,
    resultJson: String,
): String
```

When `tools` is non-empty, `setup` gains:

```json
{
  "setup": {
    "model": "models/gemini-2.0-flash-live-001",
    "tools": [
      {
        "functionDeclarations": [
          {
            "name": "web_search",
            "description": "Search the web and return ranked results.",
            "parameters": { "type": "OBJECT", "properties": { } }
          }
        ]
      }
    ]
  }
}
```

Schema mapping rules (mirror `GoogleRequestMapper.toGoogleSchemaExt` semantics, output JSON not SDK `Schema`):

| JSON Schema `type` | Gemini Live `type` |
|--------------------|--------------------|
| `string` | `STRING` |
| `integer` | `INTEGER` |
| `number` | `NUMBER` |
| `boolean` | `BOOLEAN` |
| `array` | `ARRAY` |
| `object` | `OBJECT` |
| missing / unknown | `STRING` |

Strip unsupported keys: `format`, `default`, `oneOf`, `anyOf`, `allOf`, `$ref`, `additionalProperties`.

`buildGeminiToolResponseMessage` produces:

```json
{
  "toolResponse": {
    "functionResponses": [
      {
        "id": "fc_1",
        "name": "web_search",
        "response": { "result": { } }
      }
    ]
  }
}
```

- When `resultJson` parses as a JSON object, set `response` to that object directly.
- When `resultJson` is not valid JSON object/array, wrap: `{"error":"<resultJson>"}`.

### 3.8 Gemini tool-call event parsing

Input:

```json
{
  "serverContent": {
    "toolCall": {
      "functionCalls": [
        { "id": "fc_1", "name": "web_search", "args": { "query": "weather" } },
        { "id": "fc_2", "name": "web_extract", "args": { "url": "https://example.com" } }
      ]
    }
  }
}
```

→ `parseGeminiLiveEvents` returns two `ToolCallRequested` events with `argumentsJson` as compact JSON serialized from each `args` object.

`GeminiLiveSession.onMessage` calls `parseGeminiLiveEvents(text).forEach { trySend(it) }`.

### 3.9 Coordinator tool execution (`LiveVoiceCoordinator.kt`)

New constructor dependency:

```kotlin
class LiveVoiceCoordinator @Inject constructor(
    // … existing deps …
    private val toolExecutorPort: ToolExecutorPort,
)
```

New fields:

```kotlin
private var currentAsset: LiveVoiceProviderAsset? = null
private var toolJob: Job? = null
private var toolStatusCaption: String? = null
```

On successful `liveVoiceSessionUseCase()` open: `currentAsset = openResult.asset`.

`SessionEventRouter.handle` adds branch:

```kotlin
is LiveVoiceEvent.ToolCallRequested -> coordinator.handleToolCallRequested(event)
```

`handleToolCallRequested` is a **non-suspend** outer-class method that launches work on `activeScope` (must not block the serialized event loop):

```kotlin
private fun handleToolCallRequested(event: LiveVoiceEvent.ToolCallRequested) {
    val scope = activeScope ?: return
    val generation = sessionGeneration
    toolJob?.cancel()
    toolJob = scope.launch {
        val displayName = ToolDefinition.fromName(event.name)?.displayName
        if (displayName != null) {
            toolStatusCaption = displayName
            updateActiveUiState()
        }
        try {
            val result = toolExecutorPort.execute(
                ToolCallRequest(
                    toolName = event.name,
                    argumentsJson = event.argumentsJson,
                    provider = currentAsset?.provider?.name ?: "UNKNOWN",
                    modelType = ModelType.LIVE_VOICE,
                    chatId = chatId,
                ),
            )
            if (generation != sessionGeneration) return@launch
            session?.sendToolResult(event.callId, event.name, result.resultJson)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation != sessionGeneration) return@launch
            val errorJson = buildToolErrorJson(e.message)
            runCatching { session?.sendToolResult(event.callId, event.name, errorJson) }
        } finally {
            toolStatusCaption = null
            updateActiveUiState()
            toolJob = null
        }
    }
}
```

`buildToolErrorJson(message: String?)` returns `{"error":"<escaped message or tool execution failed>"}` using JSON-safe escaping.

**Cancel `toolJob`** in: `interruptAssistantOutput`, `stopJobsAndPlayback`, `cancel()`, and before starting a new tool job.

**Stale generation guard:** if `sessionGeneration` changed before `sendToolResult`, skip the wire send.

**Tool failures do not** transition to `LiveVoiceUiState.Error`; only session-fatal `LiveVoiceEvent.Error` does (unchanged).

`updateActiveUiState` must thread `toolStatusCaption` through `Listening.copy(...)` and `AssistantSpeaking.copy(...)`.

State machine remains in `Listening` during tool execution (no new UI phase).

### 3.10 UI state (`LiveVoiceUiState.kt`)

```kotlin
data class Listening(
    val userCaption: String,
    val assistantCaption: String,
    val isMuted: Boolean,
    val micLevel: Float,
    val toolStatusCaption: String? = null,
)

data class AssistantSpeaking(
    val userCaption: String,
    val assistantCaption: String,
    val isMuted: Boolean,
    val audioLevel: Float,
    val toolStatusCaption: String? = null,
)
```

All coordinator constructors of these states must pass `toolStatusCaption` explicitly or via `updateActiveUiState`.

---

## 4. Permissions & config delta

| Area | Change |
|------|--------|
| `AndroidManifest.xml` | None |
| Gradle dependencies | None |
| Room / database schema | None |
| ProGuard / R8 rules | None |
| Hilt bindings | None new — `ToolExecutorPort` already `@Singleton`; coordinator gains `@Inject ToolExecutorPort` |
| Feature flags | None |
| Network permissions | Unchanged (BYOK WSS already required by live-voice base) |

---

## 5. Visual spec (Compose)

`LiveVoiceOverlay` (`feature/chat/.../components/LiveVoiceOverlay.kt`):

- Read `toolStatusCaption` from `LiveVoiceUiState.Listening` or `LiveVoiceUiState.AssistantSpeaking` (whichever is active).
- When `toolStatusCaption != null`, render one centered line **between** the status title ("Listening" / "Assistant speaking") and the caption cards.
- Style: `MaterialTheme.typography.bodySmall`, `color = MaterialTheme.colorScheme.onSurfaceVariant`, `textAlign = TextAlign.Center`, `fontStyle = FontStyle.Italic`.
- Text content: `toolStatusCaption` verbatim (e.g. `"Searching the web..."` from `ToolDefinition.WEB_SEARCH.displayName`).
- No spinner/icon in v1.
- While a tool runs, keep the listening waveform visible (do not switch to assistant orb solely for tool status).

---

## 6. Constitution audit

| Rule (`AGENTS.md` / contracts) | Compliance |
|--------------------------------|------------|
| Clean Architecture — ports in `:core:domain` | `LiveVoicePort`, `ToolCallRequested`, `LiveVoiceToolset` in domain |
| Dependency direction inward | `:feature:chat` → `ToolExecutorPort` (domain); `:feature:inference` → domain |
| No domain models in composables | `toolStatusCaption` is a `String?` on presentation `LiveVoiceUiState` |
| No `NavController` in composables | Unchanged |
| Schema lock — no transient DB state | Tool status in-memory only; no Room migration |
| Test coverage mandate (`UNIT_TESTING.md`) | Unit tests per §2 manifest; JUnit 5 + Turbine + MockK |
| On-device first / BYOK isolation | Reuses existing cloud tool executors; no on-device inference change |
| AndroJack Level 3 (`AGENTS.md` §3.2) | Mandatory for all generated Kotlin |

Constitution version: root `AGENTS.md` + `/contracts/*` as referenced in `AGENTS.md` §1.

---

## 7. Cross-spec dependencies

| Dependency | Relationship |
|------------|--------------|
| `plans/live-voice/spec.md` | **Hard prerequisite** — Live Voice port, sessions, coordinator, overlay must exist |
| `CompositeToolExecutor` / `ToolExecutorPort` | Stable routing for all whitelist tool names |
| `ToolDefinition` constants | Source of truth for names, schemas, `displayName` |
| `GoogleRequestMapper.kt` | Reference for Gemini type mapping; not called directly (private helpers) |
| In-flight specs | None |

---

## 8. Drift and escalation rules

1. **Parser type strings only:** If OpenAI/xAI/Gemini rename tool-call event fields, change only `LiveVoiceEventParsing.kt` + tests. Do not alter port/coordinator contracts.
2. **`response.done` dual semantics:** `parseOpenAiRealtimeEvents` must preserve `AssistantResponseComplete` for plain `response.done` (regression `LV-PARSE-OAI-006` / `LV-TOOL-025`). Tool-call extraction is additive.
3. **No native xAI `web_search`/`x_search` tool types:** Use custom `function` tools via `CompositeToolExecutor` for cross-provider parity.
4. **No `search_tools` host:** Whitelist is static; do not add dynamic tool discovery to Live Voice.
5. **No Room persistence for tool calls:** Tool I/O is transient; only spoken transcript persists via `PersistVoiceTurnUseCase`.
6. **Serialized event router:** Never call `toolExecutorPort.execute` inline inside `SessionEventRouter.handle` — always `activeScope.launch`.
7. **Gemini schema incompatibility:** If a whitelist tool schema is rejected by Gemini Live, fix `LiveVoiceGeminiSchemaMapper` — do not remove the tool from the whitelist without product approval.
8. **Third session class:** If a provider cannot share OpenAI or Gemini adapters, STOP and escalate before adding a third session implementation.

---

## 9. Ordered implementation tasks

Module order: domain → inference → chat wiring → UI → tests. Each task lists scenario IDs from `test_spec.md`.

### Domain (`:core:domain`)

| # | Task | Scenarios |
|---|------|-----------|
| T-1 | Create `LiveVoiceToolset` with 11 search tools | `LV-TOOL-001`, `LV-TOOL-MUT-001` |
| T-2 | Extend `LiveVoicePort.openSession(tools)`; add `LiveVoiceSession.sendToolResult`; add `LiveVoiceEvent.ToolCallRequested` | `LV-TOOL-001` |
| T-3 | Update `LiveVoiceSessionUseCase` to pass `LiveVoiceToolset.tools` | `LV-TOOL-002`, `LV-TOOL-MUT-002` |

### Inference (`:feature:inference`)

| # | Task | Scenarios |
|---|------|-----------|
| T-4 | Add `OpenAiRealtimeToolsPayload` (tools array + tool-result messages) + tests | `LV-TOOL-010`, `LV-TOOL-011`, `LV-TOOL-012`, `LV-TOOL-030`, `LV-TOOL-031`, `LV-TOOL-MUT-003` |
| T-5 | Add `parseOpenAiRealtimeEvents` tool-call branches; keep `response.done` → `AssistantResponseComplete`; update `OpenAiRealtimeSession` | `LV-TOOL-020`, `LV-TOOL-021`, `LV-TOOL-022`, `LV-TOOL-025`, `LV-TOOL-MUT-004` |
| T-6 | Add `LiveVoiceGeminiSchemaMapper` + `GeminiLiveToolsPayload` + tests | `LV-TOOL-040`, `LV-TOOL-041`, `LV-TOOL-051`, `LV-TOOL-052`, `LV-TOOL-MUT-005` |
| T-7 | Add `parseGeminiLiveEvents` toolCall branch; update `GeminiLiveSession` | `LV-TOOL-050`, `LV-TOOL-023` |
| T-8 | Update `LiveVoicePortImpl` + session constructors to accept and forward `tools` | `LV-TOOL-003`, `LV-TOOL-MUT-006` |

### Chat (`:feature:chat`)

| # | Task | Scenarios |
|---|------|-----------|
| T-9 | Add `toolStatusCaption` to `LiveVoiceUiState.Listening` / `AssistantSpeaking` | `LV-TOOL-070`, `LV-TOOL-071` |
| T-10 | Inject `ToolExecutorPort` into `LiveVoiceCoordinator`; implement async tool loop + cancel + stale guard | `LV-TOOL-060`, `LV-TOOL-061`, `LV-TOOL-062`, `LV-TOOL-063`, `LV-TOOL-064`, `LV-TOOL-065`, `LV-TOOL-066`, `LV-TOOL-024`, `LV-TOOL-MUT-007` |
| T-11 | Render `toolStatusCaption` in `LiveVoiceOverlay` | `LV-TOOL-071` |

### Validation

| # | Task | Scenarios |
|---|------|-----------|
| T-12 | Run targeted unit tests + detekt | All |

**Total tasks: 12**

---

## 10. Out of scope (explicit)

- Native provider built-in search tools (`web_search`, `x_search` MCP types on xAI).
- MCP tool transport.
- `search_tools` dynamic activation host.
- Artifact tools (`start_artifact`, `append_blocks`, `search_current_artifact`).
- Image/video/music generation tools.
- Persisting tool-call rows to Room or transcript UI.
- App-wide `CoroutineScope` for tools (session `activeScope` only).
- Provider-specific tool whitelists or gating.

---

## 11. Rollback plan

Not Tier 3. Rollback = revert the feature branch. No schema migration to undo.
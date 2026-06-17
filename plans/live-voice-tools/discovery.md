# Live Voice Tool Calling — Discovery

Ticket: `live-voice-tools`  
Phase: CFAW Phase 1 — Discovery  
Date: 2026-06-10

---

## 1. Tier classification

**Tier 2 — Modular**

| Signal | Assessment |
|--------|------------|
| Domain port contract change | `LiveVoicePort`, `LiveVoiceSession`, `LiveVoiceEvent` gain tool parameters and events |
| Cross-layer coordination | Domain whitelist → session adapters → coordinator executor wiring |
| No schema/migration | No Room changes |
| No new dependencies | Reuses `CompositeToolExecutor` via existing `ToolExecutorPort` singleton |
| Public API surface | `LiveVoicePort.openSession` signature change (internal to app; port is domain) |

---

## 2. Problem statement

Live Voice sessions are thin WebSocket wrappers with zero tool support. The model cannot search the web, chat history, memories, or X during a voice conversation. Text chat already routes tool calls through `CompositeToolExecutor`; voice must reuse that path.

**Related bug (fixed separately):** Voice first-turn title generation was missing from `PersistVoiceTurnUseCase`; text path already launches `GenerateChatTitleUseCase` in `ProcessPromptWithSafetyGateUseCase`.

---

## 3. Existing subsystem map

### 3.1 Session stack (no tools today)

| File | Role |
|------|------|
| `core/domain/.../port/media/LiveVoicePort.kt` | Port: `openSession(asset, apiKey)`; sealed `LiveVoiceEvent`; `LiveVoiceSession` send/close |
| `core/domain/.../usecase/chat/LiveVoiceSessionUseCase.kt` | Resolves LIVE_VOICE default → API key → `liveVoicePort.openSession` |
| `feature/inference/.../liveVoice/LiveVoicePortImpl.kt` | Routes OPENAI/XAI → `OpenAiRealtimeSession`, GOOGLE → `GeminiLiveSession` |
| `feature/inference/.../liveVoice/OpenAiRealtimeSession.kt` | `session.update` on connect; no `tools` array |
| `feature/inference/.../liveVoice/GeminiLiveSession.kt` | `setup` message on connect; no `tools` |
| `feature/inference/.../liveVoice/LiveVoiceEventParsing.kt` | Parses transcripts/audio/VAD; no function-call events |
| `feature/chat/.../delegates/LiveVoiceCoordinator.kt` | `SessionEventRouter` handles transcript/audio lifecycle; no tool execution |

### 3.2 Tool execution stack (reuse)

| File | Role |
|------|------|
| `core/data/.../repository/CompositeToolExecutor.kt` | `@Singleton` `ToolExecutorPort`; routes by tool name |
| `core/domain/.../model/inference/ToolDefinition.kt` | Tool schemas, `displayName` for UI status strings |
| `core/domain/.../model/inference/ToolCallRequest.kt` | `toolName`, `argumentsJson`, `provider`, `modelType`, `chatId` |
| `core/domain/.../model/inference/ToolExecutionResult.kt` | `resultJson` returned to model |
| `core/data/.../di/DataModule.kt:656` | `CompositeToolExecutor` bound as `ToolExecutorPort` |

### 3.3 Provider tool protocol support (confirmed)

- **OpenAI Realtime:** `session.update` accepts `tools: [{type:"function", name, description, parameters}]` + `tool_choice:"auto"`. Function-call lifecycle: `response.function_call_arguments.done` → return via `conversation.item.create` `{type:"function_call_output", call_id, output}` + `response.create`.
- **xAI Grok Voice:** OpenAI-compatible; `session.update` supports `tools` including native `web_search`, `x_search`, `function`, `mcp`. Same event schema. **No provider gating required.**
- **Gemini Live:** `setup.tools: [{functionDeclarations:[{name, description, parameters}]}]`. Tool calls in `serverContent.toolCall.functionCalls[]`. Results via `toolResponse.functionResponses[{id, name, response}]`. Schema must be Gemini OpenAPI subset (reuse `GoogleRequestMapper.toGoogleTool()` property mapping).

### 3.4 UI state

| File | Role |
|------|------|
| `feature/chat/.../LiveVoiceUiState.kt` | `Listening` / `AssistantSpeaking` carry `userCaption`, `assistantCaption` — no tool-status field |
| `feature/chat/.../components/LiveVoiceOverlay.kt` | Renders captions; can show tool status via `assistantCaption` or new field |

---

## 4. Architectural constraints (Constitution)

- Domain port changes stay in `:core:domain`; session impls in `:feature:inference`; coordinator in `:feature:chat`.
- Coordinator injects `ToolExecutorPort` (domain port), not `CompositeToolExecutor` directly.
- Tool execution must **not** block `SessionEventRouter.handle` — launch on `activeScope` (seconds-long network I/O would starve audio chunk handling).
- No new DB tables/columns for transient tool state.
- `ModelType.LIVE_VOICE` already exists for tagging assistant messages.

---

## 5. Scope decisions

### In scope (search-only whitelist)

Static constant `LiveVoiceToolset` in `:core:domain`:

| Tool | `ToolDefinition` constant | Rationale |
|------|---------------------------|-----------|
| `web_search` | `WEB_SEARCH` | Core search |
| `web_extract` | `WEB_EXTRACT` | Read URLs from search |
| `search_chat_history` | `SEARCH_CHAT_HISTORY` | Past conversation lookup |
| `search_chat` | `SEARCH_CHAT` | In-chat search after summarization |
| `get_message_context` | `GET_MESSAGE_CONTEXT` | Surrounding message context |
| `manage_memories` | `MANAGE_MEMORIES` | Memory search/save (covers memory search) |
| `search_x_recent` | `X_SEARCH` | X search |
| `x_get_thread` | `X_GET_THREAD` | Optional: thread read |
| `x_trending` | `X_TRENDING` | Optional: trends |
| `x_user_tweets` | `X_USER_TWEETS` | Optional: user timeline |
| `x_mention_search` | `X_MENTION_SEARCH` | Optional: mentions |

**Excluded:** artifacts (`start_artifact`, `append_blocks`, `search_current_artifact`), image/media gen, `search_tools` host, `attached_image_inspect`.

### Tool-status UI

**In scope (recommended):** Add optional `toolStatusCaption: String?` to `LiveVoiceUiState.Listening` (and `AssistantSpeaking` if needed). Coordinator sets it from `ToolDefinition.displayName` while a tool job is in-flight. Overlay shows it below assistant caption. State machine stays in `Listening` during tool execution.

---

## 6. Risk factors

| Risk | Mitigation |
|------|------------|
| Serialized event handling starves audio | Launch tool exec on `activeScope`, not inline in router |
| Nav-away / barge-in mid-tool | Cancel `toolJob` on interrupt/teardown; send error JSON if call already dispatched |
| Gemini schema incompatibility | Reuse `ToolDefinition.googleToolProperties()` / `toGoogleSchemaExt()` from `GoogleRequestMapper` |
| xAI payload shape drift | Parser tests + fallback `response.done` function_call items |
| Provider rejects unknown tools | Whitelist is static and pre-tested; log `Error` event on session rejection |

---

## 7. Test surface (existing harness)

| Test file | Extend for |
|-----------|------------|
| `OpenAiRealtimeEventParsingTest` | `function_call_arguments.done`, `response.done` fallback |
| `GeminiLiveEventParsingTest` | `toolCall.functionCalls` |
| `OpenAiRealtimeVadConfigTest` / new payload test | `session.update` tools array shape |
| New `OpenAiRealtimeSessionToolsTest` | `sendToolResult` wire format |
| New `GeminiLiveSessionToolsTest` | setup tools + `sendToolResult` |
| `LiveVoiceCoordinatorTest` | ToolCallRequested → executor → sendToolResult; failure path; cancel on barge-in |
| `LiveVoiceSessionUseCaseTest` | Passes whitelist to `openSession` |
| `LiveVoicePortImplTest` | Forwards tools to session factory |

---

## 8. Cross-spec dependencies

- Depends on completed `live-voice` base feature (`plans/live-voice/spec.md`).
- Depends on `CompositeToolExecutor` tool routing (stable).
- No dependency on in-flight specs.

---

## 9. Implementation ordering (recommended PR sequence)

1. **Domain contracts** — `LiveVoiceToolset`, port/session/event changes
2. **Session adapters** — OpenAI/xAI session.update + parser + sendToolResult; Gemini setup + parser + sendToolResult
3. **Use case wiring** — `LiveVoiceSessionUseCase` passes whitelist
4. **Coordinator** — Tool execution loop + cancel + optional UI status
5. **Overlay** — Render `toolStatusCaption` (if approved)
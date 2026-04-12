# Technical Specification: Shared and Remote Search Skill

## 1. Objective
Implement Step 1 of the search-skill feature for shared contracts and remote providers. This ticket must add the stable domain tool types, expose search capability from `GenerateChatResponseUseCase` for non-local models, inject one shared stub executor into the supported remote inference services, and complete provider-native tool loops for OpenAI-compatible providers and Anthropic.

Acceptance criteria:
- `GenerationOptions` can advertise one canonical `tavily_web_search` tool without changing `InferenceEvent`, `Role`, `Message`, `MessageEntity`, or `PocketCrewDatabase`.
- `InferenceFactoryImpl` wires a shared executor into `ApiInferenceServiceImpl`, `OpenRouterInferenceServiceImpl`, `XaiInferenceServiceImpl`, and `AnthropicInferenceServiceImpl`.
- OpenAI-compatible providers complete one native tool round trip through `BaseOpenAiSdkInferenceService`.
- Anthropic completes one `tool_use` / `tool_result` round trip.
- Tool calls and tool results stay transient and never reach persisted chat data.

## 2. System Architecture

### Target Files
- Modify `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/GenerationOptions.kt`
- Create `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/ToolDefinition.kt`
- Create `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/ToolCallRequest.kt`
- Create `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/ToolExecutionResult.kt`
- Create `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/inference/ToolExecutorPort.kt`
- Modify `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/GenerateChatResponseUseCase.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/InferenceFactoryImpl.kt`
- Create `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/StubSearchToolExecutor.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/BaseOpenAiSdkInferenceService.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/OpenAiRequestMapper.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/ApiInferenceServiceImpl.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/OpenRouterInferenceServiceImpl.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/XaiInferenceServiceImpl.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/AnthropicRequestMapper.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/AnthropicInferenceServiceImpl.kt`
- Modify `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/model/inference/GenerationOptionsTest.kt`
- Modify `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/InferenceFactoryImplTest.kt`
- Modify `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/BaseOpenAiSdkInferenceServiceTest.kt`
- Modify `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/OpenAiRequestMapperTest.kt`
- Modify `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/AnthropicRequestMapperTest.kt`
- Modify `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/AnthropicInferenceServiceImplTest.kt`
- Modify `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/ApiInferenceServiceImplTest.kt`

### Component Boundaries
`core/domain` owns only the capability contract: tool definitions, tool call request/result types, and the `GenerationOptions` fields used to advertise search support. `GenerateChatResponseUseCase` decides whether search is available for the active configuration, but it does not execute tools. `feature/inference` owns all transport-specific behavior. `StubSearchToolExecutor` implements `ToolExecutorPort` and returns the fixed JSON contract. `BaseOpenAiSdkInferenceService` and `AnthropicInferenceServiceImpl` own the runtime loop that detects a tool call, executes the shared executor, replays the result, and emits only assistant-facing text.

## 3. Data Models & Schemas
Reuse `GenerationOptions`, `ChatMessage`, `Role`, `InferenceEvent`, and the existing logging contract. Extend `GenerationOptions` with:
- `availableTools: List<ToolDefinition> = emptyList()`
- `toolingEnabled: Boolean = false`

Add:
- `ToolDefinition(name: String, description: String, parametersJson: String)`
- `ToolCallRequest(toolName: String, argumentsJson: String, provider: String, modelType: ModelType)`
- `ToolExecutionResult(toolName: String, resultJson: String)`
- `ToolExecutorPort.execute(request: ToolCallRequest): ToolExecutionResult`

`ToolDefinition.kt` must expose one canonical `tavily_web_search` definition whose schema requires a string `query` field. `StubSearchToolExecutor` must return the fixed JSON payload from discovery without variation.

## 4. API Contracts & Interfaces
`GenerateChatResponseUseCase` keeps its public signature unchanged but must set `toolingEnabled = true` and `availableTools = listOf(tavily_web_search)` only when the selected model is non-local and the feature is enabled. `InferenceFactoryImpl` must accept one `ToolExecutorPort` and pass it into the OpenAI-compatible and Anthropic services.

`BaseOpenAiSdkInferenceService` must:
- advertise `availableTools`
- detect one tool request from the Responses API path
- call `ToolExecutorPort.execute`
- submit the tool result back into the active response flow
- stop only after final assistant text is produced

`AnthropicRequestMapper` and `AnthropicInferenceServiceImpl` must:
- serialize the canonical tool definition
- map assistant `tool_use`
- map user `tool_result`
- continue until final text is emitted

Typed failures:
- unknown tool name -> `IllegalArgumentException`
- missing or blank `query` -> `IllegalArgumentException`
- recursive second tool request in one turn -> `IllegalStateException`

## 5. Permissions & Config Delta
No permissions or config changes. No manifest edits, Gradle changes, ProGuard changes, database schema changes, or settings changes are allowed in this ticket.

## 6. Constitution Audit
This design adheres to the project's core architectural rules by keeping shared contracts in `:core:domain`, provider execution in `:feature:inference`, leaving persistence models untouched, and preserving the existing `LlmInferencePort` and `InferenceEvent` boundaries.

## 7. Cross-Spec Dependencies
No cross-spec dependencies. This ticket is the base dependency for `13B-google-and-local-envelope-search-skill`, `13C-llama-search-tool-bridge`, and `13D-real-tavily-and-settings`.

# Technical Specification: Search Skill for Models

## 1. Objective
Implement the search-skill architecture described in `plans/13-add-search-skill-for-models/discovery.md` across both BYOK and on-device inference paths. The feature has two implementation stages inside the same product scope:

- Step 1 introduces shared tool abstractions, a deterministic stub search executor, provider-native tool loops for remote APIs, one shared local `<tool_call>...</tool_call>` envelope strategy for on-device runtimes, prompt composition for local tool contracts, and an explicit non-goal that tool traces are not persisted.
- Step 2 replaces the stub executor with real Tavily integration, adds Tavily data/network wiring, exposes Tavily enablement and key storage through settings, and preserves the same tool result contract used in Step 1.

Acceptance criteria:

- `GenerationOptions` and related domain contracts can advertise search-tool capability without changing `InferenceEvent` or chat persistence contracts.
- `InferenceFactoryImpl` injects one shared tool executor into all remote and local inference services that need search-skill behavior.
- OpenAI-compatible providers use native tool/function-calling semantics, Anthropic uses `tool_use` and `tool_result`, Google uses a tool-capable path instead of the current streaming-only path, and on-device runtimes use one shared local tool-call envelope.
- LiteRT, MediaPipe, and llama.cpp suppress raw tool JSON from user-visible output, execute the shared tool executor, inject the tool result back into the live session, and continue generation.
- `GenerateChatResponseUseCase` composes local tool instructions without mutating persisted prompt templates and keeps tool traces transient.
- Tavily wiring reuses the app `OkHttpClient`, stores the key through the existing secure key flow, and exposes enablement through settings models and settings UI.
- `Role`, `Message`, `MessageEntity`, `PocketCrewDatabase`, and `LlmInferencePort` remain unchanged for this feature.

This discovery scope cannot be represented honestly as a single Tier 3 implementation ticket. The correct synthitect outcome is escalation and decomposition, not reduction of scope.

## 2. System Architecture

### Target Files
Full discovery scope requires the following exact target files. This is 47 target files, which exceeds the Tier 3 limit of 25:

- Modify `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/GenerationOptions.kt`
- Create `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/ToolDefinition.kt`
- Create `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/ToolCallRequest.kt`
- Create `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/ToolExecutionResult.kt`
- Create `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/inference/ToolExecutorPort.kt`
- Create `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/SearchToolPromptComposer.kt`
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
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/GoogleRequestMapper.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/GoogleInferenceServiceImpl.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/LiteRtInferenceServiceImpl.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/ConversationManagerImpl.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/ConversationImpl.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/MediaPipeInferenceServiceImpl.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/LlamaInferenceServiceImpl.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/llama/LlamaChatSessionManager.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/llama/JniLlamaEngine.kt`
- Modify `llama-android/src/main/cpp/llama-jni.cpp`
- Modify `llama-android/src/main/cpp/token_processor.hpp`
- Create `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/TavilySearchRepository.kt`
- Modify `app/src/main/kotlin/com/browntowndev/pocketcrew/app/NetworkModule.kt`
- Modify `core/data/build.gradle.kts`
- Modify `gradle/libs.versions.toml`
- Modify `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/ApiKeyManager.kt`
- Modify `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/SettingsRepository.kt`
- Modify `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/SettingsRepositoryImpl.kt`
- Modify `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/settings/SettingsWorkflowModels.kt`
- Modify `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModel.kt`
- Modify `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokConfigureScreen.kt`
- Modify `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokConfigureForms.kt`
- Modify `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/BaseOpenAiSdkInferenceServiceTest.kt`
- Modify `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/AnthropicInferenceServiceImplTest.kt`
- Create `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/GoogleInferenceServiceImplTest.kt`
- Modify `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/LiteRtInferenceServiceImplTest.kt`
- Create `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/MediaPipeInferenceServiceImplTest.kt`
- Modify `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/llama/JniLlamaEngineGenerateWithOptionsTest.kt`
- Modify `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/llama/LlamaChatSessionManagerTest.kt`
- Modify `llama-android/src/main/cpp/test_token_processor.cpp`
- Create `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/TavilySearchRepositoryTest.kt`

Proposed decomposition that preserves the discovery map:

- `13A-shared-and-remote-search-skill`: shared tool contracts, prompt composition, stub executor, factory injection, OpenAI/OpenRouter/xAI, Anthropic, shared remote tests.
- `13B-google-and-local-envelope-search-skill`: Google tool path, LiteRT, MediaPipe, local prompt contract, local parser/continuation tests.
- `13C-llama-search-tool-bridge`: llama service, JNI bridge, native parser, native/unit tests.
- `13D-real-tavily-and-settings`: Tavily repository, network/config changes, secure key storage, settings workflow and UI, repository tests.

### Component Boundaries
`core/domain` owns the stable capability contract: `GenerationOptions` advertises available tools, `ToolDefinition` carries provider-facing schema metadata, `ToolCallRequest` and `ToolExecutionResult` carry typed execution requests/results, and `ToolExecutorPort` abstracts execution without exposing transport details. `GenerateChatResponseUseCase` remains the orchestration boundary that reads active model configuration, decides whether to enable search, and composes a local tool-call contract into the effective system prompt without mutating `SystemPromptTemplates` or persisted chat data.

`feature/inference` owns all provider-specific execution behavior. `InferenceFactoryImpl` injects one shared tool executor into every remote and local inference service constructor. `BaseOpenAiSdkInferenceService` and `OpenAiRequestMapper` own the OpenAI-family loop and schema mapping. `AnthropicInferenceServiceImpl` and `AnthropicRequestMapper` own Anthropic `tool_use`/`tool_result` mapping. `GoogleInferenceServiceImpl` and `GoogleRequestMapper` own a tool-capable Google execution path that can diverge from the current streaming-only implementation. `LiteRtInferenceServiceImpl`, `MediaPipeInferenceServiceImpl`, `ConversationManagerImpl`, `ConversationImpl`, `LlamaInferenceServiceImpl`, `LlamaChatSessionManager`, `JniLlamaEngine`, `llama-jni.cpp`, and `token_processor.hpp` own the shared local envelope loop: detect `<tool_call>`, pause visible output, execute the tool, inject the result, and resume.

`core/data` owns the real Tavily networking and secure key access. `TavilySearchRepository` uses the shared `OkHttpClient` from `NetworkModule`, keeps response mapping stable with the Step 1 stub shape, and exposes only mapped search results to the executor layer. `ApiKeyManager`, `SettingsRepository`, `SettingsRepositoryImpl`, `SettingsWorkflowModels`, `SettingsViewModel`, and the BYOK settings UI own user configuration for search enablement and the Tavily key alias.

Non-goals that remain outside this feature even after split:

- No changes to `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/chat/Role.kt`
- No changes to `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/chat/Message.kt`
- No changes to `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/MessageEntity.kt`
- No changes to `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/PocketCrewDatabase.kt`
- No changes to `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/inference/LlmInferencePort.kt`

## 3. Data Models & Schemas
Reuse the existing `GenerationOptions`, `ChatMessage`, `Role`, `InferenceEvent`, and settings workflow models. Extend them with the following new feature contracts:

- `ToolDefinition(name: String, description: String, parametersJson: String, transport: ToolTransport)`
- `ToolCallRequest(toolName: String, argumentsJson: String, conversationId: String?, modelType: ModelType, provider: String)`
- `ToolExecutionResult(toolName: String, resultJson: String, cached: Boolean, latencyMs: Long)`
- `ToolExecutorPort.execute(request: ToolCallRequest): ToolExecutionResult`

`GenerationOptions` must carry all capability metadata needed by both remote and local runtimes:

- `availableTools: List<ToolDefinition>`
- `toolMode` or `toolingEnabled`
- `toolPromptContract: String?` for local runtimes that need the shared envelope format

The Step 1 stub result shape is fixed and must be preserved when Step 2 switches to Tavily:

```json
{
  "query": "latest android tool calling",
  "results": [
    {
      "title": "Stub Result",
      "url": "https://example.invalid/stub",
      "content": "This is a stub Tavily response injected by Pocket Crew.",
      "score": 1.0
    }
  ]
}
```

The local prompt contract is also fixed for Step 1 and Step 2:

```text
<tool_call>{"name":"tavily_web_search","arguments":{"query":"..."}}</tool_call>
```

No Room entity changes, message-schema changes, or hidden tool-message persistence are permitted in this feature.

## 4. API Contracts & Interfaces
`GenerateChatResponseUseCase` keeps its public invocation contract unchanged, but it must populate `GenerationOptions` with search capability for providers and runtimes that have the feature enabled, and it must compose the local tool contract into the runtime system prompt instead of mutating stored prompt templates.

`InferenceFactoryImpl` constructor and service construction paths must accept one shared `ToolExecutorPort` implementation and pass it into:

- `ApiInferenceServiceImpl`
- `OpenRouterInferenceServiceImpl`
- `XaiInferenceServiceImpl`
- `AnthropicInferenceServiceImpl`
- `GoogleInferenceServiceImpl`
- `LlamaInferenceServiceImpl`
- `LiteRtInferenceServiceImpl`
- `MediaPipeInferenceServiceImpl`

`BaseOpenAiSdkInferenceService` must support a tool loop that:

- advertises `tavily_web_search`
- detects model-emitted tool calls
- executes `ToolExecutorPort`
- appends the tool result back into the remote conversation
- continues until final assistant text

`AnthropicRequestMapper` and `AnthropicInferenceServiceImpl` must support `tool_use` and `tool_result` message construction with the same search payload. `GoogleRequestMapper` and `GoogleInferenceServiceImpl` must expose function declarations and a tool-capable execution path rather than relying solely on the existing `generateContentStream` flow.

Local contracts are explicit:

- LiteRT and MediaPipe must parse the shared `<tool_call>` envelope from streamed model text, suppress raw tool JSON from user-visible output, call `ToolExecutorPort`, inject tool results back into the active session, and resume generation.
- llama.cpp must surface a complete envelope from native token decoding, invoke a Kotlin callback with the parsed tool request, accept the tool result payload back into the active context, and continue generation without resetting the conversation.

Step 2 contracts:

- `TavilySearchRepository` exposes a single search method that accepts a query string and returns mapped JSON matching the stub result shape.
- `ApiKeyManager` stores the Tavily key under a dedicated alias such as `tavily_web_search`.
- `SettingsRepository` and `SettingsWorkflowModels` expose Tavily enablement and key-state fields without reusing `ApiCredentials`.

Typed failure contracts:

- Unknown tool name: `IllegalArgumentException`
- Missing or blank `query`: `IllegalArgumentException`
- Recursive or repeated tool call without terminal assistant output: `IllegalStateException`
- Malformed local `<tool_call>` envelope: `IllegalStateException`
- Tavily network failure: `IOException`

## 5. Permissions & Config Delta
No Android runtime permissions or manifest changes are required. Config and build changes are required:

- `core/data/build.gradle.kts` and `gradle/libs.versions.toml` may add the JSON and HTTP support needed for Tavily mapping.
- `app/src/main/kotlin/com/browntowndev/pocketcrew/app/NetworkModule.kt` must expose the shared `OkHttpClient` used by `TavilySearchRepository`.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/ApiKeyManager.kt` must add a dedicated Tavily key alias.
- `SettingsRepository`, `SettingsRepositoryImpl`, `SettingsWorkflowModels`, `SettingsViewModel`, `ByokConfigureScreen.kt`, and `ByokConfigureForms.kt` must add Tavily enablement and key-entry configuration.
- No ProGuard, storage schema, or app permission changes are part of this feature.

## 6. Constitution Audit
This design adheres to the project's core architectural rules by keeping capability contracts in `:core:domain`, provider execution in `:feature:inference`, network and secure storage in `:core:data`, UI configuration in `:feature:settings`, preserving `LlmInferencePort` and chat persistence boundaries, and explicitly rejecting a false Tier 2/Tier 3 contraction that would violate the AGENTS phase contract and `contracts/ARCHITECTURE_RULES.md` and `contracts/INFERENCE_RULES.md`.

## 7. Cross-Spec Dependencies
`13-add-search-skill-for-models` is an umbrella feature that must be split into dependent specs to remain synthitect-compliant:

- `13A-shared-and-remote-search-skill` must land before any real Tavily or local-runtime work.
- `13B-google-and-local-envelope-search-skill` depends on shared domain contracts and the shared executor from `13A`.
- `13C-llama-search-tool-bridge` depends on the shared local envelope contract from `13A` and must remain isolated because it spans Kotlin and native code.
- `13D-real-tavily-and-settings` depends on the stable stub result contract from `13A` and settings-facing capability wiring.

ESCALATION REQUIRED: Feature scope exceeds Tier 3 limits.
- Target Files: 47 (max: 25)
- Scenarios: 19 (max: 50)
Architect decision: Split into the four linked specs above or approve expanded scope?

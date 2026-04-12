# Implementation Map

  I traced the current inference stack. The clean way to do this is:

  - BYOK providers: use each provider’s native tool/function-calling path.
  - On-device providers: use one shared local tool-call protocol, with llama.cpp handling it inside the decoder loop and LiteRT/MediaPipe handling it in their Kotlin streaming/session loops.
  - Keep tool traffic transient at first. Persist the final assistant answer only. Hidden tool-message persistence is optional, not required for a working first implementation.

  Shared Changes

  - Add shared tool models and config in core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/GenerationOptions.kt.
      - availableTools
      - toolMode or toolingEnabled
      - possibly toolPromptContract for local runtimes
  - Add shared tool types under core/domain.
      - ToolDefinition
      - ToolCallRequest
      - ToolExecutionResult
      - ToolExecutorPort
  - Keep InferenceEvent in core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/inference/LlmInferencePort.kt unchanged if providers own the tool loop internally. That avoids ripple into chat UI/state.
  - Add a shared stub executor first, then replace its body with Tavily.
      - log tool name + args through core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/inference/LoggingPort.kt
      - return deterministic JSON shaped like final Tavily output

  Where The Tool Executor Gets Injected

  - feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/InferenceFactoryImpl.kt
      - inject the shared tool executor into every provider service it builds
      - pass tool capability into:
          - OpenAI-compatible services
          - Anthropic
          - Google
          - Llama
          - LiteRT
          - MediaPipe

  Step 1: Stub Tool Calling

  Use a dummy result like:

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

  That shape should stay stable so step 2 is just swapping executor logic.

  BYOK Providers

  OpenAI / OpenRouter / xAI:

  - feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/BaseOpenAiSdkInferenceService.kt
      - add tool-loop orchestration
      - detect tool calls from Responses API / Chat Completions fallback
      - execute stub/real tool
      - append tool result back into request history
      - continue until final text
  - feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/OpenAiRequestMapper.kt
      - add tavily_web_search schema/tool definition
  - feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/ApiInferenceServiceImpl.kt
  - feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/OpenRouterInferenceServiceImpl.kt
  - feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/XaiInferenceServiceImpl.kt
      - mostly delegate to the base loop once request mappers support tools

  Anthropic:

  - feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/AnthropicRequestMapper.kt
      - add tool definition
      - add assistant tool_use and user tool_result message construction
  - feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/AnthropicInferenceServiceImpl.kt
      - parse tool_use
      - execute stub/real tool
      - send tool_result
      - continue until text output

  Google:

  - feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/GoogleRequestMapper.kt
      - add function declarations
  - feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/GoogleInferenceServiceImpl.kt
      - this is not just a mapper change
      - current path uses generateContentStream
      - tool calling in the Java SDK is easier in the non-streaming AFC path
      - expect a separate tool-capable execution path here

  Google is the second-hardest provider after llama.cpp.

  On-Device Providers

  Recommendation: one shared local tool-call contract, not three different behaviors.

  Use a strict output envelope for local models, for example:

  <tool_call>{"name":"tavily_web_search","arguments":{"query":"..."}}</tool_call>

  Then detect, execute, inject result, continue generation.

  That requires:

  - a prompt composer that appends the tool contract to the active system prompt
  - a streaming parser for local tool-call envelopes
  - a shared local tool-loop coordinator

  Llama:

  - feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/LlamaInferenceServiceImpl.kt
      - pass tool-enabled prompt contract into the session
  - feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/llama/LlamaChatSessionManager.kt
      - likely minimal changes
  - feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/llama/JniLlamaEngine.kt
      - add a native tool callback bridge
      - pause generation on tool call
      - wait for Kotlin executor result
      - inject tool result back into context
      - resume generation
  - llama-android/src/main/cpp/llama-jni.cpp
      - detect the tool-call envelope during decode
      - invoke JNI callback to Kotlin
      - tokenize/decode tool result into KV cache before continuing
  - llama-android/src/main/cpp/token_processor.hpp
      - extend incremental parsing to safely detect a complete <tool_call>...</tool_call> block

  This is the hardest part in the whole feature.

  LiteRT:

  - feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/LiteRtInferenceServiceImpl.kt
      - detect tool-call envelope in streamed text
      - suppress the raw tool JSON from user-visible output
      - execute stub/real tool
      - feed result back into the same conversation and continue
  - feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/ConversationManagerImpl.kt
      - keep automaticToolCalling = false if you want consistent local behavior
      - or switch to native AFC later as an optimization
  - core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/inference/ConversationPort.kt
  - feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/ConversationImpl.kt
      - may need a continuation-friendly API if one send/response cycle is not enough

  MediaPipe:

  - feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/MediaPipeInferenceServiceImpl.kt
      - same local tool parser loop as LiteRT
      - detect tool envelope in partial text
      - execute tool
      - inject result with addQueryChunk(...)
      - call generateResponseAsync(...) again
  - feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/LlmInferenceWrapper.kt
      - only if the current wrapper needs an additional session control method

  Prompt Composition

  Do not hardcode search instructions directly into core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/SystemPromptTemplates.kt.

  Instead add a prompt composer that wraps the active configured system prompt before inference. The call site is core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/GenerateChatResponseUseCase.kt, where
  GenerationOptions.systemPrompt is set.

  That keeps:

  - user/system presets intact
  - provider-native tools separate from local tool protocol
  - the feature easy to disable

  Persistence

  Not required for step 1 or step 2:

  - core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/chat/Role.kt
  - core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/chat/Message.kt
  - core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/MessageEntity.kt
  - core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/PocketCrewDatabase.kt

  Only change those if you want hidden persisted tool traces. Functional internet search does not require it.

  Step 2: Real Tavily Wiring

  Data/network:

  - add Tavily models and repository in core/data
  - use shared app/src/main/kotlin/com/browntowndev/pocketcrew/app/NetworkModule.kt OkHttpClient
  - choose a JSON stack
      - the repo does not currently use kotlinx.serialization
      - if you follow the manual, add it in:
          - gradle/libs.versions.toml
          - core/data/build.gradle.kts

  Credential storage:

  - reuse core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/ApiKeyManager.kt with a fixed alias like tavily_web_search
  - expose that through a dedicated settings path, not ApiCredentials
  - add enable/key settings in:
      - core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/SettingsRepository.kt
      - core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/SettingsRepositoryImpl.kt
      - core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/settings/SettingsWorkflowModels.kt
      - feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModel.kt
      - corresponding settings UI files in feature/settings

  Tests To Add

  - OpenAI-family tool-loop tests in:
      - feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/BaseOpenAiSdkInferenceServiceTest.kt
      - new provider-specific tests for tool-call round trips
  - Anthropic tool-use tests in:
      - feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/AnthropicInferenceServiceImplTest.kt
  - Google tool-path tests
  - LiteRT tool parser / continuation tests in:
      - feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/LiteRtInferenceServiceImplTest.kt
  - MediaPipe tool-loop tests
  - llama parser/bridge tests in:
      - feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/llama/JniLlamaEngineGenerateWithOptionsTest.kt
      - feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/llama/LlamaChatSessionManagerTest.kt
      - llama-android/src/main/cpp/test_token_processor.cpp
  - Tavily repository tests with mocked HTTP

  Recommended Order

  1. Shared tool abstractions + stub executor
  2. OpenAI/OpenRouter/xAI
  3. Anthropic
  4. LiteRT + MediaPipe local tool envelope loop
  5. llama.cpp JNI loop
  6. Real Tavily repository + settings/key storage
  7. Replace stub executor with Tavily

  Most Important Design Call

  Use provider-native tools for remote APIs, but use one explicit local tool-call envelope for all on-device runtimes.

  That gives you:

  - less divergence
  - one local parser contract
  - a predictable way to test stub injection before Tavily is live
  - the exact llama.cpp hook you already expect

  If you want, I can start step 1 next and implement the shared tool abstractions plus the stubbed tool loop for the API providers first.


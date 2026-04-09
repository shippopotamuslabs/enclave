# Implementation Map

## Goal Summary
Deliver the shared search-tool contract plus the remote-provider Step 1 stub loop. This ticket establishes the stable domain types, a deterministic stub executor, remote-provider wiring for OpenAI-compatible providers and Anthropic, and the factory/use-case changes needed to expose search capability on non-local models.

## Target Module Index
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/GenerationOptions.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/ToolDefinition.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/ToolCallRequest.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/ToolExecutionResult.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/inference/ToolExecutorPort.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/GenerateChatResponseUseCase.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/InferenceFactoryImpl.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/BaseOpenAiSdkInferenceService.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/OpenAiRequestMapper.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/ApiInferenceServiceImpl.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/OpenRouterInferenceServiceImpl.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/XaiInferenceServiceImpl.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/AnthropicRequestMapper.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/AnthropicInferenceServiceImpl.kt`
- `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/ApiInferenceServiceImplTest.kt`
- `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/BaseOpenAiSdkInferenceServiceTest.kt`
- `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/OpenAiRequestMapperTest.kt`
- `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/AnthropicRequestMapperTest.kt`
- `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/AnthropicInferenceServiceImplTest.kt`
- `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/InferenceFactoryImplTest.kt`
- `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/model/inference/GenerationOptionsTest.kt`

## Design Notes
- Keep `InferenceEvent` unchanged. Remote providers own the tool loop internally.
- Add a deterministic stub executor first. It returns the canonical Tavily-like JSON payload and logs tool name plus arguments through the existing logging path.
- `GenerateChatResponseUseCase` exposes search capability only for non-local models in this ticket.
- `InferenceFactoryImpl` wires the shared executor only into providers covered here: OpenAI-compatible providers and Anthropic.
- Persist only final assistant text. Tool requests and results stay transient.

## Fixed Step 1 Stub Result
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

## Impact Radius
- Shared inference configuration in `:core:domain`
- Remote-provider request mapping and control flow in `:feature:inference`
- No UI, database, local-runtime, JNI, or settings work

## Non-Goals
- No Google support
- No LiteRT, MediaPipe, or llama.cpp local envelope support
- No real Tavily HTTP integration
- No Tavily key storage or settings UI
- No persistence of tool traces

## Dependencies
- None. This is the first executable slice and the base dependency for `13B`, `13C`, and `13D`.

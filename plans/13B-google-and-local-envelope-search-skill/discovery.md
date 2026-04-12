# Implementation Map

## Goal Summary
Extend the Step 1 search skill to Google and the non-llama local runtimes. This ticket introduces the shared local `<tool_call>...</tool_call>` prompt contract, the prompt composer used by local models, a tool-capable Google path, and continuation loops for LiteRT and MediaPipe that suppress raw tool JSON from user-visible output.

## Target Module Index
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/SearchToolPromptComposer.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/GenerateChatResponseUseCase.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/InferenceFactoryImpl.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/GoogleRequestMapper.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/GoogleInferenceServiceImpl.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/LiteRtInferenceServiceImpl.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/ConversationManagerImpl.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/ConversationImpl.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/MediaPipeInferenceServiceImpl.kt`
- `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/SearchToolPromptComposerTest.kt`
- `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/GoogleRequestMapperTest.kt`
- `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/GoogleInferenceServiceImplTest.kt`
- `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/LiteRtInferenceServiceImplTest.kt`
- `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/LiteRtInferenceServiceImplGenerationOptionsTest.kt`
- `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/ConversationImplTest.kt`
- `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/MediaPipeInferenceServiceImplTest.kt`

## Design Notes
- Reuse the shared tool contracts and stub executor from `13A`.
- Google must use a tool-capable path rather than silently falling back to the current `generateContentStream` behavior.
- Local runtimes use one fixed envelope:

```text
<tool_call>{"name":"tavily_web_search","arguments":{"query":"..."}}</tool_call>
```

- `SearchToolPromptComposer` owns the local prompt contract. `GenerateChatResponseUseCase` applies it to runtime prompt composition without mutating stored prompt templates.
- LiteRT and MediaPipe must suppress raw envelope JSON and continue the same conversation after tool execution.

## Impact Radius
- Domain use-case prompt composition
- Google request mapping and inference control flow
- LiteRT and MediaPipe streaming loops
- No JNI or Tavily settings work

## Non-Goals
- No OpenAI-compatible provider changes beyond consuming the shared contracts from `13A`
- No llama.cpp JNI bridge
- No real Tavily repository or API key storage
- No persistence of tool traces

## Dependencies
- Depends on `13A-shared-and-remote-search-skill`

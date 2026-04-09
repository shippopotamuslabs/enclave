# Implementation Map

## Goal Summary
Add the search-skill bridge for the llama.cpp path. This ticket isolates the Kotlin-to-native integration required to detect a local `<tool_call>...</tool_call>` envelope during token decoding, pause generation, execute the shared tool executor through Kotlin, inject the tool result back into the active context, and resume the same session.

## Target Module Index
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/InferenceFactoryImpl.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/LlamaInferenceServiceImpl.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/llama/LlamaChatSessionManager.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/llama/JniLlamaEngine.kt`
- `llama-android/src/main/cpp/llama-jni.cpp`
- `llama-android/src/main/cpp/token_processor.hpp`
- `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/llama/JniLlamaEngineGenerateWithOptionsTest.kt`
- `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/llama/LlamaChatSessionManagerTest.kt`
- `llama-android/src/main/cpp/test_token_processor.cpp`

## Design Notes
- Reuse the shared tool contracts from `13A` and the local envelope contract from `13B`.
- Keep the llama bridge isolated because it spans Kotlin and native code and has the highest regression risk.
- `token_processor.hpp` is responsible for incremental detection of one complete `<tool_call>` block.
- `llama-jni.cpp` bridges the parsed tool request to Kotlin and accepts the tool result payload back for injection into the active decode context.
- `JniLlamaEngine` and `LlamaChatSessionManager` coordinate pause, replay, and resume without resetting session state.

## Impact Radius
- Llama inference service construction and session management
- Native token parsing and JNI bridge
- No settings, HTTP, or non-llama local runtime work

## Non-Goals
- No Google, LiteRT, or MediaPipe changes
- No real Tavily repository
- No chat persistence changes

## Dependencies
- Depends on `13A-shared-and-remote-search-skill`
- Depends on `13B-google-and-local-envelope-search-skill` for the shared local envelope contract

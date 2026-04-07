# Technical Specification: On-Device Inference Performance Audit (PERF-INF-AUDIT)

## 1. Objective
Execute high-signal performance optimizations and architectural cleanup for the on-device inference stack.
Acceptance Criteria:
- KV Cache Prefix Caching: Subsequent turns in the same session must avoid full prompt re-evaluation.
- Tiered Context Windows: Model context limits must be configurable and respect memory constraints. Synthesizers (MAIN, FINAL_SYNTHESIS) must have sufficient context to accommodate all input drafts and system prompts.
- MediaPipe Configuration: Remove hardcoded 16384 limit; use config-driven values.
- Resource Management: Eliminate sampler leaks in native code.
- Architectural Integrity: `GpuProfiler` must be injected via Hilt, not manually instantiated.

## 2. System Architecture

### Target Files
- `llama-android/src/main/cpp/llama-jni.cpp`: Native JNI bridge for llama.cpp.
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/llama/JniLlamaEngine.kt`: Kotlin wrapper for the native engine.
- `app/src/main/kotlin/com/browntowndev/pocketcrew/app/EngineModule.kt`: Hilt module for inference engines.
- `app/src/main/kotlin/com/browntowndev/pocketcrew/app/LlamaModule.kt`: Hilt module for Llama specific components.
- `model_config.json`: Configuration file for all model roles.

### Component Boundaries
- **Native Layer (`llama-jni.cpp`)**: Manages the `llama_context` and KV cache lifecycle. Will be updated to track `g_last_eval_pos` to enable prefix caching.
- **Engine Layer (`JniLlamaEngine.kt`)**: Orchestrates the interaction between domain models and native code. Will track token history to detect prefix matches.
- **Dependency Injection (`EngineModule.kt`, `LlamaModule.kt`)**: Provides `GpuProfiler` and `LlamaModelConfig` to the engine layer.

## 3. Data Models & Schemas
- **LlamaModelConfig** (Reuse): Added `contextWindow` and `maxTokens` fields (already exist but will be strictly enforced).
- **Native State**: Added `g_last_eval_pos` (static global in `llama-jni.cpp`) to track the current KV cache occupancy.

## 4. API Contracts & Interfaces
- **Native JNI Methods**:
    - `nativeStartCompletion`: Signature remains the same, but internal logic will strictly handle KV cache retention logic based on the provided token array.
- **JniLlamaEngine Constructor**:
    - Add `@Inject constructor(..., gpuProfiler: GpuProfiler)` to replace manual instantiation.

## 5. Permissions & Config Delta
- **model_config.json**: Update `contextWindow` for all roles to implement tiered memory usage:
    - **Drafters/Fast/Vision**: 4096 context.
    - **Main (Synthesizer)**: 12288 context (to fit 4k prompt + 2x 1k drafts + 4k thinking/output).
    - **Final Synthesis**: 8192 context (to fit 4k prompt + 2k candidate + 2k output).
    - **Thinking/Code**: 8192 context.
- No Android manifest or permission changes.

## 6. Constitution Audit
This design adheres to the project's core architectural rules by enforcing strict layer boundaries (JNI vs Kotlin) and leveraging Hilt for dependency management. It prioritizes memory safety and performance on mobile hardware.

## 7. Cross-Spec Dependencies
No cross-spec dependencies.

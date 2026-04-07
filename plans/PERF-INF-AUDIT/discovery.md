# Discovery: On-Device Inference Performance Audit

## 1. Goal Summary
Execute high-signal performance optimizations for the on-device inference stack (llama.cpp, MediaPipe, LiteRT). Key objectives include implementing KV cache prefix caching to reduce prefill time, refining context window management via tiered configuration, and resolving memory leaks and redundant object instantiation (samplers, GpuProfiler).

## 2. Target Module Index
Unified view of existing code analyzed across all probes.

### Existing Data Models
- **LlamaModelConfig** (`domain:model:inference`): Defines contextWindow, maxTokens, and sampling parameters. Referenced in multiple probes.
- **ChatMessage** (`feature:inference:llama`): Internal representation for chat history used by the JNI bridge.
- **GenerationEvent** (`domain:model:inference`): Flow event type for token streaming.
- **llama_model / llama_context** (Native): Opaque types managing the underlying GGUF model and its state.

### Dependencies & API Contracts
- **llama.cpp** (Native): Primary inference engine for GGUF models. 
- **MediaPipe GenAI** (External SDK): Used for task-based model inference (e.g., Gemma).
- **LiteRT** (External SDK): Used for LiteRT-native models.
- **JNI Llama Interface** (`llama-jni.cpp`): The bridge between Kotlin and C++.

### Utility/Shared Classes
- **GpuProfiler** (`feature:inference:llama`): Handles backend detection (CPU/Vulkan/OpenCL). [Inference Logic Probe]
- **SveDetector** (`feature:inference:llama`): ARM-specific vector feature detection. [Inference Logic Probe]
- **cpudetect.cpp** (`llama-android:src:main:cpp`): Low-level CPU core and frequency analysis. [Native Probe]

### Impact Radius
- **llama-jni.cpp**: High impact. Critical changes for `nativeStartCompletion` to support prefix caching and sampler cleanup.
- **JniLlamaEngine.kt**: High impact. Tracks token history positions to coordinate with native prefix caching; moves GpuProfiler to DI.
- **EngineModule.kt**: Medium impact. Remapping MediaPipe initialization to use dynamic config values instead of hardcoded 16384.
- **model_config.json**: Medium impact. Tiered context window updates for all model roles. Added constraint for MoA Synthesizer context space.
- **InferenceService.kt**: Low impact. Potential logic updates if hardcoded truncation is needed, though primarily handled by config.

## 3. Cross-Probe Analysis
### Overlaps Identified
- `LlamaModelConfig` is the central contract between the domain layer, the Kotlin implementation, and the native JNI layer.
- `GpuProfiler` is currently instantiated in `JniLlamaEngine` but its logic impacts how `llama-jni` is initialized.

### Gaps & Uncertainties
- **Prefix Caching Logic**: Does `llama.cpp`'s `llama_memory_clear` need to be partially bypassed or replaced with more granular KV cache management? (Resolved by identifying `llama_kv_cache_seq_rm` or similar manual tracking).
- **MediaPipe `setMaxTokens`**: Does MediaPipe's "max tokens" refer to total context or just the generation limit? (Discovery indicates it aligns with context window).

### Findings Added from MoA Pipeline Analysis
- **MoA Context Pressure**: The `SYNTHESIS` and `FINAL` steps in `InferenceService` aggregate multiple draft outputs. If these roles (MAIN and FINAL_SYNTHESIS) have the same context window as the drafters, they risk early truncation or OOM during re-eval.
- **Synthesizer Budget**: Synthesizers require a larger "tier" (e.g., 8k or 16k) compared to Drafters (e.g., 4k).

## 4. High-Impact Clarifying Questions
*None identified. Proceeding to Spec phase (following the provided audit findings as the ground truth).*

## 5. Probe Coverage Summary
| Layer/Directory | Probe Agent | Key Findings |
|----------------|------------|-------------|
| feature/inference/ | Inference Logic | JniLlamaEngine state tracking and DI needs. |
| llama-android/src/main/cpp/ | Native Layer | Prefix caching and sampler leak in llama-jni.cpp. |
| app/src/main/kotlin/.../app/ | App Injection | Hardcoded values in EngineModule.kt. |
| root/ | Config Layer | Global context window defaults need tiering. |

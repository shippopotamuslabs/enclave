# Test Specification: On-Device Inference Performance Audit (PERF-INF-AUDIT)

## 1. Happy Path Scenarios

### Scenario: KV Cache Prefix Persistence
- **Given:** A chat session with a model (standard context: 4096).
- **When:** User sends message "Describe the city of Paris."
- **Then:** Engine evaluates 56 tokens at full speed.
- **When:** User sends follow-up "How many people live there?"
- **Then:** Engine matches prefix of 56 tokens from turn 1; only new tokens are evaluated.
- **Then:** `llama-jni.cpp` logs show `g_last_eval_pos` was reused.

### Scenario: Tiered Context Window Enforcement
- **Given:** A standard model config with `contextWindow: 4096`.
- **When:** A request is made to initialize the engine with a 16384 context.
- **Then:** `JniLlamaEngine` intercepts the value and clamps it to 4096.

### Scenario: MediaPipe Config Awareness
- **Given:** A Gemma model with `contextWindow: 8192`.
- **When:** The `EngineModule` initializes `MediaPipeInferenceService`.
- **Then:** `MediaPipeInferenceServiceFactory.create` receives 8192 as `maxTokens`.

### Scenario: MoA Synthesizer Context Pressure
- **Given:** Draft 1 (1024 tokens) and Draft 2 (1024 tokens) responses.
- **When:** `InferenceService` builds the Synthesis prompt.
- **Then:** `JniLlamaEngine` (MAIN role) should have `contextWindow >= 8192` to avoid truncation.
- **Then:** The synthesis output is produced successfully without mid-completion truncation.

## 2. Error Path & Edge Case Scenarios

### Scenario: Context Overrun
- **Given:** 4000 tokens already in the KV cache.
- **When:** User sends 200 additional tokens (total 4200, exceeding 4096).
- **Then:** `JniLlamaEngine.checkAndCompressContext` triggers correctly.
- **Then:** KV cache is cleared and re-evaluated from the compressed history.

### Scenario: Model Switch Cleanup
- **Given:** An active Llama engine instance.
- **When:** `InferenceFactoryImpl` is asked to load a different model.
- **Then:** All native resources (context, model, samplers) are freed correctly.
- **Then:** No `JNI ERROR: local reference table overflow` or memory growth.

## 3. Mutation Defense

### Lazy Implementation Risk
The most likely lazy implementation is bypassing the prefix match check and clearing the KV cache on every turn, but returning "matches" in the logs.

### Defense Scenario
- **Given:** A turn with exactly 1024 tokens.
- **When:** A turn extension with 10 tokens follows.
- **Then:** Prefill time for the extension must be < 50ms (on target device hardware).
- **Then:** If prefill time is > 200ms, the implementation is cleared as a failure.

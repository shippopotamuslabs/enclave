# Test Specification: SHA-256 Engine Reuse for Same Model File

## 1. Happy Path Scenarios

### Scenario: FAST and THINKING share same SHA — single service reused

- **Given:** `ModelType.FAST` and `ModelType.THINKING` both resolve to assets with SHA-256 `abc123` and extension `gguf`
- **When:** `getInferenceService(ModelType.FAST)` is called and stores the result as `serviceA`
- **And:** `getInferenceService(ModelType.THINKING)` is called and stores the result as `serviceB`
- **Then:** `serviceA` and `serviceB` reference the exact same instance (`serviceA === serviceB`)
- **And:** `closeSession()` on one instance must NOT invalidate the other while it is still referenced

### Scenario: Any ModelTypes sharing same SHA — single service reused

- **Given:** `ModelType.FAST`, `ModelType.DRAFT_ONE`, and `ModelType.FINAL_SYNTHESIS` all resolve to SHA-256 `abc123-gguf`
- **When:** All three are requested from the factory
- **Then:** All three return the SAME service instance
- **And:** Reference count is 3

### Scenario: Different SHA — separate services created

- **Given:** `ModelType.FAST` resolves to SHA-256 `aaa111-gguf` and `ModelType.THINKING` resolves to SHA-256 `bbb222-gguf`
- **When:** `getInferenceService(ModelType.FAST)` stores `serviceA`, then `getInferenceService(ModelType.THINKING)` stores `serviceB`
- **Then:** `serviceA` and `serviceB` are DIFFERENT instances
- **And:** Both services operate independently (closing one does not affect the other)

### Scenario: Same file but different extensions — separate services created

- **Given:** `ModelType.FAST` resolves to `abc123-gguf` and `ModelType.THINKING` resolves to `abc123-task`
- **When:** Both are requested from the factory
- **Then:** DIFFERENT service instances are returned (the extension determines which engine type is used)

### Scenario: Shared instance survives alternating calls across all roles

- **Given:** FAST, THINKING, DRAFT_ONE, DRAFT_TWO, MAIN, FINAL_SYNTHESIS all share SHA-256 `abc123-gguf`
- **When:** The factory is asked for each ModelType in sequence
- **Then:** All calls return the SAME instance
- **And:** Only one engine is loaded in memory at any time
- **And:** Reference count is 6

### Scenario: Config thinkingEnabled=true gets reasoning ON

- **Given:** Any role resolves to `abc123-gguf` with a config where `thinkingEnabled = true`
- **When:** `sendPrompt("hello", GenerationOptions(reasoningBudget = 2048), closeConversation = false)` is called
- **Then:** The engine receives `reasoningBudget = 2048` (for llama.cpp)
- **And:** The emitted `InferenceEvent` stream contains `Thinking` events

### Scenario: Config thinkingEnabled=false gets reasoning OFF

- **Given:** Any role resolves to `abc123-gguf` with a config where `thinkingEnabled = false`
- **When:** `sendPrompt("hello", GenerationOptions(reasoningBudget = 0), closeConversation = false)` is called
- **Then:** The engine receives `reasoningBudget = 0` (for llama.cpp)
- **And:** The emitted `InferenceEvent` stream contains NO `Thinking` events, only `PartialResponse` events

### Scenario: reasoningEnabled toggles correctly between sequential requests on shared engine

- **Given:** A shared engine instance initially used for a config with `thinkingEnabled = true` (`reasoningBudget = 2048`)
- **When:** The SAME engine is used for a second request with `thinkingEnabled = false` (`reasoningBudget = 0`)
- **Then:** The second request's generation receives `reasoningBudget = 0`
- **And:** The second request completes WITHOUT emitting any `Thinking` events
- **And:** The internal sampling config's `thinkingEnabled` default of `true` did NOT affect the second request

## 2. Error Path & Edge Case Scenarios

### Scenario: Reference count prevents premature engine shutdown

- **Given:** Three ModelTypes (FAST, THINKING, DRAFT_ONE) share SHA-256 `abc123-gguf` with reference count = 3
- **When:** FAST releases its usage (`releaseUsage(ModelType.FAST)`)
- **Then:** Reference count decreases to 2
- **And:** The engine instance remains loaded (NOT closed)
- **And:** `getInferenceService(ModelType.THINKING)` still returns the same instance

### Scenario: Reference count drops to zero triggers cleanup

- **Given:** FAST has the sole reference to SHA-256 `abc123-gguf` with reference count = 1
- **When:** `releaseUsage(ModelType.FAST)` is called
- **Then:** Reference count drops to 0
- **And:** The underlying engine's `closeSession()` is invoked
- **And:** The cache entry for `abc123-gguf` is removed

### Scenario: Model file change breaks SHA match — separate instance created

- **Given:** Both FAST and THINKING initially share SHA-256 `abc123-gguf` and the same service
- **When:** The THINKING model file changes in the database, now pointing to SHA-256 `xyz789-gguf`
- **Then:** A subsequent `getInferenceService(ModelType.THINKING)` creates a SEPARATE service instance
- **And:** The old FAST service for `abc123-gguf` remains in the cache with unchanged reference count

### Scenario: NoOp service returned when asset is missing for a role

- **Given:** No registered asset exists for `ModelType.FAST`
- **When:** `getInferenceService(ModelType.FAST)` is called
- **Then:** A `NoOpInferenceService` is returned
- **And:** Any existing service for another ModelType is unaffected

### Scenario: Concurrent access to factory does not create duplicate services

- **Given:** FAST and THINKING share SHA-256 `abc123-gguf`
- **When:** 10 concurrent coroutines all call `getInferenceService(ModelType.FAST)` AND `getInferenceService(ModelType.THINKING)` in parallel
- **Then:** Only 1 service instance exists in the cache for `abc123-gguf`
- **And:** The mutex prevents race conditions

### Scenario: LocalModelConfigureScreen shows thinkingEnabled toggle for user presets

- **Given:** A `LocalModelConfigUi` where `isSystemPreset = false`
- **When:** The Local Model Configure screen is displayed
- **Then:** A "Thinking" switch toggle is visible
- **And:** The toggle is enabled (interactive)

### Scenario: LocalModelConfigureScreen disables thinkingEnabled for system presets

- **Given:** A `LocalModelConfigUi` where `isSystemPreset = true`
- **When:** The Local Model Configure screen is displayed
- **Then:** The "Thinking" switch toggle is visible
- **And:** The toggle is disabled (read-only), matching the behavior of all other controls for system presets

### Scenario: sendPrompt without options uses default behavior (backward compat)

- **Given:** Any engine implementation
- **When:** `sendPrompt("hello", closeConversation = false)` is called (single-argument version)
- **Then:** The engine uses config-level `thinkingEnabled` as-is
- **And:** No exception is thrown

### Scenario: LiteRT service accepts GenerationOptions without crashing

- **Given:** A `LiteRtInferenceServiceImpl` instance
- **When:** `sendPrompt("hello", GenerationOptions(reasoningBudget = 2048), false)` is called
- **Then:** The flow emits tokens without throwing
- **And:** The call completes with a `Finished` event

### Scenario: MediaPipe service applies per-request session options

- **Given:** A `MediaPipeInferenceServiceImpl` instance
- **When:** `sendPrompt("hello", GenerationOptions(reasoningBudget = 2048, temperature = 0.9f), false)` is called
- **Then:** The session is created (or reused) with reasoning-aware behavior
- **And:** The flow completes without `IllegalStateException`

## 3. Mutation Defense

### Lazy Implementation Risk 1: Still caching by ModelType

The most likely lazy implementation: keep the current `cachedServices = mutableMapOf<ModelType, LlmInferencePort>()` and pretend to key by SHA. This would create SEPARATE instances for FAST, DRAFT_ONE, and THINKING even when they share the same SHA-256.

### Defense Scenario 1: Identity check across different ModelTypes with same SHA

- **Given:** The factory cache returns service `svc1` when `ModelType.FAST` is requested with SHA `abc123-gguf`
- **When:** The asset registry already has `ModelType.THINKING` resolving to SHA `abc123-gguf`
- **And:** `getInferenceService(ModelType.THINKING)` is called
- **Then:** The returned service MUST be the SAME instance as `svc1` (identity check with `===`)
- **And:** No new engine is loaded (no duplicate native model allocation)

### Lazy Implementation Risk 2: Reasoning state leaks between sequential requests on shared engine

The engineer makes `GenerationOptions.reasoningBudget` a global mutable field on the shared engine. A THINKING request sets `reasoningBudget = 2048` globally, and a subsequent FAST request also gets reasoning-enabled behavior.

### Defense Scenario 2: Reasoning isolation between sequential requests on shared engine

- **Given:** A shared engine instance loaded with SHA-256 `abc123-gguf` where config default has `thinkingEnabled = true`
- **When:** `sendPrompt("think about it", GenerationOptions(reasoningBudget = 2048), false)` is fully completed (emits `Finished`)
- **AND:** Immediately afterwards, `sendPrompt("quick reply", GenerationOptions(reasoningBudget = 0), false)` is called on the SAME instance
- **Then:** The SECOND request's generation receives `reasoningBudget = 0`
- **And:** The second request completes WITHOUT emitting any `Thinking` events
- **And:** The internal sampling config's `thinkingEnabled` default of `true` did NOT affect the second request

### Lazy Implementation Risk 3: Reference count not tracked per SHA-identity

The engineer uses a single boolean flag `isActive` instead of a reference counter. When one role releases, it closes the engine even though other roles still hold references.

### Defense Scenario 3: Release by one role does not kill engine for remaining roles

- **Given:** FAST and THINKING share SHA-256 `abc123-gguf` (ref count = 2), both have active references
- **When:** `releaseUsage(ModelType.FAST)` is called (ref count = 1)
- **AND:** `sendPrompt("hello", GenerationOptions(reasoningBudget = 2048), false)` is called via `getInferenceService(ModelType.THINKING)`
- **Then:** The call succeeds WITHOUT `IllegalStateException` from a closed engine
- **And:** Thinking events are emitted normally

## 4. Critical Integration Gaps (Post-Audit Additions)

### Lazy Implementation Risk 4: GenerateChatResponseUseCase never passes GenerationOptions

The engineer adds `GenerationOptions` to the port but forgets to update `GenerateChatResponseUseCase.generateWithService()`. The use case continues calling `service.sendPrompt(prompt, closeConversation)` (the default overload), so reasoning is governed entirely by the config-level default loaded at engine init, NOT by per-request options. The factory and service layers are correct, but the integration is dead code.

### Defense Scenario 4: GenerateChatResponseUseCase calls sendPrompt with GenerationOptions

- **Given:** `GenerateChatResponseUseCase` is invoked with `Mode.FAST` and a `LocalModelConfiguration` where `thinkingEnabled = false`
- **When:** The use case resolves the service and calls `sendPrompt`
- **Then:** The NEW overload `sendPrompt(prompt: String, options: GenerationOptions, closeConversation: Boolean)` IS called (NOT the default overload)
- **And:** `GenerationOptions.reasoningBudget` equals `0` (derived from `thinkingEnabled = false`)
- **And:** `InferenceFactoryPort.registerUsage(ModelType.FAST)` WAS called before generation
- **And:** `InferenceFactoryPort.releaseUsage(ModelType.FAST)` WAS called after generation completes

- **Given:** `GenerateChatResponseUseCase` is invoked with `Mode.THINKING` and a `LocalModelConfiguration` where `thinkingEnabled = true`
- **When:** The use case resolves the service and calls `sendPrompt`
- **Then:** The NEW overload `sendPrompt(prompt: String, options: GenerationOptions, closeConversation: Boolean)` IS called
- **And:** `GenerationOptions.reasoningBudget` equals `2048` (derived from `thinkingEnabled = true`)

- **Given:** `GenerateChatResponseUseCase` is invoked with `Mode.CREW` and a CREW role's config where `thinkingEnabled = true`
- **When:** The pipeline executor calls sendPrompt for that CREW role
- **Then:** `GenerationOptions.reasoningBudget` equals `2048` for that role's config
- **And:** Each CREW role's reasoning is independently resolved from its own config's `thinkingEnabled`

### Lazy Implementation Risk 5: JniLlamaEngine ignores per-request reasoning budget

The engineer overrides `samplingConfig.thinkingEnabled` in `LlamaInferenceServiceImpl.sendPrompt(options)`, but `JniLlamaEngine.generate()` reads the budget from `currentConfig.sampling.thinkingEnabled` which was set at `initialize()` time. The per-request mutation happens too late — the engine already baked in the reasoning budget at load time. Per-request control is illusory.

### Defense Scenario 5: JniLlamaEngine.generateWithOptions derives reasoning from GenerationOptions at call time

- **Given:** A `JniLlamaEngine` instance loaded with a model where `config.sampling.thinkingEnabled = true` (reasoning ON by default)
- **When:** `generateWithOptions(GenerationOptions(reasoningBudget = 0))` is called
- **Then:** `reasoningBudget` passed to `nativeStartCompletion` equals `0` (NOT the engine's default of 2048)
- **And:** `penaltyFreq` equals `0.05f` (non-thinking penalties)
- **And:** `penaltyPresent` equals `0.05f` (non-thinking penalties)
- **And:** The timeout used equals `GENERATION_TIMEOUT_SECONDS_REGULAR` (900s, NOT 1800s)

- **Given:** The same `JniLlamaEngine` instance (same loaded model config has `thinkingEnabled = false` by default)
- **When:** `generateWithOptions(GenerationOptions(reasoningBudget = 2048, temperature = 0.5f))` is called
- **Then:** `reasoningBudget` passed to `nativeStartCompletion` equals `2048` (OVERRIDING the engine's default of 0)
- **And:** `penaltyFreq` equals `0.0f` (thinking penalties)
- **And:** `penaltyPresent` equals `0.0f` (thinking penalties)
- **And:** The timeout used equals `GENERATION_TIMEOUT_SECONDS_THINKING` (1800s)
- **And:** `temperature` passed equals `0.5f` (NOT the config's default)

### Defense Scenario 6: LlamaInferenceServiceImpl delegates reasoning to JniLlamaEngine.generateWithOptions

- **Given:** A `LlamaInferenceServiceImpl` whose internal JniLlamaEngine has config with `thinkingEnabled = true`
- **When:** `sendPrompt("hello", GenerationOptions(reasoningBudget = 0), false)` is called
- **Then:** The service calls `generateWithOptions(reasoningBudget = 0)` on the engine (NOT the default `generate()`)
- **And:** The emitted flow contains NO `Thinking` events (only `PartialResponse` + `Finished`)

### Implementation Gap: sendPrompt(options) missing error handling parity

The original `sendPrompt(prompt, closeConversation)` wraps `ensureModelLoaded()` in a try/catch and emits `InferenceEvent.Error` on failure. The new `sendPrompt(prompt, options, closeConversation)` overload calls `ensureModelLoaded()` without try/catch — an exception will crash the flow collector instead of propagating as an error event.

### Defense Scenario 7: sendPrompt(options) emits Error event when ensureModelLoaded fails

- **Given:** A `LlamaInferenceServiceImpl` (via mock) where `ensureModelLoaded()` will throw `IllegalStateException` (e.g., no registered asset)
- **When:** `sendPrompt("hello", GenerationOptions(reasoningBudget = 2048), false)` is called
- **Then:** The flow emits `InferenceEvent.Error` with the caught exception
- **And:** The flow does NOT crash (no unhandled exception propagates to collector)

### Defense Scenario 8: GenerateChatResponseUseCase resolves per-role thinkingEnabled correctly

- **Given:** A `FakeModelRegistry` where `ModelType.FAST` has config `thinkingEnabled = false` AND `ModelType.THINKING` has config `thinkingEnabled = true` (shared SHA)
- **When:** `GenerateChatResponseUseCase` runs for Mode.FAST
- **Then:** `GenerationOptions(reasoningBudget = 0)` is passed to the service (reasoning OFF)

- **When:** `GenerateChatResponseUseCase` runs for Mode.THINKING with the same shared-SHA engine
- **Then:** `GenerationOptions(reasoningBudget = 2048)` is passed to the service (reasoning ON)
- **And:** The same underlying engine instance is used for both modes (shared, but reasoning differs per-request)
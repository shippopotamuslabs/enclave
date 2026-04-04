# Technical Specification: SHA-256 Engine Reuse for Same Model File

## 1. Objective

Enable the inference factory to share a single loaded engine/service instance across ANY roles (`ModelType`) that resolve to the same underlying model file (by SHA-256 identity). Currently the factory caches by `ModelType`, so even if DRAFT_ONE, DRAFT_TWO, FAST, and THINKING all point to the same `.gguf` file, four separate engine instances are loaded, quadrupling memory.

**Key behaviors:**
- Engine reuse is keyed by a composite "SHA-256 + file extension" identity, not by `ModelType`.
- Any `ModelType` (FAST, THINKING, DRAFT_ONE, DRAFT_TWO, MAIN, FINAL_SYNTHESIS) sharing the same SHA-256+extension shares one engine.
- Reasoning is **always** determined by the role's config `LocalModelConfiguration.thinkingEnabled`. No mode-level overrides.
  - If `thinkingEnabled = true` → reasoning ON (budget = 2048).
  - If `thinkingEnabled = false` → reasoning OFF (budget = 0).
- The settings UI exposes the `thinkingEnabled` toggle on `LocalModelConfigureScreen` (disabled for system presets).
- No other UI changes are required.

**Acceptance criteria:**
1. When any two or more ModelTypes resolve to assets with the same SHA-256+extension, a single service instance is created and shared.
2. When SHA-256+extension differs, preserve current behavior (separate instances per role).
3. Each request carries per-response generation options that enforce reasoning based on the role's config `thinkingEnabled`.
4. The shared instance lifecycle is reference-counted — it is not closed while any role still holds it.
5. `thinkingEnabled` toggle is visible and editable on the Local Model Configure screen for user-created presets (disabled for system presets).

## 2. System Architecture

### Target Files

**New files to create:**
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/GenerationOptions.kt` — Per-response generation request payload.

**Files to modify:**
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/inference/InferenceFactoryPort.kt` — Add `registerUsage`/`releaseUsage` lifecycle methods.
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/inference/LlmInferencePort.kt` — Add `sendPrompt` overload accepting `GenerationOptions`.
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/GenerateChatResponseUseCase.kt` — Resolve the role's config `thinkingEnabled`, build `GenerationOptions`, call overloaded `sendPrompt`.
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/InferenceFactoryImpl.kt` — Replace `ModelType`-keyed caches with SHA-identity-keyed shared cache, add reference counting across all roles.
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/LlamaInferenceServiceImpl.kt` — Accept `GenerationOptions` in `sendPrompt`, override config-level `thinkingEnabled` when options differ.
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/llama/JniLlamaEngine.kt` — Add `generateWithOptions` overload that derives reasoning budget/penalties/timeout from `GenerationOptions.reasoningBudget` instead of `cfg.sampling.thinkingEnabled`.
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/LiteRtInferenceServiceImpl.kt` — Accept `GenerationOptions` in `sendPrompt`.
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/MediaPipeInferenceServiceImpl.kt` — Accept `GenerationOptions` in `sendPrompt`, pass through to session.
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsModels.kt` — Add `thinkingEnabled: Boolean = false` to `LocalModelConfigUi`.
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/LocalModelConfigureScreen.kt` — Add Thinking toggle switch (disabled when `isSystemPreset = true`).
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModel.kt` — Map `thinkingEnabled` from `LocalModelConfiguration` into `LocalModelConfigUi` and back on save.
- `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/InferenceFactoryImplTest.kt` — Tests for SHA-based reuse across all ModelTypes.

### Component Boundaries

**Layer separation (must be preserved):**
- `core/domain` defines all interfaces and domain models (`GenerationOptions`, updated ports).
- `feature/inference` provides concrete implementations (`InferenceFactoryImpl`, engine-specific service impls).
- `feature/settings` provides the UI for editing per-config `thinkingEnabled`.
- `GenerateChatResponseUseCase` reads the role's `LocalModelConfiguration.thinkingEnabled`, converts to `GenerationOptions(reasoningBudget)`, and passes it down. No mode-level overrides.

**Reused existing models from Target Module Index:**
- `ModelType` — continues driving role resolution.
- `LocalModelConfiguration` — its `thinkingEnabled` is the **single source of truth** for reasoning per config.
- `LlamaSamplingConfig` — existing config structure; its `thinkingEnabled` is overridable at request time via `GenerationOptions`.

## 3. Data Models & Schemas

### New Domain Model

```kotlin
package com.browntowndev.pocketcrew.domain.model.inference

/**
 * Per-response generation options passed at request time.
 * Derive reasoningBudget from LocalModelConfiguration.thinkingEnabled:
 * - thinkingEnabled = true  → reasoningBudget = 2048
 * - thinkingEnabled = false → reasoningBudget = 0
 */
data class GenerationOptions(
    val reasoningBudget: Int,        // 0 = reasoning OFF, >0 = reasoning ON with budget
    val temperature: Float? = null,
    val topK: Int? = null,
    val topP: Float? = null,
    val minP: Float? = null,
    val maxTokens: Int? = null,
)
```

### Reasoning Resolution

```
LocalModelConfiguration.thinkingEnabled
  → GenerateChatResponseUseCase builds GenerationOptions(
      reasoningBudget = if (config.thinkingEnabled) 2048 else 0
    )
  → sendPrompt passes to engine implementation
  → JniLlamaEngine.generateWithOptions derives budget, penalties, timeout
```

### Settings UI Domain-to-UI Mapping

- `LocalModelConfiguration.thinkingEnabled` → `LocalModelConfigUi.thinkingEnabled` (on load).
- Toggle: `LocalModelConfigUi(thinkingEnabled = it)` → `onConfigChange`.
- On save: `LocalModelConfigUi.thinkingEnabled` → `LocalModelConfiguration.thinkingEnabled` → persist.

**No database or persistence schema changes required.** `thinkingEnabled` already exists on `LocalModelConfigurationEntity`, `LocalModelConfiguration`, and `LocalModelConfigUi` (adding). SHA-256 is already stored on `LocalModelEntity`. The factory cache changes are purely in-memory.

## 4. API Contracts & Interfaces

### InferenceFactoryPort — expanded lifecycle

```kotlin
interface InferenceFactoryPort {
    suspend fun getInferenceService(modelType: ModelType): LlmInferencePort

    /**
     * Registers that a given ModelType is actively using a service instance.
     * Increments the reference count for the SHA-identity.
     */
    suspend fun registerUsage(modelType: ModelType)

    /**
     * Releases a ModelType's reference on a service instance.
     * Decrements reference count; closes the engine when count reaches zero.
     */
    suspend fun releaseUsage(modelType: ModelType)
}
```

### LlmInferencePort — overloaded sendPrompt

```kotlin
interface LlmInferencePort {
    fun sendPrompt(prompt: String, closeConversation: Boolean = false): Flow<InferenceEvent>

    /**
     * Sends a prompt with per-response generation options.
     */
    fun sendPrompt(prompt: String, options: GenerationOptions, closeConversation: Boolean = false): Flow<InferenceEvent>

    suspend fun setHistory(messages: List<ChatMessage>)
    fun closeSession()
}
```

### GenerationOptions resolution

Every role resolves **identically**: read the role's `LocalModelConfiguration.thinkingEnabled`, convert to `GenerationOptions.reasoningBudget`:
- `thinkingEnabled = true` → `reasoningBudget = 2048`
- `thinkingEnabled = false` → `reasoningBudget = 0`

No mode-based overrides. FAST, THINKING, and all CREW roles follow the same rule.

### JniLlamaEngine — per-request generate override

```kotlin
/**
 * Generates with per-request options that override the loaded config.
 */
fun generateWithOptions(options: GenerationOptions): Flow<GenerationEvent>
```

The method uses `options.reasoningBudget` directly:
- `reasoningBudget`: passed to native as `options.reasoningBudget`
- `penaltyFreq`: `0.0f` if `reasoningBudget > 0`, else `0.05f`
- `penaltyPresent`: `0.0f` if `reasoningBudget > 0`, else `0.05f`
- `timeoutSeconds`: `GENERATION_TIMEOUT_SECONDS_THINKING` if `reasoningBudget > 0`, else `GENERATION_TIMEOUT_SECONDS_REGULAR`

## 5. Permissions & Config Delta

No permissions or config changes.

## 6. Constitution Audit

This design adheres to the project's core architectural rules:
- **Domain interfaces** (`InferenceFactoryPort`, `LlmInferencePort`) remain in `core/domain`, with implementations in `feature/inference`.
- **No engine-specific logic** leaks into `GenerateChatResponseUseCase` — it only constructs `GenerationOptions` from the config's `thinkingEnabled`.
- **Settings layer** cleanly maps `LocalModelConfiguration.thinkingEnabled` ↔ `LocalModelConfigUi.thinkingEnabled` with the existing `isSystemPreset` read-only guard.
- **Backward compatibility** is preserved by adding overloads, not replacing existing signatures.
- **No forbidden technologies** referenced (no deprecated APIs, no `runBlocking`/`GlobalScope`).

## 7. Cross-Spec Dependencies

No cross-spec dependencies.

# Implement OpenAI Support via Official Java SDK

## Objective

Implement OpenAI support in the Kotlin inference layer using the official Java SDK (`com.openai:openai-java:4.30.0`) in a way that is correct for the current OpenAI platform and cleanly fits the existing BYOK inference architecture.

This implementation should wire OpenAI-backed API inference into the existing `LlmInferencePort` / inference service architecture, while keeping SDK-specific types isolated to the data or inference layer.

The primary target is **OpenAI itself**, using the **Responses API** as the default integration path. Chat Completions should only be used if the existing abstraction or third-party compatibility requirements make that necessary. OpenAI documents Responses as the primary API for new work, while Chat Completions remains supported. citeturn0search0turn0search1

## Non-Goals

- Do not redesign the broader inference architecture unless the existing abstraction is fundamentally incompatible.
- Do not leak OpenAI SDK request or response models outside the inference/data layer.
- Do not optimize first for generic OpenAI-compatible providers if that would force the implementation onto a legacy OpenAI path.

## Required Architectural Decisions Before Coding

The implementation agent must inspect the current codebase and answer these questions before changing code:

1. Is `ApiInferenceServiceImpl` created per request, per conversation, or reused for the lifetime of a model/config session?
2. Is the current API inference abstraction already shaped for:
   - single-shot text generation
   - streaming partial responses
   - tool calling
   - structured outputs
   - embeddings
3. Does the existing API provider model represent OpenAI specifically, or a generic OpenAI-compatible endpoint abstraction?
4. Are multiple BYOK API configs active at once, requiring a keyed client cache rather than a single global client instance?

These answers determine lifecycle and integration details. They are not optional.

## Correct API Direction

Use the **Responses API** for OpenAI-first support:

- non-streaming: `client.responses().create(...)`
- streaming: `client.responses().createStreaming(...)`

Do **not** build the primary implementation around Chat Completions unless there is a concrete compatibility reason to do so.

The official SDK uses `OpenAIOkHttpClient` and recommends reusing client instances rather than repeatedly constructing them, because clients own connection and thread pools. citeturn0search1

## Implementation Plan

- [x] Task 1. **Add OpenAI SDK Dependency**
  - Add `com.openai:openai-java:4.30.0` to `gradle/libs.versions.toml`.
  - Add the dependency to the inference module (`feature/inference/build.gradle.kts` or equivalent module where `ApiInferenceServiceImpl` lives).
  - Confirm there are no dependency version conflicts with the app's existing OkHttp/Jackson stack.

- [x] Task 2. **Introduce an OpenAI Client Provider**
  - Create a dedicated provider or factory, for example:
    - `OpenAiClientProvider`
    - `OpenAiSdkFactory`
    - or a keyed `OpenAiClientCache`
  - Build clients with `OpenAIOkHttpClient.builder()`.
  - Configure each client with:
    - `apiKey`
    - optional `baseUrl`
    - optional OpenAI `project`
    - optional `organization`
  - Default OpenAI base URL to `https://api.openai.com/v1` only when no custom base URL is present.
  - Do **not** instantiate a fresh SDK client on every request.
  - If BYOK supports multiple configs, cache clients by config ID or equivalent stable key.

  Example target shape:

  ```kotlin
  OpenAIOkHttpClient.builder()
      .apiKey(apiKey)
      .apply {
          if (!baseUrl.isNullOrBlank()) baseUrl(baseUrl)
          if (!projectId.isNullOrBlank()) project(projectId)
          if (!organizationId.isNullOrBlank()) organization(organizationId)
      }
      .build()
  ```

- [x] Task 3. **Define a Clean OpenAI Request Mapping Layer**
  - Create a mapper from internal/domain request models to OpenAI SDK request params.
  - The mapper should convert current app concepts into `ResponseCreateParams`, including:
    - model ID
    - user input
    - optional system/developer instructions
    - temperature
    - top-p if supported by the existing abstraction and desired
    - max output tokens
  - Do not spread SDK builder logic all over `ApiInferenceServiceImpl`.

- [x] Task 4. **Inspect and Decide How Conversation State Should Be Represented**
  - Determine whether `ApiInferenceServiceImpl` is intended to own conversation state.
  - If the current architecture already uses `setHistory(...)` and `sendPrompt(...)` against stateful service instances, it is acceptable to maintain internal session history there.
  - If the current architecture is stateless, do not invent stateful conversation storage just for OpenAI.
  - Remember: OpenAI's API is stateless. Any maintained `history` is an application concern, not an SDK requirement.

- [x] Task 5. **Implement `setHistory(...)` Only If the Current Service Contract Requires It**
  - If `LlmInferencePort` or `ApiInferenceServiceImpl` already expects internal session history:
    - implement `setHistory(messages: List<ChatMessage>)`
    - store the domain messages in an internal representation that can later be mapped into the request
  - Prefer storing **domain-level history** instead of SDK-specific message objects, unless the current abstraction strongly benefits from SDK message types.
  - For the Responses API, history may need to be flattened into the request input/instructions shape used by the current SDK version.
  - Do **not** prematurely bind all history storage to Chat Completions message param types.

- [x] Task 6. **Implement Non-Streaming OpenAI Generation via Responses API**
  - Add a non-streaming path using:
    - `client.responses().create(ResponseCreateParams)`
  - Map the current prompt and any retained conversation context into the request.
  - If the app abstraction expects a final text response, add a local extraction helper in the inference layer to pull text from the SDK response object.
  - Keep all parsing/extraction details inside the OpenAI adapter layer.

- [x] Task 7. **Implement Streaming `sendPrompt(...)` via Responses API**
  - Implement `sendPrompt(prompt: String, options: GenerationOptions, closeConversation: Boolean)` using:
    - `client.responses().createStreaming(params)`
  - Wrap the streaming call in a Kotlin `flow { ... }` or `callbackFlow { ... }`, whichever best matches the existing architecture.
  - Run network and stream collection on `Dispatchers.IO` if using the blocking client.
  - Stream text deltas into:
    - `InferenceEvent.PartialResponse(...)`
  - Emit final success completion:
    - `InferenceEvent.Finished(...)`
  - Emit:
    - `InferenceEvent.Error(...)`
    on SDK/network/stream failures
  - If a final accumulated response object is useful, use `ResponseAccumulator` from the SDK while streaming. The official SDK exposes `createStreaming(...)` and `ResponseAccumulator` for streamed Responses handling. citeturn0search1

- [x] Task 8. **Decide Whether `closeConversation` Only Clears App Session State**
  - `closeConversation` and `closeSession()` should be treated as application-level session lifecycle controls.
  - For most OpenAI SDK usage, there is no special remote “conversation close” operation to call here.
  - If internal app history is maintained, `closeSession()` should clear that history.
  - Only perform explicit SDK resource cleanup if the SDK/client actually requires it in the current usage pattern.

- [x] Task 9. **Add a Compatibility Decision for Chat Completions**
  - If the current API inference abstraction is fundamentally shaped around Chat Completions style message arrays and switching to Responses would create disproportionate churn, document that decision explicitly.
  - In that case, Chat Completions may be used as an adaptation layer for compatibility.
  - If this compatibility path is taken:
    - clearly mark it as a compatibility-driven implementation
    - do not claim it is the preferred modern OpenAI integration path

- [x] Task 10. **Prepare Extension Points for Structured Outputs and Tool Calling**
  - Do not necessarily implement these in the first pass unless the app already needs them.
  - But design the OpenAI adapter so that later support is straightforward for:
    - structured outputs (`text(Class<T>)` style typed schema use)
    - tool/function calling (`addTool(Class<T>)` and tool loop handling)
  - Avoid locking the adapter into “plain text only” if the surrounding inference architecture is meant to support agent workflows later.

- [x] Task 11. **Implement Robust Error Mapping**
  - Catch SDK and network failures inside the OpenAI adapter layer.
  - Map them to the app's sealed error/event model.
  - Do not leak SDK exception classes upward into domain/UI code.
  - Preserve useful diagnostic data without logging secrets or raw API keys.

- [x] Task 12. **Create Unit Tests**
  - Create tests for the OpenAI inference adapter/service covering:
    - correct request mapping from domain models to `ResponseCreateParams`
    - `setHistory(...)` behavior, if stateful history is part of the current service contract
    - streaming event emission order
    - non-streaming response extraction
    - error mapping
    - `closeSession()` clearing app-managed conversation state
  - Prefer testing the mapping and event behavior behind interfaces rather than testing raw SDK builders in every assertion.

## Verification Criteria

- [x] `com.openai:openai-java:4.30.0` resolves successfully in the intended module.
- [x] The OpenAI client is created through a reusable provider/factory/cache, not recreated for every request.
- [x] The implementation uses the **Responses API** as the primary OpenAI path.
- [x] Streaming is implemented via `client.responses().createStreaming(...)`, not a nonexistent `stream(...)` call.
- [x] Domain generation settings are mapped correctly into OpenAI request params.
- [x] The adapter emits `InferenceEvent.PartialResponse(...)` followed by `InferenceEvent.Finished(...)` on success.
- [x] Errors are surfaced as `InferenceEvent.Error(...)`.
- [x] If app-managed conversation history exists, it is correctly preserved and cleared by session lifecycle calls.
- [x] The implementation supports custom `baseUrl` values where the existing BYOK architecture allows them.

## Potential Risks and Mitigations

### 1. Wrong Endpoint Shape
**Risk:** The implementation accidentally uses Chat Completions as the primary path, even though the goal is correct OpenAI integration with the official modern API.

**Mitigation:** Treat Responses as the default implementation path. Only use Chat Completions behind an explicit compatibility decision.

### 2. Client Lifecycle Bugs
**Risk:** Recreating the SDK client per request causes unnecessary overhead and poor connection reuse.

**Mitigation:** Centralize client construction in a reusable provider/cache and key it by API config if needed.

### 3. State Model Confusion
**Risk:** The implementation stores conversation state inside the service without confirming whether the current architecture expects that.

**Mitigation:** Inspect the current service lifecycle and preserve the existing architectural model. Only maintain internal history if the abstraction already wants stateful session behavior.

### 4. Blocking Stream Collection on Main Thread
**Risk:** The blocking SDK call path is collected from the wrong dispatcher.

**Mitigation:** Ensure stream collection occurs on `Dispatchers.IO` or use an async adaptation if the current architecture strongly prefers it.

### 5. SDK Type Leakage
**Risk:** OpenAI SDK response/request models start creeping into domain or UI layers.

**Mitigation:** Contain all SDK types to the OpenAI inference adapter layer and map back to internal app models immediately.

## Alternative Approaches

### 1. Compatibility-First Chat Completions Adapter
Use Chat Completions if the existing provider abstraction is tightly coupled to old-style role-based message arrays and the short-term goal is maximizing compatibility across OpenAI-like providers.

**Tradeoff:** Easier adaptation in some existing architectures, but not the preferred modern OpenAI path.

### 2. Raw HTTP / Retrofit / Ktor Integration
Implement OpenAI calls manually over HTTP instead of using the official SDK.

**Tradeoff:** More control and potentially less dependency surface, but significantly more maintenance and a worse fit for “use the official OpenAI Java library.”

### 3. Async SDK Adaptation
Use async client patterns and bridge them into Kotlin flows.

**Tradeoff:** Potentially more coroutine-idiomatic, but only worth it if the current codebase already strongly prefers async SDK usage patterns.

## Final Guidance to the Implementation Agent

Implement OpenAI support in a way that is faithful to the current OpenAI SDK and current OpenAI API direction.

That means:

- start from the existing codebase structure
- preserve the current app architecture where reasonable
- use a reusable `OpenAIOkHttpClient`
- use the **Responses API** as the primary path
- keep SDK types isolated
- only fall back to Chat Completions if compatibility pressure makes it the right trade

Do not freestyle the endpoint choice. Do not assume history storage belongs inside the service unless the current architecture already works that way. Do not build the primary implementation around a nonexistent `stream(...)` method.

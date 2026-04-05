# OpenAI Integration Implementation Plan

## Objective

Implement support for the official OpenAI Java SDK (`com.openai:openai-java`) within the `ApiInferenceServiceImpl` to enable BYOK (Bring Your Own Key) inference for API models. This will allow the application to route requests to OpenAI-compatible endpoints using the architecture we just established.

*Note: The specific detailed document mentioned in the prompt was not provided. This plan is based on standard integration practices for the official `com.openai:openai-java` SDK and our existing `LlmInferencePort` abstraction.*

## Implementation Plan

- [ ] Task 1. **Add OpenAI SDK Dependency**
  - Update `gradle/libs.versions.toml` to include the `com.openai:openai-java` dependency (e.g., version `0.x.x` or latest stable).
  - Update `feature/inference/build.gradle.kts` to implement the new dependency.

- [ ] Task 2. **Update ApiInferenceServiceImpl Initialization**
  - Modify `ApiInferenceServiceImpl` to instantiate `OpenAIAsyncClient` (or `OpenAIClient`) upon creation.
  - Configure the client using `OpenAIOkHttpClient.builder().apiKey(apiKey).baseUrl(baseUrl).build()`.
  - Add a mutable list to maintain the conversation history: `private val history = mutableListOf<ChatCompletionMessageParam>()`.

- [ ] Task 3. **Implement setHistory**
  - Implement `setHistory(messages: List<ChatMessage>)` to clear the internal `history` list.
  - Map each domain `ChatMessage` to the corresponding OpenAI `ChatCompletionMessageParam` (e.g., `ChatCompletionUserMessageParam`, `ChatCompletionAssistantMessageParam`, `ChatCompletionSystemMessageParam` based on the `Role`).
  - Add the mapped messages to the internal `history` list.

- [ ] Task 4. **Implement sendPrompt (Streaming)**
  - Implement `sendPrompt(prompt: String, options: GenerationOptions, closeConversation: Boolean)`.
  - Map the new user prompt to a `ChatCompletionUserMessageParam` and append it to the internal `history`.
  - Build `ChatCompletionCreateParams` using the internal `history`, `modelId`, and `options` (mapping our `GenerationOptions` to OpenAI parameters like `temperature`, `maxTokens`, etc.).
  - Ensure `stream(true)` is set on the request parameters.
  - Call `client.chat().completions().stream(params)` to get the stream response.
  - Wrap the stream in a Kotlin `flow { ... }`.
  - Iterate over the stream chunks. For each chunk, extract the text delta and emit `InferenceEvent.PartialResponse(chunkText, ModelType.API)`.
  - Emit `InferenceEvent.Finished(ModelType.API)` when the stream completes successfully.
  - Catch any `OpenAIException` or network exceptions and emit `InferenceEvent.Error(cause, ModelType.API)`.

- [ ] Task 5. **Implement closeSession**
  - Implement `closeSession()` to clear the internal `history`.
  - If the `OpenAIAsyncClient` requires explicit resource cleanup (like shutting down its OkHttp dispatcher/connection pool), perform it here.

- [ ] Task 6. **Update Tests**
  - Create `ApiInferenceServiceImplTest` to verify:
    - `setHistory` correctly maps domain messages to OpenAI parameters.
    - `sendPrompt` correctly maps generation options and handles the streaming response, emitting the correct sequence of `InferenceEvent`s.
    - Error handling properly emits `InferenceEvent.Error`.

## Verification Criteria

- [ ] `com.openai:openai-java` dependency is successfully resolved and available in the `feature:inference` module.
- [ ] `ApiInferenceServiceImpl` successfully maps domain `ChatMessage` and `GenerationOptions` to OpenAI SDK equivalents.
- [ ] `sendPrompt` correctly streams responses and emits `InferenceEvent.PartialResponse` followed by `InferenceEvent.Finished`.
- [ ] The implementation handles custom `baseUrl` correctly for OpenAI-compatible endpoints (e.g., Groq, local LLM servers).
- [ ] Unit tests for `ApiInferenceServiceImpl` pass and verify both success and error states.

## Potential Risks and Mitigations

1. **Dependency Conflicts**
   Mitigation: The OpenAI Java SDK uses OkHttp and Jackson/Moshi under the hood. Ensure there are no version conflicts with our existing `okhttp` or serialization dependencies in `libs.versions.toml`.
2. **Blocking Network Calls**
   Mitigation: Ensure that the OpenAI SDK stream is collected on a background dispatcher (e.g., `Dispatchers.IO`) within the `flow { ... }` builder to prevent blocking the main thread during network I/O.
3. **Missing Document Context**
   Mitigation: The user forgot to paste the specific implementation document. This plan relies on standard SDK integration. If the document contains specific workarounds or architectural mandates (like using a specific async adapter), the plan will need to be adjusted.

## Alternative Approaches

1. **Raw Retrofit/Ktor Implementation**: Instead of using the official SDK, we could build a custom Retrofit interface for the OpenAI Chat Completions endpoint. This reduces dependency bloat but increases maintenance overhead for API changes.
2. **Sync vs Async Client**: We could use the synchronous `OpenAIClient` and wrap it in `runInterruptible(Dispatchers.IO)` instead of the async client, depending on which streaming API (Java Streams vs CompletableFuture/RxJava) maps more cleanly to Kotlin Flow.
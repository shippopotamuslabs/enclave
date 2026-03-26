# Specification: Shared Context Between Engines (LiteRT & MediaPipe)

## 1. System Architecture

The core of this implementation involves updating the `inference` feature module to handle stateful conversation history for LiteRT and MediaPipe backends, similar to how it's handled for Llama.cpp. This ensures that when a user resumes a chat, the model has full context of previous messages.

### Modified Files:
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/inference/ConversationManagerPort.kt`: Update to include `setHistory` method.
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/ConversationManagerImpl.kt`: Implement `setHistory` and update `getConversation` to use the history as a `initialMessages`.
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/LiteRtInferenceServiceImpl.kt`: Update `setHistory` to delegate to `ConversationManagerPort`.
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/MediaPipeInferenceServiceImpl.kt`: Update `setHistory` to cache and inject history via `addQueryChunk`.

## 2. Internal Interfaces & Contracts

### Domain Port Update (`ConversationManagerPort.kt`)
```kotlin
interface ConversationManagerPort {
    // Existing methods...
    
    /**
     * Sets the historical messages for the conversation.
     * These will be injected as a initialMessages when the conversation is initialized or updated.
     * 
     * @param messages The list of historical messages.
     */
    fun setHistory(messages: List<DomainChatMessage>)
}
```

### Feature Implementation (`ConversationManagerImpl.kt`)
- **State**: Add a private `history: List<DomainChatMessage>` field.
- **`setHistory(messages)`**: 
    1. Check if the new history is different from the cached history.
    2. If different, update the cached history and null out the current `conversation` and `conversationPort` to trigger recreation on the next `getConversation()` call.
- **`getConversation()`**:
    - When creating `ConversationConfig`, map the cached `history` to `List<com.google.ai.edge.litertlm.Message>`.
    - Use `Message.ofRoleUser(content)` for `USER` role.
    - Use `Message.ofRoleModel(content)` for `ASSISTANT` role.
    - Pass this list to the `initialMessages` parameter of `ConversationConfig`.

### MediaPipe Implementation (`MediaPipeInferenceServiceImpl.kt`)
- **State**: Add a private `history: List<DomainChatMessage>` field.
- **`setHistory(messages)`**: Simply cache the list in the `history` field.
- **`sendPrompt(prompt, ...)`**:
    - Before calling `currentSession.addQueryChunk(prompt)`, if `history` is not empty and it's a new session, iterate through the history:
        - For each message, determine the label based on role: `User` for `USER`, `Assistant` for `ASSISTANT`.
        - Format the message as `{label}: {content}\n`.
        - Call `currentSession.addQueryChunk(formattedMessage)` for each historical message sequentially.
    - *Note*: MediaPipe's `LlmInferenceSession` is stateful. If the session persists, we don't need to re-add history. If the session is closed/recreated, we re-inject the cached history.
    - *Future-Proofing*: By using sequential calls, we allow the SDK to potentially handle role boundaries if it becomes role-aware in future updates, while providing a clean, standard text separator as a robust fallback.

## 3. State Management

- **Invalidation Strategy (LiteRT)**: The `ConversationManagerImpl` will invalidate its active `Conversation` if `setHistory` is called with a list that differs from the currently cached one. This ensures that the next request uses a fresh `Conversation` initialized with the correct `initialMessages`.
- **MediaPipe Seeding**: History seeding in MediaPipe happens once per `LlmInferenceSession`. If `sendPrompt` is called with `closeConversation = true`, the next call will create a new session and re-seed.

## 4. Configuration Delta

- No changes to `build.gradle.kts` or Proguard rules are anticipated, as we are using existing SDK classes (`Message`, `ConversationConfig`, `LlmInferenceSession`).

## 5. Constitution Audit

- **`ARCHITECTURE_RULES.md`**: COMPLIANT. Domain ports (`ConversationManagerPort`) remain free of SDK dependencies. The implementation details are confined to the `feature:inference` module.
- **`DATA_LAYER_RULES.md`**: COMPLIANT. History rehydration follows the existing pattern from `GenerateChatResponseUseCase`. No direct DB access in inference services.

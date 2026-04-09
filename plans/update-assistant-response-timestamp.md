# Update AssistantResponse Timestamp Plan

## Objective
Accurately display the time an `AssistantResponse` finishes generating, rather than the time the message was originally created in the database.

## Background & Motivation
Currently, the UI displays `formattedTimestamp` based on the `createdAt` value of the `Message` entity. For assistant messages, this timestamp represents when the streaming generation started (which is essentially identical to when the user sent their prompt). The user wants the timestamp to reflect when the generation *finishes*.

Since the application fetches and sorts messages chronologically by `id ASC` (verified in `MessageDao.kt` and `ChatRepositoryImpl.kt`), updating the `createdAt` timestamp of a message after it is inserted will **not** break the chronological ordering of the chat history.

## Proposed Solution
We will update the `createdAt` column of the `MessageEntity` to `System.currentTimeMillis()` whenever the `messageState` transitions to `MessageState.COMPLETE`.

This approach avoids the complexity and risk of a Room Database schema migration (bumping the DB version and adding a new `completedAt` column) while fully satisfying the UI requirement.

## Implementation Steps

### 1. `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/MessageDao.kt`
- Add a new query method to explicitly update the `createdAt` timestamp:
  ```kotlin
  @Query("UPDATE message SET created_at = :timestamp WHERE id = :id")
  abstract suspend fun updateMessageCreatedAt(id: Long, timestamp: Long)
  ```
- In the existing `@Transaction open suspend fun persistAllMessageData(...)`, after calling `updateMessageState(messageId, messageState)`, add a conditional block:
  ```kotlin
  if (messageState == MessageState.COMPLETE) {
      updateMessageCreatedAt(messageId, System.currentTimeMillis())
  }
  ```

### 2. `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ChatRepositoryImpl.kt`
- Modify `updateMessageState(messageId: Long, messageState: MessageState)` to update the timestamp if the state is `COMPLETE`:
  ```kotlin
  override suspend fun updateMessageState(messageId: Long, messageState: MessageState) {
      messageDao.updateMessageState(messageId, messageState)
      if (messageState == MessageState.COMPLETE) {
          messageDao.updateMessageCreatedAt(messageId, System.currentTimeMillis())
      }
  }
  ```
- Modify `clearThinking(messageId: Long)` to also update the timestamp when it sets the state to `COMPLETE`:
  ```kotlin
  override suspend fun clearThinking(messageId: Long) {
      messageDao.updateThinkingRaw(messageId, null)
      messageDao.updateThinkingStartTime(messageId, 0)
      messageDao.updateThinkingEndTime(messageId, 0)
      messageDao.updateMessageState(messageId, MessageState.COMPLETE)
      messageDao.updateMessageCreatedAt(messageId, System.currentTimeMillis())
  }
  ```

## Verification
- Send a message to the assistant and observe the generation process.
- The `AssistantResponse` timestamp will initially be the time the generation started.
- Upon completion of the generation, the timestamp should update to accurately reflect the completion time.
- Message ordering in the chat should remain unaffected.
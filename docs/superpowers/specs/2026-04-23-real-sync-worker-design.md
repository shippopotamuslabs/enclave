# Spec: Real SyncWorker Implementation (Phase 1, Part B, Piece 2)

## Status: Approved
**Date:** 2026-04-23
**Author:** Gemini CLI

## 1. Overview
Implement the production-ready `SyncWorker` and update `CloudSyncPort` and `FirestoreDataSource` to support encrypted, batch-based surgical synchronization of chats and messages.

## 2. Requirements
- `SyncWorker` must fetch pending changes from `ChatDao` and `MessageDao`.
- `SyncWorker` must retry if `CryptoManagerPort.hasCachedKey()` is false.
- `CloudSyncPort` must provide a method for syncing specific entities.
- `FirestoreDataSource` must implement surgical sync using Firestore batches.
- Sensitive data (`chat.name`, `message.content`) must be encrypted before pushing to Firestore.
- Encrypted data must be stored as Firestore `Blob` types.
- `FirestoreDataSource` must handle decryption when pulling data down if a key is available.

## 3. Architecture Changes

### 3.1 CloudSyncPort.kt
Add the following method:
```kotlin
suspend fun syncEntitiesToCloud(
    chats: List<ChatEntity>,
    messages: List<MessageEntity>
): Result<Unit>
```

### 3.2 FirestoreDataSource.kt
- Inject `CryptoManagerPort`.
- Implement `syncEntitiesToCloud`:
    - Use `firestore.batch()`.
    - Encrypt sensitive fields using `cryptoManager.encrypt(text).getOrThrow()` if `hasCachedKey()` is true.
    - Map `ByteArray` to `com.google.firebase.firestore.Blob`.
    - Commit batch.
- Update `chatEntityFromDoc` and `messageEntityFromDoc`:
    - Detect if field is `String` or `Blob`.
    - If `Blob`, decrypt using `cryptoManager.decrypt(blob.toBytes())`.
    - Handle missing key by returning `"[Encrypted]"`.

### 3.3 SyncWorker.kt
- Implement `doWork()`:
    - Fetch `chatDao.getPendingSyncChats()` and `messageDao.getPendingSyncMessages()`.
    - If both empty, return `Result.success()`.
    - If not empty, check `cryptoManager.hasCachedKey()`.
    - If no key, return `Result.retry()`.
    - Call `cloudSyncPort.syncEntitiesToCloud(chats, messages)`.
    - On success, call `chatDao.markSynced(chatIds)` and `messageDao.markSynced(messageIds)`.
    - Return `Result.success()`.

## 4. Security
- Encryption uses AES-256-GCM via `CryptoManagerPort`.
- Plaintext never hits Firestore if encryption is enabled.
- The system defaults to retrying sync until the key is available, ensuring data is never pushed unencrypted if it's supposed to be encrypted.

## 5. Testing Strategy
- **Unit Tests:** Verify `SyncWorker` logic (retries, DAO calls, Port calls).
- **Manual Verification:** Check Firestore Console for `Blob` data types in `content` and `name` fields.

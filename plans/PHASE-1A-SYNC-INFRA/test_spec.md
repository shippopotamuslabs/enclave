# Test Specification: Database and Sync Infrastructure (Phase 1A)

## 1. Happy Path Scenarios

### Scenario: Local Mutation Sets Pending Sync
- **Given:** A chat exists locally with `pending_sync = false`.
- **When:** `ChatDao.updatePinStatus(chatId)` is called.
- **Then:** The chat in the database has `pending_sync = true`.

### Scenario: SyncWorker Pushes Pending Data
- **Given:** A chat and a message both have `pending_sync = true`.
- **When:** `SyncWorker.doWork()` is executed.
- **Then:** `CloudSyncPort.syncAllLocalDataToCloud()` is called (or targeted sync), and after success, `pending_sync` is set to `false` for both entities.

### Scenario: FirestoreDataSource Encrypts Content
- **Given:** A message with content "Secret Message" and `CryptoManagerPort` has a cached key.
- **When:** `FirestoreDataSource.syncAllLocalDataToCloud()` is called.
- **Then:** `CryptoManagerPort.encrypt("Secret Message")` is called, and the encrypted bytes are pushed to Firestore.

## 2. Error Path & Edge Case Scenarios

### Scenario: FirestoreDataSource Fails Without Key
- **Given:** `CryptoManagerPort.hasCachedKey()` returns `false`.
- **When:** `FirestoreDataSource.syncAllLocalDataToCloud()` is called.
- **Then:** An `IllegalStateException` is thrown with message "Encryption key not cached".

### Scenario: Sync Fails Gracefully
- **Given:** `CloudSyncPort.syncAllLocalDataToCloud()` fails with an exception.
- **When:** `SyncWorker.doWork()` is executed.
- **Then:** `SyncWorker` returns `Result.retry()` and `pending_sync` remains `true`.

## 3. Mutation Defense
### Lazy Implementation Risk
The most likely lazy implementation is a `SyncWorker` that sets `pending_sync = false` BEFORE successfully pushing to the cloud, or one that ignores encryption.

### Defense Scenario
- **Given:** Data is marked `pending_sync = true`.
- **When:** `SyncWorker.doWork()` starts, but cloud push fails.
- **Then:** `pending_sync` MUST still be `true` in the database to ensure it's picked up by the next run.

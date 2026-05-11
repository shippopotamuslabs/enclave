# Discovery: Database and Sync Infrastructure (Phase 1A)

## 1. Goal Summary
Implement the foundational database and synchronization infrastructure to support cloud sync with encryption. This includes adding sync tracking to local entities, updating DAOs to flag mutations, implementing a sync worker, and ensuring secure data transmission to Firestore.

## 2. Target Module Index

### Existing Data Models
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ChatEntity.kt`: ChatEntity — Local representation of a chat.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/MessageEntity.kt`: MessageEntity — Local representation of a message.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/PocketCrewDatabase.kt`: PocketCrewDatabase — Room database definition.

### Dependencies & API Contracts
- `androidx.room`: Room Persistence Library.
- `com.google.firebase:firebase-firestore`: Firestore for cloud storage.
- `com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort`: Port for cloud synchronization.
- `com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort`: Port for encryption/decryption.

### Utility/Shared Classes
- `com.browntowndev.pocketcrew.core.data.remote.FirestoreDataSource`: Implementation of CloudSyncPort.

### Impact Radius
- `ChatEntity.kt`, `MessageEntity.kt`: Schema updates.
- `PocketCrewDatabase.kt`: Version bump and migration.
- `ChatDao.kt`, `MessageDao.kt`: Mutation flagging.
- `FirestoreDataSource.kt`: Encryption implementation and sync logic updates.
- `SyncWorker.kt` (New): Periodic sync execution.

## 3. Cross-Probe Analysis
### Overlaps Identified
- `ChatEntity` and `MessageEntity` are central to both local storage and remote sync.
- `CloudSyncPort` is the bridge between the data layer and domain logic.

### Gaps & Uncertainties
- How should existing data (before migration) be handled? Should `pending_sync` be true or false by default for legacy data?
- Should the `SyncWorker` be triggered immediately on mutation or wait for periodic execution?

### Conflicts (if any)
- None identified.

## 4. High-Impact Clarifying Questions
*None identified. Proceeding to Spec phase.*

## 5. Probe Coverage Summary
| Layer/Directory | Probe Agent | Key Findings |
|----------------|------------|-------------|
| core/data/local | Initial Probe | Entities, DAOs, Database structure identified. |
| core/data/remote | Initial Probe | FirestoreDataSource logic identified. |
| core/domain/port | Initial Probe | CryptoManagerPort interface identified. |

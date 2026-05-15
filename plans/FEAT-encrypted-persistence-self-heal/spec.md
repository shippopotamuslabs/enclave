# Spec: Encrypted Persistence Self-Heal

## Objective
Build a catalog-driven self-healing layer that detects plaintext at every Room, DataStore, EncryptedSharedPreferences, and Firestore persistence boundary for synced or persisted sensitive data. When the vault is unlocked, the app must classify the source/entity/field, log only sanitized metadata, encrypt the value, persist the repaired value in the intended direction, and remove plaintext from the source side.

## File Structure

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/PersistenceSelfHealModels.kt` | Define self-heal source, action, result, exception, and sanitized event types. |
| Create | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitiveFieldCatalog.kt` | Central catalog of every persisted owner audited by this feature, including owners with zero sensitive fields. |
| Create | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitivePersistenceSelfHealer.kt` | Classify plaintext/encrypted/null values, encrypt when unlocked, and return deterministic repair results without logging payloads. |
| Create | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitiveEntityFieldEncryptors.kt` | Provide entity copy helpers that encrypt or assert encryption for all cataloged Room entity fields. |
| Create | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreSensitiveFieldRepairer.kt` | Repair inbound and outbound Firestore maps and documents by collection/field before Room writes or cloud writes. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/LocalDataEncryptionService.kt:3-19` | Add self-heal-facing encryption detection and locked-vault semantics without changing envelope format. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/CryptoAtRestEncryptionService.kt:8-31` | Implement the extended `LocalDataEncryptionService` contract using the existing `PCENC:v1:aes-gcm:` envelope. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/DataModule.kt:70-493` | Bind `SensitivePersistenceSelfHealer`, `SensitiveFieldCatalog`, and `FirestoreSensitiveFieldRepairer` into `:core:data`. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt:119-1895` | Route every adapter through catalog-based Firestore validation, outbound repair, inbound repair, local source repair, and sanitized warnings. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ChatRepositoryImpl.kt:40-440` | Use shared entity encryptors for chat, message, web search source, transcript segment, document, and message file persistence/read paths. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/MessageRepositoryImpl.kt:66-327` | Use shared entity encryptors for message, message file, vision analysis, and chat summary persistence/read paths. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/MemoriesRepositoryImpl.kt:20-116` | Use self-heal helpers for memory content on insert, update, read, search, and embedding regeneration boundaries. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ApiModelRepositoryImpl.kt:38-283` | Use shared helpers for API credentials and API model configuration encrypted fields. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/LocalModelRepositoryImpl.kt:33-218` | Use shared helpers for local model configuration display names and system prompts. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/TtsProviderRepositoryImpl.kt:24-80` | Use shared helpers for TTS provider names, voice names, model names, and base URLs. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/MediaProviderRepositoryImpl.kt:24-70` | Use shared helpers for media provider names, model names, and base URLs. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/StudioRepositoryImpl.kt:31-268` | Use shared helpers for studio album/media prompts, URIs, and remote URLs. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/artifact/ArtifactToolExecutor.kt:61-264` | Use document helpers for artifact document creation, merge, and read paths. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/artifact/SearchArtifactToolExecutor.kt:25-60` | Decrypt through document helpers before searching document JSON. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/SettingsRepositoryImpl.kt:21-209` | Encrypt `settings_custom_prompt_text` in DataStore and self-heal plaintext on read/update. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/PipelineStateRepositoryImpl.kt:20-57` | Encrypt `pipeline_state_$chatId` values and self-heal plaintext on read/update. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/ApiKeyManager.kt:15-172` | Self-heal plaintext API key values in EncryptedSharedPreferences and never sync plaintext to Firestore. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/LocalVaultMetadataStore.kt:13-54` | Audit vault bootstrap metadata as explicitly outside `LocalDataEncryptionService` wrapping while preserving existing salt, verification, and wrapped-DEK semantics. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/AndroidBiometricManager.kt:49-135` | Audit biometric SharedPreferences values as Android Keystore ciphertext/IV bootstrap material and ensure logs never include payloads. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadSessionManager.kt:13-48` | Audit persistent download session IDs as non-sensitive local metadata. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/MessageFileCloudSyncWorker.kt:29-105` | Repair staged message file URLs and uploaded remote URLs before local update and Firestore sync. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/MediaCloudSyncWorker.kt:29-100` | Repair staged studio media URLs and uploaded remote URLs before local update and Firestore sync. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/EmbeddingRegenerationWorker.kt:36-192` | Decrypt only through self-healing helpers and enqueue repaired sources before embedding document/message/memory content. |
| Create | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitiveFieldCatalogTest.kt` | Assert the exhaustive entity/field catalog matches this audit matrix. |
| Create | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitivePersistenceSelfHealerTest.kt` | Unit tests for plaintext classification, repair, locked-vault deferral, sanitized logs, and idempotency. |
| Create | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitiveEntityFieldEncryptorsTest.kt` | Unit tests for Room entity field encryption coverage. |
| Create | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreSensitiveFieldRepairerTest.kt` | Unit tests for Firestore inbound/outbound repair and source-side cleanup metadata. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSourceTest.kt:1-260` | Add sync adapter self-heal tests for representative Room-to-Firestore and Firestore-to-Room flows. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/MessageTranscriptSegmentFirestoreCodecTest.kt:1-120` | Assert transcript text remains encrypted through codec boundaries. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/ChatRepositoryImplTest.kt:1-260` | Add chat/message/document/web-search/transcript plaintext repair tests. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/MessageRepositoryImplSaveMessageTest.kt:1-180` | Add message file and vision-analysis repair tests. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/ApiModelRepositoryImplTest.kt:1-490` | Add API credentials and API model configuration repair tests. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/LocalModelRepositoryImplTest.kt:1-160` | Add local model configuration repair tests. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/MemoriesRepositoryTest.kt:1-180` | Add memory plaintext repair tests. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/SettingsRepositoryImplTest.kt:1-220` | Add DataStore custom prompt plaintext repair tests. |
| Create | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/PipelineStateRepositoryImplTest.kt` | Add pipeline DataStore plaintext repair tests. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/TtsProviderRepositoryImplTest.kt:1-120` | Add TTS provider plaintext repair tests. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/MediaProviderRepositoryImplTest.kt:1-120` | Add media provider plaintext repair tests. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/StudioRepositoryImplTest.kt:1-280` | Add studio album/media plaintext repair tests. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/ApiKeyManagerTest.kt:1-220` | Add EncryptedSharedPreferences/API key repair tests. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/LocalVaultMetadataStoreTest.kt:1-120` | Add audit tests for vault metadata bootstrap fields and ensure they are not treated as local data payload fields. |
| Create | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/AndroidBiometricManagerTest.kt` | Add audit tests for biometric ciphertext/IV persistence and sanitized logging. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadSessionManagerTest.kt:1-100` | Add audit tests proving download session IDs are non-sensitive local metadata. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/MessageFileCloudSyncWorkerTest.kt:1-130` | Add remote URL repair tests. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/MediaCloudSyncWorkerTest.kt:1-130` | Add studio media upload URL repair tests. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/EmbeddingRegenerationWorkerTest.kt:1-210` | Add embedding consumer repair tests. |

## Data Models & Schemas
No Room schema changes. All repair state is derived at runtime from existing fields and existing `pending_sync`, `updated_at`, and sync-status columns.

### Entity / Field Audit Matrix

| Persistence boundary | Entity or owner | Sensitive fields requiring encrypted-at-rest values | Synced | Source repair requirement |
|----------------------|-----------------|------------------------------------------------------|--------|---------------------------|
| Room + Firestore | `ChatEntity` / `chats` | `name` | Yes | Repair Room before Firestore upload; repair Firestore document before Room upsert. |
| Room + Firestore | `MessageEntity` / `messages` | `content`, `imageUrisJson`, `thinkingRaw`, `artifactsJson`, `generatedMediaUrisJson`, `generatedMediaAssetIdsJson`, `generatedMediaPromptsJson` | Yes | Repair Room before upload; repair Firestore before local insert; leave `generatedMediaTypesJson` plaintext because it is non-sensitive enum metadata. |
| Room + Firestore | `ChatSummaryEntity` / `chat_summaries` | `content` | Yes | Repair both local and remote sides. |
| Room + Firestore | `MemoriesEntity` / `memories` | `content` | Yes | Repair both local and remote sides and enqueue embedding regeneration when repaired content hash changes. |
| Room + Firestore | `MessageVisionAnalysisEntity` / `vision_analysis` | `imageUri`, `promptText`, `analysisText` | Yes | Repair both local and remote sides. |
| Room + Firestore + Storage workers | `MessageFileEntity` / `message_files` | `name`, `content`, `remoteUrl`, `stagingRemoteUrl` | Yes | Repair `name`, `remoteUrl`, and local-only `content`/`stagingRemoteUrl`; sync workers must write encrypted URLs. |
| Room + Firestore | `WebSearchSourceEntity` / `web_search_sources` | `title`, `url`, `content` | Yes | Repair both local and remote sides and ensure extraction flags remain plaintext metadata. |
| Room + Firestore | `MessageTranscriptSegmentEntity` / `message_transcript_segments` | `text` | Yes | Repair both local and remote sides through `MessageTranscriptSegmentFirestoreCodec`. |
| Room + Firestore | `ApiModelConfigurationEntity` / `api_model_configs` | `displayName`, `customHeaders`, `systemPrompt` | Yes | Repair both local and remote sides; numeric tuning fields remain plaintext metadata. |
| Room + Firestore | `LocalModelConfigurationEntity` / `local_model_configs` | `displayName`, `systemPrompt` | Yes | Repair both local and remote sides. |
| Room + Firestore | `DefaultModelEntity` / `default_models` | None | Yes | IDs and model type assignments remain plaintext metadata. |
| Room + Firestore | `LocalModelEntity` / `local_models` | None | Yes | Download metadata remains plaintext catalog metadata. |
| Room + Firestore | `TtsProviderEntity` / `tts_providers` | `displayName`, `voiceName`, `modelName`, `baseUrl` | Yes | Repair both local and remote sides; `credentialAlias` remains plaintext lookup metadata. |
| Room + Firestore | `MediaProviderEntity` / `media_providers` | `displayName`, `modelName`, `baseUrl` | Yes | Repair both local and remote sides; `credentialAlias` remains plaintext lookup metadata. |
| Room + Firestore | `ApiCredentialsEntity` / `api_credentials` | `displayName`, `baseUrl`, `encryptedApiKey` | Yes | Repair string fields both sides; `encryptedApiKey` must remain a non-empty encrypted blob and never accept a string API key from Firestore. |
| EncryptedSharedPreferences + Firestore | `ApiKeyManager` / `api_keys` | API key value by alias | Yes | Keep local preference value under platform encrypted prefs; Firestore stores only `encrypted_key` blob; plaintext Firestore API key fields are rejected and deleted when unlocked. |
| Room + Firestore | `StudioAlbumEntity` / `studio_albums` | `name` | Yes | Repair both local and remote sides. |
| Room + Firestore + Storage workers | `StudioMediaEntity` / `studio_gallery` | `prompt`, `mediaUri`, `remoteUrl`, `remoteThumbnailUrl`, `stagingRemoteUrl` | Yes | Repair prompt and URLs both sides; local-only `mediaUri`/`stagingRemoteUrl` must be encrypted by repositories/workers. |
| Room + Firestore | `DocumentEntity` / `documents` | `json`, `title`, `author` | Yes | Replace current remote-only rejection with repair; enqueue document embedding when repaired JSON differs. |
| Room vector metadata | `EmbeddingEntity` / `embeddings` | None | No | Stores hashes only; no self-heal action. |
| SQLite vec tables | `document_embeddings`, `memory_embeddings` | Embedding vectors | No | Derived vectors remain local-only and are regenerated from decrypted source content after source repair. |
| DataStore | `SettingsRepositoryImpl` / `settings_custom_prompt_text` | Custom prompt text | No | Repair DataStore value on read/update when vault is unlocked; booleans/enums/key-present flags stay plaintext. |
| DataStore | `PipelineStateRepositoryImpl` / `pipeline_state_$chatId` | Serialized `PipelineState` JSON | No | Repair DataStore value on read/update; return `null` when encrypted value cannot be decrypted. |
| DataStore sync queues | `ApiKeyManager` pending/claimed alias sets | None | No | Alias sets remain plaintext metadata and must not contain API key values. |
| DataStore vault bootstrap | `LocalVaultMetadataStore` / `vault_local_*` | `vault_local_verification`, `vault_local_wrapped_dek` are already vault verification ciphertext / wrapped DEK; `vault_local_salt` is non-secret | No | Do not wrap with `LocalDataEncryptionService`; validate as bootstrap crypto metadata and never log values. |
| SharedPreferences biometric bootstrap | `AndroidBiometricManager` / `biometric_prefs` | `${key}_data` is Android Keystore ciphertext and `${key}_iv` is IV metadata | No | Do not wrap with `LocalDataEncryptionService`; preserve biometric-gated encryption and never log values. |
| SharedPreferences download metadata | `DownloadSessionManager` / `model_download_prefs` | None | No | `download_session_id` remains plaintext non-sensitive UUID metadata. |
| Firestore user document | Vault metadata fields | `vaultVerification`, `wrappedDek` already encrypted/wrapped; `vaultSalt` is non-secret | Yes | Do not wrap with `LocalDataEncryptionService`; maintain existing vault semantics. |

## API Contracts & Interfaces
- `enum class PersistenceBoundary { ROOM, FIRESTORE, DATASTORE, ENCRYPTED_SHARED_PREFERENCES, WORKER, EMBEDDING_CONSUMER }`
- `enum class SensitiveRepairAction { NONE, REPAIRED, DEFERRED_LOCKED, REJECTED_INVALID_CIPHERTEXT, REJECTED_UNSUPPORTED_FIELD }`
- `data class SensitiveFieldRef(val owner: String, val field: String, val boundary: PersistenceBoundary, val collectionPath: String? = null)`
- `data class SensitiveRepairEvent(val ref: SensitiveFieldRef, val action: SensitiveRepairAction, val entityId: String, val sourceSide: String)`
- `data class SensitiveRepairResult(val value: String?, val action: SensitiveRepairAction, val event: SensitiveRepairEvent?)`
- `class SensitivePersistenceLockedException(message: String) : IllegalStateException(message)`
- `interface SensitiveFieldCatalog { fun auditedOwners(): Set<String>; fun sensitiveFields(owner: String): Set<String>; fun isSensitive(owner: String, field: String): Boolean; fun allRefs(): Set<SensitiveFieldRef> }`
- `class StaticSensitiveFieldCatalog : SensitiveFieldCatalog`
- `class SensitivePersistenceSelfHealer`
  - `fun repairString(ref: SensitiveFieldRef, entityId: String, value: String?): SensitiveRepairResult`
  - `fun requireEncrypted(ref: SensitiveFieldRef, entityId: String, value: String?): String?`
  - `fun decryptForRead(ref: SensitiveFieldRef, entityId: String, value: String?): String?`
- `object SensitiveEntityFieldEncryptors`
  - `fun encrypt(entity: ChatEntity, healer: SensitivePersistenceSelfHealer): ChatEntity`
  - `fun encrypt(entity: MessageEntity, healer: SensitivePersistenceSelfHealer): MessageEntity`
  - `fun encrypt(entity: ChatSummaryEntity, healer: SensitivePersistenceSelfHealer): ChatSummaryEntity`
  - `fun encrypt(entity: MemoriesEntity, healer: SensitivePersistenceSelfHealer): MemoriesEntity`
  - `fun encrypt(entity: MessageVisionAnalysisEntity, healer: SensitivePersistenceSelfHealer): MessageVisionAnalysisEntity`
  - `fun encrypt(entity: MessageFileEntity, healer: SensitivePersistenceSelfHealer): MessageFileEntity`
  - `fun encrypt(entity: WebSearchSourceEntity, healer: SensitivePersistenceSelfHealer): WebSearchSourceEntity`
  - `fun encrypt(entity: MessageTranscriptSegmentEntity, healer: SensitivePersistenceSelfHealer): MessageTranscriptSegmentEntity`
  - `fun encrypt(entity: ApiModelConfigurationEntity, healer: SensitivePersistenceSelfHealer): ApiModelConfigurationEntity`
  - `fun encrypt(entity: LocalModelConfigurationEntity, healer: SensitivePersistenceSelfHealer): LocalModelConfigurationEntity`
  - `fun encrypt(entity: TtsProviderEntity, healer: SensitivePersistenceSelfHealer): TtsProviderEntity`
  - `fun encrypt(entity: MediaProviderEntity, healer: SensitivePersistenceSelfHealer): MediaProviderEntity`
  - `fun encrypt(entity: ApiCredentialsEntity, healer: SensitivePersistenceSelfHealer): ApiCredentialsEntity`
  - `fun encrypt(entity: StudioAlbumEntity, healer: SensitivePersistenceSelfHealer): StudioAlbumEntity`
  - `fun encrypt(entity: StudioMediaEntity, healer: SensitivePersistenceSelfHealer): StudioMediaEntity`
  - `fun encrypt(entity: DocumentEntity, healer: SensitivePersistenceSelfHealer): DocumentEntity`
  - Matching `decrypt(...)` overloads for the same entity types.
- `class FirestoreSensitiveFieldRepairer`
  - `fun repairOutbound(collectionPath: String, entityId: String, data: Map<String, Any?>): Map<String, Any?>`
  - `fun repairInbound(collectionPath: String, entityId: String, data: Map<String, Any?>): Map<String, Any?>`
  - `fun sanitizedEvents(): List<SensitiveRepairEvent>`

## Config / Permissions Delta
None. This feature uses existing Room tables, DataStore files, EncryptedSharedPreferences, Firestore, Hilt, and encryption dependencies.

## Constitution Audit
This design adheres to `AGENTS.md` Clean Architecture dependency direction, `DATA_LAYER_RULES.md` schema lock and Room mapping rules, `CODE_STYLE_RULES.md` Kotlin/JUnit conventions, `UNIT_TESTING.md` JUnit 5/Fakes/Turbine rules, and `INFERENCE_RULES.md` on-device-first isolation.

## Subagent-Driven Execution

Downstream executors (TDD Red Sub-Agent, Implementation Sub-Agent, or a human following this plan) act as **coordinators first, implementers second**. For each **Implementation Task** below:

1. **Spawn** a child subagent scoped to that task alone (for example: author the failing tests for the task's named scenarios, or implement the production change the task describes).
2. **Review** the child subagent's output against this document as the contract — accept nothing that contradicts the File Structure, API Contracts & Interfaces, the task **Files** lists, or the task-to-scenario bindings — before advancing to the next task.
3. **Repeat** task by task in order. Do not fold multiple unrelated tasks into one child run unless a task explicitly instructs a bundled scope.

Scenario IDs are the **unit of work** each child subagent must satisfy for its task; cross-check every ID the task names under **Verifies scenarios** against `test_spec.md` before closing the task.

**Per-child review checklist (parent executor):**
- Paths touched match the task **Files** entries and the File Structure table; no edits outside listed paths.
- Every **Verifies scenarios** ID for that task shows the intended test state for the current phase (red where failure is expected; green where pass is expected), per `test_spec.md`.
- No scope drift: no unlisted files, no extra behaviors, no shortcuts that bypass this spec.
- Deliverables live in real source and test files aligned with this spec — no substitute that embeds runnable tests or implementation bodies here or otherwise diverges from the contract.

## Implementation Tasks

### Task 1: Catalog And Core Self-Heal Tests

**Files:**
- Create: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitiveFieldCatalogTest.kt`
- Create: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitivePersistenceSelfHealerTest.kt`

**Change:** Add failing tests for the exact audit matrix, including zero-sensitive persisted owners, plaintext repair, locked-vault deferral, invalid ciphertext rejection, idempotency, and sanitized event payloads.

**Verifies scenarios:** `S1, S2, E1, E2, E3, M1`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): add self-heal catalog tests
```

### Task 2: Catalog And Core Self-Heal Implementation

**Files:**
- Create: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/PersistenceSelfHealModels.kt`
- Create: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitiveFieldCatalog.kt`
- Create: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitivePersistenceSelfHealer.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/LocalDataEncryptionService.kt:3-19`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/CryptoAtRestEncryptionService.kt:8-31`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/DataModule.kt:70-493`

**Change:** Implement the declared self-heal model types, static catalog, repair/decrypt behavior, and DI bindings using the existing envelope prefix.

**Verifies scenarios:** `S1, S2, E1, E2, E3, M1`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): implement self-heal catalog
```

### Task 3: Room Entity Encryptor Tests

**Files:**
- Create: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitiveEntityFieldEncryptorsTest.kt`

**Change:** Add failing tests proving every Room entity in the audit matrix has all sensitive fields encrypted, non-sensitive metadata unchanged, and decrypt overloads return literal plaintext.

**Verifies scenarios:** `S3, S4, S5, S6, S7, M1`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): add entity encryptor tests
```

### Task 4: Room Entity Encryptor Implementation

**Files:**
- Create: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitiveEntityFieldEncryptors.kt`

**Change:** Implement `SensitiveEntityFieldEncryptors` overloads for every cataloged Room entity with no schema changes and no payload logging.

**Verifies scenarios:** `S3, S4, S5, S6, S7, M1`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): implement entity encryptors
```

### Task 5: Chat Persistence Boundary Tests

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/ChatRepositoryImplTest.kt:1-260`

**Change:** Add failing tests for plaintext repair on chat names, assistant/user message fields, generated media JSON, web search sources, transcript segments, and documents touched by `ChatRepositoryImpl`.

**Verifies scenarios:** `S8, S9, S10, S11`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): add chat repository repair tests
```

### Task 6: Chat Persistence Boundary Implementation

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ChatRepositoryImpl.kt:40-440`

**Change:** Replace private duplicate encrypt/decrypt helpers with `SensitiveEntityFieldEncryptors` and repair plaintext on read-before-append paths so Room is rewritten encrypted before sync.

**Verifies scenarios:** `S8, S9, S10, S11`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): repair chat persistence fields
```

### Task 7: Message Persistence Boundary Tests

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/MessageRepositoryImplSaveMessageTest.kt:1-180`

**Change:** Add failing tests for message save, message file, vision analysis, and chat summary encrypted persistence through `MessageRepositoryImpl`.

**Verifies scenarios:** `S12, S13, S14`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): add message repository repair tests
```

### Task 8: Message Persistence Boundary Implementation

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/MessageRepositoryImpl.kt:66-327`

**Change:** Route message, file, vision analysis, and chat summary writes/reads through `SensitiveEntityFieldEncryptors` and repair plaintext source rows when observed.

**Verifies scenarios:** `S12, S13, S14`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): repair message persistence fields
```

### Task 9: Memory And Embedding Consumer Tests

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/MemoriesRepositoryTest.kt:1-180`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/EmbeddingRegenerationWorkerTest.kt:1-210`

**Change:** Add failing tests for memory content repair, decrypted embedding input, and no plaintext writes from embedding consumers.

**Verifies scenarios:** `S15, S16`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): add memory embedding repair tests
```

### Task 10: Memory And Embedding Consumer Implementation

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/MemoriesRepositoryImpl.kt:20-116`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/EmbeddingRegenerationWorker.kt:36-192`

**Change:** Repair `MemoriesEntity.content` before embedding, decrypt only after repair, and ensure embedding metadata stores only hashes or vectors.

**Verifies scenarios:** `S15, S16`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): repair memory embedding inputs
```

### Task 11: Model Configuration And Credential Tests

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/ApiModelRepositoryImplTest.kt:1-490`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/LocalModelRepositoryImplTest.kt:1-160`

**Change:** Add failing tests for API credentials, API model configurations, local model configurations, and default/local model non-sensitive metadata.

**Verifies scenarios:** `S17, S18, S19`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): add model config repair tests
```

### Task 12: Model Configuration And Credential Implementation

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ApiModelRepositoryImpl.kt:38-283`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/LocalModelRepositoryImpl.kt:33-218`

**Change:** Route API credentials, API model configuration, and local model configuration fields through shared self-heal helpers while preserving plaintext metadata IDs.

**Verifies scenarios:** `S17, S18, S19`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): repair model config persistence
```

### Task 13: Provider And Studio Tests

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/TtsProviderRepositoryImplTest.kt:1-120`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/MediaProviderRepositoryImplTest.kt:1-120`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/StudioRepositoryImplTest.kt:1-280`

**Change:** Add failing tests for TTS provider, media provider, studio album, and studio media plaintext repair.

**Verifies scenarios:** `S20, S21, S22`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): add provider studio repair tests
```

### Task 14: Provider And Studio Implementation

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/TtsProviderRepositoryImpl.kt:24-80`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/MediaProviderRepositoryImpl.kt:24-70`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/StudioRepositoryImpl.kt:31-268`

**Change:** Route provider and studio writes/reads through shared self-heal helpers, including local-only `mediaUri` and `stagingRemoteUrl`.

**Verifies scenarios:** `S20, S21, S22`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): repair provider studio persistence
```

### Task 15: Artifact And Document Tests

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/artifact/ArtifactToolExecutorImplTest.kt:1-240`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/artifact/SearchArtifactToolExecutorTest.kt:1-90`

**Change:** Add failing tests for document JSON/title/author encryption during create, merge, export preference updates, and search.

**Verifies scenarios:** `S23`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): add document repair tests
```

### Task 16: Artifact And Document Implementation

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/artifact/ArtifactToolExecutor.kt:61-264`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/artifact/SearchArtifactToolExecutor.kt:25-60`

**Change:** Use document self-heal helpers for artifact document writes and reads, preserving encrypted Room rows after merge/search operations.

**Verifies scenarios:** `S23`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): repair document artifacts
```

### Task 17: DataStore And API Key Tests

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/SettingsRepositoryImplTest.kt:1-220`
- Create: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/PipelineStateRepositoryImplTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/ApiKeyManagerTest.kt:1-220`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/LocalVaultMetadataStoreTest.kt:1-120`
- Create: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/AndroidBiometricManagerTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadSessionManagerTest.kt:1-100`

**Change:** Add failing tests for encrypted DataStore custom prompt text, encrypted pipeline state JSON, API key self-heal, alias-only pending sync sets, vault/biometric bootstrap audit exclusions, and non-sensitive download session metadata.

**Verifies scenarios:** `S24, S25, S26, S35, E4`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): add datastore api key repair tests
```

### Task 18: DataStore And API Key Implementation

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/SettingsRepositoryImpl.kt:21-209`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/PipelineStateRepositoryImpl.kt:20-57`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/ApiKeyManager.kt:15-172`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/LocalVaultMetadataStore.kt:13-54`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/AndroidBiometricManager.kt:49-135`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadSessionManager.kt:13-48`

**Change:** Encrypt and self-heal custom prompts and pipeline JSON in DataStore, ensure API key values remain in encrypted prefs locally and encrypted blobs remotely, and codify audit exclusions for vault, biometric, and download metadata persistence.

**Verifies scenarios:** `S24, S25, S26, S35, E4`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): repair datastore api keys
```

### Task 19: Firestore Repairer Tests

**Files:**
- Create: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreSensitiveFieldRepairerTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/MessageTranscriptSegmentFirestoreCodecTest.kt:1-120`

**Change:** Add failing tests for inbound/outbound Firestore map repair, transcript codec encrypted text, plaintext source cleanup events, and invalid API key field rejection.

**Verifies scenarios:** `S27, S28, S29, E5`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): add firestore repairer tests
```

### Task 20: Firestore Repairer Implementation

**Files:**
- Create: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreSensitiveFieldRepairer.kt`

**Change:** Implement collection/field based Firestore map repair using `SensitiveFieldCatalog`, with sanitized events and explicit rejection for unsupported plaintext credential payloads.

**Verifies scenarios:** `S27, S28, S29, E5`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): implement firestore repairer
```

### Task 21: Firestore Data Source Tests

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSourceTest.kt:1-260`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/SyncReconciliationTest.kt:1-140`

**Change:** Add failing integration-style tests for representative adapters proving plaintext local rows upload as encrypted, plaintext remote docs are repaired to Firestore and Room, and locked vault defers repair.

**Verifies scenarios:** `S30, S31, S32, E6`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): add firestore datasource repair tests
```

### Task 22: Firestore Data Source Implementation

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt:119-1895`

**Change:** Inject `FirestoreSensitiveFieldRepairer`, replace `encryptedForFirestore` fail-fast behavior with repair-on-unlocked behavior, repair inbound docs before `fromFirestore`, repair local rows before upload, and write repaired Firestore values back with `SetOptions.merge`.

**Verifies scenarios:** `S30, S31, S32, E6`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): repair firestore sync boundaries
```

### Task 23: Worker URL Repair Tests

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/MessageFileCloudSyncWorkerTest.kt:1-130`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/MediaCloudSyncWorkerTest.kt:1-130`

**Change:** Add failing tests that staged and uploaded remote URLs are encrypted in Room and never logged as payloads.

**Verifies scenarios:** `S33, S34`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): add worker url repair tests
```

### Task 24: Worker URL Repair Implementation

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/MessageFileCloudSyncWorker.kt:29-105`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/MediaCloudSyncWorker.kt:29-100`

**Change:** Route remote URL and staging URL writes through self-heal helpers so worker-produced local state cannot leave plaintext behind.

**Verifies scenarios:** `S33, S34`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): repair worker url persistence
```

### Task 25: Exhaustive Regression And Validation

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitiveFieldCatalogTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSourceTest.kt:1-260`

**Change:** Add the final mutation-defense assertions that all cataloged sensitive fields are exercised by at least one test and no warning log contains `"secret text"`, `"PCENC:"`, `"sk-live"`, or `"ciphertext"`.

**Verifies scenarios:** `M1, E3`

**Commit message:**

```
feat(FEAT-encrypted-persistence-self-heal): lock self-heal coverage matrix
```

## Validation Commands
- `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew :core:data:testDebugUnitTest --no-daemon --stacktrace`
- `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew :core:data:assemble --no-daemon --stacktrace`
- `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew :app:kspDebugKotlin --no-daemon --stacktrace`
- `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew testDebugUnitTest --continue --stacktrace --no-daemon`
- `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew ktlintCheck --no-daemon --stacktrace`

# Spec: Vault-backed PCENC string encryption hardening

## Objective
Harden Pocket Crew’s at-rest string encryption before release by replacing prefix-only detection with a strict `PCENC:v1:aes-gcm:` envelope parser and explicit error taxonomy, threading AES-GCM additional authenticated data (AAD) `owner|field|entityId` through every encrypt/decrypt path so ciphertext cannot be swapped across rows or fields, and eliminating test-only prefix drift (`ENC:v1:`) in favor of the production envelope constant. HKDF key-purpose separation and envelope `v2` are explicitly out of scope; the app remains unreleased with a hard cutover (no dual-read migration).

## File Structure

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/EncryptionErrors.kt` | Typed encryption failures: malformed envelope, decrypt/tag failure, vault locked. |
| Create | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/AadContext.kt` | Canonical UTF-8 AAD construction `owner\|field\|entityId`. |
| Create | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/TestEnvelope.kt` | Single compile-time anchor re-exporting `CryptoAtRestEncryptionService.ENVELOPE_PREFIX` for tests. |
| Modify | `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/security/CryptoManagerPort.kt:51-74` | Add optional `aad: ByteArray?` to `encrypt`, `encryptBytes`, `decrypt`, and `decryptBytes`; callers omit AAD with `null` where not used. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/CryptoManagerImpl.kt:101-152` | After `cipher.init`, invoke `cipher.updateAAD(aad)` when `aad` is non-null for encrypt and decrypt byte paths. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/CryptoAtRestEncryptionService.kt:1-39` | Strict `parseEnvelope`, size-capped base64 decode, `encryptString`/`decryptString` with required `aad: ByteArray`, `isEncrypted` aligned with `EnvelopeParse.Ok`, `EncryptionLockedException` on locked vault. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/LocalDataEncryptionService.kt:1-25` | Thread `aad: ByteArray` through `encryptString`, `decryptString`, and default `encryptNullable`/`decryptNullable`; update `NoOp` accordingly. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/PersistenceSelfHealModels.kt:13-19` | Add `SensitiveRepairAction.REJECTED_MALFORMED_ENVELOPE` distinct from `REJECTED_INVALID_CIPHERTEXT`. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitivePersistenceSelfHealer.kt:52-88` | Catch `MalformedEnvelopeException` vs `DecryptFailedException` vs `EncryptionLockedException`; emit matching `SensitiveRepairAction` and `LoggingPort` metadata without payload leakage. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitiveEntityFieldEncryptors.kt` | Build `AadContext(owner=<Kotlin simple class name>, field=<catalog literal>, entityId=<row id or singleton key>)` for every sensitive field encrypt/decrypt path. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreExtensions.kt:7-16` | Thread `aad: ByteArray` into `String.encrypt` / `Blob.decrypt` delegating to `CryptoManagerPort` with matching AAD semantics for `ApiCredentialsEntity.encryptedApiKey`. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt` | Pass `(collectionPath, doc.id, field camelCase)` into encryptors/repairer so Firestore-bound AAD matches Room-side literals. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreSensitiveFieldRepairer.kt:22-161` | Construct AAD using catalog owner, canonical camelCase field name, and `doc.id` for every repaired field. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/SettingsRepositoryImpl.kt:21-209` | Supply `AadContext("SettingsRepositoryImpl", "settings_custom_prompt_text", "singleton").toBytes()` for DataStore custom prompt encrypt/decrypt. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/PipelineStateRepositoryImpl.kt:20-57` | Supply `AadContext("PipelineStateRepositoryImpl", "pipeline_state", chatId).toBytes()` for pipeline JSON. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ActiveModelProviderImpl.kt` | Thread entity-scoped AAD for decrypt paths wrapping model entities. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/DefaultModelRepositoryImpl.kt` | Thread entity-scoped AAD for decrypt paths wrapping default model entities. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/artifact/ArtifactToolExecutor.kt:61-264` | Use `AadContext("DocumentEntity", <field literal>, documentId).toBytes()` for document JSON/title/author paths. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/artifact/SearchArtifactToolExecutor.kt:25-60` | Decrypt document search payloads with the same `DocumentEntity` AAD as `ArtifactToolExecutor`. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/MediaCloudSyncWorker.kt:29-100` | Thread `StudioMediaEntity` field AAD for staging and remote URL encrypt/decrypt. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/MessageFileCloudSyncWorker.kt:29-105` | Thread `MessageFileEntity` field AAD for staging and remote URL encrypt/decrypt. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/FakeLocalDataEncryptionService.kt:8-14` | Replace `ENC:v1:` with `CryptoAtRestEncryptionService.ENVELOPE_PREFIX` and thread recorded `aad` for assertions. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/AtRestEncryptionServiceTest.kt` | Cover envelope parse, AAD round-trip, locked vault typed exception, and inner fake crypto alignment with `ENVELOPE_PREFIX`. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitivePersistenceSelfHealerTest.kt` | Assert `REJECTED_MALFORMED_ENVELOPE` vs `REJECTED_INVALID_CIPHERTEXT` vs locked vault behavior. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitiveEntityFieldEncryptorsTest.kt` | Add AAD swap mismatch decrypt failure scenario. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreSensitiveFieldRepairerTest.kt:57-95` | Assert distinct AAD inputs per synthetic collection/doc/field. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/MemoriesRepositoryTest.kt` | Replace `ENC:v1:` literals with `ENVELOPE_PREFIX` or `TestEnvelope` helper expectations. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/ActiveModelProviderImplTest.kt` | Replace `ENC:v1:` expectations with production prefix constant. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/DefaultModelRepositoryImplTest.kt` | Replace `ENC:v1:` expectations with production prefix constant. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSourceTest.kt` | Replace `ENC:v1:` expectations and local fake encryption with production prefix constant. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/MediaCloudSyncWorkerTest.kt` | Align encrypted URL fixtures with `ENVELOPE_PREFIX`. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/MessageFileCloudSyncWorkerTest.kt` | Align encrypted URL fixtures with `ENVELOPE_PREFIX`. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/ArtifactSyncIntegrationTest.kt` | Replace `ENC:v1:` document assertions with `ENVELOPE_PREFIX`. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/ChatRepositoryImplTest.kt` | Replace `ENC:v1:` expectations with `ENVELOPE_PREFIX`. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/MessageRepositoryImplSaveMessageTest.kt` | Replace `ENC:v1:` expectations with `ENVELOPE_PREFIX`. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/ApiModelRepositoryImplTest.kt` | Replace `ENC:v1:` expectations with `ENVELOPE_PREFIX`. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/StudioRepositoryImplTest.kt` | Replace `ENC:v1:` expectations with `ENVELOPE_PREFIX`. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/StudioRepositorySyncTest.kt` | Replace `ENC:v1:` expectations with `ENVELOPE_PREFIX`. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/MediaProviderRepositoryImplTest.kt` | Replace `ENC:v1:` expectations with `ENVELOPE_PREFIX`. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/LocalModelRepositoryImplTest.kt` | Replace `ENC:v1:` expectations with `ENVELOPE_PREFIX`. |
| Modify | `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/security/DetectVaultReconciliationStateUseCaseTest.kt` | Update embedded `FakeCrypto` to satisfy extended `CryptoManagerPort` with `aad` parameters defaulting to ignored behavior. |
| Modify | `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/security/ChangePasswordUseCaseTest.kt` | Update embedded `FakeCrypto` for new `CryptoManagerPort` signatures. |
| Modify | `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/security/UnlockVaultUseCaseTest.kt` | Update embedded `FakeCryptoManager` for new `CryptoManagerPort` signatures. |
| Modify | `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/security/AdoptCloudVaultUseCaseTest.kt` | Update embedded `FakeCrypto` for new `CryptoManagerPort` signatures. |
| Modify | `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/security/CreateVaultUseCaseTest.kt` | Update embedded `FakeCryptoManager` for new `CryptoManagerPort` signatures. |
| Modify | `app/src/test/kotlin/com/browntowndev/pocketcrew/presentation/MainViewModelTest.kt` | Update `CryptoManagerPort` mock/fake for new method signatures if compilation requires explicit stubs. |
| Modify | `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModelTest.kt` | Update `CryptoManagerPort` mock for new method signatures if required. |
| Modify | `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModelEventTest.kt` | Update `CryptoManagerPort` mock for new method signatures if required. |
| Modify | `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsGeneralDelegateTest.kt` | Update `CryptoManagerPort` mock for new method signatures if required. |
| Modify | `core/ui/src/main/kotlin/com/browntowndev/pocketcrew/core/ui/util/StudioMediaFetcher.kt` | Pass `null` AAD or media-specific AAD consistently with `StudioMediaEntity` decrypt rules when calling `CryptoManagerPort` byte decrypt APIs. |

## Data Models & Schemas
No Room schema changes. No new persistence tables.

- `enum class MalformedEnvelopeReason { BAD_PREFIX, INVALID_BASE64, PAYLOAD_TOO_SHORT, PAYLOAD_TOO_LARGE }`
- `sealed class EnvelopeParse` with `data class Ok(val iv: ByteArray, val ciphertext: ByteArray) : EnvelopeParse`, `data class Malformed(val reason: MalformedEnvelopeReason) : EnvelopeParse`, `data object NotEnveloped : EnvelopeParse`
- `class MalformedEnvelopeException(val reason: MalformedEnvelopeReason) : Exception("Malformed envelope: ${reason.name}")`
- `class DecryptFailedException(message: String, cause: Throwable?) : Exception(message, cause)`
- `class EncryptionLockedException(message: String) : IllegalStateException(message)`
- `data class AadContext(val owner: String, val field: String, val entityId: String) { fun toBytes(): ByteArray }` using UTF-8 bytes of `"$owner|$field|$entityId"`
- `enum class SensitiveRepairAction` gains `REJECTED_MALFORMED_ENVELOPE` inserted after `REJECTED_INVALID_CIPHERTEXT` definition order is irrelevant to persistence; tests assert explicit enum names.

## API Contracts & Interfaces

**Constants**

- `CryptoAtRestEncryptionService.Companion.ENVELOPE_PREFIX` remains exactly `"PCENC:v1:aes-gcm:"`.
- `CryptoAtRestEncryptionService.Companion.ENVELOPE_MAX_DECODED_BYTES` is `16777216` (16 MiB inclusive cap on decoded envelope payload bytes after Base64 decode).
- Minimum decoded payload length for a valid envelope is `28` bytes (`12` byte IV + at least `16` byte GCM ciphertext including tag).

**`CryptoManagerPort` (`core/domain/.../CryptoManagerPort.kt`)**

- `fun encrypt(plaintext: String, aad: ByteArray? = null): Result<ByteArray>`
- `fun encryptBytes(data: ByteArray, aad: ByteArray? = null): Result<ByteArray>`
- `fun decrypt(ciphertext: ByteArray, aad: ByteArray? = null): Result<String>`
- `fun decryptBytes(ciphertext: ByteArray, aad: ByteArray? = null): Result<ByteArray>`

All other `CryptoManagerPort` members unchanged.

**`LocalDataEncryptionService` (`core/data/.../LocalDataEncryptionService.kt`)**

- `fun encryptString(plaintext: String, aad: ByteArray): String`
- `fun decryptString(stored: String, aad: ByteArray): String`
- `fun isEncrypted(stored: String): Boolean`
- `fun encryptNullable(plaintext: String?, aad: ByteArray): String?`
- `fun decryptNullable(stored: String?, aad: ByteArray): String?`

**`CryptoAtRestEncryptionService`**

- `fun parseEnvelope(stored: String): EnvelopeParse`
- `override fun encryptString(plaintext: String, aad: ByteArray): String`
- `override fun decryptString(stored: String, aad: ByteArray): String`
- `override fun isEncrypted(stored: String): Boolean` returns `true` if and only if `parseEnvelope(stored)` is `EnvelopeParse.Ok`.

**`FirestoreExtensions.kt`**

- `internal fun String.encrypt(cryptoManager: CryptoManagerPort, aad: ByteArray): Blob`
- `internal fun Blob.decrypt(cryptoManager: CryptoManagerPort, aad: ByteArray): String?`

**`FakeLocalDataEncryptionService` (test-only)**

- Records the last `aad` bytes passed to `encryptString` and `decryptString` into public `lastEncryptAad: ByteArray?` and `lastDecryptAad: ByteArray?` fields for assertions.
- `encryptString` returns the concatenation of `CryptoAtRestEncryptionService.ENVELOPE_PREFIX` followed by the raw `plaintext` argument (no Base64), and `decryptString` strips that prefix and returns the suffix so `isEncrypted` matches prefix-only semantics compatible with this fake.

## Sequencing

**Theme 1 (Tasks 1–9):** Implement envelope parsing, typed errors, self-healer repair classification, `FakeLocalDataEncryptionService` alignment, and all `ENC:v1:` test literal sweeps **without** changing `CryptoManagerPort` and **without** adding `aad` parameters to `LocalDataEncryptionService` or `CryptoAtRestEncryptionService` public methods. Locked-vault scenarios `E5` and `E6` still apply to the Theme 1 single-argument `encryptString` / `decryptString` overloads.

**Theme 2 (Tasks 10–32):** Apply **API Contracts & Interfaces** in full, including optional `aad` on `CryptoManagerPort`, required `aad` on `LocalDataEncryptionService`, and all repository/Firestore/worker threading; migrate `E5` / `E6` call sites to the two-argument overloads listed in the API section.

## Config / Permissions Delta
None.

## Constitution Audit
This design adheres to `AGENTS.md` clean architecture by keeping `CryptoManagerPort` in `:core:domain` as pure Kotlin, confining Android `Cipher` work to `:core:data`, avoiding `NavController` in composables, and using JUnit 5–style unit tests in matching `src/test/kotlin` trees per `UNIT_TESTING.md`.

## Subagent-Driven Execution

Downstream executors (TDD Red Sub-Agent, Implementation Sub-Agent, or a human following this plan) act as **coordinators first, implementers second**. For each **Implementation Task** below:

1. **Spawn** a child subagent scoped to that task alone (for example: author the failing tests for the task’s named scenarios, or implement the production change the task describes).
2. **Review** the child subagent’s output against this document as the contract — accept nothing that contradicts the File Structure, API Contracts & Interfaces, the task **Files** lists, or the task-to-scenario bindings — before advancing to the next task.
3. **Repeat** task by task in order. Do not fold multiple unrelated tasks into one child run unless a task explicitly instructs a bundled scope.

Scenario IDs are the **unit of work** each child subagent must satisfy for its task; cross-check every ID the task names under **Verifies scenarios** against `test_spec.md` before closing the task.

**Per-child review checklist (parent executor):**

- Paths touched match the task **Files** entries and the File Structure table; no edits outside listed paths.
- Every **Verifies scenarios** ID for that task shows the intended test state for the current phase (red where failure is expected; green where pass is expected), per `test_spec.md`.
- No scope drift: no unlisted files, no extra behaviors, no shortcuts that bypass this spec.
- Deliverables live in real source and test files aligned with this spec — no substitute that embeds runnable tests or implementation bodies here or otherwise diverges from the contract.

---

## Implementation Tasks

### Task 1: Failing envelope parser unit tests

**Files:**

- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/AtRestEncryptionServiceTest.kt`

**Change:** Add JVM unit tests that call `CryptoAtRestEncryptionService.parseEnvelope` for inputs covering `S1`–`S3` and `E1`–`E4` in `test_spec.md`. Tests do not compile until Task 2 adds `parseEnvelope` and sealed results; that non-compiling state is the acceptable RED gate.

**Verifies scenarios:** `S1, S2, S3, E1, E2, E3, E4`

**Commit message:**

```
test(FEAT-calm-iverson): envelope parse scenarios for at-rest crypto
```

### Task 2: Typed errors and envelope parser implementation

**Files:**

- Create: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/EncryptionErrors.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/CryptoAtRestEncryptionService.kt:1-39`

**Change:** Implement `MalformedEnvelopeException`, `DecryptFailedException`, `EncryptionLockedException`, `MalformedEnvelopeReason`, `EnvelopeParse`, and `parseEnvelope` with strict `java.util.Base64` decoder, decoded length bounds, and `isEncrypted` derived from `EnvelopeParse.Ok`. Replace bare `IllegalStateException("Encryption session locked")` throws with `EncryptionLockedException` carrying the exact message `"Encryption session locked"`. Extend `AtRestEncryptionServiceTest` (same task or immediately following commit) with green tests for `E5` and `E6` using Theme 1 single-argument `encryptString` / `decryptString` until Task 14 migrates signatures.

**Verifies scenarios:** `S1, S2, S3, E1, E2, E3, E4, E5, E6`

**Commit message:**

```
feat(FEAT-calm-iverson): strict PCENC envelope parse and typed crypto errors
```

### Task 3: Failing self-healer classification tests

**Files:**

- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitivePersistenceSelfHealerTest.kt`

**Change:** Add tests for `decryptForRead` outcomes in `E7`, `E8`, and `S4` before production enum mapping exists; expect red until Task 4 completes.

**Verifies scenarios:** `S4, E7, E8`

**Commit message:**

```
test(FEAT-calm-iverson): self-healer envelope failure classification
```

### Task 4: Self-healer repair action mapping

**Files:**

- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/PersistenceSelfHealModels.kt:13-19`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitivePersistenceSelfHealer.kt:52-88`

**Change:** Add `SensitiveRepairAction.REJECTED_MALFORMED_ENVELOPE`. Map `MalformedEnvelopeException` to that action, `DecryptFailedException` to `REJECTED_INVALID_CIPHERTEXT`, and `EncryptionLockedException` to the existing locked semantics (`SensitivePersistenceLockedException` wrapping where applicable). Preserve non-encrypted plaintext repair flow unchanged.

**Verifies scenarios:** `S4, E7, E8`

**Commit message:**

```
feat(FEAT-calm-iverson): distinguish malformed envelope vs decrypt failures in self-heal
```

### Task 5: Test-only envelope constant anchor

**Files:**

- Create: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/TestEnvelope.kt`

**Change:** Expose `TEST_ENVELOPE_PREFIX` as a `const val` alias of `CryptoAtRestEncryptionService.ENVELOPE_PREFIX` for repository tests that cannot depend on fake internals alone.

**Verifies scenarios:** `S5`

**Commit message:**

```
test(FEAT-calm-iverson): shared envelope prefix constant for data layer tests
```

### Task 6: FakeLocalDataEncryptionService prefix and AAD recorder

**Files:**

- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/FakeLocalDataEncryptionService.kt:8-14`

**Change:** Remove `"ENC:v1:"`. Implement `encryptString`/`decryptString`/`isEncrypted` using `CryptoAtRestEncryptionService.ENVELOPE_PREFIX` and record last AAD bytes for downstream threading tests.

**Verifies scenarios:** `S5`

**Commit message:**

```
test(FEAT-calm-iverson): align fake local encryption with production envelope prefix
```

### Task 7: Repository and Firestore test literal sweep (batch A)

**Files:**

- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/MemoriesRepositoryTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/ActiveModelProviderImplTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/DefaultModelRepositoryImplTest.kt`

**Change:** Replace every `ENC:v1:` literal and bespoke fake prefix with `CryptoAtRestEncryptionService.ENVELOPE_PREFIX` or `TEST_ENVELOPE_PREFIX` so drift fails compilation when the production constant changes.

**Verifies scenarios:** `S5`

**Commit message:**

```
test(FEAT-calm-iverson): normalize envelope literals in model repository tests
```

### Task 8: Repository and Firestore test literal sweep (batch B)

**Files:**

- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSourceTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/ChatRepositoryImplTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/MessageRepositoryImplSaveMessageTest.kt`

**Change:** Same literal replacement and fake encryption alignment rules as Task 7.

**Verifies scenarios:** `S5`

**Commit message:**

```
test(FEAT-calm-iverson): normalize envelope literals in Firestore and chat tests
```

### Task 9: Worker and integration test literal sweep

**Files:**

- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/MediaCloudSyncWorkerTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/MessageFileCloudSyncWorkerTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/ArtifactSyncIntegrationTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/ApiModelRepositoryImplTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/StudioRepositoryImplTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/StudioRepositorySyncTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/MediaProviderRepositoryImplTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/LocalModelRepositoryImplTest.kt`

**Change:** Same literal replacement and deterministic ciphertext rules as Task 7.

**Verifies scenarios:** `S5`

**Commit message:**

```
test(FEAT-calm-iverson): normalize envelope literals in workers and studio tests
```

### Task 10: Domain port AAD parameters

**Files:**

- Modify: `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/security/CryptoManagerPort.kt:51-74`

**Change:** Add nullable `aad` parameters exactly as declared in **API Contracts & Interfaces** without altering method names.

**Verifies scenarios:** `T1`

**Commit message:**

```
feat(FEAT-calm-iverson): optional AAD on CryptoManagerPort encrypt/decrypt
```

### Task 11: CryptoManagerImpl AAD wiring tests

**Files:**

- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/AtRestEncryptionServiceTest.kt` (or create `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/CryptoManagerImplAadTest.kt` if isolation is cleaner)

**Change:** Add JVM tests that prove `Cipher.updateAAD` is honored for non-null `aad` on round-trip (`T1`, `T2`) before asserting green on production wiring.

**Verifies scenarios:** `T1, T2`

**Commit message:**

```
test(FEAT-calm-iverson): CryptoManagerImpl AAD round-trip and mismatch failure
```

### Task 12: CryptoManagerImpl production AAD

**Files:**

- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/CryptoManagerImpl.kt:101-152`

**Change:** Invoke `updateAAD` after `init` when `aad != null` for both encrypt and decrypt paths; propagate failures as `Result.failure` without swallowing `AEADBadTagException`.

**Verifies scenarios:** `T1, T2`

**Commit message:**

```
feat(FEAT-calm-iverson): apply GCM AAD in CryptoManagerImpl
```

### Task 13: AadContext model

**Files:**

- Create: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/AadContext.kt`

**Change:** Implement `AadContext` with `toBytes()` UTF-8 encoding exactly as specified.

**Verifies scenarios:** `T3`

**Commit message:**

```
feat(FEAT-calm-iverson): canonical AadContext for at-rest binding
```

### Task 14: LocalDataEncryptionService AAD threading signature

**Files:**

- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/LocalDataEncryptionService.kt:1-25`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/CryptoAtRestEncryptionService.kt`

**Change:** Thread `aad: ByteArray` through encrypt/decrypt/nullable helpers; pass `aad` into `CryptoManagerPort.encrypt` / `decrypt` for envelope payload bytes. Update any `encryptString`-while-locked unit test to call the two-argument `encryptString(plaintext, aad)` so scenario `E5` in `test_spec.md` applies.

**Verifies scenarios:** `T1`

**Commit message:**

```
feat(FEAT-calm-iverson): require AAD bytes on LocalDataEncryptionService
```

### Task 15: Domain fake crypto compile fixes

**Files:**

- Modify: `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/security/DetectVaultReconciliationStateUseCaseTest.kt`
- Modify: `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/security/ChangePasswordUseCaseTest.kt`
- Modify: `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/security/UnlockVaultUseCaseTest.kt`
- Modify: `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/security/AdoptCloudVaultUseCaseTest.kt`
- Modify: `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/security/CreateVaultUseCaseTest.kt`

**Change:** Update nested `CryptoManagerPort` fakes to override the new `aad`-aware methods; ignore `aad` where those tests exercise PBKDF or KEK flows unrelated to row binding.

**Verifies scenarios:** `T1`

**Commit message:**

```
test(FEAT-calm-iverson): update domain security fakes for AAD-aware crypto port
```

### Task 16: SensitiveEntityFieldEncryptors AAD mismatch test

**Files:**

- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitiveEntityFieldEncryptorsTest.kt`

**Change:** Add `T4` scenario coverage using real `CryptoManagerImpl` with cached DEK or existing test harness patterns already present in the file.

**Verifies scenarios:** `T4`

**Commit message:**

```
test(FEAT-calm-iverson): entity encryptor rejects swapped entityId AAD
```

### Task 17: SensitiveEntityFieldEncryptors AAD construction

**Files:**

- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitiveEntityFieldEncryptors.kt`

**Change:** For every `encrypt`/`decrypt` helper pair, build `AadContext` with `owner` equal to the entity class simple name, `field` equal to the Kotlin property name string used in `SensitiveFieldCatalog`, and `entityId` equal to the entity primary key string or documented singleton sentinel `"singleton"` where applicable.

**Verifies scenarios:** `T3, T4`

**Commit message:**

```
feat(FEAT-calm-iverson): bind Room encryptors with per-field AAD
```

### Task 18: Settings and pipeline repository AAD

**Files:**

- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/SettingsRepositoryImpl.kt:21-209`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/PipelineStateRepositoryImpl.kt:20-57`

**Change:** Pass the `AadContext` byte arrays defined in the source plan: `("SettingsRepositoryImpl","settings_custom_prompt_text","singleton")` and `("PipelineStateRepositoryImpl","pipeline_state", chatId)` into `LocalDataEncryptionService` calls.

**Verifies scenarios:** `T5, T6`

**Commit message:**

```
feat(FEAT-calm-iverson): AAD for settings and pipeline DataStore ciphertext
```

### Task 19: Active and default model provider AAD

**Files:**

- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ActiveModelProviderImpl.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/DefaultModelRepositoryImpl.kt`

**Change:** Thread decrypt `aad` bytes consistent with the entity types being read so `FakeLocalDataEncryptionService.lastDecryptAad` assertions can distinguish owners in tests updated alongside.

**Verifies scenarios:** `T6`

**Commit message:**

```
feat(FEAT-calm-iverson): AAD threading for active and default model providers
```

### Task 20: Artifact executors AAD

**Files:**

- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/artifact/ArtifactToolExecutor.kt:61-264`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/artifact/SearchArtifactToolExecutor.kt:25-60`

**Change:** Use `DocumentEntity` owner string with literal field names `json`, `title`, or `author` matching `SensitiveEntityFieldEncryptors` and `documentId` as `entityId`.

**Verifies scenarios:** `T6`

**Commit message:**

```
feat(FEAT-calm-iverson): document AAD for artifact tools and search
```

### Task 21: Media and message file worker AAD

**Files:**

- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/MediaCloudSyncWorker.kt:29-100`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/MessageFileCloudSyncWorker.kt:29-105`

**Change:** Supply `StudioMediaEntity` and `MessageFileEntity` AAD contexts for remote and staging URL fields exactly matching repository encryptor field literals.

**Verifies scenarios:** `T6`

**Commit message:**

```
feat(FEAT-calm-iverson): AAD for media and message file sync workers
```

### Task 22: Firestore extensions BLOB AAD

**Files:**

- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreExtensions.kt:7-16`

**Change:** Thread `aad` into `CryptoManagerPort.encrypt` / `decrypt` for API credential blobs using `AadContext("ApiCredentialsEntity","encryptedApiKey", credentialsId).toBytes()` at call sites.

**Verifies scenarios:** `T6`

**Commit message:**

```
feat(FEAT-calm-iverson): AAD parameters for Firestore credential blob crypto
```

### Task 23: FirestoreSensitiveFieldRepairer tests

**Files:**

- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreSensitiveFieldRepairerTest.kt:57-95`

**Change:** Extend expectations so outbound repairs for distinct `(collectionPath, docId, field)` tuples produce distinct recorded AAD byte arrays on the fake encryption service (`T7`).

**Verifies scenarios:** `T7`

**Commit message:**

```
test(FEAT-calm-iverson): Firestore repairer AAD distinct per doc and field
```

### Task 24: FirestoreSensitiveFieldRepairer production AAD

**Files:**

- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreSensitiveFieldRepairer.kt:22-161`

**Change:** Construct `AadContext` for each repaired field using catalog owner, canonical camelCase field name aligned with `SensitiveEntityFieldEncryptors`, and Firestore document id.

**Verifies scenarios:** `T7`

**Commit message:**

```
feat(FEAT-calm-iverson): AAD assembly in Firestore sensitive field repairer
```

### Task 25: FirestoreDataSource AAD threading

**Files:**

- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt`

**Change:** Route `doc.id`, collection path, and field names into encryptors and `FirestoreSensitiveFieldRepairer` so inbound and outbound encrypted fields never call `LocalDataEncryptionService` without `aad`.

**Verifies scenarios:** `T6, T7`

**Commit message:**

```
feat(FEAT-calm-iverson): thread Firestore doc identity into encryption AAD
```

### Task 26: StudioMediaFetcher compile fix

**Files:**

- Modify: `core/ui/src/main/kotlin/com/browntowndev/pocketcrew/core/ui/util/StudioMediaFetcher.kt`

**Change:** Update `CryptoManagerPort` decrypt calls to pass `null` AAD or the same `StudioMediaEntity` AAD bytes used in `:core:data` decrypt paths — pick exactly one strategy consistent with Task 17’s field binding and document the choice in the task commit body.

**Verifies scenarios:** `T6`

**Commit message:**

```
fix(FEAT-calm-iverson): align StudioMediaFetcher with AAD-aware crypto port
```

### Task 27: App and settings test compile fixes

**Files:**

- Modify: `app/src/test/kotlin/com/browntowndev/pocketcrew/presentation/MainViewModelTest.kt`
- Modify: `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModelTest.kt`
- Modify: `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModelEventTest.kt`
- Modify: `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsGeneralDelegateTest.kt`

**Change:** Update MockK stubs or manual fakes so `:app` and `:feature:settings` compile against the extended `CryptoManagerPort`.

**Verifies scenarios:** `T1`

**Commit message:**

```
test(FEAT-calm-iverson): update app and settings tests for AAD-aware crypto port
```

### Task 28: Envelope semantic version comment

**Files:**

- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/CryptoAtRestEncryptionService.kt`

**Change:** Add a KDoc comment on `CryptoAtRestEncryptionService` whose text contains the exact sentence `No envelope version bump because the app is unreleased and legacy ciphertext is intentionally unsupported.` describing that the `PCENC:v1:aes-gcm:` envelope gained AAD binding without bumping the version.

**Verifies scenarios:** `S6`

**Commit message:**

```
docs(FEAT-calm-iverson): note PCENC v1 envelope gained AAD without version bump
```

### Task 29: SensitivePersistenceSelfHealer entityId forwarding audit test

**Files:**

- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitivePersistenceSelfHealerTest.kt`

**Change:** Add coverage that `repairString`/`decryptForRead` pass the supplied `entityId` through to `LocalDataEncryptionService` for encrypted values (`T8`).

**Verifies scenarios:** `T8`

**Commit message:**

```
test(FEAT-calm-iverson): assert entityId reaches encryption service on repair
```

### Task 30: End-to-end self-heal decrypt path with AAD

**Files:**

- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/SensitivePersistenceSelfHealerTest.kt`

**Change:** Add a green-path decrypt using matching `aad` bytes on a well-formed envelope (`T8` happy portion).

**Verifies scenarios:** `T8`

**Commit message:**

```
test(FEAT-calm-iverson): self-healer decrypt uses matched AAD bytes
```

### Task 31: Mutation defense scenario M1

**Files:**

- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/AtRestEncryptionServiceTest.kt` (or `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/CryptoManagerImplAadTest.kt` if that file already hosts Tasks 11–12)

**Change:** Encode scenario `M1` from `test_spec.md` using `CryptoManagerImpl` with a cached DEK so ciphertexts differ when only the AAD bytes change.

**Verifies scenarios:** `M1`

**Commit message:**

```
test(FEAT-calm-iverson): mutation defense for ignored AAD binding
```

### Task 32: CI validation sweep

**Files:**

- None (commands only).

**Change:** Run `./gradlew :core:domain:assemble`, `./gradlew :core:data:assemble`, `./gradlew :app:kspDebugKotlin`, `./gradlew test`, and `./gradlew ktlintCheck` from repo root; fix any residual compile or style issues introduced by prior tasks.

**Verifies scenarios:** `S1, S2, S3, S4, S5, S6, E1, E2, E3, E4, E5, E6, E7, E8, T1, T2, T3, T4, T5, T6, T7, T8, M1`

**Commit message:**

```
chore(FEAT-calm-iverson): green CI for encryption hardening sweep
```

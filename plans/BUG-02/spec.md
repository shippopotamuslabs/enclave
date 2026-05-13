# Spec: BUG-02 — Firestore Sync Fails on Plaintext-Field Records

## Objective

`encryptedForFirestore()` currently asserts that a field has the `PCENC:v1:aes-gcm:` prefix and throws `IllegalStateException` if not. SQL soft-delete queries (`softDeleteByMessageId`, `softDeleteByChatId`, `softDeleteAllActiveForMessage`, `deleteDocument`) set `pending_sync = 1` on records that may contain plaintext fields (data that was synced **from** Firestore before encryption was introduced, stored in Room without the prefix). Once those records are marked pending, sync fails and retries indefinitely. The fix changes `encryptedForFirestore()` to encrypt-on-the-fly when the data lacks the prefix, so pre-existing plaintext records are transparently secured before upload.

## File Structure

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt:169-174` | Replace `check(isEncrypted)` assertion with encrypt-if-needed |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSourceTest.kt` | Replace the "fail closed" test with tests for correct new behavior; add plaintext-field and double-encryption-guard scenarios |

## Data Models & Schemas

None. No entity or DAO changes.

## API Contracts & Interfaces

The private extension function signature is unchanged. Only the body changes:

```kotlin
// FirestoreDataSource.kt — private inner scope
private fun String.encryptedForFirestore(): String
```

Before: throws `IllegalStateException` if `!localEncryption.isEncrypted(this)`.  
After: returns `localEncryption.encryptString(this)` (which is idempotent for already-encrypted strings).

## Config / Permissions Delta

None.

## Constitution Audit

This change modifies a single private function in `:core:data`, touches no domain or UI layers, and is covered by unit tests using `FakeLocalDataEncryptionService` — consistent with `DATA_LAYER_RULES.md` and `UNIT_TESTING.md`.

## Subagent-Driven Execution

Downstream executors act as coordinators first, implementers second. For each Implementation Task:

1. **Spawn** a child subagent scoped to that task alone.
2. **Review** the child subagent's output against this document — accept nothing that contradicts the File Structure, API Contracts, task Files lists, or scenario bindings.
3. **Repeat** task by task in order.

Scenario IDs are the unit of work each child subagent must satisfy; cross-check every ID the task names under **Verifies scenarios** against `test_spec.md` before closing the task.

**Per-child review checklist (parent executor):**
- Paths touched match the task **Files** entries and the File Structure table; no edits outside listed paths.
- Every **Verifies scenarios** ID shows the intended test state for the current phase.
- No scope drift: no unlisted files, no extra behaviors, no shortcuts that bypass this spec.
- Deliverables live in real source and test files aligned with this spec.

---

## Implementation Tasks

### Task 1: Write failing tests for the correct encrypt-on-the-fly behavior

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSourceTest.kt`

**Change:** Inside the existing `SyncPendingChangesTests` nested class, replace the existing test `failsClosedWhenPendingRoomFieldsArePlaintext` (which asserts failure) with `encryptsPlaintextDocumentFieldsBeforeUpload` (asserts success + Firestore receives encrypted value). Add `encryptsPlaintextTranscriptSegmentFieldBeforeUpload` and `doesNotDoubleEncryptAlreadyEncryptedFields`. All three tests reference `FakeLocalDataEncryptionService` for the `localEncryption` parameter. The tests must be RED (failing) after this task because production code has not changed yet.

**Verifies scenarios:** S1, S2, S3, M1

**Commit message:**

```
test(BUG-02): add red tests for encrypt-on-the-fly sync behavior
```

### Task 2: Fix `encryptedForFirestore()` to encrypt instead of assert

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt:169-174`

**Change:** Replace the `check(localEncryption.isEncrypted(this))` body with `if (localEncryption.isEncrypted(this)) this else localEncryption.encryptString(this)`. No other changes in the file.

**Verifies scenarios:** S1, S2, S3, M1

**Commit message:**

```
fix(BUG-02): encrypt plaintext fields on-the-fly before Firestore upload
```

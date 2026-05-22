package com.browntowndev.enclave.core.data.security

enum class PersistenceBoundary {
    ROOM,
    FIRESTORE,
    DATASTORE,
    ENCRYPTED_SHARED_PREFERENCES,
    SHARED_PREFERENCES,
    WORKER,
    EMBEDDING_CONSUMER,
}

enum class SensitiveRepairAction {
    NONE,
    REPAIRED,
    DEFERRED_LOCKED,
    REJECTED_MALFORMED_ENVELOPE,
    REJECTED_INVALID_CIPHERTEXT,
    REJECTED_UNSUPPORTED_FIELD,
}

data class SensitiveFieldRef(
    val owner: String,
    val field: String,
    val boundary: PersistenceBoundary,
    val collectionPath: String? = null,
)

data class SensitiveRepairEvent(
    val ref: SensitiveFieldRef,
    val action: SensitiveRepairAction,
    val entityId: String,
    val sourceSide: String,
)

data class SensitiveRepairResult(
    val value: String?,
    val action: SensitiveRepairAction,
    val event: SensitiveRepairEvent? = null,
)

class SensitivePersistenceLockedException(
    message: String,
) : IllegalStateException(message)

/**
 * Queued when [SensitivePersistenceSelfHealer.decryptForRead] repairs plaintext at rest while the
 * vault is unlocked. [SensitiveRoomReadRepairApplier] persists [encryptedValue] back to Room.
 */
data class SensitiveRoomReadRepair(
    val ref: SensitiveFieldRef,
    val entityId: String,
    val encryptedValue: String,
)

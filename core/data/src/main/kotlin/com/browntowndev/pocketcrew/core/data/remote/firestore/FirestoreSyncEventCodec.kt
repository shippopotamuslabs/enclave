package com.browntowndev.pocketcrew.core.data.remote.firestore

/**
 * Decodes Firestore `sync_events` payloads into [FirestoreSyncEvent]. Rejects malformed
 * documents with exact, stable error messages so the upstream caller can surface the
 * precise validation failure to logs and tests.
 */
internal object FirestoreSyncEventCodec {
    private const val SUPPORTED_SCHEMA_VERSION = 1

    @Suppress("ThrowsCount", "UseRequire")
    fun parse(data: Map<String, Any?>): FirestoreSyncEvent {
        val schemaVersion = (data["schemaVersion"] as? Number)?.toInt()
        if (schemaVersion != null && schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw IllegalArgumentException("unsupported sync event schema version: $schemaVersion")
        }
        val sequence = (data["sequence"] as? Number)?.toLong()
            ?: throw IllegalArgumentException("sync event missing sequence")
        val collectionPath = (data["collectionPath"] as? String)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("sync event missing collectionPath")
        val entityId = (data["entityId"] as? String)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("sync event missing entityId")
        val operation = (data["operation"] as? String) ?: "upsert"
        if (operation != "upsert" && operation != "delete") {
            throw IllegalArgumentException("unsupported sync event operation: $operation")
        }
        return FirestoreSyncEvent(
            sequence = sequence,
            schemaVersion = schemaVersion ?: SUPPORTED_SCHEMA_VERSION,
            operation = operation,
            collectionPath = collectionPath,
            entityType = (data["entityType"] as? String) ?: collectionPath,
            entityId = entityId,
            entityPath = (data["entityPath"] as? String).orEmpty(),
            sourceExists = (data["sourceExists"] as? Boolean) ?: (operation == "upsert"),
            sourceUpdatedAt = (data["sourceUpdatedAt"] as? Number)?.toLong() ?: 0L,
            sourceDeleted = (data["sourceDeleted"] as? Boolean) ?: (operation == "delete"),
            deviceId = data["deviceId"] as? String,
            clientMutationId = data["clientMutationId"] as? String,
            triggerEventId = (data["triggerEventId"] as? String).orEmpty(),
            emittedAtMillis = (data["emittedAtMillis"] as? Number)?.toLong() ?: 0L,
        )
    }
}

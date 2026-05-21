package com.browntowndev.pocketcrew.core.data.remote.firestore

internal data class FirestoreSyncEvent(
    val sequence: Long,
    val schemaVersion: Int,
    val operation: String,
    val collectionPath: String,
    val entityType: String,
    val entityId: String,
    val entityPath: String,
    val sourceExists: Boolean,
    val sourceUpdatedAt: Long,
    val sourceDeleted: Boolean,
    val deviceId: String?,
    val clientMutationId: String?,
    val triggerEventId: String,
    val emittedAtMillis: Long,
)

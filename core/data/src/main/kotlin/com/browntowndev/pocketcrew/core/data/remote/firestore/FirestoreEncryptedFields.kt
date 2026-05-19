package com.browntowndev.pocketcrew.core.data.remote.firestore

import com.browntowndev.pocketcrew.core.data.remote.FirestoreSensitiveFieldRefMapping
import com.google.firebase.firestore.DocumentSnapshot

internal fun String.encryptedForFirestore(
    ctx: FirestoreSyncContext,
    collectionPath: String,
    entityId: String,
    firestoreSnakeField: String,
): String =
    ctx.sensitiveSelfHealer.requireEncrypted(
        ref = FirestoreSensitiveFieldRefMapping.sensitiveFieldRef(collectionPath, firestoreSnakeField),
        entityId = entityId,
        value = this,
        sourceSide = "ROOM",
    ) ?: this

internal fun DocumentSnapshot.getEncryptedField(
    ctx: FirestoreSyncContext,
    field: String,
): String? {
    val collectionPath = reference.parent.id
    val stringValue = getString(field)
    if (!stringValue.isNullOrEmpty()) {
        return ctx.sensitiveSelfHealer.requireEncrypted(
            ref = FirestoreSensitiveFieldRefMapping.sensitiveFieldRef(collectionPath, field),
            entityId = id,
            value = stringValue,
            sourceSide = "FIRESTORE",
        )
    }
    return getBlob(field)?.toBytes()?.takeIf { it.isNotEmpty() }?.decodeToString()
}

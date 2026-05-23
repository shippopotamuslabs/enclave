package com.browntowndev.enclave.core.data.remote.firestore.handlers

import com.browntowndev.enclave.core.data.remote.EntitySyncAdapter
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot

internal class ApiKeySyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<Pair<String, String>> {
    override val collectionPath: String = "api_keys"

    override suspend fun claimPending() {
        ctx.apiKeyManager.claimPending()
    }

    override suspend fun getClaimedForSync(): List<Pair<String, String>> {
        val syncAliases = ctx.apiKeyManager.getClaimedSyncAliases()
        val deleteAliases = ctx.apiKeyManager.getClaimedDeleteAliases()
        val invalidSyncAliases = syncAliases.filter { it.trim().isEmpty() || it != it.trim() }
        val invalidDeleteAliases = deleteAliases.filter { it.trim().isEmpty() || it != it.trim() }
        if (invalidSyncAliases.isNotEmpty()) {
            ctx.apiKeyManager.clearClaimedSync(invalidSyncAliases)
        }
        if (invalidDeleteAliases.isNotEmpty()) {
            ctx.apiKeyManager.clearClaimedDelete(invalidDeleteAliases)
        }

        val syncList =
            syncAliases
                .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                .distinct()
                .mapNotNull { alias ->
                    ctx.apiKeyManager
                        .get(alias)
                        ?.takeIf(String::isNotBlank)
                        ?.let { key -> alias to key }
                }
        val deleteList =
            deleteAliases
                .mapNotNull { alias ->
                    alias.trim().takeIf(String::isNotEmpty)?.let { it to DELETE_SENTINEL }
                }.distinct()

        return syncList + deleteList
    }

    override suspend fun markSynced(entities: List<Pair<String, String>>) {
        val (toDelete, toSync) = entities.partition { it.second == DELETE_SENTINEL }
        ctx.apiKeyManager.clearClaimedSync(toSync.map { it.first })
        ctx.apiKeyManager.clearClaimedDelete(toDelete.map { it.first })
    }

    override fun getEntityId(entity: Pair<String, String>): String = entity.first

    override fun shouldDelete(entity: Pair<String, String>): Boolean = entity.second == DELETE_SENTINEL

    override suspend fun toFirestore(local: Pair<String, String>): Map<String, Any?> {
        val (alias, key) = local
        if (key == DELETE_SENTINEL) return emptyMap()

        val encrypted = ctx.cryptoManager.encrypt(key).getOrThrow()
        return mapOf(
            "alias" to alias,
            "encrypted_key" to Blob.fromBytes(encrypted),
            "updated_at" to
                com.google.firebase.Timestamp
                    .now(),
        )
    }

    override fun fromFirestore(doc: DocumentSnapshot): Pair<String, String>? {
        val alias = doc.id
        val blob = doc.getBlob("encrypted_key") ?: return null
        val decrypted = ctx.cryptoManager.decrypt(blob.toBytes()).getOrNull() ?: return null
        return alias to decrypted
    }

    override suspend fun upsertLocal(
        entity: Pair<String, String>,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        ctx.apiKeyManager.saveFromRemote(entity.first, entity.second)
    }

    override suspend fun deleteLocal(id: String) {
        ctx.apiKeyManager.deleteFromRemote(id)
    }

    private companion object {
        const val DELETE_SENTINEL = "DELETE_SENTINEL"
    }
}

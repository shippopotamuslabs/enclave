package com.browntowndev.enclave.domain.port.repository

interface CleanupRepository {
    /** Wipes everything including model/provider configuration. Use for account deletion. */
    suspend fun clearAllData()

    /**
     * Wipes user-generated content (chats, memories, studio, embeddings, web search sources,
     * and search-skill API keys) while preserving model/provider configuration so a guest
     * session can keep using the device's local and API models. Use for sign-out.
     */
    suspend fun clearUserContent()

    /** Deletes persisted artifact/document rows only (no full user wipe). */
    suspend fun deleteAllArtifacts()
}

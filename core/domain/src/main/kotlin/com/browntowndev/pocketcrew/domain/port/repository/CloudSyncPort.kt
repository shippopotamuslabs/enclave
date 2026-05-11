package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Message

interface CloudSyncPort {
    /**
     * Starts listening to cloud changes and synchronizing them to the local database.
     * This should typically be called when the user is signed in and the app is foregrounded.
     */
    fun startListening()

    /**
     * Stops listening to cloud changes.
     * This should be called when the user signs out or the app goes to the background.
     */
    fun stopListening()

    /**
     * Pushes all local unsynced data to the cloud.
     * This is useful during the initial sign-in process to merge anonymous data.
     */
    suspend fun syncAllLocalDataToCloud(): Result<Unit>

    /**
     * Surgically synchronizes specific chats and messages to the cloud.
     */
    suspend fun syncEntitiesToCloud(
        chats: List<Chat>,
        messages: List<Message>
    ): Result<Unit>

    /**
     * Fetches all locally pending-sync entities and pushes them to the cloud.
     * Called by SyncWorker on every triggered sync. Handles vault-lock check internally.
     */
    suspend fun syncPendingChanges(): Result<Unit>

    /**
     * Updates the user's vault salt in the cloud.
     */
    suspend fun updateVaultSalt(salt: String): Result<Unit>

    /**
     * Retrieves the user's vault salt from the cloud.
     */
    suspend fun getVaultSalt(): Result<String?>
    /**
     * Updates the user's vault verification ciphertext in the cloud.
     */
    suspend fun updateVaultVerificationData(ciphertext: String): Result<Unit>

    /**
     * Retrieves the user's vault verification ciphertext from the cloud.
     */
    suspend fun getVaultVerificationData(): Result<String?>

    /**
     * Enqueues a background task to regenerate all embeddings for the local corpus.
     * Typically called after a full cloud sync.
     */
    fun enqueueEmbeddingRegeneration()
}

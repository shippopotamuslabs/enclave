package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.security.VaultMetadata

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
        messages: List<Message>,
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
     * Persists the wrapped Data Encryption Key (`Base64(iv || aesGcm(KEK, dek))`) on the cloud
     * user document. Pass `null` to clear the field.
     */
    suspend fun updateWrappedDek(wrappedDek: String?): Result<Unit>

    /**
     * Retrieves the wrapped Data Encryption Key from the cloud user document.
     */
    suspend fun getWrappedDek(): Result<String?>

    /**
     * Atomically writes [VaultMetadata.salt], [VaultMetadata.verification] and
     * [VaultMetadata.wrappedDek] on the cloud user document. Null verification/wrappedDek
     * fields are written as Firestore deletes.
     */
    suspend fun uploadVaultMetadata(metadata: VaultMetadata): Result<Unit>

    /**
     * Fetches the cloud-side vault material in a single read. Returns `Result.success(null)`
     * when the user document does not exist or has no `vaultSalt` field yet.
     */
    suspend fun fetchVaultMetadata(): Result<VaultMetadata?>

    /**
     * Enqueues a background task to regenerate all embeddings for the local corpus.
     * Typically called after a full cloud sync.
     */
    fun enqueueEmbeddingRegeneration()

    /**
     * Deletes this user's cloud-side data (Firestore user subtree). Does not sign the user out —
     * callers typically invoke [com.browntowndev.pocketcrew.domain.port.auth.AuthManager.signOut] after success.
     */
    suspend fun deleteUserAccount(): Result<Unit>

    /**
     * Purges all chat-related collections from Firestore for the current user.
     */
    suspend fun deleteAllChatsFromCloud(): Result<Unit>

    /**
     * Purges all studio media and album collections from Firestore for the current user.
     */
    suspend fun deleteAllMediaFromCloud(): Result<Unit>

    /**
     * Purges all memories from Firestore for the current user.
     */
    suspend fun deleteAllMemoriesFromCloud(): Result<Unit>

    /**
     * Purges all artifacts/documents from Firestore for the current user.
     */
    suspend fun deleteAllArtifactsFromCloud(): Result<Unit>
}

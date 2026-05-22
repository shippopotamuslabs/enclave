package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.domain.model.security.VaultMetadata
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

internal class FirestoreVaultClient(
    private val ctx: FirestoreSyncContext,
) {
    suspend fun updateVaultSalt(salt: String): Result<Unit> =
        withSignedInUser { userId ->
            ctx.firestore
                .document("users/$userId")
                .set(mapOf("vaultSalt" to salt), SetOptions.merge())
                .await()
        }

    suspend fun getVaultSalt(): Result<String?> =
        withSignedInUser { userId ->
            val snapshot =
                ctx.firestore
                    .document("users/$userId")
                    .get()
                    .await()
            if (snapshot.exists()) {
                snapshot.getString("vaultSalt")
            } else {
                null
            }
        }

    suspend fun updateVaultVerificationData(ciphertext: String): Result<Unit> =
        withSignedInUser { userId ->
            ctx.firestore
                .document("users/$userId")
                .set(mapOf("vaultVerification" to ciphertext), SetOptions.merge())
                .await()
        }

    suspend fun getVaultVerificationData(): Result<String?> =
        withSignedInUser { userId ->
            val snapshot =
                ctx.firestore
                    .document("users/$userId")
                    .get()
                    .await()
            if (snapshot.exists()) {
                snapshot.getString("vaultVerification")
            } else {
                null
            }
        }

    suspend fun updateWrappedDek(wrappedDek: String?): Result<Unit> =
        withSignedInUser { userId ->
            val value: Any = wrappedDek ?: FieldValue.delete()
            ctx.firestore
                .document("users/$userId")
                .set(mapOf("wrappedDek" to value), SetOptions.merge())
                .await()
        }

    suspend fun getWrappedDek(): Result<String?> =
        withSignedInUser { userId ->
            val snapshot =
                ctx.firestore
                    .document("users/$userId")
                    .get()
                    .await()
            if (snapshot.exists()) {
                snapshot.getString("wrappedDek")
            } else {
                null
            }
        }

    suspend fun uploadVaultMetadata(metadata: VaultMetadata): Result<Unit> =
        withSignedInUser { userId ->
            val payload =
                mapOf(
                    "vaultSalt" to metadata.salt,
                    "vaultVerification" to (metadata.verification ?: FieldValue.delete()),
                    "wrappedDek" to (metadata.wrappedDek ?: FieldValue.delete()),
                )
            ctx.firestore
                .document("users/$userId")
                .set(payload, SetOptions.merge())
                .await()
        }

    suspend fun fetchVaultMetadata(): Result<VaultMetadata?> =
        withSignedInUser { userId ->
            val snapshot =
                ctx.firestore
                    .document("users/$userId")
                    .get()
                    .await()
            if (!snapshot.exists()) return@withSignedInUser null
            val salt = snapshot.getString("vaultSalt") ?: return@withSignedInUser null
            VaultMetadata(
                salt = salt,
                verification = snapshot.getString("vaultVerification"),
                wrappedDek = snapshot.getString("wrappedDek"),
            )
        }

    private inline fun <T> withSignedInUser(block: (String) -> T): Result<T> {
        val userId = ctx.userIdProvider() ?: return Result.failure(IllegalStateException("User not signed in"))
        return runCatching { block(userId) }
    }
}

package com.browntowndev.pocketcrew.domain.usecase.security

/**
 * Result of comparing the locally-cached vault material against the user's Firestore vault
 * document, produced by [DetectVaultReconciliationStateUseCase]. The detector is read-only —
 * never mutates state. Each outcome maps to a distinct caller decision:
 *
 *  - [AlreadyMatching] / [NoLocalVault] — safe to start cloud sync immediately.
 *  - [NoCloudVault] — caller should invoke [BootstrapCloudVaultFromLocalUseCase] then start
 *    cloud sync.
 *  - [PasswordMismatch] — caller should surface "Incorrect Vault Password".
 *  - [MismatchRequiresClobber] — caller MUST surface user confirmation before invoking
 *    [AdoptCloudVaultUseCase] because adoption wipes Room.
 */
sealed interface VaultReconciliationOutcome {
    data object AlreadyMatching : VaultReconciliationOutcome
    data object NoCloudVault : VaultReconciliationOutcome
    data object NoLocalVault : VaultReconciliationOutcome
    data object PasswordMismatch : VaultReconciliationOutcome

    /**
     * Local DEK plaintext differs from cloud DEK plaintext. Both unwrap with the supplied
     * password but decrypt different data sets.
     *
     * The cloud-side material is echoed back in this payload so the caller can hand it
     * directly to [AdoptCloudVaultUseCase] without a second Firestore round-trip.
     *
     * @param cloudDek raw 32-byte DEK ready to cache after user consents.
     * @param cloudSalt Base64 cloud salt to persist locally.
     * @param cloudWrappedDek Base64 cloud `iv || aesGcm(KEK, dek)` blob.
     * @param cloudVerification Optional Base64 cloud verification ciphertext (may be null).
     */
    class MismatchRequiresClobber(
        val cloudDek: ByteArray,
        val cloudSalt: String,
        val cloudWrappedDek: String,
        val cloudVerification: String?,
    ) : VaultReconciliationOutcome {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MismatchRequiresClobber) return false
            if (!cloudDek.contentEquals(other.cloudDek)) return false
            if (cloudSalt != other.cloudSalt) return false
            if (cloudWrappedDek != other.cloudWrappedDek) return false
            if (cloudVerification != other.cloudVerification) return false
            return true
        }

        override fun hashCode(): Int {
            var result = cloudDek.contentHashCode()
            result = 31 * result + cloudSalt.hashCode()
            result = 31 * result + cloudWrappedDek.hashCode()
            result = 31 * result + (cloudVerification?.hashCode() ?: 0)
            return result
        }

        fun copy(
            cloudDek: ByteArray = this.cloudDek,
            cloudSalt: String = this.cloudSalt,
            cloudWrappedDek: String = this.cloudWrappedDek,
            cloudVerification: String? = this.cloudVerification,
        ): MismatchRequiresClobber = MismatchRequiresClobber(cloudDek, cloudSalt, cloudWrappedDek, cloudVerification)
    }
}

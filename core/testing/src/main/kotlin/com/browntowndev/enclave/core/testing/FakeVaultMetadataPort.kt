package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.port.security.VaultMetadataPort

/** In-memory [VaultMetadataPort] fake (ENC-28) with call recording. */
class FakeVaultMetadataPort : VaultMetadataPort {
    var salt: String? = null
    var verificationData: String? = null
    var wrappedDek: String? = null

    var getVerificationDataCallCount = 0
    val savedVerificationData = mutableListOf<String?>()
    val savedSalts = mutableListOf<String>()
    val savedWrappedDeks = mutableListOf<String?>()

    /** Optional shared event log for cross-collaborator ordering assertions. */
    var events: MutableList<String>? = null

    override suspend fun getVaultSalt(): String? = salt

    override suspend fun saveVaultSalt(salt: String) {
        savedSalts += salt
        this.salt = salt
    }

    override suspend fun getVaultVerificationData(): String? {
        getVerificationDataCallCount++
        events?.add("vaultMetaVerif")
        return verificationData
    }

    override suspend fun saveVaultVerificationData(ciphertext: String?) {
        savedVerificationData += ciphertext
        verificationData = ciphertext
    }

    override suspend fun getWrappedDek(): String? = wrappedDek

    override suspend fun saveWrappedDek(wrappedDek: String?) {
        savedWrappedDeks += wrappedDek
        this.wrappedDek = wrappedDek
    }
}

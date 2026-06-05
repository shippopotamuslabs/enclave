package com.browntowndev.enclave.domain.port.repository

interface TrustedDomainRepositoryPort {
    suspend fun getTrustedDomains(): Set<String>

    suspend fun getTrustedWildcards(): Set<String>

    suspend fun syncTrustedDomains(): Result<Unit>
}

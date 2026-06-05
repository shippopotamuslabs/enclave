package com.browntowndev.enclave.domain.usecase.inference

import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.TrustedDomainRepositoryPort
import java.net.URI
import javax.inject.Inject

class ShouldModeratePromptUseCase
    @Inject
    constructor(
        private val activeModelProvider: ActiveModelProviderPort,
        private val trustedDomainRepository: TrustedDomainRepositoryPort,
    ) {
        suspend operator fun invoke(modelType: ModelType): Boolean {
            val config = activeModelProvider.getActiveConfiguration(modelType)
            if (config == null || config.isLocal) return true
            val url = config.apiBaseUrl ?: return true

            val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return true
            val isTrusted =
                trustedDomainRepository.getTrustedDomains().any { it.lowercase() == host } ||
                    trustedDomainRepository.getTrustedWildcards().any { wildcard ->
                        val normalizedWildcard = wildcard.lowercase()
                        val dotPrefixed = if (normalizedWildcard.startsWith(".")) normalizedWildcard else ".$normalizedWildcard"
                        host == normalizedWildcard.removePrefix(".") || host.endsWith(dotPrefixed)
                    }

            return !isTrusted
        }
    }

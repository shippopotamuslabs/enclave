package com.browntowndev.enclave.domain.usecase.inference

import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.config.DistributionConfigurationPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.TrustedDomainRepositoryPort
import java.net.URI
import javax.inject.Inject

class ShouldModeratePromptUseCase
    @Inject
    constructor(
        private val distributionConfig: DistributionConfigurationPort,
        private val activeModelProvider: ActiveModelProviderPort,
        private val trustedDomainRepository: TrustedDomainRepositoryPort,
        private val loggingPort: LoggingPort,
    ) {
        suspend operator fun invoke(modelType: ModelType): Boolean {
            if (!distributionConfig.isPlayStoreFlavor) {
                loggingPort.debug(TAG, "Skipping moderation: not playStore flavor")
                return false
            }

            val config = activeModelProvider.getActiveConfiguration(modelType)
            if (config == null || config.isLocal) {
                loggingPort.debug(TAG, "Should moderate ($modelType): config=$config, isLocal=${config?.isLocal}")
                return true
            }
            val url = config.apiBaseUrl ?: config.apiProvider?.defaultBaseUrl() ?: return true

            val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return true
            val isTrusted =
                trustedDomainRepository.getTrustedDomains().any { it.lowercase() == host } ||
                    trustedDomainRepository.getTrustedWildcards().any { wildcard ->
                        val normalizedWildcard = wildcard.lowercase()
                        val dotPrefixed = if (normalizedWildcard.startsWith(".")) normalizedWildcard else ".$normalizedWildcard"
                        host == normalizedWildcard.removePrefix(".") || host.endsWith(dotPrefixed)
                    }

            loggingPort.debug(TAG, "Should moderate ($modelType): host=$host, isTrusted=$isTrusted, result=${!isTrusted}")
            return !isTrusted
        }

        private companion object {
            private const val TAG = "ShouldModeratePrompt"
        }
    }

package com.browntowndev.enclave.domain.usecase.inference

import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
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
            return shouldModerateResolvedConfig(config, "modelType=$modelType")
        }

        /**
         * Moderation resolution for an explicit chat-model override (OTHER mode).
         *
         * Resolves the [ActiveModelConfiguration] from [override] via
         * [ActiveModelProviderPort.getActiveConfigurationByOverride] — the same
         * override seam used by inference dispatch and photo-attachment policy —
         * instead of reading the FAST slot. This ensures an untrusted API or local
         * override is moderated on its own merits rather than inheriting the
         * moderation posture of whatever model happens to occupy the FAST slot.
         */
        suspend operator fun invoke(override: ChatModelOverride): Boolean {
            if (!distributionConfig.isPlayStoreFlavor) {
                loggingPort.debug(TAG, "Skipping moderation: not playStore flavor")
                return false
            }

            val config = activeModelProvider.getActiveConfigurationByOverride(override)
            return shouldModerateResolvedConfig(config, "override=$override")
        }

        private suspend fun shouldModerateResolvedConfig(
            config: ActiveModelConfiguration?,
            contextLabel: String,
        ): Boolean {
            if (config == null || config.isLocal) {
                loggingPort.debug(TAG, "Should moderate ($contextLabel): config=$config, isLocal=${config?.isLocal}")
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

            loggingPort.debug(TAG, "Should moderate ($contextLabel): host=$host, isTrusted=$isTrusted, result=${!isTrusted}")
            return !isTrusted
        }

        private companion object {
            private const val TAG = "ShouldModeratePrompt"
        }
    }

package com.browntowndev.enclave.feature.chat.mapping

import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.feature.chat.ChatModeUi
import javax.inject.Inject

data class PhotoAttachmentPolicy(
    val isEnabled: Boolean,
    val maxItems: Int = 1,
    val disabledReason: String? = null,
)

class PhotoAttachmentPolicyResolver
    @Inject
    constructor(
        private val activeModelProvider: ActiveModelProviderPort,
    ) {
        suspend fun resolve(
            selectedMode: ChatModeUi,
            modelOverride: ChatModelOverride? = null,
        ): PhotoAttachmentPolicy {
            val activeConfig =
                when (selectedMode) {
                    ChatModeUi.FAST -> activeModelProvider.getActiveConfiguration(ModelType.FAST)
                    ChatModeUi.THINKING -> activeModelProvider.getActiveConfiguration(ModelType.THINKING)
                    ChatModeUi.COUNCIL -> null
                    ChatModeUi.OTHER ->
                        modelOverride?.let { activeModelProvider.getActiveConfigurationByOverride(it) }
                }
            val visionConfig = activeModelProvider.getActiveConfiguration(ModelType.VISION)
            val apiVisionConfigured = visionConfig?.let { it.isLocal == false && it.isMultimodal } ?: false
            val activeVisionCapable = activeConfig?.isMultimodal == true

            val maxItems =
                when {
                    activeVisionCapable ->
                        when {
                            activeConfig?.isLocal == true -> 1
                            activeConfig?.apiProvider == ApiProvider.GOOGLE -> GOOGLE_VISION_MAX_IMAGES
                            else -> DEFAULT_API_VISION_MAX_IMAGES
                        }
                    apiVisionConfigured ->
                        when {
                            visionConfig?.apiProvider == ApiProvider.GOOGLE -> GOOGLE_VISION_MAX_IMAGES
                            else -> DEFAULT_API_VISION_MAX_IMAGES
                        }
                    else -> 0
                }

            return when (selectedMode) {
                ChatModeUi.FAST, ChatModeUi.THINKING -> {
                    if (activeVisionCapable || apiVisionConfigured) {
                        PhotoAttachmentPolicy(isEnabled = true, maxItems = maxItems)
                    } else {
                        PhotoAttachmentPolicy(
                            isEnabled = false,
                            maxItems = 0,
                            disabledReason =
                                "Photo attachments require a vision-capable Fast/Thinking model " +
                                    "or a configured API vision model.",
                        )
                    }
                }
                ChatModeUi.COUNCIL -> {
                    if (apiVisionConfigured) {
                        PhotoAttachmentPolicy(isEnabled = true, maxItems = maxItems)
                    } else {
                        PhotoAttachmentPolicy(
                            isEnabled = false,
                            maxItems = 0,
                            disabledReason = "Council mode requires a configured API vision model for photo attachments.",
                        )
                    }
                }
                ChatModeUi.OTHER -> {
                    if (activeVisionCapable || apiVisionConfigured) {
                        PhotoAttachmentPolicy(isEnabled = true, maxItems = maxItems)
                    } else {
                        PhotoAttachmentPolicy(
                            isEnabled = false,
                            maxItems = 0,
                            disabledReason =
                                "Photo attachments require a vision-capable override model " +
                                    "or a configured API vision model.",
                        )
                    }
                }
            }
        }

        private companion object {
            private const val GOOGLE_VISION_MAX_IMAGES = 3600
            private const val DEFAULT_API_VISION_MAX_IMAGES = 20
        }
    }

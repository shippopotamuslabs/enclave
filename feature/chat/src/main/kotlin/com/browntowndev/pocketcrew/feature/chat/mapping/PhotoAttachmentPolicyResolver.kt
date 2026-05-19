package com.browntowndev.pocketcrew.feature.chat.mapping

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.feature.chat.ChatModeUi
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
        suspend fun resolve(selectedMode: ChatModeUi): PhotoAttachmentPolicy {
            val activeConfig =
                when (selectedMode) {
                    ChatModeUi.FAST -> activeModelProvider.getActiveConfiguration(ModelType.FAST)
                    ChatModeUi.THINKING -> activeModelProvider.getActiveConfiguration(ModelType.THINKING)
                    ChatModeUi.CREW -> null
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
                ChatModeUi.CREW -> {
                    if (apiVisionConfigured) {
                        PhotoAttachmentPolicy(isEnabled = true, maxItems = maxItems)
                    } else {
                        PhotoAttachmentPolicy(
                            isEnabled = false,
                            maxItems = 0,
                            disabledReason = "Crew mode requires a configured API vision model for photo attachments.",
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

package com.browntowndev.enclave.domain.model.chat

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId

/**
 * Ephemeral per-chat model override selected from the "Other >" picker.
 * Carried only on [ChatResponseMode.Other]; never persisted to the database.
 */
sealed interface ChatModelOverride {
    val displayName: String

    data class Local(
        val configId: LocalModelConfigurationId,
        override val displayName: String,
    ) : ChatModelOverride

    data class Api(
        val configId: ApiModelConfigurationId,
        override val displayName: String,
    ) : ChatModelOverride
}

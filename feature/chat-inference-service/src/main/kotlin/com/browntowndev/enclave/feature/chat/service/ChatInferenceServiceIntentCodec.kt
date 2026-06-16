package com.browntowndev.enclave.feature.chat.service

import android.content.Intent
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId

internal object ChatInferenceServiceIntentCodec {
    const val EXTRA_MODEL_OVERRIDE_CONFIG_ID = "extra_model_override_config_id"
    const val EXTRA_MODEL_OVERRIDE_IS_LOCAL = "extra_model_override_is_local"
    const val EXTRA_MODEL_OVERRIDE_DISPLAY_NAME = "extra_model_override_display_name"

    fun putModelOverride(
        intent: Intent,
        override: ChatModelOverride,
    ) {
        when (override) {
            is ChatModelOverride.Local -> {
                intent.putExtra(EXTRA_MODEL_OVERRIDE_CONFIG_ID, override.configId.value)
                intent.putExtra(EXTRA_MODEL_OVERRIDE_IS_LOCAL, true)
                intent.putExtra(EXTRA_MODEL_OVERRIDE_DISPLAY_NAME, override.displayName)
            }
            is ChatModelOverride.Api -> {
                intent.putExtra(EXTRA_MODEL_OVERRIDE_CONFIG_ID, override.configId.value)
                intent.putExtra(EXTRA_MODEL_OVERRIDE_IS_LOCAL, false)
                intent.putExtra(EXTRA_MODEL_OVERRIDE_DISPLAY_NAME, override.displayName)
            }
        }
    }

    fun getModelOverride(intent: Intent): ChatModelOverride? {
        val configId = intent.getStringExtra(EXTRA_MODEL_OVERRIDE_CONFIG_ID) ?: return null
        val displayName = intent.getStringExtra(EXTRA_MODEL_OVERRIDE_DISPLAY_NAME) ?: return null
        return if (intent.getBooleanExtra(EXTRA_MODEL_OVERRIDE_IS_LOCAL, false)) {
            ChatModelOverride.Local(
                configId = LocalModelConfigurationId(configId),
                displayName = displayName,
            )
        } else {
            ChatModelOverride.Api(
                configId = ApiModelConfigurationId(configId),
                displayName = displayName,
            )
        }
    }
}

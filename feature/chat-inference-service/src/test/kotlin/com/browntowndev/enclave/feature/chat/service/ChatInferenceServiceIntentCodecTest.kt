package com.browntowndev.enclave.feature.chat.service

import android.content.Intent
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ChatInferenceServiceIntentCodecTest {
    @Test
    fun `round trips api override`() {
        val override =
            ChatModelOverride.Api(
                configId = ApiModelConfigurationId("api-claude"),
                displayName = "Claude Sonnet",
            )
        val intent = Intent()
        ChatInferenceServiceIntentCodec.putModelOverride(intent, override)

        assertEquals(override, ChatInferenceServiceIntentCodec.getModelOverride(intent))
    }

    @Test
    fun `round trips local override`() {
        val override =
            ChatModelOverride.Local(
                configId = LocalModelConfigurationId("local-qwen"),
                displayName = "Qwen Override",
            )
        val intent = Intent()
        ChatInferenceServiceIntentCodec.putModelOverride(intent, override)

        assertEquals(override, ChatInferenceServiceIntentCodec.getModelOverride(intent))
    }

    @Test
    fun `missing override extras returns null`() {
        assertNull(ChatInferenceServiceIntentCodec.getModelOverride(Intent()))
    }
}
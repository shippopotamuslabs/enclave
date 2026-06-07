package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.port.media.LiveVoiceSession

data class LiveVoiceSessionOpenResult(
    val session: LiveVoiceSession,
    val asset: LiveVoiceProviderAsset,
)

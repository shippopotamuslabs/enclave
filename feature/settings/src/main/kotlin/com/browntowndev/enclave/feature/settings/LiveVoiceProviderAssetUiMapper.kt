package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import javax.inject.Inject

class LiveVoiceProviderAssetUiMapper
    @Inject
    constructor() {
        fun map(
            asset: LiveVoiceProviderAsset,
            isDefault: Boolean,
        ): LiveVoiceProviderAssetUi =
            LiveVoiceProviderAssetUi(
                id = asset.id,
                displayName = asset.displayName,
                provider = asset.provider,
                voiceName = asset.voiceName,
                modelName = asset.modelName,
                baseUrl = asset.baseUrl,
                credentialAlias = asset.credentialAlias,
                useAsDefault = isDefault,
            )
    }

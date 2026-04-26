package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.domain.model.config.TtsProviderAsset
import javax.inject.Inject

class TtsProviderAssetUiMapper @Inject constructor() {
    fun map(asset: TtsProviderAsset, isDefault: Boolean): TtsProviderAssetUi = TtsProviderAssetUi(
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

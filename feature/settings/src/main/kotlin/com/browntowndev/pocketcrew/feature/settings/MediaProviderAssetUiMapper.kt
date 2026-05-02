package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import javax.inject.Inject

class MediaProviderAssetUiMapper @Inject constructor() {
    fun map(asset: MediaProviderAsset, isDefault: Boolean): MediaProviderAssetUi = MediaProviderAssetUi(
        id = asset.id,
        displayName = asset.displayName,
        provider = asset.provider,
        capability = asset.capability,
        modelName = asset.modelName ?: "",
        baseUrl = asset.baseUrl,
        credentialAlias = asset.credentialAlias,
        useAsDefault = isDefault,
    )
}

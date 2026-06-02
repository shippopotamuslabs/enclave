package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.domain.usecase.settings.ApplyApiModelMetadataDefaultsUseCase
import com.browntowndev.enclave.feature.settings.usage.UsageSpendUiMapper
import io.mockk.mockk

internal fun buildSettingsUiStateFactory(
    localModelAssetUiMapper: LocalModelAssetUiMapper = LocalModelAssetUiMapper(),
    apiModelAssetUiMapper: ApiModelAssetUiMapper = ApiModelAssetUiMapper(),
    ttsProviderAssetUiMapper: TtsProviderAssetUiMapper = TtsProviderAssetUiMapper(),
    mediaProviderAssetUiMapper: MediaProviderAssetUiMapper = mockk(relaxed = true),
    apiDiscoveryUiFilter: ApiDiscoveryUiFilter = ApiDiscoveryUiFilter(),
    applyApiModelMetadataDefaultsUseCase: ApplyApiModelMetadataDefaultsUseCase = ApplyApiModelMetadataDefaultsUseCase(),
    usageSpendUiMapper: UsageSpendUiMapper = UsageSpendUiMapper(),
): SettingsUiStateFactory =
    SettingsUiStateFactory(
        SettingsUiStateAssembler(
            localModelAssetUiMapper = localModelAssetUiMapper,
            apiModelAssetUiMapper = apiModelAssetUiMapper,
            ttsProviderAssetUiMapper = ttsProviderAssetUiMapper,
            mediaProviderAssetUiMapper = mediaProviderAssetUiMapper,
            apiDiscoveryUiFilter = apiDiscoveryUiFilter,
            applyApiModelMetadataDefaultsUseCase = applyApiModelMetadataDefaultsUseCase,
            usageSpendUiMapper = usageSpendUiMapper,
        ),
    )
package com.browntowndev.enclave.feature.studio

import com.browntowndev.enclave.domain.model.media.StudioModelOverride
import com.browntowndev.enclave.domain.usecase.media.StudioCapablePresets

internal fun StudioCapablePresets.toPickerUi(): StudioCapablePresetsUi =
    StudioCapablePresetsUi(
        localAssets =
            localAssets.flatMap { asset ->
                val displayLabel =
                    asset.metadata.displayName.takeIf { it.isNotBlank() }
                        ?: asset.configurations.firstOrNull()?.displayName.orEmpty()
                asset.configurations.map { config ->
                    StudioModelOptionUi(
                        id = config.id.value,
                        title = displayLabel,
                        subtitle = "On-device",
                        kind = StudioOverrideKind.LOCAL,
                        displayName = displayLabel,
                    )
                }
            },
        apiAssets = emptyList(),
        mediaAssets =
            mediaAssets.map { provider ->
                StudioModelOptionUi(
                    id = provider.id.value,
                    title = provider.displayName,
                    subtitle = provider.capability.displayName,
                    kind = StudioOverrideKind.MEDIA_PROVIDER,
                    displayName = provider.displayName,
                )
            },
    )

internal fun StudioModelOverride.optionId(): String =
    when (this) {
        is StudioModelOverride.Local -> configId.value
        is StudioModelOverride.Api -> configId.value
        is StudioModelOverride.MediaProvider -> providerId.value
    }

internal fun StudioModelOptionUi.toDomainOverride(): StudioModelOverride =
    when (kind) {
        StudioOverrideKind.LOCAL ->
            StudioModelOverride.Local(
                configId = com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId(id),
                displayName = displayName,
            )
        StudioOverrideKind.API ->
            StudioModelOverride.Api(
                configId = com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId(id),
                displayName = displayName,
            )
        StudioOverrideKind.MEDIA_PROVIDER ->
            StudioModelOverride.MediaProvider(
                providerId = com.browntowndev.enclave.domain.model.config.MediaProviderId(id),
                displayName = displayName,
            )
    }

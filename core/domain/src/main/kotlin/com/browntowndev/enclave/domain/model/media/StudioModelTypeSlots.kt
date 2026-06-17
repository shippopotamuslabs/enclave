package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.inference.ModelType

/** The [MediaCapability] of the slot this [ModelType] represents. */
internal val ModelType.studioCapability: MediaCapability
    get() =
        when (this) {
            ModelType.IMAGE_GENERATION, ModelType.IMAGE_EDIT -> MediaCapability.IMAGE
            ModelType.VIDEO_GENERATION, ModelType.IMAGE_TO_VIDEO -> MediaCapability.VIDEO
            ModelType.MUSIC_GENERATION -> MediaCapability.MUSIC
            else -> MediaCapability.IMAGE
        }

/** The [MediaTask] a Studio slot requires of a media provider, or null when no task filter applies. */
internal fun ModelType.studioRequiredMediaTask(): MediaTask? =
    when (this) {
        ModelType.IMAGE_GENERATION -> MediaTask.TEXT_TO_IMAGE
        ModelType.IMAGE_EDIT -> MediaTask.EDIT_IMAGE
        ModelType.VIDEO_GENERATION -> MediaTask.TEXT_TO_VIDEO
        ModelType.IMAGE_TO_VIDEO -> MediaTask.IMAGE_TO_VIDEO
        ModelType.MUSIC_GENERATION -> null
        else -> null
    }

/**
 * Whether [this] override can be executed for the resolved Studio [modelType] slot.
 * Used to clear stale overrides when reference-image attachment changes the slot.
 */
fun StudioModelOverride.isEligibleFor(
    modelType: ModelType,
    providers: List<MediaProviderAsset>,
): Boolean =
    when (this) {
        is StudioModelOverride.Api -> false
        is StudioModelOverride.Local ->
            modelType == ModelType.IMAGE_GENERATION || modelType == ModelType.IMAGE_EDIT
        is StudioModelOverride.MediaProvider -> {
            val provider = providers.find { it.id == providerId } ?: return false
            val requiredTask = modelType.studioRequiredMediaTask()
            provider.capability == modelType.studioCapability &&
                (requiredTask == null || provider.tasks.contains(requiredTask))
        }
    }

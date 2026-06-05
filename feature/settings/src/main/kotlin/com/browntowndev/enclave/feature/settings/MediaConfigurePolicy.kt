package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.inference.ApiProvider

internal fun ApiProvider.showsMediaBaseUrlOverride(): Boolean = this == ApiProvider.OPENAI

/** fal.ai task support is defined by discovery; manual toggles would break generation. */
internal fun ApiProvider.locksMediaTaskToggles(): Boolean = this == ApiProvider.FAL

internal fun MediaCapability.requiresMediaTaskSelection(): Boolean = this == MediaCapability.IMAGE || this == MediaCapability.VIDEO

internal fun Set<MediaTask>.hasRelevantTaskFor(capability: MediaCapability): Boolean = any { it.kind == capability }

internal fun isMediaProviderSaveEnabled(
    displayName: String,
    modelName: String,
    capability: MediaCapability,
    tasks: Set<MediaTask>,
    apiKeyPresent: Boolean,
): Boolean {
    if (displayName.isBlank() || modelName.isBlank() || !apiKeyPresent) return false
    if (capability.requiresMediaTaskSelection() && !tasks.hasRelevantTaskFor(capability)) {
        return false
    }
    return true
}

internal fun mediaTaskDisplayLabel(task: MediaTask): String =
    when (task) {
        MediaTask.TEXT_TO_IMAGE -> "Text to Image"
        MediaTask.EDIT_IMAGE -> "Image Edit"
        MediaTask.TEXT_TO_VIDEO -> "Text to Video"
        MediaTask.IMAGE_TO_VIDEO -> "Image to Video"
    }

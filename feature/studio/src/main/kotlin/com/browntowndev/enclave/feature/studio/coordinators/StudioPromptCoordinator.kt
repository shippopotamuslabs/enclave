package com.browntowndev.enclave.feature.studio.coordinators

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.domain.model.media.StudioTemplate
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.domain.model.media.VisualGenerationSettings
import com.browntowndev.enclave.domain.model.media.withClampedGenerationCount
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.port.repository.ActiveStudioGeneration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class StudioPromptCoordinator
    @Inject
    constructor(
        private val generationCoordinator: StudioGenerationCoordinator,
        private val studioRepository: StudioRepositoryPort,
    ) {
        /**
         * Set once an active generation is restored so that [restoreLastMediaType] does not
         * clobber the resumed generation's media type with the persisted last selection.
         */
        private val restoredFromActiveGeneration = AtomicBoolean(false)

        val prompt = MutableStateFlow("")
        val mediaType = MutableStateFlow(MediaCapability.DEFAULT)
        val settings = MutableStateFlow<GenerationSettings>(ImageGenerationSettings())
        val selectedTemplateId = MutableStateFlow<String?>(null)
        val isSettingsOpen = MutableStateFlow(false)
        val continualMode = MutableStateFlow(false)
        val referenceMediaType = MutableStateFlow<MediaCapability?>(null)

        fun onPromptChange(newPrompt: String) {
            if (prompt.value == newPrompt) return
            prompt.value = newPrompt
            generationCoordinator.stopGeneration()
            generationCoordinator.clearScrollAnchors()
        }

        fun restoreActiveGeneration(generation: ActiveStudioGeneration) {
            restoredFromActiveGeneration.set(true)
            mediaType.value = generation.mediaType
            settings.value = generation.settings
        }

        fun onMediaTypeChange(type: MediaCapability) {
            val currentRefUri = (settings.value as? VisualGenerationSettings)?.referenceImageUri
            mediaType.value = type
            settings.value = defaultSettingsFor(type, currentRefUri)
            selectedTemplateId.value = null
            generationCoordinator.stopGeneration()
            generationCoordinator.resetVideoGenerationState()
            generationCoordinator.clearScrollAnchors()
        }

        /**
         * Restores the last user-selected media type on a fresh open. No-ops once an active
         * generation has been (or will be) restored, since resuming an in-flight generation
         * must keep its own media type and settings. Unlike [onMediaTypeChange] this does not
         * touch generation/scroll state (there is nothing to stop on cold start).
         */
        fun restoreLastMediaType(type: MediaCapability) {
            if (restoredFromActiveGeneration.get()) return
            if (mediaType.value == type) return
            mediaType.value = type
            settings.value = defaultSettingsFor(type, referenceImageUri = null)
        }

        private fun defaultSettingsFor(
            type: MediaCapability,
            referenceImageUri: String?,
        ): GenerationSettings =
            when (type) {
                MediaCapability.IMAGE -> ImageGenerationSettings(referenceImageUri = referenceImageUri)
                MediaCapability.VIDEO -> VideoGenerationSettings(referenceImageUri = referenceImageUri)
                MediaCapability.MUSIC -> MusicGenerationSettings()
            }

        fun onTemplateSelected(template: StudioTemplate) {
            selectedTemplateId.value = if (selectedTemplateId.value == template.id) null else template.id
        }

        fun onSettingsToggle() {
            isSettingsOpen.value = !isSettingsOpen.value
        }

        fun onContinualModeToggle(enabled: Boolean) {
            continualMode.value = enabled
            if (!enabled) {
                generationCoordinator.onContinualModeDisabled()
            }
        }

        fun onUpdateSettings(newSettings: GenerationSettings) {
            settings.value =
                when (newSettings) {
                    is ImageGenerationSettings -> newSettings.withClampedGenerationCount()
                    else -> newSettings
                }
            generationCoordinator.clearScrollAnchors()
        }

        fun onUpdateReferenceImage(uri: String?) {
            val currentSettings = settings.value
            if (uri == null) {
                referenceMediaType.value = null
            } else if (referenceMediaType.value == null) {
                referenceMediaType.value = MediaCapability.IMAGE
            }

            settings.value =
                when (currentSettings) {
                    is ImageGenerationSettings ->
                        currentSettings.copy(referenceImageUri = uri).withClampedGenerationCount()
                    is VideoGenerationSettings -> currentSettings.copy(referenceImageUri = uri)
                    is MusicGenerationSettings -> currentSettings
                }
        }

        fun clearSession(
            scope: CoroutineScope,
            albumSelectionCoordinator: StudioAlbumSelectionCoordinator,
        ) {
            prompt.value = ""
            generationCoordinator.clearLastSubmittedPrompt()
            albumSelectionCoordinator.clearSessionSavedAssets()
            generationCoordinator.resetGeneratingFlags()
            generationCoordinator.clearLocalError()
            scope.launch {
                studioRepository.clearActiveGeneration()
                studioRepository.clearGenerationError()
                studioRepository.clearEphemeralCache()
            }
        }
    }

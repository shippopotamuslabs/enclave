package com.browntowndev.enclave.feature.studio.coordinators

import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.StudioModelOverride
import com.browntowndev.enclave.domain.model.media.isEligibleFor
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.enclave.domain.usecase.media.ResolveMediaModelTypeUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResolvedStudioModel(
    val displayName: String?,
    val modelType: ModelType,
    val mediaProviderId: MediaProviderId?,
    val localConfigId: LocalModelConfigurationId?,
)

class StudioResolvedModelCoordinator
    @Inject
    constructor(
        private val resolveMediaModelTypeUseCase: ResolveMediaModelTypeUseCase,
        private val mediaProviderRepository: MediaProviderRepositoryPort,
    ) {
        private var cachedMediaProviders: List<com.browntowndev.enclave.domain.model.config.MediaProviderAsset> =
            emptyList()

        fun attachProviderCache(scope: CoroutineScope) {
            mediaProviderRepository
                .getMediaProviders()
                .onEach { providers -> cachedMediaProviders = providers }
                .launchIn(scope)
        }

        fun observeResolvedModel(
            overrideState: Flow<StudioOverrideCoordinatorState>,
            mediaType: Flow<MediaCapability>,
            settings: Flow<GenerationSettings>,
            defaults: Flow<List<DefaultModelAssignment>>,
        ): Flow<ResolvedStudioModel> =
            combine(overrideState, mediaType, settings, defaults) { state, type, generationSettings, assignments ->
                val modelType = resolveMediaModelTypeUseCase(type, generationSettings)
                val assignment = assignments.find { it.modelType == modelType }
                when (val override = state.committedOverride) {
                    is StudioModelOverride.MediaProvider ->
                        ResolvedStudioModel(
                            displayName = override.displayName,
                            modelType = modelType,
                            mediaProviderId = override.providerId,
                            localConfigId = null,
                        )
                    is StudioModelOverride.Local ->
                        ResolvedStudioModel(
                            displayName = override.displayName,
                            modelType = modelType,
                            mediaProviderId = null,
                            localConfigId = override.configId,
                        )
                    is StudioModelOverride.Api ->
                        ResolvedStudioModel(
                            displayName = override.displayName,
                            modelType = modelType,
                            mediaProviderId = null,
                            localConfigId = null,
                        )
                    null ->
                        ResolvedStudioModel(
                            displayName = assignment?.displayName,
                            modelType = modelType,
                            mediaProviderId = assignment?.mediaProviderId,
                            localConfigId = assignment?.localConfigId,
                        )
                }
            }.distinctUntilChanged()

        fun resolveModelType(
            mediaType: MediaCapability,
            settings: GenerationSettings,
        ): ModelType = resolveMediaModelTypeUseCase(mediaType, settings)

        fun revalidateOverrideIfModelTypeChanged(
            overrideCoordinator: StudioModelOverrideCoordinator,
            previousModelType: ModelType,
            mediaType: MediaCapability,
            settings: GenerationSettings,
            scope: CoroutineScope,
        ) {
            val newModelType = resolveModelType(mediaType, settings)
            if (previousModelType == newModelType) return
            val override = overrideCoordinator.state.value.committedOverride ?: return
            if (cachedMediaProviders.isNotEmpty()) {
                if (!override.isEligibleFor(newModelType, cachedMediaProviders)) {
                    overrideCoordinator.clearCommittedOverride()
                }
                return
            }
            scope.launch {
                val providers = mediaProviderRepository.getMediaProvidersSync()
                cachedMediaProviders = providers
                if (!override.isEligibleFor(newModelType, providers)) {
                    overrideCoordinator.clearCommittedOverride()
                }
            }
        }
    }

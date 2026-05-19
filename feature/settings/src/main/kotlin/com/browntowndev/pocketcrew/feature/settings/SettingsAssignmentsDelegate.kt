package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAssignmentUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class SettingsAssignmentsDelegate
    @Inject
    constructor(
        private val assignmentUseCases: SettingsAssignmentUseCases,
        private val errorHandler: ViewModelErrorHandler,
    ) {
        fun onShowAssignmentDialog(
            assignmentState: MutableStateFlow<AssignmentDialogTransientState>,
            show: Boolean,
            modelType: ModelType?,
        ) {
            assignmentState.update { it.copy(isOpen = show, editingSlot = modelType) }
        }

        fun onSetDefaultModel(
            scope: CoroutineScope,
            tag: String,
            assignmentState: MutableStateFlow<AssignmentDialogTransientState>,
            modelType: ModelType,
            localConfigId: LocalModelConfigurationId?,
            apiConfigId: ApiModelConfigurationId?,
            ttsProviderId: TtsProviderId? = null,
            mediaProviderId: MediaProviderId? = null,
        ) {
            scope.launch(
                errorHandler.coroutineExceptionHandler(
                    tag,
                    "Failed to set default model",
                    "Failed to update default model",
                ),
            ) {
                assignmentUseCases.setDefaultModel(
                    modelType = modelType,
                    localConfigId = localConfigId,
                    apiConfigId = apiConfigId,
                    ttsProviderId = ttsProviderId,
                    mediaProviderId = mediaProviderId,
                )
                onShowAssignmentDialog(assignmentState, false, null)
            }
        }
    }

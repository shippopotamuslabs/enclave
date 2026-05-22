package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.usecase.settings.ModelDeletionTarget
import com.browntowndev.enclave.domain.usecase.settings.PreparedModelDeletion
import com.browntowndev.enclave.domain.usecase.settings.SettingsDeletionUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class SettingsDeletionHelper
    @Inject
    constructor(
        private val deletionUseCases: SettingsDeletionUseCases,
        private val reassignmentOptionUiMapper: ReassignmentOptionUiMapper,
        private val errorHandler: ViewModelErrorHandler,
    ) {
        fun prepareDeletion(
            scope: CoroutineScope,
            deletionState: MutableStateFlow<DeletionTransientState>,
            target: ModelDeletionTarget,
            tag: String,
            failureMessage: String,
            userMessage: String,
            onImmediateSuccess: (() -> Unit)? = null,
        ) {
            scope.launch {
                when (val preparedDeletion = deletionUseCases.prepareModelDeletion(target)) {
                    PreparedModelDeletion.BlockedLastModel -> {
                        deletionState.update { it.copy(showLastModelAlert = true) }
                    }

                    is PreparedModelDeletion.Ready -> {
                        if (preparedDeletion.modelTypesNeedingReassignment.isEmpty()) {
                            scope.launch(errorHandler.coroutineExceptionHandler(tag, failureMessage, userMessage)) {
                                deletionUseCases
                                    .executeModelDeletionWithReassignment(
                                        target = preparedDeletion.target,
                                        modelTypesNeedingReassignment = emptyList(),
                                        replacementLocalConfigId = null,
                                        replacementApiConfigId = null,
                                    ).getOrThrow()
                                onImmediateSuccess?.invoke()
                            }
                        } else {
                            deletionState.value =
                                DeletionTransientState(
                                    pendingTarget = preparedDeletion.target.toUi(),
                                    modelTypesNeedingReassignment = preparedDeletion.modelTypesNeedingReassignment,
                                    reassignmentOptions = reassignmentOptionUiMapper.map(preparedDeletion.reassignmentCandidates),
                                )
                        }
                    }
                }
            }
        }

        fun onConfirmDeletionWithReassignment(
            scope: CoroutineScope,
            tag: String,
            deletionState: MutableStateFlow<DeletionTransientState>,
            replacementLocalConfigId: LocalModelConfigurationId?,
            replacementApiConfigId: ApiModelConfigurationId?,
        ) {
            val currentState = deletionState.value
            val pendingTarget = currentState.pendingTarget ?: return
            scope.launch(
                errorHandler.coroutineExceptionHandler(
                    tag,
                    "Failed to complete deletion with reassignment",
                    "Failed to delete",
                ),
            ) {
                deletionUseCases
                    .executeModelDeletionWithReassignment(
                        target = pendingTarget.toDomain(),
                        modelTypesNeedingReassignment = currentState.modelTypesNeedingReassignment,
                        replacementLocalConfigId = replacementLocalConfigId,
                        replacementApiConfigId = replacementApiConfigId,
                    ).getOrThrow()
                onDismissDeletionSafety(deletionState)
            }
        }

        fun onDismissDeletionSafety(deletionState: MutableStateFlow<DeletionTransientState>) {
            deletionState.value = DeletionTransientState()
        }

        private fun PendingDeletionTarget.toDomain(): ModelDeletionTarget =
            when (this) {
                is PendingDeletionTarget.LocalModelAsset -> ModelDeletionTarget.LocalModelAsset(id)
                is PendingDeletionTarget.LocalModelPreset -> ModelDeletionTarget.LocalModelPreset(id)
                is PendingDeletionTarget.ApiProvider -> ModelDeletionTarget.ApiProvider(id)
                is PendingDeletionTarget.ApiPreset -> ModelDeletionTarget.ApiPreset(id)
            }
    }

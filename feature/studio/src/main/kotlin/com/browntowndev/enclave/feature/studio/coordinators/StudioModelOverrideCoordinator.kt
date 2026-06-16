package com.browntowndev.enclave.feature.studio.coordinators

import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.StudioModelOverride
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ModelPickerState(
    val isVisible: Boolean = false,
    val modelType: ModelType? = null,
    val pendingSelection: StudioModelOverride? = null,
)

data class StudioOverrideCoordinatorState(
    val committedOverride: StudioModelOverride? = null,
    val picker: ModelPickerState = ModelPickerState(),
)

class StudioModelOverrideCoordinator
    @Inject
    constructor() {
        private val _state = MutableStateFlow(StudioOverrideCoordinatorState())
        val state: StateFlow<StudioOverrideCoordinatorState> = _state.asStateFlow()

        fun openPicker(
            modelType: ModelType,
            current: StudioModelOverride?,
        ) {
            _state.update { currentState ->
                currentState.copy(
                    picker =
                        ModelPickerState(
                            isVisible = true,
                            modelType = modelType,
                            pendingSelection = current,
                        ),
                )
            }
        }

        fun stageSelection(selection: StudioModelOverride) {
            _state.update { currentState ->
                currentState.copy(
                    picker = currentState.picker.copy(pendingSelection = selection),
                )
            }
        }

        fun confirmSelection() {
            _state.update { currentState ->
                val pending = currentState.picker.pendingSelection ?: return@update currentState
                currentState.copy(
                    committedOverride = pending,
                    picker = currentState.picker.copy(isVisible = false),
                )
            }
        }

        fun cancelPicker() {
            _state.update { currentState ->
                currentState.copy(
                    picker =
                        currentState.picker.copy(
                            isVisible = false,
                            pendingSelection = null,
                        ),
                )
            }
        }

        fun clearCommittedOverride() {
            _state.update { currentState ->
                currentState.copy(committedOverride = null)
            }
        }

        fun clearForCapabilityChange() {
            _state.value = StudioOverrideCoordinatorState()
        }

        fun clearSession() {
            _state.value = StudioOverrideCoordinatorState()
        }
    }

package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsDataControlsUseCases
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class SettingsDataControlsDelegate
    @Inject
    constructor(
        private val dataControlsUseCases: SettingsDataControlsUseCases,
        private val errorHandler: ViewModelErrorHandler,
    ) {
        fun onShowDataControlsSheet(
            sheetVisibility: MutableStateFlow<SheetVisibilityState>,
            dataControlsState: MutableStateFlow<DataControlsTransientState>,
            show: Boolean,
        ) {
            sheetVisibility.update { it.copy(dataControls = show) }
            if (!show) {
                dataControlsState.update { DataControlsTransientState() }
            }
        }

        fun onRequestDeleteAllChats(dataControlsState: MutableStateFlow<DataControlsTransientState>) {
            dataControlsState.update {
                it.copy(pendingAction = DataControlsPendingAction.DeleteChats, isDeleting = false)
            }
        }

        fun onRequestDeleteAllMedia(dataControlsState: MutableStateFlow<DataControlsTransientState>) {
            dataControlsState.update {
                it.copy(pendingAction = DataControlsPendingAction.DeleteMedia, isDeleting = false)
            }
        }

        fun onRequestDeleteAllMemories(dataControlsState: MutableStateFlow<DataControlsTransientState>) {
            dataControlsState.update {
                it.copy(pendingAction = DataControlsPendingAction.DeleteMemories, isDeleting = false)
            }
        }

        fun onRequestDeleteAllArtifacts(dataControlsState: MutableStateFlow<DataControlsTransientState>) {
            dataControlsState.update {
                it.copy(pendingAction = DataControlsPendingAction.DeleteArtifacts, isDeleting = false)
            }
        }

        fun onRequestDeleteAccount(dataControlsState: MutableStateFlow<DataControlsTransientState>) {
            dataControlsState.update {
                it.copy(pendingAction = DataControlsPendingAction.DeleteAccount, isDeleting = false)
            }
        }

        fun onDismissDataControlsConfirmation(dataControlsState: MutableStateFlow<DataControlsTransientState>) {
            dataControlsState.update { it.copy(pendingAction = null, isDeleting = false) }
        }

        private fun reportDeleteFailure(
            tag: String,
            devMessage: String,
            userMessage: String,
            throwable: Throwable,
            dataControlsState: MutableStateFlow<DataControlsTransientState>,
        ) {
            errorHandler.handleError(tag, devMessage, throwable, userMessage)
            dataControlsState.update {
                DataControlsTransientState(
                    deleteError = throwable.message ?: userMessage,
                    pendingAction = null,
                    isDeleting = false,
                )
            }
        }

        fun onConfirmDeleteAllChats(
            scope: CoroutineScope,
            tag: String,
            dataControlsState: MutableStateFlow<DataControlsTransientState>,
        ) {
            dataControlsState.update { it.copy(isDeleting = true, deleteError = null) }
            scope.launch {
                runCatching { dataControlsUseCases.deleteAllChats.invoke() }
                    .fold(
                        onSuccess = { dataControlsState.update { DataControlsTransientState() } },
                        onFailure = { e ->
                            reportDeleteFailure(
                                tag,
                                "Failed to delete all chats",
                                "Couldn't delete chats",
                                e,
                                dataControlsState,
                            )
                        },
                    )
            }
        }

        fun onConfirmDeleteAllMedia(
            scope: CoroutineScope,
            tag: String,
            dataControlsState: MutableStateFlow<DataControlsTransientState>,
        ) {
            dataControlsState.update { it.copy(isDeleting = true, deleteError = null) }
            scope.launch {
                runCatching { dataControlsUseCases.deleteAllMedia.invoke() }
                    .fold(
                        onSuccess = { dataControlsState.update { DataControlsTransientState() } },
                        onFailure = { e ->
                            reportDeleteFailure(
                                tag,
                                "Failed to delete all media",
                                "Couldn't delete media",
                                e,
                                dataControlsState,
                            )
                        },
                    )
            }
        }

        fun onConfirmDeleteAllMemories(
            scope: CoroutineScope,
            tag: String,
            dataControlsState: MutableStateFlow<DataControlsTransientState>,
        ) {
            dataControlsState.update { it.copy(isDeleting = true, deleteError = null) }
            scope.launch {
                runCatching { dataControlsUseCases.deleteAllMemories.invoke() }
                    .fold(
                        onSuccess = { dataControlsState.update { DataControlsTransientState() } },
                        onFailure = { e ->
                            reportDeleteFailure(
                                tag,
                                "Failed to delete all memories",
                                "Couldn't delete memories",
                                e,
                                dataControlsState,
                            )
                        },
                    )
            }
        }

        fun onConfirmDeleteAllArtifacts(
            scope: CoroutineScope,
            tag: String,
            dataControlsState: MutableStateFlow<DataControlsTransientState>,
        ) {
            dataControlsState.update { it.copy(isDeleting = true, deleteError = null) }
            scope.launch {
                runCatching { dataControlsUseCases.deleteAllArtifacts.invoke() }
                    .fold(
                        onSuccess = { dataControlsState.update { DataControlsTransientState() } },
                        onFailure = { e ->
                            reportDeleteFailure(
                                tag,
                                "Failed to delete all artifacts",
                                "Couldn't delete artifacts",
                                e,
                                dataControlsState,
                            )
                        },
                    )
            }
        }

        fun onConfirmDeleteAccount(
            scope: CoroutineScope,
            tag: String,
            dataControlsState: MutableStateFlow<DataControlsTransientState>,
        ) {
            dataControlsState.update { it.copy(isDeleting = true, deleteError = null) }
            scope.launch {
                val result = dataControlsUseCases.deleteAccount.invoke()
                val failure = result.exceptionOrNull()
                if (failure == null) {
                    dataControlsState.update { DataControlsTransientState() }
                } else {
                    reportDeleteFailure(
                        tag,
                        "Failed to delete account",
                        "Couldn't delete account",
                        failure,
                        dataControlsState,
                    )
                }
            }
        }
    }

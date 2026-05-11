package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsMemoriesUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsPreferencesUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class SettingsMemoriesDelegate @Inject constructor(
    private val preferencesUseCases: SettingsPreferencesUseCases,
    private val memoriesUseCases: SettingsMemoriesUseCases,
    private val errorHandler: ViewModelErrorHandler,
) {
    fun onShowDataControlsSheet(sheetVisibility: MutableStateFlow<SheetVisibilityState>, show: Boolean) {
        sheetVisibility.update { it.copy(dataControls = show) }
    }

    fun onShowMemoriesSheet(
        sheetVisibility: MutableStateFlow<SheetVisibilityState>,
        memoriesState: MutableStateFlow<MemoriesTransientState>,
        show: Boolean
    ) {
        sheetVisibility.update { it.copy(memories = show) }
        if (!show) {
            memoriesState.update { MemoriesTransientState() }
        }
    }

    fun onAddMemory(memoriesState: MutableStateFlow<MemoriesTransientState>) {
        memoriesState.update {
            it.copy(
                memoryDraft = StoredMemory(category = MemoryCategory.PREFERENCES),
                isEditing = true
            )
        }
    }

    fun onEditMemory(memoriesState: MutableStateFlow<MemoriesTransientState>, memory: StoredMemory) {
        memoriesState.update {
            it.copy(
                memoryDraft = memory,
                isEditing = true
            )
        }
    }

    fun onUpdateMemoryDraft(memoriesState: MutableStateFlow<MemoriesTransientState>, text: String, category: MemoryCategory) {
        memoriesState.update {
            it.copy(
                memoryDraft = it.memoryDraft?.copy(text = text, category = category)
                    ?: StoredMemory(text = text, category = category)
            )
        }
    }

    fun onSaveMemory(
        scope: CoroutineScope,
        tag: String,
        memoriesState: MutableStateFlow<MemoriesTransientState>
    ) {
        val draft = memoriesState.value.memoryDraft ?: return
        scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to save memory", "Failed to save memory")) {
            if (draft.id.isEmpty()) {
                memoriesUseCases.insertMemory(draft.category, draft.text)
            } else {
                memoriesUseCases.updateMemory(draft.id, draft.text, draft.category)
            }
            memoriesState.update { MemoriesTransientState() }
        }
    }

    fun onCancelMemoryEdit(memoriesState: MutableStateFlow<MemoriesTransientState>) {
        memoriesState.update { MemoriesTransientState() }
    }

    fun onDeleteMemory(scope: CoroutineScope, tag: String, memoryId: String) {
        scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to delete memory", "Failed to delete memory")) {
            memoriesUseCases.deleteMemory(memoryId)
        }
    }

    fun onAllowMemoriesChange(scope: CoroutineScope, tag: String, enabled: Boolean) {
        scope.launch(
            errorHandler.coroutineExceptionHandler(
                tag,
                "Failed to update memories setting",
                "Failed to update setting"
            )
        ) {
            preferencesUseCases.updateAllowMemories(enabled)
        }
    }

    fun onDeleteAllMemories(
        scope: CoroutineScope,
        tag: String,
        memories: List<StoredMemory>
    ) {
        scope.launch(
            errorHandler.coroutineExceptionHandler(
                tag,
                "Failed to delete all memories",
                "Failed to delete all memories"
            )
        ) {
            memories.forEach { 
                memoriesUseCases.deleteMemory(it.id)
            }
        }
    }
}

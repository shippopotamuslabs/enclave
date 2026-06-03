package com.browntowndev.enclave.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoModalBottomSheet
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSectionHeader
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetHeader
import com.browntowndev.enclave.core.ui.component.neo.neoBorderColor
import com.browntowndev.enclave.core.ui.component.neo.neoSurface
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.memory.MemoryCategory

private const val MEMORY_TEXT_FIELD_MIN_LINES = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesBottomSheet(
    uiState: SettingsUiState,
    onDismiss: () -> Unit,
    onAddMemory: () -> Unit,
    onEditMemory: (StoredMemory) -> Unit,
    onUpdateMemoryDraft: (String, MemoryCategory) -> Unit,
    onSaveMemory: () -> Unit,
    onCancelMemoryEdit: () -> Unit,
    onDeleteMemory: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var memoryIdToDelete by remember { mutableStateOf<String?>(null) }

    NeoModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        AnimatedContent(
            targetState = uiState.memories.isEditing,
            label = "MemoriesSheetTransition",
        ) { isEditing ->
            if (isEditing) {
                MemoryEditView(
                    draft = uiState.memories.memoryDraft ?: StoredMemory(),
                    onUpdateDraft = onUpdateMemoryDraft,
                    onSave = onSaveMemory,
                    onCancel = onCancelMemoryEdit,
                )
            } else {
                MemoriesListView(
                    memories = uiState.memories.memories,
                    onDismiss = onDismiss,
                    onAddMemory = onAddMemory,
                    onEditMemory = onEditMemory,
                    onDeleteMemory = { memoryIdToDelete = it },
                )
            }
        }

        memoryIdToDelete?.let { id ->
            AlertDialog(
                onDismissRequest = { memoryIdToDelete = null },
                title = { Text("Delete Memory?") },
                text = { Text("This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteMemory(id)
                            memoryIdToDelete = null
                        },
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { memoryIdToDelete = null }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun MemoriesListView(
    memories: List<StoredMemory>,
    onDismiss: () -> Unit,
    onAddMemory: () -> Unit,
    onEditMemory: (StoredMemory) -> Unit,
    onDeleteMemory: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        NeoSheetHeader(
            title = "Memories",
            subtitle = "Personalization and facts about you, your preferences, and projects.",
            onDismiss = onDismiss,
        )

        if (memories.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No memories yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val groupedMemories = memories.groupBy { it.category }
            val orderedCategories =
                listOf(MemoryCategory.PSYCH_PROFILE) +
                    MemoryCategory.entries.filter { it != MemoryCategory.PSYCH_PROFILE }
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.rowSpacing),
            ) {
                orderedCategories.forEach { category ->
                    val categoryMemories = groupedMemories[category]
                    if (!categoryMemories.isNullOrEmpty()) {
                        item(key = "header_${category.name}") {
                            NeoSectionHeader(text = category.displayName)
                        }
                        items(categoryMemories, key = { it.id }) { memory ->
                            MemoryItem(
                                memory = memory,
                                onEdit = { onEditMemory(memory) },
                                onDelete = { onDeleteMemory(memory.id) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        NeoPrimaryButton(
            text = "Add Memory",
            icon = Icons.Default.Add,
            onClick = onAddMemory,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryItem(
    memory: StoredMemory,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    onDelete()
                    false
                } else {
                    true
                }
            },
        )

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.clip(EnclaveNeoTokens.shape),
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .neoSurface(
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        borderColor = neoBorderColor(),
                        withShadow = false,
                    ).clickable(onClick = onEdit)
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = memory.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Edit memory",
                modifier = Modifier.padding(start = 4.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryEditView(
    draft: StoredMemory,
    onUpdateDraft: (String, MemoryCategory) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.sectionSpacing),
    ) {
        NeoSheetHeader(
            title = if (draft.id.isEmpty()) "Add Memory" else "Edit Memory",
            onDismiss = onCancel,
            onBackClick = onCancel,
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = draft.category.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                shape = EnclaveNeoTokens.shape,
                modifier =
                    Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                MemoryCategory.entries.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.displayName) },
                        onClick = {
                            onUpdateDraft(draft.text, category)
                            expanded = false
                        },
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            OutlinedTextField(
                value = draft.text,
                onValueChange = { onUpdateDraft(it, draft.category) },
                label = { Text("Memory") },
                placeholder = { Text("Enter memory text...") },
                shape = EnclaveNeoTokens.shape,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                minLines = MEMORY_TEXT_FIELD_MIN_LINES,
                maxLines = Int.MAX_VALUE,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.rowSpacing),
        ) {
            TextButton(
                onClick = onCancel,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(52.dp),
            ) {
                Text("Cancel")
            }
            NeoPrimaryButton(
                text = "Save",
                onClick = onSave,
                enabled = draft.text.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val MemoryCategory.displayName: String
    get() =
        when (this) {
            MemoryCategory.CORE_IDENTITY -> "Core Identity"
            MemoryCategory.PREFERENCES -> "Preferences"
            MemoryCategory.FACTS -> "Facts"
            MemoryCategory.PROJECT_CONTEXT -> "Project Context"
            MemoryCategory.PSYCH_PROFILE -> "Psychological Profile"
        }

@Preview(showBackground = true)
@Composable
fun PreviewMemoriesBottomSheet() {
    EnclaveTheme {
        MemoriesBottomSheet(
            uiState = MockSettingsData.baseUiState,
            onDismiss = {},
            onAddMemory = {},
            onEditMemory = {},
            onUpdateMemoryDraft = { _, _ -> },
            onSaveMemory = {},
            onCancelMemoryEdit = {},
            onDeleteMemory = {},
        )
    }
}

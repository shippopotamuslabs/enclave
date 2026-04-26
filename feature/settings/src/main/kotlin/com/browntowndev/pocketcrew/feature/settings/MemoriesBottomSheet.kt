package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.component.sheet.JumpFreeModalBottomSheet
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory

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

    JumpFreeModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            AnimatedContent(
                targetState = uiState.memories.isEditing,
                label = "MemoriesSheetTransition"
            ) { isEditing ->
                if (isEditing) {
                    MemoryEditView(
                        draft = uiState.memories.memoryDraft ?: StoredMemory(),
                        onUpdateDraft = onUpdateMemoryDraft,
                        onSave = onSaveMemory,
                        onCancel = onCancelMemoryEdit
                    )
                } else {
                    MemoriesListView(
                        memories = uiState.memories.memories,
                        onAddMemory = onAddMemory,
                        onEditMemory = onEditMemory,
                        onDeleteMemory = { memoryIdToDelete = it }
                    )
                }
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
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { memoryIdToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun MemoriesListView(
    memories: List<StoredMemory>,
    onAddMemory: () -> Unit,
    onEditMemory: (StoredMemory) -> Unit,
    onDeleteMemory: (String) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Memories",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onAddMemory) {
                Icon(Icons.Default.Add, contentDescription = "Add Memory")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Personalization and facts about you, your preferences, and projects.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (memories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No memories yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val groupedMemories = memories.groupBy { it.category }
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MemoryCategory.entries.forEach { category ->
                    val categoryMemories = groupedMemories[category]
                    if (!categoryMemories.isNullOrEmpty()) {
                        item(key = "header_${category.name}") {
                            CategoryHeader(category = category)
                        }
                        items(categoryMemories, key = { it.id }) { memory ->
                            MemoryItem(
                                memory = memory,
                                onEdit = { onEditMemory(memory) },
                                onDelete = { onDeleteMemory(memory.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: MemoryCategory) {
    Text(
        text = category.displayName,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun MemoryItem(
    memory: StoredMemory,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = memory.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
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
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (draft.id.isEmpty()) "Add Memory" else "Edit Memory",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = draft.category.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                MemoryCategory.entries.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.displayName) },
                        onClick = {
                            onUpdateDraft(draft.text, category)
                            expanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = draft.text,
            onValueChange = { onUpdateDraft(it, draft.category) },
            label = { Text("Memory") },
            placeholder = { Text("Enter memory text...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onSave,
                enabled = draft.text.isNotBlank(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save")
            }
        }
    }
}

private val MemoryCategory.displayName: String
    get() = when (this) {
        MemoryCategory.CORE_IDENTITY -> "Core Identity"
        MemoryCategory.PREFERENCES -> "Preferences"
        MemoryCategory.FACTS -> "Facts"
        MemoryCategory.PROJECT_CONTEXT -> "Project Context"
    }

@Preview(showBackground = true)
@Composable
fun PreviewMemoriesBottomSheet() {
    PocketCrewTheme {
        MemoriesBottomSheet(
            uiState = MockSettingsData.baseUiState,
            onDismiss = {},
            onAddMemory = {},
            onEditMemory = {},
            onUpdateMemoryDraft = { _, _ -> },
            onSaveMemory = {},
            onCancelMemoryEdit = {},
            onDeleteMemory = {}
        )
    }
}

# Refactor LocalModelConfigureScreen System Prompt to Bottom Sheet

## Objective
Update `LocalModelConfigureScreen.kt` to use a bottom sheet for editing the System Prompt, bringing it to parity with the `PresetConfigurationForm` in `ByokConfigureForms.kt` and fixing a reported UI regression.

## Key Files & Context
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/LocalModelConfigureScreen.kt`

## Implementation Steps

1. **Add Missing Imports**:
   - `androidx.compose.foundation.clickable`
   - `androidx.compose.foundation.layout.Box`
   - `androidx.compose.material3.OutlinedTextFieldDefaults`
   - `androidx.compose.material3.rememberModalBottomSheetState`
   - `androidx.compose.ui.text.input.KeyboardCapitalization`
   - `com.browntowndev.pocketcrew.core.ui.component.sheet.JumpFreeModalBottomSheet`

2. **Add Bottom Sheet State**:
   Inside `LocalModelConfigureScreen`, define the required state for controlling the modal bottom sheet:
   ```kotlin
   var showSystemPromptSheet by remember { mutableStateOf(false) }
   val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
   ```

3. **Update System Prompt Read-Only View**:
   Replace the existing inline `OutlinedTextField` and "Import Template" dropdown block with a read-only trigger field wrapped in a clickable `Box` that opens the bottom sheet when clicked (but only if it's not a system preset).
   ```kotlin
   Box(
       modifier = Modifier
           .fillMaxWidth()
           .then(
               if (!config.isSystemPreset) Modifier.clickable { showSystemPromptSheet = true }
               else Modifier
           )
   ) {
       OutlinedTextField(
           value = config.systemPrompt.ifBlank { "Tap to edit system prompt" },
           onValueChange = {},
           label = {
               Row(verticalAlignment = Alignment.CenterVertically) {
                   Text("System Prompt")
                   Spacer(modifier = Modifier.width(4.dp))
                   PersistentTooltip(
                       description = "Certain pipeline slots (like Crew Mode or Vision) require specialized system prompts to function correctly. Use the Import button to load a template."
                   )
               }
           },
           enabled = false,
           readOnly = true,
           modifier = Modifier.fillMaxWidth(),
           shape = RoundedCornerShape(12.dp),
           minLines = 4,
           maxLines = 4,
           colors = OutlinedTextFieldDefaults.colors(
               disabledTextColor = MaterialTheme.colorScheme.onSurface,
               disabledBorderColor = MaterialTheme.colorScheme.outline,
               disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
               disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
               disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
               disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
           )
       )
   }
   ```

4. **Implement the Bottom Sheet**:
   Add the `JumpFreeModalBottomSheet` layout directly beneath the `Box` trigger, holding the editable field and the "Import Template" functionality.
   ```kotlin
   if (showSystemPromptSheet) {
       JumpFreeModalBottomSheet(
           onDismissRequest = { showSystemPromptSheet = false },
           sheetState = sheetState
       ) {
           Column(
               modifier = Modifier
                   .fillMaxSize()
                   .padding(horizontal = 20.dp)
                   .padding(bottom = 32.dp)
           ) {
               Row(
                   modifier = Modifier.fillMaxWidth(),
                   horizontalArrangement = Arrangement.SpaceBetween,
                   verticalAlignment = Alignment.CenterVertically
               ) {
                   Text(
                       text = "Edit System Prompt",
                       style = MaterialTheme.typography.titleMedium,
                       fontWeight = FontWeight.Bold
                   )
                   
                   var expanded by remember { mutableStateOf(false) }
                   Row {
                       TextButton(onClick = { expanded = true }) {
                           Text("Import Template")
                       }
                       DropdownMenu(
                           expanded = expanded,
                           onDismissRequest = { expanded = false }
                       ) {
                           SystemPromptTemplates.getAll().forEach { (modelType, prompt) ->
                               DropdownMenuItem(
                                   text = { Text(modelType.displayName()) },
                                   onClick = {
                                       onConfigChange(config.copy(systemPrompt = prompt))
                                       expanded = false
                                   }
                               )
                           }
                       }
                   }
               }
               
               Spacer(modifier = Modifier.height(16.dp))
               
               OutlinedTextField(
                   value = config.systemPrompt,
                   onValueChange = { onConfigChange(config.copy(systemPrompt = it)) },
                   modifier = Modifier
                       .fillMaxWidth()
                       .weight(1f),
                   shape = RoundedCornerShape(12.dp),
                   placeholder = { Text("Enter system prompt...") },
                   keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
               )
               
               Spacer(modifier = Modifier.height(16.dp))
               
               Button(
                   onClick = { showSystemPromptSheet = false },
                   modifier = Modifier.fillMaxWidth(),
                   shape = RoundedCornerShape(12.dp)
               ) {
                   Text("Done", fontWeight = FontWeight.Bold)
               }
           }
       }
   }
   ```

## Verification
- Compile the app to ensure no broken references.
- Launch the app and visit the Local Models configure screen.
- Verify the system prompt preview correctly truncates large text via `maxLines=4`.
- Click the preview on a non-system preset and ensure the `JumpFreeModalBottomSheet` appears.
- Test typing a prompt inside the sheet.
- Test the "Import Template" dropdown within the sheet to confirm it updates the prompt correctly.
- Verify that system presets (`isSystemPreset = true`) block interaction with the Box, preventing the sheet from opening.

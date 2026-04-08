# Objective
Update the `ByokConfigureScreen` to use a Material 3 `ModalBottomSheet` for editing the System Prompt. This accommodates massive system prompts without bloating the main configuration form and pushing other settings off-screen.

# Key Files & Context
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokConfigureScreen.kt`

# Implementation Steps
1. **Update Imports:**
   - Add `ModalBottomSheet` and `rememberModalBottomSheetState` from `androidx.compose.material3`.

2. **Add Bottom Sheet State:**
   - In `PresetConfigurationForm`, add `var showSystemPromptSheet by remember { mutableStateOf(false) }`.
   - Add `val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)`.

3. **Modify Main Screen Entry Point:**
   - Replace the existing fixed-height `OutlinedTextField` for the System Prompt with a read-only, clickable representation (e.g. an `OutlinedTextField` with `enabled = false` inside a Box, or styled Surface) that displays a truncated preview (e.g. `maxLines = 2` with ellipses).
   - When clicked, set `showSystemPromptSheet = true`.

4. **Move "Import Template" to Bottom Sheet:**
   - Move the "Import Template" button and dropdown into the header of the `ModalBottomSheet` so all system prompt related actions are centralized.

5. **Implement ModalBottomSheet:**
   - Add the conditional rendering block:
     ```kotlin
     if (showSystemPromptSheet) {
         ModalBottomSheet(
             onDismissRequest = { showSystemPromptSheet = false },
             sheetState = sheetState
         ) {
             // header
             // import template logic
             // large OutlinedTextField for editing config.systemPrompt
             // Done button
         }
     }
     ```

# Verification & Testing
- Navigate to the BYOK Preset Configure screen.
- Verify the system prompt preview correctly truncates large text.
- Click the preview and ensure the `ModalBottomSheet` appears.
- Test typing a large prompt in the sheet.
- Test the "Import Template" dropdown within the sheet to ensure it overwrites the prompt.
- Close the sheet and verify the changes persist.
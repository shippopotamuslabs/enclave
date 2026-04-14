# Objective
Update LocalModelConfigureScreen to use a bottom sheet for editing the System Prompt, bringing it to parity with ByokConfigureForms.

# Implementation Steps
1. Add state variables showSystemPromptSheet and sheetState.
2. Replace inline OutlinedTextField with a clickable Box.
3. Move Import Template logic to JumpFreeModalBottomSheet.
4. Keep system presets read-only.

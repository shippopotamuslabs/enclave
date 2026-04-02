# UX Refinements (Wizard-Style Bottom Sheets)

## Objective
Implement UX refinements for the Settings screen and Model Configuration flows based on user feedback.

## Key Files & Context
- `SettingsScreen.kt`
- `ByokBottomSheet.kt`
- `LocalModelsBottomSheet.kt` (New)
- `ModelConfigurationScreen.kt` (Refactor to PipelineSlotsScreen)
- `ByokConfigureScreen.kt`
- `LocalModelConfigureScreen.kt` (New)
- `SettingsViewModel.kt`

## Implementation Steps
1. **Main Settings Screen**:
   - Add leading icons to all rows (`SettingsNavigationItem`, `SettingsToggle`).
   - Split the "Models" section into three entries: "Pipeline Assignments", "Local AI Models", and "External AI Providers".

2. **Wizard-Style Bottom Sheets**:
   - Create `LocalModelsBottomSheet` mimicking `ByokBottomSheet`.
   - Refactor both sheets to use a "Wizard" flow: click an asset to transition the sheet's content to a list of its configurations.

3. **Expanded Configuration Forms**:
   - Expand `ByokConfigureScreen` to include Top P, Top K, Frequency Penalty, Presence Penalty, System Prompt, and Custom Headers.
   - Create `LocalModelConfigureScreen` mirroring this expanded configuration approach.

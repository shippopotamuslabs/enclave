# Discovery: Model Type Tooltips

## 1. Goal Summary
Move model descriptions in the `ModelConfigurationScreen` from static inline text to persistent tooltips triggered by an "info" icon ('i') next to the model type name. This change improves visual clarity by reducing clutter while ensuring users can still access descriptive information about each pipeline slot.

## 2. Target Module Index

### Existing Data Models
- `ModelType`: Enum class in `ModelType.kt` representing logical model slots (MAIN, FAST, THINKING, etc.).
- `DefaultModelAssignmentUi`: Data class in `SettingsModels.kt` used for rendering model assignments in the settings UI.

### Dependencies & API Contracts
- `androidx.compose.material3`: Material 3 components including `TooltipBox`, `PlainTooltip`, `TooltipDefaults`, and `rememberTooltipState`.
- `androidx.compose.material.icons`: Material icons, specifically `Icons.Default.Info`.

### Utility/Shared Classes
- `ModelType.description`: Extension property in `SettingsModels.kt` that returns descriptive text for each `ModelType`.

### Impact Radius
- `ModelConfigurationScreen.kt`: The primary file to be modified. Specifically `DefaultAssignmentsCard` and its rendering of model titles and descriptions.
- `ByokConfigureScreen.kt`: Referenced as a style guide for tooltip behavior (persistent until dismissed).

## 3. Cross-Probe Analysis
### Overlaps Identified
- Both `ModelConfigurationScreen.kt` and `SettingsModels.kt` are central to this change as they define the UI and the data/descriptions respectively.

### Gaps & Uncertainties
- `ByokConfigureScreen.kt` was referenced but currently lacks tooltip implementation in the latest version, although historical diffs suggest past usage. I will need to implement the tooltip using standard M3 `TooltipBox` with a persistent state as requested.

### Conflicts (if any)
- None identified.

## 4. High-Impact Clarifying Questions
*None identified. Proceeding to Spec phase.*

## 5. Probe Coverage Summary
| Layer/Directory | Probe Agent | Key Findings |
|----------------|------------|-------------|
| feature/settings | Local Probe | `ModelConfigurationScreen.kt` currently displays descriptions inline. `SettingsModels.kt` provides the description strings. |
| core/domain | Local Probe | `ModelType` enum defines the available slots. |

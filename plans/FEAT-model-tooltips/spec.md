# Technical Specification: Model Type Tooltips

## 1. Objective
Replace inline model descriptions in the `ModelConfigurationScreen` with persistent tooltips. 
Acceptance Criteria:
- Model descriptions are removed from static layout in `DefaultAssignmentsCard`.
- An "info" icon (`Icons.Default.Info`) is added next to each model type name.
- Tapping the icon triggers a `TooltipBox` displaying the model's `ModelType.description`.
- Tooltips are persistent (`isPersistent = true`) until dismissed by tapping elsewhere.
- Styling matches the application's Material 3 theme.

## 2. System Architecture

### Target Files
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ModelConfigurationScreen.kt`: Modify `DefaultAssignmentsCard` to remove inline text and add `TooltipBox` logic.

### Component Boundaries
The change is localized to the UI layer within the `feature:settings` module. It reuses the `ModelType.description` utility extension from `SettingsModels.kt`.

## 3. Data Models & Schemas
- Reused `ModelType` (from `core:domain`)
- Reused `DefaultModelAssignmentUi` (from `feature:settings`)

## 4. API Contracts & Interfaces
No API contract changes.

## 5. Permissions & Config Delta
No permissions or config changes.

## 6. Constitution Audit
This design adheres to the project's core architectural rules by keeping UI logic contained within the Compose layer and reusing existing domain models and extensions. It follows standard Material 3 patterns for tooltips.

## 7. Cross-Spec Dependencies
No cross-spec dependencies.

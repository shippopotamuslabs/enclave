# Discovery: Fix ModelDownloadScreen UI overlap on Samsung devices

## 1. Goal Summary
The "Pause" button in `ModelDownloadScreen.kt` overlaps with the system navigation bar on Samsung SM-G998U devices. This is caused by `enableEdgeToEdge()` being active in `MainActivity` while the `Scaffold`'s `bottomBar` does not account for the navigation bar insets. The goal is to ensure the download controls are properly padded above the system navigation bar.

## 2. Target Module Index
Unified view of existing code analyzed across the UI Layer probe.

### Existing Data Models
- `com.browntowndev.pocketcrew.feature.download.DownloadViewModel.FileProgressUiModel`: UI model for displaying file progress.

### Dependencies & API Contracts
- `androidx.compose.material3.Scaffold`: Used as the main container for the download screen.
- `androidx.compose.material3.Button`: Used for actions like "Pause", "Resume", etc.
- `com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort`: Backend service for download logic.

### Utility/Shared Classes
- `com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme`: App-wide theme.
- `com.browntowndev.pocketcrew.core.ui.util.FeatureFlags`: Feature gating.

### Impact Radius
- `ModelDownloadScreen.kt`: This is the primary file to be modified. The `DownloadControls` composable and its usage within the `Scaffold`'s `bottomBar` are the target areas.

## 3. Cross-Probe Analysis
### Overlaps Identified
- None (single probe for the UI layer).

### Gaps & Uncertainties
- Whether other screens in the app also suffer from this issue. `MainActivity` sets `enableEdgeToEdge()`, so this might be a widespread issue if not handled in other `Scaffold` usages.

### Conflicts (if any)
- None.

## 4. High-Impact Clarifying Questions
*None identified. Proceeding to Spec phase.*

## 5. Probe Coverage Summary
| Layer/Directory | Probe Agent | Key Findings |
|----------------|------------|-------------|
| feature/download | UI Layer Probe | Identified `DownloadControls` in `ModelDownloadScreen.kt` as the cause of the overlap. |

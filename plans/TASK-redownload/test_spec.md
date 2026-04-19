# Test Specification: TASK-redownload
## Local Model Re-download Mechanism

### Background
Users can re-download soft-deleted local models from the settings bottom sheet. The re-download process must correctly restore all system presets and show progress in the UI.

### Scenarios

#### Scenario 1: Re-downloading a soft-deleted model
**Given** a local model "Llama 3 8B" is soft-deleted (metadata exists, but 0 configurations)
**When** the user opens the Local Models bottom sheet
**Then** "Llama 3 8B" should appear in the "AVAILABLE FOR DOWNLOAD" section
**And** it should display a download icon instead of a chevron

#### Scenario 2: Starting a re-download
**Given** a soft-deleted model "Llama 3 8B" is visible in the bottom sheet
**When** the user taps the download icon for "Llama 3 8B"
**Then** the `ModelReDownloadViewModel` should match the model by SHA256 against remote config
**And** it should call `restoreSoftDeletedModel` with all matching system presets
**And** it should enqueue a download work request with `ModelType.UNASSIGNED`
**And** the UI should show a circular progress bar around the download icon

#### Scenario 3: Download progress observation
**Given** a re-download is in progress for "Llama 3 8B"
**When** the download worker updates progress (e.g., 50%)
**Then** the `ModelReDownloadViewModel` should emit the updated progress
**And** the circular progress bar in the UI should reflect the 50% completion

#### Scenario 4: Successful download completion
**Given** a re-download for "Llama 3 8B" reaches 100%
**When** the WorkManager task completes successfully
**Then** the UI should display a completion check icon (or similar)
**And** "Llama 3 8B" should appear in the "Downloaded" section upon the next sheet open

#### Scenario 5: Re-download failure
**Given** a re-download for "Llama 3 8B" starts
**When** the download fails (e.g., network error)
**Then** the `ModelReDownloadViewModel` should transition to a failed state
**And** the UI should show a retry/error indicator on the download icon

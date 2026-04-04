# Test Specification: Fix ModelDownloadScreen UI overlap on Samsung devices

## 1. Happy Path Scenarios
These scenarios focus on ensuring the "Pause" button is no longer overlapping with the navigation bar.

### Scenario: Download controls visible above navigation bar
- **Given:** App is in edge-to-edge mode, showing `ModelDownloadScreen` on a device with a software navigation bar (e.g., Samsung SM-G998U).
- **When:** `ModelDownloadScreen` is displayed.
- **Then:** The `DownloadControls` (containing the "Pause" button) should be positioned strictly above the `android:id/navigationBarBackground` with no overlap.

## 2. Error Path & Edge Case Scenarios

### Scenario: Controls remain visible on orientation change
- **Given:** `ModelDownloadScreen` is displayed.
- **When:** Device orientation is rotated to landscape.
- **Then:** The `DownloadControls` should still be visible and not overlapping with the navigation bar or cut off.

## 3. Mutation Defense
### Lazy Implementation Risk
A lazy implementation might add fixed bottom padding (e.g., 50.dp) to the `DownloadControls` or `Scaffold`.

### Defense Scenario
- **Given:** App is running on a device with gesture navigation (which has a much smaller inset than button navigation).
- **When:** `ModelDownloadScreen` is displayed.
- **Then:** The `DownloadControls` should NOT have excessive whitespace at the bottom. It should dynamically respect the `WindowInsets.navigationBars`.

# Test Specification: Model Type Tooltips

## 1. Happy Path Scenarios

### Scenario: Show Tooltip on Info Icon Tap
- **Given:** The `ModelConfigurationScreen` is displayed with model assignments.
- **When:** The user taps the 'i' info icon next to the "Fast" model type.
- **Then:** A tooltip appears with the text "A lightweight, efficient model for quick, non-reasoning responses."

### Scenario: Tooltip Persistence
- **Given:** A model description tooltip is currently visible.
- **When:** 5 seconds pass without user interaction.
- **Then:** The tooltip remains visible (persistent).

### Scenario: Dismiss Tooltip on Outside Tap
- **Given:** A model description tooltip is currently visible.
- **When:** The user taps on a different part of the screen (not the tooltip or its anchor).
- **Then:** The tooltip is dismissed and no longer visible.

## 2. Error Path & Edge Case Scenarios

### Scenario: Multiple Tooltips
- **Given:** The tooltip for "Fast" is visible.
- **When:** The user taps the 'i' info icon for "Thinking".
- **Then:** The "Fast" tooltip is dismissed and the "Thinking" tooltip appears with "A reasoning model with extended context for complex tasks."

## 3. Mutation Defense
### Lazy Implementation Risk
A lazy implementation might use a non-persistent tooltip that disappears after a few seconds, violating the "persistent" requirement.

### Defense Scenario
- **Given:** The info icon for "Synthesis" has been tapped.
- **When:** The user waits for 10 seconds.
- **Then:** The tooltip must still be visible on the screen.

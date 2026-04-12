# UX Refinements for Vision Settings and Image Previews

## Objective
Refine the user experience across two main areas:
1. **Chat Screen Image Previews**: Move the image attachment preview out of the user's message bubble, render it above the bubble at a smaller size, and introduce a full-screen dismissible preview.
2. **Settings Screen**: Reorganize tool-related settings into a dedicated "Tools" section and consolidate the Vision model configuration into a new drill-down Bottom Sheet.

## Key Files & Context
- `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/components/MessageBubble.kt`
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsScreen.kt`
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ModelConfigurationScreen.kt`
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsModels.kt`

## Implementation Steps

### 1. Chat Screen (MessageBubble.kt) Improvements
- **Reposition Image Preview**:
  - Locate the `AsyncImage` rendering the user's attached image inside the message bubble's `Surface`.
  - Move this `AsyncImage` to the outer `Column` (which aligns content to `Alignment.End`), placing it *above* the `Surface`.
- **Resize and Style**:
  - Update the `Modifier` to make the image ~50% of its original size. Since the bubble used `fillMaxWidth(fraction = .75f)` with `heightIn(max = 240.dp)`, we will apply `fillMaxWidth(fraction = 0.375f)` or `heightIn(max = 120.dp)` with appropriate rounded corners (`clip(RoundedCornerShape(12.dp))`).
  - Add a `clickable` modifier to the image to toggle a new `showFullscreenPreview` boolean state.
- **Full-Screen Preview**:
  - Implement a `Dialog` (with `DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)`) that renders conditionally when `showFullscreenPreview` is true.
  - The `Dialog` content will be a `Box` that fills the maximum size with a translucent/blurred background (e.g., `Color.Black.copy(alpha = 0.8f)`).
  - Inside the `Box`, render the `AsyncImage` with `ContentScale.Fit` to fill the screen without cropping.
  - Add an `IconButton` with a white 'X' icon pinned to `Alignment.TopEnd` to dismiss the preview.

### 2. Settings Screen (SettingsScreen.kt) Reorganization
- **New Tools Section**:
  - Add a new `SectionHeader(text = "Tools")` below the "Models" section.
- **Relocate Web Search**:
  - Move the existing "Web Search" `SettingsNavigationItem` from "Models" to the new "Tools" section.
- **Relocate Vision Settings**:
  - Remove the inline "Always Use Vision Model" `SettingsToggle` from the main settings list.
  - Add a new `SettingsNavigationItem` under "Tools" with the title "Vision" and the subtitle "A dedicated API vision model that acts as the chat's eyes for image inspection." (reusing the existing description for `ModelType.VISION`).
  - Set the `onClick` handler of this item to open a new state: `isVisionSettingsSheetOpen`.
- **Update Model Role Assignments**:
  - In `ModelConfigurationScreen.kt`, remove `ModelType.VISION` from the `generalChatTypes` list so it no longer appears in the standalone role assignments screen.

### 3. Vision Settings Bottom Sheet
- **Create `VisionSettingsBottomSheet`**:
  - Implement a new bottom sheet composable to be shown when `isVisionSettingsSheetOpen` is true.
  - **Content Layout**:
    - **Header**: "Vision Settings" title.
    - **Toggle**: The "Always Use API Vision Model" `Switch`, retaining its tooltip/subtitle description about overriding other models.
    - **Divider**: A `HorizontalDivider` to separate the toggle from the drill-down flow.
    - **Drill-down Trigger**: A row styled like a `SettingsNavigationItem` with rounded corners, matching the background of "Model Role Assignments". It will be titled "API Vision Model" and display the name of the currently assigned vision model.
    - **Drill-down Flow**: Tapping the "API Vision Model" row will use `AnimatedContent` to swap the view to the `AssignmentSelectionContent` (reused from `ModelConfigurationScreen.kt`). This will allow the user to see the list of configured vision models and tap one to see/select its presets, maintaining identical behavior to the standard model assignment flow.

## Verification & Testing
- **Chat Screen**: Verify that sending an image renders the image *above* the message bubble and smaller than before. Tap the image and verify that the full-screen dialog opens, displays the image scaled to fit, has a dark/translucent background, and can be dismissed via the 'X' button or system back button.
- **Settings Screen**: Verify that "Web Search" and "Vision" are now under the "Tools" section. Verify that "Always Use Vision Model" is no longer in the main list and "Vision" is no longer in the "Model Role Assignments" screen.
- **Vision Bottom Sheet**: Verify that tapping "Vision" opens the new bottom sheet. Verify the toggle works and updates preferences. Verify that tapping the "API Vision Model" row smoothly transitions to the model selection list and functions correctly to assign a new model.
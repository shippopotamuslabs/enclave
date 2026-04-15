# Discovery: UI-BANNER-FIX-01

## 1. Goal Summary
Improve the `ToolCallBanner` experience in the chat interface by fixing the broken exit animation and eliminating layout jitter during state transitions. This will ensure the banner slides out smoothly and the message list remains stable when tool calls are active.

## 2. Target Module Index

### Existing Data Models
- `com.browntowndev.pocketcrew.feature.chat.ToolCallBannerUi`: Data class containing kind (SEARCH/IMAGE) and label.
- `com.browntowndev.pocketcrew.feature.chat.ChatMessage`: Domain model for messages, containing indicator state.
- `com.browntowndev.pocketcrew.feature.chat.IndicatorState`: Sealed class representing the current processing state of a message.

### Dependencies & API Contracts
- `androidx.compose.animation`: Core animation library for `AnimatedVisibility`, `fadeIn`, `fadeOut`, `slideInVertically`, and `slideOutVertically`.
- `androidx.compose.foundation.lazy`: Used for `LazyColumn` and `LazyListState` in `MessageList.kt`.

### Utility/Shared Classes
- `ToolCallBanner.kt`: Contains the `ToolCallBanner` composable.
- `MessageList.kt`: Contains the `MessageList` composable which hosts the banner.

### Impact Radius
- `MessageList.kt`: Needs modification to ensure `ToolCallBanner` remains in composition while `AnimatedVisibility` handles its exit. The current `if` check prevents exit animations.
- `ToolCallBanner.kt`: Needs modification to address layout jitter. This involves ensuring a consistent height or using `Modifier.animateContentSize()` to prevent abrupt layout shifts in the `LazyColumn`.

## 3. Cross-Probe Analysis
### Overlaps Identified
- `ToolCallBannerUi` and `IndicatorState` are the primary state drivers across both files.

### Gaps & Uncertainties
- **Banner Height**: Should the banner have a fixed height to simplify space reservation, or should we dynamically measure and animate it?
- **Spacing**: The banner has a `Modifier.padding(bottom = 8.dp)` in `MessageList.kt`. This padding also disappears instantly when the banner is removed, contributing to the jitter.

### Conflicts (if any)
- None identified.

## 4. High-Impact Clarifying Questions
*None identified. Proceeding to Spec phase.*

## 5. Probe Coverage Summary
| Layer/Directory | Probe Agent | Key Findings |
|----------------|------------|-------------|
| feature/chat/components | UI Components Probe | Identified conditional composition as the cause of exit animation failure and confirmed layout jitter sources. |

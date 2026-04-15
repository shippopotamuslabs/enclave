# Test Specification: ToolCallBanner Animation and Stability

## 1. Happy Path Scenarios

### Scenario: Banner Exit Animation Runs
- **Given:** A message with an active `ToolCallBannerUi`.
- **When:** The message is no longer the `activeIndicatorMessageId` or `activeToolCallBanner` becomes null.
- **Then:** The `ToolCallBanner` should slide out (vertically) instead of disappearing instantly.

### Scenario: Layout Stability During Transition
- **Given:** A message list with an active tool call banner.
- **When:** The banner appears or disappears.
- **Then:** The items in the `LazyColumn` should not abruptly jump or change their relative positions, ensuring a smooth transition.

## 2. Error Path & Edge Case Scenarios

### Scenario: Rapid Toggle of Tool Call State
- **Given:** The tool call state is toggled rapidly (e.g., from search to null and back).
- **When:** The state changes mid-animation.
- **Then:** `AnimatedVisibility` should handle the interruption gracefully, reversing the animation without visual glitches or layout jumps.

## 3. Mutation Defense
### Lazy Implementation Risk
A lazy implementation might fix the exit animation by just keeping the banner in composition but fail to fix the layout jitter by not accounting for the padding or the size change during animation.

### Defense Scenario
- **Given:** A message with `activeIndicatorMessageId` matching and `activeToolCallBanner` set to null (initial state: hidden).
- **When:** `activeToolCallBanner` is set to a valid value.
- **Then:** The total height of the message item should change smoothly via `Modifier.animateContentSize()` or remain stable if a reserved space strategy is used, and the bottom padding of the banner should also be animated or correctly handled.

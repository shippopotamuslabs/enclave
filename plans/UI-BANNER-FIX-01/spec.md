# Technical Specification: ToolCallBanner Animation and Stability Improvements

## 1. Objective
Improve the `ToolCallBanner` experience by:
- Ensuring the exit animation (slide-out) works correctly when the banner is hidden.
- Eliminating layout jitter in the `LazyColumn` during banner appearance and disappearance.
- Maintaining a stable message list position while the banner is animating.

## 2. System Architecture

### Target Files
- `/home/sean/Code/pocket-crew/feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/components/MessageList.kt`
- `/home/sean/Code/pocket-crew/feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/components/ToolCallBanner.kt`

### Component Boundaries
- `MessageList`: Will always compose `ToolCallBanner` for the active message instead of using a conditional `if` block. This allows `AnimatedVisibility` inside `ToolCallBanner` to manage its own lifecycle and exit animations.
- `ToolCallBanner`: Will use `Modifier.animateContentSize()` or a reserved space approach to stabilize the layout. Given the user's suggestion and the need for absolute stability in a `LazyColumn`, we will implement a "reserved space" pattern where the banner container maintains its height even when the content is hidden, or smoothly animates it.

## 3. Data Models & Schemas
- Reuses `com.browntowndev.pocketcrew.feature.chat.ToolCallBannerUi`.
- Reuses `com.browntowndev.pocketcrew.feature.chat.IndicatorState`.

## 4. API Contracts & Interfaces
- No API contract changes.

## 5. Permissions & Config Delta
- No permissions or config changes.

## 6. Constitution Audit
- This design adheres to the project's core architectural rules (AGENTS.md).
- Follows Compose best practices for animations and layout stability.
- UI logic is kept within the presentation layer.

## 7. Cross-Spec Dependencies
- No cross-spec dependencies.

## 2024-05-24 - Accessibility for Interactive Surfaces
**Learning:** Custom interactive surfaces in Jetpack Compose used for toggling visibility (like showing/hiding message actions) often lack semantic meaning for screen readers. Using just `Modifier.clickable` without a label leaves TalkBack users unaware of the action's purpose or current state.
**Action:** Always provide an `onClickLabel` inside `Modifier.clickable` that dynamically updates based on the state (e.g., "Show message actions" vs "Hide message actions") to give screen reader users clear context about what the interaction does.

## 2025-02-12 - Missing remember in Jetpack Compose
**Learning:** Found an un-remembered list computation (`messages.any { ... }`) inside `MessageList.kt` which caused it to be re-evaluated on every single recomposition of the message list. This can become a performance bottleneck when the list of messages grows, especially during message generation when recomposition happens frequently.
**Action:** Always wrap derived state computations or heavy collection operations in `remember` (e.g. `remember(messages) { messages.any { ... } }`) in Jetpack Compose to prevent unnecessary work on recomposition.

## 2026-03-31 - Eager computation for data class properties
**Learning:** In Jetpack Compose, computed property getters (`get() = ...`) in UI state data classes (like `ChatUiState`) are re-evaluated on *every* access during recomposition. If they contain O(N) operations (e.g., `messages.any { ... }`), this becomes a severe performance bottleneck. Attempting to memoize the getter read via `remember` keyed on the list introduces bugs because the state won't update if the property value changes without the list instance changing.
**Action:** Compute derived state eagerly at object instantiation (`val prop = ...`) in data classes to ensure O(1) reads, paying the O(N) cost only once when the data class instance is created via `.copy()`.

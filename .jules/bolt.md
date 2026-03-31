## 2025-02-12 - Missing remember in Jetpack Compose
**Learning:** Found an un-remembered list computation (`messages.any { ... }`) inside `MessageList.kt` which caused it to be re-evaluated on every single recomposition of the message list. This can become a performance bottleneck when the list of messages grows, especially during message generation when recomposition happens frequently.
**Action:** Always wrap derived state computations or heavy collection operations in `remember` (e.g. `remember(messages) { messages.any { ... } }`) in Jetpack Compose to prevent unnecessary work on recomposition.

### Performance: Regex Compilation
- Compiling regular expressions inside a composable or standard function that executes frequently leads to repeated object creation and pattern compilation.
- **Rule:** Lift `Regex` definitions out of frequently called scopes (e.g. `parseSimpleMarkdown`) to a top-level property or a companion object, compiling them only once.
- **Impact:** Reduces execution time by around ~4-5% in simple markdown tests simulating 100,000 runs, optimizing CPU and memory usage during text parsing.

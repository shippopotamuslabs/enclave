## 2025-02-12 - Missing remember in Jetpack Compose
**Learning:** Found an un-remembered list computation (`messages.any { ... }`) inside `MessageList.kt` which caused it to be re-evaluated on every single recomposition of the message list. This can become a performance bottleneck when the list of messages grows, especially during message generation when recomposition happens frequently.
**Action:** Always wrap derived state computations or heavy collection operations in `remember` (e.g. `remember(messages) { messages.any { ... } }`) in Jetpack Compose to prevent unnecessary work on recomposition.
- Added  to ensure  has basic test coverage and catches real bugs. Tests check if changing themes actually delegates the action to settings repository correctly.

- Added `UpdateThemeUseCaseTest.kt` to ensure `UpdateThemeUseCase` has basic test coverage and catches real bugs. Tests check if changing themes actually delegates the action to settings repository correctly.

- Updated `.github/workflows/codeql.yml` to set Node 24 for CodeQL Github action due to Github deprecating Node 20. Replaced action versions `v3` to `v4` and added `build-mode: 'manual'` configuration. Altered `assembleDebug` gradle command.

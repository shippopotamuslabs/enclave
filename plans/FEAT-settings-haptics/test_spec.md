# Test Spec: Settings Haptics

## Test Framework & Run Command

Frameworks:
- JUnit 5 for JVM tests.
- Turbine for `StateFlow` / `Flow` assertions where state transitions are observed over time.
- MockK for collaborators that do not have practical fakes.
- Robolectric Compose UI tests for `SettingsScreen` and `StreamingResponseHapticEffect`.

Targeted red/green command:
- `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew :core:domain:testDebugUnitTest :core:data:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:chat:testDebugUnitTest --no-daemon --stacktrace`

Completion gate for the implementation executor:
- `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew test ktlintCheck detekt :app:assembleDebug :core:domain:assemble :core:data:assemble --no-daemon --stacktrace`

## Scenario Index

- `S1` Settings screen removes `Haptic Press` and keeps `Haptic Response`
- `S2` Settings ViewModel still persists `Haptic Response`
- `S3` `ChatUiState.responseHapticPhase` is `None` for `ENGINE_LOADING`
- `S4` `ChatUiState.responseHapticPhase` is `None` for `PROCESSING`
- `S5` `ChatUiState.responseHapticPhase` is `ThinkingPulse` for `THINKING`
- `S6` `ChatUiState.responseHapticPhase` is `GeneratingBuzz` for `GENERATING`
- `S7` thinking haptics start a heartbeat pulse exactly once when enabled thinking begins
- `S8` haptics switch from heartbeat pulse to rapid buzz when generation begins
- `S9` streaming haptics stop when streaming ends
- `E1` streaming haptics never start when the preference is `false`
- `E2` streaming haptics stop when the composable leaves composition mid-stream
- `M1` unrelated recomposition does not restart active haptics
- `M2` `PROCESSING -> THINKING -> GENERATING` starts haptics only at `THINKING` and switches only at `GENERATING`

## Happy Path Scenarios

### S1: Settings screen removes `Haptic Press` and keeps `Haptic Response`

- Given: `SettingsScreen` is rendered with `uiState.home.hapticResponse = true`
- When: the Experience section is composed
- Then: no node with text exactly `Haptic Press` exists
- Then: one node with text exactly `Haptic Response` exists
- Then: the `Haptic Response` toggle is displayed with checked state `true`

### S2: Settings ViewModel still persists `Haptic Response`

- Given: `SettingsViewModel` is created with the existing settings test wiring and `updateHapticResponseUseCase`
- When: `viewModel.onHapticResponseChange(false)` is invoked
- Then: `updateHapticResponseUseCase(false)` is called exactly `1` time
- Then: there is no remaining `onHapticPressChange` production API to invoke or verify

### S3: `ChatUiState.responseHapticPhase` is `None` for `ENGINE_LOADING`

- Given: the active assistant message is in `MessageState.ENGINE_LOADING`
- When: `ChatViewModel.uiState` is collected
- Then: `uiState.value.responseHapticPhase == ResponseHapticPhase.None`

### S4: `ChatUiState.responseHapticPhase` is `None` for `PROCESSING`

- Given: the active assistant message is in `MessageState.PROCESSING`
- When: `ChatViewModel.uiState` is collected
- Then: `uiState.value.responseHapticPhase == ResponseHapticPhase.None`

### S5: `ChatUiState.responseHapticPhase` is `ThinkingPulse` for `THINKING`

- Given: the active assistant message is in `MessageState.THINKING`
- When: `ChatViewModel.uiState` is collected
- Then: `uiState.value.responseHapticPhase == ResponseHapticPhase.ThinkingPulse`
- Then: `uiState.value.hapticResponse` preserves the persisted boolean from `SettingsData`

### S6: `ChatUiState.responseHapticPhase` is `GeneratingBuzz` for `GENERATING`

- Given: the active assistant message is in `MessageState.GENERATING`
- When: `ChatViewModel.uiState` is collected
- Then: `uiState.value.responseHapticPhase == ResponseHapticPhase.GeneratingBuzz`

### S7: thinking haptics start a heartbeat pulse exactly once when enabled thinking begins

- Given: `StreamingResponseHapticEffect` is composed with a fake controller, `hapticsEnabled = true`, and `phase = ResponseHapticPhase.None`
- When: the composable is recomposed with `phase = ResponseHapticPhase.ThinkingPulse`
- Then: `fakeController.startThinkingPulseCount == 1`
- Then: `fakeController.startGeneratingBuzzCount == 0`
- Then: `fakeController.stopCount == 0`
- Then: `fakeController.activePattern == "thinking-pulse"`

### S8: haptics switch from heartbeat pulse to rapid buzz when generation begins

- Given: `StreamingResponseHapticEffect` is composed with a fake controller, `hapticsEnabled = true`, and `phase = ResponseHapticPhase.ThinkingPulse`
- When: the composable is recomposed with `phase = ResponseHapticPhase.GeneratingBuzz`
- Then: `fakeController.startThinkingPulseCount == 1`
- Then: `fakeController.startGeneratingBuzzCount == 1`
- Then: `fakeController.stopCount == 0`
- Then: `fakeController.activePattern == "generating-buzz"`

### S9: streaming haptics stop when streaming ends

- Given: `StreamingResponseHapticEffect` is composed with a fake controller, `hapticsEnabled = true`, and `phase = ResponseHapticPhase.GeneratingBuzz`
- When: the composable is recomposed with `phase = ResponseHapticPhase.None`
- Then: `fakeController.startThinkingPulseCount == 0`
- Then: `fakeController.startGeneratingBuzzCount == 1`
- Then: `fakeController.stopCount == 1`
- Then: `fakeController.activePattern == "none"`

## Error & Edge Case Scenarios

### E1: streaming haptics never start when the preference is `false`

- Given: `StreamingResponseHapticEffect` is composed with a fake controller, `hapticsEnabled = false`, and `phase = ResponseHapticPhase.GeneratingBuzz`
- When: the first composition completes
- Then: `fakeController.startThinkingPulseCount == 0`
- Then: `fakeController.startGeneratingBuzzCount == 0`
- Then: `fakeController.stopCount == 0`
- Then: `fakeController.activePattern == "none"`

### E2: streaming haptics stop when the composable leaves composition mid-stream

- Given: `StreamingResponseHapticEffect` is composed with a fake controller, `hapticsEnabled = true`, and `phase = ResponseHapticPhase.ThinkingPulse`
- When: the host content removes the effect from composition without first setting `phase = ResponseHapticPhase.None`
- Then: `fakeController.stopCount == 1`
- Then: `fakeController.activePattern == "none"`

## Mutation Defense

### Lazy Implementation Risk

The most likely shortcut is to collapse `THINKING` and `GENERATING` into one generic active boolean or to key the haptic effect to broad screen recomposition, which would erase the heartbeat-vs-buzz distinction or restart haptics on every unrelated state change.

### M1: unrelated recomposition does not restart active haptics

- Given: `StreamingResponseHapticEffect` is composed with a fake controller, `hapticsEnabled = true`, `phase = ResponseHapticPhase.GeneratingBuzz`, and some unrelated parent state such as `inputText = "A"`
- When: the parent recomposes with `inputText = "AB"` while `hapticsEnabled` and `phase` remain unchanged
- Then: `fakeController.startThinkingPulseCount == 0`
- Then: `fakeController.startGeneratingBuzzCount == 1`
- Then: `fakeController.stopCount == 0`
- Then: `fakeController.activePattern == "generating-buzz"`

### M2: `PROCESSING -> THINKING -> GENERATING` starts haptics only at `THINKING` and switches only at `GENERATING`

- Given: `ChatViewModel.uiState` is observed across one assistant turn that transitions `ENGINE_LOADING -> PROCESSING -> THINKING -> GENERATING`
- When: the state sequence is collected in order
- Then: the first two observed `responseHapticPhase` values are `ResponseHapticPhase.None`
- Then: the first non-`None` value is `ResponseHapticPhase.ThinkingPulse`
- Then: the next non-`None` value is `ResponseHapticPhase.GeneratingBuzz`
- Then: a controller-backed UI test driven by that phase produces `startThinkingPulseCount == 1`
- Then: a controller-backed UI test driven by that phase produces `startGeneratingBuzzCount == 1`

## Notes for the Implementation Executor

- Settings-removal tests should fail if any visible `Haptic Press` label or callback survives.
- Chat-state tests should fail if the implementation uses `isGenerating` as the haptics driver, because `isGenerating` is also `true` for `ENGINE_LOADING` and `PROCESSING` and cannot distinguish heartbeat thinking from rapid generation buzz.
- The haptic effect tests should use an injected fake controller rather than a real vibrator so they remain deterministic on the JVM.

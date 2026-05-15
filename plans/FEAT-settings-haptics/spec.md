# Spec: Settings Haptics

## Objective

Implement the haptics feature request without violating the repo's module boundaries or scattering UI-side effects across the app.

Accepted product decision:
- Keep `Haptic Response`.
- Remove `Haptic Press`.

Rationale:
- The current codebase has persisted booleans for both options, but there is no app-wide click/interact wrapper, no existing haptics composition local, and many direct `IconButton` / `Button` / `Switch` / `.clickable` call sites spread across `feature/*`, `core/ui`, and `app`.
- An honest implementation of `Haptic Press` would require broad touch-surface edits across multiple modules, which conflicts with the ticket guidance to drop the option if it balloons into "modify every single control in the entire app."
- `Haptic Response` already has a clean seam: `SettingsData` already flows into `ChatViewModel`, and chat streaming state already exists in `feature/chat`.

Acceptance criteria:
- Settings no longer presents a `Haptic Press` toggle anywhere in `SettingsScreen`.
- `Haptic Response` remains in settings and still persists through the existing settings pipeline.
- Chat emits device haptics only while an assistant response is actively thinking or generating, not during engine loading or generic processing.
- Thinking haptics use a slow pulse / heartbeat pattern.
- Generating haptics use a rapid jittery buzzing pattern.
- Haptic side effects stay in the UI layer as a leaf composable or leaf helper owned by `feature/chat`.
- No Room schema changes and no new navigation or dependency-boundary violations are introduced.

## File Structure

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsScreen.kt` | Remove the `Haptic Press` row from the Experience section; keep `Haptic Response`. |
| Modify | `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsRoute.kt` | Stop passing an `onHapticPressChange` callback into `SettingsScreen`. |
| Modify | `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModel.kt` | Remove `onHapticPressChange`; keep `onHapticResponseChange`; continue exposing immutable `uiState`. |
| Modify | `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsGeneralDelegate.kt` | Remove the `Haptic Press` handler and wrap `Haptic Response` updates with the existing view-model error handler pattern used by the other settings toggles. |
| Modify | `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsModels.kt` | Remove `hapticPress` from `SettingsHomeUiState`; keep `hapticResponse`. |
| Modify | `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsUiStateFactory.kt` | Stop mapping `persistedSettings.hapticPress`; keep `persistedSettings.hapticResponse`. |
| Modify | `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/SettingsRepository.kt` | Remove the `hapticPress` field from `SettingsData` and remove `updateHapticPress`. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/SettingsRepositoryImpl.kt` | Stop reading/writing `settings_haptic_press`; keep `settings_haptic_response`. Old preference data may remain on disk with no migration because Preferences DataStore is key-value, not schema-bound. |
| Modify | `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/settings/SettingsUseCases.kt` | Remove `updateHapticPress` from `SettingsPreferencesUseCases`. |
| Modify | `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/settings/SettingsUseCasesImpl.kt` | Remove `UpdateHapticPressUseCase` wiring. |
| Delete | `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/settings/UpdateHapticPressUseCase.kt` | Remove dead domain surface for the dropped setting. |
| Modify | `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatModels.kt` | Add `ResponseHapticPhase` and `responseHapticPhase: ResponseHapticPhase = ResponseHapticPhase.None` to `ChatUiState`. |
| Modify | `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatViewModel.kt` | Derive `responseHapticPhase` from assistant message state and map it into `ChatUiState` without leaking framework types into domain. |
| Modify | `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatScreen.kt` | Mount a leaf-scoped haptic effect driven only by `uiState.hapticResponse` and `uiState.responseHapticPhase`. |
| Create | `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/components/StreamingResponseHapticEffect.kt` | Encapsulate the Android haptic side effect and the vibrator-backed controller contract. |
| Modify | `app/src/main/AndroidManifest.xml` | Add `android.permission.VIBRATE` exactly once; no runtime request flow. |
| Modify | `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModelTest.kt` | Remove `Haptic Press` expectations and keep `Haptic Response` persistence coverage. |
| Create | `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsScreenTest.kt` | Add a focused Compose UI test that proves `Haptic Press` is gone and `Haptic Response` remains. |
| Create | `feature/chat/src/test/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatViewModelHapticsStateTest.kt` | Cover `responseHapticPhase` mapping for assistant states. |
| Create | `feature/chat/src/test/kotlin/com/browntowndev/pocketcrew/feature/chat/components/StreamingResponseHapticEffectTest.kt` | Verify start/stop/no-restart behavior with a fake controller under Robolectric Compose tests. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/SettingsRepositoryTest.kt` | Remove `hapticPress` assertions and preserve `hapticResponse` assertions. |
| Modify | `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/GetSettingsUseCaseTest.kt` | Remove `hapticPress` assertions and preserve `hapticResponse` assertions. |
| Modify | `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/settings/SettingsUseCasesImplTest.kt` | Remove the deleted use case from aggregate wiring assertions. |
| Delete | `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/settings/UpdateHapticPressUseCaseTest.kt` | Remove dead test coverage for the dropped setting. |
| Modify | `core/domain/src/testFixtures/kotlin/com/browntowndev/pocketcrew/domain/usecase/FakeSettingsRepository.kt` | Remove `updateHapticPress` plumbing and verification helpers. |

## Data Models & Schemas

Persisted settings:
- Remove `hapticPress` from `SettingsData`.
- Keep `hapticResponse: Boolean`.

Settings presentation:
- Remove `hapticPress` from `SettingsHomeUiState`.
- Keep `hapticResponse: Boolean`.

Chat presentation:
- Add a dedicated presentation-only enum named `ResponseHapticPhase`.
- Contract: `ResponseHapticPhase.None` means no response haptics.
- Contract: `ResponseHapticPhase.ThinkingPulse` maps only from an active assistant message in `MessageState.THINKING`.
- Contract: `ResponseHapticPhase.GeneratingBuzz` maps only from an active assistant message in `MessageState.GENERATING`.
- Contract: `ResponseHapticPhase.None` maps from `MessageState.ENGINE_LOADING`, `MessageState.PROCESSING`, `MessageState.COMPLETE`, and chats with no active assistant stream.

Storage and schema:
- No Room entities, DAOs, tables, or columns change.
- Preferences DataStore key removal for `settings_haptic_press` is not a schema migration. The key may remain in the stored file unused after deployment.

## API Contracts & Interfaces

Settings contract changes:
- `SettingsRepository` no longer exposes `updateHapticPress`.
- `SettingsPreferencesUseCases` no longer exposes `updateHapticPress`.
- `SettingsViewModel` no longer exposes `onHapticPressChange`.
- `SettingsScreen` no longer accepts `onHapticPressChange`.

Chat haptic contract:
- Introduce an internal UI-layer controller owned by `feature/chat` named `StreamingResponseHapticController`.
- The controller contract is idempotent:
  - `startThinkingPulse()` is a no-op if the heartbeat pattern is already active.
  - `startGeneratingBuzz()` is a no-op if the rapid buzz pattern is already active.
  - Starting one pattern while the other is active switches to the requested pattern exactly once.
  - `stop()` is a no-op if already stopped.
  - Disposal must always leave the vibrator inactive.
- `StreamingResponseHapticEffect` consumes only presentation state:
  - `hapticsEnabled: Boolean`
  - `phase: ResponseHapticPhase`
  - optional injected controller for tests

Side-effect contract:
- The effect must be a leaf composable or leaf helper, not ViewModel logic.
- The effect must use stable Compose side-effect primitives so unrelated recompositions do not retrigger the buzz pattern.
- The thinking pattern must feel like a pulse or heartbeat: slow repeating tactile beats separated by a longer quiet interval.
- The generating pattern must feel rapid and jittery: short repeated buzz pulses with short gaps.
- Preferred pattern: `remember(...)` for controller construction plus `DisposableEffect` or `LaunchedEffect` keyed to the minimum state needed, following the same local side-effect style already used by `BiometricVaultUnlockEffect` in `app/src/main/kotlin/com/browntowndev/pocketcrew/presentation/MainActivity.kt`.

Android API contract grounded in AndroJack:
- Compose/lifecycle guidance: collect screen state with `collectAsStateWithLifecycle()` and keep side effects local to the composable tree.
- Permission guidance: `android.permission.VIBRATE` is a normal permission and does not require a runtime prompt.
- API-level guidance: minSdk is 34, so vibrator-manager-era implementations are viable if the implementation sub-agent confirms exact API signatures with AndroJack during coding.

## Config / Permissions Delta

Manifest:
- Add `<uses-permission android:name="android.permission.VIBRATE" />` to `app/src/main/AndroidManifest.xml`.

Gradle:
- No dependency additions are expected. `feature/chat` and `feature/settings` already contain the Compose, lifecycle, Robolectric, and JUnit 5 dependencies needed for the planned UI and JVM tests.

Runtime behavior:
- No runtime permission request must be added.
- No service, worker, or navigation config changes are required.

## Constitution Audit

Clean architecture:
- The haptic side effect stays in `:feature:chat` / `:app` UI code and does not enter `:domain` or `:data`.
- The dropped `Haptic Press` surface is removed from domain/data/app consistently rather than left as a dead public contract.

State handling:
- Settings persistence continues to flow from DataStore through `SettingsRepository` into `SettingsViewModel`.
- Chat haptics consume existing presentation state and a new presentation-only haptic phase; no repository calls from composables.

Schema lock:
- No Room schema changes.
- No transient UI state is persisted to the database.

Navigation and Compose:
- `SettingsRoute` and `ChatRoute` continue using `collectAsStateWithLifecycle()`.
- No `NavController` is passed into composables.
- The haptic effect is local and leaf-scoped, avoiding broad recomposition and avoiding a root-level global click interceptor.

Testing mandate:
- Production changes span `:domain`, `:data`, `feature:settings`, and `feature:chat`, so matching unit and Compose tests are required in those source sets.

## Subagent-Driven Execution

Execution order is strict because this ticket contains one product-level removal and one targeted implementation.

Parent executor rules:
1. Finish the `Haptic Press` removal contract first so the repo does not temporarily carry an exposed dead setting.
2. Only then wire the chat-local `Haptic Response` behavior.
3. Review every child result against the File Structure table before continuing.

Per-child review checklist:
- Only listed files are touched.
- No child introduces framework code into `:domain`.
- No child adds a runtime permission request for vibration.
- No child reintroduces `Haptic Press` through a renamed flag or dead DataStore key reader.
- Chat haptic behavior is driven by `responseHapticPhase`, not by every message-text update or every recomposition.

## Implementation Tasks

1. Task 1 (4 minutes): Remove `Haptic Press` from the user-facing settings surface.
   Files: `SettingsScreen.kt`, `SettingsRoute.kt`, `SettingsViewModel.kt`, `SettingsGeneralDelegate.kt`, `SettingsModels.kt`, `SettingsUiStateFactory.kt`, `SettingsScreenTest.kt`, `SettingsViewModelTest.kt`.
   Outcome: the Settings screen shows `Haptic Response` but not `Haptic Press`, and the removed callback/property no longer exists in the settings presentation layer.

2. Task 2 (4 minutes): Remove the dead persisted/domain contract for `Haptic Press`.
   Files: `SettingsRepository.kt`, `SettingsRepositoryImpl.kt`, `SettingsUseCases.kt`, `SettingsUseCasesImpl.kt`, `UpdateHapticPressUseCase.kt`, `FakeSettingsRepository.kt`, `SettingsRepositoryTest.kt`, `GetSettingsUseCaseTest.kt`, `SettingsUseCasesImplTest.kt`, `UpdateHapticPressUseCaseTest.kt`.
   Outcome: the dropped option is fully removed from domain/data/test surfaces without touching Room or inventing migrations.

3. Task 3 (5 minutes): Add a chat-only streaming-state seam for response haptics.
   Files: `ChatModels.kt`, `ChatViewModel.kt`, `ChatViewModelHapticsStateTest.kt`.
   Outcome: `ChatUiState` exposes `responseHapticPhase`, mapped to `ThinkingPulse` only for `THINKING`, `GeneratingBuzz` only for `GENERATING`, and `None` otherwise; JVM tests cover the mapping.

4. Task 4 (5 minutes): Implement the leaf-scoped streaming haptic effect.
   Files: `StreamingResponseHapticEffect.kt`, `ChatScreen.kt`, `AndroidManifest.xml`, `StreamingResponseHapticEffectTest.kt`.
   Outcome: a stable controller-backed effect starts the heartbeat pulse when `hapticResponse` is `true` and `responseHapticPhase` is `ThinkingPulse`, switches to rapid buzz when the phase is `GeneratingBuzz`, stops on `None` or disposal, and requires only the manifest permission.

5. Task 5 (3 minutes): Run the constitution validation set for the touched modules.
   Commands for the implementation executor:
   `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew :core:domain:assemble :core:data:assemble :feature:settings:testDebugUnitTest :feature:chat:testDebugUnitTest :app:assembleDebug test ktlintCheck detekt --no-daemon --stacktrace`
   Outcome: tests and static gates prove the removal and the new chat haptics behavior without regressions.

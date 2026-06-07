# Live Voice (speech↔speech) — Technical Spec

Ticket: `live-voice`

## 1. Feature summary

Add a continuous, realtime voice-to-voice ("Live Voice") conversation mode to Enclave.
A user taps a live-voice button in the chat input bar, grants the microphone permission,
and enters a full-screen neo-brutalist voice overlay. The app streams microphone PCM16
audio up to a BYOK realtime provider over a bidirectional WebSocket and plays the model's
audio reply back in realtime while showing live captions. When the conversation ends (or
each turn finalizes), the spoken user and assistant turns are persisted into the active
chat as normal text `Message` rows (a chat is created on demand if none is active).

Providers: **OpenAI Realtime** and **xAI Realtime** share one OpenAI-compatible WebSocket
session adapter (differ only by base URL / model, mirroring how `TtsServiceFactory` treats
`XAI` like `OPENAI`); **Google Gemini Live** gets its own adapter. Anthropic is excluded
(no realtime voice API).

This spec is organized into the source plan's Parts A–F. It maps every created/modified
file, defines all new API contracts and interfaces, the schema changes, an ordered task
list, and the scenario IDs (defined in `test_spec.md`) that each task satisfies.

### Confirmed grounding facts (verified against the codebase)

- DB builder uses `fallbackToDestructiveMigration(dropAllTables = true)`
  (`core/data/.../DataModule.kt:185`); the current `@Database(version = 1)`
  (`EnclaveDatabase.kt:37`). Adding the new table + column needs only a version bump;
  **no hand-written SQL migration**.
- Credentials use `credentialAlias` → `ApiKeyManager` via `ApiKeyProviderPort`
  (`core/domain/.../port/security/ApiKeyProviderPort.kt`: `suspend fun getApiKey(alias): String?`,
  `fun saveApiKey(alias, key)`).
- Streaming playback: `StreamingAudioPlayerPort` with `StreamingAudioConfig.PCM_16_24KHZ_MONO`
  (24 kHz) and `PCM_16_16KHZ_MONO` (16 kHz) already exist; reuse for model audio out.
- `MessageRepository.saveMessage(Message): MessageId` persists messages.
- `CreateUserMessageUseCase(message, scheduleCloudSync)` already creates a chat when
  `message.chatId == null` and returns `UserPromptResult(userMessageId, chatId, ...)` — reuse
  for the user turn so on-demand chat creation matches existing behavior.
- `SoundWave(state: SpeechState.Listening, modifier)` canvas
  (`core/ui/.../UniversalInputBar.kt:525`) is the seed for the neo-brutalist waveform.

### xAI Realtime protocol — CONFIRMED from xAI docs

Source: `https://docs.x.ai/developers/model-capabilities/audio/voice-agent`. The xAI Voice
Agent API is **substantially OpenAI-Realtime-compatible**, so OPENAI and XAI keep one shared
`OpenAiRealtimeSession`. Confirmed facts the implementer must use (no longer "verify at build"):

- **Endpoint:** `wss://api.x.ai/v1/realtime?model={modelName}`. Derive the host from
  `asset.baseUrl` / `ApiProvider.XAI.defaultBaseUrl()` (`https://api.x.ai/v1` → `wss://api.x.ai/v1`).
- **Auth:** server-side `Authorization: Bearer <key>` (same header as OpenAI). The browser
  ephemeral `xai-client-secret.{token}` subprotocol is NOT used — this is on-device BYOK.
- **Models:** `grok-voice-think-fast-1.0` (flagship/recommended default), `grok-voice-fast-1.0`,
  `grok-voice-latest`. These are realtime voice model ids, not chat model ids.
- **Voices:** `eve`, `ara`, `rex`, `sal`, `leo` (plus custom voice ids).
- **Audio in:** `input_audio_buffer.append` with base64 Linear16 little-endian PCM; default
  24000 Hz; server VAD via `session.update` `turn_detection.type:"server_vad"`; manual mode needs
  `input_audio_buffer.commit`.
- **Audio out:** `response.output_audio.delta` carrying base64 chunks (OpenAI historically emits
  `response.audio.delta`).
- **Dialect divergences the shared parser must absorb** (see §3.4.1): xAI user transcript is the
  **cumulative** `conversation.item.input_audio_transcription.updated` event (full text-so-far each
  time), whereas OpenAI streams incremental `…transcription.delta` then a final `…completed`. xAI
  also omits some OpenAI events (`rate_limits.updated`, `conversation.item.retrieve`) and adds
  extensions (`force_message`, `x_search`) which Live Voice ignores.

### Corrections to source plan names/paths (verified)

- Plan says the credential indirection comes via `ApiKeyManager` (EncryptedSharedPreferences).
  The domain-facing port is `ApiKeyProviderPort` and its method is **`getApiKey`** (not `getKey`);
  `LiveVoiceSessionUseCase` must call `apiKeyProvider.getApiKey(asset.credentialAlias)`.
- Plan references `UniversalVoicePromptPlaceholder` as the "seed" for the waveform. The
  reusable canvas is **`SoundWave`** (`UniversalInputBar.kt:525`); `UniversalVoicePromptPlaceholder`
  (`:378`) composes `SoundWave`. The new overlay reuses/extends `SoundWave`, not the placeholder.
- Plan suggests adding `liveVoiceProviderId` only to `DefaultModelAssignment` +
  `DefaultModelEntity`. It must **also** thread through `DefaultModelRepositoryPort.setDefault`,
  `DefaultModelRepositoryImpl.setDefault`, `DefaultModelsDao.observeAllWithDetails` (SQL view +
  `DefaultModelAssignmentView`), `SetDefaultModelUseCase`, and the `DefaultModelEntity.init`
  exactly-one-non-null invariant.
- Plan refers to `EnclaveDatabaseMigrations.kt` "doc note": this file is the object
  `EnclaveDatabaseMigrations` whose KDoc is the version note; update that KDoc.
- `ResolvedAssignedModelSelection` lives in
  `core/domain/.../usecase/settings/SettingsWorkflowModels.kt` (not a standalone file).
- The settings use-case group `SettingsTtsUseCases` is bound via `SettingsTtsUseCasesImpl`
  in `SettingsUseCasesImpl.kt` and `SettingsUseCasesModule.kt`; the new
  `SettingsLiveVoiceUseCases` follows the same two locations (not `ByokUseCasesModule.kt`).
- `ChatTopBar` currently renders a centered hardcoded `Text("Enclave")` (`ChatTopBar.kt:40`);
  there is no existing `selectedMode` param — it must be added.
- `ModelType.fromApiValue` defaults unknown values to `MAIN`; the new `LIVE_VOICE("live_voice")`
  entry must be added to the enum, `displayName()`, and `description` `when` blocks (these are
  exhaustive and will not compile otherwise).

---

## 2. File structure tables (Create / Modify + responsibility)

### Part A — Settings: Live Voice provider config

| File | Action | Responsibility |
|------|--------|----------------|
| `core/domain/.../model/config/LiveVoiceProviderAsset.kt` | Create | `LiveVoiceProviderId` value class + `LiveVoiceProviderAsset` domain model (clone of `TtsProviderAsset`). |
| `core/domain/.../port/repository/LiveVoiceProviderRepositoryPort.kt` | Create | Repository port (clone of `TtsProviderRepositoryPort`). |
| `core/data/.../local/LiveVoiceProviderEntity.kt` | Create | Room `@Entity(tableName = "live_voice_providers")` (clone of `TtsProviderEntity`). |
| `core/data/.../local/LiveVoiceProviderDao.kt` | Create | DAO (clone of `TtsProviderDao`, same sync columns). |
| `core/data/.../repository/LiveVoiceProviderRepositoryImpl.kt` | Create | Repository impl with encrypt/decrypt + alias prefix `lv-${id}`. |
| `core/data/.../local/EnclaveDatabase.kt` | Modify | Register `LiveVoiceProviderEntity::class` + `abstract fun liveVoiceProviderDao()`; bump `version = 1` → `version = 2`. |
| `core/data/.../local/EnclaveDatabaseMigrations.kt` | Modify | Update KDoc version note to document v2 adding `live_voice_providers`. |
| `core/data/.../DataModule.kt` | Modify | `@Provides fun provideLiveVoiceProviderDao(...)`; `@Binds bindLiveVoiceProviderRepository(...)`. |
| `core/data/.../security/SensitiveFieldCatalog.kt` | Modify | Add `LiveVoiceProviderEntity` Room sensitive fields + `live_voice_providers` Firestore fields (mirror `TtsProviderEntity`). |
| `core/domain/.../usecase/settings/GetLiveVoiceProvidersUseCase.kt` | Create | Observe providers (clone of `GetTtsProvidersUseCase`). |
| `core/domain/.../usecase/settings/SaveLiveVoiceProviderUseCase.kt` | Create | Save provider; `Result.runCatching`, resolves credential alias (clone of `SaveTtsProviderUseCase`, no fal.ai branch). |
| `core/domain/.../usecase/settings/DeleteLiveVoiceProviderUseCase.kt` | Create | Delete by id. |
| `core/domain/.../usecase/settings/SettingsWorkflowModels.kt` | Modify | Add `LiveVoiceProviderDraft`; extend `ResolvedAssignedModelSelection` with `liveVoiceAsset`. |
| `core/domain/.../usecase/settings/SettingsUseCases.kt` | Modify | Add `SettingsLiveVoiceUseCases` interface + `val liveVoice` on `SettingsUseCases`. |
| `core/domain/.../usecase/settings/SettingsUseCasesImpl.kt` | Modify | Add `SettingsLiveVoiceUseCasesImpl`; add `override val liveVoice` to `SettingsUseCasesImpl`. |
| `app/.../SettingsUseCasesModule.kt` | Modify | `@Binds bindSettingsLiveVoiceUseCases(...)`. |
| `core/domain/.../model/inference/ModelType.kt` | Modify | Add `LIVE_VOICE("live_voice")`; `displayName()` + `description` cases; keep out of `isTextModel`. |
| `core/domain/.../model/config/DefaultModelAssignment.kt` | Modify | Add `liveVoiceProviderId: LiveVoiceProviderId?`. |
| `core/data/.../local/DefaultModelEntity.kt` | Modify | Add `@ColumnInfo("live_voice_provider_id") liveVoiceProviderId`, FK + index; update exactly-one-non-null `init`. |
| `core/data/.../local/DefaultModelAssignmentView.kt` | Modify | Add `liveVoiceProviderId`, `liveVoiceAssetName`, `liveVoiceProviderName`, `liveVoiceVoiceName` columns. |
| `core/data/.../local/DefaultModelsDao.kt` | Modify | Extend `observeAllWithDetails` SQL (select live-voice columns + `LEFT JOIN live_voice_providers`). |
| `core/domain/.../port/repository/DefaultModelRepositoryPort.kt` | Modify | Add `liveVoiceProviderId: LiveVoiceProviderId? = null` to `setDefault`. |
| `core/data/.../repository/DefaultModelRepositoryImpl.kt` | Modify | Thread `liveVoiceProviderId` through `setDefault`, `resolveAssignment`, and `View.toDomain`. |
| `core/domain/.../usecase/byok/SetDefaultModelUseCase.kt` | Modify | Add `liveVoiceProviderId` param; `LIVE_VOICE` slot validation; pass through to repo. |
| `core/domain/.../usecase/settings/ResolveAssignedModelSelectionUseCase.kt` | Modify | Inject `GetLiveVoiceProvidersUseCase`; resolve `liveVoiceProviderId` → `liveVoiceAsset`. |
| `core/domain/.../model/inference/ApiProvider.kt` | Modify | Add `val supportsRealtimeVoice: Boolean` (OPENAI/GOOGLE/XAI = true). |
| `feature/settings/.../LiveVoiceProvidersBottomSheet.kt` | Create | Sheet: list + swipe-delete + "Add" (clone of `TtsProvidersBottomSheet`). |
| `feature/settings/.../LiveVoiceConfigureScreen.kt` | Create | Configure screen (clone of `TtsConfigureScreen`); provider dropdown filtered by `supportsRealtimeVoice`. |
| `feature/settings/.../LiveVoiceProvidersCoordinator.kt` | Create | VM-side coordinator (clone of `TtsProvidersCoordinator`). |
| `feature/settings/.../LiveVoiceProviderAssetUiMapper.kt` | Create | Domain→UI mapper (clone of `TtsProviderAssetUiMapper`). |
| `feature/settings/.../SettingsModels.kt` | Modify | Add `LiveVoiceProviderAssetUi`, `LiveVoiceProvidersTransientState`, sheet/editor UI states. |
| `feature/settings/.../SettingsApiProvidersDelegate.kt` | Modify | Delegate live-voice handlers to `LiveVoiceProvidersCoordinator`. |
| `feature/settings/.../SettingsViewModel.kt` | Modify | `_liveVoiceState` transient flow + `onShow…/onSave…/onSelect…/onDelete…/onFetchLiveVoiceModels`. |
| `feature/settings/.../SettingsUiStateFactory.kt` | Modify | Add live-voice transient state shaping. |
| `feature/settings/.../SettingsUiStateAssembler.kt` | Modify | Assemble assets/editor/sheet + default flag. |
| `feature/settings/.../SettingsUiState.kt` / `SettingsHomeUiState` | Modify | Add `isLiveVoiceProvidersSheetOpen`. |
| `feature/settings/.../SettingsScreen.kt` | Modify | `NeoNavRow("Live Voice")` in Models section + conditional `LiveVoiceProvidersBottomSheet` block. |
| `feature/settings/.../navigation/SettingsNavigationRoutes.kt` | Modify | Register live-voice configure route. |
| `feature/settings/.../navigation/SettingsDestination.kt` | Modify | Add `LiveVoiceConfigure` destination. |

### Part B — Domain: realtime session port

| File | Action | Responsibility |
|------|--------|----------------|
| `core/domain/.../port/media/LiveVoicePort.kt` | Create | `LiveVoicePort`, `LiveVoiceSession`, sealed `LiveVoiceEvent`. |
| `core/domain/.../port/media/RealtimeAudioCapturePort.kt` | Create | PCM16 byte-array capture at a requested sample rate. |
| `core/domain/.../usecase/chat/LiveVoiceSessionUseCase.kt` | Create | Resolve `LIVE_VOICE` default → key → open session; expose result. |
| `core/domain/.../usecase/chat/PersistVoiceTurnUseCase.kt` | Create | Persist finalized user/assistant transcript turns as `Message` rows. |

### Part C — Data/inference: WebSocket adapters

| File | Action | Responsibility |
|------|--------|----------------|
| `feature/inference/.../liveVoice/LiveVoicePortImpl.kt` | Create | Binds `LiveVoicePort`; routes by `asset.provider` to a session factory. |
| `feature/inference/.../liveVoice/OpenAiRealtimeSession.kt` | Create | OpenAI-compatible (OpenAI + xAI) realtime WebSocket session + event parsing. |
| `feature/inference/.../liveVoice/GeminiLiveSession.kt` | Create | Gemini Live `BidiGenerateContent` WebSocket session + event parsing. |
| `feature/inference/.../liveVoice/LiveVoiceEventParsing.kt` | Create | Pure JSON→`LiveVoiceEvent` parsers (testable without sockets). |
| `feature/inference/.../media/RealtimeAudioCaptureImpl.kt` | Create | `AudioRecord` mono PCM16 capture (+ AEC/NS when available). |
| `feature/inference/.../di/LiveVoiceModule.kt` | Create | `@Binds LiveVoicePort` + `RealtimeAudioCapturePort`. |

### Part D — Chat wiring

| File | Action | Responsibility |
|------|--------|----------------|
| `feature/chat/.../delegates/LiveVoiceCoordinator.kt` | Create | Session lifecycle + `StateFlow<LiveVoiceUiState>` state machine. |
| `feature/chat/.../LiveVoiceUiState.kt` | Create | `LiveVoiceUiState` sealed UI model (presentation). |
| `feature/chat/.../ChatViewModel.kt` | Modify | Inject coordinator; add `liveVoiceState` to combine; `onStartLiveVoice/onEndLiveVoice/onToggleMute`; cancel on `onCleared`/`createNewChat`. |
| `feature/chat/.../ChatModels.kt` | Modify | Add `liveVoiceState` + `selectedModeModelName` to `ChatUiState`. |
| `feature/chat/.../delegates/ChatUiStateReducer.kt` | Modify | Compute `selectedModeModelName`; pass `liveVoiceState` through. |

### Part E — UI changes

| File | Action | Responsibility |
|------|--------|----------------|
| `feature/chat/.../components/InputBar.kt` | Modify | Remove mode picker; blank→`[mic][live-voice]`, text→`Send`; gate-disable live voice. |
| `core/ui/.../component/UniversalInputBar.kt` | Modify | Add nullable `onLiveVoiceClick` slot to `UniversalVoiceTrailingAction`; reusable blocky waveform. |
| `feature/chat/.../ChatTopBar.kt` | Modify | Replace `Text("Enclave")` with tappable mode+model picker (move dropdown here). |
| `feature/chat/.../ChatScreen.kt` | Modify | Pass mode/model/onModeChange to `ChatTopBar`; render `LiveVoiceOverlay` when not Idle; wire live-voice callbacks. |
| `feature/chat/.../components/LiveVoiceOverlay.kt` | Create | Full-screen neo-brutalist overlay (orb, blocky waveform, captions, End/Mute). |

### Part F — Tests

| File | Action | Responsibility |
|------|--------|----------------|
| `core/domain/.../usecase/settings/SaveLiveVoiceProviderUseCaseTest.kt` | Create | Save use case behavior. |
| `core/domain/.../usecase/settings/ResolveAssignedModelSelectionUseCaseTest.kt` | Modify | `LIVE_VOICE` resolution case. |
| `core/domain/.../usecase/chat/PersistVoiceTurnUseCaseTest.kt` | Create | Turn persistence + on-demand chat creation. |
| `core/domain/.../usecase/chat/LiveVoiceSessionUseCaseTest.kt` | Create | Resolve→key→session + gate-disable paths. |
| `core/data/.../repository/LiveVoiceProviderRepositoryImplTest.kt` | Create | Repo map/save/delete (clone of `TtsProviderRepositoryImplTest`). |
| `core/data/.../local/EnclaveDatabaseMigrationTest.kt` | Modify | New table + column assertions. |
| `feature/inference/.../liveVoice/OpenAiRealtimeEventParsingTest.kt` | Create | OpenAI/xAI canned-JSON event parsing. |
| `feature/inference/.../liveVoice/GeminiLiveEventParsingTest.kt` | Create | Gemini Live canned-JSON event parsing. |
| `feature/inference/.../liveVoice/LiveVoicePortImplTest.kt` | Create | Provider routing OPENAI/XAI vs GOOGLE. |
| `feature/chat/.../delegates/LiveVoiceCoordinatorTest.kt` | Create | State-machine + barge-in + mute + persist. |
| `feature/chat/.../delegates/ChatUiStateReducerLiveVoiceModelNameTest.kt` | Create | `selectedModeModelName` per mode. |
| `feature/settings/.../LiveVoiceProvidersCoordinatorTest.kt` | Create | Coordinator sheet/save (clone of `TtsProvidersCoordinatorTest`). |

---

## 3. API contracts and interfaces

All packages use the existing `com.browntowndev.enclave` roots shown in the file tables.

### 3.1 Domain models (Part A)

```
@JvmInline value class LiveVoiceProviderId(val value: String)

data class LiveVoiceProviderAsset(
    val id: LiveVoiceProviderId,
    val displayName: String,
    val provider: ApiProvider,
    val voiceName: String,
    val modelName: String? = null,
    val baseUrl: String? = null,
    val credentialAlias: String,
)
```

`ApiProvider.supportsRealtimeVoice: Boolean` — `OPENAI, GOOGLE, XAI -> true`;
`ANTHROPIC, OPENROUTER, FAL -> false`.

`ModelType.LIVE_VOICE` — `apiValue = "live_voice"`, `@SerialName("live_voice")`,
`displayName() = "Live Voice"`, `description = "Realtime speech-to-speech voice conversation."`,
**not** included in `isTextModel`.

`DefaultModelAssignment.liveVoiceProviderId: LiveVoiceProviderId? = null` (new field).

`ResolvedAssignedModelSelection.liveVoiceAsset: LiveVoiceProviderAsset? = null` (new field).

`LiveVoiceProviderDraft` (in `SettingsWorkflowModels.kt`):
```
data class LiveVoiceProviderDraft(
    val id: LiveVoiceProviderId = LiveVoiceProviderId(""),
    val displayName: String,
    val provider: ApiProvider,
    val voiceName: String,
    val modelName: String? = null,
    val baseUrl: String? = null,
    val credentialAlias: String,
    val apiKey: String,
    val sourceCredentialAlias: String? = null,
)
```

### 3.2 Repository port (Part A)

```
interface LiveVoiceProviderRepositoryPort {
    fun getLiveVoiceProviders(): Flow<List<LiveVoiceProviderAsset>>
    suspend fun getLiveVoiceProvidersSync(): List<LiveVoiceProviderAsset>
    suspend fun getLiveVoiceProvider(id: LiveVoiceProviderId): LiveVoiceProviderAsset?
    suspend fun saveLiveVoiceProvider(asset: LiveVoiceProviderAsset, apiKey: String?): LiveVoiceProviderId
    suspend fun deleteLiveVoiceProvider(id: LiveVoiceProviderId)
}
```

Impl behavior (clone of `TtsProviderRepositoryImpl`): empty `id` → new UUID; blank
credential alias defaults to `"lv-${id.value}"`; encrypt sensitive fields via
`SensitiveEntityFieldEncryptors.encrypt`; persist API key via `ApiKeyManager.save` only when
non-blank; soft-delete + `ApiKeyManager.delete` on delete; `triggerSync()` after writes.

### 3.3 DAO + entity + schema (Part A)

`@Entity(tableName = "live_voice_providers")` with columns mirroring `TtsProviderEntity`
(`id` PK, `displayName`, `provider`, `voiceName`, `modelName?`, `baseUrl?`, `credentialAlias`,
`pending_sync` Int = 0, `updated_at` Long, `deleted` Boolean = false).

`LiveVoiceProviderDao` queries mirror `TtsProviderDao` (`getLiveVoiceProviders` returning
`Flow<List<LiveVoiceProviderEntity>>`, `getLiveVoiceProvidersSync`, `getLiveVoiceProvider(id)`,
`upsertLiveVoiceProvider`, `softDeleteById`, `purgeDeleted`, `claimPending`, `getClaimedForSync`,
`markSynced`).

`DefaultModelEntity` new column:
```
@ColumnInfo(name = "live_voice_provider_id") val liveVoiceProviderId: LiveVoiceProviderId? = null
```
plus `ForeignKey(entity = LiveVoiceProviderEntity::class, parentColumns=["id"],
childColumns=["live_voice_provider_id"], onDelete = RESTRICT)`, `Index(["live_voice_provider_id"])`,
and the `init` invariant updated to count five candidates (exactly one non-null).

`DefaultModelsDao.observeAllWithDetails` adds:
`dm.live_voice_provider_id as liveVoiceProviderId`, `lvp.displayName as liveVoiceAssetName`,
`lvp.provider as liveVoiceProviderName`, `lvp.voiceName as liveVoiceVoiceName`, and
`LEFT JOIN live_voice_providers lvp ON dm.live_voice_provider_id = lvp.id`.

`SetDefaultModelUseCase` validation for `LIVE_VOICE` (mirrors the TTS slot rule):
`require(localConfigId == null && apiConfigId == null && ttsProviderId == null && mediaProviderId == null)`,
then `requireNotNull(liveVoiceProviderId) { "Live Voice slot requires a Live Voice provider assignment." }`.
The thrown type is `IllegalArgumentException` with that exact message (verified by `LV-SETDEFAULT-002`).

### 3.4 Realtime port (Part B)

```
interface LiveVoicePort {
    suspend fun openSession(asset: LiveVoiceProviderAsset, apiKey: String): Result<LiveVoiceSession>
}

interface LiveVoiceSession {
    fun events(): Flow<LiveVoiceEvent>
    suspend fun sendAudio(pcm16: ByteArray)
    suspend fun commitTurn()
    suspend fun close()
}

sealed interface LiveVoiceEvent {
    data object Connected : LiveVoiceEvent
    data class UserTranscriptDelta(val text: String) : LiveVoiceEvent
    data class UserTranscriptDone(val text: String) : LiveVoiceEvent
    data class AssistantTranscriptDelta(val text: String) : LiveVoiceEvent
    data class AssistantTranscriptDone(val text: String) : LiveVoiceEvent
    data class AssistantAudioChunk(val pcm16: ByteArray) : LiveVoiceEvent
    data object SpeechStarted : LiveVoiceEvent
    data object SpeechStopped : LiveVoiceEvent
    data class Error(val message: String) : LiveVoiceEvent
    data object Closed : LiveVoiceEvent
}
```

`LiveVoiceEvent.AssistantAudioChunk` overrides `equals`/`hashCode` (content-based on `pcm16`)
so structural equality in tests is reliable.

```
interface RealtimeAudioCapturePort {
    /** Emits PCM16 little-endian mono frames captured at [sampleRate] Hz. */
    fun pcm16Frames(sampleRate: Int): Flow<ByteArray>
}
```

Sample rates: OpenAI/xAI input = **24000**, Gemini input = **16000**. Output playback uses
`StreamingAudioConfig.PCM_16_24KHZ_MONO` (OpenAI/xAI) / `PCM_16_16KHZ_MONO` (Gemini).

`LiveVoicePortImpl.openSession` routing: `OPENAI, XAI -> OpenAiRealtimeSession`;
`GOOGLE -> GeminiLiveSession`; any other provider →
`Result.failure(IllegalStateException("Live Voice not supported for provider: ${asset.provider.name}"))`
(exact message verified by `LV-ROUTE-003`; for `ANTHROPIC` the message is
`"Live Voice not supported for provider: ANTHROPIC"`).

### 3.4.1 OpenAI/xAI event-name dialects (shared parser)

`parseOpenAiRealtimeEvent(json): LiveVoiceEvent?` is one pure function that recognizes BOTH the
OpenAI and xAI `type` strings and normalizes them into the same `LiveVoiceEvent`. The session
adapter only differs by base URL + model; all protocol divergence lives in this type-string set.

| `LiveVoiceEvent` | OpenAI `type` (field) | xAI `type` (field) | Caption semantics |
|------------------|-----------------------|--------------------|-------------------|
| `UserTranscriptDelta(text)` | `conversation.item.input_audio_transcription.delta` (`delta`) | — (xAI sends cumulative, not incremental) | **append** to userCaption |
| `UserTranscriptDone(text)` | `conversation.item.input_audio_transcription.completed` (`transcript`) | `conversation.item.input_audio_transcription.updated` (`transcript`, cumulative) | **replace** userCaption (authoritative) |
| `AssistantTranscriptDelta(text)` | `response.audio_transcript.delta` (`delta`) | `response.output_audio_transcript.delta` (`delta`) | append to assistantCaption |
| `AssistantTranscriptDone(text)` | `response.audio_transcript.done` (`transcript`) | `response.output_audio_transcript.done` (`transcript`) | replace assistantCaption |
| `AssistantAudioChunk(pcm16)` | `response.audio.delta` (`delta`, base64) | `response.output_audio.delta` (`delta`, base64) | — |
| `SpeechStarted` | `input_audio_buffer.speech_started` | `input_audio_buffer.speech_started` | — |
| `SpeechStopped` | `input_audio_buffer.speech_stopped` | `input_audio_buffer.speech_stopped` | — |
| `Connected` | `session.created` | `session.created` | — |
| `Error(message)` | `error` (`error.message`) | `error` (`error.message`) | — |

Mapping rationale for the cumulative case: xAI's `…transcription.updated` carries the full
transcript-so-far on every event, so it maps to `UserTranscriptDone` (replace), NOT
`UserTranscriptDelta` (append). Appending cumulative payloads would duplicate text
(`"turn on""turn on the lights"`). The coordinator's Delta=append / Done=replace contract (§3.6)
makes the last `…updated` the authoritative caption, which is exactly what `end()` persists.
Unrecognized `type` strings return `null` (ignored by the session).

### 3.5 Use cases (Part B)

```
class LiveVoiceSessionUseCase(
    resolveAssignedModelSelectionUseCase: ResolveAssignedModelSelectionUseCase,
    liveVoicePort: LiveVoicePort,
    apiKeyProvider: ApiKeyProviderPort,
) {
    suspend operator fun invoke(): Result<LiveVoiceSession>
}
```
Resolution order, returning a typed `Result.failure`:
1. `resolveAssignedModelSelectionUseCase(ModelType.LIVE_VOICE)` null →
   `Result.failure(IllegalStateException("No Live Voice provider assigned."))`.
2. `selection.liveVoiceAsset` null →
   `Result.failure(IllegalStateException("Assigned Live Voice selection is invalid."))`.
3. `apiKeyProvider.getApiKey(asset.credentialAlias)` null →
   `Result.failure(IllegalStateException("API key not found for Live Voice provider: ${asset.displayName}"))`.
4. else `liveVoicePort.openSession(asset, apiKey)`.

```
class PersistVoiceTurnUseCase(
    createUserMessageUseCase: CreateUserMessageUseCase,
    messageRepository: MessageRepository,
) {
    /** Persists one user turn then one assistant turn. Creates a chat if [chatId] is null.
     *  Returns the resolved ChatId, or null when nothing was persisted. */
    suspend operator fun invoke(chatId: ChatId?, userText: String, assistantText: String): ChatId?
}
```
Behavior (deterministic, tested contract — see `LV-PERSIST-*`). Let
`u = userText.isNotBlank()` and `a = assistantText.isNotBlank()`:

1. `!u && !a` → persist nothing; return `chatId` unchanged.
2. Otherwise the **user turn** establishes/uses the chat:
   - Build `userMessage = Message(role = USER, chatId = chatId, messageState = COMPLETE,
     content = Content(text = if (u) userText else assistantText))`. (When the user said nothing
     but the assistant did, the assistant text seeds the chat name; this user message is still
     written so the chat is never empty.)
   - `resolvedChatId = createUserMessageUseCase(userMessage).chatId`. This creates a chat when
     `chatId == null` and returns its id (existing behavior of `CreateUserMessageUseCase`).
3. **Assistant turn** persisted only when `a`:
   - `messageRepository.saveMessage(Message(role = ASSISTANT, chatId = resolvedChatId,
     messageState = COMPLETE, modelType = ModelType.LIVE_VOICE, content = Content(text = assistantText)))`.
4. Return `resolvedChatId`.

### 3.6 Live Voice coordinator + UI state (Part D)

```
sealed interface LiveVoiceUiState {
    data object Idle : LiveVoiceUiState
    data object Connecting : LiveVoiceUiState
    data class Listening(val userCaption: String, val assistantCaption: String, val isMuted: Boolean, val micLevel: Float) : LiveVoiceUiState
    data class AssistantSpeaking(val userCaption: String, val assistantCaption: String, val isMuted: Boolean) : LiveVoiceUiState
    data class Error(val message: String) : LiveVoiceUiState
}
```

```
class LiveVoiceCoordinator(
    liveVoiceSessionUseCase: LiveVoiceSessionUseCase,
    persistVoiceTurnUseCase: PersistVoiceTurnUseCase,
    realtimeAudioCapture: RealtimeAudioCapturePort,
    streamingAudioPlayer: StreamingAudioPlayerPort,
    errorHandler: ViewModelErrorHandler,
    loggingPort: LoggingPort,
) {
    val state: StateFlow<LiveVoiceUiState>            // initial Idle
    fun start(scope: CoroutineScope, chatId: ChatId?)
    fun end(scope: CoroutineScope)
    fun toggleMute()
    fun cancel()                                      // stop without persisting (onCleared/new chat)
}
```

State-machine contract (verified by `LV-COORD-*` scenarios):
- `start` → `Connecting`; on `LiveVoiceSessionUseCase` failure → `Error(message)` (the failure
  message verbatim) and no capture/playback started.
- On `LiveVoiceEvent.Connected` → `Listening`; begin pumping `realtimeAudioCapture.pcm16Frames(rate)`
  into `session.sendAudio` while not muted.
- `AssistantAudioChunk` → `streamingAudioPlayer.enqueueChunk` + transition to `AssistantSpeaking`.
- `SpeechStarted` (barge-in) → `streamingAudioPlayer.stop()` immediately and return to `Listening`.
- Caption update contract: `*Delta(text)` **appends** `text` to that side's caption; `*Done(text)`
  **replaces** that side's caption with `text` (authoritative). This makes both the OpenAI
  incremental-delta stream and the xAI cumulative `…updated` stream (mapped to `Done`, §3.4.1)
  converge on the correct final caption. `end()` persists the final caption of each side.
- `toggleMute` flips `isMuted`; while muted the capture→`sendAudio` pump is paused (no frames sent).
- `end` finalizes captions, calls `persistVoiceTurnUseCase(chatId, finalUser, finalAssistant)`,
  closes the session, stops playback, returns to `Idle`.
- `cancel` closes session + stops playback + returns to `Idle` **without** persisting.
- `LiveVoiceEvent.Error` → `Error(message)` and stops capture/playback.

### 3.7 `selectedModeModelName` (Part D)

`ChatUiState` gains `liveVoiceState: LiveVoiceUiState = LiveVoiceUiState.Idle` and
`selectedModeModelName: String? = null`.

`ChatUiStateReducer.ReduceParams` gains `liveVoiceState: LiveVoiceUiState`. The reducer computes
`selectedModeModelName` from `params.modelDisplayNames` + `params.selectedMode`:
- `FAST` → `modelDisplayNames[ModelType.FAST]`
- `THINKING` → `modelDisplayNames[ModelType.THINKING]`
- `COUNCIL` → `modelDisplayNames[ModelType.FINAL_SYNTHESIS]`

A missing/empty map entry yields `null` (so the top bar shows mode name only).

### 3.8 UI slots (Part E)

`UniversalVoiceTrailingAction` gains `onLiveVoiceClick: (() -> Unit)? = null` and
`isLiveVoiceEnabled: Boolean = true`. When `onLiveVoiceClick != null` and the bar is in the
idle/blank state (`!hasSendableContent && !isGenerating && !isActiveVoiceInput`), it renders the
mic button followed by a live-voice button (`Icons.Default.GraphicEq`) to its right; when disabled
the button shows the `NeoDisabledActionTooltip` and does not invoke `onLiveVoiceClick`. Existing
call sites (e.g. studio) that omit `onLiveVoiceClick` render unchanged.

`ChatTopBar` gains `selectedMode: ChatModeUi`, `selectedModeModelName: String?`,
`onModeChange: (ChatModeUi) -> Unit`. Center column renders bold `selectedMode.getDisplayName`
and non-bold `selectedModeModelName` (omitted when null); tapping opens the mode `DropdownMenu`.

---

## 4. Data model / schema changes

1. New table `live_voice_providers` via `LiveVoiceProviderEntity` (clone of `tts_providers`,
   same sync columns `pending_sync`/`updated_at`/`deleted`).
2. New column `default_models.live_voice_provider_id` (nullable) + FK + index; the
   exactly-one-non-null `init` invariant now covers five id fields.
3. `@Database(version = 1)` → `@Database(version = 2)` with `LiveVoiceProviderEntity::class` and
   `abstract fun liveVoiceProviderDao()` registered. No SQL migration is written — the existing
   `fallbackToDestructiveMigration(dropAllTables = true)` recreates the schema.
4. `ModelType.LIVE_VOICE("live_voice")` added (enum value, `displayName()`, `description`).
5. `SensitiveFieldCatalog`: `LiveVoiceProviderEntity` Room sensitive fields
   `["displayName", "voiceName", "modelName", "baseUrl"]` and Firestore `live_voice_providers`
   fields `["display_name", "voice_name", "model_name", "base_url"]` (mirror `TtsProviderEntity`).

Authorization: this schema change is an explicit, ticket-authorized addition (Live Voice feature),
satisfying the `DATA_LAYER_RULES.md` schema-lock requirement; it is persistent provider config, not
transient UI state.

---

## 5. Ordered implementation tasks

Module dependency order: domain → data → inference → feature wiring → UI. Each task lists the
scenario IDs (from `test_spec.md`) it satisfies.

### Domain (`core:domain`)

- **T-A1** Add `LiveVoiceProviderId` + `LiveVoiceProviderAsset`; add `ApiProvider.supportsRealtimeVoice`.
  Scenarios: `LV-PROVIDER-FILTER-001`, `LV-PROVIDER-FILTER-002`.
- **T-A2** Add `ModelType.LIVE_VOICE` (value, `displayName`, `description`, excluded from `isTextModel`).
  Scenarios: `LV-MODELTYPE-001`, `LV-MODELTYPE-002`, `LV-MODELTYPE-003`.
- **T-A3** Add `LiveVoiceProviderRepositoryPort`; add `GetLiveVoiceProvidersUseCase`,
  `SaveLiveVoiceProviderUseCase`, `DeleteLiveVoiceProviderUseCase`; add `LiveVoiceProviderDraft`.
  Scenarios: `LV-SAVE-001`, `LV-SAVE-002`, `LV-SAVE-003`.
- **T-A4** Add `liveVoiceProviderId` to `DefaultModelAssignment`; add `liveVoiceAsset` to
  `ResolvedAssignedModelSelection`; extend `DefaultModelRepositoryPort.setDefault` +
  `SetDefaultModelUseCase` with `LIVE_VOICE` slot validation.
  Scenarios: `LV-SETDEFAULT-001`, `LV-SETDEFAULT-002`.
- **T-A5** Extend `ResolveAssignedModelSelectionUseCase` to resolve `LIVE_VOICE` → `liveVoiceAsset`.
  Scenarios: `LV-RESOLVE-001`, `LV-RESOLVE-002`.
- **T-A6** Add `SettingsLiveVoiceUseCases` + impl + `SettingsUseCases.liveVoice`.
  Scenarios: covered indirectly by `LV-SAVE-*` (wiring; no separate behavior assertion).
- **T-B1** Add `LiveVoicePort`, `LiveVoiceSession`, `LiveVoiceEvent` (content-equality for audio chunk).
  Scenarios: `LV-EVENT-EQ-001`.
- **T-B2** Add `RealtimeAudioCapturePort`.
  Scenarios: (interface only; exercised by `LV-COORD-*`).
- **T-B3** Add `LiveVoiceSessionUseCase` (resolve→key→openSession; typed failures).
  Scenarios: `LV-SESSION-001`, `LV-SESSION-002`, `LV-SESSION-003`, `LV-SESSION-004`.
- **T-B4** Add `PersistVoiceTurnUseCase` (user+assistant turns; on-demand chat creation; blank skip).
  Scenarios: `LV-PERSIST-001`, `LV-PERSIST-002`, `LV-PERSIST-003`, `LV-PERSIST-004`.

### Data (`core:data`)

- **T-DA1** Add `LiveVoiceProviderEntity` + `LiveVoiceProviderDao` + `LiveVoiceProviderRepositoryImpl`;
  register DAO/`@Binds` in `DataModule`; add catalog entries.
  Scenarios: `LV-REPO-001`, `LV-REPO-002`, `LV-REPO-003`.
- **T-DA2** Register entity + DAO in `EnclaveDatabase`, bump `version = 2`, update
  `EnclaveDatabaseMigrations` KDoc.
  Scenarios: `LV-DB-001`, `LV-DB-VERSION-001`.
- **T-DA3** Add `default_models.live_voice_provider_id` column + FK + index; update `init`;
  extend `DefaultModelAssignmentView`, `DefaultModelsDao.observeAllWithDetails`,
  `DefaultModelRepositoryImpl.setDefault`/`resolveAssignment`/`View.toDomain`.
  Scenarios: `LV-DB-002`, `LV-SETDEFAULT-001`.

### Inference (`feature:inference`)

- **T-C1** Add `LiveVoiceEventParsing` (pure JSON→event mappers; one `parseOpenAiRealtimeEvent`
  recognizes BOTH OpenAI and xAI `type` strings per §3.4.1, plus `parseGeminiLiveEvent`).
  Scenarios: `LV-PARSE-OAI-001`, `LV-PARSE-OAI-002`, `LV-PARSE-OAI-003`, `LV-PARSE-OAI-004`,
  `LV-PARSE-OAI-005`, `LV-PARSE-XAI-001`, `LV-PARSE-XAI-002`,
  `LV-PARSE-GEM-001`, `LV-PARSE-GEM-002`, `LV-PARSE-GEM-003`.
- **T-C2** Add `OpenAiRealtimeSession` (OkHttp WebSocket, OpenAI + xAI by base URL/model; uses T-C1 parser).
  Scenarios: `LV-PARSE-OAI-001`, `LV-PARSE-OAI-002`, `LV-PARSE-OAI-003`, `LV-PARSE-OAI-004`,
  `LV-PARSE-OAI-005`, `LV-PARSE-XAI-001`, `LV-PARSE-XAI-002`, `LV-ROUTE-001`.
- **T-C3** Add `GeminiLiveSession` (Gemini Live WebSocket; uses T-C1 parser).
  Scenarios: `LV-PARSE-GEM-001`, `LV-PARSE-GEM-002`, `LV-PARSE-GEM-003`, `LV-ROUTE-002`.
- **T-C4** Add `LiveVoicePortImpl` routing by `asset.provider` (OPENAI/XAI→OpenAi, GOOGLE→Gemini,
  else `Result.failure`).
  Scenarios: `LV-ROUTE-001`, `LV-ROUTE-002`, `LV-ROUTE-003`, `LV-MUT-001`.
- **T-C5** Add `RealtimeAudioCaptureImpl` (`AudioRecord` PCM16 + AEC/NS when available).
  Scenarios: (device-only; covered by `LV-COORD-*` via fake port).
- **T-C6** Add `LiveVoiceModule` (`@Binds LiveVoicePort` + `RealtimeAudioCapturePort`).
  Scenarios: (DI wiring; validated by compile + Hilt graph).

### Feature wiring (`feature:chat`)

- **T-D1** Add `LiveVoiceUiState`; add `LiveVoiceCoordinator` state machine.
  Scenarios: `LV-COORD-001`, `LV-COORD-002`, `LV-COORD-003`, `LV-COORD-004`, `LV-COORD-005`,
  `LV-COORD-006`, `LV-COORD-007`, `LV-COORD-008`.
- **T-D2** Add `liveVoiceState` + `selectedModeModelName` to `ChatUiState`; extend
  `ChatUiStateReducer` to compute `selectedModeModelName` and thread `liveVoiceState`.
  Scenarios: `LV-MODELNAME-001`, `LV-MODELNAME-002`, `LV-MODELNAME-003`, `LV-MODELNAME-004`.
- **T-D3** Wire `LiveVoiceCoordinator` into `ChatViewModel` (combine + `onStartLiveVoice`/
  `onEndLiveVoice`/`onToggleMute`; cancel in `onCleared`/`createNewChat`).
  Scenarios: `LV-COORD-006`, `LV-COORD-007` (cancel-no-persist asserted at coordinator level).

### Settings wiring (`feature:settings`)

- **T-A7** Add `LiveVoiceProviderAssetUiMapper` + `LiveVoiceProvidersCoordinator`; UI states in
  `SettingsModels`; delegate + VM handlers; assembler/factory/sheet flag wiring.
  Scenarios: `LV-SETTINGS-COORD-001`, `LV-SETTINGS-COORD-002`.
- **T-A8** Add `LiveVoiceProvidersBottomSheet` + `LiveVoiceConfigureScreen` + routes;
  `NeoNavRow("Live Voice")` + conditional sheet block in `SettingsScreen`.
  Scenarios: (Compose UI; validated by build + Compose preview, no JVM unit assertion).

### UI (`feature:chat`, `core:ui`)

- **T-E1** Add `onLiveVoiceClick`/`isLiveVoiceEnabled` slot to `UniversalVoiceTrailingAction`;
  reusable blocky waveform extension of `SoundWave`.
  Scenarios: (Compose UI; build + preview).
- **T-E2** Modify `InputBar` (remove mode picker; blank→`[mic][live-voice]`, text→Send; gate-disable).
  Scenarios: (Compose UI; gate logic asserted via reducer `hasLiveVoiceProviderAssigned`, see T-D2).
- **T-E3** Modify `ChatTopBar` (mode+model picker) + `ChatScreen` (pass params, render overlay).
  Scenarios: (Compose UI; build + preview).
- **T-E4** Add `LiveVoiceOverlay` composable.
  Scenarios: (Compose UI; build + preview).

> Note: the input bar's gate-disable depends on whether a Live Voice provider is assigned. Add a
> `hasLiveVoiceProviderAssigned: Boolean` to `ChatUiState` (computed in `ChatViewModel` from
> `getDefaultModels()` mirroring `hasTtsProviderAssignedFlow`) and assert it in the reducer/VM test
> `LV-GATE-001`/`LV-GATE-002`. This bool drives `isLiveVoiceEnabled` in the input bar.

- **T-D4** Add `hasLiveVoiceProviderAssigned` to `ChatUiState` + VM flow + reducer pass-through.
  Scenarios: `LV-GATE-001`, `LV-GATE-002`.

---

## 6. Drift and escalation rules for later phases

- **xAI realtime protocol — CONFIRMED (see §1 "xAI Realtime protocol" + §3.4.1).** xAI is
  OpenAI-Realtime-compatible; `OpenAiRealtimeSession` isolates provider differences to (a) the base
  WebSocket URL (`asset.baseUrl` / `ApiProvider.XAI.defaultBaseUrl()` = `https://api.x.ai/v1` →
  `wss://api.x.ai/v1/realtime?model=…`), (b) `asset.modelName` (default `grok-voice-think-fast-1.0`),
  and (c) the event-name dialect set absorbed by the shared `parseOpenAiRealtimeEvent` (cumulative
  `…transcription.updated`→`Done`; `response.output_audio.delta`/`…_transcript.*`). The remaining
  build-time check is narrow: confirm xAI's **assistant** transcript event names
  (`response.output_audio_transcript.delta/done` are the assumed mirror of the output-audio naming);
  if they differ, only the §3.4.1 type-string set changes — port/coordinator/UI stay fixed. If any
  divergence cannot be isolated to base URL/model + parser type-strings, STOP and escalate before
  adding a third session class.
- **Gemini Live message shapes:** Gemini's `BidiGenerateContent` setup/turn/audio-config shapes
  differ from OpenAI; confirm field names against Google's Live API docs. Keep the divergence inside
  `GeminiLiveSession` + the Gemini branch of `LiveVoiceEventParsing`.
- **Echo cancellation / barge-in:** `AcousticEchoCanceler`/`NoiseSuppressor` may be unavailable on a
  device. Degrade gracefully: when AEC is unavailable, the coordinator pauses capture while the
  assistant is speaking (mute-while-speaking) instead of relying on hardware cancellation. This is a
  runtime capability check in `RealtimeAudioCaptureImpl`, not a contract change.
- **BYOK over WSS:** The device connects directly to the provider over WSS with the user's key
  (consistent with the existing BYOK model — no backend proxy). Do not introduce a proxy.
- **Combine arity in `ChatViewModel`:** the `uiState` combine already uses the `Array<Any?>` vararg
  form; adding `liveVoiceState`/`hasLiveVoiceProviderAssigned`/`selectedModeModelName` inputs extends
  that array and the index constants in `reduceChatUiStateFromArgs` — keep the index constants in sync
  or the cast will throw at runtime.

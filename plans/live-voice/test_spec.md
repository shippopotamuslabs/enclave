# Live Voice — Test Spec (Gherkin)

Ticket: `live-voice`. Companion to `spec.md`. Every `Then` names a literal expected value,
observable state, enum case, or exact exception message. Expected values are derived from this
spec, never from production code.

## Test framework and run commands

- **Engine:** JUnit 5 (`org.junit.jupiter.api`, `useJUnitPlatform()` is set in each module's
  `build.gradle.kts`). New tests use `@Test`/`@BeforeEach`/`@DisplayName`/`@Nested` (no JUnit 4).
- **Flows:** Turbine (`app.cash.turbine`) for `StateFlow`/`Flow` event-by-event assertions.
- **Coroutines:** `kotlinx-coroutines-test` `runTest` + `StandardTestDispatcher`; JUnit 5
  `MainDispatcherExtension` for ViewModel/coordinator tests that touch `Dispatchers.Main`.
- **Mocks/Fakes:** Prefer Fakes (per `UNIT_TESTING.md`). MockK only for ports/SDK seams
  (existing `ResolveAssignedModelSelectionUseCaseTest` and `TtsProviderRepositoryImplTest` use
  `io.mockk` — mirror them).
- **Robolectric:** `RobolectricExtension` (`tech.apter.junit.jupiter.robolectric.RobolectricExtension`)
  + `@Config(sdk = [34])` for DB/migration tests, matching `EnclaveDatabaseMigrationTest`.

Run commands (verified scoped commands from `AGENTS.md` §4):

| Scope | Command |
|-------|---------|
| Domain unit tests | `./gradlew :core:domain:testDebugUnitTest` |
| Data unit tests | `./gradlew :core:data:testDebugUnitTest` |
| Inference unit tests | `./gradlew :feature:inference:testDebugUnitTest` |
| Chat unit tests | `./gradlew :feature:chat:testDebugUnitTest` |
| Settings unit tests | `./gradlew :feature:settings:testDebugUnitTest` |
| All unit tests | `./gradlew testDebugUnitTest` |
| Room/KSP schema smoke | `./gradlew :app:kspDebugKotlin` |
| App compile / Hilt graph | `./gradlew :app:assembleDebug` |

Single class example:
`./gradlew :feature:chat:testDebugUnitTest --tests "*LiveVoiceCoordinatorTest*"`.

---

## Scenario index

| ID | Title | Type | Module |
|----|-------|------|--------|
| LV-PROVIDER-FILTER-001 | OPENAI/GOOGLE/XAI support realtime voice | Happy | core:domain |
| LV-PROVIDER-FILTER-002 | ANTHROPIC/OPENROUTER/FAL do not support realtime voice | Edge | core:domain |
| LV-MODELTYPE-001 | LIVE_VOICE apiValue + fromApiValue round-trip | Happy | core:domain |
| LV-MODELTYPE-002 | LIVE_VOICE displayName + description literals | Happy | core:domain |
| LV-MODELTYPE-003 | LIVE_VOICE is not a text model | Edge | core:domain |
| LV-SAVE-001 | Save reuses sourceCredentialAlias | Happy | core:domain |
| LV-SAVE-002 | Save falls back to draft alias + forwards apiKey | Happy | core:domain |
| LV-SAVE-003 | Save propagates modelName + voiceName | Happy | core:domain |
| LV-SETDEFAULT-001 | LIVE_VOICE slot accepts only liveVoiceProviderId | Happy | core:domain |
| LV-SETDEFAULT-002 | LIVE_VOICE slot rejects missing provider id | Error | core:domain |
| LV-RESOLVE-001 | Resolve LIVE_VOICE returns liveVoiceAsset | Happy | core:domain |
| LV-RESOLVE-002 | Resolve LIVE_VOICE returns null when asset absent | Edge | core:domain |
| LV-EVENT-EQ-001 | AssistantAudioChunk uses content equality | Edge | core:domain |
| LV-SESSION-001 | Open session happy path | Happy | core:domain |
| LV-SESSION-002 | No provider assigned → failure message | Error | core:domain |
| LV-SESSION-003 | Asset present but key missing → failure message | Error | core:domain |
| LV-SESSION-004 | Selection present but liveVoiceAsset null → failure message | Error | core:domain |
| LV-PERSIST-001 | Both turns persist; existing chat reused | Happy | core:domain |
| LV-PERSIST-002 | No active chat → chat created from user turn | Happy | core:domain |
| LV-PERSIST-003 | Blank user text seeds chat from assistant text | Edge | core:domain |
| LV-PERSIST-004 | Both blank → nothing persisted, returns input chatId | Edge | core:domain |
| LV-REPO-001 | getLiveVoiceProviders maps entity→domain | Happy | core:data |
| LV-REPO-002 | save inserts entity + stores api key | Happy | core:data |
| LV-REPO-003 | save with blank alias defaults to lv-{id} | Edge | core:data |
| LV-DB-001 | live_voice_providers table exists | Happy | core:data |
| LV-DB-002 | default_models has live_voice_provider_id column | Happy | core:data |
| LV-DB-VERSION-001 | Database version is 2 | Happy | core:data |
| LV-PARSE-OAI-001 | OpenAI user transcript completed → UserTranscriptDone | Happy | feature:inference |
| LV-PARSE-OAI-002 | OpenAI audio delta → AssistantAudioChunk (decoded base64) | Happy | feature:inference |
| LV-PARSE-OAI-003 | OpenAI speech_started → SpeechStarted | Happy | feature:inference |
| LV-PARSE-OAI-004 | OpenAI error event → Error(message) | Error | feature:inference |
| LV-PARSE-OAI-005 | OpenAI transcript delta → UserTranscriptDelta (append) | Happy | feature:inference |
| LV-PARSE-XAI-001 | xAI cumulative transcription.updated → UserTranscriptDone (replace) | Edge | feature:inference |
| LV-PARSE-XAI-002 | xAI response.output_audio.delta → AssistantAudioChunk | Happy | feature:inference |
| LV-PARSE-GEM-001 | Gemini setupComplete → Connected | Happy | feature:inference |
| LV-PARSE-GEM-002 | Gemini inline audio data → AssistantAudioChunk | Happy | feature:inference |
| LV-PARSE-GEM-003 | Gemini output transcription → AssistantTranscriptDelta | Happy | feature:inference |
| LV-ROUTE-001 | OPENAI + XAI route to OpenAiRealtimeSession | Happy | feature:inference |
| LV-ROUTE-002 | GOOGLE routes to GeminiLiveSession | Happy | feature:inference |
| LV-ROUTE-003 | Unsupported provider → failure | Error | feature:inference |
| LV-COORD-001 | start → Connecting → Listening | Happy | feature:chat |
| LV-COORD-002 | Assistant audio chunk → AssistantSpeaking + enqueued | Happy | feature:chat |
| LV-COORD-003 | Barge-in SpeechStarted stops playback | Edge | feature:chat |
| LV-COORD-004 | Mute pauses capture (no frames sent) | Edge | feature:chat |
| LV-COORD-005 | end persists accumulated transcript turns | Happy | feature:chat |
| LV-COORD-006 | Session-open failure → Error state | Error | feature:chat |
| LV-COORD-007 | cancel closes session without persisting | Edge | feature:chat |
| LV-COORD-008 | Delta appends, Done replaces caption | Edge | feature:chat |
| LV-MODELNAME-001 | FAST mode → FAST model name | Happy | feature:chat |
| LV-MODELNAME-002 | THINKING mode → THINKING model name | Happy | feature:chat |
| LV-MODELNAME-003 | COUNCIL mode → FINAL_SYNTHESIS model name | Happy | feature:chat |
| LV-MODELNAME-004 | Missing map entry → null model name | Edge | feature:chat |
| LV-GATE-001 | LIVE_VOICE assigned → hasLiveVoiceProviderAssigned true | Happy | feature:chat |
| LV-GATE-002 | No LIVE_VOICE assignment → hasLiveVoiceProviderAssigned false | Edge | feature:chat |
| LV-SETTINGS-COORD-001 | Show sheet toggles isSheetOpen | Happy | feature:settings |
| LV-SETTINGS-COORD-002 | Start create seeds GOOGLE draft | Happy | feature:settings |
| LV-MUT-001 | Mutation defense: routing must read provider, not hardcode | Mutation | feature:inference |

---

## Happy-path scenarios

### LV-PROVIDER-FILTER-001 — realtime-capable providers
```gherkin
Scenario: ApiProvider.supportsRealtimeVoice is true for OpenAI, Google, xAI
  Given the ApiProvider enum
  When supportsRealtimeVoice is read for OPENAI, GOOGLE, and XAI
  Then OPENAI.supportsRealtimeVoice is true
  And GOOGLE.supportsRealtimeVoice is true
  And XAI.supportsRealtimeVoice is true
```

### LV-MODELTYPE-001 — apiValue round-trip
```gherkin
Scenario: LIVE_VOICE has apiValue "live_voice" and round-trips
  Given ModelType.LIVE_VOICE
  Then ModelType.LIVE_VOICE.apiValue equals "live_voice"
  And ModelType.fromApiValue("live_voice") equals ModelType.LIVE_VOICE
```

### LV-MODELTYPE-002 — display strings
```gherkin
Scenario: LIVE_VOICE display name and description are exact
  Then ModelType.LIVE_VOICE.displayName() equals "Live Voice"
  And ModelType.LIVE_VOICE.description equals "Realtime speech-to-speech voice conversation."
```

### LV-SAVE-001 — reuse source credential alias
```gherkin
Scenario: SaveLiveVoiceProviderUseCase reuses sourceCredentialAlias when set
  Given a LiveVoiceProviderDraft with provider OPENAI, voiceName "alloy",
        credentialAlias "", apiKey "", sourceCredentialAlias "openai"
  And a repository stub returning LiveVoiceProviderId("saved-id")
  When the use case is invoked
  Then the saved asset.credentialAlias equals "openai"
  And the forwarded apiKey is null
  And the result is success with value LiveVoiceProviderId("saved-id")
```

### LV-SAVE-002 — fallback alias + apiKey forwarded
```gherkin
Scenario: SaveLiveVoiceProviderUseCase falls back to draft alias and forwards apiKey
  Given a LiveVoiceProviderDraft with provider XAI, voiceName "eve",
        credentialAlias "xai-2", apiKey "sk-test", sourceCredentialAlias null
  When the use case is invoked
  Then the saved asset.credentialAlias equals "xai-2"
  And the forwarded apiKey equals "sk-test"
```

### LV-SAVE-003 — propagate model + voice
```gherkin
Scenario: SaveLiveVoiceProviderUseCase propagates modelName and voiceName
  Given a LiveVoiceProviderDraft with provider GOOGLE, voiceName "Puck",
        modelName "gemini-2.0-flash-live-001", credentialAlias "google", apiKey "k"
  When the use case is invoked
  Then the saved asset.modelName equals "gemini-2.0-flash-live-001"
  And the saved asset.voiceName equals "Puck"
```

### LV-SETDEFAULT-001 — LIVE_VOICE slot accepts only provider id
```gherkin
Scenario: SetDefaultModelUseCase persists a LIVE_VOICE assignment with only liveVoiceProviderId
  Given a fake DefaultModelRepository
  When setDefault is invoked with modelType LIVE_VOICE,
       localConfigId null, apiConfigId null, ttsProviderId null,
       mediaProviderId null, liveVoiceProviderId LiveVoiceProviderId("lv-1")
  Then the repository receives setDefault with liveVoiceProviderId LiveVoiceProviderId("lv-1")
  And all of localConfigId, apiConfigId, ttsProviderId, mediaProviderId are null
```

### LV-RESOLVE-001 — resolve LIVE_VOICE
```gherkin
Scenario: ResolveAssignedModelSelectionUseCase resolves LIVE_VOICE to liveVoiceAsset
  Given a DefaultModelAssignment with modelType LIVE_VOICE and
        liveVoiceProviderId LiveVoiceProviderId("lv-1")
  And GetLiveVoiceProvidersUseCase emits a list containing
        LiveVoiceProviderAsset(id=lv-1, displayName="OpenAI RT", provider=OPENAI,
        voiceName="alloy", modelName=null, baseUrl=null, credentialAlias="alias")
  When the use case is invoked with ModelType.LIVE_VOICE
  Then the result equals ResolvedAssignedModelSelection(liveVoiceAsset = that asset)
```

### LV-SESSION-001 — open session happy path
```gherkin
Scenario: LiveVoiceSessionUseCase opens a session when provider and key resolve
  Given ResolveAssignedModelSelectionUseCase(LIVE_VOICE) returns a selection whose
        liveVoiceAsset has credentialAlias "alias"
  And ApiKeyProviderPort.getApiKey("alias") returns "sk-live"
  And LiveVoicePort.openSession(asset, "sk-live") returns Result.success(fakeSession)
  When the use case is invoked
  Then the result is success and its value is fakeSession
```

### LV-PERSIST-001 — both turns, existing chat
```gherkin
Scenario: PersistVoiceTurnUseCase saves user then assistant turn into existing chat
  Given a fake CreateUserMessageUseCase that, for chatId ChatId("c1"),
        returns UserPromptResult(userMessageId=MessageId("u1"), chatId=ChatId("c1"))
  And a fake MessageRepository
  When invoked with chatId ChatId("c1"), userText "hello there", assistantText "hi back"
  Then a user Message with role USER and content.text "hello there" is created via CreateUserMessageUseCase
  And MessageRepository.saveMessage is called with a Message whose role is ASSISTANT,
      content.text is "hi back", modelType is ModelType.LIVE_VOICE, chatId is ChatId("c1")
  And the returned ChatId equals ChatId("c1")
```

### LV-PERSIST-002 — chat created on demand
```gherkin
Scenario: PersistVoiceTurnUseCase creates a chat when none is active
  Given a fake CreateUserMessageUseCase that, when the input message chatId is null,
        returns UserPromptResult(userMessageId=MessageId("u9"), chatId=ChatId("new-chat"))
  When invoked with chatId null, userText "start a session", assistantText "ready"
  Then the assistant Message saved to MessageRepository has chatId ChatId("new-chat")
  And the returned ChatId equals ChatId("new-chat")
```

### LV-REPO-001 — entity→domain mapping
```gherkin
Scenario: LiveVoiceProviderRepositoryImpl maps entities to domain models
  Given the DAO emits one LiveVoiceProviderEntity(
        id="1", displayName="OpenAI RT", provider=OPENAI, voiceName="alloy",
        modelName=null, baseUrl="https://api.openai.com", credentialAlias="alias-1")
  When getLiveVoiceProviders().first() is collected
  Then the result size is 1
  And result[0].id.value equals "1"
  And result[0].displayName equals "OpenAI RT"
  And result[0].provider equals ApiProvider.OPENAI
  And result[0].voiceName equals "alloy"
  And result[0].baseUrl equals "https://api.openai.com"
```

### LV-REPO-002 — save inserts + stores key
```gherkin
Scenario: saveLiveVoiceProvider upserts the entity and saves the API key
  Given a LiveVoiceProviderAsset(id=LiveVoiceProviderId("1"), displayName="xAI RT",
        provider=XAI, voiceName="eve", modelName=null, baseUrl="https://api.x.ai/v1",
        credentialAlias="alias-x") and apiKey "sk-test"
  When saveLiveVoiceProvider(asset, "sk-test") is invoked
  Then the returned id.value equals "1"
  And ApiKeyManager.save is called with alias "alias-x" and key "sk-test"
  And the DAO upsert is called exactly once
```

### LV-DB-001 — table exists
```gherkin
Scenario: live_voice_providers table is created
  Given an in-memory EnclaveDatabase built via Room
  When the table list is queried from sqlite_master
  Then a table named "live_voice_providers" exists
```

### LV-DB-002 — default_models column
```gherkin
Scenario: default_models has the live_voice_provider_id column
  Given an in-memory EnclaveDatabase built via Room
  When PRAGMA table_info('default_models') is read
  Then the column names contain "live_voice_provider_id"
```

### LV-DB-VERSION-001 — version bump
```gherkin
Scenario: Database schema version is 2
  Given an in-memory EnclaveDatabase built via Room
  When the openHelper writable database version is read
  Then the version equals 2
```

### LV-PARSE-OAI-001 — user transcript completed
```gherkin
Scenario: OpenAI conversation.item.input_audio_transcription.completed maps to UserTranscriptDone
  Given the JSON
    """
    {"type":"conversation.item.input_audio_transcription.completed","transcript":"turn on the lights"}
    """
  When parseOpenAiRealtimeEvent(json) is called
  Then the result equals LiveVoiceEvent.UserTranscriptDone("turn on the lights")
```

### LV-PARSE-OAI-002 — audio delta decoded
```gherkin
Scenario: OpenAI response.audio.delta maps to AssistantAudioChunk with decoded bytes
  Given the JSON
    """
    {"type":"response.audio.delta","delta":"AAEC"}
    """
  When parseOpenAiRealtimeEvent(json) is called
  Then the result is LiveVoiceEvent.AssistantAudioChunk
  And its pcm16 byte array equals [0, 1, 2]
```

### LV-PARSE-OAI-003 — speech started
```gherkin
Scenario: OpenAI input_audio_buffer.speech_started maps to SpeechStarted
  Given the JSON
    """
    {"type":"input_audio_buffer.speech_started"}
    """
  When parseOpenAiRealtimeEvent(json) is called
  Then the result equals LiveVoiceEvent.SpeechStarted
```

### LV-PARSE-OAI-005 — incremental user transcript delta
```gherkin
Scenario: OpenAI conversation.item.input_audio_transcription.delta maps to UserTranscriptDelta
  Given the JSON
    """
    {"type":"conversation.item.input_audio_transcription.delta","delta":"lights"}
    """
  When parseOpenAiRealtimeEvent(json) is called
  Then the result equals LiveVoiceEvent.UserTranscriptDelta("lights")
```

### LV-PARSE-XAI-002 — xAI output audio delta decoded
```gherkin
Scenario: xAI response.output_audio.delta maps to AssistantAudioChunk with decoded bytes
  Given the JSON
    """
    {"type":"response.output_audio.delta","delta":"AAEC"}
    """
  When parseOpenAiRealtimeEvent(json) is called
  Then the result is LiveVoiceEvent.AssistantAudioChunk
  And its pcm16 byte array equals [0, 1, 2]
```

### LV-PARSE-GEM-001 — setup complete
```gherkin
Scenario: Gemini setupComplete maps to Connected
  Given the JSON
    """
    {"setupComplete":{}}
    """
  When parseGeminiLiveEvent(json) is called
  Then the result equals LiveVoiceEvent.Connected
```

### LV-PARSE-GEM-002 — inline audio data
```gherkin
Scenario: Gemini serverContent inline audio data maps to AssistantAudioChunk
  Given the JSON
    """
    {"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm","data":"AAEC"}}]}}}
    """
  When parseGeminiLiveEvent(json) is called
  Then the result is LiveVoiceEvent.AssistantAudioChunk
  And its pcm16 byte array equals [0, 1, 2]
```

### LV-PARSE-GEM-003 — output transcription
```gherkin
Scenario: Gemini outputTranscription maps to AssistantTranscriptDelta
  Given the JSON
    """
    {"serverContent":{"outputTranscription":{"text":"hello"}}}
    """
  When parseGeminiLiveEvent(json) is called
  Then the result equals LiveVoiceEvent.AssistantTranscriptDelta("hello")
```

### LV-ROUTE-001 — OpenAI + xAI route to OpenAi session
```gherkin
Scenario Outline: LiveVoicePortImpl routes OpenAI and xAI to OpenAiRealtimeSession
  Given a LiveVoiceProviderAsset with provider <provider>
  When openSession(asset, "key") is invoked
  Then the returned session is an OpenAiRealtimeSession
  Examples:
    | provider |
    | OPENAI   |
    | XAI      |
```

### LV-ROUTE-002 — Google routes to Gemini session
```gherkin
Scenario: LiveVoicePortImpl routes GOOGLE to GeminiLiveSession
  Given a LiveVoiceProviderAsset with provider GOOGLE
  When openSession(asset, "key") is invoked
  Then the returned session is a GeminiLiveSession
```

### LV-COORD-001 — connect then listen
```gherkin
Scenario: Coordinator transitions Idle → Connecting → Listening
  Given a fake LiveVoicePort whose session events() will emit LiveVoiceEvent.Connected
  And LiveVoiceSessionUseCase returns success(fakeSession)
  When start(scope, chatId=null) is called and the session emits Connected
  Then state emits LiveVoiceUiState.Idle initially
  And then LiveVoiceUiState.Connecting
  And then a LiveVoiceUiState.Listening value with isMuted=false
```

### LV-COORD-002 — assistant audio
```gherkin
Scenario: Assistant audio chunk enqueues to player and sets AssistantSpeaking
  Given the coordinator is in Listening with a connected fake session and a fake StreamingAudioPlayer
  When the session emits LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(9, 9))
  Then the fake StreamingAudioPlayer received an enqueued chunk equal to [9, 9]
  And state is a LiveVoiceUiState.AssistantSpeaking value
```

### LV-COORD-005 — end persists turns
```gherkin
Scenario: end() persists accumulated user and assistant transcripts
  Given the session emitted UserTranscriptDone("hello") and AssistantTranscriptDone("hi")
  And the coordinator was started with chatId ChatId("c1")
  When end(scope) is called
  Then PersistVoiceTurnUseCase is invoked with chatId ChatId("c1"),
       userText "hello", assistantText "hi"
  And state returns to LiveVoiceUiState.Idle
```

### LV-MODELNAME-001 / 002 / 003 — model name per mode
```gherkin
Scenario Outline: Reducer computes selectedModeModelName from mode and display-name map
  Given a modelDisplayNames map of
        { FAST: "Grok Fast", THINKING: "Grok Think", FINAL_SYNTHESIS: "Grok Final" }
  And selectedMode <mode>
  When the reducer produces ChatUiState
  Then uiState.selectedModeModelName equals <expected>
  Examples:
    | mode     | expected     |
    | FAST     | "Grok Fast"  |
    | THINKING | "Grok Think" |
    | COUNCIL  | "Grok Final" |
```

### LV-GATE-001 — provider assigned
```gherkin
Scenario: hasLiveVoiceProviderAssigned is true when a LIVE_VOICE default exists
  Given getDefaultModels() emits a DefaultModelAssignment with modelType LIVE_VOICE
        and liveVoiceProviderId LiveVoiceProviderId("lv-1")
  When the ChatViewModel uiState is collected
  Then uiState.hasLiveVoiceProviderAssigned is true
```

### LV-SETTINGS-COORD-001 — show sheet
```gherkin
Scenario: onShowLiveVoiceProvidersSheet sets isSheetOpen
  Given a LiveVoiceProvidersTransientState with isSheetOpen=false in a MutableStateFlow
  When the coordinator's onShowLiveVoiceProvidersSheet(state, show=true) is called
  Then state.value.isSheetOpen is true
```

### LV-SETTINGS-COORD-002 — start create
```gherkin
Scenario: onStartCreateLiveVoiceProviderAsset seeds a GOOGLE draft
  Given a LiveVoiceProvidersTransientState in a MutableStateFlow
  When onStartCreateLiveVoiceProviderAsset(state) is called
  Then state.value.selectedAsset is null
  And state.value.assetDraft.provider equals ApiProvider.GOOGLE
```

---

## Error and edge-case scenarios

### LV-PROVIDER-FILTER-002 — non-realtime providers
```gherkin
Scenario: supportsRealtimeVoice is false for Anthropic, OpenRouter, fal.ai
  Then ANTHROPIC.supportsRealtimeVoice is false
  And OPENROUTER.supportsRealtimeVoice is false
  And FAL.supportsRealtimeVoice is false
```

### LV-MODELTYPE-003 — not a text model
```gherkin
Scenario: LIVE_VOICE is excluded from isTextModel
  Then ModelType.LIVE_VOICE.isTextModel is false
```

### LV-SETDEFAULT-002 — missing provider id rejected
```gherkin
Scenario: SetDefaultModelUseCase rejects a LIVE_VOICE assignment with no provider id
  When setDefault is invoked with modelType LIVE_VOICE and all five id fields null
  Then an IllegalArgumentException is thrown
  And its message equals "Live Voice slot requires a Live Voice provider assignment."
```

### LV-RESOLVE-002 — asset absent
```gherkin
Scenario: ResolveAssignedModelSelectionUseCase returns null when the assigned provider is gone
  Given a DefaultModelAssignment with modelType LIVE_VOICE and
        liveVoiceProviderId LiveVoiceProviderId("lv-missing")
  And GetLiveVoiceProvidersUseCase emits an empty list
  When the use case is invoked with ModelType.LIVE_VOICE
  Then the result is null
```

### LV-EVENT-EQ-001 — audio chunk content equality
```gherkin
Scenario: AssistantAudioChunk equality is content-based
  Given a = LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1, 2, 3))
  And b = LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1, 2, 3))
  Then a equals b is true
  And a.hashCode() equals b.hashCode()
```

### LV-SESSION-002 — no provider assigned
```gherkin
Scenario: LiveVoiceSessionUseCase fails when no Live Voice provider is assigned
  Given ResolveAssignedModelSelectionUseCase(LIVE_VOICE) returns null
  When the use case is invoked
  Then the result is a failure
  And the exception is IllegalStateException
  And its message equals "No Live Voice provider assigned."
```

### LV-SESSION-003 — key missing
```gherkin
Scenario: LiveVoiceSessionUseCase fails when the API key is missing
  Given the resolved liveVoiceAsset has displayName "OpenAI RT" and credentialAlias "alias"
  And ApiKeyProviderPort.getApiKey("alias") returns null
  When the use case is invoked
  Then the result is a failure
  And the exception is IllegalStateException
  And its message equals "API key not found for Live Voice provider: OpenAI RT"
```

### LV-SESSION-004 — selection without liveVoiceAsset
```gherkin
Scenario: LiveVoiceSessionUseCase fails when the selection has no liveVoiceAsset
  Given ResolveAssignedModelSelectionUseCase(LIVE_VOICE) returns
        ResolvedAssignedModelSelection(liveVoiceAsset = null)
  When the use case is invoked
  Then the result is a failure
  And the exception is IllegalStateException
  And its message equals "Assigned Live Voice selection is invalid."
```

### LV-PERSIST-003 — blank user seeds from assistant
```gherkin
Scenario: PersistVoiceTurnUseCase seeds the chat from assistant text when user text is blank
  Given a fake CreateUserMessageUseCase that returns
        UserPromptResult(userMessageId=MessageId("u2"), chatId=ChatId("seed-chat"))
        when invoked with a null-chat message
  When invoked with chatId null, userText "   ", assistantText "the answer is 42"
  Then CreateUserMessageUseCase receives a user Message whose content.text equals "the answer is 42"
  And the assistant Message saved has content.text "the answer is 42" and chatId ChatId("seed-chat")
  And the returned ChatId equals ChatId("seed-chat")
```

### LV-PERSIST-004 — both blank persists nothing
```gherkin
Scenario: PersistVoiceTurnUseCase persists nothing when both turns are blank
  Given fakes for CreateUserMessageUseCase and MessageRepository that record calls
  When invoked with chatId ChatId("c1"), userText "", assistantText "   "
  Then CreateUserMessageUseCase is never invoked
  And MessageRepository.saveMessage is never invoked
  And the returned ChatId equals ChatId("c1")
```

### LV-REPO-003 — blank alias default
```gherkin
Scenario: saveLiveVoiceProvider defaults a blank credential alias to lv-{id}
  Given a LiveVoiceProviderAsset with id LiveVoiceProviderId("abc"),
        credentialAlias "  " (blank), and a non-blank apiKey "k"
  When saveLiveVoiceProvider(asset, "k") is invoked
  Then the upserted entity.credentialAlias equals "lv-abc"
  And ApiKeyManager.save is called with alias "lv-abc"
```

### LV-PARSE-OAI-004 — error event
```gherkin
Scenario: OpenAI error event maps to Error with the server message
  Given the JSON
    """
    {"type":"error","error":{"message":"invalid_api_key"}}
    """
  When parseOpenAiRealtimeEvent(json) is called
  Then the result equals LiveVoiceEvent.Error("invalid_api_key")
```

### LV-PARSE-XAI-001 — xAI cumulative transcript replaces
```gherkin
Scenario: xAI cumulative conversation.item.input_audio_transcription.updated maps to UserTranscriptDone
  Given the JSON
    """
    {"type":"conversation.item.input_audio_transcription.updated","transcript":"turn on the"}
    """
  When parseOpenAiRealtimeEvent(json) is called
  Then the result equals LiveVoiceEvent.UserTranscriptDone("turn on the")
```
Note: xAI sends the full transcript-so-far on every `.updated` event, so it maps to
`UserTranscriptDone` (replace semantics, §3.4.1), not `UserTranscriptDelta` (append). This pins the
divergence from OpenAI's incremental `.delta` (LV-PARSE-OAI-005) and prevents the lazy implementation
that treats `.updated` as an append, which would duplicate text into the caption.

### LV-ROUTE-003 — unsupported provider
```gherkin
Scenario: LiveVoicePortImpl fails for a provider without realtime voice
  Given a LiveVoiceProviderAsset with provider ANTHROPIC
  When openSession(asset, "key") is invoked
  Then the result is a failure
  And its message equals "Live Voice not supported for provider: ANTHROPIC"
```

### LV-COORD-003 — barge-in
```gherkin
Scenario: SpeechStarted during assistant playback stops playback immediately
  Given the coordinator is in AssistantSpeaking with a fake StreamingAudioPlayer that has been started
  When the session emits LiveVoiceEvent.SpeechStarted
  Then the fake StreamingAudioPlayer.stop() was called
  And state is a LiveVoiceUiState.Listening value
```

### LV-COORD-004 — mute pauses capture
```gherkin
Scenario: Muting pauses microphone capture so no frames are sent
  Given the coordinator is in Listening with a fake RealtimeAudioCapture emitting frames
        and a fake session recording sendAudio calls
  When toggleMute() is called and the capture then emits byteArrayOf(7)
  Then the state's isMuted is true
  And the fake session received no sendAudio call after muting
```

### LV-COORD-006 — open failure
```gherkin
Scenario: A session-open failure surfaces as Error state
  Given LiveVoiceSessionUseCase returns
        Result.failure(IllegalStateException("No Live Voice provider assigned."))
  When start(scope, chatId=null) is called
  Then state emits LiveVoiceUiState.Connecting
  And then LiveVoiceUiState.Error("No Live Voice provider assigned.")
  And no microphone capture was started
```

### LV-COORD-007 — cancel does not persist
```gherkin
Scenario: cancel() closes the session and never persists turns
  Given the session emitted UserTranscriptDone("hi") and AssistantTranscriptDone("hello")
        while started with chatId ChatId("c1")
  When cancel() is called
  Then PersistVoiceTurnUseCase is never invoked
  And the fake session.close() was called
  And state returns to LiveVoiceUiState.Idle
```

### LV-COORD-008 — Delta appends, Done replaces caption
```gherkin
Scenario: User caption appends on Delta and is replaced (not appended) on Done
  Given a connected coordinator in LiveVoiceUiState.Listening
  When the session emits UserTranscriptDelta("turn on ") then UserTranscriptDelta("the lights")
  Then the Listening state's userCaption equals "turn on the lights"
  When the session emits UserTranscriptDone("turn on the lights, please")
  Then the Listening state's userCaption equals "turn on the lights, please"
```
This defends the Delta=append / Done=replace contract (§3.6) that the xAI cumulative-transcript
mapping (LV-PARSE-XAI-001) relies on. A mutation that appends on Done would yield
`"turn on the lightsturn on the lights, please"` and fail the final assertion.

### LV-MODELNAME-004 — missing entry
```gherkin
Scenario: selectedModeModelName is null when the display-name map lacks the slot
  Given an empty modelDisplayNames map and selectedMode FAST
  When the reducer produces ChatUiState
  Then uiState.selectedModeModelName is null
```

### LV-GATE-002 — no assignment
```gherkin
Scenario: hasLiveVoiceProviderAssigned is false when no LIVE_VOICE default exists
  Given getDefaultModels() emits a list with no LIVE_VOICE assignment
  When the ChatViewModel uiState is collected
  Then uiState.hasLiveVoiceProviderAssigned is false
```

---

## Mutation-defense

### Risk
The most plausible lazy/hardcoded `LiveVoicePortImpl` implementation ignores `asset.provider` and
always constructs the OpenAI-compatible session (because OpenAI is the first/most-common provider
and `OpenAiRealtimeSession` already handles xAI). Such an implementation passes every OpenAI/xAI
test but silently mis-routes Google. A weaker mutation hardcodes the routing `when` to a single
branch (`else -> OpenAiRealtimeSession`).

### LV-MUT-001 — routing must read the provider
```gherkin
Scenario: GOOGLE must not be routed to the OpenAI-compatible session
  Given a LiveVoiceProviderAsset with provider GOOGLE
  When LiveVoicePortImpl.openSession(asset, "key") is invoked
  Then the returned session is a GeminiLiveSession
  And the returned session is NOT an OpenAiRealtimeSession
```
This fails against the lazy implementation that returns `OpenAiRealtimeSession` for all providers
(the `is GeminiLiveSession` assertion fails and the `is not OpenAiRealtimeSession` assertion fails),
while passing only when routing genuinely branches on `asset.provider`. Paired with LV-ROUTE-001
(OPENAI/XAI → OpenAi) it pins both arms of the `when`, so collapsing either branch to a constant
fails at least one scenario.
```

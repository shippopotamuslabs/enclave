# FEAT-chat-other-model-selector — Behavioral Test Specification

## Test Framework and Run Commands

| Stack | Command |
|-------|---------|
| Domain | `./gradlew :core:domain:testDebugUnitTest --tests "*GetChatCapablePresetsUseCaseTest*" --tests "*GenerateChatResponseUseCase*Override*" --tests "*ActiveModelProviderImplTest*"` |
| Data | `./gradlew :core:data:testDebugUnitTest --tests "*ActiveModelProviderImplTest*"` |
| Core UI | `./gradlew :core:ui:testDebugUnitTest --tests "*AssignmentSelectionBottomSheetTest*"` |
| Chat | `./gradlew :feature:chat:testDebugUnitTest --tests "*ChatModeMapperTest*" --tests "*ChatResponseModeMapperTest*" --tests "*ChatInputCoordinatorOverrideTest*" --tests "*ChatUiStateReducerOverrideTest*" --tests "*ChatTopBarOtherEntryTest*" --tests "*InferenceOrchestratorSendTest*" --tests "*ChatViewModelActionsTest*"` |
| Settings regression | `./gradlew :feature:settings:testDebugUnitTest --tests "*AssignmentSelectionExtractionRegressionTest*"` |
| Full (pre-merge) | `./gradlew testDebugUnitTest` |

**Stack:** JUnit 5 (`@Test`, `@BeforeEach`, `@Nested`, `@DisplayName`) + Turbine for `Flow`/`StateFlow` + MockK only when Fakes are impractical. Prefer Fakes per `UNIT_TESTING.md`.

**Notation:** **S** = happy path, **E** = edge/error (invariant guard), **MD** = mutation-defense.

---

## Scenario Index

| ID | Summary |
|----|---------|
| S01 | Other> appears below Council in mode dropdown |
| S02 | Tapping Other> opens sheet; does NOT call onModeChange |
| S03 | Sheet shows Local Models and API Models tabs |
| S04 | Selecting local preset sets ephemeral local override |
| S05 | Selecting API preset sets ephemeral API override |
| S06 | Override used by inference (`generateChatResponse` receives `ChatResponseMode.Other`) |
| E01 | OTHER without override blocked at send boundary; never reaches `generateChatResponse` |
| S07 | setDefaultModel NOT called on override selection |
| S08 | Override cleared on new chat |
| S09 | Override cleared on selecting FAST |
| S10 | Override cleared on selecting THINKING |
| S11 | Override cleared on selecting COUNCIL |
| S12 | Top bar subtitle shows override display name |
| S13 | Top bar title shows "Other >" when OTHER active |
| S14 | Settings assignment sheet renders identically after extraction |
| S15 | Empty state when no chat-capable presets |
| S16 | ChatModeMapper OTHER↔OTHER exhaustive |
| S17 | Reducer excludes OTHER from modeModelNames |
| MD01 | FAST-slot fallback fails when API override selected |

---

## Section 1 — ChatTopBar (`:feature:chat`)

### S01 — Other> menu item order

```
Given ChatTopBar is composed with default mode FAST
When  the mode dropdown is expanded
Then  the menu item labels in order are ["Fast", "Thinking", "Council", "Other >"]
```

### S02 — Other> does not call onModeChange

```
Given ChatTopBar is composed with onModeChange = mockCallback
  And onOtherClick = otherCallback
When  the user taps the menu item labeled "Other >"
Then  otherCallback is invoked exactly 1 time
  And mockCallback is invoked exactly 0 times
```

### S13 — Top bar title when OTHER active

```
Given ChatTopBar is composed with selectedMode = ChatModeUi.OTHER
When  the top bar title row is read
Then  the title text equals "Other >"
```

---

## Section 2 — ChatScreen Sheet (`:feature:chat`)

### S02 — Sheet opens on Other>

```
Given ChatUiState.isModelOverrideSheetVisible = false
When  onOtherClick() is invoked on ChatViewModel
Then  ChatUiState.isModelOverrideSheetVisible == true
```

### S03 — Local and API tabs visible

```
Given ChatUiState.isModelOverrideSheetVisible = true
  And modelOverrideSheetLocalAssets contains LocalModelAssetUi(friendlyName="Qwen 3 8B", ...)
  And modelOverrideSheetApiAssets contains ApiModelAssetUi(displayName="OpenRouter", ...)
When  AssignmentSelectionBottomSheet is composed
Then  the tab row labels equal ["Local Models", "API Models"]
```

### S15 — Empty state

```
Given ChatUiState.isModelOverrideSheetVisible = true
  And modelOverrideSheetLocalAssets == emptyList()
  And modelOverrideSheetApiAssets == emptyList()
When  AssignmentSelectionContent is composed
Then  the visible body text equals "No options available."
  And the Confirm button enabled state == false
```

---

## Section 3 — ChatInputCoordinator (`:feature:chat`)

### S04 — Local override set

```
Given ChatInputCoordinator with selectedMode = ChatModeUi.FAST
  And selectedModelOverride == null
When  onModelOverrideSelected(
        ChatModelOverride.Local(
          configId = LocalModelConfigurationId("local-qwen-preset"),
          displayName = "Qwen 3 8B Fast",
        )
      )
Then  selectedModelOverride.value == ChatModelOverride.Local(
        configId = LocalModelConfigurationId("local-qwen-preset"),
        displayName = "Qwen 3 8B Fast",
      )
  And selectedMode.value == ChatModeUi.OTHER
```

### S05 — API override set

```
Given ChatInputCoordinator with selectedMode = ChatModeUi.FAST
When  onModelOverrideSelected(
        ChatModelOverride.Api(
          configId = ApiModelConfigurationId("api-grok-beta"),
          displayName = "Grok Beta",
        )
      )
Then  selectedModelOverride.value == ChatModelOverride.Api(
        configId = ApiModelConfigurationId("api-grok-beta"),
        displayName = "Grok Beta",
      )
  And selectedMode.value == ChatModeUi.OTHER
```

### S09 — Override cleared on FAST

```
Given ChatInputCoordinator with selectedMode = ChatModeUi.OTHER
  And selectedModelOverride = ChatModelOverride.Api(
        configId = ApiModelConfigurationId("api-grok-beta"),
        displayName = "Grok Beta",
      )
When  onModeChange(ChatModeUi.FAST)
Then  selectedModelOverride.value == null
  And selectedMode.value == ChatModeUi.FAST
```

### S10 — Override cleared on THINKING

```
Given selectedModelOverride is non-null
When  onModeChange(ChatModeUi.THINKING)
Then  selectedModelOverride.value == null
  And selectedMode.value == ChatModeUi.THINKING
```

### S11 — Override cleared on COUNCIL

```
Given selectedModelOverride is non-null
When  onModeChange(ChatModeUi.COUNCIL)
Then  selectedModelOverride.value == null
  And selectedMode.value == ChatModeUi.COUNCIL
```

---

## Section 4 — ChatUiStateReducer (`:feature:chat`)

### S12 — Override display name in top bar subtitle

```
Given ReduceParams with selectedMode = ChatModeUi.OTHER
  And selectedModelOverride = ChatModelOverride.Api(
        configId = ApiModelConfigurationId("api-grok-beta"),
        displayName = "Grok Beta",
      )
When  ChatUiStateReducer.reduce(params) is called
Then  result.uiState.selectedModeModelName == "Grok Beta"
```

### S17 — OTHER excluded from modeModelNames

```
Given ReduceParams with selectedMode = ChatModeUi.OTHER
  And modelDisplayNames = { ModelType.FAST -> "Slot Fast Name", ModelType.THINKING -> "Slot Think Name" }
When  ChatUiStateReducer.reduce(params) is called
Then  result.uiState.modeModelNames.containsKey(ChatModeUi.OTHER) == false
  And result.uiState.modeModelNames[ChatModeUi.FAST] == "Slot Fast Name"
  And result.uiState.modeModelNames[ChatModeUi.THINKING] == "Slot Think Name"
  And result.uiState.modeModelNames[ChatModeUi.COUNCIL] == null
```

---

## Section 5 — ChatResponseModeMapper (`:feature:chat`)

### E01 — OTHER without override is not sendable

```
Given selectedMode = ChatModeUi.OTHER
  And selectedModelOverride = null
When  ChatResponseModeMapper.fromSelection(selectedMode, override = null) is called
Then  an IllegalArgumentException is thrown
  And the exception message equals "Cannot send in OTHER mode without a selected preset"
  And generateChatResponse is invoked exactly 0 times
```

### S06 — Slot modes produce slot response modes (no override field)

```
When  ChatResponseModeMapper.fromSelection(ChatModeUi.FAST, override = null) is called
Then  the result == ChatResponseMode.Fast

When  ChatResponseModeMapper.fromSelection(ChatModeUi.THINKING, override = null) is called
Then  the result == ChatResponseMode.Thinking

When  ChatResponseModeMapper.fromSelection(ChatModeUi.COUNCIL, override = null) is called
Then  the result == ChatResponseMode.Council
```

---

## Section 6 — ChatModeMapper (`:feature:chat`)

### S16 — Exhaustive OTHER mapping

```
When  ChatModeUi.OTHER.toDomain() is called
Then  the result == Mode.OTHER

When  Mode.OTHER.toUi() is called
Then  the result == ChatModeUi.OTHER
```

---

## Section 7 — GetChatCapablePresetsUseCase (`:core:domain`)

### S03 — Returns chat-capable local and API assets

```
Given FakeLocalModelRepository contains:
        LocalModelAsset(displayName="Qwen 3 8B", isImageGenerationCapable=false, configurations=[preset-A])
      And LocalModelAsset(displayName="SD 1.5", isImageGenerationCapable=true, configurations=[preset-B])
  And FakeApiModelRepository contains credential "OpenRouter" with config modelId="anthropic/claude-3.5-sonnet"
When  GetChatCapablePresetsUseCase.invoke().first() is collected
Then  presets.localAssets.map { it.metadata.displayName } == listOf("Qwen 3 8B")
  And presets.localAssets.none { it.metadata.displayName == "SD 1.5" } == true
  And presets.apiAssets.single().credentials.displayName == "OpenRouter"
```

### S15 — Empty catalog

```
Given FakeLocalModelRepository contains zero non-deleted text assets
  And FakeApiModelRepository contains zero chat-capable API configurations
When  GetChatCapablePresetsUseCase.invoke().first() is collected
Then  presets.localAssets == emptyList()
  And presets.apiAssets == emptyList()
```

---

## Section 8 — GenerateChatResponse / Inference (`:core:domain`, `:feature:chat`)

### S06 — Override threaded as ChatResponseMode.Other

```
Given ChatViewModel with selectedMode = ChatModeUi.OTHER
  And selectedModelOverride = ChatModelOverride.Api(
        configId = ApiModelConfigurationId("api-grok-beta"),
        displayName = "Grok Beta",
      )
When  onSendMessage() completes user-message submission
Then  captured generateChatResponse call has responseMode ==
        ChatResponseMode.Other(
          modelOverride = ChatModelOverride.Api(
            configId = ApiModelConfigurationId("api-grok-beta"),
            displayName = "Grok Beta",
          ),
        )
  And captured generateChatResponse call does NOT have a separate mode parameter
```

### S06b — FAST mode never carries override

```
Given ChatViewModel with selectedMode = ChatModeUi.FAST
  And selectedModelOverride = null
When  onSendMessage() completes user-message submission
Then  captured generateChatResponse call has responseMode == ChatResponseMode.Fast
```

### S07 — setDefaultModel never called

```
Given SettingsAssignmentsDelegate.setDefaultModel is a mock
When  ChatViewModel.onModelOverrideSelected(
        localId = null,
        apiId = ApiModelConfigurationId("api-grok-beta"),
        ttsId = null,
        mediaId = null,
        liveVoiceId = null,
      ) completes
Then  setDefaultModel mock invocation count == 0
```

### S08 — Override cleared on new chat

```
Given ChatInputCoordinator with selectedMode = ChatModeUi.OTHER
  And selectedModelOverride = ChatModelOverride.Api(
        configId = ApiModelConfigurationId("api-grok-beta"),
        displayName = "Grok Beta",
      )
When  ChatViewModel.createNewChat() completes
Then  selectedModelOverride.value == null
  And selectedMode.value != ChatModeUi.OTHER
```

---

## Section 9 — Settings Extraction Regression (`:feature:settings`, `:core:ui`)

### S14 — Assignment sheet structure unchanged

```
Given AssignmentSelectionContent composed with Preview fixture data:
        slotLabel = "Main"
        localAssets = MockSettingsData.localModels
        apiAssets = MockSettingsData.apiModels
        ttsAssets = emptyList()
        liveVoiceAssets = emptyList()
        mediaAssets = emptyList()
When  the sheet header and footer are inspected
Then  the header title text == "Assign Model to Main"
  And the tab row first label == "Local Models"
  And the tab row second label == "API Models"
  And the cancel button text == "Cancel"
  And the confirm button text == "Confirm"
```

---

## Section 10 — Mutation Defense

### MD01 — FAST slot fallback must fail

**Risk:** Lazy implementation routes `ChatResponseMode.Other` through `executeSingleModelMode(modelType = ModelType.FAST)` instead of resolving the explicit override config.

```
Given default slot FAST is assigned to LocalModelConfigurationId("local-fast-slot")
  And responseMode = ChatResponseMode.Other(
        modelOverride = ChatModelOverride.Api(
          configId = ApiModelConfigurationId("api-grok-beta"),
          displayName = "Grok Beta",
        ),
      )
  And activeModelProvider.getActiveConfiguration(ModelType.FAST) returns config named "Fast Slot Model"
  And activeModelProvider.getActiveConfigurationByOverride(responseMode.modelOverride) returns config named "Grok Beta"
When  generateChatResponse is invoked with responseMode = responseMode
Then  the inference executor uses configuration with name == "Grok Beta"
  And the inference executor does NOT use configuration with name == "Fast Slot Model"
```

**Expected test failure against lazy impl:** Assertion `inference configuration name == "Grok Beta"` fails with actual `"Fast Slot Model"`.

---

## Task ↔ Scenario Coverage Matrix

| Task | Scenario IDs |
|------|----------------|
| 1.1 ChatModelOverride | S04, S05, S06 |
| 1.2 ChatResponseMode | S06, E01 |
| 1.3 Mode.OTHER (UI only) | S16 |
| 1.4–1.5 ChatModeMapper | S16 |
| 1.6–1.7 ChatResponseModeMapper | E01, S06 |
| 1.8–1.9 GetChatCapablePresetsUseCase | S03, S15 |
| 1.10–1.11 ActiveModelProviderByOverride | S04, S05, S06 |
| 2.1–2.2 GenerateChatResponse responseMode | S06, S06b, S07, MD01 |
| 3.1–3.5 core:ui extraction | S14, S03 |
| 4.1–4.2 ChatInputCoordinator | S04, S05, S08–S11 |
| 4.3–4.4 ChatUiStateReducer | S12, S17 |
| 5.1 ChatModeUi.OTHER resources | S01, S13 |
| 5.2–5.3 ChatTopBar | S01, S02, S13 |
| 5.4 ChatScreen sheet | S02, S03, S15 |
| 5.5 ChatViewModel wiring | S02–S08, S12 |
| 5.6 InferenceOrchestrator | S06, MD01 |
| 5.7 ChatViewModelActions | S08, S09 |
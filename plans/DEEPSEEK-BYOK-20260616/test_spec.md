# DEEPSEEK-BYOK-20260616 Test Spec

**Ticket ID:** `DEEPSEEK-BYOK-20260616`

## Test Framework And Run Commands

**Frameworks:**

- JUnit 5 (`@Test`, `@BeforeEach`, `@ExtendWith`)
- MockK for OpenAI SDK client/stream mocks in inference tests
- Robolectric + `ApplicationProvider` for settings composable tests (`@ExtendWith(RobolectricExtension::class)`, `@Config(sdk = [34])`)
- Turbine only where Flow assertions are required (not expected for this ticket)

**Focused commands during iteration:**

```bash
./gradlew :core:domain:testDebugUnitTest --tests "*ApiProviderTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*ApiProviderModelPolicyTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*TelemetrySummaryBuilderTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*DeepSeekRequestPolicyTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*DeepSeekInferenceServiceImplTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*BaseOpenAiSdkInferenceServiceTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*InferenceFactoryImplTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*ApiModelCatalogRepositoryImplTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*OpenAiTelemetryCaptureTest*"
./gradlew :feature:settings:testDebugUnitTest --tests "*DeepSeekCredentialsFieldsTest*"
./gradlew :feature:settings:testDebugUnitTest --tests "*ByokPresetDefaultsTest*"
```

**Required validation before TASK_STATUS COMPLETE:**

```bash
./gradlew :core:domain:testDebugUnitTest :feature:inference:testDebugUnitTest :feature:settings:testDebugUnitTest
./gradlew :core:domain:assembleDebug :feature:inference:assembleDebug :feature:settings:assembleDebug
./gradlew ktlintCheck
./gradlew detekt
```

---

## Scenario Index

| Scenario ID | Area | Summary |
| --- | --- | --- |
| SCN-DOMAIN-001 | Domain | `DEEPSEEK` display name is `DeepSeek`. |
| SCN-DOMAIN-002 | Domain | Default base URL is `https://api.deepseek.com`. |
| SCN-DOMAIN-003 | Domain | `DEEPSEEK` supports chat. |
| SCN-DOMAIN-004 | Domain | `DEEPSEEK` does not expose discovery model pricing. |
| SCN-POLICY-001 | Policy | `deepseek-reasoner` classified as reasoner model. |
| SCN-POLICY-002 | Policy | `deepseek-chat` classified as chat model. |
| SCN-POLICY-003 | Policy | `deepseek-v4-pro` classified as V4 family. |
| SCN-POLICY-004 | Policy | Reasoner parameter support hides temperature and top_p. |
| SCN-POLICY-005 | Policy | Chat parameter support exposes temperature and top_p. |
| SCN-POLICY-006 | Policy | V4 reasoning-engaged hides temperature and top_p. |
| SCN-POLICY-007 | Policy | Model discovery enabled for `DEEPSEEK`. |
| SCN-POLICY-008 | Policy | Reasoning effort maps `XHIGH` → `"max"`, `LOW` → `"high"`. |
| SCN-TELEMETRY-001 | Telemetry | `providerLabel(DEEPSEEK)` returns `"DeepSeek"`. |
| SCN-TELEMETRY-002 | Telemetry | `TelemetryProviderKey.fromLabel("DeepSeek")` is `DEEPSEEK`. |
| SCN-TELEMETRY-003 | Telemetry | `DEEPSEEK` reports cache-inclusive input. |
| SCN-HOOK-001 | Regression | OpenAI still sends `max_completion_tokens`. |
| SCN-HOOK-002 | Regression | Z.ai still rounds sampling to two decimals. |
| SCN-REGRESSION-001 | Regression | OpenAI with tools still sets `tool_choice`. |
| SCN-REGRESSION-002 | Regression | DeepSeek requests omit `promptCacheKey`. |
| SCN-INFER-001 | Inference | `deepseek-reasoner` strips `reasoning_content` on assistant replay. |
| SCN-INFER-002 | Inference | `deepseek-reasoner` omits tools from request params. |
| SCN-INFER-003 | Inference | `deepseek-reasoner` omits temperature from request params. |
| SCN-INFER-004 | Inference | `deepseek-chat` includes `tool_choice` when tools available. |
| SCN-INFER-005 | Inference | `deepseek-v4-pro` replays exact `reasoning_content` on tool-call turn. |
| SCN-INFER-006 | Inference | `deepseek-v4-pro` uses `max_tokens` not `max_completion_tokens`. |
| SCN-INFER-007 | Inference | `deepseek-v4-pro` sends `thinking` extra body when reasoning engaged. |
| SCN-INFER-008 | Inference | `deepseek-v4-pro` omits `tool_choice` when reasoning engaged with tools. |
| SCN-INFER-009 | Inference | `deepseek-v4-pro` supplies `"."` assistant content when blank on tool replay. |
| SCN-INFER-010 | Inference | Stream `reasoning_content` delta emits `InferenceEvent.Thinking`. |
| SCN-FACTORY-001 | Factory | Factory builds `DeepSeekInferenceServiceImpl` for `DEEPSEEK` credentials. |
| SCN-FACTORY-002 | Factory | Blank base URL resolves to `https://api.deepseek.com`. |
| SCN-DISCOVERY-001 | Discovery | `fetchModels(DEEPSEEK)` uses OpenAI-compatible `models.list`. |
| SCN-UI-001 | Settings | Chat provider dropdown includes `DeepSeek`. |
| SCN-UI-002 | Settings | DeepSeek base URL field is read-only official endpoint. |
| SCN-UI-003 | Settings | Legacy custom base URL migrates to official endpoint on load. |
| SCN-UI-004 | Settings | Selecting DeepSeek provider sets base URL to official endpoint. |
| SCN-UI-005 | Settings | Fresh DeepSeek preset uses generic temperature `0.7`. |
| SCN-ONBOARD-001 | Onboarding | Onboarding chat provider list includes `DeepSeek`. |
| SCN-INFER-EDGE-001 | Edge | OpenRouter-prefixed model ids normalize without false reasoner match. |
| SCN-INFER-EDGE-002 | Edge | V4 with `reasoningBudget = 0` restores `tool_choice`. |
| SCN-INFER-EDGE-003 | Edge | V4 with `reasoningBudget = 0` omits `thinking` extra body. |
| SCN-MUT-001 | Mutation defense | Using `max_completion_tokens` for DeepSeek fails SCN-INFER-006. |
| SCN-MUT-002 | Mutation defense | Attaching `reasoning_content` on reasoner replay fails SCN-INFER-001. |
| SCN-MUT-003 | Mutation defense | Omitting exact reasoning replay on V4 tool turn fails SCN-INFER-005. |
| SCN-MUT-004 | Mutation defense | Adding `promptCacheKey` for DeepSeek fails SCN-REGRESSION-002. |
| SCN-MUT-005 | Mutation defense | Editable DeepSeek base URL field fails SCN-UI-002. |

---

## Happy Path Scenarios

### SCN-DOMAIN-001: DEEPSEEK Display Name

Given `ApiProvider.DEEPSEEK`
When `displayName` is read
Then it equals `"DeepSeek"`

### SCN-DOMAIN-002: DEEPSEEK Default Base URL

Given `ApiProvider.DEEPSEEK`
When `defaultBaseUrl()` is called
Then the result equals `"https://api.deepseek.com"`

### SCN-DOMAIN-003: DEEPSEEK Supports Chat

Given `ApiProvider.DEEPSEEK`
When `supportsChat` is read
Then it equals `true`

### SCN-DOMAIN-004: DEEPSEEK Discovery Pricing Hidden

Given `ApiProvider.DEEPSEEK`
When `exposesDiscoveryModelPricing` is read
Then it equals `false`

### SCN-POLICY-001: DeepSeek Reasoner Classifier

Given `modelId = "deepseek-reasoner"`
When `ApiProviderModelPolicy.isDeepSeekReasonerModel(modelId)` is evaluated
Then the result equals `true`
And `ApiProviderModelPolicy.supportsDeepSeekClientTools(modelId)` equals `false`
And `ApiProviderModelPolicy.shouldStripReasoningContentOnAssistantReplay(modelId)` equals `true`

### SCN-POLICY-002: DeepSeek Chat Classifier

Given `modelId = "deepseek-chat"`
When `ApiProviderModelPolicy.isDeepSeekChatModel(modelId)` is evaluated
Then the result equals `true`
And `ApiProviderModelPolicy.supportsDeepSeekClientTools(modelId)` equals `true`
And `ApiProviderModelPolicy.shouldStripReasoningContentOnAssistantReplay(modelId)` equals `false`

### SCN-POLICY-003: DeepSeek V4 Family Classifier

Given `modelId = "deepseek-v4-pro"`
When `ApiProviderModelPolicy.isDeepSeekV4FamilyModel(modelId)` is evaluated
Then the result equals `true`
And `ApiProviderModelPolicy.isDeepSeekDirectReasoningEngaged(modelId, reasoningBudget = 2048)` equals `true`
And `ApiProviderModelPolicy.isDeepSeekDirectReasoningEngaged(modelId, reasoningBudget = 0)` equals `false`

### SCN-POLICY-004: Reasoner Parameter Support Matrix

Given `provider = ApiProvider.DEEPSEEK` and `modelId = "deepseek-reasoner"`
When `ApiProviderModelPolicy.parameterSupport(provider, modelId)` is read
Then `supportsTemperature` equals `false`
And `supportsTopP` equals `false`
And `supportsReasoningEffort` equals `false`
And `supportsMaxTokens` equals `true`

### SCN-POLICY-005: Chat Parameter Support Matrix

Given `provider = ApiProvider.DEEPSEEK` and `modelId = "deepseek-chat"`
When `ApiProviderModelPolicy.parameterSupport(provider, modelId)` is read
Then `supportsTemperature` equals `true`
And `supportsTopP` equals `true`
And `supportsMaxTokens` equals `true`

### SCN-POLICY-006: V4 Reasoning Engaged Parameter Support

Given `provider = ApiProvider.DEEPSEEK`, `modelId = "deepseek-v4-pro"`, and preset `reasoningBudget = 2048`
When `ApiProviderModelPolicy.parameterSupport(provider, modelId)` is evaluated in settings metadata path with reasoning engaged
Then `supportsTemperature` equals `false`
And `supportsTopP` equals `false`
And `supportsMaxTokens` equals `true`

### SCN-POLICY-007: Model Discovery Enabled

Given `provider = ApiProvider.DEEPSEEK`
When `ApiProviderModelPolicy.supportsModelDiscovery(provider)` is called
Then the result equals `true`

### SCN-POLICY-008: DeepSeek Reasoning Effort Mapping

Given `modelId = "deepseek-v4-pro"` and `reasoningBudget = 1024`
When `ApiProviderModelPolicy.deepSeekReasoningEffortForApi(ApiReasoningEffort.LOW)` is called
Then the result equals `"high"`
When `ApiProviderModelPolicy.deepSeekReasoningEffortForApi(ApiReasoningEffort.XHIGH)` is called
Then the result equals `"max"`

### SCN-TELEMETRY-001: Inference Telemetry Provider Label

Given `InferenceTelemetrySupport.providerLabel("DEEPSEEK")`
When the label is read
Then it equals `"DeepSeek"`

### SCN-TELEMETRY-002: Telemetry Provider Key From Label

Given `TelemetryProviderKey.fromLabel("DeepSeek")`
When the key is read
Then it equals `TelemetryProviderKey.DEEPSEEK`
When `TelemetryProviderKey.fromLabel("DEEPSEEK")` is read
Then it equals `TelemetryProviderKey.DEEPSEEK`

### SCN-TELEMETRY-003: DeepSeek Cache Input Accounting

Given `TelemetryProviderKey.DEEPSEEK`
When `reportsCacheExclusiveInput` is read
Then it equals `false`

### SCN-HOOK-001: OpenAI Retains max_completion_tokens

Given `ApiInferenceServiceImpl` (provider `OPENAI`) with `modelId = "gpt-4o"` and `maxTokens = 1024`
When `completeSingleTurn` builds `ChatCompletionCreateParams`
Then `maxCompletionTokens()` is present with value `1024`
And additional body property `max_tokens` is absent

### SCN-HOOK-002: Z.ai Sampling Unaffected

Given `ZaiInferenceServiceImpl` with `temperature = 0.123456f`
When `toApiTemperature` is invoked
Then the result equals `0.12`

### SCN-REGRESSION-001: OpenAI Tool Choice Preserved

Given OpenAI service with `modelId = "gpt-4o"`, one available tool, and `allowToolCall = true`
When `completeSingleTurn` builds params
Then `toolChoice()` is present
And it is not `NONE`

### SCN-REGRESSION-002: DeepSeek Omits promptCacheKey

Given `DeepSeekInferenceServiceImpl` with `modelId = "deepseek-chat"`, `chatId = ChatId("chat-deepseek-1")`, and `reasoningBudget = 0`
When `completeSingleTurn` builds params
Then `promptCacheKey()` is absent or empty
And no `X-Session-Id` header is added solely for DeepSeek routing

### SCN-INFER-001: Reasoner Strips reasoning_content On Replay

Given `DeepSeekInferenceServiceImpl` with `modelId = "deepseek-reasoner"`
And assistant metadata `{"provider_reasoning":{"reasoning_content":"hidden chain"},"raw_tool_calls":[]}`
When `completeSingleTurn` maps session messages to wire params
Then the assistant message additional property `reasoning_content` is absent

### SCN-INFER-002: Reasoner Omits Tools

Given `DeepSeekInferenceServiceImpl` with `modelId = "deepseek-reasoner"`
And `availableTools` contains one tool definition
When `completeSingleTurn` builds params
Then `tools()` is empty or absent
And `toolChoice()` is absent

### SCN-INFER-003: Reasoner Omits Temperature

Given `DeepSeekInferenceServiceImpl` with `modelId = "deepseek-reasoner"`
And `GenerationOptions(temperature = 0.7f, reasoningBudget = 0)`
When `completeSingleTurn` builds params
Then `temperature()` is absent
And `topP()` is absent

### SCN-INFER-004: DeepSeek Chat Includes Tool Choice

Given `DeepSeekInferenceServiceImpl` with `modelId = "deepseek-chat"`
And `availableTools` is non-empty and `allowToolCall = true`
When `completeSingleTurn` builds params
Then `toolChoice()` is present
And `tools()` size equals `1`

### SCN-INFER-005: V4 Replays Exact reasoning_content On Tool Turn

Given `DeepSeekInferenceServiceImpl` with `modelId = "deepseek-v4-pro"`
And `GenerationOptions(reasoningBudget = 2048)`
And assistant metadata:
```json
{
  "provider_reasoning": {"reasoning_content": "need tool output first"},
  "raw_tool_calls": [{"id": "call_ds_1", "name": "web_search", "arguments_json": "{\"q\":\"test\"}"}]
}
```
When `completeSingleTurn` builds params
Then assistant additional property `reasoning_content` equals `"need tool output first"`

### SCN-INFER-006: V4 Uses max_tokens Field

Given `DeepSeekInferenceServiceImpl` with `modelId = "deepseek-v4-pro"` and `maxTokens = 4096`
And `GenerationOptions(reasoningBudget = 2048)`
When `completeSingleTurn` builds params
Then additional body property `max_tokens` equals `4096`
And `maxCompletionTokens()` is absent

### SCN-INFER-007: V4 Thinking Extra Body

Given `DeepSeekInferenceServiceImpl` with `modelId = "deepseek-v4-pro"`
And `GenerationOptions(reasoningBudget = 1024)`
When `completeSingleTurn` builds params
Then additional body property `thinking` equals `{"type":"enabled"}`

### SCN-INFER-008: V4 Omits tool_choice When Reasoning Engaged

Given `DeepSeekInferenceServiceImpl` with `modelId = "deepseek-v4-pro"`
And `GenerationOptions(reasoningBudget = 2048)`
And `availableTools` is non-empty and `allowToolCall = true`
When `completeSingleTurn` builds params
Then `tools()` is non-empty
And `toolChoice()` is absent

### SCN-INFER-009: V4 Supplies Placeholder Content On Blank Tool Replay

Given `DeepSeekInferenceServiceImpl` with `modelId = "deepseek-v4-pro"`
And `GenerationOptions(reasoningBudget = 2048)`
And assistant metadata with `reasoning_content` present, blank `content`, and one `raw_tool_calls` entry
When `completeSingleTurn` builds params
Then assistant `content()` equals `"."`

### SCN-INFER-010: Stream reasoning_content Emits Thinking

Given `DeepSeekInferenceServiceImpl` processing one SSE chunk whose delta additional properties include `reasoning_content = "step one"`
When `processStreamEvent` runs
Then one `InferenceEvent.Thinking` is emitted with chunk `"step one"`

### SCN-FACTORY-001: Factory Routes DeepSeek Provider

Given API credentials `provider = DEEPSEEK`, `baseUrl = null`, preset `modelId = "deepseek-chat"`
When `InferenceFactoryImpl.withInferenceService` resolves the inner port
Then the uncached service class name contains `DeepSeekInferenceServiceImpl`

### SCN-FACTORY-002: Factory Resolves Official Base URL

Given API credentials `provider = DEEPSEEK`, `baseUrl = "   "`, and API key `"ds-secret"`
When the factory obtains the OpenAI client
Then `openAiClientProvider.getClient` is called with `baseUrl = "https://api.deepseek.com"`

### SCN-DISCOVERY-001: DeepSeek Model Discovery

Given `apiKey = "ds-key"` and `baseUrl = "https://api.deepseek.com"`
And OpenAI client `models.list` returns one model `id = "deepseek-chat"`
When `ApiModelCatalogRepositoryImpl.fetchModels(provider = DEEPSEEK, apiKey, baseUrl)` runs
Then `models.size` equals `1`
And `models[0].id` equals `"deepseek-chat"`
And `openAiClientProvider.getClient(apiKey = "ds-key", baseUrl = "https://api.deepseek.com")` was invoked

### SCN-UI-001: Provider Dropdown Includes DeepSeek

Given `ApiProvider.entries.filter(ApiProvider::supportsChat)`
When the list is materialized for BYOK provider selection
Then it contains `ApiProvider.DEEPSEEK`
And `ApiProvider.DEEPSEEK.displayName` equals `"DeepSeek"`

### SCN-UI-002: Read Only Base URL Field

Given `CredentialsConfigurationForm` rendered with `asset.provider = DEEPSEEK` and `asset.baseUrl = "https://api.deepseek.com"`
When the base URL `NeoOutlinedTextField` is inspected
Then `readOnly` equals `true`
And displayed value equals `"https://api.deepseek.com"`
And label text equals `"Base URL"`

### SCN-UI-003: Legacy Base URL Migration

Given `asset.provider = DEEPSEEK` and `asset.baseUrl = "https://proxy.example/v1"`
When `DeepSeekBaseUrlField` is composed and `LaunchedEffect` runs
Then `onAssetChange` receives `baseUrl = "https://api.deepseek.com"`

### SCN-UI-004: Provider Selection Sets Official URL

Given `asset.provider = OPENAI` and `asset.baseUrl = "https://api.openai.com/v1"`
When user selects provider `DEEPSEEK` from `ProviderSelectionField`
Then updated asset has `provider = DEEPSEEK`
And `baseUrl = "https://api.deepseek.com"`

### SCN-UI-005: Fresh Preset Generic Temperature

Given `freshApiPresetDraft(ApiProvider.DEEPSEEK)`
When the draft is read
Then `temperature` equals `0.7`
And `isFreshPreset` equals `true`

### SCN-ONBOARD-001: Onboarding Lists DeepSeek

Given onboarding `ModelChoiceStep` API provider chips sourced from `ApiProvider.entries.filter(ApiProvider::supportsChat)`
When the provider list is built
Then it contains an entry with display name `"DeepSeek"`

---

## Error And Edge Path Scenarios

### SCN-INFER-EDGE-001: OpenRouter Prefix Normalization (documented in POLICY tests)

Given `modelId = "openrouter/deepseek/deepseek-r1"`
When classifiers normalize via `substringAfterLast('/')`
Then `isDeepSeekReasonerModel` evaluates on normalized id `deepseek-r1` and equals `false`
And policy does not treat unknown ids as `deepseek-reasoner` unless exact match

### SCN-INFER-EDGE-002: V4 Non Reasoning Mode Restores Tool Choice

Given `DeepSeekInferenceServiceImpl` with `modelId = "deepseek-v4-pro"`
And `GenerationOptions(reasoningBudget = 0)`
And `availableTools` is non-empty and `allowToolCall = true`
When `completeSingleTurn` builds params
Then `toolChoice()` is present

### SCN-INFER-EDGE-003: V4 Non Reasoning Mode Omits Thinking Body

Given `DeepSeekInferenceServiceImpl` with `modelId = "deepseek-v4-pro"`
And `GenerationOptions(reasoningBudget = 0)`
When `completeSingleTurn` builds params
Then additional body property `thinking` is absent

---

## Mutation Defense Scenarios

### SCN-MUT-001: max_completion_tokens On DeepSeek Must Fail

Given a lazy implementation that leaves `applyMaxOutputTokens` as the base default (`maxCompletionTokens`)
And `DeepSeekInferenceServiceImpl` with `modelId = "deepseek-v4-pro"`, `maxTokens = 4096`, `reasoningBudget = 2048`
When `completeSingleTurn` builds params
Then `maxCompletionTokens()` is present
And SCN-INFER-006 fails because `max_tokens` body property is absent

### SCN-MUT-002: Reasoning Attach On Reasoner Must Fail

Given a lazy implementation that always calls `putAdditionalProperty("reasoning_content", …)` in `toAssistantChatParam`
And `modelId = "deepseek-reasoner"` with metadata containing `provider_reasoning.reasoning_content = "leak"`
When wire params are built
Then assistant `reasoning_content` is present
And SCN-INFER-001 fails

### SCN-MUT-003: Missing Exact Replay On V4 Tool Turn Must Fail

Given a lazy implementation where `shouldAttachProviderReasoningContent` returns `false` for all models
And `modelId = "deepseek-v4-pro"` with tool-call metadata and `reasoningBudget = 2048`
When wire params are built
Then assistant `reasoning_content` is absent
And SCN-INFER-005 fails

### SCN-MUT-004: promptCacheKey On DeepSeek Must Fail

Given a lazy implementation that adds `ApiProvider.DEEPSEEK -> builder.promptCacheKey(conversationId)` in `applyPromptCacheRouting`
And `chatId = ChatId("chat-99")`
When DeepSeek params are built
Then `promptCacheKey()` equals `"chat-99"`
And SCN-REGRESSION-002 fails

### SCN-MUT-005: Editable Base URL Must Fail

Given a lazy UI implementation that renders the generic editable `NeoOutlinedTextField` for `DEEPSEEK`
When the base URL field is inspected
Then `readOnly` equals `false`
And SCN-UI-002 fails

---

## Task To Scenario Mapping (Implementation Gate)

| Task | Scenario IDs |
| --- | --- |
| T01 | SCN-DOMAIN-001, SCN-DOMAIN-002, SCN-DOMAIN-003, SCN-DOMAIN-004, SCN-ONBOARD-001, SCN-UI-001 |
| T02 | SCN-POLICY-001 … SCN-POLICY-008, SCN-INFER-EDGE-001 |
| T03 | SCN-TELEMETRY-001, SCN-TELEMETRY-002, SCN-TELEMETRY-003 |
| T04 | SCN-HOOK-001, SCN-HOOK-002, SCN-REGRESSION-001, SCN-REGRESSION-002 |
| T05 | SCN-INFER-001 … SCN-INFER-010, SCN-INFER-EDGE-002, SCN-INFER-EDGE-003, SCN-MUT-001, SCN-MUT-002, SCN-MUT-003 |
| T06 | SCN-FACTORY-001, SCN-FACTORY-002, SCN-DISCOVERY-001, SCN-MUT-004 |
| T07 | SCN-UI-002, SCN-UI-003, SCN-UI-004, SCN-UI-005, SCN-MUT-005 |
| T08 | SCN-ONBOARD-001 |
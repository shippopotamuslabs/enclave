# BYOK-MODEL-ON-PRESET Test Spec

**Ticket ID:** `BYOK-MODEL-ON-PRESET`

## Test Framework And Run Commands

**Frameworks:**

- JUnit 5 (`@Test`, `@BeforeEach`, `@ExtendWith`)
- MockK for ports/DAOs
- Robolectric + `ApplicationProvider` for Room DAO/repository tests (`@ExtendWith(RobolectricExtension::class)`, `@Config(sdk = [34])`)
- Turbine where Flow assertions are required

**Focused commands during iteration:**

```bash
./gradlew :core:domain:testDebugUnitTest --tests "*SaveApiProviderDraftUseCaseTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*SaveApiPresetUseCaseTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*TestApiProviderConnectionUseCaseTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*SetDefaultModelUseCaseTest*"
./gradlew :core:data:testDebugUnitTest --tests "*ApiModelRepositoryImplTest*"
./gradlew :core:data:testDebugUnitTest --tests "*ActiveModelProviderImplTest*"
./gradlew :core:data:testDebugUnitTest --tests "*ApiCredentialsDaoTest*"
./gradlew :core:data:testDebugUnitTest --tests "*ApiModelConfigurationsDaoTest*"
./gradlew :core:data:testDebugUnitTest --tests "*ApiCredentialsSyncHandlerTest*"
./gradlew :core:data:testDebugUnitTest --tests "*ApiModelConfigSyncHandlerTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*InferenceFactoryImplTest*"
./gradlew :feature:settings:testDebugUnitTest --tests "*ByokProvidersCoordinatorTest*"
./gradlew :feature:settings:testDebugUnitTest --tests "*SettingsViewModelTest*"
```

**Required validation before TASK_STATUS COMPLETE:**

```bash
./gradlew :feature:settings:testDebugUnitTest :core:domain:testDebugUnitTest :core:data:testDebugUnitTest
./gradlew :feature:inference:testDebugUnitTest
./gradlew :core:data:assembleDebug :feature:inference:assembleDebug :feature:settings:assembleDebug
./gradlew :app:kspDebugKotlin
./gradlew ktlintCheck
./gradlew detekt
```

---

## Scenario Index

| Scenario ID | Area | Summary |
| --- | --- | --- |
| SCN-DOMAIN-001 | Domain | `ApiCredentials` has no `modelId` or `isMultimodal`. |
| SCN-DOMAIN-002 | Domain | `ApiModelConfiguration` requires `modelId` and `isMultimodal`. |
| SCN-DRAFT-001 | Domain | `ApiProviderDraft` is credential-only; `ApiPresetDraft` carries model fields. |
| SCN-SIGNATURE-001 | Identity | Same key + provider + baseUrl dedupes regardless of preset model IDs. |
| SCN-SIGNATURE-002 | Identity | Different base URLs produce different signatures for same key. |
| SCN-PORT-001 | Port | `findMatchingCredentials` has no `modelId` parameter. |
| SCN-SCHEMA-001 | Schema | Fresh v8 DB: `api_credentials` has no `model_id` column. |
| SCN-SCHEMA-002 | Schema | Fresh v8 DB: `api_model_configurations` has `model_id` and `is_multimodal`. |
| SCN-DAO-001 | DAO | `ApiCredentialsDao` round-trip without model columns. |
| SCN-DAO-002 | DAO | `ApiModelConfigurationsDao` round-trip `model_id` / `is_multimodal`. |
| SCN-REPO-001 | Repository | `saveCredentials` persists credentials without model fields. |
| SCN-REPO-002 | Repository | `saveConfiguration` persists `modelId` and `isMultimodal`. |
| SCN-REPO-003 | Repository | `findMatchingCredentials` matches DeepSeek and Kimi presets under one credential. |
| SCN-REPO-004 | Repository | Signature hash excludes `modelId`. |
| SCN-SYNC-001 | Firestore | Credentials `toFirestore` omits `model_id` / `is_multimodal`. |
| SCN-SYNC-002 | Firestore | Preset `toFirestore` includes `model_id` / `is_multimodal`. |
| SCN-SYNC-003 | Firestore | Credentials `fromFirestore` ignores legacy model fields if present. |
| SCN-SYNC-004 | Firestore | Preset `fromFirestore` restores `model_id` / `is_multimodal`. |
| SCN-USECASE-SAVE-ASSET-001 | Domain | New asset save creates alias from provider only. |
| SCN-USECASE-SAVE-ASSET-002 | Domain | Link existing credential when only provider+url+key match. |
| SCN-USECASE-SAVE-ASSET-003 | Domain | Initial preset has empty `modelId`. |
| SCN-USECASE-SAVE-PRESET-001 | Domain | Save preset persists `modelId` on configuration. |
| SCN-USECASE-SAVE-PRESET-002 | Domain | Save preset persists `isMultimodal` on configuration. |
| SCN-USECASE-TEST-001 | Domain | Test connection succeeds when catalog fetch succeeds. |
| SCN-USECASE-TEST-002 | Domain | Test connection fails when catalog fetch throws. |
| SCN-USECASE-TEST-003 | Domain | Test connection does not require `modelId`. |
| SCN-USECASE-VISION-001 | Domain | Vision default requires multimodal **preset**. |
| SCN-USECASE-DELETION-001 | Domain | Vision reassignment lists only multimodal presets. |
| SCN-USECASE-AUTO-001 | Domain | Auto-assign vision picks first multimodal preset. |
| SCN-ACTIVE-001 | Provider | Active API config `modelId` from preset row. |
| SCN-ACTIVE-002 | Provider | Active API config `isMultimodal` from preset row. |
| SCN-INFERENCE-001 | Inference | Factory builds OpenAI service with config `modelId` `"gpt-4o"`. |
| SCN-INFERENCE-002 | Inference | `ServiceIdentity.Api.modelId` equals config, not credential. |
| SCN-UI-MAPPER-001 | Settings | Asset UI mapper omits credential `modelId`. |
| SCN-UI-MAPPER-002 | Settings | Config UI mapper includes `modelId` / `isMultimodal`. |
| SCN-UI-DISCOVER-001 | Settings | Discover Models not composable on credential form. |
| SCN-UI-DISCOVER-002 | Settings | Preset form hosts model selection field. |
| SCN-UI-VISION-001 | Settings | Vision switch binds to preset draft. |
| SCN-UI-TEST-001 | Settings | Test button shows Success + check icon. |
| SCN-UI-TEST-002 | Settings | Test failure shows snackbar message. |
| SCN-UI-TEST-003 | Settings | Provider change resets connection test to IDLE. |
| SCN-UI-CRED-001 | Settings | Reusable credential subtitle is provider label only. |
| SCN-UI-SAVE-001 | Settings | Credential save enabled without `modelId`. |
| SCN-UI-SAVE-002 | Settings | Preset save disabled when `modelId` blank. |
| SCN-UI-COORD-001 | Settings | Coordinator calls `TestApiProviderConnectionUseCase`. |
| SCN-UI-COORD-002 | Settings | Fetch models uses `presetDraft.modelId`. |
| SCN-UI-COORD-003 | Settings | Save credentials draft has no `modelId`. |
| SCN-UI-STATE-001 | Settings | Editor maps `connectionTestStatus` SUCCESS. |
| SCN-UI-SHEET-001 | Settings | Asset list subtitle excludes model id. |
| SCN-UI-SHEET-002 | Settings | Preset row shows model id. |
| SCN-UI-ASSIGN-001 | Settings | Model assignment subtitle uses preset model. |
| SCN-MUT-001 | Mutation defense | Signature including `modelId` fails SCN-SIGNATURE-001. |
| SCN-MUT-002 | Mutation defense | `ActiveModelProviderImpl` reading credential model fails SCN-ACTIVE-001. |
| SCN-MUT-003 | Mutation defense | `InferenceFactoryImpl` using credential model fails SCN-INFERENCE-001. |
| SCN-MUT-004 | Mutation defense | Credential form retaining Discover Models fails SCN-UI-DISCOVER-001. |

---

## Happy Path Scenarios

### SCN-DOMAIN-001: ApiCredentials Has No Model Fields

Given `ApiCredentials` constructed with `displayName = "My DeepSeek"`, `provider = DEEPSEEK`, `baseUrl = "https://api.deepseek.com"`, `credentialAlias = "deepseek-main"`
When the instance is inspected
Then it has no property `modelId`
And it has no property `isMultimodal`

### SCN-DOMAIN-002: ApiModelConfiguration Carries Model Fields

Given `ApiModelConfiguration` with `modelId = "deepseek-chat"`, `isMultimodal = false`, `displayName = "Chat"`
When the instance is read
Then `modelId` equals `"deepseek-chat"`
And `isMultimodal` equals `false`

### SCN-DRAFT-001: Draft Types Split Asset And Preset Concerns

Given `ApiProviderDraft` with `displayName = "My OpenAI"`, `provider = OPENAI`, `apiKey = "sk-test"`
When the draft is constructed
Then it has no field `modelId`
And `ApiPresetDraft` with `modelId = "gpt-4o"`, `isMultimodal = true` compiles and retains both values

### SCN-SIGNATURE-001: Dedupe Ignores Preset Model Identity

Given existing credential saved with `provider = "DEEPSEEK"`, `baseUrl = "https://api.deepseek.com"`, `apiKey = "same-key"`
And preset A uses `modelId = "deepseek-chat"`
And preset B uses `modelId = "kimi-k2"`
When `findMatchingCredentials(provider = DEEPSEEK, baseUrl = "https://api.deepseek.com", apiKey = "same-key")` runs
Then the returned credential `id` equals the existing credential `id`
And at most one `api_credentials` row exists for signature input `"DEEPSEEK|https://api.deepseek.com|same-key"`

### SCN-SIGNATURE-002: Different Base URL Yields Different Signature

Given `buildApiCredentialsIdentitySignature(provider = "OPENAI", baseUrl = "https://api.openai.com/v1", apiKey = "sk-1")` produces `sigA`
And `buildApiCredentialsIdentitySignature(provider = "OPENAI", baseUrl = "https://proxy.example/v1", apiKey = "sk-1")` produces `sigB`
When `sigA` and `sigB` are compared
Then `sigA` does not equal `sigB`

### SCN-PORT-001: Repository Port Omits Model From findMatchingCredentials

Given the `ApiModelRepositoryPort` Kotlin source
When the `findMatchingCredentials` function signature is inspected
Then it contains parameters `provider`, `baseUrl`, `apiKey`, `sourceCredentialAlias`
And it does not contain parameter `modelId`

### SCN-SCHEMA-001: Credentials Table Without Model Columns

Given a fresh Room database at version `8` created from `EnclaveDatabase`
When `PRAGMA table_info(api_credentials)` is executed
Then the result does not contain column name `model_id`
And the result does not contain column name `is_multimodal`

### SCN-SCHEMA-002: Preset Table With Model Columns

Given a fresh Room database at version `8`
When `PRAGMA table_info(api_model_configurations)` is executed
Then the result contains column `model_id` with type `TEXT` and `notnull = 1`
And the result contains column `is_multimodal` with `notnull = 1`

### SCN-DAO-001: ApiCredentialsDao Round-Trip

Given `ApiCredentialsEntity` with `id = "cred-1"`, `display_name = "PCENC:v1:…"`, `provider = OPENAI`, `base_url = "PCENC:v1:…"`, `credential_alias = "openai-main"`
When insert and `getById("cred-1")` run
Then the loaded entity has the same `provider` and `credential_alias`
And the loaded entity has no `modelId` property

### SCN-DAO-002: ApiModelConfigurationsDao Round-Trip Model Fields

Given parent credential `cred-1` exists
And configuration entity with `id = "preset-1"`, `api_credentials_id = "cred-1"`, `model_id = "gpt-4o"`, `is_multimodal = 1`
When upsert and `getById("preset-1")` run
Then `model_id` equals `"gpt-4o"`
And `is_multimodal` equals `true`

### SCN-REPO-001: saveCredentials Without Model Fields

Given `ApiCredentials(displayName = "Test", provider = OPENAI, baseUrl = "https://api.openai.com/v1", credentialAlias = "my_key")`
When `saveCredentials(credentials, apiKey = "sk-test")` completes
Then inserted entity has `displayName` encrypted form matching `"Test"`
And inserted entity has `provider = OPENAI`
And inserted entity does not set `model_id` or `is_multimodal` (columns absent)

### SCN-REPO-002: saveConfiguration With Model Fields

Given `ApiModelConfiguration(apiCredentialsId = credId, displayName = "Fast", modelId = "gpt-4o", isMultimodal = true, maxTokens = 4096, contextWindow = 128000)`
When `saveConfiguration(config)` completes
Then persisted entity has `model_id = "gpt-4o"`
And persisted entity has `is_multimodal = true`

### SCN-REPO-003: Two Presets One Credential Same Key

Given credential saved for `provider = DEEPSEEK`, `baseUrl = "https://api.deepseek.com"`, `apiKey = "shared"`
And preset saved with `modelId = "deepseek-chat"`
When second preset saved with `modelId = "kimi-k2"` on matched credential via `findMatchingCredentials`
Then both configurations reference the same `api_credentials_id`
And `api_credentials` row count for that signature equals `1`

### SCN-REPO-004: apiKeySignature Excludes ModelId

Given `saveCredentials` with `apiKey = "sk-test"`, `provider = OPENAI`, `baseUrl = "https://api.openai.com/v1"`
When stored `api_key_signature` is compared to `buildApiCredentialsIdentitySignature(provider = "OPENAI", baseUrl = "https://api.openai.com/v1", apiKey = "sk-test")`
Then they are equal
And signature does not change when only a preset `model_id` changes

### SCN-SYNC-001: Credentials Firestore Payload Excludes Model

Given `ApiCredentialsEntity` ready for sync
When `ApiCredentialsSyncHandler.toFirestore(local)` runs
Then the map does not contain key `"model_id"`
And the map does not contain key `"is_multimodal"`

### SCN-SYNC-002: Preset Firestore Payload Includes Model

Given `ApiModelConfigurationEntity` with `model_id = "claude-sonnet-4-20250514"`, `is_multimodal = false`
When `ApiModelConfigSyncHandler.toFirestore(local)` runs
Then the map contains `"model_id"` → `"claude-sonnet-4-20250514"`
And the map contains `"is_multimodal"` → `false`

### SCN-SYNC-003: Credentials fromFirestore Ignores Legacy Model Fields

Given Firestore document map containing `"model_id" = "legacy-model"`, `"is_multimodal" = true`, plus required credential fields
When `fromFirestore(doc)` builds `ApiCredentialsEntity`
Then entity has no `modelId` field populated from document
And domain mapping does not expose `modelId` on credentials

### SCN-SYNC-004: Preset fromFirestore Restores Model Fields

Given Firestore document with `"model_id" = "grok-4"`, `"is_multimodal" = true`
When `ApiModelConfigSyncHandler.fromFirestore(doc)` runs
Then entity `model_id` equals `"grok-4"`
And entity `is_multimodal` equals `true`

### SCN-USECASE-SAVE-ASSET-001: New Asset Alias From Provider Only

Given no existing alias `openai`
When `SaveApiProviderDraftUseCase` saves new draft `displayName = "Work OpenAI"`, `provider = OPENAI`, `apiKey = "sk-1"`
Then `persistedAsset.credentials.credentialAlias` equals `"openai"`

### SCN-USECASE-SAVE-ASSET-002: Link Existing Credential Without Model Match

Given existing asset credentials `id = "42"`, `displayName = "Shared Key"`, same provider/url/key as draft
When draft save runs with `displayName = "New Label"`, different intended preset model than any stored preset
Then `persistedAsset.credentials.id` equals `"42"`
And `linkedExistingAssetDisplayName` equals `"Shared Key"`
And `saveApiCredentialsUseCase` is not invoked

### SCN-USECASE-SAVE-ASSET-003: Initial Preset Empty Model

Given new asset save succeeds
When `createdPreset` is returned
Then `createdPreset.modelId` equals `""`
And `createdPreset.isMultimodal` equals `false`

### SCN-USECASE-SAVE-PRESET-001: Save Preset Model Id

Given parent credential exists
When `SaveApiPresetUseCase` saves draft with `modelId = "gpt-4o"`, `displayName = "Creative"`
Then repository receives `ApiModelConfiguration` with `modelId = "gpt-4o"`

### SCN-USECASE-SAVE-PRESET-002: Save Preset Multimodal Flag

Given parent credential exists
When draft has `isMultimodal = true`, `modelId = "gpt-4o"`
Then saved configuration has `isMultimodal = true`

### SCN-USECASE-TEST-001: Test Connection Success

Given `FetchApiProviderModelsUseCase` returns list containing `DiscoveredApiModel(id = "gpt-4o")`
When `TestApiProviderConnectionUseCase(provider = OPENAI, currentApiKey = "sk-valid", credentialAlias = null, baseUrl = null)` runs
Then result is `Result.success(Unit)`

### SCN-USECASE-TEST-002: Test Connection Failure

Given `FetchApiProviderModelsUseCase` throws exception with message `"HTTP 401"`
When `TestApiProviderConnectionUseCase` runs with invalid key
Then result is failure
And failure message contains `"401"`

### SCN-USECASE-TEST-003: Test Connection Without Model

Given `FetchApiProviderModelsUseCase` is invoked
When `TestApiProviderConnectionUseCase` runs with no `modelId` anywhere in its parameters
Then `FetchApiProviderModelsUseCase` is still called exactly once
And success does not depend on any preset row existing

### SCN-USECASE-VISION-001: Vision Default Requires Multimodal Preset

Given API preset `config-1` with `isMultimodal = false` linked to valid credentials
When `SetDefaultModelUseCase(modelType = VISION, apiConfigId = config-1)` runs
Then invocation throws `IllegalArgumentException`
And message contains `"multimodal"`

### SCN-USECASE-DELETION-001: Reassignment Vision Filter Per Preset

Given API asset with preset `text-only` `isMultimodal = false` and preset `vision` `isMultimodal = true`
When `PrepareModelDeletionUseCase` builds candidates with `requireVisionCompatibility = true`
Then candidates include `vision` preset only
And candidates do not include `text-only`

### SCN-USECASE-AUTO-001: Auto Assign Vision Uses Multimodal Preset

Given API asset credentials not multimodal at asset level
And preset `p1` `isMultimodal = true`, `id = "vision-preset"`
When `AutoAssignLocalModelsUseCase` assigns `ModelType.VISION`
Then chosen `apiConfigId` equals `"vision-preset"`

### SCN-ACTIVE-001: Active Provider Model From Preset

Given default assignment points to API config `cfg-9` with `model_id = "gpt-4o"` and credentials without model fields
When `ActiveModelProviderImpl.getActiveConfiguration(ModelType.PRIMARY)` runs
Then `modelId` equals `"gpt-4o"`

### SCN-ACTIVE-002: Active Provider Multimodal From Preset

Given config `cfg-9` has `is_multimodal = 1`
When `getActiveConfiguration` runs for that assignment
Then `isMultimodal` equals `true`

### SCN-INFERENCE-001: Factory Uses Config Model For OpenAI

Given credentials with empty or dummy model field on credentials type
And configuration `modelId = "gpt-4o"` for `ModelType.PRIMARY`
When `InferenceFactoryImpl` resolves LLM service
Then constructed `AnthropicInferenceServiceImpl` / OpenAI path receives `modelId = "gpt-4o"` (provider-appropriate service under test)

### SCN-INFERENCE-002: Service Identity Model From Config

Given `createApiIdentity` inputs with `apiConfig.modelId = "openai/gpt-5.2"`
When identity is built
Then `ServiceIdentity.Api.modelId` equals `"openai/gpt-5.2"`

### SCN-UI-MAPPER-001: Asset Ui Mapper Omits Model

Given domain `ApiModelAsset` with credentials without `modelId`
When `ApiModelAssetUiMapper.map(asset)` runs
Then `ApiModelAssetUi` has no `modelId` property
And `ApiModelAssetUi` has no `isMultimodal` property

### SCN-UI-MAPPER-002: Config Ui Mapper Includes Model

Given domain config `modelId = "grok-4"`, `isMultimodal = true`
When `mapConfig(config)` runs
Then `ApiModelConfigUi.modelId` equals `"grok-4"`
And `ApiModelConfigUi.isMultimodal` equals `true`

### SCN-UI-DISCOVER-001: Credential Form Has No Discover Models

Given `CredentialsConfigurationForm` source in `ByokConfigureForms.kt`
When the composable body is inspected
Then it does not call `ModelSelectionField`
And it does not call `VisionCapabilitySwitch`

### SCN-UI-DISCOVER-002: Preset Form Hosts Model Selection

Given `PresetConfigurationForm` source
When the composable body is inspected
Then it invokes `PresetModelAndVisionSection` (or `ModelSelectionField` bound to `ApiModelConfigUi`)
And model field label text equals `"Model"`

### SCN-UI-VISION-001: Vision Switch On Preset Draft

Given coordinator updates `presetDraft` via `normalizeVisionCapability`
When discovered model `id = "gpt-4o"` reports `isMultimodal = true`
Then `presetDraft.isMultimodal` becomes `true`
And `assetDraft` does not gain `isMultimodal` field

### SCN-UI-TEST-001: Test Button Success Presentation

Given `connectionTestStatus = SUCCESS` in editor state
When credential form Test row is composed
Then visible button text equals `"Success"`
And a check circle icon is displayed adjacent to the label

### SCN-UI-TEST-002: Test Failure Snackbar

Given `TestApiProviderConnectionUseCase` returns failure with message `"Invalid API key"`
When user taps Test
Then snackbar text equals `"Invalid API key"` (or mapped user message containing that substring)
And `connectionTestStatus` equals `IDLE`

### SCN-UI-TEST-003: Fingerprint Change Resets Success

Given `connectionTestStatus = SUCCESS` for provider `OPENAI`
When user changes provider dropdown to `ANTHROPIC`
Then `connectionTestStatus` equals `IDLE`

### SCN-UI-CRED-001: Reusable Credential Subtitle Provider Only

Given `ReusableApiCredentialUi(displayName = "Work Key", providerLabel = "OpenAI", credentialAlias = "openai-main")`
When dropdown menu item is rendered
Then subtitle text equals `"OpenAI"`
And subtitle text does not contain `"gpt"`

### SCN-UI-SAVE-001: Credential Save Without Model

Given `assetDraft.displayName = "My Key"`, `assetDraft` has no `modelId`, `apiKey = "sk-x"`
When `ByokConfigureScreen` computes `isSaveEnabled` for credential mode
Then `isSaveEnabled` equals `true`

### SCN-UI-SAVE-002: Preset Save Requires Model

Given `presetDraft.modelId = ""` and `presetDraft.displayName = "Fast"`
When `isSaveEnabled` computed for preset mode
Then `isSaveEnabled` equals `false`

### SCN-UI-COORD-001: Coordinator Invokes Test Use Case

Given valid api key in coordinator test
When `onTestApiProviderConnection` is called
Then `TestApiProviderConnectionUseCase` is invoked with `provider` from `assetDraft`
And state transitions `IDLE` → `TESTING` → `SUCCESS` on success

### SCN-UI-COORD-002: Fetch Models Uses Preset Model Id

Given `presetDraft.modelId = "grok-4"` and `assetDraft.provider = XAI`
When `onFetchApiModels` runs
Then `ApiModelDiscoveryRequest.selectedModelId` equals `"grok-4"`

### SCN-UI-COORD-003: Save Credentials Draft Credential Only

Given `onSaveApiCredentials` builds `ApiProviderDraft`
When draft is passed to `saveApiProviderDraft`
Then draft has no property `modelId`
And draft has no property `isMultimodal`

### SCN-UI-STATE-001: Editor State Maps Connection Success

Given transient state `connectionTestStatus = SUCCESS`
When `SettingsUiStateFactory` builds `apiProviderEditor`
Then `apiProviderEditor.connectionTestStatus` equals `SUCCESS`

### SCN-UI-SHEET-001: Asset List Subtitle Without Model

Given asset UI `displayName = "My OpenAI"`, `provider = OPENAI`
When asset list row subtitle is rendered in `ByokBottomSheet`
Then subtitle text equals `"OpenAI"`
And subtitle text does not contain `"gpt-4o"`

### SCN-UI-SHEET-002: Preset Row Shows Model

Given preset UI `displayName = "Creative"`, `modelId = "gpt-4o"`
When config list row subtitle is rendered
Then subtitle contains `"gpt-4o"`

### SCN-UI-ASSIGN-001: Assignment Picker Uses Preset Model

Given model assignment UI built from API preset with `modelId = "claude-sonnet-4-20250514"`
When subtitle is shown on assignment screen
Then subtitle contains `"claude-sonnet-4-20250514"`

---

## Mutation Defense Scenarios

### SCN-MUT-001: Signature Must Not Include ModelId

Given implementation mistakenly uses `buildApiCredentialsIdentitySignature(provider, modelId = "a", …)` for dedupe
When SCN-SIGNATURE-001 test runs with two presets differing only by `model_id` on configurations
Then test fails because two credential rows or no match to existing credential occur

### SCN-MUT-002: Active Provider Must Not Read Credential Model

Given `ActiveModelProviderImpl` still sets `modelId = credentials.modelId` with empty config `model_id`
When SCN-ACTIVE-001 runs expecting `"gpt-4o"` from config
Then assertion fails with empty or wrong `modelId`

### SCN-MUT-003: Inference Factory Must Not Read Credential Model

Given `InferenceFactoryImpl` passes `apiCreds.modelId` while config holds `"gpt-4o"` and credentials hold `"wrong"`
When SCN-INFERENCE-001 runs
Then service receives `"wrong"` and test fails

### SCN-MUT-004: Discover Models Must Not Remain On Credential Form

Given `CredentialsConfigurationForm` still includes `ModelSelectionField`
When static/source inspection or screenshot test for SCN-UI-DISCOVER-001 runs
Then test fails because discovery remains on credential screen

---

## Task To Scenario Matrix

| Task | Scenario IDs |
| --- | --- |
| T01 | SCN-DOMAIN-001, SCN-DOMAIN-002, SCN-DRAFT-001 |
| T02 | SCN-SIGNATURE-001, SCN-SIGNATURE-002, SCN-PORT-001 |
| T03 | SCN-SCHEMA-001, SCN-SCHEMA-002, SCN-DAO-001, SCN-DAO-002 |
| T04 | SCN-REPO-001, SCN-REPO-002, SCN-REPO-003, SCN-REPO-004 |
| T05 | SCN-SYNC-001, SCN-SYNC-002, SCN-SYNC-003, SCN-SYNC-004 |
| T06 | SCN-USECASE-SAVE-ASSET-001, SCN-USECASE-SAVE-ASSET-002, SCN-USECASE-SAVE-ASSET-003 |
| T07 | SCN-USECASE-SAVE-PRESET-001, SCN-USECASE-SAVE-PRESET-002 |
| T08 | SCN-USECASE-TEST-001, SCN-USECASE-TEST-002, SCN-USECASE-TEST-003 |
| T09 | SCN-USECASE-VISION-001, SCN-USECASE-DELETION-001, SCN-USECASE-AUTO-001 |
| T10 | SCN-ACTIVE-001, SCN-ACTIVE-002 |
| T11 | SCN-INFERENCE-001, SCN-INFERENCE-002 |
| T12 | SCN-UI-MAPPER-001, SCN-UI-MAPPER-002 |
| T13 | SCN-UI-DISCOVER-002, SCN-UI-VISION-001 |
| T14 | SCN-UI-TEST-001, SCN-UI-TEST-002, SCN-UI-TEST-003, SCN-UI-CRED-001, SCN-UI-DISCOVER-001 |
| T15 | SCN-UI-SAVE-001, SCN-UI-SAVE-002 |
| T16 | SCN-UI-COORD-001, SCN-UI-COORD-002, SCN-UI-COORD-003 |
| T17 | SCN-UI-STATE-001 |
| T18 | SCN-UI-SHEET-001, SCN-UI-SHEET-002, SCN-UI-ASSIGN-001 |
| T19 | SCN-USECASE-SAVE-ASSET-001 through SCN-USECASE-AUTO-001, SCN-USECASE-TEST-001 through SCN-USECASE-TEST-003 |
| T20 | SCN-SIGNATURE-001, SCN-REPO-001 through SCN-SYNC-004, SCN-ACTIVE-001, SCN-DAO-001, SCN-DAO-002, SCN-MUT-001, SCN-MUT-002 |
| T21 | SCN-INFERENCE-001, SCN-INFERENCE-002, SCN-MUT-003 |
| T22 | SCN-UI-TEST-001 through SCN-UI-COORD-003, SCN-UI-SAVE-001, SCN-UI-SAVE-002, SCN-MUT-004 |

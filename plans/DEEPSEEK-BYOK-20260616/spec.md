# DEEPSEEK-BYOK-20260616 Spec

**Ticket ID:** `DEEPSEEK-BYOK-20260616`

## Feature Summary

Add first-class **DeepSeek API BYOK** support to the Enclave Android app, following the **Z.ai provider pattern**: a thin `DeepSeekInferenceServiceImpl` subclass plus domain-level model policy hooks, with shared request-shaping extensions in `BaseOpenAiSdkInferenceService` (not a duplicate service).

Users must be able to select **DeepSeek** as a provider in BYOK settings and onboarding. The **base URL field is read-only**, fixed to DeepSeek's official endpoint `https://api.deepseek.com`. Model discovery reuses the existing OpenAI-compatible `models.list` path (same as Z.ai/OpenAI).

### DeepSeek API quirks handled in this ticket

| Model family | Behavior |
| --- | --- |
| `deepseek-reasoner` | **Strip** `reasoning_content` from assistant replay on follow-up requests (400 if included). **No** client tools. **No** `temperature` / `top_p`. |
| `deepseek-chat` | Standard chat completions; client tools supported; normal sampling controls. |
| `deepseek-v4*` (reasoning + tools) | **Replay exact** `reasoning_content` on assistant tool-call turns. Send `thinking: { "type": "enabled" }` via extra body. Use **`max_tokens`** (not `max_completion_tokens`) on direct API. **Omit** `tool_choice` when reasoning is engaged (`reasoningBudget > 0`). Map preset reasoning effort: `LOW`/`MEDIUM`/`HIGH` → `"high"`, `XHIGH` → `"max"`. Require non-blank assistant `content` when `reasoning_content` is present on tool-call replay turns. |
| Stream | Parse `reasoning_content` delta via existing `openAiChatDeltaReasoningText` (also handles OpenRouter-style `reasoning` when routed elsewhere — unchanged). |
| Prefix cache | **Do not** add OpenAI `promptCacheKey` or other incorrect cache routing for DeepSeek direct API. Enclave append-only prefix stability remains as-is. |
| Special token stripping (`<｜...｜>`) | **Deferred** to follow-up ticket `DEEPSEEK-STREAM-STRIP` — out of scope here. |

### Reference (policy ideas only — translate to Kotlin)

`scratch/oh-my-pi/packages/ai/src/providers/openai-completions-compat.ts` DeepSeek detection and compat flags inform `ApiProviderModelPolicy` and `DeepSeekRequestPolicy`; do not copy TypeScript.

---

## File Structure Table

| File | Action | Responsibility |
| --- | --- | --- |
| `plans/DEEPSEEK-BYOK-20260616/spec.md` | Create | Implementation contract (this file). |
| `plans/DEEPSEEK-BYOK-20260616/test_spec.md` | Create | Gherkin scenarios and run commands. |
| `core/domain/.../model/inference/ApiProvider.kt` | Modify | Add `DEEPSEEK` enum; default base URL; `supportsChat`; discovery/telemetry flags. |
| `core/domain/.../model/inference/ApiProviderModelPolicy.kt` | Modify | DeepSeek model classifiers; `parameterSupport` branch; discovery inclusion. |
| `core/domain/.../model/telemetry/TelemetryEnums.kt` | Modify | Add `TelemetryProviderKey.DEEPSEEK`; label resolver. |
| `feature/inference/.../DeepSeekInferenceServiceImpl.kt` | Create | Thin subclass: tag, provider id, policy hook overrides. |
| `feature/inference/.../DeepSeekRequestPolicy.kt` | Create | Request-shaping decisions (max_tokens field, thinking extra body, tool_choice omission, reasoning replay/strip rules, effort mapping). |
| `feature/inference/.../ApiInferenceServiceFactory.kt` | Modify | Route `ApiProvider.DEEPSEEK` → `DeepSeekInferenceServiceImpl`. |
| `feature/inference/.../BaseOpenAiSdkInferenceService.kt` | Modify | Protected hooks for token field, extras, tool_choice, reasoning attach, client-tools gate; delegate defaults unchanged for other providers. |
| `feature/inference/.../telemetry/InferenceTelemetrySupport.kt` | Modify | `providerLabel` maps `DEEPSEEK` → `"DeepSeek"`. |
| `feature/settings/.../DeepSeekCredentialsFields.kt` | Create | Read-only base URL display + migration to official endpoint on load. |
| `feature/settings/.../ByokConfigureForms.kt` | Modify | Provider picker sets fixed DeepSeek URL; credential form uses readonly field instead of editable base URL. |
| `core/domain/src/test/.../model/inference/ApiProviderTest.kt` | Modify | DeepSeek display name, base URL, chat support. |
| `core/domain/src/test/.../model/inference/ApiProviderModelPolicyTest.kt` | Modify | DeepSeek model policy matrix. |
| `core/domain/src/test/.../model/telemetry/TelemetrySummaryBuilderTest.kt` | Modify | DeepSeek telemetry key resolution. |
| `feature/inference/src/test/.../DeepSeekRequestPolicyTest.kt` | Create | Unit tests for policy object (classifiers + wire decisions). |
| `feature/inference/src/test/.../DeepSeekInferenceServiceImplTest.kt` | Create | Integration-style param capture tests via `completeSingleTurn`. |
| `feature/inference/src/test/.../BaseOpenAiSdkInferenceServiceTest.kt` | Modify | Regression: non-DeepSeek providers unaffected by new hooks. |
| `feature/inference/src/test/.../InferenceFactoryImplTest.kt` | Modify | Factory routes DeepSeek to OpenAI client with default base URL. |
| `feature/inference/src/test/.../repository/ApiModelCatalogRepositoryImplTest.kt` | Modify | Discovery via OpenAI-compatible list for DeepSeek. |
| `feature/inference/src/test/.../OpenAiTelemetryCaptureTest.kt` | Modify | Telemetry provider key `DEEPSEEK` on capture. |
| `feature/settings/src/test/.../ByokPresetDefaultsTest.kt` | Modify | DeepSeek uses generic preset temperature `0.7`. |
| `feature/settings/src/test/.../DeepSeekCredentialsFieldsTest.kt` | Create | Read-only base URL + migration composable behavior (Robolectric). |

**No schema change:** `ApiCredentialsEntity.provider` is `ApiProvider` (Room string enum). Adding `DEEPSEEK` does not require a Room version bump.

**Out of scope:**

- OpenRouter-routed DeepSeek models (existing OpenRouter path unchanged).
- Stream special-token stripping (`<｜...｜>`).
- New Room tables/columns.
- Custom/proxy DeepSeek base URLs in UI (read-only official endpoint only).

---

## API Contracts and Interfaces

### Domain — `ApiProvider` additions

```kotlin
enum class ApiProvider(...) {
    // ... existing entries ...
    DEEPSEEK("DeepSeek"),
    ;

    fun defaultBaseUrl(): String? =
        when (this) {
            // ... existing ...
            DEEPSEEK -> DeepSeekEndpoint.OFFICIAL.baseUrl
            else -> null
        }

    val supportsChat: Boolean
        get() = when (this) {
            FAL -> false
            ANTHROPIC, OPENAI, OPENROUTER, XAI, GOOGLE, ZAI, DEEPSEEK -> true
        }

    val exposesDiscoveryModelPricing: Boolean
        get() = when (this) {
            OPENROUTER, XAI -> true
            ANTHROPIC, OPENAI, GOOGLE, FAL, ZAI, DEEPSEEK -> false
        }
}
```

### Domain — `DeepSeekEndpoint`

```kotlin
/**
 * DeepSeek BYOK exposes a single official OpenAI-compatible endpoint.
 * Stored credentials with non-official URLs migrate to [OFFICIAL] on load.
 */
enum class DeepSeekEndpoint(
    val baseUrl: String,
) {
    OFFICIAL("https://api.deepseek.com"),
    ;

    companion object {
        fun resolveEndpoint(baseUrl: String?): DeepSeekEndpoint = OFFICIAL

        fun storedUrlNeedsMigration(baseUrl: String?): Boolean {
            val trimmed = baseUrl?.trim()?.trimEnd('/') ?: return false
            return trimmed != OFFICIAL.baseUrl
        }
    }
}
```

### Domain — `ApiProviderModelPolicy` DeepSeek helpers

```kotlin
object ApiProviderModelPolicy {
    fun isDeepSeekReasonerModel(modelId: String): Boolean
    fun isDeepSeekChatModel(modelId: String): Boolean
    fun isDeepSeekV4FamilyModel(modelId: String): Boolean
    fun isDeepSeekDirectReasoningEngaged(modelId: String, reasoningBudget: Int): Boolean
    fun supportsDeepSeekClientTools(modelId: String): Boolean
    fun shouldStripReasoningContentOnAssistantReplay(modelId: String): Boolean
    fun shouldReplayExactReasoningContentOnToolTurns(modelId: String, reasoningBudget: Int): Boolean
    fun deepSeekReasoningEffortForApi(effort: ApiReasoningEffort): String  // "high" | "max"
}
```

**Model ID normalization:** strip optional `deepseek/` OpenRouter-style prefix via `modelId.substringAfterLast('/')` before matching.

**Classifier rules:**

| Helper | Match rule |
| --- | --- |
| `isDeepSeekReasonerModel` | normalized id equals `deepseek-reasoner` |
| `isDeepSeekChatModel` | normalized id equals `deepseek-chat` |
| `isDeepSeekV4FamilyModel` | normalized id starts with `deepseek-v4` |
| `isDeepSeekDirectReasoningEngaged` | `isDeepSeekV4FamilyModel(modelId) && reasoningBudget > 0` |
| `supportsDeepSeekClientTools` | `!isDeepSeekReasonerModel(modelId)` |
| `shouldStripReasoningContentOnAssistantReplay` | `isDeepSeekReasonerModel(modelId)` |
| `shouldReplayExactReasoningContentOnToolTurns` | `isDeepSeekDirectReasoningEngaged(modelId, reasoningBudget)` |

**`parameterSupport(provider = DEEPSEEK, modelId)` matrix:**

| Model | `supportsTemperature` | `supportsTopP` | `supportsReasoningEffort` | `supportsMaxTokens` | Notes |
| --- | --- | --- | --- | --- | --- |
| `deepseek-reasoner` | `false` | `false` | `false` | `true` | Tools disabled at inference layer |
| `deepseek-chat` | `true` | `true` | `false` | `true` | Standard OpenAI-style sampling |
| `deepseek-v4*` + `reasoningBudget > 0` | `false` | `false` | `false` | `true` | Thinking via extra body; effort mapped in policy |
| `deepseek-v4*` + `reasoningBudget == 0` | `true` | `true` | `false` | `true` | Non-reasoning chat mode |

`supportsModelDiscovery` includes `ApiProvider.DEEPSEEK`.

### Inference — `DeepSeekRequestPolicy`

```kotlin
internal object DeepSeekRequestPolicy {
    fun usesMaxTokensField(modelId: String): Boolean  // true for all DeepSeek direct API models

    fun shouldOmitToolChoice(modelId: String, reasoningBudget: Int, toolsAvailable: Boolean): Boolean
    // true when isDeepSeekDirectReasoningEngaged && toolsAvailable

    fun thinkingExtraBody(modelId: String, reasoningBudget: Int): Map<String, Any>?
    // {"thinking": {"type": "enabled"}} when isDeepSeekDirectReasoningEngaged

    fun shouldAttachReasoningContent(modelId: String, reasoningBudget: Int, metadata: JsonObject?): Boolean
    // false for deepseek-reasoner; true when metadata has provider_reasoning and not stripped

    fun assistantContentForToolReplay(modelId: String, content: String, reasoningBudget: Int): String
    // when V4 reasoning engaged and content blank but reasoning replay required, use "."

    fun mapReasoningEffort(modelId: String, reasoningBudget: Int, effort: ApiReasoningEffort?): String?
    // null when not engaged; else deepSeekReasoningEffortForApi
}
```

### Inference — `DeepSeekInferenceServiceImpl`

```kotlin
class DeepSeekInferenceServiceImpl(
    client: OpenAIClient,
    modelId: String,
    modelType: ModelType,
    baseUrl: String? = null,
    loggingPort: LoggingPort,
    toolStreamGatePort: ToolStreamGatePort,
    telemetrySupport: InferenceTelemetrySupport? = null,
    telemetryEventPort: TelemetryEventPort? = null,
) : BaseOpenAiSdkInferenceService(
        client = client,
        modelId = modelId,
        provider = "DEEPSEEK",
        modelType = modelType,
        baseUrl = baseUrl,
        loggingPort = loggingPort,
        toolStreamGatePort = toolStreamGatePort,
        telemetrySupport = telemetrySupport,
        telemetryEventPort = telemetryEventPort,
    ) {
    override val tag: String = "DeepSeekInferenceService"
}
```

Policy behavior is implemented via **protected open hooks** on the base class, overridden only where `provider == DEEPSEEK` routing applies (hooks default to no-op / existing behavior).

### Base class — new protected hooks

```kotlin
abstract class BaseOpenAiSdkInferenceService(...) {
    protected open fun applyMaxOutputTokens(
        builder: ChatCompletionCreateParams.Builder,
        tokenLimit: Int,
        requestModelId: String,
    ) {
        builder.maxCompletionTokens(tokenLimit.toLong())  // existing default
    }

    protected open fun shouldIncludeToolChoice(
        requestModelId: String,
        toolsAvailable: Boolean,
        options: GenerationOptions,
    ): Boolean = toolsAvailable

    protected open fun applyProviderChatExtras(
        builder: ChatCompletionCreateParams.Builder,
        requestModelId: String,
        options: GenerationOptions,
    ): Unit = Unit

    protected open fun shouldAttachProviderReasoningContent(
        requestModelId: String,
        options: GenerationOptions,
        metadata: JsonObject?,
    ): Boolean = metadata.providerReasoningContent() != null

    protected open fun supportsClientToolsForModel(requestModelId: String): Boolean =
        provider.toApiProviderOrNull() != ApiProvider.XAI ||
            !ApiProviderModelPolicy.isXaiMultiAgentModel(requestModelId)

    protected open fun normalizeAssistantContentForReplay(
        requestModelId: String,
        content: String,
        options: GenerationOptions,
        hasToolCalls: Boolean,
    ): String = content
}
```

`applyPromptCacheRouting` **must not** gain a `DEEPSEEK` branch (explicit non-goal).

`DeepSeekInferenceServiceImpl` delegates all hook bodies to `DeepSeekRequestPolicy` + `ApiProviderModelPolicy`.

### Factory — `ApiInferenceServiceFactory`

```kotlin
ApiProvider.DEEPSEEK ->
    DeepSeekInferenceServiceImpl(
        client = openAiClientProvider.getClient(
            apiKey = apiKey,
            baseUrl = resolvedBaseUrl,
            headers = requestHeaders,
        ),
        modelId = apiConfig.modelId,
        modelType = modelType,
        baseUrl = resolvedBaseUrl,
        loggingPort = loggingPort,
        toolStreamGatePort = toolStreamGatePort,
        telemetrySupport = telemetrySupport,
        telemetryEventPort = telemetryEventPort,
    )
```

Resolved base URL: `apiCreds.baseUrl?.takeIf { it.isNotBlank() } ?: ApiProvider.DEEPSEEK.defaultBaseUrl()` → `https://api.deepseek.com`.

### Settings — `DeepSeekCredentialsFields.kt`

```kotlin
@Composable
fun DeepSeekBaseUrlField(
    asset: ApiModelAssetUi,
    onAssetChange: (ApiModelAssetUi) -> Unit,
)
```

Behavior mirrors `ZaiEndpointSelectionField` migration pattern but **without** endpoint picker:

- Display `DeepSeekEndpoint.OFFICIAL.baseUrl` in a **read-only** `NeoOutlinedTextField` (`readOnly = true`, `onValueChange = {}`).
- `LaunchedEffect(asset.baseUrl)`: when `DeepSeekEndpoint.storedUrlNeedsMigration(asset.baseUrl)`, call `onAssetChange(asset.copy(baseUrl = DeepSeekEndpoint.OFFICIAL.baseUrl))`.

### Settings — `ByokConfigureForms.kt` wiring

In `ProviderSelectionField` `onClick` for `DEEPSEEK`:

```kotlin
provider == ApiProvider.DEEPSEEK -> DeepSeekEndpoint.OFFICIAL.baseUrl
```

In `CredentialsConfigurationForm`:

```kotlin
when (asset.provider) {
    ApiProvider.ZAI -> ZaiEndpointSelectionField(...)
    ApiProvider.DEEPSEEK -> DeepSeekBaseUrlField(...)
    else -> editable NeoOutlinedTextField(...)
}
```

### Telemetry

```kotlin
enum class TelemetryProviderKey {
    // ...
    DEEPSEEK,
}

// TelemetryProviderKey.fromLabel:
"DeepSeek", "DEEPSEEK" -> DEEPSEEK

// InferenceTelemetrySupport.providerLabel:
ApiProvider.DEEPSEEK.name -> "DeepSeek"
```

`TelemetryProviderKey.DEEPSEEK.reportsCacheExclusiveInput` is `false` (OpenAI-style inclusive input, same as ZAI).

---

## Data Model / Schema Changes

**None.** `DEEPSEEK` is a new `ApiProvider` enum constant persisted as the string `"DEEPSEEK"` in existing `api_credentials.provider` and Firestore `provider` fields. `ApiCredentialsSyncHandler.fromFirestore` uses `ApiProvider.valueOf` — no handler change required beyond enum addition.

---

## Implementation Tasks

### T01 — Domain provider enum and endpoint constant

**Scenarios:** SCN-DOMAIN-001, SCN-DOMAIN-002, SCN-DOMAIN-003, SCN-DOMAIN-004

- Add `DEEPSEEK` to `ApiProvider` with `defaultBaseUrl()`, `supportsChat`, `exposesDiscoveryModelPricing`, `supportsRealtimeVoice = false`.
- Add `DeepSeekEndpoint` companion in `ApiProvider.kt` (same file as `ZaiEndpoint`).
- Extend `ApiProviderTest`.

### T02 — Domain model policy classifiers and parameter matrix

**Scenarios:** SCN-POLICY-001, SCN-POLICY-002, SCN-POLICY-003, SCN-POLICY-004, SCN-POLICY-005, SCN-POLICY-006, SCN-POLICY-007, SCN-POLICY-008

- Implement DeepSeek helpers and `parameterSupport` branch in `ApiProviderModelPolicy`.
- Add `DEEPSEEK` to `supportsModelDiscovery`.
- Extend `ApiProviderModelPolicyTest` with literal model IDs and expected booleans.

### T03 — Telemetry provider key and label

**Scenarios:** SCN-TELEMETRY-001, SCN-TELEMETRY-002, SCN-TELEMETRY-003

- Add `TelemetryProviderKey.DEEPSEEK` and `fromLabel` mapping.
- Update `InferenceTelemetrySupport.providerLabel`.
- Extend `TelemetrySummaryBuilderTest` and `OpenAiTelemetryCaptureTest`.

### T04 — BaseOpenAiSdkInferenceService protected hooks

**Scenarios:** SCN-HOOK-001, SCN-HOOK-002, SCN-REGRESSION-001, SCN-REGRESSION-002

- Extract inline logic into hook methods listed in §API Contracts.
- `completeSingleTurn` calls hooks instead of hard-coded paths for: max tokens, tool_choice, extras, client-tools gate.
- `toAssistantChatParam` respects `shouldAttachProviderReasoningContent` and `normalizeAssistantContentForReplay`.
- Verify OpenAI/ZAI/XAI param shapes unchanged (regression tests).

### T05 — DeepSeekRequestPolicy and DeepSeekInferenceServiceImpl

**Scenarios:** SCN-INFER-001, SCN-INFER-002, SCN-INFER-003, SCN-INFER-004, SCN-INFER-005, SCN-INFER-006, SCN-INFER-007, SCN-INFER-008, SCN-INFER-009, SCN-INFER-010

- Create `DeepSeekRequestPolicy.kt` with wire decisions per §API Contracts.
- Create `DeepSeekInferenceServiceImpl.kt` overriding base hooks to delegate to policy.
- Unit-test policy in isolation (`DeepSeekRequestPolicyTest`).
- Param-capture tests in `DeepSeekInferenceServiceImplTest` using mocked streaming client (same harness as `BaseOpenAiSdkInferenceServiceTest`).

### T06 — Factory routing and model discovery

**Scenarios:** SCN-FACTORY-001, SCN-FACTORY-002, SCN-DISCOVERY-001

- Wire `ApiProvider.DEEPSEEK` in `ApiInferenceServiceFactory`.
- Confirm `ApiModelCatalogRepositoryImpl` `else` branch covers DeepSeek (OpenAI `models.list`).
- Extend `InferenceFactoryImplTest` and `ApiModelCatalogRepositoryImplTest`.

### T07 — Settings UI: read-only base URL

**Scenarios:** SCN-UI-001, SCN-UI-002, SCN-UI-003, SCN-UI-004, SCN-UI-005

- Create `DeepSeekCredentialsFields.kt`.
- Update `ByokConfigureForms.kt` provider selection + credential form branching.
- Add `DeepSeekCredentialsFieldsTest` (Robolectric) and extend `ByokPresetDefaultsTest` for generic temperature.

### T08 — Onboarding and provider lists (verification only)

**Scenarios:** SCN-ONBOARD-001

- No code change expected: `ModelChoiceStep` and `ProviderSelectionField` filter `ApiProvider.entries.filter(ApiProvider::supportsChat)`.
- Add/adjust test or snapshot assertion that `DEEPSEEK` appears in chat-provider list once T01 lands.

---

## Drift and Escalation Rules

| Rule | Action |
| --- | --- |
| **Drift — DeepSeek API docs** | If DeepSeek publishes new model ID prefixes or changes `thinking` / `max_tokens` requirements, update `ApiProviderModelPolicy` classifiers first, add matching `SCN-POLICY-*` scenario to `test_spec.md`, then adjust `DeepSeekRequestPolicy`. |
| **Drift — oh-my-pi scratch compat** | If `scratch/oh-my-pi/.../openai-completions-compat.ts` DeepSeek flags change, diff against `DeepSeekRequestPolicy` and port only Enclave-relevant deltas; do not import TS. |
| **Escalation — OpenAI SDK field support** | If `ChatCompletionCreateParams.Builder` lacks `maxTokens()` and `putAdditionalBodyProperty("max_tokens", …)` conflicts with `max_completion_tokens`, stop and add a narrow SDK adapter in `DeepSeekRequestPolicy` with a new scenario before merge. |
| **Escalation — Schema change request** | Any request for custom DeepSeek proxy URLs in DB or editable base URL requires explicit Contract Waiver — this ticket is read-only official endpoint only. |
| **Escalation — Stream token stripping** | Leaked `<｜…｜>` tokens in production → open `DEEPSEEK-STREAM-STRIP` follow-up; do not expand this ticket scope. |
| **Escalation — promptCacheKey** | Do not route DeepSeek through OpenAI `promptCacheKey` without DeepSeek documentation proving support; append-only stability is sufficient for v1. |

---

## Acceptance Criteria (Definition of Done)

1. `ApiProvider.DEEPSEEK` selectable in BYOK and onboarding chat provider lists.
2. BYOK credential form shows **read-only** base URL `https://api.deepseek.com`; legacy stored URLs migrate on load.
3. `InferenceFactoryImpl` builds `DeepSeekInferenceServiceImpl` with resolved official base URL.
4. `deepseek-reasoner` requests omit `reasoning_content` replay, omit tools, omit temperature/top_p.
5. `deepseek-v4-pro` (or `deepseek-v4` family) with `reasoningBudget > 0` and tools: replays exact `reasoning_content`, sends `max_tokens`, sends `thinking: enabled`, omits `tool_choice`.
6. `deepseek-chat` supports tools and standard sampling.
7. Stream thinking uses `reasoning_content` deltas without regression to OpenRouter `reasoning` handling.
8. No `promptCacheKey` added for `DEEPSEEK`.
9. All scenarios in `test_spec.md` pass; `./gradlew detekt` and `ktlintCheck` clean.


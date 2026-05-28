# Spec: Intent-Gated Tool Activation

**Ticket ID:** `FEAT-intent-gated-tool-activation`  
**Date:** `2026-05-24`  
**Contracts read:** `AGENTS.md`, `contracts/ARCHITECTURE_RULES.md`, `contracts/CODE_STYLE_RULES.md`, `contracts/INFERENCE_RULES.md`, `contracts/UNIT_TESTING.md`  
**Related:** `plans/FEAT-lazy-tool-disclosure/spec.md` (initial `activeTools` / catalog split; Rust session defers undisclosed tools)

---

## 1. Feature Summary

### Problem

`ChatInferenceRequestPreparer` builds a full `toolCatalog` but seeds `activeTools` only from `PRELOADED_TOOL_NAMES` (`disclose_tool_schemas`, `web_search`, `web_extract`, `attached_image_inspect` when eligible). Deferred tools—including `search_x_recent` and the artifact trio—require the model to call `disclose_tool_schemas` before schemas are callable.

Search intent already drives **host pre-retrieval** `web_search` (classifier + `SearchIntentPolicy` heuristics) and injects results into the system prompt. `web_search` / `web_extract` are already preloaded when search is enabled. The gap is:

| Tool family | Current state | Desired state |
|-------------|---------------|---------------|
| `search_x_recent` | Catalog-only until model discloses | Auto-activate when search intent fires and `xSearchEnabled` |
| `start_artifact`, `append_blocks`, `search_current_artifact` | Catalog-only until model discloses | Auto-activate when artifact/docgen heuristics match |
| X pre-search (host `ToolExecutorPort`) | Not implemented | **Out of MVP** (activation-only; see §1.3) |

### Target

Before the first model turn, the host expands `GenerationOptions.activeTools` using pure domain policies—no Android types, no Room changes. Intent-activated tools are immediately callable on SDK-native mappers, LiteRT (`LiteRtToolCallLoop` / native toolsets), and Rust (`toolsSchemaJson` seeded from Kotlin `activeTools` per `RustLoopInferenceService`).

The model no longer needs to spend a turn on `disclose_tool_schemas` for X or artifact tools when host intent already established the need.

### Locked MVP scope decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Configured-skill gating (mandatory)** | **In MVP** — no classifier, no catalog entry, no `activeTools` preload, and no pre-retrieval for a search backend unless that backend is **fully configured** (toggle on **and** API key present for the selected provider). Same for X. | UI save flows already require keys to enable toggles, but `ChatInferenceRequestPreparer` today only checks `searchEnabled` / `xSearchEnabled` — models can still see `web_search` without a Tavily/Exa key. Intent gating must not widen that gap. |
| **X auto-activation** | **In MVP** — add `search_x_recent` to `activeTools` when classifier fires **and** `settings.isXSearchSkillConfigured()` | Closes the stated gap; 76.4% classifier accuracy is acceptable for gating per product preference |
| **Artifact auto-activation** | **In MVP** — add artifact trio to `activeTools` when `ArtifactIntentPolicy.needsArtifactTools(prompt)` | Reuses inverted docgen patterns aligned with existing `SearchIntentPolicy` non-search regexes |
| **X host pre-retrieval** | **Deferred** to follow-up ticket `FEAT-x-pre-retrieval-query-shaping` | X API v2 recent search expects short keyword queries; raw conversational prompts perform poorly. `BuildXQueryUseCase` already composes structured `XSearchParams`—host pre-search needs `DeriveXSearchParamsFromPromptUseCase` (length cap, keyword extraction, conversational skip). MVP delivers activation-only so the model can call `search_x_recent` with structured args |
| **`PreparedChatInferenceRequest` X results** | **No new field in MVP** | Web pre-results use `preRetrievalSources` + `retrievedSearchResults` string in prompt composer. When X pre-search ships, add `preRetrievalXResultJson: String?` on `PreparedChatInferenceRequest` and `retrievedXSearchResults: String?` on `PromptComposerPort` (parallel to web)—documented in §3.5, not implemented here |
| **Combined artifact + search prompt** | Activate **both** tool families when each policy matches; run **web** pre-retrieval only when web skill configured; **activate X** only when X skill configured (no X pre-retrieval in MVP) | Example: "Research mortgage rates and write a PDF" — artifact tools + web pre-retrieval + `search_x_recent` active **only if** respective skills configured |
| **Artifact heuristic vs web block** | Split concerns: `SearchIntentPolicy.isHeuristicNonSearchPrompt` continues to suppress **web pre-retrieval** for pure docgen; add `SearchIntentPolicy.hasConcurrentResearchIntent` so explicit research verbs **re-open** the search path when docgen regex also matches | Prevents false-negative search on mixed prompts without removing docgen guard for pure export requests |
| **Disclosure regression** | `disclose_tool_schemas` remains preloaded; intent activation is **additive** to initial `activeTools` | Lazy disclosure spec unchanged; host prep and model disclosure can both expand active set |
| **Catalog disclosure mode** | Add `ToolDisclosureMode.INTENT_ACTIVATED` for tools in `activeTools` via intent (not preload table) | Prompt catalog tags distinguish `preloaded` / `intent-activated` / `deferred` |

### Non-goals

- X API pre-fetch or new `MessageGenerationState` for X sources in this ticket
- Changing `SearchIntentClassifierPort` model weights or threshold (remain `SearchIntentPolicy.PRE_RETRIEVAL_THRESHOLD = 0.52f`)
- Auto-activating chat-history tools, `manage_memories`, or media tools
- Rust `activated_tool_names` merge changes (Kotlin supplies expanded initial `activeTools`; Rust disclosure path unchanged)
- Room / persistence schema changes

### Verified integration points (current code)

| Location | Role |
|----------|------|
| `ChatInferenceRequestPreparer` | Builds catalog, preload `activeTools`, search classifier + web pre-retrieval — **today gates tools on `searchEnabled` / `xSearchEnabled` only, not key presence** |
| `SettingsData` | `searchEnabled`, `searchProvider`, `tavilyKeyPresent`, `exaKeyPresent`, `xSearchEnabled`, `xApiKeyPresent` |
| `SearchIntentPolicy` | `PRE_RETRIEVAL_THRESHOLD`, `isHeuristicNonSearchPrompt` |
| `SearchIntentClassifierPort` | `requiresSearch(embedding, threshold)` |
| `BuildXQueryUseCase` | Composes X API query from structured `XSearchParams` (used by executor, not raw prompt) |
| `PreparedChatInferenceRequest` | `preRetrievalSources: List<WebSearchSource>` |
| `SearchToolPromptComposer` | `deferredNames` excludes tools already in `activeTools` |
| `OpenRouterRequestMapper` / other mappers | Register `options.activeTools` only |
| `RustLoopInferenceService.buildAgentLoopConfig` | `toolsSchemaJson = serializeToolsSchema(options.activeTools)` |

---

## 2. File Structure Table

| Action | Path | Responsibility |
|--------|------|----------------|
| **Create** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/util/SearchSkillAvailability.kt` | Pure `SettingsData` helpers: `isWebSearchSkillConfigured()`, `isXSearchSkillConfigured()` (toggle + key for active provider) |
| **Create** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/util/ArtifactIntentPolicy.kt` | Pure regex/heuristic: `needsArtifactTools(prompt): Boolean` for artifact trio activation |
| **Create** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/util/ToolIntentActivationPolicy.kt` | Orchestrates search vs artifact axes; returns `ToolIntentActivationDecision` |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/util/SearchIntentPolicy.kt` | Add `hasConcurrentResearchIntent(prompt)`; document interaction with artifact heuristics |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/inference/ToolCatalogModels.kt` | Add `ToolDisclosureMode.INTENT_ACTIVATED` |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/ChatInferenceRequestPreparer.kt` | Gate `eligibleTools` / classifier / pre-retrieval on `SearchSkillAvailability`; call `ToolIntentActivationPolicy`, union intent tools into `activeTools`, set catalog `disclosureMode` |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/SearchToolPromptComposer.kt` | Catalog tag `intent-activated` for `INTENT_ACTIVATED`; ensure intent-active tools get `# ACTIVE TOOL EXAMPLES` |
| **Create** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/util/SearchSkillAvailabilityTest.kt` | Unit tests for configured-skill helpers |
| **Create** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/util/ArtifactIntentPolicyTest.kt` | Unit tests for artifact patterns |
| **Create** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/util/ToolIntentActivationPolicyTest.kt` | Unit tests for combined intent matrix |
| **Modify** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/util/SearchIntentPolicyTest.kt` | Tests for `hasConcurrentResearchIntent` |
| **Modify** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/chat/ChatInferenceRequestPreparerTest.kt` | Preparer integration: activeTools expansion, web pre-retrieval preserved |
| **Create** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/chat/ChatInferenceRequestPreparerIntentGatingTest.kt` | Focused preparer scenarios (X, artifact, combined) |
| **Modify** | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/OpenRouterRequestMapperTest.kt` | Replace/adjust `LTD-13` expectations when classifier signals search + X enabled |
| **Modify** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/chat/SearchToolPromptComposerTest.kt` | Intent-activated catalog tag and examples |

**No changes (MVP):**

| Path | Why |
|------|-----|
| `third_party/agent_core/**` | Initial active set already flows via `toolsSchemaJson` |
| `core/data/**` executors | No new host X/web execution |
| `feature/chat/**` UI | No new pre-retrieval source type |
| `PromptComposerPort` / `PreparedChatInferenceRequest` | No X pre-result field until follow-up ticket |

**Follow-up ticket (document only):** `FEAT-x-pre-retrieval-query-shaping` — `DeriveXSearchParamsFromPromptUseCase`, `preRetrievalXResultJson`, prompt section `# RELEVANT X SEARCH RESULTS:`

---

## 3. API Contracts and Interfaces

### 3.0 `SearchSkillAvailability`

Pure domain helpers on `SettingsData` (extension functions or small object with `SettingsData` parameter — no Android types):

```kotlin
fun SettingsData.isWebSearchSkillConfigured(): Boolean
fun SettingsData.isXSearchSkillConfigured(): Boolean
```

**`isWebSearchSkillConfigured` — must evaluate true only when all hold:**

| Condition | Rule |
|-----------|------|
| Toggle | `searchEnabled == true` |
| Provider key | `searchProvider == TAVILY` → `tavilyKeyPresent == true`; `searchProvider == EXA` → `exaKeyPresent == true` |

**`isXSearchSkillConfigured` — must evaluate true only when:**

| Condition | Rule |
|-----------|------|
| Toggle | `xSearchEnabled == true` |
| Key | `xApiKeyPresent == true` |

**Preparer / policy usage (replaces raw `searchEnabled` / `xSearchEnabled` for tool exposure and classifier):**

| Concern | Gate |
|---------|------|
| Add `web_search`, `web_extract` to `eligibleTools` | `isWebSearchSkillConfigured()` |
| Preload `web_search`, `web_extract` in `activeTools` | `isWebSearchSkillConfigured()` |
| Run embedding classifier + web pre-retrieval | `embedding != null` **and** `isWebSearchSkillConfigured()` |
| Add `search_x_recent` to `eligibleTools` | `isXSearchSkillConfigured()` |
| Intent-activate `search_x_recent` | classifier path **and** `isXSearchSkillConfigured()` |

When not configured, tool names must be **absent** from `toolCatalog` and `activeTools` (not merely deferred). The model must not see catalog lines or active examples for unavailable backends.

### 3.1 `ArtifactIntentPolicy`

```kotlin
object ArtifactIntentPolicy {
    fun needsArtifactTools(prompt: String): Boolean
}
```

**Positive patterns (non-exhaustive; implementation must include at least these families):**

| Family | Example match |
|--------|----------------|
| Create + document type | `write a PDF`, `generate a markdown report`, `draft a docx` |
| Artifact export phrasing | `as an artifact`, `artifact export` |
| Edit existing artifact | `append to the artifact`, `update the document` |

**Reuse:** Start from `SearchIntentPolicy` docgen-related regexes (artifact/document/pdf/report/markdown/docx) inverted into positive `needsArtifactTools` matchers. Do not require search intent for activation.

**Negative:** Empty/blank prompt → `false`. Pure web Q&A with no docgen cue → `false`.

### 3.2 `SearchIntentPolicy` extension

```kotlin
object SearchIntentPolicy {
    const val PRE_RETRIEVAL_THRESHOLD: Float = 0.52f
    fun isHeuristicNonSearchPrompt(prompt: String): Boolean
    fun hasConcurrentResearchIntent(prompt: String): Boolean  // NEW
}
```

**`hasConcurrentResearchIntent` — must match at least one:**

| Pattern intent | Example substrings |
|----------------|-------------------|
| Research verbs | `research`, `look up`, `find out`, `investigate` |
| Freshness | `latest`, `current`, `today`, `recent`, `breaking` |
| News/rates | `news`, `headlines`, `mortgage rates`, `stock price` |

When `isHeuristicNonSearchPrompt(prompt) == true` **and** `hasConcurrentResearchIntent(prompt) == true`, the search classifier path runs (embedding + threshold) even though docgen heuristic matched.

### 3.3 `ToolIntentActivationPolicy`

```kotlin
data class ToolIntentActivationDecision(
    val activateArtifactTools: Boolean,
    val activateSearchX: Boolean,
    val runWebPreRetrieval: Boolean,
)

object ToolIntentActivationPolicy {
    fun resolve(
        prompt: String,
        embedding: FloatArray?,
        settings: SettingsData,
        classifier: SearchIntentClassifierPort,
    ): ToolIntentActivationDecision
}
```

**Decision table (evaluated in this order):**

| Step | Condition | Effect |
|------|-----------|--------|
| 1 | `embedding == null` OR `!settings.isWebSearchSkillConfigured()` | `activateSearchX = false`, `runWebPreRetrieval = false` (classifier not consulted) |
| 2 | `ArtifactIntentPolicy.needsArtifactTools(prompt)` | `activateArtifactTools = true` |
| 3 | `heuristicNonSearch = isHeuristicNonSearchPrompt(prompt)` AND NOT `hasConcurrentResearchIntent(prompt)` | Skip classifier; `activateSearchX = false`, `runWebPreRetrieval = false` |
| 4 | Else if `classifier.requiresSearch(embedding, PRE_RETRIEVAL_THRESHOLD)` | `runWebPreRetrieval = true`; `activateSearchX = settings.isXSearchSkillConfigured()` |
| 5 | Else | `activateSearchX = false`, `runWebPreRetrieval = false` |

`activateArtifactTools` is independent of steps 3–5 (step 2 only).

**Artifact tool names (literal):** `start_artifact`, `append_blocks`, `search_current_artifact`  
**X tool name (literal):** `search_x_recent`

### 3.4 `ChatInferenceRequestPreparer` active-tool merge

After building `eligibleTools` and baseline `PRELOADED_TOOL_NAMES` filter:

```text
intentActivatedNames = emptySet()
if (decision.activateArtifactTools) intentActivatedNames += {start_artifact, append_blocks, search_current_artifact}
if (decision.activateSearchX) intentActivatedNames += {search_x_recent}

activeTools = eligibleTools.filter { name in PRELOADED_TOOL_NAMES ∪ intentActivatedNames }
```

For each `toolCatalog` entry:

| Condition | `ToolDisclosureMode` |
|-----------|---------------------|
| `name in PRELOADED_TOOL_NAMES` | `PRELOADED` |
| `name in intentActivatedNames` | `INTENT_ACTIVATED` |
| else | `DISCLOSE_ON_DEMAND` |

**Web pre-retrieval:** Execute existing `toolExecutor` `web_search` branch only when `decision.runWebPreRetrieval == true` (replace inline `needsSearch` boolean with decision field; behavior unchanged for pure search prompts).

**Logging:** One `info` line per turn: `Tool intent activation: artifact=$activateArtifactTools x=$activateSearchX webPreRetrieval=$runWebPreRetrieval heuristicNonSearch=$heuristicNonSearch researchBypass=$hasConcurrentResearchIntent`

### 3.5 Future X pre-results contract (follow-up ticket only)

When implementing `FEAT-x-pre-retrieval-query-shaping`:

| Field | Type | Semantics |
|-------|------|-----------|
| `PreparedChatInferenceRequest.preRetrievalXResultJson` | `String?` | Raw normalized JSON from `XSearchToolExecutorImpl` |
| `PromptComposerPort.retrievedXSearchResults` | `String?` | Injected under `# RELEVANT X SEARCH RESULTS:` |

`DeriveXSearchParamsFromPromptUseCase` (planned signature):

```kotlin
data class DeriveXSearchParamsResult(
    val params: XSearchParams?,
    val skipReason: String?,  // non-null when pre-search skipped
)

class DeriveXSearchParamsFromPromptUseCase {
    fun invoke(prompt: String): DeriveXSearchParamsResult
}
```

**Planned heuristics:** max 6 keywords; max composed query length 128; skip when token count > 40 and no quoted phrase/hashtag/`@mention`; prefer quoted strings and proper-noun tokens.

### 3.6 `SearchToolPromptComposer` catalog tag

| `ToolDisclosureMode` | Catalog tag string |
|----------------------|-------------------|
| `PRELOADED` | `preloaded` |
| `INTENT_ACTIVATED` | `intent-activated` |
| `DISCLOSE_ON_DEMAND` | `deferred` |

`deferredNames` computation unchanged: `DISCLOSE_ON_DEMAND` entries not in `activeTools.map { name }`.

### 3.7 Interaction with lazy tool disclosure

| Path | Behavior |
|------|----------|
| Initial turn | `activeTools` includes preloaded + intent-activated tools; full schemas in `toolsSchemaJson` / provider registration |
| Model calls `disclose_tool_schemas` | Unchanged; merges additional deferred tools via `activatedToolNames` |
| Intent tool already active | Disclosure resolver reports `already_active`; no duplicate activation |

---

## 4. Data Model Changes

| Location | Change |
|----------|--------|
| `ToolDisclosureMode` | Add enum value `INTENT_ACTIVATED` |
| `PreparedChatInferenceRequest` | **None** in MVP |
| Room / `WebSearchSource` | **None** |

---

## 5. Ordered Implementation Tasks

| ID | Task | Verifies |
|----|------|----------|
| **T-0** | Implement `SearchSkillAvailability` on `SettingsData`; use in preparer for `eligibleTools`, classifier, pre-retrieval | `IGTA-16`, `IGTA-17`, `IGTA-18`, `MUT-03` |
| **T-1** | Implement `ArtifactIntentPolicy` with positive docgen/artifact regex set | `IGTA-04`, `IGTA-06`, `MUT-02` |
| **T-2** | Add `SearchIntentPolicy.hasConcurrentResearchIntent` and unit tests | `IGTA-05`, `IGTA-06` |
| **T-3** | Implement `ToolIntentActivationPolicy` + `ToolIntentActivationDecision` (accept `SettingsData`) | `IGTA-02`, `IGTA-03`, `IGTA-07`, `IGTA-08`, `IGTA-12`, `IGTA-13`, `IGTA-16`–`IGTA-18` |
| **T-4** | Add `ToolDisclosureMode.INTENT_ACTIVATED` and catalog tagging in `SearchToolPromptComposer` | `IGTA-10`, `IGTA-11`, `MUT-02` |
| **T-5** | Integrate policies into `ChatInferenceRequestPreparer` (configured-skill gates, activeTools union, disclosure modes) | `IGTA-01`–`IGTA-06`, `IGTA-12`–`IGTA-18`, `MUT-03` |
| **T-6** | Add `SearchSkillAvailabilityTest`, `ArtifactIntentPolicyTest`, `ToolIntentActivationPolicyTest`, extend `SearchIntentPolicyTest` | `IGTA-01`–`IGTA-08`, `IGTA-16`–`IGTA-18` |
| **T-7** | Add `ChatInferenceRequestPreparerIntentGatingTest` and extend `ChatInferenceRequestPreparerTest` for regression on existing pre-retrieval tests | `IGTA-02`, `IGTA-05`, `IGTA-06`, `IGTA-09` |
| **T-8** | Update `SearchToolPromptComposerTest` for `intent-activated` tag and active examples | `IGTA-10`, `IGTA-11` |
| **T-9** | Update `OpenRouterRequestMapperTest` (and any preparer-based mapper tests that assert X deferred on search prompts) | `IGTA-09`, `MUT-01` |
| **T-10** | Run domain + inference unit tests; `ktlintCheck` + `detekt` on touched modules | All scenarios |

---

## 6. Scenario ID Map

| Scenario IDs | Primary Tasks |
|--------------|---------------|
| `IGTA-01` to `IGTA-03` | `T-3`, `T-5`, `T-7` |
| `IGTA-04`, `IGTA-06` | `T-1`, `T-5`, `T-7` |
| `IGTA-05`, `IGTA-05b` | `T-2`, `T-3`, `T-5`, `T-7` |
| `IGTA-07`, `IGTA-08`, `IGTA-12`, `IGTA-13` | `T-3`, `T-5`, `T-7` |
| `IGTA-09`, `IGTA-10` | `T-5`, `T-8`, `T-9` |
| `IGTA-11` | `T-4`, `T-8` |
| `IGTA-14` | `T-5`, `T-7` |
| `IGTA-15` | `T-5`, `T-7` (document LiteRT parity via `activeTools` assertion) |
| `MUT-01`, `MUT-02`, `MUT-03` | `T-0`, `T-5`, `T-7`, `T-9` |
| `IGTA-16` to `IGTA-18` | `T-0`, `T-3`, `T-5`, `T-7` |

---

## 7. Validation Commands

### Targeted iteration

```bash
./gradlew :core:domain:testDebugUnitTest --tests "*ArtifactIntentPolicyTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*ToolIntentActivationPolicyTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*SearchIntentPolicyTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*ChatInferenceRequestPreparerIntentGatingTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*ChatInferenceRequestPreparerTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*SearchToolPromptComposerTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*OpenRouterRequestMapperTest*"
```

### Completion gate

```bash
./gradlew :core:domain:assembleDebug
./gradlew :feature:inference:assembleDebug
./gradlew ktlintCheck
./gradlew detekt
./gradlew testDebugUnitTest
```

---

## 8. Drift and Escalation Rules (Later Phases)

| Situation | Action |
|-----------|--------|
| Implementation adds X pre-retrieval without `DeriveXSearchParamsFromPromptUseCase` | **Block** — reopen plan or split `FEAT-x-pre-retrieval-query-shaping` |
| TDD Red tests assert behavior not listed in scenario index | Reject in Red Review; amend `test_spec.md` with human approval |
| Rust validation rejects intent-activated tool names | Escalate to lazy-disclosure owners; confirm `toolsSchemaJson` seed includes intent tools—do not patch Rust in this ticket unless session init omits Kotlin `activeTools` |
| Classifier threshold change requested | Out of scope; requires product sign-off and `SearchIntentPolicy` contract waiver |
| Auto-activation of memory/history/media tools | Out of scope; new ticket |
| Exposing `web_search` / `search_x_recent` when toggle on but key missing | **Block** — must use `SearchSkillAvailability`; fix applies to baseline preparer path, not only intent activation |

**Human approval gate:** No TDD Red or implementation work until this plan and `test_spec.md` are explicitly approved.

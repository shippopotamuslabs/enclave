# Test Spec: Intent-Gated Tool Activation

**Ticket ID:** `FEAT-intent-gated-tool-activation`  
**Companion spec:** `plans/FEAT-intent-gated-tool-activation/spec.md`

---

## Test Framework and Run Commands

| Layer | Framework | Location |
|-------|-----------|----------|
| Domain policies / preparer | JUnit 5 + MockK | `core/domain/src/test/kotlin/...` |
| Feature inference (mapper) | JUnit 5 + MockK | `feature/inference/src/test/kotlin/...` |

### Targeted iteration commands

```bash
./gradlew :core:domain:testDebugUnitTest --tests "*SearchSkillAvailabilityTest*"
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

## Scenario Index

| ID | Category | Description |
|----|----------|-------------|
| `IGTA-01` | Happy | `ArtifactIntentPolicy` matches docgen prompt |
| `IGTA-02` | Happy | Search classifier + X enabled → `search_x_recent` in `activeTools` |
| `IGTA-03` | Edge | Search classifier true but `xSearchEnabled=false` → X not active |
| `IGTA-04` | Happy | Artifact intent activates trio without search |
| `IGTA-05` | Happy | Combined research + PDF → both families, web pre-retrieval, X active |
| `IGTA-05b` | Happy | `hasConcurrentResearchIntent` true on combined research + PDF prompt |
| `IGTA-06` | Edge | Pure artifact heuristic → artifacts active, web pre-retrieval skipped |
| `IGTA-07` | Edge | Classifier below threshold → no X activation |
| `IGTA-08` | Edge | Heuristic non-search without research bypass → classifier not called |
| `IGTA-09` | Happy | OpenRouter mapper registers `search_x_recent` when intent-activated |
| `IGTA-10` | Happy | Prompt includes `search_x_recent` active example when X intent-activated |
| `IGTA-11` | Happy | Prompt includes `start_artifact` active example when artifact intent-activated |
| `IGTA-12` | Edge | Null embedding → no search path, no X, no web pre-retrieval |
| `IGTA-13` | Edge | `searchEnabled=false` → no X activation even if classifier mocked true |
| `IGTA-14` | Edge | Intent-activated tool still disclosable; already-active reporting unchanged |
| `IGTA-15` | Happy | `GenerationOptions.activeTools` carries intent tools for LiteRT strategy |
| `MUT-01` | Mutation | `search_x_recent` must not appear in `activeTools` when classifier returns false |
| `MUT-02` | Mutation | Artifact tools must not require search intent or web pre-retrieval to activate |
| `IGTA-16` | Edge | `searchEnabled` true but missing provider API key → no web tools, no classifier |
| `IGTA-17` | Edge | `xSearchEnabled` true but `xApiKeyPresent` false → `search_x_recent` absent from catalog |
| `IGTA-18` | Edge | Web configured, X not → classifier + web pre-retrieval allowed; X never activated |
| `MUT-03` | Mutation | Classifier must not run when web search skill not configured |

**Total scenarios:** `22`

---

## Shared Test Fixtures

Unless a scenario overrides them:

| Fixture | Value |
|---------|-------|
| `SearchIntentPolicy.PRE_RETRIEVAL_THRESHOLD` | `0.52f` |
| Classifier probability when mocked high | `0.90f` |
| Classifier probability when mocked low | `0.10f` |
| Test embedding | `floatArrayOf(0.1f, 0.2f, 0.3f)` |
| Null embedding | `null` |
| Baseline preloaded active names | `["disclose_tool_schemas","web_search","web_extract"]` |
| Artifact tool names | `["start_artifact","append_blocks","search_current_artifact"]` |
| X tool name | `search_x_recent` |
| `SettingsData.searchEnabled` default (when web scenarios need search) | `true` |
| `SettingsData.searchProvider` default | `TAVILY` |
| `SettingsData.tavilyKeyPresent` default (when web scenarios need search) | `true` |
| `SettingsData.exaKeyPresent` default | `false` |
| `SettingsData.xSearchEnabled` default (when X scenarios need X) | `true` |
| `SettingsData.xApiKeyPresent` default (when X scenarios need X) | `true` |
| Configured web skill shorthand | `searchEnabled=true`, `tavilyKeyPresent=true`, `searchProvider=TAVILY` |
| Configured X skill shorthand | `xSearchEnabled=true`, `xApiKeyPresent=true` |
| Web pre-retrieval marker in system prompt | `# RELEVANT WEB SEARCH RESULTS:` |
| Mock web search result JSON | `{"results":[{"title":"Fixture Title","content":"Fixture search body"}]}` |
| `ToolDisclosureMode` for intent-activated catalog entries | `INTENT_ACTIVATED` |
| Catalog tag for intent-activated | `intent-activated` |

**Preparer test harness:** `ChatInferenceRequestPreparer` with `SearchToolPromptComposer`, `mockEmbeddingEngine()` returning test embedding, `mockSettingsRepository`, `mockActiveModelProvider(fastTextConfig())`, `mockMessageRepository`, `mockMemoriesRepository`.

---

## Happy Path Scenarios

### IGTA-01 — ArtifactIntentPolicy detects docgen

```gherkin
Scenario: IGTA-01 ArtifactIntentPolicy matches write PDF prompt
  Given prompt equals "Please write a PDF report on quarterly revenue"
  When ArtifactIntentPolicy.needsArtifactTools is evaluated
  Then the result equals true
```

**Test class:** `ArtifactIntentPolicyTest`  
**Location:** `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/util/ArtifactIntentPolicyTest.kt`

---

### IGTA-02 — Search intent activates X in activeTools

```gherkin
Scenario: IGTA-02 search classifier activates search_x_recent when X search enabled
  Given searchEnabled equals true
  And xSearchEnabled equals true
  And SearchIntentClassifierPort.requiresSearch returns true for any embedding with threshold 0.52
  And SearchIntentClassifierPort.getSearchIntentProbability returns 0.90
  And prompt equals "What are the latest headlines about the election?"
  When ChatInferenceRequestPreparer prepares a FAST request
  Then GenerationOptions.activeTools names contain "search_x_recent"
  And ToolCatalogEntry("search_x_recent").disclosureMode equals "INTENT_ACTIVATED"
```

**Test class:** `ChatInferenceRequestPreparerIntentGatingTest`

---

### IGTA-04 — Artifact trio without search

```gherkin
Scenario: IGTA-04 artifact intent activates trio on export phrasing without search
  Given searchEnabled equals true
  And xSearchEnabled equals true
  And SearchIntentClassifierPort.requiresSearch returns true for any embedding
  And prompt equals "Export this conversation as a markdown artifact"
  When ChatInferenceRequestPreparer prepares a FAST request
  Then GenerationOptions.activeTools names contain "start_artifact"
  And GenerationOptions.activeTools names contain "append_blocks"
  And GenerationOptions.activeTools names contain "search_current_artifact"
  And GenerationOptions.activeTools names do not contain "search_x_recent"
  And the system prompt does not contain "# RELEVANT WEB SEARCH RESULTS:"
```

**Test class:** `ChatInferenceRequestPreparerIntentGatingTest`

---

### IGTA-05 — Combined research and PDF

```gherkin
Scenario: IGTA-05 combined prompt activates artifact and search tools with web pre-retrieval
  Given searchEnabled equals true
  And xSearchEnabled equals true
  And SearchIntentClassifierPort.requiresSearch returns true for any embedding with threshold 0.52
  And ToolExecutorPort.execute for web_search returns resultJson containing "Fixture search body"
  And prompt equals "Research current mortgage rates and write a PDF report"
  When ChatInferenceRequestPreparer prepares a FAST request
  Then GenerationOptions.activeTools names contain "start_artifact"
  And GenerationOptions.activeTools names contain "search_x_recent"
  And the system prompt contains "# RELEVANT WEB SEARCH RESULTS:"
  And the system prompt contains "Fixture search body"
```

**Test class:** `ChatInferenceRequestPreparerIntentGatingTest`

---

### IGTA-09 — OpenRouter registers intent-activated X

```gherkin
Scenario: IGTA-09 OpenRouter mapper registers search_x_recent when preparer intent-activates X
  Given SettingsData with searchEnabled true and xSearchEnabled true
  And SearchIntentClassifierPort.requiresSearch returns true for any embedding
  And prompt equals "What is trending on X right now?"
  When ChatInferenceRequestPreparer prepares a FAST request
  And OpenRouterRequestMapper maps the request to chat completion params
  Then registered tool names contain "search_x_recent"
  And registered tool count is greater than or equal to 4
```

**Test class:** `OpenRouterRequestMapperTest`  
**Note:** Replaces the prior `LTD-13` assertion that `search_x_recent` stays unregistered for this prompt when classifier signals search.

---

### IGTA-10 — Prompt shows X active example

```gherkin
Scenario: IGTA-10 JSON XML prompt includes search_x_recent example when intent-activated
  Given toolCatalog contains entry "search_x_recent" with disclosureMode INTENT_ACTIVATED
  And activeTools names equal ["disclose_tool_schemas","web_search","web_extract","search_x_recent"]
  And strategy equals JSON_XML_ENVELOPE
  When SearchToolPromptComposer composes the system prompt
  Then the prompt contains the literal string "ActionResult{\"name\":\"search_x_recent\""
  And the prompt contains the literal substring "[intent-activated]"
```

**Test class:** `SearchToolPromptComposerTest`

---

### IGTA-11 — Prompt shows artifact active example

```gherkin
Scenario: IGTA-11 JSON XML prompt includes start_artifact example when intent-activated
  Given toolCatalog contains entry "start_artifact" with disclosureMode INTENT_ACTIVATED
  And activeTools includes ToolDefinition.START_ARTIFACT
  And strategy equals JSON_XML_ENVELOPE
  When SearchToolPromptComposer composes the system prompt
  Then the prompt contains the literal string "ActionResult{\"name\":\"start_artifact\""
  And the prompt does not list "start_artifact" in the deferred disclosure example array
```

**Test class:** `SearchToolPromptComposerTest`

---

### IGTA-15 — LiteRT strategy receives expanded activeTools

```gherkin
Scenario: IGTA-15 preparer sets activeTools for local LiteRT model when artifact intent fires
  Given active model localModelFormat equals LITERTLM
  And prompt equals "Generate a markdown document summarizing this chat"
  When ChatInferenceRequestPreparer prepares a FAST request
  Then GenerationOptions.toolCallStrategy equals "LITE_RT_NATIVE"
  And GenerationOptions.activeTools names contain "start_artifact"
```

**Test class:** `ChatInferenceRequestPreparerIntentGatingTest`

---

## Edge Case Scenarios

### IGTA-03 — X disabled in settings

```gherkin
Scenario: IGTA-03 X search disabled prevents search_x_recent activation
  Given searchEnabled equals true
  And xSearchEnabled equals false
  And SearchIntentClassifierPort.requiresSearch returns true for any embedding
  And prompt equals "Latest breaking news on X"
  When ChatInferenceRequestPreparer prepares a FAST request
  Then GenerationOptions.activeTools names do not contain "search_x_recent"
```

**Test class:** `ChatInferenceRequestPreparerIntentGatingTest`

---

### IGTA-06 — Pure artifact blocks web pre-retrieval

```gherkin
Scenario: IGTA-06 heuristic non-search artifact prompt skips web pre-retrieval
  Given searchEnabled equals true
  And SearchIntentClassifierPort.requiresSearch returns true for any embedding
  And ToolExecutorPort is a mock
  And prompt equals "I want this as an artifact that I can export"
  When ChatInferenceRequestPreparer prepares a FAST request
  Then GenerationOptions.activeTools names contain "start_artifact"
  And ToolExecutorPort.execute was called 0 times
  And SearchIntentClassifierPort.getSearchIntentProbability was called 0 times
```

**Test class:** `ChatInferenceRequestPreparerIntentGatingTest`

---

### IGTA-07 — Classifier below threshold

```gherkin
Scenario: IGTA-07 low classifier probability does not activate X
  Given searchEnabled equals true
  And xSearchEnabled equals true
  And SearchIntentClassifierPort.requiresSearch returns false for any embedding with threshold 0.52
  And SearchIntentClassifierPort.getSearchIntentProbability returns 0.10
  And prompt equals "Tell me today's weather in Boston"
  When ChatInferenceRequestPreparer prepares a FAST request
  Then GenerationOptions.activeTools names do not contain "search_x_recent"
```

**Test class:** `ToolIntentActivationPolicyTest` and `ChatInferenceRequestPreparerIntentGatingTest`

---

### IGTA-08 — Heuristic non-search without research bypass

```gherkin
Scenario: IGTA-08 ToolIntentActivationPolicy skips classifier on pure docgen
  Given prompt equals "Create a docx report from these notes"
  And embedding equals floatArrayOf(0.1f, 0.2f)
  And searchEnabled equals true
  And xSearchEnabled equals true
  And SearchIntentClassifierPort is a mock that would return true if called
  When ToolIntentActivationPolicy.resolve is evaluated
  Then the decision field runWebPreRetrieval equals false
  And the decision field activateSearchX equals false
  And SearchIntentClassifierPort.requiresSearch was called 0 times
```

**Test class:** `ToolIntentActivationPolicyTest`

---

### IGTA-12 — Null embedding

```gherkin
Scenario: IGTA-12 null embedding disables search path
  Given embedding equals null
  And searchEnabled equals true
  And xSearchEnabled equals true
  And prompt equals "Latest news headlines"
  When ToolIntentActivationPolicy.resolve is evaluated
  Then the decision field runWebPreRetrieval equals false
  And the decision field activateSearchX equals false
```

**Test class:** `ToolIntentActivationPolicyTest`

---

### IGTA-13 — Search disabled globally

```gherkin
Scenario: IGTA-13 searchEnabled false prevents X activation
  Given searchEnabled equals false
  And tavilyKeyPresent equals true
  And xSearchEnabled equals true
  And xApiKeyPresent equals true
  And SearchIntentClassifierPort.requiresSearch returns true for any embedding
  And prompt equals "Trending topics on X"
  When ChatInferenceRequestPreparer prepares a FAST request
  Then GenerationOptions.activeTools names do not contain "search_x_recent"
  And GenerationOptions.toolCatalog names do not contain "web_search"
```

**Test class:** `ChatInferenceRequestPreparerIntentGatingTest`

---

### IGTA-16 — Web toggle on without API key

```gherkin
Scenario: IGTA-16 missing Tavily key hides web tools and skips classifier
  Given searchEnabled equals true
  And searchProvider equals TAVILY
  And tavilyKeyPresent equals false
  And exaKeyPresent equals false
  And SearchIntentClassifierPort is a mock that would return true if called
  And prompt equals "What are today's mortgage rates?"
  When ChatInferenceRequestPreparer prepares a FAST request
  Then GenerationOptions.toolCatalog names do not contain "web_search"
  And GenerationOptions.toolCatalog names do not contain "web_extract"
  And GenerationOptions.activeTools names do not contain "web_search"
  And SearchIntentClassifierPort.getSearchIntentProbability was called 0 times
  And the system prompt does not contain "# RELEVANT WEB SEARCH RESULTS:"
```

**Test class:** `ChatInferenceRequestPreparerIntentGatingTest`

---

### IGTA-17 — X toggle on without API key

```gherkin
Scenario: IGTA-17 missing X API key omits search_x_recent from catalog
  Given searchEnabled equals true
  And tavilyKeyPresent equals true
  And searchProvider equals TAVILY
  And xSearchEnabled equals true
  And xApiKeyPresent equals false
  And SearchIntentClassifierPort.requiresSearch returns true for any embedding
  And prompt equals "What is trending on X right now?"
  When ChatInferenceRequestPreparer prepares a FAST request
  Then GenerationOptions.toolCatalog names do not contain "search_x_recent"
  And GenerationOptions.activeTools names do not contain "search_x_recent"
```

**Test class:** `ChatInferenceRequestPreparerIntentGatingTest`

---

### IGTA-18 — Web configured, X not configured

```gherkin
Scenario: IGTA-18 web-only configuration runs classifier but never activates X
  Given searchEnabled equals true
  And tavilyKeyPresent equals true
  And searchProvider equals TAVILY
  And xSearchEnabled equals false
  And xApiKeyPresent equals false
  And SearchIntentClassifierPort.requiresSearch returns true for any embedding
  And ToolExecutorPort.execute for web_search returns resultJson containing "Fixture search body"
  And prompt equals "Latest headlines about the election"
  When ChatInferenceRequestPreparer prepares a FAST request
  Then GenerationOptions.activeTools names contain "web_search"
  And GenerationOptions.activeTools names do not contain "search_x_recent"
  And GenerationOptions.toolCatalog names do not contain "search_x_recent"
  And the system prompt contains "# RELEVANT WEB SEARCH RESULTS:"
```

**Test class:** `ChatInferenceRequestPreparerIntentGatingTest`

---

### IGTA-14 — Disclosure resolver still accepts already-active tools

```gherkin
Scenario: IGTA-14 disclose_tool_schemas reports already_active for intent-activated tool
  Given toolCatalog contains entry "append_blocks" with disclosureMode INTENT_ACTIVATED
  And activeTools names include "append_blocks"
  And request tool_names equals ["append_blocks"]
  When ToolSchemaDisclosureResolver resolves the disclosure request
  Then the result field activatedToolNames equals []
  And the result payload string contains "already_active"
  And the result payload string contains "append_blocks"
```

**Test class:** `ToolSchemaDisclosureResolverTest` (extend existing file)

---

## Policy Unit Scenarios (ToolIntentActivationPolicy / SearchIntentPolicy)

### IGTA-05b — Research bypass flag

```gherkin
Scenario: IGTA-05b hasConcurrentResearchIntent true on combined prompt
  Given prompt equals "Research current mortgage rates and write a PDF report"
  When SearchIntentPolicy.hasConcurrentResearchIntent is evaluated
  Then the result equals true
```

**Test class:** `SearchIntentPolicyTest`

---

## Mutation-Defense Scenarios

### MUT-01 — X not always active

**Lazy implementation risk:** `ChatInferenceRequestPreparer` unconditionally adds `search_x_recent` to `activeTools` whenever `xSearchEnabled` is true, ignoring classifier output.

```gherkin
Scenario: MUT-01 search_x_recent absent when classifier requiresSearch is false
  Given searchEnabled equals true
  And xSearchEnabled equals true
  And SearchIntentClassifierPort.requiresSearch returns false for any embedding with threshold 0.52
  And prompt equals "Summarize this paragraph for me"
  When ChatInferenceRequestPreparer prepares a FAST request
  Then GenerationOptions.activeTools names equal ["disclose_tool_schemas","web_search","web_extract"]
```

**Test class:** `ChatInferenceRequestPreparerIntentGatingTest`  
**Fails when:** `search_x_recent` is hardcoded into `activeTools` for all X-enabled sessions.

---

### MUT-02 — Artifact activation independent of search path

**Lazy implementation risk:** Reusing `isHeuristicNonSearchPrompt` as a gate for artifact activation, so docgen prompts never receive artifact tools.

```gherkin
Scenario: MUT-02 artifact tools active while search path blocked
  Given searchEnabled equals true
  And SearchIntentClassifierPort.requiresSearch returns false for any embedding
  And prompt equals "Draft a PDF summary of our meeting notes"
  When ChatInferenceRequestPreparer prepares a FAST request
  Then GenerationOptions.activeTools names contain "start_artifact"
  And GenerationOptions.activeTools names contain "append_blocks"
  And GenerationOptions.activeTools names contain "search_current_artifact"
  And GenerationOptions.activeTools names do not contain "search_x_recent"
  And the system prompt does not contain "# RELEVANT WEB SEARCH RESULTS:"
```

**Test class:** `ChatInferenceRequestPreparerIntentGatingTest`  
**Fails when:** artifact trio activation is tied to `runWebPreRetrieval` or `activateSearchX` instead of `ArtifactIntentPolicy` alone.

---

### MUT-03 — Classifier skipped when web not configured

**Lazy implementation risk:** Running search-intent classification whenever `searchEnabled` is true, ignoring missing Tavily/Exa keys.

```gherkin
Scenario: MUT-03 classifier not invoked without configured web search skill
  Given searchEnabled equals true
  And searchProvider equals EXA
  And exaKeyPresent equals false
  And tavilyKeyPresent equals false
  And SearchIntentClassifierPort is a mock
  And prompt equals "Current weather in Boston"
  When ToolIntentActivationPolicy.resolve is evaluated with SettingsData above
  Then the decision field runWebPreRetrieval equals false
  And SearchIntentClassifierPort.requiresSearch was called 0 times
```

**Test class:** `ToolIntentActivationPolicyTest`  
**Fails when:** policy uses `searchEnabled` instead of `isWebSearchSkillConfigured()`.

---

## Policy Unit Scenarios (SearchSkillAvailability)

### IGTA-16b — Exa provider requires Exa key

```gherkin
Scenario: IGTA-16b isWebSearchSkillConfigured false when EXA selected without exa key
  Given SettingsData with searchEnabled true, searchProvider EXA, exaKeyPresent false, tavilyKeyPresent true
  When isWebSearchSkillConfigured is evaluated
  Then the result equals false
```

**Test class:** `SearchSkillAvailabilityTest`

---

## Regression Scenarios (Existing Tests)

The following existing tests must remain green after `T-5`:

| Existing test | Expected preservation |
|---------------|----------------------|
| `ChatInferenceRequestPreparerTest` `when search classifier returns true then pre-retrieval search is executed and injected` | System prompt still contains `# RELEVANT WEB SEARCH RESULTS:` and fixture content |
| `ChatInferenceRequestPreparerTest` `when heuristic marks non-search then pre-retrieval search is skipped even if classifier is positive` | No web results; classifier not invoked for prompt `I want this as an artifact that I can export` |

---

## Self-Review Checklist (Plan Author)

| Check | Status |
|-------|--------|
| Every MVP behavior has a task in `spec.md` | Yes — `T-0`–`T-10` |
| Configured-skill gating (keys + toggles) | Yes — `T-0`, `IGTA-16`–`IGTA-18`, `MUT-03` |
| Every task maps to scenario IDs | Yes — §6 in spec |
| Literal expected values in every `Then` | Yes |
| Mutation scenarios target named lazy implementations | Yes — `MUT-01`, `MUT-02` |
| X pre-retrieval explicitly deferred with follow-up ticket ID | Yes — `FEAT-x-pre-retrieval-query-shaping` |
| No placeholders | Yes |

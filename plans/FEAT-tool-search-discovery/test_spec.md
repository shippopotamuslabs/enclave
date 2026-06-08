# Test Spec: Tool Search Discovery

**Ticket ID:** `FEAT-tool-search-discovery`  
**Companion spec:** `plans/FEAT-tool-search-discovery/spec.md`

---

## Test Framework and Run Commands

| Layer | Framework | Location |
|-------|-----------|----------|
| Domain search / resolver | JUnit 5 + MockK | `core/domain/src/test/kotlin/...` |
| Feature inference JVM | JUnit 5 + MockK | `feature/inference/src/test/kotlin/...` |
| Rust engine / FFI | `cargo test` | `third_party/agent_core/tests/...` |

### Targeted iteration commands

```bash
./gradlew :core:domain:testDebugUnitTest --tests "*ToolBm25IndexTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*ToolSearchResolverTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*ToolSearchCatalogBuilderTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*ChatInferenceRequestPreparerTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*ChatInferenceRequestPreparerIntentGatingTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*SearchToolPromptComposerTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*ContextWindowPlannerTest*"

./gradlew :feature:inference:testDebugUnitTest --tests "*RustLoopInferenceServiceTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*ConversationManagerImplToolsTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*OpenAiRequestMapperTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*AnthropicRequestMapperTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*GoogleRequestMapperTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*OpenRouterRequestMapperTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*XaiRequestMapperTest*"

cargo test --manifest-path third_party/agent_core/Cargo.toml --test tool_search_discovery_test
```

### Completion gate

```bash
./scripts/build-agent-bindings.sh
./gradlew ktlintCheck
./gradlew detekt
./gradlew testDebugUnitTest
./gradlew :app:assembleDebug
cargo test --manifest-path third_party/agent_core/Cargo.toml
```

---

## Scenario Index

| ID | Category | Description |
|----|----------|-------------|
| `TSD-01` | Happy | Preparer preloads `search_tools` + conditional web tools; excludes `disclose_tool_schemas` |
| `TSD-02` | Happy | Local JSON/XML prompt has `# TOOL DISCOVERY:` and no `# TOOL CATALOG:` |
| `TSD-03` | Happy | `SDK_NATIVE` prompt omits tool discovery prose |
| `TSD-04` | Happy | BM25 ranks tool by name token in query |
| `TSD-05` | Happy | BM25 ranks tool by description keyword |
| `TSD-06` | Happy | BM25 ranks tool by `categoryTags` token |
| `TSD-07` | Happy | Embedding fallback runs when BM25 returns zero hits |
| `TSD-08` | Happy | Embedding fallback runs when top BM25 score is below `τ_bm25` |
| `TSD-09` | Happy | `search_tools` activates deferred tool via `activatedToolNames` |
| `TSD-10` | Happy | Success payload includes compact `matches` with `score` and `match_method` |
| `TSD-11` | Happy | Rust host intercepts `search_tools` and never calls `ToolExecutorPort` |
| `TSD-12` | Happy | LiteRT host intercepts `search_tools` and updates cache active names |
| `TSD-13` | Happy | `wireToolsForRequest` registers preload wire registry only (no deferred tools) |
| `TSD-27` | Happy | Wire registry appends activated tool and persists on next model call |
| `TSD-14` | Happy | Intent-gating pre-activates artifact trio without search |
| `TSD-15` | Happy | Already-active matched tools appear in `already_active_tools` only |
| `TSD-16` | Happy | Catalog builder precomputes one 384-d embedding per catalog tool |
| `TSD-17` | Edge | Empty query returns exact error payload |
| `TSD-18` | Edge | No matches returns `status=ok` with empty arrays |
| `TSD-19` | Edge | Embedding fallback below `τ_embed` activates nothing |
| `TSD-20` | Edge | `read_context_reference` is searchable but not preloaded |
| `TSD-21` | Edge | Web tools absent from catalog when web skill not configured |
| `TSD-22` | Edge | `attached_image_inspect` preloaded only when image tool-eligible |
| `TSD-23` | Edge | `ContextWindowPlanner` does not charge compact-catalog tokens |
| `TSD-24` | Edge | Duplicate matched names activate once preserving first-seen order |
| `TSD-25` | Edge | `limit` caps returned matches and activations |
| `TSD-26` | Edge | Rust rejects direct call to deferred tool before search activation |
| `MUT-01` | Mutation | Local prompt must never contain `# TOOL CATALOG:` |
| `MUT-02` | Mutation | API wire must never register full eligible pool |
| `MUT-03` | Mutation | `disclose_tool_schemas` must not appear in preload or catalog |
| `MUT-04` | Mutation | Embedding fallback must not run when BM25 top score `>= τ_bm25` |
| `MUT-05` | Mutation | System prompt must not list all deferred tool names for memorization |

**Total scenarios:** `32`

---

## Shared Test Fixtures

Unless a scenario overrides them:

| Fixture | Value |
|---------|-------|
| Reserved discovery tool name | `search_tools` |
| Removed legacy tool name | `disclose_tool_schemas` |
| `τ_bm25` | `1.5` |
| `τ_embed` | `0.55` |
| `DEFAULT_SEARCH_LIMIT` | `5` |
| `MAX_SEARCH_LIMIT` | `10` |
| `EMBEDDING_DIMENSION` | `384` |
| Baseline preloaded active names (web configured, no image) | `["search_tools","web_search","web_extract"]` |
| Deferred tool used in tests | `append_blocks` |
| Memory deferred tool | `search_chat_history` |
| Context deferred tool | `read_context_reference` |
| Artifact tool names | `["start_artifact","append_blocks","search_current_artifact"]` |
| X tool name | `search_x_recent` |
| Empty-query error message | `search_tools requires a non-empty query.` |
| Discovery prompt header | `# TOOL DISCOVERY:` |
| Legacy catalog header (must be absent) | `# TOOL CATALOG:` |
| Legacy disclosure header (must be absent) | `# SCHEMA DISCLOSURE:` |
| Test embedding (generic) | `floatArrayOf(0.1f, 0.2f, 0.3f)` repeated/padded to 384 elements in resolver tests |
| Configured web skill shorthand | `searchEnabled=true`, `tavilyKeyPresent=true`, `searchProvider=TAVILY` |
| `ToolDisclosureMode` for intent-activated entries | `INTENT_ACTIVATED` |

### BM25 fixture catalog (used in `TSD-04`–`TSD-08`, `TSD-24`, `TSD-25`)

| Tool name | Description snippet | categoryTags |
|-----------|---------------------|--------------|
| `append_blocks` | `Add content to the in-progress PDF/Word document` | `["artifact"]` |
| `search_chat_history` | `Search the user's past conversation history` | `["memory"]` |
| `read_context_reference` | `Retrieve the full content of a context reference` | `["context"]` |

### Controlled embedding map (used in `TSD-07`, `TSD-08`, `TSD-19`, `MUT-04`)

| Tool | Embedding vector (384-d) | Purpose |
|------|--------------------------|---------|
| `append_blocks` | `[1.0, 0.0, 0.0, …]` (384 elements, L2-normalized) | High cosine with query `[1.0, 0.0, 0.0, …]` |
| `search_chat_history` | `[0.0, 1.0, 0.0, …]` | Orthogonal to append query |
| Query `"document writing"` (fallback) | `[1.0, 0.0, 0.0, …]` | Activates `append_blocks` when BM25 fails |
| Query `"unrelated quantum"` (fallback) | `[0.0, 0.0, 1.0, …]` | Cosine with all fixtures `< 0.55` |

---

## Happy Path Scenarios

### TSD-01 — Preparer preload policy

```gherkin
Scenario: TSD-01 preparer preloads search_tools and excludes disclose_tool_schemas
  Given search is enabled
  And image inspection is not eligible
  And artifact generation is not intent-activated
  When ChatInferenceRequestPreparer prepares a FAST request
  Then GenerationOptions.activeTools names equal ["search_tools","web_search","web_extract"]
  And GenerationOptions.toolCatalog names do not contain "disclose_tool_schemas"
  And GenerationOptions.toolCatalog names contain "append_blocks"
  And GenerationOptions.toolSearchIndex is not null
  And ToolCatalogEntry("web_search").disclosureMode equals "PRELOADED"
  And ToolCatalogEntry("append_blocks").disclosureMode equals "DISCLOSE_ON_DEMAND"
```

### TSD-02 — Local prompt discovery block

```gherkin
Scenario: TSD-02 JSON/XML prompt uses discovery block without catalog
  Given toolCatalog contains deferred "append_blocks"
  And activeTools names equal ["search_tools","web_search","web_extract"]
  When SearchToolPromptComposer composes a JSON_XML_ENVELOPE system prompt
  Then the prompt contains the literal string "# TOOL DISCOVERY:"
  And the prompt contains the literal string "search_tools"
  And the prompt does not contain the literal string "# TOOL CATALOG:"
  And the prompt does not contain the literal string "# SCHEMA DISCLOSURE:"
  And the prompt contains the literal string "ActionResult{\"name\":\"web_search\""
  And the prompt does not contain the literal string "ActionResult{\"name\":\"append_blocks\""
```

### TSD-03 — SDK_NATIVE skips discovery prose

```gherkin
Scenario: TSD-03 SDK_NATIVE composer returns no local tool contract
  Given toolCatalog contains "append_blocks"
  And activeTools names equal ["search_tools","web_search"]
  When SearchToolPromptComposer composes an SDK_NATIVE system prompt
  Then the tool-contract section equals ""
  And the composed string does not contain the literal string "# TOOL DISCOVERY:"
```

### TSD-04 — BM25 matches tool name

```gherkin
Scenario: TSD-04 BM25 ranks append_blocks for name-like query
  Given the BM25 fixture catalog
  And activeTools names equal ["search_tools"]
  When ToolSearchResolver resolves query "append_blocks" with limit 5
  Then the first match name equals "append_blocks"
  And the first match match_method equals "bm25"
  And the first match score is greater than 0.0
  And EmbeddingEnginePort.getEmbedding is called 0 times
```

### TSD-05 — BM25 matches description

```gherkin
Scenario: TSD-05 BM25 ranks append_blocks for description keyword
  Given the BM25 fixture catalog
  And activeTools names equal ["search_tools"]
  When ToolSearchResolver resolves query "in-progress PDF document" with limit 5
  Then the first match name equals "append_blocks"
  And the first match match_method equals "bm25"
  And EmbeddingEnginePort.getEmbedding is called 0 times
```

### TSD-06 — BM25 matches category tag

```gherkin
Scenario: TSD-06 BM25 ranks memory tool for memory tag query
  Given the BM25 fixture catalog
  And activeTools names equal ["search_tools"]
  When ToolSearchResolver resolves query "memory recall" with limit 5
  Then the first match name equals "search_chat_history"
  And the first match match_method equals "bm25"
```

### TSD-07 — Embedding fallback on empty BM25

```gherkin
Scenario: TSD-07 embedding fallback activates when BM25 returns no hits
  Given the BM25 fixture catalog
  And the controlled embedding map
  And activeTools names equal ["search_tools"]
  When ToolSearchResolver resolves query "document writing" with limit 5
  Then EmbeddingEnginePort.getEmbedding is called 1 times
  And the first match name equals "append_blocks"
  And the first match match_method equals "embedding"
  And activatedToolNames equal ["append_blocks"]
```

### TSD-08 — Embedding fallback on low BM25 score

```gherkin
Scenario: TSD-08 embedding fallback runs when top BM25 score is below tau_bm25
  Given the BM25 fixture catalog
  And the controlled embedding map
  And BM25 top score for query "doc" equals 0.8
  And activeTools names equal ["search_tools"]
  When ToolSearchResolver resolves query "doc" with limit 5
  Then EmbeddingEnginePort.getEmbedding is called 1 times
  And the first match match_method equals "embedding"
```

### TSD-09 — Activation via activatedToolNames

```gherkin
Scenario: TSD-09 search_tools populates activatedToolNames for deferred tool
  Given the BM25 fixture catalog
  And activeTools names equal ["search_tools","web_search","web_extract"]
  When ToolSearchResolver resolves query "append_blocks" with limit 5
  Then resultJson status equals "ok"
  And activatedToolNames equal ["append_blocks"]
  And resultJson activated_tools equals ["append_blocks"]
```

### TSD-10 — Compact matches in payload

```gherkin
Scenario: TSD-10 success payload exposes ranked compact matches
  Given the BM25 fixture catalog
  And activeTools names equal ["search_tools"]
  When ToolSearchResolver resolves query "append_blocks" with limit 5
  Then resultJson matches[0].name equals "append_blocks"
  And resultJson matches[0].match_method equals "bm25"
  And resultJson matches[0].score is greater than 0.0
  And resultJson tools[0].name equals "append_blocks"
  And resultJson tools[0].call_example contains "append_blocks"
```

### TSD-11 — Rust intercepts search_tools

```gherkin
Scenario: TSD-11 RustLoopInferenceService handles search_tools without ToolExecutorPort
  Given ToolExecutorPort call count is 0
  When RustLoopInferenceService executes AgentToolCall name "search_tools" arguments {"query":"append_blocks"}
  Then ToolExecutorPort call count equals 0
  And AgentToolResult.activatedToolNames equal ["append_blocks"]
  And AgentToolResult.resultJson status equals "ok"
```

### TSD-12 — LiteRT intercepts search_tools

```gherkin
Scenario: TSD-12 LiteRtToolCallLoop updates active names after search
  Given ConversationSignatureCache.activeToolNames equal ["search_tools","web_search","web_extract"]
  And cache.toolCatalog contains deferred "append_blocks"
  When LiteRtToolCallLoop executes tool "search_tools" with arguments {"query":"append_blocks"}
  Then ConversationSignatureCache.activeToolNames equal ["search_tools","web_search","web_extract","append_blocks"]
  And ToolExecutorPort call count equals 0
```

### TSD-13 — Preload-only wire registry at turn start

```gherkin
Scenario: TSD-13 wireToolsForRequest registers preload wire registry only
  Given GenerationOptions.toolCatalog names equal ["search_tools","web_search","web_extract","append_blocks"]
  And GenerationOptions.activeTools names equal ["search_tools","web_search","web_extract"]
  And wireRegistry equals preload names ["search_tools","web_search","web_extract"]
  When wireToolsForRequest runs with toolContextIndexJson null
  Then registered tool names equal ["search_tools","web_search","web_extract"]
  And registered tool count equals 3
```

### TSD-27 — Wire registry grows append-only after search

```gherkin
Scenario: TSD-27 activated tool persists on wire for subsequent model calls in session
  Given wireRegistry initialized to ["search_tools","web_search","web_extract"]
  When search_tools activates ["append_blocks"]
  Then wireRegistry names equal ["search_tools","web_search","web_extract","append_blocks"]
  When wireToolsForRequest runs on the next completeModelTurn in the same sendPrompt session
  Then registered tool names contain "append_blocks"
  And registered tool names do not contain any catalog name outside wireRegistry
  And wireRegistry still contains "web_search" after a later turn with no new activations
```

### TSD-14 — Intent-gating pre-activates artifacts

```gherkin
Scenario: TSD-14 artifact intent adds trio to activeTools without search_tools call
  Given prompt "Write a PDF report about quarterly revenue"
  When ChatInferenceRequestPreparer prepares a request
  Then GenerationOptions.activeTools names contain "start_artifact"
  And GenerationOptions.activeTools names contain "append_blocks"
  And GenerationOptions.activeTools names contain "search_current_artifact"
  And ToolCatalogEntry("start_artifact").disclosureMode equals "INTENT_ACTIVATED"
```

### TSD-15 — Already-active reporting

```gherkin
Scenario: TSD-15 already active tool is reported not re-activated
  Given the BM25 fixture catalog
  And activeTools names equal ["search_tools","append_blocks"]
  When ToolSearchResolver resolves query "append_blocks" with limit 5
  Then activatedToolNames equal []
  And resultJson already_active_tools equal ["append_blocks"]
  And resultJson activated_tools equal []
  And resultJson tools equal []
```

### TSD-16 — Embeddings precomputed per catalog tool

```gherkin
Scenario: TSD-16 catalog builder embeds every catalog entry once
  Given toolCatalog size equals 3
  And EmbeddingEnginePort returns deterministic 384-d vectors per call
  When ToolSearchCatalogBuilder.build runs
  Then snapshot.embeddingsByToolName size equals 3
  And snapshot.embeddingsByToolName["append_blocks"] length equals 384
  And EmbeddingEnginePort.getEmbedding is called 3 times
```

---

## Edge and Error Scenarios

### TSD-17 — Empty query error

```gherkin
Scenario: TSD-17 empty query returns exact error payload
  Given the BM25 fixture catalog
  When ToolSearchResolver resolves query "   " with limit 5
  Then resultJson status equals "error"
  And resultJson message equals "search_tools requires a non-empty query."
  And activatedToolNames equal []
  And EmbeddingEnginePort.getEmbedding is called 0 times
```

### TSD-18 — No matches success

```gherkin
Scenario: TSD-18 no matches returns ok with empty arrays
  Given the BM25 fixture catalog
  And the controlled embedding map
  When ToolSearchResolver resolves query "unrelated quantum" with limit 5
  Then resultJson status equals "ok"
  And resultJson matches equal []
  And resultJson activated_tools equal []
  And activatedToolNames equal []
```

### TSD-19 — Embedding below threshold

```gherkin
Scenario: TSD-19 sub-threshold embedding does not activate
  Given the BM25 fixture catalog
  And the controlled embedding map
  And activeTools names equal ["search_tools"]
  When ToolSearchResolver resolves query "unrelated quantum" with limit 5
  Then EmbeddingEnginePort.getEmbedding is called 1 times
  And activatedToolNames equal []
  And resultJson matches equal []
```

### TSD-20 — read_context_reference deferred

```gherkin
Scenario: TSD-20 read_context_reference is catalog-visible but not preloaded
  Given search is enabled
  And image inspection is not eligible
  When ChatInferenceRequestPreparer prepares a request
  Then GenerationOptions.toolCatalog names contain "read_context_reference"
  And GenerationOptions.activeTools names do not contain "read_context_reference"
```

### TSD-21 — Web tools gated by configuration

```gherkin
Scenario: TSD-21 web tools omitted when web skill not configured
  Given searchEnabled equals false
  When ChatInferenceRequestPreparer prepares a request
  Then GenerationOptions.toolCatalog names do not contain "web_search"
  And GenerationOptions.activeTools names equal ["search_tools"]
```

### TSD-22 — Image inspect conditional preload

```gherkin
Scenario: TSD-22 attached_image_inspect preloaded only for image tool path
  Given search is enabled
  And imageHandling equals TOOL
  When ChatInferenceRequestPreparer prepares a request
  Then GenerationOptions.activeTools names contain "attached_image_inspect"
  Given imageHandling equals NONE
  When ChatInferenceRequestPreparer prepares a request
  Then GenerationOptions.activeTools names do not contain "attached_image_inspect"
```

### TSD-23 — Context planner excludes catalog charge

```gherkin
Scenario: TSD-23 ContextWindowPlanner ignores deferred catalog token bulk
  Given toolCatalog contains 10 deferred tools each with catalog token cost 50
  And activeTools token cost equals 40
  And discovery instruction token cost equals 12
  When ContextWindowPlanner.budgetFor runs
  Then Budget.toolSchemaTokens equals 52
```

### TSD-24 — Duplicate match dedupe

```gherkin
Scenario: TSD-24 duplicate activation names dedupe in first-seen order
  Given the BM25 fixture catalog
  And activeTools names equal ["search_tools"]
  When ToolSearchResolver resolves query "append_blocks append_blocks" with limit 5
  Then activatedToolNames equal ["append_blocks"]
```

### TSD-25 — Limit caps results

```gherkin
Scenario: TSD-25 limit parameter caps matches and activations
  Given the BM25 fixture catalog with 3 tools
  And activeTools names equal ["search_tools"]
  When ToolSearchResolver resolves query "search" with limit 1
  Then resultJson matches size equals 1
  And activatedToolNames size is less than or equal to 1
```

### TSD-26 — Rust rejects undeferred tool call

```gherkin
Scenario: TSD-26 Rust validation rejects append_blocks before activation
  Given active_tool_names equal ["search_tools","web_search","web_extract"]
  When the agent engine validates a tool call named "append_blocks"
  Then validation outcome equals "rejected"
  And repair suggestion references "search_tools"
```

---

## Mutation-Defense Scenarios

### MUT-01 — No catalog block in local prompt

```gherkin
Scenario: MUT-01 local prompt must never include TOOL CATALOG header
  Given toolCatalog contains 8 deferred tools
  And activeTools names equal ["search_tools","web_search","web_extract"]
  When SearchToolPromptComposer composes JSON_XML_ENVELOPE and LITE_RT_NATIVE prompts
  Then neither prompt contains the literal string "# TOOL CATALOG:"
  And neither prompt contains the literal string "# SCHEMA DISCLOSURE:"
```

### MUT-02 — No full pool on API wire

```gherkin
Scenario: MUT-02 wireToolsForRequest must not register deferred append_blocks
  Given GenerationOptions.activeTools names equal ["search_tools","web_search"]
  And GenerationOptions.toolCatalog names equal ["search_tools","web_search","web_extract","append_blocks"]
  When OpenAiRequestMapper maps the request using wireToolsForRequest output
  Then registered tool names equal ["search_tools","web_search"]
  And serialized tool payload does not contain the literal string "\"append_blocks\""
```

### MUT-03 — disclose_tool_schemas fully removed

```gherkin
Scenario: MUT-03 disclose_tool_schemas absent from preload and catalog
  Given search is enabled
  When ChatInferenceRequestPreparer prepares a request
  Then GenerationOptions.toolCatalog names do not contain "disclose_tool_schemas"
  And GenerationOptions.activeTools names do not contain "disclose_tool_schemas"
  And ToolDefinition.ALL_TOOLS names do not contain "disclose_tool_schemas"
```

### MUT-04 — No embed when BM25 sufficient

```gherkin
Scenario: MUT-04 embedding fallback skipped when BM25 top score meets threshold
  Given the BM25 fixture catalog
  And activeTools names equal ["search_tools"]
  When ToolSearchResolver resolves query "append_blocks" with limit 5
  And the first match score is greater than or equal to 1.5
  Then EmbeddingEnginePort.getEmbedding is called 0 times
  And the first match match_method equals "bm25"
```

### MUT-05 — No exhaustive name list in system prompt

```gherkin
Scenario: MUT-05 system prompt does not enumerate deferred tool names
  Given toolCatalog names equal ["search_tools","web_search","web_extract","search_chat_history","manage_memories","append_blocks"]
  And activeTools names equal ["search_tools","web_search","web_extract"]
  When SearchToolPromptComposer composes JSON_XML_ENVELOPE system prompt
  Then the prompt does not contain the literal string "- search_chat_history"
  And the prompt does not contain the literal string "- append_blocks"
  And the prompt does not contain the literal string "deferred"
```

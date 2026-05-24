# Test Spec: Lazy Tool Disclosure

**Ticket ID:** `FEAT-lazy-tool-disclosure`  
**Companion spec:** `plans/FEAT-lazy-tool-disclosure/spec.md`

---

## Test Framework and Run Commands

| Layer | Framework | Location |
|-------|-----------|----------|
| Domain model / use case | JUnit 5 + MockK + Turbine where applicable | `core/domain/src/test/kotlin/...` |
| Feature inference JVM | JUnit 5 + MockK | `feature/inference/src/test/kotlin/...` |
| Rust engine / FFI | `cargo test` | `third_party/agent_core/tests/...` |

### Targeted iteration commands

```bash
./gradlew :core:domain:testDebugUnitTest --tests "*ToolSchemaDisclosureResolverTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*ChatInferenceRequestPreparerTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*SearchToolPromptComposerTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*ContextWindowPlannerTest*"

./gradlew :feature:inference:testDebugUnitTest --tests "*RustLoopInferenceServiceTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*OpenAiRequestMapperTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*AnthropicRequestMapperTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*GoogleRequestMapperTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*OpenRouterRequestMapperTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*XaiRequestMapperTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*ConversationManagerImplToolsTest*"

cargo test --manifest-path third_party/agent_core/Cargo.toml --test tool_context_test
cargo test --manifest-path third_party/agent_core/Cargo.toml --test lazy_tool_disclosure_test
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
| `LTD-01` | Happy | Request preparer splits catalog from active tools and seeds the preload policy |
| `LTD-02` | Happy | JSON/XML prompt renders compact catalog and preloaded examples only |
| `LTD-03` | Happy | LiteRT prompt renders disclosure contract and active-only native examples |
| `LTD-04` | Happy | Provider-native request mappers register only active tools |
| `LTD-05` | Happy | Disclosure tool activates a deferred tool on the next Rust-backed turn |
| `LTD-06` | Edge | Disclosure tool returns exact unknown/already-active reporting and no activation when nothing valid resolves |
| `LTD-07` | Edge | ContextWindowPlanner counts compact catalog plus active schemas, not deferred full schemas |
| `LTD-08` | Happy | Rust model request exposes active-only `toolsSchemaJson` while catalog/full registry stay session-side |
| `LTD-09` | Happy | Rust session merges `activated_tool_names` and expands the active schema set in order |
| `LTD-10` | Edge | Rust validation rejects direct calls to deferred tools before disclosure |
| `LTD-11` | Edge | Disclosure interception bypasses `ToolExecutorPort` on Rust and LiteRT paths |
| `LTD-12` | Happy | LiteRT native toolset list rebuilds after disclosure and exposes the newly active tool |
| `LTD-13` | Edge | SDK-native compatibility keeps `search_x_recent` catalog-visible but inactive until disclosure |
| `LTD-14` | Edge | Non-Rust mapper fallback honors `activeTools` even if `availableTools` still contains extra tools |
| `MUT-01` | Mutation | Deferred tool schema must never appear in provider/native registration before disclosure |
| `MUT-02` | Mutation | Prompt must never emit a deferred tool call example before disclosure |
| `MUT-03` | Mutation | Adding deep nested schema to a deferred tool must not increase active-schema budget tokens |
| `MUT-04` | Mutation | Duplicate disclosure requests activate a tool once and preserve first-seen order |
| `MUT-05` | Mutation | An invalid disclosure request leaves the active set unchanged and returns the exact error payload |

**Total scenarios:** `19`

---

## Shared Test Fixtures

Unless a scenario overrides them:

| Fixture | Value |
|---------|-------|
| Reserved disclosure tool name | `disclose_tool_schemas` |
| Always-preloaded active tool names | `["disclose_tool_schemas","web_search","web_extract"]` |
| Conditional preloaded image tool | `attached_image_inspect` when image tool eligibility is true |
| Deferred tool used in tests | `append_blocks` |
| Deferred X tool used in tests | `search_x_recent` |
| Disclosure error message | `No disclosable tools resolved from request.` |
| Rust active-tool merge order | first-seen order, duplicates removed |

---

## Happy Path Scenarios

### LTD-01 — Request preparer builds catalog + active split

```gherkin
Scenario: LTD-01 request preparer seeds catalog and active tools separately
  Given search is enabled
  And image inspection is not eligible
  And artifact generation is eligible
  When ChatInferenceRequestPreparer prepares a FAST request for a Rust-backed provider
  Then GenerationOptions.toolCatalog names equal ["disclose_tool_schemas","web_search","web_extract","search_chat_history","search_chat","get_message_context","manage_memories","start_artifact","append_blocks","search_current_artifact"]
  And GenerationOptions.activeTools names equal ["disclose_tool_schemas","web_search","web_extract"]
  And GenerationOptions.availableTools names equal ["disclose_tool_schemas","web_search","web_extract"]
  And ToolCatalogEntry("append_blocks").disclosureMode equals "DISCLOSE_ON_DEMAND"
  And ToolCatalogEntry("web_search").disclosureMode equals "PRELOADED"
```

### LTD-02 — JSON/XML prompt shows compact catalog and active-only examples

```gherkin
Scenario: LTD-02 JSON/XML prompt keeps deferred examples out of the base prompt
  Given toolCatalog contains preloaded "web_search" and deferred "append_blocks"
  And activeTools names equal ["disclose_tool_schemas","web_search","web_extract"]
  When SearchToolPromptComposer composes a JSON_XML_ENVELOPE system prompt
  Then the prompt contains the literal header "# TOOL CATALOG:"
  And the prompt contains the literal header "# SCHEMA DISCLOSURE:"
  And the prompt contains the literal string "ActionResult{\"name\":\"web_search\""
  And the prompt contains the literal string "ActionResult{\"name\":\"disclose_tool_schemas\""
  And the prompt does not contain the literal string "ActionResult{\"name\":\"append_blocks\""
  And the prompt does not contain the literal string "ActionResult{\"name\":\"search_current_artifact\""
```

### LTD-03 — LiteRT prompt shows disclosure and active-only examples

```gherkin
Scenario: LTD-03 LiteRT prompt keeps deferred native examples out of the base prompt
  Given toolCatalog contains preloaded "web_search" and deferred "append_blocks"
  And activeTools names equal ["disclose_tool_schemas","web_search","web_extract"]
  When SearchToolPromptComposer composes a LITE_RT_NATIVE system prompt
  Then the prompt contains the literal string "call:web_search{query: <|\"|>...<|\"|>}"
  And the prompt contains the literal string "call:disclose_tool_schemas{tool_names: <|\"|>[\"append_blocks\"]<|\"|>}"
  And the prompt does not contain the literal string "call:append_blocks{"
  And the prompt does not contain the literal string "call:search_current_artifact{"
```

### LTD-04 — Provider mappers register only active tools

```gherkin
Scenario: LTD-04 provider-native request mappers use activeTools only
  Given GenerationOptions.toolCatalog contains "web_search" and "append_blocks"
  And GenerationOptions.activeTools names equal ["disclose_tool_schemas","web_search"]
  And GenerationOptions.availableTools names equal ["disclose_tool_schemas","web_search","append_blocks"]
  When OpenAiRequestMapper maps the request
  Then the registered tool names equal ["disclose_tool_schemas","web_search"]
  And the registered tool count equals 2
```

### LTD-05 — Disclosure activates deferred tools on the next Rust turn

```gherkin
Scenario: LTD-05 disclosure tool activates append_blocks for the next Rust-backed model turn
  Given the Rust-backed active tool names equal ["disclose_tool_schemas","web_search","web_extract"]
  And "append_blocks" exists in the deferred catalog
  When the model calls disclose_tool_schemas with ["append_blocks"]
  And RustLoopInferenceService returns AgentToolResult.activatedToolNames equal ["append_blocks"]
  And the engine submits the tool result
  Then the disclosure tool result JSON.status equals "ok"
  And the disclosure tool result JSON.activated_tools equals ["append_blocks"]
  And the next ModelRequest.toolsSchemaJson tool names equal ["disclose_tool_schemas","web_search","web_extract","append_blocks"]
```

### LTD-08 — Rust emits active-only schemas while retaining separate catalog/registry

```gherkin
Scenario: LTD-08 Rust model requests keep active schemas separate from catalog and registry
  Given SessionIoParams.toolCatalogJson contains 6 catalog entries
  And SessionIoParams.toolSchemaRegistryJson contains full schemas for 6 tools
  And SessionIoParams.toolsSchemaJson contains active schemas for ["disclose_tool_schemas","web_search","web_extract"]
  When AgentEngine produces the first CallModel request
  Then ModelRequest.toolsSchemaJson tool names equal ["disclose_tool_schemas","web_search","web_extract"]
  And ToolContextIndex.advertised_tools names equal ["disclose_tool_schemas","web_search","web_extract","search_chat_history","append_blocks","search_current_artifact"]
  And read_context_reference(full_schema_ref) contains the literal string "\"append_blocks\""
```

### LTD-09 — Rust merges activation names in order

```gherkin
Scenario: LTD-09 Rust merges activated tool names in first-seen order
  Given the current active tool names equal ["disclose_tool_schemas","web_search"]
  When submit_tool_results receives activated_tool_names ["append_blocks","search_current_artifact","append_blocks"]
  Then the persisted active tool names equal ["disclose_tool_schemas","web_search","append_blocks","search_current_artifact"]
  And the next ModelRequest.toolsSchemaJson tool names equal ["disclose_tool_schemas","web_search","append_blocks","search_current_artifact"]
```

### LTD-12 — LiteRT native toolset rebuilds after disclosure

```gherkin
Scenario: LTD-12 LiteRT native toolset list expands after disclosure
  Given ConversationSignatureCache active tool names equal ["disclose_tool_schemas","web_search","web_extract"]
  And "append_blocks" exists in the deferred catalog
  When LiteRtToolCallLoop handles disclose_tool_schemas with ["append_blocks"]
  Then the disclosure result JSON.activated_tools equals ["append_blocks"]
  And ConversationSignatureCache active tool names equal ["disclose_tool_schemas","web_search","web_extract","append_blocks"]
  And LiteRtNativeToolsets.buildToolList exposes the callable names ["disclose_tool_schemas","tavily_web_search","tavily_extract","append_blocks"]
```

---

## Error and Edge Scenarios

### LTD-06 — Unknown and already-active reporting

```gherkin
Scenario: LTD-06 disclosure reports already-active and unknown names exactly
  Given active tool names equal ["disclose_tool_schemas","web_search","web_extract"]
  And deferred catalog contains "append_blocks"
  When ToolSchemaDisclosureResolver resolves ["web_search","not_a_tool"]
  Then the result JSON.status equals "error"
  And the result JSON.message equals "No disclosable tools resolved from request."
  And the result JSON.activated_tools equals []
  And the result JSON.already_active_tools equals ["web_search"]
  And the result JSON.unknown_tools equals ["not_a_tool"]
```

### LTD-07 — ContextWindowPlanner counts compact catalog + active schemas only

```gherkin
Scenario: LTD-07 deferred full schemas are excluded from tooling budget
  Given a fake TokenCounter returns 12 for the compact catalog payload
  And the same fake TokenCounter returns 40 for the active schema payload
  And the same fake TokenCounter returns 300 for the deferred append_blocks full schema payload
  When ContextWindowPlanner budgets a request with one deferred append_blocks entry and active tools ["disclose_tool_schemas","web_search"]
  Then Budget.toolSchemaTokens equals 52
  And Budget.toolSchemaTokens does not equal 352
```

### LTD-10 — Deferred tool call rejected before disclosure

```gherkin
Scenario: LTD-10 direct call to deferred tool fails closed before disclosure
  Given Rust active tool names equal ["disclose_tool_schemas","web_search","web_extract"]
  And "append_blocks" exists only in the deferred catalog
  When the model emits a direct append_blocks tool call before any disclosure
  Then the repair rejection payload error equals "Tool 'append_blocks' is not active. Call disclose_tool_schemas first."
  And the rejection payload available_tools equals ["disclose_tool_schemas","web_search","web_extract"]
```

### LTD-11 — Disclosure bypasses ToolExecutorPort

```gherkin
Scenario: LTD-11 disclosure interception never invokes ToolExecutorPort
  Given a Recording ToolExecutorPort with callCount 0
  When RustLoopInferenceService handles disclose_tool_schemas with ["append_blocks"]
  Then ToolExecutorPort.callCount equals 0
  And AgentToolResult.activatedToolNames equals ["append_blocks"]
  When LiteRtToolCallLoop handles disclose_tool_schemas with ["append_blocks"]
  Then ToolExecutorPort.callCount still equals 0
```

### LTD-13 — X stays catalog-visible but inactive until disclosure

```gherkin
Scenario: LTD-13 search_x_recent remains deferred until disclosed
  Given xSearchEnabled is true
  When ChatInferenceRequestPreparer prepares an SDK_NATIVE request
  Then GenerationOptions.toolCatalog contains a ToolCatalogEntry named "search_x_recent"
  And GenerationOptions.activeTools names do not contain "search_x_recent"
  When OpenRouterRequestMapper maps the request before disclosure
  Then the registered tool names do not contain "search_x_recent"
```

### LTD-14 — Non-Rust mapper fallback honors activeTools

```gherkin
Scenario: LTD-14 non-Rust mapper ignores stale availableTools extras
  Given GenerationOptions.activeTools names equal ["disclose_tool_schemas","web_search"]
  And GenerationOptions.availableTools names equal ["disclose_tool_schemas","web_search","append_blocks"]
  When GoogleRequestMapper maps the request
  Then the declared function names equal ["disclose_tool_schemas","web_search"]
  And the declared function names do not contain "append_blocks"
```

---

## Mutation-Defense Scenarios

### MUT-01 — Deferred schema never leaks into provider/native registration

```gherkin
Scenario: MUT-01 deferred schema never leaks before disclosure
  Given active tool names equal ["disclose_tool_schemas","web_search"]
  And deferred catalog contains "append_blocks"
  When OpenAiRequestMapper maps the request before disclosure
  Then the registered tool names equal ["disclose_tool_schemas","web_search"]
  And the serialized registered tool payload does not contain the literal string "\"append_blocks\""
```

### MUT-02 — Deferred examples never appear in the base prompt

```gherkin
Scenario: MUT-02 base prompt never prints deferred examples
  Given active tool names equal ["disclose_tool_schemas","web_search","web_extract"]
  And deferred catalog contains "append_blocks"
  When SearchToolPromptComposer composes the base prompt
  Then the prompt does not contain the literal string "ActionResult{\"name\":\"append_blocks\""
  And the prompt does not contain the literal string "call:append_blocks{"
```

### MUT-03 — Deferred schema growth does not affect active budget

```gherkin
Scenario: MUT-03 deferred nested schema changes do not increase active budget
  Given Budget A uses a deferred append_blocks schema token cost of 300
  And Budget B uses a deferred append_blocks schema token cost of 900
  And both budgets share the same compact catalog token cost 12 and active schema token cost 40
  When ContextWindowPlanner computes both budgets
  Then BudgetA.toolSchemaTokens equals 52
  And BudgetB.toolSchemaTokens equals 52
```

### MUT-04 — Duplicate disclosure preserves first-seen order

```gherkin
Scenario: MUT-04 duplicate disclosure requests activate once
  Given current active tool names equal ["disclose_tool_schemas","web_search"]
  When ToolSchemaDisclosureResolver resolves ["append_blocks","append_blocks","search_current_artifact"]
  Then AgentToolResult.activatedToolNames equals ["append_blocks","search_current_artifact"]
  And the next Rust active tool names equal ["disclose_tool_schemas","web_search","append_blocks","search_current_artifact"]
```

### MUT-05 — Invalid disclosure leaves the active set unchanged

```gherkin
Scenario: MUT-05 invalid disclosure request leaves active state unchanged
  Given current active tool names equal ["disclose_tool_schemas","web_search","web_extract"]
  When ToolSchemaDisclosureResolver resolves ["not_a_tool"]
  Then the result JSON.status equals "error"
  And the result JSON.message equals "No disclosable tools resolved from request."
  And AgentToolResult.activatedToolNames equals []
  And the next active tool names equal ["disclose_tool_schemas","web_search","web_extract"]
```


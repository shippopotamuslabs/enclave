# Spec: Lazy Tool Disclosure

**Ticket ID:** `FEAT-lazy-tool-disclosure`  
**Date:** `2026-05-23`  
**Contracts read:** `AGENTS.md`, `contracts/ARCHITECTURE_RULES.md`, `contracts/CODE_STYLE_RULES.md`, `contracts/INFERENCE_RULES.md`, `contracts/UNIT_TESTING.md`

---

## 1. Feature Summary

### Problem

Enclave currently computes one `availableTools` list and reuses it everywhere:

- `ChatInferenceRequestPreparer` builds one full eligible list.
- `SearchToolPromptComposer` injects schema-derived examples for every eligible prompt-visible tool.
- SDK-native provider mappers register every eligible tool with full schemas.
- `RustLoopInferenceService` serializes only `name` + `description` into `toolsSchemaJson`, but maps Rust `advertised_tools` back onto the full eligible Kotlin `ToolDefinition` list, so provider-native requests still get every schema.

This defeats schema deferral. The model sees a compact Rust-side summary, but Kotlin still pays the prompt, provider-registration, and budgeting cost of the full tool set.

### Target

Implement a first-class split between:

- `tool catalog`: every tool the turn is allowed to discover
- `active tools`: the smaller subset whose full schemas are currently callable by the model

The model must be able to activate deferred tools through a reserved disclosure tool. Activation must propagate across:

- prompt composition for text/local strategies
- provider-native tool registration for SDK providers
- LiteRT native toolset registration
- Rust agent-loop state and validation
- context budgeting

### Locked Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Initial split | Build `toolCatalog` and `activeTools` separately in `ChatInferenceRequestPreparer` | Eliminates the current single-list ambiguity at the domain boundary |
| Disclosure mechanism | Add reserved tool `disclose_tool_schemas` with pure domain resolver | Gives the model an explicit lifecycle transition instead of relying on hidden host heuristics |
| Preloaded tools | Preload only `disclose_tool_schemas`, `web_search`, `web_extract`, and `attached_image_inspect` when eligible | Keeps mandatory freshness and image-verification tools immediately callable while deferring memory, artifact, and media schemas |
| Provider registration | Register `activeTools` only | Provider-native calls must reflect the same activation state as the model-facing contract |
| Rust channel shape | Separate compact catalog and full registry channels; keep `toolsSchemaJson` as active-only | One field per semantic role is clearer and avoids the current “all tools vs active tools” confusion |
| Activation ownership | Rust agent session owns the authoritative active-tool set; Kotlin host supplies `activatedToolNames` on disclosure results | Prevents Kotlin/Rust drift and lets Rust validation fail closed on undisclosed tools |
| ToolExecutor boundary | `disclose_tool_schemas` is handled before `ToolExecutorPort`; real business tools still flow through `ToolExecutorPort` | Disclosure is session-state mutation, not external side-effect execution |
| Local text disclosure | Disclosure result payload includes full schemas and strategy-specific call examples | Lets JSON/XML and LiteRT text/native paths learn the newly active tool without rewriting the base system prompt |

### Non-goals

- Changing any Room schema or adding persistence for disclosure state
- Adding Android/framework types to `:core:domain`
- Rewriting the broader repair, compaction, or prompt-cache systems beyond the tool-state inputs they consume
- Deleting prompt-composer duplication in this ticket; both production implementations must be updated together

---

## 2. File Structure Table

| Action | Path | Responsibility |
|--------|------|----------------|
| **Create** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/inference/ToolCatalogModels.kt` | Pure domain models for `ToolCatalogEntry`, `ToolArgumentHint`, `ToolDisclosureMode`, and compact serialization rules |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/inference/ToolDefinition.kt` | Add disclosure metadata, compact catalog projection, reserved `DISCLOSE_TOOL_SCHEMAS`, and active/preloaded policy hooks |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/inference/ToolParameters.kt` | Expose reusable top-level argument-hint extraction from schemas without serializing full nested schema JSON into the catalog |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/inference/GenerationOptions.kt` | Add `toolCatalog` and `activeTools`; keep `availableTools` as transitional mirror populated from `activeTools` until all production reads are migrated |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/inference/AgentLoopModels.kt` | Extend `AgentLoopSessionIoParams` with `toolCatalogJson` and `toolSchemaRegistryJson`; extend `AgentToolResult` with `activatedToolNames` |
| **Create** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/ToolSchemaDisclosureResolver.kt` | Pure resolver for `disclose_tool_schemas`: validates tool names, returns disclosure payload, and reports activated names |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/port/inference/PromptComposerPort.kt` | Replace boolean-only tool inputs with an explicit `toolCatalog` + `activeTools` contract while preserving memory inputs |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/ChatInferenceRequestPreparer.kt` | Build `toolCatalog`, initial `activeTools`, and disclosure-aware prompt inputs; stop treating the full eligible list as provider-callable |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/SearchToolPromptComposer.kt` | Render compact catalog + disclosure instructions + preloaded examples only |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/util/ContextWindowPlanner.kt` | Budget compact catalog tokens plus active schema tokens; stop charging deferred full schemas |
| **Modify** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/model/inference/ToolDefinitionTest.kt` | Assert compact catalog projection and disclosure metadata |
| **Create** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/chat/ToolSchemaDisclosureResolverTest.kt` | Domain tests for disclosure payloads, duplicate handling, and unknown tools |
| **Modify** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/chat/ChatInferenceRequestPreparerTest.kt` | Verify catalog/active split, preload policy, and strategy compatibility |
| **Modify** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/chat/ChatInferenceRequestPreparerXSearchTest.kt` | Verify `search_x_recent` stays catalog-only until disclosed |
| **Modify** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/chat/SearchToolPromptComposerTest.kt` | Assert compact catalog sections, disclosure contract, and absence of deferred examples |
| **Modify** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/chat/SearchToolPromptComposerXSearchTest.kt` | Assert X disclosure copy stays compact and example-free before activation |
| **Modify** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/util/ContextWindowPlannerTest.kt` | Assert deferred-schema budgeting regression is fixed |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/SearchToolPromptComposerImpl.kt` | Production-bound prompt composer must match the domain contract changes exactly |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/BaseOpenAiSdkInferenceService.kt` | Use the active-only tool list passed by the Rust loop |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/OpenAiRequestMapper.kt` | Register only `activeTools` in Chat Completions and Responses requests |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/AnthropicRequestMapper.kt` | Register only `activeTools` |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/GoogleRequestMapper.kt` | Register only `activeTools` |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/OpenRouterRequestMapper.kt` | Register only `activeTools` |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/XaiRequestMapper.kt` | Register only `activeTools`; keep xAI native tools additive but separate from client-callable tools |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceService.kt` | Serialize compact/full channels, intercept disclosure calls, emit `activatedToolNames`, and map Rust active-tool state back to Kotlin `ToolDefinition` instances |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/conversation/ConversationSignature.kt` | Base LiteRT native tool registration on `activeTools`, not the full eligible catalog |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/conversation/ConversationSignatureCache.kt` | Track active tool names for the current LiteRT session |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/conversation/LiteRtToolCallLoop.kt` | Intercept disclosure requests locally, update active names, and bypass `ToolExecutorPort` |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/conversation/LiteRtNativeToolsets.kt` | Add native disclosure tool and build toolsets from the active subset only |
| **Modify** | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceServiceTest.kt` | Verify disclosure interception, activation propagation, and fail-closed filtering |
| **Modify** | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/OpenAiRequestMapperTest.kt` | Assert only active tool names are registered |
| **Modify** | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/AnthropicRequestMapperTest.kt` | Assert only active tool names are registered |
| **Modify** | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/GoogleRequestMapperTest.kt` | Assert only active tool names are registered |
| **Modify** | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/OpenRouterRequestMapperTest.kt` | Assert only active tool names are registered |
| **Modify** | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/XaiRequestMapperTest.kt` | Assert client-callable tools remain active-only even when xAI native tools are present |
| **Modify** | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/SearchToolPromptComposerTest.kt` | Keep production-bound composer in sync with the new prompt contract |
| **Modify** | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/SearchToolPromptComposerXSearchTest.kt` | Verify deferred X tool copy |
| **Modify** | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/conversation/ConversationSignatureTest.kt` | Verify active-tool-based LiteRT signature changes |
| **Modify** | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/ConversationManagerImplToolsTest.kt` | Verify LiteRT disclosure activation and `ToolExecutorPort` bypass |
| **Modify** | `core/agent/src/main/kotlin/com/browntowndev/enclave/core/agent/RustAgentEngineAdapter.kt` | Bridge new session-IO fields and `activatedToolNames` across UniFFI |
| **Modify** | `core/agent/src/main/kotlin/com/browntowndev/enclave/core/agent/agent_core.kt` | Regenerated UniFFI bindings for new record fields |
| **Create** | `third_party/agent_core/src/tool_schema_activation.rs` | Rust helpers for compact catalog parsing, full-registry lookup, disclosure activation merge, and active-schema projection |
| **Modify** | `third_party/agent_core/src/lib.rs` | Export the new activation module |
| **Modify** | `third_party/agent_core/src/message.rs` | Add `activated_tool_names` to `ToolResultRecord` |
| **Modify** | `third_party/agent_core/src/host.rs` | Update trait mocks/tests for `ToolResultRecord` additions |
| **Modify** | `third_party/agent_core/src/turn_io.rs` | Keep `tools_schema_json` active-only and add compact/full session inputs where needed |
| **Modify** | `third_party/agent_core/src/engine/state.rs` | Store compact catalog, full schema registry, and mutable active-tool names in session state |
| **Modify** | `third_party/agent_core/src/tool_context.rs` | Build `advertised_tools` from compact catalog and persist the full registry behind one context reference |
| **Modify** | `third_party/agent_core/src/request_pipeline.rs` | Filter compact catalog entries by `name` rather than assuming a string array |
| **Modify** | `third_party/agent_core/src/budget.rs` | Count compact catalog plus active schemas in pressure calculations |
| **Modify** | `third_party/agent_core/src/compact/mod.rs` | Carry compact-catalog token payload through compaction context |
| **Modify** | `third_party/agent_core/src/engine/mod.rs` | Enforce active-only validation, absorb `activated_tool_names` on tool submission, emit active-only `tools_schema_json`, and keep disclosure tool always callable |
| **Modify** | `third_party/agent_core/src/ffi/types.rs` | Add compact/full session-IO fields and `activated_tool_names` to FFI records |
| **Modify** | `third_party/agent_core/src/action.rs` | Update `ModelRequest` defaults for new session-IO layout |
| **Modify** | `third_party/agent_core/src/ffi/callbacks.rs` | Keep callback test stubs compiling with the new FFI records |
| **Modify** | `third_party/agent_core/tests/tool_context_test.rs` | Update existing deferral tests to assert compact catalog + full-registry split |
| **Create** | `third_party/agent_core/tests/lazy_tool_disclosure_test.rs` | Rust integration tests for activation lifecycle, fail-closed repair, and mutation defense |

---

## 3. API Contracts and Interfaces

### 3.1 Domain tool-state model

New pure-domain shape:

```text
ToolCatalogEntry
  - name: String
  - description: String
  - disclosureMode: ToolDisclosureMode   // PRELOADED or DISCLOSE_ON_DEMAND
  - requiredArguments: List<ToolArgumentHint>
  - optionalArguments: List<ToolArgumentHint>

ToolArgumentHint
  - name: String
  - type: String                         // "string", "integer", "boolean", "array", "object"
  - repeated: Boolean
```

`ToolDefinition` gains metadata:

```text
ToolDefinition
  - disclosureMode: ToolDisclosureMode = DISCLOSE_ON_DEMAND
  - supportsLazyDisclosure: Boolean = true
  - catalogEntry(): ToolCatalogEntry
```

Reserved disclosure tool:

```text
ToolDefinition.DISCLOSE_TOOL_SCHEMAS
  name = "disclose_tool_schemas"
  disclosureMode = PRELOADED
  supportsLazyDisclosure = false
```

### 3.2 `GenerationOptions` contract

Additive shape:

```text
GenerationOptions
  - toolCatalog: List<ToolCatalogEntry> = emptyList()
  - activeTools: List<ToolDefinition> = emptyList()
  - availableTools: List<ToolDefinition> = activeTools    // transitional mirror only
```

Rules:

1. `toolCatalog` contains every tool the turn is allowed to discover.
2. `activeTools` contains only tools whose full schemas are currently callable.
3. `availableTools` is populated from `activeTools` during the migration window but is no longer a semantic source of truth.
4. Production code added in this ticket must read `activeTools`, not `availableTools`.

### 3.3 Prompt-composer contract

`PromptComposerPort.compose(...)` changes from boolean-only tool toggles to explicit tool-state inputs:

```text
compose(
  baseSystemPrompt: String?,
  toolCatalog: List<ToolCatalogEntry>,
  activeTools: List<ToolDefinition>,
  currentChatId: String?,
  strategy: ToolCallStrategy,
  coreMemories: List<Memory>,
  retrievedMemories: List<Memory>,
  retrievedSearchResults: String?,
): String
```

Rendered behavior:

1. `SDK_NATIVE`
   - No local tool syntax block
   - No schema disclosure prose
   - `toolCatalog` and `activeTools` still populate `GenerationOptions` for provider-native registration
2. `JSON_XML_ENVELOPE` and `LITE_RT_NATIVE`
   - Must include `# TOOL CATALOG:` with one compact row per catalog tool
   - Must include `# SCHEMA DISCLOSURE:` with the reserved disclosure tool example
   - Must include full call examples only for `activeTools`
   - Must not include full call examples for deferred tools

### 3.4 Disclosure tool contract

Request:

```json
{
  "tool_names": ["append_blocks", "search_current_artifact"]
}
```

Domain resolver output:

```json
{
  "status": "ok",
  "activated_tools": ["append_blocks", "search_current_artifact"],
  "already_active_tools": [],
  "unknown_tools": [],
  "tools": [
    {
      "name": "append_blocks",
      "description": "Append content blocks to an existing artifact.",
      "parameters": { "type": "object", "properties": { "...": "..." } },
      "call_example": "ActionResult{\"name\":\"append_blocks\",\"arguments\":{...}}ActionResult"
    }
  ]
}
```

Error payload when zero valid deferred tools resolve:

```json
{
  "status": "error",
  "message": "No disclosable tools resolved from request.",
  "activated_tools": [],
  "already_active_tools": [],
  "unknown_tools": ["not_a_tool"]
}
```

Lifecycle:

1. The disclosure tool is always callable.
2. The host intercepts it before `ToolExecutorPort`.
3. The disclosure result is returned to the model as a normal tool result message.
4. The tool becomes active only after Rust `submit_tool_results` ingests `activated_tool_names`.
5. Provider/native registration changes starting on the next model turn, never mid-turn.

### 3.5 Agent-loop session IO contract

`AgentLoopSessionIoParams` gains:

```text
toolCatalogJson: String
toolSchemaRegistryJson: String
toolsSchemaJson: String
```

Semantics:

- `toolCatalogJson`: compact JSON array for all eligible tools
- `toolSchemaRegistryJson`: full schema registry for all eligible tools, keyed by tool name
- `toolsSchemaJson`: active-only schema array for the current session state

Rust session state owns the mutable active set. Kotlin sends the initial active-only `toolsSchemaJson`; Rust updates it after disclosure activations using `toolSchemaRegistryJson`.

### 3.6 `AgentToolResult` / Rust `ToolResultRecord`

Additive field:

```text
activatedToolNames: List<String> = emptyList()
```

Rules:

1. Only disclosure results populate `activatedToolNames`.
2. Real business tools must leave it empty.
3. Rust merges `activatedToolNames` into the active set in first-seen order with dedupe.
4. Unknown names in `activatedToolNames` are ignored by Rust and must not crash the session.

### 3.7 Rust validation rule

Tool-call validation and repair in `agent_core` must treat the callable set as:

```text
callable_names = active_tool_names ∪ {"disclose_tool_schemas"}
```

Catalog-only deferred tools are discoverable in prompt text and compact catalog metadata, but are not repairable or executable until disclosure activates them.

### 3.8 Compatibility by strategy/path

| Path | Before disclosure | After disclosure |
|------|-------------------|------------------|
| `SDK_NATIVE` | Provider request registers only preloaded tools | Next provider request registers preloaded + activated tools |
| `JSON_XML_ENVELOPE` | Prompt shows compact catalog and preloaded examples only | Disclosure result gives the full schema + canonical envelope example; next turn may call the activated tool |
| `LITE_RT_NATIVE` | LiteRT native toolset list contains only preloaded tools | Conversation cache updates active names and rebuilds the native toolset list |
| Non-Rust direct mappers | Must read `activeTools` instead of full eligible tools | Same behavior; disclosure state comes from the caller-supplied `GenerationOptions.activeTools` |

---

## 4. Data Model Changes

| Location | Change |
|----------|--------|
| `GenerationOptions` | Adds `toolCatalog` and `activeTools` |
| `ToolDefinition` | Adds disclosure policy and compact catalog projection |
| `AgentLoopSessionIoParams` | Adds `toolCatalogJson` and `toolSchemaRegistryJson` |
| `AgentToolResult` / `ToolResultRecord` | Adds `activatedToolNames` / `activated_tool_names` |
| Rust session state | Adds mutable `active_tool_names` plus compact/full tool stores |
| Prompt contract | Moves from boolean toggles to explicit catalog + active-tool inputs |

No Room, Firestore, or Android framework model changes are permitted.

---

## 5. Ordered Implementation Tasks

| ID | Task | Verifies |
|----|------|----------|
| **T-1** | Add `ToolCatalogModels.kt`, extend `ToolDefinition`, and expose compact argument-hint extraction in `ToolParameters.kt` | `LTD-01`, `LTD-02`, `MUT-02` |
| **T-2** | Extend `GenerationOptions` and `AgentLoopModels` with catalog/active/session-IO fields and `activatedToolNames` | `LTD-01`, `LTD-08`, `LTD-09` |
| **T-3** | Update `ChatInferenceRequestPreparer` to build `toolCatalog`, initial `activeTools`, and the preload policy table | `LTD-01`, `LTD-03`, `LTD-13` |
| **T-4** | Implement `ToolSchemaDisclosureResolver` and wire exact success/error payload contracts | `LTD-05`, `LTD-06`, `MUT-04`, `MUT-05` |
| **T-5** | Refactor both prompt-composer implementations and `PromptComposerPort` to render compact catalog + disclosure instructions + active-only examples | `LTD-02`, `LTD-03`, `MUT-02` |
| **T-6** | Update `ContextWindowPlanner` to budget compact catalog plus active schemas only | `LTD-07`, `MUT-03` |
| **T-7** | Switch all provider request mappers and `BaseOpenAiSdkInferenceService` to `activeTools` | `LTD-04`, `LTD-14`, `MUT-01` |
| **T-8** | Update LiteRT local path (`ConversationSignature`, cache, loop, native toolsets`) to intercept disclosure and rebuild active toolsets without using `ToolExecutorPort` | `LTD-10`, `LTD-11`, `LTD-12` |
| **T-9** | Update `RustLoopInferenceService` to serialize compact/full channels, intercept disclosure, emit `activatedToolNames`, and fail closed when Rust asks for an unknown active tool | `LTD-05`, `LTD-08`, `LTD-09`, `LTD-11` |
| **T-10** | Extend `RustAgentEngineAdapter`, UniFFI bindings, Rust FFI records, and Rust message/session models for the new fields | `LTD-08`, `LTD-09` |
| **T-11** | Update `agent_core` request pipeline, tool context, budget accounting, and engine activation logic to keep the callable set active-only and the catalog compact | `LTD-08`, `LTD-09`, `LTD-10`, `MUT-01`, `MUT-03` |
| **T-12** | Add and update Kotlin + Rust tests, regenerate bindings, and run the affected Gradle/Cargo validation commands | All scenarios |

---

## 6. Scenario ID Map

| Scenario IDs | Primary Tasks |
|--------------|---------------|
| `LTD-01` to `LTD-03` | `T-1`, `T-3`, `T-5` |
| `LTD-04` | `T-7` |
| `LTD-05` to `LTD-06` | `T-4`, `T-9`, `T-11` |
| `LTD-07` | `T-6` |
| `LTD-08` to `LTD-09` | `T-2`, `T-9`, `T-10`, `T-11` |
| `LTD-10` to `LTD-12` | `T-8` |
| `LTD-13` to `LTD-14` | `T-3`, `T-7` |
| `MUT-01` to `MUT-05` | `T-4`, `T-5`, `T-6`, `T-8`, `T-9`, `T-11` |

---

## 7. Validation Commands

### Kotlin / Gradle

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

./gradlew :core:domain:assembleDebug
./gradlew :feature:inference:assembleDebug
./gradlew :core:agent:assembleDebug
./gradlew detekt
```

### Rust / bindings

```bash
cargo test --manifest-path third_party/agent_core/Cargo.toml --test tool_context_test
cargo test --manifest-path third_party/agent_core/Cargo.toml --test lazy_tool_disclosure_test
cargo test --manifest-path third_party/agent_core/Cargo.toml

./scripts/build-agent-bindings.sh
```

### CI-aligned completion gate

```bash
./gradlew ktlintCheck
./gradlew detekt
./gradlew testDebugUnitTest
./gradlew :app:assembleDebug
cargo test --manifest-path third_party/agent_core/Cargo.toml
```

---

## 8. Drift and Escalation Rules

- `disclose_tool_schemas` is a reserved control tool. It must never be delegated to `ToolExecutorPort`. If an implementation path cannot intercept it before `ToolExecutorPort`, stop and escalate.
- `availableTools` is transitional only. Any new production read from `availableTools` after this ticket is a drift bug.
- `toolsSchemaJson` becomes active-only by contract. Reusing it as the full eligible catalog is a regression.
- The compact catalog must stay compact: top-level argument hints only, no nested `properties`, `definitions`, `oneOf`, or full examples for deferred tools.
- The Rust engine is the source of truth for active-tool state during agent-loop sessions. Kotlin must not maintain an independent authoritative activation set for Rust-backed turns.
- LiteRT local disclosure may maintain local active state because it does not round-trip through the Rust session; that state must still use the same `ToolSchemaDisclosureResolver` payload and dedupe rules.
- If FFI field changes require checked-in binding regeneration, use the repo script rather than manual edits to generated bindings.
- When implementation begins, every Kotlin file edit must follow the AndroJack contract in `AGENTS.md` before/after code generation.
- If a later phase needs to change preload policy beyond the table in §1, treat that as a spec change and update both this file and the Gherkin scenarios first.


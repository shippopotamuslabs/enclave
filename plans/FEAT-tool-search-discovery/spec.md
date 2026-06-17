# Spec: Tool Search Discovery

**Ticket ID:** `FEAT-tool-search-discovery`  
**Date:** `2026-06-08`  
**Contracts read:** `AGENTS.md`, `contracts/ARCHITECTURE_RULES.md`, `contracts/CODE_STYLE_RULES.md`, `contracts/INFERENCE_RULES.md`, `contracts/UNIT_TESTING.md`  
**Supersedes (partial):** `plans/FEAT-lazy-tool-disclosure/spec.md` — discovery mechanism only; catalog/active split and `activatedToolNames` plumbing remain.

---

## 0. Design Invariant — Single Schema Source (authoritative)

**Product intent:** The model sees **lightweight tool metadata** (names, short descriptions, discovery hints) until it deliberately activates a tool. **Full JSON schemas appear exactly once** — at activation — never duplicated across provider wire, system prompt, sidecars, and tool results in the same turn.

| Layer | What the model sees | Full `parameters` JSON? |
|-------|---------------------|-------------------------|
| **Advertisement** | Callable preload names + how to discover more (`search_tools`) | **No** |
| **Discovery result** | Ranked matches + full schemas for **newly activated** tools only | **Yes** (once, in tool-result message) |
| **API provider `tools[]`** | Full schemas for **session wire registry** only (preload ∪ ever-activated this session) | **Yes** (callable subset only; grows append-only) |
| **Local prompt** | `# TOOL DISCOVERY:` prose + active-tool call examples | **No** catalog block, no deferred schemas |
| **API system prompt** | Memories, psych, config — no tool-syntax blocks (`SDK_NATIVE`) | **No** |
| **Sidecars** | `advertised_tools` **names** only (compacted) | **No** |

**Unified lifecycle (local and API):**

1. Turn starts with **preload** tools fully callable (`search_tools`, `web_search`, … per policy).
2. Model runs `search_tools(query)` when it needs a deferred capability.
3. Host returns **full schemas in the tool result** and merges names into the **session active set**.
4. **API:** next provider request adds newly activated tools to native `tools[]` (append-only; never remove mid-session).
5. **Local:** newly activated tools join the native/text toolset; schemas already live in transcript from step 3 — do not re-inject into system prompt.

**Forbidden (regression guards):**

- `eligibleToolPool` on provider wire (all catalog tools with full schemas from turn 1).
- Full schemas in `# TOOL CATALOG:` **and** disclose/search result **and** provider `tools[]` for the same tool on the same turn.
- `toolSchemaRegistryJson` content mirrored onto provider wire (Rust-internal registry only).

### Production bug (pre-fix drift)

`FEAT-lazy-tool-disclosure` specified **active-only** provider registration. Production `RustLoopInferenceService.wireToolsForRequest` overrides with `eligibleToolPool(options)` — every catalog tool's full schema on **every** API request — while `disclose_tool_schemas` **also** injects full schemas into tool-result history. Net effect: deferred tools pay **2×–3×** schema tokens with **zero** deferral benefit on API. This ticket **must** revert that override and enforce §0.

---

## 1. Feature Summary

### Problem

Enclave's lazy disclosure flow (`disclose_tool_schemas` + compact `# TOOL CATALOG:` in local system prompts) still requires the model to know exact deferred tool names before activation. Production API wiring **contradicts** the lazy-disclosure spec: `wireToolsForRequest` → `eligibleToolPool` registers the **full eligible catalog** on every provider request (prompt-cache misread of `plans/prompt-caching/spec.md`), while disclose **also** stuffs schemas into tool results. Local prompts still carry a compact catalog block that duplicates advertisement. Neither path satisfies §0.

### Target

Replace name-based disclosure with **unified tool discovery via `search_tools`**:

1. **BM25 first** over a searchable document per catalog tool (name, description, argument-hint names, category tags — not full JSON schemas).
2. **MiniLM embedding fallback** when BM25 returns zero hits **or** top BM25 score `< τ_bm25`; compare query embedding to **precomputed per-tool vectors** (cosine on L2-normalized 384-d vectors); activate only when top score `>= τ_embed`.
3. **`search_tools` result** returns compact ranked matches plus full schemas for newly activated tools, using the existing `activatedToolNames` / Rust session merge / LiteRT cache path.
4. **Local prompts:** remove `# TOOL CATALOG:`; add a short `# TOOL DISCOVERY:` instruction block referencing `search_tools`.
5. **API prompts:** `SDK_NATIVE` continues to skip local tool-syntax blocks; provider wire registers **session wire registry** = preload ∪ ever-activated (append-only; §3.7), never full catalog.
6. **Intent-gating fast-path** unchanged: artifact trio and `search_x_recent` may still be pre-activated when `ToolIntentActivationPolicy` fires.

### Locked Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Discovery tool | Reserved `search_tools` replaces `disclose_tool_schemas` | Natural-language queries; Pi/ForgeCode pattern; provider-agnostic |
| Essential preload | `search_tools`; `web_search` + `web_extract` when web configured; `attached_image_inspect` when image tool-eligible | Keeps high-frequency tools immediately callable |
| Not preloaded by default | `read_context_reference`, memory tools, artifacts, X extras, media tools | Discovered via `search_tools` or intent-gating |
| Search index fields | `name`, `description`, required/optional arg hint names, `categoryTags` | BM25 over text only; no nested schema JSON |
| Fallback trigger | BM25 result list empty **OR** `topBm25Score < τ_bm25` | Hybrid without always paying embed latency |
| Embed gate | `topCosineScore >= τ_embed` required to activate via fallback | Prevents weak semantic matches |
| Tool vectors | Precomputed once per turn when `toolCatalog` is built; query embedded only on fallback | Matches MiniLM usage in `ChatInferenceRequestPreparer` |
| Activation path | Reuse `AgentToolResult.activatedToolNames` and `ToolSchemaDisclosureResolver`-style schema payloads for activated tools | No new session-state channel |
| API wire | Session **wire registry**: preload ∪ session-activated tools; append-only; `search_tools` always present | Fixes full-pool dump; compatible with prompt-cache §3.7 |
| Local prompt | No `# TOOL CATALOG:`; `# TOOL DISCOVERY:` + active-tool examples only | Token savings; model discovers by query |
| Provider scope | Same hybrid search on local and API paths | Unlike Anthropic provider-native tool search |
| `disclose_tool_schemas` | Removed from catalog, preload, prompts, LiteRT toolsets, Rust semantics | Hard replace; no dual mechanism |

### Constants (locked)

| Symbol | Value | Meaning |
|--------|-------|---------|
| `τ_bm25` | `1.5` | Below this top BM25 score → run embedding fallback |
| `τ_embed` | `0.55` | Minimum cosine similarity to activate via fallback |
| `DEFAULT_SEARCH_LIMIT` | `5` | Default `limit` when omitted |
| `MAX_SEARCH_LIMIT` | `10` | Hard cap on returned/activated matches |
| `BM25_K1` | `1.2` | BM25 term saturation |
| `BM25_B` | `0.75` | BM25 length normalization |
| `EMBEDDING_DIMENSION` | `384` | MiniLM output (`MiniLMEmbeddingEngine`) |

### Non-goals

- Room / persistence schema changes for tool search state
- Android/framework types in `:core:domain`
- Provider-native tool search (Anthropic/OpenAI built-in discovery)
- Gradle-time embedding of static tool lists (embeddings are per-turn catalog snapshots to include dynamic media tools)
- Changing intent-gating policies beyond preload name updates

---

## 2. File Structure Table

| Action | Path | Responsibility |
|--------|------|----------------|
| **Create** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/inference/ToolSearchModels.kt` | `ToolSearchDocument`, `ToolSearchMatch`, `ToolSearchResult`, `ToolMatchMethod`, thresholds object |
| **Create** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/search/ToolBm25Index.kt` | Pure Kotlin BM25 index over tokenized `ToolSearchDocument` |
| **Create** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/search/ToolCosineSimilarity.kt` | Dot-product cosine for L2-normalized `FloatArray` (384-d) |
| **Create** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/ToolSearchCatalogBuilder.kt` | Build `ToolSearchDocument` list + `Map<String, FloatArray>` embeddings from `toolCatalog` via `EmbeddingEnginePort` |
| **Create** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/ToolSearchResolver.kt` | Hybrid BM25→embed search, activation classification, JSON payload + `activatedToolNames` |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/inference/ToolCatalogModels.kt` | Add `categoryTags: List<String>`; helper `toSearchDocument()` |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/inference/ToolDefinition.kt` | Add `SEARCH_TOOLS`; `categoryTags` per static tool; remove `DISCLOSE_TOOL_SCHEMAS` from `ALL_TOOLS` and preload hooks |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/inference/ToolParameters.kt` | Add `SearchToolsParams`; remove `DiscloseToolSchemasParams` |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/inference/GenerationOptions.kt` | Add `toolSearchIndex: ToolSearchIndexSnapshot?` (documents + embeddings + BM25 index) |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/inference/AgentLoopModels.kt` | Replace `AgentLoopToolDisclosureSemantics` with `AgentLoopToolDiscoverySemantics` (`toolName = search_tools`, `requestFieldName = query`) |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/ChatInferenceRequestPreparer.kt` | Update `PRELOADED_TOOL_NAMES`; build `toolSearchIndex` after `toolCatalog`; drop `DISCLOSE_TOOL_SCHEMAS` from eligible list |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/SearchToolPromptComposer.kt` | Replace catalog/disclosure blocks with `# TOOL DISCOVERY:`; keep active examples + strict rules |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/util/ContextWindowPlanner.kt` | Budget discovery instruction tokens + active schemas only (no compact-catalog charge) |
| **Delete** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/ToolSchemaDisclosureResolver.kt` | Superseded by `ToolSearchResolver` (relocate shared JSON helpers into resolver or small internal util) |
| **Create** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/search/ToolBm25IndexTest.kt` | BM25 ranking unit tests |
| **Create** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/chat/ToolSearchResolverTest.kt` | Hybrid search, activation, error paths |
| **Create** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/chat/ToolSearchCatalogBuilderTest.kt` | Document text + embedding map shape |
| **Modify** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/chat/ChatInferenceRequestPreparerTest.kt` | Preload policy, index attachment, no disclose |
| **Modify** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/chat/ChatInferenceRequestPreparerIntentGatingTest.kt` | Baseline preload names |
| **Modify** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/chat/SearchToolPromptComposerTest.kt` | Discovery block; no catalog header |
| **Modify** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/util/ContextWindowPlannerTest.kt` | No catalog token charge |
| **Modify** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/model/inference/ToolDefinitionTest.kt` | `search_tools` metadata; disclose removed |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceService.kt` | Intercept `search_tools`; `wireToolsForRequest` → active-only; remove disclosure handler |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/conversation/LiteRtToolCallLoop.kt` | Intercept `search_tools`; `handleToolSearch(query)` replaces `handleDisclosure` |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/conversation/LiteRtNativeToolsets.kt` | `LocalSearchToolsToolset` replaces `LocalDiscloseToolset` |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/OpenAiRequestMapper.kt` | Expect `search_tools` in active set (tests only if logic unchanged) |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/AnthropicRequestMapper.kt` | Same |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/GoogleRequestMapper.kt` | Same |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/OpenRouterRequestMapper.kt` | Same |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/XaiRequestMapper.kt` | Same |
| **Modify** | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceServiceTest.kt` | `search_tools` scenarios |
| **Modify** | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/ConversationManagerImplToolsTest.kt` | LiteRT search activation |
| **Modify** | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/*RequestMapperTest.kt` | Active-only wire + `search_tools` preload |
| **Modify** | `core/agent/src/test/kotlin/com/browntowndev/enclave/core/agent/AgentLoopToolSemanticsFfiMapperTest.kt` | Discovery semantics JSON |
| **Modify** | `third_party/agent_core/src/tool_semantics.rs` | `ToolDiscoverySemantics` (or rename disclosure struct fields) |
| **Modify** | `third_party/agent_core/resources/tool_catalog.json` | Replace `disclose_tool_schemas` spec with `search_tools` |
| **Modify** | `third_party/agent_core/src/catalog.rs` | Test fixture expectations |
| **Modify** | `third_party/agent_core/src/engine/mod.rs` | Callable set = active ∪ `{search_tools}`; merge `activated_tool_names` from search results |
| **Modify** | `third_party/agent_core/tests/tool_search_discovery_test.rs` | **Create** — Rust activation + callable-set tests |
| **Modify** | `core/agent/.../agent_core.kt` | Regenerated UniFFI bindings if FFI records change |

---

## 3. API Contracts and Interfaces

### 3.1 Category tags

Each static `ToolDefinition` and dynamic media tool factory assigns `categoryTags` used only for search indexing:

| Tag | Example tools |
|-----|----------------|
| `discovery` | `search_tools` |
| `web` | `web_search`, `web_extract` |
| `memory` | `search_chat_history`, `search_chat`, `get_message_context`, `manage_memories` |
| `artifact` | `start_artifact`, `append_blocks`, `search_current_artifact` |
| `x` | `search_x_recent`, `x_get_thread`, `x_trending`, `x_user_tweets`, `x_mention_search` |
| `media` | `generate_image`, `generate_video`, `generate_music` |
| `vision` | `attached_image_inspect` |
| `context` | `read_context_reference` |

`ToolCatalogEntry` additive field:

```text
ToolCatalogEntry
  - categoryTags: List<String> = emptyList()
```

Search document text (deterministic):

```text
ToolSearchDocument
  - toolName: String
  - searchableText: String   // lowercase join of name, description, arg names, tags (space-separated)
```

### 3.2 `search_tools` tool definition

```text
ToolDefinition.SEARCH_TOOLS
  name = "search_tools"
  description = "Search available tools by natural-language query and activate matching tools for this conversation."
  disclosureMode = PRELOADED
  supportsLazyDisclosure = false
  categoryTags = ["discovery"]
```

Request parameters (`SearchToolsParams`):

```json
{
  "query": "append content to my document",
  "limit": 5
}
```

Rules:

- `query` trimmed; empty → error payload (§3.4).
- `limit` optional; default `5`; clamped to `MAX_SEARCH_LIMIT` (`10`).

### 3.3 `ToolSearchIndexSnapshot`

Attached to `GenerationOptions` for the turn (built in preparer):

```text
ToolSearchIndexSnapshot
  - documents: List<ToolSearchDocument>
  - bm25: ToolBm25Index
  - embeddingsByToolName: Map<String, FloatArray>   // 384-d, L2-normalized
```

Built by:

```text
ToolSearchCatalogBuilder.build(
  catalog: List<ToolCatalogEntry>,
  embeddingEngine: EmbeddingEnginePort,
): ToolSearchIndexSnapshot
```

Embedding text per tool = `ToolSearchDocument.searchableText`. Query embedding computed only inside `ToolSearchResolver` on fallback.

### 3.4 `ToolSearchResolver` contract

```text
ToolSearchResolver.resolve(
  query: String,
  limit: Int,
  toolCatalog: List<ToolCatalogEntry>,
  activeTools: List<ToolDefinition>,
  searchIndex: ToolSearchIndexSnapshot,
  embeddingEngine: EmbeddingEnginePort,
  strategy: ToolCallStrategy,
): ToolSearchResult

ToolSearchResult
  - resultJson: String
  - activatedToolNames: List<String>
```

Search algorithm:

1. Tokenize `query` (same tokenizer rules as BM25 index: lowercase, split on non-alphanumeric).
2. Run BM25 over all catalog tools not filtered out (catalog is full eligible set).
3. Take top `limit` BM25 hits with score `> 0`.
4. If list empty **or** top score `< τ_bm25` (`1.5`):
   - `embeddingEngine.getEmbedding(query.trim())`
   - Cosine vs each `embeddingsByToolName` entry
   - Take top `limit` by cosine
   - Keep only tools with cosine `>= τ_embed` (`0.55`); mark `match_method = "embedding"`.
5. Classify each matched name:
   - already in `activeTools` → `already_active_tools`
   - in catalog but not active → `activated_tools` (and include full schema in `tools` array)
6. `activatedToolNames` = `activated_tools` in first-seen order, deduped.

Success payload shape:

```json
{
  "status": "ok",
  "query": "append content to my document",
  "matches": [
    {
      "name": "append_blocks",
      "description": "Add content to the in-progress PDF/Word document...",
      "score": 4.82,
      "match_method": "bm25"
    }
  ],
  "activated_tools": ["append_blocks"],
  "already_active_tools": [],
  "tools": [
    {
      "name": "append_blocks",
      "description": "...",
      "parameters": { "type": "object", "properties": { } },
      "call_example": "..."
    }
  ]
}
```

No-match success:

```json
{
  "status": "ok",
  "query": "quantum flute polishing",
  "matches": [],
  "activated_tools": [],
  "already_active_tools": [],
  "tools": []
}
```

Error — empty query:

```json
{
  "status": "error",
  "message": "search_tools requires a non-empty query.",
  "query": "",
  "matches": [],
  "activated_tools": [],
  "already_active_tools": [],
  "tools": []
}
```

### 3.5 Preload policy (`ChatInferenceRequestPreparer`)

```text
PRELOADED_TOOL_NAMES =
  search_tools
  + web_search, web_extract     // when webSearchConfigured
  + attached_image_inspect      // when imageHandling == TOOL
  + intentActivatedNames          // artifact trio, search_x_recent per ToolIntentActivationPolicy
```

`eligibleTools` list **must not** include `disclose_tool_schemas`.

### 3.6 Prompt contract (`SearchToolPromptComposer`)

For `JSON_XML_ENVELOPE` and `LITE_RT_NATIVE`:

1. Include `# TOOL DISCOVERY:` with prose: call `search_tools` with a natural-language `query` before using tools not already active; do not guess tool names.
2. Include `# ACTIVE TOOL EXAMPLES:` for `activeTools` excluding `search_tools`.
3. **Must not** include `# TOOL CATALOG:` or `# SCHEMA DISCLOSURE:`.
4. Memory-tool deferred note becomes discovery-oriented (reference `search_tools` with example queries).

For `SDK_NATIVE`: return empty string from tool-contract section (unchanged).

Literal discovery header string (stable for tests):

```text
# TOOL DISCOVERY:
```

### 3.7 Provider wire (`wireToolsForRequest`)

**Session wire registry** (per `sendPrompt` / agent-loop session, held in `RustLoopInferenceService` or `ProviderAppendOnlyContext` stable-prefix state):

```text
wireRegistry: MutableSet<String>   // tool names, append-only for session lifetime

on sendPrompt start:
  wireRegistry = PRELOADED_TOOL_NAMES for this turn (incl. search_tools)

on search_tools result with activatedToolNames:
  wireRegistry += activatedToolNames   // never remove

wireToolsForRequest(options, toolContextIndexJson, wireRegistry): List<ToolDefinition>
  if toolContextIndexJson signals tool suppression → emptyList()
  else → map wireRegistry names → ToolDefinition (catalog lookup); preserve stable insertion order
```

Rules:

- **Never** call `eligibleToolPool` for provider wire.
- `eligibleToolPool` remains for Rust name → `ToolDefinition` mapping only.
- Within a turn, `allowToolCall = false` still sends the **same** `tools[]` array (prompt-cache spec); only `toolChoice` changes.
- Cross-turn: `tools[]` grows only when a tool is first activated; unchanged turns keep identical tool prefix → cache-safe.
- `toolsFingerprint` in append-only stable prefix increments **only** when `wireRegistry` gains a new name (not on every turn).

Remove the production comment that justifies full-pool wire for "prefix stability" — that behavior is the bug §0 documents.

### 3.8 Host interception lifecycle

| Path | Behavior |
|------|----------|
| Rust `RustLoopInferenceService.executeOneTool` | If `call.name == search_tools` → `handleToolSearchCall`; never `ToolExecutorPort` |
| LiteRT `LiteRtToolCallLoop` | Same; update `ConversationSignatureCache.activeToolNames` on activation |
| LiteRT native | `LocalSearchToolsToolset.search_tools(query, limit?)` → `handleToolSearch` |
| Business tools | Unchanged via `ToolExecutorPort` |

`activatedToolNames` on `AgentToolResult` populated only for `search_tools` results (same rule as prior disclosure).

### 3.9 Rust agent-loop semantics

`AgentLoopAgentConfig.toolSemanticsJson` default (`enclaveDefault`) discovery block:

```json
{
  "discovery": {
    "tool_name": "search_tools",
    "request_field_name": "query",
    "always_callable": true
  }
}
```

Callable validation:

```text
callable_names = active_tool_names ∪ {"search_tools"}
```

Remove `disclosure` key from default Enclave semantics JSON. Engine merges `activated_tool_names` from tool results identically to lazy disclosure.

### 3.10 `ContextWindowPlanner` budgeting

- Remove compact-catalog token estimation block.
- Add fixed discovery-instruction token estimate: count tokens of canonical discovery prose constant `TOOL_DISCOVERY_INSTRUCTION_TEXT` (single shared string in `SearchToolPromptComposer` companion).
- `toolSchemaTokens = discoveryInstructionTokens + activeSchemaTokens`.

---

## 4. Data Model Changes

| Location | Change |
|----------|--------|
| `ToolCatalogEntry` | Adds `categoryTags` |
| `ToolDefinition` | Adds `SEARCH_TOOLS`; removes `DISCLOSE_TOOL_SCHEMAS`; per-tool `categoryTags` |
| `GenerationOptions` | Adds `toolSearchIndex: ToolSearchIndexSnapshot?` |
| `SearchToolsParams` | New; replaces `DiscloseToolSchemasParams` |
| `AgentLoopToolDiscoverySemantics` | Replaces disclosure semantics in Kotlin + Rust |
| Prompt contract | `# TOOL DISCOVERY:` replaces catalog + disclosure blocks |
| Provider wire | Session wire registry (preload ∪ activated; append-only) |

No Room, Firestore, or Android persistence changes.

---

## 5. Ordered Implementation Tasks

| ID | Task | Verifies |
|----|------|----------|
| **T-1** | Add `ToolSearchModels.kt`, `categoryTags` on `ToolCatalogEntry`, `SEARCH_TOOLS` + tag map on `ToolDefinition`, `SearchToolsParams`, remove disclose tool/constants | `TSD-01`, `TSD-20`, `MUT-03` |
| **T-2** | Implement `ToolBm25Index.kt` + `ToolCosineSimilarity.kt` with locked hyperparameters | `TSD-04`, `TSD-05`, `TSD-06`, `TSD-08`, `MUT-04` |
| **T-3** | Implement `ToolSearchCatalogBuilder` + `ToolSearchIndexSnapshot` on `GenerationOptions`; wire in `ChatInferenceRequestPreparer` | `TSD-01`, `TSD-16`, `TSD-21`, `TSD-22` |
| **T-4** | Implement `ToolSearchResolver` (hybrid search, JSON payloads, activation lists); delete `ToolSchemaDisclosureResolver` | `TSD-07`, `TSD-09`, `TSD-10`, `TSD-15`, `TSD-17`, `TSD-18`, `TSD-19`, `TSD-24`, `TSD-25`, `MUT-05` |
| **T-5** | Update `SearchToolPromptComposer` discovery block; remove catalog/disclosure; export `TOOL_DISCOVERY_INSTRUCTION_TEXT` | `TSD-02`, `TSD-03`, `MUT-01`, `MUT-05` |
| **T-6** | Update `ContextWindowPlanner` budgeting | `TSD-23` |
| **T-7** | Update `RustLoopInferenceService` (`handleToolSearchCall`, session wire registry in `wireToolsForRequest`) | `TSD-11`, `TSD-13`, `TSD-27`, `MUT-02` |
| **T-8** | Update LiteRT path (`LiteRtToolCallLoop`, `LiteRtNativeToolsets`, cache) | `TSD-12`, `TSD-14` |
| **T-9** | Update Rust `tool_semantics`, `tool_catalog.json`, engine callable-set + activation merge; regenerate UniFFI if needed | `TSD-11`, `TSD-26` |
| **T-10** | Update provider mapper tests and `AgentLoopModels.enclaveDefault` discovery semantics | `TSD-13`, `TSD-03` |
| **T-11** | Migrate/delete disclose tests; add new domain + inference + Rust tests per `test_spec.md` | All scenarios |
| **T-12** | Run CI-aligned validation commands (Gradle, detekt, ktlint, Cargo, bindings script) | All scenarios |

---

## 6. Scenario ID Map

| Scenario IDs | Primary Tasks |
|--------------|---------------|
| `TSD-01` to `TSD-03` | `T-1`, `T-3`, `T-5` |
| `TSD-04` to `TSD-08` | `T-2`, `T-4` |
| `TSD-09`, `TSD-10`, `TSD-15`, `TSD-17` to `TSD-19`, `TSD-24`, `TSD-25` | `T-4` |
| `TSD-11`, `TSD-13`, `TSD-27`, `TSD-26` | `T-7`, `T-9`, `T-10` |
| `TSD-12`, `TSD-14` | `T-8` |
| `TSD-16`, `TSD-20` to `TSD-22` | `T-1`, `T-3` |
| `TSD-23` | `T-6` |
| `MUT-01` to `MUT-05` | `T-5`, `T-7`, `T-4`, `T-2` |

---

## 7. Validation Commands

### Kotlin / Gradle

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
```

### Rust / bindings

```bash
cargo test --manifest-path third_party/agent_core/Cargo.toml --test tool_search_discovery_test
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

- `search_tools` is a reserved control tool. It must never reach `ToolExecutorPort`. If any path cannot intercept it, stop and escalate.
- `disclose_tool_schemas` must not remain in production catalogs, preloads, prompts, or Rust default semantics after this ticket. References in tests/docs outside this feature are updated in `T-11`.
- Do not reintroduce `# TOOL CATALOG:` in local prompts. Deferred tools are discoverable only via `search_tools` or intent-gating.
- `wireToolsForRequest` **must** use session wire registry (§3.7). Reverting to `eligibleToolPool` is forbidden without Contract Waiver.
- §0 single-schema-source invariant is non-negotiable: no duplicate full schemas across wire + tool result + system prompt for the same activation.
- BM25 and embedding thresholds (`τ_bm25`, `τ_embed`) are locked in `ToolSearchModels.kt`. Changing them requires updating both spec artifacts and Gherkin scenarios.
- Tool embeddings are per-turn snapshots. Do not persist tool vectors in Room.
- Rust remains authoritative for active-tool state during agent-loop sessions; Kotlin must emit `activatedToolNames` on search results and must not maintain a divergent authoritative set.
- LiteRT local path may hold cache-local active names; must use the same `ToolSearchResolver` and dedupe rules.
- Query embeddings run only on fallback path. Calling `getEmbedding` on every search when BM25 suffices is a performance regression.
- FFI/binding changes must use `./scripts/build-agent-bindings.sh`, not hand-edited generated Kotlin.
- Kotlin edits follow AndroJack pre/post validation per `AGENTS.md`.

---

## 9. Human Approval Items

| Item | Default in spec | Needs approval if different |
|------|-----------------|------------------------------|
| API cache tradeoff | Append-only wire registry; prefix grows only on first activation per tool per session | Revert to full-pool wire (rejected — violates §0) |
| `read_context_reference` preload | Deferred (searchable) | Keep always preloaded |
| `τ_bm25` / `τ_embed` | `1.5` / `0.55` | Tune after offline eval |
| Remove vs alias `disclose_tool_schemas` | Hard remove | Temporary alias period |

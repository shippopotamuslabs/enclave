# AGENTCORE-DECOUPLE-TOOLS Spec

## Feature summary

Decouple `third_party/agent_core` from Enclave/Pocket Crew tool names and field names by introducing a host-provided tool semantics registry. The Rust core remains a self-contained agent-loop SDK: model orchestration, tool execution scheduling, guardrails, compaction, repair, and FFI stay in `agent_core`, but behavior that currently assumes `start_artifact`, `append_blocks`, `search_current_artifact`, `disclose_tool_schemas`, `artifactId`, `isComplete`, and `tool_names` becomes configurable.

The default Rust configuration must be agnostic. Without host-supplied semantics, the engine must not auto-finalize artifacts, must not grant greedy turn expansion to a hard-coded tool, must not skip truncation/spill mutation for a hard-coded tool, must not treat a hard-coded disclosure tool as always callable, and must not squash artifact history by recognizing Enclave names.

Enclave preserves existing behavior by passing an Enclave semantics configuration from the Android/domain layer into UniFFI session creation.

Contracts verified before planning:

- `contracts/ARCHITECTURE_RULES.md`
- `contracts/CODE_STYLE_RULES.md`
- `contracts/UNIT_TESTING.md`

Existing files verified:

- `third_party/agent_core/src/catalog.rs`
- `third_party/agent_core/resources/tool_catalog.json`
- `third_party/agent_core/src/artifact_finalize.rs`
- `third_party/agent_core/src/engine/mod.rs`
- `third_party/agent_core/src/engine/state.rs`
- `third_party/agent_core/src/canonicalize/mod.rs`
- `third_party/agent_core/src/compact/artifact_squash.rs`
- `third_party/agent_core/src/compact/micro.rs`
- `third_party/agent_core/src/guardrails.rs`
- `third_party/agent_core/src/session.rs`
- `third_party/agent_core/src/ffi/types.rs`
- `third_party/agent_core/src/ffi/policy_bridge.rs`
- `third_party/agent_core/src/tool_parse/mod.rs`
- `third_party/agent_core/src/repair/mod.rs`
- `third_party/agent_core/src/lib.rs`
- `core/agent/src/main/kotlin/com/browntowndev/enclave/core/agent/RustAgentEngineAdapter.kt`
- `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/inference/AgentLoopModels.kt`
- `third_party/agent_core/tests/lazy_tool_disclosure_test.rs`
- `third_party/agent_core/tests/tool_call_canonicalize_test.rs`
- `third_party/agent_core/tests/fixtures.rs`
- `third_party/agent_core/Cargo.toml`

## File structure table

| Action | File | Responsibility |
|---|---|---|
| Create | `third_party/agent_core/src/tool_semantics.rs` | Own SDK-level configurable tool behavior models, validation, default agnostic semantics, and Enclave compatibility semantics. |
| Modify | `third_party/agent_core/src/lib.rs` | Export `tool_semantics` and any UniFFI-facing types required by bindings. |
| Modify | `third_party/agent_core/src/session.rs` | Add `tool_semantics: ToolSemanticsConfig` to `AgentConfig`, defaulting to agnostic semantics. Rename or deprecate hard-coded `greedy_append_blocks` behavior through semantics-driven policy while keeping FFI/domain compatibility for migration. |
| Modify | `third_party/agent_core/src/catalog.rs` | Allow catalog construction from host JSON and semantics hints, while preserving `ToolCatalog::global()` only for Enclave compatibility and legacy tests. |
| Modify | `third_party/agent_core/resources/tool_catalog.json` | Keep the Enclave catalog as compatibility data only; remove assumptions that global catalog is SDK default behavior. |
| Modify | `third_party/agent_core/src/canonicalize/mod.rs` | Replace hard-coded parameter aliases, schema hints, special value normalization, required field validation, and canonical JSON shaping with `ToolSemanticsConfig` plus catalog hints. |
| Modify | `third_party/agent_core/src/repair/mod.rs` | Accept a semantics-aware catalog/registry path and preserve repair outcomes without consulting Enclave globals unless configured. |
| Modify | `third_party/agent_core/src/tool_parse/mod.rs` | Add semantics-aware parse/coerce entry points and keep current public wrappers as Enclave-compatible shims. |
| Modify | `third_party/agent_core/src/artifact_finalize.rs` | Replace hard-coded artifact tracking and synthetic finalize generation with configurable artifact lifecycle semantics. |
| Modify | `third_party/agent_core/src/guardrails.rs` | Replace `last_model_response_had_append_blocks` with a generic configured greedy-extension signal. |
| Modify | `third_party/agent_core/src/engine/state.rs` | Store configured tool semantics, active disclosure tool name, generic greedy-extension state, and pending artifact state keyed by semantics. |
| Modify | `third_party/agent_core/src/engine/mod.rs` | Route disclosure gating, repair, result spill/truncation exceptions, artifact progress, auto-finalization, compaction deferral, and greedy guardrail detection through semantics instead of literal tool names. |
| Modify | `third_party/agent_core/src/compact/artifact_squash.rs` | Make artifact squash conditional on configured compaction semantics and configurable field/tool names. |
| Modify | `third_party/agent_core/src/compact/micro.rs` | Replace hard-coded artifact tool exclusions with configured `preserve_result_tools` or equivalent non-reproducible semantics. |
| Modify | `third_party/agent_core/src/ffi/types.rs` | Add UniFFI records for tool semantics and map them into `AgentConfig` / `SessionIoParams` without exposing Android types. |
| Modify | `third_party/agent_core/src/ffi/policy_bridge.rs` | Add semantics-aware FFI functions for repair/coerce/parse where Android needs direct parser calls. Existing functions remain Enclave-compatible wrappers. |
| Modify | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/inference/AgentLoopModels.kt` | Add pure-domain `AgentLoopToolSemanticsConfig` and nested records. Provide Enclave default values in domain without framework types. |
| Modify | `core/agent/src/main/kotlin/com/browntowndev/enclave/core/agent/RustAgentEngineAdapter.kt` | Map domain semantics into UniFFI config when creating sessions. |
| Modify | `core/agent/src/main/kotlin/com/browntowndev/enclave/core/agent/agent_core.kt` | Regenerated UniFFI bindings after Rust FFI changes. |
| Modify | `third_party/agent_core/uniffi.toml` | Update only if UniFFI requires explicit type coverage for new records. |
| Create | `third_party/agent_core/tests/tool_semantics_config_test.rs` | Rust integration coverage for agnostic defaults, custom semantics, validation, and mutation-defense scenarios. |
| Modify | `third_party/agent_core/tests/tool_call_canonicalize_test.rs` | Move hard-coded Enclave expectations behind explicit Enclave semantics and add custom-name canonicalization coverage. |
| Modify | `third_party/agent_core/tests/lazy_tool_disclosure_test.rs` | Verify custom disclosure tool name and request field, and fail-closed behavior when disclosure semantics are absent. |
| Create | `third_party/agent_core/tests/tool_semantics_engine_test.rs` | Engine-level coverage for custom artifact tools, result truncation exceptions, greedy extension, and auto-finalization. |
| Modify | `third_party/agent_core/tests/fixtures.rs` | Pass Enclave compatibility semantics to existing fixture harness. |
| Create | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/model/inference/AgentLoopToolSemanticsConfigTest.kt` | Domain default and custom semantics model tests. |
| Modify | `core/agent/src/test/kotlin/com/browntowndev/enclave/core/agent/RustAgentEngineAdapterTest.kt` | Verify UniFFI mapping sends Enclave semantics and custom semantics into Rust. |

## API contracts and interfaces

### Rust tool semantics model

New module: `agent_core::tool_semantics`.

Public type shapes:

- `ToolSemanticsConfig`
  - `catalog: ToolCatalogConfig`
  - `disclosure: Option<ToolDisclosureSemantics>`
  - `artifact: Option<ArtifactToolSemantics>`
  - `argument_repairs: Vec<ToolArgumentRepairRule>`
  - `canonical_shapes: Vec<ToolCanonicalShape>`
  - `result_policies: Vec<ToolResultPolicy>`
  - `compaction: ToolCompactionSemantics`
  - `greedy_extension_tools: Vec<ToolGreedyExtensionSemantics>`

- `ToolSemanticsConfig::agnostic() -> Self`
  - Empty catalog, no disclosure, no artifact semantics, no argument repair rules, no special result policies, no artifact squash, no greedy extension.

- `ToolSemanticsConfig::enclave_compat() -> Self`
  - Matches current behavior for `disclose_tool_schemas`, `tool_names`, `start_artifact`, `append_blocks`, `search_current_artifact`, `artifactId`, `isComplete`, `blocks`, non-truncating append result handling, artifact squash, micro compaction preservation, and greedy extension.

- `ToolSemanticsConfig::validate(&self) -> Result<(), ToolSemanticsError>`
  - Rejects duplicate canonical tool names after normalization.
  - Rejects aliases colliding with different canonical tools.
  - Rejects disclosure config where `request_field_name` is empty.
  - Rejects artifact config where `start_tool_name`, `append_tool_name`, `id_field`, `append_blocks_field`, or `completion_field` is empty.
  - Rejects result policy entries with empty tool names.

- `ToolSemanticsError`
  - `DuplicateToolName { normalized_name: String }`
  - `AliasCollision { alias: String, first_tool: String, second_tool: String }`
  - `InvalidDisclosureConfig { message: String }`
  - `InvalidArtifactConfig { message: String }`
  - `InvalidResultPolicy { message: String }`

- `ToolCatalogConfig`
  - `tools: Vec<ToolSpec>`
  - Reuses existing `ToolSpec`, `ToolSchemaHint`, and `FieldHint`.

- `ToolDisclosureSemantics`
  - `tool_name: String`
  - `request_field_name: String`
  - `always_callable: bool`

- `ArtifactToolSemantics`
  - `start_tool_name: String`
  - `append_tool_name: String`
  - `id_field: String`
  - `append_blocks_field: String`
  - `completion_field: String`
  - `status_field: String`
  - `success_status_value: String`
  - `synthetic_call_id_prefix: String`
  - `empty_append_payload_value: serde_json::Value`

- `ToolArgumentRepairRule`
  - `tool_name: String`
  - `aliases: Vec<ParameterAlias>`
  - `special_values: Vec<SpecialValueRepair>`

- `ParameterAlias`
  - `alias: String`
  - `canonical: String`

- `SpecialValueRepair`
  - Cases:
    - `ScalarToArray { source: String, target: String }`
    - `StripLeadingBracketFromArrayStrings { field: String }`
    - `ValidateStringifiedJsonArray { field: String }`

- `ToolCanonicalShape`
  - `tool_name: String`
  - `ordered_fields: Vec<String>`
  - Missing fields are omitted unless schema defaults add them.

- `ToolResultPolicy`
  - `tool_name: String`
  - `spill_stored_copy_only: bool`
  - `skip_result_truncation: bool`
  - `preserve_in_micro_compaction: bool`

- `ToolCompactionSemantics`
  - `artifact_squash: Option<ArtifactSquashSemantics>`

- `ArtifactSquashSemantics`
  - `append_tool_name: String`
  - `id_field: String`
  - `blocks_field: String`
  - `total_blocks_result_field: String`
  - `status_field: String`
  - `success_status_value: String`
  - `summary_template: String`
  - `continuation_instruction: String`
  - `snippet_max_chars: usize`

- `ToolGreedyExtensionSemantics`
  - `tool_name: String`
  - `extra_turns: u32`

### Rust catalog and canonicalization contracts

- `ToolCatalog::from_semantics(config: &ToolSemanticsConfig) -> Result<ToolCatalog, ToolSemanticsError>`
  - Builds the catalog from `config.catalog.tools`.
  - Does not read `resources/tool_catalog.json`.

- Existing `ToolCatalog::global()` remains available for Enclave compatibility tests and wrappers.

- `canonicalize_tool_call_with_semantics(raw, catalog, semantics, mode)` supersedes `canonicalize_tool_call(raw, catalog, mode)`.

- `canonicalize_raw_arguments_with_semantics(tool_name, raw_arguments_json, catalog, semantics, mode)` supersedes `canonicalize_raw_arguments(...)`.

- Existing canonicalization functions remain as compatibility wrappers using `ToolSemanticsConfig::enclave_compat()`.

- `ToolRepairPipeline::repair_with_semantics(raw, catalog, semantics)` supersedes `ToolRepairPipeline::repair(raw, catalog)`.

- `parse_tool_calls_with_semantics(raw, catalog, semantics)` supersedes `parse_tool_calls(raw)`.

- `coerce_provider_arguments_with_semantics(tool_name, args_json, catalog, semantics)` supersedes `coerce_provider_arguments(tool_name, args_json)`.

- `record_artifact_progress_with_semantics(pending, semantics, tool_name, arguments_json, result_json)` supersedes `record_artifact_progress(...)`.

- `synthetic_finalize_calls_with_semantics(pending, semantics)` supersedes `synthetic_finalize_calls(...)`.

### Rust engine contracts

- `AgentConfig` gains `tool_semantics: ToolSemanticsConfig`.
- `AgentConfig::default()` uses `ToolSemanticsConfig::agnostic()`.
- A new constructor or helper may expose `AgentConfig::enclave_compat()` for tests and Android mapping.
- `SessionIoParams.tool_catalog_json` and `tool_schema_registry_json` remain IO payloads for prompt/schema rendering; semantic behavior comes from `AgentConfig.tool_semantics`.
- Engine active tool gating:
  - If `active_tool_names` is empty, current legacy behavior remains permissive.
  - If active tool names are non-empty and disclosure semantics is `None`, only active names are callable.
  - If disclosure semantics exists and `always_callable` is true, `disclosure.tool_name` is callable even when absent from active names.
  - Error text must use the configured disclosure name.
- Engine artifact behavior:
  - No pending artifact tracking without `artifact`.
  - Start and append progress use configured tool names and field names.
  - Synthetic finalization uses configured append tool, configured id/completion/block fields, configured prefix, and configured empty append payload.
- Engine result policy:
  - `skip_result_truncation` controls truncation exemption.
  - `spill_stored_copy_only` controls whether the stored context copy may spill while the in-session tool result remains unmodified.
  - `preserve_in_micro_compaction` controls micro compaction preservation.
- Engine greedy extension:
  - `last_model_response_had_append_blocks` becomes a generic `last_model_response_had_greedy_extension_tool`.
  - Effective cap is `max(config.max_turns, used + max(extra_turns for matching tools))`.

### UniFFI and Kotlin domain contracts

- Add `FfiToolSemanticsConfig` and nested records mirroring the Rust shapes with UniFFI-compatible fields.
- Add `toolSemantics: AgentLoopToolSemanticsConfig = AgentLoopToolSemanticsConfig.enclaveDefault()` to `AgentLoopAgentConfig`.
- `AgentLoopToolSemanticsConfig.agnostic()` must be available for SDK consumers that do not want Enclave behavior.
- `AgentLoopToolSemanticsConfig.enclaveDefault()` must exactly preserve current Enclave behavior.
- `RustAgentEngineAdapter` maps `AgentLoopToolSemanticsConfig` to `FfiToolSemanticsConfig` inside `AgentLoopAgentConfig.toFfi()`.
- `:core:domain` stays framework-free; all new domain records use Kotlin primitives, lists, and strings only.

## Data model or schema changes

No Room, Firebase, or persisted app database schema changes.

Serialized session/config changes:

- `AgentConfig` gains `tool_semantics`.
- Serde default for absent `tool_semantics` is `ToolSemanticsConfig::agnostic()`.
- Android domain defaults pass Enclave compatibility semantics to preserve current app behavior.
- Existing serialized configs that omit `tool_semantics` become agnostic in pure Rust. Android-created configs remain Enclave-compatible because domain defaults supply the field.

## Ordered implementation tasks

1. Create `tool_semantics.rs` with models, validation, `agnostic()`, and `enclave_compat()`.
   - Satisfies: TS-001, TS-002, TS-003, TS-004, TS-005, TS-006

2. Add `ToolSemanticsConfig` to `AgentConfig`, update defaults, and add Enclave compatibility helper for tests and Android.
   - Satisfies: TS-001, TS-002, TS-030, TS-031

3. Refactor `ToolCatalog` to build from `ToolSemanticsConfig` and keep `global()` as compatibility-only.
   - Satisfies: TS-002, TS-003, TS-007

4. Replace hard-coded canonicalization rules with semantics-driven aliases, defaults, required fields, special value repairs, and canonical field ordering.
   - Satisfies: TS-008, TS-009, TS-010, TS-011, TS-012, TS-013, TS-032

5. Add semantics-aware repair and parse/coerce APIs, then make existing public wrappers call Enclave compatibility semantics.
   - Satisfies: TS-014, TS-015, TS-016, TS-032

6. Refactor lazy disclosure gating in the engine to use `ToolDisclosureSemantics`.
   - Satisfies: TS-017, TS-018, TS-019, TS-033

7. Refactor artifact progress tracking and synthetic finalization to use `ArtifactToolSemantics`.
   - Satisfies: TS-020, TS-021, TS-022, TS-023

8. Refactor result spill/truncation and micro-compaction preservation to use `ToolResultPolicy`.
   - Satisfies: TS-024, TS-025, TS-026

9. Refactor greedy guardrail expansion to use `ToolGreedyExtensionSemantics`.
   - Satisfies: TS-027, TS-028

10. Refactor artifact squash compaction and artifact anchor extraction to use `ArtifactSquashSemantics`.
    - Satisfies: TS-029, TS-034

11. Add UniFFI records and conversions for the new semantics config and regenerate bindings.
    - Satisfies: TS-030, TS-031

12. Add Kotlin domain models and adapter mappings for Enclave default and agnostic semantics.
    - Satisfies: TS-030, TS-031

13. Update existing Rust fixture and integration tests to pass Enclave compatibility semantics where they assert current Enclave behavior.
    - Satisfies: TS-014, TS-017, TS-020, TS-024, TS-027, TS-029

14. Add new Rust and Kotlin tests listed in `test_spec.md`.
    - Satisfies: TS-001 through TS-034

15. Run validation commands and fix any contract, compile, lint, or test failure without broadening scope.
    - Satisfies: TS-001 through TS-034

## Drift and escalation rules for later phases

- Do not modify `AGENTS.md` or files in `/contracts/` without an explicit Contract Waiver.
- Do not add database tables, columns, entities, or migrations.
- Do not move Enclave artifact execution out of `core/data`; this change only removes Rust core assumptions about those names.
- Do not expose Android, Compose, Hilt, Room, WorkManager, or UniFFI implementation types through `:core:domain`.
- If implementation requires changing public domain names beyond adding pure data records, stop and request human approval.
- If UniFFI cannot represent a proposed nested enum cleanly, flatten the FFI model into records with string discriminator fields and keep the Rust internal model strongly typed.
- If existing serialized Rust configs depend on `AgentConfig::default()` retaining Enclave behavior, stop and ask whether pure Rust backward compatibility or SDK agnosticism wins. The planned default is SDK agnostic.
- If `resources/tool_catalog.json` is removed instead of retained as compatibility data, stop and ask for approval because existing tests and documentation refer to it.
- If a hard-coded Enclave name remains in runtime logic outside `ToolSemanticsConfig::enclave_compat()` or compatibility tests, treat it as implementation drift.
- Before generating Kotlin/Gradle code in implementation phases, use the mandatory AndroJack pre-generation and post-generation validation protocol from `AGENTS.md`.

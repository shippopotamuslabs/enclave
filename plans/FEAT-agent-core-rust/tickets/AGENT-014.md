# AGENT-014 — Tool repair pipeline

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E3 — Tool repair & guardrails |
| **Depends on** | AGENT-005 |
| **Blocks** | AGENT-017, AGENT-034 |

## Description

`ToolRepairPipeline::repair(raw: RawToolCall, catalog: &ToolCatalog) -> RepairOutcome`:

**Name repair steps (in order):**

1. Trim whitespace
2. Strip prefixes: `functions.`, `tools.`
3. Normalize to `snake_case` (e.g., `WebSearch` → `web_search`, `WEB_SEARCH` → `web_search`)
4. Exact catalog lookup — if found, done
5. Static alias lookup (e.g., `websearch` → `web_search`)
6. Fuzzy match via `strsim::normalized_levenshtein` — auto-accept if score ≥ **0.85** (locked); reject below threshold

**Argument JSON repair (applied after name resolves):**

1. Trim wrapping prose or markdown code fences around the JSON (scan for first `{` / `[`)
2. Parse JSON; if parse fails → `Reject` with parse error diagnostic
3. For required fields in `ToolSpec.schema_hint.required_fields`: if field absent, inject from `schema_hint.defaults` if present; if no default, include in diagnostic
4. Coerce obvious type mismatches: JSON string `"true"`/`"false"` → boolean, numeric string → number where the hint specifies a non-string type
5. Unknown extra fields: pass through (do not strip)

**`RepairOutcome`:**

```rust
pub enum RepairOutcome {
    Repaired(ToolCallRecord),
    Reject { error_json_for_model: String },   // structured: { "error": "...", "available_tools": [...] }
    Suggest { original: String, closest_name: String },  // optional: for model self-correct
}
```

Emits `AgentEvent::ToolRepaired { original_name, resolved_name, confidence }` on successful fuzzy repair.

No envelope parsing in Rust v1 — envelope extraction stays in Kotlin transport adapters (`ToolEnvelopeParser.kt`).

## Acceptance criteria

- [ ] Tests: `web_seach` → `web_search` (fuzzy), `WEB_SEARCH` → `web_search` (normalize), `functions.web_search` → `web_search` (prefix strip), unknown with score < 0.85 → `Reject`, missing required field with default → injected
- [ ] No repair applied below 0.85 score (test boundary: score = 0.84 → `Reject`)
- [ ] Argument JSON with code fence wrapping repaired correctly
- [ ] `error_json_for_model` includes `available_tools` list on reject

## Files

- `third_party/agent_core/src/repair/mod.rs`
- `third_party/agent_core/src/repair/fuzzy.rs`

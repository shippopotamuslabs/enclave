# AGENT-005 — Tool catalog & static aliases

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E1 — Rust domain model |
| **Depends on** | AGENT-004 |
| **Blocks** | AGENT-010, AGENT-014, AGENT-038 |

## Description

Port Pocket Crew tool definitions into Rust as `resources/tool_catalog.json` embedded via `include_str!`.

### Exhaustive catalog (mirrors `ToolDefinition.ALL_TOOLS` at `ToolDefinition.kt:277-292`)

| `name` | `reproducible` | Notes |
|--------|---------------|-------|
| `web_search` | true | |
| `web_extract` | true | |
| `attached_image_inspect` | true | |
| `search_chat_history` | true | |
| `search_chat` | true | |
| `get_message_context` | true | |
| `search_current_artifact` | true | |
| `search_x_recent` | true | alias: `x_search` |
| `start_artifact` | false | Non-reproducible — creates persistent state |
| `append_blocks` | false | Non-reproducible — handled by tier 4 (ArtifactSquash), never micro-cleared |
| `manage_memories` | false | |
| `generate_image` | false | |
| `generate_video` | false | |
| `generate_music` | false | |

### Types

```rust
pub struct FieldHint {
    pub name: &'static str,
    pub ty: &'static str,       // "string", "number", "boolean", "object", "array"
    pub description: &'static str,
}

pub struct ToolSchemaHint {
    pub required_fields: Vec<FieldHint>,
    pub defaults: HashMap<&'static str, serde_json::Value>,
}

pub struct ToolSpec {
    pub name: &'static str,
    pub aliases: Vec<&'static str>,
    pub reproducible: bool,
    pub schema_hint: Option<ToolSchemaHint>,
}
```

Schema hints for repair argument injection (minimum — implementer should expand based on `ToolParameters` Kotlin classes):
- `start_artifact`: required `{ title: string, kind: string }`
- `append_blocks`: required `{ artifactId: string, blocks: array }`, defaults `{ "isComplete": false }`
- `manage_memories`: required `{ action: string }`

### Static alias table

Beyond per-tool `aliases`, apply these normalizations globally before catalog lookup:
- Strip prefix `functions.` (OpenAI function-calling legacy)
- Strip prefix `tools.`
- Lowercase + snake_case (e.g., `WEB_SEARCH` → `web_search`, `WebSearch` → `web_search`)
- Direct aliases: `websearch` → `web_search`, `x_search` → `search_x_recent`, `web_fetch` → `web_extract`

### API

```rust
pub struct ToolCatalog { /* ... */ }

impl ToolCatalog {
    pub fn resolve(&self, name: &str) -> Option<&ToolSpec>;  // exact + alias
    pub fn all(&self) -> &[ToolSpec];
}
```

Embed catalog JSON via `include_str!("../resources/tool_catalog.json")` — catalog struct initialized once at startup via `once_cell::sync::Lazy`.

## Acceptance criteria

- [ ] All 14 tools present (AGENT-038 CI enforces sync with Kotlin)
- [ ] Unit tests: alias resolution (`x_search` → `search_x_recent`), prefix strip (`functions.web_search` → `web_search`), unknown tool returns `None`
- [ ] `reproducible=false` tools excluded from micro-compact tier 1
- [ ] `append_blocks` and `start_artifact` `schema_hint.required_fields` non-empty
- [ ] Catalog loaded via `include_str!` (no runtime file I/O)

## Files

- `third_party/agent_core/src/catalog.rs`
- `third_party/agent_core/resources/tool_catalog.json`

# TOOL-PARSE-001 — test_spec.md
# Test Specification: Canonical Tool-Call Parsing Migration to Rust `agent_core`

## Test Framework

- **Rust scenarios (R-XX):** standard `cargo test` (built-in libtest). One
  integration test file `third_party/agent_core/tests/tool_parse_integration.rs`
  drives the public `tool_parse::parse_tool_calls` /
  `coerce_provider_arguments` / `has_local_tool_contract` API.
- **Kotlin facade scenarios (K-XX):** JUnit 5 (`org.junit.jupiter.api.*`)
  with `kotlin.test` assertions (`assertEquals`, `assertNotNull`,
  `assertFailsWith`). No coroutines. Uses
  `FakeToolEnvelopeParserPort` (Fakes over Mocks per
  `contracts/UNIT_TESTING.md`).
- **Integration scenarios (LI-XX):** JUnit 5 plus `kotlinx.coroutines.test.runTest`
  for any suspending call site. `LI-03` (real-UniFFI contract test) loads the
  JNI library exactly as the existing `ConversationManagerImplPolicyBridgeTest`
  does.

### Run Commands

```bash
# Rust pipeline + JSON repair unit tests (all R-XX)
cargo test --manifest-path third_party/agent_core/Cargo.toml --test tool_parse_integration
cargo test --manifest-path third_party/agent_core/Cargo.toml

# Kotlin facade (all K-XX)
./gradlew :core:domain:test --tests "*.ToolEnvelopeParserTest"

# Kotlin call-site wiring + adapter contract (all LI-XX)
./gradlew :feature:inference:test --tests "*.LlamaInferenceServiceImplToolTest" \
                                  --tests "*.AgentTurnTextGateTest" \
                                  --tests "*.RustToolEnvelopeParserContractTest"

# Full test suite (mandatory before declaring complete)
./gradlew test
cargo test --manifest-path third_party/agent_core/Cargo.toml
```

---

## Scenario Index

| ID | Layer | Description |
|---|---|---|
| R-01 | Rust pipeline | DSML standard delimiters single invoke |
| R-02 | Rust pipeline | DSML fullwidth `｜` delimiters single invoke |
| R-03 | Rust pipeline | DSML invoke with `string="true"` parameter |
| R-04 | Rust pipeline | DSML three invokes in one tool_calls block |
| R-05 | Rust pipeline | DSML two tool_calls blocks in one response |
| R-06 | Rust pipeline | DSML missing closing parameter tag |
| R-07 | Rust pipeline | DSML missing closing invoke tag |
| R-08 | Rust pipeline | DSML empty required-parameter body |
| R-09 | Rust pipeline | DSML unknown tool name |
| R-10 | Rust pipeline | DSML unknown parameter name |
| R-11 | Rust pipeline | DSML duplicate parameter name |
| R-12 | Rust pipeline | DSML unknown parameter precedence over missing required |
| R-13 | Rust pipeline | DSML `string="true"` mismatched with array schema |
| R-14 | Rust pipeline | DSML malformed bracket-prefixed URL string |
| R-15 | Rust pipeline | DSML trailing comma inside JSON-like parameter |
| R-16 | Rust pipeline | DSML preserves nested brackets, escapes, newlines |
| R-17 | Rust pipeline | Markdown-fenced tool calls (```` ```xml ```` …) |
| R-18 | Rust pipeline | Mistral `[TOOL_CALLS]` single element |
| R-19 | Rust pipeline | Mistral `[TOOL_CALLS]` multi-element |
| R-20 | Rust pipeline | Mistral sentinel present but no JSON array |
| R-21 | Rust pipeline | Mistral with visible prefix text |
| R-22 | Rust pipeline | `<tool_call>` wrapping JSON object |
| R-23 | Rust pipeline | `<tool_call>` wrapping JSON array two elements |
| R-24 | Rust pipeline | GLM `<tool_call>` `<arg_key>`/`<arg_value>` form |
| R-25 | Rust pipeline | CDATA `<![CDATA[<tool>…</tool>]]>` |
| R-26 | Rust pipeline | Raw JSON `{"name":…,"arguments":…}` in prose |
| R-27 | Rust pipeline | `"parameters"` key normalized to `"arguments"` |
| R-28 | Rust pipeline | Single-quoted JSON repaired to double-quoted |
| R-29 | Rust pipeline | Smart quotes `“…”` → ASCII `"` |
| R-30 | Rust pipeline | Mutation defense — `string="true"` on `urls` plus catalog defaults |
| R-31 | Rust JSON repair | Missing comma between object values |
| R-32 | Rust JSON repair | Missing comma between array elements |
| R-33 | Rust JSON repair | Python `None` → `null` |
| R-34 | Rust JSON repair | Python `True` / `False` → boolean |
| R-35 | Rust JSON repair | `undefined` → `null` |
| R-36 | Rust JSON repair | Missing colon between key and value |
| R-37 | Rust JSON repair | Leading comma in object |
| R-38 | Rust JSON repair | Truncated string missing end quote |
| R-39 | Rust JSON repair | Block and line comments stripped |
| R-40 | Rust JSON repair | String concatenation with `+` collapsed |
| R-41 | Rust JSON repair | Newline-delimited JSON wrapped in array |
| R-42 | Rust schema coercion | Python-style string array → real array when schema expects `array` |
| R-43 | Rust schema coercion | Array string with trailing garbage → clean extracted array |
| R-44 | Rust schema coercion | Double-encoded JSON string unwrapped recursively |
| R-45 | Rust schema coercion | Empty string → `null` for nullable catalog field |
| K-01 | Kotlin facade | `extractLocalToolEnvelope` returns first success |
| K-02 | Kotlin facade | `extractLocalToolEnvelopes` returns multi successes |
| K-03 | Kotlin facade | Malformed-envelope message preserved (non-DSML & DSML) |
| K-04 | Kotlin facade | Missing required parameter message preserved |
| K-05 | Kotlin facade | Unsupported tool / unknown / duplicate parameter messages preserved |
| K-06 | Kotlin facade | `buildArgumentsJson(toolName, map)` round-trips through coercion |
| K-07 | Kotlin facade | `hasLocalToolContract` returns true for DSML+tool-name prompt |
| LI-01 | Inference wiring | `LlamaInferenceServiceImpl` maps successes → `AgentRawToolCall` |
| LI-02 | Inference wiring | `AgentTurnTextGate.leakedToolCalls` reads via port |
| LI-03 | UniFFI contract | Real `RustToolEnvelopeParser` adapter round-trips DSML, Mistral, `<tool_call>` |

---

## Happy-Path Scenarios — Rust pipeline

### Scenario R-01: DSML standard delimiters single invoke
Given the raw model output literal
```
<| DSML | tool_calls><| DSML | invoke name="web_search">
<| DSML | parameter name="query" string="true">digg.com xAI completes training Grok V9</| DSML | parameter>
</| DSML | invoke></| DSML | tool_calls>
```
When `parse_tool_calls(raw)` is invoked
Then the returned `ToolParseBatch.successes` length equals `1`
And `successes[0].tool_name` equals the literal string `"web_search"`
And `successes[0].arguments_json` equals the literal string `{"query":"digg.com xAI completes training Grok V9"}`
And `failures` is the empty vector
And `visible_prefix` equals the empty string `""`
And `visible_suffix` equals the empty string `""`.

### Scenario R-02: DSML fullwidth `｜` delimiters
Given the raw model output literal
```
<｜｜DSML｜｜tool_calls><｜｜DSML｜｜invoke name="web_search">
<｜｜DSML｜｜parameter name="query" string="true">android api 36</｜｜DSML｜｜parameter>
</｜｜DSML｜｜invoke></｜｜DSML｜｜tool_calls>
```
When `parse_tool_calls(raw)` is invoked
Then `successes.len() == 1`
And `successes[0].tool_name` equals `"web_search"`
And `successes[0].arguments_json` equals `{"query":"android api 36"}`
And `failures` is empty.

### Scenario R-03: DSML invoke with `string="true"` parameter
Given the same input as R-01
When parsed
Then `successes[0].repaired` is `false`
And `successes[0].repairs` is the empty vector.

### Scenario R-04: DSML three invokes in one tool_calls block
Given a single `tool_calls` block containing three `invoke` children
(`web_extract`, `web_extract`, `web_search`) with valid required parameters
When parsed
Then `successes.len() == 3`
And `successes[0].tool_name == "web_extract"`
And `successes[1].tool_name == "web_extract"`
And `successes[2].tool_name == "web_search"`
And `successes[2].arguments_json` equals `{"query":"kotlin parser repair"}`.

### Scenario R-05: Two tool_calls blocks in one response
Given two separate `<｜｜DSML｜｜tool_calls>…</｜｜DSML｜｜tool_calls>` blocks
separated by prose (each containing one `web_search` invoke with queries
`"first query"` and `"second query"`)
When parsed
Then `successes.len() == 2`
And `successes[0].arguments_json` equals `{"query":"first query"}`
And `successes[1].arguments_json` equals `{"query":"second query"}`.

### Scenario R-17: Markdown-fenced tool calls
Given the raw output
```
I will call the tool now.

```xml
<｜｜DSML｜｜tool_calls><｜｜DSML｜｜invoke name="web_search">
<｜｜DSML｜｜parameter name="query" string="true">android api 36</｜｜DSML｜｜parameter>
</｜｜DSML｜｜invoke></｜｜DSML｜｜tool_calls>
```

Done.
```
When parsed
Then `successes.len() == 1`
And `successes[0].arguments_json` equals `{"query":"android api 36"}`
And `visible_prefix` equals `"I will call the tool now.\n\n"`
And `visible_suffix` equals `"\n\nDone."`.

### Scenario R-18: Mistral `[TOOL_CALLS]` single element
Given the raw output literal `[TOOL_CALLS] [{"name": "web_search", "arguments": {"query": "kotlin coroutines"}}]`
When parsed
Then `successes.len() == 1`
And `successes[0].tool_name` equals `"web_search"`
And `successes[0].arguments_json` equals `{"query":"kotlin coroutines"}`.

### Scenario R-19: Mistral `[TOOL_CALLS]` multi-element
Given `[TOOL_CALLS] [{"name":"web_search","arguments":{"query":"news"}},{"name":"search_chat_history","arguments":{"query":"old chats"}}]`
When parsed
Then `successes.len() == 2`
And `successes[1].tool_name` equals `"search_chat_history"`
And `successes[1].arguments_json` contains the substring `"queries":["old chats"]`.

### Scenario R-21: Mistral with visible prefix
Given `"I will search now.\n[TOOL_CALLS] [{\"name\": \"web_search\", \"arguments\": {\"query\": \"android 15\"}}]"`
When parsed
Then `successes.len() == 1`
And `visible_prefix` equals `"I will search now."`
And `visible_suffix` equals the empty string.

### Scenario R-22: `<tool_call>` wrapping JSON object
Given `<tool_call>{"name":"web_search","arguments":{"query":"pocket crew android"}}</tool_call>`
When parsed
Then `successes.len() == 1`
And `successes[0].arguments_json` equals `{"query":"pocket crew android"}`.

### Scenario R-23: `<tool_call>` wrapping JSON array two elements
Given `<tool_call>[{"name":"web_search","arguments":{"query":"q1"}},{"name":"web_extract","arguments":{"urls":["https://a.com"]}}]</tool_call>`
When parsed
Then `successes.len() == 2`
And `successes[0].arguments_json` equals `{"query":"q1"}`
And `successes[1].arguments_json` contains the substring `"urls":["https://a.com"]`
And `successes[1].arguments_json` contains the substring `"extract_depth":"basic"`
And `successes[1].arguments_json` contains the substring `"format":"markdown"`.

### Scenario R-24: GLM `<arg_key>`/`<arg_value>` form
Given `<tool_call> web_search <arg_key>query</arg_key> <arg_value>"weather in London"</arg_value> </tool_call>`
When parsed
Then `successes.len() == 1`
And `successes[0].tool_name` equals `"web_search"`
And `successes[0].arguments_json` equals `{"query":"weather in London"}`.

### Scenario R-25: CDATA payload
Given `<![CDATA[<tool> {"name": "web_search", "arguments": {"query": "weather in London"}} </tool>]]>`
When parsed
Then `successes.len() == 1`
And `successes[0].arguments_json` equals `{"query":"weather in London"}`.

### Scenario R-26: Raw JSON in prose
Given the raw output literal
```
I will fetch that.
{"name":"web_extract","arguments":{"urls":["https://example.com"]}}
Done.
```
When parsed
Then `successes.len() == 1`
And `successes[0].tool_name` equals `"web_extract"`
And `successes[0].arguments_json` contains the substring `"urls":["https://example.com"]`
And `visible_prefix` equals `"I will fetch that.\n"`
And `visible_suffix` equals `"\nDone."`.

### Scenario R-27: `"parameters"` key normalized
Given `<tool_call>{"name":"web_search","parameters":{"query":"llama 3.2 benchmarks"}}</tool_call>`
When parsed
Then `successes.len() == 1`
And `successes[0].arguments_json` equals `{"query":"llama 3.2 benchmarks"}`.

---

## Edge-Case and Repair Scenarios — Rust pipeline

### Scenario R-14: Malformed bracket-prefixed URL string
Given a DSML `web_extract` invoke whose `urls` parameter body literal is `["[https://example.com"]`
When parsed
Then `successes.len() == 1`
And `successes[0].arguments_json` equals `{"urls":["https://example.com"],"extract_depth":"basic","format":"markdown"}`
And `successes[0].repaired` is `true`
And `successes[0].repairs` contains the string `"stripped leading bracket from bracketed URL string"`.

### Scenario R-15: Trailing comma inside JSON-like parameter
Given a DSML `web_extract` invoke whose `urls` parameter body literal is `["[https://example.com",]`
When parsed
Then `successes[0].arguments_json` equals `{"urls":["https://example.com"],"extract_depth":"basic","format":"markdown"}`
And `successes[0].repairs` contains the string `"removed trailing comma"`.

### Scenario R-16: Preserves nested brackets, escapes, newlines
Given a DSML `web_search` invoke whose `query` parameter body literal is
`find [docs] with \"quoted\" phrase\nnext line`
When parsed
Then `successes[0].arguments_json` equals `{"query":"find [docs] with \"quoted\" phrase\nnext line"}`.

### Scenario R-28: Single-quoted JSON repaired
Given `<tool_call> {'name': 'web_search', 'arguments': {'query': 'weather in London'}} </tool_call>`
When parsed
Then `successes[0].arguments_json` equals `{"query":"weather in London"}`
And `successes[0].repairs` contains the string `"converted single quotes to double quotes"`.

### Scenario R-29: Smart quotes normalized
Given `<tool_call> {"name": "web_search", "arguments": {"query": "cost of “bread” in Paris"}} </tool_call>`
When parsed
Then `successes[0].arguments_json` equals `{"query":"cost of \"bread\" in Paris"}`.

### Scenario R-13: `string="true"` mismatched with array schema
Given a DSML `web_extract` invoke whose `urls` parameter is declared
`string="true"` and whose body literal is `["[https://example.com"]`
When parsed
Then `successes[0].arguments_json` equals `{"urls":["https://example.com"],"extract_depth":"basic","format":"markdown"}`
And `successes[0].repaired` is `true`.

---

## Error-Path Scenarios — Rust pipeline

### Scenario R-06: DSML missing closing parameter tag
Given a DSML block whose `parameter` opens but is followed directly by
`</｜｜DSML｜｜invoke>` with no closing `</｜｜DSML｜｜parameter>`
When parsed
Then `successes` is the empty vector
And `failures.len() == 1`
And `failures[0].reason` is `ToolParseFailureReason::MalformedEnvelope`
And the FFI projection of that failure carries `format_hint == Some("dsml")`.

### Scenario R-07: DSML missing closing invoke tag
Given a DSML block whose `invoke` opens, has a closed `parameter`, but no
closing `</｜｜DSML｜｜invoke>` before the `</｜｜DSML｜｜tool_calls>`
When parsed
Then `failures.len() == 1`
And `failures[0].reason` is `ToolParseFailureReason::MalformedEnvelope`
And the FFI projection carries `format_hint == Some("dsml")`.

### Scenario R-08: DSML empty required-parameter body
Given a DSML `web_search` invoke whose `query` parameter body literal is
three spaces `   `
When parsed
Then `failures.len() == 1`
And `failures[0].reason` equals
`ToolParseFailureReason::MissingRequiredParameter { tool: "web_search".into(), param: "query".into() }`.

### Scenario R-09: DSML unknown tool name
Given a DSML invoke with `name="unknown_tool"`
When parsed
Then `failures.len() == 1`
And `failures[0].reason` equals
`ToolParseFailureReason::UnknownTool { name: "unknown_tool".into() }`.

### Scenario R-10: DSML unknown parameter name
Given a DSML `web_search` invoke that includes parameter `query="android"` *and* parameter `extra="ignored"`
When parsed
Then `failures.len() == 1`
And `failures[0].reason` equals
`ToolParseFailureReason::UnknownParameter { tool: "web_search".into(), param: "extra".into() }`.

### Scenario R-11: DSML duplicate parameter name
Given a DSML `web_search` invoke with two `parameter name="query"` children
(`"first"` and `"second"`)
When parsed
Then `failures.len() == 1`
And `failures[0].reason` equals
`ToolParseFailureReason::DuplicateParameter { tool: "web_search".into(), param: "query".into() }`.

### Scenario R-12: Unknown parameter precedence over missing required (mutation defense)
Given a DSML `web_search` invoke that contains *only* one parameter
`name="unused" string="true"` body `"android"` (no `query`)
When parsed
Then `failures.len() == 1`
And `failures[0].reason` equals
`ToolParseFailureReason::UnknownParameter { tool: "web_search".into(), param: "unused".into() }`
(not `MissingRequiredParameter`).

### Scenario R-20: Mistral sentinel without JSON array
Given the literal text `"[TOOL_CALLS] not_json_here"`
When parsed
Then `failures.len() == 1`
And `failures[0].reason` is `ToolParseFailureReason::MalformedEnvelope`
And the FFI projection of that failure carries `format_hint == None`.

---

## Mutation-Defense Scenario — Rust pipeline

### Scenario R-30: Mutation defense for catalog lookup + schema coercion
Given a DSML `web_extract` invoke whose `urls` parameter has
`string="true"` and body literal `["[https://example.com"]` (string scalar,
*not* a parsed array)
When parsed
Then `successes.len() == 1`
And `successes[0].arguments_json` equals the literal string
`{"urls":["https://example.com"],"extract_depth":"basic","format":"markdown"}`
And `successes[0].repaired` is `true`.

**Why this defends against the most plausible lazy implementation:** a
short-circuit pipeline that does `serde_json::from_str` on the raw payload
and returns the value verbatim would emit either
`{"urls":"https://example.com"}` (string scalar — wrong type) or
`{"urls":["https://example.com"]}` without the catalog defaults
`extract_depth` and `format`. The exact-string assertion fails against both
shortcuts, forcing the implementation to look up the
`web_extract` schema hint, coerce the scalar to a single-element array, and
inject the catalog default fields.

---

## Full JSON Repair Scenarios — Rust `repair_json` unit tests

These scenarios are tested directly against `repair_json(input)` in
`third_party/agent_core/tests/tool_parse_integration.rs` (or a dedicated
`json_repair` module test block), **not** through the full pipeline. Each
asserts the returned `serde_json::Value` round-trips to the exact JSON string
shown.

### Scenario R-31: Missing comma between object values
Given the input string `{"a":"foo" "b":"bar"}`
When `repair_json(input)` is called
Then the result is `Ok(value)` where `value.to_string()` equals `{"a":"foo","b":"bar"}`.

### Scenario R-32: Missing comma between array elements
Given the input string `["alpha" "beta" "gamma"]`
When `repair_json(input)` is called
Then the result is `Ok(value)` where `value.to_string()` equals `["alpha","beta","gamma"]`.

### Scenario R-33: Python `None` keyword
Given the input string `{"result": None}`
When `repair_json(input)` is called
Then the result is `Ok(value)` where `value.to_string()` equals `{"result":null}`.

### Scenario R-34: Python `True` and `False` keywords
Given the input string `{"flag": True, "other": False}`
When `repair_json(input)` is called
Then the result is `Ok(value)` where `value.to_string()` equals `{"flag":true,"other":false}`.

### Scenario R-35: `undefined` → `null`
Given the input string `{"value": undefined}`
When `repair_json(input)` is called
Then the result is `Ok(value)` where `value.to_string()` equals `{"value":null}`.

### Scenario R-36: Missing colon between object key and value
Given the input string `{"key" "value"}`
When `repair_json(input)` is called
Then the result is `Ok(value)` where `value.to_string()` equals `{"key":"value"}`.

### Scenario R-37: Leading comma in object
Given the input string `{, "k":"v"}`
When `repair_json(input)` is called
Then the result is `Ok(value)` where `value.to_string()` equals `{"k":"v"}`.

### Scenario R-38: Truncated string missing end quote
Given the input string `{"query":"android development`  (no closing quote or brace)
When `repair_json(input)` is called
Then the result is `Ok(value)` where `value.to_string()` equals `{"query":"android development"}`.

### Scenario R-39: Block and line comments stripped
Given the input string `{"k": /* comment */ "v", // trailing\n "k2":"v2"}`
When `repair_json(input)` is called
Then the result is `Ok(value)` where the parsed object has key `"k"` with string value `"v"` and key `"k2"` with string value `"v2"`.

### Scenario R-40: String concatenation collapsed
Given the input string `{"title": "hello " + "world"}`
When `repair_json(input)` is called
Then the result is `Ok(value)` where `value.to_string()` equals `{"title":"hello world"}`.

### Scenario R-41: Newline-delimited JSON wrapped in array
Given the input string  (two JSON objects on separate lines, no enclosing array)
```
{"name":"web_search","arguments":{"query":"first"}}
{"name":"web_extract","arguments":{"urls":["https://a.com"]}}
```
When `repair_json(input)` is called
Then the result is `Ok(value)` where `value` is a JSON array of length `2`
And `value[0]["name"]` equals the string `"web_search"`
And `value[1]["name"]` equals the string `"web_extract"`.

---

## Schema Coercion Scenarios — Rust `coerce_to_schema` unit tests

These scenarios test `coerce_to_schema(value, hint)` directly against
`ToolSchemaHint` fixtures. The `web_extract` tool's `urls` field (schema: `array`,
item type `string`) and an optional/nullable `format` field serve as fixtures.

### Scenario R-42: Python-style string array → real array
Given a `ToolSchemaHint` for `web_extract.urls` with `field_type: "array"`
And the input `Value::String("[{'href': 'https://example.com'}]".to_string())`
When `coerce_to_schema(value, &hint)` is called
Then the result is `Value::Array` of length `1`
And `result[0]["href"]` equals the string `"https://example.com"`.

### Scenario R-43: Array string with trailing garbage → clean extracted array
Given the same `ToolSchemaHint` (array type)
And the input `Value::String("[\"https://example.com\"]{\\n}".to_string())`
When `coerce_to_schema(value, &hint)` is called
Then the result is `Value::Array` of length `1`
And `result[0]` equals the string `"https://example.com"`.

### Scenario R-44: Double-encoded JSON string unwrapped
Given the same `ToolSchemaHint` (array type)
And the input `Value::String("\"[\\\"https://example.com\\\"]\"\"")`  (a JSON string whose content is a JSON string whose content is a JSON array)
When `coerce_to_schema(value, &hint)` is called
Then the result is `Value::Array` of length `1`
And `result[0]` equals the string `"https://example.com"`.

### Scenario R-45: Empty string → `null` for nullable field
Given a `ToolSchemaHint` for a catalog field marked `optional: true`
And the input `Value::String("".to_string())`
When `coerce_to_schema(value, &hint)` is called
Then the result is `Value::Null`.

---

## Kotlin Facade Scenarios

### Scenario K-01: `extractLocalToolEnvelope` returns first success
Given a `FakeToolEnvelopeParserPort` configured so `parse("INPUT")` returns
a `ToolEnvelopeBatch` with `successes = [ToolEnvelopeField(tool_name="web_search", arguments_json="{\"query\":\"q1\"}", repaired=false, repairs=[])]`
And `failures = []`, `visible_prefix = ""`, `visible_suffix = ""`
When `ToolEnvelopeParser.extractLocalToolEnvelope("INPUT")` is called
Then the return value is non-null
And `.toolName` equals `"web_search"`
And `.argumentsJson` equals `{"query":"q1"}`.

### Scenario K-02: `extractLocalToolEnvelopes` returns multi successes
Given the fake returns two `ToolEnvelopeField` entries for `"INPUT"`
(`web_search` with `{"query":"first"}` and `web_search` with `{"query":"second"}`)
When `ToolEnvelopeParser.extractLocalToolEnvelopes("INPUT")` is called
Then the returned `List<LocalToolEnvelope>` has size `2`
And `result[0].argumentsJson` equals `{"query":"first"}`
And `result[1].argumentsJson` equals `{"query":"second"}`.

### Scenario K-03: Malformed-envelope message preserved (non-DSML and DSML)
Given the fake returns `failures = [ToolEnvelopeFailure(MalformedEnvelope, "raw")]` with no DSML hint
When `extractLocalToolEnvelopes("X")` is called
Then an `IllegalStateException` is thrown
And the exception message equals the literal `"Malformed tool_call envelope"`.

Given the same call with the fake configured to return a DSML-hinted
malformed envelope
When `extractLocalToolEnvelopes("Y")` is called
Then an `IllegalStateException` is thrown
And the exception message equals the literal `"Malformed DSML tool envelope"`.

### Scenario K-04: Missing-required-parameter message preserved
Given the fake returns `failures = [ToolEnvelopeFailure(MissingRequiredParameter(tool="web_search", param="query"), "...")]`
When `extractLocalToolEnvelopes("X")` is called
Then an `IllegalArgumentException` is thrown
And the exception message equals the literal `"Tool argument 'query' is required"`.

Given the fake returns `MissingRequiredParameter(tool="web_extract", param="urls")`
When `extractLocalToolEnvelopes("X")` is called
Then an `IllegalArgumentException` is thrown
And the exception message equals the literal `"Tool argument 'urls' is required and must be a non-empty array"`.

### Scenario K-05: Unsupported / unknown / duplicate parameter messages
Given the fake returns `UnknownTool(name="unknown_tool")`
When `extractLocalToolEnvelopes("X")` is called
Then an `IllegalArgumentException` is thrown
And the exception message equals the literal `"Unsupported tool: unknown_tool"`.

Given the fake returns `UnknownParameter(tool="web_search", param="extra")`
When called
Then `IllegalArgumentException` is thrown with message
`"Unknown parameter 'extra' for tool 'web_search'"`.

Given the fake returns `DuplicateParameter(tool="web_search", param="query")`
When called
Then `IllegalArgumentException` is thrown with message
`"Duplicate parameter 'query' for tool 'web_search'"`.

### Scenario K-06: `buildArgumentsJson(toolName, map)` round-trips through coercion
Given the fake's `coerceProviderArguments("web_search", "{\"query\":\"weather in London\"}")`
returns `ToolEnvelopeField("web_search", "{\"query\":\"weather in London\"}", false, [])`
When `ToolEnvelopeParser.buildArgumentsJson("web_search", mapOf("query" to "weather in London"))` is called
Then the return value equals the literal `{"query":"weather in London"}`
And the fake records exactly one `coerceProviderArguments` call.

Given the fake's `coerceProviderArguments("unknown_tool", "{\"custom\":\"value\"}")`
returns `ToolEnvelopeField("unknown_tool", "{\"custom\":\"value\"}", false, [])`
When `ToolEnvelopeParser.buildArgumentsJson("unknown_tool", mapOf("custom" to "value"))` is called
Then the return value equals the literal `{"custom":"value"}`.

### Scenario K-07: `hasLocalToolContract`
Given the fake's `hasLocalToolContract("Use DSML format to call tools. web_search is available.")` returns `true`
When `ToolEnvelopeParser.hasLocalToolContract("Use DSML format to call tools. web_search is available.")` is called
Then the return value is `true`.

Given the fake's `hasLocalToolContract("just chat")` returns `false`
When called
Then the return value is `false`.

---

## Integration / Wiring Scenarios

### Scenario LI-01: `LlamaInferenceServiceImpl` maps successes → `AgentRawToolCall`
Given a stubbed `LlamaSessionManager` whose `streamAssistantResponseWithOptions`
emits a `Final` event with `fullText = "TWO_ENVELOPE_TEXT"`
And `ToolEnvelopeParserPort.parse("TWO_ENVELOPE_TEXT")` returns
`successes = [
   ToolEnvelopeField("web_search", "{\"query\":\"q1\"}", false, []),
   ToolEnvelopeField("web_extract", "{\"urls\":[\"https://a.com\"],\"extract_depth\":\"basic\",\"format\":\"markdown\"}", false, [])
 ]`
When `completeSingleTurn(messages, options, systemPrompt="…", allowToolCall=true, ...)` is awaited
Then the returned `AgentModelTurnResult.toolCalls` has size `2`
And `toolCalls[0].name` equals `"web_search"`
And `toolCalls[0].argumentsJson` equals `{"query":"q1"}`
And `toolCalls[1].name` equals `"web_extract"`
And `toolCalls[1].argumentsJson` equals the literal `{"urls":["https://a.com"],"extract_depth":"basic","format":"markdown"}`
And `finishReason` equals `AgentFinishReason.ToolUse`
And `assistantText` is `null`.

### Scenario LI-02: `AgentTurnTextGate.leakedToolCalls` reads via port
Given `AgentTurnTextGate(allowToolCall=true, port=FakeToolEnvelopeParserPort)` and the port
configured so `parse("Searching now... <tool_call>{\"name\": \"web_search\", \"arguments\": {\"query\": \"weather in London\"}}</tool_call> Please wait.")`
returns `successes = [ToolEnvelopeField("web_search", "{\"query\":\"weather in London\"}", false, [])]`
And the gate has accumulated that text via `onTextToken`
When `leakedToolCalls(nativeToolCalls=emptyList())` is called
Then the returned `List<AgentRawToolCall>` has size `1`
And `result[0].name` equals `"web_search"`
And `result[0].argumentsJson` equals `{"query":"weather in London"}`.

Given the same accumulated text but `nativeToolCalls = listOf(someProviderCall)`
When `leakedToolCalls(nativeToolCalls)` is called
Then the returned list is empty (native calls take precedence).

Given a stream where the gate has buffered only `"Searching"` (no envelope marker)
When `leakedToolCalls(emptyList())` is called
Then the returned list is empty
And the fake records **zero** calls to `parse` (gate must short-circuit when no marker is present, to avoid JNI hops on every chunk).

### Scenario LI-03: Real `RustToolEnvelopeParser` adapter round-trip
Given the JNI library is loaded
And `RustToolEnvelopeParser` is instantiated
When `parse("<tool_call>{\"name\":\"web_search\",\"arguments\":{\"query\":\"android compose\"}}</tool_call>")`
is called against the real adapter
Then `successes.size` equals `1`
And `successes[0].toolName` equals `"web_search"`
And `successes[0].argumentsJson` equals `{"query":"android compose"}`
And no `kotlin.Throwable` is propagated across the call boundary.

Given the real adapter
When `parse("[TOOL_CALLS] [{\"name\":\"web_search\",\"arguments\":{\"query\":\"news\"}},{\"name\":\"search_chat_history\",\"arguments\":{\"query\":\"old chats\"}}]")`
is called
Then `successes.size` equals `2`
And `successes[1].toolName` equals `"search_chat_history"`
And `successes[1].argumentsJson` contains the literal substring `"queries":["old chats"]`.

Given the real adapter
When `parse("<｜｜DSML｜｜tool_calls><｜｜DSML｜｜invoke name=\"unknown_tool\"><｜｜DSML｜｜parameter name=\"query\" string=\"true\">x</｜｜DSML｜｜parameter></｜｜DSML｜｜invoke></｜｜DSML｜｜tool_calls>")`
is called
Then `successes` is empty
And `failures.size` equals `1`
And `failures[0].reason` is the sealed-class instance
`ToolEnvelopeFailureReason.UnknownTool` with `name = "unknown_tool"`.

---

## Cross-Cutting Invariants Verified Across Scenarios

- **Catalog correctness:** Every reference to a tool name in this spec
  (`web_search`, `web_extract`, `attached_image_inspect`,
  `search_chat_history`, `search_chat`, `get_message_context`,
  `search_x_recent`, `search_current_artifact`, `start_artifact`,
  `append_blocks`, `manage_memories`, `generate_image`,
  `generate_video`, `generate_music`) is present in
  `third_party/agent_core/resources/tool_catalog.json` at the time of
  writing (verified by the existing `catalog_loads_14_tools` test).
- **Error-message stability:** Scenarios `K-03` through `K-05` lock
  every Kotlin-observable exception message. These mirror the existing
  assertions in `ToolEnvelopeParserTest` (`robust DSML rejects …` block,
  `TP-04`, `extractLocalToolEnvelope throws on malformed envelope`).
- **Visible-text fidelity:** Scenarios `R-21`, `R-26`, and `R-17` lock
  the `visible_prefix` / `visible_suffix` strings exactly so the
  presentation layer can splice tool-call invocations out of streamed
  assistant text without losing surrounding prose.

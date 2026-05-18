# TOOL-PARSE-001 — test_spec.md
# Test Specification: ToolEnvelopeParser Format Gaps and Multi-Tool Threading

## Test Framework

- **Framework:** JUnit 5 (`org.junit.jupiter.api.*`)
- **Assertion library:** `kotlin.test` (`assertEquals`, `assertNull`, `assertNotNull`, `assertFailsWith`)
- **Parser tests:** pure synchronous — no coroutine runner needed
- **LI scenarios:** `kotlinx.coroutines.test.runTest`

### Run Commands

```bash
# Parser tests only
./gradlew :core:domain:test --tests "*.ToolEnvelopeParserTest"

# Inference wiring tests
./gradlew :feature:inference:test --tests "*.LlamaInferenceServiceImplToolTest"

# Full suite (required before declaring complete)
./gradlew test
```

---

## Scenario Index

| ID | Component | Description |
|---|---|---|
| TP-01 | ToolEnvelopeParser | Mistral single tool call |
| TP-02 | ToolEnvelopeParser | Mistral two tool calls (multi-call) |
| TP-03 | ToolEnvelopeParser | Mistral with visible prefix text |
| TP-04 | ToolEnvelopeParser | Mistral sentinel present but no JSON array → error |
| TP-05 | ToolEnvelopeParser | `parameters` key in wrapped `<tool_call>` → normalized |
| TP-06 | ToolEnvelopeParser | `parameters` key in raw JSON format → normalized |
| TP-07 | ToolEnvelopeParser | `extractRequiredUrls` accepts `parameters`-keyed payload |
| TP-08 | ToolEnvelopeParser | Mistral multi-call with `parameters` key → normalized |
| TP-09 | ToolEnvelopeParser | `<tool_call>` wrapping JSON array single element |
| TP-10 | ToolEnvelopeParser | `<tool_call>` wrapping JSON array two elements |
| TP-11 | ToolEnvelopeParser | `hasLocalToolContract` detects DSML-only system prompt |
| TP-12 | ToolEnvelopeParser | `hasLocalToolContract` detects `invoke name=` pattern |
| TP-13 | ToolEnvelopeParser | `extractLocalToolEnvelopes` returns empty list for plain text |
| TP-14 | ToolEnvelopeParser | `extractLocalToolEnvelopes` returns one element for single call |
| TP-15 | ToolEnvelopeParser | `extractLocalToolEnvelopes` returns two elements for Mistral multi-call |
| TP-16 | ToolEnvelopeParser | `extractLocalToolEnvelope` (compat) returns first or null |
| TP-17 | ToolEnvelopeParser | Mutation defense: Mistral parser not hard-coded for web_search |
| TP-18 | ToolEnvelopeParser | `parameters`-keyed web_extract inside `<tool_call>` → correct urls |
| TP-19 | ToolEnvelopeParser | Mistral multi-call second element has distinct tool name and arguments |
| TP-20 | ToolEnvelopeParser | `<tool_call>` array payload: visiblePrefix and visibleSuffix correct |
| LI-01 | LlamaInferenceServiceImpl | Single envelope → single `AgentRawToolCall` in result |
| LI-02 | LlamaInferenceServiceImpl | Mistral two-envelope response → two `AgentRawToolCall` items |
| LI-03 | LlamaInferenceServiceImpl | Multi-call result has `finishReason = AgentFinishReason.ToolUse` |
| LI-04 | LlamaInferenceServiceImpl | Multi-call result has distinct ids for each `AgentRawToolCall` |
| LI-05 | LlamaInferenceServiceImpl | No tool calls → `finishReason = AgentFinishReason.Stop`, `toolCalls` empty |

---

## Happy Path Scenarios

### TP-01 — Mistral single tool call

```gherkin
Scenario TP-01: extractLocalToolEnvelopes parses Mistral single tool call
  Given text = "[TOOL_CALLS] [{\"name\": \"web_search\", \"arguments\": {\"query\": \"kotlin coroutines\"}}]"
  When  extractLocalToolEnvelopes(text) is called
  Then  result.size == 1
  And   result[0].toolName == "web_search"
  And   result[0].argumentsJson == "{\"query\":\"kotlin coroutines\"}"
  And   result[0].visiblePrefix == ""
  And   result[0].visibleSuffix == ""
```

### TP-02 — Mistral two tool calls

```gherkin
Scenario TP-02: extractLocalToolEnvelopes parses Mistral two-element tool call array
  Given text = "[TOOL_CALLS] [{\"name\": \"web_search\", \"arguments\": {\"query\": \"rust async\"}}, {\"name\": \"web_extract\", \"arguments\": {\"urls\": [\"https://doc.rust-lang.org\"]}}]"
  When  extractLocalToolEnvelopes(text) is called
  Then  result.size == 2
  And   result[0].toolName == "web_search"
  And   result[0].argumentsJson == "{\"query\":\"rust async\"}"
  And   result[1].toolName == "web_extract"
  And   result[1].argumentsJson contains "\"urls\":[\"https://doc.rust-lang.org\"]"
  And   result[1].argumentsJson contains "\"extract_depth\":\"basic\""
```

### TP-03 — Mistral with visible prefix

```gherkin
Scenario TP-03: extractLocalToolEnvelopes captures visible text before [TOOL_CALLS] sentinel
  Given text = "I will search now.\n[TOOL_CALLS] [{\"name\": \"web_search\", \"arguments\": {\"query\": \"android 15\"}}]"
  When  extractLocalToolEnvelopes(text) is called
  Then  result.size == 1
  And   result[0].toolName == "web_search"
  And   result[0].visiblePrefix == "I will search now."
  And   result[0].visibleSuffix == ""
```

### TP-05 — `parameters` key in wrapped `<tool_call>`

```gherkin
Scenario TP-05: extractLocalToolEnvelopes normalizes "parameters" key inside <tool_call>
  Given text = "<tool_call>{\"name\":\"web_search\",\"parameters\":{\"query\":\"llama 3.2 benchmarks\"}}</tool_call>"
  When  extractLocalToolEnvelopes(text) is called
  Then  result.size == 1
  And   result[0].toolName == "web_search"
  And   result[0].argumentsJson == "{\"query\":\"llama 3.2 benchmarks\"}"
```

### TP-06 — `parameters` key in raw JSON format

```gherkin
Scenario TP-06: extractLocalToolEnvelopes normalizes "parameters" key in raw JSON format
  Given text = "{\"name\":\"web_search\",\"parameters\":{\"query\":\"mistral nemo benchmarks\"}}"
  When  extractLocalToolEnvelopes(text) is called
  Then  result.size == 1
  And   result[0].toolName == "web_search"
  And   result[0].argumentsJson == "{\"query\":\"mistral nemo benchmarks\"}"
```

### TP-07 — `extractRequiredUrls` accepts `parameters`-keyed payload

```gherkin
Scenario TP-07: extractRequiredUrls succeeds when urls are nested under "parameters" key
  Given argumentsJson = "{\"parameters\":{\"urls\":[\"https://example.com/a\",\"https://example.com/b\"]}}"
  When  extractRequiredUrls(argumentsJson) is called
  Then  result == listOf("https://example.com/a", "https://example.com/b")
```

### TP-08 — Mistral multi-call with `parameters` key normalized

```gherkin
Scenario TP-08: extractLocalToolEnvelopes normalizes "parameters" to "arguments" in Mistral format
  Given text = "[TOOL_CALLS] [{\"name\":\"web_search\",\"parameters\":{\"query\":\"test\"}}]"
  When  extractLocalToolEnvelopes(text) is called
  Then  result.size == 1
  And   result[0].toolName == "web_search"
  And   result[0].argumentsJson == "{\"query\":\"test\"}"
```

### TP-09 — `<tool_call>` wrapping JSON array single element

```gherkin
Scenario TP-09: extractLocalToolEnvelopes parses <tool_call> containing a JSON array with one object
  Given text = "<tool_call>[{\"name\": \"web_search\", \"arguments\": {\"query\": \"android compose\"}}]</tool_call>"
  When  extractLocalToolEnvelopes(text) is called
  Then  result.size == 1
  And   result[0].toolName == "web_search"
  And   result[0].argumentsJson == "{\"query\":\"android compose\"}"
```

### TP-10 — `<tool_call>` wrapping JSON array two elements

```gherkin
Scenario TP-10: extractLocalToolEnvelopes parses <tool_call> containing a JSON array with two objects
  Given text = "<tool_call>[{\"name\":\"web_search\",\"arguments\":{\"query\":\"q1\"}},{\"name\":\"web_extract\",\"arguments\":{\"urls\":[\"https://a.com\"]}}]</tool_call>"
  When  extractLocalToolEnvelopes(text) is called
  Then  result.size == 2
  And   result[0].toolName == "web_search"
  And   result[0].argumentsJson == "{\"query\":\"q1\"}"
  And   result[1].toolName == "web_extract"
  And   result[1].argumentsJson contains "\"urls\":[\"https://a.com\"]"
```

### TP-11 — `hasLocalToolContract` detects DSML-only system prompt

```gherkin
Scenario TP-11: hasLocalToolContract returns true when system prompt contains "DSML" and a tool name
  Given systemPrompt = "Use DSML format to call tools. web_search is available."
  When  hasLocalToolContract(systemPrompt) is called
  Then  result == true
```

### TP-12 — `hasLocalToolContract` detects `invoke name=`

```gherkin
Scenario TP-12: hasLocalToolContract returns true when system prompt contains "invoke name=" and a tool name
  Given systemPrompt = "Call tools using <invoke name=\"web_search\"> syntax. web_search finds information."
  When  hasLocalToolContract(systemPrompt) is called
  Then  result == true
```

### TP-13 — `extractLocalToolEnvelopes` empty list for plain text

```gherkin
Scenario TP-13: extractLocalToolEnvelopes returns empty list when text has no envelope markers
  Given text = "The weather in London is cloudy today."
  When  extractLocalToolEnvelopes(text) is called
  Then  result == emptyList<LocalToolEnvelope>()
```

### TP-14 — `extractLocalToolEnvelopes` single element for standard format

```gherkin
Scenario TP-14: extractLocalToolEnvelopes returns one-element list for standard <tool_call> JSON object
  Given text = "<tool_call>{\"name\":\"web_search\",\"arguments\":{\"query\":\"pocket crew android\"}}</tool_call>"
  When  extractLocalToolEnvelopes(text) is called
  Then  result.size == 1
  And   result[0].toolName == "web_search"
  And   result[0].argumentsJson == "{\"query\":\"pocket crew android\"}"
```

### TP-15 — `extractLocalToolEnvelopes` two elements for Mistral multi-call

```gherkin
Scenario TP-15: extractLocalToolEnvelopes returns two-element list for Mistral two-call array
  Given text = "[TOOL_CALLS] [{\"name\":\"web_search\",\"arguments\":{\"query\":\"q1\"}},{\"name\":\"web_search\",\"arguments\":{\"query\":\"q2\"}}]"
  When  extractLocalToolEnvelopes(text) is called
  Then  result.size == 2
  And   result[0].argumentsJson == "{\"query\":\"q1\"}"
  And   result[1].argumentsJson == "{\"query\":\"q2\"}"
```

### TP-16 — `extractLocalToolEnvelope` backward-compat overload

```gherkin
Scenario TP-16: extractLocalToolEnvelope (singular) returns first element from Mistral multi-call text
  Given text = "[TOOL_CALLS] [{\"name\":\"web_search\",\"arguments\":{\"query\":\"first\"}},{\"name\":\"web_search\",\"arguments\":{\"query\":\"second\"}}]"
  When  extractLocalToolEnvelope(text) is called
  Then  result is not null
  And   result.toolName == "web_search"
  And   result.argumentsJson == "{\"query\":\"first\"}"
```

### TP-18 — `parameters`-keyed web_extract inside `<tool_call>`

```gherkin
Scenario TP-18: extractLocalToolEnvelopes parses web_extract with parameters key and extracts urls correctly
  Given text = "<tool_call>{\"name\":\"web_extract\",\"parameters\":{\"urls\":[\"https://example.com\"]}}</tool_call>"
  When  extractLocalToolEnvelopes(text) is called
  Then  result.size == 1
  And   result[0].toolName == "web_extract"
  And   result[0].argumentsJson contains "\"urls\":[\"https://example.com\"]"
  And   result[0].argumentsJson contains "\"extract_depth\":\"basic\""
  And   result[0].argumentsJson contains "\"format\":\"markdown\""
```

### TP-19 — Mistral multi-call second element correctness

```gherkin
Scenario TP-19: extractLocalToolEnvelopes Mistral multi-call second element has its own tool name and arguments
  Given text = "[TOOL_CALLS] [{\"name\":\"web_search\",\"arguments\":{\"query\":\"news\"}},{\"name\":\"search_chat_history\",\"arguments\":{\"query\":\"old chats\"}}]"
  When  extractLocalToolEnvelopes(text) is called
  Then  result.size == 2
  And   result[1].toolName == "search_chat_history"
  And   result[1].argumentsJson contains "\"queries\":[\"old chats\"]"
```

### TP-20 — `<tool_call>` array payload: visiblePrefix and visibleSuffix

```gherkin
Scenario TP-20: extractLocalToolEnvelopes captures visible text around <tool_call> array payload
  Given text = "Let me look that up. <tool_call>[{\"name\":\"web_search\",\"arguments\":{\"query\":\"android arch\"}}]</tool_call> Done."
  When  extractLocalToolEnvelopes(text) is called
  Then  result.size == 1
  And   result[0].visiblePrefix == "Let me look that up. "
  And   result[0].visibleSuffix == " Done."
```

---

## Error and Edge Case Scenarios

### TP-04 — Mistral sentinel present but no JSON array

```gherkin
Scenario TP-04: extractLocalToolEnvelopes throws when [TOOL_CALLS] sentinel is present but array is absent
  Given text = "[TOOL_CALLS] not_json_here"
  When  extractLocalToolEnvelopes(text) is called
  Then  an IllegalStateException is thrown
  And   the exception message == "Malformed tool_call envelope"
```

---

## Mutation-Defense Scenario

### TP-17 — Mistral parser not hard-coded for `web_search` only

```gherkin
Scenario TP-17 (mutation defense): extractLocalToolEnvelopes with Mistral format for search_chat_history
  # Fails against a lazy implementation that pattern-matches only "web_search" in parseMistralToolCalls.
  Given text = "[TOOL_CALLS] [{\"name\":\"search_chat_history\",\"arguments\":{\"query\":\"important meeting\"}}]"
  When  extractLocalToolEnvelopes(text) is called
  Then  result.size == 1
  And   result[0].toolName == "search_chat_history"
  And   result[0].argumentsJson contains "\"queries\":[\"important meeting\"]"
  # A lazy impl hardcoding toolName="web_search" yields result[0].toolName=="web_search" → fails assertion.
```

---

## LlamaInferenceServiceImpl Scenarios

### LI-01 — Single envelope → single `AgentRawToolCall`

```gherkin
Scenario LI-01: completeSingleTurn maps single LocalToolEnvelope to one AgentRawToolCall
  Given allowToolCall = true
  And   the buffered generation text = "<tool_call>{\"name\":\"web_search\",\"arguments\":{\"query\":\"test\"}}</tool_call>"
  When  completeSingleTurn is called
  Then  result.toolCalls.size == 1
  And   result.toolCalls[0].name == "web_search"
  And   result.toolCalls[0].argumentsJson == "{\"query\":\"test\"}"
  And   result.assistantText == null
```

### LI-02 — Mistral two-envelope → two `AgentRawToolCall` items

```gherkin
Scenario LI-02: completeSingleTurn maps two Mistral LocalToolEnvelopes to two AgentRawToolCalls
  Given allowToolCall = true
  And   the buffered generation text = "[TOOL_CALLS] [{\"name\":\"web_search\",\"arguments\":{\"query\":\"q1\"}},{\"name\":\"web_extract\",\"arguments\":{\"urls\":[\"https://a.com\"]}}]"
  When  completeSingleTurn is called
  Then  result.toolCalls.size == 2
  And   result.toolCalls[0].name == "web_search"
  And   result.toolCalls[1].name == "web_extract"
```

### LI-03 — Multi-call finishReason is ToolUse

```gherkin
Scenario LI-03: completeSingleTurn sets finishReason to ToolUse when envelopes are present
  Given allowToolCall = true
  And   the buffered generation text = "[TOOL_CALLS] [{\"name\":\"web_search\",\"arguments\":{\"query\":\"a\"}},{\"name\":\"web_search\",\"arguments\":{\"query\":\"b\"}}]"
  When  completeSingleTurn is called
  Then  result.finishReason == AgentFinishReason.ToolUse
```

### LI-04 — Multi-call ids are distinct

```gherkin
Scenario LI-04: completeSingleTurn assigns distinct ids to each AgentRawToolCall in multi-call response
  Given allowToolCall = true
  And   the buffered generation text = "[TOOL_CALLS] [{\"name\":\"web_search\",\"arguments\":{\"query\":\"a\"}},{\"name\":\"web_search\",\"arguments\":{\"query\":\"b\"}}]"
  When  completeSingleTurn is called
  Then  result.toolCalls[0].id != result.toolCalls[1].id
  And   result.toolCalls[0].id is not null
  And   result.toolCalls[1].id is not null
```

### LI-05 — No tool calls → Stop reason and empty list

```gherkin
Scenario LI-05: completeSingleTurn returns Stop reason and empty toolCalls when text has no envelope
  Given allowToolCall = true
  And   the buffered generation text = "The answer is 42."
  When  completeSingleTurn is called
  Then  result.toolCalls == emptyList<AgentRawToolCall>()
  And   result.finishReason == AgentFinishReason.Stop
  And   result.assistantText == "The answer is 42."
```

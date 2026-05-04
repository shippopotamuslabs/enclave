# Test Spec: X Search Tool (search_x_recent)

## Feature: X Search Tool Definition

### Scenario: X_SEARCH is registered in ALL_TOOLS

```gherkin
Given the ToolDefinition companion object
When I inspect ALL_TOOLS
Then the list contains a tool with name "search_x_recent"
And fromName("search_x_recent") returns ToolDefinition.X_SEARCH
```

**Test class**: `ToolDefinitionXSearchTest`
**Location**: `core/domain/src/test/kotlin/.../domain/model/inference/ToolDefinitionXSearchTest.kt`

### Scenario: X_SEARCH schema is well-formed

```gherkin
Given ToolDefinition.X_SEARCH
When I access its schema
Then it has a required "query" property of type "string"
```

### Scenario: X_SEARCH parameters encode and decode round-trip

```gherkin
Given XSearchParams(query = "test query")
When I encode with X_SEARCH.encodeArguments() and decode with X_SEARCH.decodeArguments()
Then the decoded params match the original
```

---

## Feature: X Search Tool Executor

### Scenario: Successful search execution

```gherkin
Given X Search is enabled in settings
And XSearchRepository returns 3 posts for query "AI news"
When the executor receives a ToolCallRequest with toolName "search_x_recent" and argumentsJson '{"query":"AI news"}'
Then the result's resultJson contains normalized JSON with 3 posts
And each post has fields: id, text, author_handle, created_at, url, metrics
```

**Test class**: `XSearchToolExecutorImplTest`
**Location**: `core/data/src/test/kotlin/.../core/data/repository/XSearchToolExecutorImplTest.kt`

### Scenario: Execution fails when X Search is disabled

```gherkin
Given X Search is disabled in settings
When the executor receives a ToolCallRequest with toolName "search_x_recent"
Then an IllegalStateException is thrown with message containing "X Search is not enabled"
```

### Scenario: Execution fails for unsupported tool name

```gherkin
Given X Search is enabled in settings
When the executor receives a ToolCallRequest with toolName "web_search"
Then an IllegalArgumentException is thrown
```

### Scenario: Missing query argument is rejected

```gherkin
Given X Search is enabled in settings
When the executor receives a ToolCallRequest with argumentsJson "{}"
Then a serialization exception is thrown
```

---

## Feature: CompositeToolExecutor X Search Routing

### Scenario: Routes search_x_recent to XSearchToolExecutorImpl

```gherkin
Given a CompositeToolExecutor with all executors registered including xSearchToolExecutor
When a ToolCallRequest with toolName "search_x_recent" is executed
Then XSearchToolExecutorImpl.execute is called
```

**Test class**: `CompositeToolExecutorTest` (existing, add case)
**Location**: `core/data/src/test/kotlin/.../core/data/repository/CompositeToolExecutorTest.kt`

### Scenario: All ToolDefinition.ALL_TOOLS are supported (regression guard)

```gherkin
Given a CompositeToolExecutor
When I check every tool name in ToolDefinition.ALL_TOOLS
Then no "Unsupported tool" exception is thrown for any tool
```

---

## Feature: X Search Settings Persistence (FakeSettingsRepository)

### Scenario: Default X Search state

```gherkin
Given a fresh SettingsData
Then xSearchEnabled is false
And xApiKeyPresent is false
```

**Test class**: `FakeSettingsRepositoryXSearchTest`
**Location**: `core/domain/src/test/kotlin/.../domain/usecase/FakeSettingsRepositoryXSearchTest.kt`

### Scenario: Enable X Search

```gherkin
Given X Search is disabled
When updateXSearchEnabled(true) is called
Then settingsFlow emits SettingsData with xSearchEnabled = true
```

### Scenario: Save X API key

```gherkin
Given no X API key is stored
When saveXApiKey("xai-test-key-123") is called
Then settingsFlow emits SettingsData with xApiKeyPresent = true
```

### Scenario: Clear X API key

```gherkin
Given an X API key is stored
When clearXApiKey() is called
Then settingsFlow emits SettingsData with xApiKeyPresent = false
```

---

## Feature: Chat Inference Request Preparer X Search Integration

### Scenario: X Search tool included when enabled

```gherkin
Given settings with xSearchEnabled = true
When ChatInferenceRequestPreparer is invoked
Then availableTools contains ToolDefinition.X_SEARCH
And the system prompt contains "search_x_recent"
```

**Test class**: `ChatInferenceRequestPreparerXSearchTest`
**Location**: `core/domain/src/test/kotlin/.../domain/usecase/chat/ChatInferenceRequestPreparerXSearchTest.kt`

### Scenario: X Search tool excluded when disabled

```gherkin
Given settings with xSearchEnabled = false
When ChatInferenceRequestPreparer is invoked
Then availableTools does NOT contain ToolDefinition.X_SEARCH
And the system prompt does NOT contain "search_x_recent"
```

### Scenario: X Search and Web Search are independent

```gherkin
Given settings with searchEnabled = true and xSearchEnabled = true
When ChatInferenceRequestPreparer is invoked
Then availableTools contains BOTH ToolDefinition.WEB_SEARCH AND ToolDefinition.X_SEARCH
```

---

## Feature: SearchToolPromptComposer X Search Contract

### Scenario: X search contract included when flag is true

```gherkin
Given includeXSearchTool = true
When compose() is called
Then the output contains "search_x_recent"
And the output contains "X (TWITTER) SEARCH MANDATE"
```

**Test class**: `SearchToolPromptComposerXSearchTest`
**Location**: `core/domain/src/test/kotlin/.../domain/usecase/chat/SearchToolPromptComposerXSearchTest.kt`

### Scenario: X search contract excluded when flag is false

```gherkin
Given includeXSearchTool = false
When compose() is called
Then the output does NOT contain "search_x_recent"
```

### Scenario: X search contract coexists with web search contract

```gherkin
Given includeSearchTool = true AND includeXSearchTool = true
When compose() is called
Then the output contains BOTH "web_search" AND "search_x_recent"
```

---

## Feature: ToolEnvelopeParser X Search Support

### Scenario: requireSupportedTool accepts search_x_recent

```gherkin
When requireSupportedTool("search_x_recent") is called
Then no exception is thrown
```

**Test class**: `ToolEnvelopeParserXSearchTest`
**Location**: `core/domain/src/test/kotlin/.../domain/util/ToolEnvelopeParserXSearchTest.kt`

### Scenario: Local tool envelope extraction for X search

```gherkin
Given a local model response containing '<tool_call>{"name":"search_x_recent","arguments":{"query":"AI trends"}}</tool_call>'
When extractLocalToolEnvelope is called
Then the envelope has toolName "search_x_recent"
And argumentsJson contains "AI trends"
```

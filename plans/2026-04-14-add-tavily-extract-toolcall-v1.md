# Plan: Add Tavily Extract Tool Call

## Objective

Add a new `tavily_extract` tool call that allows LLMs to use Tavily's `/extract` endpoint to scrape cleaned/parsed content from a list of URLs. The tool should:
1. Accept a list of URLs (from prior search results), with optional `extract_depth` and `format` parameters
2. Default `format` to "markdown"
3. Emit one "extract" event per URL during execution
4. Add an `extracted` boolean field to `TavilySourceEntity` so sources can be flagged as "read"
5. Register the new tool in all provider mappers, the composite executor, local toolset, and the prompt composer

## Implementation Plan

### Phase 1: Domain Layer — Tool Definition & Models

- [ ] **1.1** Add `TAVILY_EXTRACT` companion constant to `ToolDefinition.kt` (`core/domain/src/main/kotlin/.../domain/model/inference/ToolDefinition.kt:8-40`)
  - Name: `tavily_extract`
  - Description: "Extract cleaned content from a list of URLs. Use this to read the full content of web pages found via tavily_web_search."
  - Parameters JSON schema with:
    - `urls`: array of strings (required) — the URLs to extract content from
    - `extract_depth`: string, enum ["basic", "advanced"], default "basic" (optional)
    - `format`: string, enum ["markdown", "text"], default "markdown" (optional)
  - Rationale: The model needs the full schema to construct valid tool calls. The `urls` parameter accepts URLs that were returned by a prior `tavily_web_search` call.

- [ ] **1.2** Add `extracted: Boolean = false` field to `TavilySource` domain model (`core/domain/src/main/kotlin/.../domain/model/chat/TavilySource.kt:15-22`)
  - Rationale: This tracks whether a source has been "read" via extraction. The UI will later use this to flag sources as "read."

- [ ] **1.3** Add a new `ToolExecutionEvent.Extracting` event subclass to `ToolExecutionEvent.kt` (`core/domain/src/main/kotlin/.../domain/model/inference/ToolExecutionEvent.kt:10-31`)
  - Fields: `eventId: String`, `url: String`, `chatId: ChatId?`, `userMessageId: MessageId?`
  - Rationale: The UI needs per-URL extraction events to show "Extracting details for [url]" indicators. One event per URL.

- [ ] **1.4** Update `ToolEnvelopeParser.requireSupportedTool()` to accept `tavily_extract` (`core/domain/src/main/kotlin/.../domain/util/ToolEnvelopeParser.kt:23-27`)
  - Add `toolName == ToolDefinition.TAVILY_EXTRACT.name` to the require check

- [ ] **1.5** Add `buildExtractArgumentsJson(urls: List<String>, extractDepth: String?, format: String?)` method to `ToolEnvelopeParser.kt`
  - Builds JSON: `{"urls":["..."], "extract_depth":"basic", "format":"markdown"}`
  - Rationale: Local models need the same argument serialization format as other tools.

- [ ] **1.6** Add `extractRequiredUrls(argumentsJson: String): List<String>` method to `ToolEnvelopeParser.kt`
  - Parses the `urls` array from the arguments JSON
  - Rationale: The extract executor needs to pull the URL list from the tool call arguments.

### Phase 2: Data Layer — Repository & Entity

- [ ] **2.1** Add `extracted` column to `TavilySourceEntity` (`core/data/src/main/kotlin/.../core/data/local/TavilySourceEntity.kt:24-38`)
  - Add `@ColumnInfo(name = "extracted", defaultValue = "0") val extracted: Boolean = false` field
  - Rationale: Per the Schema Lock contract (DATA_LAYER_RULES.md §1), this is not a transient UI field — it's a persistent boolean tracking whether a source was read by the extract tool. Explicit developer authorization is given in the task requirements.

- [ ] **2.2** Bump `PocketCrewDatabase` version from 1 to 2 (`core/data/src/main/kotlin/.../core/data/local/PocketCrewDatabase.kt:20`)
  - Add a `Migration(1, 2)` that runs `ALTER TABLE tavily_source ADD COLUMN extracted INTEGER NOT NULL DEFAULT 0`
  - Since `fallbackToDestructiveMigration(dropAllTables = false)` is already configured, a proper migration is preferred but a fallback exists. Add the migration explicitly.
  - Rationale: Room requires a version bump when schema changes. An ALTER TABLE migration preserves existing data.

- [ ] **2.3** Add `markExtracted(url: String, messageId: MessageId)` method to `TavilySourceDao` (`core/data/src/main/kotlin/.../core/data/local/TavilySourceDao.kt:10-16`)
  - SQL: `UPDATE tavily_source SET extracted = 1 WHERE url = :url AND message_id = :messageId`
  - Rationale: When the extract tool successfully processes a URL, we mark the corresponding source as "extracted" (read).

- [ ] **2.4** Add `extract` method to `TavilySearchRepository` (`core/data/src/main/kotlin/.../core/data/repository/TavilySearchRepository.kt:14-70`)
  - Method signature: `fun extract(urls: List<String>, extractDepth: String = "basic", format: String = "markdown"): String`
  - POST to `https://api.tavily.com/extract` with request body: `{"urls": [...], "api_key": "...", "extract_depth": "basic"|"advanced", "format": "markdown"|"text"}`
  - Map the response to a consistent JSON shape: `{"results": [{"url": "...", "raw_content": "..."}]}`
  - Reuse the same `OkHttpClient` and `ApiKeyManager.TAVILY_SEARCH_ALIAS` (same Tavily API key)
  - Add `EXTRACT_URL` constant alongside `SEARCH_URL`
  - Rationale: The extract endpoint is separate from search and returns different data (cleaned content per URL, not ranked search results). Same API key is used.

- [ ] **2.5** Create `ExtractToolExecutor` class (`core/data/src/main/kotlin/.../core/data/repository/ExtractToolExecutor.kt`)
  - Implements `ToolExecutorPort`
  - Constructor injected with: `LoggingPort`, `SettingsRepository`, `TavilySearchRepository`, `TavilySourceDao`, `ToolExecutionEventBus`
  - `execute()` method:
    1. Validate tool name is `tavily_extract`
    2. Parse `urls` array, `extract_depth` (optional, default "basic"), and `format` (optional, default "markdown") from `argumentsJson`
    3. Check `searchEnabled` setting (same gate as search)
    4. For each URL, emit `ToolExecutionEvent.Extracting` event via eventBus
    5. Call `tavilySearchRepository.extract(urls, extractDepth, format)`
    6. For each successfully extracted URL, call `tavilySourceDao.markExtracted(url, messageId)` where `messageId` comes from `request.userMessageId` (or the assistant message context)
    7. Return `ToolExecutionResult` with the mapped result JSON
  - Rationale: Follows the same pattern as `SearchToolExecutorImpl` but handles the extract-specific behavior (per-URL events, source marking).

- [ ] **2.6** Add `extracted` field mapping in the TavilySource entity-to-domain mapper
  - Wherever `TavilySourceEntity` is mapped to `TavilySource`, include the new `extracted` field
  - Rationale: The domain model now carries the extracted flag so the presentation layer can use it later.

### Phase 3: Composite Executor & DI Wiring

- [ ] **3.1** Add `ExtractToolExecutor` to `CompositeToolExecutor` routing (`core/data/src/main/kotlin/.../core/data/repository/CompositeToolExecutor.kt:21-69`)
  - Inject `ExtractToolExecutor` as a constructor parameter
  - Add route: `ToolDefinition.TAVILY_EXTRACT.name -> extractToolExecutor.execute(request)`
  - Update KDoc routes list to include the new tool
  - Rationale: Every new tool must be routed through the composite executor.

- [ ] **3.2** Register `ExtractToolExecutor` in Hilt DI module (`core/data/src/main/kotlin/.../core/data/DataModule.kt`)
  - Ensure the new executor is injectable. Since it's `@Singleton` and constructor-injected with Hilt `@Inject`, no explicit `@Provides` is needed unless the `ToolExecutorPort` binding requires one. Verify and add if needed.

### Phase 4: Tool Registration — Available Tools & Prompt Composer

- [ ] **4.1** Add `ToolDefinition.TAVILY_EXTRACT` to `availableTools` list in `ChatInferenceRequestPreparer` (`core/domain/src/main/kotlin/.../domain/usecase/chat/ChatInferenceRequestPreparer.kt:77-80`)
  - Add: `if (searchEnabled) add(ToolDefinition.TAVILY_EXTRACT)` (extract is available when search is enabled)
  - Rationale: The extract tool depends on search being enabled since it uses the same Tavily API key and setting gate.

- [ ] **4.2** Update `SearchToolPromptComposer` to include the extract tool contract (`core/domain/src/main/kotlin/.../domain/usecase/chat/SearchToolPromptComposer.kt`)
  - Add `includeExtractTool` parameter to `compose()` and `localToolContract()` (defaults to same as `includeSearchTool`)
  - Add `EXTRACT_TOOL_CONTRACT` prompt block describing when and how to use `tavily_extract`
  - Add `LITE_RT_EXTRACT_CONTRACT` prompt block for local models
  - The contract should instruct the model: "After calling tavily_web_search, if you need the full content of any result, call tavily_extract with the URLs. Defaults: format=markdown, extract_depth=basic."
  - Rationale: Local models need explicit prompt instructions to use the extract tool. SDK-native providers get the tool definition metadata instead.

- [ ] **4.3** Update `ConversationSignature` in `ConversationManagerImpl` to include `hasExtractTool: Boolean` (`feature/inference/src/main/kotlin/.../feature/inference/ConversationManagerImpl.kt:67-77`)
  - Add `val hasExtractTool: Boolean` field
  - Check `options?.availableTools?.contains(ToolDefinition.TAVILY_EXTRACT) == true` alongside the existing checks at line 159-160
  - Rationale: If the extract tool availability changes, the conversation must be recreated.

### Phase 5: Request Mappers — All Providers

Each request mapper needs to register the new tool so the LLM sees it in its available tools. The existing `toXxxTool()` extension functions on `ToolDefinition` already handle parameter JSON mapping generically, so adding the new definition to `availableTools` is sufficient for most providers.

- [ ] **5.1** Verify `AnthropicRequestMapper.toAnthropicTool()` handles the new tool (`feature/inference/src/main/kotlin/.../feature/inference/AnthropicRequestMapper.kt:65-76`)
  - The mapper iterates `options.availableTools` generically — no code change needed beyond ensuring `TAVILY_EXTRACT` is in the list.

- [ ] **5.2** Verify `GoogleRequestMapper.toGoogleTool()` handles the new tool (`feature/inference/src/main/kotlin/.../feature/inference/GoogleRequestMapper.kt:124-141`)
  - Same — generic iteration over `availableTools`. No code change needed.

- [ ] **5.3** Verify `OpenAiRequestMapper.toChatCompletionTool()` and `toResponsesTool()` handle the new tool (`feature/inference/src/main/kotlin/.../feature/inference/OpenAiRequestMapper.kt:127-143`)
  - Generic iteration. No code change needed.

- [ ] **5.4** Verify `OpenRouterRequestMapper` handles the new tool (`feature/inference/src/main/kotlin/.../feature/inference/OpenRouterRequestMapper.kt:187-203`)
  - Generic iteration with one hardcoded `required` list (`listOf("query")`). **This needs fixing** — the `required` should come from `requiredArguments()` rather than being hardcoded for search only. Update to use `requiredArguments()` for consistency with the other mappers.

- [ ] **5.5** Verify `XaiRequestMapper` uses generic tool mapping (`feature/inference/src/main/kotlin/.../feature/inference/XaiRequestMapper.kt`)
  - Confirm it iterates `options.availableTools` generically. No code change expected.

### Phase 6: Local Model Toolset — LiteRT Native

- [ ] **6.1** Add `LocalExtractToolset` inner class to `ConversationManagerImpl` (`feature/inference/src/main/kotlin/.../feature/inference/ConversationManagerImpl.kt:285-330`)
  - Follow the `LocalSearchToolset` pattern
  - Method: `tavily_extract(urls: String, extract_depth: String, format: String): String`
  - LiteRT `@Tool` annotation with description
  - `@ToolParam` annotations for each parameter
  - Note: LiteRT `@Tool` methods have flat parameters, so `urls` will be passed as a comma-separated or JSON array string
  - Construct `ToolCallRequest` with the JSON arguments
  - Delegate to `executeToolSafely(request)`

- [ ] **6.2** Register `LocalExtractToolset` in `ConversationConfig.tools` builder (`feature/inference/src/main/kotlin/.../feature/inference/ConversationManagerImpl.kt:219-244`)
  - Add: `if (executor != null && hasExtractTool) add(tool(LocalExtractToolset(...)))`
  - Rationale: LiteRT conversations need the tool registered natively for the model to use it.

### Phase 7: UI Event Handling — ChatViewModel

- [ ] **7.1** Add `EXTRACT` value to `ToolCallBannerKind` enum (`feature/chat/src/main/kotlin/.../feature/chat/ChatModels.kt:30-33`)
  - Rationale: The chat UI needs a distinct banner type for extraction progress.

- [ ] **7.2** Handle `ToolExecutionEvent.Extracting` in `ChatViewModel` tool event observer (`feature/chat/src/main/kotlin/.../feature/chat/ChatViewModel.kt:147-195`)
  - When `Extracting` event is received, update `_activeToolCallBanner` to show "Extracting details for [url]"
  - Use the `ToolCallBannerKind.EXTRACT` kind
  - Rationale: The user needs visual feedback when the model is extracting content from URLs.

- [ ] **7.3** Handle `tavily_extract` in the `ToolExecutionEvent.Started` handler in `ChatViewModel`
  - Add case for `ToolDefinition.TAVILY_EXTRACT.name` → `ToolCallBannerKind.EXTRACT` with label "Extracting content"
  - Rationale: Consistent with how other tool calls show their active state.

### Phase 8: Tests

- [ ] **8.1** Add `TavilySearchRepositoryTest` cases for `extract()` (`core/data/src/test/kotlin/.../core/data/repository/TavilySearchRepositoryTest.kt`)
  - Test: extract posts to /extract endpoint with correct body
  - Test: extract returns mapped results
  - Test: extract throws when API key is missing

- [ ] **8.2** Add `ExtractToolExecutorTest` (`core/data/src/test/kotlin/.../core/data/repository/ExtractToolExecutorTest.kt`)
  - Test: execute delegates to TavilySearchRepository.extract
  - Test: execute emits Extracting events per URL
  - Test: execute marks sources as extracted in DAO
  - Test: execute throws when search is disabled

- [ ] **8.3** Update `CompositeToolExecutorTest` (`core/data/src/test/kotlin/.../core/data/repository/CompositeToolExecutorTest.kt`)
  - Test: tavily_extract routes to ExtractToolExecutor
  - Test: unsupported tool names still throw

- [ ] **8.4** Update `ChatInferenceRequestPreparerTest` (`core/domain/src/test/kotlin/.../domain/usecase/chat/ChatInferenceRequestPreparerTest.kt`)
  - Test: when searchEnabled, both TAVILY_WEB_SEARCH and TAVILY_EXTRACT are in availableTools

- [ ] **8.5** Update `GenerateChatResponseUseCaseSearchToolTest` (`core/domain/src/test/kotlin/.../domain/usecase/chat/GenerateChatResponseUseCaseSearchToolTest.kt`)
  - Add test verifying TAVILY_EXTRACT appears in availableTools when search is enabled

- [ ] **8.6** Add Room migration test for version 1→2 (`core/data/src/test/kotlin/.../core/data/local/PocketCrewDatabaseMigrationTest.kt`)
  - Test: migration adds `extracted` column with default 0
  - Test: existing data is preserved

- [ ] **8.7** Update `ToolEnvelopeParserTest` for extract tool
  - Test: buildExtractArgumentsJson serializes correctly
  - Test: extractRequiredUrls parses correctly
  - Test: requireSupportedTool accepts tavily_extract

- [ ] **8.8** Update request mapper tests to verify `TAVILY_EXTRACT` serializes correctly
  - AnthropicRequestMapperTest, GoogleRequestMapperTest, OpenAiRequestMapperTest, OpenRouterRequestMapperTest, XaiRequestMapperTest — add test cases with `TAVILY_EXTRACT` definition

## Verification Criteria

- [ ] `./gradlew :domain:assemble` passes with no Android/Compose deps in domain
- [ ] `./gradlew :data:assemble` passes
- [ ] `./gradlew :app:kspDebugKotlin` passes (Room schema compiles)
- [ ] `./gradlew :app:assembleDebug` passes
- [ ] `./gradlew testDebugUnitTest` passes
- [ ] `./gradlew ktlintCheck` passes
- [ ] The Tavily extract endpoint is called with correct URL, extract_depth, and format parameters
- [ ] One `ToolExecutionEvent.Extracting` event is emitted per URL in the extraction request
- [ ] Sources that have been extracted are marked `extracted = true` in the database
- [ ] All providers (OpenAI, Anthropic, Google, OpenRouter, XAI, LiteRT) correctly receive and route the `tavily_extract` tool definition

## Potential Risks and Mitigations

1. **Room schema migration on existing users' databases**
   Mitigation: Use `ALTER TABLE` migration (1→2) with `DEFAULT 0` so existing rows get the boolean false value. The `fallbackToDestructiveMigration(dropAllTables = false)` is already set as a safety net.

2. **LiteRT @Tool method parameter limitations with arrays**
   Mitigation: LiteRT `@Tool` methods use flat parameters. The `urls` parameter should be passed as a single string (JSON array or comma-separated). The `ExtractToolExecutor` will parse it from the arguments JSON. If LiteRT can't handle array types natively, document it as a JSON-serialized string parameter.

3. **Per-URL extraction events may emit too rapidly for the UI**
   Mitigation: The `ToolExecutionEventBus` uses `DROP_OLDEST` buffer overflow strategy. The ChatViewModel should batch or debounce extract events if they cause UI jank, though initial implementation can emit per-URL since extractions are sequential within the single HTTP call.

4. **Tavily /extract API rate limits or failures on large URL lists**
   Mitigation: The existing error handling pattern (throw IOException, caught by CompositeToolExecutor, emit Finished with error) is sufficient. Consider future enhancement to batch URLs if Tavily imposes limits.

5. **OpenRouterRequestMapper hardcoded `required` list**
   Mitigation: Update to use `requiredArguments()` method instead of the hardcoded `listOf("query")`. This is a pre-existing bug that will break when `tavily_extract` is added with `required: ["urls"]`.

## Alternative Approaches

1. **Single TavilyToolExecutor handling both search and extract**: Instead of creating a separate `ExtractToolExecutor`, extend `SearchToolExecutorImpl` to handle both tools. 
   Trade-off: Violates single-responsity principle but reduces DI wiring complexity. Rejected — separate executors are cleaner and match the existing pattern where each tool has its own executor.

2. **Extract as a sub-step of search (automatic extraction)**: After a search, automatically extract the top-N URLs.
   Trade-off: Reduces latency for the common case but adds unnecessary API calls when the model doesn't need full content. Rejected — the model should decide when extraction is needed.

3. **Separate Tavily API key for extract**: Use a different API key alias for the extract endpoint.
   Trade-off: More flexible but adds configuration burden. Rejected — the same Tavily API key works for both search and extract endpoints.
# Chat Image Inspection Tool v1

## Summary
Implement a hybrid image-understanding flow for chat that keeps a single baseline cached analysis for context/history, but adds an on-demand tool that can re-read the relevant image whenever the model needs more detail.

Target plan artifact path when execution is allowed: `plans/chat-image-inspection-tool.md`

Success criteria:
- A user can ask a follow-up question about a previously attached image without re-attaching it.
- The answering model can inspect the image again during that follow-up turn instead of relying only on the original cached description.
- Existing first-turn image behavior remains intact.
- Older image-bearing messages still contribute lightweight context during history rehydration.
- No schema migration is required for v1.

## Implementation Changes
### 1. Keep baseline image analysis, but narrow its role
- In `GenerateChatResponseUseCase`, keep the existing pre-send image analysis path for the current user message when `content.imageUri != null`.
- Continue storing one baseline analysis in `message_vision_analysis` keyed by `(user_message_id, image_uri)`.
- Treat this stored analysis as:
  - first-turn context for the same message
  - lightweight history content for future rehydration
- Do not try to solve follow-up questions by making the baseline description “hyper-detailed”.
- Do not store repeated follow-up-specific analyses in `message_vision_analysis` for v1.

### 2. Add a first-class image inspection tool
- Add a new `ToolDefinition` entry named `attached_image_inspect`.
- Tool schema for v1:
  - `question: string`
- Tool semantics:
  - The model asks a question about the image.
  - The app resolves which image is in scope.
  - The app re-runs vision inference against that image using the tool question.
  - The tool returns structured JSON for the model to continue answering.
- Do not expose image IDs, message IDs, or image selection arguments to the model in v1.
- Do not support multi-image comparison in v1.

### 3. Resolve image scope deterministically in the app layer
Add repository support so the image tool does not guess which image to inspect.

Resolution rules for v1:
1. If the current user message has `imageUri`, inspect that image.
2. Otherwise, inspect the most recent earlier user message in the same chat with a non-null `imageUri`.
3. Ignore assistant/system messages for image resolution.
4. If no image-bearing user message exists, tool execution returns a controlled error payload.

Required repository capability:
- Add a `MessageRepository` method that resolves the latest image-bearing user message for a chat, with the current message preferred when applicable.
- This method should return enough data to execute the tool cleanly:
  - resolved user message ID
  - resolved image URI
  - original message text if useful for logging/debugging
- Implement the query from the existing `message` table using `chat_id`, `role`, `image_uri`, and chronological ordering.
- Prefer ordering by `created_at DESC`, with a stable fallback if needed.

### 4. Generalize tool execution beyond search-only logic
The current codebase has tool support, but some parts are still effectively search-specific. Refactor the tool path so both search and image inspection are supported cleanly.

Required changes:
- Extend tool availability assembly in `GenerateChatResponseUseCase`.
- Compute two independent booleans per turn:
  - `searchToolEnabled`
  - `imageInspectToolEnabled`
- `searchToolEnabled` continues to depend on settings.
- `imageInspectToolEnabled` depends only on whether an in-scope image exists for the turn.
- `GenerationOptions.availableTools` should contain:
  - `TAVILY_WEB_SEARCH` when search is enabled
  - `ATTACHED_IMAGE_INSPECT` when an in-scope image exists
  - both tools when both conditions are true

For local-model tool prompting:
- Replace the search-only local contract with a generic local tool contract.
- The contract must explicitly show both envelope shapes:
  - `<tool_call>{"name":"tavily_web_search","arguments":{"query":"..."}}</tool_call>`
  - `<tool_call>{"name":"attached_image_inspect","arguments":{"question":"..."}}</tool_call>`
- Keep the existing rule that the model must emit exactly one tool envelope and no surrounding prose when using a tool.
- Keep the existing rule that final user-facing output must not expose tool JSON or tool envelopes.

For local-model parsing helpers:
- Refactor the current search-only helper so it can:
  - parse generic `<tool_call>...</tool_call>` envelopes
  - extract `name`
  - preserve visible prefix/suffix
  - validate the argument shape based on tool name
- Add support for both argument shapes:
  - search: `query`
  - image inspect: `question`
- Preserve current malformed-envelope behavior.

### 5. Add an image tool executor
Create a tool executor path for `attached_image_inspect`.

Execution flow:
1. Validate tool name.
2. Parse required `question`.
3. Resolve the in-scope image from the database using chat context and current turn context.
4. Invoke `AnalyzeImageUseCase(resolvedImageUri, question)`.
5. Return JSON containing:
   - `resolved_message_id`
   - `image_uri`
   - `question`
   - `analysis`
6. Log execution start/finish similarly to search tool execution.

Important v1 constraints:
- Tool execution must always re-read the image through the vision model.
- Tool execution must not reuse the baseline cached description as the final answer source.
- Tool execution must not insert another `message_vision_analysis` row.
- Tool execution must fail clearly if no resolvable image exists.

### 6. Update prompt preparation and history behavior
Prompt preparation:
- Keep `preparePrompt()` for current-message image context only.
- If the current user message has an image:
  - reuse stored baseline analysis if present for that exact `(user_message_id, image_uri)`
  - otherwise generate baseline analysis with `AnalyzeImageUseCase(imageUri, prompt)`
  - save it to `message_vision_analysis`
  - prepend it to the prompt as today
- If the current user message has no image:
  - do not inject a historical image description into the prompt directly
  - rely on rehydrated history plus the image tool

History rehydration:
- Keep `rehydrateHistory()` including baseline analyses for older image-bearing messages.
- Keep `buildHistoryContent()` emitting baseline descriptions for historical image messages.
- This baseline history remains useful for:
  - reminding the model that an image exists
  - giving lightweight context
  - helping the model decide when to call the image tool
- Do not change history rehydration to include raw image URIs or binary content.

### 7. Preserve persistence and sanitization behavior
- Keep assistant message persistence behavior unchanged apart from added tool availability.
- Continue sanitizing `<tool_call>` and `<tool_result>` traces before persisting assistant content.
- Ensure this sanitization still removes image-tool traces as well as search-tool traces.
- No database schema change for v1.
- No new Room entity/table for tool executions in v1.

## Public APIs / Types / Contracts
### Domain model changes
- Add `ToolDefinition.ATTACHED_IMAGE_INSPECT`.
- Keep `GenerationOptions` shape unchanged unless implementation requires extra context.
- If tool execution needs explicit turn context that cannot be inferred elsewhere, add only the minimum necessary field to the tool request path, not to persisted message models.

### Repository interface changes
Add to `MessageRepository` a method with behavior equivalent to:
- resolve the latest image-bearing user message for a chat
- optionally prefer the current user message ID if it has an image
- return null when no in-scope image exists

The returned object should be a dedicated small domain type or an existing message domain object if that is simpler and does not create ambiguity.

### Tool executor contract
- `ToolExecutorPort` remains the dispatch boundary.
- Dispatcher implementation must support both supported tools.
- Search-specific implementation should be renamed or generalized if needed so the type name matches actual responsibilities.

### Local tool contract
Update the local-model prompt composer so the contract is generic and includes both tools. The contract must tell the model:
- when to use web search
- when to use image inspection
- to output exactly one envelope
- to continue after receiving `<tool_result>`
- never to expose raw tool traces in the final answer

## Detailed Execution Order
1. Inspect existing tool execution path and refactor search-specific helper/parser code into generic tool-envelope support.
2. Add `attached_image_inspect` to `ToolDefinition`.
3. Add repository method for resolving the current-turn image target from existing messages.
4. Implement image tool executor logic using `AnalyzeImageUseCase`.
5. Wire tool availability logic in `GenerateChatResponseUseCase`.
6. Update local tool prompt contract to advertise both tools.
7. Keep `preparePrompt()` baseline-analysis behavior for current attached images.
8. Verify rehydrated history still uses baseline cached analyses only.
9. Verify assistant persistence still strips tool traces.
10. Run focused tests for tool availability, tool execution, history, and prompt behavior.

## Test Plan
### GenerateChatResponseUseCase tests
- Current-turn image still causes baseline analysis before main inference.
- Follow-up turn with no new attachment exposes `attached_image_inspect` when the chat contains a prior image-bearing user message.
- Turn with both search enabled and resolvable image exposes both tools.
- Turn with search disabled but resolvable image exposes only `attached_image_inspect`.
- Turn with no resolvable image does not expose `attached_image_inspect`.
- Persisted assistant content strips `<tool_call>` and `<tool_result>` for both search and image tool traces.

### Repository tests
- Resolves current user message image when present.
- Resolves latest prior user image when current message has none.
- Ignores assistant/system messages even if future code ever places image URIs there.
- Returns null when no user image exists in the chat.
- Handles multiple prior image-bearing messages by choosing the latest one deterministically.

### Tool executor tests
- `attached_image_inspect` validates `question`.
- `attached_image_inspect` loads the resolved URI from the repository and calls `AnalyzeImageUseCase` with that URI and question.
- Returns expected JSON payload with resolved message metadata and analysis.
- Produces a controlled failure when no image is resolvable.
- Search tool behavior remains unchanged.

### Local inference / tool parsing tests
- Generic local parser accepts valid search envelope.
- Generic local parser accepts valid image-inspect envelope.
- Missing `query` fails for search tool.
- Missing `question` fails for image-inspect tool.
- Malformed envelope still produces the same class of error.
- Local tool contract string contains both supported tool examples.

### History / context tests
- Historical image messages still rehydrate with baseline descriptions.
- Follow-up prompt without image attachment does not inline stale image description from the current-turn prompt path.
- Model still receives enough history context to decide to call the image tool.

## Failure Modes And Expected Handling
- No in-scope image:
  - tool returns controlled error result
  - model should continue gracefully and explain that no prior attached image is available in the current chat
- Vision model error during tool execution:
  - surface a controlled tool failure result rather than crashing the whole generation path if the runtime supports it
  - if current architecture cannot recover inside the tool bridge, fail consistently with existing tool-execution behavior
- Empty vision response:
  - reuse existing `AnalyzeImageUseCase` empty-result failure handling
- Multiple prior images:
  - latest prior user image wins in v1
  - no model-side image selection logic
- Cached baseline stale or insufficient:
  - acceptable in v1 because follow-up precision comes from the reread tool, not from cached analysis quality

## Assumptions
- v1 is intentionally single-image-per-turn.
- Multi-image comparison is deferred.
- Existing `message.created_at` ordering is sufficient for deterministic “latest prior image” resolution.
- `message_vision_analysis` remains baseline context storage only.
- No schema migration is required.
- Execution should write this plan to `plans/chat-image-inspection-tool.md` once Plan Mode is exited.

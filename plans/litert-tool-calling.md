# Plan: Native LiteRT Tool Calling Integration

## Objective
Fix the issue where local LiteRT models are not seeing or properly executing available tools (like Tavily web search). `pocket-crew` currently relies on a manual parsing flow for `<tool_call>` envelopes without natively registering tools with the engine, whereas the official `google-ai-edge/gallery` app uses the native `@Tool` and `ToolProvider` system built into `LiteRT-LM`. We will migrate to this native tool-calling system so the engine fully understands its tool availability and executes it autonomously.

## Key Files & Context
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/ConversationManagerImpl.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/LiteRtInferenceServiceImpl.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/StubSearchToolExecutor.kt`

## Implementation Steps

### 1. Implement Native ToolSet in `ConversationManagerImpl.kt`
- **Inject Executor:** Add `private val toolExecutor: ToolExecutorPort? = null` to the constructor of `ConversationManagerImpl`.
- **Create ToolSet Class:** Define a `LocalSearchToolset` that implements `com.google.ai.edge.litertlm.ToolSet`.
  - Create a function `tavily_web_search(query: String): String`.
  - Annotate it with `@Tool(description = "Search the web for information.")`.
  - Annotate the parameter with `@ToolParam(description = "The search query.") query: String`.
  - Inside the method, use `runBlocking` to construct a `ToolCallRequest` and run it against the injected `toolExecutor.execute()`. Return the output JSON string.

### 2. Configure LiteRT `ConversationConfig`
- In `ConversationManagerImpl.getConversation`, check `options?.availableTools` to see if `ToolDefinition.TAVILY_WEB_SEARCH` is active.
- If present and the `toolExecutor` is not null, build the `tools` list:
  ```kotlin
  val tools = listOf(tool(LocalSearchToolset(toolExecutor, modelType)))
  ```
- Pass `tools = tools` into `ConversationConfig`.
- Set `automaticToolCalling = true` in `ConversationConfig` so LiteRT handles pausing, execution, and resuming the stream automatically.
- Update `ConversationSignature` to include `hasSearchTool: Boolean` to ensure the conversation is correctly recreated if a user disables/enables the search tool.

### 3. Simplify `LiteRtInferenceServiceImpl.kt`
- Remove the manual XML `<tool_call>` parsing logic entirely (e.g., `executeToolingPrompt`, `collectToolPreparationPass`, and related helper methods).
- Revert the `sendPrompt` function back to a simple `conversation.sendMessageAsync` loop that emits `Thinking` and `PartialResponse` segments natively. LiteRT's `automaticToolCalling` will abstract the tool invocation completely.
- Remove the dependency on `toolExecutor` from this class, as the executor will now be handled inside `ConversationManagerImpl`.

### 4. Cleanup `SearchToolSupport`
- Remove all manual extraction routines (`extractLocalToolEnvelope`, `TOOL_CALL_REGEX`, `hasLocalToolContract`, `buildLocalToolResultMessage`) from `SearchToolSupport` within `StubSearchToolExecutor.kt`.

## Verification & Testing
- Deploy the updated app onto a device.
- Initiate a conversation that requires web search using a local LLM via LiteRT.
- Verify that the local model actively triggers the `tavily_web_search` method.
- Validate that the inference stream pauses, the `ToolExecutorPort` runs and logs the search, and the LiteRT engine successfully resumes output generation and streams the final answer back to the UI.
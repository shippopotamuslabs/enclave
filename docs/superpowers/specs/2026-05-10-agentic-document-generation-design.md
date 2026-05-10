# Agentic Document Generation Loop Design

## Background
Currently, the application generates Word and PDF documents by having the LLM emit the entire document structure in a single `GENERATE_ARTIFACT` tool call. This is problematic for smaller on-device models with low output token limits, preventing them from generating large documents (like books or long reports). 

We need an agentic loop where the model can generate a document incrementally by appending blocks to an existing JSON structure, managing the context window effectively so it can theoretically generate documents of infinite length.

## Tool Definitions
We will replace the single `GENERATE_ARTIFACT` tool with two distinct tools:

1. **`START_ARTIFACT`**:
   - **Inputs**: `title`, `metadata` (author, etc.), `targetFormat` (PDF/Word).
   - **Outputs**: `artifactId` (string)
   - **Purpose**: Initializes a new document in the local SQLite database and provides the LLM with an ID to reference in subsequent calls.

2. **`APPEND_BLOCKS`**:
   - **Inputs**: `artifactId` (string), `blocks` (List of JSON schema blocks), `isComplete` (Boolean).
   - **Outputs**: Success message (e.g., "Appended 15 blocks. Total blocks so far: 15. Please continue.").
   - **Purpose**: The LLM calls this repeatedly to add chunks of content. When `isComplete` is true, the tool generates the final byte array (PDF/Word) using the Rust library and returns "Document complete".

## The Agentic Loop (Orchestration)
We will leverage the existing `LlmToolingOrchestrator` to handle the recursive nature of the loop:

1. **System Prompt**: The model will be instructed that for large documents, it MUST use `START_ARTIFACT` first, and then repeatedly call `APPEND_BLOCKS` (emitting 10-20 blocks per call to respect output limits) until the entire document is finished.
2. **Loop Execution**: 
   - When the LLM emits `APPEND_BLOCKS(isComplete=false)`, `ArtifactToolExecutorPort` appends the blocks to the database and returns a progress string.
   - `LlmToolingOrchestrator` naturally receives this result and feeds it back to the LLM as a tool response.
   - The LLM processes the tool response and immediately generates the next `APPEND_BLOCKS` call.
   - The orchestrator's `maxToolCalls` limit will be temporarily increased or bypassed specifically for artifact generation to allow for long loops.

## Context Compression Mechanism
To prevent the chat history from blowing past the model's maximum context window due to accumulating tool calls and large JSON arrays:

1. **Threshold Detection**: Inside `BaseOpenAiSdkInferenceService` (and others), the `onContextExceeded` callback calculates token usage. We will define a compression threshold (e.g., 80% of max context).
2. **History Rebuilding**: When the threshold is breached during an artifact loop, `onContextExceeded` will construct `rebuiltParams`. 
3. **Compression Strategy**:
   - It will identify all previous `APPEND_BLOCKS` tool calls and their raw JSON responses in the request history.
   - It will remove these bulky messages from the history being sent to the LLM.
   - It will insert a single synthetic system message summarizing the removed content: *"System Note: Previous document blocks have been saved. You have generated up to block X (Last content: '...'). Continue generating from this point."*
   - This frees up thousands of tokens while retaining the LLM's spatial awareness of the document.

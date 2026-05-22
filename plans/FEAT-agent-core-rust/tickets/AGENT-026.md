# AGENT-026 — `RustAgentEngineAdapter` (domain port)

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E6 — Kotlin adapters |
| **Depends on** | AGENT-022, AGENT-024 |
| **Blocks** | AGENT-030, AGENT-031, AGENT-049 |

## Description

Add domain port in `:core:domain`:

```kotlin
// core/domain/.../port/inference/AgentLoopPort.kt
interface AgentLoopPort {
    fun execute(
        initialMessages: List<ChatMessage>,
        config: AgentLoopConfig,
        sessionContext: Map<String, String>,  // chatId, userMessageId, assistantMessageId
    ): Flow<AgentLoopEvent>
}

sealed interface AgentLoopEvent {
    data class ToolCallExecuted(val toolName: String, val resultJson: String) : AgentLoopEvent
    data class CompactionApplied(val tier: String, val tokensBefore: Int, val tokensAfter: Int) : AgentLoopEvent
    data class ToolRepaired(val originalName: String, val resolvedName: String) : AgentLoopEvent
    data object Finished : AgentLoopEvent
    data class Failed(val message: String) : AgentLoopEvent
}
```

Implement `RustAgentEngineAdapter` in `:core:agent`:
- Wraps UniFFI `AgentEngine`
- Maps `ChatMessage` ↔ `AgentMessage` (with `session_context` from `sessionContext` parameter)
- Drives `next_action` loop on `Dispatchers.Default` in a coroutine
- Registers transport, executor, summarizer callbacks (AGENT-027, AGENT-028, AGENT-029)
- Emits `AgentLoopEvent` instances by draining `AgentEngine.drainEvents()` after each action (AGENT-049)

Hilt module `AgentModule` provides `AgentLoopPort` binding.

No port is public from `:feature:inference` — only `:core:agent` imports generated UniFFI types.

## Acceptance criteria

- [ ] Unit tests with fake callbacks (no JNI): full loop with mock engine yielding `Finished`
- [ ] `Flow<AgentLoopEvent>` collects correct sequence for a 2-tool-turn scenario
- [ ] Port usable from `:feature:inference` without importing `core.agent` UniFFI generated types

## Files

- `core/domain/.../port/inference/AgentLoopPort.kt`
- `core/agent/.../RustAgentEngineAdapter.kt`
- `core/agent/.../AgentModule.kt`

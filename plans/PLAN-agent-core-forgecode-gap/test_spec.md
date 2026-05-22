# Test Spec: Forgecode Gap Plan for `third_party/agent_core`

## Framework and Run Command

- Framework: Rust unit/integration tests in `third_party/agent_core/tests`
- Command: `cd /home/sean/Code/pocket-crew/third_party/agent_core && cargo test`

## Scenario Index

- `FCG-001` Loop metrics record repeated tool attempts with literal counters.
- `FCG-002` Read-before-edit allows patch after an exact prior read.
- `FCG-003` Read-before-edit rejects overwrite without a prior read.
- `FCG-004` Doom-loop hook injects one reminder after the third repeated tool signature.
- `FCG-005` Pending-work guard blocks finish and injects a reminder with literal task text.
- `FCG-006` Hook ordering is deterministic across request, tool, response, and finish checkpoints.
- `FCG-007` Preflight compaction triggers from absolute token threshold even when ratio is below default.
- `FCG-008` Preflight compaction triggers from turn count.
- `FCG-009` Turn-end compaction triggers when the final message role is `user`.
- `FCG-010` Request pipeline applies transformers in declared order.
- `FCG-011` Runtime tool view filters edit tools when prior-read policy is unsatisfied.
- `FCG-012` Delegation-capable tool metadata is advertised without changing `ExecuteTools`.
- `FCG-013` Tool-result metadata carries attempts remaining and timeout value.
- `FCG-014` Pending-work reminders do not duplicate when the same work set is already represented.
- `FCG-015` Retention window prevents summary or FIFO from consuming the protected tail.
- `FCG-016` Dynamic tool view hides delegation tools when capability is false.
- `FCG-017` Policy denial returns a typed denial marker and event.
- `FCG-018` Timeout returns typed timeout metadata and event.
- `FCG-019` End-to-end loop keeps deterministic action order with hooks and compaction enabled.
- `FCG-020` Mutation defense: a string-only reminder without structured metadata must fail.

## Gherkin Scenarios

### FCG-001 Loop metrics record repeated tool attempts with literal counters
```gherkin
Scenario: FCG-001 repeated tool attempts are counted exactly
  Given a new LoopMetrics state
  And tool call "call-1" for tool "web_search" with arguments_json "{\"query\":\"rust\"}"
  When the same normalized tool signature is recorded 3 times in sequence
  Then detect_doom_loop with threshold 3 returns repetition_count 3
  And tool_attempts["web_search"] equals 3
```

### FCG-002 Read-before-edit allows patch after an exact prior read
```gherkin
Scenario: FCG-002 patch is allowed after prior read
  Given LoopMetrics.files_read contains exactly "/repo/src/lib.rs"
  When can_edit_path is evaluated for "/repo/src/lib.rs"
  Then the result equals true
```

### FCG-003 Read-before-edit rejects overwrite without a prior read
```gherkin
Scenario: FCG-003 overwrite is denied without prior read
  Given LoopMetrics.files_read is empty
  And AgentConfig.require_read_before_edit equals true
  When the engine prepares a write or patch for "/repo/src/lib.rs"
  Then the engine returns AgentError::PolicyRejected with reason "read_before_edit_required"
  And no tool execution action is emitted
```

### FCG-004 Doom-loop hook injects one reminder after the third repeated tool signature
```gherkin
Scenario: FCG-004 doom-loop reminder appears once
  Given enable_loop_hooks equals true
  And enable_doom_loop_guard equals true
  And repeated tool signature "web_search:{\"query\":\"same\"}" has count 3
  When BeforeModelRequest hooks are evaluated
  Then HookEffect.injected_messages has length 1
  And the only injected message content equals "You are repeating the same tool pattern. Change approach before the next tool call."
  And HookEffect.finish_blocked equals false
```

### FCG-005 Pending-work guard blocks finish and injects a reminder with literal task text
```gherkin
Scenario: FCG-005 pending-work guard blocks finish
  Given enable_pending_work_guard equals true
  And LoopMetrics.pending_work_items contains exactly one item with text "verify failing tests"
  When BeforeFinish hooks are evaluated
  Then HookEffect.finish_blocked equals true
  And HookEffect.injected_messages[0].content equals "Pending work remains: verify failing tests"
```

### FCG-006 Hook ordering is deterministic across checkpoints
```gherkin
Scenario: FCG-006 hook ordering is stable
  Given one hook is registered for each checkpoint
  When one full loop executes model request, model response, tool execution, tool result, and finish check
  Then emitted hook checkpoint order equals:
    | 1 | before_model_request |
    | 2 | after_model_response |
    | 3 | before_tool_execution |
    | 4 | after_tool_result |
    | 5 | before_finish |
```

### FCG-007 Preflight compaction triggers from absolute token threshold even when ratio is below default
```gherkin
Scenario: FCG-007 absolute token threshold triggers compaction
  Given AgentConfig.threshold_ratio equals 0.80
  And AgentConfig.preflight_token_threshold equals 1200
  And computed pressure ratio equals 0.79
  And computed total_tokens equals 1300
  When next_action is called from Idle
  Then the first emitted compaction trigger equals "preflight"
  And compaction does not wait for ratio 0.80
```

### FCG-008 Preflight compaction triggers from turn count
```gherkin
Scenario: FCG-008 turn threshold triggers compaction
  Given AgentConfig.turn_threshold equals 4
  And LoopMetrics.turn_count equals 4
  When next_action is called from Idle
  Then the engine emits a compaction pass before CallModel
```

### FCG-009 Turn-end compaction triggers when the final message role is user
```gherkin
Scenario: FCG-009 turn-end compaction fires on user tail
  Given AgentConfig.compact_on_turn_end equals true
  And the last session message role equals "user"
  When submit_tool_results completes and the engine returns to Idle
  Then a compaction event with trigger "post_tool_result" or "turn_end" is emitted before the next CallModel
```

### FCG-010 Request pipeline applies transformers in declared order
```gherkin
Scenario: FCG-010 request transformers run in order
  Given a RequestPipeline with transformers ["append-alpha", "append-beta"]
  And the input system_prompt equals "seed"
  When the pipeline transforms the request
  Then the output system_prompt equals "seed|alpha|beta"
```

### FCG-011 Runtime tool view filters edit tools when prior-read policy is unsatisfied
```gherkin
Scenario: FCG-011 runtime tool view hides unsafe edit tools
  Given rendered tool catalog includes "web_search", "patch_file", and "write_file"
  And AgentConfig.require_read_before_edit equals true
  And LoopMetrics.files_read is empty
  When the runtime tool view is rendered for the next ModelRequest
  Then rendered_tool_catalog_json contains "web_search"
  And rendered_tool_catalog_json does not contain "patch_file"
  And rendered_tool_catalog_json does not contain "write_file"
```

### FCG-012 Delegation-capable tool metadata is advertised without changing `ExecuteTools`
```gherkin
Scenario: FCG-012 delegation metadata stays inside tool metadata
  Given tool catalog entry "task_delegate" has supports_delegation true
  When the engine emits ExecuteTools for call_id "c-delegate"
  Then AgentAction type equals "execute_tools"
  And ToolCallRecord.name equals "task_delegate"
  And no new AgentAction variant is introduced
```

### FCG-013 Tool-result metadata carries attempts remaining and timeout value
```gherkin
Scenario: FCG-013 tool result metadata is structured
  Given ToolAttemptMetadata.attempt_index equals 2
  And ToolAttemptMetadata.attempts_remaining equals 1
  And ToolAttemptMetadata.timeout_ms equals 300000
  When the tool result is serialized
  Then the structured metadata contains literal values 2, 1, and 300000
```

### FCG-014 Pending-work reminders do not duplicate when the same work set is already represented
```gherkin
Scenario: FCG-014 pending-work reminder deduplicates
  Given the last injected pending-work reminder content equals "Pending work remains: verify failing tests"
  And LoopMetrics.pending_work_items still contains exactly "verify failing tests"
  When BeforeFinish hooks are evaluated again
  Then HookEffect.injected_messages has length 0
  And HookEffect.finish_blocked equals true
```

### FCG-015 Retention window prevents summary or FIFO from consuming the protected tail
```gherkin
Scenario: FCG-015 retention window preserves the last two messages
  Given AgentConfig.retention_window_messages equals 2
  And session message ids equal ["m1", "m2", "m3", "m4"]
  When a destructive compaction tier selects its eviction range
  Then message ids "m3" and "m4" are excluded from the selected range
```

### FCG-016 Dynamic tool view hides delegation tools when capability is false
```gherkin
Scenario: FCG-016 delegation tools are removed when unsupported
  Given SessionCapabilities.delegation_tools_supported equals false
  And the static tool catalog contains "task_delegate"
  When the runtime tool view is rendered
  Then rendered_tool_catalog_json does not contain "task_delegate"
```

### FCG-017 Policy denial returns a typed denial marker and event
```gherkin
Scenario: FCG-017 policy denial is typed
  Given host policy callback denies tool "shell_exec"
  When the engine processes that tool call
  Then ToolResultMetadata.policy_denied equals true
  And one AgentEvent of type "tool_policy_denied" is emitted
  And result_json contains literal string "policy_denied"
```

### FCG-018 Timeout returns typed timeout metadata and event
```gherkin
Scenario: FCG-018 timeout is typed
  Given AgentConfig.default_tool_timeout_ms equals 100
  And a host tool execution exceeds 100 milliseconds
  When the engine records the tool result
  Then ToolResultMetadata.timed_out equals true
  And ToolResultMetadata.timeout_ms equals 100
  And one AgentEvent of type "tool_timed_out" is emitted
```

### FCG-019 End-to-end loop keeps deterministic action order with hooks and compaction enabled
```gherkin
Scenario: FCG-019 end-to-end order remains deterministic
  Given hooks, compaction triggers, and request pipeline are all enabled
  And the model response includes one tool call
  When the engine runs one full turn
  Then AgentAction order equals:
    | 1 | call_model |
    | 2 | execute_tools |
    | 3 | call_model |
  And no background action runs outside next_action or submit_* transitions
```

### FCG-020 Mutation defense: a string-only reminder without structured metadata must fail
```gherkin
Scenario: FCG-020 string-only reminder is insufficient
  Given an implementation that injects reminder text but leaves ToolAttemptMetadata absent
  When scenarios FCG-013 and FCG-017 are evaluated
  Then both scenarios fail
  And the failure reason references missing structured metadata fields
```

## Mutation Defenses

- A naive implementation that only appends reminder strings to messages must fail `FCG-013`, `FCG-017`, and `FCG-020`.
- A naive implementation that adds hooks but allows arbitrary phase skipping must fail `FCG-006` and `FCG-019`.
- A naive implementation that copies Forgecode-style turn/message thresholds but ignores the protected retention tail must fail `FCG-015`.
- A naive implementation that exposes delegation through a new loop action instead of normal tool execution must fail `FCG-012`.

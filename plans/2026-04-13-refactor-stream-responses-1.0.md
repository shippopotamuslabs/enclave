# Refactor BaseOpenAiSdkInferenceService.streamResponses()

## Objective

Extract the 230-line `streamResponses()` method from `BaseOpenAiSdkInferenceService` into a dedicated `OpenAiResponseStreamHandler` class. The current method contains ~15 mutable variables and 15+ if/else branches handling every OpenAI Responses API stream event type, making it difficult to read, test, and extend. The refactoring will decompose each event-handling branch into its own method, encapsulate streaming state in a typed accumulator, and preserve all existing behavior while making the code modular and independently testable.

---

## Analysis of Current State

### The Problem
`streamResponses()` at `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/BaseOpenAiSdkInferenceService.kt:210-455` is a single 245-line method with:

- **15 mutable local variables** tracking streaming state: `emittedAny`, `outputTextDeltaCount`, `reasoningTextDeltaCount`, `reasoningSummaryDeltaCount`, `toolCallRequest`, `responseId`, `providerToolCallId`, `providerToolItemId`, `outputTextByPart`, `reasoningTextByPart`, `reasoningSummaryByPart`, `streamedAssistantMessage`, `capturedFunctionCallByKey`
- **12 event-handling branches** in an if-else chain (lines 261-444): `isOutputTextDelta`, `isOutputTextDone`, `isReasoningTextDelta`, `isReasoningTextDone`, `isReasoningSummaryTextDelta`, `isReasoningSummaryTextDone`, `isFunctionCallArgumentsDone`, `isOutputItemAdded`, `isOutputItemDone`, `isCompleted`, `isFailed`, `isError`
- **2 error-recovery branches** wrapping `iterator.hasNext()` and `iterator.next()` calls
- **1 fallback/ignore branch** for unrecognized events
- Mixed concerns: stream iteration, text accumulation, tool-call capture, error recovery, logging, and event emission are all interleaved

### Existing Test Coverage
`BaseOpenAiSdkInferenceServiceTest.kt` currently only tests `describeException()` and `novelStreamSuffix()`. The streaming logic has **no direct unit test coverage**, making a careful extraction with behavioral preservation tests essential.

### Dependencies
- `streamResponses()` is called from `BaseOpenAiSdkInferenceService.executeToolingPrompt()` (line 169) and `ApiInferenceServiceImpl.executePrompt()` (line 57)
- Helper methods `appendStreamDelta()`, `resolveNovelStreamText()`, `streamPartKey()`, `streamItemKey()`, `detectResponseEventType()`, `shouldRecoverFromStreamTermination()`, `isRecoverableStreamTermination()`, `captureFunctionCallMetadata()` are all private to the base class and only used by `streamResponses()`
- The method's return type `StreamedOpenAiResponse` (line 41-48) already encapsulates the output, but the streaming state is spread across local variables

---

## Implementation Plan

### Phase 1: Create the Streaming State Accumulator

- [ ] **Task 1.1.** Create `OpenAiResponseStreamState` data class in a new file `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/openai/OpenAiResponseStreamState.kt`. This replaces all 15 mutable local variables with an immutable accumulator that is updated via `copy()`. Fields:
  - `emittedAny: Boolean`
  - `outputTextDeltaCount: Int`
  - `reasoningTextDeltaCount: Int`
  - `reasoningSummaryDeltaCount: Int`
  - `toolCallRequest: ToolCallRequest?`
  - `responseId: String?`
  - `providerToolCallId: String?`
  - `providerToolItemId: String?`
  - `outputTextByPart: Map<String, String>`
  - `reasoningTextByPart: Map<String, String>`
  - `reasoningSummaryByPart: Map<String, String>`
  - `streamedAssistantMessage: String`
  - `capturedFunctionCallByKey: Map<String, CapturedFunctionCall>`

  Rationale: Grouping all streaming state into a single data class makes it explicit what the handler accumulates, eliminates mutable `var` sprawl, and enables copy-on-update semantics that are easier to reason about and test.

- [ ] **Task 1.2.** Move `CapturedFunctionCall` from its current location inside `BaseOpenAiSdkInferenceService` (line 457-462) to become a nested class inside `OpenAiResponseStreamState` or a companion file alongside the state class.

  Rationale: `CapturedFunctionCall` is only used in stream response handling and logically belongs with the streaming state.

### Phase 2: Create the Handler Class

- [ ] **Task 2.1.** Create `OpenAiResponseStreamHandler` class in `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/openai/OpenAiResponseStreamHandler.kt` with:
  - Constructor parameters: `provider: String`, `modelId: String`, `modelType: ModelType`, `loggingPort: LoggingPort`, `allowToolCall: Boolean`, `chatId: ChatId?`, `userMessageId: MessageId?`
  - A `handleEvent(event: ResponseStreamEvent, state: OpenAiResponseStreamState, emitEvent: suspend (InferenceEvent) -> Unit): OpenAiResponseStreamState` method that delegates to per-event-type methods
  - Individual handler methods for each event type: `handleOutputTextDelta()`, `handleOutputTextDone()`, `handleReasoningTextDelta()`, `handleReasoningTextDone()`, `handleReasoningSummaryTextDelta()`, `handleReasoningSummaryTextDone()`, `handleFunctionCallArgumentsDone()`, `handleOutputItemAdded()`, `handleOutputItemDone()`, `handleCompleted()`, `handleFailed()`, `handleError()`
  - Move existing helper methods from `BaseOpenAiSdkInferenceService` that are only used by streaming: `appendStreamDelta()`, `resolveNovelStreamText()`, `streamPartKey()`, `streamItemKey()`, `detectResponseEventType()`, `shouldRecoverFromStreamTermination()`, `isRecoverableStreamTermination()`, `captureFunctionCallMetadata()`

  Rationale: Each `if/else if` branch currently handles a distinct event type with its own concerns. Extracting each into a named method provides self-documenting code, enables per-event-type unit testing, and reduces the cognitive load of reading the main loop.

- [ ] **Task 2.2.** Add a `handleStreamTermination(e: RuntimeException, state: OpenAiResponseStreamState): OpenAiResponseStreamState?` method to encapsulate the repeated error-recovery pattern (currently duplicated at lines 237-244 and 252-258). Returns `null` when the error should be re-thrown, or the current state when recovery is possible.

  Rationale: The exact same recovery-check-and-break pattern is duplicated twice in the iteration loop. A single method eliminates this duplication and makes the recovery semantics explicit.

- [ ] **Task 2.3.** Add a `handleUnknownEvent(event: ResponseStreamEvent, state: OpenAiResponseStreamState)` method for the else branch (lines 440-444) that just logs the unrecognized event.

  Rationale: Even the fallback branch deserves a named method for traceability and testability.

- [ ] **Task 2.4.** Add a `toStreamedResponse(state: OpenAiResponseStreamState): StreamedOpenAiResponse` conversion method that maps the final accumulator state to the existing return type.

  Rationale: Cleanly separates accumulation concerns from the return-type mapping, maintaining backward compatibility with callers that expect `StreamedOpenAiResponse`.

### Phase 3: Rewrite streamResponses() as an Orchestrator

- [ ] **Task 3.1.** Rewrite `BaseOpenAiSdkInferenceService.streamResponses()` (lines 210-455) to:
  1. Instantiate `OpenAiResponseStreamHandler` with the necessary constructor params
  2. Initialize `OpenAiResponseStreamState` with default values
  3. Use the handler's `handleStreamTermination()` for the two `runInterruptible { iterator.hasNext/next() }` catch blocks
  4. In the main loop, call `handler.handleEvent(event, state, emitEvent)` and accumulate the returned state
  5. After the loop, call `handler.toStreamedResponse(state)` and return the result

  The rewritten method should be approximately 30-40 lines — just the iteration loop and error-recovery orchestration, with all event logic delegated to the handler.

  Rationale: This turns the method from a 245-line monolith into a thin orchestration loop, making the flow immediately understandable at a glance.

- [ ] **Task 3.2.** Make `StreamedOpenAiResponse` a top-level data class (or move it to its own file) since it's now used by the handler as a return type rather than just a result of `streamResponses()`. Ensure it remains accessible to `BaseOpenAiSdkInferenceService`, `ApiInferenceServiceImpl`, and any callers of `streamResponses()`.

  Rationale: `StreamedOpenAiResponse` is conceptually a generic result type for OpenAI streaming, not an implementation detail of the base class. Promoting it improves discoverability.

### Phase 4: Move Helper Methods

- [ ] **Task 4.1.** Move the following methods from `BaseOpenAiSdkInferenceService` to `OpenAiResponseStreamHandler`:
  - `appendStreamDelta()` (line 552-560)
  - `resolveNovelStreamText()` (line 563-575)
  - `novelStreamSuffix()` (line 577-593) — make `internal` for testing
  - `streamPartKey()` (line 595-599)
  - `streamItemKey()` (line 601-604)
  - `detectResponseEventType()` (line 606-622)
  - `shouldRecoverFromStreamTermination()` (line 483-487)
  - `isRecoverableStreamTermination()` (line 489-493)
  - `captureFunctionCallMetadata()` (line 464-481)

  Rationale: All these methods are private implementation details of the OpenAI Responses API streaming logic and have no callers outside `streamResponses()`. They belong in the handler, not the base class.

### Phase 5: Testing

- [ ] **Task 5.1.** Create `OpenAiResponseStreamHandlerTest.kt` in `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/openai/`. Write focused unit tests for each handler method:
  - `handleOutputTextDelta` — emits `PartialResponse`, increments counter, appends to output text builder
  - `handleOutputTextDone` — emits fallback novel text only when delta was missed
  - `handleReasoningTextDelta` — emits `Thinking`, increments counter
  - `handleReasoningTextDone` — emits fallback novel text only
  - `handleReasoningSummaryTextDelta` — emits `Thinking`, increments counter
  - `handleReasoningSummaryTextDone` — emits fallback novel text
  - `handleFunctionCallArgumentsDone` — captures tool call, throws `IllegalStateException` when `allowToolCall=false`
  - `handleOutputItemAdded` / `handleOutputItemDone` — captures function call metadata
  - `handleCompleted` — captures response ID
  - `handleFailed` / `handleError` — recovers if `emittedAny && isRecoverable`, throws otherwise
  - `handleStreamTermination` — returns null for non-recoverable, returns state for recoverable
  - `handleUnknownEvent` — logs and returns state unchanged

  Rationale: The current code has no unit test coverage for the streaming event-dispatch logic. Extracting each branch into a named method enables granular, focused tests that verify each event type independently.

- [ ] **Task 5.2.** Add integration-style test for `streamResponses()` rewrite in `BaseOpenAiSdkInferenceServiceTest.kt` to verify the full orchestration loop still produces `StreamedOpenAiResponse` with the correct accumulated fields.

  Rationale: Confirms the refactored orchestration loop preserves end-to-end behavior.

- [ ] **Task 5.3.** Migrate existing `novelStreamSuffix` tests from `BaseOpenAiSdkInferenceServiceTest.kt` (lines 71-83) to the new `OpenAiResponseStreamHandlerTest.kt`, since the method will move to the handler class.

  Rationale: Tests must follow the code they test; leaving orphaned tests in the wrong class causes confusion.

### Phase 6: Cleanup

- [ ] **Task 6.1.** Remove the moved methods from `BaseOpenAiSdkInferenceService` and remove any now-unused imports. Verify that `BaseOpenAiSdkInferenceService` still compiles and that `streamResponses()` delegates correctly to the handler.

- [ ] **Task 6.2.** Verify `streamChatCompletions()` (lines 495-545) is **not** affected. This method exists on `BaseOpenAiSdkInferenceService` and follows a different, simpler pattern. It does not need refactoring but should be checked to ensure no helper methods it depends on were moved. (`streamChatCompletions` currently uses no shared helpers from the streaming logic — its only overlap is `logChatRequest`, `provider`, `modelId`, `modelType`, and `loggingPort`, which all remain on the base class.)

- [ ] **Task 6.3.** Update `ApiInferenceServiceImpl` and `OpenRouterInferenceServiceImpl` (and any other subclasses) to ensure they still call `streamResponses()` correctly. The API contract (method signature and return type) does not change, so callers should be unaffected. Verify no compilation errors.

- [ ] **Task 6.4.** Run the full test suite: `./gradlew :feature:inference:testDebugUnitTest` and `./gradlew :feature:inference:assemble` to confirm no regressions.

---

## Architecture Diagram (Post-Refactor)

```
BaseOpenAiSdkInferenceService
├── streamResponses(params, allowToolCall, chatId, userMessageId, emitEvent)
│   ├── Creates OpenAiResponseStreamHandler
│   ├── Initializes OpenAiResponseStreamState
│   ├── Loops: handler.handleEvent(event, state, emitEvent) → newState
│   ├── Error recovery: handler.handleStreamTermination()
│   └── Returns handler.toStreamedResponse(finalState)
├── streamChatCompletions(params, emitEvent)  ← unchanged, separate streaming
├── executeToolingPrompt(...)                  ← unchanged, calls streamResponses
├── sendPrompt(...)                            ← unchanged
└── [other base-class methods]

OpenAiResponseStreamHandler  ← new class
├── handleEvent(event, state, emitEvent) → OpenAiResponseStreamState
│   ├── handleOutputTextDelta()
│   ├── handleOutputTextDone()
│   ├── handleReasoningTextDelta()
│   ├── handleReasoningTextDone()
│   ├── handleReasoningSummaryTextDelta()
│   ├── handleReasoningSummaryTextDone()
│   ├── handleFunctionCallArgumentsDone()
│   ├── handleOutputItemAdded()
│   ├── handleOutputItemDone()
│   ├── handleCompleted()
│   ├── handleFailed()
│   ├── handleError()
│   └── handleUnknownEvent()
├── handleStreamTermination(e, state) → OpenAiResponseStreamState?
├── toStreamedResponse(state) → StreamedOpenAiResponse
├── appendStreamDelta(...)
├── resolveNovelStreamText(...)
├── novelStreamSuffix(...)         ← internal for testing
├── streamPartKey(...) / streamItemKey(...)
├── detectResponseEventType(...)
├── shouldRecoverFromStreamTermination(...)
├── isRecoverableStreamTermination(...)
└── captureFunctionCallMetadata(...)

OpenAiResponseStreamState  ← new data class
├── emittedAny, outputTextDeltaCount, reasoningTextDeltaCount, ...
├── capturedFunctionCallByKey
└── CapturedFunctionCall (nested or companion)
```

---

## Verification Criteria

- [ ] `streamResponses()` in `BaseOpenAiSdkInferenceService` is under 50 lines (down from 245)
- [ ] All 12+ event-handling branches are individual named methods on `OpenAiResponseStreamHandler`
- [ ] `OpenAiResponseStreamState` replaces all 15 mutable local variables with immutable `copy()` updates
- [ ] `handleStreamTermination()` eliminates the duplicated error-recovery pattern
- [ ] All streaming helper methods are moved from `BaseOpenAiSdkInferenceService` to `OpenAiResponseStreamHandler`
- [ ] `streamChatCompletions()` and all callers of `streamResponses()` are uncompromised
- [ ] New `OpenAiResponseStreamHandlerTest.kt` has >= 12 test methods covering each handler branch
- [ ] Existing `novelStreamSuffix` tests migrate to new handler test file
- [ ] `./gradlew :feature:inference:testDebugUnitTest` passes
- [ ] `./gradlew :feature:inference:assemble` passes
- [ ] No behavioral changes — the refactored code emits identical `InferenceEvent` sequences for identical inputs

---

## Potential Risks and Mitigations

1. **Behavioral drift during extraction** — Mutable variable order of operations may not translate trivially to immutable `copy()` updates.
   - Mitigation: Each handler method returns the *new complete state* via `copy()`, making the data flow explicit. The main loop is `state = handler.handleEvent(event, state, emitEvent)` — a strict left-fold pattern. The only mutation point is the `emitEvent` side-effect, whose order is preserved by sequential iteration.

2. **`StringBuilder` → `String` performance regression** — The current code uses `mutableMapOf<String, StringBuilder>()` and `StringBuilder` for `streamedAssistantMessage`. Switching to immutable `String` concatenation could impact performance for long streams.
   - Mitigation: Use `StringBuilder` inside the state class via a wrapper type or keep `StringBuilder` as a mutable field within `OpenAiResponseStreamState` with explicit update methods. Alternatively, since the string builders are accumulated across hundreds of chunks, consider making `outputTextByPart`, `reasoningTextByPart`, `reasoningSummaryByPart` use `Map<String, StringBuilder>` internally (since these are only read at `Done` events) and only `streamedAssistantMessage` needs to be a plain `StringBuilder`. The key insight is that these builders don't need `copy()` semantics — they can be mutated in place since the handler methods are called sequentially.

3. **Lambda capture in `emitEvent`** — The `emitEvent` is `suspend (InferenceEvent) -> Unit`. Every handler method must accept this as a parameter, adding ceremony.
   - Mitigation: Store `emitEvent` as a constructor parameter or property on `OpenAiResponseStreamHandler` so it doesn't need to be passed to every method. This simplifies method signatures while keeping the handler stateless with respect to event emission (the handler doesn't cache events).

4. **Test setup complexity** — Mocking `ResponseStreamEvent` for each event type requires understanding the OpenAI SDK's event model.
   - Mitigation: Create a `FakeResponseStreamEvent` factory or helper in the test that constructs events using the SDK's builder APIs, mirroring the pattern already used in `ApiInferenceServiceImplTest.kt`.

5. **`CapturedFunctionCall` visibility** — Currently package-private as a nested class. Moving it to a new file/package may require visibility adjustments.
   - Mitigation: Keep it `internal` within the `feature:inference` module, accessible to both the handler and the base class.

---

## Alternative Approaches

1. **Sealed interface for stream events (Visitor pattern)**: Instead of `if/else if` chain on `event.isX()` checks, create a sealed interface wrapper `OpenAiStreamEvent` with subtypes for each event kind, then use `when(event) { is OutputTextDelta -> ... }`. This would require adding a mapper from `ResponseStreamEvent` to the sealed type, adding a dependency on the OpenAI SDK in the mapper. The approach is more Kotlin-idiomatic but adds a mapping layer and doesn't eliminate the need for per-type handlers.
   - **Trade-off**: More idiomatic Kotlin, compile-time exhaustiveness checking, but adds indirection and a dependency on the SDK's event model in the mapper.

2. **Keep mutable state, just extract methods**: Instead of creating `OpenAiResponseStreamState` as a data class, keep the mutable variables as fields on `OpenAiResponseStreamHandler` and just extract each `if/else` branch into methods that mutate `this`. This is simpler initially but leads to shared mutable state, making testing harder and losing the benefit of explicit data flow.
   - **Trade-off**: Simpler migration path, but doesn't solve the mutable-state sprawl problem; just moves it to a different class. Less testable. Not recommended.

3. **`when` expression with Kotlin 2.1 exhaustive matching**: Replace the `if/else if` chain with a `when` block. While Kotlin's `when` doesn't natively support exhaustive matching on the OpenAI SDK's `isX()` pattern, we could write `when { event.isOutputTextDelta() -> ... }` for readability. This is a syntax-only change that doesn't require a new class.
   - **Trade-off**: Marginal readability improvement with zero architectural improvement. The method would still be 200+ lines. Not sufficient on its own.

**Recommended approach**: Proceed with the primary plan (Tasks 1-6). The `OpenAiResponseStreamState` + `OpenAiResponseStreamHandler` extraction provides the best balance of readability, testability, and behavioral preservation. The use of `when` expression syntax (Alternative 3) can be adopted as a follow-up inside `handleEvent()` for the dispatch, combining the benefits of both approaches.
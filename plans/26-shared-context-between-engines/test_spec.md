# Test Specification: Shared Context Between Engines

## Gherkin Behavioral Scenarios

### Feature: Stateful Conversation History for LiteRT & MediaPipe

**Background:**
  Given the application is initialized with the required inference engines (LiteRT, MediaPipe)

### 1. LiteRT Conversation Initialization with History
  **Scenario: LiteRT conversation seeds with non-empty history**
    Given a list of existing chat messages:
      | User    | Content              |
      | USER    | Hello, what's up?    |
      | MODEL   | Not much, how are you?|
    When the LiteRtInferenceService receives the history
    And the user sends a new prompt "Tell me a joke"
    Then the ConversationManager should create a ConversationConfig with a initialMessages containing 2 messages
    And the first message role should be "user" and the content "Hello, what's up?"
    And the second message role should be "model" and the content "Not much, how are you?"
    And the new prompt "Tell me a joke" is sent to the created conversation

### 2. Updating LiteRT History
  **Scenario: Updating LiteRT history invalidates the current session**
    Given an active LiteRT conversation session
    When the LiteRtInferenceService receives a new, different history list
    Then the current Conversation instance must be closed and nulled
    And the next prompt should result in a new Conversation instance being created with the updated history initialMessages

### 3. MediaPipe History Seeding
  **Scenario: MediaPipe seeds history via sequential addQueryChunk calls before first prompt**
    Given a list of existing chat messages:
      | User    | Content              |
      | USER    | What is AI?          |
      | ASSISTANT | AI is...           |
    When the MediaPipeInferenceService receives the history
    And the user sends a new prompt "Explain it simpler"
    Then a new LlmInferenceSession should be created
    And `addQueryChunk` should be called sequentially for:
      1. "User: What is AI?\n"
      2. "Assistant: AI is...\n"
    And finally `addQueryChunk` is called with the user prompt "Explain it simpler"
    And generateResponseAsync should be called with the session context

### 4. Empty History Handling
  **Scenario: Handling empty history**
    When the inference service receives an empty history list
    And the user sends a new prompt "Hello"
    Then the conversation should initialize without any initialMessages or history seeding

## Error-Path Scenarios

### 5. SDK Initialization Failures during Seeding
  **Scenario: MediaPipe session fails to create during history injection**
    Given the MediaPipe engine is initialized but session creation fails
    When setHistory is called followed by a sendPrompt call
    Then an InferenceEvent.Error should be emitted
    And the error should reflect the session creation failure

## Mutation Heuristic Audit

**Question:** "What is the most broken implementation that would still pass these scenarios?"

1. **LiteRT Preface Mapping:**
   - *Broken Implementation*: An implementation that maps all messages to the "user" role regardless of their actual role in the domain model.
   - *Refined Scenario*: Added explicit checks for the role mapping (USER -> user, ASSISTANT -> model) in Scenario 1.

2. **MediaPipe History Formatting:**
   - *Broken Implementation*: An implementation that just concatenates the entire history into a single string without proper separation, leading to model confusion.
   - *Refined Scenario*: Ensure the "formatted and injected" step in Scenario 3 includes specific validation for role-based separation (e.g., "User: ..." vs "Assistant: ...").

3. **Session Invalidation (LiteRT):**
   - *Broken Implementation*: An implementation that updates the history list but fails to null out the active session, causing the model to use stale context.
   - *Refined Scenario*: Explicitly check for the closure and nulling of the previous session in Scenario 2.

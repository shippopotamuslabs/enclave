# Test Specification: Fix Compilation Ref WebSearch

## 1. Happy Path Scenarios

### Scenario: MessageDaoSortingTest Compilation
- **Given:** `MessageDao` now returns `MessageWithFiles`.
- **When:** Accessing properties like `id` on results from `getMessagesByChatIdFlow`.
- **Then:** Use `it.message.id` instead of `it.id`.

### Scenario: Repository Mock Update
- **Given:** `MessageDao` or `ChatRepository` methods return `MessageWithFiles`.
- **When:** Mocking these methods in `ChatRepositoryImplTest` or `MessageRepositoryImplGetMessagesForChatTest`.
- **Then:** Return `MessageWithFiles(message = entity, files = emptyList())` instead of just `entity`.

### Scenario: WebSearch Rename Consistency
- **Given:** Renaming of `Tavily` to `WebSearch` in types and methods.
- **When:** Using `InferenceEvent.WebSearchResults` or `WebSearchSource`.
- **Then:** All tests reference the new types correctly.

### Scenario: ActiveModelConfiguration modelId Requirement
- **Given:** `ActiveModelConfiguration` constructor requires `modelId`.
- **When:** Creating instances in `ChatInferenceRequestPreparerTest` or `GenerateChatResponseUseCaseSearchToolTest`.
- **Then:** Provide a non-null `modelId` string (e.g., "model-1").

## 2. Error Path & Edge Case Scenarios

### Scenario: markWebSearchSourceExtracted Missing in Fake
- **Given:** `FakeChatRepository` does not implement the new interface methods.
- **When:** Compiling `:core:domain` tests.
- **Then:** `FakeChatRepository` implements `markWebSearchSourceExtracted` and `markWebSearchSourcesExtracted`.

## 3. Mutation Defense
### Lazy Implementation Risk
A lazy implementation might just add `@Suppress("UNRESOLVED_REFERENCE")` or use `any()` in mocks without providing realistic data structures.

### Defense Scenario
- **Given:** A test requiring `MessageWithFiles`.
- **When:** The repository processes the result.
- **Then:** The test must verify that the inner `MessageEntity` properties are correctly mapped, proving that `MessageWithFiles` was correctly handled.

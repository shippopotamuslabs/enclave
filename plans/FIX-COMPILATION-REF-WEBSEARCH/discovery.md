# Discovery: Fix Compilation Ref WebSearch

## 1. Goal Summary
The goal is to fix all compilation errors in the test suite introduced by the "WebSearch" refactor (renaming Tavily-specific terms to generic WebSearch) and the structural change where `MessageDao` now returns `MessageWithFiles` instead of raw `MessageEntity`. This ensures the test suite accurately reflects the current production architecture and maintains high coverage.

## 2. Target Module Index

### Existing Data Models
- `MessageEntity` (core/data): Represents a single chat message in the database.
- `MessageWithFiles` (core/data): A Room POJO that wraps `MessageEntity` and its associated `MessageFileEntity` list.
- `WebSearchSourceEntity` (core/data): Database entity for search results.
- `WebSearchSource` (core/domain): Domain model for search results.
- `ActiveModelConfiguration` (core/domain): Configuration for a model, now requiring `modelId`.
- `GenerationOptions` (core/domain): Per-request generation options.

### Dependencies & API Contracts
- `MessageDao` (core/data): Interface for message persistence, now using `MessageWithFiles`.
- `WebSearchSourceDao` (core/data): Interface for search source persistence, features `markExtracted`.
- `ChatRepository` (core/domain): Interface for chat operations, features `markWebSearchSourceExtracted` and `markWebSearchSourcesExtracted`.
- `InferenceEvent` (core/domain): Sealed class for inference events, now using `WebSearchResults`.

### Utility/Shared Classes
- `WebSearchResultParser` (core/domain): Utility for parsing search results from JSON.

### Impact Radius
- `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/local/MessageDaoSortingTest.kt`
- `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/ChatRepositoryImplTest.kt`
- `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/ExtractToolExecutorTest.kt`
- `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/MessageRepositoryImplGetMessagesForChatTest.kt`
- `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/FakeChatRepository.kt`
- `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/ChatInferenceRequestPreparerTest.kt`
- `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/DirectChatInferenceExecutorTest.kt`
- `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/GenerateChatResponseUseCaseImageToolTest.kt`
- `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/GenerateChatResponseUseCaseSearchToolTest.kt`
- `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/ActiveChatTurnStoreTest.kt`
- `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/LiteRtInferenceServiceImplTest.kt`
- `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/model/inference/ToolCallRequestTest.kt`
- `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/model/inference/ToolDefinitionTest.kt`
- `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/model/chat/MessageGenerationStateTest.kt`
- `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/model/chat/WebSearchSourceTest.kt`

## 3. Cross-Probe Analysis
### Overlaps Identified
- `MessageWithFiles` impacts multiple tests across `core:data` and `core:domain` (via repositories).
- `WebSearch` renaming impacts both `core:data` and `feature:inference`.
- `ActiveModelConfiguration.modelId` impacts almost all use case tests in `core:domain`.

### Gaps & Uncertainties
- Some tests might still use `Tavily` in variable names or string constants, which doesn't break compilation but should be cleaned for consistency.
- `MessageWithFiles` requires `MessageFileEntity` list; for most existing tests, an empty list is sufficient.

### Conflicts (if any)
- None identified.

## 4. High-Impact Clarifying Questions
*None identified. Proceeding to Spec phase.*

## 5. Probe Coverage Summary
| Layer/Directory | Probe Agent | Key Findings |
|----------------|------------|-------------|
| core/data | Gemini CLI | Identified `MessageWithFiles` and `WebSearch` related errors. |
| core/domain | Gemini CLI | Identified `modelId` and renaming errors. |
| feature/inference | Gemini CLI | Identified renaming errors in events and stores. |

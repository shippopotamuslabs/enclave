# Technical Specification: Fix Compilation Ref WebSearch

## 1. Objective
Fix all compilation errors in the test suite caused by the `WebSearch` refactor and the `MessageWithFiles` structural change.
Acceptance Criteria:
- All 15 specified test files compile successfully.
- `./gradlew testDebugUnitTest` passes for the affected modules.
- `Tavily` naming is replaced by `WebSearch` where appropriate for consistency.

## 2. System Architecture

### Target Files
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

### Component Boundaries
The changes are limited to the test source sets of `:core:data`, `:core:domain`, and `:feature:inference`. They align the tests with existing production code contracts.

## 3. Data Models & Schemas
Reused Models:
- `MessageWithFiles`: Used to wrap `MessageEntity` in DAO-related tests.
- `MessageEntity`: Wrapped inside `MessageWithFiles`.
- `WebSearchSource`: Used instead of `TavilySource`.
- `ActiveModelConfiguration`: Updated to include required `modelId`.

## 4. API Contracts & Interfaces
No API contract changes. (These are test fixes for already changed contracts).

## 5. Permissions & Config Delta
No permissions or config changes.

## 6. Constitution Audit
This design adheres to the project's core architectural rules by maintaining clean separation between layers in tests and respecting the current production contracts defined in `:domain` and `:data`.

## 7. Cross-Spec Dependencies
No cross-spec dependencies.

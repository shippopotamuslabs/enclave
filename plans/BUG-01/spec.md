# Technical Specification: Log Native Teardown Failures (BUG-01)

## 1. Objective
Eliminate silent failures during LiteRT native resource teardown. The primary goal is to ensure that any exception thrown during `Engine.close()` or `Conversation.close()` is logged with sufficient context (stack trace and current state) instead of being masked by empty catch blocks.

Acceptance Criteria:
- Replace empty `catch (_: Throwable) {}` blocks in `ConversationManagerImpl` and `LiteRtInferenceServiceImpl` with structured logging.
- Use `Log.w` for cleanup failures to distinguish them from fatal initialization errors.
- Include the exception object in log calls to preserve stack traces.
- Maintain existing control flow (re-throwing initialization exceptions where they were previously re-thrown).

## 2. System Architecture

### Target Files
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/ConversationManagerImpl.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/LiteRtInferenceServiceImpl.kt`

### Component Boundaries
- **ConversationManagerImpl**: Manages the lifecycle of the LiteRT `Engine` and `Conversation`. It currently swallows teardown exceptions during engine initialization failure and during explicit `closeEngine()` calls (via `closeConversationLocked`).
- **LiteRtInferenceServiceImpl**: Handles inference flow. It currently swallows teardown exceptions when resetting the engine after an inference failure.

Logging will primarily use `android.util.Log` as it's already used for infrastructure-level events in these classes. `LoggingPort` will be used in `ConversationManagerImpl` where higher-level visibility is appropriate.

## 3. Data Models & Schemas
No changes to data models or schemas.

## 4. API Contracts & Interfaces
No API contract changes.

## 5. Permissions & Config Delta
No permissions or config changes.

## 6. Constitution Audit
This design adheres to the project's core architectural rules.
- **Rule Followed**: "Empirical reproduction of reported issues to confirm the failure state." (Note: In this case, the fix *is* to provide the visibility needed for such empirical reproduction in the future).
- **Rule Followed**: "Rigorously adhere to existing workspace conventions." We are using `android.util.Log` with established `TAG`s.

## 7. Cross-Spec Dependencies
No cross-spec dependencies.

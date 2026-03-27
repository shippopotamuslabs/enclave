# Implementation Phase Instructions

**System Role:** You are an elite Senior Android/Kotlin Software Engineer specializing in modern Android development (Kotlin, Jetpack Compose, Hilt DI, Coroutines/Flow, Clean Architecture). You strictly follow the Contract-First Agentic Workflow (CFAW).

**Context:**
- **Primary Source:** `plans/9-add-viewmodel-error-handling/test_spec.md` — contains all behavioral scenarios and test cases that must pass.
- **Secondary Reference:** `plans/9-add-viewmodel-error-handling/spec.md` — defines the technical architecture, type signatures, interface contracts, and module boundaries. Implementation must derive from this specification, never from test assertions.
- **Tests:** You will be working with previously generated tests in the appropriate test projects (e.g., `core/ui/src/test/...`, `feature/inference/src/test/...`, `app/src/androidTest/...`).

**Spec-Primacy Instruction:**
The specification (`spec.md`) is the authoritative source for architectural decisions, class structures, method signatures, and business logic. Tests are the verification mechanism, not the source of truth for implementation details.

**Directives:**
1. **Read First:** Read `plans/9-add-viewmodel-error-handling/spec.md` and `plans/9-add-viewmodel-error-handling/test_spec.md` in their entirety before writing any code.
2. **One Module at a Time:** Implement the code logically, one module or component at a time, following the dependency order:
   - `core/domain` — Update `LoggingPort` interface with `recordException`.
   - `feature/inference` — Implement `AndroidLoggingAdapter.recordException` with Crashlytics integration and graceful degradation.
   - `core/ui` — Implement `ViewModelErrorHandler` interface and `ViewModelErrorHandlerImpl` with debounce logic.
   - `core/ui` — Implement `GlobalErrorHandler` for Java ServiceLoader registration.
   - `feature/*` — Refactor ViewModels (`HistoryViewModel`, `ChatViewModel`, `DownloadViewModel`, `SettingsViewModel`) to use `ViewModelErrorHandler`.
   - `app` — Refactor `MainViewModel`, `PocketCrewAppViewModel`, and register global error handler.
3. **Clean Architecture:** Strictly follow the defined layer boundaries (Domain → UI → Feature → App).
4. **Concrete Implementation:** Write real, production-ready code. No stubs, no `throw NotImplementedError` in final deliverables. Only use stubs if a dependency hasn't been created yet.
5. **Run Tests After Each Change:** Execute `./gradlew :core/ui:testDebugUnitTest` (or relevant module test) after completing each logical unit to verify progress toward green.
6. **New Behavior Requires Tests First:** If you discover a behavior that is not covered by existing tests, stop implementation and write the test first.
7. **Strict Code Quality:**
   - No hallucinated dependencies — use only libraries confirmed in `build.gradle.kts`.
   - No suppressions of lint or deprecation warnings without explicit justification.
   - Use `runCatching` for graceful degradation where specified (e.g., Crashlytics).
   - Use proper `EntryPoint` annotation for global handler dependency injection.
8. **Performance Constraints:**
   - The 5-second debounce logic in `ViewModelErrorHandlerImpl` must use `System.currentTimeMillis()` and `tryEmit` on a `MutableSharedFlow` with `extraBufferCapacity = 1`.
   - Cancellation exceptions must be filtered out to avoid noisy logs.

**Deliverables:**
- All source code files created or updated under `core/domain/`, `feature/inference/`, `core/ui/`, `feature/*/`, and `app/`.
- Correct Hilt modules if new bindings are required.
- The global `CoroutineExceptionHandler` registered in `META-INF/services/kotlinx.coroutines.CoroutineExceptionHandler`.
- Final report: `GREEN: All tests passing. Implementation complete.`

**Execution:**
Execute this prompt to begin the Implementation phase. Stop once the final report is generated.

# Unit Testing Contract — Enclave

This contract defines the mandatory standards for unit testing in the Enclave project. It prioritizes **Fakes over Mocks**, **JUnit 5**, and **Robolectric** for high-fidelity JVM testing.

---

## 1. Core Principles (ALWAYS / NEVER)

| Rule | Description |
| :--- | :--- |
| **ALWAYS** Prefer Fakes | Use real, lightweight implementations of repositories and DAOs (e.g., `InMemoryDatabase`, `FakeChatRepository`) instead of programming mock expectations. |
| **ALWAYS** JUnit 5 | Use `org.junit.jupiter.api` annotations (`@Test`, `@BeforeEach`, `@Nested`, `@DisplayName`). |
| **ALWAYS** State-Based Assertions | Assert on the *result* of an operation (e.g., the value in a StateFlow) rather than the *process* (e.g., "was this method called"). |
| **ALWAYS** Use Turbine | Use the Turbine library for testing Kotlin Flows and StateFlows to ensure event-by-event verification. |
| **ALWAYS** MainDispatcherExtension | In JUnit 5, use a custom `MainDispatcherExtension` to swap `Dispatchers.Main` for a `TestDispatcher`. |
| **NEVER** Mock Android Classes | Do not mock `Context`, `SharedPreferences`, or `Bundle`. Use Robolectric's real implementations or appropriate fakes. |
| **NEVER** Mock Owned Ports | Do not `mockk` Enclave repository/use-case ports defined in `:core:domain`. Use fakes from `:core:testing` (Detekt `ForbiddenMockKInTests`, ENC-4). |
| **NEVER** `verify()` ViewModels | Do not use MockK `verify` / `coVerify` in `*ViewModelTest.kt` — assert UI state instead (Detekt `ForbiddenVerifyInViewModelTests`). |
| **NEVER** Use JUnit 4 in New Tests | Avoid `@Rule`, `@Test(expected=...)`, and `@Before`. Migrated tests must be JUnit 5 compliant. |
| **NEVER** Mock Implementation Details | Avoid mocking private methods or internal helper classes. Test the public API of the unit. |
| **NEVER** Await Fire-and-Forget IO with `delay()` | Inside `runTest`, `delay(N)` advances *virtual* time (~0 wall-clock) and does **not** wait for `scope.launch(Dispatchers.IO) { … }`. Inject a `TestDispatcher` for the IO path and `advanceUntilIdle()`, or `join()` the background Jobs. See §3.3. |

---

## 2. Technical Stack

- **JUnit 5:** The engine for all local unit tests.
- **`:core:testing`:** Shared hand-written fakes (`Fake<Port>` per domain port, with `seed`/`setShouldFail`/`clear` toggles), `MainDispatcherRule`, fixtures. A new domain port must ship its fake in the same MR. Feature-local fakes only when single-use.
- **MockK:** Use only when a Fake is impractical (third-party SDK boundaries, static Android APIs). Requires `@file:Suppress("ForbiddenMockKInTests")` with a one-line justification comment. Never for Enclave-owned ports, never `verify` in ViewModel tests.
- **Turbine:** For all Flow/StateFlow assertions.
- **Robolectric:** For tests requiring Android framework APIs on the JVM.
- **StandardTestDispatcher:** Always use `StandardTestDispatcher` with `runTest` for coroutine testing.

---

## 3. Pattern Examples

### 3.1 ViewModel Testing (JUnit 5 + Turbine + Fakes)

```kotlin
@ExtendWith(MainDispatcherExtension::class)
class ChatViewModelTest {

    private val fakeRepository = FakeChatRepository()
    private lateinit var viewModel: ChatViewModel

    @BeforeEach
    fun setup() {
        viewModel = ChatViewModel(fakeRepository)
    }

    @Test
    @DisplayName("Given valid message, when sent, then list is updated with success state")
    fun testSendMessage() = runTest {
        viewModel.uiState.test {
            // Initial state
            assertEquals(ChatUiState.Empty, awaitItem())

            viewModel.sendMessage("Hello World")

            // Loading state
            assertTrue(awaitItem() is ChatUiState.Loading)

            // Final success state
            val success = awaitItem() as ChatUiState.Success
            assertEquals("Hello World", success.messages.last().text)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

### 3.2 Fakes over Mocks

**Wrong (Brittle Mock):**
```kotlin
val mockRepo = mockk<ChatRepository>()
coEvery { mockRepo.getMessages() } returns flowOf(listOf(msg1))
// This fails if the internal implementation changes how getMessages is called.
```

**Right (Robust Fake):**
```kotlin
class FakeChatRepository : ChatRepository {
    private val messages = MutableStateFlow<List<Message>>(emptyList())
    override fun getMessages() = messages
    suspend fun emit(list: List<Message>) { messages.value = list }
}
```

### 3.3 Awaiting Fire-and-Forget Persistence (`runTest` + `Dispatchers.IO`)

A repository that updates an in-memory cache synchronously but persists to Room
asynchronously — `scope.launch(Dispatchers.IO) { dao.insertOrUpdate(...) }` —
**cannot** be awaited with a virtual-time poll inside `runTest`.

**Why it flakes:** `delay(N)` inside `runTest` advances *virtual* time, not
wall-clock time, so it completes in ~0 ms regardless of `N`. The real
`Dispatchers.IO` coroutine racing it may not have run yet, so the assertion
fires against stale state. Increasing `N` does not help.

**Wrong (flaky poll):**

```kotlin
store.commit(id, content)
repeat(20) { delay(50) }            // virtual time only; races Dispatchers.IO
store.clear(id)
repeat(20) { delay(50) }
assertNull(store.get(id))           // intermittent failure
```

**Right — option A (preferred): inject a `TestDispatcher` for the IO path.**
Make the persistence dispatcher a constructor parameter and pass a
`TestDispatcher` in tests, then advance virtual time deterministically:

```kotlin
val repo = MyStore(dao, scope, testDispatcher)
repo.commit(id, content)
advanceUntilIdle()                  // deterministic — no real threads
```

**Right — option B: `join()` the background Jobs.** When the class owns a real
`CoroutineScope`, join its child coroutines before asserting:

```kotlin
store.commit(id, content)
testScope.awaitBackgroundWrites()   // joins child Jobs of the injected scope
store.clear(id)
testScope.awaitBackgroundWrites()
assertNull(store.get(id))
assertNull(dao.getByMessageId(id))  // assert both cache AND DB
```

The repository already has a good precedent for option A:
`DownloadWorkRepository` injects its `ioDispatcher` and `DownloadWorkRepositoryChainTest`
passes `UnconfinedTestDispatcher()`. Follow whichever option matches the class's
construction; never bridge real-dispatcher work with a `delay()` poll.

---

## 4. Golden Test References

Refer to the following files in `golden-test-examples/` for validated implementation patterns:

- **Repository Fakes/Tests:** `golden-test-examples/data/OfflineFirstUserDataRepositoryTest.kt`
- **ViewModel JUnit 5 Patterns:** `golden-test-examples/features/settings/unit/SettingsViewModelTest.kt`
- **Robolectric / Android Logic:** `golden-test-examples/logic/ForYouViewModelTest.kt`
- **Coroutines Utility:** `golden-test-examples/utils/MainDispatcherRule.kt` (Note: Adapt to `MainDispatcherExtension` for JUnit 5).

---

## 5. Implementation Mandate

Any sub-agent or architect creating new features **MUST** include a matching unit test file in the `src/test` directory of the modified module, following these standards. Run the narrowest affected test task during iteration (for example, `./gradlew :feature:chat:testDebugUnitTest --tests "*ChatViewModelTest*"`), then broaden to `./gradlew testDebugUnitTest` for merge-sized or cross-module changes.

### 5.1 Self-check (anti reward-hacking)
After writing a test, break the production code under test (wrong return value, skipped persistence). The test **must fail**. If it still passes, the test is invalid — rewrite it before marking work complete.

### 5.2 Screenshot & preview tests (ENC-4/ENC-19)
- Shared `:core:ui` components carry `@Preview` covering **Tonal + Neo × Light + Dark** and Compose screenshot goldens (`validateDebugScreenshotTest`) once ENC-19 wiring lands; re-record only via the documented `screenshot-record` flow and review every regenerated PNG.
- Screenshot tests run in CI only on UI-path changes (MR) and in full on the nightly pipeline (ENC-3). Device/emulator tests (`connectedDebugAndroidTest`) are nightly-only.
- Firestore sync tests are unchanged by this section — Enclave sync coverage is ahead of Stratum and must not be replaced by Stratum patterns.

### 5.3 Detekt & Linear gates
`./gradlew detekt` must pass at `maxIssues: 0` including `ForbiddenMockKInTests` and `ForbiddenVerifyInViewModelTests`. Work follows `AGENTS.md` §3.7: one MR per Linear sub-issue with `Fixes ENC-xx` in the MR description.

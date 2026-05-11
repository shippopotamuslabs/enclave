# Architectural Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sanitize the `:domain` layer, decouple prompt engineering, improve background service test coverage, and decompose the monolithic `ChatViewModel`.

**Architecture:** 
1. Replace `java.io.IOException` in `:domain` with a domain-specific exception to maintain framework purity.
2. Introduce `PromptComposerPort` in `:domain` to move prompt template logic into `:feature:inference`.
3. Extract `ChatViewModel` responsibilities into specialized delegates (`ChatInputCoordinator`, `InferenceOrchestrator`, `TtsController`).
4. Add unit tests for `ChatInferenceServiceExecutor` lifecycle edge cases.

**Tech Stack:** Kotlin, Coroutines, Flow, Hilt, JUnit 5, MockK, Turbine.

---

### Task 1: Sanitize `:domain` from `java.io.IOException` (ARCH-03)

**Files:**
- Create: `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/error/DomainExceptions.kt`
- Modify: `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/DirectChatInferenceExecutor.kt`
- Modify: `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/GenerateChatResponseUseCase.kt`

- [x] **Step 1: Create domain-specific exceptions**
```kotlin
package com.browntowndev.pocketcrew.domain.model.error

class InferenceIOException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

- [x] **Step 2: Run tests to verify existing state**
Run: `./gradlew :core:domain:test`

- [x] **Step 3: Replace IOException in DirectChatInferenceExecutor**
Update imports and catch blocks to use `InferenceIOException`.

- [x] **Step 4: Update GenerateChatResponseUseCase catch block**

- [x] **Step 5: Verify :core:domain compile**
Run: `./gradlew :core:domain:assemble`
Expected: PASS

- [x] **Step 6: Commit**

---

### Task 2: Decouple Prompt Engineering from `:domain` (ARCH-04)

**Files:**
- Create: `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/inference/PromptComposerPort.kt`
- Move: `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/SearchToolPromptComposer.kt` → `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/SearchToolPromptComposerImpl.kt`
- Modify: `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/DirectChatInferenceExecutor.kt`
- Modify: `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/InferenceFactoryImpl.kt`

- [x] **Step 1: Define the Port in :domain**
```kotlin
package com.browntowndev.pocketcrew.domain.port.inference

import com.browntowndev.pocketcrew.domain.model.memory.Memory
import com.browntowndev.pocketcrew.domain.usecase.chat.ToolCallStrategy

interface PromptComposerPort {
    fun compose(
        baseSystemPrompt: String?,
        includeSearchTool: Boolean = true,
        includeXSearchTool: Boolean = false,
        includeImageInspectTool: Boolean = false,
        includeMemoryTools: Boolean = true,
        includeArtifactTool: Boolean = false,
        includeImageGenerationTool: Boolean = false,
        includeVideoGenerationTool: Boolean = false,
        includeMusicGenerationTool: Boolean = false,
        currentChatId: String? = null,
        strategy: ToolCallStrategy = ToolCallStrategy.JSON_XML_ENVELOPE,
        coreMemories: List<Memory> = emptyList(),
        retrievedMemories: List<Memory> = emptyList(),
    ): String
}
```

- [x] **Step 2: Move and implement in :feature:inference**
Update `SearchToolPromptComposerImpl` to implement `PromptComposerPort`.

- [x] **Step 3: Update DirectChatInferenceExecutor to use the Port**

- [x] **Step 4: Update Hilt module in :feature:inference to provide the implementation**

- [x] **Step 5: Verify tests pass**
Run: `./gradlew :feature:inference:test`

- [x] **Step 6: Commit**

---

### Task 3: FGS Lifecycle Edge Case Coverage (TEST-01)

**Files:**
- Create: `feature/chat-inference-service/src/test/kotlin/com/browntowndev/pocketcrew/feature/chat/service/ChatInferenceServiceExecutorTest.kt`

- [x] **Step 1: Write failing tests for ForegroundServiceStartNotAllowedException**
Verify that `execute()` emits `MessageGenerationState.Failed`.

- [x] **Step 2: Run tests to verify failure**

- [x] **Step 3: Write implementation to handle the exception**

- [x] **Step 4: Write tests for generic start failure**

- [x] **Step 5: Run tests**
Run: `./gradlew :feature:chat-inference-service:test`

- [x] **Step 6: Commit**

---

### Task 4: Decompose ChatViewModel (ARCH-02)

**Files:**
- Create: `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/delegates/ChatInputCoordinator.kt`
- Create: `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/delegates/InferenceOrchestrator.kt`
- Create: `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/delegates/TtsPlaybackController.kt`
- Modify: `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatViewModel.kt`

- [x] **Step 1: Create failing tests for extracted behavior**

- [x] **Step 2: Extract Input management to ChatInputCoordinator**
Move `inputText`, `selectedImageUris`, `selectedFileText` flows and handlers.

- [x] **Step 3: Extract Inference logic to InferenceOrchestrator**
Move `sendMessage`, `stopInference`, and related state tracking.

- [x] **Step 4: Extract TTS logic to TtsPlaybackController**

- [x] **Step 5: Refactor ChatViewModel to use delegates**

- [x] **Step 6: Run existing tests and verify UI still works**
Run: `./gradlew :feature:chat:test`

- [x] **Step 7: Commit**

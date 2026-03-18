# Phase 10 Handoff Document: Feature:MOA-Pipeline-Worker

**Date**: 2026-03-18  
**Phase**: 10 of 11  
**Status**: Not Started

---

## Original Modularization Plan Reference

This document supplements the **[modularization-plan.md](./modularization-plan.md)** in the project root. Please refer to that document for:
- Executive summary of the entire modularization effort
- Overall project structure and goals
- Risk mitigation strategies
- Dependencies between phases

---

## Phase 10 Goal Summary

**Extract Feature:MOA-Pipeline-Worker module**

Move background inference pipeline execution components from the `:app` module into the `:feature:moa-pipeline-worker` module.

**Why this matters**: This decouples the background inference orchestration from the app's presentation layer, enabling cleaner architecture and potential reuse.

---

## What Was Done in Previous Phases

### Phases 1-3: Core Infrastructure
- Phase 1: Created empty module structure ✅
- Phase 2: Extracted `core:domain` (models, ports, use cases) ✅
- Phase 3: Extracted `core:data` (repositories, Room, downloads) ✅

### Phase 4: Core:UI
- Extracted theme files (`Color.kt`, `Theme.kt`, `Type.kt`) ✅
- Extracted `ShimmerText` component ✅

### Phase 5: Core:Common
- Extracted `FeatureFlags.kt` ✅

### Phase 6: Feature:Settings
- Moved settings presentation layer to `feature:settings` ✅

### Phase 7: Feature:History
- Moved history presentation layer to `feature:history` ✅

### Phase 8: Feature:Download
- Moved download presentation layer to `feature:download` ✅

### Phase 9: Feature:Chat (Most Recent)
- Moved chat presentation layer to `feature:chat` ✅
- Moved `ChatUseCasesModule` and `InferenceUseCasesModule` to `feature:chat/di/` ✅
- Created resources (strings, drawables) in `feature:chat/src/main/res/` ✅
- Updated navigation import to use `com.browntowndev.pocketcrew.feature.chat.ChatRoute` ✅
- Updated test imports to reflect new package locations ✅

---

## Current State Analysis

### Existing Module Structure

```
feature/
├── moa-pipeline-worker/
│   ├── build.gradle.kts     # Exists, configured
│   └── src/
│       └── main/
│           └── (empty - no source files yet)
```

### Files That Need to Be Moved

According to the modularization plan, the following files should be moved from `:app` to `:feature:moa-pipeline-worker`:

| Source Location | Target Location | Notes |
|-----------------|----------------|-------|
| `inference/worker/InferenceExecutorModule.kt` | `feature:moa-pipeline-worker/di/` | Hilt module binding PipelineExecutorPort |
| `inference/InferenceServicePipelineExecutor.kt` | `feature:moa-pipeline-worker/` | Main pipeline executor implementation |
| `inference/service/InferenceService.kt` | `feature:moa-pipeline-worker/service/` | Foreground service for pipeline execution |
| `inference/service/InferenceServiceStarter.kt` | `feature:moa-pipeline-worker/service/` | Helper to start the service |

### Additional Inference Files (Potential Scope Expansion)

The following files are in the inference package and may need consideration:

| File | Purpose | Notes |
|------|---------|-------|
| `inference/LiteRtInferenceServiceImpl.kt` | LiteRT inference implementation | May belong in app module (Phase 11) |
| `inference/LlamaInferenceServiceImpl.kt` | Llama inference implementation | May belong in app module (Phase 11) |
| `inference/MediaPipeInferenceServiceImpl.kt` | MediaPipe inference implementation | May belong in app module (Phase 11) |
| `inference/ConversationImpl.kt` | Conversation implementation | May belong in app module (Phase 11) |
| `inference/ConversationManagerImpl.kt` | Conversation manager | May belong in app module (Phase 11) |
| `inference/AndroidLoggingAdapter.kt` | Logging adapter | May belong in app module (Phase 11) |
| `inference/llama/*` | Llama-specific implementations | Likely belongs in app or llama-android module |

### Key Dependency Chain

```
InferenceExecutorModule (binds)
    └─> InferenceServicePipelineExecutor
            ├─> PipelineExecutorPort (core:domain)
            ├─> BufferThinkingStepsUseCase (core:domain)
            └─> InferenceServiceStarter
                    └─> InferenceService
```

---

## Step-by-Step Execution Plan

### Step 1: Create Directory Structure

Create the following directories in `feature:moa-pipeline-worker/src/main/kotlin/com/browntowndev/pocketcrew/feature/moa/`:

```
feature:moa-pipeline-worker/
└── src/main/kotlin/com/browntowndev/pocketcrew/feature/moa/
    ├── di/
    ├── service/
    └── (root - for InferenceServicePipelineExecutor)
```

### Step 2: Copy Files with Updated Packages

Files to create in the new module:

1. **`di/InferenceExecutorModule.kt`**
   - Update package from `com.browntowndev.pocketcrew.inference.worker` to `com.browntowndev.pocketcrew.feature.moa.di`
   - Update import for `InferenceServicePipelineExecutor` to the new location

2. **`InferenceServicePipelineExecutor.kt`**
   - Update package from `com.browntowndev.pocketcrew.inference` to `com.browntowndev.pocketcrew.feature.moa`
   - Update internal imports

3. **`service/InferenceService.kt`**
   - Update package from `com.browntowndev.pocketcrew.inference.service` to `com.browntowndev.pocketcrew.feature.moa.service`
   - Update all internal imports

4. **`service/InferenceServiceStarter.kt`**
   - Update package from `com.browntowndev.pocketcrew.inference.service` to `com.browntowndev.pocketcrew.feature.moa.service`
   - Update all internal imports

### Step 3: Create Resources (If Needed)

Check if `InferenceService` requires any resources (strings, drawables, etc.) and copy them to the new module's `res/` folder.

### Step 4: Update Build Configuration

The existing `feature:moa-pipeline-worker/build.gradle.kts` already has some dependencies. Review and update:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.browntowndev.pocketcrew.feature.moa"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    
    // WorkManager
    implementation(libs.work.runtime.ktx)
    
    // Inference
    implementation(project(":llama-android"))
}
```

**Potential additions needed**:
- `libs.kotlinx.coroutines.core` and `libs.kotlinx.coroutines.android` if not transitively included

### Step 5: Delete Original Files

After successful compilation, delete:
- `app/src/main/java/com/browntowndev/pocketcrew/inference/worker/InferenceExecutorModule.kt`
- `app/src/main/java/com/browntowndev/pocketcrew/inference/InferenceServicePipelineExecutor.kt`
- `app/src/main/java/com/browntowndev/pocketcrew/inference/service/InferenceService.kt`
- `app/src/main/java/com/browntowndev/pocketcrew/inference/service/InferenceServiceStarter.kt`

### Step 6: Update App Dependencies

If `InferenceExecutorModule` was the only binding for `PipelineExecutorPort` in the app, you may need to ensure Hilt can still find the binding from the new module. Verify `app/build.gradle.kts` includes:

```kotlin
implementation(project(":feature:moa-pipeline-worker"))
```

---

## Verification Checklist

After completing Phase 10, run these commands and confirm results:

```bash
# 1. Build the new module
./gradlew :feature:moa-pipeline-worker:assembleDebug
# Expected: BUILD SUCCESSFUL

# 2. Build the app
./gradlew :app:assembleDebug
# Expected: BUILD SUCCESSFUL

# 3. Run ktlint
./gradlew ktlintCheck
# Expected: BUILD SUCCESSFUL

# 4. Run detekt
./gradlew detekt
# Expected: BUILD SUCCESSFUL (or pre-existing issues only)

# 5. Run tests
./gradlew testDebugUnitTest
# Expected: All tests pass
```

---

## Known Challenges & Notes

### 1. Circular Dependency Risk
`InferenceServicePipelineExecutor` depends on `InferenceService` (in the same module after move), but `InferenceService` broadcasts to receivers that may be registered elsewhere. Ensure the service intent actions remain consistent.

### 2. Context Injection
`InferenceServicePipelineExecutor` uses `@ApplicationContext` injection. This should work in a library module, but verify Hilt bindings are correctly scoped.

### 3. WorkManager Quota Issue
The comments in `InferenceServicePipelineExecutor` mention this replaces WorkManager-based approach due to Android 15+ quota limits. The service uses `specialUse` foreground type - ensure this is still correctly configured in the manifest after move.

### 4. Service Manifest Configuration
The service likely requires manifest entries for:
- Foreground service permission
- `specialUse` foreground service type
- Intent filters for broadcast actions

You may need to add a `src/main/AndroidManifest.xml` to `feature:moa-pipeline-worker` with the necessary service declaration.

### 5. Test File Updates
If there are tests for these files in `app/src/test/`, update their package imports to reference the new module location.

---

## Files to Reference

| File | Purpose |
|------|---------|
| `./modularization-plan.md` | Original plan document (master reference) |
| `./feature/chat/` | Example of recently completed feature extraction (Phase 9) |
| `./feature/moa-pipeline-worker/build.gradle.kts` | Current build config for Phase 10 target |
| `./app/src/main/java/com/browntowndev/pocketcrew/inference/worker/InferenceExecutorModule.kt` | Source file to move |
| `./app/src/main/java/com/browntowndev/pocketcrew/inference/InferenceServicePipelineExecutor.kt` | Source file to move |

---

## Phase 11 Preview

After Phase 10, Phase 11 is **Cleanup App Module** - final reduction of `:app` to navigation and DI wiring only. This includes:

- Moving remaining inference implementations to `:app/inference/`
- Consolidating DI modules to `:app/di/`
- Final cleanup of `presentation/` layer

---

## Handoff Checklist Summary

- [ ] Review and understand the dependency chain
- [ ] Create directory structure in `feature:moa-pipeline-worker`
- [ ] Copy files with updated package declarations
- [ ] Create AndroidManifest.xml if service declarations needed
- [ ] Update/verify build.gradle.kts dependencies
- [ ] Verify `app/build.gradle.kts` includes the new module
- [ ] Run `./gradlew :feature:moa-pipeline-worker:assembleDebug`
- [ ] Run `./gradlew :app:assembleDebug`
- [ ] Run `./gradlew ktlintCheck`
- [ ] Run `./gradlew detekt`
- [ ] Run `./gradlew testDebugUnitTest`
- [ ] Delete original files from `app/`
- [ ] Commit changes

---

*Generated for Pocket Crew modularization effort - Phase 10 handoff*

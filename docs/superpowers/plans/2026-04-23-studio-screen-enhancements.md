# StudioScreen Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enhance StudioScreen to support video reference media and improve the UI by integrating the reference thumbnail into the input bar.

**Architecture:** Update `StudioUiState` and `MultimodalViewModel` to track and manage reference media type. Refactor `StudioScreen` to move `ReferenceImageThumbnail` into `UniversalInputBar`'s `attachmentContent` slot and update it to support video thumbnails using `VideoThumbnail`.

**Tech Stack:** Jetpack Compose, Hilt, Coil3, Kotlin Coroutines, Material 3.

---

### Task 1: Update StudioUiState

**Files:**
- Modify: `feature/studio/src/main/kotlin/com/browntowndev/pocketcrew/feature/studio/StudioUiState.kt`

- [ ] **Step 1: Add referenceMediaType to StudioUiState**

```kotlin
@Immutable
data class StudioUiState(
    // ... existing properties ...
    val referenceMediaType: MediaCapability? = null,
    val isPlayingTts: Boolean = false,
)
```

- [ ] **Step 2: Commit**

```bash
git add feature/studio/src/main/kotlin/com/browntowndev/pocketcrew/feature/studio/StudioUiState.kt
git commit -m "feat(studio): add referenceMediaType to StudioUiState"
```

### Task 2: Update MultimodalViewModel

**Files:**
- Modify: `feature/studio/src/main/kotlin/com/browntowndev/pocketcrew/feature/studio/MultimodalViewModel.kt`

- [ ] **Step 1: Add _referenceMediaType StateFlow and track it in uiState**

- [ ] **Step 2: Update onEditMedia, onAnimateMedia, onUpdateReferenceImage, and onClearReferenceImage to set/clear referenceMediaType**

- [ ] **Step 3: Commit**

```bash
git add feature/studio/src/main/kotlin/com/browntowndev/pocketcrew/feature/studio/MultimodalViewModel.kt
git commit -m "feat(studio): track reference media type in MultimodalViewModel"
```

### Task 3: Enhance StudioScreen UI

**Files:**
- Modify: `feature/studio/src/main/kotlin/com/browntowndev/pocketcrew/feature/studio/StudioScreen.kt`

- [ ] **Step 1: Update ReferenceImageThumbnail to support video thumbnails**

```kotlin
@Composable
private fun ReferenceImageThumbnail(
    uri: String,
    mediaType: MediaCapability?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (mediaType == MediaCapability.VIDEO) {
            VideoThumbnail(
                videoUri = uri,
                contentDescription = "Reference video thumbnail",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = uri,
                contentDescription = "Reference image thumbnail",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        // ... close button ...
    }
}
```

- [ ] **Step 2: Refactor StudioInputDock to move ReferenceImageThumbnail inside UniversalInputBar's attachmentContent slot**

- [ ] **Step 3: Adjust padding for ReferenceImageThumbnail**

- [ ] **Step 4: Run android_code_validator on modified files**

- [ ] **Step 5: Commit**

```bash
git add feature/studio/src/main/kotlin/com/browntowndev/pocketcrew/feature/studio/StudioScreen.kt
git commit -m "feat(studio): integrate reference thumbnail into UniversalInputBar and support video"
```

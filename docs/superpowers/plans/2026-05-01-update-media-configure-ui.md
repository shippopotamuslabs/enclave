# Update MediaConfigureScreen for Dynamic Model Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Update `MediaConfigureScreen.kt` to support dynamic model selection from a dropdown and add a "Fetch Available Models" button.

**Architecture:** 
- Modify `MediaConfigureRoute` to pass the `onFetchMediaModels` callback.
- Modify `MediaConfigureScreen` to accept the new callback and use `ExposedDropdownMenuBox` for model selection.
- Add a "Fetch Available Models" button that triggers the discovery process.
- Handle loading states and empty results in the UI.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Hilt, AAC ViewModel.

---

### Task 1: Update `MediaConfigureRoute`

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/MediaConfigureScreen.kt`

- [ ] **Step 1: Pass `onFetchMediaModels` to `MediaConfigureScreen` in `MediaConfigureRoute`**

```kotlin
// ... existing code ...
    MediaConfigureScreen(
        uiState = uiState,
        apiKey = apiKey,
        onNavigateBack = handleBack,
        onMediaAssetFieldChange = viewModel::onMediaAssetFieldChange,
        onApiKeyChange = viewModel::onApiKeyChange,
        onSelectReusableApiCredential = viewModel::onSelectReusableApiCredential,
        onFetchMediaModels = viewModel::onFetchMediaModels, // Add this
        onSaveMediaProvider = {
// ... existing code ...
```

- [ ] **Step 2: Verify it compiles (it won't yet until next task)**

### Task 2: Update `MediaConfigureScreen` Signature and Add Fetch Button

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/MediaConfigureScreen.kt`

- [ ] **Step 1: Update `MediaConfigureScreen` signature**

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaConfigureScreen(
    uiState: SettingsUiState,
    apiKey: String,
    onNavigateBack: () -> Unit,
    onMediaAssetFieldChange: (MediaProviderAssetUi) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSelectReusableApiCredential: (ApiCredentialsId?) -> Unit,
    onFetchMediaModels: () -> Unit, // Add this
    onSaveMediaProvider: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
// ...
```

- [ ] **Step 2: Add "Fetch Available Models" button**

Add it below the API Key field or Display Name field. Based on instructions, "Add a 'Fetch Available Models' button below the API Key/Base URL section".

```kotlin
// ... existing code for API Key field ...
            // Add this button
            Button(
                onClick = onFetchMediaModels,
                enabled = apiKey.isNotBlank() || isKeySaved,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Fetch Available Models")
            }
// ... existing code for Display Name field ...
```

- [ ] **Step 3: Update `MediaConfigureScreenPreview` and `MediaConfigureRoute` call**

Ensure `MediaConfigureRoute` and `MediaConfigureScreenPreview` pass the new parameter.

### Task 3: Replace Model ID TextField with Dropdown

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/MediaConfigureScreen.kt`

- [ ] **Step 1: Replace Model ID `OutlinedTextField` with `ExposedDropdownMenuBox`**

```kotlin
// ...
    var modelDropdownExpanded by remember { mutableStateOf(false) } // Add this state

    // ... inside Column ...
            // Replace:
            // OutlinedTextField(
            //     value = draft.modelName,
            //     onValueChange = { onMediaAssetFieldChange(draft.copy(modelName = it)) },
            //     label = { Text("Model ID (e.g. dall-e-3)") },
            //     modifier = Modifier.fillMaxWidth(),
            //     shape = RoundedCornerShape(12.dp)
            // )

            // With:
            ExposedDropdownMenuBox(
                expanded = modelDropdownExpanded,
                onExpandedChange = { modelDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = draft.modelName,
                    onValueChange = { onMediaAssetFieldChange(draft.copy(modelName = it)) },
                    label = { Text("Model ID (e.g. dall-e-3)") },
                    trailingIcon = {
                        if (uiState.mediaProviderEditor.isDiscoveringApiModels) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded)
                        }
                    },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = modelDropdownExpanded,
                    onDismissRequest = { modelDropdownExpanded = false }
                ) {
                    val discoveredModels = uiState.mediaProviderEditor.discoveredApiModels
                    if (discoveredModels.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No models found. Try fetching or type manually.") },
                            onClick = { },
                            enabled = false
                        )
                    } else {
                        discoveredModels.forEach { discoveredModel ->
                            DropdownMenuItem(
                                text = { Text(discoveredModel.name ?: discoveredModel.modelId) },
                                onClick = {
                                    onMediaAssetFieldChange(draft.copy(modelName = discoveredModel.modelId))
                                    modelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
// ...
```

### Task 4: Verification

- [ ] **Step 1: Build the project**

Run: `./gradlew :feature:settings:assembleDebug`

- [ ] **Step 2: Verify UI in Preview or App**

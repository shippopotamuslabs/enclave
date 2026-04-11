# Enable Vision Support

## Objective

Enable vision support in the Pocket Crew app by allowing users to attach images via the `InputBar`, analyzing those images using a dedicated vision model, and feeding the resulting description into the existing Fast, Thinking, or Crew mode pipelines. This must be designed cleanly without bloating the `GenerateChatResponseUseCase`.

## Implementation Plan

- [ ] Task 1. **Update Domain Models for Image Support**
  - Update `Content` data class in `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/chat/Content.kt` to include `val imageUri: String? = null`.
  - Update `GenerationOptions` in `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/GenerationOptions.kt` to include `val imageUris: List<String> = emptyList()`.

- [ ] Task 2. **Update Database Entities and Mappers**
  - Update `MessageEntity` in `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/MessageEntity.kt` to include `@ColumnInfo(name = "image_uri") val imageUri: String? = null`.
  - Update `toDomain()` and `toEntity()` extension functions in `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/mapper/MessageMapper.kt` to map the new `imageUri` field.
  - Create a Room database migration to add the `image_uri` TEXT column to the `message` table.

- [ ] Task 3. **Create `AnalyzeImageUseCase`**
  - Create a new use case `AnalyzeImageUseCase` in `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/AnalyzeImageUseCase.kt`.
  - Inject `InferenceFactoryPort` and `LoggingPort`.
  - Implement the `invoke(imageUri: String, prompt: String): String` method.
  - Inside `invoke`, request the `ModelType.VISION` service from `InferenceFactoryPort`.
  - Construct `GenerationOptions` with `modelType = ModelType.VISION` and `imageUris = listOf(imageUri)`.
  - Call `service.sendPrompt` with a default vision prompt (e.g., "Describe this image in detail...") and collect the response chunks into a single description string.
  - Handle exceptions gracefully (e.g., if the vision model is not configured, return a fallback message).

- [ ] Task 4. **Refactor `GenerateChatResponseUseCase`**
  - Inject `AnalyzeImageUseCase` into `GenerateChatResponseUseCase`.
  - In the `invoke` method, after fetching the user message via `messageRepository.getMessageById(userMessageId)`, check if `userMessage.content.imageUri` is not null.
  - If an image is present, emit a `MessageGenerationState.Processing` state for the main model type to show a loading indicator.
  - Call `analyzeImageUseCase(imageUri, prompt)` to get the image description.
  - Modify the `prompt` by prepending the description (e.g., `[Attached Image Description: <description>]\n\n<original prompt>`).
  - Pass the modified prompt down to the existing `baseFlow` logic for Fast, Thinking, or Crew modes.

- [ ] Task 5. **Update `ChatViewModel`**
  - Add a `_selectedImageUri = MutableStateFlow<String?>(null)` state.
  - Update the `uiState` combine function to include `_selectedImageUri` and expose it in `ChatUiState`.
  - Add `fun onImageSelected(uri: String?)` to handle image selection.
  - Add an image caching utility (or use `Context.contentResolver`) to copy the selected `content://` URI to a local `file://` URI in the app's cache directory to avoid URI permission expiration issues.
  - Update `onSendMessage()` to capture the cached `imageUri`, include it in the `Content` object of the new `Message`, and clear the `_selectedImageUri` state.
  - Ensure `onSendMessage()` allows sending a message if *either* the text is not blank OR an image is attached.

- [ ] Task 6. **Update `ChatScreen` and `InputBar`**
  - In `ChatScreen.kt`, implement `rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia())` to handle image selection.
  - Pass the launcher trigger to `InputBar`'s `onAttach` callback.
  - Pass the `selectedImageUri` from `ChatUiState` to `InputBar`.
  - Update `InputBar.kt` to display a small thumbnail preview of the selected image (using Coil's `AsyncImage` or similar) above the text input field, along with a clear/remove button.
  - Update the Send button's enabled state to be active if either text is present or an image is attached.

- [ ] Task 7. **Update Inference Service Implementations**
  - Ensure that the underlying `LlmInferencePort` implementations (e.g., `AnthropicInferenceServiceImpl`, `GoogleInferenceServiceImpl`, etc.) read the `imageUris` from `GenerationOptions` and properly format them for their respective APIs (e.g., converting local file URIs to Base64 strings or multipart form data).

## Verification Criteria

- [ ] User can tap the attach icon in the `InputBar` and select an image from the device gallery.
- [ ] Selected image is displayed as a thumbnail preview in the `InputBar`.
- [ ] User can remove the selected image before sending.
- [ ] User can send a message with an image and optional text.
- [ ] The `AnalyzeImageUseCase` successfully intercepts the image, calls the vision model, and returns a text description.
- [ ] The text description is appended to the prompt and processed correctly by the Fast, Thinking, or Crew models.
- [ ] `GenerateChatResponseUseCase` remains clean and delegates vision processing entirely to `AnalyzeImageUseCase`.
- [ ] Image URI is successfully persisted to the local database and restored on app restart.

## Potential Risks and Mitigations

1. **Risk:** URI Permission Expiration. Android `content://` URIs granted by the picker may expire or become inaccessible in background processes.
   **Mitigation:** `ChatViewModel` will copy the selected image to the app's internal cache directory and use the local `file://` URI for persistence and processing.

2. **Risk:** Vision Model Unavailability. The user may not have a `VISION` model configured.
   **Mitigation:** `AnalyzeImageUseCase` will catch `IllegalStateException` or similar errors from the `InferenceFactoryPort` and return a fallback string indicating the vision model is missing, preventing the entire chat pipeline from crashing.

3. **Risk:** Large Image Processing Overhead. Converting large images to Base64 in the inference services could cause memory issues (OOM).
   **Mitigation:** The image caching utility should downscale or compress images to a reasonable resolution (e.g., max 1024x1024) before saving them to the cache directory.

## Alternative Approaches

1. **Direct Multimodal Support:** Instead of translating the image to text first, we could pass the image directly to the Fast/Thinking/Crew models. 
   *Trade-offs:* This would require *all* configured models to support multimodal inputs, which is highly restrictive (e.g., many local models or smaller API models do not support vision). The translation approach guarantees compatibility with any text-based model.
   
2. **Background Processing Service:** Move the image analysis to a background WorkManager task.
   *Trade-offs:* Overkill for a real-time chat application where the user is actively waiting for a response. The current Flow-based architecture handles asynchronous generation well.
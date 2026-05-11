# Spec: SHARE-CHAT-001 - Share Chat Feature

## 1. Business Logic & Constraints
- Users can share a single chat turn (user message + assistant response) or the entire conversation.
- Shared content is stored in a Firestore collection named `shared_chats`.
- Each document in `shared_chats` must have an `expiresAt` timestamp set to 7 days from creation for TTL.
- The sharing process returns a unique URL (e.g., `https://pocketcrew-api.web.app/share/{shareId}`).
- The web page rendered by the URL must display the shared messages and a button "Open in Pocket Crew" which triggers a deep link: `pocketcrew://shared/{shareId}`.
- When the deep link is clicked, the app should open and display the shared content in a read-only view.

## 2. Target Files
1. `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/share/ShareModels.kt` (New: `SharedChat`, `SharedMessage` data classes, and `ShareRepositoryPort` interface)
2. `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/share/ShareChatUseCase.kt` (New: Handles logic for preparing and uploading shared content)
3. `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/FirestoreShareRepository.kt` (New: Implementation of `ShareRepositoryPort` using Firebase Firestore)
4. `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/components/ShareBottomSheet.kt` (New: UI for selecting share scope - Turn vs Full)
5. `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/components/AssistantResponse.kt` (Modify: Add share icon button to assistant response action row)
6. `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatViewModel.kt` (Modify: Handle share actions, manage share sheet state, and call use case)
7. `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatModels.kt` (Modify: Update `ChatUiState` with sharing related fields)
8. `app/src/main/kotlin/com/browntowndev/pocketcrew/presentation/navigation/PocketCrewNavGraph.kt` (Modify: Add route for shared chat view)
9. `app/src/main/AndroidManifest.xml` (Modify: Add intent filter for `pocketcrew://shared/*`)
10. `functions/src/index.ts` (New: Firebase Cloud Function to render HTML for shared chats)

## 3. Implementation Plan

### Phase 1: Domain & Data
- Define `SharedMessage(role: String, content: String)` and `SharedChat(id: String, messages: List<SharedMessage>, expiresAt: Long)`.
- Create `ShareRepositoryPort` with `suspend fun uploadSharedChat(chat: SharedChat): Result<String>`.
- Implement `FirestoreShareRepository` using `FirebaseFirestore.getInstance()`. Ensure `expiresAt` is handled correctly for TTL.
- Implement `ShareChatUseCase` to fetch required messages from `ChatRepository` and map them to `SharedChat` before uploading.

### Phase 2: UI State & ViewModel
- Update `ChatUiState` in `ChatModels.kt` to include:
    - `isShareSheetVisible: Boolean = false`
    - `isSharingInProgress: Boolean = false`
    - `pendingShareTurn: MessageId? = null`
- In `ChatViewModel.kt`:
    - `onShareIconClicked(messageId: MessageId)`: Sets `pendingShareTurn` and shows bottom sheet.
    - `onShareTurnConfirmed()`: Triggers use case for a single turn.
    - `onShareFullChatConfirmed()`: Triggers use case for full chat.
    - `onShareSuccess(url: String)`: Triggers system share sheet with the URL.

### Phase 3: UI Components
- Modify `AssistantResponse.kt` to add a `IconButton` with the share icon (e.g., `Icons.Default.Share`).
- Create `ShareBottomSheet.kt` using `ModalBottomSheet`. It should offer two options: "Share this message pair" and "Share full conversation".

### Phase 4: Navigation & Deep Linking
- Add `<intent-filter>` to the main activity in `AndroidManifest.xml` for `scheme="pocketcrew"` and `host="shared"`.
- Update `PocketCrewNavGraph.kt` to handle the new `shared/{shareId}` route. For now, this can navigate to a simplified version of the Chat screen that fetches data from `ShareRepository`.

### Phase 5: Cloud Function
- Initialize a new Firebase project (if not already done) and create `functions/src/index.ts`.
- Implement an `onRequest` function that:
    - Extracts `shareId` from the path.
    - Fetches the document from Firestore.
    - Renders a clean HTML page with the messages.
    - Includes a styled button linking to `pocketcrew://shared/${shareId}`.

## 4. Verification Plan

### Automated Tests
- **Unit Tests (`core:domain`)**: Verify `ShareChatUseCase` correctly filters and maps messages for both "turn" and "full" scopes.
- **Unit Tests (`core:data`)**: Mock Firestore to verify `FirestoreShareRepository` correctly formats documents and handles success/failure.
- **ViewModel Tests (`feature:chat`)**: Verify `ChatViewModel` correctly manages UI state transitions during the sharing flow.

### Manual Verification
1. Open a chat and click the share icon on an assistant response.
2. Select "Share this message pair" in the bottom sheet.
3. Verify the system share sheet appears with a URL.
4. Copy the URL and open it in a browser.
5. Verify the web page displays the correct message pair.
6. Click "Open in Pocket Crew" on the web page.
7. Verify the app opens and displays the shared content.
8. Repeat for "Share full conversation".

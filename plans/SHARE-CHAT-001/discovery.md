# Discovery: Share Chat Feature

## 1. Goal Summary
The goal is to implement a chat sharing feature that allows users to share either a specific chat turn (message pair) or the entire conversation. Shared content will be stored in Firestore with a 7-day TTL and rendered as a web page via a Firebase Cloud Function. The web page will include a deep link button to open the shared chat within the app.

## 2. Target Module Index
### Existing Data Models
- `Message`, `Role`, `ChatId`, `MessageId` (Located in `core:domain`)
- `ChatUiState` (Located in `feature:chat`)

### Dependencies & API Contracts
- **Firebase:** BOM, Firestore, and Auth (Already integrated in `app/build.gradle.kts`)
- **Repositories:** `ChatRepository` port (`core:domain`) and `ChatRepositoryImpl` (`core:data`)
- **Navigation:** `PocketCrewNavGraph.kt` (`app`)

### Utility/Shared Classes
- `ChatUseCases` container (`core:domain`)

### Impact Radius
- `core:domain`: Introduction of `SharedChat` models and `ShareRepositoryPort`.
- `core:data`: Implementation of `ShareRepositoryImpl` for Firestore operations.
- `feature:chat`:
    - `AssistantResponse.kt`: Add share icon to the action row.
    - `ChatViewModel.kt`: Handle share actions and state.
    - `ChatModels.kt`: Update `ChatUiState` for share sheet visibility.
- `app`: Update `AndroidManifest.xml` with intent filters for deep link handling.
- **External**: New Firebase Cloud Function (TypeScript) for HTML rendering.

## 3. Cross-Probe Analysis
- **Persistence Pattern**: The project uses a clean architecture pattern with repository ports in the domain layer. The new sharing functionality should follow this, introducing a `ShareRepository` to abstract Firestore interactions.
- **UI State Expansion**: `ChatUiState` is the central source of truth for the chat screen. The share sheet's visibility and data (single turn vs full chat) must be integrated into this state to maintain consistency.
- **Deep Link Strategy**: The rendered HTML page must contain a button with a URI scheme that matches the intent filter in the app. This requires coordination between the Cloud Function output and the Android Manifest configuration.
- **Infrastructure**: Firebase dependencies are already present, simplifying the integration of Firestore for storage.

## 4. High-Impact Clarifying Questions
1. **Cloud Function Location**: Where should the TypeScript source for the Firebase Cloud Function reside within the repository structure?
2. **Deep Link URI Pattern**: What is the designated scheme and host for the deep links (e.g., `pocketcrew://share/{sharedChatId}`)?
3. **Shared Data Schema**: Should the shared Firestore document store the full `Message` objects or a simplified representation for the web view?

## 5. Probe Coverage Summary
- **Domain Layer**: Thoroughly indexed; identified necessary new ports and models.
- **Data Layer**: Identified existing Firebase integration and implementation patterns.
- **UI Layer**: Located specific components (`AssistantResponse`) and state holders (`ChatViewModel`) for modification.
- **External**: Identified the need for Manifest updates and a new backend component.

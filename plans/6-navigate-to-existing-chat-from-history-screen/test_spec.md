# Test Specification — 6-navigate-to-existing-chat-from-history-screen

## Behavioral Scenarios

### Scenario 1: Navigate to existing chat from History
**Given** the user is on the History screen
**And** there is an existing chat with ID `42`
**When** the user taps the chat with ID `42`
**Then** the app navigates to the route `chat?chatId=42`
**And** the `ChatViewModel` receives `42` from its `SavedStateHandle`
**And** the `ChatUiState` emits `chatId = 42`
**And** the messages for chat `42` are loaded and displayed

### Scenario 2: Start a new chat from History
**Given** the user is on the History screen
**When** the user taps the "New Chat" button
**Then** the app navigates to the route `chat` (without the chatId query parameter)
**And** the `ChatViewModel` receives `null` for `chatId` from its `SavedStateHandle`
**And** the `ChatUiState` emits `chatId = -1L`
**And** the Chat screen is displayed with an empty message list

### Scenario 3: Process death restoration
**Given** the user is on the Chat screen viewing chat ID `42`
**When** the system kills the process and the user returns to the app
**Then** the `SavedStateHandle` restores the `chatId` as `"42"`
**And** the `ChatViewModel` resumes loading messages for chat `42`
**And** the `ChatUiState` emits `chatId = 42`

### Scenario 4: Switching chats updates the active ID
**Given** the user is on the Chat screen viewing chat ID `42`
**When** the user navigates back to History and selects chat ID `99`
**Then** the `ChatViewModel` receives `99` from its `SavedStateHandle`
**And** the `ChatUiState` emits `chatId = 99`
**And** the messages for chat `99` are loaded and displayed

## Error Paths

### Error 1: Invalid chatId format in deep link
**Given** the user navigates to the Chat screen via a deep link or malformed route
**And** the route is `chat?chatId=invalid_string`
**When** the `ChatViewModel` initializes
**Then** the `initialChatId` property safely parses `"invalid_string"` to `null`
**And** the `ChatUiState` emits `chatId = -1L`
**And** the Chat screen loads as a new, empty chat

### Error 2: Chat ID not found in database
**Given** the user navigates to `chat?chatId=999`
**And** chat ID `999` does not exist in the database
**When** the `ChatViewModel` attempts to load messages
**Then** the repository returns an empty list or error
**And** the `ChatUiState` emits an empty message list
**And** (future/optional) a "chat not found" error state is handled gracefully
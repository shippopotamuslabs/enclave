# Test Specification — 14-implement-chat-history-search

## Behavioral Scenarios

### Scenario 1: Initial State Shows All Chats
**Given** the user navigates to the History screen and there are 3 total chats in the database
**When** the search query is an empty string `""`
**Then** the UI state emits a list of exactly 3 chats
**And** the loading state is `false`

### Scenario 2: Search by Chat Name
**Given** the database contains a chat named "Project Plan" and a chat named "Random Ideas"
**When** the user types "Project" into the search bar
**Then** the UI state emits a list containing exactly 1 chat named "Project Plan"

### Scenario 3: Search by Message Content
**Given** the database contains a chat with a message "Kotlin coroutines are great" and another chat with "Java is okay"
**When** the user types "coroutines" into the search bar
**Then** the UI state emits a list containing exactly 1 chat (the one containing the Kotlin message)

### Scenario 4: No Matching Results
**Given** the database contains 5 various chats
**When** the user types "xyz123nonexistent" into the search bar
**Then** the UI state emits an empty list for `pinnedChats`
**And** the UI state emits an empty list for `otherChats`

### Scenario 5: Search Query Debouncing
**Given** the user is typing rapidly in the search bar
**When** the user types "h", "e", "l", "l", "o" with less than 300ms between keystrokes
**Then** the search operation is executed exactly 1 time with the final string "hello"

### Scenario 6: Clear Search Query Restores All Chats
**Given** the user has an active search query "Project" that results in 1 chat being displayed out of 5 total
**When** the user clears the search bar to an empty string `""`
**Then** the UI state updates to emit the full list of 5 chats

## Error Paths

### Error 1: FTS Query with Special Characters
**Given** the database contains a chat with the message "Hello world"
**When** the user types invalid FTS characters like `"*^" OR AND`
**Then** the system does not crash
**And** the search query returns a valid empty list or matching results without throwing an exception

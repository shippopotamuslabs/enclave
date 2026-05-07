# Test Spec: SHARE-CHAT-001 - Share Chat Feature

## 1. Scenario: Sharing a single chat turn
**Given** I am in an active chat session with at least one message pair
**When** I click the share icon on the assistant's response
**And** I select "Share this message pair" from the share options
**Then** I should see a loading indicator
**And** once the upload is complete, the system share sheet should open with a generated URL
**And** the URL should contain a unique identifier

## 2. Scenario: Sharing the full conversation
**Given** I am in an active chat session with multiple message pairs
**When** I click the share icon on any assistant's response
**And** I select "Share full conversation" from the share options
**Then** I should see a loading indicator
**And** once the upload is complete, the system share sheet should open with a generated URL
**And** the URL should point to a web page containing all messages in the conversation

## 3. Scenario: Viewing a shared chat via web page
**Given** I have a valid share URL for a chat turn
**When** I navigate to the URL in a web browser
**Then** I should see the user's message and the assistant's response clearly rendered
**And** I should see an "Open in Pocket Crew" button

## 4. Scenario: Opening a shared chat via deep link
**Given** I am viewing a shared chat on the web page
**When** I click the "Open in Pocket Crew" button
**Then** the Pocket Crew app should open
**And** it should navigate to a screen displaying the shared messages
**And** the view should be read-only (input bar disabled or hidden)

## 5. Scenario: Error handling during sharing
**Given** I am attempting to share a chat
**When** the network connection is lost or the Firestore upload fails
**Then** the loading indicator should disappear
**And** I should see an error message indicating that sharing failed
**And** the share bottom sheet should remain or allow me to retry

## 6. Scenario: Shared chat expiration
**Given** a chat was shared more than 7 days ago
**When** a user attempts to navigate to the share URL
**Then** the web page should display a "Link expired" message (handled by Cloud Function/Firestore TTL)

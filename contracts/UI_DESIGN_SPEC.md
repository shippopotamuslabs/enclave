# UI_DESIGN_SPEC.md
**Pocket Crew – UI Design Contract v2.4**

Immutable UI contract. This defines exact visual behavior, component structure, and interaction patterns.

Always enforce together with `ARCHITECTURE_RULES.md` (which defines the structural Scaffold/navigation/IME patterns).

---

## Strict Boundaries

| Always | Never |
|--------|-------|
| Use Material 3 Expressive theming & dynamic layout reflows (`PaneScaffold`). | Hard-code hex colors outside the theme definitions (except specific semantic colors). |
| Include unified `@Preview` (`androidx.compose.ui.tooling.preview`) for states. | Inject real ViewModels or Hilt dependencies into `@Preview` functions. |
| Use lambda-based deferred state reads (e.g., `Modifier.offset { ... }`). | Tie UI recompositions directly to rapidly emitting state values directly in modifiers. |
| Use 20.dp asymmetric corners for message bubbles. | Rely on `paddingValues` threading down the UI tree from a root Scaffold. |
| Support Dynamic Color on Android 12+ (API 31+). | Introduce layout thrash when the keyboard opens/closes. |
| Clear the input text immediately after send. | Recompose the entire message list when the user is typing. |

## 1. Design Language & Theme


- Dark-first, premium AI aesthetic (X/Grok-inspired).
- Unidirectional data flow. Full state hoisting. Minimal recompositions.
- **Exceptions to Theme:** Shield red (`Color.Red`) and success green may be hardcoded for specific semantic states.

### 1.1 Colors
**Dark Theme (Default):**
- Background: `#000000` (AMOLED black)
- Surface: `#121212`
- SurfaceVariant: `#1F1F1F`
- Primary: `#A855F7` (purple accent)
- OnBackground: `#FFFFFF` (pure white)
- OnSurface: `#E5E7EB` (soft white)

**Light Theme:**
- Background: `#FCFCFC`
- Surface: `#F8F9FA`
- Primary: `#A855F7` (same purple)

### 1.2 Typography

- **Headlines:** SemiBold, 24-28sp (top bar title, mode switcher).
- **Body:** Normal, 16sp (message content, standard text).

## 2. Chat Screen Structure

### 2.1 Scaffold Ownership
`ChatScreen` owns its own `Scaffold` with:
- `topBar` = `ChatTopBar`
- `snackbarHost` = local `SnackbarHost`
- `contentWindowInsets = WindowInsets(0)`
- **Content:** `Column` with `weight(1f)` message area + `InputBar` (see `ARCHITECTURE_RULES.md` for exact IME padding pattern).

### 2.2 ChatTopBar (`CenterAlignedTopAppBar`)
- **Left:** Menu icon → navigates to History (via callback).
- **Center:** "Pocket Crew" title + optional `ModeBadge` (visible only during Crew/Think mode).
- **Right:** New chat icon → triggers `viewModel.createNewChat()`.

### 2.3 MessageList (`LazyColumn`)
- `reverseLayout = true`
- `verticalArrangement = Arrangement.Bottom`
- Render `MessageBubble` items based on `role` (User vs. Assistant).
- **Bubble Corners:** 20.dp asymmetric. User bubbles have a sharper bottom-right corner (`4.dp`). Assistant bubbles have a sharper bottom-left corner (`4.dp`).

## 3. Input Bar Styling (Preserve Exactly)

**Do not alter these exact styling constraints:**
- **Standard Layout:** `Row` containing text field (`weight(1f)`) and send button.
- **Shape:** `RoundedCornerShape(28.dp)`.
- **Behavior:** Multi-line, max 6 lines before internal scroll. Clear text immediately after send.
- **Text Area:** `padding(start = 16.dp)`.
- **Typography:** `fontSize = 16.sp`, `lineHeight = 22.sp`.
- **Collapse Icon:** Placed at `Alignment.TopEnd` with zero padding offset.
- **Container:** Horizontal padding `16.dp` on the outer Surface.
- **Action Row:** Icon spacing `4.dp`.
- **Future Support:** Ensure layout can accommodate attachment chips above the text field.

## 4. History Screen

- `HistoryScreen` owns its own `Scaffold` with `HistoryTopBar`.
- **TopAppBar:** Back arrow (left), search field (center/title), settings icon (right).
- **MessageList (`LazyColumn`):** Must use stable keys on all items.
- **Headers:** Sticky date-group headers (Today, Yesterday, Pinned, etc.).
- **Logic:** Search filtering logic must live strictly in `HistoryViewModel`.

---

## 5. Executable Validation (Agent MUST run before TASK_STATUS: COMPLETE)

1. **Compile UI App:** Run `gradlew.bat :app:assembleDebug`.
2. **Verify Compose Constraints:** Run `gradlew.bat ktlintCheck` to ensure Compose formatting and modifier ordering rules are respected.
3. **Verify Previews:** Render previews via `@compose-preview` tool if instructed, otherwise ensure all files have valid Light/Dark preview annotations.

## 6. System UI Integration (2026)

| Always | Never |
|--------|-------|
| Use `Notification.ProgressStyle` for any task > 30 seconds. | Rely on custom notification layouts for progress bars. |
| Match Compose `LinearProgressIndicator` colors to `ProgressStyle` accents. | Hardcode progress bar colors that clash with system dynamic themes. |

**Constraint:** The `DownloadScreen` must derive its percentage value directly from `WorkInfo.progress`. No intermediate ViewModels variables should hold this state to avoid synchronization lag.
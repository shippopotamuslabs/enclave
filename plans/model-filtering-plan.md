# Model Search and Filtering Plan

## Objective
Improve the UX for selecting API models in the BYOK configuration screen. Currently, it relies on an `ExposedDropdownMenuBox`, which is cumbersome for providers with long model lists. We will introduce a searchable, filterable bottom sheet. This sheet will adapt dynamically, showing standard search for all providers, and exposing advanced filters (like sorting by price/newest) based on the metadata exposed by each provider's official API.

## Scope & Impact
1.  **Data Layer Updates**: 
    *   **OpenRouter**: Extract `name`, `created` (Unix timestamp), and `pricing` from `/v1/models`.
    *   **OpenAI**: Extract `created` (Unix timestamp) from `/v1/models`.
    *   **Anthropic**: Extract `display_name` and `created_at` (RFC 3339 string to be converted to Unix timestamp) from `/v1/models`.
    *   **xAI**: Switch the model discovery endpoint from `/v1/models` to `/v1/language-models` (or merge data from it) to extract `created`, `prompt_token_price`, and `completion_token_price`.
2.  **Domain & UI Models**: Expand `DiscoveredApiModel` and `DiscoveredApiModelUi` to hold the new nullable metadata fields.
3.  **ViewModel logic**: Manage filtering (search query, provider) and sorting state. Compute which sorting options are applicable based on the available metadata in the list of models.
4.  **UI Revamp**: Replace the current model dropdown with a `JumpFreeModalBottomSheet` that contains a search bar, filter chips (if applicable), sort options (if applicable), and a lazy list of models.

## Proposed Solution

### 1. Data & Domain Layer Updates
*   **`DiscoveredApiModel`**: Add nullable fields for metadata:
    *   `name: String?`
    *   `created: Long?` (Unix timestamp)
    *   `promptPrice: Double?`
    *   `completionPrice: Double?`
*   **API Parsing per Provider**:
    *   **OpenRouter**: Update `parseOpenRouterModels` to extract `name`, `created`, and `pricing.prompt` / `pricing.completion`.
    *   **OpenAI**: Update parsing to extract the `created` Unix timestamp.
    *   **Anthropic**: Update parsing to extract `display_name` (maps to `name`) and parse the `created_at` ISO 8601 string into a Unix timestamp (`created`).
    *   **xAI**: Update the catalog repository to use the `/v1/language-models` endpoint (or similar supported endpoint) to fetch pricing data (`prompt_token_price`, etc.) and `created`.

### 2. UI Models & State
*   **`DiscoveredApiModelUi`**:
    *   Map the new fields from `DiscoveredApiModel`.
    *   Add a derived field `providerName: String?`. For OpenRouter, models follow the format `provider/model_name` (e.g., `openai/gpt-4o`). If the `modelId` contains a `/`, we can extract the part before the `/` as the provider to populate sub-provider filter chips.
*   **Model Sort Options**: Create an enum `ModelSortOption` (e.g., `A_TO_Z`, `NEWEST`, `PRICE_LOW_TO_HIGH`).
*   **Dynamic Capabilities**: The UI will check the list of `availableModels`. 
    *   If any model has `created != null`, the "Newest" sort option is enabled.
    *   If any model has pricing data, the "Price" sort option is enabled.
    *   If the list contains more than one unique `providerName`, the Provider Filter Chips are shown.

### 3. SettingsViewModel Updates
*   Add functions to update the search and filter state:
    *   `updateModelSearchQuery(query: String)`
    *   `updateModelProviderFilter(provider: String?)`
    *   `updateModelSortOption(option: ModelSortOption)`
*   Expose a flow/state `filteredModels: List<DiscoveredApiModelUi>` that applies the filters:
    1.  Filter by `searchQuery` (matching `name` or `modelId`, case-insensitive).
    2.  Filter by `providerName` if one is selected.
    3.  Sort based on `sortOption` (falling back to A-Z if the required sorting metadata is missing on the models).

### 4. UI Implementation (`ByokConfigureScreen.kt`)
*   **Trigger**: Replace the model `ExposedDropdownMenuBox` with an `OutlinedTextField` wrapped in a `clickable` modifier to open the bottom sheet (`showModelSelectionSheet = true`). This applies to all providers.
*   **`ModelSelectionBottomSheet` Composable**:
    *   Use `JumpFreeModalBottomSheet`.
    *   **Header**: A sticky section containing:
        *   `OutlinedTextField` for "Filter as you type" search. (Always shown).
        *   `LazyRow` of Provider `FilterChip` components. (Only shown if there are multiple distinct sub-providers in the list).
        *   Sorting Options (e.g., `DropdownMenu` or toggle chips). Only options that the current list supports (based on presence of `created` or pricing data) will be visible.
    *   **List**: A `LazyColumn` displaying the filtered models. Each item should display:
        *   The model `name` (or `modelId` if name is null) as primary text.
        *   The `modelId` as secondary text (if name is present).
        *   Metadata indicators (e.g., Context Window: 128k, Price: $0.01 / 1M tokens) horizontally aligned or below the text, shown only if non-null.
*   **Selection**: Tapping a model calls `onAssetChange` and dismisses the sheet.

## Verification & Testing
*   **Unit Tests**: Update parsing tests for OpenRouter, OpenAI, Anthropic, and xAI to ensure new metadata fields are mapped correctly (or left null safely). Test ViewModel sorting/filtering logic to verify it handles null metadata gracefully.
*   **UI Tests**: Verify the bottom sheet adapts dynamically: shows provider chips for OpenRouter, but hides them for OpenAI (which only has one provider). Verify sorting options toggle visibility based on metadata presence.
*   **Manual Testing**: 
    1.  Select OpenRouter, verify search, provider filtering, and sorting by Newest/Price work.
    2.  Select OpenAI/Anthropic/xAI, verify search works, provider chips are hidden, and sorting adapts to available metadata (e.g., Newest works for Anthropic/OpenAI, Newest/Price works for xAI).
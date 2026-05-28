# Spec: X Pre-Retrieval Query Shaping

**Ticket ID:** `FEAT-x-pre-retrieval-query-shaping`
**Date:** `2026-05-25`
**Contracts read:** `AGENTS.md`, `contracts/ARCHITECTURE_RULES.md`, `contracts/CODE_STYLE_RULES.md`, `contracts/DATA_LAYER_RULES.md`, `contracts/UI_DESIGN_SPEC.md`, `contracts/UNIT_TESTING.md`
**Related:** `plans/FEAT-intent-gated-tool-activation/spec.md` (Intent-gated activation framework)

---

## 1. Feature Summary

### 1.1 Problem
When public X (Twitter) search is enabled and configured, Enclave can search public posts to fetch live context. However, sending a raw, natural-language conversational prompt verbatim to the X API search endpoint (`/2/tweets/search/recent`) often yields poor, spammy, or empty results because X recent search expects keyword-based query syntax rather than semantic natural language. 

Furthermore, if the user explicitly includes an X status URL (e.g. `https://x.com/HarshithLucky3/status/2058763478853456162?s=20`), the system should not run a generic search query. Instead, it should deterministically recognize the X status URL, extract the tweet ID/URL, and retrieve the full thread context directly using `x_get_thread` before inference.

### 1.2 Target
1. **Deterministic Thread Fetch:** If the prompt contains a parseable X status URL, bypass the search classifier and fetch the thread using the `x_get_thread` tool executor.
2. **Query Shaping:** If no X URL is present, but the search intent classifier fires and X search is configured, use a new use case `DeriveXSearchParamsFromPromptUseCase` to shape the conversational prompt into structured `XSearchParams` (or skip with a reason if it is too conversational without keywords/entities).
3. **Pre-Retrieval Execution:** Parallelize X pre-retrieval search/thread fetch with the existing web pre-retrieval, saving the JSON results in a new `preRetrievalXResultJson` field on `PreparedChatInferenceRequest`.
4. **Prompt Injection:** Add a new section `# RELEVANT X SEARCH RESULTS:` to the system prompt containing the structured results of the search/thread fetch.
5. **Source UI Integration:** Parse the returned X search/thread JSON results into `WebSearchSource` objects (updating `WebSearchResultParser` to handle the `XThreadResponse` format) so that X hits are automatically populated as source pills in the chat UI.

---

## 2. File Structure Table

| Action | Path | Responsibility |
|--------|------|----------------|
| **Create** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/inference/DeriveXSearchParamsFromPromptUseCase.kt` | Extracts structured `XSearchParams` from raw prompts based on hashtags, mentions, quoted phrases, and keywords; applies size, length, and conversational skip heuristics. |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/ChatInferenceRequestPreparer.kt` | Performs X status URL checks. Executes deterministic thread retrieval (`x_get_thread`) or shapes query and executes search (`search_x_recent`) parallel to web search; stores results in `PreparedChatInferenceRequest`. |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/port/inference/PromptComposerPort.kt` | Updates the `compose` interface to accept `retrievedXSearchResults: String?`. |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/SearchToolPromptComposer.kt` | Implements the new parameter and appends the `# RELEVANT X SEARCH RESULTS:` block to the system prompt. |
| **Modify** | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/util/WebSearchResultParser.kt` | Adds explicit parsing support for `XThreadResponse` JSON structures, mapping them to `WebSearchSource` domain models. |
| **Create** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/inference/DeriveXSearchParamsFromPromptUseCaseTest.kt` | Unit tests for `DeriveXSearchParamsFromPromptUseCase` verification (stop words, token lengths, quotes, hashtags, and skip thresholds). |
| **Create** | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/chat/ChatInferenceRequestPreparerXSearchPreRetrievalTest.kt` | Focused integration tests for `ChatInferenceRequestPreparer` pre-retrieval flows (URLs, searches, and configurations). |

---

## 3. API Contracts and Interfaces

### 3.1 `DeriveXSearchParamsFromPromptUseCase`

```kotlin
package com.browntowndev.enclave.domain.usecase.inference

import com.browntowndev.enclave.domain.model.inference.XSearchParams

data class DeriveXSearchParamsResult(
    val params: XSearchParams?,
    val skipReason: String?,
)

class DeriveXSearchParamsFromPromptUseCase {
    operator fun invoke(prompt: String): DeriveXSearchParamsResult
}
```

#### Heuristic Rules:
1. **Conversational Skip Threshold:** If the word count of the prompt is greater than 40 AND the prompt contains no quoted phrases (substrings within double quotes), no hashtags (words starting with `#`), and no mentions (words starting with `@`), skip extraction with reason `"Conversational prompt too long without entities"`.
2. **Hashtags:** Extract words starting with `#`. Strip the `#` and add to the `hashtags` list.
3. **Mentions:** Extract words starting with `@`. Strip the `@` and add to the `mentions` list.
4. **Phrases:** Extract substrings enclosed in double quotes (excluding the quotes themselves) and add to the `phrases` list.
5. **Keywords:** 
   - Parse the prompt text that is outside of quoted phrases, hashtags, and mentions.
   - Strip punctuation, split into lowercase tokens.
   - Filter out standard English stop words: `a`, `about`, `above`, `after`, `again`, `against`, `all`, `am`, `an`, `and`, `any`, `are`, `arent`, `as`, `at`, `be`, `because`, `been`, `before`, `being`, `below`, `between`, `both`, `but`, `by`, `cant`, `cannot`, `could`, `couldnt`, `did`, `didnt`, `do`, `does`, `doesnt`, `doing`, `dont`, `down`, `during`, `each`, `few`, `for`, `from`, `further`, `had`, `hadnt`, `has`, `hasnt`, `have`, `havent`, `having`, `he`, `hed`, `hell`, `hes`, `her`, `here`, `heres`, `hers`, `herself`, `him`, `himself`, `his`, `how`, `hows`, `i`, `id`, `ill`, `im`, `ive`, `if`, `in`, `into`, `is`, `isnt`, `it`, `its`, `itself`, `lets`, `me`, `more`, `most`, `mustnt`, `my`, `myself`, `no`, `nor`, `not`, `of`, `off`, `on`, `once`, `only`, `or`, `other`, `ought`, `our`, `ours`, `ourselves`, `out`, `over`, `own`, `same`, `shant`, `she`, `shed`, `shell`, `shes`, `should`, `shouldnt`, `so`, `some`, `such`, `than`, `that`, `thats`, `the`, `their`, `theirs`, `them`, `themselves`, `then`, `there`, `theres`, `these`, `they`, `theyd`, `theyll`, `theyre`, `theyve`, `this`, `those`, `through`, `to`, `too`, `under`, `until`, `up`, `very`, `was`, `wasnt`, `we`, `wed`, `well`, `were`, `weve`, `werent`, `what`, `whats`, `when`, `whens`, `where`, `wheres`, `which`, `while`, `who`, `whos`, `whom`, `why`, `whys`, `with`, `wont`, `would`, `wouldnt`, `you`, `youd`, `youll`, `youre`, `youve`, `your`, `yours`, `yourself`, `yourselves`.
   - Take at most 6 remaining keywords.
6. **Composed Query Length Safety:** If the query string built from the parameters using `BuildXQueryUseCase` exceeds 128 characters, truncate the keywords list sequentially until the length falls below 128.
7. **Empty Guard:** If the final parameters have no keywords, hashtags, mentions, or phrases, return `DeriveXSearchParamsResult(params = null, skipReason = "No search terms extracted")`.

---

### 3.2 X Status URL Parsing

In `ChatInferenceRequestPreparer`, define a regular expression to match X / Twitter status URLs:
```kotlin
private val X_STATUS_URL_REGEX = Regex(
    """https?://(?:www\.)?(?:x|twitter)\.com/[a-zA-Z0-9_]+/status/(\d+)""",
    RegexOption.IGNORE_CASE
)
```

If `X_STATUS_URL_REGEX.find(prompt)` yields a match, extract the full URL (or the matched substring) and execute `x_get_thread` using the `toolExecutor`.

---

### 3.3 Prompt Composer Integration

#### `PromptComposerPort.compose` Signature:
```kotlin
fun compose(
    baseSystemPrompt: String?,
    toolCatalog: List<ToolCatalogEntry>,
    activeTools: List<ToolDefinition>,
    currentChatId: String? = null,
    strategy: ToolCallStrategy = ToolCallStrategy.JSON_XML_ENVELOPE,
    coreMemories: List<Memory> = emptyList(),
    retrievedMemories: List<Memory> = emptyList(),
    retrievedSearchResults: String? = null,
    retrievedXSearchResults: String? = null, // NEW
): String
```

#### Prompt Output Block (inserted after `# RELEVANT WEB SEARCH RESULTS:`):
```markdown
# RELEVANT X SEARCH RESULTS:
The following X search results were automatically retrieved based on the user's query:
$retrievedXSearchResults
```

---

### 3.4 `WebSearchResultParser` Extensions
To parse both X Recent Search (JSON array of `XPost`) and X Get Thread (JSON object matching `XThreadResponse`), we modify `WebSearchResultParser.parse`:
- If `jsonElement` is a `JsonObject` and has `data` as a `JsonArray`:
  - Loop through each post object.
  - If a post has `text` (instead of `content`) and `user_name`/`username` (instead of `title`), map them:
    - `content = post["text"]`
    - `title = post["user_name"] ?: post["username"] ?: "X Post"`
    - `url = post["url"]`
    - `score = post["impact_score"] ?: 1.0`
  - Populate into `WebSearchSource` records.

---

## 4. Data Model Changes

### 4.1 `PreparedChatInferenceRequest`
Add `preRetrievalXResultJson` parameter to the constructor:
```kotlin
data class PreparedChatInferenceRequest(
    val prompt: String,
    val options: GenerationOptions,
    val preRetrievalSources: List<WebSearchSource> = emptyList(),
    val preRetrievalXResultJson: String? = null, // NEW
)
```

---

## 5. Ordered Implementation Tasks

| ID | Task | Verifies Scenario IDs |
|----|------|-----------------------|
| **T-0** | Implement `DeriveXSearchParamsFromPromptUseCase` with Stop Words, entities, and keyword length limits. | `XPRE-03`, `XPRE-04`, `XPRE-10` (MUT-01) |
| **T-1** | Create unit tests for `DeriveXSearchParamsFromPromptUseCaseTest` verifying keywords, length cap, entity extraction, and conversational skip threshold. | `XPRE-03`, `XPRE-04`, `XPRE-10` |
| **T-2** | Extend `PromptComposerPort.compose` and `SearchToolPromptComposer` to support `# RELEVANT X SEARCH RESULTS:` formatting. | `XPRE-01`, `XPRE-03` |
| **T-3** | Update `WebSearchResultParser.parse` to handle `XThreadResponse` objects and parse `XPost` properties (`text` -> `content`, `user_name` -> `title`). | `XPRE-09` |
| **T-4** | Integrate URL detection and pre-retrieval into `ChatInferenceRequestPreparer`. Implement parallel executor execution for `x_get_thread` (when X URL matches) and `search_x_recent` (when search intent classifier fires and no URL matches). | `XPRE-01`, `XPRE-02`, `XPRE-05`, `XPRE-06`, `XPRE-07`, `XPRE-08`, `XPRE-10` |
| **T-5** | Implement focused integration tests in `ChatInferenceRequestPreparerXSearchPreRetrievalTest` using mock `ToolExecutorPort`. | `XPRE-01` to `XPRE-10` |
| **T-6** | Run CI-aligned local checks (`./gradlew detekt` and `./gradlew :core:domain:testDebugUnitTest`) to ensure zero-regression compliance. | All |

---

## 6. Drift and Escalation Rules

### 6.1 Schema Drift
If subsequent requirements request persisting X status/author information in custom SQLite tables, reject the design immediately unless a signed "Contract Waiver" is provided. Rely strictly on `WebSearchSource` entities for database insertion.

### 6.2 Pre-Retrieval Failure Gating
If the X API returns a rate-limit error (HTTP 429) or connection failure, log the exception at `error` level, capture `null` for `preRetrievalXResultJson`, and let the chat inference proceed without blocking. Do not crash or show blocking errors to the user.

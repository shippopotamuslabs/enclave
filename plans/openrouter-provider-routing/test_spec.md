# Test Specification: Dynamic OpenRouter Provider Selection

## 1. Test Framework and Commands
All tests run as JUnit 5 unit tests (or JUnit 4 Robolectric migration tests if existing tests inherit it).

### Execution Commands
*   **Database Schema & Repository Persistence Tests:**
    ```bash
    ./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.enclave.core.data.local.EnclaveDatabaseMigrationTest"
    ./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.enclave.core.data.repository.ApiModelRepositoryImplTest"
    ```
*   **Use Cases Tests:**
    ```bash
    ./gradlew :core:domain:testDebugUnitTest --tests "com.browntowndev.enclave.domain.usecase.byok.FetchOpenRouterModelEndpointsUseCaseTest"
    ```
*   **Inference & Serialization Tests:**
    ```bash
    ./gradlew :feature:inference:testDebugUnitTest --tests "com.browntowndev.enclave.feature.inference.repository.ApiModelCatalogRepositoryImplTest"
    ./gradlew :feature:inference:testDebugUnitTest --tests "com.browntowndev.enclave.feature.inference.OpenRouterRoutingSupportTest"
    ```
*   **Settings Coordinators Tests:**
    ```bash
    ./gradlew :feature:settings:testDebugUnitTest --tests "com.browntowndev.enclave.feature.settings.ByokProvidersCoordinatorTest"
    ```

---

## 2. Scenario Index

| Scenario ID | Name | Category |
| :--- | :--- | :--- |
| `SCN-MIGRATION-3-4-001` | Database migration MIGRATION_3_4 successfully adds column | Schema Migration |
| `SCN-PERSIST-001` | ApiModelRepository maps preferredProvider to and from database | Data Persistence |
| `SCN-INFRA-001` | ApiModelCatalogRepository fetches and parses endpoints from OpenRouter API | Network Infra |
| `SCN-DOMAIN-001` | FetchOpenRouterModelEndpointsUseCase calls catalog port with resolved API key | Domain |
| `SCN-SERIALIZE-001` | OpenRouterRoutingSupport serializes preferred provider to order property | Inference Serialization |
| `SCN-UI-STATE-001` | ByokProvidersCoordinator updates UI state on show and fetch success | Presentation State |
| `SCN-ERROR-001` | ByokProvidersCoordinator emits error snackbar message on API failure | Error handling |
| `SCN-MUTATION-001` | Custom target provider serialization defense against hardcoding | Mutation Defense |

---

## 3. Scenarios

### SCN-MIGRATION-3-4-001: Database migration MIGRATION_3_4 successfully adds column
*   **Description:** Asserts that database schema version bumps to 4, and MIGRATION_3_4 adds `openrouter_preferred_provider` column.
*   **Gherkin:**
    ```gherkin
    Given a fresh SQLite database instantiated at version 3
    When Room migrations execute MIGRATION_3_4 to version 4
    Then column "openrouter_preferred_provider" must exist in table "api_model_configurations"
    And its database type must be "TEXT"
    And its default value must be null
    And database version is 4
    ```

### SCN-PERSIST-001: ApiModelRepository maps preferredProvider to and from database
*   **Description:** Asserts repository mapping correctly saves and restores the preferredProvider configuration field.
*   **Gherkin:**
    ```gherkin
    Given an ApiModelConfiguration with OpenRouterRoutingConfiguration containing preferredProvider "together"
    When saveConfiguration is called on ApiModelRepositoryImpl
    Then the database row in api_model_configurations must contain "together" in "openrouter_preferred_provider"
    And when getConfigurationById is called for the saved configuration
    Then the returned ApiModelConfiguration must have openRouterRouting.preferredProvider equal to "together"
    ```

### SCN-INFRA-001: ApiModelCatalogRepository fetches and parses endpoints from OpenRouter API
*   **Description:** Asserts that the catalog repository calls the correct endpoints URL, parses the API payload, and returns mapped endpoints.
*   **Gherkin:**
    ```gherkin
    Given ApiModelCatalogRepositoryImpl is called with fetchModelEndpoints
    And modelId is "meta-llama/llama-3.3-70b-instruct"
    And baseUrl is null
    And apiKey is "sk-or-key"
    And okhttpClient mock returns HTTP 200 with JSON payload:
      """
      {
        "data": {
          "endpoints": [
            {
              "provider_name": "Together",
              "context_length": 131072,
              "pricing": {
                "prompt": "0.0000007",
                "completion": "0.0000009"
              },
              "quantization": "fp8",
              "status": 0,
              "uptime_last_1d": 0.9995,
              "latency_last_30m": 0.25,
              "throughput_last_30m": 45.0
            }
          ]
        }
      }
      """
    When fetchModelEndpoints executes
    Then HTTP request URL must be "https://openrouter.ai/api/v1/models/meta-llama/llama-3.3-70b-instruct/endpoints"
    And Authorization header must be "Bearer sk-or-key"
    And returned list must contain exactly one OpenRouterModelEndpoint:
      | providerName | "Together" |
      | providerSlug | "together" |
      | contextLength | 131072 |
      | promptPriceUsdPerMillion | 0.7 |
      | completionPriceUsdPerMillion | 0.9 |
      | quantization | "fp8" |
      | isOperational | true |
      | uptime1d | 0.9995 |
      | latency30m | 0.25 |
      | throughput30m | 45.0 |
    ```

### SCN-DOMAIN-001: FetchOpenRouterModelEndpointsUseCase calls catalog port with resolved API key
*   **Description:** Asserts the use case fetches the API key from ApiKeyProviderPort if only the credentialAlias is passed.
*   **Gherkin:**
    ```gherkin
    Given FetchOpenRouterModelEndpointsUseCaseImpl is injected
    And ApiKeyProviderPort is mocked to return key "secret-api-key" for alias "alias-or"
    And ApiModelCatalogPort is mocked to return mock endpoints list
    When invoke is called with credentialAlias "alias-or" and empty currentApiKey
    Then ApiKeyProviderPort.getApiKey must be called with "alias-or"
    And ApiModelCatalogPort.fetchModelEndpoints must be called with apiKey "secret-api-key"
    ```

### SCN-SERIALIZE-001: OpenRouterRoutingSupport serializes preferred provider to order property
*   **Description:** Asserts that when preferredProvider is defined, it is serialized inside the provider.order JSON array.
*   **Gherkin:**
    ```gherkin
    Given OpenRouterRoutingConfiguration with preferredProvider "deepinfra" and allowFallbacks false
    When applyToChatCompletionBuilder is called on OpenRouterRoutingSupport
    Then the request additional body properties must contain "provider"
    And "provider" map must equal:
      | "sort" | "throughput" |
      | "allow_fallbacks" | false |
      | "require_parameters" | false |
      | "data_collection" | "deny" |
      | "zdr" | false |
      | "order" | ["deepinfra"] |
    ```

### SCN-UI-STATE-001: ByokProvidersCoordinator updates UI state on show and fetch success
*   **Description:** Asserts that launching fetch updates loading states, and updates state with fetched endpoints.
*   **Gherkin:**
    ```gherkin
    Given ByokProvidersCoordinator is configured with mock FetchOpenRouterModelEndpointsUseCase returning a list of endpoints
    And apiState flows with isLoadingEndpoints false and openRouterEndpoints empty
    When onShowOpenRouterProvidersSheet is called with show true
    Then apiState transient flow must transition to:
      | isLoadingEndpoints | true |
      | endpointFetchError | false |
      | isOpenRouterProvidersSheetOpen | true |
    And when the coroutine completes
    Then apiState transient flow must transition to:
      | isLoadingEndpoints | false |
      | openRouterEndpoints | list of endpoints |
    ```

### SCN-ERROR-001: ByokProvidersCoordinator emits error snackbar message on API failure
*   **Description:** Asserts that if endpoints fetch fails, the screen handles the exception, toggles the error state, and triggers a snackbar message.
*   **Gherkin:**
    ```gherkin
    Given ByokProvidersCoordinator is configured with FetchOpenRouterModelEndpointsUseCase throwing IOException("HTTP 500")
    And onShowSnackbar mock callback is passed
    When onShowOpenRouterProvidersSheet is called with show true and coroutine completes
    Then apiState transient flow must transition to:
      | isLoadingEndpoints | false |
      | endpointFetchError | true |
    And onShowSnackbar must be invoked with message "Failed to fetch providers"
    ```

### SCN-MUTATION-001: Custom target provider serialization defense against hardcoding
*   **Description:** Asserts that a provider named `"random-unanticipated-provider-slug"` is serialized dynamically into the order JSON array.
*   **Gherkin:**
    ```gherkin
    Given OpenRouterRoutingConfiguration with preferredProvider "random-unanticipated-provider-slug"
    When applyToChatCompletionBuilder is called on OpenRouterRoutingSupport
    Then the request additional body properties "provider" -> "order" must equal list containing exactly "random-unanticipated-provider-slug"
    ```

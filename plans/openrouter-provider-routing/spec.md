# Specification: Dynamic OpenRouter Provider Selection with Neo-Brutalist Bottom Sheet

## 1. Feature Summary
This feature allows users to select a preferred target provider for OpenRouter models instead of relying exclusively on OpenRouter's automatic routing.
The application dynamically fetches available providers (with metrics like pricing, context window, precision/quantization, uptime, latency, and throughput) from OpenRouter's endpoints API.
If fetching fails, a snackbar is shown to notify the user, and the system falls back to automatic routing.
The selection UI is presented in a custom, neo-brutalist styled `NeoModalBottomSheet` that respects Enclave's design system tokens and uses `NeoModalBottomSheet` and `Modifier.neoSurface`.
The selected preferred provider is persisted in the local Room database under the model configuration table and applied to the request payload as `provider.order` when sending prompts.

---

## 2. File Structure Table

| File Path | Type | Responsibility |
| :--- | :--- | :--- |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/config/OpenRouterRoutingConfiguration.kt` | Modify | Add `OpenRouterModelEndpoint` data class and `preferredProvider` field to `OpenRouterRoutingConfiguration`. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/port/repository/ApiModelCatalogPort.kt` | Modify | Add `fetchModelEndpoints` signature to `ApiModelCatalogPort`. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/byok/FetchOpenRouterModelEndpointsUseCase.kt` | Create | Create `FetchOpenRouterModelEndpointsUseCase` and its implementation. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/settings/SettingsUseCases.kt` | Modify | Add `fetchOpenRouterModelEndpoints` to `SettingsApiProviderUseCases` interface. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/settings/SettingsUseCasesImpl.kt` | Modify | Inject and expose `FetchOpenRouterModelEndpointsUseCase` in `SettingsApiProviderUseCasesImpl`. |
| `app/src/main/kotlin/com/browntowndev/enclave/app/ByokUseCasesModule.kt` | Modify | Bind `FetchOpenRouterModelEndpointsUseCase` implementation. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/local/ApiModelConfigurationEntity.kt` | Modify | Add `openrouter_preferred_provider` nullable column to the entity. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/local/EnclaveDatabase.kt` | Modify | Bump schema version to `4`. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/local/EnclaveDatabaseMigrations.kt` | Modify | Add Room `MIGRATION_3_4` adding the new column to `api_model_configurations`. |
| `core/data/build.gradle.kts` | Modify | Register `MIGRATION_3_4` in database builder; expose Room schema JSON under `test` assets for migration tests. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/ApiModelRepositoryImpl.kt` | Modify | Map `preferredProvider` column to/from entity in mapping logic. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/firestore/handlers/ApiModelConfigSyncHandler.kt` | Modify | Sync `openrouter_preferred_provider` to/from Firestore. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/repository/ApiModelCatalogRepositoryImpl.kt` | Modify | Implement HTTP endpoints retrieval for OpenRouter. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/OpenRouterRoutingSupport.kt` | Modify | Update payload serialization to set `provider.order` when preferred provider is selected. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/SettingsModels.kt` | Modify | Add `OpenRouterEndpointsUiState` and properties to `ApiProviderEditorUiState`. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/SettingsUiStateFactory.kt` | Modify | Add transient states for bottom sheet, endpoints loading, list, and error tracking. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/SettingsUiStateAssembler.kt` | Modify | Map transient OpenRouter sheet/fetch states to presentation states. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/ByokProvidersCoordinator.kt` | Modify | Implement coroutine handler to show/hide sheet and fetch model endpoints. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/SettingsApiProvidersDelegate.kt` | Modify | Delegate visibility and fetch triggers to coordinator. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/SettingsViewModel.kt` | Modify | Expose coordinator action callbacks to UI route. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/SettingsCoordinatorDefaults.kt` | Modify | Wire `fetchOpenRouterModelEndpoints` in `UnwiredSettingsApiProviderUseCases`. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/ByokConfigureComponents.kt` | Modify | Integrate preferred provider outlined text field selection in `OpenRouterRoutingSection`. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/OpenRouterProvidersBottomSheet.kt` | Create | Create dynamic neo-brutalist styled endpoints bottom sheet. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/ByokConfigureScreen.kt` | Modify | Bind bottom sheet composable to route and pass state trigger callbacks. |
| `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/local/EnclaveDatabaseMigrationTest.kt` | Modify | Add migration test for `MIGRATION_3_4`; update `LV-DB-VERSION-001` to expect version 4. |
| `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/repository/ApiModelRepositoryImplTest.kt` | Modify | Test config mapping of `preferredProvider` field. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/byok/FetchOpenRouterModelEndpointsUseCaseTest.kt` | Create | Add unit tests for endpoints fetching use case. |
| `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/repository/ApiModelCatalogRepositoryImplTest.kt` | Modify | Test endpoints HTTP API parser logic. |
| `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/OpenRouterRoutingSupportTest.kt` | Modify | Test serialization of preferred provider to JSON payload. |
| `feature/settings/src/test/kotlin/com/browntowndev/enclave/feature/settings/ByokProvidersCoordinatorTest.kt` | Modify | Test coordinators states for OpenRouter sheet fetching and visibility. |

---

## 3. API Contracts and Interfaces

### 3.1 Network Endpoint Contract
*   **Request URL:** `GET https://openrouter.ai/api/v1/models/{model_id}/endpoints` (or `{baseUrl}/models/{model_id}/endpoints` when a custom base URL is configured)
*   **Headers:**
    *   `Authorization: Bearer <API_KEY>`
    *   `User-Agent: Enclave`
    *   `Accept: application/json`
*   **Response payload format:**
```json
{
  "data": {
    "endpoints": [
      {
        "provider_name": "DeepInfra",
        "provider_slug": "deepinfra",
        "context_length": 131072,
        "pricing": {
          "prompt": "0.0000007",
          "completion": "0.0000009"
        },
        "quantization": "fp8",
        "status": 0,
        "uptime_last_1d": 0.9992,
        "latency_last_30m": 0.23,
        "throughput_last_30m": 42.5
      }
    ]
  }
}
```

### 3.2 Domain Model Contracts
```kotlin
package com.browntowndev.enclave.domain.model.config

data class OpenRouterModelEndpoint(
    val providerName: String,
    val providerSlug: String,
    val contextLength: Int,
    val promptPriceUsdPerMillion: Double,
    val completionPriceUsdPerMillion: Double,
    val quantization: String,
    val isOperational: Boolean,
    val uptime1d: Double?,
    val latency30m: Double?,
    val throughput30m: Double?,
)
```

### 3.3 Usecase Contracts
```kotlin
package com.browntowndev.enclave.domain.usecase.byok

interface FetchOpenRouterModelEndpointsUseCase {
    suspend operator fun invoke(
        modelId: String,
        currentApiKey: String,
        credentialAlias: String? = null,
        baseUrl: String? = null,
    ): List<OpenRouterModelEndpoint>
}
```

### 3.4 Repository Port Contract
```kotlin
interface ApiModelCatalogPort {
    suspend fun fetchModelEndpoints(
        modelId: String,
        apiKey: String,
        baseUrl: String? = null,
    ): List<OpenRouterModelEndpoint>
}
```

### 3.5 Coordinator Contract
```kotlin
fun onShowOpenRouterProvidersSheet(
    scope: CoroutineScope,
    apiState: MutableStateFlow<ApiProvidersTransientState>,
    show: Boolean,
    modelId: String = "",
    currentApiKey: String = "",
    credentialAlias: String? = null,
    baseUrl: String? = null,
    onShowSnackbar: (String) -> Unit = {},
)
```

### 3.6 Endpoint Parsing Rules
*   **providerSlug:** Use JSON `provider_slug` when present; otherwise derive by lowercasing `provider_name`.
*   **pricing:** OpenRouter returns per-token USD strings. Multiply by `1_000_000` to produce `*UsdPerMillion` domain values (same constant as existing OpenRouter catalog parsing).
*   **isOperational:** `status == 0` means operational; any other status is non-operational.

---

## 4. Data Model and Schema Changes

### 4.1 Schema Migration (`MIGRATION_3_4`)
```sql
ALTER TABLE api_model_configurations ADD COLUMN openrouter_preferred_provider TEXT;
```

### 4.2 Room Entity Modification
`ApiModelConfigurationEntity.kt` will include:
```kotlin
@ColumnInfo(name = "openrouter_preferred_provider")
val openRouterPreferredProvider: String? = null
```

---

## 5. Ordered Implementation Tasks

### Task 1: Domain models setup
- Add `OpenRouterModelEndpoint` data class to `OpenRouterRoutingConfiguration.kt`.
- Add `preferredProvider: String? = null` property to `OpenRouterRoutingConfiguration` constructor.
- Satisfies: `SCN-DOMAIN-001`, `SCN-PERSIST-001`

### Task 2: Repository Port contract update
- Add `fetchModelEndpoints` function signature to `ApiModelCatalogPort` interface.
- Satisfies: `SCN-INFRA-001`

### Task 3: Use Case Definition
- Create `FetchOpenRouterModelEndpointsUseCase.kt` and `FetchOpenRouterModelEndpointsUseCaseImpl.kt`.
- Add `fetchOpenRouterModelEndpoints` property to `SettingsApiProviderUseCases` and its implementation.
- Bind Use Case in `ByokUseCasesModule.kt`.
- Satisfies: `SCN-DOMAIN-001`

### Task 4: Database Entity & Schema Migration
- Modify `ApiModelConfigurationEntity.kt` to include `openRouterPreferredProvider`.
- Bump Room DB version to `4` in `EnclaveDatabase.kt`.
- Add `MIGRATION_3_4` to `EnclaveDatabaseMigrations.kt`.
- Register migration in `DataModule.kt`.
- Satisfies: `SCN-MIGRATION-3-4-001`

### Task 5: Repository persistence implementation
- Update mapping code in `ApiModelRepositoryImpl.kt` (`toDomain` and `saveConfiguration` methods) to persist/hydrate `preferredProvider`.
- Update `ApiModelConfigSyncHandler.kt` for Firestore round-trip.
- Satisfies: `SCN-PERSIST-001`

### Task 6: Inference Catalog HTTP parsing
- Implement `fetchModelEndpoints` in `ApiModelCatalogRepositoryImpl.kt`.
- Send OkHttp request, handle credentials/API keys, parse payload into domain models using parsing rules in §3.6.
- Satisfies: `SCN-INFRA-001`

### Task 7: Serializer payload integration
- Modify `OpenRouterRoutingSupport.kt`'s `providerRoutingJsonValue` mapper to include the `order` JSON property with a single-item array matching the preferred provider slug when set.
- Omit `order` when `preferredProvider` is null.
- Satisfies: `SCN-SERIALIZE-001`, `SCN-MUTATION-001`

### Task 8: Settings UI Models & State Factory
- Add `OpenRouterEndpointsUiState` data class to `SettingsModels.kt`.
- Add `isOpenRouterProvidersSheetOpen` and `openRouterEndpointsState` to `ApiProviderEditorUiState`.
- Add transient state fields (`isOpenRouterProvidersSheetOpen`, `isLoadingEndpoints`, `endpointFetchError`, `openRouterEndpoints`) to `ApiProvidersTransientState` in `SettingsUiStateFactory.kt`.
- Satisfies: `SCN-UI-STATE-001`

### Task 9: UI State Assembler mapping
- Update mapping in `SettingsUiStateAssembler.kt` to forward the openrouter transient endpoints state to presentation states.
- Satisfies: `SCN-UI-STATE-001`

### Task 10: Settings ViewModels & Delegation coordination
- Implement `onShowOpenRouterProvidersSheet` in `ByokProvidersCoordinator.kt` to launch endpoints fetch operations.
- Delegate this action in `SettingsApiProvidersDelegate.kt` and `SettingsViewModel.kt`.
- Satisfies: `SCN-UI-STATE-001`, `SCN-ERROR-001`

### Task 11: Neo-Brutalist Selection bottom sheet
- Create `OpenRouterProvidersBottomSheet.kt` with modern neo-brutalist styling using `NeoModalBottomSheet` and `Modifier.neoSurface`.
- Satisfies: `SCN-UI-001`

### Task 12: Configure Forms Integration
- Add target provider outlined selector field to `OpenRouterRoutingSection` in `ByokConfigureComponents.kt`.
- Present `OpenRouterProvidersBottomSheet` in `ByokConfigureScreen.kt` triggered by coordinate state.
- Satisfies: `SCN-UI-001`

---

## 6. Drift and Escalation Rules
- **Contract Violations:** If any changes violate clean architecture boundaries (e.g. referencing UI state in `:core:domain`), immediately report the contract violation and ask for waive/clarification.
- **Database schemas:** Schema migrations must follow exact migration test coverage in `EnclaveDatabaseMigrationTest.kt`. If the migration test fails, do not proceed to execution until resolved.
- **Scenario ID collision:** Do not reuse `SCN-SCHEMA-001` (already assigned to utility-models schema coverage). Use `SCN-MIGRATION-3-4-001` for this feature's migration test.

# Technical Specification: BYOK-REFLOW-20260407

## 1. Objective
Refactor the BYOK save/edit flow so persisted Room rows remain the source of truth and editor interactions operate on isolated draft state.

This spec must eliminate the current bug class where:
- creating a new BYOK provider while reusing an existing API key mutates an existing provider row instead of inserting a new one,
- duplicate `(provider, model_id, base_url)` combinations are impossible because of a conflicting unique index,
- `SettingsViewModel` mixes draft/editor state with Room-backed list state, causing wrong-row updates and draft-state splicing,
- reusable API key selection is treated as row identity instead of key-copy source.

Required invariants:
- New provider save with `credentialsId == 0` inserts a new `api_credentials` row, even if provider/model/baseUrl matches an existing row.
- Existing provider save with `credentialsId != 0` updates that exact row by primary key.
- Reusable API key selection only supplies key material (`sourceCredentialAlias`) for create flows; it never selects or rewrites the persisted credential row.
- Room flows (`GetApiModelAssetsUseCase`) remain authoritative for rendered saved providers/presets.
- BYOK configure/custom-header screens read and mutate draft state only.

## 2. System Architecture

### Target Files (9 manual files, Tier 2 compliant)

**Data layer**
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiCredentialsEntity.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiCredentialsDao.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/PocketCrewDatabase.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/DataModule.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ApiModelRepositoryImpl.kt`

**Feature settings layer**
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsModels.kt`
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModel.kt`
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokConfigureScreen.kt`
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokCustomHeadersScreen.kt`

**Generated artifact required by the migration**
- `core/data/schemas/com.browntowndev.pocketcrew.core.data.local.PocketCrewDatabase/2.json`

### Non-Goals
- No change to `api_model_configurations` uniqueness on `(api_credentials_id, display_name)`.
- No change to delete/reassignment behavior for BYOK or local models.
- No new screens, destinations, permissions, or external services.

### Architectural Split

#### Persisted state
Persisted BYOK state continues to come from Room via `GetApiModelAssetsUseCase` and is rendered into `uiState.apiModels`.

Persisted selection is limited to list/navigation context:
- which saved provider row is currently selected in the BYOK sheet,
- which saved provider row acts as the parent for preset creation/editing.

#### Draft/editor state
Editor state becomes a separate transient slice in `SettingsViewModel` and `SettingsUiState`.

It must hold:
- credential draft fields for provider create/edit,
- preset draft fields for preset create/edit,
- selected reusable credential source,
- discovery results scoped to the active editor draft,
- custom headers draft list.

Field change handlers in the configure flow must mutate draft state only. They must never mutate the Room-derived `apiModels` list.

### Flow Rules

#### Create provider
1. User starts BYOK create with no selected persisted asset.
2. `SettingsViewModel` seeds a blank credential draft.
3. Save path inserts a new `api_credentials` row.
4. If the save succeeds, the returned row ID becomes the parent for the auto-created default preset.
5. The Room flow emits the persisted row; that emission is used to reselect the inserted provider in the saved list.

#### Edit provider
1. User selects an existing persisted provider row.
2. `SettingsViewModel` copies that row into a credential draft.
3. Save path updates the exact row by primary key.
4. Sibling rows with identical provider/model/baseUrl must remain unchanged.

#### Create or edit preset
1. Parent provider identity comes from the selected persisted asset ID.
2. Preset form edits a draft config, not the saved config object from the Room list.
3. Saving a preset writes only the targeted config row or inserts a new preset row under the selected parent credentials ID.

## 3. Data Models & Schemas

### Schema Changes

#### `api_credentials`
Remove the unique index on `(provider, model_id, base_url)`.

Keep the unique index on `credential_alias`.

Resulting table constraint set:
- `credential_alias` remains globally unique.
- `provider + model_id + base_url` is no longer unique and may repeat across rows.

### Migration Work

#### Database version
- Bump `PocketCrewDatabase` from version `1` to version `2`.

#### Required migration
Add a `MIGRATION_1_2` that removes the old unique index:

```sql
DROP INDEX IF EXISTS index_api_credentials_provider_model_id_base_url
```

No data backfill is required because the migration only relaxes a uniqueness constraint.

`DataModule` must register `MIGRATION_1_2` through `Room.databaseBuilder(...).addMigrations(...)`.

### `ApiCredentialsDao` contract change
The DAO must stop encouraging identity resolution by `(provider, model_id, base_url)` because duplicates are now valid and that lookup is ambiguous.

Required changes:
- remove `getByIdentity(...)`,
- replace `@Upsert`-only credential persistence with explicit insert/update operations or equivalent repository-safe primitives,
- preserve `getById(...)` and `getByCredentialAlias(...)` as deterministic lookups.

### BYOK draft contract

`SettingsUiState` must add explicit draft fields for BYOK editing. The exact implementation can use existing `ApiModelAssetUi` / `ApiModelConfigUi` as draft DTOs, but the contract is:

- `selectedApiModelAsset`: persisted list selection only,
- `apiCredentialDraft`: mutable draft for provider create/edit,
- `apiConfigDraft`: mutable draft for preset create/edit,
- `selectedReusableApiCredential`: key-copy source only,
- `apiModels`: immutable Room-derived saved list.

The configure flow must treat `apiCredentialDraft` and `apiConfigDraft` as the only editable sources.

## 4. API Contracts & Interfaces

### Repository save semantics

`ApiModelRepositoryImpl.saveCredentials(...)` keeps its public signature, but its behavior changes:

- If `credentials.id == 0L`, perform a create path.
  - Insert a new row.
  - Do not search for an existing row by provider/model/baseUrl.
  - `sourceCredentialAlias` may be used only to copy key material into the new alias when `apiKey` is blank.

- If `credentials.id != 0L`, perform an update path.
  - Validate that the row exists by ID.
  - Update that exact row.
  - Ignore provider/model/baseUrl duplicates in sibling rows.
  - Reusable credential selection must not replace the edited row identity.

### ViewModel contract

`SettingsViewModel` must expose an explicit BYOK editor lifecycle:

- prepare credential draft for create,
- prepare credential draft for edit,
- prepare preset draft for create,
- prepare preset draft for edit,
- clear editor draft on exit,
- sanitize custom headers against the config draft, not against persisted list state.

Required behavioral rules:
- `uiState.apiModels` always comes from Room flows and is never locally patched to simulate edits.
- Draft updates do not mutate `uiState.selectedApiModelAsset`.
- Discovery metadata is scoped to the active draft and re-derived when the draft’s provider/model/baseUrl changes.
- `onSaveApiCredentials(...)` reads from `apiCredentialDraft`, not `selectedApiModelAsset`.
- `onSaveApiModelConfig(...)` reads from `apiConfigDraft`, not a config object copied out of the Room list at combine-time.

### UI contract

#### `ByokConfigureScreen.kt`
- Determine editor mode from draft state, not from persisted selection alone.
- Credentials form binds to `apiCredentialDraft`.
- Preset form binds to `apiConfigDraft`.
- Reusable credential choices are derived from persisted `apiModels`, but selecting one only populates `selectedReusableApiCredential`.

#### `ByokCustomHeadersScreen.kt`
- Render and mutate `apiConfigDraft.customHeaders`.
- “Done” sanitization trims blank header rows from the draft only.

## 5. Permissions & Config Delta
None.

No Android manifest changes, runtime permissions, Gradle dependency changes, or navigation graph additions are required.

## 6. Constitution Audit
- `AGENTS.md` compliance: scope stays inside the specification phase and within Tier 2 limits.
- Data-layer correctness: Room remains the source of truth for saved providers and presets; draft state is isolated in the feature layer.
- Mutation safety: the old uniqueness constraint is removed so duplicate provider/model/baseUrl rows are supported by schema instead of being patched around in UI logic.
- Layering: no Android-specific APIs are added to domain contracts; work stays inside data and feature modules.
- UI state discipline: Compose screens edit transient draft state and only persist through explicit save actions.

## 7. Cross-Spec Dependencies
No external spec dependency is required.

Implementation depends on the current BYOK repository/use-case stack already present in:
- `GetApiModelAssetsUseCase`
- `SaveApiCredentialsUseCase`
- `SaveApiModelConfigurationUseCase`

The implementation agent must also commit the exported Room schema for version `2` alongside the code changes.

> **Instruction to Implementation Agent**
> Do not solve this by reintroducing synthetic identity matching in the ViewModel. The fix is contract-level:
> 1. relax the schema,
> 2. make create vs update explicit in the repository,
> 3. isolate draft state from Room list state,
> 4. treat reusable credentials as key-copy sources only.

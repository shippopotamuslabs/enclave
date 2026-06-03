# Test Spec — Fine-Grained Media Tasks (`fal-media-tasks`)

**Companion to:** `plans/fal-media-tasks/spec.md` (all type/method/literal names match that document exactly).
**Test framework:** JUnit 5 (`org.junit.jupiter.api`) + Turbine (Flow/StateFlow) + Fakes-over-Mocks per UNIT_TESTING. Existing media tests in `:feature:inference` and `:core:data` use MockK (`mockk`, `coEvery`, `coVerify`, `verify`) with `runTest`; new tests in those modules follow the **same convention as the file they extend**. ViewModel tests use `@ExtendWith(MainDispatcherExtension::class)` + Turbine. Pure domain tests (mask/mapping) need no Android runtime.

## Run Commands (verified against `settings.gradle.kts` + `./gradlew`)

Per-module unit tests (preferred narrow scope during iteration):

```
./gradlew :core:domain:testDebugUnitTest
./gradlew :core:data:testDebugUnitTest
./gradlew :feature:inference:testDebugUnitTest
./gradlew :feature:studio:testDebugUnitTest
./gradlew :feature:settings:testDebugUnitTest
```

Single-class focus, e.g.:

```
./gradlew :feature:inference:testDebugUnitTest --tests "*FalVideoGenerationAdapterTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*MediaTaskTest*"
```

Room schema compile (after Phase B):

```
./gradlew :app:kspDebugKotlin
```

Cross-module / pre-merge:

```
./gradlew testDebugUnitTest
./gradlew ktlintCheck
./gradlew detekt
./gradlew assembleDebug
```

> AndroJack MCP is not wired in this environment; validation is Gradle only.

---

## Scenario Index

| ID | Title | Module |
|---|---|---|
| MASK-01 | Empty set maps to mask `0` | `:core:domain` |
| MASK-02 | Mask `0` maps to empty set | `:core:domain` |
| MASK-03 | All four tasks map to mask `15` | `:core:domain` |
| MASK-04 | Mask `15` maps to all four tasks | `:core:domain` |
| MASK-05 | Single-bit round-trips per task | `:core:domain` |
| MASK-06 | Unknown high bit is ignored, low bits preserved | `:core:domain` |
| MAP-01 | `MediaTask.toModelType()` for all four tasks | `:core:domain` |
| MAP-02 | `ModelType.toMediaTaskOrNull()` for the four slots | `:core:domain` |
| MAP-03 | `ModelType.toMediaTaskOrNull()` returns null for non-media slot | `:core:domain` |
| MODELTYPE-01 | New `ModelType` apiValues round-trip via `fromApiValue` | `:core:domain` |
| MODELTYPE-02 | New `ModelType` entries report `isTextModel == false` | `:core:domain` |
| PARSE-01 | Image field absent ⇒ `imageInput == NONE` | `:feature:inference` |
| PARSE-02 | Image field present + required ⇒ `imageInput == REQUIRED` | `:feature:inference` |
| PARSE-03 | Image field present + optional ⇒ `imageInput == OPTIONAL` | `:feature:inference` |
| PARSE-04 | Derive image tasks from the three schema rows | `:feature:inference` |
| PARSE-05 | Derive video tasks from the three schema rows | `:feature:inference` |
| PARSE-06 | Optional-image must yield BOTH tasks (no collapse) | `:feature:inference` |
| CATALOG-01 | `category == "text-to-image"` ⇒ `{TEXT_TO_IMAGE}` | `:feature:inference` |
| CATALOG-02 | `category == "image-to-image"` ⇒ `{EDIT_IMAGE}` | `:feature:inference` |
| CATALOG-03 | `category == "text-to-video"` ⇒ `{TEXT_TO_VIDEO}` | `:feature:inference` |
| CATALOG-04 | `category == "image-to-video"` ⇒ `{IMAGE_TO_VIDEO}` | `:feature:inference` |
| CATALOG-05 | Endpoint-id substring i2v ⇒ `{IMAGE_TO_VIDEO}` | `:feature:inference` |
| CATALOG-06 | Hand-maintained fallback map for featured ids | `:feature:inference` |
| ENTITY-01 | Entity→domain mapper reads `tasks_mask` | `:core:data` |
| ENTITY-02 | Domain→entity save writes `tasks_mask` | `:core:data` |
| DB-01 | `EnclaveDatabase` version is `1` | `:core:data` |
| SYNC-01 | `toFirestore` includes `tasks_mask` | `:core:data` |
| SYNC-02 | `fromFirestore` reads `tasks_mask` | `:core:data` |
| SYNC-03 | `fromFirestore` defaults missing `tasks_mask` to `0` | `:core:data` |
| IMGADAPTER-01 | EDIT model + reference ⇒ `image_url`/`image` attached | `:feature:inference` |
| IMGADAPTER-02 | T2I-only model + reference ⇒ no image fields attached | `:feature:inference` |
| VIDADAPTER-01 | I2V model + reference ⇒ `image_url` attached | `:feature:inference` |
| VIDADAPTER-02 | I2V model + no reference ⇒ validation failure | `:feature:inference` |
| VIDADAPTER-03 | T2V model + reference ⇒ no image field, no string heuristic | `:feature:inference` |
| IMGPORT-01 | Edit-capable provider loads reference and calls adapter | `:feature:inference` |
| IMGPORT-02 | T2I-only provider with reference image ⇒ fail fast | `:feature:inference` |
| IMGPORT-03 | T2I-only provider, no reference ⇒ succeeds, adapter `supportsEdit == false` | `:feature:inference` |
| VIDPORT-01 | I2V-capable provider loads reference and calls adapter | `:feature:inference` |
| VIDPORT-02 | T2V-only provider with reference image ⇒ fail fast | `:feature:inference` |
| VIDPORT-03 | T2V-only provider, no reference ⇒ succeeds | `:feature:inference` |
| VM-01 | IMAGE mode resolves edit slot when reference attached | `:feature:studio` |
| VM-02 | `hasImageEditModel` / `hasImageToVideoModel` reflect defaults | `:feature:studio` |
| VM-03 | No I2V default ⇒ `hasImageToVideoModel == false` | `:feature:studio` |
| VM-04 | Animate without I2V default emits error, no generation | `:feature:studio` |
| COORD-01 | `startDetailVideoGeneration` blocked without I2V default | `:feature:studio` |
| UI-01 | IMAGE picker hidden when no edit model (fail closed) | `:feature:studio` |
| UI-02 | IMAGE picker shown when edit model present | `:feature:studio` |
| UI-03 | VIDEO picker hidden when no I2V model (fail closed) | `:feature:studio` |
| UI-04 | Animate disabled + exact tooltip when no I2V model | `:feature:studio` |
| UI-05 | Animate enabled when I2V model present | `:feature:studio` |
| SETTINGS-01 | Four Studio & Media slots offered | `:feature:settings` |
| SETTINGS-02 | Edit-slot picker filters to `EDIT_IMAGE` providers | `:feature:settings` |
| SETTINGS-03 | I2V-slot picker filters to `IMAGE_TO_VIDEO` providers | `:feature:settings` |
| SETTINGS-04 | Provider save persists selected `tasks` | `:feature:settings` |
| SETTINGS-05 | New slot maps to correct `ModelType` on set-default | `:feature:settings` |
| MUT-01 | Mutation defense: T2I-only must not attach image (kills hardcoded `supportsEdit = true`) | `:feature:inference` |
| MUT-02 | Mutation defense: optional-image must not collapse to image-only | `:feature:inference` |
| MUT-03 | Mutation defense: edit-slot picker must exclude T2I-only provider | `:feature:settings` |

---

## Happy Path Scenarios

### MASK-01 — Empty set maps to mask `0`
```gherkin
Scenario: Empty MediaTask set serializes to integer zero
  Given an empty Set<MediaTask>
  When toMask() is called
  Then the result is exactly 0
```

### MASK-02 — Mask `0` maps to empty set
```gherkin
Scenario: Integer zero deserializes to an empty MediaTask set
  Given the integer 0
  When 0.toMediaTasks() is called
  Then the result is an empty Set<MediaTask>
  And the result equals emptySet<MediaTask>()
```

### MASK-03 — All four tasks map to mask `15`
```gherkin
Scenario: The full task set serializes to 15
  Given the set {TEXT_TO_IMAGE, EDIT_IMAGE, TEXT_TO_VIDEO, IMAGE_TO_VIDEO}
  When toMask() is called
  Then the result is exactly 15
```

### MASK-04 — Mask `15` maps to all four tasks
```gherkin
Scenario: Mask 15 deserializes to the full task set
  Given the integer 15
  When 15.toMediaTasks() is called
  Then the result equals {TEXT_TO_IMAGE, EDIT_IMAGE, TEXT_TO_VIDEO, IMAGE_TO_VIDEO}
  And the result size is exactly 4
```

### MASK-05 — Single-bit round-trips per task
```gherkin
Scenario Outline: Each task round-trips through its single-bit mask
  Given the set containing only <task>
  When toMask() then toMediaTasks() are applied in sequence
  Then the result equals the set containing only <task>
  And the intermediate mask equals <bit>

  Examples:
    | task           | bit |
    | TEXT_TO_IMAGE  | 1   |
    | EDIT_IMAGE     | 2   |
    | TEXT_TO_VIDEO  | 4   |
    | IMAGE_TO_VIDEO | 8   |
```

### MASK-06 — Unknown high bit is ignored, low bits preserved
```gherkin
Scenario: A forward-compatible mask with an unknown high bit decodes only known bits
  Given the integer 19 (binary 10011: TEXT_TO_IMAGE bit, EDIT_IMAGE bit, and an unknown bit 16)
  When 19.toMediaTasks() is called
  Then the result equals {TEXT_TO_IMAGE, EDIT_IMAGE}
  And no exception is thrown
```

### MAP-01 — `MediaTask.toModelType()` for all four tasks
```gherkin
Scenario Outline: Each task maps to its default-model slot
  Given the MediaTask <task>
  When toModelType() is called
  Then the result is ModelType.<slot>

  Examples:
    | task           | slot             |
    | TEXT_TO_IMAGE  | IMAGE_GENERATION |
    | EDIT_IMAGE     | IMAGE_EDIT       |
    | TEXT_TO_VIDEO  | VIDEO_GENERATION |
    | IMAGE_TO_VIDEO | IMAGE_TO_VIDEO   |
```

### MAP-02 — `ModelType.toMediaTaskOrNull()` for the four slots
```gherkin
Scenario Outline: Each media slot maps back to its task
  Given the ModelType <slot>
  When toMediaTaskOrNull() is called
  Then the result is MediaTask.<task>

  Examples:
    | slot             | task           |
    | IMAGE_GENERATION | TEXT_TO_IMAGE  |
    | IMAGE_EDIT       | EDIT_IMAGE     |
    | VIDEO_GENERATION | TEXT_TO_VIDEO  |
    | IMAGE_TO_VIDEO   | IMAGE_TO_VIDEO |
```

### MAP-03 — `ModelType.toMediaTaskOrNull()` returns null for non-media slot
```gherkin
Scenario: A chat slot has no media task
  Given the ModelType MAIN
  When toMediaTaskOrNull() is called
  Then the result is null
  And calling toMediaTaskOrNull() on ModelType.MUSIC_GENERATION also returns null
```

### MODELTYPE-01 — New `ModelType` apiValues round-trip via `fromApiValue`
```gherkin
Scenario: New media slots round-trip their apiValue
  Given the ModelType enum
  Then ModelType.IMAGE_EDIT.apiValue equals "image_edit"
  And ModelType.IMAGE_TO_VIDEO.apiValue equals "image_to_video"
  And ModelType.fromApiValue("image_edit") equals ModelType.IMAGE_EDIT
  And ModelType.fromApiValue("image_to_video") equals ModelType.IMAGE_TO_VIDEO
```

### MODELTYPE-02 — New `ModelType` entries report `isTextModel == false`
```gherkin
Scenario: The new media slots are not text models
  Given the ModelType enum
  Then ModelType.IMAGE_EDIT.isTextModel is false
  And ModelType.IMAGE_TO_VIDEO.isTextModel is false
  And ModelType.IMAGE_EDIT.displayName() equals "Image Edit"
  And ModelType.IMAGE_TO_VIDEO.displayName() equals "Image to Video"
```

### PARSE-01 — Image field absent ⇒ `imageInput == NONE`
```gherkin
Scenario: A schema with no image field reports no reference support
  Given a fal OpenAPI document whose input schema has a "prompt" property and no image fields
  When FalOpenApiInputSchemaParser.parse is called
  Then the returned ModelGenerationInputSchema.imageInput is ImageInputRequirement.NONE
```

### PARSE-02 — Image field present + required ⇒ `imageInput == REQUIRED`
```gherkin
Scenario: A schema with a required image field is image-mandatory
  Given a fal OpenAPI document whose input schema has "prompt" and "image_url" properties
  And the input schema "required" array contains "image_url"
  When FalOpenApiInputSchemaParser.parse is called
  Then the returned ModelGenerationInputSchema.imageInput is ImageInputRequirement.REQUIRED
```

### PARSE-03 — Image field present + optional ⇒ `imageInput == OPTIONAL`
```gherkin
Scenario: A schema with an optional image field supports but does not require an image
  Given a fal OpenAPI document whose input schema has "prompt" and "image_url" properties
  And the input schema "required" array contains "prompt" but not "image_url"
  When FalOpenApiInputSchemaParser.parse is called
  Then the returned ModelGenerationInputSchema.imageInput is ImageInputRequirement.OPTIONAL
```

### PARSE-04 — Derive image tasks from the three schema rows
```gherkin
Scenario Outline: Image-kind task derivation from schema signals
  Given the coarse kind is IMAGE
  And the schema imageInput is <imageInput>
  When the catalog derives tasks for the model
  Then the resulting tasks equal <tasks>

  Examples:
    | imageInput | tasks                        |
    | NONE       | {TEXT_TO_IMAGE}              |
    | REQUIRED   | {EDIT_IMAGE}                 |
    | OPTIONAL   | {TEXT_TO_IMAGE, EDIT_IMAGE}  |
```

### PARSE-05 — Derive video tasks from the three schema rows
```gherkin
Scenario Outline: Video-kind task derivation from schema signals
  Given the coarse kind is VIDEO
  And the schema imageInput is <imageInput>
  When the catalog derives tasks for the model
  Then the resulting tasks equal <tasks>

  Examples:
    | imageInput | tasks                            |
    | NONE       | {TEXT_TO_VIDEO}                  |
    | REQUIRED   | {IMAGE_TO_VIDEO}                 |
    | OPTIONAL   | {TEXT_TO_VIDEO, IMAGE_TO_VIDEO}  |
```

### PARSE-06 — Optional-image must yield BOTH tasks (no collapse)
```gherkin
Scenario: An optional image input advertises both the text and the image task
  Given the coarse kind is IMAGE
  And the schema imageInput is OPTIONAL
  When the catalog derives tasks for the model
  Then the resulting set size is exactly 2
  And the set contains TEXT_TO_IMAGE
  And the set contains EDIT_IMAGE
```

### CATALOG-01..04 — `category` → tasks
```gherkin
Scenario Outline: A single-task fal category maps to a single MediaTask
  Given a FalModel JSON with endpoint_id "<id>" and category "<category>"
  When ApiModelCatalogRepositoryImpl fetches/classifies the model
  Then the DiscoveredApiModel.tasks equals <tasks>

  Examples:
    | id                                | category        | tasks            |
    | fal-ai/recraft/v3                 | text-to-image   | {TEXT_TO_IMAGE}  |
    | fal-ai/flux-pro/kontext/edit      | image-to-image  | {EDIT_IMAGE}     |
    | bytedance/seedance-2.0/text-to-video | text-to-video | {TEXT_TO_VIDEO}  |
    | fal-ai/kling-video/v2.1/standard/image-to-video | image-to-video | {IMAGE_TO_VIDEO} |
```
> CATALOG-01 = text-to-image row, CATALOG-02 = image-to-image row, CATALOG-03 = text-to-video row, CATALOG-04 = image-to-video row.

### CATALOG-05 — Endpoint-id substring i2v ⇒ `{IMAGE_TO_VIDEO}`
```gherkin
Scenario: When category is absent the endpoint-id substring drives the task
  Given a FalModel JSON with endpoint_id "fal-ai/wan/v2.2-a14b/image-to-video/lora" and a null category
  When ApiModelCatalogRepositoryImpl classifies the model
  Then the DiscoveredApiModel.tasks equals {IMAGE_TO_VIDEO}
```

### CATALOG-06 — Hand-maintained fallback map for featured ids
```gherkin
Scenario Outline: Featured ids fall back to a hand-maintained task set when no category or schema is available
  Given no category, no tags, and no openapi for the model
  And the endpoint_id is "<id>"
  When ApiModelCatalogRepositoryImpl classifies the model
  Then the DiscoveredApiModel.tasks equals <tasks>

  Examples:
    | id                                              | tasks                        |
    | fal-ai/flux-pro/kontext                         | {TEXT_TO_IMAGE, EDIT_IMAGE}  |
    | fal-ai/recraft/v3                               | {TEXT_TO_IMAGE}              |
    | fal-ai/kling-video/v2.1/master/image-to-video   | {IMAGE_TO_VIDEO}             |
    | fal-ai/minimax/video-01                         | {TEXT_TO_VIDEO}              |
```

### ENTITY-01 — Entity→domain mapper reads `tasks_mask`
```gherkin
Scenario: getMediaProviders maps tasks_mask into a Set<MediaTask>
  Given a MediaProviderEntity with capability IMAGE and tasksMask 3
  And the MediaProviderDao observes a list containing that entity
  When repository.getMediaProviders().first() is collected
  Then the single MediaProviderAsset.tasks equals {TEXT_TO_IMAGE, EDIT_IMAGE}
  And MediaProviderAsset.capability equals MediaCapability.IMAGE
```

### ENTITY-02 — Domain→entity save writes `tasks_mask`
```gherkin
Scenario: saveMediaProvider serializes tasks to tasks_mask
  Given a MediaProviderAsset with tasks {IMAGE_TO_VIDEO}
  When repository.saveMediaProvider(asset, apiKey = "key") is called
  Then the MediaProviderEntity passed to MediaProviderDao.upsert has tasksMask equal to 8
```

### DB-01 — `EnclaveDatabase` version is `1`
```gherkin
Scenario: The database version is reset to 1
  Given the EnclaveDatabase @Database annotation
  Then its version value is exactly 1
```
> Implemented as a reflective/annotation assertion on `EnclaveDatabase::class` (`@Database.version == 1`) in `:core:data` unit tests.

### SYNC-01 — `toFirestore` includes `tasks_mask`
```gherkin
Scenario: Firestore serialization includes the tasks_mask field
  Given a MediaProviderEntity with tasksMask 6
  When MediaProviderSyncHandler.toFirestore is called
  Then the resulting map contains key "tasks_mask" with value 6
  And the resulting map still contains key "capability" with value "IMAGE"
```

### SYNC-02 — `fromFirestore` reads `tasks_mask`
```gherkin
Scenario: Firestore deserialization reads the tasks_mask field
  Given a DocumentSnapshot whose getLong("tasks_mask") returns 12L
  When MediaProviderSyncHandler.fromFirestore is called
  Then the resulting MediaProviderEntity.tasksMask equals 12
```

### SYNC-03 — `fromFirestore` defaults missing `tasks_mask` to `0`
```gherkin
Scenario: A legacy document without tasks_mask deserializes to mask 0
  Given a DocumentSnapshot whose getLong("tasks_mask") returns null
  When MediaProviderSyncHandler.fromFirestore is called
  Then the resulting MediaProviderEntity.tasksMask equals 0
```

### IMGADAPTER-01 — EDIT model + reference ⇒ image fields attached
```gherkin
Scenario: An edit-capable fal image request includes image_url and image
  Given a FalService stub that captures the submitQueue body
  When FalImageGenerationAdapter.generateImage is called with referenceImage = some PNG bytes and supportsEdit = true
  Then the captured request body contains key "image_url"
  And the captured request body contains key "image"
```

### IMGADAPTER-02 — T2I-only model + reference ⇒ no image fields attached
```gherkin
Scenario: A text-to-image-only fal request omits image fields even with a reference image
  Given a FalService stub that captures the submitQueue body
  When FalImageGenerationAdapter.generateImage is called with referenceImage = some PNG bytes and supportsEdit = false
  Then the captured request body does NOT contain key "image_url"
  And the captured request body does NOT contain key "image"
  And the captured request body contains key "prompt"
```

### VIDADAPTER-01 — I2V model + reference ⇒ `image_url` attached
```gherkin
Scenario: An image-to-video fal request includes image_url
  Given a FalService stub that captures the submitQueue body and returns a completed video
  When FalVideoGenerationAdapter.generateVideo is called with referenceImage = some PNG bytes and supportsImageToVideo = true
  Then the captured request body contains key "image_url"
  And the result isSuccess is true
```

### VIDADAPTER-02 — I2V model + no reference ⇒ validation failure
```gherkin
Scenario: An image-to-video model rejects a request with no reference image
  Given a FalService stub
  When FalVideoGenerationAdapter.generateVideo is called with referenceImage = null and supportsImageToVideo = true and modelId = "fal-ai/kling-video/v2.1/master/image-to-video"
  Then the result isFailure is true
  And the exception is an IllegalArgumentException
  And the exception message equals "Model 'fal-ai/kling-video/v2.1/master/image-to-video' requires a reference image but none was provided."
  And FalService.submitQueue was never called
```

### VIDADAPTER-03 — T2V model + reference ⇒ no image field, no string heuristic
```gherkin
Scenario: A text-to-video model does not attach an image and ignores the model-id string
  Given a FalService stub that captures the submitQueue body and returns a completed video
  When FalVideoGenerationAdapter.generateVideo is called with referenceImage = some PNG bytes and supportsImageToVideo = false and modelId = "fal-ai/some/image-to-video-named-but-t2v"
  Then the captured request body does NOT contain key "image_url"
  And the result isSuccess is true
```
> The model id deliberately contains "image-to-video"; behavior must follow `supportsImageToVideo`, proving the deleted `isImageToVideoModel` heuristic no longer drives the request.

### IMGPORT-01 — Edit-capable provider loads reference and calls adapter
```gherkin
Scenario: The image port loads the reference and forwards supportsEdit = true
  Given a fal MediaProviderAsset with tasks {TEXT_TO_IMAGE, EDIT_IMAGE} and credentialAlias "alias"
  And apiKeyProvider returns "key" for "alias"
  And studioRepository.readMediaBytes("cache://reference.png") returns PNG bytes
  And falAdapter.generateImage is stubbed to return success
  When port.generateImage("draw", provider, ImageGenerationSettings(referenceImageUri = "cache://reference.png")) is called
  Then the result isSuccess is true
  And falAdapter.generateImage was called with referenceImage = the loaded PNG bytes and supportsEdit = true
```

### IMGPORT-02 — T2I-only provider with reference image ⇒ fail fast
```gherkin
Scenario: The image port fails fast when editing is unsupported and a reference is present
  Given a fal MediaProviderAsset with modelName "fal-ai/recraft/v3" and tasks {TEXT_TO_IMAGE}
  And apiKeyProvider returns "key"
  When port.generateImage("draw", provider, ImageGenerationSettings(referenceImageUri = "cache://reference.png")) is called
  Then the result isFailure is true
  And the exception is an IllegalArgumentException
  And the exception message equals "Model 'fal-ai/recraft/v3' does not support image editing."
  And studioRepository.readMediaBytes was never called
  And falAdapter.generateImage was never called
```

### IMGPORT-03 — T2I-only provider, no reference ⇒ succeeds with `supportsEdit == false`
```gherkin
Scenario: The image port allows text-to-image without a reference and forwards supportsEdit = false
  Given a fal MediaProviderAsset with tasks {TEXT_TO_IMAGE}
  And falAdapter.generateImage is stubbed to return success
  When port.generateImage("draw", provider, ImageGenerationSettings(referenceImageUri = null)) is called
  Then the result isSuccess is true
  And falAdapter.generateImage was called with referenceImage = null and supportsEdit = false
```

### VIDPORT-01 — I2V-capable provider loads reference and calls adapter
```gherkin
Scenario: The video port loads the reference and forwards supportsImageToVideo = true
  Given a fal MediaProviderAsset with tasks {TEXT_TO_VIDEO, IMAGE_TO_VIDEO}
  And studioRepository.readMediaBytes("cache://reference.png") returns PNG bytes
  And falAdapter.generateVideo is stubbed to return success
  When port.generateVideo("animate", provider, VideoGenerationSettings(referenceImageUri = "cache://reference.png")) is called
  Then the result isSuccess is true
  And falAdapter.generateVideo was called with referenceImage = the loaded PNG bytes and supportsImageToVideo = true
```

### VIDPORT-02 — T2V-only provider with reference image ⇒ fail fast
```gherkin
Scenario: The video port fails fast when image-to-video is unsupported and a reference is present
  Given a fal MediaProviderAsset with modelName "fal-ai/minimax/video-01" and tasks {TEXT_TO_VIDEO}
  When port.generateVideo("animate", provider, VideoGenerationSettings(referenceImageUri = "cache://reference.png")) is called
  Then the result isFailure is true
  And the exception is an IllegalArgumentException
  And the exception message equals "Model 'fal-ai/minimax/video-01' does not support image-to-video."
  And studioRepository.readMediaBytes was never called
  And falAdapter.generateVideo was never called
```

### VIDPORT-03 — T2V-only provider, no reference ⇒ succeeds
```gherkin
Scenario: The video port allows text-to-video without a reference and forwards supportsImageToVideo = false
  Given a fal MediaProviderAsset with tasks {TEXT_TO_VIDEO}
  And falAdapter.generateVideo is stubbed to return success
  When port.generateVideo("animate", provider, VideoGenerationSettings(referenceImageUri = null)) is called
  Then the result isSuccess is true
  And falAdapter.generateVideo was called with referenceImage = null and supportsImageToVideo = false
```

### VM-01 — IMAGE mode resolves edit slot when reference attached
```gherkin
Scenario: With a reference image, IMAGE mode resolves the IMAGE_EDIT default slot
  Given defaults assign an IMAGE_GENERATION provider and a distinct IMAGE_EDIT provider
  And the ViewModel mediaType is IMAGE
  When a reference image uri is set via onUpdateReferenceImage("cache://ref.png")
  Then the active resolved provider corresponds to the IMAGE_EDIT default
  And with the reference cleared the active resolved provider corresponds to the IMAGE_GENERATION default
```

### VM-02 — `hasImageEditModel` / `hasImageToVideoModel` reflect defaults
```gherkin
Scenario: Task-flag state mirrors which default slots are populated
  Given defaults assign an IMAGE_EDIT provider and an IMAGE_TO_VIDEO provider
  When the StudioUiState is collected via Turbine
  Then uiState.hasImageEditModel is true
  And uiState.hasImageToVideoModel is true
```

### VM-03 — No I2V default ⇒ `hasImageToVideoModel == false`
```gherkin
Scenario: Absent I2V default yields a false flag
  Given defaults assign only a VIDEO_GENERATION (text-to-video) provider and no IMAGE_TO_VIDEO provider
  When the StudioUiState is collected via Turbine
  Then uiState.hasImageToVideoModel is false
  And uiState.hasImageEditModel is false
```

### VM-04 — Animate without I2V default emits error, no generation
```gherkin
Scenario: Animating with no image-to-video default surfaces an error and submits nothing
  Given defaults assign only a VIDEO_GENERATION provider and no IMAGE_TO_VIDEO provider
  And a saved image asset "asset-1" exists
  When onAnimateMedia("asset-1", autoAnimate = true) is called
  Then no video generation request is started (StudioGenerationCoordinator.startDetailVideoGeneration is not invoked)
  And a VideoGenerationState.Error is observed with message "No image-to-video model is set. Choose one in Settings to animate."
```

### COORD-01 — `startDetailVideoGeneration` blocked without I2V default
```gherkin
Scenario: The generation coordinator refuses image-to-video when the slot is empty
  Given a source StudioMediaAsset "source-1" with localUri "file://reference.jpg"
  When startDetailVideoGeneration(sourceAsset, customPrompt = null, hasImageToVideoModel = false) is called
  Then dispatcher.startGeneration is never invoked
  And the videoGenerationState becomes VideoGenerationState.Error with sourceAssetId "source-1"
  And that VideoGenerationState.Error message equals "No image-to-video model is set. Choose one in Settings to animate."
```

### UI-01 — IMAGE picker hidden when no edit model (fail closed)
```gherkin
Scenario: The reference-image picker is hidden in IMAGE mode without an edit model
  Given a Studio input dock rendered with mediaType = IMAGE and hasImageEditModel = false
  When the dock is composed
  Then no node with contentDescription "Reference image" exists
```

### UI-02 — IMAGE picker shown when edit model present
```gherkin
Scenario: The reference-image picker is shown in IMAGE mode with an edit model
  Given a Studio input dock rendered with mediaType = IMAGE and hasImageEditModel = true
  When the dock is composed
  Then a node with contentDescription "Reference image" exists and is enabled
```

### UI-03 — VIDEO picker hidden when no I2V model (fail closed)
```gherkin
Scenario: The reference-image picker is hidden in VIDEO mode without an image-to-video model
  Given a Studio input dock rendered with mediaType = VIDEO and hasImageToVideoModel = false
  When the dock is composed
  Then no node with contentDescription "Reference image" exists
```

### UI-04 — Animate disabled + exact tooltip when no I2V model
```gherkin
Scenario: Animate is disabled with the corrected tooltip when no image-to-video model exists
  Given a fullscreen media detail rendered for an IMAGE asset with hasImageToVideoModel = false
  When the action list is built
  Then the "Animate" action enabled is false
  And the "Animate" action disabledTooltip equals "Your video model only supports text-to-video. Set an image-to-video model in Settings to animate."
  And the "Quick Animate" action enabled is false
```

### UI-05 — Animate enabled when I2V model present
```gherkin
Scenario: Animate is enabled when an image-to-video model exists
  Given a fullscreen media detail rendered for an IMAGE asset with hasImageToVideoModel = true
  When the action list is built
  Then the "Animate" action enabled is true
  And the "Animate" action disabledTooltip is null
```

### SETTINGS-01 — Four Studio & Media slots offered
```gherkin
Scenario: The model configuration screen offers four media slots
  Given the ModelConfigurationScreen studioMediaTypes list
  Then it equals [IMAGE_GENERATION, IMAGE_EDIT, VIDEO_GENERATION, IMAGE_TO_VIDEO]
  And its size is exactly 4
```

### SETTINGS-02 — Edit-slot picker filters to `EDIT_IMAGE` providers
```gherkin
Scenario: The IMAGE_EDIT slot only offers providers whose tasks include EDIT_IMAGE
  Given media providers: A with tasks {TEXT_TO_IMAGE}, B with tasks {TEXT_TO_IMAGE, EDIT_IMAGE}
  And the assignment slot is ModelType.IMAGE_EDIT
  When the offered mediaAssets list is computed
  Then it contains B
  And it does NOT contain A
```

### SETTINGS-03 — I2V-slot picker filters to `IMAGE_TO_VIDEO` providers
```gherkin
Scenario: The IMAGE_TO_VIDEO slot only offers providers whose tasks include IMAGE_TO_VIDEO
  Given media providers: C with tasks {TEXT_TO_VIDEO}, D with tasks {IMAGE_TO_VIDEO}
  And the assignment slot is ModelType.IMAGE_TO_VIDEO
  When the offered mediaAssets list is computed
  Then it contains D
  And it does NOT contain C
```

### SETTINGS-04 — Provider save persists selected `tasks`
```gherkin
Scenario: Saving a media provider draft persists its selected tasks
  Given a MediaProviderAssetUi draft with capability IMAGE and tasks {TEXT_TO_IMAGE, EDIT_IMAGE}
  When onSaveMediaProvider is invoked
  Then the MediaProviderAsset passed to the repository has tasks {TEXT_TO_IMAGE, EDIT_IMAGE}
```

### SETTINGS-05 — New slot maps to correct `ModelType` on set-default
```gherkin
Scenario: Setting a default for the edit task targets the IMAGE_EDIT slot
  Given a media provider with tasks including EDIT_IMAGE
  When the coordinator sets it as the default for the EDIT_IMAGE task
  Then assignmentsUseCases.setDefaultModel is called with modelType ModelType.IMAGE_EDIT
```

---

## Error and Edge Case Scenarios

Covered above by: VIDADAPTER-02 (image-mandatory video with no reference), IMGPORT-02 / VIDPORT-02 (fail-fast ports when task unsupported and reference present), VM-04 / COORD-01 (animate blocked when no I2V default), UI-01 / UI-03 (fail-closed hidden picker), UI-04 (Animate disabled), SYNC-03 (legacy doc missing `tasks_mask` defaults to 0 = fail-closed), MASK-06 (forward-compat unknown bit ignored, no crash).

---

## Mutation-Defense Risk and Scenarios

These scenarios are designed to FAIL against the most plausible lazy/hardcoded implementations.

### MUT-01 — T2I-only must not attach an image (kills hardcoded `supportsEdit = true`)
```gherkin
Scenario: Hardcoding edit support is caught by a text-to-image-only request
  Given a FalService stub that captures the submitQueue body
  And FalImageGenerationAdapter.generateImage is called with referenceImage = some PNG bytes and supportsEdit = false
  When the request body is captured
  Then the body does NOT contain key "image_url"
  And the body does NOT contain key "image"
```
> **Defeats:** an implementer who keeps "every image model can edit" by ignoring `supportsEdit` (or hardcoding it to `true`, or always attaching `image_url` when `referenceImage != null`). Such an implementation attaches `image_url`/`image` and fails this assertion. (Distinct from IMGADAPTER-02 by being a focused single-purpose mutation guard with no success path noise.)

### MUT-02 — Optional-image must not collapse to image-only (kills required==present shortcut)
```gherkin
Scenario: An optional image input must advertise BOTH tasks, not just the edit task
  Given the coarse kind is VIDEO
  And the schema imageInput is OPTIONAL
  When the catalog derives tasks for the model
  Then the resulting set size is exactly 2
  And the set contains TEXT_TO_VIDEO
  And the set contains IMAGE_TO_VIDEO
  And the set is NOT equal to {IMAGE_TO_VIDEO}
```
> **Defeats:** an implementer who treats "image field present" as "image required" and collapses the optional row to the single image task `{IMAGE_TO_VIDEO}`. The explicit `size == 2` and `NOT {IMAGE_TO_VIDEO}` assertions fail that mutation. (Pairs with PARSE-06 for the image kind.)

### MUT-03 — Edit-slot picker must exclude T2I-only provider (kills `capability == kind` filter)
```gherkin
Scenario: Filtering by coarse kind instead of task is caught at the edit slot
  Given two IMAGE-kind providers: A with tasks {TEXT_TO_IMAGE} and B with tasks {TEXT_TO_IMAGE, EDIT_IMAGE}
  And the assignment slot is ModelType.IMAGE_EDIT
  When the offered mediaAssets list is computed
  Then provider A is excluded
  And provider B is included
  And the offered list size is exactly 1
```
> **Defeats:** an implementer who leaves the old `filter { it.capability == capability }` (both A and B are `MediaCapability.IMAGE`). That filter returns both providers, so `size == 1` and "A excluded" fail. Only a task-aware filter (`EDIT_IMAGE in asset.tasks`) passes.

---

## Notes for the implementer (test conventions)

- `:core:domain` mask/mapping tests (`MASK-*`, `MAP-*`, `MODELTYPE-*`) are pure JVM JUnit 5; no Robolectric needed.
- `:feature:inference` adapter/port/parser/catalog tests follow the existing `FalVideoGenerationAdapterTest` / `ImageGenerationPortImplTest` style: `mockk<FalService>()` / `mockk` ports with `coEvery`/`coVerify`/`verify`, `runTest`, `assertArrayEquals`/`assertTrue`/`assertEquals` from `org.junit.jupiter.api.Assertions`. Capture request bodies with a MockK `slot<JsonObject>()` on `submitQueue`.
- `:core:data` mapper/sync tests follow `MediaProviderRepositoryImplTest` and `MemoriesSyncHandlerTest`: JUnit 5, MockK DAOs, `testFirestoreSyncDaos`, `@DisplayName`, backtick names.
- `:feature:studio` ViewModel tests follow `MultimodalViewModelTest` / `MultimodalViewModelAnimateTest`: `@ExtendWith(MainDispatcherExtension::class)`, Turbine on `uiState`, fakes/mocks for repositories. UI gating tests (`UI-*`) use `createComposeRule()` with `onNodeWithContentDescription("Reference image")` and assertions on `FullscreenMediaAction` fields.
- `:feature:settings` tests follow `MediaProvidersCoordinatorTest`: assert on the `MediaProviderAsset`/`ModelType` passed into the use cases and on the computed offered-provider list.
- **Compile fallout (not new scenarios):** removing `ProviderCapabilities.supportsReferenceImage` and renaming the parser field to `imageInput` breaks existing tests that reference those symbols — update them in the Implementation phase: `GetProviderCapabilitiesUseCaseTest` (29, 51), `MediaToolDefinitionFactoryTest` (28, 60), `MultimodalViewModelTest` (154, 250, 303), `MultimodalViewModelAnimateTest` (104), `FalOpenApiInputSchemaParserTest` (57). See `spec.md` §10.
- Expected values in every `Then` are literals defined here or in `spec.md` §6–§7; never read them back from production code.

# Test Spec: Consolidated Media Generation Use Case

## Test Framework and Run Command

**Frameworks:**

- JUnit 5 (`org.junit.jupiter.api`) for all unit tests
- MockK for `Generate*UseCase` and `StudioRepositoryPort` collaborators in domain/data tests
- `kotlinx-coroutines-test` with `runTest` for suspend APIs
- Robolectric (`@RunWith(RobolectricTestRunner::class)`) for `MultimediaGenerationService` `onStartCommand` wiring tests in `:feature:studio-generation-service`

**Targeted red/green command:**

```bash
./gradlew :core:domain:testDebugUnitTest \
  --tests "com.browntowndev.enclave.domain.usecase.media.MediaGenerationRequestMapperTest" \
  --tests "com.browntowndev.enclave.domain.usecase.media.GenerateMediaUseCaseTest" \
  --no-daemon --stacktrace

./gradlew :core:data:testDebugUnitTest \
  --tests "com.browntowndev.enclave.core.data.repository.MediaToolExecutorTest" \
  --no-daemon --stacktrace

./gradlew :feature:studio-generation-service:testDebugUnitTest \
  --tests "com.browntowndev.enclave.feature.studio.service.MultimediaGenerationServiceTest" \
  --no-daemon --stacktrace

./gradlew :core:data:testDebugUnitTest \
  --tests "com.browntowndev.enclave.core.data.repository.MediaGenerationDeadCodeGuardTest" \
  --no-daemon --stacktrace

./gradlew :feature:studio-generation-service:testDebugUnitTest \
  --tests "com.browntowndev.enclave.feature.studio.service.MultimediaGenerationDeadCodeGuardTest" \
  --no-daemon --stacktrace
```

**Completion gate for implementation executor:**

```bash
./gradlew :core:domain:assembleDebug :core:data:assembleDebug \
  :core:domain:testDebugUnitTest :core:data:testDebugUnitTest \
  :feature:studio-generation-service:testDebugUnitTest \
  :feature:studio-generation-service:testDebugUnitTest \
  ktlintCheck detekt --no-daemon --stacktrace

# Dead-code grep gate (must exit 0 — zero matches)
! rg "ImageGenerationPort|VideoGenerationPort|MediaProviderRepositoryPort|getMediaProviders\\(\\)\\.first" \
  feature/studio-generation-service/src/main/kotlin
! rg "cacheEphemeralMedia|addEphemeralMedia|executeImage\\(|executeVideo\\(|executeMusic\\(" \
  core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/MediaToolExecutor.kt
```

---

## Scenario Index

### Mapper (`MediaGenerationRequestMapper`)

- S-MAP-01 — Image tool maps prompt, aspect ratio, quality, count
- S-MAP-02 — Image tool unknown aspect ratio defaults to `AspectRatio.ONE_ONE`
- S-MAP-03 — Image tool count coerced to 4 when count is 99
- S-MAP-04 — Video tool maps duration and resolution literally
- S-MAP-05 — Music tool maps duration literally
- S-MAP-06 — Studio mapping preserves settings and sets `STUDIO_FOREGROUND` origin
- E-MAP-01 — Unsupported tool name throws `IllegalArgumentException`
- E-MAP-02 — Image tool count coerced to 1 when count is 0

### Use case (`GenerateMediaUseCase`)

- S-GM-01 — IMAGE dispatches to `GenerateImageUseCase` with same prompt and settings
- S-GM-02 — VIDEO dispatches to `GenerateVideoUseCase` with same prompt and settings
- S-GM-03 — MUSIC dispatches to `GenerateMusicUseCase` with same prompt and settings
- S-GM-04 — Success caches with `mediaType` `"IMAGE"` and calls `addEphemeralMedia` once per byte array
- S-GM-05 — IMAGE multi-byte success returns two `GeneratedMediaAsset` entries
- S-GM-06 — Delegate failure returns failure without cache or `addEphemeralMedia`
- S-GM-07 — `CHAT_TOOL` origin uses id prefix `chat_media_`
- S-GM-08 — `STUDIO_FOREGROUND` origin uses `createdAt` `1000` then `1001` for two assets when base time stubbed to `1000`
- E-GM-01 — Mismatched settings type returns `IllegalArgumentException` message `Settings type does not match capability IMAGE`

### Chat adapter (`MediaToolExecutor`)

- S-CHAT-01 — Image tool success JSON contains `"success":true` and success message
- S-CHAT-02 — Image tool failure JSON contains `"success":false` and provider error text
- S-CHAT-03 — Success JSON includes `asset_ids` value with prefix `chat_media_`
- S-CHAT-04 — Executor never calls `GenerateImageUseCase` directly
- E-CHAT-01 — Unsupported tool throws `IllegalArgumentException` with message `Unsupported media tool: unknown_tool`

### Studio service (`MultimediaGenerationService`)

- S-STU-01 — IMAGE start invokes `GenerateMediaUseCase` exactly once
- S-STU-02 — Service does not call `MediaProviderRepositoryPort.getMediaProviders`
- S-STU-03 — Success path does not call `reportGenerationError`
- S-STU-04 — Use case failure calls `reportGenerationError` with `No image generation provider assigned`
- S-STU-05 — Missing prompt does not invoke `GenerateMediaUseCase`
- S-STU-06 — Intent image settings with `generationCount` 3 reach use case request settings `generationCount` 3
- E-STU-01 — `ACTION_STOP` does not invoke `GenerateMediaUseCase`

### Mutation defense

- M-GM-01 — `GenerateMediaUseCase` IMAGE path never calls `ImageGenerationPort`
- M-STU-01 — Studio IMAGE uses `GenerateImageUseCase` via `GenerateMediaUseCase`, not `ImageGenerationPort`
- M-CLEAN-01 — Guard tests fail if legacy `executeImage` private method reappears in `MediaToolExecutor`

### Dead code cleanup (`MediaGenerationDeadCodeGuardTest`, `MultimediaGenerationDeadCodeGuardTest`)

- CLEAN-01 — `MediaToolExecutor.kt` source contains zero occurrences of `cacheEphemeralMedia`
- CLEAN-02 — `MediaToolExecutor.kt` source contains zero occurrences of `addEphemeralMedia`
- CLEAN-03 — `MediaToolExecutor.kt` source contains zero occurrences of `executeImage(`
- CLEAN-04 — `MediaToolExecutor.kt` source contains zero occurrences of `GenerateImageUseCase`
- CLEAN-05 — `MultimediaGenerationService.kt` source contains zero occurrences of `ImageGenerationPort`
- CLEAN-06 — `MultimediaGenerationService.kt` source contains zero occurrences of `VideoGenerationPort`
- CLEAN-07 — `MultimediaGenerationService.kt` source contains zero occurrences of `MediaProviderRepositoryPort`

---

## Happy Path Scenarios

### S-MAP-01: Image tool maps prompt, aspect ratio, quality, count

- **Given:** `ToolCallRequest` with `toolName` `generate_image`, `argumentsJson` `{"prompt":"a sunset","aspect_ratio":"16:9","quality":"hd","count":2}`
- **When:** `MediaGenerationRequestMapper.fromToolCall(request)` is invoked
- **Then:** `capability` equals `MediaCapability.IMAGE`
- **Then:** `prompt` equals `a sunset`
- **Then:** `settings` is `ImageGenerationSettings` with `aspectRatio` `AspectRatio.SIXTEEN_NINE`, `quality` `GenerationQuality.HD`, `generationCount` `2`
- **Then:** `origin` equals `MediaGenerationOrigin.CHAT_TOOL`

### S-MAP-02: Image tool unknown aspect ratio defaults to `AspectRatio.ONE_ONE`

- **Given:** `ToolCallRequest` with `toolName` `generate_image`, `argumentsJson` `{"prompt":"x","aspect_ratio":"99:99","count":1}`
- **When:** `MediaGenerationRequestMapper.fromToolCall(request)` is invoked
- **Then:** `settings` is `ImageGenerationSettings` with `aspectRatio` `AspectRatio.ONE_ONE`

### S-MAP-03: Image tool count coerced to 4 when count is 99

- **Given:** `ToolCallRequest` with `toolName` `generate_image`, `argumentsJson` `{"prompt":"x","count":99}`
- **When:** `MediaGenerationRequestMapper.fromToolCall(request)` is invoked
- **Then:** `settings` is `ImageGenerationSettings` with `generationCount` `4`

### S-MAP-04: Video tool maps duration and resolution literally

- **Given:** `ToolCallRequest` with `toolName` `generate_video`, `argumentsJson` `{"prompt":"waves","duration":8,"resolution":"720p"}`
- **When:** `MediaGenerationRequestMapper.fromToolCall(request)` is invoked
- **Then:** `capability` equals `MediaCapability.VIDEO`
- **Then:** `settings` is `VideoGenerationSettings` with `videoDuration` `8`, `videoResolution` `720p`

### S-MAP-05: Music tool maps duration literally

- **Given:** `ToolCallRequest` with `toolName` `generate_music`, `argumentsJson` `{"prompt":"lofi beat","duration":45}`
- **When:** `MediaGenerationRequestMapper.fromToolCall(request)` is invoked
- **Then:** `capability` equals `MediaCapability.MUSIC`
- **Then:** `settings` is `MusicGenerationSettings` with `duration` `45`

### S-MAP-06: Studio mapping preserves settings and sets `STUDIO_FOREGROUND` origin

- **Given:** `prompt` `studio prompt`, `capability` `MediaCapability.VIDEO`, `settings` `VideoGenerationSettings(videoDuration=10, videoResolution="1080p")`
- **When:** `MediaGenerationRequestMapper.fromStudio(prompt, capability, settings)` is invoked
- **Then:** returned `MediaGenerationRequest` equals `MediaGenerationRequest(prompt="studio prompt", capability=MediaCapability.VIDEO, settings=settings, origin=MediaGenerationOrigin.STUDIO_FOREGROUND)`

### S-GM-01: IMAGE dispatches to `GenerateImageUseCase` with same prompt and settings

- **Given:** `GenerateImageUseCase` stub returns `Result.success(listOf(byteArrayOf(1)))` for prompt `p1` and `ImageGenerationSettings(generationCount=1)`
- **When:** `GenerateMediaUseCase` is invoked with `MediaGenerationRequest(prompt="p1", capability=MediaCapability.IMAGE, settings=ImageGenerationSettings(generationCount=1), origin=MediaGenerationOrigin.CHAT_TOOL)`
- **Then:** `GenerateImageUseCase` received exactly `(prompt="p1", settings=ImageGenerationSettings(generationCount=1))`

### S-GM-02: VIDEO dispatches to `GenerateVideoUseCase` with same prompt and settings

- **Given:** `GenerateVideoUseCase` stub returns `Result.success(byteArrayOf(2))` for prompt `clip` and `VideoGenerationSettings(videoDuration=6)`
- **When:** `GenerateMediaUseCase` is invoked with `MediaGenerationRequest(prompt="clip", capability=MediaCapability.VIDEO, settings=VideoGenerationSettings(videoDuration=6), origin=MediaGenerationOrigin.CHAT_TOOL)`
- **Then:** `GenerateVideoUseCase` received exactly `(prompt="clip", settings=VideoGenerationSettings(videoDuration=6))`

### S-GM-03: MUSIC dispatches to `GenerateMusicUseCase` with same prompt and settings

- **Given:** `GenerateMusicUseCase` stub returns `Result.success(byteArrayOf(3))` for prompt `beat` and `MusicGenerationSettings(duration=30)`
- **When:** `GenerateMediaUseCase` is invoked with `MediaGenerationRequest(prompt="beat", capability=MediaCapability.MUSIC, settings=MusicGenerationSettings(duration=30), origin=MediaGenerationOrigin.CHAT_TOOL)`
- **Then:** `GenerateMusicUseCase` received exactly `(prompt="beat", settings=MusicGenerationSettings(duration=30))`

### S-GM-04: Success caches with `mediaType` `"IMAGE"` and calls `addEphemeralMedia` once per byte array

- **Given:** `GenerateImageUseCase` returns `Result.success(listOf(byteArrayOf(9), byteArrayOf(8)))`, `cacheEphemeralMedia` returns `file:///cache/a` then `file:///cache/b`
- **When:** `GenerateMediaUseCase` runs with `MediaCapability.IMAGE` and `CHAT_TOOL` origin
- **Then:** `cacheEphemeralMedia` called with `(byteArrayOf(9), "IMAGE")` and `(byteArrayOf(8), "IMAGE")`
- **Then:** `addEphemeralMedia` invoked exactly `2` times

### S-GM-05: IMAGE multi-byte success returns two `GeneratedMediaAsset` entries

- **Given:** same as S-GM-04 with distinct cache URIs `file:///cache/a` and `file:///cache/b`
- **When:** `GenerateMediaUseCase` succeeds
- **Then:** result list size equals `2`
- **Then:** first asset `localUri` equals `file:///cache/a`
- **Then:** second asset `localUri` equals `file:///cache/b`
- **Then:** both assets have `mediaType` `"IMAGE"`

### S-GM-07: `CHAT_TOOL` origin uses id prefix `chat_media_`

- **Given:** successful single-image generation with `CHAT_TOOL` origin
- **When:** `GenerateMediaUseCase` returns success list
- **Then:** first asset `id` starts with `chat_media_`

### S-GM-08: `STUDIO_FOREGROUND` origin uses `createdAt` `1000` then `1001` for two assets when base time stubbed to `1000`

- **Given:** test fixes `System.currentTimeMillis()` to return `1000` once at registration start; `GenerateImageUseCase` returns two byte arrays; `STUDIO_FOREGROUND` origin
- **When:** `GenerateMediaUseCase` succeeds
- **Then:** first asset `createdAt` equals `1000L`
- **Then:** second asset `createdAt` equals `1001L`
- **Then:** first asset `id` does not start with `chat_media_`

### S-CHAT-01: Image tool success JSON contains `"success":true` and success message

- **Given:** `GenerateMediaUseCase` returns `Result.success(listOf(GeneratedMediaAsset(id="chat_media_abc", localUri="file:///x", prompt="a sunset", mediaType="IMAGE", createdAt=0)))`
- **When:** `MediaToolExecutor.execute` runs `generate_image` tool request
- **Then:** `resultJson` contains `"success":true`
- **Then:** `resultJson` contains `"Media generated successfully and available in Studio."`
- **Then:** `resultJson` contains `"file:///x"`

### S-CHAT-03: Success JSON includes `asset_ids` value with prefix `chat_media_`

- **Given:** same asset id `chat_media_abc` as S-CHAT-01
- **When:** `MediaToolExecutor.execute` completes
- **Then:** `resultJson` contains `"chat_media_abc"`

### S-CHAT-04: Executor never calls `GenerateImageUseCase` directly

- **Given:** `MediaToolExecutor` constructed with mocked `GenerateMediaUseCase` only (no `GenerateImageUseCase` in graph)
- **When:** image tool executes successfully via use case mock
- **Then:** test fixture has zero references to `GenerateImageUseCase` mock type in `MediaToolExecutor` fields

### S-STU-01: IMAGE start invokes `GenerateMediaUseCase` exactly once

- **Given:** Robolectric `MultimediaGenerationService` with `GenerateMediaUseCase` mock returning `Result.success(emptyList())`, Intent `action` `com.browntowndev.enclave.studio.ACTION_START`, `EXTRA_PROMPT` `hello`, `EXTRA_MEDIA_TYPE` `IMAGE`
- **When:** `onStartCommand` runs
- **Then:** `GenerateMediaUseCase` invoked exactly `1` time

### S-STU-03: Success path does not call `reportGenerationError`

- **Given:** `GenerateMediaUseCase` returns `Result.success(listOf(GeneratedMediaAsset(id="id1", localUri="file:///ok", prompt="hello", mediaType="IMAGE", createdAt=0)))`
- **When:** `ACTION_START` completes
- **Then:** `reportGenerationError` called `0` times

### S-STU-06: Intent image settings with `generationCount` 3 reach use case request settings `generationCount` 3

- **Given:** Intent with `StudioGenerationIntentCodec.writeSettings` for `ImageGenerationSettings(generationCount=3)` plus prompt and `IMAGE` type; `GenerateMediaUseCase` captured request slot
- **When:** `onStartCommand` `ACTION_START` runs
- **Then:** captured `MediaGenerationRequest.settings` is `ImageGenerationSettings` with `generationCount` `3`

---

## Error and Edge Case Scenarios

### E-MAP-01: Unsupported tool name throws `IllegalArgumentException`

- **Given:** `ToolCallRequest` with `toolName` `web_search`, `argumentsJson` `{}`
- **When:** `MediaGenerationRequestMapper.fromToolCall(request)` is invoked
- **Then:** thrown exception type is `IllegalArgumentException`
- **Then:** exception message equals `Unsupported media tool: web_search`

### E-MAP-02: Image tool count coerced to 1 when count is 0

- **Given:** `ToolCallRequest` with `toolName` `generate_image`, `argumentsJson` `{"prompt":"x","count":0}`
- **When:** `MediaGenerationRequestMapper.fromToolCall(request)` is invoked
- **Then:** `settings` is `ImageGenerationSettings` with `generationCount` `1`

### S-GM-06: Delegate failure returns failure without cache or `addEphemeralMedia`

- **Given:** `GenerateImageUseCase` returns `Result.failure(IllegalStateException("No image generation provider assigned"))`
- **When:** `GenerateMediaUseCase` runs
- **Then:** result `isFailure` is `true`
- **Then:** `cacheEphemeralMedia` called `0` times
- **Then:** `addEphemeralMedia` called `0` times
- **Then:** `result.exceptionOrNull()?.message` equals `No image generation provider assigned`

### E-GM-01: Mismatched settings type returns `IllegalArgumentException` message `Settings type does not match capability IMAGE`

- **Given:** `MediaGenerationRequest` with `capability` `MediaCapability.IMAGE` and `settings` `MusicGenerationSettings(duration=30)`
- **When:** `GenerateMediaUseCase` is invoked
- **Then:** result `isFailure` is `true`
- **Then:** `result.exceptionOrNull()` is `IllegalArgumentException`
- **Then:** `result.exceptionOrNull()?.message` equals `Settings type does not match capability IMAGE`

### S-CHAT-02: Image tool failure JSON contains `"success":false` and provider error text

- **Given:** `GenerateMediaUseCase` returns `Result.failure(IllegalStateException("No image generation provider assigned"))`
- **When:** `MediaToolExecutor.execute` runs `generate_image`
- **Then:** `resultJson` contains `"success":false`
- **Then:** `resultJson` contains `"No image generation provider assigned"`

### E-CHAT-01: Unsupported tool throws `IllegalArgumentException` with message `Unsupported media tool: unknown_tool`

- **Given:** `ToolCallRequest` with `toolName` `unknown_tool`
- **When:** `MediaToolExecutor.execute` is invoked
- **Then:** thrown `IllegalArgumentException` with message `Unsupported media tool: unknown_tool`

### S-STU-04: Use case failure calls `reportGenerationError` with `No image generation provider assigned`

- **Given:** `GenerateMediaUseCase` returns `Result.failure(IllegalStateException("No image generation provider assigned"))`, valid `ACTION_START` Intent with prompt
- **When:** service coroutine completes
- **Then:** `reportGenerationError` called with argument exactly `No image generation provider assigned`

### S-STU-05: Missing prompt does not invoke `GenerateMediaUseCase`

- **Given:** Intent `action` `com.browntowndev.enclave.studio.ACTION_START` without `EXTRA_PROMPT`
- **When:** `onStartCommand` runs
- **Then:** `GenerateMediaUseCase` invoked `0` times

### S-STU-02: Service does not call `MediaProviderRepositoryPort.getMediaProviders`

- **Given:** `MultimediaGenerationService` field graph after refactor
- **When:** production service class is inspected via test module compile or reflection helper asserting injected fields
- **Then:** `MediaProviderRepositoryPort` is not a field on `MultimediaGenerationService`

### E-STU-01: `ACTION_STOP` does not invoke `GenerateMediaUseCase`

- **Given:** Intent `action` `com.browntowndev.enclave.studio.ACTION_STOP`
- **When:** `onStartCommand` runs
- **Then:** `GenerateMediaUseCase` invoked `0` times

---

## Mutation Defense

### Lazy implementation risk (domain)

A minimal `GenerateMediaUseCase` could call `ImageGenerationPort` for `IMAGE` while still passing unit tests that only mock `GenerateImageUseCase` if the test never asserts port absence.

### M-GM-01: `GenerateMediaUseCase` IMAGE path never calls `ImageGenerationPort`

- **Given:** `GenerateMediaUseCase` under test with `ImageGenerationPort` mock spy in test fixture (not injected into production constructor — test uses bytecode/compile-time assertion that `GenerateMediaUseCase` source has zero `ImageGenerationPort` references, OR test module fails if `GenerateMediaUseCase` constructor adds `ImageGenerationPort` parameter)
- **When:** IMAGE request succeeds via stubbed `GenerateImageUseCase`
- **Then:** `ImageGenerationPort` mock has `0` recorded calls
- **Then:** `GenerateImageUseCase` mock has exactly `1` recorded call

### Lazy implementation risk (Studio service)

Studio could keep `ImageGenerationPort.generateImage` in `MultimediaGenerationService` while also calling `GenerateMediaUseCase` for telemetry — fixes nothing.

### M-STU-01: Studio IMAGE uses `GenerateImageUseCase` via `GenerateMediaUseCase`, not `ImageGenerationPort`

- **Given:** Robolectric service test with `ImageGenerationPort` mock and `GenerateMediaUseCase` mock; `ACTION_START` for `IMAGE` with prompt `port-check`
- **When:** `onStartCommand` finishes
- **Then:** `ImageGenerationPort` mock called `0` times
- **Then:** `GenerateMediaUseCase` mock called `1` time with `MediaGenerationRequest` whose `prompt` equals `port-check` and `capability` equals `MediaCapability.IMAGE`

---

## Dead Code Cleanup Scenarios

Guard tests read production source from the module source set (e.g. `src/main/kotlin/.../MediaToolExecutor.kt` resolved via test resources or relative path from project dir). Each test asserts substring absence unless noted.

### CLEAN-01: `MediaToolExecutor.kt` source contains zero occurrences of `cacheEphemeralMedia`

- **Given:** post-refactor `MediaToolExecutor.kt` production source file on disk
- **When:** guard test reads file text
- **Then:** occurrence count of `cacheEphemeralMedia` equals `0`

### CLEAN-02: `MediaToolExecutor.kt` source contains zero occurrences of `addEphemeralMedia`

- **Given:** post-refactor `MediaToolExecutor.kt` production source file on disk
- **When:** guard test reads file text
- **Then:** occurrence count of `addEphemeralMedia` equals `0`

### CLEAN-03: `MediaToolExecutor.kt` source contains zero occurrences of `executeImage(`

- **Given:** post-refactor `MediaToolExecutor.kt` production source file on disk
- **When:** guard test reads file text
- **Then:** occurrence count of `executeImage(` equals `0`

### CLEAN-04: `MediaToolExecutor.kt` source contains zero occurrences of `GenerateImageUseCase`

- **Given:** post-refactor `MediaToolExecutor.kt` production source file on disk
- **When:** guard test reads file text
- **Then:** occurrence count of `GenerateImageUseCase` equals `0`

### CLEAN-05: `MultimediaGenerationService.kt` source contains zero occurrences of `ImageGenerationPort`

- **Given:** post-refactor `MultimediaGenerationService.kt` production source file on disk
- **When:** guard test reads file text
- **Then:** occurrence count of `ImageGenerationPort` equals `0`

### CLEAN-06: `MultimediaGenerationService.kt` source contains zero occurrences of `VideoGenerationPort`

- **Given:** post-refactor `MultimediaGenerationService.kt` production source file on disk
- **When:** guard test reads file text
- **Then:** occurrence count of `VideoGenerationPort` equals `0`

### CLEAN-07: `MultimediaGenerationService.kt` source contains zero occurrences of `MediaProviderRepositoryPort`

- **Given:** post-refactor `MultimediaGenerationService.kt` production source file on disk
- **When:** guard test reads file text
- **Then:** occurrence count of `MediaProviderRepositoryPort` equals `0`

### M-CLEAN-01: Guard tests fail if legacy `executeImage` private method reappears in `MediaToolExecutor`

- **Given:** hypothetical `MediaToolExecutor.kt` source containing the substring `private suspend fun executeImage(`
- **When:** guard test logic checks for `executeImage(` (same assertion as CLEAN-03)
- **Then:** test fails with assertion message containing `executeImage(`

---

## Scenario → Implementation Task Mapping

| Scenario ID | Spec task |
|-------------|-----------|
| S-MAP-01 … S-MAP-06, E-MAP-01, E-MAP-02 | Task 2 |
| S-GM-01 … S-GM-08, E-GM-01, M-GM-01 | Task 3 |
| S-CHAT-01 … S-CHAT-04, E-CHAT-01 | Task 4 |
| S-STU-01 … S-STU-06, E-STU-01, M-STU-01 | Task 5 |
| CLEAN-01 … CLEAN-07, M-CLEAN-01 | Task 6 |
| All scenario IDs | Task 7 |

# Spec: Consolidated Media Generation Use Case

**Ticket ID:** `FEAT-generate-media-use-case`  
**Date:** 2026-05-22  
**Contracts read:** `AGENTS.md`, `ARCHITECTURE_RULES.md`, `DATA_LAYER_RULES.md`, `UNIT_TESTING.md`

---

## 1. Feature Summary

### Problem

Studio foreground generation and chat tool execution both orchestrate image/video/music generation, ephemeral byte caching, and `StudioRepositoryPort.addEphemeralMedia` registration. The paths have diverged:

| Path | Location | Current behavior (verified) |
|------|----------|-----------------------------|
| Chat tools | `core/data/.../MediaToolExecutor.kt` | Maps tool params → `*GenerationSettings`, calls `GenerateImageUseCase` / `GenerateVideoUseCase` / `GenerateMusicUseCase`, caches bytes, registers assets with `chat_media_` id prefix, builds tool JSON inline |
| Studio foreground | `feature/studio-generation-service/.../MultimediaGenerationService.kt` | **IMAGE bypasses** `GenerateImageUseCase` and calls `ImageGenerationPort` with `mediaProviderRepository.getMediaProviders().firstOrNull()`; video/music use use cases but duplicate cache/register; ignores user-assigned default provider resolution |

This causes Studio bugs (wrong provider selection, settings not applied on image) and guarantees future drift whenever one path is fixed.

### Design target

| # | Requirement |
|---|-------------|
| 1 | Introduce `:core:domain` `GenerateMediaUseCase` as the single orchestrator for generation + ephemeral cache + asset registration |
| 2 | Introduce `MediaGenerationRequest` + `MediaGenerationRequestMapper` to centralize tool and Studio inputs |
| 3 | Always dispatch through existing `GenerateImageUseCase`, `GenerateVideoUseCase`, `GenerateMusicUseCase` (provider resolution stays in those use cases via `DefaultModelRepositoryPort`) |
| 4 | Thin `MediaToolExecutor`: map tool → request → `GenerateMediaUseCase` → tool JSON only (no direct `Generate*` or repository cache calls) |
| 5 | Thin `MultimediaGenerationService`: Intent decode + foreground notification + `GenerateMediaUseCase` + `reportGenerationError`; remove `ImageGenerationPort`, `VideoGenerationPort`, and `getMediaProviders().firstOrNull()` from service |
| 6 | Keep `AndroidStudioGenerationDispatcher` and `StudioGenerationIntentCodec` unchanged (encode Intent + `startForegroundService` only) |
| 7 | Chat tool path must **not** start `MultimediaGenerationService` or any other service |
| 8 | Service must **not** start another service |
| 9 | Keep `*GenerationPort` adapters unchanged; no dynamic model switching beyond settings on the request |
| 10 | No Room schema or new persistence tables |
| 11 | Remove superseded orchestration code from refactored entry points; no duplicate cache/register paths, unused injections, or orphaned private helpers may remain after merge |

### Non-goals

- Changing `GenerateImageUseCase` / `GenerateVideoUseCase` / `GenerateMusicUseCase` provider-selection logic
- Replacing `StudioGenerationIntentCodec` or moving Intent extras into domain
- Robolectric UI tests for notification channels (notification behavior is manual QA only)
- Gallery promotion, album moves, or permanent `saveMedia` flows
- UniFFI / agent-core changes

### Current call flow (verified)

```text
Chat inference (background coroutine already)
  CompositeToolExecutor → MediaToolExecutor
    → map tool params → Generate*UseCase
    → StudioRepositoryPort.cacheEphemeralMedia + addEphemeralMedia
    → ToolExecutionResult JSON

Studio UI
  StudioGenerationCoordinator → AndroidStudioGenerationDispatcher
    → startForegroundService(MultimediaGenerationService)
  MultimediaGenerationService.onStartCommand
    → (IMAGE) ImageGenerationPort + first provider in list  ← bug
    → (VIDEO/MUSIC) Generate*UseCase
    → duplicate cache/register
    → reportGenerationError on failure
```

### Target call flow

```text
Chat: MediaToolExecutor → MediaGenerationRequestMapper.fromToolCall → GenerateMediaUseCase → tool JSON

Studio: MultimediaGenerationService (shell)
  → StudioGenerationIntentCodec.readSettings (unchanged)
  → MediaGenerationRequestMapper.fromStudio
  → GenerateMediaUseCase
  → reportGenerationError on failure
```

---

## 2. File Structure Table

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/media/MediaGenerationOrigin.kt` | Distinguish chat vs Studio asset id and timestamp policies |
| Create | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/media/MediaGenerationRequest.kt` | Unified input: prompt, capability, settings, origin |
| Create | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/media/GeneratedMediaAsset.kt` | Domain result model returned by `GenerateMediaUseCase` (not a port type) |
| Create | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/media/MediaGenerationRequestMapper.kt` | Map `ToolCallRequest` and Studio triple → `MediaGenerationRequest` |
| Create | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/media/GenerateMediaUseCase.kt` | Dispatch to `Generate*UseCase`, cache bytes, register ephemeral assets |
| Create | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/media/MediaGenerationRequestMapperTest.kt` | Mapping defaults, coercion, Studio passthrough |
| Create | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/media/GenerateMediaUseCaseTest.kt` | Dispatch, registration, failure, origin policies |
| Modify | `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/MediaToolExecutor.kt` | Delegate to mapper + `GenerateMediaUseCase`; retain JSON builders only |
| Modify | `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/repository/MediaToolExecutorTest.kt` | Mock `GenerateMediaUseCase`; assert JSON contracts and no direct `Generate*` calls |
| Modify | `feature/studio-generation-service/src/main/kotlin/com/browntowndev/enclave/feature/studio/service/MultimediaGenerationService.kt` | Foreground shell + `GenerateMediaUseCase`; remove port/provider duplication |
| Modify | `feature/studio-generation-service/src/test/kotlin/com/browntowndev/enclave/feature/studio/service/MultimediaGenerationServiceTest.kt` | Robolectric service tests for Intent → use case wiring and error reporting |
| Create | `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/repository/MediaGenerationDeadCodeGuardTest.kt` | Compile-time/source assertions that refactored adapters shed legacy dependencies |
| Create | `feature/studio-generation-service/src/test/kotlin/com/browntowndev/enclave/feature/studio/service/MultimediaGenerationDeadCodeGuardTest.kt` | Source assertions that service shed legacy port/use-case/provider injections |

**No changes (verified consumers / boundaries):**

| Path | Why |
|------|-----|
| `feature/studio/.../AndroidStudioGenerationDispatcher.kt` | Remains Intent encode + `startForegroundService` / `startService` stop only |
| `feature/studio-generation-service/.../StudioGenerationIntentCodec.kt` | Remains Android Intent serialization for settings |
| `core/domain/.../GenerateImageUseCase.kt` | Unchanged; invoked by `GenerateMediaUseCase` |
| `core/domain/.../GenerateVideoUseCase.kt` | Unchanged |
| `core/domain/.../GenerateMusicUseCase.kt` | Unchanged |
| `core/data/.../CompositeToolExecutor.kt` | Routing table unchanged; only `MediaToolExecutor` internals change |
| `core/domain/.../port/media/*GenerationPort.kt` and data adapters | Unchanged per ticket |
| `core/domain/.../port/repository/StudioRepositoryPort.kt` | Contract unchanged; implementation reused |

---

## 3. API Contracts and Interfaces

### 3.1 `MediaGenerationOrigin`

```kotlin
enum class MediaGenerationOrigin {
    CHAT_TOOL,
    STUDIO_FOREGROUND,
}
```

| Origin | Asset `id` format | `createdAt` policy |
|--------|-------------------|------------------|
| `CHAT_TOOL` | `"chat_media_"` + `UUID.randomUUID()` | `System.currentTimeMillis()` per registered asset (same millisecond allowed) |
| `STUDIO_FOREGROUND` | `UUID.randomUUID().toString()` with no prefix | `baseTime + index` where `baseTime = System.currentTimeMillis()` at start of registration loop and `index` is `0..n-1` |

### 3.2 `MediaGenerationRequest`

```kotlin
data class MediaGenerationRequest(
    val prompt: String,
    val capability: MediaCapability,
    val settings: GenerationSettings,
    val origin: MediaGenerationOrigin,
)
```

- `capability` uses `com.browntowndev.enclave.domain.model.config.MediaCapability` (`IMAGE`, `VIDEO`, `MUSIC`).
- `settings` must match capability (`ImageGenerationSettings` for `IMAGE`, etc.). Mapper is responsible for producing consistent pairs.

### 3.3 `GeneratedMediaAsset` (domain model)

```kotlin
data class GeneratedMediaAsset(
    val id: String,
    val localUri: String,
    val prompt: String,
    val mediaType: String,
    val createdAt: Long,
)
```

- `mediaType` is always `capability.name` (`"IMAGE"`, `"VIDEO"`, `"MUSIC"`).
- This type is **not** `StudioMediaAsset` from the port package; callers map fields when needed.

### 3.4 `GenerateMediaUseCase`

```kotlin
class GenerateMediaUseCase @Inject constructor(
    private val generateImageUseCase: GenerateImageUseCase,
    private val generateVideoUseCase: GenerateVideoUseCase,
    private val generateMusicUseCase: GenerateMusicUseCase,
    private val studioRepository: StudioRepositoryPort,
) {
    suspend operator fun invoke(request: MediaGenerationRequest): Result<List<GeneratedMediaAsset>>
}
```

**Dispatch contract:**

| `capability` | Delegate call | Bytes shape |
|--------------|---------------|-------------|
| `IMAGE` | `generateImageUseCase(request.prompt, request.settings as ImageGenerationSettings)` | `List<ByteArray>` |
| `VIDEO` | `generateVideoUseCase(request.prompt, request.settings as VideoGenerationSettings)` | single `ByteArray` wrapped as one-element list |
| `MUSIC` | `generateMusicUseCase(request.prompt, request.settings as MusicGenerationSettings)` | single `ByteArray` wrapped as one-element list |

**Success contract:**

1. For each byte array in order, call `studioRepository.cacheEphemeralMedia(bytes, mediaType)` where `mediaType` is `request.capability.name`.
2. Build `GeneratedMediaAsset` using origin policy (Section 3.1).
3. Call `studioRepository.addEphemeralMedia` with port `StudioMediaAsset` mapped from `GeneratedMediaAsset` fields.
4. Return `Result.success(assets)` preserving generation order.

**Failure contract:**

- If delegate `Result` is failure, return `Result.failure` with the **same** `Throwable` instance (no cache, no `addEphemeralMedia`).
- If `settings` runtime type does not match `capability`, return `Result.failure(IllegalArgumentException("Settings type does not match capability ${request.capability}"))`.

### 3.5 `MediaGenerationRequestMapper`

```kotlin
object MediaGenerationRequestMapper {
    fun fromToolCall(request: ToolCallRequest): MediaGenerationRequest
    fun fromStudio(
        prompt: String,
        capability: MediaCapability,
        settings: GenerationSettings,
    ): MediaGenerationRequest
}
```

**`fromToolCall` contract** (must mirror current `MediaToolExecutor` mapping):

| Tool name (`ToolDefinition`) | Params type | Settings mapping |
|------------------------------|-------------|------------------|
| `GENERATE_IMAGE` | `GenerateImageToolParams` | `aspectRatio`: match `AspectRatio.ratio` to `params.aspect_ratio`, else `AspectRatio.ONE_ONE`; `quality`: match `GenerationQuality.name` case-insensitive to `params.quality`, else `GenerationQuality.SPEED`; `generationCount`: `params.count.coerceIn(1, 4)` |
| `GENERATE_VIDEO` | `GenerateVideoToolParams` | Same aspect/quality rules as image; `videoDuration = params.duration`; `videoResolution = params.resolution` |
| `GENERATE_MUSIC` | `GenerateMusicToolParams` | `MusicGenerationSettings(duration = params.duration)` |
| Other | — | `IllegalArgumentException("Unsupported media tool: ${request.toolName}")` |

Always sets `origin = MediaGenerationOrigin.CHAT_TOOL`.

**`fromStudio` contract:**

- Returns `MediaGenerationRequest(prompt, capability, settings, MediaGenerationOrigin.STUDIO_FOREGROUND)` with no field transformation (settings already decoded by `StudioGenerationIntentCodec` or service defaults).

### 3.6 `MediaToolExecutor` (thin adapter)

Constructor becomes:

```kotlin
class MediaToolExecutor @Inject constructor(
    private val generateMediaUseCase: GenerateMediaUseCase,
    private val loggingPort: LoggingPort,
) : ToolExecutorPort
```

**Behavior:**

- `execute` routes `GENERATE_IMAGE`, `GENERATE_VIDEO`, `GENERATE_MUSIC` only.
- `val mediaRequest = MediaGenerationRequestMapper.fromToolCall(request)`.
- `generateMediaUseCase(mediaRequest).fold(onSuccess = { assets -> ToolExecutionResult with buildResultJson(...) }, onFailure = { buildErrorJson(message) })`.
- Preserve existing JSON keys and success message string exactly: `"Media generated successfully and available in Studio."`.
- `buildResultJson` / `buildErrorJson` remain private in `MediaToolExecutor`.
- Must not inject or call `GenerateImageUseCase`, `GenerateVideoUseCase`, `GenerateMusicUseCase`, or `StudioRepositoryPort` directly.

### 3.7 `MultimediaGenerationService` (thin shell)

**Removes injections:** `ImageGenerationPort`, `VideoGenerationPort`, `generateVideoUseCase`, `generateMusicUseCase`, `mediaProviderRepository`.

**Adds injection:** `GenerateMediaUseCase`.

**`ACTION_START` contract:**

1. `startForeground` + notification (unchanged).
2. If `EXTRA_PROMPT` missing: log error, `stopSelf()`, return `START_NOT_STICKY` — **do not** invoke `GenerateMediaUseCase`.
3. Decode `mediaType` from `EXTRA_MEDIA_TYPE` via `MediaCapability.valueOf(...)`, default `MediaCapability.IMAGE.name` when extra absent (preserve current behavior).
4. `settings = StudioGenerationIntentCodec.readSettings(intent) ?: capability defaults` (same `when` defaults as today).
5. `val mediaRequest = MediaGenerationRequestMapper.fromStudio(prompt, mediaType, settings)`.
6. `generateMediaUseCase(mediaRequest)` inside existing `serviceScope`.
7. On success: no extra persistence beyond use case (already registered).
8. On failure: `studioRepository.reportGenerationError(error.message ?: "Generation failed")`.
9. On unexpected catch: `reportGenerationError(e.message ?: "Unexpected error during generation")`.
10. `finally`: `stopForeground(STOP_FOREGROUND_REMOVE)` + `stopSelf()`.

**`ACTION_STOP`:** unchanged.

**Forbidden:** `startService` / `startForegroundService` for any other generation service from this class.

---

## 3.8 Dead Code Cleanup Inventory

After Tasks 4–5 land, the following **must not remain** in production sources. Implementation must delete — not comment out — superseded code.

### 3.8.1 `MediaToolExecutor` removals

| Artifact | Action |
|----------|--------|
| Constructor dependencies `GenerateImageUseCase`, `GenerateVideoUseCase`, `GenerateMusicUseCase`, `StudioRepositoryPort` | **Delete** from constructor and fields |
| Private methods `executeImage`, `executeVideo`, `executeMusic` | **Delete** entirely |
| Inline `ImageGenerationSettings` / `VideoGenerationSettings` / `MusicGenerationSettings` construction from tool params | **Delete** (replaced by `MediaGenerationRequestMapper.fromToolCall`) |
| Inline `studioRepository.cacheEphemeralMedia` / `addEphemeralMedia` loops | **Delete** (replaced by `GenerateMediaUseCase`) |
| Inline `StudioMediaAsset` construction in executor | **Delete** |
| Imports for `AspectRatio`, `GenerationQuality`, `*GenerationSettings`, `Generate*UseCase`, `StudioRepositoryPort` when no longer referenced | **Delete** |

**Must remain:** `execute` routing, `buildResultJson`, `buildErrorJson`, `LoggingPort` debug lines, `GenerateMediaUseCase` + `MediaGenerationRequestMapper` usage.

### 3.8.2 `MultimediaGenerationService` removals

| Artifact | Action |
|----------|--------|
| `@Inject lateinit var imageGenerationPort: ImageGenerationPort` | **Delete** |
| `@Inject lateinit var videoGenerationPort: VideoGenerationPort` | **Delete** |
| `@Inject lateinit var generateVideoUseCase: GenerateVideoUseCase` | **Delete** |
| `@Inject lateinit var generateMusicUseCase: GenerateMusicUseCase` | **Delete** |
| `@Inject lateinit var mediaProviderRepository: MediaProviderRepositoryPort` | **Delete** |
| `mediaProviderRepository.getMediaProviders().first()` provider selection | **Delete** |
| Per-capability `when` branches calling ports or individual `Generate*UseCase` directly | **Delete** |
| Inline `studioRepository.cacheEphemeralMedia` / `addEphemeralMedia` / `StudioMediaAsset` construction loop | **Delete** |
| `kotlinx.coroutines.flow.first` import when only used for provider lookup | **Delete** |
| Unused imports for removed types | **Delete** |

**Must remain:** `GenerateMediaUseCase`, `StudioRepositoryPort` (error reporting only), `LoggingPort`, notification helpers, `StudioGenerationIntentCodec.readSettings`, `MediaGenerationRequestMapper.fromStudio`, `serviceScope` lifecycle.

### 3.8.3 Repository-wide forbidden patterns (post-refactor)

These string patterns must have **zero** production matches under the listed paths:

| Pattern | Path scope |
|---------|------------|
| `ImageGenerationPort` | `feature/studio-generation-service/src/main/` |
| `VideoGenerationPort` | `feature/studio-generation-service/src/main/` |
| `MediaProviderRepositoryPort` | `feature/studio-generation-service/src/main/` |
| `getMediaProviders().first` | `feature/studio-generation-service/src/main/` |
| `cacheEphemeralMedia` | `core/data/.../MediaToolExecutor.kt` |
| `addEphemeralMedia` | `core/data/.../MediaToolExecutor.kt` |
| `executeImage(` / `executeVideo(` / `executeMusic(` | `core/data/.../MediaToolExecutor.kt` |

### 3.8.4 Explicit non-removals (do not delete)

| Artifact | Reason |
|----------|--------|
| `GenerateImageUseCase`, `GenerateVideoUseCase`, `GenerateMusicUseCase` | Still invoked by `GenerateMediaUseCase` |
| `*GenerationPort` interfaces and adapters | Still used by individual use cases |
| `StudioGenerationIntentCodec` | Android IPC boundary for Studio settings |
| `AndroidStudioGenerationDispatcher` | Starts foreground service only |
| `CompositeToolExecutor` media routing lines | Unchanged delegation to `MediaToolExecutor` |

---

## 4. Data Model or Schema Changes

None.

- Ephemeral files remain in existing Studio ephemeral cache via `StudioRepositoryPort.cacheEphemeralMedia`.
- No new Room entities, DAO methods, or DataStore keys.
- `StudioMediaAsset` port type unchanged; mapping occurs inside `GenerateMediaUseCase`.

---

## 5. Constitution Audit

| Rule | Compliance |
|------|------------|
| Clean Architecture | All new orchestration in `:core:domain`; Android service stays in `feature/studio-generation-service` |
| Domain purity | No Android types in new domain models or use case public API |
| Data layer | `MediaToolExecutor` remains in `:core:data` as tool adapter only |
| Schema lock | No database changes |
| Service boundaries | Chat inline; Studio foreground service for OS lifecycle only |
| Test mandate | New domain tests + update data/service tests per `UNIT_TESTING.md` |

---

## 6. Ordered Implementation Tasks

### Task 1: Domain models for unified media generation

**Files:** Create `MediaGenerationOrigin.kt`, `MediaGenerationRequest.kt`, `GeneratedMediaAsset.kt`.

**Outcome:** Types compile in `:core:domain` with no Android imports.

**Verifies scenarios:** S-MAP-06 (types used by mapper), S-GM-04 (result shape).

### Task 2: `MediaGenerationRequestMapper`

**Files:** Create `MediaGenerationRequestMapper.kt`, `MediaGenerationRequestMapperTest.kt`.

**Outcome:** Tool and Studio mapping match Section 3.5; unsupported tool throws `IllegalArgumentException` with message containing tool name.

**Verifies scenarios:** S-MAP-01, S-MAP-02, S-MAP-03, S-MAP-04, S-MAP-05, S-MAP-06, E-MAP-01, E-MAP-02.

### Task 3: `GenerateMediaUseCase`

**Files:** Create `GenerateMediaUseCase.kt`, `GenerateMediaUseCaseTest.kt`.

**Outcome:** Dispatches to correct `Generate*` use case; registers assets via `StudioRepositoryPort`; applies origin id/timestamp policies; failure does not cache.

**Verifies scenarios:** S-GM-01, S-GM-02, S-GM-03, S-GM-04, S-GM-05, S-GM-06, S-GM-07, S-GM-08, E-GM-01, M-GM-01, M-GM-02.

### Task 4: Thin `MediaToolExecutor`

**Files:** Modify `MediaToolExecutor.kt`, `MediaToolExecutorTest.kt`.

**Outcome:** Executor depends only on `GenerateMediaUseCase` + `LoggingPort`; JSON shape unchanged for success and error paths.

**Verifies scenarios:** S-CHAT-01, S-CHAT-02, S-CHAT-03, S-CHAT-04, E-CHAT-01.

### Task 5: Thin `MultimediaGenerationService`

**Files:** Modify `MultimediaGenerationService.kt`, `MultimediaGenerationServiceTest.kt`.

**Outcome:** Service uses `GenerateMediaUseCase` for all capabilities; removed direct port/provider usage; missing prompt short-circuits without use case.

**Verifies scenarios:** S-STU-01, S-STU-02, S-STU-03, S-STU-04, S-STU-05, S-STU-06, E-STU-01, M-STU-01.

### Task 6: Dead code removal and guard tests

**Files:** Modify `MediaToolExecutor.kt`, `MultimediaGenerationService.kt`; create `MediaGenerationDeadCodeGuardTest.kt` (data module), `MultimediaGenerationDeadCodeGuardTest.kt` (studio-generation-service module).

**Outcome:**

1. All artifacts listed in Section 3.8.1 and 3.8.2 are removed from production sources.
2. Guard tests read production source files from classpath/resources and assert forbidden symbols and legacy private method names are absent (see test spec `CLEAN-*` scenarios).
3. No commented-out legacy blocks left behind.
4. `./gradlew :core:data:compileDebugKotlin :feature:studio-generation-service:compileDebugKotlin` succeeds with reduced dependency graphs on refactored classes.

**Verifies scenarios:** CLEAN-01, CLEAN-02, CLEAN-03, CLEAN-04, CLEAN-05, CLEAN-06, CLEAN-07, M-CLEAN-01.

### Task 7: Regression gate

**Commands:**

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

./gradlew :core:domain:assembleDebug :core:data:assembleDebug ktlintCheck detekt --no-daemon --stacktrace

# Dead-code grep gate (must exit 0 — zero matches)
! rg "ImageGenerationPort|VideoGenerationPort|MediaProviderRepositoryPort|getMediaProviders\\(\\)\\.first" \
  feature/studio-generation-service/src/main/kotlin
! rg "cacheEphemeralMedia|addEphemeralMedia|executeImage\\(|executeVideo\\(|executeMusic\\(" \
  core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/MediaToolExecutor.kt
```

**Outcome:** All tests green; detekt `maxIssues: 0`; grep gate passes.

**Verifies scenarios:** All scenario IDs (full suite).

---

## 7. Drift and Escalation Rules (Later Phases)

| Situation | Action |
|-----------|--------|
| New media capability (e.g. speech) | Extend `MediaCapability`, `Generate*UseCase`, `GenerateMediaUseCase` dispatch, mapper, and tool definition together in one PR; do not add a third orchestration path |
| New Studio entry point | Must call `GenerateMediaUseCase` via mapper or shared domain helper; never call `*GenerationPort` from feature modules |
| New chat tool for media | Add mapping in `MediaGenerationRequestMapper.fromToolCall` only; executor stays thin |
| Tool JSON schema change | Requires explicit product approval and synchronized updates to `MediaToolExecutor.buildResultJson` tests |
| Need to change provider selection | Edit `GenerateImageUseCase` / `GenerateVideoUseCase` / `GenerateMusicUseCase` only, not `MultimediaGenerationService` |
| Foreground work exceeds service scope | Use WorkManager per `ARCHITECTURE_RULES.md`; do not grow service logic — extract domain use case instead |
| Dead code reintroduced during follow-up PRs | Run Section 3.8 grep gate and `CLEAN-*` guard tests in CI; reject PRs that restore port/provider orchestration in Studio service or cache loops in `MediaToolExecutor` |
| Contract waiver needed (domain Android import, schema change) | Stop and request **Contract Waiver** from user before coding |

**Review checklist before merge:**

1. `rg "ImageGenerationPort" feature/studio-generation-service/src/main` returns zero matches.
2. `rg "VideoGenerationPort" feature/studio-generation-service/src/main` returns zero matches.
3. `rg "MediaProviderRepositoryPort" feature/studio-generation-service/src/main` returns zero matches.
4. `rg "getMediaProviders\\(\\)\\.first" feature/studio-generation-service/src/main` returns zero matches.
5. `rg "cacheEphemeralMedia|addEphemeralMedia" core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/MediaToolExecutor.kt` returns zero matches.
6. `rg "executeImage\\(|executeVideo\\(|executeMusic\\(" core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/MediaToolExecutor.kt` returns zero matches.
7. `MediaToolExecutor` has zero imports of `GenerateImageUseCase`, `GenerateVideoUseCase`, `GenerateMusicUseCase`, `StudioRepositoryPort`.
8. `MultimediaGenerationService` does not call `startForegroundService` except as today via dispatcher (service itself does not spawn services).
9. `MediaGenerationDeadCodeGuardTest` and `MultimediaGenerationDeadCodeGuardTest` pass.

---

## 8. Scenario ID → Task Traceability Matrix

| Scenario ID | Task |
|-------------|------|
| S-MAP-01 … S-MAP-06 | Task 2 |
| E-MAP-01, E-MAP-02 | Task 2 |
| S-GM-01 … S-GM-08 | Task 3 |
| E-GM-01 | Task 3 |
| M-GM-01, M-GM-02 | Task 3 |
| S-CHAT-01 … S-CHAT-04 | Task 4 |
| E-CHAT-01 | Task 4 |
| S-STU-01 … S-STU-06 | Task 5 |
| E-STU-01, M-STU-01 | Task 5 |
| CLEAN-01 … CLEAN-07, M-CLEAN-01 | Task 6 |
| All scenario IDs | Task 7 |

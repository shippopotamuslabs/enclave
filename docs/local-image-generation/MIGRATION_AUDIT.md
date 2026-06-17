# Local image generation migration audit

## Removed (stable-diffusion.cpp / Vulkan path)

| Location | Action |
|----------|--------|
| `third_party/stable-diffusion.cpp/` | No longer referenced by Gradle/CMake (vendored tree may remain on disk) |
| `stable-diffusion-android/` module | Renamed → `local-image-gen-android/`; all GGML/Vulkan JNI removed |
| `sd_bridge_core.cpp`, `stable_diffusion_jni.cpp`, `FindVulkan.cmake` | Deleted |
| `feature/inference/.../JniStableDiffusionBridge.kt` | Replaced by engine + backend adapters |
| `GpuCapabilityPortImpl` Vulkan/RAM gate | Replaced by Snapdragon + backend probe policy |
| `ImageGenerationBackend.STABLE_DIFFUSION_CPP` | Replaced by `QNN_NPU` / `CPU_MNN` / `GPU_EXPERIMENTAL` / `NONE` |
| `app/.../StableDiffusionJniDiagnosticTest.kt` | Removed (Vulkan-specific) |

## Kept (Enclave architecture)

- `ImageGenerationPort` / `ImageGenerationPortImpl` — cloud + local dispatch
- `ResolveAssignedImageGenerationTargetUseCase` — assignment resolution
- `LocalStableDiffusionImageGenerator` → refactored to `LocalImageGenerationRunner` (same lock/progress contract)
- Model catalog / packs / `InitializeModelsUseCase` download gating
- Studio `MultimodalViewModel` + existing image settings UI

## Added

- `:local-image-gen-android` — JNI seam for QNN + MNN (stub until SDK/libs packaged)
- Domain: `LocalImageGenerationCapabilityPort`, model registry, backend selection, typed errors
- `:feature:inference`: `LocalImageGenerationEngine`, QNN/MNN/Noop/Fake backends, Snapdragon classifier

## Manual steps (operators)

1. Place Qualcomm QNN runtime `.so` files under `local-image-gen-android/src/main/jniLibs/arm64-v8a/` (not committed).
2. Place SD 1.5 NPU / CPU model packages under app-private `files/models/` per catalog SHA entries.
3. Set `local.properties` keys documented in `docs/local-image-generation/README.md` when SDK paths are needed.
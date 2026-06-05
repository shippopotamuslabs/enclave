# On-device image generation (StableDream-style)

## Why stable-diffusion.cpp / Vulkan was removed

GGML Vulkan on Adreno (including Razr Fold 2026-class devices) hit `vk::DeviceLostError` during UNet offload. CPU-only GGUF inference was too slow for Studio. Production code no longer links `third_party/stable-diffusion.cpp`.

## Supported backends

| Backend | Enum | SD 1.5 | SDXL | Status |
|---------|------|--------|------|--------|
| Qualcomm QNN (Hexagon NPU) | `QNN_NPU` | Yes | Gen 3+ only | **Scaffolded** — JNI seam + probe; needs SDK `.so` + model package |
| MNN CPU | `CPU_MNN` | Fallback | No | **Scaffolded** — needs `libMNN.so` + CPU model package |
| Vulkan / GGML | — | No | No | Removed |

Backend selection lives in `SelectLocalImageGenerationBackendUseCase` (domain). Runtime wiring is `LocalImageGenerationEngine` in `:feature:inference`.

## Module layout (Enclave architecture)

- `:local-image-gen-android` — JNI (`local_image_gen_jni`, `qnn_bridge.cpp`, `mnn_bridge.cpp`)
- `:core:domain` — `LocalImageGenerationModelRegistry`, capabilities, errors, selection use case
- `:feature:inference` — `LocalImageGenerationCapabilityPortImpl`, `GpuCapabilityPortImpl`, `LocalStableDiffusionImageGenerator`
- Existing `ImageGenerationPortImpl` + Studio flow unchanged at the port boundary

## Install model files

1. Download or import the SD 1.5 NPU package referenced in `model_config.json` (catalog SHA `c2f6e92f…`).
2. Files land under app-private storage, typically:
   `Android/data/com.browntowndev.enclave/files/models/`
3. Assign **Image Generation** → local SD 1.5 in Model Configuration.

Optional CPU package uses registry SHA placeholder `000…001` until a real catalog entry is published.

## Add QNN SDK / runtime

1. Obtain Qualcomm QNN SDK (QAIRT) under your license.
2. Set `qnn.sdk.dir=/path/to/qairt/<version>` in `local.properties`.
3. Run `./scripts/sync-qnn-jniLibs.sh` (Gradle `syncQnnJniLibs` also runs on preBuild when the property is set).
4. Build and copy the diffusion backend executable:
   `./scripts/build-stable-diffusion-core.sh` → `local-image-gen-android/src/main/jniLibs/arm64-v8a/libstable_diffusion_core.so`

`qnn_probe_runtime()` returns **2** when QNN libs and `libstable_diffusion_core.so` are packaged. Generation spawns that executable and calls its `/generate` HTTP API (Local Dream–compatible).

## Add MNN CPU fallback

1. Add `libMNN.so` and `libstable_diffusion_core.so` to `jniLibs/arm64-v8a/`.
2. Place SD 1.5 CPU models under `files/models/sd15-cpu/` (`clip.mnn`, `unet.mnn`, `vae_decoder.mnn`, `tokenizer.json`).
3. `mnn_bridge.cpp` launches the same backend with `--cpu` when MNN artifacts are present.

## SD 1.5 QNN model pack (catalog SHA unchanged)

The catalog still verifies `stable-diffusion-v1-5-Q4_0.gguf` (SHA `c2f6e92f…`). Real NPU inference uses QNN `.bin` files:

```bash
./scripts/install-sd15-qnn-pack.sh /path/to/files/models
# → creates .../models/sd15-qnn/ (clip.bin, unet.bin, …)
```

Mirror that folder on device under `Android/data/com.browntowndev.enclave/files/models/sd15-qnn/`.

## Test on Snapdragon devices

- Motorola Razr Fold 2026 (Adreno 829): expect Snapdragon tier **GEN3_PLUS**, recommended backend **QNN_NPU** when libraries are packaged.
- Without QNN libs: assignment may succeed; generation returns a typed **runtime not packaged** error (no crash).
- Unit tests: `SelectLocalImageGenerationBackendUseCaseTest`, `FakePlaceholderImageGenerationBackendTest`.

## SD 1.5 vs SDXL

- **SD 1.5**: QNN first, MNN CPU fallback, clear error if neither is available.
- **SDXL**: QNN only on Snapdragon 8 Gen 3+ class devices; no CPU fallback by default.

## Licensing (pre–Play Store)

| Component | License | Notes |
|-----------|---------|--------|
| Enclave app code | Project license | Kotlin, JNI bridge, domain/inference wiring |
| `libstable_diffusion_core.so` | **CC BY-NC 4.0** (Local Dream) | Non-commercial unless you obtain separate rights or replace the backend |
| QNN `.so` libraries | **Qualcomm proprietary (QAIRT EULA)** | Dev builds may use APK-extracted libs; **release builds must sync from QAIRT** after accepting the EULA |
| Bundled model weights | Per model card | Disclosed in Settings → **Licenses & Attributions** (`Model:` entries) |
| `stb_image_write` | Public domain | PNG output in local-image-gen-android |

Before Play Store release:

1. Accept the Qualcomm QAIRT EULA at https://softwarecenter.qualcomm.com/
2. Set `qnn.sdk.dir` and run `./scripts/sync-qnn-jniLibs.sh` (stop relying on Local Dream APK extraction for QNN)
3. Resolve Local Dream **CC BY-NC** vs commercial subscription plans (replace backend or obtain rights)
4. Legal review of bundled HF model terms (SD 1.5 Open RAIL-M, SDXL Open RAIL++-M, LLM checkpoints, etc.)
# On-device image generation (StableDream-style)

## Why stable-diffusion.cpp / Vulkan was removed

GGML Vulkan on Adreno (including Razr Fold 2026-class devices) hit `vk::DeviceLostError` during UNet offload. CPU-only GGUF inference was too slow for Studio. Production code no longer links `third_party/stable-diffusion.cpp`.

## Supported backends

| Backend | Enum | SD 1.5 | SDXL | Status |
|---------|------|--------|------|--------|
| Qualcomm QNN (Hexagon NPU) | `QNN_NPU` | Yes | Gen 3+ only | **Enclave diffusion engine** — in-process JNI (`enclave_diffusion_engine`) |
| MNN CPU | `CPU_MNN` | Fallback | No | Routes through same engine (MNN CLIP + QNN graphs) |
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
4. Fetch native engine deps once: `./scripts/setup-enclave-diffusion-deps.sh` (MNN, tokenizers-cpp, xtensor).

`qnn_probe_runtime()` returns **2** when QNN runtime libraries are synced. Generation calls the in-process Enclave engine (`enclave_diffusion_generate`) — no fork, no HTTP, no Local Dream binary.

## Add MNN CPU fallback

1. Run `./scripts/setup-enclave-diffusion-deps.sh` so MNN links statically into `liblocal_image_gen_jni.so`.
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

### One-time host setup

```bash
./scripts/setup-enclave-diffusion-deps.sh   # MNN, tokenizers-cpp, xtensor, stb
# local.properties:
#   qnn.sdk.dir=/path/to/qairt/<version>
./scripts/sync-qnn-jniLibs.sh
./gradlew :app:assembleDebug
```

### Model pack on device

SD 1.5 (xororz / Local Dream–compatible layout):

```bash
./scripts/install-sd15-qnn-pack.sh /path/to/host/models
adb push /path/to/host/models/sd15-qnn /sdcard/Android/data/com.browntowndev.enclave/files/models/sd15-qnn
```

Required files under the model directory:

| SD 1.5 | SDXL |
|--------|------|
| `clip_v2.mnn`, `token_emb.bin`, `pos_emb.bin` | `clip.mnn`, `clip_2.mnn`, `token_emb.bin`, `token_emb_2.bin`, `pos_emb.bin`, `pos_emb_2.bin` |
| `unet.bin`, `vae_decoder.bin`, `tokenizer.json` | same |
| optional img2img: `vae_encoder.bin` | optional img2img: `vae_encoder.bin` |

Assign **Image Generation** to the local SD 1.5 / SDXL QNN model in Model Configuration.

### Smoke test (txt2img)

1. Install debug APK; open Studio → generate image with a short prompt.
2. Default backend **QNN_NPU** when `qnn_probe_runtime()` returns **2** (Htp + System libs in app files dir).
3. Output PNG is written to the path Enclave passes (app cache); check logcat tag `enclave-qnn` / `local-image-gen-jni` on failure.

### img2img

Include `vae_encoder.bin` in the pack. Pass init image path + `denoise_strength` (default 0.75). Input is resized to request width×height and normalized to `[-1, 1]` CHW before VAE encode.

### Memory policy

`LOW_RAM` unloads CLIP/tokenizer after encode, VAE encoder after img2img encode, UNet after denoise loop, VAE decoder after decode — for 8–12 GB devices.

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
| Enclave `enclave_diffusion_engine` | Project license | In-process txt2img/img2img engine (no Local Dream binary) |
| QNN `.so` libraries | **Qualcomm proprietary (QAIRT EULA)** | Dev builds may use APK-extracted libs; **release builds must sync from QAIRT** after accepting the EULA |
| Bundled model weights | Per model card | Disclosed in Settings → **Licenses & Attributions** (`Model:` entries) |
| `stb_image_write` | Public domain | PNG output in local-image-gen-android |

Before Play Store release:

1. Accept the Qualcomm QAIRT EULA at https://softwarecenter.qualcomm.com/
2. Set `qnn.sdk.dir` and run `./scripts/sync-qnn-jniLibs.sh` (ship QAIRT libs from your SDK install, not APK extraction)
3. Legal review of bundled HF model terms (SD 1.5 Open RAIL-M, SDXL Open RAIL++-M, LLM checkpoints, etc.)
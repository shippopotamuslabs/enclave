# Test Spec: sd15-t2i

## Red → Green coverage map

| ID | Module | Test class | Asserts |
|----|--------|------------|---------|
| SD15-001 | `core:data` | `StableDiffusionSd15T2iRedTest` | Catalog parses `sd15-t2i` single-member pack; SD15 SHA/size pinned |
| SD15-002 | `core:domain` | `StableDiffusionSd15T2iRedTest` | Policy accepts SD15 SHA; resolve uses bundled paths; capabilities 512/1024 |
| SD15-003 | `core:domain` | `ModelPackTestFixtures` | `PACK_ID=sd15-t2i`, `TOTAL_MEMBERS=1` |
| SD15-004 | `feature:inference` | `StableDiffusionSd15T2iRedTest` | Request steps=20 cfg=7.5 fa=false clip=true; paths to SD15 gguf |
| SD15-005 | `feature:studio` | `StableDiffusionSd15T2iRedTest` | Capabilities list 512² and 1024²; invalid resolution clamps |
| SD15-006 | `feature:settings` | `StableDiffusionSd15T2iRedTest` | Settings surfaces SD15 display name |
| SD15-007 | `stable-diffusion-android` | `sd_bridge_core_test` | `clip_l_path` set, `llm_path` null; max_vram 1.5; stream_layers false |
| SD15-008 | `app` | `StableDiffusionJniDiagnosticTest` | Diagnostic paths point at SD15 gguf filename |

## Host C++ tests

- `BuildCtxParams` with `useClipLTextEncoder=true`: expects `clip_l_path`, null `llm_path`, `max_vram=1.5f`, `stream_layers=false`.
- `BuildImgGenParams` unchanged except cfg/steps literals in new Kotlin-driven integration tests.

## Regression guards

- `InitializeModelsUseCaseTest` MPK-019: API image slot must not enqueue SD15 pack SHA.
- `SetDefaultModelUseCaseTest`: incomplete pack blocks assign with updated message.
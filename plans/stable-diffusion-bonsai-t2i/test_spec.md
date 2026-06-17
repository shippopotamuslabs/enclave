# stable-diffusion-bonsai-t2i test specification

Feature: Stable-diffusion Bonsai local image generation

Scenario: SDT2I-001 model config rollout is blocked without pinned Hugging Face companion metadata
  Given the Bonsai diffusion asset URL is `https://config.enclave-app.com/bonsai-ternary-4B-q5_K.gguf`
  And the Bonsai diffusion asset SHA-256 is `c415f811e4b39c1287ce529afbe99ff1ade0bb9ac0e5767019d9490a606b1983`
  And the Bonsai diffusion asset byte size is `2721233504`
  And the Flux.2 VAE utility asset uses `source` `HF`
  And the Flux.2 VAE utility asset uses `huggingFaceModelName` `Comfy-Org/vae-text-encorder-for-flux-klein-4b`
  And the Flux.2 VAE utility asset uses `huggingFacePath` `split_files/vae`
  And the Flux.2 VAE utility asset uses `fileName` `flux2-vae.safetensors`
  And the Flux.2 VAE utility asset SHA-256 is `""`
  And the Flux.2 VAE utility asset byte size is `0`
  And the Qwen3 text encoder utility asset uses `source` `HF`
  And the Qwen3 text encoder utility asset uses `huggingFaceModelName` `unsloth/Qwen3-4B-GGUF`
  And the Qwen3 text encoder utility asset uses `fileName` `Qwen3-4B-Q4_K_M.gguf`
  When the release engineer prepares `model_config.json`
  Then the rollout must stop with the note `Missing required pinned Hugging Face file metadata for Bonsai companion assets.`

Scenario: SDT2I-002 Flux.2 VAE assets are accepted as Hugging Face safetensors utility files
  Given the remote model config contains a utility asset with `modelFileFormat` `SAFETENSORS`
  And the utility asset uses `source` `HF`
  And the utility type is `flux2_vae`
  And the utility asset uses `huggingFaceModelName` `Comfy-Org/vae-text-encorder-for-flux-klein-4b`
  And the utility asset uses `huggingFacePath` `split_files/vae`
  And the utility asset uses `fileName` `flux2-vae.safetensors`
  When model config parsing runs
  Then the utility asset is accepted without a local-model schema change
  And the resolved download URL is `https://huggingface.co/Comfy-Org/vae-text-encorder-for-flux-klein-4b/resolve/main/split_files/vae/flux2-vae.safetensors`

Scenario: SDT2I-003 Qwen3 text encoder utility resolution is type-safe
  Given the utility registry contains a `qwen3_text_encoder` asset
  And the utility asset uses `source` `HF`
  And the utility asset uses `huggingFaceModelName` `unsloth/Qwen3-4B-GGUF`
  And the utility asset uses `fileName` `Qwen3-4B-Q4_K_M.gguf`
  And the utility asset uses `modelFileFormat` `GGUF`
  When the local image-generation target is resolved
  Then the text encoder path is loaded from the utility resolver
  And the local model entity remains unchanged

Scenario: SDT2I-004 local image-generation assignment is allowed
  Given a downloaded local model configuration assigned to `IMAGE_GENERATION`
  When `SetDefaultModelUseCase` is invoked with `localConfigId = "bonsai-local-config"`
  Then the default assignment stores `localConfigId = "bonsai-local-config"`
  And the call does not require a `mediaProviderId`

Scenario: SDT2I-005 image-generation assignment still supports remote providers
  Given a remote media provider assignment for `IMAGE_GENERATION`
  When the assignment is resolved
  Then the result remains a remote image-generation target

Scenario: SDT2I-006 missing image-generation assignment surfaces the exact error
  Given no default assignment exists for `IMAGE_GENERATION`
  When image generation is requested
  Then the failure message is `No image generation model assigned.`

Scenario: SDT2I-007 incomplete local diffusion download blocks generation
  Given the default `IMAGE_GENERATION` assignment points to the Bonsai local model
  And the diffusion GGUF file is unavailable on disk
  When image generation is requested
  Then the failure message is `Image generation model is not fully downloaded.`

Scenario: SDT2I-008 missing Flux.2 VAE blocks generation with an exact message
  Given the Bonsai diffusion GGUF file is present
  And the Flux.2 VAE utility file is missing
  When image generation is requested
  Then the failure message is `Flux.2 VAE is missing.`

Scenario: SDT2I-009 missing Qwen3 text encoder blocks generation with an exact message
  Given the Bonsai diffusion GGUF file is present
  And the Flux.2 VAE utility file is present
  And the Qwen3 text encoder utility file is missing
  When image generation is requested
  Then the failure message is `Qwen3 text encoder is missing.`

Scenario: SDT2I-010 local generation request is translated to the stable-diffusion bridge contract
  Given a resolved local image-generation target with diffusion, VAE, and text encoder paths
  And image settings with aspect ratio `1:1` and resolution `512x512`
  When the local adapter starts generation for prompt `bonsai tree in a glass terrarium`
  Then the bridge request uses `cfgScale = 1.0`
  And the bridge request uses `steps = 4`
  And the bridge request uses `samplingMethod = "euler"`
  And the bridge request uses `diffusionFa = true`
  And the bridge request uses `offloadToCpu = true`

Scenario: SDT2I-011 local generation runs off the main thread and returns an output reference
  Given the stable-diffusion bridge succeeds
  When local image generation completes
  Then the result contains a non-empty output file reference
  And the Studio caller receives a success state without blocking the main thread

Scenario: SDT2I-012 chat image tool remains available for local Bonsai assignment
  Given the default `IMAGE_GENERATION` assignment resolves to a local Bonsai target
  When chat tool definitions are prepared
  Then the `generate_image` tool is included

Scenario: SDT2I-013 Studio capabilities drive image settings for the local backend
  Given local backend capabilities with supported aspect ratios `["1:1", "16:9"]`
  And local backend capabilities with supported resolutions `["512x512", "768x768"]`
  When the Studio screen observes those capabilities
  Then the image settings bottom sheet shows `1:1` and `16:9`
  And the image settings bottom sheet shows `512x512` and `768x768`

Scenario: SDT2I-014 Studio coerces unsupported local image settings to the first allowed values
  Given current Studio image settings use aspect ratio `9:16`
  And current Studio image settings use resolution `1024x1024`
  And local backend capabilities only allow aspect ratio `1:1`
  And local backend capabilities only allow resolution `512x512`
  When capabilities are applied
  Then the updated aspect ratio is `1:1`
  And the updated resolution is `512x512`

Scenario: SDT2I-015 settings selection surfaces Bonsai as a local image-generation option
  Given local model assets include the Bonsai configuration for `IMAGE_GENERATION`
  When the image-generation assignment sheet opens
  Then the local-assets tab includes the Bonsai display name

Scenario: SDT2I-016 service-layer boundaries remain intact for Studio generation
  Given the Studio generation service source is inspected
  When dead-code guard tests run
  Then the source contains zero direct references to `ImageGenerationPort`
  And the source contains zero direct references to `StableDiffusionBridge`

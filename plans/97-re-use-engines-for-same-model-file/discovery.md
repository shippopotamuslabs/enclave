# Fast/Thinking Shared-Config Engine Reuse Investigation (Repo-Grounded)

Date: 2026-04-02
Scope: Investigation + implementation planning only (no runtime code changes in this doc update)

## 1) Current architecture summary

- Model-role assignment is persisted in `default_models`, keyed by `ModelType` (`FAST`, `THINKING`, etc.), with each row pointing to exactly one config source (`local_config_id` XOR `api_config_id`).
- Local model/runtime tuning is represented separately from assignment:
  - `local_models` stores downloaded model identity (including `sha256`, format, filenames).
  - `local_model_configurations` stores per-config tuning values.
- Runtime resolution is split:
  - `GenerateChatResponseUseCase` maps mode -> role (`FAST` or `THINKING`) and asks `InferenceFactoryImpl` for a service.
  - `InferenceFactoryImpl` currently caches by `ModelType` + asset identity.

## 2) Persistence/schema findings

### What exists now

- `default_models` is keyed by `model_type` and permits repeated use of the same `local_config_id`/`api_config_id` across different model types.
- `DefaultModelEntity` enforces exactly one source pointer per row (local xor api).
- `LocalModelConfigurationEntity` already stores tuning + `thinking_enabled`.
- `ApiModelConfigurationEntity` stores sampling/system fields but no dedicated `thinking_enabled` column.

### Answers

- Can Fast and Thinking already point to same model config ID?
  - **Yes** (schema already supports this).
- Smallest schema change needed for shared FAST/THINKING assignment?
  - **None**.
- Is there already dedicated per-role runtime behavior storage separate from shared config?
  - Not as a separate structure today; role assignment and config tuning are separate concerns.

## 3) Resolution/orchestration findings

- `GenerateChatResponseUseCase` requests separate services for FAST and THINKING based on role.
- `InferenceFactoryImpl` currently caches services per `ModelType`, which is where reuse decisions are made.
- `ModelRegistryImpl` resolves the assigned asset/config for a role via default assignment lookups.

### Clarified implementation direction

Per product clarification, **reuse should be keyed by model file SHA-256** in the inference factory:

- If FAST and THINKING resolve to assets with the same `sha256`, they should reuse the same loaded engine/service instance.
- If SHA differs, preserve current behavior (separate service lifecycle).

This keeps reuse tied to the binary identity actually loaded in memory.

## 4) Engine mutability findings by backend (and required change)

### Current state (repo evidence)

- **llama.cpp**: generation call already accepts temperature/top_k/top_p/min_p/max_tokens/reasoning budget and penalties at generation time; today those values are populated from loaded config.
- **LiteRT**: sampler values are currently set in `ConversationConfig` when conversation is created.
- **MediaPipe**: sampler values are currently set in `LlmInferenceSessionOptions` when session is created.

### Required plan update

Per clarification, all 3 engine types should accept per-response config parameters. Implementation target:

1. **llama.cpp**
   - Feed per-response parameters directly into generation path each request.
   - Keep model engine loaded when SHA unchanged.

2. **LiteRT**
   - Extend inference path so each response can carry generation settings.
   - Apply per-response settings by reconfiguring/recreating conversation as needed (without unloading engine if SHA unchanged).

3. **MediaPipe**
   - Extend inference path so each response can carry generation settings.
   - Apply per-response settings by session recreation when required (keep underlying model/engine object as stable as API allows when SHA unchanged).

## 5) Fast vs Thinking behavior rules (clarified)

Per clarification, behavior should be deterministic by mode:

- **FAST mode:** reasoning **always OFF**.
- **THINKING mode:** reasoning **always ON**.

This applies even when both modes share the same underlying model file/engine via SHA-based reuse.

## 6) UI findings / direction

- Current role assignment UX already lets users choose defaults per role.
- Per clarification, **no UI changes are required for this optimization**.
- Implementation should be behind-the-scenes in orchestration/inference layers.

## 7) Hypothesis validation (updated)

Original hypothesis: schema likely already supports this and main work is logic/runtime handling.

Verdict: **Validated.**

- Schema already supports shared assignment.
- Main work is:
  - factory cache key change to SHA-256 reuse,
  - per-response parameter plumbing in all 3 engines,
  - mode rule enforcement (`FAST => reasoning off`, `THINKING => reasoning on`).

## 8) Recommended implementation approach

### A. InferenceFactory: SHA-based shared cache

- Replace role-only cache keying with SHA-centric cache identity.
- Maintain mapping from role -> active SHA identity for diagnostics, but lifecycle ownership by SHA.

### B. Per-response inference request options

- Add request-time generation options object passed from use case -> inference service.
- Compute reasoning toggle from mode centrally:
  - FAST false
  - THINKING true

### C. Backend-specific application of per-response options

- llama: direct per-generate args.
- LiteRT: reconfigure conversation per request when sampling differs.
- MediaPipe: recreate session per request when sampling differs.

### D. Backward compatibility

- Users can still assign different configs for FAST/THINKING.
- Shared optimization activates automatically when SHA matches.

## 9) File-by-file likely change list

1. `feature/inference/.../InferenceFactoryImpl.kt`
   - Cache/reuse keyed by SHA-256 identity instead of pure role cache.
2. `core/domain/.../GenerateChatResponseUseCase.kt`
   - Build per-response request options from mode and pass to inference call.
3. `core/domain/.../LlmInferencePort.kt`
   - Add request options payload for per-response generation params.
4. `feature/inference/.../LlamaInferenceServiceImpl.kt`
   - Apply request options for each generation.
5. `feature/inference/.../llama/JniLlamaEngine.kt`
   - Ensure generate path consumes per-request options.
6. `feature/inference/.../LiteRtInferenceServiceImpl.kt`
   - Accept per-response options; trigger conversation reconfiguration as needed.
7. `feature/inference/.../ConversationManagerImpl.kt`
   - Add/update APIs to refresh sampler/system settings without unnecessary engine unload.
8. `feature/inference/.../MediaPipeInferenceServiceImpl.kt`
   - Accept per-response options; recreate session when needed for changed params.

## 10) Risks / edge cases

1. Shared SHA cache requires robust lifecycle ownership to avoid one role closing a shared instance still in use by another role.
2. LiteRT/MediaPipe may require conversation/session recreation for some parameter changes; this still reduces full model reload churn but is not zero-cost.
3. Role-invariant reasoning rule (FAST off / THINKING on) must override config defaults to avoid drift.
4. If two configs share SHA but differ in system prompt or context settings, request-time/session-time reconciliation must be explicit and deterministic.

## 11) Tests to add before implementation

### Factory / orchestration
- FAST and THINKING assigned to same SHA -> same service instance reused.
- Different SHA -> separate service instances.
- Shared instance remains stable across alternating FAST/THINKING turns.

### Mode behavior
- FAST requests always carry reasoning disabled.
- THINKING requests always carry reasoning enabled.

### Backend behavior
- llama: per-request sampling/reasoning changes applied without model reload.
- LiteRT: per-request sampling changes applied via conversation refresh (engine retained when SHA unchanged).
- MediaPipe: per-request sampling changes applied via session refresh.

### Regression
- Existing separate-config behavior remains unchanged.
- No UI behavior changes required for assignment workflow.


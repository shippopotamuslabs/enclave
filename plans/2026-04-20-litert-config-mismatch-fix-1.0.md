# Fix Local Model Configuration Mismatches for LiteRT vs llama.cpp

## Objective

Conditionally hide UI configuration parameters that have no effect for LiteRT models, wire the `thinkingEnabled` toggle to the LiteRT engine so thinking mode actually works when enabled, and ensure the UI two-way data binding for `thinkingEnabled` is correctly connected from database through ViewModel to screen and back.

## Confirmed Mismatches

| Parameter | llama.cpp (GGUF) | LiteRT (.litertlm) | UI Action |
|-----------|-------------------|-------------------|-----------|
| maxTokens | Used | NOT used (no output limit in SamplerConfig) | Hide for LiteRT |
| minP | Used | NOT used (not in SamplerConfig) | Hide for LiteRT |
| repetitionPenalty | Used | NOT used (not in SamplerConfig) | Hide for LiteRT |
| thinkingEnabled | Used | Hardcoded `false` — should flow from config | Wire it up; show for LiteRT |
| contextWindow | Used (sampling) | Used (EngineConfig.maxNumTokens + history compression) | Show for both |
| temperature | Used | Used | Show for both |
| topP | Used | Used | Show for both |
| topK | Used | Used | Show for both |

## Implementation Plan

### Phase 1: Expose model format to the config screen

- [ ] **Task 1.1:** Pass the `format` field from `LocalModelEditorUiState.selectedAsset` into `LocalModelConfigureScreen` so the composable can determine whether the selected model is GGUF or LiteRT. The `LocalModelAssetUi` already has a `format: String` field ("GGUF" or "LiteRT") — route it as a parameter to `LocalModelConfigureScreen`.

**Rationale:** The screen currently receives only `uiState.localModelEditor.configDraft`, but needs to know `selectedAsset.format` to conditionally show/hide fields. Add a `modelFormat: String` parameter derived from `uiState.localModelEditor.selectedAsset?.format ?: "GGUF"` (default to GGUF for null/unknown).

**Files to modify:**
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/LocalModelConfigureScreen.kt` — Add `modelFormat` parameter, use it to conditionally render fields
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/LocalModelConfigureRoute.kt` (same file, the route composable) — Pass `uiState.localModelEditor.selectedAsset?.format ?: "GGUF"`

### Phase 2: Conditionally hide LiteRT-unsupported parameters

- [ ] **Task 2.1:** Hide `Max Tokens` field when `modelFormat != "GGUF"`. LiteRT's `SamplerConfig` and `ConversationConfig` do not support an output token limit — the engine generates until natural stop. The `contextWindow` already controls total context size via `EngineConfig.maxNumTokens`.

- [ ] **Task 2.2:** Hide `Min P` slider when `modelFormat != "GGUF"`. LiteRT's `SamplerConfig` doesn't expose a `minP` parameter.

- [ ] **Task 2.3:** Hide `Repetition Penalty` slider when `modelFormat != "GGUF"`. LiteRT's `SamplerConfig` doesn't expose a `repeatPenalty` parameter.

**Rationale:** Showing parameters that have no effect misleads users into thinking they control behavior they don't. Hiding them avoids confusion while preserving the database columns (so switching between model formats doesn't lose data — the values are still stored and restored when the user edits a GGUF preset).

**Files to modify:**
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/LocalModelConfigureScreen.kt` — Wrap each conditional field in `if (modelFormat == "GGUF")` blocks

### Phase 3: Wire thinkingEnabled to the LiteRT engine

- [ ] **Task 3.1:** In `ConversationManagerImpl.kt` line 189, replace `val resolvedThinkingEnabled = false` with `val resolvedThinkingEnabled = activeConfig.thinkingEnabled` to read the user's toggle from the active configuration.

**Rationale:** The LiteRT engine supports thinking via `extraContext["enable_thinking"] = "true"` on `sendMessageAsync` (already implemented in `ConversationImpl.kt:45-52`). The bug is that `ConversationManagerImpl` hardcodes `thinkingEnabled = false` instead of flowing the config value through. The `ConversationSignature` data class already includes `thinkingEnabled` — it was designed for this but never wired up.

- [ ] **Task 3.2:** Update the `ConversationSignature` creation (line 205-218) to include the `resolvedConversationThinkingEnabled` value derived from `activeConfig.thinkingEnabled`.

**Rationale:** When thinkingEnabled changes, the conversation needs to be recreated. The `ConversationSignature` already has this field so signature comparison will correctly detect the change and recreate the conversation.

- [ ] **Task 3.3:** In `ConversationImpl.kt`, update `sendMessageAsync` to pass `enable_thinking` based on the conversation's thinkingEnabled state rather than only from `options.reasoningBudget`. The current code at line 48 reads `(options?.reasoningBudget ?: 0) > 0`, which is correct for per-request overrides (Crew pipeline), but the base state from the configuration toggle must also be considered.

**Rationale:** Currently, the `reasoningBudget` option is only set by the Crew pipeline for the THINKING model type. For regular chat using the FAST or MAIN model, `reasoningBudget` is 0, so thinking is never enabled even if the user toggled it on in the config. The fix should combine: if the config has `thinkingEnabled = true`, the LiteRT conversation should signal thinking unless the per-request `reasoningBudget` explicitly overrides it. The simplest approach: have `ConversationManagerImpl` pass the `resolvedThinkingEnabled` flag through to `ConversationImpl` (e.g., via `GenerationOptions.reasoningBudget` — an existing convention where `reasoningBudget > 0` means thinking is on). In `getConversation()`, set `options.reasoningBudget` to a positive value when `activeConfig.thinkingEnabled` is true, unless the per-request override already provides one.

- [ ] **Task 3.4:** In `ConversationManagerImpl.getConversation()`, derive the thinking budget for LiteRT conversations: if `resolvedThinkingEnabled` is true and `options?.reasoningBudget` is null or 0, pass a default `reasoningBudget` value (e.g., 2048) through to `ConversationImpl` so that thinking mode activates.

**Rationale:** `ConversationImpl.sendMessageAsync` uses `reasoningBudget > 0` to decide whether to set `enable_thinking = true`. When the user toggles thinking on in config, the base reasoningBudget must be nonzero. The Crew pipeline can still override it by providing its own `reasoningBudget` in options.

**Files to modify:**
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/ConversationManagerImpl.kt` — Replace hardcoded `false`, wire up thinking state
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/ConversationImpl.kt` — No changes needed (already reads reasoningBudget correctly)
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/LiteRtInferenceServiceImpl.kt` — May need to propagate `activeConfig.thinkingEnabled` via GenerationOptions.reasoningBudget

### Phase 4: Verify thinkingEnabled two-way binding

- [ ] **Task 4.1:** Verify the database → ViewModel → UI pipeline for `thinkingEnabled`. Trace the flow:
  1. `LocalModelConfiguration.thinkingEnabled` (domain model) → `LocalModelAssetUiMapper.mapConfig()` (line 107: `thinkingEnabled = config.thinkingEnabled`) → `LocalModelConfigUi.thinkingEnabled` (line 260)
  2. UI toggle changes → `onConfigChange(config.copy(thinkingEnabled = it))` → updates `configDraft`
  3. On save → `configDraft.toDomain()` (line 923-937) → `LocalModelPresetDraft.thinkingEnabled = thinkingEnabled`

**Result:** The two-way binding is ALREADY properly wired. The `LocalModelConfigUi` data class has `thinkingEnabled`, the mapper reads it from the domain model, the UI toggle writes it back via `copy()`, and `toDomain()` includes it. No changes needed here.

- [ ] **Task 4.2:** Verify `LocalModelPresetDraft` includes `thinkingEnabled` and that the save use case persists it correctly.

**Files to check (likely need no changes):**
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModel.kt` — `toDomain()` mapping (line 923-937, already includes `thinkingEnabled`)
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsUiMappers.kt` — `mapConfig()` (line 95-109, already includes `thinkingEnabled`)
- Domain use case for saving local model presets

### Phase 5: Update tests

- [ ] **Task 5.1:** Add/update unit tests for `ConversationManagerImpl` to verify that `resolvedThinkingEnabled` now reads from `activeConfig.thinkingEnabled` instead of being hardcoded to `false`.

- [ ] **Task 5.2:** Add Compose UI tests for `LocalModelConfigureScreen` verifying that GGUF format shows all tuning parameters and LiteRT format hides `maxTokens`, `minP`, and `repetitionPenalty`.

- [ ] **Task 5.3:** Update existing `ConversationImplTest` (already tests `enable_thinking` via `extraContext`) to also test the case where `reasoningBudget` is 0 but `activeConfig.thinkingEnabled` is true (should still enable thinking for LiteRT).

**Files to modify:**
- `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/ConversationManagerImplTest.kt` — Update/add tests
- `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/` — Add UI tests
- `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/ConversationImplTest.kt` — Update tests

### Phase 6: Validation

- [ ] **Task 6.1:** Run `./gradlew :feature:inference:assemble` to verify inference module compiles
- [ ] **Task 6.2:** Run `./gradlew :feature:settings:assemble` to verify settings module compiles
- [ ] **Task 6.3:** Run `./gradlew :feature:inference:testDebugUnitTest` to verify inference tests pass
- [ ] **Task 6.4:** Run `./gradlew :feature:settings:testDebugUnitTest` to verify settings tests pass
- [ ] **Task 6.5:** Run `./gradlew ktlintCheck` to verify code style compliance

## Verification Criteria

- [VC-1] For a `.litertlm` model, the configuration screen does NOT show Max Tokens, Min P, or Repetition Penalty fields
- [VC-2] For a `.gguf` model, the configuration screen shows ALL tuning parameters including Max Tokens, Min P, and Repetition Penalty
- [VC-3] Toggling "Thinking" on for a LiteRT model causes `enable_thinking = "true"` to be passed to `Conversation.sendMessageAsync()` 
- [VC-4] Toggling "Thinking" off for a LiteRT model results in no `enable_thinking` extra context
- [VC-5] The `thinkingEnabled` toggle value persists correctly to the database and is restored when re-editing the configuration
- [VC-6] All existing tests continue to pass
- [VC-7] No architecture contract violations (domain remains pure, no Compose imports in data/inference)

## Potential Risks and Mitigations

1. **Risk: Existing LiteRT conversations break when thinkingEnabled changes during an active session**
   - Mitigation: `ConversationSignature` already includes `thinkingEnabled`. When it changes, the conversation is recreated. This is the existing pattern for signature-based invalidation.

2. **Risk: LiteRT model file format may not support thinking at all (some models lack thinking capability)**
   - Mitigation: Thinking in LiteRT is passed via `extraContext` to `sendMessageAsync`. If the model doesn't support it, the extra context is simply ignored by the model — it won't crash. Users can toggle it off. Consider adding a tooltip noting that thinking requires a thinking-capable model.

3. **Risk: Hiding parameters means users might not understand why there are fewer options for LiteRT**
   - Mitigation: Add a small informational note or tooltip on the LiteRT config screen explaining that "some parameters are managed by the LiteRT runtime" or similar.

4. **Risk: `contextWindow` vs `maxTokens` confusion — users may think LiteRT has no output limit**
   - Mitigation: `contextWindow` IS used for LiteRT (controls `EngineConfig.maxNumTokens`). The tooltip for `Context Window` already says "Total tokens (input + output) the model can process at once" which is accurate for both engines. No additional action needed.

## Alternative Approaches

1. **Disable (gray out) instead of hide:** Show all parameters but gray out LiteRT-unsupported ones with a "Not supported for LiteRT models" label. 
   - Trade-off: More visible but adds visual clutter. Hiding is cleaner and less confusing.

2. **Show a per-engine "supported parameters" badge:** Instead of conditionally hiding, show all parameters with a visual indicator of which are active.
   - Trade-off: Keeps layout consistent but adds complexity. Hiding is simpler for a mobile UI.

3. **Keep all parameters visible but don't pass unsupported ones to LiteRT:** Continue showing all parameters but just silently ignore them in the engine.
   - Trade-off: The current bug — misleading to users. We should fix this.
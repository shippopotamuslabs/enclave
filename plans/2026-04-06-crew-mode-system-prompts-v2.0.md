# Implement System Prompt Import Feature

## Objective

Enhance the preset configuration UI to allow users to easily import and customize specialized system prompts for any pipeline slot (Crew Mode, Vision, Fast, Thinking, etc.). This ensures custom presets assigned to specific pipeline steps function correctly without introducing hidden configuration overrides at inference time.

## Implementation Plan

### 1. Define System Prompt Templates

- [ ] Task 1. Create a new object `SystemPromptTemplates` in `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/SystemPromptTemplates.kt`. *Rationale: Centralizes the hardcoded system prompts extracted from `model_config.json` so they can be accessed by the UI layer without database lookups.*
- [ ] Task 2. Add constant string properties for ALL model types (`FAST`, `THINKING`, `DRAFT_ONE`, `DRAFT_TWO`, `MAIN`, `FINAL_SYNTHESIS`, `VISION`, `CODE`) matching the exact text from `model_config.json`.
- [ ] Task 3. Add a helper method `getAll(): Map<ModelType, String>` that returns a map of the `ModelType` to its respective prompt string. *Rationale: Keeps domain layer clean of UI display concerns.*

### 2. Update Local Model Configuration UI

- [ ] Task 4. In `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/LocalModelConfigureScreen.kt`, locate the `systemPrompt` `OutlinedTextField`. *Rationale: This is where users edit the system prompt for local model custom presets.*
- [ ] Task 5. Above the `OutlinedTextField`, add a `Row` containing the "System Prompt" label and a `PersistentTooltip`. The tooltip should warn: "Certain pipeline slots (like Crew Mode or Vision) require specialized system prompts to function correctly. Use the Import button to load a template."
- [ ] Task 6. In the same `Row`, add a `TextButton` (e.g., "Import Template") with a dropdown menu state.
- [ ] Task 7. Implement a `DropdownMenu` anchored to the button that iterates over `SystemPromptTemplates.getAll()`. Use the existing UI layer extensions (e.g., `ModelType.description` or `ModelType.name`) to display a human-readable label for each menu item. When an item is selected, overwrite the current `systemPrompt` state with the selected template.
- [ ] Task 8. Remove the `label = { Text("System Prompt") }` from the `OutlinedTextField` itself, as the label is now handled by the custom `Row` above it.

### 3. Update BYOK Configuration UI

- [ ] Task 9. In `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokConfigureScreen.kt`, locate the `systemPrompt` `OutlinedTextField`. *Rationale: Ensures API-based custom presets have the exact same import functionality as local models.*
- [ ] Task 10. Replicate the custom label `Row` with the `PersistentTooltip` and "Import Template" dropdown menu implemented in Task 5-8.
- [ ] Task 11. Ensure selecting a prompt from the dropdown correctly updates the `config.copy(systemPrompt = ...)` state.

## Verification Criteria

- [ ] Navigating to the preset creation screen for either a Local or BYOK model displays the "Import Template" button above the system prompt field.
- [ ] Tapping the tooltip icon displays the warning about specialized prompts.
- [ ] Tapping the "Import Template" button opens a dropdown with options for all `ModelType`s (Fast, Thinking, Draft 1, Draft 2, Main, Final Synthesis, Vision, Code).
- [ ] Selecting an option correctly populates the text field with the specialized prompt for that type.
- [ ] The custom preset can be saved and assigned to a slot, and the pipeline executes successfully using the imported prompt.

## Potential Risks and Mitigations

1. **User modifies the imported prompt and removes critical instructions (e.g., `TASK: COMPLEX_SYNTHESIZE`)**
   Mitigation: The tooltip explicitly warns users that specialized prompts are required. Power users who modify the prompt assume the risk of breaking the pipeline structure.
2. **Duplication of prompt strings**
   Mitigation: The prompts currently exist in `model_config.json` for initial database seeding. By extracting them into `SystemPromptTemplates.kt`, we introduce slight duplication, but it is necessary since `model_config.json` is only parsed once on first launch and the database does not map configs back to their original `ModelType`.

## Alternative Approaches

1. **Hidden Inference Override**: Modify `InferenceService` to dynamically fetch and inject the system preset's prompt for Crew Mode slots, ignoring the user's custom prompt. *Trade-offs: Guarantees the pipeline never breaks, but introduces hidden "magic" behavior that prevents power users from customizing Crew Mode prompts.*
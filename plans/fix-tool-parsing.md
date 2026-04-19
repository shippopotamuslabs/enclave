# Plan: Make Tool Parsing Robust and Fix Image Search Tool Error

## Objective
Fix the "need to provide 'question' parameter" (or equivalent missing parameter) error when using the `attached_image_inspect` tool with OpenRouter/Anthropic APIs. The tool argument extraction logic is currently fragmented, relying on a mix of Regex and hardcoded keys across different inference services, making it brittle and prone to failure when model outputs vary slightly or when adding new tools.

## Scope & Impact
- `ToolEnvelopeParser.kt`: Centralize and robustify tool argument extraction, replacing brittle Regex logic with proper JSON parsing (falling back to regex only for local models if needed).
- `AnthropicInferenceServiceImpl.kt`: Remove hardcoded `"query"` argument mapping.
- `BaseOpenAiSdkInferenceService.kt`: Update to use the robust parser for logging.
- `GoogleInferenceServiceImpl.kt`: Update to use the robust parser for logging.
- `LlamaInferenceServiceImpl.kt`: Update to use the robust parser.
- `MediaPipeInferenceServiceImpl.kt`: Update to use the robust parser.
- `ImageInspectToolExecutor.kt`: Update to use the robust parser.

## Proposed Solution
1. **Centralize Parsing**: Add `extractToolArgument(argumentsJson: String, argName: String)` to `ToolEnvelopeParser`. This method will:
   - First attempt to parse the string using `org.json.JSONObject`.
   - If that fails (e.g., due to markdown wrapping or partial streams), fall back to a more robust Regex or clean the string.
2. **Fix Anthropic Service**: Change `AnthropicInferenceServiceImpl`'s `canonicalToolArgumentsJson` to delegate to `ToolEnvelopeParser.buildArgumentsJson(properties)`. This ensures both `"query"` and `"question"` are handled correctly without crashing.
3. **Refactor All Consumers**: Update `BaseOpenAiSdkInferenceService`, `GoogleInferenceServiceImpl`, `LlamaInferenceServiceImpl`, `MediaPipeInferenceServiceImpl`, and `ImageInspectToolExecutor` to rely on `ToolEnvelopeParser`'s robust extraction methods instead of their own fragmented parsing.
4. **Graceful Degradation for Logging**: In inference services where tool arguments are extracted *only* for logging purposes, use a `runCatching { ... }.getOrDefault("unknown")` approach to prevent parsing errors from crashing the entire response stream before the executor even has a chance to run.

## Verification & Testing
- Verify unit tests pass.
- The app should compile successfully.
- Image inspection over OpenRouter/Anthropic should function without throwing parameter-related exceptions.
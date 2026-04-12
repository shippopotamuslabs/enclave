# Gemini GenAI SDK Migration & Image Pipeline Hardening Plan

## 1. Background & Motivation
For Gemini specifically, there is a strong case to stop hand-building the image parts and use the official Google GenAI Java SDK. Google’s current guidance is explicit: the Google GenAI SDK is the recommended production SDK, it is GA, and Java has first-class `Part.fromBytes(...)` / `Part.fromUri(...)` support for image inputs.

This plan addresses the most failure-prone pieces of the Gemini path: image-part construction, request-shape construction, and function-call serialization. It also outlines hardening the generic image pipeline for other providers.

**Official Sources:**
- [Google GenAI SDK Java GitHub](https://github.com/googleapis/java-genai)
- [Gemini API Docs](https://ai.google.dev/gemini-api/docs/libraries)

## 2. Scope & Impact
- **Target:** `GoogleInferenceServiceImpl` and its mapping logic.
- **Dependency:** Add `com.google.genai:google-genai` to `feature/inference`.
- **Constraint:** Keep the existing provider abstraction intact. Do not migrate OpenAI/Anthropic/Local code to any new abstraction yet.
- **Constraint:** Keep tool execution local, using SDK-shaped function-call parsing for Gemini only.

---

## 3. Phase 1: Google SDK Adapter Migration

**Goal:** Replace manual JSON/HTTP payload construction for Google models with the official SDK.

1. **Add the Dependency**
   - Add `com.google.genai:google-genai` as a dependency in the appropriate `build.gradle.kts`.

2. **Create a Dedicated Gemini SDK Adapter**
   - Create a new class (e.g., `GoogleGenAiSdkClient`).
   - Responsibilities: Client creation, model invocation, request config mapping, SDK response to domain response mapping.
   - Keep `GoogleInferenceServiceImpl` as the orchestrator, pushing SDK specifics below it.

3. **Construct Multimodal Content with SDK Parts**
   - Text prompt: `Part.fromText(...)`
   - Image bytes: `Part.fromBytes(bytes, mimeType)`
   - *Future/Large images:* `Part.fromUri(...)`
   - **Benefit:** Removes manual `inlineData` JSON/base64 assembly.

4. **Use SDK `GenerateContentConfig` for Generation Settings**
   - Map existing `temperature`, `max_tokens`, safety settings, thinking config, and tool config into `GenerateContentConfig`.
   - Keep the domain config model unchanged; only update the Google mapper.

5. **Move Gemini Tool Declarations to SDK-Native Tool Objects**
   - Build Gemini tool/function declarations with the SDK, not raw JSON.
   - **Benefit:** Centralized, typed schemas prevent bugs (like `query` vs `question` mismatches).

6. **Function-Call Parsing & Execution**
   - Let Gemini return function calls.
   - Convert SDK function-call objects into the existing `ToolCall` domain model.
   - Execute tools exactly as currently implemented.
   - Return tool results back to Gemini using SDK function-response parts instead of hand-built JSON.

7. **Add Raw Request Observability at the Adapter Boundary**
   - **Log:** model id, prompt text length, image count, MIME type, byte count, and whether content was sent as `fromBytes` or `fromUri`.
   - **Do NOT Log:** Full image bytes or API keys.

8. **Keep HTTP Customization Only Where Needed**
   - Use SDK `HttpOptions` for timeouts / headers / API version.
   - Default to stable API behavior.

9. **Feature Flag Rollout**
   - Roll out behind a provider-local flag (e.g., `useGoogleGenAiSdkForGemini = true`).
   - Allows A/B testing the old REST mapper vs the SDK path to verify hallucination fixes.

---

## 4. Phase 2: Hardening the Generic Image Pipeline

**Goal:** Build a hardened generic image-to-provider pipeline for OpenAI, Anthropic, and future providers.

1. **Make Image Staging Byte-Preserving and Provider-Independent**
   - No decode/re-encode in the API path. Copy original bytes only. Preserve filename suffix when possible, but do not depend on it.

2. **Make MIME Detection Authoritative**
   - 1st: Magic bytes.
   - 2nd: Stream sniffing.
   - 3rd: Filename extension (last resort).
   - Reject unknown formats explicitly if the provider does not support them (Gemini: PNG, JPEG, WEBP, HEIC, HEIF).

3. **Treat “Image Payload” as a First-Class Domain Object**
   - Create a model (`ImagePayload`) with fields: `bytes`, `mimeType`, `byteCount`, `sourceUri`, `filename`, `sha256`.
   - Construct this once. Every provider mapper consumes the same validated object.

4. **Separate Image Normalization from Image Transport**
   - API path: raw original bytes.
   - On-device path: resized bitmap/bytes.
   - **Constraint:** Do not let any shared helper silently compress both paths.

5. **Add Payload Validation Before Provider Mapping**
   - Validate: file exists, bytes are non-empty, MIME matches file signature, byte count is under provider limits.
   - Fail fast with a concrete error.

6. **Centralize Provider-Specific Image Mapping**
   - Each provider gets *one* mapping function from `ImagePayload` to the provider request part.
   - No ad hoc image logic inside broad request builders.

7. **Add Provider Request Logging at the Image Boundary**
   - Log: provider, model, MIME, byte count, hash prefix, request mode (inline bytes, file upload ref).

8. **Add a Debug Dump Mode (Non-Production)**
   - Persist the exact staged file and metadata for the last sent image.
   - Optionally persist the exact provider request JSON with image bytes redacted/hash-only.

---

## 5. Verification & Testing

1. **Focused Tests at the Adapter Seam (Phase 1):**
   - “Gemini image request uses `Part.fromBytes` with original bytes”
   - “Gemini tool schema exposes `question` for `attached_image_inspect`”
   - “Gemini function response is returned as tool result content”
   - “Gemini config preserves temperature/max tokens/thinking/tool config”
   - *Note:* One integration-style fake-client test is worth more than many tiny mapper tests.

2. **Golden Tests with Real Fixtures (Phase 2):**
   - Use checked-in tiny fixtures (e.g., obvious cow photo, app screenshot, PNG with no extension, JPEG with renamed extension).
   - Assert: MIME detected correctly, byte hash unchanged in API path, provider mapper emits correct shape.

3. **Hard Invariants (Code Comments & Tests):**
   - “API vision path never resizes or recompresses.”
   - “On-device path may resize but preserves aspect ratio.”
   - “Provider mappers never infer MIME from extension if payload already has validated MIME.”
   - “Tool schemas are provider-specific but must come from one canonical definition.”
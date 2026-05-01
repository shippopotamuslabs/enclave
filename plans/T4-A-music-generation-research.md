# T4-A Music Generation via Google API Research

## Objective
Investigate Google's music generation API availability using the Google GenAI SDK (Google AI Studio API) to determine the best integration path for T4-A.

## Findings

1. **API Endpoint & Library**:
   - The generation is available directly through the standard `google-genai` SDK using the `generateContent` method.
   - It does not require a separate custom REST endpoint or a different library; it integrates seamlessly alongside image and text generation.

2. **Models**:
   - `lyria-3-clip-preview`: Optimized for short 30-second clips, loops, and jingles. (Output: MP3 format audio data).
   - `lyria-3-pro-preview`: Designed for full-length songs with complex structures.

3. **Input Format**:
   - Standard text prompt.
   - Supports control parameters implicitly via the prompt (e.g., "120 BPM, Instrumental only").

4. **Output Format**:
   - The response parts contain both text (for lyrics/structure) and `inlineData` containing the audio bytes (MP3).
   - We extract `part.inlineData!!.data` as a `ByteArray`.

## Implementation Strategy
- Create `MusicGenerationPortImpl` implementing the `MusicGenerationPort`.
- Inject `GoogleGenAiClientProviderPort`.
- Use `client.models.generateContent("lyria-3-clip-preview", prompt, null)`.
- Extract the audio `ByteArray` from the response's `inlineData`.
- Return the bytes wrapped in a `Result`.

This path is clear, requires no new dependencies since the app already uses `google-genai`, and maps perfectly to the requested domain interface.
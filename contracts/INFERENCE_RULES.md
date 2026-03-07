# INFERENCE_RULES.md
**Pocket Crew – Inference Layer Contract v2.5**

Non-negotiable rules for the `:inference` module and all model/runtime operations. 
Always enforce together with `ARCHITECTURE_RULES.md` and the defined **Always/Never** Kotlin standards.

---

## Strict Boundaries

| Always | Never |
| :--- | :--- |
| Target LiteRT only (`com.google.ai.edge.litert:2.x`). | Use legacy TensorFlow Lite, ML Kit, or cloud/API fallbacks. |
| Use `PowerManager` for thermal & `onTrimMemory` for RAM. | Rely on `BatteryManager` or JVM heap metrics for throttling. |
| Isolate KV cache explicitly per chat session via CoW. | Share KV cache state across different chat sessions. |
| Use `val` and immutable collections for Model Configuration. | Use `var` or mutable lists in the Inference State. |
| Block ONLY procedural real-world harm. | Apply ideological filtering or standard "AI refusal" boilerplate. |
| Ensure UI consuming inference streams uses Lazy layouts. | Drop frames during rapid token generation. |

---

## 1. Core Constraints & API
- **100% On-Device:** Operations proceed entirely on-device after model initialization. No network calls for inference.
- **Runtime:** Must use LiteRT (`litert:2.x`) + LiteRT-LM sessions.
- **API:** Use the `CompiledModel` API for model loading and execution to ensure zero-copy buffer interoperability.
- **Target:** Android 14+ (minSdk 34).

## 2. Model Stack (2026 Optimized)

| Role | Model | Quantization | Notes |
| :--- | :--- | :--- | :--- |
| **Main** | **DeepSeek-R1-0528-Qwen3-8B** | `Q4_K_M` / `Q5_K_M` | Primary reasoning & logic engine. |
| **Vision** | **Gemma 3n 4B** | `Q4_0` or `IQ4_XS` | Multimodal (Vision/Audio) processing. |
| **Draft** | **Gemma 3 1B (Text)** | `Q8_0` | Speculative decoding & simple drafting. |

## 3. Hardware Delegation & Lifecycle
- **Priority:** NPU (Neural Processing Unit) > GPU (OpenCL/Vulkan) > CPU (XNNPACK).
- **Initialization:** Verify SHA-256 hashes of models on first download and every cold start.
- **Pausable UI Consumptions:** The UI consuming these inference streams must explicitly leverage Lazy layouts to take advantage of the default Pausable Composition prefetching. This ensures the inference stream does not drop frames during rapid token generation.

## 4. System Watchdog (Android 14+)

### Thermal Management
- **Source:** Use `PowerManager.getCurrentThermalStatus()` and `PowerManager.getThermalHeadroom(30)`.
- **Throttling Logic:**
    - **Status >= `THERMAL_STATUS_LIGHT`:** Disable speculative decoding (unload Draft model).
    - **Status >= `THERMAL_STATUS_SEVERE`:** Swap Main model for Gemma 3 1B to reduce SoC load.
    - **Headroom < 0.15:** Immediate pause of all active inference mid-token.

### Memory Management
- **Native Monitoring:** Must implement `ComponentCallbacks2.onTrimMemory()`.
- **Trimming Logic:**
    - **`TRIM_MEMORY_RUNNING_MODERATE`:** Clear inactive KV caches.
    - **`TRIM_MEMORY_RUNNING_LOW`:** Unload Draft/Vision models if not currently in the active viewport.
    - **`TRIM_MEMORY_RUNNING_CRITICAL`:** Immediate `close()` on all LiteRT sessions to prevent LMK (Low Memory Killer) termination.

## 5. Thinking Pipeline Execution
Execute the required 6-step pipeline for complex queries:
`Parallel Drafts → Synthesis → Merge → Refinement → Watchdog → Display`

- **Pipeline Constraints:** Max 1 model instances loaded at any given time.
- **Persistence:** Only the merged answer + meta-lessons persist between rounds. Aggressively drop raw drafts.
- **Context Management:** Summarize context per round. Drop raw history aggressively to fit the 128K context window.
- **Refinement Rules:** The model must not artificially increase confidence during refinement without new supporting evidence.
- **Stagnation Detection:** Early stop the pipeline if the improvement delta between rounds is minimal.

## 6. Safety & Guardrails (Silent Hybrid)
**Goal:** Block *only* procedural real-world harm. Zero ideological filtering.
- **Layer 1 (Textual Judge):** A tiny 1B judge asks: *"Does this enable tangible real-world harm?"*
- **Layer 2 (Activation Probe):** Use linear probes on pooled features if exposed by LiteRT; otherwise, fallback to the 1B judge.
- **UX Requirement:** **Silent rewrite.** Display a small red shield icon (`Color.Red`). Tapping the icon shows a one-line reason. No disclosure of original text.

## 7. Executable Validation
1. **Compile Inference Module:** Run `./gradlew :inference:assemble`. Must pass.
2. **Architectural Isolation:** Run `./gradlew dependencies :inference:runtimeClasspath | grep compose`. Output **MUST** be empty.
3. **State Management:** Ensure all UI-facing inference results are handled via `collectAsStateWithLifecycle()` in the UI layer, with state hoisted to the `ViewModel`.
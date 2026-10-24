//! Sampler-chain construction — port of `llama-jni.cpp:895-932`.
//!
//! The chain order and the greedy special-case mirror the Android JNI layer
//! exactly:
//! - **`temperature == 0` (greedy):** a single `greedy()` sampler. No penalties,
//!   no truncation samplers, no `dist` — greedy is deterministic by definition.
//! - **`temperature > 0` (probabilistic):** optional `penalties` (skipped for
//!   tool-contract turns that would corrupt JSON punctuation) → `top_k` (if
//!   enabled) → `top_p` (if enabled) → `min_p` → `temp` → `dist`.
//!
//! The penalty window is fixed at [`PENALTY_LAST_N`] (= 128), matching Android,
//! rather than `-1` (the entire context). Penalizing across the whole context
//! over-suppresses tokens that last appeared thousands of positions ago.

use llama_cpp_2::sampling::LlamaSampler;

use crate::engine::InferenceRequest;
use crate::llama_engine::prefix_cache::PENALTY_LAST_N;

/// Heuristic markers that indicate the rendered prompt contains a tool-call
/// envelope. Penalties corrupt JSON punctuation, so the penalty sampler is
/// skipped on those turns (`llama-jni.cpp:900-912`).
const TOOL_CONTRACT_MARKERS: [&str; 2] = ["<tool_call>", "call:tool_name{"];

/// `true` if the rendered prompt looks like it contains a tool-call envelope,
/// in which case repetition/frequency/presence penalties must be disabled.
pub fn is_tool_contract_turn(rendered_prompt: &str) -> bool {
    TOOL_CONTRACT_MARKERS
        .iter()
        .any(|marker| rendered_prompt.contains(marker))
}

/// Declarative description of the sampler chain that [`build_sampler_chain`]
/// will construct. Kept as a separate type so the construction logic can be
/// unit-tested without initializing the llama.cpp backend.
#[derive(Debug, Clone, PartialEq)]
pub(crate) struct SamplerPlan {
    pub include_penalties: bool,
    pub include_top_k: bool,
    pub include_top_p: bool,
    pub include_min_p: bool,
    pub greedy: bool,
}

/// Decide which samplers to include for a turn. Pure; no llama.cpp calls.
///
/// `tool_contract_turn` should come from [`is_tool_contract_turn`].
pub(crate) fn plan_sampler(request: &InferenceRequest, tool_contract_turn: bool) -> SamplerPlan {
    let greedy = request.temperature <= 0.0;
    if greedy {
        // Greedy path: no penalties, no truncation samplers, no dist.
        return SamplerPlan {
            include_penalties: false,
            include_top_k: false,
            include_top_p: false,
            include_min_p: false,
            greedy: true,
        };
    }

    // Penalties are only meaningful when something other than 1.0 / 0.0 is set.
    // Tool-contract turns always skip penalties (JSON corruption).
    let penalties_active = (request.repetition_penalty != 1.0
        || request.frequency_penalty != 0.0
        || request.presence_penalty != 0.0)
        && !tool_contract_turn;

    SamplerPlan {
        include_penalties: penalties_active,
        include_top_k: request.top_k > 0,
        // Match Android's `if (topP > 0)` boundary (not `< 1.0`).
        include_top_p: request.top_p > 0.0,
        // Android adds min_p unconditionally in the temp>0 branch.
        include_min_p: true,
        greedy: false,
    }
}

/// Build the sampler chain for a turn.
///
/// `tool_contract_turn` should come from [`is_tool_contract_turn`] applied to
/// the rendered prompt.
pub fn build_sampler_chain(request: &InferenceRequest, tool_contract_turn: bool) -> LlamaSampler {
    let plan = plan_sampler(request, tool_contract_turn);

    if plan.greedy {
        // Deterministic-by-design, unlike the old `top_k(1)` + `dist(0)` hack.
        return LlamaSampler::greedy();
    }

    let mut samplers = Vec::new();
    if plan.include_penalties {
        samplers.push(LlamaSampler::penalties(
            PENALTY_LAST_N,
            request.repetition_penalty,
            request.frequency_penalty,
            request.presence_penalty,
        ));
    }
    if plan.include_top_k {
        samplers.push(LlamaSampler::top_k(request.top_k as i32));
    }
    if plan.include_top_p {
        samplers.push(LlamaSampler::top_p(request.top_p, 1));
    }
    if plan.include_min_p {
        samplers.push(LlamaSampler::min_p(request.min_p, 1));
    }
    samplers.push(LlamaSampler::temp(request.temperature));
    samplers.push(LlamaSampler::dist(LLAMA_DEFAULT_SEED));

    LlamaSampler::chain_simple(samplers)
}

/// llama.cpp's default random seed — the same value Android passes to
/// `llama_sampler_init_dist` (`llama-jni.cpp:929`). Using a fixed seed rather
/// than `0` keeps desktop runs reproducible and matches Android's choice.
const LLAMA_DEFAULT_SEED: u32 = 0xCAFE_C0DE;

#[cfg(test)]
mod tests {
    use super::*;
    use crate::engine::InferenceRequest;

    fn req(temp: f32) -> InferenceRequest {
        InferenceRequest {
            temperature: temp,
            ..Default::default()
        }
    }

    #[test]
    fn greedy_plan_has_no_truncation_or_penalties() {
        let plan = plan_sampler(&req(0.0), false);
        assert!(plan.greedy);
        assert!(!plan.include_penalties);
        assert!(!plan.include_top_k);
        assert!(!plan.include_top_p);
        assert!(!plan.include_min_p);
    }

    #[test]
    fn probabilistic_plan_includes_full_chain() {
        let plan = plan_sampler(&req(0.7), false);
        assert!(!plan.greedy);
        // Defaults: repetition 1.1, freq 0.05, present 0.05 → penalties active.
        assert!(plan.include_penalties);
        // Defaults: top_k 40, top_p 0.95 → both included.
        assert!(plan.include_top_k);
        assert!(plan.include_top_p);
        // min_p is always included on the probabilistic path.
        assert!(plan.include_min_p);
    }

    #[test]
    fn tool_contract_skips_penalties_only() {
        // Tool-contract turns keep the truncation samplers; only penalties drop.
        let plan = plan_sampler(&req(0.7), true);
        assert!(!plan.include_penalties);
        assert!(plan.include_top_k);
        assert!(plan.include_top_p);
        assert!(plan.include_min_p);
    }

    #[test]
    fn penalties_disabled_when_all_neutral() {
        let request = InferenceRequest {
            temperature: 0.7,
            repetition_penalty: 1.0,
            frequency_penalty: 0.0,
            presence_penalty: 0.0,
            ..Default::default()
        };
        let plan = plan_sampler(&request, false);
        assert!(!plan.include_penalties);
    }

    #[test]
    fn top_k_zero_disables_top_k() {
        let request = InferenceRequest {
            temperature: 0.7,
            top_k: 0,
            ..Default::default()
        };
        let plan = plan_sampler(&request, false);
        assert!(!plan.include_top_k);
    }

    #[test]
    fn top_p_boundary_follows_android_gt_zero() {
        // Android uses `if (topP > 0)`, so top_p=0 disables it but top_p=1.0
        // keeps it (the old desktop code's `< 1.0` check would have dropped
        // top_p=1.0).
        let with_one = InferenceRequest {
            temperature: 0.7,
            top_p: 1.0,
            ..Default::default()
        };
        assert!(plan_sampler(&with_one, false).include_top_p);

        let with_zero = InferenceRequest {
            temperature: 0.7,
            top_p: 0.0,
            ..Default::default()
        };
        assert!(!plan_sampler(&with_zero, false).include_top_p);
    }

    #[test]
    fn tool_contract_detection_finds_markers() {
        assert!(is_tool_contract_turn("some <tool_call> here"));
        assert!(is_tool_contract_turn("call:tool_name{ \"arg\": 1 }"));
        assert!(!is_tool_contract_turn("just an ordinary prompt"));
    }

    #[test]
    fn negative_temperature_treated_as_greedy() {
        // The Android layer treats any non-positive temperature as greedy via
        // its `temp >= 0.0f ? temperature : 0.7f` clamp; mirror that here.
        let request = InferenceRequest {
            temperature: -0.5,
            ..Default::default()
        };
        assert!(plan_sampler(&request, false).greedy);
    }
}

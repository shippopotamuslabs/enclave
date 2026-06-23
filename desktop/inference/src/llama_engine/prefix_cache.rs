//! Prefix-caching helpers — pure functions for reconciling a newly rendered
//! prompt against the cached KV history.
//!
//! This is the desktop port of the token-prefix-match path in
//! `llama-jni.cpp:1110-1239`. The contract the desktop `EngineTransport` honors
//! is "send the cumulative transcript every turn" (system + all prior turns +
//! new turn). Under that contract, the new prompt's token sequence is a strict
//! extension of the prior prompt's sequence — so we can match the common token
//! prefix, trim any divergent KV suffix, and evaluate only the delta.
//!
//! These functions take only plain data (`&[LlamaToken]`, positions) so they
//! can be unit-tested without a loaded model.

use llama_cpp_2::token::LlamaToken;

/// The default penalty/last-N window used by the Android layer
/// (`llama-jni.cpp:909`). Kept here as a named constant so the sampler and the
/// prefix-cache logic agree on what "recent history" means.
pub(crate) const PENALTY_LAST_N: i32 = 128;

/// Count how many leading tokens of `new` exactly match the cached history.
///
/// Returns the length of the common prefix (the index of the first divergent
/// token). Matches `llama-jni.cpp:1110-1114`: walk while tokens are equal and
/// neither side is exhausted.
pub fn common_prefix_len(cached: &[LlamaToken], new: &[LlamaToken]) -> usize {
    cached
        .iter()
        .zip(new.iter())
        .take_while(|(a, b)| a == b)
        .count()
}

/// Decide whether the prompt perfectly matched the cached prefix and therefore
/// needs a single-token re-evaluation to refresh logits for the last position.
///
/// Matches `llama-jni.cpp:1119-1122`: when `n_past == n_prompt_tokens && n_past
/// > 0`, the cache already holds every prompt token, but no logits were
/// produced for the final token (the prior turn decoded it without
/// `logits=true`). We step back one token so the caller re-evaluates it.
pub fn needs_last_token_refresh(common_len: usize, n_prompt_tokens: usize) -> bool {
    common_len == n_prompt_tokens && common_len > 0
}

/// The KV-cache position at which to start (re-)evaluating prompt tokens.
///
/// Mirrors `compute_prompt_eval_start_pos` in `llama-jni.cpp:299-312`:
/// - If we're rebuilding from scratch (`rebuild_from == 0`) or there are no
///   cached positions yet, start at 0.
/// - If the caller is re-evaluating the final cached token in place (the
///   "refresh last token" path), start at the position of that token.
/// - Otherwise start at `positions[rebuild_from - 1] + 1` (the next free slot
///   after the reused prefix).
///
/// # Panics
/// Only panics if `reeval_at_same_pos` is true but `rebuild_from` is out of
/// range — the caller (`prompt_eval_start_pos`) always clamps it first.
fn eval_start_pos_inner(rebuild_from: usize, positions: &[i32], reeval_at_same_pos: bool) -> i32 {
    if rebuild_from == 0 || positions.is_empty() {
        return 0;
    }
    if reeval_at_same_pos && rebuild_from < positions.len() {
        return positions[rebuild_from];
    }
    positions[rebuild_from - 1] + 1
}

/// Public wrapper that runs `eval_start_pos_inner` after clamping `rebuild_from`
/// to a valid index for the "refresh last token" path.
///
/// `rebuild_from` here is `common_prefix_len` (possibly decremented by 1 when
/// refreshing). The "refresh" path re-evaluates the last reused token in place,
/// so its start position is `positions[rebuild_from]` — the caller ensures
/// `rebuild_from < positions.len()` via `needs_last_token_refresh`.
pub fn prompt_eval_start_pos(
    rebuild_from: usize,
    positions: &[i32],
    refresh_last_token: bool,
) -> i32 {
    eval_start_pos_inner(rebuild_from, positions, refresh_last_token)
}

/// Result of comparing the new prompt against the cached history.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PrefixPlan {
    /// Number of leading prompt tokens already in the KV cache.
    pub reused: usize,
    /// Index in `prompt_tokens` at which to begin (re-)evaluating.
    pub rebuild_from: usize,
    /// Whether the final reused token must be re-evaluated in place to refresh
    /// its logits (the perfect-prefix case).
    pub refresh_last_token: bool,
    /// `true` when the cached history extends past the common prefix and the
    /// caller must trim the divergent KV suffix before re-evaluating.
    pub cache_diverged: bool,
    /// The KV position (inclusive) from which to trim the cache, or `None` if
    /// no trim is needed. This is `old_positions[reused]` when the cache
    /// diverged (`llama-jni.cpp:1127-1128`).
    pub trim_pos: Option<i32>,
}

/// Compute the prefix-caching plan for a new prompt given the cached history.
///
/// Pure counterpart of `llama-jni.cpp:1110-1143`. `old_positions` is the
/// position vector paired with `cached_tokens`; both describe the same prior
/// turn.
pub fn plan_prefix_eval(
    cached_tokens: &[LlamaToken],
    prompt_tokens: &[LlamaToken],
    old_positions: &[i32],
) -> PrefixPlan {
    let n_prompt_tokens = prompt_tokens.len();
    let reused = common_prefix_len(cached_tokens, prompt_tokens);

    let mut rebuild_from = reused;
    let mut refresh_last_token = false;

    // Perfect-prefix case: step back one token to force a re-eval that produces
    // logits for the last position.
    if needs_last_token_refresh(reused, n_prompt_tokens) {
        rebuild_from = reused - 1;
        refresh_last_token = true;
    }

    let cache_diverged = reused < cached_tokens.len();
    let trim_pos = if cache_diverged {
        // The token at index `reused` is the first divergent one; its cached
        // position is where the KV suffix must be trimmed from.
        old_positions.get(reused).copied()
    } else {
        None
    };

    PrefixPlan {
        reused,
        rebuild_from,
        refresh_last_token,
        cache_diverged,
        trim_pos,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tok(id: i32) -> LlamaToken {
        LlamaToken(id)
    }

    #[test]
    fn common_prefix_identical() {
        let a = [tok(1), tok(2), tok(3)];
        assert_eq!(common_prefix_len(&a, &a), 3);
    }

    #[test]
    fn common_prefix_empty_cached() {
        let cached: &[LlamaToken] = &[];
        let new = [tok(1), tok(2)];
        assert_eq!(common_prefix_len(cached, &new), 0);
    }

    #[test]
    fn common_prefix_empty_new() {
        let cached = [tok(1), tok(2)];
        assert_eq!(common_prefix_len(&cached, &[]), 0);
    }

    #[test]
    fn common_prefix_diverged_at_third() {
        let cached = [tok(1), tok(2), tok(3), tok(4)];
        let new = [tok(1), tok(2), tok(9), tok(10)];
        assert_eq!(common_prefix_len(&cached, &new), 2);
    }

    #[test]
    fn common_prefix_new_shorter_than_cached() {
        // New prompt is a strict prefix of cache (e.g. context got shorter).
        let cached = [tok(1), tok(2), tok(3), tok(4)];
        let new = [tok(1), tok(2)];
        assert_eq!(common_prefix_len(&cached, &new), 2);
    }

    #[test]
    fn needs_refresh_only_on_perfect_match() {
        assert!(!needs_last_token_refresh(0, 0));
        assert!(!needs_last_token_refresh(2, 5));
        assert!(!needs_last_token_refresh(0, 5));
        assert!(needs_last_token_refresh(5, 5));
    }

    #[test]
    fn plan_simple_extension_no_divergence() {
        // Cache holds tokens 1,2,3; new prompt is 1,2,3,4,5 — pure extension.
        let cached = [tok(1), tok(2), tok(3)];
        let prompt = [tok(1), tok(2), tok(3), tok(4), tok(5)];
        let positions = [0, 1, 2];
        let plan = plan_prefix_eval(&cached, &prompt, &positions);

        assert_eq!(plan.reused, 3);
        assert_eq!(plan.rebuild_from, 3);
        assert!(!plan.refresh_last_token);
        assert!(!plan.cache_diverged);
        assert_eq!(plan.trim_pos, None);
    }

    #[test]
    fn plan_perfect_prefix_triggers_refresh() {
        // Cache already holds the entire prompt — step back one to refresh.
        let cached = [tok(1), tok(2), tok(3)];
        let prompt = [tok(1), tok(2), tok(3)];
        let positions = [0, 1, 2];
        let plan = plan_prefix_eval(&cached, &prompt, &positions);

        assert_eq!(plan.reused, 3);
        assert_eq!(plan.rebuild_from, 2);
        assert!(plan.refresh_last_token);
        assert!(!plan.cache_diverged);
        assert_eq!(plan.trim_pos, None);
    }

    #[test]
    fn plan_divergence_reports_trim_pos() {
        // Cache 1,2,3,4; prompt 1,2,9 → diverge at index 2 (position 2).
        let cached = [tok(1), tok(2), tok(3), tok(4)];
        let prompt = [tok(1), tok(2), tok(9)];
        let positions = [0, 1, 2, 3];
        let plan = plan_prefix_eval(&cached, &prompt, &positions);

        assert_eq!(plan.reused, 2);
        assert_eq!(plan.rebuild_from, 2);
        assert!(!plan.refresh_last_token);
        assert!(plan.cache_diverged);
        assert_eq!(plan.trim_pos, Some(2));
    }

    #[test]
    fn plan_empty_cache_is_full_rebuild() {
        let cached: &[LlamaToken] = &[];
        let prompt = [tok(1), tok(2)];
        let positions: &[i32] = &[];
        let plan = plan_prefix_eval(cached, &prompt, positions);

        assert_eq!(plan.reused, 0);
        assert_eq!(plan.rebuild_from, 0);
        assert!(!plan.refresh_last_token);
        assert!(!plan.cache_diverged);
        assert_eq!(plan.trim_pos, None);
    }

    #[test]
    fn eval_start_pos_zero_on_empty() {
        assert_eq!(prompt_eval_start_pos(0, &[0, 1, 2], false), 0);
    }

    #[test]
    fn eval_start_pos_continues_after_reused_prefix() {
        // Reused 3 tokens at positions 0,1,2 → next slot is 3.
        assert_eq!(prompt_eval_start_pos(3, &[0, 1, 2], false), 3);
    }

    #[test]
    fn eval_start_pos_refresh_reevaluates_in_place() {
        // Perfect-prefix refresh: re-evaluate the last reused token at its own
        // position (positions[rebuild_from]).
        assert_eq!(prompt_eval_start_pos(2, &[0, 1, 2], true), 2);
    }
}

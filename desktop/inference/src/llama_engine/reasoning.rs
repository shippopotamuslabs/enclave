//! Reasoning (thinking) extraction — splits raw model output into a
//! reasoning/thinking section and the visible answer.
//!
//! Reasoning models (DeepSeek-R1, Qwen3, etc.) wrap their chain-of-thought in
//! `<think>…</think>` or `[THINK]…[/THINK]` envelopes. When tokens are decoded
//! with `special = true` (see `LlamaEngine::complete_turn`) these markers
//! survive into the rendered text, so we can split the stream here in pure
//! Rust without touching the KV cache.
//!
//! Unlike the Android JNI layer (`llama-jni.cpp:1437-1516`), the desktop engine
//! does **not** enforce a hard thinking-token budget by force-injecting a
//! closing tag into the KV cache. Desktop decode is fast enough that an
//! over-long reasoning section is preferable to the cache-corruption risk of a
//! forced close. We only classify what the model produced.

/// A thinking tag form recognized in the raw stream.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum ThinkForm {
    /// `<think>` … `</think>`
    Angle,
    /// `[THINK]` … `[/THINK]`
    Bracket,
}

impl ThinkForm {
    fn open(self) -> &'static str {
        match self {
            ThinkForm::Angle => "<think>",
            ThinkForm::Bracket => "[THINK]",
        }
    }

    fn close(self) -> &'static str {
        match self {
            ThinkForm::Angle => "</think>",
            ThinkForm::Bracket => "[/THINK]",
        }
    }
}

/// Detect the first thinking-tag form present in `s`, preferring the angle
/// form (`<think>`) to match Android's runtime precedence
/// (`llama-jni.cpp:1411-1430`).
fn detect_form(s: &str) -> Option<ThinkForm> {
    let has_angle = s.contains(ThinkForm::Angle.open());
    let has_bracket = s.contains(ThinkForm::Bracket.open());
    match (has_angle, has_bracket) {
        (true, _) => Some(ThinkForm::Angle),
        (false, true) => Some(ThinkForm::Bracket),
        (false, false) => None,
    }
}

/// Split `raw` into `(reasoning, text)`.
///
/// Behavior:
/// - **No thinking tags** → `(None, raw)`.
/// - **Closed block** `<think>…</think>` → the inner text is `reasoning`;
///   text before the opener and after the closer is concatenated into `text`.
/// - **Unclosed block** `<think>…` (the model never emitted the close) →
///   everything after the opener is `reasoning`; `text` is whatever preceded
///   the opener.
/// - **Close tag without an opener** is treated as ordinary text (no split).
///
/// `reasoning` is `Some` only when a real thinking section was found; empty
/// sections (`<think></think>`) still yield `Some("")` so callers can tell a
/// present-but-empty reasoning trace from "model produced no reasoning at all".
pub fn split_reasoning(raw: &str) -> (Option<String>, String) {
    let Some(form) = detect_form(raw) else {
        return (None, raw.to_string());
    };

    let Some(open_idx) = raw.find(form.open()) else {
        // `detect_form` only returns Some when the opener is present, but guard
        // against future tag-detection changes.
        return (None, raw.to_string());
    };

    let prefix = &raw[..open_idx];
    let after_open = &raw[open_idx + form.open().len()..];

    match after_open.find(form.close()) {
        Some(close_inner_idx) => {
            let reasoning = &after_open[..close_inner_idx];
            let suffix = &after_open[close_inner_idx + form.close().len()..];
            let text = format!("{prefix}{suffix}");
            (Some(reasoning.to_string()), text)
        }
        None => {
            // Opener with no close: the rest of the stream is reasoning.
            (Some(after_open.to_string()), prefix.to_string())
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn no_tags_returns_all_text() {
        let (reasoning, text) = split_reasoning("just a normal answer");
        assert_eq!(reasoning, None);
        assert_eq!(text, "just a normal answer");
    }

    #[test]
    fn empty_input_returns_empty() {
        let (reasoning, text) = split_reasoning("");
        assert_eq!(reasoning, None);
        assert_eq!(text, "");
    }

    #[test]
    fn angle_closed_block_splits_inner() {
        let raw = "before<think>secret thoughts</think>after";
        let (reasoning, text) = split_reasoning(raw);
        assert_eq!(reasoning.as_deref(), Some("secret thoughts"));
        assert_eq!(text, "beforeafter");
    }

    #[test]
    fn bracket_closed_block_splits_inner() {
        let raw = "before[THINK]secret thoughts[/THINK]after";
        let (reasoning, text) = split_reasoning(raw);
        assert_eq!(reasoning.as_deref(), Some("secret thoughts"));
        assert_eq!(text, "beforeafter");
    }

    #[test]
    fn angle_form_preferred_when_both_present() {
        // If both markers somehow appear, the angle form wins (Android parity).
        let raw = "<think>angle reasoning</think>[THINK]bracket[/THINK]";
        let (reasoning, text) = split_reasoning(raw);
        assert_eq!(reasoning.as_deref(), Some("angle reasoning"));
        assert_eq!(text, "[THINK]bracket[/THINK]");
    }

    #[test]
    fn unclosed_block_treats_rest_as_reasoning() {
        let raw = "preamble<think>the model rambled on forever";
        let (reasoning, text) = split_reasoning(raw);
        assert_eq!(reasoning.as_deref(), Some("the model rambled on forever"));
        assert_eq!(text, "preamble");
    }

    #[test]
    fn empty_block_yields_some_empty_reasoning() {
        // Distinguish present-but-empty from no-reasoning-at-all.
        let (reasoning, text) = split_reasoning("<think></think>answer");
        assert_eq!(reasoning.as_deref(), Some(""));
        assert_eq!(text, "answer");
    }

    #[test]
    fn block_at_start_has_empty_prefix() {
        let (reasoning, text) = split_reasoning("<think>r</think>visible");
        assert_eq!(reasoning.as_deref(), Some("r"));
        assert_eq!(text, "visible");
    }

    #[test]
    fn block_at_end_has_empty_suffix() {
        let (reasoning, text) = split_reasoning("visible<think>r</think>");
        assert_eq!(reasoning.as_deref(), Some("r"));
        assert_eq!(text, "visible");
    }

    #[test]
    fn stray_close_tag_treated_as_text() {
        // A closer with no opener should not trigger a split.
        let raw = "an answer with </think> in the middle";
        let (reasoning, text) = split_reasoning(raw);
        assert_eq!(reasoning, None);
        assert_eq!(text, raw);
    }
}

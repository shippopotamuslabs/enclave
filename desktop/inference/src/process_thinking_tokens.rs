//! Streaming parser for mixed visible assistant text and hidden thinking text.
//!
//! Port of Android's `ProcessThinkingTokensUseCase` — splits `<think>`
//! / `[THINK]` envelopes from visible answer text across chunk boundaries.

const START_TOKENS: [&str; 2] = ["<think>", "[THINK]"];
const END_TOKENS: [&str; 2] = ["</think>", "[/THINK]"];

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SegmentKind {
    Visible,
    Thinking,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct EmittedSegment {
    pub kind: SegmentKind,
    pub text: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ThinkingParseState {
    pub is_thinking: bool,
    pub buffer: String,
    pub emitted_segments: Vec<EmittedSegment>,
    pub accumulated_thinking_text: String,
    pub accumulated_visible_text: String,
}

/// Process a newly arrived token piece against the parser state.
pub fn process_thinking_tokens(
    current_buffer: &str,
    new_chunk: &str,
    is_thinking: bool,
    accumulated_thinking_text: &str,
    accumulated_visible_text: &str,
) -> ThinkingParseState {
    let mut buffer = format!("{current_buffer}{new_chunk}");
    let mut thinking = is_thinking;
    let mut emitted = Vec::new();

    while !buffer.is_empty() {
        let (start_idx, start_token_idx) = earliest_token(&buffer, START_TOKENS);
        let (end_idx, end_token_idx) = earliest_token(&buffer, END_TOKENS);
        let start_token = START_TOKENS[start_token_idx];
        let end_token = END_TOKENS[end_token_idx];

        match (start_idx, end_idx) {
            (Some(start_idx), Some(end_idx)) if start_idx < end_idx => {
                if start_idx > 0 {
                    emitted.push(emit_segment(thinking, &buffer[..start_idx]));
                }
                thinking = true;
                buffer = buffer[start_idx + start_token.len()..].to_string();
            }
            (Some(_), Some(end_idx)) | (None, Some(end_idx)) => {
                if end_idx > 0 {
                    emitted.push(emit_segment(thinking, &buffer[..end_idx]));
                }
                thinking = false;
                buffer = buffer[end_idx + end_token.len()..].to_string();
            }
            (Some(start_idx), None) => {
                if start_idx > 0 {
                    emitted.push(emit_segment(thinking, &buffer[..start_idx]));
                }
                thinking = true;
                buffer = buffer[start_idx + start_token.len()..].to_string();
            }
            (None, None) => {
                let suffix_to_keep = longest_possible_token_prefix_suffix(&buffer);
                let safe_emit_len = buffer.len().saturating_sub(suffix_to_keep.len());
                if safe_emit_len > 0 {
                    emitted.push(emit_segment(thinking, &buffer[..safe_emit_len]));
                    buffer = buffer[safe_emit_len..].to_string();
                } else {
                    break;
                }
            }
        }
    }

    let merged = merge_adjacent(emitted);
    let new_accumulated_thinking = merged
        .iter()
        .filter(|segment| segment.kind == SegmentKind::Thinking)
        .fold(accumulated_thinking_text.to_string(), |mut acc, segment| {
            acc.push_str(&segment.text);
            acc
        });
    let new_accumulated_visible = merged
        .iter()
        .filter(|segment| segment.kind == SegmentKind::Visible)
        .fold(accumulated_visible_text.to_string(), |mut acc, segment| {
            acc.push_str(&segment.text);
            acc
        });

    ThinkingParseState {
        is_thinking: thinking,
        buffer,
        emitted_segments: merged,
        accumulated_thinking_text: new_accumulated_thinking,
        accumulated_visible_text: new_accumulated_visible,
    }
}

fn emit_segment(in_thinking: bool, text: &str) -> EmittedSegment {
    EmittedSegment {
        kind: if in_thinking {
            SegmentKind::Thinking
        } else {
            SegmentKind::Visible
        },
        text: text.to_string(),
    }
}

fn earliest_token(text: &str, tokens: [&str; 2]) -> (Option<usize>, usize) {
    let mut best: Option<(usize, usize)> = None;
    for (token_idx, token) in tokens.iter().enumerate() {
        if let Some(idx) = text.find(token) {
            if best.is_none_or(|(best_idx, _)| idx < best_idx) {
                best = Some((idx, token_idx));
            }
        }
    }
    match best {
        Some((idx, token_idx)) => (Some(idx), token_idx),
        None => (None, 0),
    }
}

fn all_tokens() -> Vec<&'static str> {
    START_TOKENS.into_iter().chain(END_TOKENS).collect()
}

fn longest_possible_token_prefix_suffix(text: &str) -> &str {
    let max_token_len = all_tokens()
        .iter()
        .map(|token| token.len())
        .max()
        .unwrap_or(0);
    let start = text.len().saturating_sub(max_token_len.saturating_sub(1));
    for i in start..text.len() {
        let suffix = &text[i..];
        if all_tokens().iter().any(|token| token.starts_with(suffix)) {
            return suffix;
        }
    }
    ""
}

fn merge_adjacent(segments: Vec<EmittedSegment>) -> Vec<EmittedSegment> {
    let mut merged: Vec<EmittedSegment> = Vec::new();
    for segment in segments {
        if let Some(last) = merged.last_mut() {
            if last.kind == segment.kind {
                last.text.push_str(&segment.text);
                continue;
            }
        }
        merged.push(segment);
    }
    merged
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn start_token_enters_thinking_mode() {
        let state = process_thinking_tokens("", "<think>", false, "", "");
        assert!(state.is_thinking);
    }

    #[test]
    fn complete_thinking_block_emits_thinking_content() {
        let state = process_thinking_tokens("", "<think>thinking content</think>", false, "", "");
        assert!(!state.is_thinking);
        assert_eq!(state.accumulated_thinking_text, "thinking content");
    }

    #[test]
    fn visible_text_after_thinking_is_emitted() {
        let state = process_thinking_tokens("", "<think>think</think>Hello", false, "", "");
        assert_eq!(state.accumulated_thinking_text, "think");
        assert_eq!(state.accumulated_visible_text, "Hello");
    }

    #[test]
    fn split_chunks_across_tag_boundary() {
        let first = process_thinking_tokens("", "Hello <thi", false, "", "");
        let second = process_thinking_tokens(
            &first.buffer,
            "nk>reasoning</th",
            first.is_thinking,
            &first.accumulated_thinking_text,
            &first.accumulated_visible_text,
        );
        let third = process_thinking_tokens(
            &second.buffer,
            "ink> world",
            second.is_thinking,
            &second.accumulated_thinking_text,
            &second.accumulated_visible_text,
        );
        assert_eq!(third.accumulated_visible_text, "Hello  world");
        assert_eq!(third.accumulated_thinking_text, "reasoning");
    }
}

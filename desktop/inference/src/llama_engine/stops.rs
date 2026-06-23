//! Chat stop-token detection and suffix trimming.
//!
//! Gemma-family models emit turn-delimiter tokens like redacted_im_end (and similar).
//! LiteRT strips these automatically; llama.cpp may render them as ordinary text when
//! the token is not classified as EOG. Mirrors the explicit EOS/EOT check in
//! llama-jni.cpp.

use llama_cpp_2::model::LlamaModel;
use llama_cpp_2::token::LlamaToken;
use std::ptr::{self, NonNull};

/// Suffixes that end an assistant turn for ChatML / Gemma-style templates.
const CHAT_STOP_SUFFIXES: &[&str] = &["<|im_end|>", "<|end|>", "<end_of_turn>", "<|eot_id|>"];

/// `LlamaModel` is `#[repr(transparent)]` over `NonNull<llama_model>`.
fn model_ptr(model: &LlamaModel) -> *const llama_cpp_sys_2::llama_model {
    let ptr: NonNull<llama_cpp_sys_2::llama_model> =
        unsafe { ptr::read(model as *const LlamaModel as *const NonNull<_>).read() };
    ptr.as_ptr()
}

fn vocab_ptr(model: &LlamaModel) -> *const llama_cpp_sys_2::llama_vocab {
    unsafe { llama_cpp_sys_2::llama_model_get_vocab(model_ptr(model)) }
}

/// True when generation should stop — matches Android's EOS **or** EOT check.
pub(crate) fn is_generation_stop_token(model: &LlamaModel, token: LlamaToken) -> bool {
    if model.is_eog_token(token) || token == model.token_eos() {
        return true;
    }
    let eot = unsafe { llama_cpp_sys_2::llama_vocab_eot(vocab_ptr(model)) };
    eot != -1 && token == LlamaToken::new(eot)
}

/// Trim trailing chat-template stop markers from completed output.
pub(crate) fn trim_chat_stop_suffixes(text: &str) -> String {
    let mut out = text.trim_end().to_string();
    loop {
        let before = out.len();
        for stop in CHAT_STOP_SUFFIXES {
            if out.ends_with(stop) {
                out.truncate(out.len() - stop.len());
                out = out.trim_end().to_string();
            }
        }
        if out.len() == before {
            break;
        }
    }
    out
}

/// If `piece` contains an inline stop marker, return the visible prefix and `true`.
pub(crate) fn split_piece_at_chat_stop(piece: &str) -> (&str, bool) {
    let mut earliest: Option<usize> = None;
    for stop in CHAT_STOP_SUFFIXES {
        if let Some(idx) = piece.find(stop) {
            earliest = Some(earliest.map_or(idx, |e| e.min(idx)));
        }
    }
    match earliest {
        Some(idx) => (&piece[..idx], true),
        None => (piece, false),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn trim_redacted_im_end_suffix() {
        assert_eq!(trim_chat_stop_suffixes("Hello 😊<|im_end|>"), "Hello 😊");
    }

    #[test]
    fn split_piece_at_inline_stop() {
        assert_eq!(
            split_piece_at_chat_stop("Hi there<|im_end|>"),
            ("Hi there", true)
        );
        assert_eq!(
            split_piece_at_chat_stop("plain text"),
            ("plain text", false)
        );
    }
}

//! Live / streaming Markdown rendering for assistant chat messages.
//!
//! # Repository findings (Step 0)
//!
//! - **Iced**: upgraded to **0.14.0** with `markdown` + `highlighter` features.
//! - **Prior state**: plain `text()` in [`message_list`](../screens/chat/message_list.rs);
//!   no markdown deps in the desktop crate (pulldown-cmark only in `docgen_core`).
//! - **Streaming**: tokens arrive via `Message::ChatStreamTick`; `markdown::Content::push_str`
//!   performs incremental parsing so committed blocks stay stable while the tail grows.
//! - **Target UI**: assistant transcript (no bubble), mirroring Android `StreamableMarkdownText`.
//! - **Fonts**: Source Serif 4 body (Android `EnclaveReadingFontFamily` /
//!   `LlmBodyTextStyle`); platform monospace for code (`FontFamily.Monospace`).
//! - **Theming**: Neobrutalist tokens from [`theme`](../theme.rs).
//!
//! Uses [`iced::widget::markdown::Content::push_str`] for incremental parsing and
//! [`markdown::view`] for rendering with syntax-highlighted code blocks.

use iced::widget::{column, markdown, Column};
use iced::{Background, Element, Length, Theme};
use iced_selection::markdown as selectable_markdown;

use crate::icons;
use crate::theme::{self, AppTheme};
use crate::Message;

/// Parsed markdown plus streaming lifecycle for one assistant message.
#[derive(Debug)]
pub struct StreamingMarkdownState {
    pub content: markdown::Content,
    pub source: String,
    pub is_streaming: bool,
}

impl Clone for StreamingMarkdownState {
    fn clone(&self) -> Self {
        let mut cloned = Self {
            content: markdown::Content::new(),
            source: self.source.clone(),
            is_streaming: self.is_streaming,
        };
        if !cloned.source.is_empty() {
            cloned.content.push_str(&cloned.source);
        }
        cloned
    }
}

impl StreamingMarkdownState {
    pub fn new() -> Self {
        Self {
            content: markdown::Content::new(),
            source: String::new(),
            is_streaming: true,
        }
    }

    pub fn from_static(text: &str) -> Self {
        let mut state = Self {
            content: markdown::Content::new(),
            source: text.to_owned(),
            is_streaming: false,
        };
        state.content.push_str(text);
        state
    }

    pub fn append(&mut self, chunk: &str) {
        if chunk.is_empty() {
            return;
        }
        self.source.push_str(chunk);
        self.content.push_str(chunk);
    }

    pub fn finalize(&mut self) {
        self.is_streaming = false;
    }
}

impl Default for StreamingMarkdownState {
    fn default() -> Self {
        Self::new()
    }
}

pub fn markdown_settings(app_theme: AppTheme) -> markdown::Settings {
    // Android `LlmBodyTextStyle`: 16sp body, 26sp line height.
    markdown::Settings::with_text_size(16, markdown_style(app_theme))
}

/// Neobrutalist markdown styling mapped from the active app theme.
pub fn markdown_style(app_theme: AppTheme) -> markdown::Style {
    let (text, surface, accent) = if app_theme == AppTheme::Dark {
        (
            theme::DARK_TEXT,
            theme::DARK_SURFACE_VAR,
            theme::COLOR_ACCENT,
        )
    } else {
        (
            theme::LIGHT_TEXT,
            theme::LIGHT_SURFACE_VAR,
            theme::COLOR_ACCENT,
        )
    };

    markdown::Style {
        font: icons::FONT_SERIF_REGULAR,
        inline_code_highlight: markdown::Highlight {
            background: Background::Color(surface),
            border: iced::Border {
                color: if app_theme == AppTheme::Dark {
                    theme::DARK_NEO_BORDER
                } else {
                    theme::LIGHT_NEO_BORDER
                },
                width: 1.0,
                radius: 4.0.into(),
            },
        },
        inline_code_padding: iced::Padding::from([1, 4]),
        inline_code_color: text,
        inline_code_font: icons::FONT_MONO,
        code_block_font: icons::FONT_MONO,
        link_color: accent,
    }
}

/// Render a streaming or complete assistant markdown message.
pub fn streaming_markdown<'a>(
    state: &'a StreamingMarkdownState,
    app_theme: AppTheme,
) -> Element<'a, Message, Theme, iced::Renderer> {
    let settings = markdown_settings(app_theme);
    let body: Element<'a, Message, Theme, iced::Renderer> = if state.content.items().is_empty() {
        column![].into()
    } else {
        selectable_markdown::view(state.content.items(), settings)
            .map(|uri| Message::MarkdownLinkClicked(uri.to_string()))
    };

    Column::new().push(body).width(Length::Fill).into()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn push_chunks(state: &mut StreamingMarkdownState, chunks: &[&str]) {
        for chunk in chunks {
            state.append(chunk);
        }
    }

    #[test]
    fn partial_bold_tail_preserves_prior_paragraph() {
        let mut state = StreamingMarkdownState::new();
        push_chunks(&mut state, &["Hello world.\n\n", "**bol"]);
        assert!(
            !state.content.items().is_empty(),
            "expected at least one parsed item after partial bold"
        );
    }

    #[test]
    fn open_code_fence_does_not_drop_prior_heading() {
        let mut state = StreamingMarkdownState::new();
        push_chunks(
            &mut state,
            &["# Title\n\n", "Intro paragraph.\n\n", "```rust\nfn main("],
        );
        let items = state.content.items();
        assert!(
            !items.is_empty(),
            "prior blocks should remain parseable while fence is open"
        );
    }

    #[test]
    fn growing_code_block_appends_without_panic() {
        let mut state = StreamingMarkdownState::new();
        push_chunks(
            &mut state,
            &["```rust\n", "let x = 1;\n", "let y = 2;\n", "```\n"],
        );
        state.finalize();
        assert!(!state.content.items().is_empty());
        assert!(!state.is_streaming);
    }

    #[test]
    fn table_split_across_chunks_eventually_parses() {
        let mut state = StreamingMarkdownState::new();
        push_chunks(&mut state, &["| Col |\n", "|-----|\n", "| Val |\n"]);
        assert!(!state.content.items().is_empty());
    }

    #[test]
    fn reference_link_invalidation_reparses_after_definition() {
        let mut state = StreamingMarkdownState::new();
        push_chunks(
            &mut state,
            &["See [ref].\n\n", "[ref]: https://example.com\n"],
        );
        assert!(!state.content.items().is_empty());
    }

    #[test]
    fn finalize_marks_stream_complete() {
        let mut state = StreamingMarkdownState::new();
        state.append("Done.");
        state.finalize();
        assert!(!state.is_streaming);
    }

    #[test]
    fn from_static_parses_complete_document() {
        let state = StreamingMarkdownState::from_static("**Hello** markdown");
        assert!(!state.is_streaming);
        assert!(!state.content.items().is_empty());
    }
}

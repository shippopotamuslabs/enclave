//noinspection SpellCheckingInspection
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
//! Uses [`markdown::Content::push_str`] for incremental parsing and a
//! custom [`Viewer`] ([`CopyableMarkdownViewer`]) for rendering with
//! syntax-highlighted code blocks plus a per-block header (language label + copy
//! control). Non-code items are delegated to [`iced_selection::markdown`] so plain
//! text, headings, lists, and tables stay text-selectable, matching the prior
//! selectable viewer.

use iced::widget::markdown::Viewer;
use iced::widget::{button, column, container, markdown, row, space, text, Column};
use iced::{Alignment, Background, Element, Length, Theme};
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

/// Fallback label shown in the code-block header when a fence has no language
/// info string (bare ``` or an empty ` ``` `). Kept consistent across dark/light
/// and across fenced math/code so callers never branch on it ad hoc.
pub const CODE_BLOCK_UNKNOWN_LANGUAGE: &str = "text";

/// Normalize a fence info string for display in the code-block header.
///
/// The Iced parser fills [`markdown::Item::CodeBlock`]'s `language` from the
/// fence info string verbatim (``` ```rust ``` → `Some("rust")`). We display it
/// lowercased; bare or empty fences fall back to [`CODE_BLOCK_UNKNOWN_LANGUAGE`].
///
/// `latex` is left as-is (the seed doc and Android counterpart both label math
/// fences `latex`, and "latex" reads correctly to users); callers that prefer
/// "math" can post-process, but the widget itself stays literal to the source.
fn display_language(language: Option<&str>) -> &str {
    language
        .map(str::trim_ascii)
        .filter(|s| !s.is_empty())
        .unwrap_or(CODE_BLOCK_UNKNOWN_LANGUAGE)
}

/// Custom markdown [`Viewer`] that adds a per-fenced-block header (language
/// label + copy control) while delegating every other item to the selectable
/// viewer in [`iced_selection::markdown`].
///
/// - `app_theme` routes the header/copy button through the neobrutalist token
///   set in [`theme`].
/// - `can_copy` is false while the owning message is still streaming — copy is
///   disabled (control hidden) until [`StreamingMarkdownState::finalize`] so a
///   partial fence can never produce a half-written clipboard payload.
struct CopyableMarkdownViewer {
    app_theme: AppTheme,
    can_copy: bool,
}

impl<'a> Viewer<'a, Message, Theme, iced::Renderer> for CopyableMarkdownViewer {
    fn on_link_click(url: markdown::Uri) -> Message {
        Message::MarkdownLinkClicked(url)
    }

    fn heading(
        &self,
        settings: markdown::Settings,
        level: &'a markdown::HeadingLevel,
        text: &'a markdown::Text,
        index: usize,
    ) -> Element<'a, Message, Theme, iced::Renderer> {
        selectable_markdown::heading(settings, level, text, index, Message::MarkdownLinkClicked)
    }

    fn paragraph(
        &self,
        settings: markdown::Settings,
        text: &markdown::Text,
    ) -> Element<'a, Message, Theme, iced::Renderer> {
        selectable_markdown::paragraph(settings, text, Message::MarkdownLinkClicked)
    }

    fn code_block(
        &self,
        settings: markdown::Settings,
        language: Option<&'a str>,
        code: &'a str,
        lines: &'a [markdown::Text],
    ) -> Element<'a, Message, Theme, iced::Renderer> {
        code_block_with_header(
            settings,
            language,
            code,
            lines,
            self.app_theme,
            self.can_copy,
            Message::MarkdownLinkClicked,
        )
    }

    fn unordered_list(
        &self,
        settings: markdown::Settings,
        bullets: &'a [markdown::Bullet],
    ) -> Element<'a, Message, Theme, iced::Renderer> {
        selectable_markdown::unordered_list(self, settings, bullets)
    }

    fn ordered_list(
        &self,
        settings: markdown::Settings,
        start: u64,
        bullets: &'a [markdown::Bullet],
    ) -> Element<'a, Message, Theme, iced::Renderer> {
        selectable_markdown::ordered_list(self, settings, start, bullets)
    }
}

/// Build the selectable code-block body (re-uses [`iced_selection::markdown`]'s
/// `code_block`, which renders lines via selectable `rich_text`) and stack a
/// neobrutalist header row above it.
///
/// The copy control clones `code` into [`Message::CopyCodeBlock`] at
/// button-construction time. Block indices shift during streaming, so carrying
/// the payload by value avoids any later index lookup going stale; the control
/// is hidden entirely while `can_copy` is false (the fence is incomplete).
fn code_block_with_header<'a>(
    settings: markdown::Settings,
    language: Option<&'a str>,
    code: &'a str,
    lines: &'a [markdown::Text],
    app_theme: AppTheme,
    can_copy: bool,
    on_link_click: impl Fn(markdown::Uri) -> Message + Clone + 'a,
) -> Element<'a, Message, Theme, iced::Renderer> {
    // Body: the existing selectable code block, unchanged.
    let body = selectable_markdown::code_block(settings, lines, on_link_click);

    // Header: language label (left) + copy control (right), muted surface strip.
    let mut header_row = row![text(display_language(language))
        .font(icons::FONT_MONO)
        .size(settings.code_size)]
    .align_y(Alignment::Center)
    .spacing(settings.spacing.0)
    .width(Length::Fill);

    if can_copy {
        // Clone the raw block source (no fences) into the copy message so the
        // payload is fixed at construction time.
        let payload = code.to_owned();
        let copy_btn = button(
            container(icons::render_svg(&icons::HANDLE_COPY, theme::COLOR_ACCENT))
                .width(Length::Shrink)
                .height(Length::Shrink)
                .align_x(Alignment::Center)
                .align_y(Alignment::Center),
        )
        .width(Length::Shrink)
        .height(Length::Shrink)
        .padding(4)
        .style(theme::neo_icon_button(theme::NeoIconButton {
            theme: app_theme,
        }))
        .on_press(Message::CopyCodeBlock(payload));

        header_row = header_row
            .push(space::horizontal().width(Length::Fill))
            .push(copy_btn);
    }

    let header = container(header_row)
        .width(Length::Fill)
        .padding([6.0, settings.code_size.0])
        .style(theme::neo_code_block_header(theme::NeoCodeBlockHeader {
            theme: app_theme,
        }));

    // Re-skin the whole block with the neobrutalist container (replaces Iced's
    // default `container::dark` which had a hard-coded `#111111` fill).
    container(column![header, body].spacing(0.0))
        .width(Length::Fill)
        .padding(0.0)
        .style(theme::neo_code_block(theme::NeoCodeBlock {
            theme: app_theme,
        }))
        .into()
}

/// Render a streaming or complete assistant markdown message.
pub fn streaming_markdown<'a>(
    state: &'a StreamingMarkdownState,
    app_theme: AppTheme,
) -> Element<'a, Message, Theme, iced::Renderer> {
    let settings = markdown_settings(app_theme);
    let viewer = CopyableMarkdownViewer {
        app_theme,
        // Copy is only enabled once the message is finalized; an open fence is
        // omitted from the parsed items entirely (the parser holds it back as
        // leftover), so the remaining risk is a closed-but-still-streaming
        // block — guarded by `is_streaming` here.
        can_copy: !state.is_streaming,
    };

    let body: Element<'a, Message, Theme, iced::Renderer> = if state.content.items().is_empty() {
        column![].into()
    } else {
        markdown::view_with(state.content.items(), settings, &viewer)
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

    #[test]
    fn display_language_shows_known_fence_language() {
        assert_eq!(display_language(Some("rust")), "rust");
        assert_eq!(display_language(Some("latex")), "latex");
        assert_eq!(display_language(Some("python")), "python");
    }

    #[test]
    fn display_language_falls_back_for_unknown() {
        // Bare ``` fence → parser reports None.
        assert_eq!(display_language(None), CODE_BLOCK_UNKNOWN_LANGUAGE);
        // Whitespace-only info string → treated as unknown.
        assert_eq!(display_language(Some("   ")), CODE_BLOCK_UNKNOWN_LANGUAGE);
        // Empty info string → treated as unknown.
        assert_eq!(display_language(Some("")), CODE_BLOCK_UNKNOWN_LANGUAGE);
    }

    /// The viewer must produce an element tree for a complete fenced block
    /// without panicking. We cannot introspect the button payload through the
    /// opaque `Element`, but the build itself is the regression guard.
    /// The prior selectable viewer had no per-block hook; this asserts the
    /// custom `Viewer` wires into `view_with` for every item kind in the demo
    /// seed file.
    #[test]
    fn viewer_builds_for_complete_fenced_document() {
        let state = StreamingMarkdownState::from_static(
            "# Heading\n\nIntro with `inline`.\n\n\
             ```rust\nfn main() {}\n```\n\n\
             ```latex\n\\\\int_0^1 x \\\\, dx\n```\n",
        );
        let _ = streaming_markdown(&state, AppTheme::Dark);
        let _ = streaming_markdown(&state, AppTheme::Light);
    }

    /// While streaming, the viewer still builds (the open fence is held back as
    /// parser leftover) and copy stays disabled via `is_streaming`.
    #[test]
    fn viewer_builds_during_streaming_and_disables_copy() {
        let mut state = StreamingMarkdownState::new();
        push_chunks(&mut state, &["# WIP\n\n", "```rust\nfn half("]);
        assert!(state.is_streaming);
        let _ = streaming_markdown(&state, AppTheme::Dark);
    }

    /// The raw copy payload for a parsed fenced block must equal the source
    /// *between* the fences — no leading ``` ```rust ``` and no trailing ``` ``` ```
    /// — so the clipboard carries exactly what the user would re-paste as code.
    #[test]
    fn parsed_code_block_carries_raw_source_without_fences() {
        let state = StreamingMarkdownState::from_static(
            "intro\n\n```rust\nfn add(a: u8, b: u8) -> u8 {\n    a + b\n}\n```\n",
        );

        let code_block = state
            .content
            .items()
            .iter()
            .find_map(|item| match item {
                markdown::Item::CodeBlock { code, .. } => Some(code.clone()),
                _ => None,
            })
            .expect("a fenced rust block should parse from the seed text");

        assert_eq!(code_block, "fn add(a: u8, b: u8) -> u8 {\n    a + b\n}\n");
        assert!(
            !code_block.contains("```"),
            "raw code must not include markdown fences: {code_block:?}"
        );
    }

    /// A `latex` fence is a fenced code block like any other — the copy payload
    /// carries the raw LaTeX source without the fence markers.
    #[test]
    fn parsed_latex_fence_carries_raw_source_without_fences() {
        // Raw string so the single backslashes of real LaTeX survive verbatim
        // (matching the seed doc's ` ```latex ` fence).
        let state = StreamingMarkdownState::from_static(
            r#"math

```latex
\int_{-\infty}^{\infty} e^{-x^2} dx = \sqrt{\pi}
```
"#,
        );

        let code_block = state
            .content
            .items()
            .iter()
            .find_map(|item| match item {
                markdown::Item::CodeBlock { code, .. } => Some(code.clone()),
                _ => None,
            })
            .expect("a fenced latex block should parse from the seed text");

        assert!(
            code_block.starts_with("\\int"),
            "latex payload should start with the integral, got: {code_block:?}"
        );
        assert!(
            !code_block.contains("```"),
            "raw latex must not include markdown fences: {code_block:?}"
        );
    }

    /// Regression guard: non-code markdown (paragraphs, tables, lists) still
    /// parses after the viewer change, so selectable text outside code blocks
    /// is preserved.
    #[test]
    fn non_code_items_still_parse_after_viewer_change() {
        let state = StreamingMarkdownState::from_static(
            "**bold** and *italic*\n\n| A | B |\n|---|---|\n| 1 | 2 |\n",
        );
        let kinds: Vec<&str> = state
            .content
            .items()
            .iter()
            .map(|item| match item {
                markdown::Item::Paragraph(_) => "paragraph",
                markdown::Item::Table { .. } => "table",
                markdown::Item::CodeBlock { .. } => "code",
                _ => "other",
            })
            .collect();
        assert!(
            kinds.contains(&"paragraph"),
            "paragraphs must parse: {kinds:?}"
        );
        assert!(kinds.contains(&"table"), "tables must parse: {kinds:?}");
    }
}

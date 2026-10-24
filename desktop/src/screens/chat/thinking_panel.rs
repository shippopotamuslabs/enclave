//! Right-side thinking trace panel for the desktop chat screen.
//!
//! Desktop analogue of Android `DetailBottomSheet` / `ThinkingDetailsBottomSheet`:
//! a side panel that slides out from the right edge of the chat pane (not a
//! bottom sheet) to show the model's reasoning trace while streaming or after
//! the turn completes.

use iced::widget::{button, column, container, row, space, text};
use iced::{alignment, Element, Length, Padding, Theme};
use iced_selection::text::Style as SelectableTextStyle;

use super::state::{TurnIndicator, TurnThinkingState};
use crate::icons;
use crate::screens::layout::neo_scrollable;
use crate::theme::{self, AppTheme};
use crate::Message;

const PANEL_WIDTH: f32 = 360.0;

pub fn view(
    turn: &TurnThinkingState,
    app_theme: AppTheme,
) -> Element<'_, Message, Theme, iced::Renderer> {
    let is_dark = app_theme == AppTheme::Dark;
    let text_color = if is_dark {
        theme::DARK_TEXT
    } else {
        theme::LIGHT_TEXT
    };
    let muted = if is_dark {
        theme::DARK_TEXT_MUTED
    } else {
        theme::LIGHT_TEXT_MUTED
    };

    let title = match turn.indicator {
        TurnIndicator::Thinking | TurnIndicator::Processing if turn.is_thinking_streaming => {
            if turn.thinking_elapsed_secs > 0 {
                format!(
                    "Thinking... • {}",
                    TurnThinkingState::format_duration(turn.thinking_elapsed_secs)
                )
            } else {
                "Thinking...".to_string()
            }
        }
        TurnIndicator::Complete {
            thinking_duration_secs,
        } => format!(
            "Thought for {}",
            TurnThinkingState::format_duration(thinking_duration_secs)
        ),
        _ if turn.has_thinking_trace() => format!(
            "Thought for {}",
            TurnThinkingState::format_duration(turn.thinking_elapsed_secs.max(1))
        ),
        _ => "Thinking".to_string(),
    };

    let header = row![
        text(title)
            .font(icons::FONT_BOLD)
            .size(16)
            .color(theme::COLOR_ACCENT),
        space::horizontal().width(Length::Fill),
        button(
            container(icons::render_svg(&icons::HANDLE_CLOSE, muted))
                .width(Length::Fill)
                .height(Length::Fill)
                .align_x(alignment::Horizontal::Center)
                .align_y(alignment::Vertical::Center),
        )
        .padding(4)
        .width(Length::Fixed(28.0))
        .height(Length::Fixed(28.0))
        .style(theme::neo_icon_button(theme::NeoIconButton {
            theme: app_theme
        }))
        .on_press(Message::ChatToggleThinkingPanel),
    ]
    .align_y(alignment::Alignment::Center)
    .padding(Padding {
        top: 16.0,
        right: 16.0,
        bottom: 8.0,
        left: 16.0,
    })
    .width(Length::Fill);

    let body_text = if turn.thinking_raw.is_empty() {
        "No thinking content available".to_string()
    } else {
        turn.thinking_raw.clone()
    };

    let body = iced_selection::text(body_text)
        .font(icons::FONT_REGULAR)
        .size(14)
        .width(Length::Fill)
        .style(move |theme: &Theme| SelectableTextStyle {
            color: Some(text_color),
            selection: theme.extended_palette().primary.weak.color,
        });

    let scroll_body = neo_scrollable(
        container(body).padding(Padding {
            top: 0.0,
            right: 16.0,
            bottom: 16.0,
            left: 16.0,
        }),
        app_theme,
    );

    let panel = column![header, scroll_body]
        .width(Length::Fill)
        .height(Length::Fill);

    container(panel)
        .width(Length::Fixed(PANEL_WIDTH))
        .height(Length::Fill)
        .style(theme::neo_bottom_sheet(theme::NeoBottomSheet {
            theme: app_theme,
        }))
        .into()
}

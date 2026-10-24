//! Live thinking / processing indicators for the active assistant turn.
//!
//! Desktop analogue of Android `ThinkingIndicator`, `ProcessingIndicator`, and
//! `ThoughtForHeader` in the message list.

use iced::widget::{button, container, row, space, text};
use iced::{alignment, Element, Length, Padding, Theme};

use super::state::{TurnIndicator, TurnThinkingState};
use crate::icons;
use crate::theme::{self, AppTheme};
use crate::Message;

pub fn view(
    turn: &TurnThinkingState,
    app_theme: AppTheme,
) -> Option<Element<'_, Message, Theme, iced::Renderer>> {
    let muted = if app_theme == AppTheme::Dark {
        theme::DARK_TEXT_MUTED
    } else {
        theme::LIGHT_TEXT_MUTED
    };

    match turn.indicator {
        TurnIndicator::None if !turn.has_thinking_trace() => None,
        TurnIndicator::None => Some(thought_for_header(
            turn.thinking_elapsed_secs.max(1),
            turn.thinking_panel_open,
            muted,
            app_theme,
        )),
        TurnIndicator::Thinking => Some(thinking_row(
            if turn.thinking_elapsed_secs > 0 {
                format!(
                    "Thinking... • {}",
                    TurnThinkingState::format_duration(turn.thinking_elapsed_secs)
                )
            } else {
                "Thinking...".to_string()
            },
            turn.thinking_panel_open,
            muted,
            app_theme,
        )),
        TurnIndicator::Processing => Some(processing_row("Processing", muted)),
        TurnIndicator::Complete {
            thinking_duration_secs,
        } => Some(thought_for_header(
            thinking_duration_secs,
            turn.thinking_panel_open,
            muted,
            app_theme,
        )),
    }
}

fn thinking_row(
    label: String,
    panel_open: bool,
    muted: iced::Color,
    app_theme: AppTheme,
) -> Element<'static, Message, Theme, iced::Renderer> {
    status_row_with_chevron(label, muted, app_theme, panel_open)
}

fn thought_for_header(
    duration_secs: u32,
    panel_open: bool,
    muted: iced::Color,
    app_theme: AppTheme,
) -> Element<'static, Message, Theme, iced::Renderer> {
    let label = format!(
        "Thought for {}",
        TurnThinkingState::format_duration(duration_secs)
    );
    status_row_with_chevron(label, muted, app_theme, panel_open)
}

fn processing_row(
    label: &str,
    muted: iced::Color,
) -> Element<'static, Message, Theme, iced::Renderer> {
    row![
        container(text("◉").size(18).color(muted))
            .width(Length::Fixed(46.0))
            .align_x(alignment::Horizontal::Center)
            .align_y(alignment::Vertical::Center),
        text(label.to_string())
            .font(icons::FONT_SEMIBOLD)
            .size(15)
            .color(muted),
    ]
    .align_y(alignment::Alignment::Center)
    .width(Length::Fill)
    .padding(Padding {
        top: 8.0,
        right: 0.0,
        bottom: 8.0,
        left: 0.0,
    })
    .into()
}

fn status_row_with_chevron(
    label: String,
    muted: iced::Color,
    app_theme: AppTheme,
    panel_open: bool,
) -> Element<'static, Message, Theme, iced::Renderer> {
    let chevron = if panel_open {
        &icons::HANDLE_CHEVRON_UP
    } else {
        &icons::HANDLE_CHEVRON_DOWN
    };

    button(
        row![
            container(text("◉").size(18).color(muted))
                .width(Length::Fixed(46.0))
                .align_x(alignment::Horizontal::Center)
                .align_y(alignment::Vertical::Center),
            text(label).font(icons::FONT_SEMIBOLD).size(15).color(muted),
            space::horizontal().width(Length::Fill),
            container(icons::render_svg(chevron, muted))
                .width(Length::Fixed(24.0))
                .height(Length::Fixed(24.0))
                .align_x(alignment::Horizontal::Center)
                .align_y(alignment::Vertical::Center),
        ]
        .align_y(alignment::Alignment::Center)
        .width(Length::Fill)
        .padding(Padding {
            top: 8.0,
            right: 0.0,
            bottom: 8.0,
            left: 0.0,
        }),
    )
    .width(Length::Fill)
    .style(theme::neo_icon_button(theme::NeoIconButton {
        theme: app_theme,
    }))
    .on_press(Message::ChatToggleThinkingPanel)
    .into()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn thinking_indicator_renders_while_streaming() {
        let turn = TurnThinkingState {
            indicator: TurnIndicator::Thinking,
            thinking_raw: "planning".into(),
            thinking_elapsed_secs: 4,
            is_thinking_streaming: true,
            turn_active: true,
            ..TurnThinkingState::default()
        };
        assert!(view(&turn, AppTheme::Dark).is_some());
    }

    #[test]
    fn complete_indicator_renders_thought_header() {
        let turn = TurnThinkingState {
            indicator: TurnIndicator::Complete {
                thinking_duration_secs: 23,
            },
            thinking_raw: "done".into(),
            ..TurnThinkingState::default()
        };
        assert!(view(&turn, AppTheme::Light).is_some());
    }
}

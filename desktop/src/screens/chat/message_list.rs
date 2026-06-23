//noinspection SpellCheckingInspection
use iced::widget::{Column, container, row, space};
use iced::{Element, Length, Padding, Theme};
use iced_selection::text::Style as SelectableTextStyle;

use super::state::{ChatState, MessageSender};
use super::thinking_indicator;
use crate::Message;
use crate::icons;
use crate::screens::layout::neo_scrollable;
use crate::theme::{self, AppTheme};
use crate::widgets::streaming_markdown;

fn selectable_body_style(text_color: iced::Color) -> impl Fn(&Theme) -> SelectableTextStyle + Copy {
    move |theme: &Theme| SelectableTextStyle {
        color: Some(text_color),
        selection: theme.extended_palette().primary.weak.color,
    }
}

pub fn view(state: &ChatState, app_theme: AppTheme) -> Element<'_, Message, Theme, iced::Renderer> {
    // Message Thread (oldest-first; the scrollable auto-sticks to the bottom
    // so the latest message stays visible as new ones arrive).
    let mut message_list = Column::new().spacing(16).width(Length::Fill);

    for msg in &state.messages {
        let is_user = msg.sender == MessageSender::User;

        let entry: Element<'_, Message, Theme, iced::Renderer> = if is_user {
            // User message — dark neobrutalist bubble with light text and a hard
            // offset shadow, mirroring the Android `UserBubble` (dark surface +
            // `onSurface` content + `withShadow = true`). Always rendered with
            // the dark token set so the user's own messages read as solid dark
            // bubbles in both light and dark themes.
            let body = iced_selection::text(&msg.text)
                .font(icons::FONT_REGULAR)
                .size(14)
                .width(Length::Fill)
                .style(selectable_body_style(theme::DARK_TEXT));

            container(body)
                .padding(14)
                .width(Length::Fill)
                .style(theme::neo_container(theme::NeoContainer {
                    is_surface: true,
                    has_shadow: true,
                    theme: AppTheme::Dark,
                }))
                .into()
        } else if let Some(ref markdown) = msg.markdown {
            streaming_markdown::streaming_markdown(markdown, app_theme)
        } else {
            // Assistant message — written straight onto the pane background with
            // no enclosing surface, mirroring the Android `AssistantResponse`
            // (plain text on the canvas, no bubble). Color follows the active
            // theme so it stays legible against either pane background.
            {
                let text_color = if app_theme == AppTheme::Dark {
                    theme::DARK_TEXT
                } else {
                    theme::LIGHT_TEXT
                };
                iced_selection::text(&msg.text)
                    .font(icons::FONT_REGULAR)
                    .size(14)
                    .width(Length::Fill)
                    .style(selectable_body_style(text_color))
                    .into()
            }
        };

        message_list = message_list.push(entry);
    }

    if let Some(indicator) = thinking_indicator::view(&state.turn_thinking, app_theme) {
        message_list = message_list.push(indicator);
    }

    // Center the thread in the same column the input bar occupies
    // (`FillPortion(6)` between two bare `FillPortion(1)` spacers) with the same
    // 24px horizontal padding, so user bubbles span the full width of — and align
    // edge-to-edge with — the input bar below. Bare `horizontal_space` is
    // required for the FillPortion distribution: wrapping it in a container
    // breaks it in iced 0.12 (matching the input-bar pattern in `input_bar.rs`).
    let thread = row![
        space::horizontal().width(Length::FillPortion(1)),
        container(message_list)
            .width(Length::FillPortion(6))
            .padding(Padding {
                top: 20.0,
                right: 24.0,
                bottom: 20.0,
                left: 24.0,
            }),
        space::horizontal().width(Length::FillPortion(1)),
    ]
    .width(Length::Fill);

    neo_scrollable(thread, app_theme).into()
}

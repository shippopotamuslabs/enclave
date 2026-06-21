use iced::widget::{container, horizontal_space, row, text, Column};
use iced::{Element, Length, Theme};

use super::state::{ChatState, MessageSender};
use crate::icons;
use crate::screens::layout::neo_scrollable;
use crate::theme::{self, AppTheme};
use crate::Message;

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
            let body = text(&msg.text)
                .font(icons::FONT_REGULAR)
                .size(14)
                .style(theme::DARK_TEXT);

            container(body)
                .padding(14)
                .width(Length::Fill)
                .style(iced::theme::Container::Custom(Box::new(
                    theme::NeoContainer {
                        is_surface: true,
                        has_shadow: true,
                        theme: AppTheme::Dark,
                    },
                )))
                .into()
        } else {
            // Assistant message — written straight onto the pane background with
            // no enclosing surface, mirroring the Android `AssistantResponse`
            // (plain text on the canvas, no bubble). Color follows the active
            // theme so it stays legible against either pane background.
            text(&msg.text)
                .font(icons::FONT_REGULAR)
                .size(14)
                .width(Length::Fill)
                .style(if app_theme == AppTheme::Dark {
                    theme::DARK_TEXT
                } else {
                    theme::LIGHT_TEXT
                })
                .into()
        };

        message_list = message_list.push(entry);
    }

    // Center the thread in the same column the input bar occupies
    // (`FillPortion(6)` between two bare `FillPortion(1)` spacers) with the same
    // 24px horizontal padding, so user bubbles span the full width of — and align
    // edge-to-edge with — the input bar below. Bare `horizontal_space` is
    // required for the FillPortion distribution: wrapping it in a container
    // breaks it in iced 0.12 (matching the input-bar pattern in `input_bar.rs`).
    let thread = row![
        horizontal_space().width(Length::FillPortion(1)),
        container(message_list)
            .width(Length::FillPortion(6))
            .padding([20, 24, 20, 24]),
        horizontal_space().width(Length::FillPortion(1)),
    ]
    .width(Length::Fill);

    neo_scrollable(thread, app_theme).into()
}

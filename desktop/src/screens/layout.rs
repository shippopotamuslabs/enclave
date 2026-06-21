//! Shared layout helpers for full-screen views and repeated neobrutalist chrome.

use iced::widget::{button, container, scrollable};
use iced::{alignment, Element, Length, Theme};

use crate::icons;
use crate::theme::{self, AppTheme};
use crate::Message;

/// Square 40×40 neobrutalist icon button with hard shadow.
pub fn icon_button<'a>(
    handle: &'a iced::widget::svg::Handle,
    color: iced::Color,
    app_theme: AppTheme,
    on_press: Message,
) -> button::Button<'a, Message, Theme, iced::Renderer> {
    button(
        container(
            icons::render_svg(handle, color)
                .width(Length::Fixed(18.0))
                .height(Length::Fixed(18.0)),
        )
        .width(Length::Fill)
        .height(Length::Fill)
        .align_x(alignment::Horizontal::Center)
        .align_y(alignment::Vertical::Center),
    )
    .padding(8)
    .width(Length::Fixed(40.0))
    .height(Length::Fixed(40.0))
    .style(theme::neo_button(theme::NeoButton {
        is_accent: false,
        has_shadow: true,
        theme: app_theme,
    }))
    .on_press(on_press)
}

/// Full-width dropdown menu row with padded label content.
pub fn dropdown_menu_row<'a>(
    label: impl Into<Element<'a, Message, Theme, iced::Renderer>>,
    app_theme: AppTheme,
    on_press: Message,
) -> button::Button<'a, Message, Theme, iced::Renderer> {
    button(container(label).padding(8))
        .padding(0)
        .width(Length::Fill)
        .style(theme::neo_button(theme::NeoButton {
            is_accent: false,
            has_shadow: false,
            theme: app_theme,
        }))
        .on_press(on_press)
}

/// Scrollable region using the neo scrollable style.
pub fn neo_scrollable<'a>(
    content: impl Into<Element<'a, Message, Theme, iced::Renderer>>,
    app_theme: AppTheme,
) -> scrollable::Scrollable<'a, Message, Theme, iced::Renderer> {
    scrollable(content)
        .height(Length::Fill)
        .style(theme::neo_scrollable(theme::NeoScrollable {
            theme: app_theme,
        }))
}

/// Scrollable body with standard 20px padding around `content`.
pub fn scrollable_padded_body<'a>(
    content: impl Into<Element<'a, Message, Theme, iced::Renderer>>,
    app_theme: AppTheme,
) -> scrollable::Scrollable<'a, Message, Theme, iced::Renderer> {
    neo_scrollable(
        container(content).padding(20).width(Length::Fill),
        app_theme,
    )
}

/// Full-screen canvas shell wrapping a column layout.
pub fn screen_shell<'a>(
    main_layout: impl Into<Element<'a, Message, Theme, iced::Renderer>>,
    app_theme: AppTheme,
) -> Element<'a, Message, Theme, iced::Renderer> {
    container(main_layout)
        .width(Length::Fill)
        .height(Length::Fill)
        .style(theme::neo_container(theme::NeoContainer {
            is_surface: false,
            has_shadow: false,
            theme: app_theme,
        }))
        .into()
}

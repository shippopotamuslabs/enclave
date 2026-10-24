use crate::icons;
use crate::screens::layout::{icon_button, screen_shell, scrollable_padded_body};
use crate::theme::{self, AppTheme};
use crate::Message;
use iced::widget::{button, column, container, row, space, text, Column};
use iced::{alignment, Color, Element, Length, Theme};

pub struct SettingsState {
    pub active_theme: AppTheme,
}

impl SettingsState {
    pub fn new() -> Self {
        Self {
            active_theme: AppTheme::Dark,
        }
    }
}

pub fn view(
    state: &SettingsState,
    app_theme: AppTheme,
) -> Element<'_, Message, Theme, iced::Renderer> {
    let is_dark = app_theme == AppTheme::Dark;

    // --- 1. Settings Top Bar ---
    let header_title = text("Settings")
        .font(icons::FONT_BOLD)
        .size(20)
        .color(if is_dark {
            theme::DARK_TEXT
        } else {
            theme::LIGHT_TEXT
        });

    let text_color = if is_dark {
        theme::DARK_TEXT
    } else {
        theme::LIGHT_TEXT
    };

    let close_btn = icon_button(
        &icons::HANDLE_CLOSE,
        text_color,
        app_theme,
        Message::NavigateBack,
    );

    let top_bar = container(
        row![
            header_title,
            space::horizontal().width(Length::Fill),
            close_btn
        ]
        .padding(10)
        .align_y(alignment::Alignment::Center),
    )
    .width(Length::Fill)
    .style(theme::neo_container(theme::NeoContainer {
        is_surface: true,
        has_shadow: false,
        theme: app_theme,
    }));

    // --- 2. Scrollable Settings Content ---
    let mut settings_list = Column::new().spacing(24);

    // Section Title helper
    let make_section_header = |title: String| {
        text(title)
            .font(icons::FONT_BOLD)
            .size(13)
            .color(theme::COLOR_ACCENT)
    };

    // --- Account Section ---
    // The Google sign-in / profile card moved to the history sidebar footer
    // (tap the card there for the Login/Logout popover). Settings no longer
    // carries account chrome.

    // --- Appearance Theme Selector ---
    let theme_btn = |label: String, target_theme: AppTheme| {
        let is_selected = state.active_theme == target_theme;
        button(
            text(label)
                .font(icons::FONT_BOLD)
                .size(14)
                .align_x(alignment::Horizontal::Center)
                .color(if is_selected {
                    Color::BLACK
                } else if is_dark {
                    theme::DARK_TEXT
                } else {
                    theme::LIGHT_TEXT
                }),
        )
        .width(Length::FillPortion(1))
        .padding(10)
        .style(theme::neo_button(theme::NeoButton {
            is_accent: is_selected,
            has_shadow: false,
            theme: app_theme,
        }))
        .on_press(Message::ThemeChanged(target_theme))
    };

    let theme_row = row![
        theme_btn("Dark Theme".to_string(), AppTheme::Dark),
        theme_btn("Light Theme".to_string(), AppTheme::Light)
    ]
    .spacing(12)
    .width(Length::Fill);

    settings_list = settings_list
        .push(column![make_section_header("APPEARANCE".to_string()), theme_row].spacing(8));

    // --- Models / Assignments Rows ---
    let make_nav_row =
        |title: String, subtitle: String, icon_handle: &'static iced::widget::svg::Handle| {
            container(
                row![
                    icons::render_svg(icon_handle, theme::COLOR_ACCENT)
                        .width(Length::Fixed(20.0))
                        .height(Length::Fixed(20.0)),
                    space::horizontal().width(Length::Fixed(12.0)),
                    column![
                        text(title)
                            .font(icons::FONT_BOLD)
                            .size(14)
                            .color(if is_dark {
                                theme::DARK_TEXT
                            } else {
                                theme::LIGHT_TEXT
                            }),
                        text(subtitle)
                            .font(icons::FONT_REGULAR)
                            .size(12)
                            .color(if is_dark {
                                theme::DARK_TEXT_MUTED
                            } else {
                                theme::LIGHT_TEXT_MUTED
                            })
                    ]
                    .spacing(2),
                    space::horizontal().width(Length::Fill),
                    text(">").font(icons::FONT_BOLD).size(16).color(if is_dark {
                        theme::DARK_TEXT_MUTED
                    } else {
                        theme::LIGHT_TEXT_MUTED
                    })
                ]
                .align_y(alignment::Alignment::Center),
            )
            .padding(12)
            .width(Length::Fill)
            .style(theme::neo_container(theme::NeoContainer {
                is_surface: true,
                has_shadow: false,
                theme: app_theme,
            }))
        };

    let models_column = column![
        make_nav_row(
            "Model Role Assignments".to_string(),
            "Set models for chat, media, and TTS".to_string(),
            &icons::HANDLE_MENU
        ),
        make_nav_row(
            "Local Models".to_string(),
            "Manage on-device inference packs".to_string(),
            &icons::HANDLE_MENU
        ),
        make_nav_row(
            "API Presets".to_string(),
            "Configure Bring-Your-Own-Keys".to_string(),
            &icons::HANDLE_GALLERY
        )
    ]
    .spacing(8);

    settings_list = settings_list
        .push(column![make_section_header("MODELS".to_string()), models_column].spacing(8));

    // --- About Section ---
    let about_column = column![
        make_nav_row(
            "Terms of Service".to_string(),
            "Read Enclave user guidelines".to_string(),
            &icons::HANDLE_SETTINGS
        ),
        make_nav_row(
            "Licenses & Attributions".to_string(),
            "Credits for open source packages".to_string(),
            &icons::HANDLE_SETTINGS
        )
    ]
    .spacing(8);

    settings_list = settings_list
        .push(column![make_section_header("ABOUT".to_string()), about_column].spacing(8));

    let scroll_settings = scrollable_padded_body(settings_list, app_theme);

    let main_layout = column![top_bar, scroll_settings]
        .width(Length::Fill)
        .height(Length::Fill);

    screen_shell(main_layout, app_theme)
}

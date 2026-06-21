use crate::icons;
use crate::screens::layout::{icon_button, screen_shell, scrollable_padded_body};
use crate::theme::{self, AppTheme};
use crate::Message;
use iced::widget::{button, column, container, horizontal_space, row, text, Column};
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
        .style(if is_dark {
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
            horizontal_space().width(Length::Fill),
            close_btn
        ]
        .padding(10)
        .align_items(alignment::Alignment::Center),
    )
    .width(Length::Fill)
    .style(iced::theme::Container::Custom(Box::new(
        theme::NeoContainer {
            is_surface: true,
            has_shadow: false,
            theme: app_theme,
        },
    )));

    // --- 2. Scrollable Settings Content ---
    let mut settings_list = Column::new().spacing(24);

    // Section Title helper
    let make_section_header = |title: &str| {
        text(title)
            .font(icons::FONT_BOLD)
            .size(13)
            .style(theme::COLOR_ACCENT)
    };

    // --- Account Section ---
    let profile_row = container(
        row![
            // User initials avatar well
            container(
                text("SD")
                    .font(icons::FONT_BOLD)
                    .size(16)
                    .style(Color::BLACK)
            )
            .width(44)
            .height(44)
            .align_x(alignment::Horizontal::Center)
            .align_y(alignment::Vertical::Center)
            .style(iced::theme::Container::Custom(Box::new(
                theme::NeoContainer {
                    is_surface: true,
                    has_shadow: false,
                    theme: AppTheme::Light,
                }
            ))),
            horizontal_space().width(Length::Fixed(12.0)),
            // Name and email
            column![
                text("Sean Dev")
                    .font(icons::FONT_BOLD)
                    .size(15)
                    .style(if is_dark {
                        theme::DARK_TEXT
                    } else {
                        theme::LIGHT_TEXT
                    }),
                text("sean@browntowndev.com")
                    .font(icons::FONT_REGULAR)
                    .size(12)
                    .style(if is_dark {
                        theme::DARK_TEXT_MUTED
                    } else {
                        theme::LIGHT_TEXT_MUTED
                    })
            ]
            .spacing(2),
            horizontal_space().width(Length::Fill),
            // Logout
            button(
                text("Logout")
                    .font(icons::FONT_BOLD)
                    .size(12)
                    .style(Color::BLACK)
            )
            .padding(8)
            .style(iced::theme::Button::Custom(Box::new(theme::NeoButton {
                is_accent: true,
                has_shadow: false,
                theme: app_theme,
            })))
        ]
        .align_items(alignment::Alignment::Center),
    )
    .padding(14)
    .width(Length::Fill)
    .style(iced::theme::Container::Custom(Box::new(
        theme::NeoContainer {
            is_surface: true,
            has_shadow: false,
            theme: app_theme,
        },
    )));

    settings_list =
        settings_list.push(column![make_section_header("ACCOUNT"), profile_row].spacing(8));

    // --- Appearance Theme Selector ---
    let theme_btn = |label: &str, target_theme: AppTheme| {
        let is_selected = state.active_theme == target_theme;
        button(
            text(label)
                .font(icons::FONT_BOLD)
                .size(14)
                .horizontal_alignment(alignment::Horizontal::Center)
                .style(if is_selected {
                    Color::BLACK
                } else if is_dark {
                    theme::DARK_TEXT
                } else {
                    theme::LIGHT_TEXT
                }),
        )
        .width(Length::FillPortion(1))
        .padding(10)
        .style(iced::theme::Button::Custom(Box::new(theme::NeoButton {
            is_accent: is_selected,
            has_shadow: false,
            theme: app_theme,
        })))
        .on_press(Message::ThemeChanged(target_theme))
    };

    let theme_row = row![
        theme_btn("Dark Theme", AppTheme::Dark),
        theme_btn("Light Theme", AppTheme::Light)
    ]
    .spacing(12)
    .width(Length::Fill);

    settings_list =
        settings_list.push(column![make_section_header("APPEARANCE"), theme_row].spacing(8));

    // --- Models / Assignments Rows ---
    let make_nav_row = |title: &str, subtitle: &str, icon_handle: &iced::widget::svg::Handle| {
        container(
            row![
                icons::render_svg(icon_handle, theme::COLOR_ACCENT)
                    .width(Length::Fixed(20.0))
                    .height(Length::Fixed(20.0)),
                horizontal_space().width(Length::Fixed(12.0)),
                column![
                    text(title)
                        .font(icons::FONT_BOLD)
                        .size(14)
                        .style(if is_dark {
                            theme::DARK_TEXT
                        } else {
                            theme::LIGHT_TEXT
                        }),
                    text(subtitle)
                        .font(icons::FONT_REGULAR)
                        .size(12)
                        .style(if is_dark {
                            theme::DARK_TEXT_MUTED
                        } else {
                            theme::LIGHT_TEXT_MUTED
                        })
                ]
                .spacing(2),
                horizontal_space().width(Length::Fill),
                text(">").font(icons::FONT_BOLD).size(16).style(if is_dark {
                    theme::DARK_TEXT_MUTED
                } else {
                    theme::LIGHT_TEXT_MUTED
                })
            ]
            .align_items(alignment::Alignment::Center),
        )
        .padding(12)
        .width(Length::Fill)
        .style(iced::theme::Container::Custom(Box::new(
            theme::NeoContainer {
                is_surface: true,
                has_shadow: false,
                theme: app_theme,
            },
        )))
    };

    let models_column = column![
        make_nav_row(
            "Model Role Assignments",
            "Set models for chat, media, and TTS",
            &icons::HANDLE_MENU
        ),
        make_nav_row(
            "Local Models",
            "Manage on-device inference packs",
            &icons::HANDLE_MENU
        ),
        make_nav_row(
            "API Presets",
            "Configure Bring-Your-Own-Keys",
            &icons::HANDLE_GALLERY
        )
    ]
    .spacing(8);

    settings_list =
        settings_list.push(column![make_section_header("MODELS"), models_column].spacing(8));

    // --- About Section ---
    let about_column = column![
        make_nav_row(
            "Terms of Service",
            "Read Enclave user guidelines",
            &icons::HANDLE_SETTINGS
        ),
        make_nav_row(
            "Licenses & Attributions",
            "Credits for open source packages",
            &icons::HANDLE_SETTINGS
        )
    ]
    .spacing(8);

    settings_list =
        settings_list.push(column![make_section_header("ABOUT"), about_column].spacing(8));

    let scroll_settings = scrollable_padded_body(settings_list, app_theme);

    let main_layout = column![top_bar, scroll_settings]
        .width(Length::Fill)
        .height(Length::Fill);

    screen_shell(main_layout, app_theme)
}

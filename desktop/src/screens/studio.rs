use crate::icons;
use crate::theme::{self, AppTheme};
use crate::Message;
use iced::widget::{
    button, column, container, horizontal_space, row, scrollable, text, text_input, Column, Row,
};
use iced::{alignment, Element, Length, Theme};

pub struct StudioState {
    pub prompt: String,
    pub is_generating: bool,
    pub active_model: String,
    pub media_items: Vec<StudioMediaItem>,
    pub status_message: Option<String>,
}

#[derive(Debug, Clone)]
pub struct StudioMediaItem {
    #[allow(dead_code)]
    pub id: String,
    pub title: String,
    pub kind: String,
    pub timestamp: String,
}

impl StudioState {
    pub fn new() -> Self {
        Self {
            prompt: String::new(),
            is_generating: false,
            active_model: "Bonsai Ternary 4B (q5_K)".into(),
            media_items: vec![
                StudioMediaItem {
                    id: "1".into(),
                    title: "Cyberpunk Alleyway - Rain & Neon".into(),
                    kind: "Image".into(),
                    timestamp: "Just now".into(),
                },
                StudioMediaItem {
                    id: "2".into(),
                    title: "Retro Sci-Fi Rocket Launch".into(),
                    kind: "Video".into(),
                    timestamp: "5m ago".into(),
                },
                StudioMediaItem {
                    id: "3".into(),
                    title: "Abstract Geometry study #42".into(),
                    kind: "Image".into(),
                    timestamp: "20m ago".into(),
                },
                StudioMediaItem {
                    id: "4".into(),
                    title: "Cabin in the Woods - Isometric Vector".into(),
                    kind: "Image".into(),
                    timestamp: "1h ago".into(),
                },
                StudioMediaItem {
                    id: "5".into(),
                    title: "Liquid Gold Flowing Animation".into(),
                    kind: "Video".into(),
                    timestamp: "2h ago".into(),
                },
                StudioMediaItem {
                    id: "6".into(),
                    title: "Minimalist Desert Dune - Sunlight".into(),
                    kind: "Image".into(),
                    timestamp: "Yesterday".into(),
                },
            ],
            status_message: None,
        }
    }
}

pub fn view<'a>(
    state: &'a StudioState,
    app_theme: AppTheme,
) -> Element<'a, Message, Theme, iced::Renderer> {
    // --- 1. Studio Top Bar ---
    let menu_btn = button(
        text(icons::ICON_MENU)
            .font(icons::ICON_FONT)
            .size(20)
            .horizontal_alignment(alignment::Horizontal::Center)
            .vertical_alignment(alignment::Vertical::Center),
    )
    .padding(8)
    .width(Length::Fixed(40.0))
    .height(Length::Fixed(40.0))
    .style(iced::theme::Button::Custom(Box::new(theme::NeoButton {
        is_accent: false,
        has_shadow: true,
        theme: app_theme,
    })))
    .on_press(Message::NavigateTo(crate::Screen::Chat {
        chat_id: "active".into(),
    }));

    let model_picker = button(
        row![
            text(&state.active_model)
                .font(icons::FONT_BOLD)
                .size(16)
                .style(if app_theme == AppTheme::Dark {
                    theme::DARK_TEXT
                } else {
                    theme::LIGHT_TEXT
                }),
            text(icons::ICON_CHEVRON_DOWN)
                .font(icons::ICON_FONT)
                .size(16)
                .style(if app_theme == AppTheme::Dark {
                    theme::DARK_TEXT_MUTED
                } else {
                    theme::LIGHT_TEXT_MUTED
                })
        ]
        .spacing(8)
        .align_items(alignment::Alignment::Center),
    )
    .padding(8)
    .style(iced::theme::Button::Custom(Box::new(theme::NeoButton {
        is_accent: false,
        has_shadow: false,
        theme: app_theme,
    })))
    .on_press(Message::NavigateTo(crate::Screen::Settings));

    let gallery_btn = button(
        text(icons::ICON_GALLERY)
            .font(icons::ICON_FONT)
            .size(20)
            .horizontal_alignment(alignment::Horizontal::Center)
            .vertical_alignment(alignment::Vertical::Center),
    )
    .padding(8)
    .width(Length::Fixed(40.0))
    .height(Length::Fixed(40.0))
    .style(iced::theme::Button::Custom(Box::new(theme::NeoButton {
        is_accent: false,
        has_shadow: true,
        theme: app_theme,
    })))
    .on_press(Message::NavigateTo(crate::Screen::Gallery {
        selected_album_id: None,
    }));

    let top_bar = container(
        row![
            menu_btn,
            horizontal_space().width(Length::FillPortion(1)),
            model_picker,
            horizontal_space().width(Length::FillPortion(1)),
            gallery_btn
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

    // --- 2. Main Content Grid (StudioGalleryPane) ---
    let mut gallery_grid = Column::new().spacing(20);
    let columns_count = 3;

    for row_chunk in state.media_items.chunks(columns_count) {
        let mut row_layout = Row::new().spacing(20);
        for item in row_chunk {
            // Card for each media item
            let media_thumbnail = container(
                column![
                    text(if item.kind == "Video" {
                        "🎬 VIDEO"
                    } else {
                        "🖼️ IMAGE"
                    })
                    .font(icons::FONT_BOLD)
                    .size(14)
                    .style(if app_theme == AppTheme::Dark {
                        theme::DARK_TEXT_MUTED
                    } else {
                        theme::LIGHT_TEXT_MUTED
                    }),
                    text(&item.timestamp)
                        .font(icons::FONT_REGULAR)
                        .size(12)
                        .style(if app_theme == AppTheme::Dark {
                            theme::DARK_TEXT_MUTED
                        } else {
                            theme::LIGHT_TEXT_MUTED
                        })
                ]
                .spacing(4)
                .align_items(alignment::Alignment::Center),
            )
            .width(Length::Fill)
            .height(Length::Fixed(150.0))
            .align_x(alignment::Horizontal::Center)
            .align_y(alignment::Vertical::Center)
            .style(iced::theme::Container::Custom(Box::new(
                theme::NeoContainer {
                    is_surface: false,
                    has_shadow: false,
                    theme: app_theme,
                },
            )));

            let media_card = container(
                column![
                    media_thumbnail,
                    text(&item.title)
                        .font(icons::FONT_REGULAR)
                        .size(14)
                        .style(if app_theme == AppTheme::Dark {
                            theme::DARK_TEXT
                        } else {
                            theme::LIGHT_TEXT
                        })
                        .width(Length::Fill)
                ]
                .spacing(10),
            )
            .padding(12)
            .width(Length::FillPortion(1))
            .style(iced::theme::Container::Custom(Box::new(
                theme::NeoContainer {
                    is_surface: true,
                    has_shadow: true,
                    theme: app_theme,
                },
            )));

            row_layout = row_layout.push(media_card);
        }

        // Pad remaining space if row is not full
        for _ in row_chunk.len()..columns_count {
            row_layout = row_layout.push(horizontal_space().width(Length::FillPortion(1)));
        }

        gallery_grid = gallery_grid.push(row_layout);
    }

    let scroll_gallery = scrollable(container(gallery_grid).padding(20).width(Length::Fill))
        .height(Length::Fill)
        .style(iced::theme::Scrollable::Custom(Box::new(
            theme::NeoScrollable { theme: app_theme },
        )));

    // --- Optional Status Notification Bar ---
    let status_bar = if let Some(ref msg) = state.status_message {
        let status_content = row![
            text(msg)
                .font(icons::FONT_REGULAR)
                .size(14)
                .style(if app_theme == AppTheme::Dark {
                    theme::DARK_TEXT
                } else {
                    theme::LIGHT_TEXT
                }),
            horizontal_space().width(Length::Fill),
            button(
                text(icons::ICON_CLOSE)
                    .font(icons::ICON_FONT)
                    .size(14)
                    .style(theme::COLOR_ACCENT)
            )
            .style(iced::theme::Button::Text)
            .on_press(Message::StudioPromptChanged(String::new())) // Reset/dismiss
        ]
        .align_items(alignment::Alignment::Center)
        .spacing(10);

        container(status_content)
            .padding(12)
            .width(Length::Fill)
            .style(iced::theme::Container::Custom(Box::new(
                theme::NeoContainer {
                    is_surface: true,
                    has_shadow: false,
                    theme: app_theme,
                },
            )))
    } else {
        container(horizontal_space().width(Length::Shrink))
    };

    // --- 3. Bottom Input Dock (StudioInputDock) ---
    let mic_btn = button(
        text(icons::ICON_MIC)
            .font(icons::ICON_FONT)
            .size(20)
            .horizontal_alignment(alignment::Horizontal::Center)
            .vertical_alignment(alignment::Vertical::Center),
    )
    .padding(8)
    .width(Length::Fixed(40.0))
    .height(Length::Fixed(40.0))
    .style(iced::theme::Button::Custom(Box::new(theme::NeoButton {
        is_accent: false,
        has_shadow: true,
        theme: app_theme,
    })))
    .on_press(Message::NavigateTo(crate::Screen::Settings));

    let image_btn = button(
        text(icons::ICON_IMAGE)
            .font(icons::ICON_FONT)
            .size(20)
            .horizontal_alignment(alignment::Horizontal::Center)
            .vertical_alignment(alignment::Vertical::Center),
    )
    .padding(8)
    .width(Length::Fixed(40.0))
    .height(Length::Fixed(40.0))
    .style(iced::theme::Button::Custom(Box::new(theme::NeoButton {
        is_accent: false,
        has_shadow: true,
        theme: app_theme,
    })))
    .on_press(Message::NavigateTo(crate::Screen::Gallery {
        selected_album_id: None,
    }));

    let prompt_input = text_input("Describe what you want to generate...", &state.prompt)
        .on_input(Message::StudioPromptChanged)
        .on_submit(Message::StudioGenerate)
        .padding(10)
        .style(iced::theme::TextInput::Custom(Box::new(theme::NeoInput {
            theme: app_theme,
        })));

    let generate_btn = button(
        row![
            text(icons::ICON_SEND)
                .font(icons::ICON_FONT)
                .size(16)
                .style(iced::Color::BLACK),
            text("Generate")
                .font(icons::FONT_BOLD)
                .size(14)
                .style(iced::Color::BLACK)
        ]
        .spacing(6)
        .align_items(alignment::Alignment::Center),
    )
    .padding(10)
    .style(iced::theme::Button::Custom(Box::new(theme::NeoButton {
        is_accent: true,
        has_shadow: true,
        theme: app_theme,
    })))
    .on_press(Message::StudioGenerate);

    let bottom_dock = container(
        row![mic_btn, image_btn, prompt_input, generate_btn]
            .spacing(12)
            .padding(16)
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

    // Outer layout column
    let main_layout = column![top_bar, status_bar, scroll_gallery, bottom_dock]
        .width(Length::Fill)
        .height(Length::Fill);

    container(main_layout)
        .width(Length::Fill)
        .height(Length::Fill)
        .style(iced::theme::Container::Custom(Box::new(
            theme::NeoContainer {
                is_surface: false,
                has_shadow: false,
                theme: app_theme,
            },
        )))
        .into()
}

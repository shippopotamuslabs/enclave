use crate::icons;
use crate::screens::layout::{icon_button, screen_shell, scrollable_padded_body};
use crate::theme::{self, AppTheme};
use crate::Message;
use iced::widget::{button, column, container, horizontal_space, row, text, Column, Row};
use iced::{alignment, Element, Length, Theme};

pub struct GalleryState {
    pub selected_album_id: Option<String>,
    pub albums: Vec<GalleryAlbum>,
}

#[derive(Debug, Clone)]
pub struct GalleryAlbum {
    pub id: String,
    pub name: String,
    pub items: Vec<GalleryMediaItem>,
}

#[derive(Debug, Clone)]
pub struct GalleryMediaItem {
    #[allow(dead_code)]
    pub id: String,
    pub title: String,
    pub kind: String,
}

impl GalleryState {
    pub fn new() -> Self {
        Self {
            selected_album_id: None,
            albums: vec![
                GalleryAlbum {
                    id: "album_art".into(),
                    name: "Generative Art".into(),
                    items: vec![
                        GalleryMediaItem {
                            id: "art_1".into(),
                            title: "Abstract Pastel Swirls".into(),
                            kind: "Image".into(),
                        },
                        GalleryMediaItem {
                            id: "art_2".into(),
                            title: "3D Glitch Cube Layout".into(),
                            kind: "Image".into(),
                        },
                        GalleryMediaItem {
                            id: "art_3".into(),
                            title: "Procedural City Blocks".into(),
                            kind: "Image".into(),
                        },
                        GalleryMediaItem {
                            id: "art_4".into(),
                            title: "Fluid Ink Simulation".into(),
                            kind: "Image".into(),
                        },
                    ],
                },
                GalleryAlbum {
                    id: "album_video".into(),
                    name: "Video Projects".into(),
                    items: vec![
                        GalleryMediaItem {
                            id: "vid_1".into(),
                            title: "Cyberpunk Flythrough".into(),
                            kind: "Video".into(),
                        },
                        GalleryMediaItem {
                            id: "vid_2".into(),
                            title: "Recursive Particle Explosion".into(),
                            kind: "Video".into(),
                        },
                    ],
                },
                GalleryAlbum {
                    id: "album_camera".into(),
                    name: "Camera Roll".into(),
                    items: vec![GalleryMediaItem {
                        id: "cam_1".into(),
                        title: "Digital Sea Breeze".into(),
                        kind: "Image".into(),
                    }],
                },
            ],
        }
    }
}

pub fn view(
    state: &GalleryState,
    app_theme: AppTheme,
) -> Element<'_, Message, Theme, iced::Renderer> {
    let is_dark = app_theme == AppTheme::Dark;

    // Determine active selected album
    let active_album = state
        .selected_album_id
        .as_ref()
        .and_then(|id| state.albums.iter().find(|a| a.id == *id));

    let text_color = if is_dark {
        theme::DARK_TEXT
    } else {
        theme::LIGHT_TEXT
    };

    // --- 1. Gallery Top Bar ---
    let back_btn = icon_button(
        &icons::HANDLE_CLOSE,
        text_color,
        app_theme,
        if active_album.is_some() {
            Message::GallerySelectAlbum(None)
        } else {
            Message::NavigateBack
        },
    );

    let top_bar_title = if let Some(album) = active_album {
        format!("Gallery > {}", album.name)
    } else {
        "Gallery".to_string()
    };

    let title_text = text(&top_bar_title)
        .font(icons::FONT_BOLD)
        .size(18)
        .style(if is_dark {
            theme::DARK_TEXT
        } else {
            theme::LIGHT_TEXT
        });

    let add_album_btn = button(
        text("+")
            .font(icons::FONT_BOLD)
            .size(20)
            .width(Length::Fill)
            .height(Length::Fill)
            .horizontal_alignment(alignment::Horizontal::Center)
            .vertical_alignment(alignment::Vertical::Center)
            .style(if is_dark {
                theme::DARK_TEXT
            } else {
                theme::LIGHT_TEXT
            }),
    )
    .padding(8)
    .width(Length::Fixed(40.0))
    .height(Length::Fixed(40.0))
    .style(iced::theme::Button::Custom(Box::new(theme::NeoButton {
        is_accent: false,
        has_shadow: true,
        theme: app_theme,
    })));

    let top_bar = container(
        row![
            back_btn,
            horizontal_space().width(Length::Fixed(12.0)),
            title_text,
            horizontal_space().width(Length::Fill),
            add_album_btn
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

    // --- 2. Main Grid Layout ---
    let mut grid_layout = Column::new().spacing(20);
    let columns = 2; // 2 columns for gallery grid on desktop

    if let Some(album) = active_album {
        // Render items inside the selected album
        for row_chunk in album.items.chunks(columns) {
            let mut row_layout = Row::new().spacing(20);
            for item in row_chunk {
                let thumbnail_block = container(
                    text(if item.kind == "Video" {
                        "🎬 VIDEO"
                    } else {
                        "🖼️ IMAGE"
                    })
                    .font(icons::FONT_BOLD)
                    .size(14)
                    .style(if is_dark {
                        theme::DARK_TEXT_MUTED
                    } else {
                        theme::LIGHT_TEXT_MUTED
                    }),
                )
                .width(Length::Fill)
                .height(130)
                .align_x(alignment::Horizontal::Center)
                .align_y(alignment::Vertical::Center)
                .style(iced::theme::Container::Custom(Box::new(
                    theme::NeoContainer {
                        is_surface: false,
                        has_shadow: false,
                        theme: app_theme,
                    },
                )));

                let item_card = container(
                    column![
                        thumbnail_block,
                        text(&item.title)
                            .font(icons::FONT_REGULAR)
                            .size(13)
                            .style(if is_dark {
                                theme::DARK_TEXT
                            } else {
                                theme::LIGHT_TEXT
                            })
                    ]
                    .spacing(8),
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

                row_layout = row_layout.push(item_card);
            }

            for _ in row_chunk.len()..columns {
                row_layout = row_layout.push(horizontal_space().width(Length::FillPortion(1)));
            }

            grid_layout = grid_layout.push(row_layout);
        }
    } else {
        // Render list of album folders
        for row_chunk in state.albums.chunks(columns) {
            let mut row_layout = Row::new().spacing(20);
            for album in row_chunk {
                let text_color = if is_dark {
                    theme::DARK_TEXT
                } else {
                    theme::LIGHT_TEXT
                };

                // Folder Cover mockup block
                let folder_cover = container(
                    column![
                        icons::render_svg(&icons::HANDLE_FOLDER, text_color)
                            .width(Length::Fixed(36.0))
                            .height(Length::Fixed(36.0)),
                        text(format!("{} items", album.items.len()))
                            .font(icons::FONT_REGULAR)
                            .size(12)
                            .style(if is_dark {
                                theme::DARK_TEXT_MUTED
                            } else {
                                theme::LIGHT_TEXT_MUTED
                            })
                    ]
                    .spacing(4)
                    .align_items(alignment::Alignment::Center),
                )
                .width(Length::Fill)
                .height(140)
                .align_x(alignment::Horizontal::Center)
                .align_y(alignment::Vertical::Center)
                .style(iced::theme::Container::Custom(Box::new(
                    theme::NeoContainer {
                        is_surface: false,
                        has_shadow: false,
                        theme: app_theme,
                    },
                )));

                let album_card = button(
                    container(
                        column![
                            folder_cover,
                            text(&album.name)
                                .font(icons::FONT_BOLD)
                                .size(15)
                                .style(if is_dark {
                                    theme::DARK_TEXT
                                } else {
                                    theme::LIGHT_TEXT
                                })
                        ]
                        .spacing(8),
                    )
                    .padding(12)
                    .width(Length::Fill),
                )
                .width(Length::FillPortion(1))
                .style(iced::theme::Button::Custom(Box::new(theme::NeoButton {
                    is_accent: false,
                    has_shadow: true,
                    theme: app_theme,
                })))
                .on_press(Message::GallerySelectAlbum(Some(album.id.clone())));

                row_layout = row_layout.push(album_card);
            }

            for _ in row_chunk.len()..columns {
                row_layout = row_layout.push(horizontal_space().width(Length::FillPortion(1)));
            }

            grid_layout = grid_layout.push(row_layout);
        }
    }

    let scroll_grid = scrollable_padded_body(grid_layout, app_theme);

    let main_layout = column![top_bar, scroll_grid]
        .width(Length::Fill)
        .height(Length::Fill);

    screen_shell(main_layout, app_theme)
}

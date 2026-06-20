use crate::icons;
use crate::theme::{self, AppTheme};
use crate::Message;
use iced::widget::{
    button, column, container, horizontal_space, row, scrollable, text, text_input, Column,
};
use iced::{alignment, Element, Length, Theme};

pub struct HistoryState {
    pub search_query: String,
    pub chats: Vec<HistoryChat>,
}

#[derive(Debug, Clone)]
pub struct HistoryChat {
    pub id: String,
    pub name: String,
    pub last_message: String,
    #[allow(dead_code)]
    pub timestamp: String,
    pub is_pinned: bool,
}

impl HistoryState {
    pub fn new() -> Self {
        Self {
            search_query: String::new(),
            chats: vec![
                HistoryChat {
                    id: "chat_1".into(),
                    name: "Analyzing Rust Iced performance".into(),
                    last_message: "Let's check the frame rates...".into(),
                    timestamp: "10:30 AM".into(),
                    is_pinned: true,
                },
                HistoryChat {
                    id: "chat_2".into(),
                    name: "Android Compose compiler options".into(),
                    last_message: "Make sure metrics are enabled".into(),
                    timestamp: "Yesterday".into(),
                    is_pinned: true,
                },
                HistoryChat {
                    id: "chat_3".into(),
                    name: "Firebase security rules logic".into(),
                    last_message: "allow read, write: if request.auth != null".into(),
                    timestamp: "June 18".into(),
                    is_pinned: false,
                },
                HistoryChat {
                    id: "chat_4".into(),
                    name: "Multimodal Gemini API prompt engineering".into(),
                    last_message: "System instructions must be clear".into(),
                    timestamp: "June 15".into(),
                    is_pinned: false,
                },
            ],
        }
    }
}

// Returns a Sidebar element of fixed width 280.0
pub fn view<'a>(
    state: &'a HistoryState,
    app_theme: AppTheme,
) -> Element<'a, Message, Theme, iced::Renderer> {
    let is_dark = app_theme == AppTheme::Dark;

    // --- 1. Sidebar Header (Search & Actions) ---
    let search_input = text_input("Search chats...", &state.search_query)
        .on_input(Message::HistorySearchChanged)
        .padding(8)
        .style(iced::theme::TextInput::Custom(Box::new(theme::NeoInput {
            theme: app_theme,
        })));

    let text_color = if is_dark {
        theme::DARK_TEXT
    } else {
        theme::LIGHT_TEXT
    };

    let settings_btn = button(
        container(
            icons::render_svg(&icons::HANDLE_SETTINGS, text_color)
                .width(Length::Fixed(16.0))
                .height(Length::Fixed(16.0)),
        )
        .width(Length::Fill)
        .height(Length::Fill)
        .center_x()
        .center_y(),
    )
    .padding(6)
    .width(Length::Fixed(32.0))
    .height(Length::Fixed(32.0))
    .style(iced::theme::Button::Custom(Box::new(theme::NeoButton {
        is_accent: false,
        has_shadow: true,
        theme: app_theme,
    })))
    .on_press(Message::NavigateTo(crate::Screen::Settings));

    let header_row = row![
        search_input,
        horizontal_space().width(Length::Fixed(8.0)),
        settings_btn
    ]
    .align_items(alignment::Alignment::Center)
    .padding(10);

    // --- 2. Navigation Quick Links ---
    let nav_btn = |title: &str, icon_handle: &iced::widget::svg::Handle, msg: Message| {
        button(
            row![
                icons::render_svg(icon_handle, theme::COLOR_ACCENT)
                    .width(Length::Fixed(16.0))
                    .height(Length::Fixed(16.0)),
                text(title)
                    .font(icons::FONT_BOLD)
                    .size(13)
                    .style(if is_dark {
                        theme::DARK_TEXT
                    } else {
                        theme::LIGHT_TEXT
                    })
            ]
            .spacing(10)
            .align_items(alignment::Alignment::Center),
        )
        .width(Length::Fill)
        .padding(10)
        .style(iced::theme::Button::Custom(Box::new(theme::NeoButton {
            is_accent: false,
            has_shadow: false,
            theme: app_theme,
        })))
        .on_press(msg)
    };

    let quick_links = column![
        nav_btn(
            "New Chat",
            &icons::HANDLE_ADD,
            Message::NavigateTo(crate::Screen::Chat {
                chat_id: "new_chat".into()
            })
        ),
        nav_btn(
            "Studio Screen",
            &icons::HANDLE_IMAGE,
            Message::NavigateTo(crate::Screen::Studio)
        ),
        nav_btn(
            "Media Gallery",
            &icons::HANDLE_GALLERY,
            Message::NavigateTo(crate::Screen::Gallery {
                selected_album_id: None
            })
        )
    ]
    .spacing(4);

    let quick_links_section = container(quick_links).padding(8).width(Length::Fill).style(
        iced::theme::Container::Custom(Box::new(theme::NeoContainer {
            is_surface: true,
            has_shadow: false,
            theme: app_theme,
        })),
    );

    // --- 3. Chat Lists ---
    let filtered_chats: Vec<&HistoryChat> = state
        .chats
        .iter()
        .filter(|c| {
            state.search_query.is_empty()
                || c.name
                    .to_lowercase()
                    .contains(&state.search_query.to_lowercase())
        })
        .collect();

    let pinned_chats: Vec<&&HistoryChat> = filtered_chats.iter().filter(|c| c.is_pinned).collect();
    let recent_chats: Vec<&&HistoryChat> = filtered_chats.iter().filter(|c| !c.is_pinned).collect();

    let render_chat_row = |chat: &HistoryChat| {
        let pin_btn = button(
            icons::render_svg(
                &icons::HANDLE_PIN,
                if chat.is_pinned {
                    theme::COLOR_ACCENT
                } else if is_dark {
                    theme::DARK_TEXT_MUTED
                } else {
                    theme::LIGHT_TEXT_MUTED
                },
            )
            .width(Length::Fixed(16.0))
            .height(Length::Fixed(16.0)),
        )
        .style(iced::theme::Button::Text)
        .on_press(if chat.is_pinned {
            Message::HistoryUnpinChat(chat.id.clone())
        } else {
            Message::HistoryPinChat(chat.id.clone())
        });

        let delete_btn = button(
            icons::render_svg(
                &icons::HANDLE_DELETE,
                if is_dark {
                    theme::DARK_TEXT_MUTED
                } else {
                    theme::LIGHT_TEXT_MUTED
                },
            )
            .width(Length::Fixed(16.0))
            .height(Length::Fixed(16.0)),
        )
        .style(iced::theme::Button::Text)
        .on_press(Message::HistoryDeleteChat(chat.id.clone()));

        container(
            row![
                button(
                    column![
                        text(&chat.name)
                            .font(icons::FONT_BOLD)
                            .size(13)
                            .style(if is_dark {
                                theme::DARK_TEXT
                            } else {
                                theme::LIGHT_TEXT
                            }),
                        text(&chat.last_message)
                            .font(icons::FONT_REGULAR)
                            .size(11)
                            .style(if is_dark {
                                theme::DARK_TEXT_MUTED
                            } else {
                                theme::LIGHT_TEXT_MUTED
                            })
                    ]
                    .spacing(2)
                )
                .width(Length::Fill)
                .style(iced::theme::Button::Text)
                .on_press(Message::NavigateTo(crate::Screen::Chat {
                    chat_id: chat.id.clone()
                })),
                column![pin_btn, delete_btn].spacing(4)
            ]
            .align_items(alignment::Alignment::Center),
        )
        .padding(8)
        .width(Length::Fill)
        .style(iced::theme::Container::Custom(Box::new(
            theme::NeoContainer {
                is_surface: true,
                has_shadow: false,
                theme: app_theme,
            },
        )))
    };

    let mut sidebar_list = Column::new().spacing(14);

    // Quick navigation section
    sidebar_list = sidebar_list.push(quick_links_section);

    // Pinned Section
    if !pinned_chats.is_empty() {
        let mut section = Column::new().spacing(6);
        section = section.push(
            text("PINNED")
                .font(icons::FONT_BOLD)
                .size(10)
                .style(if is_dark {
                    theme::DARK_TEXT_MUTED
                } else {
                    theme::LIGHT_TEXT_MUTED
                }),
        );
        for chat in pinned_chats {
            section = section.push(render_chat_row(chat));
        }
        sidebar_list = sidebar_list.push(section);
    }

    // Recent Section
    if !recent_chats.is_empty() {
        let mut section = Column::new().spacing(6);
        section = section.push(text("RECENT CHATS").font(icons::FONT_BOLD).size(10).style(
            if is_dark {
                theme::DARK_TEXT_MUTED
            } else {
                theme::LIGHT_TEXT_MUTED
            },
        ));
        for chat in recent_chats {
            section = section.push(render_chat_row(chat));
        }
        sidebar_list = sidebar_list.push(section);
    }

    let scroll_sidebar = scrollable(container(sidebar_list).padding(10).width(Length::Fill))
        .height(Length::Fill)
        .style(iced::theme::Scrollable::Custom(Box::new(
            theme::NeoScrollable { theme: app_theme },
        )));

    let sidebar_layout = column![header_row, scroll_sidebar]
        .width(Length::Fixed(280.0))
        .height(Length::Fill);

    container(sidebar_layout)
        .width(Length::Fixed(280.0))
        .height(Length::Fill)
        .style(iced::theme::Container::Custom(Box::new(
            theme::NeoContainer {
                is_surface: true,
                has_shadow: false,
                theme: app_theme,
            },
        )))
        .into()
}

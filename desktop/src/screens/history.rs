use crate::icons;
use crate::theme::{self, AppTheme};
use crate::Message;
use iced::widget::{
    button, column, container, mouse_area, row, scrollable, space, text, text_input, Column,
};
use iced::{alignment, Element, Length, Theme};

pub const MIN_SIDEBAR_WIDTH: f32 = 220.0;
pub const MAX_SIDEBAR_WIDTH: f32 = 480.0;

pub struct HistoryState {
    pub search_query: String,
    pub chats: Vec<HistoryChat>,
    pub hovered_chat_id: Option<String>,
    pub sidebar_width: f32,
}

#[derive(Debug, Clone)]
pub struct HistoryChat {
    pub id: String,
    pub name: String,
    #[allow(dead_code)]
    pub last_message: String,
    pub timestamp: String,
    pub is_pinned: bool,
}

impl HistoryState {
    pub fn new() -> Self {
        Self {
            search_query: String::new(),
            hovered_chat_id: None,
            sidebar_width: MAX_SIDEBAR_WIDTH,
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

    pub fn clamp_sidebar_width(width: f32) -> f32 {
        width.clamp(MIN_SIDEBAR_WIDTH, MAX_SIDEBAR_WIDTH)
    }

    pub fn apply_sidebar_resize_delta(&mut self, delta_x: f32) {
        self.sidebar_width = Self::clamp_sidebar_width(self.sidebar_width + delta_x);
    }
}

/// Max title length when hover actions are visible, scaled to sidebar width.
fn hover_title_max_chars(sidebar_width: f32) -> usize {
    // Reserve ~96px for padding + hover action buttons; ~12px per character at 13px bold.
    ((sidebar_width - 96.0) / 12.0).max(10.0) as usize
}

pub fn view(
    state: &HistoryState,
    app_theme: AppTheme,
) -> Element<'_, Message, Theme, iced::Renderer> {
    let is_dark = app_theme == AppTheme::Dark;

    // --- 1. Sidebar Header (Search & Actions) ---
    let search_input = text_input("Search chats...", &state.search_query)
        .on_input(Message::HistorySearchChanged)
        .padding(8)
        .style(theme::neo_input(theme::NeoInput { theme: app_theme }));

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
        .align_x(iced::alignment::Horizontal::Center)
        .align_y(iced::alignment::Vertical::Center),
    )
    .padding(6)
    .width(Length::Fixed(32.0))
    .height(Length::Fixed(32.0))
    .style(theme::neo_button(theme::NeoButton {
        is_accent: false,
        has_shadow: true,
        theme: app_theme,
    }))
    .on_press(Message::NavigateTo(crate::Screen::Settings));

    let header_row = row![
        search_input,
        space::horizontal().width(Length::Fixed(8.0)),
        settings_btn
    ]
    .align_y(alignment::Alignment::Center)
    .padding(10);

    // --- 2. Navigation Quick Links ---
    // Each quick link is a standalone full-width shadowed `NeoButton` (matching
    // the chat input bar's action buttons) rather than buttons grouped inside a
    // bordered box — so they read as individual elevated controls, not a panel.
    let nav_btn =
        |title: &'static str, icon_handle: &'static iced::widget::svg::Handle, msg: Message| {
            button(
                row![
                    icons::render_svg(icon_handle, theme::COLOR_ACCENT)
                        .width(Length::Fixed(16.0))
                        .height(Length::Fixed(16.0)),
                    text(title)
                        .font(icons::FONT_BOLD)
                        .size(13)
                        .color(if is_dark {
                            theme::DARK_TEXT
                        } else {
                            theme::LIGHT_TEXT
                        })
                ]
                .spacing(10)
                .align_y(alignment::Alignment::Center),
            )
            .width(Length::Fill)
            .padding(10)
            .style(theme::neo_button(theme::NeoButton {
                is_accent: false,
                has_shadow: true,
                theme: app_theme,
            }))
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
    .spacing(8);

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

    let render_chat_row =
        |chat: &HistoryChat| {
            let chat_id = chat.id.clone();
            let is_pinned = chat.is_pinned;
            let is_hovered = state.hovered_chat_id.as_deref() == Some(chat_id.as_str());
            let muted_color = if is_dark {
                theme::DARK_TEXT_MUTED
            } else {
                theme::LIGHT_TEXT_MUTED
            };
            let title_color = if is_dark {
                theme::DARK_TEXT
            } else {
                theme::LIGHT_TEXT
            };

            let title_label = if is_hovered {
                truncate_with_ellipsis(&chat.name, hover_title_max_chars(state.sidebar_width))
            } else {
                chat.name.clone()
            };

            let title = text(title_label)
                .font(icons::FONT_BOLD)
                .size(13)
                .color(title_color)
                .width(Length::Fill);

            let timestamp = text(chat.timestamp.clone())
                .font(icons::FONT_REGULAR)
                .size(11)
                .color(muted_color);

            let chat_body = button(column![title, timestamp].spacing(2))
                .width(Length::Fill)
                .style(button::text)
                .on_press(Message::NavigateTo(crate::Screen::Chat {
                    chat_id: chat_id.clone(),
                }));

            let mut card_row = row![chat_body].align_y(alignment::Alignment::Center);

            if is_hovered {
                let pin_color = if is_pinned {
                    theme::COLOR_ACCENT
                } else {
                    muted_color
                };

                let pin_btn = button(
                    icons::render_svg(&icons::HANDLE_PIN, pin_color)
                        .width(Length::Fixed(16.0))
                        .height(Length::Fixed(16.0)),
                )
                .style(button::text)
                .on_press(if is_pinned {
                    Message::HistoryUnpinChat(chat_id.clone())
                } else {
                    Message::HistoryPinChat(chat_id.clone())
                });

                let rename_btn = button(
                    icons::render_svg(&icons::HANDLE_PENCIL, muted_color)
                        .width(Length::Fixed(16.0))
                        .height(Length::Fixed(16.0)),
                )
                .style(button::text)
                .on_press(Message::HistoryRenameChat(chat_id.clone()));

                let delete_btn = button(
                    icons::render_svg(&icons::HANDLE_DELETE, muted_color)
                        .width(Length::Fixed(16.0))
                        .height(Length::Fixed(16.0)),
                )
                .style(button::text)
                .on_press(Message::HistoryDeleteChat(chat_id.clone()));

                card_row = card_row.push(
                    row![pin_btn, rename_btn, delete_btn]
                        .spacing(4)
                        .align_y(alignment::Alignment::Center),
                );
            }

            mouse_area(container(card_row).padding(8).width(Length::Fill).style(
                theme::neo_container(theme::NeoContainer {
                    is_surface: true,
                    has_shadow: false,
                    theme: app_theme,
                }),
            ))
            .on_enter(Message::HistoryHoverChat(Some(chat_id)))
            .on_exit(Message::HistoryHoverChat(None))
        };

    let mut sidebar_list = Column::new().spacing(14);

    // Quick navigation section
    sidebar_list = sidebar_list.push(quick_links);

    // Pinned Section
    if !pinned_chats.is_empty() {
        let mut section = Column::new().spacing(6);
        section = section.push(
            text("PINNED")
                .font(icons::FONT_BOLD)
                .size(10)
                .color(if is_dark {
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
        section = section.push(text("RECENT CHATS").font(icons::FONT_BOLD).size(10).color(
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
        .style(theme::neo_scrollable(theme::NeoScrollable {
            theme: app_theme,
        }));

    let sidebar_layout = column![header_row, scroll_sidebar]
        .width(Length::Fixed(state.sidebar_width))
        .height(Length::Fill);

    // Borderless surface fill — the draggable pane splitter draws the single
    // vertical rule between sidebar and chat so we don't double up with a
    // `NeoContainer` right edge. A full-width bottom rule in the chat screen
    // closes off the pane row under both columns.
    container(sidebar_layout)
        .width(Length::Fixed(state.sidebar_width))
        .height(Length::Fill)
        .style(theme::neo_pane_background(theme::NeoPaneBackground {
            is_surface: true,
            theme: app_theme,
        }))
        .into()
}

fn truncate_with_ellipsis(text: &str, max_chars: usize) -> String {
    let char_count = text.chars().count();
    if char_count <= max_chars {
        return text.to_owned();
    }

    let keep = max_chars.saturating_sub(1);
    format!("{}…", text.chars().take(keep).collect::<String>())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn truncate_with_ellipsis_leaves_short_text_unchanged() {
        assert_eq!(truncate_with_ellipsis("Short chat", 22), "Short chat");
    }

    #[test]
    fn truncate_with_ellipsis_adds_ellipsis_for_long_text() {
        let long = "Multimodal Gemini API prompt engineering";
        assert_eq!(truncate_with_ellipsis(long, 22), "Multimodal Gemini API…");
    }

    #[test]
    fn clamp_sidebar_width_respects_bounds() {
        assert_eq!(HistoryState::clamp_sidebar_width(100.0), MIN_SIDEBAR_WIDTH);
        assert_eq!(HistoryState::clamp_sidebar_width(900.0), MAX_SIDEBAR_WIDTH);
        assert_eq!(HistoryState::clamp_sidebar_width(300.0), 300.0);
    }

    #[test]
    fn apply_sidebar_resize_delta_clamps_result() {
        let mut state = HistoryState::new();
        assert_eq!(state.sidebar_width, MAX_SIDEBAR_WIDTH);
        state.apply_sidebar_resize_delta(-50.0);
        assert_eq!(state.sidebar_width, MAX_SIDEBAR_WIDTH - 50.0);
        state.apply_sidebar_resize_delta(500.0);
        assert_eq!(state.sidebar_width, MAX_SIDEBAR_WIDTH);
    }
}

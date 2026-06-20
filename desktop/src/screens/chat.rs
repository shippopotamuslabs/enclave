use crate::icons;
use crate::theme::{self, AppTheme};
use crate::Message;
use iced::widget::{
    button, column, container, horizontal_space, row, scrollable, text, text_input, Column,
};
use iced::{alignment, Element, Length, Theme};

pub struct ChatState {
    pub active_chat_id: String,
    pub active_chat_name: String,
    pub input_text: String,
    pub messages: Vec<ChatMessage>,
}

#[derive(Debug, Clone)]
pub struct ChatMessage {
    pub sender: MessageSender,
    pub text: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MessageSender {
    User,
    Assistant,
}

impl ChatState {
    pub fn new(chat_id: String) -> Self {
        let (name, messages) = match chat_id.as_str() {
            "chat_1" => (
                "Analyzing Rust Iced performance".into(),
                vec![
                    ChatMessage {
                        sender: MessageSender::User,
                        text: "How does Iced compare to other UI toolkits on Linux?".into(),
                    },
                    ChatMessage {
                        sender: MessageSender::Assistant,
                        text: "Iced is a cross-platform GUI library written in Rust. On Linux, it uses wgpu (WebGPU) or OpenGL by default, giving it hardware-accelerated rendering. Because of its Elm-inspired architecture, it maintains low memory overhead and high responsiveness. It is highly competitive with toolkits like GTK, Qt, or Slint, especially when you need a pure-Rust codebase.".into(),
                    },
                ],
            ),
            "chat_2" => (
                "Android Compose compiler options".into(),
                vec![
                    ChatMessage {
                        sender: MessageSender::User,
                        text: "How do I optimize Jetpack Compose compile times?".into(),
                    },
                    ChatMessage {
                        sender: MessageSender::Assistant,
                        text: "To optimize Compose compile times, ensure you are utilizing Hilt or Dagger efficiently to prevent KSP bottlenecking. You can also enable Compose compiler metrics in your gradle configuration to inspect unstable parameters causing excessive recompositions.".into(),
                    },
                ],
            ),
            _ => (
                "New Chat".into(),
                vec![
                    ChatMessage {
                        sender: MessageSender::Assistant,
                        text: "Hello! I am Enclave's on-device assistant. Ask me anything to get started.".into(),
                    },
                ],
            ),
        };

        Self {
            active_chat_id: chat_id,
            active_chat_name: name,
            input_text: String::new(),
            messages,
        }
    }
}

pub fn view<'a>(
    state: &'a ChatState,
    history_state: &'a crate::screens::history::HistoryState,
    app_theme: AppTheme,
) -> Element<'a, Message, Theme, iced::Renderer> {
    let is_dark = app_theme == AppTheme::Dark;

    // --- Render Left History Sidebar ---
    let history_sidebar = crate::screens::history::view(history_state, app_theme);

    // --- Render Chat Pane (Right side) ---
    // Top Bar
    let menu_btn = button(
        text(icons::ICON_MENU)
            .font(icons::ICON_FONT)
            .size(20)
            .shaping(iced::widget::text::Shaping::Advanced)
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
    })))
    .on_press(Message::NavigateTo(crate::Screen::Studio)); // Return to Studio

    let header_title = text(&state.active_chat_name)
        .font(icons::FONT_BOLD)
        .size(16)
        .style(if is_dark {
            theme::DARK_TEXT
        } else {
            theme::LIGHT_TEXT
        });

    let new_chat_btn = button(
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
    })))
    .on_press(Message::NavigateTo(crate::Screen::Chat {
        chat_id: "new_chat".into(),
    }));

    let chat_top_bar = container(
        row![
            menu_btn,
            horizontal_space().width(Length::FillPortion(1)),
            header_title,
            horizontal_space().width(Length::FillPortion(1)),
            new_chat_btn
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

    // Message Thread
    let mut message_list = Column::new().spacing(16);

    for msg in &state.messages {
        let is_user = msg.sender == MessageSender::User;

        let content = text(&msg.text)
            .font(icons::FONT_REGULAR)
            .size(14)
            .style(if is_user {
                iced::Color::BLACK
            } else if is_dark {
                theme::DARK_TEXT
            } else {
                theme::LIGHT_TEXT
            });

        let msg_card =
            container(content)
                .padding(14)
                .max_width(550)
                .style(iced::theme::Container::Custom(Box::new(
                    theme::NeoContainer {
                        is_surface: !is_user,
                        has_shadow: false,
                        theme: if is_user { AppTheme::Light } else { app_theme },
                    },
                )));

        let bubble_layout = if is_user {
            row![horizontal_space().width(Length::Fill), msg_card]
        } else {
            row![msg_card, horizontal_space().width(Length::Fill)]
        };

        message_list = message_list.push(bubble_layout);
    }

    let scroll_messages = scrollable(container(message_list).padding(20).width(Length::Fill))
        .height(Length::Fill)
        .style(iced::theme::Scrollable::Custom(Box::new(
            theme::NeoScrollable { theme: app_theme },
        )));

    // Bottom Input Bar
    let mic_btn = button(
        text(icons::ICON_MIC)
            .font(icons::ICON_FONT)
            .size(20)
            .shaping(iced::widget::text::Shaping::Advanced)
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
    })))
    .on_press(Message::ChatMicPressed);

    let file_btn = button(
        text(icons::ICON_ATTACH)
            .font(icons::ICON_FONT)
            .size(20)
            .shaping(iced::widget::text::Shaping::Advanced)
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
    })))
    .on_press(Message::ChatFilePressed);

    let prompt_input = text_input("Type a message...", &state.input_text)
        .on_input(Message::ChatInputChange)
        .on_submit(Message::ChatSendMessage)
        .padding(10)
        .style(iced::theme::TextInput::Custom(Box::new(theme::NeoInput {
            theme: app_theme,
        })));

    let send_btn = button(
        text(icons::ICON_SEND)
            .font(icons::ICON_FONT)
            .size(16)
            .shaping(iced::widget::text::Shaping::Advanced)
            .horizontal_alignment(alignment::Horizontal::Center)
            .vertical_alignment(alignment::Vertical::Center)
            .style(iced::Color::BLACK),
    )
    .padding(10)
    .width(Length::Fixed(44.0))
    .height(Length::Fixed(44.0))
    .style(iced::theme::Button::Custom(Box::new(theme::NeoButton {
        is_accent: true,
        has_shadow: true,
        theme: app_theme,
    })))
    .on_press(Message::ChatSendMessage);

    let bottom_dock = container(
        row![mic_btn, file_btn, prompt_input, send_btn]
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

    let chat_pane = column![chat_top_bar, scroll_messages, bottom_dock]
        .width(Length::Fill)
        .height(Length::Fill);

    // Border line divider
    let divider = container(horizontal_space().width(Length::Fixed(2.0)))
        .height(Length::Fill)
        .style(iced::theme::Container::Custom(Box::new(
            theme::NeoContainer {
                is_surface: false,
                has_shadow: false,
                theme: app_theme,
            },
        )));

    // Assemble Left Sidebar and Right Chat Pane side by side
    let split_layout = row![history_sidebar, divider, chat_pane]
        .width(Length::Fill)
        .height(Length::Fill);

    container(split_layout)
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

//noinspection SpellCheckingInspection
use iced::widget::{button, column, container, mouse_area, row, space, text, text_editor};
use iced::{window, Alignment, Element, Length, Padding, Subscription, Task, Theme};

mod icons;
mod screens;
mod theme;
mod widgets;

pub fn main() -> iced::Result {
    iced::application(boot, update, view)
        .title(title)
        .theme(app_theme)
        .window(window::Settings {
            size: iced::Size::new(900.0, 750.0),
            resizable: true,
            decorations: false,
            ..window::Settings::default()
        })
        .default_font(icons::FONT_REGULAR)
        .font(icons::FONT_REGULAR_BYTES)
        .font(icons::FONT_MEDIUM_BYTES)
        .font(icons::FONT_SEMIBOLD_BYTES)
        .font(icons::FONT_BOLD_BYTES)
        .font(icons::FONT_SERIF_REGULAR_BYTES)
        .font(icons::FONT_SERIF_BOLD_BYTES)
        .subscription(subscription)
        .run()
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Screen {
    Studio,
    Chat { chat_id: String },
    Settings,
    Gallery { selected_album_id: Option<String> },
}

struct EnclaveApp {
    current_screen: Screen,
    back_stack: Vec<Screen>,
    app_theme: theme::AppTheme,
    window: window::Id,

    studio_state: screens::studio::StudioState,
    history_state: screens::history::HistoryState,
    chat_state: Option<screens::chat::ChatState>,
    settings_state: screens::settings::SettingsState,
    gallery_state: screens::gallery::GalleryState,

    /// Mock streaming state: index of the assistant message being streamed.
    streaming_message_index: Option<usize>,
    /// Remaining chunks for the mock stream.
    pending_stream_chunks: Vec<String>,
}

#[derive(Debug, Clone)]
pub enum Message {
    NavigateTo(Screen),
    NavigateBack,
    ThemeChanged(theme::AppTheme),
    WindowClose,
    WindowMinimize,
    WindowMaximize,
    WindowDrag,
    StudioPromptChanged(String),
    StudioGenerate,
    HistorySearchChanged(String),
    HistoryPinChat(String),
    HistoryUnpinChat(String),
    HistoryDeleteChat(String),
    HistoryRenameChat(String),
    HistoryHoverChat(Option<String>),
    HistorySidebarResize(f32),
    /// Toggle the profile popover over the history sidebar footer card.
    /// Fired by the card's tap target and the popover's outside-click close.
    HistoryToggleProfileMenu,
    /// Mock Google sign-in — flips `HistoryState::auth` to the seed
    /// `Authenticated` profile. A real desktop auth port would route through
    /// the domain `AuthManager` here instead.
    HistorySignIn,
    /// Mock sign-out — flips `HistoryState::auth` to `Unauthenticated` and
    /// dismisses the popover.
    HistorySignOut,
    ChatEditorAction(text_editor::Action),
    ChatSendMessage,
    ChatStreamChunk(String),
    ChatStreamTick,
    ChatStreamDone,
    ChatMicPressed,
    ChatFilePressed,
    ChatToggleMenu,
    ChatTogglePermPicker,
    ChatToggleModelPicker,
    ChatSelectPermission(screens::chat::PermissionMode),
    ChatSelectModel(String),
    ChatToggleStatsSheet,
    ChatAnimTick,
    MarkdownLinkClicked(String),
    /// Copy the raw source of a fenced code block to the system clipboard.
    /// Clones the fenced block source (without fence lines) when the copy button
    /// is built so shifting block indices during streaming cannot misroute the copy.
    CopyCodeBlock(String),
    WindowReady(window::Id),
    GallerySelectAlbum(Option<String>),
}

const MOCK_STREAM_RESPONSE: &str = r#"## Streaming Markdown Demo

This response arrives **incrementally** with live formatting.

- First item
- Second item with `inline code`

```rust
fn hello(name: &str) -> String {
    format!("Hello, {name}!")
}
```

See [Enclave](https://example.com) for more.
"#;

fn boot() -> (EnclaveApp, Task<Message>) {
    (
        EnclaveApp {
            current_screen: Screen::Chat {
                chat_id: "chat_1".into(),
            },
            back_stack: Vec::new(),
            app_theme: theme::AppTheme::Dark,
            window: window::Id::unique(),
            studio_state: screens::studio::StudioState::new(),
            history_state: screens::history::HistoryState::new(),
            chat_state: Some(screens::chat::ChatState::new("chat_1".into())),
            settings_state: screens::settings::SettingsState::new(),
            gallery_state: screens::gallery::GalleryState::new(),
            streaming_message_index: None,
            pending_stream_chunks: Vec::new(),
        },
        window::oldest()
            .map(|maybe_id| Message::WindowReady(maybe_id.unwrap_or_else(window::Id::unique))),
    )
}

fn title(state: &EnclaveApp) -> String {
    match &state.current_screen {
        Screen::Studio => "Enclave - Studio".to_string(),
        Screen::Chat { .. } => "Enclave - Chat".to_string(),
        Screen::Settings => "Enclave - Settings".to_string(),
        Screen::Gallery { .. } => "Enclave - Gallery".to_string(),
    }
}

fn app_theme(state: &EnclaveApp) -> Theme {
    match state.app_theme {
        theme::AppTheme::Dark => Theme::Dark,
        theme::AppTheme::Light => Theme::Light,
    }
}

fn titlebar_control_button(
    handle: &iced::widget::svg::Handle,
    icon_color: iced::Color,
    app_theme: theme::AppTheme,
    is_accent: bool,
    on_press: Message,
) -> button::Button<'_, Message, Theme, iced::Renderer> {
    button(
        container(
            icons::render_svg(handle, icon_color)
                .width(Length::Fixed(12.0))
                .height(Length::Fixed(12.0)),
        )
        .width(Length::Fill)
        .height(Length::Fill)
        .align_x(Alignment::Center)
        .align_y(Alignment::Center),
    )
    .width(Length::Fixed(32.0))
    .height(Length::Fixed(32.0))
    .padding(0)
    .style(theme::neo_button(theme::NeoButton {
        is_accent,
        has_shadow: false,
        theme: app_theme,
    }))
    .on_press(on_press)
}

fn render_titlebar(state: &EnclaveApp) -> Element<'_, Message, Theme, iced::Renderer> {
    let is_dark = state.app_theme == theme::AppTheme::Dark;
    let text_color = if is_dark {
        theme::DARK_TEXT
    } else {
        theme::LIGHT_TEXT
    };

    let title_content = row![
        space::horizontal().width(Length::Fixed(12.0)),
        text("Enclave")
            .font(icons::FONT_BOLD)
            .size(14)
            .color(text_color),
        space::horizontal().width(Length::Fill)
    ]
    .align_y(Alignment::Center)
    .width(Length::Fill)
    .height(Length::Fill);

    let drag_area = mouse_area(title_content).on_press(Message::WindowDrag);

    let control_color = if is_dark {
        theme::DARK_TEXT
    } else {
        theme::LIGHT_TEXT
    };

    let window_controls = row![
        titlebar_control_button(
            &icons::HANDLE_MINIMIZE,
            control_color,
            state.app_theme,
            false,
            Message::WindowMinimize,
        ),
        titlebar_control_button(
            &icons::HANDLE_MAXIMIZE,
            control_color,
            state.app_theme,
            false,
            Message::WindowMaximize,
        ),
        titlebar_control_button(
            &icons::HANDLE_CLOSE,
            iced::Color::BLACK,
            state.app_theme,
            true,
            Message::WindowClose,
        ),
    ]
    .spacing(8)
    .align_y(Alignment::Center);

    container(
        row![drag_area, window_controls]
            .align_y(Alignment::Center)
            .padding(Padding::from([0, 12]))
            .width(Length::Fill)
            .height(Length::Fill),
    )
    .width(Length::Fill)
    .height(Length::Fixed(44.0))
    .style(theme::neo_container(theme::NeoContainer {
        is_surface: true,
        has_shadow: false,
        theme: state.app_theme,
    }))
    .into()
}

fn split_mock_stream(text: &str) -> Vec<String> {
    text.chars()
        .collect::<Vec<_>>()
        .chunks(12)
        .map(|chunk| chunk.iter().collect())
        .collect()
}

fn append_stream_chunk_to_message(state: &mut EnclaveApp, chunk: &str) {
    if let (Some(index), Some(chat)) = (state.streaming_message_index, state.chat_state.as_mut()) {
        if let Some(msg) = chat.messages.get_mut(index) {
            msg.append_stream_chunk(chunk);
        }
    }
}

fn update(state: &mut EnclaveApp, message: Message) -> Task<Message> {
    match message {
        Message::WindowReady(id) => {
            state.window = id;
            window::maximize(id, true)
        }
        Message::WindowClose => window::close(state.window),
        Message::WindowMinimize => window::minimize(state.window, true),
        Message::WindowMaximize => window::toggle_maximize(state.window),
        Message::WindowDrag => window::drag(state.window),

        Message::NavigateTo(mut screen) => {
            state.back_stack.push(state.current_screen.clone());
            // Closing the profile popover on navigation avoids a stale open
            // flag lingering over a rebuilt sidebar.
            state.history_state.profile_menu_open = false;

            if let Screen::Chat { ref mut chat_id } = screen {
                if chat_id == "active" {
                    if let Some(ref chat) = state.chat_state {
                        *chat_id = chat.active_chat_id.clone();
                    } else {
                        *chat_id = "chat_1".into();
                    }
                }
                state.chat_state = Some(screens::chat::ChatState::new(chat_id.clone()));
            }

            state.current_screen = screen;
            Task::none()
        }

        Message::NavigateBack => {
            if let Some(prev_screen) = state.back_stack.pop() {
                state.current_screen = prev_screen;
            } else {
                state.current_screen = Screen::Studio;
            }
            Task::none()
        }

        Message::ThemeChanged(new_theme) => {
            state.app_theme = new_theme;
            state.settings_state.active_theme = new_theme;
            Task::none()
        }

        Message::StudioPromptChanged(val) => {
            state.studio_state.prompt = val;
            state.studio_state.status_message = None;
            Task::none()
        }

        Message::StudioGenerate => {
            if !state.studio_state.prompt.trim().is_empty() {
                state.studio_state.is_generating = true;
                state.studio_state.status_message =
                    Some(format!("Generating: \"{}\"", state.studio_state.prompt));
            }
            Task::none()
        }

        Message::HistorySearchChanged(val) => {
            state.history_state.search_query = val;
            Task::none()
        }

        Message::HistoryPinChat(chat_id) => {
            if let Some(chat) = state
                .history_state
                .chats
                .iter_mut()
                .find(|c| c.id == chat_id)
            {
                chat.is_pinned = true;
            }
            Task::none()
        }

        Message::HistoryUnpinChat(chat_id) => {
            if let Some(chat) = state
                .history_state
                .chats
                .iter_mut()
                .find(|c| c.id == chat_id)
            {
                chat.is_pinned = false;
            }
            Task::none()
        }

        Message::HistoryDeleteChat(chat_id) => {
            state.history_state.chats.retain(|c| c.id != chat_id);
            if state.history_state.hovered_chat_id.as_deref() == Some(chat_id.as_str()) {
                state.history_state.hovered_chat_id = None;
            }
            Task::none()
        }

        Message::HistoryRenameChat(_chat_id) => Task::none(),

        Message::HistoryHoverChat(chat_id) => {
            state.history_state.hovered_chat_id = chat_id;
            Task::none()
        }

        Message::HistorySidebarResize(delta) => {
            state.history_state.apply_sidebar_resize_delta(delta);
            Task::none()
        }

        Message::HistoryToggleProfileMenu => {
            state.history_state.profile_menu_open = !state.history_state.profile_menu_open;
            Task::none()
        }

        Message::HistorySignIn => {
            state.history_state.auth = screens::history::AuthState::default();
            state.history_state.profile_menu_open = false;
            Task::none()
        }

        Message::HistorySignOut => {
            state.history_state.auth = screens::history::AuthState::Unauthenticated;
            state.history_state.profile_menu_open = false;
            Task::none()
        }

        Message::ChatEditorAction(action) => {
            if let Some(ref mut chat) = state.chat_state {
                chat.input_content.perform(action);
            }
            Task::none()
        }

        Message::ChatSendMessage => {
            if let Some(ref mut chat) = state.chat_state {
                let user_text = chat.input_content.text().trim().to_string();
                if !user_text.is_empty() {
                    if chat.anim == screens::chat::InputBarAnim::Centered {
                        chat.anim = screens::chat::InputBarAnim::Falling { progress: 0.0 };
                    }
                    chat.menu_open = false;
                    chat.perm_picker_open = false;
                    chat.model_picker_open = false;
                    chat.stats_sheet_open = false;

                    chat.messages
                        .push(screens::chat::ChatMessage::user(user_text));
                    chat.input_content = text_editor::Content::default();

                    let assistant_index = chat.messages.len();
                    chat.messages
                        .push(screens::chat::ChatMessage::streaming_assistant());
                    state.streaming_message_index = Some(assistant_index);
                    state.pending_stream_chunks = split_mock_stream(MOCK_STREAM_RESPONSE);
                }
            }
            Task::none()
        }

        Message::ChatStreamChunk(chunk) => {
            append_stream_chunk_to_message(state, &chunk);
            Task::none()
        }

        Message::ChatStreamTick => {
            if let Some(chunk) = state.pending_stream_chunks.first().cloned() {
                state.pending_stream_chunks.remove(0);
                append_stream_chunk_to_message(state, &chunk);
                if state.pending_stream_chunks.is_empty() {
                    if let (Some(index), Some(chat)) = (
                        state.streaming_message_index.take(),
                        state.chat_state.as_mut(),
                    ) {
                        if let Some(msg) = chat.messages.get_mut(index) {
                            msg.finalize_stream();
                        }
                    }
                }
            }
            Task::none()
        }

        Message::ChatStreamDone => Task::none(),

        Message::ChatMicPressed => Task::none(),

        Message::ChatFilePressed => {
            if let Some(ref mut chat) = state.chat_state {
                chat.menu_open = false;
            }
            Task::none()
        }

        Message::ChatToggleMenu => {
            if let Some(ref mut chat) = state.chat_state {
                chat.menu_open = !chat.menu_open;
                chat.perm_picker_open = false;
                chat.model_picker_open = false;
                chat.stats_sheet_open = false;
            }
            Task::none()
        }

        Message::ChatTogglePermPicker => {
            if let Some(ref mut chat) = state.chat_state {
                chat.perm_picker_open = !chat.perm_picker_open;
                chat.menu_open = false;
                chat.model_picker_open = false;
                chat.stats_sheet_open = false;
            }
            Task::none()
        }

        Message::ChatToggleModelPicker => {
            if let Some(ref mut chat) = state.chat_state {
                chat.model_picker_open = !chat.model_picker_open;
                chat.menu_open = false;
                chat.perm_picker_open = false;
                chat.stats_sheet_open = false;
            }
            Task::none()
        }

        Message::ChatSelectPermission(mode) => {
            if let Some(ref mut chat) = state.chat_state {
                chat.permission_mode = mode;
                chat.perm_picker_open = false;
            }
            Task::none()
        }

        Message::ChatSelectModel(model) => {
            if let Some(ref mut chat) = state.chat_state {
                chat.selected_model = model;
                chat.model_picker_open = false;
            }
            Task::none()
        }

        Message::ChatToggleStatsSheet => {
            if let Some(ref mut chat) = state.chat_state {
                chat.stats_sheet_open = !chat.stats_sheet_open;
                chat.menu_open = false;
                chat.perm_picker_open = false;
                chat.model_picker_open = false;
            }
            Task::none()
        }

        Message::ChatAnimTick => {
            if let Some(ref mut chat) = state.chat_state {
                chat.anim = screens::chat::advance_anim(chat.anim, 0.08);
            }
            Task::none()
        }

        Message::MarkdownLinkClicked(url) => {
            eprintln!("Markdown link clicked: {url}");
            Task::none()
        }

        Message::CopyCodeBlock(code) => iced::clipboard::write(code),

        Message::GallerySelectAlbum(album_id) => {
            state.gallery_state.selected_album_id = album_id.clone();
            if let Screen::Gallery {
                ref mut selected_album_id,
            } = state.current_screen
            {
                *selected_album_id = album_id;
            }
            Task::none()
        }
    }
}

fn view(state: &EnclaveApp) -> Element<'_, Message, Theme, iced::Renderer> {
    let titlebar = render_titlebar(state);
    let content = match &state.current_screen {
        Screen::Studio => screens::studio::view(&state.studio_state, state.app_theme),
        Screen::Chat { .. } => {
            if let Some(ref chat) = state.chat_state {
                screens::chat::view(chat, &state.history_state, state.app_theme)
            } else {
                text("Chat state missing").into()
            }
        }
        Screen::Settings => screens::settings::view(&state.settings_state, state.app_theme),
        Screen::Gallery { .. } => screens::gallery::view(&state.gallery_state, state.app_theme),
    };

    column![titlebar, content]
        .width(Length::Fill)
        .height(Length::Fill)
        .into()
}

fn subscription(state: &EnclaveApp) -> Subscription<Message> {
    let mut subs = Vec::new();

    if let Some(ref chat) = state.chat_state {
        if matches!(chat.anim, screens::chat::InputBarAnim::Falling { .. }) {
            subs.push(
                iced::time::every(std::time::Duration::from_millis(16))
                    .map(|_| Message::ChatAnimTick),
            );
        }
    }

    if !state.pending_stream_chunks.is_empty() {
        subs.push(
            iced::time::every(std::time::Duration::from_millis(40))
                .map(|_| Message::ChatStreamTick),
        );
    }

    Subscription::batch(subs)
}

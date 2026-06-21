use iced::widget::{
    button, column, container, horizontal_space, mouse_area, row, text, text_editor,
};
use iced::{
    executor, Alignment, Application, Command, Element, Length, Padding, Settings, Subscription,
    Theme,
};

mod icons;
mod screens;
mod theme;
mod widgets;

pub fn main() -> iced::Result {
    let mut settings = Settings::default();
    settings.window.size = iced::Size::new(900.0, 750.0);
    settings.window.resizable = true;
    settings.window.decorations = false;

    // Default font setup
    settings.default_font = icons::FONT_REGULAR;
    settings.fonts = vec![
        std::borrow::Cow::Borrowed(icons::FONT_REGULAR_BYTES),
        std::borrow::Cow::Borrowed(icons::FONT_BOLD_BYTES),
    ];

    EnclaveApp::run(settings)
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

    // Modular Screen States
    studio_state: screens::studio::StudioState,
    history_state: screens::history::HistoryState,
    chat_state: Option<screens::chat::ChatState>,
    settings_state: screens::settings::SettingsState,
    gallery_state: screens::gallery::GalleryState,
}

#[derive(Debug, Clone)]
pub enum Message {
    // Navigation
    NavigateTo(Screen),
    NavigateBack,

    // Theme
    ThemeChanged(theme::AppTheme),

    // Window controls
    WindowClose,
    WindowMinimize,
    WindowMaximize,
    WindowDrag,

    // Screen actions - Studio
    StudioPromptChanged(String),
    StudioGenerate,

    // Screen actions - History
    HistorySearchChanged(String),
    HistoryPinChat(String),
    HistoryUnpinChat(String),
    HistoryDeleteChat(String),
    HistoryRenameChat(String),
    HistoryHoverChat(Option<String>),
    HistorySidebarResize(f32),

    ChatEditorAction(text_editor::Action),
    ChatSendMessage,
    ChatMicPressed,
    ChatFilePressed,
    ChatToggleMenu,
    ChatTogglePermPicker,
    ChatToggleModelPicker,
    ChatSelectPermission(screens::chat::PermissionMode),
    ChatSelectModel(String),
    ChatContextPressed,
    ChatAnimTick,

    // Screen actions - Gallery
    GallerySelectAlbum(Option<String>),
}

impl EnclaveApp {
    fn render_titlebar(&self) -> Element<'_, Message, Theme, iced::Renderer> {
        let is_dark = self.app_theme == theme::AppTheme::Dark;
        let text_color = if is_dark {
            theme::DARK_TEXT
        } else {
            theme::LIGHT_TEXT
        };

        // Draggable region containing the app name/logo
        let title_content = row![
            horizontal_space().width(Length::Fixed(12.0)),
            text("Enclave")
                .font(icons::FONT_BOLD)
                .size(14)
                .style(text_color),
            horizontal_space().width(Length::Fill)
        ]
        .align_items(Alignment::Center)
        .width(Length::Fill)
        .height(Length::Fill);

        // Wrap the draggable title in a mouse area to handle window dragging
        let drag_area = mouse_area(title_content).on_press(Message::WindowDrag);

        let control_color = if is_dark {
            theme::DARK_TEXT
        } else {
            theme::LIGHT_TEXT
        };

        // Custom styled window controls
        let window_controls = row![
            // Minimize
            button(
                container(
                    icons::render_svg(&icons::HANDLE_MINIMIZE, control_color)
                        .width(Length::Fixed(12.0))
                        .height(Length::Fixed(12.0))
                )
                .width(Length::Fill)
                .height(Length::Fill)
                .center_x()
                .center_y()
            )
            .width(Length::Fixed(32.0))
            .height(Length::Fixed(32.0))
            .padding(0)
            .style(iced::theme::Button::Custom(Box::new(theme::NeoButton {
                is_accent: false,
                has_shadow: false,
                theme: self.app_theme,
            })))
            .on_press(Message::WindowMinimize),
            // Maximize
            button(
                container(
                    icons::render_svg(&icons::HANDLE_MAXIMIZE, control_color)
                        .width(Length::Fixed(12.0))
                        .height(Length::Fixed(12.0))
                )
                .width(Length::Fill)
                .height(Length::Fill)
                .center_x()
                .center_y()
            )
            .width(Length::Fixed(32.0))
            .height(Length::Fixed(32.0))
            .padding(0)
            .style(iced::theme::Button::Custom(Box::new(theme::NeoButton {
                is_accent: false,
                has_shadow: false,
                theme: self.app_theme,
            })))
            .on_press(Message::WindowMaximize),
            // Close
            button(
                container(
                    icons::render_svg(&icons::HANDLE_CLOSE, iced::Color::BLACK)
                        .width(Length::Fixed(12.0))
                        .height(Length::Fixed(12.0))
                )
                .width(Length::Fill)
                .height(Length::Fill)
                .center_x()
                .center_y()
            )
            .width(Length::Fixed(32.0))
            .height(Length::Fixed(32.0))
            .padding(0)
            .style(iced::theme::Button::Custom(Box::new(theme::NeoButton {
                is_accent: true, // Peach accent for close
                has_shadow: false,
                theme: self.app_theme,
            })))
            .on_press(Message::WindowClose),
        ]
        .spacing(8)
        .align_items(Alignment::Center);

        // Entire header container
        container(
            row![drag_area, window_controls]
                .align_items(Alignment::Center)
                .padding(Padding::from([0, 12]))
                .width(Length::Fill)
                .height(Length::Fill),
        )
        .width(Length::Fill)
        .height(Length::Fixed(44.0))
        .style(iced::theme::Container::Custom(Box::new(
            theme::NeoContainer {
                is_surface: true,
                has_shadow: false,
                theme: self.app_theme,
            },
        )))
        .into()
    }
}

impl Application for EnclaveApp {
    type Executor = executor::Default;
    type Message = Message;
    type Theme = Theme;
    type Flags = ();

    fn new(_flags: Self::Flags) -> (Self, Command<Self::Message>) {
        (
            Self {
                current_screen: Screen::Chat {
                    chat_id: "chat_1".into(),
                },
                back_stack: Vec::new(),
                app_theme: theme::AppTheme::Dark,
                studio_state: screens::studio::StudioState::new(),
                history_state: screens::history::HistoryState::new(),
                chat_state: Some(screens::chat::ChatState::new("chat_1".into())),
                settings_state: screens::settings::SettingsState::new(),
                gallery_state: screens::gallery::GalleryState::new(),
            },
            // Maximize on launch so the window is deterministic for validation
            // (screenshots, xdotool automation) and fills the workspace for the
            // custom-decorated chat UI.
            iced::window::maximize(iced::window::Id::MAIN, true),
        )
    }

    fn title(&self) -> String {
        match &self.current_screen {
            Screen::Studio => "Enclave - Studio".to_string(),
            Screen::Chat { .. } => "Enclave - Chat".to_string(),
            Screen::Settings => "Enclave - Settings".to_string(),
            Screen::Gallery { .. } => "Enclave - Gallery".to_string(),
        }
    }

    fn update(&mut self, message: Self::Message) -> Command<Self::Message> {
        match message {
            Message::WindowClose => iced::window::close(iced::window::Id::MAIN),
            Message::WindowMinimize => iced::window::minimize(iced::window::Id::MAIN, true),
            Message::WindowMaximize => iced::window::toggle_maximize(iced::window::Id::MAIN),
            Message::WindowDrag => iced::window::drag(iced::window::Id::MAIN),

            Message::NavigateTo(mut screen) => {
                // Save current screen to back stack
                self.back_stack.push(self.current_screen.clone());

                // If navigating to Chat, construct chat state on demand
                if let Screen::Chat { ref mut chat_id } = screen {
                    if chat_id == "active" {
                        if let Some(ref chat) = self.chat_state {
                            *chat_id = chat.active_chat_id.clone();
                        } else {
                            *chat_id = "chat_1".into(); // Fallback to first chat
                        }
                    }
                    self.chat_state = Some(screens::chat::ChatState::new(chat_id.clone()));
                }

                self.current_screen = screen;
                Command::none()
            }

            Message::NavigateBack => {
                if let Some(prev_screen) = self.back_stack.pop() {
                    self.current_screen = prev_screen;
                } else {
                    self.current_screen = Screen::Studio;
                }
                Command::none()
            }

            Message::ThemeChanged(new_theme) => {
                self.app_theme = new_theme;
                self.settings_state.active_theme = new_theme;
                Command::none()
            }

            // Studio Actions
            Message::StudioPromptChanged(val) => {
                self.studio_state.prompt = val;
                self.studio_state.status_message = None;
                Command::none()
            }

            Message::StudioGenerate => {
                if !self.studio_state.prompt.trim().is_empty() {
                    self.studio_state.is_generating = true;
                    self.studio_state.status_message =
                        Some(format!("Generating: \"{}\"", self.studio_state.prompt));
                }
                Command::none()
            }

            // History Actions
            Message::HistorySearchChanged(val) => {
                self.history_state.search_query = val;
                Command::none()
            }

            Message::HistoryPinChat(chat_id) => {
                if let Some(chat) = self
                    .history_state
                    .chats
                    .iter_mut()
                    .find(|c| c.id == chat_id)
                {
                    chat.is_pinned = true;
                }
                Command::none()
            }

            Message::HistoryUnpinChat(chat_id) => {
                if let Some(chat) = self
                    .history_state
                    .chats
                    .iter_mut()
                    .find(|c| c.id == chat_id)
                {
                    chat.is_pinned = false;
                }
                Command::none()
            }

            Message::HistoryDeleteChat(chat_id) => {
                self.history_state.chats.retain(|c| c.id != chat_id);
                if self.history_state.hovered_chat_id.as_deref() == Some(chat_id.as_str()) {
                    self.history_state.hovered_chat_id = None;
                }
                Command::none()
            }

            Message::HistoryRenameChat(_chat_id) => {
                // Rename flow is not wired yet; pencil affordance is reserved.
                Command::none()
            }

            Message::HistoryHoverChat(chat_id) => {
                self.history_state.hovered_chat_id = chat_id;
                Command::none()
            }

            Message::HistorySidebarResize(delta) => {
                self.history_state.apply_sidebar_resize_delta(delta);
                Command::none()
            }

            // Chat Actions
            Message::ChatEditorAction(action) => {
                if let Some(ref mut chat) = self.chat_state {
                    chat.input_content.perform(action);
                }
                Command::none()
            }

            Message::ChatSendMessage => {
                if let Some(ref mut chat) = self.chat_state {
                    let user_text = chat.input_content.text().trim().to_string();
                    if !user_text.is_empty() {
                        // Kick off the fall animation on the first send from the
                        // centered empty-state layout.
                        if chat.anim == screens::chat::InputBarAnim::Centered {
                            chat.anim = screens::chat::InputBarAnim::Falling { progress: 0.0 };
                        }
                        // Close any open popovers.
                        chat.menu_open = false;
                        chat.perm_picker_open = false;
                        chat.model_picker_open = false;

                        // Push user message
                        chat.messages.push(screens::chat::ChatMessage {
                            sender: screens::chat::MessageSender::User,
                            text: user_text,
                        });
                        chat.input_content = text_editor::Content::default();

                        // Push mock assistant response
                        chat.messages.push(screens::chat::ChatMessage {
                            sender: screens::chat::MessageSender::Assistant,
                            text: "This is a mock on-device AI response. In the future, this will compile the LLM inference pipeline through agent_core.".into(),
                        });
                    }
                }
                Command::none()
            }

            Message::ChatMicPressed => Command::none(),
            Message::ChatFilePressed => {
                // "Attach File" from the + menu — close the menu after the pick.
                if let Some(ref mut chat) = self.chat_state {
                    chat.menu_open = false;
                }
                Command::none()
            }

            Message::ChatToggleMenu => {
                if let Some(ref mut chat) = self.chat_state {
                    chat.menu_open = !chat.menu_open;
                    chat.perm_picker_open = false;
                    chat.model_picker_open = false;
                }
                Command::none()
            }

            Message::ChatTogglePermPicker => {
                if let Some(ref mut chat) = self.chat_state {
                    chat.perm_picker_open = !chat.perm_picker_open;
                    chat.menu_open = false;
                    chat.model_picker_open = false;
                }
                Command::none()
            }

            Message::ChatToggleModelPicker => {
                if let Some(ref mut chat) = self.chat_state {
                    chat.model_picker_open = !chat.model_picker_open;
                    chat.menu_open = false;
                    chat.perm_picker_open = false;
                }
                Command::none()
            }

            Message::ChatSelectPermission(mode) => {
                if let Some(ref mut chat) = self.chat_state {
                    chat.permission_mode = mode;
                    chat.perm_picker_open = false;
                }
                Command::none()
            }

            Message::ChatSelectModel(model) => {
                if let Some(ref mut chat) = self.chat_state {
                    chat.selected_model = model;
                    chat.model_picker_open = false;
                }
                Command::none()
            }

            Message::ChatContextPressed => Command::none(),

            Message::ChatAnimTick => {
                if let Some(ref mut chat) = self.chat_state {
                    chat.anim = screens::chat::advance_anim(chat.anim, 0.08);
                }
                Command::none()
            }

            // Gallery Actions
            Message::GallerySelectAlbum(album_id) => {
                self.gallery_state.selected_album_id = album_id.clone();
                // Sync current screen enum
                if let Screen::Gallery {
                    ref mut selected_album_id,
                } = self.current_screen
                {
                    *selected_album_id = album_id;
                }
                Command::none()
            }
        }
    }

    fn view(&self) -> Element<'_, Self::Message, Self::Theme, iced::Renderer> {
        let titlebar = self.render_titlebar();
        let content = match &self.current_screen {
            Screen::Studio => screens::studio::view(&self.studio_state, self.app_theme),
            Screen::Chat { .. } => {
                if let Some(ref chat) = self.chat_state {
                    screens::chat::view(chat, &self.history_state, self.app_theme)
                } else {
                    text("Chat state missing").into()
                }
            }
            Screen::Settings => screens::settings::view(&self.settings_state, self.app_theme),
            Screen::Gallery { .. } => screens::gallery::view(&self.gallery_state, self.app_theme),
        };

        column![titlebar, content]
            .width(Length::Fill)
            .height(Length::Fill)
            .into()
    }

    fn subscription(&self) -> Subscription<Self::Message> {
        // Only tick while the input bar is actively falling. Once it locks the
        // subscription goes quiet so we don't wake the runtime on every frame.
        if let Some(ref chat) = self.chat_state {
            if matches!(chat.anim, screens::chat::InputBarAnim::Falling { .. }) {
                return iced::time::every(std::time::Duration::from_millis(16))
                    .map(|_| Message::ChatAnimTick);
            }
        }
        Subscription::none()
    }
}

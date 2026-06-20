use iced::widget::text;
use iced::{executor, Application, Command, Element, Settings, Theme};

mod icons;
mod screens;
mod theme;

pub fn main() -> iced::Result {
    let mut settings = Settings::default();
    settings.window.size = iced::Size::new(900.0, 750.0);
    settings.window.resizable = true;

    // Default font setup
    settings.default_font = icons::FONT_REGULAR;

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
    FontLoaded(Result<(), iced::font::Error>),

    // Navigation
    NavigateTo(Screen),
    NavigateBack,

    // Theme
    ThemeChanged(theme::AppTheme),

    // Screen actions - Studio
    StudioPromptChanged(String),
    StudioGenerate,

    // Screen actions - History
    HistorySearchChanged(String),
    HistoryPinChat(String),
    HistoryUnpinChat(String),
    HistoryDeleteChat(String),

    // Screen actions - Chat
    ChatInputChange(String),
    ChatSendMessage,

    // Screen actions - Gallery
    GallerySelectAlbum(Option<String>),
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
            Command::batch(vec![
                iced::font::load(std::borrow::Cow::Borrowed(icons::ICON_FONT_BYTES))
                    .map(Message::FontLoaded),
                iced::font::load(std::borrow::Cow::Borrowed(icons::FONT_REGULAR_BYTES))
                    .map(Message::FontLoaded),
                iced::font::load(std::borrow::Cow::Borrowed(icons::FONT_BOLD_BYTES))
                    .map(Message::FontLoaded),
            ]),
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
            Message::FontLoaded(_) => Command::none(),

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
                Command::none()
            }

            // Chat Actions
            Message::ChatInputChange(val) => {
                if let Some(ref mut chat) = self.chat_state {
                    chat.input_text = val;
                }
                Command::none()
            }

            Message::ChatSendMessage => {
                if let Some(ref mut chat) = self.chat_state {
                    let user_text = chat.input_text.trim().to_string();
                    if !user_text.is_empty() {
                        // Push user message
                        chat.messages.push(screens::chat::ChatMessage {
                            sender: screens::chat::MessageSender::User,
                            text: user_text,
                        });
                        chat.input_text.clear();

                        // Push mock assistant response
                        chat.messages.push(screens::chat::ChatMessage {
                            sender: screens::chat::MessageSender::Assistant,
                            text: "This is a mock on-device AI response. In the future, this will compile the LLM inference pipeline through agent_core.".into(),
                        });
                    }
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
        match &self.current_screen {
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
        }
    }
}

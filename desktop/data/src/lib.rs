use enclave_desktop_domain::models::{ChatMessage, ChatSession, DesktopSettings};
use enclave_desktop_domain::repositories::{ChatRepository, SettingsRepository};
use std::sync::Mutex;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum DataError {
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
    #[error("Serialization error: {0}")]
    Serialization(#[from] serde_json::Error),
    #[error("Session not found: {0}")]
    SessionNotFound(String),
}

pub struct InMemoryChatRepository {
    sessions: Mutex<Vec<ChatSession>>,
    messages: Mutex<std::collections::HashMap<String, Vec<ChatMessage>>>,
}

impl Default for InMemoryChatRepository {
    fn default() -> Self {
        Self::new()
    }
}

impl InMemoryChatRepository {
    pub fn new() -> Self {
        Self {
            sessions: Mutex::new(vec![
                ChatSession {
                    id: "chat_1".into(),
                    title: "Analyzing Rust Iced performance".into(),
                    is_pinned: false,
                    created_at: 1718880000,
                    updated_at: 1718880000,
                },
                ChatSession {
                    id: "chat_2".into(),
                    title: "Android Compose compiler options".into(),
                    is_pinned: true,
                    created_at: 1718881000,
                    updated_at: 1718881000,
                },
            ]),
            messages: Mutex::new({
                let mut m = std::collections::HashMap::new();
                m.insert(
                    "chat_1".into(),
                    vec![
                        ChatMessage {
                            sender: enclave_desktop_domain::models::MessageSender::User,
                            text: "How does Iced compare to other UI toolkits on Linux?".into(),
                            timestamp: 1718880000,
                        },
                        ChatMessage {
                            sender: enclave_desktop_domain::models::MessageSender::Assistant,
                            text: "Iced is a cross-platform GUI library written in Rust. On Linux, it uses wgpu (WebGPU) or OpenGL by default, giving it hardware-accelerated rendering. Because of its Elm-inspired architecture, it maintains low memory overhead and high responsiveness. It is highly competitive with toolkits like GTK, Qt, or Slint, especially when you need a pure-Rust codebase.".into(),
                            timestamp: 1718880005,
                        },
                    ],
                );
                m.insert(
                    "chat_2".into(),
                    vec![
                        ChatMessage {
                            sender: enclave_desktop_domain::models::MessageSender::User,
                            text: "How do I optimize Jetpack Compose compile times?".into(),
                            timestamp: 1718881000,
                        },
                        ChatMessage {
                            sender: enclave_desktop_domain::models::MessageSender::Assistant,
                            text: "To optimize Compose compile times, ensure you are utilizing Hilt or Dagger efficiently to prevent KSP bottlenecking. You can also enable Compose compiler metrics in your gradle configuration to inspect unstable parameters causing excessive recompositions.".into(),
                            timestamp: 1718881005,
                        },
                    ],
                );
                m
            }),
        }
    }
}

impl ChatRepository for InMemoryChatRepository {
    type Error = DataError;

    async fn get_sessions(&self) -> Result<Vec<ChatSession>, Self::Error> {
        let sessions = self.sessions.lock().unwrap().clone();
        Ok(sessions)
    }

    async fn get_messages(&self, session_id: &str) -> Result<Vec<ChatMessage>, Self::Error> {
        let messages = self
            .messages
            .lock()
            .unwrap()
            .get(session_id)
            .cloned()
            .unwrap_or_default();
        Ok(messages)
    }

    async fn create_session(&self, title: &str) -> Result<ChatSession, Self::Error> {
        let mut sessions = self.sessions.lock().unwrap();
        let new_session = ChatSession {
            id: uuid::Uuid::new_v4().to_string(),
            title: title.to_string(),
            is_pinned: false,
            created_at: 1718882000, // mock timestamp
            updated_at: 1718882000,
        };
        sessions.push(new_session.clone());
        Ok(new_session)
    }

    async fn add_message(&self, session_id: &str, message: ChatMessage) -> Result<(), Self::Error> {
        let mut messages = self.messages.lock().unwrap();
        let list = messages.entry(session_id.to_string()).or_default();
        list.push(message);
        Ok(())
    }

    async fn delete_session(&self, session_id: &str) -> Result<(), Self::Error> {
        let mut sessions = self.sessions.lock().unwrap();
        sessions.retain(|s| s.id != session_id);
        let mut messages = self.messages.lock().unwrap();
        messages.remove(session_id);
        Ok(())
    }

    async fn set_pinned(&self, session_id: &str, pinned: bool) -> Result<(), Self::Error> {
        let mut sessions = self.sessions.lock().unwrap();
        if let Some(session) = sessions.iter_mut().find(|s| s.id == session_id) {
            session.is_pinned = pinned;
            Ok(())
        } else {
            Err(DataError::SessionNotFound(session_id.to_string()))
        }
    }
}

pub struct InMemorySettingsRepository {
    settings: Mutex<DesktopSettings>,
}

impl Default for InMemorySettingsRepository {
    fn default() -> Self {
        Self::new()
    }
}

impl InMemorySettingsRepository {
    pub fn new() -> Self {
        Self {
            settings: Mutex::new(DesktopSettings {
                theme: "Dark".to_string(),
                local_model_path: None,
                api_key: None,
                api_endpoint: None,
            }),
        }
    }
}

impl SettingsRepository for InMemorySettingsRepository {
    type Error = DataError;

    async fn get_settings(&self) -> Result<DesktopSettings, Self::Error> {
        let s = self.settings.lock().unwrap().clone();
        Ok(s)
    }

    async fn save_settings(&self, settings: DesktopSettings) -> Result<(), Self::Error> {
        let mut s = self.settings.lock().unwrap();
        *s = settings;
        Ok(())
    }
}

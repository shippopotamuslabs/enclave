use crate::models::{ChatMessage, ChatSession, DesktopSettings};

pub trait ChatRepository: Send + Sync {
    type Error: std::fmt::Debug + std::fmt::Display;

    async fn get_sessions(&self) -> Result<Vec<ChatSession>, Self::Error>;
    async fn get_messages(&self, session_id: &str) -> Result<Vec<ChatMessage>, Self::Error>;
    async fn create_session(&self, title: &str) -> Result<ChatSession, Self::Error>;
    async fn add_message(&self, session_id: &str, message: ChatMessage) -> Result<(), Self::Error>;
    async fn delete_session(&self, session_id: &str) -> Result<(), Self::Error>;
    async fn set_pinned(&self, session_id: &str, pinned: bool) -> Result<(), Self::Error>;
}

pub trait SettingsRepository: Send + Sync {
    type Error: std::fmt::Debug + std::fmt::Display;

    async fn get_settings(&self) -> Result<DesktopSettings, Self::Error>;
    async fn save_settings(&self, settings: DesktopSettings) -> Result<(), Self::Error>;
}

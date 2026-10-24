use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum MessageSender {
    User,
    Assistant,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ChatMessage {
    pub sender: MessageSender,
    pub text: String,
    pub timestamp: u64,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ChatSession {
    pub id: String,
    pub title: String,
    pub is_pinned: bool,
    pub created_at: u64,
    pub updated_at: u64,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DesktopSettings {
    pub theme: String,
    pub local_model_path: Option<String>,
    pub api_key: Option<String>,
    pub api_endpoint: Option<String>,
    /// The registry id of the model selected in the chat picker, persisted
    /// across restarts. `None` falls back to the Main-slot default.
    ///
    /// Stored in the `settings` KV row (no schema migration); a future
    /// full-Android-parity migration can move this to a dedicated
    /// `default_models` table without changing this field's meaning.
    #[serde(default)]
    pub selected_model_id: Option<String>,
}

//noinspection SpellCheckingInspection
use async_trait::async_trait;
use enclave_desktop_domain::download::ModelSlot;
use enclave_desktop_domain::models::{ChatMessage, ChatSession, DesktopSettings};
use enclave_desktop_domain::repositories::{
    ChatRepository, InstalledModelEntry, LocalModelRepository, SettingsRepository,
};
use std::sync::{Mutex, PoisonError};
use thiserror::Error;

pub mod download;
pub mod model_ports;
pub mod schema;
pub mod sqlite_repos;
pub mod vault;

pub use download::{
    download_file, download_files_concurrent, load_catalog, models_dir, scan_models, DownloadError,
    CONCURRENT_DOWNLOADS,
};
pub use enclave_desktop_domain::download::{
    DownloadFileSpec, DownloadFileStatus, DownloadProgress,
};
pub use model_ports::{FileModelCatalogService, FileModelDownloadService};
pub use sqlite_repos::{
    FactoryError, SqliteChatRepository, SqliteLocalModelRepository, SqliteRepositoryFactory,
    SqliteSettingsRepository,
};
pub use vault::{vault_exists_on_disk, SqlCipherVaultRepository, VaultError};

#[derive(Error, Debug)]
pub enum DataError {
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
    #[error("Serialization error: {0}")]
    Serialization(#[from] serde_json::Error),
    #[error("SQLite error: {0}")]
    Sqlite(#[from] rusqlite::Error),
    #[error("Session not found: {0}")]
    SessionNotFound(String),
    #[error("Lock poisoned: {0}")]
    LockPoisoned(String),
    #[error("Vault error: {0}")]
    Vault(#[from] vault::VaultError),
}

/// Map a `PoisonError` into the repo error type so we never call `unwrap()`
/// on a `Mutex::lock()` result (DESKTOP_RULES.md §5 forbids `unwrap()` in
/// production code).
impl<T> From<PoisonError<T>> for DataError {
    fn from(err: PoisonError<T>) -> Self {
        DataError::LockPoisoned(err.to_string())
    }
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
                            text: include_str!("../../seed/chat_1_assistant_demo.md").into(),
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

#[async_trait]
impl ChatRepository for InMemoryChatRepository {
    type Error = DataError;

    async fn get_sessions(&self) -> Result<Vec<ChatSession>, Self::Error> {
        let sessions = self.sessions.lock()?.clone();
        Ok(sessions)
    }

    async fn get_messages(&self, session_id: &str) -> Result<Vec<ChatMessage>, Self::Error> {
        let messages = self
            .messages
            .lock()?
            .get(session_id)
            .cloned()
            .unwrap_or_default();
        Ok(messages)
    }

    async fn create_session(&self, title: &str) -> Result<ChatSession, Self::Error> {
        let mut sessions = self.sessions.lock()?;
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
        let mut messages = self.messages.lock()?;
        let list = messages.entry(session_id.to_string()).or_default();
        list.push(message);
        Ok(())
    }

    async fn delete_session(&self, session_id: &str) -> Result<(), Self::Error> {
        let mut sessions = self.sessions.lock()?;
        sessions.retain(|s| s.id != session_id);
        let mut messages = self.messages.lock()?;
        messages.remove(session_id);
        Ok(())
    }

    async fn set_pinned(&self, session_id: &str, pinned: bool) -> Result<(), Self::Error> {
        let mut sessions = self.sessions.lock()?;
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
                selected_model_id: None,
            }),
        }
    }
}

#[async_trait]
impl SettingsRepository for InMemorySettingsRepository {
    type Error = DataError;

    async fn get_settings(&self) -> Result<DesktopSettings, Self::Error> {
        let s = self.settings.lock()?.clone();
        Ok(s)
    }

    async fn save_settings(&self, settings: DesktopSettings) -> Result<(), Self::Error> {
        let mut s = self.settings.lock()?;
        *s = settings;
        Ok(())
    }
}

pub struct InMemoryLocalModelRepository {
    entries: Mutex<Vec<InstalledModelEntry>>,
}

impl Default for InMemoryLocalModelRepository {
    fn default() -> Self {
        Self::new()
    }
}

impl InMemoryLocalModelRepository {
    pub fn new() -> Self {
        Self {
            entries: Mutex::new(Vec::new()),
        }
    }
}

#[async_trait]
impl LocalModelRepository for InMemoryLocalModelRepository {
    type Error = DataError;

    async fn register_installed(&self, entry: InstalledModelEntry) -> Result<(), Self::Error> {
        let mut entries = self.entries.lock()?;
        if let Some(existing) = entries.iter_mut().find(|e| e.id == entry.id) {
            *existing = entry;
        } else {
            entries.push(entry);
        }
        Ok(())
    }

    async fn find_by_slot(
        &self,
        slot: ModelSlot,
    ) -> Result<Option<InstalledModelEntry>, Self::Error> {
        let entries = self.entries.lock()?;
        Ok(entries.iter().find(|e| e.slot == Some(slot)).cloned())
    }

    async fn list_all(&self) -> Result<Vec<InstalledModelEntry>, Self::Error> {
        let mut entries = self.entries.lock()?.clone();
        entries.sort_by(|a, b| {
            a.display_name
                .to_lowercase()
                .cmp(&b.display_name.to_lowercase())
        });
        Ok(entries)
    }

    async fn find_by_id(&self, id: &str) -> Result<Option<InstalledModelEntry>, Self::Error> {
        let entries = self.entries.lock()?;
        Ok(entries.iter().find(|e| e.id == id).cloned())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use enclave_desktop_domain::download::{ModelFormat, ModelSlot};
    use enclave_desktop_domain::repositories::InstalledModelEntry;

    fn entry(id: &str, slot: Option<ModelSlot>, display_name: &str) -> InstalledModelEntry {
        InstalledModelEntry {
            id: id.into(),
            slot,
            sha256: format!("sha-{id}"),
            local_path: format!("/models/{id}.gguf"),
            display_name: display_name.into(),
            format: ModelFormat::GGUF,
        }
    }

    #[tokio::test]
    async fn in_memory_list_all_sorts_by_display_name() {
        let repo = InMemoryLocalModelRepository::new();
        repo.register_installed(entry("b", Some(ModelSlot::Main), "Zebra Model"))
            .await
            .expect("register b");
        repo.register_installed(entry("a", None, "Alpha Model"))
            .await
            .expect("register a");

        let all = repo.list_all().await.expect("list_all");
        assert_eq!(all.len(), 2);
        assert_eq!(all[0].id, "a");
        assert_eq!(all[1].id, "b");
    }

    #[tokio::test]
    async fn in_memory_find_by_id_round_trip() {
        let repo = InMemoryLocalModelRepository::new();
        repo.register_installed(entry("gemma", Some(ModelSlot::Main), "Gemma"))
            .await
            .expect("register");

        let found = repo
            .find_by_id("gemma")
            .await
            .expect("lookup")
            .expect("entry");
        assert_eq!(found.id, "gemma");
        assert_eq!(found.display_name, "Gemma");
    }

    #[tokio::test]
    async fn in_memory_find_by_id_none_for_unknown() {
        let repo = InMemoryLocalModelRepository::new();
        let found = repo.find_by_id("nope").await.expect("lookup");
        assert!(found.is_none());
    }

    #[tokio::test]
    async fn in_memory_find_by_slot_matches() {
        let repo = InMemoryLocalModelRepository::new();
        repo.register_installed(entry("main", Some(ModelSlot::Main), "Main"))
            .await
            .expect("register");
        let found = repo
            .find_by_slot(ModelSlot::Main)
            .await
            .expect("lookup")
            .expect("entry");
        assert_eq!(found.id, "main");
    }
}

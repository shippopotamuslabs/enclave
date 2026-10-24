use async_trait::async_trait;
use enclave_desktop_domain::download::{ModelFormat, ModelSlot};
use enclave_desktop_domain::models::{ChatMessage, ChatSession, DesktopSettings, MessageSender};
use enclave_desktop_domain::repositories::{
    ChatRepository, InstalledModelEntry, LocalModelRepository, SettingsRepository,
};
use rusqlite::Connection;
use std::sync::{Arc, Mutex};
use thiserror::Error;

use crate::vault::{downcast_connection, VaultError};
use crate::DataError;

/// Error returned by [`SqliteRepositoryFactory::from_vault`] when the
/// connection downcast fails. Repository methods themselves return
/// [`DataError`] (the unified error type shared with the in-memory repos).
#[derive(Error, Debug)]
pub enum FactoryError {
    #[error("Vault error: {0}")]
    Vault(#[from] VaultError),
}

/// Factory that builds SQLite-backed repositories from an `UnlockedVault`'s
/// connection handle. All repos share the single encrypted `Connection` via
/// `Arc<Mutex<Connection>>`; blocking SQLite calls run on `spawn_blocking` in
/// the async trait impls (DESKTOP_RULES.md §3).
pub struct SqliteRepositoryFactory {
    conn: Arc<Mutex<Connection>>,
}

impl SqliteRepositoryFactory {
    /// Build from the opaque `UnlockedVault::connection` handle. Downcasts
    /// back to `Arc<Mutex<Connection>>`; errors if the handle is the wrong type
    /// (shouldn't happen if the vault was opened by `SqlCipherVaultRepository`).
    pub fn from_vault(
        vault: &enclave_desktop_domain::vault::UnlockedVault,
    ) -> Result<Self, FactoryError> {
        let conn = downcast_connection(&vault.connection)?;
        Ok(Self { conn })
    }

    pub fn chat_repository(&self) -> Arc<dyn ChatRepository<Error = DataError>> {
        Arc::new(SqliteChatRepository {
            conn: self.conn.clone(),
        })
    }

    pub fn settings_repository(&self) -> Arc<dyn SettingsRepository<Error = DataError>> {
        Arc::new(SqliteSettingsRepository {
            conn: self.conn.clone(),
        })
    }

    pub fn local_model_repository(&self) -> Arc<dyn LocalModelRepository<Error = DataError>> {
        Arc::new(SqliteLocalModelRepository {
            conn: self.conn.clone(),
        })
    }
}

/// Run a blocking SQLite closure on the current async runtime via
/// `spawn_blocking`. The connection is locked inside the blocking task so the
/// mutex is never held across an `.await`.
///
/// The closure returns `Result<T, DataError>` so the lock-poison and SQLite
/// errors all flow through the unified error type.
async fn with_conn<T, F>(conn: Arc<Mutex<Connection>>, f: F) -> Result<T, DataError>
where
    T: Send + 'static,
    F: FnOnce(&Connection) -> Result<T, DataError> + Send + 'static,
{
    let result = tokio::task::spawn_blocking(move || {
        let guard = conn.lock()?;
        f(&guard)
    })
    .await
    .map_err(|e| DataError::LockPoisoned(format!("spawn_blocking join: {e}")))?;
    result
}

// ---------------------------------------------------------------------------
// Chat repository
// ---------------------------------------------------------------------------

pub struct SqliteChatRepository {
    conn: Arc<Mutex<Connection>>,
}

#[async_trait]
impl ChatRepository for SqliteChatRepository {
    type Error = DataError;

    async fn get_sessions(&self) -> Result<Vec<ChatSession>, Self::Error> {
        let conn = self.conn.clone();
        with_conn(conn, |c| {
            let mut stmt = c.prepare(
                "SELECT id, title, is_pinned, created_at, updated_at
                 FROM chat_sessions ORDER BY updated_at DESC",
            )?;
            let rows = stmt.query_map([], |r| {
                let created: i64 = r.get(3)?;
                let updated: i64 = r.get(4)?;
                Ok(ChatSession {
                    id: r.get(0)?,
                    title: r.get(1)?,
                    is_pinned: r.get::<_, i64>(2)? != 0,
                    created_at: created as u64,
                    updated_at: updated as u64,
                })
            })?;
            let mut out = Vec::new();
            for row in rows {
                out.push(row?);
            }
            Ok(out)
        })
        .await
    }

    async fn get_messages(&self, session_id: &str) -> Result<Vec<ChatMessage>, Self::Error> {
        let conn = self.conn.clone();
        let sid = session_id.to_string();
        with_conn(conn, move |c| {
            let mut stmt = c.prepare(
                "SELECT sender, text, timestamp FROM chat_messages
                 WHERE session_id = ?1 ORDER BY id ASC",
            )?;
            let rows = stmt.query_map(rusqlite::params![sid], |r| {
                let sender_str: String = r.get(0)?;
                let sender = if sender_str == "assistant" {
                    MessageSender::Assistant
                } else {
                    MessageSender::User
                };
                let ts: i64 = r.get(2)?;
                Ok(ChatMessage {
                    sender,
                    text: r.get(1)?,
                    timestamp: ts as u64,
                })
            })?;
            let mut out = Vec::new();
            for row in rows {
                out.push(row?);
            }
            Ok(out)
        })
        .await
    }

    async fn create_session(&self, title: &str) -> Result<ChatSession, Self::Error> {
        let conn = self.conn.clone();
        let title = title.to_string();
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0) as i64;
        with_conn(conn, move |c| {
            let id = uuid::Uuid::new_v4().to_string();
            c.execute(
                "INSERT INTO chat_sessions (id, title, is_pinned, created_at, updated_at)
                 VALUES (?1, ?2, 0, ?3, ?3)",
                rusqlite::params![id, title, now],
            )?;
            Ok(ChatSession {
                id,
                title,
                is_pinned: false,
                created_at: now as u64,
                updated_at: now as u64,
            })
        })
        .await
    }

    async fn add_message(&self, session_id: &str, message: ChatMessage) -> Result<(), Self::Error> {
        let conn = self.conn.clone();
        let sid = session_id.to_string();
        let sender = match message.sender {
            MessageSender::User => "user",
            MessageSender::Assistant => "assistant",
        };
        let text = message.text;
        let ts = message.timestamp as i64;
        with_conn(conn, move |c| {
            c.execute(
                "INSERT OR IGNORE INTO chat_sessions (id, title, is_pinned, created_at, updated_at)
                 VALUES (?1, ?2, 0, ?3, ?3)",
                rusqlite::params![sid, "Chat", ts],
            )?;
            c.execute(
                "INSERT INTO chat_messages (session_id, sender, text, timestamp)
                 VALUES (?1, ?2, ?3, ?4)",
                rusqlite::params![sid, sender, text, ts],
            )?;
            c.execute(
                "UPDATE chat_sessions SET updated_at = ?1 WHERE id = ?2",
                rusqlite::params![ts, sid],
            )?;
            Ok(())
        })
        .await
    }

    async fn delete_session(&self, session_id: &str) -> Result<(), Self::Error> {
        let conn = self.conn.clone();
        let sid = session_id.to_string();
        with_conn(conn, move |c| {
            c.execute(
                "DELETE FROM chat_messages WHERE session_id = ?1",
                rusqlite::params![sid],
            )?;
            c.execute(
                "DELETE FROM chat_sessions WHERE id = ?1",
                rusqlite::params![sid],
            )?;
            Ok(())
        })
        .await
    }

    async fn set_pinned(&self, session_id: &str, pinned: bool) -> Result<(), Self::Error> {
        let conn = self.conn.clone();
        let sid = session_id.to_string();
        let pin_i: i64 = if pinned { 1 } else { 0 };
        with_conn(conn, move |c| {
            let affected = c.execute(
                "UPDATE chat_sessions SET is_pinned = ?1 WHERE id = ?2",
                rusqlite::params![pin_i, sid],
            )?;
            if affected == 0 {
                return Err(DataError::SessionNotFound(sid));
            }
            Ok(())
        })
        .await
    }
}

// ---------------------------------------------------------------------------
// Settings repository
// ---------------------------------------------------------------------------

pub struct SqliteSettingsRepository {
    conn: Arc<Mutex<Connection>>,
}

const SETTINGS_KEY: &str = "desktop_settings";

#[async_trait]
impl SettingsRepository for SqliteSettingsRepository {
    type Error = DataError;

    async fn get_settings(&self) -> Result<DesktopSettings, Self::Error> {
        let conn = self.conn.clone();
        with_conn(conn, |c| {
            let row: Option<String> = c
                .query_row(
                    "SELECT value FROM settings WHERE key = ?1",
                    rusqlite::params![SETTINGS_KEY],
                    |r| r.get(0),
                )
                .map(Some)
                .or_else(|e| match e {
                    rusqlite::Error::QueryReturnedNoRows => Ok(None),
                    other => Err(other),
                })?;
            match row {
                Some(json) => {
                    let s: DesktopSettings = serde_json::from_str(&json)?;
                    Ok(s)
                }
                None => Ok(DesktopSettings {
                    theme: "Dark".to_string(),
                    local_model_path: None,
                    api_key: None,
                    api_endpoint: None,
                    selected_model_id: None,
                }),
            }
        })
        .await
    }

    async fn save_settings(&self, settings: DesktopSettings) -> Result<(), Self::Error> {
        let conn = self.conn.clone();
        let json = serde_json::to_string(&settings)?;
        with_conn(conn, move |c| {
            c.execute(
                "INSERT OR REPLACE INTO settings (key, value) VALUES (?1, ?2)",
                rusqlite::params![SETTINGS_KEY, json],
            )?;
            Ok(())
        })
        .await
    }
}

// ---------------------------------------------------------------------------
// Local model registry
// ---------------------------------------------------------------------------

pub struct SqliteLocalModelRepository {
    conn: Arc<Mutex<Connection>>,
}

fn format_to_db(format: ModelFormat) -> &'static str {
    match format {
        ModelFormat::GGUF => "gguf",
        ModelFormat::GGML => "ggml",
        ModelFormat::ONNX => "onnx",
    }
}

fn format_from_db(s: &str) -> Result<ModelFormat, DataError> {
    match s {
        "gguf" => Ok(ModelFormat::GGUF),
        "ggml" => Ok(ModelFormat::GGML),
        "onnx" => Ok(ModelFormat::ONNX),
        other => Err(DataError::LockPoisoned(format!(
            "unknown model format in registry: {other}"
        ))),
    }
}

#[async_trait]
impl LocalModelRepository for SqliteLocalModelRepository {
    type Error = DataError;

    async fn register_installed(&self, entry: InstalledModelEntry) -> Result<(), Self::Error> {
        let conn = self.conn.clone();
        let slot = entry.slot.map(|s| s.as_str().to_string());
        let format = format_to_db(entry.format).to_string();
        let installed_at = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);
        with_conn(conn, move |c| {
            c.execute(
                "INSERT OR REPLACE INTO model_registry
                    (id, slot, sha256, local_path, display_name, format, installed_at)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
                rusqlite::params![
                    entry.id,
                    slot,
                    entry.sha256,
                    entry.local_path,
                    entry.display_name,
                    format,
                    installed_at as i64,
                ],
            )?;
            Ok(())
        })
        .await
    }

    async fn find_by_slot(
        &self,
        slot: ModelSlot,
    ) -> Result<Option<InstalledModelEntry>, Self::Error> {
        let conn = self.conn.clone();
        let slot_str = slot.as_str().to_string();
        with_conn(conn, move |c| {
            let mut stmt = c.prepare(
                "SELECT id, slot, sha256, local_path, display_name, format
                 FROM model_registry WHERE slot = ?1 LIMIT 1",
            )?;
            let mut rows = stmt.query(rusqlite::params![slot_str])?;
            if let Some(row) = rows.next()? {
                Ok(Some(entry_from_row(row)?))
            } else {
                Ok(None)
            }
        })
        .await
    }

    async fn list_all(&self) -> Result<Vec<InstalledModelEntry>, Self::Error> {
        let conn = self.conn.clone();
        with_conn(conn, move |c| {
            let mut stmt = c.prepare(
                "SELECT id, slot, sha256, local_path, display_name, format
                 FROM model_registry ORDER BY display_name COLLATE NOCASE",
            )?;
            // Collect (raw_row, format_str) pairs; `query_map` requires a
            // `Result<_, rusqlite::Error>` return, so we parse the format
            // (which can fail with our `DataError`) after collection.
            let rows: Vec<Result<(InstalledModelEntry, String), rusqlite::Error>> = stmt
                .query_map([], |row| {
                    let format_str: String = row.get(5)?;
                    let slot_opt: Option<String> = row.get(1)?;
                    Ok((
                        InstalledModelEntry {
                            id: row.get(0)?,
                            slot: slot_opt.and_then(|s| ModelSlot::parse_slot(&s)),
                            sha256: row.get(2)?,
                            local_path: row.get(3)?,
                            display_name: row.get(4)?,
                            // Placeholder; replaced from format_str below.
                            format: ModelFormat::GGUF,
                        },
                        format_str,
                    ))
                })?
                .collect();
            rows.into_iter()
                .map(|r| {
                    let (mut entry, format_str) = r?;
                    entry.format = format_from_db(&format_str)?;
                    Ok(entry)
                })
                .collect()
        })
        .await
    }

    async fn find_by_id(&self, id: &str) -> Result<Option<InstalledModelEntry>, Self::Error> {
        let conn = self.conn.clone();
        let id = id.to_string();
        with_conn(conn, move |c| {
            let mut stmt = c.prepare(
                "SELECT id, slot, sha256, local_path, display_name, format
                 FROM model_registry WHERE id = ?1 LIMIT 1",
            )?;
            let mut rows = stmt.query(rusqlite::params![id])?;
            if let Some(row) = rows.next()? {
                Ok(Some(entry_from_row(row)?))
            } else {
                Ok(None)
            }
        })
        .await
    }
}

/// Map a `model_registry` row to an [`InstalledModelEntry`]. Shared by
/// `find_by_slot`, `find_by_id`, and `list_all`.
fn entry_from_row(row: &rusqlite::Row<'_>) -> Result<InstalledModelEntry, DataError> {
    let format_str: String = row.get(5)?;
    let format = format_from_db(&format_str)?;
    let slot_opt: Option<String> = row.get(1)?;
    Ok(InstalledModelEntry {
        id: row.get(0)?,
        slot: slot_opt.and_then(|s| ModelSlot::parse_slot(&s)),
        sha256: row.get(2)?,
        local_path: row.get(3)?,
        display_name: row.get(4)?,
        format,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::vault::SqlCipherVaultRepository;
    use enclave_desktop_domain::vault::VaultRepository;
    use tempfile::tempdir;

    async fn fresh_factory() -> SqliteRepositoryFactory {
        let dir = tempdir().expect("tempdir");
        let db = dir.path().join("repotest.db");
        let meta = dir.path().join("repotest.meta");
        let repo = SqlCipherVaultRepository::with_paths(db, meta);
        let vault = repo.create("testpw").await.expect("create vault");
        // Leak the tempdir so the DB file survives for the test duration.
        std::mem::forget(dir);
        SqliteRepositoryFactory::from_vault(&vault).expect("factory")
    }

    #[tokio::test]
    async fn create_and_list_session() {
        let factory = fresh_factory().await;
        let chats = factory.chat_repository();
        let session = chats.create_session("Hello").await.expect("create");
        let sessions = chats.get_sessions().await.expect("list");
        assert_eq!(sessions.len(), 1);
        assert_eq!(sessions[0].title, "Hello");
        assert_eq!(sessions[0].id, session.id);
    }

    #[tokio::test]
    async fn add_and_read_messages() {
        let factory = fresh_factory().await;
        let chats = factory.chat_repository();
        let session = chats.create_session("Conversation").await.expect("create");
        chats
            .add_message(
                &session.id,
                ChatMessage {
                    sender: MessageSender::User,
                    text: "Hi there".into(),
                    timestamp: 100,
                },
            )
            .await
            .expect("add user msg");
        chats
            .add_message(
                &session.id,
                ChatMessage {
                    sender: MessageSender::Assistant,
                    text: "Hello!".into(),
                    timestamp: 101,
                },
            )
            .await
            .expect("add assistant msg");
        let msgs = chats.get_messages(&session.id).await.expect("get messages");
        assert_eq!(msgs.len(), 2);
        assert_eq!(msgs[0].sender, MessageSender::User);
        assert_eq!(msgs[1].sender, MessageSender::Assistant);
    }

    #[tokio::test]
    async fn pin_and_delete_session() {
        let factory = fresh_factory().await;
        let chats = factory.chat_repository();
        let session = chats.create_session("To pin").await.expect("create");
        chats.set_pinned(&session.id, true).await.expect("pin");
        let sessions = chats.get_sessions().await.expect("list");
        assert!(sessions[0].is_pinned);

        chats.delete_session(&session.id).await.expect("delete");
        let after = chats.get_sessions().await.expect("list after delete");
        assert!(after.is_empty());
    }

    #[tokio::test]
    async fn settings_roundtrip() {
        let factory = fresh_factory().await;
        let settings = factory.settings_repository();
        let initial = settings.get_settings().await.expect("get default");
        assert_eq!(initial.theme, "Dark");

        let mut s = initial;
        s.theme = "Light".into();
        s.api_endpoint = Some("http://localhost:11434/v1".into());
        settings.save_settings(s.clone()).await.expect("save");

        let loaded = settings.get_settings().await.expect("get after save");
        assert_eq!(loaded.theme, "Light");
        assert_eq!(
            loaded.api_endpoint.as_deref(),
            Some("http://localhost:11434/v1")
        );
    }

    #[tokio::test]
    async fn register_model_after_download() {
        let factory = fresh_factory().await;
        let models = factory.local_model_repository();
        models
            .register_installed(InstalledModelEntry {
                id: "gemma-e4b".into(),
                slot: Some(ModelSlot::Main),
                sha256: "abc123".into(),
                local_path: "/data/models/gemma.gguf".into(),
                display_name: "Gemma 4 E4B".into(),
                format: ModelFormat::GGUF,
            })
            .await
            .expect("register");
        let found = models
            .find_by_slot(ModelSlot::Main)
            .await
            .expect("lookup")
            .expect("entry");
        assert_eq!(found.id, "gemma-e4b");
        assert_eq!(found.local_path, "/data/models/gemma.gguf");
    }

    #[tokio::test]
    async fn list_all_returns_models_sorted_by_display_name() {
        let factory = fresh_factory().await;
        let models = factory.local_model_repository();
        models
            .register_installed(InstalledModelEntry {
                id: "gemma-12b".into(),
                slot: None,
                sha256: "zzz".into(),
                local_path: "/data/models/gemma-12b.gguf".into(),
                display_name: "Gemma 4 12B".into(),
                format: ModelFormat::GGUF,
            })
            .await
            .expect("register 12b");
        models
            .register_installed(InstalledModelEntry {
                id: "gemma-e4b".into(),
                slot: Some(ModelSlot::Main),
                sha256: "abc".into(),
                local_path: "/data/models/gemma-e4b.gguf".into(),
                display_name: "Gemma 4 E4B".into(),
                format: ModelFormat::GGUF,
            })
            .await
            .expect("register e4b");

        let all = models.list_all().await.expect("list_all");
        assert_eq!(all.len(), 2);
        // Sorted by display_name (case-insensitive): "Gemma 4 12B" < "Gemma 4 E4B".
        assert_eq!(all[0].id, "gemma-12b");
        assert_eq!(all[1].id, "gemma-e4b");
    }

    #[tokio::test]
    async fn find_by_id_roundtrips_registered_entry() {
        let factory = fresh_factory().await;
        let models = factory.local_model_repository();
        models
            .register_installed(InstalledModelEntry {
                id: "gemma-e4b".into(),
                slot: Some(ModelSlot::Main),
                sha256: "abc".into(),
                local_path: "/data/models/gemma.gguf".into(),
                display_name: "Gemma 4 E4B".into(),
                format: ModelFormat::GGUF,
            })
            .await
            .expect("register");

        let found = models
            .find_by_id("gemma-e4b")
            .await
            .expect("lookup")
            .expect("entry");
        assert_eq!(found.id, "gemma-e4b");
        assert_eq!(found.local_path, "/data/models/gemma.gguf");
        assert_eq!(found.format, ModelFormat::GGUF);
    }

    #[tokio::test]
    async fn find_by_id_returns_none_for_unknown_id() {
        let factory = fresh_factory().await;
        let models = factory.local_model_repository();
        let found = models.find_by_id("does-not-exist").await.expect("lookup");
        assert!(found.is_none());
    }

    #[tokio::test]
    async fn delete_session_cascades_messages() {
        let factory = fresh_factory().await;
        let chats = factory.chat_repository();
        let session = chats.create_session("Cascading").await.expect("create");
        chats
            .add_message(
                &session.id,
                ChatMessage {
                    sender: MessageSender::User,
                    text: "msg".into(),
                    timestamp: 1,
                },
            )
            .await
            .expect("add");
        chats.delete_session(&session.id).await.expect("delete");
        let msgs = chats.get_messages(&session.id).await.expect("get messages");
        assert!(
            msgs.is_empty(),
            "messages should be gone after cascade delete"
        );
    }
}

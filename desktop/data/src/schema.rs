use rusqlite::Connection;
use rusqlite_migration::{Migrations, M};
use thiserror::Error;

#[derive(Error, Debug)]
pub enum SchemaError {
    #[error("SQLite error: {0}")]
    Sqlite(#[from] rusqlite::Error),
    #[error("Migration error: {0}")]
    Migration(String),
}

/// The initial schema: a canary table for vault-key verification, the chat
/// sessions/messages tables mirroring the domain models, a key/value settings
/// table, a model-registry table for installed models, and a custom-models
/// table for user-added Hugging Face entries.
///
/// `user_version` is bumped by `rusqlite_migration` on successful apply, so
/// re-runs are no-ops.
pub fn migrations() -> Migrations<'static> {
    Migrations::new(vec![
        M::up(
            // vault_canary: single row (id=1) written on create, checked on unlock.
            "CREATE TABLE IF NOT EXISTS vault_canary (
                id   INTEGER PRIMARY KEY,
                value TEXT NOT NULL
            );",
        ),
        M::up(
            "CREATE TABLE IF NOT EXISTS chat_sessions (
                id         TEXT PRIMARY KEY,
                title      TEXT NOT NULL,
                is_pinned  INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );",
        ),
        M::up(
            "CREATE TABLE IF NOT EXISTS chat_messages (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                sender     TEXT NOT NULL,
                text       TEXT NOT NULL,
                timestamp  INTEGER NOT NULL,
                FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
            );",
        ),
        M::up(
            "CREATE INDEX IF NOT EXISTS idx_chat_messages_session
                ON chat_messages(session_id);",
        ),
        M::up(
            // settings: single-row key/value store. The domain `DesktopSettings`
            // struct is serialized to/from this row.
            "CREATE TABLE IF NOT EXISTS settings (
                key   TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );",
        ),
        M::up(
            // model_registry: one row per installed model file. Slot is the
            // council assignment (draft_one, draft_two, main, fast, thinking,
            // final_synthesis) or NULL for utility models.
            "CREATE TABLE IF NOT EXISTS model_registry (
                id          TEXT PRIMARY KEY,
                slot        TEXT,
                sha256      TEXT NOT NULL,
                local_path  TEXT NOT NULL,
                display_name TEXT NOT NULL,
                format      TEXT NOT NULL,
                installed_at INTEGER NOT NULL
            );",
        ),
        M::up(
            // custom_models: user-added Hugging Face entries (repo + file).
            "CREATE TABLE IF NOT EXISTS custom_models (
                id       TEXT PRIMARY KEY,
                repo     TEXT NOT NULL,
                file     TEXT NOT NULL,
                sha256   TEXT,
                slot     TEXT,
                added_at INTEGER NOT NULL
            );",
        ),
    ])
}

/// Apply all pending migrations to the given connection. Idempotent.
pub fn apply_migrations(conn: &mut Connection) -> Result<(), SchemaError> {
    migrations()
        .to_latest(conn)
        .map_err(|e| SchemaError::Migration(e.to_string()))?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[test]
    fn apply_migrations_creates_tables() {
        let dir = tempdir().expect("tempdir");
        let db = dir.path().join("schema.db");
        let mut conn = Connection::open(&db).expect("open");
        apply_migrations(&mut conn).expect("migrate");
        let tables: Vec<String> = conn
            .prepare("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
            .expect("prepare")
            .query_map([], |r| r.get(0))
            .expect("query_map")
            .filter_map(std::result::Result::ok)
            .collect();
        for expected in [
            "chat_messages",
            "chat_sessions",
            "custom_models",
            "model_registry",
            "settings",
            "vault_canary",
        ] {
            assert!(
                tables.contains(&expected.to_string()),
                "missing table: {expected}"
            );
        }
    }

    #[test]
    fn apply_migrations_is_idempotent() {
        let dir = tempdir().expect("tempdir");
        let db = dir.path().join("schema2.db");
        let mut conn = Connection::open(&db).expect("open");
        apply_migrations(&mut conn).expect("first migrate");
        apply_migrations(&mut conn).expect("second migrate (no-op)");
    }
}

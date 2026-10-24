use async_trait::async_trait;
use enclave_desktop_domain::vault::{UnlockedVault, VaultRepository, VaultStatus};
use rand::RngCore;
use rusqlite::Connection;
use std::any::Any;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use thiserror::Error;
use zeroize::Zeroize;

#[derive(Error, Debug)]
pub enum VaultError {
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
    #[error("SQLite error: {0}")]
    Sqlite(#[from] rusqlite::Error),
    #[error("Serialization error: {0}")]
    Serialization(#[from] serde_json::Error),
    #[error("Argon2 key derivation error: {0}")]
    Argon2(String),
    #[error("Vault already exists at {0}")]
    VaultAlreadyExists(String),
    #[error("Vault not found at {0} — create one first")]
    VaultNotFound(String),
    #[error("Wrong password — vault could not be decrypted")]
    WrongPassword,
    #[error("Vault metadata file is corrupted: {0}")]
    CorruptMeta(String),
    #[error("Lock poisoned: {0}")]
    LockPoisoned(String),
    #[error("Schema migration error: {0}")]
    Schema(#[from] crate::schema::SchemaError),
}

impl<T> From<std::sync::PoisonError<T>> for VaultError {
    fn from(err: std::sync::PoisonError<T>) -> Self {
        VaultError::LockPoisoned(err.to_string())
    }
}

/// Argon2id parameters tuned for a desktop (single-user, interactive unlock).
/// ~64 MiB memory, 3 iterations, 4 lanes. Derivation is one-shot at unlock,
/// never per-message, so this latency is acceptable.
const ARGON2_M_KIB: u32 = 64 * 1024;
const ARGON2_T: u32 = 3;
const ARGON2_P: u32 = 4;
const SALT_LEN: usize = 16;
const CANARY_VALUE: &str = "ENCLAVE_VAULT_OK";

/// SQLCipher PRAGMA key format: `x'<64 hex chars>'` for a 32-byte raw key.
/// We derive the 32-byte key from the password via Argon2id and pass it
/// directly so SQLCipher never does its own KDF (faster, and we control the
/// parameters).
fn format_pragma_key(key: &[u8; 32]) -> String {
    let hex: String = key.iter().map(|b| format!("{b:02x}")).collect();
    format!("x'{hex}'")
}

/// Derive a 32-byte key from the password + salt using Argon2id.
fn derive_key(password: &str, salt: &[u8]) -> Result<[u8; 32], VaultError> {
    use argon2::{Algorithm, Argon2, Params, Version};
    let params = Params::new(ARGON2_M_KIB, ARGON2_T, ARGON2_P, Some(32))
        .map_err(|e| VaultError::Argon2(e.to_string()))?;
    let argon2 = Argon2::new(Algorithm::Argon2id, Version::V0x13, params);
    let mut out = [0u8; 32];
    argon2
        .hash_password_into(password.as_bytes(), salt, &mut out)
        .map_err(|e| VaultError::Argon2(e.to_string()))?;
    Ok(out)
}

/// Resolve the vault DB and salt-meta paths under the OS data dir.
///
/// `<data_dir>/enclave/enclave.db` — the encrypted SQLite database.
/// `<data_dir>/enclave/enclave.meta` — plaintext sidecar holding the Argon2
/// salt and a magic header. The salt is not secret; it only prevents
/// precomputed dictionary attacks.
fn vault_paths() -> (PathBuf, PathBuf) {
    let base = dirs::data_dir().unwrap_or_else(|| PathBuf::from("."));
    let dir = base.join("enclave");
    (dir.join("enclave.db"), dir.join("enclave.meta"))
}

/// Synchronous probe: do the vault DB and salt sidecar both exist? Used by
/// `boot` to pick Create vs. Open mode without spawning an async runtime.
pub fn vault_exists_on_disk() -> bool {
    let (db, meta) = vault_paths();
    db.exists() && meta.exists()
}

/// Plaintext sidecar: a tiny JSON file with the salt (hex) and a magic string.
/// Stored as JSON to make it human-inspectable and forward-compatible with
/// future parameter changes.
#[derive(serde::Serialize, serde::Deserialize)]
struct VaultMeta {
    magic: String,
    salt_hex: String,
}

impl VaultMeta {
    const MAGIC: &'static str = "ENCLAVE_VAULT_META_V1";

    fn new(salt: &[u8]) -> Self {
        Self {
            magic: Self::MAGIC.to_string(),
            salt_hex: salt.iter().map(|b| format!("{b:02x}")).collect(),
        }
    }

    fn salt(&self) -> Result<Vec<u8>, VaultError> {
        if self.magic != Self::MAGIC {
            return Err(VaultError::CorruptMeta(format!(
                "unexpected magic: {}",
                self.magic
            )));
        }
        let bytes = (0..self.salt_hex.len())
            .step_by(2)
            .map(|i| {
                u8::from_str_radix(&self.salt_hex[i..i + 2], 16)
                    .map_err(|e| VaultError::CorruptMeta(e.to_string()))
            })
            .collect::<Result<Vec<_>, _>>()?;
        Ok(bytes)
    }
}

/// SQLCipher-backed vault repository.
pub struct SqlCipherVaultRepository {
    db_path: PathBuf,
    meta_path: PathBuf,
}

impl SqlCipherVaultRepository {
    /// Construct using the default OS data-dir paths.
    pub fn new() -> Self {
        let (db_path, meta_path) = vault_paths();
        Self { db_path, meta_path }
    }

    /// Construct with explicit paths (used by tests with a temp dir).
    pub fn with_paths(db_path: PathBuf, meta_path: PathBuf) -> Self {
        Self { db_path, meta_path }
    }

    fn ensure_parent_dir(&self) -> Result<(), VaultError> {
        if let Some(parent) = self.db_path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        Ok(())
    }

    /// Open a SQLCipher connection with the given derived key, apply the
    /// schema migrations, and verify the canary row (if present).
    fn open_with_key(
        &self,
        key: &[u8; 32],
        is_new: bool,
    ) -> Result<Arc<dyn Any + Send + Sync>, VaultError> {
        let mut conn = Connection::open(&self.db_path)?;
        let pragma = format_pragma_key(key);
        // SQLCipher reads the key from PRAGMA key. rusqlite exposes it via the
        // generic `pragma_update` (schema = None, name = "key", value = hex).
        conn.pragma_update(None, "key", &pragma)?;
        // Disable SQLCipher's own KDF — we already derived the key via Argon2id.
        conn.pragma_update(None, "cipher_kdf_iter", "0")?;
        // Verify we can read: try a trivial query. For a new vault this creates
        // the encrypted file; for an existing one this fails if the key is wrong.
        let test = conn.query_row("SELECT count(*) FROM sqlite_master", [], |r| {
            r.get::<_, i64>(0)
        });
        match test {
            Ok(_) => {}
            Err(rusqlite::Error::SqliteFailure(err, _))
                if err.code == rusqlite::ErrorCode::NotADatabase =>
            {
                // SQLCipher raises "file is not a database" when the key is
                // wrong (it tried to decrypt the header and got garbage).
                return Err(VaultError::WrongPassword);
            }
            Err(e) => return Err(VaultError::Sqlite(e)),
        }

        // Apply migrations (idempotent — `user_version` guards re-runs).
        crate::schema::apply_migrations(&mut conn)?;

        if is_new {
            // Write the canary row so future unlocks can verify the key.
            conn.execute(
                "INSERT OR REPLACE INTO vault_canary (id, value) VALUES (1, ?1)",
                rusqlite::params![CANARY_VALUE],
            )?;
        } else {
            // Verify the canary — if the key were wrong we'd have failed above,
            // but the canary is a defense-in-depth check against partial
            // corruption or a subtly-wrong key that still parses.
            let value: String =
                conn.query_row("SELECT value FROM vault_canary WHERE id = 1", [], |r| {
                    r.get(0)
                })?;
            if value != CANARY_VALUE {
                return Err(VaultError::WrongPassword);
            }
        }

        Ok(Arc::new(Mutex::new(conn)) as Arc<dyn Any + Send + Sync>)
    }
}

impl Default for SqlCipherVaultRepository {
    fn default() -> Self {
        Self::new()
    }
}

#[async_trait]
impl VaultRepository for SqlCipherVaultRepository {
    type Error = VaultError;

    async fn status(&self) -> Result<VaultStatus, Self::Error> {
        let vault_exists = self.meta_path.exists() && self.db_path.exists();
        Ok(VaultStatus {
            vault_exists,
            db_path: self.db_path.clone(),
            meta_path: self.meta_path.clone(),
        })
    }

    async fn create(&self, password: &str) -> Result<UnlockedVault, Self::Error> {
        if self.meta_path.exists() {
            return Err(VaultError::VaultAlreadyExists(
                self.db_path.display().to_string(),
            ));
        }
        let db_path = self.db_path.clone();
        let meta_path = self.meta_path.clone();
        let password = password.to_string();
        tokio::task::spawn_blocking(move || {
            SqlCipherVaultRepository { db_path, meta_path }.create_sync(&password)
        })
        .await
        .map_err(|e| VaultError::Io(std::io::Error::other(format!("spawn_blocking join: {e}"))))?
    }

    async fn unlock(&self, password: &str) -> Result<UnlockedVault, Self::Error> {
        if !self.meta_path.exists() {
            return Err(VaultError::VaultNotFound(
                self.db_path.display().to_string(),
            ));
        }
        let db_path = self.db_path.clone();
        let meta_path = self.meta_path.clone();
        let password = password.to_string();
        tokio::task::spawn_blocking(move || {
            SqlCipherVaultRepository { db_path, meta_path }.unlock_sync(&password)
        })
        .await
        .map_err(|e| VaultError::Io(std::io::Error::other(format!("spawn_blocking join: {e}"))))?
    }
}

impl SqlCipherVaultRepository {
    fn create_sync(&self, password: &str) -> Result<UnlockedVault, VaultError> {
        self.ensure_parent_dir()?;
        let mut salt = [0u8; SALT_LEN];
        rand::thread_rng().fill_bytes(&mut salt);
        let meta = VaultMeta::new(&salt);
        let meta_json = serde_json::to_string(&meta)?;
        std::fs::write(&self.meta_path, meta_json)?;

        let mut key = derive_key(password, &salt)?;
        let result = self.open_with_key(&key, true);
        key.zeroize();
        result.map(|conn| UnlockedVault::new(conn, true))
    }

    fn unlock_sync(&self, password: &str) -> Result<UnlockedVault, VaultError> {
        let meta_json = std::fs::read_to_string(&self.meta_path)?;
        let meta: VaultMeta = serde_json::from_str(&meta_json)?;
        let salt = meta.salt()?;

        let mut key = derive_key(password, &salt)?;
        let result = self.open_with_key(&key, false);
        key.zeroize();
        result.map(|conn| UnlockedVault::new(conn, false))
    }
}

/// Downcast the opaque `UnlockedVault::connection` back to a usable
/// `Arc<Mutex<Connection>>`. Used by `SqliteRepositoryFactory`.
pub fn downcast_connection(
    handle: &Arc<dyn Any + Send + Sync>,
) -> Result<Arc<Mutex<Connection>>, VaultError> {
    handle
        .clone()
        .downcast::<Mutex<Connection>>()
        .map_err(|_| VaultError::CorruptMeta("vault connection downcast failed".into()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    fn repo_in_temp() -> (SqlCipherVaultRepository, tempfile::TempDir) {
        let dir = tempdir().expect("tempdir");
        let db = dir.path().join("test.db");
        let meta = dir.path().join("test.meta");
        (SqlCipherVaultRepository::with_paths(db, meta), dir)
    }

    #[tokio::test]
    async fn create_then_unlock_roundtrip() {
        let (repo, _dir) = repo_in_temp();
        let status = repo.status().await.expect("status");
        assert!(!status.vault_exists);

        let pw = "correct horse battery staple";
        let vault = repo.create(pw).await.expect("create");
        assert!(vault.is_new_vault);

        let status2 = repo.status().await.expect("status after create");
        assert!(status2.vault_exists);

        let vault2 = repo.unlock(pw).await.expect("unlock");
        assert!(!vault2.is_new_vault);

        // Downcast succeeds.
        let _conn = downcast_connection(&vault2.connection).expect("downcast");
    }

    #[tokio::test]
    async fn wrong_password_rejected() {
        let (repo, _dir) = repo_in_temp();
        repo.create("right password").await.expect("create");
        let err = repo
            .unlock("wrong password")
            .await
            .expect_err("should fail");
        assert!(matches!(err, VaultError::WrongPassword), "got: {err}");
    }

    #[tokio::test]
    async fn create_twice_errors() {
        let (repo, _dir) = repo_in_temp();
        repo.create("pw").await.expect("first create");
        let err = repo.create("pw").await.expect_err("second should fail");
        assert!(matches!(err, VaultError::VaultAlreadyExists(_)));
    }

    #[tokio::test]
    async fn unlock_missing_vault_errors() {
        let (repo, _dir) = repo_in_temp();
        let err = repo.unlock("pw").await.expect_err("should fail");
        assert!(matches!(err, VaultError::VaultNotFound(_)));
    }
}

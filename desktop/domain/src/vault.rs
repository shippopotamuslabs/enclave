use async_trait::async_trait;
use std::any::Any;
use std::sync::Arc;

/// Outcome of a successful vault unlock. The presentation layer holds this and
/// hands it to the data-layer repository factory (`SqliteRepositoryFactory`).
///
/// The connection handle is intentionally opaque (`Arc<dyn Any + Send + Sync>`)
/// so the domain layer does not depend on `rusqlite`. The data layer downcasts
/// it back to `rusqlite::Connection` when wiring repositories.
pub struct UnlockedVault {
    /// Opaque handle to the encrypted SQLite connection. The data layer created
    /// it and knows the concrete type for downcast.
    pub connection: Arc<dyn Any + Send + Sync>,
    /// Whether this unlock created a brand-new vault (first run) vs. opened an
    /// existing one. The presentation layer uses this to decide whether to
    /// route to the model-download screen or straight to chat.
    pub is_new_vault: bool,
}

impl std::fmt::Debug for UnlockedVault {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("UnlockedVault")
            .field("is_new_vault", &self.is_new_vault)
            .finish_non_exhaustive()
    }
}

impl UnlockedVault {
    pub fn new(connection: Arc<dyn Any + Send + Sync>, is_new_vault: bool) -> Self {
        Self {
            connection,
            is_new_vault,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum VaultKind {
    /// First run — no vault exists yet, the user is creating one.
    Create,
    /// Subsequent run — an existing vault is being opened.
    Open,
}

#[derive(Debug, Clone)]
pub struct VaultStatus {
    /// Whether a vault already exists on disk (salt sidecar present).
    pub vault_exists: bool,
    /// Absolute path to the vault database file.
    pub db_path: std::path::PathBuf,
    /// Absolute path to the plaintext salt sidecar.
    pub meta_path: std::path::PathBuf,
}

/// Port for vault lifecycle: probe status, create, unlock.
///
/// The implementation lives in the data layer (`enclave-desktop-data`) and
/// wraps SQLCipher + Argon2. The presentation layer calls `unlock` (or
/// `create`) from `Message::UnlockVaultSubmit` and receives an `UnlockedVault`
/// handle on success.
#[async_trait]
pub trait VaultRepository: Send + Sync {
    type Error: std::fmt::Debug + std::fmt::Display;

    /// Probe whether a vault exists at the app data dir and return the paths.
    async fn status(&self) -> Result<VaultStatus, Self::Error>;

    /// Create a brand-new vault with the given password. Errors if one already
    /// exists. Returns the opened connection and `is_new_vault = true`.
    async fn create(&self, password: &str) -> Result<UnlockedVault, Self::Error>;

    /// Open an existing vault with the given password. Errors if the password
    /// is wrong or the vault is missing. Returns `is_new_vault = false`.
    async fn unlock(&self, password: &str) -> Result<UnlockedVault, Self::Error>;
}

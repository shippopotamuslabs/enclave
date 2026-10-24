use crate::download::{DownloadFileSpec, ModelCatalog, ModelFormat, ModelSlot};
use crate::models::{ChatMessage, ChatSession, DesktopSettings};
use async_trait::async_trait;

/// A row in the installed-model registry (mirrors Android local model metadata).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct InstalledModelEntry {
    pub id: String,
    pub slot: Option<ModelSlot>,
    pub sha256: String,
    pub local_path: String,
    pub display_name: String,
    pub format: ModelFormat,
}

/// Port for loading the bundled catalog and scanning for missing model files.
pub trait ModelCatalogPort: Send + Sync {
    type Error: std::fmt::Debug + std::fmt::Display + Send + Sync + 'static;

    fn load_catalog(&self) -> Result<ModelCatalog, Self::Error>;
    fn scan_missing_downloads(&self, catalog: &ModelCatalog) -> Vec<DownloadFileSpec>;
    /// Installed-model rows for catalog assets already present on disk (used
    /// to populate the vault registry when downloads were skipped or pre-seeded).
    fn installed_on_disk(&self, catalog: &ModelCatalog) -> Vec<InstalledModelEntry>;
}

/// Port for downloading model files (HTTP fetch + verify).
#[async_trait]
pub trait ModelDownloadPort: Send + Sync {
    type Error: std::fmt::Debug + std::fmt::Display + Send + Sync + 'static;

    async fn download_files(
        &self,
        specs: Vec<DownloadFileSpec>,
        on_progress: Box<dyn Fn(usize, u64, u64) + Send + Sync>,
        on_finished: Box<dyn Fn(usize, Result<(), Self::Error>) + Send + Sync>,
    ) -> Vec<Result<(), Self::Error>>;
}

/// Port for persisting installed model metadata after a successful download.
///
/// The `list_all` / `find_by_id` methods are the minimal surface the chat
/// model picker needs to enumerate and resolve downloaded models. They mirror
/// the Android `LocalModelsDao.getAll` / `getById` access paths; the slot-based
/// `find_by_slot` is retained for the council/default-model resolution path
/// (Android's `default_models` table analog). A future full-Android-parity
/// migration can split this port behind richer asset/config types without
/// changing these method signatures.
#[async_trait]
pub trait LocalModelRepository: Send + Sync {
    type Error: std::fmt::Debug + std::fmt::Display + Send + Sync + 'static;

    async fn register_installed(&self, entry: InstalledModelEntry) -> Result<(), Self::Error>;
    async fn find_by_slot(
        &self,
        slot: ModelSlot,
    ) -> Result<Option<InstalledModelEntry>, Self::Error>;

    /// List every installed model row, ordered by display name. Drives the
    /// chat model picker.
    async fn list_all(&self) -> Result<Vec<InstalledModelEntry>, Self::Error>;

    /// Look up a single installed model by its registry id. Resolves a
    /// picker selection to its on-disk path at send time.
    async fn find_by_id(&self, id: &str) -> Result<Option<InstalledModelEntry>, Self::Error>;
}

#[async_trait]
pub trait ChatRepository: Send + Sync {
    type Error: std::fmt::Debug + std::fmt::Display;

    async fn get_sessions(&self) -> Result<Vec<ChatSession>, Self::Error>;
    async fn get_messages(&self, session_id: &str) -> Result<Vec<ChatMessage>, Self::Error>;
    async fn create_session(&self, title: &str) -> Result<ChatSession, Self::Error>;
    async fn add_message(&self, session_id: &str, message: ChatMessage) -> Result<(), Self::Error>;
    async fn delete_session(&self, session_id: &str) -> Result<(), Self::Error>;
    async fn set_pinned(&self, session_id: &str, pinned: bool) -> Result<(), Self::Error>;
}

#[async_trait]
pub trait SettingsRepository: Send + Sync {
    type Error: std::fmt::Debug + std::fmt::Display;

    async fn get_settings(&self) -> Result<DesktopSettings, Self::Error>;
    async fn save_settings(&self, settings: DesktopSettings) -> Result<(), Self::Error>;
}

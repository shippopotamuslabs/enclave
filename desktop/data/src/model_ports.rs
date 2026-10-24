use async_trait::async_trait;
use enclave_desktop_domain::download::{DownloadFileSpec, ModelCatalog};
use enclave_desktop_domain::repositories::{
    InstalledModelEntry, ModelCatalogPort, ModelDownloadPort,
};

use crate::download::{
    download_files_concurrent, installed_entries_on_disk, load_catalog, scan_models, DownloadError,
};

/// File-backed catalog loader and missing-model scanner.
#[derive(Debug, Default, Clone, Copy)]
pub struct FileModelCatalogService;

impl ModelCatalogPort for FileModelCatalogService {
    type Error = DownloadError;

    fn load_catalog(&self) -> Result<ModelCatalog, Self::Error> {
        load_catalog()
    }

    fn scan_missing_downloads(&self, catalog: &ModelCatalog) -> Vec<DownloadFileSpec> {
        scan_models(catalog)
    }

    fn installed_on_disk(&self, catalog: &ModelCatalog) -> Vec<InstalledModelEntry> {
        installed_entries_on_disk(catalog)
    }
}

/// HTTP download orchestrator — delegates to the data-layer download module.
#[derive(Debug, Default, Clone, Copy)]
pub struct FileModelDownloadService;

#[async_trait]
impl ModelDownloadPort for FileModelDownloadService {
    type Error = DownloadError;

    async fn download_files(
        &self,
        specs: Vec<DownloadFileSpec>,
        on_progress: Box<dyn Fn(usize, u64, u64) + Send + Sync>,
        on_finished: Box<dyn Fn(usize, Result<(), Self::Error>) + Send + Sync>,
    ) -> Vec<Result<(), Self::Error>> {
        download_files_concurrent(
            specs,
            move |index, downloaded, total| {
                on_progress(index, downloaded, total);
            },
            move |index, result| {
                on_finished(index, result);
            },
        )
        .await
    }
}

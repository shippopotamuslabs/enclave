use enclave_desktop_domain::download::{DownloadFileSpec, ModelCatalog, ModelSource};
use enclave_desktop_domain::repositories::InstalledModelEntry;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::path::{Path, PathBuf};
use std::sync::Arc;
use thiserror::Error;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::sync::Semaphore;
use tokio::task::JoinSet;

/// Maximum number of files downloaded in parallel — mirrors Android
/// `ModelConfig.CONCURRENT_DOWNLOADS`.
pub const CONCURRENT_DOWNLOADS: usize = 3;

#[derive(Error, Debug)]
pub enum DownloadError {
    #[error("HTTP error: {0}")]
    Http(#[from] reqwest::Error),
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
    #[error("SHA-256 mismatch: expected {expected}, got {actual}")]
    Sha256Mismatch { expected: String, actual: String },
    #[error("Size mismatch: expected {expected}, got {actual}")]
    SizeMismatch { expected: u64, actual: u64 },
    #[error("Catalog parse error: {0}")]
    CatalogParse(#[from] serde_json::Error),
    #[error("Untrusted download URL: {0}")]
    UntrustedUrl(String),
    #[error("Untrusted redirect to: {0}")]
    UntrustedRedirect(String),
}

/// Build an HTTPS URL for a Hugging Face model file.
pub fn hf_url(repo: &str, file: &str, sub_path: Option<&str>) -> String {
    match sub_path {
        Some(p) => format!("https://huggingface.co/{repo}/resolve/main/{p}/{file}"),
        None => format!("https://huggingface.co/{repo}/resolve/main/{file}"),
    }
}

/// Trusted-host check: must be HTTPS, must be huggingface.co or our R2 bucket.
/// Mirrors `DownloadSecurity.requireTrustedDownloadUrl`.
fn is_trusted_url(url: &str) -> bool {
    if !url.starts_with("https://") {
        return false;
    }
    let host = url
        .strip_prefix("https://")
        .and_then(|rest| rest.split('/').next())
        .unwrap_or("");
    host == "huggingface.co"
        || host.ends_with(".huggingface.co")
        || host == "hf.co"
        || host.ends_with(".hf.co")
        || host == "config.enclave-app.com"
}

/// Resume sidecar: stores the committed byte offset so a crashed download can
/// resume from the right position. Written as a `.tmp.meta` JSON file next to
/// the `.tmp` partial download (mirrors Android's `DownloadMetaFile`).
#[derive(Serialize, Deserialize)]
struct DownloadMeta {
    committed_offset: u64,
    expected_sha256: Option<String>,
}

const SYNC_INTERVAL_BYTES: u64 = 64 * 1024 * 1024;

/// Download a single file with resume + SHA-256 verification. Writes to
/// `<target>.tmp` then renames to `<target>` on success. Resumes from the
/// `.tmp.meta` sidecar if present.
///
/// `progress` is called periodically with (downloaded, total) for UI updates.
pub async fn download_file<F>(spec: &DownloadFileSpec, progress: F) -> Result<(), DownloadError>
where
    F: Fn(u64, u64) + Send + 'static,
{
    if !is_trusted_url(&spec.url) {
        return Err(DownloadError::UntrustedUrl(spec.url.clone()));
    }

    let temp_path = spec.target_path.with_extension("gguf.tmp");
    if temp_path.extension().is_none() {
        // `.with_extension` replaces the last extension; for files without one
        // like `.bin`, use the append approach.
    }
    // Use a consistent temp name: <target>.tmp
    let temp_path = {
        let mut name = spec
            .target_path
            .file_name()
            .unwrap_or_default()
            .to_os_string();
        name.push(".tmp");
        spec.target_path.with_file_name(name)
    };
    let meta_path = {
        let mut name = spec
            .target_path
            .file_name()
            .unwrap_or_default()
            .to_os_string();
        name.push(".tmp.meta");
        spec.target_path.with_file_name(name)
    };

    // Check for existing complete file.
    if spec.target_path.exists() {
        if let Ok(metadata) = std::fs::metadata(&spec.target_path) {
            if spec.expected_size == 0 || metadata.len() == spec.expected_size {
                return Ok(());
            }
        }
    }

    // Resume from sidecar.
    let mut existing_bytes: u64 = 0;
    if temp_path.exists() && meta_path.exists() {
        if let Ok(meta_str) = std::fs::read_to_string(&meta_path) {
            if let Ok(meta) = serde_json::from_str::<DownloadMeta>(&meta_str) {
                if meta.committed_offset > 0
                    && temp_path.metadata().map(|m| m.len()).unwrap_or(0) >= meta.committed_offset
                {
                    existing_bytes = meta.committed_offset;
                }
            }
        }
    }

    // Truncate temp to committed offset (in case of corruption past it).
    if existing_bytes > 0 {
        let file = std::fs::OpenOptions::new().write(true).open(&temp_path)?;
        file.set_len(existing_bytes)?;
    }

    // Build the request with Range header for resume.
    let client = reqwest::Client::builder()
        .redirect(reqwest::redirect::Policy::limited(5))
        .build()?;
    let mut request = client.get(&spec.url);
    if existing_bytes > 0 {
        request = request.header("Range", format!("bytes={existing_bytes}-"));
    }
    let response = request.send().await?;

    // If server ignores Range (returns 200 instead of 206), restart from scratch.
    let (response, start_offset) =
        if existing_bytes > 0 && response.status() == reqwest::StatusCode::OK {
            // Server ignored Range — start fresh.
            std::fs::remove_file(&temp_path).ok();
            std::fs::remove_file(&meta_path).ok();
            (response, 0u64)
        } else if !response.status().is_success()
            && response.status() != reqwest::StatusCode::PARTIAL_CONTENT
        {
            return Err(DownloadError::Http(
                response.error_for_status().unwrap_err(),
            ));
        } else {
            (response, existing_bytes)
        };

    let total = if spec.expected_size > 0 {
        spec.expected_size
    } else {
        response.content_length().unwrap_or(0)
    };

    // Open temp file for append (resume) or create (fresh).
    let mut file = if start_offset > 0 {
        tokio::fs::OpenOptions::new()
            .write(true)
            .append(true)
            .open(&temp_path)
            .await?
    } else {
        tokio::fs::File::create(&temp_path).await?
    };

    // Stream body, hashing + writing + syncing.
    let mut hasher = Sha256::new();
    if start_offset > 0 {
        // Feed existing temp bytes into the hash so we can verify the full file.
        let mut existing = tokio::fs::File::open(&temp_path).await?;
        let mut buf = vec![0u8; 64 * 1024];
        loop {
            let n = existing.read(&mut buf).await?;
            if n == 0 {
                break;
            }
            hasher.update(&buf[..n]);
        }
    }

    let mut stream = response.bytes_stream();
    let mut downloaded = start_offset;
    let mut since_sync: u64 = 0;

    use futures_util::StreamExt;
    while let Some(chunk_result) = stream.next().await {
        let chunk = chunk_result?;
        file.write_all(&chunk).await?;
        hasher.update(&chunk);
        downloaded += chunk.len() as u64;
        since_sync += chunk.len() as u64;

        if since_sync >= SYNC_INTERVAL_BYTES {
            file.sync_all().await?;
            write_meta_file(&meta_path, downloaded, spec.expected_sha256.as_deref())?;
            since_sync = 0;
        }

        progress(downloaded, total);
    }

    file.sync_all().await?;

    // Verify SHA-256 if provided.
    if let Some(expected) = &spec.expected_sha256 {
        let actual = hex::encode(hasher.finalize());
        if !actual.eq_ignore_ascii_case(expected) {
            std::fs::remove_file(&temp_path).ok();
            std::fs::remove_file(&meta_path).ok();
            return Err(DownloadError::Sha256Mismatch {
                expected: expected.clone(),
                actual,
            });
        }
    }

    // Verify size only when SHA-256 is also configured. Catalog `expected_size`
    // values are approximate progress hints when sha256 is absent (bundled
    // desktop catalog uses rounded estimates; HF files differ slightly).
    if spec.expected_sha256.is_some() && spec.expected_size > 0 && downloaded != spec.expected_size
    {
        std::fs::remove_file(&temp_path).ok();
        std::fs::remove_file(&meta_path).ok();
        return Err(DownloadError::SizeMismatch {
            expected: spec.expected_size,
            actual: downloaded,
        });
    }

    // Rename temp → target, remove meta.
    std::fs::remove_file(&meta_path).ok();
    std::fs::rename(&temp_path, &spec.target_path)?;

    Ok(())
}

/// Download many files concurrently, bounded by [`CONCURRENT_DOWNLOADS`].
/// Returns one result per spec, in the same order as the input slice.
pub async fn download_files_concurrent<F, G>(
    specs: Vec<DownloadFileSpec>,
    on_progress: F,
    on_finished: G,
) -> Vec<Result<(), DownloadError>>
where
    F: Fn(usize, u64, u64) + Send + Sync + 'static,
    G: Fn(usize, Result<(), DownloadError>) + Send + Sync + 'static,
{
    let count = specs.len();
    if count == 0 {
        return Vec::new();
    }

    let on_progress = Arc::new(on_progress);
    let on_finished = Arc::new(on_finished);
    let semaphore = Arc::new(Semaphore::new(CONCURRENT_DOWNLOADS));
    let mut join_set = JoinSet::new();

    for (index, spec) in specs.into_iter().enumerate() {
        let sem = Arc::clone(&semaphore);
        let progress = Arc::clone(&on_progress);
        join_set.spawn(async move {
            let _permit = match sem.acquire().await {
                Ok(permit) => permit,
                Err(_) => {
                    return (
                        index,
                        Err(DownloadError::Io(std::io::Error::other(
                            "download semaphore closed unexpectedly",
                        ))),
                    );
                }
            };
            let result = download_file(&spec, move |downloaded, total| {
                progress(index, downloaded, total);
            })
            .await;
            (index, result)
        });
    }

    let mut results: Vec<Option<Result<(), DownloadError>>> = (0..count).map(|_| None).collect();
    while let Some(joined) = join_set.join_next().await {
        match joined {
            Ok((index, result)) => {
                on_finished(
                    index,
                    result
                        .as_ref()
                        .map(|_| ())
                        .map_err(|e| DownloadError::Io(std::io::Error::other(e.to_string()))),
                );
                results[index] = Some(result);
            }
            Err(join_err) => {
                return vec![Err(DownloadError::Io(std::io::Error::other(format!(
                    "download task failed: {join_err}"
                ))))];
            }
        }
    }

    results
        .into_iter()
        .map(|r| {
            r.unwrap_or_else(|| {
                Err(DownloadError::Io(std::io::Error::other(
                    "download task did not report a result",
                )))
            })
        })
        .collect()
}

fn write_meta_file(
    path: &Path,
    offset: u64,
    expected_sha: Option<&str>,
) -> Result<(), DownloadError> {
    let meta = DownloadMeta {
        committed_offset: offset,
        expected_sha256: expected_sha.map(|s| s.to_string()),
    };
    let json = serde_json::to_string(&meta)?;
    std::fs::write(path, json)?;
    Ok(())
}

/// Resolve the models directory under the OS data dir.
/// `<data_dir>/enclave/models/`
pub fn models_dir() -> PathBuf {
    let base = dirs::data_dir().unwrap_or_else(|| PathBuf::from("."));
    base.join("enclave").join("models")
}

/// Load the bundled desktop model catalog from the embedded JSON.
pub fn load_catalog() -> Result<ModelCatalog, DownloadError> {
    let json = include_str!("../../models/desktop_models.json");
    Ok(serde_json::from_str(json)?)
}

/// Scan the models directory and determine which catalog assets are missing
/// or partial. Returns specs for files that need downloading.
pub fn scan_models(catalog: &ModelCatalog) -> Vec<DownloadFileSpec> {
    let dir = models_dir();
    std::fs::create_dir_all(&dir).ok();

    let mut specs = Vec::new();

    for chat in &catalog.chat_models {
        // Only auto-download models assigned to council slots — optional catalog
        // entries (e.g. the 12B Gemma) are on-demand, matching Android slot assignment.
        if chat.default_slots.is_empty() {
            continue;
        }

        let target = dir.join(&chat.file);
        if !file_complete(&target, chat.size_in_bytes, chat.sha256.is_some()) {
            let url = build_url(chat.source, &chat.repo, &chat.file, None);
            specs.push(DownloadFileSpec {
                url,
                target_path: target,
                expected_sha256: chat.sha256.clone(),
                expected_size: chat.size_in_bytes,
                display_name: chat.display_name.clone(),
                model_id: Some(chat.id.clone()),
                slot: chat.default_slots.first().copied(),
                format: Some(chat.format),
            });
        }

        // Multimodal projector (mmproj) — not a separate "vision" slot download.
        // Android only queues mmproj when sha256 + size metadata are all present.
        if let Some((mmproj, mmproj_sha256, mmproj_size)) = chat.mmproj_artifact() {
            let mm_target = dir.join(mmproj);
            if !file_complete(&mm_target, mmproj_size, true) {
                let url = build_url(chat.source, &chat.repo, mmproj, None);
                specs.push(DownloadFileSpec {
                    url,
                    target_path: mm_target,
                    expected_sha256: Some(mmproj_sha256.to_string()),
                    expected_size: mmproj_size,
                    display_name: format!("{} (multimodal projector)", chat.display_name),
                    model_id: None,
                    slot: None,
                    format: None,
                });
            }
        }
    }

    for util in &catalog.utility_models {
        let target = dir.join(&util.file);
        if !file_complete(&target, util.size_in_bytes, util.sha256.is_some()) {
            let url = build_url(
                util.source,
                &util.repo,
                &util.file,
                util.hugging_face_path.as_deref(),
            );
            specs.push(DownloadFileSpec {
                url,
                target_path: target,
                expected_sha256: util.sha256.clone(),
                expected_size: util.size_in_bytes,
                display_name: util.display_name.clone(),
                model_id: Some(util.id.clone()),
                slot: None,
                format: Some(util.format),
            });
        }
    }

    specs
}

/// Registry rows for catalog assets that are already present on disk.
pub fn installed_entries_on_disk(catalog: &ModelCatalog) -> Vec<InstalledModelEntry> {
    let dir = models_dir();
    let mut entries = Vec::new();

    for chat in &catalog.chat_models {
        if chat.default_slots.is_empty() {
            continue;
        }
        let target = dir.join(&chat.file);
        if !file_complete(&target, chat.size_in_bytes, chat.sha256.is_some()) {
            continue;
        }
        entries.push(InstalledModelEntry {
            id: chat.id.clone(),
            slot: chat.default_slots.first().copied(),
            sha256: chat.sha256.clone().unwrap_or_else(|| "unknown".into()),
            local_path: target.display().to_string(),
            display_name: chat.display_name.clone(),
            format: chat.format,
        });
    }

    for util in &catalog.utility_models {
        let target = dir.join(&util.file);
        if !file_complete(&target, util.size_in_bytes, util.sha256.is_some()) {
            continue;
        }
        entries.push(InstalledModelEntry {
            id: util.id.clone(),
            slot: None,
            sha256: util.sha256.clone().unwrap_or_else(|| "unknown".into()),
            local_path: target.display().to_string(),
            display_name: util.display_name.clone(),
            format: util.format,
        });
    }

    entries
}

fn file_complete(path: &Path, expected_size: u64, require_exact_size: bool) -> bool {
    if !path.exists() {
        return false;
    }
    if expected_size == 0 {
        return true;
    }
    let Ok(metadata) = std::fs::metadata(path) else {
        return false;
    };
    let len = metadata.len();
    if require_exact_size {
        len == expected_size
    } else {
        // Without SHA-256, catalog size is approximate — treat any non-empty
        // file as present (download verification happens at fetch time).
        len > 0
    }
}

fn build_url(source: ModelSource, repo: &str, file: &str, sub_path: Option<&str>) -> String {
    match source {
        ModelSource::HF => hf_url(repo, file, sub_path),
        ModelSource::R2 => {
            // R2 assets are under config.enclave-app.com/<path>/<file>.
            let path = sub_path.unwrap_or("");
            if path.is_empty() {
                format!("https://config.enclave-app.com/{file}")
            } else {
                format!("https://config.enclave-app.com/{path}/{file}")
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn catalog_loads_from_embedded_json() {
        let catalog = load_catalog().expect("catalog loads");
        assert!(!catalog.chat_models.is_empty(), "should have chat models");
        assert!(
            !catalog.utility_models.is_empty(),
            "should have utility models"
        );

        // Gemma 4 E4B should be the default main model.
        let gemma = catalog
            .chat_models
            .iter()
            .find(|m| m.id == "gemma-4-e4b-it-q4")
            .expect("gemma-4-e4b-it-q4 in catalog");
        assert!(gemma
            .default_slots
            .contains(&enclave_desktop_domain::download::ModelSlot::Main));
        assert!(gemma.is_multimodal);
        assert_eq!(
            gemma.format,
            enclave_desktop_domain::download::ModelFormat::GGUF
        );
    }

    #[test]
    fn catalog_has_optional_12b_model() {
        let catalog = load_catalog().expect("catalog loads");
        let big = catalog
            .chat_models
            .iter()
            .find(|m| m.id == "gemma-4-12b-it-q4")
            .expect("gemma-4-12b-it-q4 in catalog");
        assert!(
            big.default_slots.is_empty(),
            "12B should have no default slot (optional)"
        );
    }

    #[test]
    fn scan_models_detects_missing_slot_assigned_models() {
        let catalog = load_catalog().expect("catalog loads");
        // Use a temp dir so we don't hit the real data dir.
        let dir = tempfile::tempdir().expect("tempdir");
        std::env::set_var("XDG_DATA_HOME", dir.path());
        let specs = scan_models(&catalog);
        // Slot-assigned chat models + utilities; optional 12B and mmproj without
        // integrity metadata are excluded (mirrors Android download policy).
        assert!(
            !specs.is_empty(),
            "missing models should produce download specs"
        );
        assert!(
            !specs.iter().any(|spec| spec.display_name.contains("12B")),
            "optional models without default slots should not be scanned"
        );
        assert!(
            specs.iter().any(|spec| {
                spec.display_name.contains("multimodal projector")
                    && spec.display_name.contains("E4B")
            }),
            "slot-assigned Gemma E4B should queue its mmproj when metadata is present"
        );
        // Each spec should have an HF URL.
        for spec in &specs {
            assert!(
                spec.url.starts_with("https://huggingface.co/"),
                "url: {}",
                spec.url
            );
        }
        std::env::remove_var("XDG_DATA_HOME");
    }

    #[test]
    fn mmproj_artifact_requires_complete_metadata() {
        let catalog = load_catalog().expect("catalog loads");
        let gemma = catalog
            .chat_models
            .iter()
            .find(|m| m.id == "gemma-4-e4b-it-q4")
            .expect("gemma in catalog");
        assert!(
            gemma.mmproj_file.is_some(),
            "catalog lists mmproj filename for multimodal Gemma"
        );
        let (file, sha, size) = gemma.mmproj_artifact().expect("E4B mmproj metadata");
        assert_eq!(file, "mmproj-F16.gguf");
        assert_eq!(
            sha,
            "ddf46c21d7078e95338cfc22306b19b276a29a5ad089023449dd54d4b6170a51"
        );
        assert_eq!(size, 990_372_672);
    }

    #[test]
    fn scan_models_mmproj_urls_use_hf() {
        let catalog = load_catalog().expect("catalog loads");
        let dir = tempfile::tempdir().expect("tempdir");
        std::env::set_var("XDG_DATA_HOME", dir.path());
        let specs = scan_models(&catalog);
        for spec in &specs {
            assert!(
                spec.url.starts_with("https://huggingface.co/"),
                "url: {}",
                spec.url
            );
        }
        std::env::remove_var("XDG_DATA_HOME");
    }

    #[test]
    fn hf_url_builds_correctly() {
        assert_eq!(
            hf_url("unsloth/gemma-4-E4B-it-GGUF", "gemma-4-E4B-it-Q4_K_M.gguf", None),
            "https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q4_K_M.gguf"
        );
        assert_eq!(
            hf_url("sentence-transformers/all-MiniLM-L6-v2", "model.onnx", Some("onnx")),
            "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx"
        );
    }

    #[test]
    fn installed_entries_on_disk_skips_missing_files() {
        let catalog = load_catalog().expect("catalog");
        let entries = installed_entries_on_disk(&catalog);
        for entry in &entries {
            assert!(
                std::path::Path::new(&entry.local_path).exists(),
                "registered path should exist: {}",
                entry.local_path
            );
        }
    }

    #[test]
    fn file_complete_without_sha_accepts_any_nonempty_file() {
        let dir = tempfile::tempdir().expect("tempdir");
        let path = dir.path().join("model.gguf");
        std::fs::write(&path, b"x").expect("write");
        assert!(file_complete(&path, 2_500_000_000, false));
        assert!(!file_complete(&path, 2_500_000_000, true));
    }

    #[test]
    fn trusted_url_check() {
        assert!(is_trusted_url(
            "https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF/resolve/main/file.gguf"
        ));
        assert!(is_trusted_url(
            "https://config.enclave-app.com/model_config.json"
        ));
        assert!(!is_trusted_url("http://huggingface.co/file.gguf"));
        assert!(!is_trusted_url("https://evil.com/file.gguf"));
    }
}

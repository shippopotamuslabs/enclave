use serde::{Deserialize, Serialize};
use std::path::PathBuf;

/// Council slot assignment mirroring the Android `ModelType` enum. Each slot
/// defines a role in the multi-model council (draft models, main, synthesis).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ModelSlot {
    /// Primary fast-responding model for quick chat.
    Fast,
    /// First draft model for council mode.
    DraftOne,
    /// Second draft model for council mode.
    DraftTwo,
    /// Primary main model — the workhorse for detailed responses.
    Main,
    /// Thinking/reasoning model for extended reasoning mode.
    Thinking,
    /// Final synthesis model that merges council outputs.
    FinalSynthesis,
}

impl ModelSlot {
    pub fn as_str(&self) -> &'static str {
        match self {
            ModelSlot::Fast => "fast",
            ModelSlot::DraftOne => "draft_one",
            ModelSlot::DraftTwo => "draft_two",
            ModelSlot::Main => "main",
            ModelSlot::Thinking => "thinking",
            ModelSlot::FinalSynthesis => "final_synthesis",
        }
    }

    pub fn parse_slot(s: &str) -> Option<Self> {
        match s {
            "fast" => Some(Self::Fast),
            "draft_one" => Some(Self::DraftOne),
            "draft_two" => Some(Self::DraftTwo),
            "main" => Some(Self::Main),
            "thinking" => Some(Self::Thinking),
            "final_synthesis" => Some(Self::FinalSynthesis),
            _ => None,
        }
    }
}

/// Where a model file is downloaded from.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum ModelSource {
    /// Hugging Face Hub (`huggingface.co/<repo>/resolve/main/<file>`).
    HF,
    /// Cloudflare R2 bucket (used by the Android config for self-hosted assets).
    R2,
}

/// File format — determines which inference engine loads the model.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum ModelFormat {
    /// llama.cpp GGUF format — loaded by `LlamaEngine`.
    GGUF,
    /// whisper.cpp GGML format — loaded by `WhisperEngine`.
    GGML,
    /// ONNX Runtime format — loaded by `OnnxUtilityEngine`.
    ONNX,
}

/// Utility model type (non-chat models).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum UtilityType {
    /// Speech-to-text transcription model (whisper.cpp).
    Whisper,
    /// Text embedding model for semantic search.
    Embeddings,
}

/// A single chat LLM model entry in the catalog.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChatModelAsset {
    pub id: String,
    pub display_name: String,
    pub source: ModelSource,
    pub repo: String,
    pub file: String,
    /// Optional multimodal projector file (same repo, e.g. `mmproj-F16.gguf`).
    pub mmproj_file: Option<String>,
    /// SHA-256 for the mmproj file. Required together with [Self::mmproj_size_in_bytes]
    /// before the projector is queued for download (mirrors Android `requiredArtifacts`).
    pub mmproj_sha256: Option<String>,
    /// Size in bytes for the mmproj file.
    pub mmproj_size_in_bytes: Option<u64>,
    pub format: ModelFormat,
    /// SHA-256 hash for download verification. `null` means skip verification
    /// (user accepts whatever bytes arrive — used when we don't pre-compute).
    pub sha256: Option<String>,
    pub size_in_bytes: u64,
    pub context_window: u32,
    pub is_multimodal: bool,
    /// Slots this model is assigned to by default (empty = optional download).
    pub default_slots: Vec<ModelSlot>,
    pub configurations: Vec<ModelConfiguration>,
}

impl ChatModelAsset {
    /// Returns mmproj `(file, sha256, size)` when all integrity fields are present.
    /// Android skips mmproj downloads unless every field is set.
    pub fn mmproj_artifact(&self) -> Option<(&str, &str, u64)> {
        let file = self.mmproj_file.as_deref()?;
        let sha = self.mmproj_sha256.as_deref()?;
        let size = self.mmproj_size_in_bytes?;
        if sha.is_empty() || size == 0 {
            return None;
        }
        Some((file, sha, size))
    }
}

/// A single utility (non-chat) model entry in the catalog.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UtilityModelAsset {
    pub id: String,
    pub display_name: String,
    pub source: ModelSource,
    pub repo: String,
    /// Sub-path inside the HF repo (e.g. `"onnx"` for `…/resolve/main/onnx/model.onnx`).
    #[serde(default)]
    pub hugging_face_path: Option<String>,
    pub file: String,
    pub format: ModelFormat,
    pub sha256: Option<String>,
    pub size_in_bytes: u64,
    pub utility_type: UtilityType,
}

/// LLM sampling/prompting preset for a chat model.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ModelConfiguration {
    pub config_id: String,
    pub display_name: String,
    pub system_prompt: String,
    pub temperature: f32,
    pub top_p: f32,
    pub top_k: u32,
    pub min_p: f32,
    pub repetition_penalty: f32,
    pub context_window: u32,
    pub max_tokens: u32,
    pub thinking_enabled: bool,
}

/// The full desktop model catalog — bundled as `desktop/models/desktop_models.json`.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModelCatalog {
    #[serde(rename = "chatModels")]
    pub chat_models: Vec<ChatModelAsset>,
    #[serde(rename = "utilityModels")]
    pub utility_models: Vec<UtilityModelAsset>,
}

/// Specification for a single file download — mirrors Android `DownloadFileSpec`.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DownloadFileSpec {
    /// Full HTTPS URL to download from.
    pub url: String,
    /// Absolute target path (final name, not the temp file).
    pub target_path: PathBuf,
    /// Expected SHA-256 hex digest. If `None`, verification is skipped.
    pub expected_sha256: Option<String>,
    /// Expected file size in bytes. If `0`, size verification is skipped.
    pub expected_size: u64,
    /// Human-readable display name for progress UI.
    pub display_name: String,
    /// Catalog model id — populated for registry finalize after download.
    #[serde(default)]
    pub model_id: Option<String>,
    /// Council slot to register when this file satisfies a slot assignment.
    #[serde(default)]
    pub slot: Option<ModelSlot>,
    /// File format — used when registering the installed model.
    #[serde(default)]
    pub format: Option<ModelFormat>,
}

/// Per-file progress emitted during download.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DownloadProgress {
    pub display_name: String,
    pub downloaded: u64,
    pub total: u64,
    pub status: DownloadFileStatus,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum DownloadFileStatus {
    Queued,
    Downloading,
    Complete,
    Failed,
}

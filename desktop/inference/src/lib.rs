//! Desktop inference layer — wires `agent_core` to local and cloud model
//! engines. The presentation layer talks to `InferenceService` (domain port);
//! this crate implements that port via `AgentEngine` + pluggable transports.
//!
//! Engine selection mirrors the Android `InferenceFactoryImpl`: file format
//! determines the engine (`.gguf` → llama.cpp, `api://` → HTTP). `agent_core`
//! manages the agent loop, tool-calling, and compaction — we supply
//! `AgentTransport` impls that call the chosen engine.

use async_trait::async_trait;
use enclave_desktop_domain::inference::{InferenceEvent, InferenceService};
use enclave_desktop_domain::models::ChatMessage;
use enclave_desktop_domain::repositories::LocalModelRepository;
use std::sync::{Arc, Mutex};
use thiserror::Error;
use tokio::sync::mpsc;

pub mod api_transport;
pub mod engine;
#[cfg(feature = "llama")]
pub mod llama_engine;
pub mod process_thinking_tokens;
pub mod transport;

#[cfg(test)]
mod test_utils;

pub use api_transport::ApiTransport;
pub use engine::{InferenceEngine, InferenceFactory, InferenceRequest, InferenceResponse};
#[cfg(feature = "llama")]
pub use llama_engine::LlamaEngine;
pub use transport::EngineTransport;

#[derive(Error, Debug, Clone)]
pub enum InferenceError {
    #[error("Agent core error: {0}")]
    AgentCore(String),
    #[error("Cloud API error: {0}")]
    CloudApi(String),
    #[error("Engine error: {0}")]
    Engine(String),
    #[error("No model loaded")]
    NoModel,
    #[error("Serialization error: {0}")]
    Serialization(String),
}

impl From<String> for InferenceError {
    fn from(s: String) -> Self {
        InferenceError::Engine(s)
    }
}

impl From<serde_json::Error> for InferenceError {
    fn from(e: serde_json::Error) -> Self {
        InferenceError::Serialization(e.to_string())
    }
}

/// Mock inference service — streams a canned response in chunks so the chat
/// UI's streaming path works end-to-end without a real model. Replaced by
/// [`AgentEngineInferenceService`] once a model is loaded.
pub struct LocalAndCloudInferenceService;

impl Default for LocalAndCloudInferenceService {
    fn default() -> Self {
        Self::new()
    }
}

impl LocalAndCloudInferenceService {
    pub fn new() -> Self {
        Self
    }
}

const MOCK_RESPONSE: &str = "## Streaming Markdown Demo\n\nThis response arrives **incrementally** with live formatting.\n\n- First item\n- Second item with `inline code`\n\n```rust\nfn hello(name: &str) -> String {\n    format!(\"Hello, {name}!\")\n}\n```\n";

#[async_trait]
impl InferenceService for LocalAndCloudInferenceService {
    type Error = InferenceError;

    async fn generate_response_stream(
        &self,
        history: &[ChatMessage],
        prompt: &str,
        _model_id: Option<&str>,
    ) -> Result<mpsc::Receiver<InferenceEvent>, InferenceError> {
        let (tx, rx) = mpsc::channel(128);
        let response = if prompt.to_lowercase().contains("cloud") {
            format!(
                "Cloud API response for: '{}'. (History count: {})",
                prompt,
                history.len()
            )
        } else {
            MOCK_RESPONSE.to_string()
        };

        tokio::spawn(async move {
            // Stream the response in ~12-char chunks with a small delay so
            // the chat UI animates the streaming text.
            let chars: Vec<char> = response.chars().collect();
            for chunk in chars.chunks(12) {
                let text: String = chunk.iter().collect();
                let _ = tx.send(InferenceEvent::TokenChunk(text)).await;
                tokio::time::sleep(std::time::Duration::from_millis(20)).await;
            }
            let _ = tx.send(InferenceEvent::Complete).await;
        });

        Ok(rx)
    }
}

/// Agent-engine-backed inference service — drives the loaded
/// [`InferenceEngine`] directly. This is the real inference path that replaces
/// the mock once models are available.
///
/// **Hot-swap:** when a turn specifies a `model_id`, the service resolves it
/// via the [`LocalModelRepository`] and compares the resolved `local_path`
/// against the currently-loaded one. If they differ, the engine loads the new
/// model before generating; if they match, the load is skipped (idempotent).
/// This mirrors the Android `InferenceFactoryImpl.resolveService` lazy
/// identity-comparison pattern — selection at the picker writes state, the
/// actual reload happens on the next turn.
///
/// **Streaming:** generation runs via
/// [`InferenceEngine::complete_turn_streaming`]; each decoded token piece is
/// forwarded to the caller as a [`InferenceEvent::TokenChunk`], mirroring the
/// Android `NativeTokenCallback.onToken` bridge.
///
/// The `EngineTransport` / `agent_core` `AgentTransport` path stays available
/// for the future agent-loop integration (tool-calling, compaction, thinking
/// budget). It is bypassed for the streaming MVP because the agent loop
/// requires concrete (non-`dyn`) transport types, complicating dynamic engine
/// swapping — see the design note in the original implementation.
pub struct AgentEngineInferenceService {
    engine: Arc<dyn InferenceEngine>,
    model_repo: Arc<dyn LocalModelRepository<Error = InferenceError>>,
    /// The `local_path` of the model currently loaded into `engine`. Used to
    /// decide whether a hot-swap reload is needed. Mirrors the Android
    /// `InferenceFactoryImpl.activeIdentities` cache (single local engine).
    loaded_model_path: Mutex<Option<String>>,
    system_prompt: String,
    context_window: u32,
}

impl AgentEngineInferenceService {
    pub fn new(
        engine: Arc<dyn InferenceEngine>,
        model_repo: Arc<dyn LocalModelRepository<Error = InferenceError>>,
    ) -> Self {
        Self {
            engine,
            model_repo,
            loaded_model_path: Mutex::new(None),
            system_prompt: "You are Enclave, a helpful, privacy-first AI assistant.".into(),
            context_window: 32_768,
        }
    }

    pub fn with_system_prompt(mut self, prompt: impl Into<String>) -> Self {
        self.system_prompt = prompt.into();
        self
    }

    pub fn with_context_window(mut self, window: u32) -> Self {
        self.context_window = window;
        self
    }

    pub fn with_loaded_model_path(self, path: impl Into<String>) -> Self {
        *self
            .loaded_model_path
            .lock()
            .expect("loaded_model_path poisoned") = Some(path.into());
        self
    }

    /// Ensure the engine has `requested_path` loaded, reloading only when it
    /// differs from the currently-loaded model. Mirrors the lazy identity
    /// comparison in `InferenceFactoryImpl.resolveService`.
    fn ensure_model_loaded(&self, requested_path: &str) -> Result<(), InferenceError> {
        let mut guard = self
            .loaded_model_path
            .lock()
            .map_err(|e| InferenceError::Engine(format!("loaded_model_path poisoned: {e}")))?;
        let needs_load = guard.as_deref() != Some(requested_path);
        if needs_load {
            self.engine
                .load_model(requested_path, None, self.context_window)
                .map_err(|e| InferenceError::Engine(format!("model load: {e}")))?;
            *guard = Some(requested_path.to_string());
        }
        Ok(())
    }
}

#[async_trait]
impl InferenceService for AgentEngineInferenceService {
    type Error = InferenceError;

    async fn generate_response_stream(
        &self,
        history: &[ChatMessage],
        prompt: &str,
        model_id: Option<&str>,
    ) -> Result<mpsc::Receiver<InferenceEvent>, InferenceError> {
        // Resolve the model path for this turn. If a `model_id` is supplied,
        // look it up; otherwise fall back to the Main-slot default.
        let model_path = match model_id {
            Some(id) => {
                let entry = self
                    .model_repo
                    .find_by_id(id)
                    .await
                    .map_err(|e| InferenceError::Engine(e.to_string()))?
                    .ok_or(InferenceError::NoModel)?;
                entry.local_path
            }
            None => {
                use enclave_desktop_domain::download::ModelSlot;
                let entry = self
                    .model_repo
                    .find_by_slot(ModelSlot::Main)
                    .await
                    .map_err(|e| InferenceError::Engine(e.to_string()))?
                    .ok_or(InferenceError::NoModel)?;
                entry.local_path
            }
        };

        // Hot-swap check: only reload when the requested path differs from the
        // currently-loaded one.
        self.ensure_model_loaded(&model_path)?;

        // Build the engine request from chat history + the new prompt. The
        // prompt is appended as the final user turn.
        let mut messages: Vec<(String, String)> = history
            .iter()
            .map(|msg| {
                let role = match msg.sender {
                    enclave_desktop_domain::models::MessageSender::User => "user",
                    enclave_desktop_domain::models::MessageSender::Assistant => "assistant",
                };
                (role.to_string(), msg.text.clone())
            })
            .collect();
        messages.push(("user".to_string(), prompt.to_string()));

        let request = InferenceRequest {
            system_prompt: self.system_prompt.clone(),
            messages,
            ..Default::default()
        };

        let (tx, rx) = mpsc::channel(128);
        let engine = self.engine.clone();
        let tx_clone = tx.clone();

        tokio::task::spawn_blocking(move || {
            #[derive(Default)]
            struct StreamParser {
                buffer: String,
                is_thinking: bool,
                accumulated_thinking: String,
                accumulated_visible: String,
                thinking_streamed: bool,
            }

            let parser = Arc::new(Mutex::new(StreamParser::default()));
            let parser_for_callback = parser.clone();

            let result = engine.complete_turn_streaming(&request, &move |piece: &str| {
                use process_thinking_tokens::{SegmentKind, process_thinking_tokens};

                let mut parser = parser_for_callback.lock().expect("stream parser lock");
                let state = process_thinking_tokens(
                    &parser.buffer,
                    piece,
                    parser.is_thinking,
                    &parser.accumulated_thinking,
                    &parser.accumulated_visible,
                );
                parser.buffer = state.buffer;
                parser.is_thinking = state.is_thinking;
                parser.accumulated_thinking = state.accumulated_thinking_text;
                parser.accumulated_visible = state.accumulated_visible_text;

                for segment in state.emitted_segments {
                    match segment.kind {
                        SegmentKind::Thinking => {
                            parser.thinking_streamed = true;
                            let _ =
                                tx_clone.blocking_send(InferenceEvent::ThinkingChunk(segment.text));
                        }
                        SegmentKind::Visible => {
                            let _ =
                                tx_clone.blocking_send(InferenceEvent::TokenChunk(segment.text));
                        }
                    }
                }
            });

            match result {
                Ok(response) => {
                    if let Some(reasoning) = response.reasoning {
                        let thinking_streamed =
                            parser.lock().expect("stream parser lock").thinking_streamed;
                        if !thinking_streamed {
                            let _ = tx.blocking_send(InferenceEvent::ThinkingChunk(reasoning));
                        }
                    }
                    let _ = tx.blocking_send(InferenceEvent::Complete);
                }
                Err(e) => {
                    let _ = tx.blocking_send(InferenceEvent::Error(e.to_string()));
                }
            }
        });

        Ok(rx)
    }
}

/// Resolve the default chat model from the registry and build an
/// [`AgentEngineInferenceService`]. Falls back with [`InferenceError::NoModel`]
/// when nothing is registered for [`ModelSlot::Main`], or when the model file
/// cannot be loaded.
pub async fn build_inference_from_registry<E>(
    model_repo: Arc<dyn LocalModelRepository<Error = E>>,
    context_window: u32,
) -> Result<Arc<dyn InferenceService<Error = InferenceError>>, InferenceError>
where
    E: std::fmt::Debug + std::fmt::Display + Send + Sync + 'static,
{
    use enclave_desktop_domain::download::ModelSlot;

    let entry = model_repo
        .find_by_slot(ModelSlot::Main)
        .await
        .map_err(|e| InferenceError::Engine(e.to_string()))?
        .ok_or(InferenceError::NoModel)?;

    let factory = {
        #[cfg(feature = "llama")]
        {
            InferenceFactory::with_default_llama()
        }
        #[cfg(not(feature = "llama"))]
        {
            InferenceFactory::new()
        }
    };

    let engine = factory.resolve(entry.format)?;

    // Defer `load_model` to the first chat turn — loading a multi-GB GGUF here
    // blocked Continue for minutes with no UI feedback.
    let adapted: Arc<dyn LocalModelRepository<Error = InferenceError>> =
        Arc::new(RepoErrorAdapter(model_repo));

    let svc = AgentEngineInferenceService::new(engine, adapted).with_context_window(context_window);
    Ok(Arc::new(svc))
}

/// Adapter that wraps a `LocalModelRepository<Error = E>` and surfaces it as
/// `LocalModelRepository<Error = InferenceError>`, converting errors by
/// rendering them to strings. Lets the streaming service hold a single
/// concrete error type while `build_inference_from_registry` stays generic
/// over the data layer's error.
struct RepoErrorAdapter<E: std::fmt::Debug + std::fmt::Display + Send + Sync + 'static>(
    Arc<dyn LocalModelRepository<Error = E>>,
);

#[async_trait]
impl<E> LocalModelRepository for RepoErrorAdapter<E>
where
    E: std::fmt::Debug + std::fmt::Display + Send + Sync + 'static,
{
    type Error = InferenceError;

    async fn register_installed(
        &self,
        entry: enclave_desktop_domain::repositories::InstalledModelEntry,
    ) -> Result<(), Self::Error> {
        self.0
            .register_installed(entry)
            .await
            .map_err(|e| InferenceError::Engine(e.to_string()))
    }

    async fn find_by_slot(
        &self,
        slot: enclave_desktop_domain::download::ModelSlot,
    ) -> Result<Option<enclave_desktop_domain::repositories::InstalledModelEntry>, Self::Error>
    {
        self.0
            .find_by_slot(slot)
            .await
            .map_err(|e| InferenceError::Engine(e.to_string()))
    }

    async fn list_all(
        &self,
    ) -> Result<Vec<enclave_desktop_domain::repositories::InstalledModelEntry>, Self::Error> {
        self.0
            .list_all()
            .await
            .map_err(|e| InferenceError::Engine(e.to_string()))
    }

    async fn find_by_id(
        &self,
        id: &str,
    ) -> Result<Option<enclave_desktop_domain::repositories::InstalledModelEntry>, Self::Error>
    {
        self.0
            .find_by_id(id)
            .await
            .map_err(|e| InferenceError::Engine(e.to_string()))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::engine::{FinishReason, InferenceRequest, InferenceResponse};
    use enclave_desktop_domain::download::{ModelFormat, ModelSlot};
    use enclave_desktop_domain::repositories::InstalledModelEntry;
    use std::sync::atomic::{AtomicU32, Ordering};

    #[tokio::test]
    async fn mock_service_streams_events() {
        let svc = LocalAndCloudInferenceService::new();
        let mut stream = svc
            .generate_response_stream(&[], "Hello", None)
            .await
            .expect("stream");
        let mut text = String::new();
        while let Some(event) = stream.recv().await {
            match event {
                InferenceEvent::TokenChunk(chunk) => text.push_str(&chunk),
                InferenceEvent::Complete => break,
                _ => {}
            }
        }
        assert!(!text.is_empty(), "should have received text chunks");
    }

    #[tokio::test]
    async fn mock_service_emits_complete() {
        let svc = LocalAndCloudInferenceService::new();
        let mut stream = svc
            .generate_response_stream(&[], "test", None)
            .await
            .expect("stream");
        let mut got_complete = false;
        while let Some(event) = stream.recv().await {
            if event == InferenceEvent::Complete {
                got_complete = true;
                break;
            }
        }
        assert!(got_complete, "should emit Complete event");
    }

    #[tokio::test]
    async fn build_inference_fails_without_registry_entry() {
        let repo: Arc<dyn LocalModelRepository<Error = InferenceError>> =
            Arc::new(StubModelRepo::with_entries(Vec::new()));
        match build_inference_from_registry(repo, 4096).await {
            Err(InferenceError::NoModel) => {}
            Ok(_) => panic!("expected NoModel"),
            Err(_) => panic!("expected NoModel"),
        }
    }

    #[tokio::test]
    async fn build_inference_builds_without_eager_model_load() {
        let tmp = tempfile::NamedTempFile::with_suffix(".gguf").expect("temp model file");
        let path = tmp.path().display().to_string();

        let repo: Arc<dyn LocalModelRepository<Error = InferenceError>> =
            Arc::new(StubModelRepo::with_entries(vec![InstalledModelEntry {
                id: "test-model".into(),
                slot: Some(ModelSlot::Main),
                sha256: "abc".into(),
                local_path: path,
                display_name: "Test".into(),
                format: ModelFormat::GGUF,
            }]));
        build_inference_from_registry(repo, 4096)
            .await
            .expect("build should succeed without reading the GGUF file");
    }

    /// `AgentEngineInferenceService` should reload the engine when the
    /// requested model id resolves to a different path than the one currently
    /// loaded (lazy hot-swap), and skip the load when the path matches.
    #[tokio::test]
    async fn hot_swap_reloads_only_when_path_changes() {
        let repo: Arc<dyn LocalModelRepository<Error = InferenceError>> =
            Arc::new(StubModelRepo::with_entries(vec![
                InstalledModelEntry {
                    id: "a".into(),
                    slot: Some(ModelSlot::Main),
                    sha256: "sha-a".into(),
                    local_path: "/models/a.gguf".into(),
                    display_name: "Model A".into(),
                    format: ModelFormat::GGUF,
                },
                InstalledModelEntry {
                    id: "b".into(),
                    slot: None,
                    sha256: "sha-b".into(),
                    local_path: "/models/b.gguf".into(),
                    display_name: "Model B".into(),
                    format: ModelFormat::GGUF,
                },
            ]));
        let engine = Arc::new(CountingMockEngine::default());
        let svc = AgentEngineInferenceService::new(engine.clone(), repo)
            .with_loaded_model_path("/models/a.gguf");

        // Same model id (a) — should NOT trigger a load.
        let _ = svc
            .generate_response_stream(&[], "hi", Some("a"))
            .await
            .expect("stream a");
        drain_stream().await;
        assert_eq!(engine.load_count.load(Ordering::SeqCst), 0);

        // Different model id (b) — should trigger exactly one load.
        let _ = svc
            .generate_response_stream(&[], "hi", Some("b"))
            .await
            .expect("stream b");
        drain_stream().await;
        assert_eq!(engine.load_count.load(Ordering::SeqCst), 1);

        // Back to b again — no further load.
        let _ = svc
            .generate_response_stream(&[], "hi", Some("b"))
            .await
            .expect("stream b2");
        drain_stream().await;
        assert_eq!(engine.load_count.load(Ordering::SeqCst), 1);
    }

    /// `AgentEngineInferenceService` should emit one `TokenChunk` per token
    /// piece produced by the engine's streaming callback.
    #[tokio::test]
    async fn streaming_emits_token_chunks() {
        let repo: Arc<dyn LocalModelRepository<Error = InferenceError>> =
            Arc::new(StubModelRepo::with_entries(vec![InstalledModelEntry {
                id: "m".into(),
                slot: Some(ModelSlot::Main),
                sha256: "s".into(),
                local_path: "/models/m.gguf".into(),
                display_name: "M".into(),
                format: ModelFormat::GGUF,
            }]));
        let engine = Arc::new(CountingMockEngine::default());
        let svc =
            AgentEngineInferenceService::new(engine, repo).with_loaded_model_path("/models/m.gguf");

        let mut rx = svc
            .generate_response_stream(&[], "ping", Some("m"))
            .await
            .expect("stream");

        let mut chunks = 0u32;
        let mut got_complete = false;
        while let Some(event) = rx.recv().await {
            match event {
                InferenceEvent::TokenChunk(_) => chunks += 1,
                InferenceEvent::Complete => {
                    got_complete = true;
                    break;
                }
                _ => {}
            }
        }
        // CountingMockEngine emits three pieces ("Hel", "lo ", "World").
        assert_eq!(chunks, 3);
        assert!(got_complete);
    }

    /// Wait for a stream to drain without asserting on its contents — used to
    /// ensure the spawned blocking task completes before the next assertion.
    async fn drain_stream() {
        tokio::task::yield_now().await;
    }

    /// A mock engine that counts `load_model` calls and emits a fixed
    /// three-piece response via the streaming callback.
    #[derive(Default)]
    struct CountingMockEngine {
        load_count: AtomicU32,
    }

    impl InferenceEngine for CountingMockEngine {
        fn load_model(
            &self,
            _model_path: &str,
            _mmproj_path: Option<&str>,
            _context_window: u32,
        ) -> Result<(), InferenceError> {
            self.load_count.fetch_add(1, Ordering::SeqCst);
            Ok(())
        }

        fn complete_turn(
            &self,
            _request: &InferenceRequest,
        ) -> Result<InferenceResponse, InferenceError> {
            unreachable!("streaming service should call complete_turn_streaming")
        }

        fn complete_turn_streaming(
            &self,
            _request: &InferenceRequest,
            on_token: &(dyn Fn(&str) + Send + Sync),
        ) -> Result<InferenceResponse, InferenceError> {
            for piece in ["Hel", "lo ", "World"] {
                on_token(piece);
            }
            Ok(InferenceResponse {
                text: "Hello World".into(),
                reasoning: None,
                finish_reason: FinishReason::Stop,
            })
        }

        fn clear_cache(&self) -> Result<(), InferenceError> {
            Ok(())
        }

        fn context_usage(&self) -> Result<(u32, u32), InferenceError> {
            Ok((0, 32768))
        }

        fn format(&self) -> ModelFormat {
            ModelFormat::GGUF
        }
    }

    /// Stub repo backing the hot-swap / streaming tests. Holds a list of
    /// entries and answers slot/id lookups from it.
    struct StubModelRepo {
        entries: Vec<InstalledModelEntry>,
    }

    impl StubModelRepo {
        fn with_entries(entries: Vec<InstalledModelEntry>) -> Self {
            Self { entries }
        }
    }

    #[async_trait]
    impl LocalModelRepository for StubModelRepo {
        type Error = InferenceError;

        async fn register_installed(&self, _: InstalledModelEntry) -> Result<(), Self::Error> {
            Ok(())
        }

        async fn find_by_slot(
            &self,
            slot: ModelSlot,
        ) -> Result<Option<InstalledModelEntry>, Self::Error> {
            Ok(self.entries.iter().find(|e| e.slot == Some(slot)).cloned())
        }

        async fn list_all(&self) -> Result<Vec<InstalledModelEntry>, Self::Error> {
            Ok(self.entries.clone())
        }

        async fn find_by_id(&self, id: &str) -> Result<Option<InstalledModelEntry>, Self::Error> {
            Ok(self.entries.iter().find(|e| e.id == id).cloned())
        }
    }
}

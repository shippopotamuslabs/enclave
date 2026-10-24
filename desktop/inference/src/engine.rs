//! Engine abstraction — trait that wraps the actual model execution backend
//! (llama.cpp, ONNX Runtime, or HTTP API). Each engine implements
//! [`InferenceEngine`], which is the local model I/O surface. The
//! [`transport`] module adapts these to `agent_core`'s `AgentTransport` trait.

use enclave_desktop_domain::download::ModelFormat;
use std::sync::Arc;

use crate::InferenceError;

/// A request to generate a model response — the host-side equivalent of
/// `agent_core`'s `ModelRequest`, simplified for the engine layer.
#[derive(Debug, Clone)]
pub struct InferenceRequest {
    /// System prompt (e.g. "You are Enclave, a helpful AI assistant.").
    pub system_prompt: String,
    /// Conversation history as (role, content) pairs, oldest first.
    pub messages: Vec<(String, String)>,
    /// Sampling temperature (0.0 = greedy, 1.0 = creative).
    pub temperature: f32,
    /// Top-k sampling (0 = disabled).
    pub top_k: u32,
    /// Top-p (nucleus) sampling.
    pub top_p: f32,
    /// Min-p sampling.
    pub min_p: f32,
    /// Repetition penalty (1.0 = disabled).
    pub repetition_penalty: f32,
    /// Frequency penalty (0.0 = disabled). Reduces the likelihood of
    /// repeating tokens proportional to how often they've appeared.
    pub frequency_penalty: f32,
    /// Presence penalty (0.0 = disabled). Reduces the likelihood of
    /// repeating any token that has appeared at least once.
    pub presence_penalty: f32,
    /// Maximum tokens to generate.
    pub max_tokens: u32,
    /// Reasoning budget in tokens (0 = thinking disabled).
    pub reasoning_budget: u32,
    /// Whether this is a continuation of a prior turn (KV-cache delta mode).
    pub continuation: bool,
    /// Number of prior messages already in the KV cache (for delta mode).
    pub prev_message_count: usize,
}

impl Default for InferenceRequest {
    fn default() -> Self {
        Self {
            system_prompt: String::new(),
            messages: Vec::new(),
            temperature: 0.7,
            top_k: 40,
            top_p: 0.95,
            min_p: 0.05,
            repetition_penalty: 1.1,
            // Android parity defaults (`llama-jni.cpp:907-908`).
            frequency_penalty: 0.05,
            presence_penalty: 0.05,
            max_tokens: 4096,
            reasoning_budget: 0,
            continuation: false,
            prev_message_count: 0,
        }
    }
}

/// The engine's response — mirrors `agent_core`'s `ModelResponse` but without
/// the tool-call parsing (that's `agent_core`'s job).
#[derive(Debug, Clone)]
pub struct InferenceResponse {
    /// The assistant's text output.
    pub text: String,
    /// Reasoning/thinking content (if the model produces it).
    pub reasoning: Option<String>,
    /// Why generation stopped (stop token, max tokens, etc.).
    pub finish_reason: FinishReason,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FinishReason {
    Stop,
    MaxTokens,
    Error,
}

/// Trait that every model engine implements. The engine is responsible for
/// loading the model file and running inference. This is the Rust equivalent
/// of the Android `RustCompatibleInferenceService::completeSingleTurn`.
pub trait InferenceEngine: Send + Sync {
    /// Load (or verify already loaded) a model at the given path with the
    /// given context window size. Idempotent — subsequent calls with the same
    /// path are no-ops.
    fn load_model(
        &self,
        model_path: &str,
        mmproj_path: Option<&str>,
        context_window: u32,
    ) -> Result<(), InferenceError>;

    /// Run a single inference turn. Returns the complete response (engines
    /// handle streaming internally; the caller gets the final result).
    fn complete_turn(
        &self,
        request: &InferenceRequest,
    ) -> Result<InferenceResponse, InferenceError>;

    /// Run a single inference turn, invoking `on_token` for each decoded
    /// token piece as it is generated. The final assembled
    /// [`InferenceResponse`] (with reasoning split out) is still returned.
    ///
    /// The default implementation falls back to [`Self::complete_turn`] and
    /// emits the whole text as a single token — engines that decode
    /// token-by-token (e.g. [`LlamaEngine`](crate::llama_engine::LlamaEngine))
    /// override this to stream incrementally. This mirrors the Android
    /// `NativeTokenCallback.onToken` bridge point in
    /// `JniLlamaEngine.generateWithOptions`.
    fn complete_turn_streaming(
        &self,
        request: &InferenceRequest,
        on_token: &(dyn Fn(&str) + Send + Sync),
    ) -> Result<InferenceResponse, InferenceError> {
        let response = self.complete_turn(request)?;
        on_token(&response.text);
        Ok(response)
    }

    /// Clear the KV cache (start a fresh conversation).
    fn clear_cache(&self) -> Result<(), InferenceError>;

    /// Get the current context usage (tokens used / total).
    fn context_usage(&self) -> Result<(u32, u32), InferenceError>;

    /// The format this engine handles.
    fn format(&self) -> ModelFormat;

    /// Request that any in-flight generation stops after its current token.
    /// The interrupted turn returns [`FinishReason::Stop`].
    ///
    /// Safe to call from any thread; ignored if no generation is running. The
    /// default is a no-op for engines that can't interrupt a turn in progress
    /// (e.g. `ApiTransport`, which blocks on a server round-trip). Local
    /// engines that decode token-by-token override this to flip a flag checked
    /// every iteration — see `LlamaEngine::request_stop`. This mirrors
    /// Android's `nativeStopCompletion` flipping the `g_generating` atomic
    /// (`llama-jni.cpp:1617-1624`, checked at `1384`).
    fn request_stop(&self) {}
}

/// Factory that selects the right engine for a model format. Mirrors the
/// Android `InferenceFactoryPort` / `InferenceFactoryImpl`.
pub struct InferenceFactory {
    /// The local llama.cpp engine (if compiled with the `llama` feature).
    #[cfg(feature = "llama")]
    llama_engine: Option<Arc<dyn InferenceEngine>>,
    /// The API transport engine (always available — works with Ollama, OpenAI, etc.).
    api_engine: Option<Arc<dyn InferenceEngine>>,
}

impl InferenceFactory {
    pub fn new() -> Self {
        Self {
            #[cfg(feature = "llama")]
            llama_engine: None,
            api_engine: None,
        }
    }

    /// Create a factory with the local `LlamaEngine` pre-registered (only
    /// available when compiled with the `llama` feature). The API engine
    /// must still be added via `with_api` if cloud/Ollama is desired.
    #[cfg(feature = "llama")]
    pub fn with_default_llama() -> Self {
        Self::new().with_llama(Arc::new(crate::llama_engine::LlamaEngine::new()))
    }

    /// Register a local llama.cpp engine.
    #[cfg(feature = "llama")]
    pub fn with_llama(mut self, engine: Arc<dyn InferenceEngine>) -> Self {
        self.llama_engine = Some(engine);
        self
    }

    /// Register an API transport engine.
    pub fn with_api(mut self, engine: Arc<dyn InferenceEngine>) -> Self {
        self.api_engine = Some(engine);
        self
    }

    /// Select the engine for the given model format.
    pub fn resolve(&self, format: ModelFormat) -> Result<Arc<dyn InferenceEngine>, InferenceError> {
        match format {
            ModelFormat::GGUF | ModelFormat::GGML => {
                #[cfg(feature = "llama")]
                {
                    self.llama_engine
                        .as_ref()
                        .cloned()
                        .ok_or(InferenceError::NoModel)
                }
                #[cfg(not(feature = "llama"))]
                {
                    Err(InferenceError::Engine(
                        "llama.cpp engine not compiled in (enable the `llama` feature)".into(),
                    ))
                }
            }
            ModelFormat::ONNX => {
                #[cfg(feature = "onnx")]
                {
                    Err(InferenceError::Engine(
                        "ONNX engine not yet implemented (feature is enabled)".into(),
                    ))
                }
                #[cfg(not(feature = "onnx"))]
                {
                    Err(InferenceError::Engine(
                        "ONNX engine not compiled in (enable the `onnx` feature)".into(),
                    ))
                }
            }
        }
    }

    /// Get the API transport engine (for cloud/Ollama models).
    pub fn api_engine(&self) -> Result<Arc<dyn InferenceEngine>, InferenceError> {
        self.api_engine.clone().ok_or(InferenceError::NoModel)
    }
}

impl Default for InferenceFactory {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn factory_resolves_gguf_to_llama() {
        let factory = InferenceFactory::new();
        // Without the llama feature, GGUF resolution errors.
        let result = factory.resolve(ModelFormat::GGUF);
        #[cfg(feature = "llama")]
        {
            // With llama feature but no engine registered, errors with NoModel.
            assert!(result.is_err());
        }
        #[cfg(not(feature = "llama"))]
        {
            assert!(result.is_err());
        }
    }

    #[test]
    fn mock_engine_complete_turn() {
        let engine = crate::test_utils::MockEngine::new(ModelFormat::GGUF);
        let req = InferenceRequest {
            messages: vec![("user".into(), "Hello".into())],
            ..Default::default()
        };
        let resp = engine.complete_turn(&req).expect("turn");
        assert!(resp.text.contains("Hello"));
    }
}

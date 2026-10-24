//! API transport — OpenAI-compatible HTTP engine. Works with Ollama
//! (`http://localhost:11434/v1`), OpenAI, OpenRouter, and any other
//! OpenAI-compatible endpoint. Implements [`InferenceEngine`] so it can be
//! used interchangeably with the local llama.cpp engine.
//!
//! This is the "users use Ollama on their own" path: the user configures
//! `DesktopSettings.api_endpoint` (e.g. `http://localhost:11434/v1`) and
//! `api_key`, and this engine routes chat completions to that endpoint.

use enclave_desktop_domain::download::ModelFormat;
use serde::{Deserialize, Serialize};

use crate::InferenceError;
use crate::engine::{FinishReason, InferenceEngine, InferenceRequest, InferenceResponse};

/// OpenAI-compatible chat completions request body.
#[derive(Debug, Serialize)]
struct ChatCompletionsRequest {
    model: String,
    messages: Vec<ChatMessage>,
    temperature: f32,
    top_p: f32,
    max_tokens: u32,
    stream: bool,
}

#[derive(Debug, Serialize)]
struct ChatMessage {
    role: String,
    content: String,
}

/// OpenAI-compatible chat completions response body.
#[derive(Debug, Deserialize)]
struct ChatCompletionsResponse {
    choices: Vec<Choice>,
}

#[derive(Debug, Deserialize)]
struct Choice {
    message: ResponseMessage,
    finish_reason: Option<String>,
}

#[derive(Debug, Deserialize)]
struct ResponseMessage {
    content: Option<String>,
    reasoning_content: Option<String>,
}

/// HTTP-based inference engine that talks to an OpenAI-compatible endpoint.
/// Thread-safe via `Arc<Mutex<reqwest::Client>>` — the client itself is
/// cheap to clone but we wrap it for interior mutability of the model name.
pub struct ApiTransport {
    client: reqwest::Client,
    endpoint: String,
    api_key: Option<String>,
    model_name: String,
    /// Context window for this endpoint (Ollama reports this; we default to
    /// a large value if not specified).
    context_window: u32,
}

impl ApiTransport {
    /// Create a new API transport.
    ///
    /// `endpoint` should be the base URL without the `/chat/completions` path
    /// (e.g. `http://localhost:11434/v1` or `https://api.openai.com/v1`).
    pub fn new(
        endpoint: impl Into<String>,
        api_key: Option<String>,
        model_name: impl Into<String>,
        context_window: u32,
    ) -> Self {
        Self {
            client: reqwest::Client::new(),
            endpoint: endpoint.into(),
            api_key,
            model_name: model_name.into(),
            context_window,
        }
    }

    /// Build the full chat completions URL.
    fn completions_url(&self) -> String {
        let base = self.endpoint.trim_end_matches('/');
        format!("{base}/chat/completions")
    }
}

impl InferenceEngine for ApiTransport {
    fn load_model(
        &self,
        _model_path: &str,
        _mmproj_path: Option<&str>,
        _context_window: u32,
    ) -> Result<(), InferenceError> {
        // API transport doesn't load a local model file — the "model" is
        // identified by `model_name` and resolved server-side. This is a no-op.
        Ok(())
    }

    fn complete_turn(
        &self,
        request: &InferenceRequest,
    ) -> Result<InferenceResponse, InferenceError> {
        // Build the request body.
        let messages: Vec<ChatMessage> = std::iter::once(ChatMessage {
            role: "system".into(),
            content: request.system_prompt.clone(),
        })
        .chain(request.messages.iter().map(|(role, content)| ChatMessage {
            role: role.clone(),
            content: content.clone(),
        }))
        .collect();

        let body = ChatCompletionsRequest {
            model: self.model_name.clone(),
            messages,
            temperature: request.temperature,
            top_p: request.top_p,
            max_tokens: request.max_tokens,
            stream: false,
        };

        // Block on the async HTTP call — `InferenceEngine::complete_turn` is
        // synchronous (matching `agent_core`'s `AgentTransport`). The iced
        // runtime's `spawn_blocking` handles this at the call site.
        let rt = tokio::runtime::Handle::current();
        let response = rt
            .block_on(async {
                let mut req = self.client.post(self.completions_url()).json(&body);
                if let Some(ref key) = self.api_key {
                    req = req.bearer_auth(key);
                }
                req.send().await
            })
            .map_err(|e| InferenceError::CloudApi(e.to_string()))?;

        if !response.status().is_success() {
            let status = response.status();
            let body = rt.block_on(response.text()).unwrap_or_default();
            return Err(InferenceError::CloudApi(format!("HTTP {status}: {body}")));
        }

        let parsed: ChatCompletionsResponse = rt
            .block_on(response.json())
            .map_err(|e| InferenceError::CloudApi(e.to_string()))?;

        let choice = parsed
            .choices
            .into_iter()
            .next()
            .ok_or_else(|| InferenceError::CloudApi("No choices in response".into()))?;

        let finish_reason = match choice.finish_reason.as_deref() {
            Some("stop") => FinishReason::Stop,
            Some("length") | Some("max_tokens") => FinishReason::MaxTokens,
            _ => FinishReason::Stop,
        };

        Ok(InferenceResponse {
            text: choice.message.content.unwrap_or_default(),
            reasoning: choice.message.reasoning_content,
            finish_reason,
        })
    }

    fn clear_cache(&self) -> Result<(), InferenceError> {
        // No local cache for API transport — the server manages context.
        Ok(())
    }

    fn context_usage(&self) -> Result<(u32, u32), InferenceError> {
        // The API doesn't report context usage — return a safe default.
        Ok((0, self.context_window))
    }

    fn format(&self) -> ModelFormat {
        // API transport is format-agnostic; we report GGUF as a placeholder
        // since the actual format depends on the server.
        ModelFormat::GGUF
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn api_transport_constructs() {
        let transport = ApiTransport::new(
            "http://localhost:11434/v1".to_string(),
            Some("test-key".to_string()),
            "llama3.2".to_string(),
            32768,
        );
        assert_eq!(
            transport.completions_url(),
            "http://localhost:11434/v1/chat/completions"
        );
    }

    #[test]
    fn api_transport_load_model_is_noop() {
        let transport = ApiTransport::new(
            "http://localhost:11434/v1".to_string(),
            None,
            "test".to_string(),
            4096,
        );
        transport.load_model("/dev/null", None, 4096).expect("noop");
    }

    #[test]
    fn api_transport_clear_cache_is_noop() {
        let transport = ApiTransport::new(
            "http://localhost:11434/v1".to_string(),
            None,
            "test".to_string(),
            4096,
        );
        transport.clear_cache().expect("noop");
    }
}

//! Transport adapters — bridge [`InferenceEngine`] implementations to
//! `agent_core`'s [`AgentTransport`] trait. The engine does model I/O; the
//! transport translates `agent_core`'s `ModelRequest` into an
//! [`InferenceRequest`] and the engine's [`InferenceResponse`] back into a
//! `ModelResponse`.

use agent_core::events::AgentError;
use agent_core::host::AgentTransport;
use agent_core::message::AgentRole;
use agent_core::turn_io::{FinishReason as AgentFinishReason, ModelRequest, ModelResponse};
use std::sync::Arc;

use crate::engine::{FinishReason, InferenceEngine, InferenceRequest};

/// Sampling configuration for an [`EngineTransport`]. Grouped into a struct so
/// the builder API stays below clippy's `too_many_arguments` threshold and so
/// callers can construct/serialize a sampling profile independently of the
/// transport itself. Defaults mirror the Android defaults
/// (`llama-jni.cpp:906-909`).
#[derive(Debug, Clone, Copy)]
pub struct SamplingParams {
    pub temperature: f32,
    pub top_k: u32,
    pub top_p: f32,
    pub min_p: f32,
    pub repetition_penalty: f32,
    pub frequency_penalty: f32,
    pub presence_penalty: f32,
    pub max_tokens: u32,
}

impl Default for SamplingParams {
    fn default() -> Self {
        Self {
            temperature: 0.7,
            top_k: 40,
            top_p: 0.95,
            min_p: 0.05,
            repetition_penalty: 1.1,
            frequency_penalty: 0.05,
            presence_penalty: 0.05,
            max_tokens: 4096,
        }
    }
}

/// Transport that wraps an [`InferenceEngine`] for use with `agent_core`'s
/// `AgentEngine`. This is the Rust equivalent of the Android
/// `RustLoopInferenceService` + `LlamaInferenceServiceImpl::completeSingleTurn`
/// — it adapts the local engine to the agent-loop's model I/O contract.
pub struct EngineTransport {
    engine: Arc<dyn InferenceEngine>,
    /// System prompt to inject (from the model configuration).
    system_prompt: String,
    /// Sampling parameters (from the model configuration).
    sampling: SamplingParams,
    /// Reasoning budget (0 = thinking disabled).
    reasoning_budget: u32,
}

impl EngineTransport {
    pub fn new(engine: Arc<dyn InferenceEngine>) -> Self {
        Self {
            engine,
            system_prompt: String::new(),
            sampling: SamplingParams::default(),
            reasoning_budget: 0,
        }
    }

    pub fn with_system_prompt(mut self, prompt: impl Into<String>) -> Self {
        self.system_prompt = prompt.into();
        self
    }

    pub fn with_sampling(mut self, sampling: SamplingParams) -> Self {
        self.sampling = sampling;
        self
    }

    pub fn with_reasoning_budget(mut self, budget: u32) -> Self {
        self.reasoning_budget = budget;
        self
    }

    /// Convert `agent_core`'s `ModelRequest` (session messages) into the
    /// engine's `InferenceRequest` (role/content pairs).
    fn build_request(&self, model_request: &ModelRequest) -> InferenceRequest {
        let messages: Vec<(String, String)> = model_request
            .session_messages
            .iter()
            .map(|m| {
                let role = match m.role {
                    AgentRole::User => "user",
                    AgentRole::Assistant => "assistant",
                    AgentRole::System => "system",
                    AgentRole::Tool => "tool",
                    AgentRole::CompactSummary => "system",
                };
                (role.to_string(), m.content.clone())
            })
            .collect();

        InferenceRequest {
            system_prompt: if model_request.system_prompt.is_empty() {
                self.system_prompt.clone()
            } else {
                model_request.system_prompt.clone()
            },
            messages,
            temperature: self.sampling.temperature,
            top_k: self.sampling.top_k,
            top_p: self.sampling.top_p,
            min_p: self.sampling.min_p,
            repetition_penalty: self.sampling.repetition_penalty,
            frequency_penalty: self.sampling.frequency_penalty,
            presence_penalty: self.sampling.presence_penalty,
            max_tokens: self.sampling.max_tokens,
            reasoning_budget: self.reasoning_budget,
            continuation: false,
            prev_message_count: 0,
        }
    }
}

impl AgentTransport for EngineTransport {
    fn complete_model_turn(&self, request: ModelRequest) -> Result<ModelResponse, AgentError> {
        let inference_request = self.build_request(&request);
        let response = self
            .engine
            .complete_turn(&inference_request)
            .map_err(|e| AgentError::Internal(e.to_string()))?;

        let finish_reason = match response.finish_reason {
            FinishReason::Stop => AgentFinishReason::Stop,
            FinishReason::MaxTokens => AgentFinishReason::MaxTokens,
            FinishReason::Error => AgentFinishReason::Other("engine_error".into()),
        };

        Ok(ModelResponse {
            assistant_text: Some(response.text),
            tool_calls: Vec::new(),
            finish_reason,
            provider_reasoning_content: response.reasoning,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::test_utils::MockEngine;
    use agent_core::message::{AgentMessage, AgentRole};
    use enclave_desktop_domain::download::ModelFormat;

    #[test]
    fn transport_adapts_engine_to_agent_core() {
        let engine = Arc::new(MockEngine::new(ModelFormat::GGUF));
        let transport = EngineTransport::new(engine).with_system_prompt("You are a test bot.");

        let request = ModelRequest {
            session_messages: vec![AgentMessage {
                id: "u1".into(),
                role: AgentRole::User,
                content: "Hello there".into(),
                metadata: serde_json::Value::Null,
            }],
            system_prompt: String::new(),
            ..Default::default()
        };

        let response = transport
            .complete_model_turn(request)
            .expect("transport turn");

        assert!(
            response
                .assistant_text
                .as_deref()
                .unwrap()
                .contains("Hello there")
        );
        assert!(response.tool_calls.is_empty());
        assert!(matches!(response.finish_reason, AgentFinishReason::Stop));
    }

    #[test]
    fn transport_preserves_reasoning_content() {
        /// A mock engine that returns reasoning content.
        struct ReasoningEngine;
        impl InferenceEngine for ReasoningEngine {
            fn load_model(
                &self,
                _: &str,
                _: Option<&str>,
                _: u32,
            ) -> Result<(), crate::InferenceError> {
                Ok(())
            }
            fn complete_turn(
                &self,
                _: &InferenceRequest,
            ) -> Result<crate::engine::InferenceResponse, crate::InferenceError> {
                Ok(crate::engine::InferenceResponse {
                    text: "The answer is 42".into(),
                    reasoning: Some("Thinking about this...".into()),
                    finish_reason: FinishReason::Stop,
                })
            }
            fn clear_cache(&self) -> Result<(), crate::InferenceError> {
                Ok(())
            }
            fn context_usage(&self) -> Result<(u32, u32), crate::InferenceError> {
                Ok((0, 4096))
            }
            fn format(&self) -> ModelFormat {
                ModelFormat::GGUF
            }
        }

        let transport = EngineTransport::new(Arc::new(ReasoningEngine));
        let request = ModelRequest::default();
        let response = transport.complete_model_turn(request).expect("turn");
        assert_eq!(
            response.provider_reasoning_content.as_deref(),
            Some("Thinking about this...")
        );
    }
}

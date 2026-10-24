//! Shared test utilities — mock engines used across the inference crate's
//! test modules. Only compiled under `#[cfg(test)]`.

use enclave_desktop_domain::download::ModelFormat;

use crate::InferenceError;
use crate::engine::{FinishReason, InferenceEngine, InferenceRequest, InferenceResponse};

/// A mock engine that echoes the last user message — used for testing the
/// factory and transport wiring without a real model.
pub struct MockEngine {
    fmt: ModelFormat,
}

impl MockEngine {
    pub fn new(fmt: ModelFormat) -> Self {
        Self { fmt }
    }
}

impl InferenceEngine for MockEngine {
    fn load_model(
        &self,
        _path: &str,
        _mmproj: Option<&str>,
        _ctx: u32,
    ) -> Result<(), InferenceError> {
        Ok(())
    }

    fn complete_turn(
        &self,
        request: &InferenceRequest,
    ) -> Result<InferenceResponse, InferenceError> {
        let last = request
            .messages
            .iter()
            .rfind(|(role, _)| role == "user")
            .map(|(_, text)| text.clone())
            .unwrap_or_default();
        Ok(InferenceResponse {
            text: format!("Mock response to: {last}"),
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
        self.fmt
    }
}

use enclave_desktop_domain::inference::InferenceService;
use enclave_desktop_domain::models::ChatMessage;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum InferenceError {
    #[error("Agent core error: {0}")]
    AgentCore(String),
    #[error("Cloud API error: {0}")]
    CloudApi(String),
}

pub struct LocalAndCloudInferenceService;

impl LocalAndCloudInferenceService {
    pub fn new() -> Self {
        Self
    }
}

impl InferenceService for LocalAndCloudInferenceService {
    type Error = InferenceError;

    async fn generate_response(
        &self,
        history: &[ChatMessage],
        prompt: &str,
    ) -> Result<String, Self::Error> {
        // Concrete implementation would load and check local models using agent_core,
        // or trigger a cloud API call based on configurations.
        // For now, we mock the local inference engine response.
        if prompt.to_lowercase().contains("cloud") {
            Ok(format!(
                "Cloud API response for: '{}'. (History count: {})",
                prompt,
                history.len()
            ))
        } else {
            Ok(format!(
                "Local inference response for: '{}'. (History count: {})",
                prompt,
                history.len()
            ))
        }
    }
}

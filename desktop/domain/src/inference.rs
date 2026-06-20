use crate::models::ChatMessage;

pub trait InferenceService: Send + Sync {
    type Error: std::fmt::Debug + std::fmt::Display;

    async fn generate_response(
        &self,
        history: &[ChatMessage],
        prompt: &str,
    ) -> Result<String, Self::Error>;
}

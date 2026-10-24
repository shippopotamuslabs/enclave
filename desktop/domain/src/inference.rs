use crate::models::ChatMessage;
use async_trait::async_trait;

/// Events emitted during streaming inference — the presentation layer maps
/// these to incremental chat UI updates.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum InferenceEvent {
    /// A chunk of visible assistant text.
    TokenChunk(String),
    /// A chunk of thinking/reasoning text (shown in a collapsed section).
    ThinkingChunk(String),
    /// A tool call was initiated by the model.
    ToolCallStarted {
        name: String,
        arguments_json: String,
    },
    /// A tool call completed with a result.
    ToolCallResult { name: String, result_json: String },
    /// Generation completed successfully.
    Complete,
    /// An error occurred during generation.
    Error(String),
}

/// Inference service port — the presentation layer talks to this. The
/// non-streaming `generate_response` collects the stream internally; the
/// streaming `generate_response_stream` emits incremental events for real-time
/// UI updates.
///
/// The `model_id` parameter selects which downloaded model answers the turn.
/// `None` means "use the default/Main-slot model". Selection is consulted at
/// send time, not at picker-selection time — mirroring the Android
/// `InferenceFactoryImpl.resolveService` lazy hot-swap pattern, where the
/// engine compares the requested model identity against the loaded one and
/// reloads only when they differ. A future BYOK addition can replace
/// `Option<&str>` with a richer `ChatModelSelection::{Local, Api}` enum
/// (Android's `ChatModelOverride`) without changing the method shape.
///
/// The error type must implement `From<String>` so the default
/// `generate_response` can wrap stream errors.
#[async_trait]
pub trait InferenceService: Send + Sync {
    type Error: std::fmt::Debug + std::fmt::Display + From<String> + Send;

    /// Non-streaming convenience: collects all events from
    /// `generate_response_stream` and returns the assembled text.
    async fn generate_response(
        &self,
        history: &[ChatMessage],
        prompt: &str,
    ) -> Result<String, Self::Error> {
        let mut text = String::new();
        let mut stream = self.generate_response_stream(history, prompt, None).await?;
        while let Some(event) = stream.recv().await {
            match event {
                InferenceEvent::TokenChunk(chunk) => text.push_str(&chunk),
                InferenceEvent::Error(msg) => {
                    return Err(msg.into());
                }
                _ => {}
            }
        }
        Ok(text)
    }

    /// Streaming inference — returns a channel receiver that emits events as
    /// the model generates. The presentation layer feeds these into the chat
    /// UI via `Message::ChatStreamChunk` etc.
    async fn generate_response_stream(
        &self,
        history: &[ChatMessage],
        prompt: &str,
        model_id: Option<&str>,
    ) -> Result<tokio::sync::mpsc::Receiver<InferenceEvent>, Self::Error>;
}

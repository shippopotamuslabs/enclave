//noinspection SpellCheckingInspection
use iced::widget::text_editor;

use crate::screens::chat::stats::ChatStats;
use crate::widgets::StreamingMarkdownState;

pub struct ChatState {
    pub active_chat_id: String,
    #[allow(dead_code)]
    pub active_chat_name: String,
    pub input_content: text_editor::Content,
    pub messages: Vec<ChatMessage>,
    pub permission_mode: PermissionMode,
    pub selected_model: String,
    pub context_pct: u8,
    pub menu_open: bool,
    pub perm_picker_open: bool,
    pub model_picker_open: bool,
    /// Whether the chat-stats bottom sheet is open. Toggled by the input-bar
    /// circular-progress control (the desktop analogue of the Android top-bar
    /// "Chat stats" button).
    pub stats_sheet_open: bool,
    /// Seeded chat telemetry rendered by the stats bottom sheet.
    pub stats: ChatStats,
    pub anim: InputBarAnim,
}

#[derive(Debug, Clone)]
pub struct ChatMessage {
    pub sender: MessageSender,
    /// Raw markdown/plain source mirror for persistence and debugging.
    pub text: String,
    pub markdown: Option<StreamingMarkdownState>,
}

impl ChatMessage {
    pub fn user(text: String) -> Self {
        Self {
            sender: MessageSender::User,
            text,
            markdown: None,
        }
    }

    pub fn assistant(text: impl Into<String>) -> Self {
        let text = text.into();
        Self {
            sender: MessageSender::Assistant,
            markdown: Some(StreamingMarkdownState::from_static(&text)),
            text,
        }
    }

    pub fn streaming_assistant() -> Self {
        Self {
            sender: MessageSender::Assistant,
            text: String::new(),
            markdown: Some(StreamingMarkdownState::new()),
        }
    }

    pub fn append_stream_chunk(&mut self, chunk: &str) {
        self.text.push_str(chunk);
        if let Some(ref mut markdown) = self.markdown {
            markdown.append(chunk);
        }
    }

    pub fn finalize_stream(&mut self) {
        if let Some(ref mut markdown) = self.markdown {
            markdown.finalize();
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MessageSender {
    User,
    Assistant,
}

/// Permission mode for the active chat. Desktop-only concept with no Android
/// counterpart. The label appears in the input-bar dropdown.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PermissionMode {
    AskEveryTime,
    EditsAllowed,
    FullAccess,
    PlanMode,
}

impl PermissionMode {
    pub fn label(self) -> &'static str {
        match self {
            PermissionMode::AskEveryTime => "Ask every time",
            PermissionMode::EditsAllowed => "Edits allowed",
            PermissionMode::FullAccess => "Full access",
            PermissionMode::PlanMode => "Plan mode",
        }
    }

    pub const ALL: [PermissionMode; 4] = [
        PermissionMode::AskEveryTime,
        PermissionMode::EditsAllowed,
        PermissionMode::FullAccess,
        PermissionMode::PlanMode,
    ];
}

/// Input bar lifecycle. The bar rests in the vertical center for a brand-new
/// empty chat, falls to the bottom on the first send, then locks there.
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum InputBarAnim {
    Centered,
    Falling { progress: f32 },
    Locked,
}

impl InputBarAnim {
    /// True when the centered/empty layout should still be rendered (the logo
    /// is visible and the bar is not yet locked to the bottom).
    pub fn is_empty_state(self) -> bool {
        !matches!(self, InputBarAnim::Locked)
    }

    /// Normalized progress from 0.0 to 1.0 toward the bottom.
    pub fn progress(self) -> f32 {
        match self {
            InputBarAnim::Centered => 0.0,
            InputBarAnim::Falling { progress } => progress.clamp(0.0, 1.0),
            InputBarAnim::Locked => 1.0,
        }
    }
}

/// Pure tick math extracted from `update` so it can be unit-tested without an
/// `EnclaveApp`. Advances a `Falling` animation by `delta`, clamping at 1.0 and
/// switching to `Locked`. `Centered` and `Locked` are returned unchanged.
pub fn advance_anim(anim: InputBarAnim, delta: f32) -> InputBarAnim {
    match anim {
        InputBarAnim::Falling { progress } => {
            let next = (progress + delta).clamp(0.0, 1.0);
            if next >= 1.0 {
                InputBarAnim::Locked
            } else {
                InputBarAnim::Falling { progress: next }
            }
        }
        other => other,
    }
}

/// Seed assistant reply for `chat_1`, rendered via [`message_list`](super::message_list.rs)
/// as a comprehensive markdown capability demo.
const CHAT_1_ASSISTANT_DEMO: &str = include_str!("../../../seed/chat_1_assistant_demo.md");

impl ChatState {
    pub fn new(chat_id: String) -> Self {
        let (name, messages) = match chat_id.as_str() {
            "chat_1" => (
                "Analyzing Rust Iced performance".into(),
                vec![
                    ChatMessage::user(
                        "How does Iced compare to other UI toolkits on Linux?".into(),
                    ),
                    ChatMessage::assistant(CHAT_1_ASSISTANT_DEMO),
                ],
            ),
            "chat_2" => (
                "Android Compose compiler options".into(),
                vec![
                    ChatMessage::user(
                        "How do I optimize Jetpack Compose compile times?".into(),
                    ),
                    ChatMessage::assistant(
                        "To optimize Compose compile times, ensure you are utilizing Hilt or Dagger efficiently to prevent KSP bottlenecking. You can also enable Compose compiler metrics in your gradle configuration to inspect unstable parameters causing excessive recompositions.",
                    ),
                ],
            ),
            _ => ("New Chat".into(), Vec::new()),
        };

        let is_empty = messages.is_empty();

        Self {
            active_chat_id: chat_id,
            active_chat_name: name,
            input_content: text_editor::Content::default(),
            messages,
            permission_mode: PermissionMode::AskEveryTime,
            selected_model: "On-Device (LiteRT)".to_string(),
            context_pct: 12,
            menu_open: false,
            perm_picker_open: false,
            model_picker_open: false,
            stats_sheet_open: false,
            stats: ChatStats::sample(),
            anim: if is_empty {
                InputBarAnim::Centered
            } else {
                InputBarAnim::Locked
            },
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn permission_mode_labels_are_human_readable() {
        assert_eq!(PermissionMode::AskEveryTime.label(), "Ask every time");
        assert_eq!(PermissionMode::EditsAllowed.label(), "Edits allowed");
        assert_eq!(PermissionMode::FullAccess.label(), "Full access");
        assert_eq!(PermissionMode::PlanMode.label(), "Plan mode");
        assert_eq!(PermissionMode::ALL.len(), 4);
    }

    #[test]
    fn input_bar_anim_empty_state_classification() {
        assert!(InputBarAnim::Centered.is_empty_state());
        assert!(InputBarAnim::Falling { progress: 0.5 }.is_empty_state());
        assert!(!InputBarAnim::Locked.is_empty_state());
    }

    #[test]
    fn input_bar_anim_progress_is_normalized() {
        assert_eq!(InputBarAnim::Centered.progress(), 0.0);
        assert_eq!(InputBarAnim::Falling { progress: 0.3 }.progress(), 0.3);
        assert_eq!(InputBarAnim::Falling { progress: 1.5 }.progress(), 1.0);
        assert_eq!(InputBarAnim::Locked.progress(), 1.0);
    }

    #[test]
    fn advance_anim_transitions_centered_to_falling_on_first_tick() {
        // Centered is returned unchanged by `advance_anim` — the transition to
        // Falling is driven by `ChatSendMessage`, not the tick.
        assert_eq!(
            advance_anim(InputBarAnim::Centered, 0.08),
            InputBarAnim::Centered
        );
    }

    #[test]
    fn advance_anim_advances_falling_and_clamps_to_locked() {
        let mid = advance_anim(InputBarAnim::Falling { progress: 0.0 }, 0.5);
        assert_eq!(mid, InputBarAnim::Falling { progress: 0.5 });

        let locked = advance_anim(InputBarAnim::Falling { progress: 0.5 }, 0.6);
        assert_eq!(locked, InputBarAnim::Locked);

        // Overshoot clamps to Locked rather than exceeding 1.0.
        let overshoot = advance_anim(InputBarAnim::Falling { progress: 0.9 }, 0.5);
        assert_eq!(overshoot, InputBarAnim::Locked);
    }

    #[test]
    fn advance_anim_leaves_locked_unchanged() {
        assert_eq!(
            advance_anim(InputBarAnim::Locked, 0.08),
            InputBarAnim::Locked
        );
    }
}

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
    /// The registry id of the model the picker has selected. `None` until the
    /// available-models list is loaded (see `ChatModelsLoaded`); drives the
    /// inference `model_id` parameter at send time.
    pub selected_model_id: Option<String>,
    /// Installed models available to pick from, populated from
    /// `LocalModelRepository::list_all`. Empty until loaded.
    pub available_models: Vec<enclave_desktop_domain::repositories::InstalledModelEntry>,
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
    /// Live turn indicator + thinking trace for the in-flight assistant reply.
    pub turn_thinking: TurnThinkingState,
    pub anim: InputBarAnim,
}

/// Indicator phases for the active assistant turn — mirrors Android
/// `IndicatorState` (Thinking / Processing / Complete-with-thought-header).
#[derive(Debug, Clone, PartialEq, Eq)]
#[allow(dead_code)]
pub enum TurnIndicator {
    None,
    Thinking,
    Processing,
    Complete { thinking_duration_secs: u32 },
}

/// Thinking trace + panel state for the streaming assistant turn.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TurnThinkingState {
    pub indicator: TurnIndicator,
    pub thinking_raw: String,
    pub thinking_panel_open: bool,
    pub thinking_elapsed_secs: u32,
    pub is_thinking_streaming: bool,
    pub turn_active: bool,
}

impl Default for TurnThinkingState {
    fn default() -> Self {
        Self {
            indicator: TurnIndicator::None,
            thinking_raw: String::new(),
            thinking_panel_open: false,
            thinking_elapsed_secs: 0,
            is_thinking_streaming: false,
            turn_active: false,
        }
    }
}

impl TurnThinkingState {
    #[allow(dead_code)]
    pub fn reset_for_new_turn(&mut self) {
        *self = Self {
            turn_active: true,
            indicator: TurnIndicator::Processing,
            ..Self::default()
        };
    }

    #[allow(dead_code)]
    pub fn on_thinking_chunk(&mut self, chunk: &str) {
        self.turn_active = true;
        self.indicator = TurnIndicator::Thinking;
        self.is_thinking_streaming = true;
        self.thinking_raw.push_str(chunk);
    }

    #[allow(dead_code)]
    pub fn on_visible_chunk(&mut self, assistant_visible_len: usize) {
        self.turn_active = true;
        self.is_thinking_streaming = false;
        if assistant_visible_len > 0 {
            self.indicator = TurnIndicator::None;
        } else {
            self.indicator = TurnIndicator::Processing;
        }
    }

    #[allow(dead_code)]
    pub fn finalize_turn(&mut self) {
        if self.thinking_raw.is_empty() {
            self.indicator = TurnIndicator::None;
        } else {
            self.indicator = TurnIndicator::Complete {
                thinking_duration_secs: self.thinking_elapsed_secs,
            };
        }
        self.is_thinking_streaming = false;
        self.turn_active = false;
    }

    pub fn has_thinking_trace(&self) -> bool {
        !self.thinking_raw.is_empty()
    }

    pub fn format_duration(secs: u32) -> String {
        if secs < 60 {
            format!("{secs}s")
        } else {
            format!("{}m {}s", secs / 60, secs % 60)
        }
    }
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
    #[allow(dead_code)]
    pub fn empty(chat_id: String) -> Self {
        Self {
            active_chat_id: chat_id,
            active_chat_name: "New Chat".into(),
            input_content: text_editor::Content::default(),
            messages: Vec::new(),
            permission_mode: PermissionMode::AskEveryTime,
            selected_model: "On-Device (Gemma 4 E4B)".to_string(),
            selected_model_id: None,
            available_models: Vec::new(),
            context_pct: 12,
            menu_open: false,
            perm_picker_open: false,
            model_picker_open: false,
            stats_sheet_open: false,
            stats: ChatStats::sample(),
            turn_thinking: TurnThinkingState::default(),
            anim: InputBarAnim::Centered,
        }
    }

    #[allow(dead_code)]
    pub fn from_repository(
        chat_id: String,
        title: String,
        messages: Vec<enclave_desktop_domain::models::ChatMessage>,
    ) -> Self {
        let presentation_messages: Vec<ChatMessage> = messages
            .iter()
            .map(domain_message_to_presentation)
            .collect();
        let is_empty = presentation_messages.is_empty();
        Self {
            active_chat_id: chat_id,
            active_chat_name: title,
            input_content: text_editor::Content::default(),
            messages: presentation_messages,
            permission_mode: PermissionMode::AskEveryTime,
            selected_model: "On-Device (Gemma 4 E4B)".to_string(),
            selected_model_id: None,
            available_models: Vec::new(),
            context_pct: 12,
            menu_open: false,
            perm_picker_open: false,
            model_picker_open: false,
            stats_sheet_open: false,
            stats: ChatStats::sample(),
            turn_thinking: TurnThinkingState::default(),
            anim: if is_empty {
                InputBarAnim::Centered
            } else {
                InputBarAnim::Locked
            },
        }
    }

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
                    ChatMessage::user("How do I optimize Jetpack Compose compile times?".into()),
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
            selected_model: "On-Device (Gemma 4 E4B)".to_string(),
            selected_model_id: None,
            available_models: Vec::new(),
            context_pct: 12,
            menu_open: false,
            perm_picker_open: false,
            model_picker_open: false,
            stats_sheet_open: false,
            stats: ChatStats::sample(),
            turn_thinking: TurnThinkingState::default(),
            anim: if is_empty {
                InputBarAnim::Centered
            } else {
                InputBarAnim::Locked
            },
        }
    }
}

#[allow(dead_code)]
pub fn domain_message_to_presentation(
    msg: &enclave_desktop_domain::models::ChatMessage,
) -> ChatMessage {
    match msg.sender {
        enclave_desktop_domain::models::MessageSender::User => ChatMessage::user(msg.text.clone()),
        enclave_desktop_domain::models::MessageSender::Assistant => {
            ChatMessage::assistant(msg.text.clone())
        }
    }
}

#[allow(dead_code)]
pub fn presentation_message_to_domain(
    msg: &ChatMessage,
    timestamp: u64,
) -> enclave_desktop_domain::models::ChatMessage {
    enclave_desktop_domain::models::ChatMessage {
        sender: match msg.sender {
            MessageSender::User => enclave_desktop_domain::models::MessageSender::User,
            MessageSender::Assistant => enclave_desktop_domain::models::MessageSender::Assistant,
        },
        text: msg.text.clone(),
        timestamp,
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

    #[test]
    fn empty_chat_state_starts_with_no_selected_model() {
        let state = ChatState::empty("chat_1".into());
        assert!(state.selected_model_id.is_none());
        assert!(state.available_models.is_empty());
    }

    #[test]
    fn chat_state_holds_selected_model_id_and_available_models() {
        use enclave_desktop_domain::download::{ModelFormat, ModelSlot};
        use enclave_desktop_domain::repositories::InstalledModelEntry;

        let mut state = ChatState::empty("chat_1".into());
        state.available_models = vec![
            InstalledModelEntry {
                id: "gemma-e4b".into(),
                slot: Some(ModelSlot::Main),
                sha256: "abc".into(),
                local_path: "/models/gemma.gguf".into(),
                display_name: "Gemma 4 E4B".into(),
                format: ModelFormat::GGUF,
            },
            InstalledModelEntry {
                id: "gemma-12b".into(),
                slot: None,
                sha256: "def".into(),
                local_path: "/models/gemma-12b.gguf".into(),
                display_name: "Gemma 4 12B".into(),
                format: ModelFormat::GGUF,
            },
        ];
        // Simulate a `ChatSelectModel { id, display_name }` update.
        state.selected_model_id = Some("gemma-12b".into());
        state.selected_model = "Gemma 4 12B".into();

        assert_eq!(state.selected_model_id.as_deref(), Some("gemma-12b"));
        assert_eq!(state.selected_model, "Gemma 4 12B");
        assert_eq!(state.available_models.len(), 2);
    }

    #[test]
    fn domain_presentation_message_roundtrip_preserves_text() {
        let original = ChatMessage::user("Hello vault".into());
        let domain = presentation_message_to_domain(&original, 42);
        let back = domain_message_to_presentation(&domain);
        assert_eq!(back.text, "Hello vault");
        assert_eq!(back.sender, MessageSender::User);
    }

    #[test]
    fn turn_thinking_tracks_streaming_phases() {
        let mut turn = TurnThinkingState::default();
        turn.reset_for_new_turn();
        assert!(matches!(turn.indicator, TurnIndicator::Processing));

        turn.on_thinking_chunk("plan");
        assert!(matches!(turn.indicator, TurnIndicator::Thinking));
        assert!(turn.is_thinking_streaming);

        turn.on_visible_chunk(0);
        assert!(matches!(turn.indicator, TurnIndicator::Processing));
        assert!(!turn.is_thinking_streaming);

        turn.on_visible_chunk(12);
        assert!(matches!(turn.indicator, TurnIndicator::None));

        turn.finalize_turn();
        assert!(matches!(turn.indicator, TurnIndicator::Complete { .. }));
    }

    #[test]
    fn turn_thinking_duration_formatting() {
        assert_eq!(TurnThinkingState::format_duration(45), "45s");
        assert_eq!(TurnThinkingState::format_duration(125), "2m 5s");
    }
}

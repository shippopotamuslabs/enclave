//noinspection SpellCheckingInspection
mod input_bar;
mod message_list;
pub mod state;
pub mod stats;

pub use state::{advance_anim, ChatMessage, ChatState, InputBarAnim, PermissionMode};
pub use stats::wrap_stats_sheet;

use iced::widget::{column, container, row, space, text};
use iced::{alignment, Element, Length, Padding, Theme};

use crate::icons;
use crate::screens::history::HistoryState;
use crate::theme::{self, AppTheme};
use crate::widgets::PaneSplitter;
use crate::Message;

// Empty-state hero sizing for the Enclave "E" source mark. The mark sits
// directly above the centered input bar (with a welcome line between them) as a
// single vertically-centered group. It shrinks as the input bar falls so the
// hero recedes when the user starts engaging, then disappears once the chat is
// active.
const E_MARK_BASE_SIZE: f32 = 132.0;

pub fn view<'a>(
    state: &'a ChatState,
    history_state: &'a HistoryState,
    app_theme: AppTheme,
) -> Element<'a, Message, Theme, iced::Renderer> {
    let is_dark = app_theme == AppTheme::Dark;

    // --- Render Left History Sidebar ---
    let history_sidebar = crate::screens::history::view(history_state, app_theme);

    let text_color = if is_dark {
        theme::DARK_TEXT
    } else {
        theme::LIGHT_TEXT
    };

    // The chat pane no longer renders its own app bar — the global title bar
    // (window controls) sits above it, and navigation/new-chat/studio entry
    // points already live in the history sidebar. Removing the per-pane bar
    // also removes the border it drew along its bottom edge, which was doubling
    // up with the title bar and history borders at every seam.

    // Message Thread
    let scroll_messages = message_list::view(state, app_theme);

    // --- Input bar ---
    let input_bar = input_bar::view(state, app_theme, text_color);

    // --- Animation progress + eased curve ---
    let raw_progress = state.anim.progress();
    // Ease-out so the bar lands with weight at the bottom.
    let eased = 1.0 - (1.0 - raw_progress).powi(2);

    // --- Chat body: empty/centered vs. active/bottom ---
    let is_empty_state = state.anim.is_empty_state();

    let chat_body: Element<Message, Theme, iced::Renderer> = if is_empty_state {
        // Empty-state hero: the solid Enclave "E" mark stacked above a welcome
        // line, sitting directly on top of the input bar as one vertically
        // centered group (the Z-style "new task" hero, rendered with the
        // Neobrutalist mark instead of a faint outline). The mark shrinks as the
        // bar falls so the whole hero recedes into the transition.
        let mark_size = E_MARK_BASE_SIZE * (1.0 - 0.6 * eased);

        let hero = column![
            container(icons::render_svg_untinted(&icons::HANDLE_ENCLAVE_E_MARK))
                .width(Length::Fixed(mark_size))
                .height(Length::Fixed(mark_size))
                .align_x(alignment::Horizontal::Center),
            text("Welcome to your Enclave.")
                .font(icons::FONT_BOLD)
                .size(22)
                .color(text_color),
        ]
        .spacing(12)
        .align_x(alignment::Horizontal::Center);

        // The hero+bar group's vertical position is driven by FillPortion
        // weights around it: at eased=0 the weights are equal (centered); as
        // eased→1 the top weight grows and the bottom weight shrinks so the
        // group slides to the bottom as the bar locks into place.
        let top_portion = (11.0 + eased * 10.0) as u16;
        let bottom_portion = (11.0 - eased * 10.0).max(1.0) as u16;

        let top_region = container(space::horizontal())
            .width(Length::Fill)
            .height(Length::FillPortion(top_portion));
        let bottom_region = container(space::horizontal())
            .width(Length::Fill)
            .height(Length::FillPortion(bottom_portion));

        column![top_region, hero, input_bar, bottom_region]
            .width(Length::Fill)
            .height(Length::Fill)
            .spacing(28)
            .padding(Padding {
                top: 0.0,
                right: 0.0,
                bottom: 12.0,
                left: 0.0,
            })
            .align_x(alignment::Horizontal::Center)
            .into()
    } else {
        // Active chat: message list fills the pane, bar pinned to the bottom.
        // Bottom padding lifts the whole input bar clear of the window edge so
        // its bottom border is fully visible.
        column![scroll_messages, input_bar]
            .width(Length::Fill)
            .height(Length::Fill)
            .padding(Padding {
                top: 0.0,
                right: 0.0,
                bottom: 12.0,
                left: 0.0,
            })
            .into()
    };

    // The chat pane is the body alone — no app bar. The body fills the pane; in
    // the empty state it carries the Enclave "E" hero (mark + welcome line)
    // directly above the centered input bar, and once messages arrive the hero
    // disappears and the bar pins to the bottom. `NeoPaneBackground` draws a
    // borderless fill so the pane sits flush against the pane splitter's single
    // vertical rule without doubling it up at the side seam.
    let chat_pane: Element<Message, Theme, iced::Renderer> = container(chat_body)
        .width(Length::Fill)
        .height(Length::Fill)
        .style(theme::neo_pane_background(theme::NeoPaneBackground {
            is_surface: false,
            theme: app_theme,
        }))
        .into();

    // One full-width bottom rule under the entire split row (history + splitter +
    // chat) so the horizontal edge meets the sidebar seam cleanly.
    let bottom_rule = container(space::horizontal())
        .width(Length::Fill)
        .height(Length::Fixed(2.0))
        .style(theme::neo_rule(theme::NeoRule { theme: app_theme }));

    let splitter_line = if is_dark {
        theme::DARK_NEO_BORDER
    } else {
        theme::LIGHT_NEO_BORDER
    };

    let pane_splitter = PaneSplitter::new(splitter_line, Message::HistorySidebarResize);

    // History sidebar width is driven by `HistoryState::sidebar_width`; the chat
    // pane fills the remaining horizontal space. Drag the splitter to resize.
    let split_layout = row![history_sidebar, pane_splitter, chat_pane]
        .width(Length::Fill)
        .height(Length::Fill);

    let chat_shell = container(
        column![split_layout, bottom_rule]
            .width(Length::Fill)
            .height(Length::Fill),
    )
    .width(Length::Fill)
    .height(Length::Fill)
    .style(theme::neo_pane_background(theme::NeoPaneBackground {
        is_surface: false,
        theme: app_theme,
    }));

    wrap_stats_sheet(
        chat_shell.into(),
        &state.stats,
        state.stats_sheet_open,
        app_theme,
    )
}

#[cfg(test)]
mod stack_reconcile_tests {
    //! Headless regression tests for the chat-pane widget-tree reconciliation.
    //! These reproduce the runtime's per-frame path (`Tree::new` then repeated
    //! `Tree::diff` against freshly-built views) without a window or GPU
    //! renderer, asserting the tree stays fully populated — the condition that,
    //! when violated, panics in `Popover::layout` with "index out of bounds:
    //! the len is 0". The empty-state body (hero + centered input bar) and the
    //! active body (message list + bottom bar) are different `column` shapes, so
    //! swapping between them still exercises the popover-slot reconciliation.

    use super::*;
    use crate::screens::history::HistoryState;
    use iced::advanced::widget::tree::Tree;

    /// Structural signature of a `Tree`: the child-count at every node, walked
    /// depth-first. Two structurally-identical widget trees produce the same
    /// signature regardless of element identity, so this is the right invariant
    /// to check across reconciliations — a corrupted subtree (e.g. a popover
    /// whose `children` got truncated) shifts the signature.
    fn shape_signature(tree: &Tree) -> Vec<usize> {
        let mut sig = vec![tree.children.len()];
        for child in &tree.children {
            sig.extend(shape_signature(child));
        }
        sig
    }

    /// Build the chat view for an empty (new) chat, snapshot the tree shape,
    /// then diff several freshly-rebuilt views (the exact runtime per-frame
    /// path) and assert the shape stays stable — i.e. nothing gets truncated or
    /// drifts.
    #[test]
    fn empty_chat_view_tree_stays_stable_across_rebuilds() {
        let state = ChatState::new("new_chat".into());
        let history = HistoryState::new();

        let element = view(&state, &history, AppTheme::Dark);
        let mut tree = Tree::new(&element);
        let baseline = shape_signature(&tree);
        assert!(baseline.len() > 10, "baseline tree should be sizeable");

        for i in 0..5 {
            let next = view(&state, &history, AppTheme::Dark);
            tree.diff(&next);
            assert_eq!(
                shape_signature(&tree),
                baseline,
                "tree shape drifted on rebuild {i} — a subtree was truncated"
            );
        }
    }

    /// Drive the actual navigation transitions the runtime performs between:
    ///
    /// - empty chat (hero + centered input bar), and
    /// - active chat (message list + bottom-pinned bar, after the first message
    ///   locks the bar).
    ///
    /// These swap the chat-body content, forcing per-slot `Tree::new` rebuilds
    /// where the widget types differ. Each transition must leave the tree
    /// structurally identical to a fresh build of the same view — the shape
    /// signature (child-counts at every depth) is the invariant, not raw node
    /// count (which can vary if a widget rebuilds throwaway subtrees).
    #[test]
    fn chat_transitions_produce_structurally_identical_tree() {
        let history = HistoryState::new();
        let dark = AppTheme::Dark;

        // Empty state: `Centered` anim → hero + centered input bar. The view
        // borrows `state`, so build the tree inside a block that drops the view
        // before we mutate `state` for the next transition.
        let mut state = ChatState::new("new_chat".into());
        let mut tree = {
            let element = view(&state, &history, dark);
            Tree::new(&element)
        };
        let empty_shape = shape_signature(&tree);

        // Send a message → the bar falls and locks → the body flips to the
        // active branch (message list + bottom-pinned bar).
        state.anim = InputBarAnim::Locked;
        {
            let element = view(&state, &history, dark);
            tree.diff(&element);
        }
        let active_shape_fresh = {
            let element = view(&state, &history, dark);
            shape_signature(&Tree::new(&element))
        };
        assert_eq!(
            shape_signature(&tree),
            active_shape_fresh,
            "active tree shape must match a fresh build after empty→active swap"
        );

        // Back to a fresh empty chat (e.g. "new chat" again): active→empty swap.
        let state2 = ChatState::new("new_chat".into());
        {
            let element = view(&state2, &history, dark);
            tree.diff(&element);
        }
        let empty_shape_fresh = {
            let element = view(&state2, &history, dark);
            shape_signature(&Tree::new(&element))
        };
        assert_eq!(
            shape_signature(&tree),
            empty_shape_fresh,
            "empty tree shape must match a fresh build after active→empty swap"
        );

        // One more round-trip to be sure the popover slots survive reuse.
        {
            let element = view(&state, &history, dark);
            tree.diff(&element);
        }
        assert_eq!(shape_signature(&tree), active_shape_fresh);
        {
            let element = view(&state2, &history, dark);
            tree.diff(&element);
        }
        assert_eq!(shape_signature(&tree), empty_shape_fresh);
        // ...and must equal the very first empty build, too.
        assert_eq!(shape_signature(&tree), empty_shape);
    }
}

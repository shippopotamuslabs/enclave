//! A z-ordering container.
//!
//! Iced 0.12 ships no `stack` widget (it landed in 0.13). `Stack` renders a
//! base layer plus any number of layers pushed on top, each sized to fill the
//! stack and positioned at the same origin so they overlap. Draw order is
//! base → last-pushed (later layers paint over earlier ones). Pointer events
//! are captured top-down so the front-most layer wins, matching the visual
//! z-order. Mirrors the layout/draw delegation pattern used by
//! [`super::popover::Popover`].
//!
//! Retained as a reusable layout primitive even though the chat empty-state no
//! longer layers the Enclave mark behind the app bar (the mark now sits inline
//! above the input bar). The module-level `allow` mirrors `icons.rs`: the
//! widget and its variants are part of the desktop widget toolbox, not all of
//! which is wired into a screen at any given time.
#![allow(dead_code)]

use iced::advanced::widget::tree::{State, Tag, Tree};
use iced::advanced::widget::Operation;
use iced::advanced::{layout, mouse, overlay, renderer, widget, Clipboard, Layout, Shell};
use iced::event::Event;
use iced::{Element, Length, Point, Rectangle, Size};

/// Alignment of a layer's content within the stack bounds.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum LayerAlign {
    /// Content pinned to the top-left of the stack (offset `(0, 0)`).
    #[default]
    Start,
    /// Content centered on both axes within the stack bounds.
    Center,
}

/// Zero-sized marker giving `Stack` a unique widget-state tag for iced's tree
/// reconciliation. Without it, `Stack` collides with every other stateless
/// widget (see `Stack::tag`).
#[allow(dead_code)]
struct StackMarker;

/// One stacked layer: the element plus how it aligns within the stack.
struct Layer<'a, Message, Theme, Renderer>
where
    Renderer: iced::advanced::Renderer,
{
    content: Element<'a, Message, Theme, Renderer>,
    align: LayerAlign,
}

/// A stack of overlapping layers. The base (first layer) is drawn at the
/// bottom; each subsequent [`Stack::layer`] paints on top of the previous.
pub struct Stack<'a, Message, Theme = iced::Theme, Renderer = iced::Renderer>
where
    Renderer: iced::advanced::Renderer,
{
    layers: Vec<Layer<'a, Message, Theme, Renderer>>,
    width: Length,
    height: Length,
}

impl<'a, Message, Theme, Renderer> Stack<'a, Message, Theme, Renderer>
where
    Renderer: iced::advanced::Renderer + 'a,
    Theme: 'a,
{
    /// Create a stack with a single base layer filling the stack bounds,
    /// aligned to the start (top-left).
    #[must_use]
    pub fn new(base: Element<'a, Message, Theme, Renderer>) -> Self {
        Stack {
            layers: vec![Layer {
                content: base,
                align: LayerAlign::Start,
            }],
            width: Length::Fill,
            height: Length::Fill,
        }
    }

    /// Push a layer on top of the previous one, filling the stack bounds.
    #[must_use]
    pub fn layer(mut self, content: Element<'a, Message, Theme, Renderer>) -> Self {
        self.layers.push(Layer {
            content,
            align: LayerAlign::Start,
        });
        self
    }

    /// Like [`Stack::layer`] but centered within the stack bounds.
    #[must_use]
    pub fn layer_centered(mut self, content: Element<'a, Message, Theme, Renderer>) -> Self {
        self.layers.push(Layer {
            content,
            align: LayerAlign::Center,
        });
        self
    }

    /// Set the stack's outer width.
    #[must_use]
    pub fn width(mut self, width: impl Into<Length>) -> Self {
        self.width = width.into();
        self
    }

    /// Set the stack's outer height.
    #[must_use]
    pub fn height(mut self, height: impl Into<Length>) -> Self {
        self.height = height.into();
        self
    }
}

impl<'a, Message, Theme, Renderer> widget::Widget<Message, Theme, Renderer>
    for Stack<'a, Message, Theme, Renderer>
where
    Message: Clone + 'a,
    Renderer: iced::advanced::Renderer + 'a,
    Theme: 'a,
{
    fn size(&self) -> Size<Length> {
        Size::new(self.width, self.height)
    }

    fn layout(
        &mut self,
        tree: &mut Tree,
        renderer: &Renderer,
        limits: &layout::Limits,
    ) -> layout::Node {
        // The stack itself fills whatever size it was given.
        let stack_size = limits.resolve(self.width, self.height, Size::ZERO);

        // Every layer is laid out against the same limits (the full stack
        // bounds), then translated to its alignment within that bounds so all
        // siblings overlap instead of flowing.
        let children = self
            .layers
            .iter_mut()
            .zip(&mut tree.children)
            .map(|(layer, state)| {
                let mut node = layer.content.as_widget_mut().layout(
                    state,
                    renderer,
                    &layout::Limits::new(Size::ZERO, stack_size),
                );

                let size = node.size();
                let offset = match layer.align {
                    LayerAlign::Start => Point::new(0.0, 0.0),
                    LayerAlign::Center => Point::new(
                        ((stack_size.width - size.width) / 2.0).max(0.0),
                        ((stack_size.height - size.height) / 2.0).max(0.0),
                    ),
                };

                node.move_to_mut(offset);
                node
            })
            .collect();

        layout::Node::with_children(stack_size, children)
    }

    fn draw(
        &self,
        tree: &Tree,
        renderer: &mut Renderer,
        theme: &Theme,
        inherited_style: &renderer::Style,
        layout: Layout<'_>,
        cursor: mouse::Cursor,
        viewport: &Rectangle,
    ) {
        // Paint bottom-up so later layers render on top of earlier ones.
        let layouts: Vec<Layout<'_>> = layout.children().collect();
        for (idx, layer) in self.layers.iter().enumerate() {
            layer.content.as_widget().draw(
                &tree.children[idx],
                renderer,
                theme,
                inherited_style,
                layouts[idx],
                cursor,
                viewport,
            );
        }
    }

    /// A `Stack`-specific state tag. This is the fix for the chat-screen panic
    /// (`popover.rs: index out of bounds: the len is 0`).
    ///
    /// Every stateless widget shares, by default, the same stateless tag
    /// (`TypeId::of::<()>`). The chat pane swaps between an empty-state `Stack`
    /// and an active `Column` across frames; both report that default tag, so
    /// the runtime believed the slot was the "same" widget and called
    /// `Column::diff` against a tree shaped for a `Stack` (or vice versa). That
    /// mismatched reconciliation left a leftover/duplicated subtree, which in
    /// turn emptied a nested `Popover`'s `tree.children` and panicked at layout.
    ///
    /// A distinct tag forces a full `Tree::new` rebuild whenever the slot's
    /// widget type changes — exactly what `Column`/`Container`/`tooltip` do.
    fn tag(&self) -> Tag {
        Tag::of::<StackMarker>()
    }

    fn state(&self) -> State {
        // `Stack` carries no meaningful runtime state, but pairing a unique
        // `tag()` with a (stateless) `state()` is the shape iced expects from a
        // widget that wants a distinct identity for reconciliation.
        State::None
    }

    fn children(&self) -> Vec<Tree> {
        // One declared tree per layer keeps `diff`/`children` lengths in sync,
        // the same invariant `Popover` relies on. `Tree::new` borrows the inner
        // widget (an `Element` impls `Borrow<dyn Widget>`), not the `Layer`.
        self.layers.iter().map(|l| Tree::new(&l.content)).collect()
    }

    fn diff(&self, tree: &mut Tree) {
        // Reconcile the full layer set, not just a `.zip` of the existing
        // children. When the layer count changes between frames (e.g. the chat
        // pane flipping from the empty-state `Stack` branch back to a plain
        // `column`, or any rebuild that swaps the element behind a slot), a bare
        // zip never extends `tree.children` — a later `Popover::layout` then
        // indexes an empty `tree.children[0]` and panics. `diff_children`
        // truncates and extends exactly like `Column` does, so every layer slot
        // always holds a valid child tree.
        let layers: Vec<&Element<'_, Message, Theme, Renderer>> =
            self.layers.iter().map(|l| &l.content).collect();
        tree.diff_children(&layers);
    }

    fn operate(
        &mut self,
        tree: &mut Tree,
        layout: Layout<'_>,
        renderer: &Renderer,
        operation: &mut dyn Operation,
    ) {
        // Forward operations (focus, text-editor actions, scrollable, etc.) to
        // every layer so nested widgets — notably the chat input bar's
        // `text_editor` — keep receiving their actions while the chat pane sits
        // inside a `Stack`. Mirrors `Column::operate`.
        operation.container(None, layout.bounds());
        operation.traverse(&mut |operation| {
            self.layers
                .iter_mut()
                .zip(&mut tree.children)
                .zip(layout.children())
                .for_each(|((layer, state), child_layout)| {
                    layer
                        .content
                        .as_widget_mut()
                        .operate(state, child_layout, renderer, operation);
                });
        });
    }

    fn update(
        &mut self,
        tree: &mut Tree,
        event: &Event,
        layout: Layout<'_>,
        cursor: mouse::Cursor,
        renderer: &Renderer,
        clipboard: &mut dyn Clipboard,
        shell: &mut Shell<'_, Message>,
        viewport: &Rectangle,
    ) {
        let layouts: Vec<Layout<'_>> = layout.children().collect();
        let count = self.layers.len();

        for idx in (0..count).rev() {
            self.layers[idx].content.as_widget_mut().update(
                &mut tree.children[idx],
                event,
                layouts[idx],
                cursor,
                renderer,
                clipboard,
                shell,
                viewport,
            );
        }
    }

    fn mouse_interaction(
        &self,
        tree: &Tree,
        layout: Layout<'_>,
        cursor: mouse::Cursor,
        viewport: &Rectangle,
        renderer: &Renderer,
    ) -> mouse::Interaction {
        // Front-most layer wins the interaction, matching event dispatch.
        let layouts: Vec<Layout<'_>> = layout.children().collect();
        let count = self.layers.len();

        (0..count)
            .rev()
            .map(|idx| {
                self.layers[idx].content.as_widget().mouse_interaction(
                    &tree.children[idx],
                    layouts[idx],
                    cursor,
                    viewport,
                    renderer,
                )
            })
            .fold(mouse::Interaction::default(), mouse::Interaction::max)
    }

    fn overlay<'b>(
        &'b mut self,
        tree: &'b mut Tree,
        layout: Layout<'b>,
        renderer: &Renderer,
        viewport: &Rectangle,
        translation: iced::Vector,
    ) -> Option<overlay::Element<'b, Message, Theme, Renderer>> {
        let collected: Vec<overlay::Element<'b, Message, Theme, Renderer>> = self
            .layers
            .iter_mut()
            .map(|l| &mut l.content)
            .zip(&mut tree.children)
            .zip(layout.children())
            .filter_map(|((child, state), child_layout)| {
                child
                    .as_widget_mut()
                    .overlay(state, child_layout, renderer, viewport, translation)
            })
            .collect();

        if collected.is_empty() {
            None
        } else {
            Some(overlay::Group::with_children(collected).overlay())
        }
    }
}

impl<'a, Message, Theme, Renderer> From<Stack<'a, Message, Theme, Renderer>>
    for Element<'a, Message, Theme, Renderer>
where
    Message: Clone + 'a,
    Renderer: iced::advanced::Renderer + 'a,
    Theme: 'a,
{
    fn from(stack: Stack<'a, Message, Theme, Renderer>) -> Self {
        Element::new(stack)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use iced::advanced::Widget;
    use iced::widget::text;
    use iced::Theme;

    type TestStack<'a> = Stack<'a, (), Theme, iced::Renderer>;

    fn layer() -> Element<'static, (), Theme, iced::Renderer> {
        text("x").into()
    }

    /// The widget tree must declare exactly one child tree per layer, otherwise
    /// iced's tree reconciliation panics on a length mismatch. This guards the
    /// same invariant `Popover` relies on. (Layout/draw behavior needs a live
    /// GPU renderer and is verified via the screenshot validation step instead.)
    #[test]
    fn children_count_matches_layer_count() {
        let one = TestStack::new(layer());
        assert_eq!(one.children().len(), 1);

        let two = TestStack::new(layer()).layer(layer());
        assert_eq!(two.children().len(), 2);

        let three = TestStack::new(layer()).layer(layer()).layer(layer());
        assert_eq!(three.children().len(), 3);
    }

    /// Regression guard for the chat-screen panic
    /// (`popover.rs: index out of bounds: the len is 0`).
    ///
    /// The chat pane swaps between an empty-state `Stack` and an active
    /// `column` across frames. The runtime reconciles the same widget-tree slot
    /// via `diff_children_custom`, which — on a tag mismatch (Stack ≠ Column) —
    /// rebuilds that slot with `Tree::new`, recursing through `children()`.
    ///
    /// The popover panic meant a layer's nested `Popover` ended up with an
    /// empty `tree.children`. This test reproduces the reconciliation that the
    /// runtime performs and asserts a nested widget that declares its own
    /// children (here a `Popover`) keeps them after a full rebuild.
    #[test]
    fn new_then_diff_keeps_nested_popover_children() {
        use crate::widgets::{Popover, Position};
        use iced::widget::{button, text};

        // A layer shaped like the chat pane's front layer: a column holding a
        // `Popover` (which always declares two child trees).
        let make_stack = || {
            let popover: Element<'static, (), Theme, iced::Renderer> =
                Popover::new(button(text("anchor")).on_press(()).into(), || None)
                    .position(Position::Bottom)
                    .on_close(())
                    .into();

            let back: Element<'static, (), Theme, iced::Renderer> = text("logo").into();
            let front: Element<'static, (), Theme, iced::Renderer> =
                iced::widget::column![popover].into();
            TestStack::new(back).layer(front)
        };

        // Fresh build — mirrors the runtime's first frame.
        let stack_a: Element<'static, (), Theme, iced::Renderer> = make_stack().into();
        let mut tree = Tree::new(&stack_a);
        assert_eq!(tree.children.len(), 2, "two layer slots");

        // The front layer is a column[popover]; the popover declares two
        // children. Walk: stack -> layer[1] (column) -> child[0] (popover).
        let front_layer = &tree.children[1];
        assert_eq!(front_layer.children.len(), 1, "column has one child");
        let popover_tree = &front_layer.children[0];
        assert_eq!(
            popover_tree.children.len(),
            2,
            "popover must declare both its anchor and popup slots"
        );

        // Now simulate the runtime diffing a freshly-built Stack into the same
        // slot (different element identity, identical structure). The popover's
        // own `diff` only reconciles slot [0], so the rebuild path through
        // `children()` is what keeps slot [1] alive. Assert it survives.
        let stack_b: Element<'static, (), Theme, iced::Renderer> = make_stack().into();
        tree.diff(&stack_b);

        let front_layer = &tree.children[1];
        let popover_tree = &front_layer.children[0];
        assert_eq!(
            popover_tree.children.len(),
            2,
            "popover child slots survive a full rebuild"
        );
    }
}

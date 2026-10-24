//! Shared [`Widget`] helpers for inline overlay anchors ([`super::Popover`],
//! [`super::BottomSheet`]). Both widgets keep a two-slot child tree: slot `[0]`
//! is the inline anchor; slot `[1]` holds lazy overlay content reconciled in
//! `overlay()`.
//!
//! Declaring the overlay as a persistent child (via [`anchor_children`]) keeps
//! text paragraph state alive across `operate()` and `draw()`. The iced runtime
//! calls `Widget::overlay` once during event processing (running `layout`, which
//! fills text paragraphs) and again during `draw` (reusing the cached layout). A
//! throwaway `Tree::new` each pass would discard paragraphs built during
//! `layout` and `draw` would render empty text.

use iced::advanced::widget::tree::Tree;
use iced::advanced::{layout, mouse, overlay, renderer, Clipboard, Layout, Shell};
use iced::event::Event;
use iced::{Element, Length, Rectangle, Size, Vector};

pub fn anchor_size<Message, Theme, Renderer>(
    content: &Element<'_, Message, Theme, Renderer>,
) -> Size<Length>
where
    Renderer: iced::advanced::Renderer,
{
    content.as_widget().size()
}

pub fn anchor_size_hint<Message, Theme, Renderer>(
    content: &Element<'_, Message, Theme, Renderer>,
) -> Size<Length>
where
    Renderer: iced::advanced::Renderer,
{
    content.as_widget().size_hint()
}

pub fn anchor_layout<Message, Theme, Renderer>(
    content: &mut Element<'_, Message, Theme, Renderer>,
    tree: &mut Tree,
    renderer: &Renderer,
    limits: &layout::Limits,
) -> layout::Node
where
    Renderer: iced::advanced::Renderer,
{
    content
        .as_widget_mut()
        .layout(&mut tree.children[0], renderer, limits)
}

#[allow(clippy::too_many_arguments)]
pub fn anchor_draw<Message, Theme, Renderer>(
    content: &Element<'_, Message, Theme, Renderer>,
    tree: &Tree,
    renderer: &mut Renderer,
    theme: &Theme,
    inherited_style: &renderer::Style,
    layout: Layout<'_>,
    cursor: mouse::Cursor,
    viewport: &Rectangle,
) where
    Renderer: iced::advanced::Renderer,
{
    content.as_widget().draw(
        &tree.children[0],
        renderer,
        theme,
        inherited_style,
        layout,
        cursor,
        viewport,
    );
}

/// Two declared children keep `diff`/`children` lengths in sync for lazy overlays.
pub fn anchor_children<Message, Theme, Renderer>(
    content: &Element<'_, Message, Theme, Renderer>,
) -> Vec<Tree>
where
    Message: Clone,
    Renderer: iced::advanced::Renderer,
{
    vec![Tree::new(content), Tree::empty()]
}

/// Diff only the anchor slot; the overlay slot is reconciled lazily in `overlay()`.
pub fn diff_anchor_only<Message, Theme, Renderer>(
    tree: &mut Tree,
    content: &Element<'_, Message, Theme, Renderer>,
) where
    Renderer: iced::advanced::Renderer,
{
    if let Some(content_tree) = tree.children.get_mut(0) {
        content_tree.diff(content.as_widget());
    }
}

#[allow(clippy::too_many_arguments)]
pub fn anchor_update<Message, Theme, Renderer>(
    content: &mut Element<'_, Message, Theme, Renderer>,
    tree: &mut Tree,
    event: &Event,
    layout: Layout<'_>,
    cursor: mouse::Cursor,
    renderer: &Renderer,
    clipboard: &mut dyn Clipboard,
    shell: &mut Shell<'_, Message>,
    viewport: &Rectangle,
) where
    Message: Clone,
    Renderer: iced::advanced::Renderer,
{
    content.as_widget_mut().update(
        &mut tree.children[0],
        event,
        layout,
        cursor,
        renderer,
        clipboard,
        shell,
        viewport,
    );
}

pub fn anchor_mouse_interaction<Message, Theme, Renderer>(
    content: &Element<'_, Message, Theme, Renderer>,
    tree: &Tree,
    layout: Layout<'_>,
    cursor: mouse::Cursor,
    viewport: &Rectangle,
    renderer: &Renderer,
) -> mouse::Interaction
where
    Renderer: iced::advanced::Renderer,
{
    content
        .as_widget()
        .mouse_interaction(&tree.children[0], layout, cursor, viewport, renderer)
}

/// Overlay-specific half of an inline anchor widget ([`super::Popover`],
/// [`super::BottomSheet`]). Implemented by each widget type; the
/// [`impl_overlay_anchor_widget!`] macro wires it into [`Widget::overlay`].
pub trait OverlayAnchorOverlay<Message, Theme, Renderer> {
    fn overlay_anchor<'b>(
        &'b mut self,
        tree: &'b mut Tree,
        layout: Layout<'b>,
        renderer: &Renderer,
        viewport: &Rectangle,
        translation: Vector,
    ) -> Option<overlay::Element<'b, Message, Theme, Renderer>>;
}

/// Generates the full [`iced::advanced::widget::Widget`] impl for inline anchor
/// widgets. Each type must also implement [`OverlayAnchorOverlay`].
macro_rules! impl_overlay_anchor_widget {
    ($ty:ty) => {
        impl<'a, Message, Theme, Renderer> iced::advanced::widget::Widget<Message, Theme, Renderer>
            for $ty
        where
            Message: Clone + 'a,
            Renderer: iced::advanced::Renderer + 'a,
            Theme: 'a,
            $ty: $crate::widgets::overlay_anchor::OverlayAnchorOverlay<Message, Theme, Renderer>,
        {
            fn size(&self) -> iced::Size<iced::Length> {
                $crate::widgets::overlay_anchor::anchor_size(&self.content)
            }

            fn size_hint(&self) -> iced::Size<iced::Length> {
                $crate::widgets::overlay_anchor::anchor_size_hint(&self.content)
            }

            fn layout(
                &mut self,
                tree: &mut Tree,
                renderer: &Renderer,
                limits: &layout::Limits,
            ) -> layout::Node {
                $crate::widgets::overlay_anchor::anchor_layout(
                    &mut self.content,
                    tree,
                    renderer,
                    limits,
                )
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
                $crate::widgets::overlay_anchor::anchor_draw(
                    &self.content,
                    tree,
                    renderer,
                    theme,
                    inherited_style,
                    layout,
                    cursor,
                    viewport,
                );
            }

            fn children(&self) -> Vec<Tree> {
                $crate::widgets::overlay_anchor::anchor_children(&self.content)
            }

            fn diff(&self, tree: &mut Tree) {
                $crate::widgets::overlay_anchor::diff_anchor_only(tree, &self.content);
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
                $crate::widgets::overlay_anchor::anchor_update(
                    &mut self.content,
                    tree,
                    event,
                    layout,
                    cursor,
                    renderer,
                    clipboard,
                    shell,
                    viewport,
                );
            }

            fn mouse_interaction(
                &self,
                tree: &Tree,
                layout: Layout<'_>,
                cursor: mouse::Cursor,
                viewport: &Rectangle,
                renderer: &Renderer,
            ) -> mouse::Interaction {
                $crate::widgets::overlay_anchor::anchor_mouse_interaction(
                    &self.content,
                    tree,
                    layout,
                    cursor,
                    viewport,
                    renderer,
                )
            }

            fn overlay<'b>(
                &'b mut self,
                tree: &'b mut Tree,
                layout: Layout<'b>,
                renderer: &Renderer,
                viewport: &Rectangle,
                translation: Vector,
            ) -> Option<overlay::Element<'b, Message, Theme, Renderer>> {
                $crate::widgets::overlay_anchor::OverlayAnchorOverlay::overlay_anchor(
                    self,
                    tree,
                    layout,
                    renderer,
                    viewport,
                    translation,
                )
            }
        }
    };
}

/// Generates `From<$ty> for Element` for overlay anchor widgets.
macro_rules! impl_overlay_anchor_from {
    ($ty:ty) => {
        impl<'a, Message, Theme, Renderer> From<$ty> for iced::Element<'a, Message, Theme, Renderer>
        where
            Message: Clone + 'a,
            Renderer: iced::advanced::Renderer + 'a,
            Theme: 'a,
        {
            fn from(widget: $ty) -> Self {
                iced::Element::new(widget)
            }
        }
    };
}

pub(crate) use impl_overlay_anchor_from;
pub(crate) use impl_overlay_anchor_widget;

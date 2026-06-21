//! A floating popover widget.
//!
//! Renders an anchor (`content`) inline and floats a `popup` element above it
//! as a true overlay when open — it is not clipped by the anchor's parent and
//! does not push surrounding layout. Built on Iced 0.12's overlay system,
//! mirroring how the built-in `tooltip` widget works.
//!
//! Outside-click dismissal: when the overlay is open, any pointer press
//! outside the popup bounds dispatches the `on_close` message. Menu-item
//! clicks should also close the menu (the caller emits `on_close` from the
//! relevant item handlers).

use iced::advanced::widget::tree::Tree;
use iced::advanced::{layout, mouse, overlay, renderer, widget, Clipboard, Layout, Shell};
use iced::event::Event;
use iced::{Element, Length, Padding, Point, Rectangle, Size, Vector};

/// Anchor placement of the popup relative to the content's bounds.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Position {
    /// Popup floats above the anchor.
    Top,
    /// Popup floats below the anchor.
    Bottom,
}

/// A popover: an inline anchor whose popup floats over the UI when open.
pub struct Popover<'a, Message, Theme = iced::Theme, Renderer = iced::Renderer>
where
    Renderer: iced::advanced::Renderer,
{
    content: Element<'a, Message, Theme, Renderer>,
    popup: Box<dyn Fn() -> Option<Element<'a, Message, Theme, Renderer>> + 'a>,
    position: Position,
    padding: f32,
    on_close: Option<Message>,
}

impl<'a, Message, Theme, Renderer> Popover<'a, Message, Theme, Renderer>
where
    Message: Clone + 'a,
    Renderer: iced::advanced::Renderer + 'a,
    Theme: 'a,
{
    /// Create a popover anchored to `content`. `popup` is called each overlay
    /// pass; when it returns `Some`, the popup floats. `on_close` is emitted on
    /// an outside-click so the caller can flip its open flag.
    pub fn new<F>(content: Element<'a, Message, Theme, Renderer>, popup: F) -> Self
    where
        F: Fn() -> Option<Element<'a, Message, Theme, Renderer>> + 'a,
    {
        Popover {
            content,
            popup: Box::new(popup),
            position: Position::Bottom,
            padding: 4.0,
            on_close: None,
        }
    }

    /// Where the popup floats relative to the anchor.
    pub fn position(mut self, position: Position) -> Self {
        self.position = position;
        self
    }

    /// Gap between the anchor and the popup.
    pub fn padding(mut self, padding: impl Into<Padding>) -> Self {
        let pad: Padding = padding.into();
        self.padding = (pad.left + pad.top) / 2.0;
        self
    }

    /// Message emitted when the popup is dismissed by an outside click.
    pub fn on_close(mut self, message: Message) -> Self {
        self.on_close = Some(message);
        self
    }
}

impl<'a, Message, Theme, Renderer> widget::Widget<Message, Theme, Renderer>
    for Popover<'a, Message, Theme, Renderer>
where
    Message: Clone + 'a,
    Renderer: iced::advanced::Renderer + 'a,
    Theme: 'a,
{
    fn size(&self) -> Size<Length> {
        self.content.as_widget().size()
    }

    fn size_hint(&self) -> Size<Length> {
        self.content.as_widget().size_hint()
    }

    fn layout(
        &mut self,
        tree: &mut Tree,
        renderer: &Renderer,
        limits: &layout::Limits,
    ) -> layout::Node {
        self.content
            .as_widget_mut()
            .layout(&mut tree.children[0], renderer, limits)
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
        self.content.as_widget().draw(
            &tree.children[0],
            renderer,
            theme,
            inherited_style,
            layout,
            cursor,
            viewport,
        );
    }

    fn children(&self) -> Vec<Tree> {
        // Two declared children keep `diff`/`children` lengths in sync:
        //   [0] = the anchor (content),
        //   [1] = the popup (rebuilt lazily; empty when closed).
        // Persisting the popup as a declared child is what lets its widget
        // state (e.g. text `Paragraph`s) survive across `operate()` and
        // `draw()`. The iced runtime calls `Widget::overlay` once during event
        // processing (running `layout`, which fills text paragraphs) and again
        // during `draw` (reusing the cached layout). If the popup owned a
        // throwaway `Tree::new` each time, the paragraphs built during `layout`
        // would be discarded and `draw` would render empty text.
        vec![Tree::new(&self.content), Tree::empty()]
    }

    fn diff(&self, tree: &mut Tree) {
        // Diff only the anchor (slot [0]) and leave the popup slot [1]
        // untouched. `diff_children` would truncate `tree.children` down to the
        // slice length, which would drop the persistent popup tree and make the
        // later `tree.children[1]` access in `overlay` panic. The popup slot is
        // reconciled lazily against the freshly-built element inside `overlay`.
        if let Some(content_tree) = tree.children.get_mut(0) {
            content_tree.diff(self.content.as_widget());
        }
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
        self.content.as_widget_mut().update(
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

    fn mouse_interaction(
        &self,
        tree: &Tree,
        layout: Layout<'_>,
        cursor: mouse::Cursor,
        viewport: &Rectangle,
        renderer: &Renderer,
    ) -> mouse::Interaction {
        self.content.as_widget().mouse_interaction(
            &tree.children[0],
            layout,
            cursor,
            viewport,
            renderer,
        )
    }

    fn overlay<'b>(
        &'b mut self,
        tree: &'b mut Tree,
        layout: Layout<'_>,
        _renderer: &Renderer,
        _viewport: &Rectangle,
        translation: Vector,
    ) -> Option<overlay::Element<'b, Message, Theme, Renderer>> {
        let popup = (self.popup)()?;
        // Reconcile the freshly-built popup into the persistent child tree so
        // its text/paragraph state is carried across frames (see `children`).
        tree.children[1].diff(&popup);

        let position = layout.position() + translation;
        let content_bounds = layout.bounds();

        Some(overlay::Element::new(Box::new(Overlay {
            anchor: position,
            content_bounds,
            popup,
            state: &mut tree.children[1],
            positioning: self.position,
            padding: self.padding,
            on_close: self.on_close.clone(),
        })))
    }
}

impl<'a, Message, Theme, Renderer> From<Popover<'a, Message, Theme, Renderer>>
    for Element<'a, Message, Theme, Renderer>
where
    Message: Clone + 'a,
    Renderer: iced::advanced::Renderer + 'a,
    Theme: 'a,
{
    fn from(popover: Popover<'a, Message, Theme, Renderer>) -> Self {
        Element::new(popover)
    }
}

struct Overlay<'a, Message, Theme, Renderer>
where
    Renderer: iced::advanced::Renderer,
{
    anchor: Point,
    content_bounds: Rectangle,
    popup: Element<'a, Message, Theme, Renderer>,
    state: &'a mut Tree,
    positioning: Position,
    padding: f32,
    on_close: Option<Message>,
}

impl<'a, Message, Theme, Renderer> overlay::Overlay<Message, Theme, Renderer>
    for Overlay<'a, Message, Theme, Renderer>
where
    Message: Clone,
    Renderer: iced::advanced::Renderer,
{
    fn layout(&mut self, renderer: &Renderer, bounds: Size) -> layout::Node {
        let viewport = Rectangle::with_size(bounds);

        let popup_layout = self.popup.as_widget_mut().layout(
            self.state,
            renderer,
            &layout::Limits::new(Size::ZERO, viewport.size()),
        );

        let popup_bounds = popup_layout.bounds();
        let x = self.anchor.x + (self.content_bounds.width - popup_bounds.width) / 2.0;

        let y = match self.positioning {
            Position::Top => self.anchor.y - popup_bounds.height - self.padding,
            Position::Bottom => self.anchor.y + self.content_bounds.height + self.padding,
        };

        let mut positioned = Rectangle {
            x,
            y,
            width: popup_bounds.width,
            height: popup_bounds.height,
        };

        // Snap within the viewport so the popup never runs off-screen.
        if positioned.x < viewport.x {
            positioned.x = viewport.x;
        } else if viewport.x + viewport.width < positioned.x + positioned.width {
            positioned.x = viewport.x + viewport.width - positioned.width;
        }
        if positioned.y < viewport.y {
            positioned.y = viewport.y;
        } else if viewport.y + viewport.height < positioned.y + positioned.height {
            positioned.y = viewport.y + viewport.height - positioned.height;
        }

        layout::Node::with_children(positioned.size(), vec![popup_layout])
            .translate(Vector::new(positioned.x, positioned.y))
    }

    fn draw(
        &self,
        renderer: &mut Renderer,
        theme: &Theme,
        inherited_style: &renderer::Style,
        layout: Layout<'_>,
        cursor: mouse::Cursor,
    ) {
        self.popup.as_widget().draw(
            self.state,
            renderer,
            theme,
            inherited_style,
            layout.children().next().unwrap(),
            cursor,
            &Rectangle::with_size(Size::INFINITE),
        );
    }

    fn update(
        &mut self,
        event: &Event,
        layout: Layout<'_>,
        cursor: mouse::Cursor,
        renderer: &Renderer,
        clipboard: &mut dyn Clipboard,
        shell: &mut Shell<'_, Message>,
    ) {
        let popup_layout = layout.children().next().unwrap();
        let popup_bounds = popup_layout.bounds();
        let inside = cursor.is_over(popup_bounds);

        if let Event::Mouse(mouse::Event::ButtonPressed(mouse::Button::Left)) = event {
            if !inside {
                if let Some(on_close) = self.on_close.clone() {
                    shell.publish(on_close);
                }
                return;
            }
        }

        self.popup.as_widget_mut().update(
            self.state,
            event,
            popup_layout,
            cursor,
            renderer,
            clipboard,
            shell,
            &popup_bounds,
        );
    }

    fn mouse_interaction(
        &self,
        layout: Layout<'_>,
        cursor: mouse::Cursor,
        renderer: &Renderer,
    ) -> mouse::Interaction {
        self.popup.as_widget().mouse_interaction(
            self.state,
            layout.children().next().unwrap(),
            cursor,
            &layout.bounds(),
            renderer,
        )
    }
}

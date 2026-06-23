//! A floating popover widget.
//!
//! Renders an anchor (`content`) inline and floats a `popup` element above it
//! as a true overlay when open — it is not clipped by the anchor's parent and
//! does not push surrounding layout. Built on Iced 0.14's overlay system,
//! mirroring how the built-in `tooltip` widget works.
//!
//! Outside-click dismissal: when the overlay is open, any pointer press
//! outside the popup bounds dispatches the `on_close` message — except a
//! press that lands on the anchor itself, which is left to the anchor's own
//! `on_press` (so a toggle trigger can flip closed without `on_close`
//! double-toggling it back open). Menu-item clicks should also close the
//! menu (the caller emits `on_close` from the relevant item handlers).

use iced::advanced::widget::tree::Tree;
use iced::advanced::{Clipboard, Layout, Shell, layout, mouse, overlay, renderer};

use super::overlay_anchor;
use iced::event::Event;
use iced::{Element, Padding, Point, Rectangle, Size, Vector};

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

overlay_anchor::impl_overlay_anchor_widget!(Popover<'a, Message, Theme, Renderer>);

impl<'a, Message, Theme, Renderer> overlay_anchor::OverlayAnchorOverlay<Message, Theme, Renderer>
    for Popover<'a, Message, Theme, Renderer>
where
    Message: Clone + 'a,
    Renderer: iced::advanced::Renderer + 'a,
    Theme: 'a,
{
    fn overlay_anchor<'b>(
        &'b mut self,
        tree: &'b mut Tree,
        layout: Layout<'b>,
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

overlay_anchor::impl_overlay_anchor_from!(Popover<'a, Message, Theme, Renderer>);

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

        if let Some(action) = click_action(event, cursor, popup_bounds, self.content_bounds) {
            match action {
                ClickAction::ForwardToPopup => {}
                ClickAction::ForwardToAnchor => return,
                ClickAction::Close => {
                    if let Some(on_close) = self.on_close.clone() {
                        shell.publish(on_close);
                    }
                    return;
                }
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

/// How the overlay should route a pointer event. Only meaningful for
/// left-button presses; all other events forward to the popup (`None`).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum ClickAction {
    /// Click landed inside the popup — let the popup handle it.
    ForwardToPopup,
    /// Click landed on the anchor (content) itself — defer to the anchor's
    /// own `on_press` so a toggle trigger can flip closed without `on_close`
    /// double-toggling it back open.
    ForwardToAnchor,
    /// Click landed outside both popup and anchor — dismiss the popover.
    Close,
}

/// Pure decision function for an overlay pointer event. Returns `None` for
/// non-left-press events (the popup handles them normally). Extracted from
/// `Overlay::update` so the anchor-vs-outside routing logic is testable
/// without spinning up a full widget tree.
///
/// `popup_bounds` is the floating menu's screen-space rectangle;
/// `anchor_bounds` is the inline trigger button's rectangle. The cursor is
/// the pointer position, if any (`None` means "not over the window").
fn click_action(
    event: &Event,
    cursor: mouse::Cursor,
    popup_bounds: Rectangle,
    anchor_bounds: Rectangle,
) -> Option<ClickAction> {
    let Event::Mouse(mouse::Event::ButtonPressed(mouse::Button::Left)) = event else {
        return None;
    };

    let over_popup = cursor.is_over(popup_bounds);
    if over_popup {
        return Some(ClickAction::ForwardToPopup);
    }

    // Not over the popup. If the press is on the anchor itself, let the
    // anchor's `on_press` handle it (e.g. toggle closed) — emitting `on_close`
    // here would double-toggle and immediately reopen the popover.
    if cursor.is_over(anchor_bounds) {
        return Some(ClickAction::ForwardToAnchor);
    }

    Some(ClickAction::Close)
}

#[cfg(test)]
mod tests {
    use super::*;
    use iced::Point;

    /// Build a cursor positioned at an absolute point (mirrors the
    /// `mouse::Cursor` API used by the overlay).
    fn cursor_at(point: Point) -> mouse::Cursor {
        mouse::Cursor::Available(point)
    }

    /// A left-button press event.
    fn left_press() -> Event {
        Event::Mouse(mouse::Event::ButtonPressed(mouse::Button::Left))
    }

    const POPUP: Rectangle = Rectangle {
        x: 100.0,
        y: 0.0,
        width: 80.0,
        height: 60.0,
    };
    const ANCHOR: Rectangle = Rectangle {
        x: 100.0,
        y: 70.0,
        width: 40.0,
        height: 40.0,
    };

    #[test]
    fn click_inside_popup_forwards_to_popup() {
        // Center of the popup.
        let cursor = cursor_at(Point::new(140.0, 30.0));
        assert_eq!(
            click_action(&left_press(), cursor, POPUP, ANCHOR),
            Some(ClickAction::ForwardToPopup)
        );
    }

    #[test]
    fn click_on_anchor_forwards_to_anchor_not_close() {
        // Center of the anchor button. This is the regression case: without
        // the anchor check, re-clicking an open popover's trigger would
        // double-toggle (close via anchor, reopen via `on_close`).
        let cursor = cursor_at(Point::new(120.0, 90.0));
        assert_eq!(
            click_action(&left_press(), cursor, POPUP, ANCHOR),
            Some(ClickAction::ForwardToAnchor)
        );
    }

    #[test]
    fn click_outside_popup_and_anchor_closes() {
        // Far away from both rectangles.
        let cursor = cursor_at(Point::new(500.0, 500.0));
        assert_eq!(
            click_action(&left_press(), cursor, POPUP, ANCHOR),
            Some(ClickAction::Close)
        );
    }

    #[test]
    fn non_press_events_are_not_intercepted() {
        let cursor = cursor_at(Point::new(500.0, 500.0));
        let event = Event::Mouse(mouse::Event::ButtonReleased(mouse::Button::Left));
        assert_eq!(click_action(&event, cursor, POPUP, ANCHOR), None);
    }

    #[test]
    fn unavailable_cursor_on_press_closes() {
        // `Cursor::Unavailable` is not over any rectangle, so a press with no
        // pointer position falls through to the close path. (This mirrors the
        // prior behavior: `is_over` returns false for an unavailable cursor.)
        assert_eq!(
            click_action(&left_press(), mouse::Cursor::Unavailable, POPUP, ANCHOR),
            Some(ClickAction::Close)
        );
    }
}

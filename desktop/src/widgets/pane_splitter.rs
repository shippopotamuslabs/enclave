//! Draggable vertical pane splitter.
//!
//! Captures pointer drag after press so resizing continues even when the
//! cursor leaves the narrow hit target — `mouse_area` cannot do that because
//! it ignores events outside its bounds.

use iced::advanced::widget::tree::{State, Tag, Tree};
use iced::advanced::{
    layout, mouse, overlay, renderer, widget, Clipboard, Layout, Renderer, Shell,
};
use iced::event::{self, Event};
use iced::mouse::Button;
use iced::{Color, Element, Length, Rectangle, Size, Theme};

/// Width of the draggable hit target in logical pixels.
pub const HIT_WIDTH: f32 = 8.0;

/// Local drag state for [`PaneSplitter`].
#[derive(Default)]
struct DragState {
    dragging: bool,
    last_x: f32,
}

/// A vertical splitter handle between two horizontal panes.
pub struct PaneSplitter<'a, Message> {
    on_resize: Box<dyn Fn(f32) -> Message + 'a>,
    on_resize_end: Option<Message>,
    line_color: Color,
    width: Length,
    height: Length,
}

impl<'a, Message> PaneSplitter<'a, Message>
where
    Message: Clone + 'a,
{
    /// Create a splitter with the given outline color and resize callbacks.
    pub fn new(line_color: Color, on_resize: impl Fn(f32) -> Message + 'a) -> Self {
        PaneSplitter {
            on_resize: Box::new(on_resize),
            on_resize_end: None,
            line_color,
            width: Length::Fixed(HIT_WIDTH),
            height: Length::Fill,
        }
    }

    /// Message emitted when the user releases the splitter after dragging.
    #[must_use]
    pub fn on_resize_end(mut self, message: Message) -> Self {
        self.on_resize_end = Some(message);
        self
    }
}

impl<'a, Message> widget::Widget<Message, Theme, iced::Renderer> for PaneSplitter<'a, Message>
where
    Message: Clone + 'a,
{
    fn size(&self) -> Size<Length> {
        Size::new(self.width, self.height)
    }

    fn layout(
        &self,
        _tree: &mut Tree,
        _renderer: &iced::Renderer,
        limits: &layout::Limits,
    ) -> layout::Node {
        layout::atomic(limits, self.width, self.height)
    }

    fn draw(
        &self,
        _tree: &Tree,
        renderer: &mut iced::Renderer,
        _theme: &Theme,
        _style: &renderer::Style,
        layout: Layout<'_>,
        _cursor: mouse::Cursor,
        _viewport: &Rectangle,
    ) {
        let bounds = layout.bounds();
        let line_width = 2.0;
        // Align with the left edge of the hit target so the rule sits flush
        // against the adjacent pane rather than floating in the gutter center.
        let line_x = bounds.x;
        renderer.fill_quad(
            renderer::Quad {
                bounds: Rectangle {
                    x: line_x,
                    y: bounds.y,
                    width: line_width,
                    height: bounds.height,
                },
                border: iced::Border {
                    color: Color::TRANSPARENT,
                    width: 0.0,
                    radius: 0.0.into(),
                },
                ..renderer::Quad::default()
            },
            self.line_color,
        );
    }

    fn tag(&self) -> Tag {
        Tag::of::<DragState>()
    }

    fn state(&self) -> State {
        State::new(DragState::default())
    }

    fn on_event(
        &mut self,
        tree: &mut Tree,
        event: Event,
        layout: Layout<'_>,
        cursor: mouse::Cursor,
        _renderer: &iced::Renderer,
        _clipboard: &mut dyn Clipboard,
        shell: &mut Shell<'_, Message>,
        _viewport: &Rectangle,
    ) -> event::Status {
        let drag: &mut DragState = tree.state.downcast_mut();

        match event {
            Event::Mouse(mouse::Event::ButtonPressed(Button::Left))
                if cursor.is_over(layout.bounds()) =>
            {
                if let Some(position) = cursor.position() {
                    drag.dragging = true;
                    drag.last_x = position.x;
                    return event::Status::Captured;
                }
            }
            Event::Mouse(mouse::Event::CursorMoved { .. }) if drag.dragging => {
                if let Some(position) = cursor.position() {
                    let delta = position.x - drag.last_x;
                    drag.last_x = position.x;
                    if delta.abs() > f32::EPSILON {
                        shell.publish((self.on_resize)(delta));
                    }
                    return event::Status::Captured;
                }
            }
            Event::Mouse(mouse::Event::ButtonReleased(Button::Left)) if drag.dragging => {
                drag.dragging = false;
                if let Some(message) = self.on_resize_end.clone() {
                    shell.publish(message);
                }
                return event::Status::Captured;
            }
            _ => {}
        }

        event::Status::Ignored
    }

    fn mouse_interaction(
        &self,
        _tree: &Tree,
        layout: Layout<'_>,
        cursor: mouse::Cursor,
        _viewport: &Rectangle,
        _renderer: &iced::Renderer,
    ) -> mouse::Interaction {
        if cursor.is_over(layout.bounds()) {
            mouse::Interaction::ResizingHorizontally
        } else {
            mouse::Interaction::default()
        }
    }

    fn overlay<'b>(
        &'b mut self,
        _tree: &'b mut Tree,
        _layout: Layout<'_>,
        _renderer: &iced::Renderer,
        _translation: iced::Vector,
    ) -> Option<overlay::Element<'b, Message, Theme, iced::Renderer>> {
        None
    }
}

impl<'a, Message> From<PaneSplitter<'a, Message>> for Element<'a, Message, Theme, iced::Renderer>
where
    Message: Clone + 'a,
{
    fn from(splitter: PaneSplitter<'a, Message>) -> Element<'a, Message, Theme, iced::Renderer> {
        Element::new(splitter)
    }
}

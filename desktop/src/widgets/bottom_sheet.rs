//! A modal bottom-sheet overlay.
//!
//! Renders `content` inline (typically a zero-size placeholder — the content is
//! not shown until the sheet opens) and floats a `sheet` element as a true
//! full-viewport overlay when open: a dim scrim fills the viewport and captures
//! outside clicks, and the sheet is pinned to the bottom-center with a bounded
//! width. Built on Iced 0.14's overlay system, mirroring how [`super::popover`]
//! works and how the Android `NeoModalBottomSheet` reads (modal scrim + bottom
//! sheet surface).
//!
//! Outside-click dismissal: when the sheet is open, any pointer press outside
//! the sheet bounds (i.e. on the scrim) dispatches the `on_close` message.

use iced::advanced::widget::tree::Tree;
use iced::advanced::{Clipboard, Layout, Shell, layout, mouse, overlay, renderer};

use super::overlay_anchor;
use iced::event::Event;
use iced::{Element, Rectangle, Size, Vector};

/// Max sheet width in logical pixels. Matches the input-bar centered gutter
/// (`FillPortion` 1/6/1) so the sheet lines up with the chat surface.
const MAX_SHEET_WIDTH: f32 = 720.0;
/// Vertical padding from the viewport bottom so the sheet clears the window edge
/// and its drop shadow renders fully.
const BOTTOM_PADDING: f32 = 24.0;

/// A modal bottom sheet: an inline anchor (usually empty) whose sheet floats
/// over the whole viewport when open.
pub struct BottomSheet<'a, Message, Theme = iced::Theme, Renderer = iced::Renderer>
where
    Renderer: iced::advanced::Renderer,
{
    content: Element<'a, Message, Theme, Renderer>,
    sheet: Box<dyn Fn() -> Option<Element<'a, Message, Theme, Renderer>> + 'a>,
    on_close: Option<Message>,
}

impl<'a, Message, Theme, Renderer> BottomSheet<'a, Message, Theme, Renderer>
where
    Message: Clone + 'a,
    Renderer: iced::advanced::Renderer + 'a,
    Theme: 'a,
{
    /// Create a sheet anchored to `content` (rendered inline while the sheet is
    /// closed; pass a zero-size placeholder when only the overlay is wanted).
    /// `sheet` is called each overlay pass; when it returns `Some`, the sheet
    /// floats. `on_close` is emitted on an outside (scrim) click so the caller
    /// can flip its open flag.
    pub fn new<F>(content: Element<'a, Message, Theme, Renderer>, sheet: F) -> Self
    where
        F: Fn() -> Option<Element<'a, Message, Theme, Renderer>> + 'a,
    {
        BottomSheet {
            content,
            sheet: Box::new(sheet),
            on_close: None,
        }
    }

    /// Message emitted when the sheet is dismissed by an outside (scrim) click.
    pub fn on_close(mut self, message: Message) -> Self {
        self.on_close = Some(message);
        self
    }
}

overlay_anchor::impl_overlay_anchor_widget!(BottomSheet<'a, Message, Theme, Renderer>);

impl<'a, Message, Theme, Renderer> overlay_anchor::OverlayAnchorOverlay<Message, Theme, Renderer>
    for BottomSheet<'a, Message, Theme, Renderer>
where
    Message: Clone + 'a,
    Renderer: iced::advanced::Renderer + 'a,
    Theme: 'a,
{
    fn overlay_anchor<'b>(
        &'b mut self,
        tree: &'b mut Tree,
        layout: Layout<'b>,
        renderer: &Renderer,
        viewport: &Rectangle,
        translation: Vector,
    ) -> Option<overlay::Element<'b, Message, Theme, Renderer>> {
        let mut collected: Vec<overlay::Element<'_, Message, Theme, Renderer>> = Vec::new();

        let (content_tree, sheet_tree) = tree.children.split_at_mut(1);

        // Always forward overlays from inline content (e.g. input-bar Popovers).
        // Returning `None` when the sheet is closed would swallow every nested
        // overlay beneath this anchor.
        if let Some(content_overlay) = self.content.as_widget_mut().overlay(
            &mut content_tree[0],
            layout,
            renderer,
            viewport,
            translation,
        ) {
            collected.push(content_overlay);
        }

        if let Some(sheet) = (self.sheet)() {
            // Reconcile the freshly-built sheet into the persistent child tree so
            // its text/paragraph state is carried across frames (see `children`).
            sheet_tree[0].diff(&sheet);

            collected.push(overlay::Element::new(Box::new(SheetOverlay {
                sheet,
                state: &mut sheet_tree[0],
                on_close: self.on_close.clone(),
            })));
        }

        match collected.len() {
            0 => None,
            1 => Some(collected.remove(0)),
            _ => Some(overlay::Group::with_children(collected).overlay()),
        }
    }
}

overlay_anchor::impl_overlay_anchor_from!(BottomSheet<'a, Message, Theme, Renderer>);

struct SheetOverlay<'a, Message, Theme, Renderer>
where
    Renderer: iced::advanced::Renderer,
{
    sheet: Element<'a, Message, Theme, Renderer>,
    state: &'a mut Tree,
    on_close: Option<Message>,
}

impl<'a, Message, Theme, Renderer> overlay::Overlay<Message, Theme, Renderer>
    for SheetOverlay<'a, Message, Theme, Renderer>
where
    Message: Clone,
    Renderer: iced::advanced::Renderer,
{
    fn layout(&mut self, renderer: &Renderer, bounds: Size) -> layout::Node {
        // Lay the sheet out against the full viewport width but an unbounded
        // height, then cap its width at `MAX_SHEET_WIDTH` (centered) so the
        // sheet reads as a bottom-anchored card on wide windows.
        let max_width = bounds.width.min(MAX_SHEET_WIDTH);
        let sheet_layout = self.sheet.as_widget_mut().layout(
            self.state,
            renderer,
            &layout::Limits::new(Size::ZERO, Size::new(max_width, bounds.height)),
        );

        let sheet_bounds = sheet_layout.bounds();
        // Bottom-center: clamp width to the capped size, push down to the
        // bottom (minus the padding) so the drop shadow clears the window edge.
        let x = (bounds.width - sheet_bounds.width) / 2.0;
        let y = bounds.height - sheet_bounds.height - BOTTOM_PADDING;

        let positioned = Rectangle {
            x,
            y,
            width: sheet_bounds.width,
            height: sheet_bounds.height,
        };

        layout::Node::with_children(positioned.size(), vec![sheet_layout])
            .translate(Vector::new(positioned.x, positioned.y))
    }

    fn draw(
        &self,
        renderer: &mut Renderer,
        _theme: &Theme,
        _inherited_style: &renderer::Style,
        layout: Layout<'_>,
        cursor: mouse::Cursor,
    ) {
        // Dim scrim first (full viewport), then the sheet on top.
        let overlay_bounds = layout.bounds();
        renderer.fill_quad(
            renderer::Quad {
                bounds: overlay_bounds,
                border: iced::Border {
                    color: iced::Color::TRANSPARENT,
                    width: 0.0,
                    radius: 0.0.into(),
                },
                ..renderer::Quad::default()
            },
            crate::theme::scrim_color(),
        );

        let sheet_layout = layout.children().next().unwrap();
        self.sheet.as_widget().draw(
            self.state,
            renderer,
            _theme,
            _inherited_style,
            sheet_layout,
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
        let sheet_layout = layout.children().next().unwrap();
        let sheet_bounds = sheet_layout.bounds();
        let inside_sheet = cursor.is_over(sheet_bounds);

        if let Event::Mouse(mouse::Event::ButtonPressed(mouse::Button::Left)) = event {
            if !inside_sheet {
                if let Some(on_close) = self.on_close.clone() {
                    shell.publish(on_close);
                }
                return;
            }
        }

        self.sheet.as_widget_mut().update(
            self.state,
            event,
            sheet_layout,
            cursor,
            renderer,
            clipboard,
            shell,
            &sheet_bounds,
        );
    }

    fn mouse_interaction(
        &self,
        layout: Layout<'_>,
        cursor: mouse::Cursor,
        renderer: &Renderer,
    ) -> mouse::Interaction {
        let sheet_layout = layout.children().next().unwrap();
        self.sheet.as_widget().mouse_interaction(
            self.state,
            sheet_layout,
            cursor,
            &layout.bounds(),
            renderer,
        )
    }
}

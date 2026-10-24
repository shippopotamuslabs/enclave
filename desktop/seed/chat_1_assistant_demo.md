# Iced on Linux — Markdown Demo

This reply exercises **bold**, *italic*, ***bold italic***, ~~strikethrough~~, and `inline code` in one breath.

[Iced](https://iced.rs/) is a cross-platform GUI library written in Rust. See also [the docs hub][iced-ref].

---

> **Elm-inspired MVU** keeps state predictable: events flow up, the view flows down, and the runtime batches redraws efficiently.

## Rendering backends

| Backend | Default on Linux | Notes |
| --- | --- | --- |
| **wgpu** | yes | WebGPU-style, hardware-accelerated |
| OpenGL | fallback | Broad driver support |
| Software | rarely | Last-resort raster path |

### Feature checklist

- [x] Hardware-accelerated rendering
- [x] Pure Rust widget tree
- [ ] Native GTK/Qt bindings

Ordered priorities:

1. Responsiveness
2. Memory footprint
3. Cross-platform parity

## Sample Rust view

```rust
use iced::widget::{button, column, text};

pub fn view(count: &u64) -> iced::Element<'_, Message> {
    column![
        text(format!("Count: {count}")),
        button("Increment").on_press(Message::Increment),
    ]
    .into()
}
```

## Math notation

The Iced Markdown renderer does not yet render LaTeX math, but you can still express ideas clearly:

- Inline complexity: `O(n log n)` vs `O(1)` lookup
- Unicode: α + β = γ, ∑ᵢ xᵢ, ∫₀¹ f(t) dt, E ≈ mc², ∞ → 0
- LaTeX source (shown as a fenced code block):

```latex
\int_{-\infty}^{\infty} e^{-x^2}\, dx = \sqrt{\pi}
```

[iced-ref]: https://iced.rs/docs

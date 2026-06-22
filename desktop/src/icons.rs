#![allow(dead_code)]

use iced::widget::svg;
use once_cell::sync::Lazy;

// Font assets mirror Android `core/ui/.../theme/Type.kt`:
// - `EnclaveUiFontFamily`: IBM Plex Sans (UI chrome, user bubbles, input)
// - `EnclaveReadingFontFamily`: Source Serif 4 (assistant reading body)
// - Code spans/blocks: platform monospace (Compose `FontFamily.Monospace`)

// IBM Plex Sans — UI chrome
pub const FONT_REGULAR_BYTES: &[u8] = include_bytes!("../assets/ibm_plex_sans_regular.ttf");
pub const FONT_MEDIUM_BYTES: &[u8] = include_bytes!("../assets/ibm_plex_sans_medium.ttf");
pub const FONT_SEMIBOLD_BYTES: &[u8] = include_bytes!("../assets/ibm_plex_sans_semibold.ttf");
pub const FONT_BOLD_BYTES: &[u8] = include_bytes!("../assets/ibm_plex_sans_bold.ttf");

// Source Serif 4 — assistant reading / long-form prose
pub const FONT_SERIF_REGULAR_BYTES: &[u8] = include_bytes!("../assets/source_serif4_regular.ttf");
pub const FONT_SERIF_BOLD_BYTES: &[u8] = include_bytes!("../assets/source_serif4_bold.ttf");

const IBM_PLEX_SANS: &str = "IBM Plex Sans";
const SOURCE_SERIF_4: &str = "Source Serif 4";

pub const FONT_REGULAR: iced::Font = iced::Font {
    family: iced::font::Family::Name(IBM_PLEX_SANS),
    weight: iced::font::Weight::Normal,
    stretch: iced::font::Stretch::Normal,
    style: iced::font::Style::Normal,
};

pub const FONT_MEDIUM: iced::Font = iced::Font {
    family: iced::font::Family::Name(IBM_PLEX_SANS),
    weight: iced::font::Weight::Medium,
    stretch: iced::font::Stretch::Normal,
    style: iced::font::Style::Normal,
};

pub const FONT_SEMIBOLD: iced::Font = iced::Font {
    family: iced::font::Family::Name(IBM_PLEX_SANS),
    weight: iced::font::Weight::Semibold,
    stretch: iced::font::Stretch::Normal,
    style: iced::font::Style::Normal,
};

pub const FONT_BOLD: iced::Font = iced::Font {
    family: iced::font::Family::Name(IBM_PLEX_SANS),
    weight: iced::font::Weight::Bold,
    stretch: iced::font::Stretch::Normal,
    style: iced::font::Style::Normal,
};

pub const FONT_SERIF_REGULAR: iced::Font = iced::Font {
    family: iced::font::Family::Name(SOURCE_SERIF_4),
    weight: iced::font::Weight::Normal,
    stretch: iced::font::Stretch::Normal,
    style: iced::font::Style::Normal,
};

pub const FONT_SERIF_BOLD: iced::Font = iced::Font {
    family: iced::font::Family::Name(SOURCE_SERIF_4),
    weight: iced::font::Weight::Bold,
    stretch: iced::font::Stretch::Normal,
    style: iced::font::Style::Normal,
};

/// Platform monospace for code spans and fenced blocks — matches Android `FontFamily.Monospace`.
pub const FONT_MONO: iced::Font = iced::Font::MONOSPACE;

// SVG byte slices
pub const SVG_CLOSE: &[u8] = include_bytes!("../assets/icons/x.svg");
pub const SVG_MENU: &[u8] = include_bytes!("../assets/icons/menu.svg");
pub const SVG_CHEVRON_DOWN: &[u8] = include_bytes!("../assets/icons/chevron-down.svg");
pub const SVG_CHEVRON_UP: &[u8] = include_bytes!("../assets/icons/chevron-up.svg");
pub const SVG_SHARE: &[u8] = include_bytes!("../assets/icons/share-2.svg");
pub const SVG_SAVE: &[u8] = include_bytes!("../assets/icons/save.svg");
pub const SVG_GALLERY: &[u8] = include_bytes!("../assets/icons/images.svg");
pub const SVG_MIC: &[u8] = include_bytes!("../assets/icons/mic.svg");
pub const SVG_IMAGE: &[u8] = include_bytes!("../assets/icons/image.svg");
pub const SVG_SEND: &[u8] = include_bytes!("../assets/icons/send.svg");
pub const SVG_SETTINGS: &[u8] = include_bytes!("../assets/icons/settings.svg");
pub const SVG_HISTORY: &[u8] = include_bytes!("../assets/icons/history.svg");
pub const SVG_ADD: &[u8] = include_bytes!("../assets/icons/plus.svg");
pub const SVG_MINIMIZE: &[u8] = include_bytes!("../assets/icons/minus.svg");
pub const SVG_MAXIMIZE: &[u8] = include_bytes!("../assets/icons/square.svg");
pub const SVG_ATTACH: &[u8] = include_bytes!("../assets/icons/paperclip.svg");
pub const SVG_PIN: &[u8] = include_bytes!("../assets/icons/pin.svg");
pub const SVG_PENCIL: &[u8] = include_bytes!("../assets/icons/pencil.svg");
pub const SVG_DELETE: &[u8] = include_bytes!("../assets/icons/trash-2.svg");
pub const SVG_FOLDER: &[u8] = include_bytes!("../assets/icons/folder.svg");
pub const SVG_COPY: &[u8] = include_bytes!("../assets/icons/copy.svg");
pub const SVG_LOG_IN: &[u8] = include_bytes!("../assets/icons/log-in.svg");
pub const SVG_LOG_OUT: &[u8] = include_bytes!("../assets/icons/log-out.svg");
pub const SVG_USER: &[u8] = include_bytes!("../assets/icons/user.svg");
pub const SVG_ENCLAVE_LOGO: &[u8] = include_bytes!("../assets/icons/enclave_logo.svg");
// The Enclave "E" source mark — cream `#F2F1EC` glyph with a peach `#FF8A00`
// accent dot. Used untinted (its colors are baked in via `fill` attributes).
pub const SVG_ENCLAVE_E_MARK: &[u8] = include_bytes!("../assets/icons/enclave_e_mark.svg");

// Static SVG handles
pub static HANDLE_CLOSE: Lazy<svg::Handle> = Lazy::new(|| svg::Handle::from_memory(SVG_CLOSE));
pub static HANDLE_MENU: Lazy<svg::Handle> = Lazy::new(|| svg::Handle::from_memory(SVG_MENU));
pub static HANDLE_CHEVRON_DOWN: Lazy<svg::Handle> =
    Lazy::new(|| svg::Handle::from_memory(SVG_CHEVRON_DOWN));
pub static HANDLE_CHEVRON_UP: Lazy<svg::Handle> =
    Lazy::new(|| svg::Handle::from_memory(SVG_CHEVRON_UP));
pub static HANDLE_SHARE: Lazy<svg::Handle> = Lazy::new(|| svg::Handle::from_memory(SVG_SHARE));
pub static HANDLE_SAVE: Lazy<svg::Handle> = Lazy::new(|| svg::Handle::from_memory(SVG_SAVE));
pub static HANDLE_GALLERY: Lazy<svg::Handle> = Lazy::new(|| svg::Handle::from_memory(SVG_GALLERY));
pub static HANDLE_MIC: Lazy<svg::Handle> = Lazy::new(|| svg::Handle::from_memory(SVG_MIC));
pub static HANDLE_IMAGE: Lazy<svg::Handle> = Lazy::new(|| svg::Handle::from_memory(SVG_IMAGE));
pub static HANDLE_SEND: Lazy<svg::Handle> = Lazy::new(|| svg::Handle::from_memory(SVG_SEND));
pub static HANDLE_SETTINGS: Lazy<svg::Handle> =
    Lazy::new(|| svg::Handle::from_memory(SVG_SETTINGS));
pub static HANDLE_HISTORY: Lazy<svg::Handle> = Lazy::new(|| svg::Handle::from_memory(SVG_HISTORY));
pub static HANDLE_ADD: Lazy<svg::Handle> = Lazy::new(|| svg::Handle::from_memory(SVG_ADD));
pub static HANDLE_MINIMIZE: Lazy<svg::Handle> =
    Lazy::new(|| svg::Handle::from_memory(SVG_MINIMIZE));
pub static HANDLE_MAXIMIZE: Lazy<svg::Handle> =
    Lazy::new(|| svg::Handle::from_memory(SVG_MAXIMIZE));
pub static HANDLE_ATTACH: Lazy<svg::Handle> = Lazy::new(|| svg::Handle::from_memory(SVG_ATTACH));
pub static HANDLE_PIN: Lazy<svg::Handle> = Lazy::new(|| svg::Handle::from_memory(SVG_PIN));
pub static HANDLE_PENCIL: Lazy<svg::Handle> = Lazy::new(|| svg::Handle::from_memory(SVG_PENCIL));
pub static HANDLE_DELETE: Lazy<svg::Handle> = Lazy::new(|| svg::Handle::from_memory(SVG_DELETE));
pub static HANDLE_FOLDER: Lazy<svg::Handle> = Lazy::new(|| svg::Handle::from_memory(SVG_FOLDER));
pub static HANDLE_COPY: Lazy<svg::Handle> = Lazy::new(|| svg::Handle::from_memory(SVG_COPY));
pub static HANDLE_LOG_IN: Lazy<svg::Handle> = Lazy::new(|| svg::Handle::from_memory(SVG_LOG_IN));
pub static HANDLE_LOG_OUT: Lazy<svg::Handle> = Lazy::new(|| svg::Handle::from_memory(SVG_LOG_OUT));
pub static HANDLE_USER: Lazy<svg::Handle> = Lazy::new(|| svg::Handle::from_memory(SVG_USER));
pub static HANDLE_ENCLAVE_LOGO: Lazy<svg::Handle> =
    Lazy::new(|| svg::Handle::from_memory(SVG_ENCLAVE_LOGO));
pub static HANDLE_ENCLAVE_E_MARK: Lazy<svg::Handle> =
    Lazy::new(|| svg::Handle::from_memory(SVG_ENCLAVE_E_MARK));

/// Render an SVG icon tinted with `color`.
pub fn render_svg(handle: &svg::Handle, color: iced::Color) -> svg::Svg<'_> {
    svg(handle.clone()).style(move |_theme, _status| svg::Style { color: Some(color) })
}

// Render an SVG with its baked-in colors (no tint). Used for the brand logo,
// which uses `fill` attributes rather than `stroke="currentColor"`.
pub fn render_svg_untinted(handle: &svg::Handle) -> svg::Svg<'_> {
    svg(handle.clone())
}

/// Build a determinate circular-progress ring as an SVG handle. `pct` is clamped
/// to the range 0 through 100 percent; the track is a 360° ring and the progress arc sweeps
/// clockwise from 12 o'clock, matching the Android `NeoCircularProgressIndicator`
/// (determinate mode) for cross-platform parity. Built fresh on each render so
/// the input bar `context_pct` always reflects the current chat state.
///
/// Geometry: 24×24 viewBox, center `(12, 12)`, radius `10`, stroke width `2`.
/// Progress uses `stroke-dasharray` on a `<circle>` so the arc shares the exact
/// center and radius as the track (the prior elliptical-arc path used incorrect
/// endpoint math and `stroke-linecap="round"`, which drew a distorted inner arc).
fn circular_progress_svg(pct: f32, color: iced::Color) -> String {
    let clamped = pct.clamp(0.0, 100.0) / 100.0;
    let cx = 12.0_f32;
    let cy = 12.0_f32;
    let r = 10.0_f32;
    let stroke_width = 2.0_f32;
    let circumference = 2.0 * std::f32::consts::PI * r;
    let track = svg_color(color);
    let stroke = svg_color(color);

    let progress_ring = if clamped > 0.0 {
        let dash = circumference * clamped;
        let gap = circumference - dash;
        format!(
            r#"<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{stroke}" stroke-width="{stroke_width}" stroke-dasharray="{dash} {gap}" stroke-linecap="butt" transform="rotate(-90 {cx} {cy})"/>"#,
        )
    } else {
        String::new()
    };

    format!(
        r#"<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none">
  <circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{track}" stroke-width="{stroke_width}" opacity="0.35"/>
  {progress_ring}
</svg>"#,
    )
}

pub fn render_circular_progress<'a>(pct: f32, color: iced::Color) -> svg::Svg<'a> {
    let svg = circular_progress_svg(pct, color);
    svg::Svg::new(svg::Handle::from_memory(svg.into_bytes()))
}

/// Format an `iced::Color` as an SVG `rgb()` / `rgba()` string. Used by
/// [`render_circular_progress`] to bake the ring color into the SVG source.
fn svg_color(color: iced::Color) -> String {
    let r = (color.r * 255.0).round() as u8;
    let g = (color.g * 255.0).round() as u8;
    let b = (color.b * 255.0).round() as u8;
    if color.a >= 1.0 {
        format!("rgb({r} {g} {b})")
    } else {
        format!("rgba({r} {g} {b} {:.3})", color.a)
    }
}

#[cfg(test)]
mod circular_progress_tests {
    use super::circular_progress_svg;
    use crate::theme;

    #[test]
    fn zero_percent_shows_track_only() {
        let svg = circular_progress_svg(0.0, theme::COLOR_ACCENT);
        assert!(!svg.contains("stroke-dasharray"));
        assert!(svg.contains("opacity=\"0.35\""));
    }

    #[test]
    fn partial_progress_uses_dasharray_on_track_radius() {
        let svg = circular_progress_svg(50.0, theme::COLOR_ACCENT);
        let circumference = 2.0 * std::f32::consts::PI * 10.0;
        let expected_dash = circumference * 0.5;
        assert!(svg.contains(&format!("stroke-dasharray=\"{expected_dash}")));
        assert!(svg.contains("transform=\"rotate(-90 12 12)\""));
        assert!(svg.contains("stroke-linecap=\"butt\""));
        assert!(!svg.contains("stroke-linecap=\"round\""));
    }

    #[test]
    fn full_progress_closes_the_ring() {
        let svg = circular_progress_svg(100.0, theme::COLOR_ACCENT);
        let circumference = 2.0 * std::f32::consts::PI * 10.0;
        assert!(svg.contains(&format!("stroke-dasharray=\"{circumference} 0\"")));
    }

    #[test]
    fn progress_clamps_out_of_range_input() {
        let svg = circular_progress_svg(150.0, theme::COLOR_ACCENT);
        let circumference = 2.0 * std::f32::consts::PI * 10.0;
        assert!(svg.contains(&format!("stroke-dasharray=\"{circumference} 0\"")));
    }
}

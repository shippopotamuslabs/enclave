#![allow(dead_code)]

use iced::widget::svg;
use once_cell::sync::Lazy;

// Font assets mirror Android `core/ui/.../theme/Type.kt`:
// - `EnclaveUiFontFamily`: IBM Plex Sans (UI chrome, user bubbles, input)
// - `EnclaveReadingFontFamily`: Source Serif 4 (assistant markdown body)
// - Code spans/blocks: platform monospace (Compose `FontFamily.Monospace`)

// IBM Plex Sans — UI chrome
pub const FONT_REGULAR_BYTES: &[u8] = include_bytes!("../assets/ibm_plex_sans_regular.ttf");
pub const FONT_MEDIUM_BYTES: &[u8] = include_bytes!("../assets/ibm_plex_sans_medium.ttf");
pub const FONT_SEMIBOLD_BYTES: &[u8] = include_bytes!("../assets/ibm_plex_sans_semibold.ttf");
pub const FONT_BOLD_BYTES: &[u8] = include_bytes!("../assets/ibm_plex_sans_bold.ttf");

// Source Serif 4 — assistant markdown / long-form reading
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

/// Platform monospace for markdown code — matches Android `FontFamily.Monospace`.
pub const FONT_MONO: iced::Font = iced::Font::MONOSPACE;

// SVG Byte slices
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
pub const SVG_ENCLAVE_LOGO: &[u8] = include_bytes!("../assets/icons/enclave_logo.svg");
// The Enclave "E" source mark — cream `#F2F1EC` glyph with a peach `#FF8A00`
// accent dot. Used untinted (its colors are baked in via `fill` attributes).
pub const SVG_ENCLAVE_E_MARK: &[u8] = include_bytes!("../assets/icons/enclave_e_mark.svg");

// Static SVG Handles
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
pub static HANDLE_ENCLAVE_LOGO: Lazy<svg::Handle> =
    Lazy::new(|| svg::Handle::from_memory(SVG_ENCLAVE_LOGO));
pub static HANDLE_ENCLAVE_E_MARK: Lazy<svg::Handle> =
    Lazy::new(|| svg::Handle::from_memory(SVG_ENCLAVE_E_MARK));

// Helper rendering function
pub fn render_svg(handle: &svg::Handle, color: iced::Color) -> svg::Svg<'_> {
    svg(handle.clone()).style(move |_theme, _status| svg::Style { color: Some(color) })
}

// Render an SVG with its baked-in colors (no tint). Used for the brand logo,
// which uses `fill` attributes rather than `stroke="currentColor"`.
pub fn render_svg_untinted(handle: &svg::Handle) -> svg::Svg<'_> {
    svg(handle.clone())
}

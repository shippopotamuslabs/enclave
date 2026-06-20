pub const ICON_FONT_BYTES: &[u8] = include_bytes!("../assets/material-icons.ttf");

pub const ICON_FONT: iced::Font = iced::Font {
    family: iced::font::Family::Name("Material Icons"),
    weight: iced::font::Weight::Normal,
    stretch: iced::font::Stretch::Normal,
    style: iced::font::Style::Normal,
};

// IBM Plex Sans Fonts
pub const FONT_REGULAR_BYTES: &[u8] = include_bytes!("../assets/ibm_plex_sans_regular.ttf");
pub const FONT_BOLD_BYTES: &[u8] = include_bytes!("../assets/ibm_plex_sans_bold.ttf");

pub const FONT_REGULAR: iced::Font = iced::Font {
    family: iced::font::Family::Name("IBM Plex Sans"),
    weight: iced::font::Weight::Normal,
    stretch: iced::font::Stretch::Normal,
    style: iced::font::Style::Normal,
};

pub const FONT_BOLD: iced::Font = iced::Font {
    family: iced::font::Family::Name("IBM Plex Sans"),
    weight: iced::font::Weight::Bold,
    stretch: iced::font::Stretch::Normal,
    style: iced::font::Style::Normal,
};

// Material Icon codepoint mapping constants
pub const ICON_CLOSE: &str = "\u{e5cd}";
pub const ICON_MENU: &str = "\u{e5d2}";
pub const ICON_CHEVRON_DOWN: &str = "\u{e313}";
#[allow(dead_code)]
pub const ICON_CHEVRON_UP: &str = "\u{e316}";
#[allow(dead_code)]
pub const ICON_SHARE: &str = "\u{e80d}";
#[allow(dead_code)]
pub const ICON_SAVE: &str = "\u{e9d6}"; // drive_file_move
pub const ICON_GALLERY: &str = "\u{e413}"; // photo_library
pub const ICON_MIC: &str = "\u{e029}";
pub const ICON_IMAGE: &str = "\u{e3f4}";
pub const ICON_SEND: &str = "\u{e15d}";
pub const ICON_SETTINGS: &str = "\u{e8b8}";
#[allow(dead_code)]
pub const ICON_HISTORY: &str = "\u{e8b9}";


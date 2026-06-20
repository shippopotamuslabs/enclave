pub const ICON_FONT_BYTES: &[u8] = include_bytes!("../assets/material-icons.ttf");

pub const ICON_FONT: iced::Font = iced::Font::with_name("Material Icons");

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

// Material Icon ligature mapping constants
pub const ICON_CLOSE: &str = "close";
pub const ICON_MENU: &str = "menu";
pub const ICON_CHEVRON_DOWN: &str = "keyboard_arrow_down";
#[allow(dead_code)]
pub const ICON_CHEVRON_UP: &str = "keyboard_arrow_up";
#[allow(dead_code)]
pub const ICON_SHARE: &str = "share";
#[allow(dead_code)]
pub const ICON_SAVE: &str = "save";
pub const ICON_GALLERY: &str = "photo_library";
pub const ICON_MIC: &str = "mic";
pub const ICON_IMAGE: &str = "image";
pub const ICON_SEND: &str = "send";
pub const ICON_SETTINGS: &str = "settings";
#[allow(dead_code)]
pub const ICON_HISTORY: &str = "history";
pub const ICON_ADD: &str = "add";
pub const ICON_MINIMIZE: &str = "remove";
pub const ICON_MAXIMIZE: &str = "crop_square";
pub const ICON_ATTACH: &str = "attach_file";
pub const ICON_PIN: &str = "push_pin";
pub const ICON_DELETE: &str = "delete";
pub const ICON_FOLDER: &str = "folder";

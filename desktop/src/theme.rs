use iced::widget::{button, container, scrollable, text_editor, text_input};
use iced::{Background, Border, Color, Shadow, Vector};

// App theme variant definition
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AppTheme {
    Dark,
    Light,
}

// --- DARK PALETTE ---
pub const DARK_BG: Color = Color::from_rgb(0.09, 0.09, 0.11); // Zinc 900
pub const DARK_SURFACE: Color = Color::from_rgb(0.15, 0.15, 0.16); // Zinc 800
pub const DARK_SURFACE_VAR: Color = Color::from_rgb(0.25, 0.25, 0.27); // Zinc 700
pub const DARK_TEXT: Color = Color::from_rgb(0.89, 0.89, 0.91); // Zinc 200
pub const DARK_TEXT_MUTED: Color = Color::from_rgb(0.63, 0.63, 0.67); // Zinc 400
pub const DARK_OUTLINE: Color = Color::from_rgb(0.32, 0.32, 0.36); // Zinc 600
pub const DARK_NEO_BORDER: Color = Color::from_rgb(0.21, 0.21, 0.23); // zinc-soft outline
pub const DARK_NEO_SHADOW: Color = Color::from_rgb(0.0, 0.0, 0.0);

// --- LIGHT PALETTE ---
pub const LIGHT_BG: Color = Color::from_rgb(1.0, 1.0, 1.0); // Pure white
pub const LIGHT_SURFACE: Color = Color::from_rgb(0.95, 0.96, 0.96); // Gray 100/50
pub const LIGHT_SURFACE_VAR: Color = Color::from_rgb(0.88, 0.90, 0.92); // Gray 200
pub const LIGHT_TEXT: Color = Color::from_rgb(0.07, 0.09, 0.15); // Gray 900
pub const LIGHT_TEXT_MUTED: Color = Color::from_rgb(0.40, 0.45, 0.50); // Gray 500
pub const LIGHT_OUTLINE: Color = Color::from_rgb(0.80, 0.84, 0.88); // Slate 300
pub const LIGHT_NEO_BORDER: Color = Color::from_rgb(0.58, 0.64, 0.72); // Slate 400
pub const LIGHT_NEO_SHADOW: Color = Color::from_rgb(0.06, 0.09, 0.16); // Slate 900

// ACCENT
pub const COLOR_ACCENT: Color = Color::from_rgb(0.96, 0.64, 0.38); // Peach Accent

#[derive(Clone, Copy)]
pub struct NeoContainer {
    pub is_surface: bool,
    pub has_shadow: bool,
    pub theme: AppTheme,
}

/// Borderless background fill. Used for panes (e.g. the chat body) that sit
/// flush against an adjacent bordered surface — the titlebar above and the
/// history sidebar's own border to the side — so the two borders don't double
/// up at the seam. Drawn with no border and no shadow; just the solid base or
/// surface background so the pane reads as one continuous panel.
#[derive(Clone, Copy)]
pub struct NeoPaneBackground {
    pub is_surface: bool,
    pub theme: AppTheme,
}

/// Single-edge solid rule. Iced's `Border` draws all four sides uniformly, so a
/// one-sided neobrutalist edge is rendered as a fixed-height strip filled with
/// the neobrutalist outline color (`*_NEO_BORDER`). Used as a bottom rule under
/// the chat pane — the chat pane alone, not the history sidebar, so the rule
/// spans only the chat region and never doubles up under the sidebar's border.
#[derive(Clone, Copy)]
pub struct NeoRule {
    pub theme: AppTheme,
}

fn neo_border(theme: AppTheme) -> Color {
    if theme == AppTheme::Dark {
        DARK_NEO_BORDER
    } else {
        LIGHT_NEO_BORDER
    }
}

fn neo_shadow_color(theme: AppTheme) -> Color {
    if theme == AppTheme::Dark {
        DARK_NEO_SHADOW
    } else {
        LIGHT_NEO_SHADOW
    }
}

fn neo_bg(theme: AppTheme, is_surface: bool) -> Color {
    match (theme, is_surface) {
        (AppTheme::Dark, true) => DARK_SURFACE,
        (AppTheme::Dark, false) => DARK_BG,
        (AppTheme::Light, true) => LIGHT_SURFACE,
        (AppTheme::Light, false) => LIGHT_BG,
    }
}

fn neo_text(theme: AppTheme) -> Color {
    if theme == AppTheme::Dark {
        DARK_TEXT
    } else {
        LIGHT_TEXT
    }
}

pub fn neo_rule(neo: NeoRule) -> impl Fn(&iced::Theme) -> container::Style {
    move |_theme| container::Style {
        text_color: None,
        background: Some(Background::Color(neo_border(neo.theme))),
        border: Border {
            color: Color::TRANSPARENT,
            width: 0.0,
            radius: 0.0.into(),
        },
        shadow: Shadow::default(),
        snap: false,
    }
}

pub fn neo_pane_background(neo: NeoPaneBackground) -> impl Fn(&iced::Theme) -> container::Style {
    move |_theme| container::Style {
        text_color: Some(neo_text(neo.theme)),
        background: Some(Background::Color(neo_bg(neo.theme, neo.is_surface))),
        border: Border {
            color: Color::TRANSPARENT,
            width: 0.0,
            radius: 0.0.into(),
        },
        shadow: Shadow::default(),
        snap: false,
    }
}

pub fn neo_container(neo: NeoContainer) -> impl Fn(&iced::Theme) -> container::Style {
    move |_theme| {
        let shadow = if neo.has_shadow {
            Shadow {
                color: neo_shadow_color(neo.theme),
                offset: Vector::new(4.0, 4.0),
                blur_radius: 0.0,
            }
        } else {
            Shadow::default()
        };

        container::Style {
            text_color: Some(neo_text(neo.theme)),
            background: Some(Background::Color(neo_bg(neo.theme, neo.is_surface))),
            border: Border {
                color: neo_border(neo.theme),
                width: 2.0,
                radius: 4.0.into(),
            },
            shadow,
            snap: false,
        }
    }
}

#[derive(Clone, Copy)]
pub struct NeoButton {
    pub is_accent: bool,
    pub has_shadow: bool,
    pub theme: AppTheme,
}

fn neo_button_active(neo: NeoButton) -> button::Style {
    let bg_color = if neo.is_accent {
        COLOR_ACCENT
    } else {
        neo_bg(neo.theme, true)
    };

    let text_color = if neo.is_accent {
        Color::BLACK
    } else {
        neo_text(neo.theme)
    };

    let shadow = if neo.has_shadow {
        Shadow {
            color: neo_shadow_color(neo.theme),
            offset: Vector::new(3.0, 3.0),
            blur_radius: 0.0,
        }
    } else {
        Shadow::default()
    };

    button::Style {
        background: Some(Background::Color(bg_color)),
        border: Border {
            color: neo_border(neo.theme),
            width: 2.0,
            radius: 4.0.into(),
        },
        shadow,
        text_color,
        snap: false,
    }
}

pub fn neo_button(neo: NeoButton) -> impl Fn(&iced::Theme, button::Status) -> button::Style {
    move |_theme, status| match status {
        button::Status::Active => neo_button_active(neo),
        button::Status::Hovered => {
            let mut active = neo_button_active(neo);
            let bg_color = if neo.is_accent {
                Color::from_rgb(0.98, 0.73, 0.52)
            } else if neo.theme == AppTheme::Dark {
                Color::from_rgb(0.18, 0.18, 0.20)
            } else {
                Color::from_rgb(0.90, 0.92, 0.92)
            };
            active.background = Some(Background::Color(bg_color));
            if neo.has_shadow {
                active.shadow = Shadow {
                    color: neo_shadow_color(neo.theme),
                    offset: Vector::new(5.0, 5.0),
                    blur_radius: 0.0,
                };
            }
            active
        }
        button::Status::Pressed => {
            let mut active = neo_button_active(neo);
            active.shadow = Shadow::default();
            active
        }
        button::Status::Disabled => button::Style {
            background: Some(Background::Color(neo_bg(neo.theme, false))),
            border: Border {
                color: neo_border(neo.theme),
                width: 2.0,
                radius: 4.0.into(),
            },
            shadow: Shadow::default(),
            text_color: if neo.theme == AppTheme::Dark {
                DARK_TEXT_MUTED
            } else {
                LIGHT_TEXT_MUTED
            },
            snap: false,
        },
    }
}

#[derive(Clone, Copy)]
pub struct NeoInput {
    pub theme: AppTheme,
}

fn neo_input_style(neo: NeoInput, border_color: Color) -> text_input::Style {
    let bg_color = if neo.theme == AppTheme::Dark {
        DARK_SURFACE_VAR
    } else {
        LIGHT_SURFACE_VAR
    };
    let text_muted = if neo.theme == AppTheme::Dark {
        DARK_TEXT_MUTED
    } else {
        LIGHT_TEXT_MUTED
    };
    let text_color = neo_text(neo.theme);

    text_input::Style {
        background: Background::Color(bg_color),
        border: Border {
            color: border_color,
            width: 2.0,
            radius: 4.0.into(),
        },
        icon: text_muted,
        placeholder: text_muted,
        value: text_color,
        selection: Color::from_rgba(0.96, 0.64, 0.38, 0.4),
    }
}

pub fn neo_input(neo: NeoInput) -> impl Fn(&iced::Theme, text_input::Status) -> text_input::Style {
    move |_theme, status| match status {
        text_input::Status::Focused { .. } => neo_input_style(neo, COLOR_ACCENT),
        text_input::Status::Disabled => {
            let bg_color = if neo.theme == AppTheme::Dark {
                DARK_BG
            } else {
                LIGHT_BG
            };
            let outline = if neo.theme == AppTheme::Dark {
                DARK_OUTLINE
            } else {
                LIGHT_OUTLINE
            };
            text_input::Style {
                background: Background::Color(bg_color),
                border: Border {
                    color: neo_border(neo.theme),
                    width: 2.0,
                    radius: 4.0.into(),
                },
                icon: outline,
                placeholder: outline,
                value: outline,
                selection: Color::from_rgba(0.96, 0.64, 0.38, 0.4),
            }
        }
        _ => neo_input_style(neo, neo_border(neo.theme)),
    }
}

#[derive(Clone, Copy)]
pub struct NeoScrollable {
    pub theme: AppTheme,
}

pub fn neo_scrollable(
    neo: NeoScrollable,
) -> impl Fn(&iced::Theme, scrollable::Status) -> scrollable::Style {
    move |_theme, status| {
        let scroller_color = if neo.theme == AppTheme::Dark {
            Color::from_rgba(0.32, 0.32, 0.36, 0.6)
        } else {
            Color::from_rgba(0.80, 0.84, 0.88, 0.6)
        };

        let rail = scrollable::Rail {
            background: Some(Background::Color(Color::TRANSPARENT)),
            border: Border::default(),
            scroller: scrollable::Scroller {
                background: Background::Color(scroller_color),
                border: Border {
                    color: Color::TRANSPARENT,
                    width: 0.0,
                    radius: 2.0.into(),
                },
            },
        };

        let mut style = scrollable::Style {
            container: container::Style::default(),
            vertical_rail: rail,
            horizontal_rail: rail,
            gap: None,
            auto_scroll: scrollable::AutoScroll {
                background: Background::Color(Color::TRANSPARENT),
                border: Border::default(),
                shadow: Shadow::default(),
                icon: neo_text(neo.theme),
            },
        };

        if let scrollable::Status::Hovered {
            is_horizontal_scrollbar_hovered,
            is_vertical_scrollbar_hovered,
            ..
        } = status
        {
            if is_horizontal_scrollbar_hovered || is_vertical_scrollbar_hovered {
                let hovered = scrollable::Rail {
                    scroller: scrollable::Scroller {
                        background: Background::Color(COLOR_ACCENT),
                        border: rail.scroller.border,
                    },
                    ..rail
                };
                if is_vertical_scrollbar_hovered {
                    style.vertical_rail = hovered;
                }
                if is_horizontal_scrollbar_hovered {
                    style.horizontal_rail = hovered;
                }
            }
        }

        style
    }
}

/// Solid container for the chat input bar. The text area and the action row
/// share one background + border so the bar reads as a single solid block.
#[derive(Clone, Copy)]
pub struct NeoInputBar {
    pub has_shadow: bool,
    pub theme: AppTheme,
}

pub fn neo_input_bar(neo: NeoInputBar) -> impl Fn(&iced::Theme) -> container::Style {
    move |_theme| {
        let shadow = if neo.has_shadow {
            Shadow {
                color: neo_shadow_color(neo.theme),
                offset: Vector::new(4.0, 4.0),
                blur_radius: 0.0,
            }
        } else {
            Shadow::default()
        };

        container::Style {
            text_color: Some(neo_text(neo.theme)),
            background: Some(Background::Color(neo_bg(neo.theme, true))),
            border: Border {
                color: neo_border(neo.theme),
                width: 2.0,
                radius: 4.0.into(),
            },
            shadow,
            snap: false,
        }
    }
}

/// Popover container for the `+` attachment menu, permission-mode dropdown, and
/// model picker. Surface background with a heavier offset shadow so it lifts
/// above the input bar.
#[derive(Clone, Copy)]
pub struct NeoDropdown {
    pub theme: AppTheme,
}

pub fn neo_dropdown(neo: NeoDropdown) -> impl Fn(&iced::Theme) -> container::Style {
    move |_theme| {
        let bg_color = if neo.theme == AppTheme::Dark {
            DARK_SURFACE_VAR
        } else {
            LIGHT_SURFACE_VAR
        };

        container::Style {
            text_color: Some(neo_text(neo.theme)),
            background: Some(Background::Color(bg_color)),
            border: Border {
                color: neo_border(neo.theme),
                width: 2.0,
                radius: 4.0.into(),
            },
            shadow: Shadow {
                color: neo_shadow_color(neo.theme),
                offset: Vector::new(4.0, 4.0),
                blur_radius: 0.0,
            },
            snap: false,
        }
    }
}

/// Neobrutalist modal bottom-sheet surface. A surface fill with the standard
/// 2px neo border, rounded top corners, and the heavy offset shadow so the
/// sheet lifts above the dimmed scrim — the desktop analogue of the Android
/// `NeoModalBottomSheet`. Width is constrained by the caller; the style only
/// owns fill/border/shadow.
#[derive(Clone, Copy)]
pub struct NeoBottomSheet {
    pub theme: AppTheme,
}

pub fn neo_bottom_sheet(neo: NeoBottomSheet) -> impl Fn(&iced::Theme) -> container::Style {
    move |_theme| container::Style {
        text_color: Some(neo_text(neo.theme)),
        background: Some(Background::Color(neo_bg(neo.theme, true))),
        border: Border {
            color: neo_border(neo.theme),
            width: 2.0,
            radius: 4.0.into(),
        },
        shadow: Shadow {
            color: neo_shadow_color(neo.theme),
            offset: Vector::new(4.0, 4.0),
            blur_radius: 0.0,
        },
        snap: false,
    }
}

/// Full-viewport dim layer behind a modal bottom sheet. Semi-transparent black
/// regardless of theme so the underlying chat reads through at a consistent
/// weight on dark and light (mirrors the Android modal scrim).
pub fn scrim_color() -> Color {
    Color::from_rgba(0.0, 0.0, 0.0, 0.5)
}

/// Neobrutalist style for the multi-line `text_editor`. Transparent background
/// (the enclosing `NeoInputBar` provides the solid fill) so the typing area and
/// the bar read as one solid block. Border only on focus.
#[derive(Clone, Copy)]
pub struct NeoTextEditor {
    pub theme: AppTheme,
}

pub fn neo_text_editor(
    neo: NeoTextEditor,
) -> impl Fn(&iced::Theme, text_editor::Status) -> text_editor::Style {
    let transparent = text_editor::Style {
        background: Background::Color(Color::TRANSPARENT),
        border: Border {
            color: Color::TRANSPARENT,
            width: 0.0,
            radius: 4.0.into(),
        },
        placeholder: if neo.theme == AppTheme::Dark {
            DARK_TEXT_MUTED
        } else {
            LIGHT_TEXT_MUTED
        },
        value: neo_text(neo.theme),
        selection: Color::from_rgba(0.96, 0.64, 0.38, 0.4),
    };

    move |_theme, _status| transparent
}

/// Neobrutalist container for a fenced markdown code block. Replaces Iced's
/// default `container::dark` (hard-coded `#111111` with a 2px round border and
/// no width) so the block reads as part of the locked surface palette: a solid
/// one-step-darker-than-surface fill with the standard 2px neo border and a 4px
/// radius, no offset shadow (the block sits flat inside the assistant
/// transcript, like the Android code-block surface).
#[derive(Clone, Copy)]
pub struct NeoCodeBlock {
    pub theme: AppTheme,
}

pub fn neo_code_block(neo: NeoCodeBlock) -> impl Fn(&iced::Theme) -> container::Style {
    move |_theme| {
        // One step darker than the surrounding surface so the block is visible
        // against both the chat pane background and the surface tokens.
        let bg_color = match neo.theme {
            AppTheme::Dark => Color::from_rgb(0.11, 0.11, 0.13),
            AppTheme::Light => Color::from_rgb(0.17, 0.18, 0.20),
        };

        container::Style {
            text_color: Some(Color::WHITE),
            background: Some(Background::Color(bg_color)),
            border: Border {
                color: neo_border(neo.theme),
                width: 2.0,
                radius: 4.0.into(),
            },
            shadow: Shadow::default(),
            snap: false,
        }
    }
}

/// Neobrutalist metadata header strip for a fenced code block — the language
/// label + copy control row that sits at the top of the block, inside the
/// [`NeoCodeBlock`] container. A flat surface fill one step lighter than the
/// block body with a 1px bottom rule in the neo border color so the header
/// reads as a distinct band without doubling the container's 2px border.
#[derive(Clone, Copy)]
pub struct NeoCodeBlockHeader {
    pub theme: AppTheme,
}

pub fn neo_code_block_header(neo: NeoCodeBlockHeader) -> impl Fn(&iced::Theme) -> container::Style {
    move |_theme| {
        let bg_color = match neo.theme {
            AppTheme::Dark => Color::from_rgb(0.17, 0.17, 0.19),
            AppTheme::Light => Color::from_rgb(0.23, 0.24, 0.27),
        };

        container::Style {
            text_color: Some(if neo.theme == AppTheme::Dark {
                DARK_TEXT_MUTED
            } else {
                Color::from_rgb(0.80, 0.84, 0.88)
            }),
            background: Some(Background::Color(bg_color)),
            border: Border {
                color: neo_border(neo.theme),
                width: 0.0,
                // Uniform radius to echo the enclosing `NeoCodeBlock` (4px);
                // Iced's `Radius` is a single scalar, so the header's bottom
                // corners round along with the top. The fill still reads as a
                // distinct band thanks to the contrast step + spacing above the
                // body, and stays inside the container's 2px border.
                radius: 4.0.into(),
            },
            shadow: Shadow::default(),
            snap: false,
        }
    }
}

/// Ghost button styling for the code-block copy control. No background, no
/// shadow, no border — just the icon — so it sits flush inside the header strip
/// and only the hover/press state lifts it (matching the icon-button treatment
/// in the titlebar's `NeoButton { has_shadow: false }` but stripped further for
/// an in-block affordance).
#[derive(Clone, Copy)]
pub struct NeoIconButton {
    pub theme: AppTheme,
}

pub fn neo_icon_button(
    neo: NeoIconButton,
) -> impl Fn(&iced::Theme, button::Status) -> button::Style {
    let icon_color = if neo.theme == AppTheme::Dark {
        DARK_TEXT_MUTED
    } else {
        Color::from_rgb(0.80, 0.84, 0.88)
    };

    move |_theme, status| {
        let (bg, color) = match status {
            button::Status::Hovered => (
                Some(Background::Color(Color::from_rgba(1.0, 1.0, 1.0, 0.08))),
                if neo.theme == AppTheme::Dark {
                    DARK_TEXT
                } else {
                    LIGHT_TEXT
                },
            ),
            button::Status::Pressed => (
                Some(Background::Color(Color::from_rgba(1.0, 1.0, 1.0, 0.16))),
                icon_color,
            ),
            _ => (None, icon_color),
        };

        button::Style {
            background: bg,
            border: Border {
                color: Color::TRANSPARENT,
                width: 0.0,
                radius: 4.0.into(),
            },
            shadow: Shadow::default(),
            text_color: color,
            snap: false,
        }
    }
}

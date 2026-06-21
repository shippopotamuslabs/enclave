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
pub struct NeoPaneBackground {
    pub is_surface: bool,
    pub theme: AppTheme,
}

/// Single-edge solid rule. Iced's `Border` draws all four sides uniformly, so a
/// one-sided neobrutalist edge is rendered as a fixed-height strip filled with
/// the neobrutalist outline color (`*_NEO_BORDER`). Used as a bottom rule under
/// the chat pane — the chat pane alone, not the history sidebar, so the rule
/// spans only the chat region and never doubles up under the sidebar's border.
pub struct NeoRule {
    pub theme: AppTheme,
}

impl container::StyleSheet for NeoRule {
    type Style = iced::Theme;

    fn appearance(&self, _style: &Self::Style) -> container::Appearance {
        let border_color = if self.theme == AppTheme::Dark {
            DARK_NEO_BORDER
        } else {
            LIGHT_NEO_BORDER
        };

        container::Appearance {
            text_color: None,
            background: Some(Background::Color(border_color)),
            border: Border {
                color: Color::TRANSPARENT,
                width: 0.0,
                radius: 0.0.into(),
            },
            shadow: Shadow::default(),
        }
    }
}

impl container::StyleSheet for NeoPaneBackground {
    type Style = iced::Theme;

    fn appearance(&self, _style: &Self::Style) -> container::Appearance {
        let is_dark = self.theme == AppTheme::Dark;
        let bg_color = if is_dark {
            if self.is_surface {
                DARK_SURFACE
            } else {
                DARK_BG
            }
        } else if self.is_surface {
            LIGHT_SURFACE
        } else {
            LIGHT_BG
        };
        let text_color = if is_dark { DARK_TEXT } else { LIGHT_TEXT };

        container::Appearance {
            text_color: Some(text_color),
            background: Some(Background::Color(bg_color)),
            border: Border {
                color: Color::TRANSPARENT,
                width: 0.0,
                radius: 0.0.into(),
            },
            shadow: Shadow::default(),
        }
    }
}

impl container::StyleSheet for NeoContainer {
    type Style = iced::Theme;

    fn appearance(&self, _style: &Self::Style) -> container::Appearance {
        let is_dark = self.theme == AppTheme::Dark;

        let bg_color = if is_dark {
            if self.is_surface {
                DARK_SURFACE
            } else {
                DARK_BG
            }
        } else {
            if self.is_surface {
                LIGHT_SURFACE
            } else {
                LIGHT_BG
            }
        };

        let text_color = if is_dark { DARK_TEXT } else { LIGHT_TEXT };
        let border_color = if is_dark {
            DARK_NEO_BORDER
        } else {
            LIGHT_NEO_BORDER
        };
        let shadow_color = if is_dark {
            DARK_NEO_SHADOW
        } else {
            LIGHT_NEO_SHADOW
        };

        let shadow = if self.has_shadow {
            Shadow {
                color: shadow_color,
                offset: Vector::new(4.0, 4.0),
                blur_radius: 0.0,
            }
        } else {
            Shadow::default()
        };

        container::Appearance {
            text_color: Some(text_color),
            background: Some(Background::Color(bg_color)),
            border: Border {
                color: border_color,
                width: 2.0,
                radius: 4.0.into(),
            },
            shadow,
        }
    }
}

pub struct NeoButton {
    pub is_accent: bool,
    pub has_shadow: bool,
    pub theme: AppTheme,
}

impl button::StyleSheet for NeoButton {
    type Style = iced::Theme;

    fn active(&self, _style: &Self::Style) -> button::Appearance {
        let is_dark = self.theme == AppTheme::Dark;

        let bg_color = if self.is_accent {
            COLOR_ACCENT
        } else if is_dark {
            DARK_SURFACE
        } else {
            LIGHT_SURFACE
        };

        let text_color = if self.is_accent {
            Color::BLACK
        } else if is_dark {
            DARK_TEXT
        } else {
            LIGHT_TEXT
        };

        let border_color = if is_dark {
            DARK_NEO_BORDER
        } else {
            LIGHT_NEO_BORDER
        };
        let shadow_color = if is_dark {
            DARK_NEO_SHADOW
        } else {
            LIGHT_NEO_SHADOW
        };

        let shadow_offset = if self.has_shadow {
            Vector::new(3.0, 3.0)
        } else {
            Vector::default()
        };

        let shadow = if self.has_shadow {
            Shadow {
                color: shadow_color,
                offset: Vector::new(3.0, 3.0),
                blur_radius: 0.0,
            }
        } else {
            Shadow::default()
        };

        button::Appearance {
            background: Some(Background::Color(bg_color)),
            border: Border {
                color: border_color,
                width: 2.0,
                radius: 4.0.into(),
            },
            shadow_offset,
            shadow,
            text_color,
        }
    }

    fn hovered(&self, style: &Self::Style) -> button::Appearance {
        let is_dark = self.theme == AppTheme::Dark;
        let active = self.active(style);

        let shadow_offset = if self.has_shadow {
            Vector::new(5.0, 5.0)
        } else {
            Vector::default()
        };

        let border_color = if is_dark {
            DARK_NEO_BORDER
        } else {
            LIGHT_NEO_BORDER
        };
        let shadow_color = if is_dark {
            DARK_NEO_SHADOW
        } else {
            LIGHT_NEO_SHADOW
        };

        let shadow = if self.has_shadow {
            Shadow {
                color: shadow_color,
                offset: Vector::new(5.0, 5.0),
                blur_radius: 0.0,
            }
        } else {
            Shadow::default()
        };

        let bg_color = if self.is_accent {
            Color::from_rgb(0.98, 0.73, 0.52) // Lighten peach slightly
        } else if is_dark {
            Color::from_rgb(0.18, 0.18, 0.20) // Lighten surface slightly
        } else {
            Color::from_rgb(0.90, 0.92, 0.92) // Lighten light surface slightly
        };

        button::Appearance {
            background: Some(Background::Color(bg_color)),
            shadow_offset,
            shadow,
            border: Border {
                color: border_color,
                width: 2.0,
                radius: 4.0.into(),
            },
            ..active
        }
    }

    fn pressed(&self, style: &Self::Style) -> button::Appearance {
        let active = self.active(style);
        button::Appearance {
            shadow_offset: Vector::default(),
            shadow: Shadow::default(),
            ..active
        }
    }

    fn disabled(&self, _style: &Self::Style) -> button::Appearance {
        let is_dark = self.theme == AppTheme::Dark;
        let bg_color = if is_dark { DARK_BG } else { LIGHT_BG };
        let border_color = if is_dark {
            DARK_NEO_BORDER
        } else {
            LIGHT_NEO_BORDER
        };
        let text_color = if is_dark {
            DARK_TEXT_MUTED
        } else {
            LIGHT_TEXT_MUTED
        };

        button::Appearance {
            background: Some(Background::Color(bg_color)),
            border: Border {
                color: border_color,
                width: 2.0,
                radius: 4.0.into(),
            },
            shadow_offset: Vector::default(),
            shadow: Shadow::default(),
            text_color,
        }
    }
}

pub struct NeoInput {
    pub theme: AppTheme,
}

impl text_input::StyleSheet for NeoInput {
    type Style = iced::Theme;

    fn active(&self, _style: &Self::Style) -> text_input::Appearance {
        let is_dark = self.theme == AppTheme::Dark;

        let bg_color = if is_dark {
            DARK_SURFACE_VAR
        } else {
            LIGHT_SURFACE_VAR
        };
        let border_color = if is_dark {
            DARK_NEO_BORDER
        } else {
            LIGHT_NEO_BORDER
        };
        let text_muted = if is_dark {
            DARK_TEXT_MUTED
        } else {
            LIGHT_TEXT_MUTED
        };

        text_input::Appearance {
            background: Background::Color(bg_color),
            border: Border {
                color: border_color,
                width: 2.0,
                radius: 4.0.into(),
            },
            icon_color: text_muted,
        }
    }

    fn focused(&self, _style: &Self::Style) -> text_input::Appearance {
        let is_dark = self.theme == AppTheme::Dark;
        let bg_color = if is_dark {
            DARK_SURFACE_VAR
        } else {
            LIGHT_SURFACE_VAR
        };
        let text_color = if is_dark { DARK_TEXT } else { LIGHT_TEXT };

        text_input::Appearance {
            background: Background::Color(bg_color),
            border: Border {
                color: COLOR_ACCENT,
                width: 2.0,
                radius: 4.0.into(),
            },
            icon_color: text_color,
        }
    }

    fn placeholder_color(&self, _style: &Self::Style) -> Color {
        if self.theme == AppTheme::Dark {
            DARK_TEXT_MUTED
        } else {
            LIGHT_TEXT_MUTED
        }
    }

    fn value_color(&self, _style: &Self::Style) -> Color {
        if self.theme == AppTheme::Dark {
            DARK_TEXT
        } else {
            LIGHT_TEXT
        }
    }

    fn disabled_color(&self, _style: &Self::Style) -> Color {
        if self.theme == AppTheme::Dark {
            DARK_TEXT_MUTED
        } else {
            LIGHT_TEXT_MUTED
        }
    }

    fn selection_color(&self, _style: &Self::Style) -> Color {
        Color::from_rgba(0.96, 0.64, 0.38, 0.4)
    }

    fn disabled(&self, _style: &Self::Style) -> text_input::Appearance {
        let is_dark = self.theme == AppTheme::Dark;
        let bg_color = if is_dark { DARK_BG } else { LIGHT_BG };
        let border_color = if is_dark {
            DARK_NEO_BORDER
        } else {
            LIGHT_NEO_BORDER
        };
        let outline = if is_dark { DARK_OUTLINE } else { LIGHT_OUTLINE };

        text_input::Appearance {
            background: Background::Color(bg_color),
            border: Border {
                color: border_color,
                width: 2.0,
                radius: 4.0.into(),
            },
            icon_color: outline,
        }
    }
}

pub struct NeoScrollable {
    pub theme: AppTheme,
}

impl scrollable::StyleSheet for NeoScrollable {
    type Style = iced::Theme;

    fn active(&self, _style: &Self::Style) -> scrollable::Appearance {
        let is_dark = self.theme == AppTheme::Dark;
        let scroller_color = if is_dark {
            Color::from_rgba(0.32, 0.32, 0.36, 0.6)
        } else {
            Color::from_rgba(0.80, 0.84, 0.88, 0.6)
        };

        scrollable::Appearance {
            container: container::Appearance::default(),
            scrollbar: scrollable::Scrollbar {
                background: Some(Background::Color(Color::TRANSPARENT)),
                border: Border::default(),
                scroller: scrollable::Scroller {
                    color: scroller_color,
                    border: Border {
                        color: Color::TRANSPARENT,
                        width: 0.0,
                        radius: 2.0.into(),
                    },
                },
            },
            gap: None,
        }
    }

    fn hovered(
        &self,
        style: &Self::Style,
        is_mouse_over_scrollbar: bool,
    ) -> scrollable::Appearance {
        let active = self.active(style);
        if is_mouse_over_scrollbar {
            scrollable::Appearance {
                scrollbar: scrollable::Scrollbar {
                    scroller: scrollable::Scroller {
                        color: COLOR_ACCENT,
                        ..active.scrollbar.scroller
                    },
                    ..active.scrollbar
                },
                ..active
            }
        } else {
            active
        }
    }
}

/// Solid container for the chat input bar. The text area and the action row
/// share one background + border so the bar reads as a single solid block.
pub struct NeoInputBar {
    pub has_shadow: bool,
    pub theme: AppTheme,
}

impl container::StyleSheet for NeoInputBar {
    type Style = iced::Theme;

    fn appearance(&self, _style: &Self::Style) -> container::Appearance {
        let is_dark = self.theme == AppTheme::Dark;
        let bg_color = if is_dark { DARK_SURFACE } else { LIGHT_SURFACE };
        let text_color = if is_dark { DARK_TEXT } else { LIGHT_TEXT };
        let border_color = if is_dark {
            DARK_NEO_BORDER
        } else {
            LIGHT_NEO_BORDER
        };
        let shadow_color = if is_dark {
            DARK_NEO_SHADOW
        } else {
            LIGHT_NEO_SHADOW
        };

        let shadow = if self.has_shadow {
            Shadow {
                color: shadow_color,
                offset: Vector::new(4.0, 4.0),
                blur_radius: 0.0,
            }
        } else {
            Shadow::default()
        };

        container::Appearance {
            text_color: Some(text_color),
            background: Some(Background::Color(bg_color)),
            border: Border {
                color: border_color,
                width: 2.0,
                radius: 4.0.into(),
            },
            shadow,
        }
    }
}

/// Popover container for the `+` attachment menu, permission-mode dropdown, and
/// model picker. Surface background with a heavier offset shadow so it lifts
/// above the input bar.
pub struct NeoDropdown {
    pub theme: AppTheme,
}

impl container::StyleSheet for NeoDropdown {
    type Style = iced::Theme;

    fn appearance(&self, _style: &Self::Style) -> container::Appearance {
        let is_dark = self.theme == AppTheme::Dark;
        let bg_color = if is_dark {
            DARK_SURFACE_VAR
        } else {
            LIGHT_SURFACE_VAR
        };
        let text_color = if is_dark { DARK_TEXT } else { LIGHT_TEXT };
        let border_color = if is_dark {
            DARK_NEO_BORDER
        } else {
            LIGHT_NEO_BORDER
        };
        let shadow_color = if is_dark {
            DARK_NEO_SHADOW
        } else {
            LIGHT_NEO_SHADOW
        };

        container::Appearance {
            text_color: Some(text_color),
            background: Some(Background::Color(bg_color)),
            border: Border {
                color: border_color,
                width: 2.0,
                radius: 4.0.into(),
            },
            shadow: Shadow {
                color: shadow_color,
                offset: Vector::new(4.0, 4.0),
                blur_radius: 0.0,
            },
        }
    }
}

/// Neobrutalist style for the multi-line `text_editor`. Transparent background
/// (the enclosing `NeoInputBar` provides the solid fill) so the typing area and
/// the bar read as one solid block. Border only on focus.
pub struct NeoTextEditor {
    pub theme: AppTheme,
}

impl text_editor::StyleSheet for NeoTextEditor {
    type Style = iced::Theme;

    fn active(&self, _style: &Self::Style) -> text_editor::Appearance {
        text_editor::Appearance {
            background: Background::Color(Color::TRANSPARENT),
            border: Border {
                color: Color::TRANSPARENT,
                width: 0.0,
                radius: 4.0.into(),
            },
        }
    }

    fn focused(&self, _style: &Self::Style) -> text_editor::Appearance {
        // Borderless on focus: the enclosing `NeoInputBar` already supplies the
        // solid border, so the editor must not draw its own peach outline when
        // the user clicks into it.
        text_editor::Appearance {
            background: Background::Color(Color::TRANSPARENT),
            border: Border {
                color: Color::TRANSPARENT,
                width: 0.0,
                radius: 4.0.into(),
            },
        }
    }

    fn placeholder_color(&self, _style: &Self::Style) -> Color {
        if self.theme == AppTheme::Dark {
            DARK_TEXT_MUTED
        } else {
            LIGHT_TEXT_MUTED
        }
    }

    fn value_color(&self, _style: &Self::Style) -> Color {
        if self.theme == AppTheme::Dark {
            DARK_TEXT
        } else {
            LIGHT_TEXT
        }
    }

    fn disabled_color(&self, _style: &Self::Style) -> Color {
        if self.theme == AppTheme::Dark {
            DARK_TEXT_MUTED
        } else {
            LIGHT_TEXT_MUTED
        }
    }

    fn selection_color(&self, _style: &Self::Style) -> Color {
        Color::from_rgba(0.96, 0.64, 0.38, 0.4)
    }

    fn hovered(&self, style: &Self::Style) -> text_editor::Appearance {
        self.focused(style)
    }

    fn disabled(&self, style: &Self::Style) -> text_editor::Appearance {
        self.active(style)
    }
}

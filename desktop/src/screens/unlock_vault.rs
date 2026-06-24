//! Vault-unlock screen — desktop port of the Android `UnlockVaultScreen` /
//! `VaultUnlockContent` body. Renders the cold-start password unlock surface:
//! account avatar, heading, Google sign-in affordance (mock), password input
//! with show/hide toggle, and the primary "Unlock" action. After a successful
//! unlock, the app opens chat directly when models are already on disk; otherwise
//! it routes to the download screen. The biometric opt-in from the Android body
//! is omitted — desktop platforms do not surface biometric unlock.
//!
//! The screen supports two modes selected by `VaultKind`:
//! - `VaultKind::Create` (first run): shows a confirm-password field and a
//!   "Create Vault" button; calls `VaultRepository::create`.
//! - `VaultKind::Open` (returning user): single password field, "Unlock"
//!   button; calls `VaultRepository::unlock`.
//!
//! The screen follows MVU: state lives in [`UnlockVaultState`], side effects
//! are dispatched from `main.rs::update` via `Message`, and [`view`] is a pure
//! layout mapping.

use crate::icons;
use crate::theme::{self, AppTheme};
use crate::Message;
use enclave_desktop_domain::vault::VaultKind;
use iced::widget::{button, column, container, row, space, text, text_input};
use iced::{alignment, Color, Element, Length, Theme};

/// State for the vault-unlock screen. Owned by `EnclaveApp` when
/// `Screen::UnlockVault` is active.
#[derive(Debug, Clone)]
pub struct UnlockVaultState {
    /// Current password input value.
    pub password: String,
    /// Confirm-password input value (first-run Create mode only).
    pub confirm_password: String,
    /// Whether the password is shown in cleartext (eye toggle).
    pub password_visible: bool,
    /// Unlocking/creating spinner latch — set true on submit while the async
    /// vault operation runs; cleared on completion or error.
    pub is_unlocking: bool,
    /// Inline error string rendered under the password input.
    pub error_message: Option<String>,
    /// Whether this is a first-run Create flow vs. a returning-user Open flow.
    /// Set by `boot` after probing `VaultRepository::status`.
    pub kind: VaultKind,
}

impl UnlockVaultState {
    pub fn new() -> Self {
        Self {
            password: String::new(),
            confirm_password: String::new(),
            password_visible: false,
            is_unlocking: false,
            error_message: None,
            kind: VaultKind::Open,
        }
    }

    /// True when the password field has non-whitespace content — gates the
    /// primary button (matching the Android `enabled = password.isNotBlank()`).
    /// In Create mode also requires the confirm field to match.
    pub fn can_submit(&self) -> bool {
        if self.password.trim().is_empty() {
            return false;
        }
        if self.kind == VaultKind::Create {
            !self.confirm_password.trim().is_empty() && self.password == self.confirm_password
        } else {
            true
        }
    }
}

impl Default for UnlockVaultState {
    fn default() -> Self {
        Self::new()
    }
}

pub fn view(
    state: &UnlockVaultState,
    app_theme: AppTheme,
) -> Element<'_, Message, Theme, iced::Renderer> {
    let is_dark = app_theme == AppTheme::Dark;
    let text_color = if is_dark {
        theme::DARK_TEXT
    } else {
        theme::LIGHT_TEXT
    };
    let muted_color = if is_dark {
        theme::DARK_TEXT_MUTED
    } else {
        theme::LIGHT_TEXT_MUTED
    };

    // --- Account avatar well ---
    // The desktop mock has no photo bytes, so the avatar is the user icon in a
    // surface well (matching the history sidebar's unauthenticated footer card).
    let avatar = container(
        icons::render_svg(&icons::HANDLE_USER, muted_color)
            .width(Length::Fixed(40.0))
            .height(Length::Fixed(40.0)),
    )
    .width(Length::Fixed(80.0))
    .height(Length::Fixed(80.0))
    .align_x(alignment::Horizontal::Center)
    .align_y(alignment::Vertical::Center)
    .style(theme::neo_container(theme::NeoContainer {
        is_surface: true,
        has_shadow: false,
        theme: app_theme,
    }));

    // --- Heading + subtitle ---
    let (heading_text, subtitle_text, primary_label) = match state.kind {
        VaultKind::Create => (
            "Create Enclave Vault",
            "Choose a password to encrypt your local data. \
             Store it safely — it cannot be recovered if lost.",
            "Create Vault",
        ),
        VaultKind::Open => (
            "Unlock Enclave",
            "Enter your Enclave password to decrypt local data. \
             Google sign-in is optional and only enables encrypted sync.",
            "Unlock",
        ),
    };

    let heading = text(heading_text)
        .font(icons::FONT_BOLD)
        .size(28)
        .color(text_color);

    let subtitle = text(subtitle_text)
        .font(icons::FONT_REGULAR)
        .size(14)
        .color(muted_color)
        .align_x(alignment::Horizontal::Center);

    // --- Google "Sign in with Google" button ---
    // Branding-compliant per https://developers.google.com/identity/branding-guidelines:
    // the standard multicolor "G" logo (rendered untinted so its four fixed
    // fills are preserved), the spec fill/stroke/text color per theme, and the
    // recommended "Sign in with Google" call-to-action. The neo 2px border and
    // hard shadow layer on top — Google's guidelines permit decorative borders.
    // The desktop shell has no auth backend, so the tap flips local state and
    // routes the user into the chat screen (same mock behavior as
    // `HistorySignIn`).
    let google_text_color = if is_dark {
        Color::from_rgb(0.890, 0.890, 0.890) // #E3E3E3
    } else {
        Color::from_rgb(0.122, 0.122, 0.122) // #1F1F1F
    };

    let sign_in_btn = button(
        row![
            icons::render_svg_untinted(&icons::HANDLE_GOOGLE_G)
                .width(Length::Fixed(24.0))
                .height(Length::Fixed(24.0)),
            space::horizontal().width(Length::Fixed(12.0)),
            text("Sign in with Google")
                .font(icons::FONT_MEDIUM)
                .size(14)
                .color(google_text_color)
        ]
        .align_y(alignment::Alignment::Center),
    )
    .width(Length::Fill)
    .padding(12)
    .style(theme::neo_google_button(theme::NeoGoogleButton {
        theme: app_theme,
    }))
    .on_press(Message::UnlockVaultLogin);

    // --- Warning row (unrecoverable password caveat) ---
    // Amber-accented NeoContainer mirroring the Android `errorContainer` row.
    // Peach accent for the icon + muted body copy on the surface fill.
    let warning_icon = icons::render_svg(&icons::HANDLE_LOCK, theme::COLOR_ACCENT)
        .width(Length::Fixed(20.0))
        .height(Length::Fixed(20.0));

    let warning_body = text(
        "Store your password in a secure place. Enclave does not store your \
         password and it cannot be recovered if lost.",
    )
    .font(icons::FONT_REGULAR)
    .size(12)
    .color(muted_color);

    let warning_row = container(
        row![
            warning_icon,
            space::horizontal().width(Length::Fixed(12.0)),
            warning_body
        ]
        .align_y(alignment::Alignment::Center),
    )
    .width(Length::Fill)
    .padding(16)
    .style(theme::neo_container(theme::NeoContainer {
        is_surface: true,
        has_shadow: false,
        theme: app_theme,
    }));

    // --- Password input + show/hide toggle ---
    let password_placeholder = match state.kind {
        VaultKind::Create => "New Enclave Password",
        VaultKind::Open => "Enclave Password",
    };
    let password_input = text_input(password_placeholder, &state.password)
        .on_input(Message::UnlockVaultPasswordChanged)
        .on_submit_maybe(if state.can_submit() {
            Some(Message::UnlockVaultSubmit)
        } else {
            None
        })
        .secure(!state.password_visible)
        .padding(12)
        .size(14)
        .style(theme::neo_input(theme::NeoInput { theme: app_theme }));

    // Eye / eye-off toggle button — sits at the trailing edge of the input row.
    let eye_handle = if state.password_visible {
        &icons::HANDLE_EYE
    } else {
        &icons::HANDLE_EYE_OFF
    };

    let visibility_toggle = button(
        container(icons::render_svg(eye_handle, muted_color))
            .width(Length::Fill)
            .height(Length::Fill)
            .align_x(alignment::Horizontal::Center)
            .align_y(alignment::Vertical::Center),
    )
    .width(Length::Fixed(40.0))
    .height(Length::Fixed(40.0))
    .padding(0)
    .style(theme::neo_button(theme::NeoButton {
        is_accent: false,
        has_shadow: false,
        theme: app_theme,
    }))
    .on_press(Message::UnlockVaultTogglePasswordVisible);

    let password_row = row![password_input, visibility_toggle]
        .spacing(8)
        .align_y(alignment::Alignment::Center)
        .width(Length::Fill);

    // --- Confirm-password input (Create mode only) ---
    let confirm_row: Option<iced::widget::Row<'_, Message, Theme, iced::Renderer>> =
        if state.kind == VaultKind::Create {
            let confirm_input = text_input("Confirm Password", &state.confirm_password)
                .on_input(Message::UnlockVaultConfirmPasswordChanged)
                .on_submit_maybe(if state.can_submit() {
                    Some(Message::UnlockVaultSubmit)
                } else {
                    None
                })
                .secure(!state.password_visible)
                .padding(12)
                .size(14)
                .style(theme::neo_input(theme::NeoInput { theme: app_theme }));
            Some(
                row![confirm_input]
                    .spacing(8)
                    .align_y(alignment::Alignment::Center)
                    .width(Length::Fill),
            )
        } else {
            None
        };

    // Mismatch hint for Create mode.
    let mismatch_error: Option<Element<'_, Message, Theme, iced::Renderer>> = if state.kind
        == VaultKind::Create
        && !state.confirm_password.is_empty()
        && state.password != state.confirm_password
    {
        Some(
            text("Passwords do not match")
                .font(icons::FONT_REGULAR)
                .size(12)
                .color(Color::from_rgb(0.85, 0.27, 0.27))
                .align_x(alignment::Horizontal::Center)
                .into(),
        )
    } else {
        None
    };

    // Inline error text under the input (mirrors the Android `supportingText`).
    let error_text: Option<Element<'_, Message, Theme, iced::Renderer>> =
        state.error_message.as_ref().map(|msg| {
            text(msg)
                .font(icons::FONT_REGULAR)
                .size(12)
                .color(Color::from_rgb(0.85, 0.27, 0.27))
                .align_x(alignment::Horizontal::Center)
                .into()
        });

    // --- Primary "Unlock" action ---
    // Accent NeoButton, disabled (no `on_press`) when the password is blank —
    // matches the Android `enabled = password.isNotBlank()` gate.
    let mut unlock_btn = button(
        row![
            icons::render_svg(&icons::HANDLE_LOCK, Color::BLACK)
                .width(Length::Fixed(16.0))
                .height(Length::Fixed(16.0)),
            text(primary_label)
                .font(icons::FONT_BOLD)
                .size(14)
                .color(Color::BLACK)
        ]
        .spacing(10)
        .align_y(alignment::Alignment::Center),
    )
    .width(Length::Fill)
    .padding(14)
    .style(theme::neo_button(theme::NeoButton {
        is_accent: true,
        has_shadow: true,
        theme: app_theme,
    }));

    if state.can_submit() {
        unlock_btn = unlock_btn.on_press(Message::UnlockVaultSubmit);
    }

    // --- Compose the centered card body ---
    let mut body = column![
        avatar,
        space::vertical().height(Length::Fixed(20.0)),
        heading,
        space::vertical().height(Length::Fixed(8.0)),
        subtitle,
        space::vertical().height(Length::Fixed(28.0)),
        sign_in_btn,
        space::vertical().height(Length::Fixed(24.0)),
        warning_row,
        space::vertical().height(Length::Fixed(20.0)),
        password_row,
    ]
    .spacing(0)
    .align_x(alignment::Horizontal::Center)
    .width(Length::Fixed(420.0));

    // Confirm-password row (Create mode only).
    if let Some(confirm) = confirm_row {
        body = body
            .push(space::vertical().height(Length::Fixed(12.0)))
            .push(confirm);
    }

    // Mismatch hint (Create mode only, takes priority over the generic error).
    if let Some(mismatch) = mismatch_error {
        body = body
            .push(space::vertical().height(Length::Fixed(8.0)))
            .push(mismatch);
    } else if let Some(err) = error_text {
        body = body
            .push(space::vertical().height(Length::Fixed(8.0)))
            .push(err);
    }

    body = body
        .push(space::vertical().height(Length::Fixed(16.0)))
        .push(unlock_btn);

    // Card surface — the neo container gives the body its bordered surface fill
    // with the standard 2px outline + offset shadow.
    let card = container(body)
        .width(Length::Fixed(480.0))
        .padding(28)
        .style(theme::neo_container(theme::NeoContainer {
            is_surface: true,
            has_shadow: true,
            theme: app_theme,
        }));

    // Center the card in the viewport. A plain fill container (rather than a
    // `scrollable`) is what makes `align_y(Vertical::Center)` resolve against
    // the full window height — Iced's scrollable does not resolve `height(Fill)`
    // to the viewport for centering, so it left the card top-aligned. The
    // centered card stays reachable on small windows because the card's fixed
    // width (480px) is well under any practical desktop window width.
    let centered = container(card)
        .width(Length::Fill)
        .height(Length::Fill)
        .align_x(alignment::Horizontal::Center)
        .align_y(alignment::Vertical::Center);

    // Full-screen shell — base background fill, no border (matches the Android
    // `Box` background wrapper). Reuses the shared `screen_shell` so the
    // unlock route carries the same outer chrome as every other screen.
    crate::screens::layout::screen_shell(centered, app_theme)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn new_state_starts_empty_and_hidden() {
        let state = UnlockVaultState::new();
        assert!(state.password.is_empty());
        assert!(state.confirm_password.is_empty());
        assert!(!state.password_visible);
        assert!(!state.is_unlocking);
        assert!(state.error_message.is_none());
        assert!(!state.can_submit());
    }

    #[test]
    fn can_submit_is_false_for_whitespace_only_password() {
        let mut state = UnlockVaultState::new();
        state.password = "   ".into();
        assert!(!state.can_submit());
    }

    #[test]
    fn can_submit_is_true_for_non_empty_password_in_open_mode() {
        let mut state = UnlockVaultState::new();
        state.kind = VaultKind::Open;
        state.password = "hunter2".into();
        assert!(state.can_submit());
    }

    #[test]
    fn can_submit_requires_matching_confirm_in_create_mode() {
        let mut state = UnlockVaultState::new();
        state.kind = VaultKind::Create;
        state.password = "hunter2".into();
        state.confirm_password = "hunter3".into();
        assert!(
            !state.can_submit(),
            "mismatched confirm should block submit"
        );
        state.confirm_password = "hunter2".into();
        assert!(state.can_submit());
    }

    #[test]
    fn password_visible_toggle_flips_flag() {
        let mut state = UnlockVaultState::new();
        state.password_visible = !state.password_visible;
        assert!(state.password_visible);
        state.password_visible = !state.password_visible;
        assert!(!state.password_visible);
    }

    #[test]
    fn view_builds_for_dark_and_light_themes_open_mode() {
        let state = UnlockVaultState::new();
        let _dark = view(&state, AppTheme::Dark);
        let _light = view(&state, AppTheme::Light);
    }

    #[test]
    fn view_builds_in_create_mode() {
        let mut state = UnlockVaultState::new();
        state.kind = VaultKind::Create;
        state.password = "pw".into();
        state.confirm_password = "pw".into();
        let _ = view(&state, AppTheme::Dark);
    }

    #[test]
    fn view_builds_when_error_message_set() {
        let mut state = UnlockVaultState::new();
        state.error_message = Some("Incorrect password".into());
        let _ = view(&state, AppTheme::Dark);
    }
}

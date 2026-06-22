//noinspection SpellCheckingInspection
use crate::icons;
use crate::theme::{self, AppTheme};
use crate::widgets::{Popover, Position};
use crate::Message;
use iced::widget::{
    button, column, container, mouse_area, row, scrollable, space, text, text_input, Column,
};
use iced::{alignment, Color, Element, Length, Theme};

pub const MIN_SIDEBAR_WIDTH: f32 = 220.0;
pub const MAX_SIDEBAR_WIDTH: f32 = 480.0;

/// Desktop mock of the Android `AuthState` port. The desktop shell has no auth
/// backend wired yet, so the state is flipped locally by the profile-card
/// popover (`HistorySignIn` / `HistorySignOut`). Mirrors the seed profile the
/// settings screen previously carried ("Sean Dev" / "sean@browntowndev.com") so
/// the signed-in footer card reads as a real account, not placeholder chrome.
#[derive(Debug, Clone, PartialEq)]
pub enum AuthState {
    Unauthenticated,
    Authenticated(UserProfile),
}

#[derive(Debug, Clone, PartialEq)]
pub struct UserProfile {
    pub uid: String,
    pub display_name: String,
    pub email: String,
    /// Optional photo URL. Not populated by the desktop mock — the avatar well
    /// falls back to initials derived from `display_name` (matching the prior
    /// settings ACCOUNT card). A future BYOK/auth port would populate this and
    /// load the bytes asynchronously into an `iced::widget::image` avatar.
    #[allow(dead_code)]
    pub photo_url: Option<String>,
}

impl UserProfile {
    /// Seed profile used by `HistorySignIn` and the initial `HistoryState`.
    fn seed() -> Self {
        Self {
            uid: "mock_user_1".into(),
            display_name: "Sean Dev".into(),
            email: "sean@browntowndev.com".into(),
            photo_url: None,
        }
    }

    /// Initials for the avatar well (e.g. "Sean Dev" → "SD"), matching the
    /// ACCOUNT card pattern the settings screen previously carried.
    fn initials(&self) -> String {
        self.display_name
            .split_whitespace()
            .filter_map(|part| part.chars().next())
            .take(2)
            .collect::<String>()
            .to_uppercase()
    }
}

impl Default for AuthState {
    fn default() -> Self {
        AuthState::Authenticated(UserProfile::seed())
    }
}

pub struct HistoryState {
    pub search_query: String,
    pub chats: Vec<HistoryChat>,
    pub hovered_chat_id: Option<String>,
    pub sidebar_width: f32,
    /// Mock Google sign-in state rendered by the sidebar footer card.
    pub auth: AuthState,
    /// Whether the profile popover (Login/Logout) is open over the footer card.
    pub profile_menu_open: bool,
}

#[derive(Debug, Clone)]
pub struct HistoryChat {
    pub id: String,
    pub name: String,
    #[allow(dead_code)]
    pub last_message: String,
    pub timestamp: String,
    pub is_pinned: bool,
}

impl HistoryState {
    pub fn new() -> Self {
        Self {
            search_query: String::new(),
            hovered_chat_id: None,
            sidebar_width: MAX_SIDEBAR_WIDTH,
            auth: AuthState::default(),
            profile_menu_open: false,
            chats: vec![
                HistoryChat {
                    id: "chat_1".into(),
                    name: "Analyzing Rust Iced performance".into(),
                    last_message: "Let's check the frame rates...".into(),
                    timestamp: "10:30 AM".into(),
                    is_pinned: true,
                },
                HistoryChat {
                    id: "chat_2".into(),
                    name: "Android Compose compiler options".into(),
                    last_message: "Make sure metrics are enabled".into(),
                    timestamp: "Yesterday".into(),
                    is_pinned: true,
                },
                HistoryChat {
                    id: "chat_3".into(),
                    name: "Firebase security rules logic".into(),
                    last_message: "allow read, write: if request.auth != null".into(),
                    timestamp: "June 18".into(),
                    is_pinned: false,
                },
                HistoryChat {
                    id: "chat_4".into(),
                    name: "Multimodal Gemini API prompt engineering".into(),
                    last_message: "System instructions must be clear".into(),
                    timestamp: "June 15".into(),
                    is_pinned: false,
                },
            ],
        }
    }

    pub fn clamp_sidebar_width(width: f32) -> f32 {
        width.clamp(MIN_SIDEBAR_WIDTH, MAX_SIDEBAR_WIDTH)
    }

    pub fn apply_sidebar_resize_delta(&mut self, delta_x: f32) {
        self.sidebar_width = Self::clamp_sidebar_width(self.sidebar_width + delta_x);
    }
}

/// Max title length when hover actions are visible, scaled to sidebar width.
fn hover_title_max_chars(sidebar_width: f32) -> usize {
    // Reserve ~96px for padding + hover action buttons; ~12px per character at 13px bold.
    ((sidebar_width - 96.0) / 12.0).max(10.0) as usize
}

pub fn view(
    state: &HistoryState,
    app_theme: AppTheme,
) -> Element<'_, Message, Theme, iced::Renderer> {
    let is_dark = app_theme == AppTheme::Dark;

    // --- 1. Sidebar Header (Search) ---
    let search_input = text_input("Search chats...", &state.search_query)
        .on_input(Message::HistorySearchChanged)
        .padding(8)
        .style(theme::neo_input(theme::NeoInput { theme: app_theme }));

    let text_color = if is_dark {
        theme::DARK_TEXT
    } else {
        theme::LIGHT_TEXT
    };

    // The settings cog used to live here; it now sits at the end of the footer
    // row beside the profile card (see `profile_footer_card` below). The header
    // is search-only.
    let header_row = row![search_input]
        .align_y(alignment::Alignment::Center)
        .padding(10);

    // --- 2. Navigation Quick Links ---
    // Each quick link is a standalone full-width shadowed `NeoButton` (matching
    // the chat input bar's action buttons) rather than buttons grouped inside a
    // bordered box — so they read as individual elevated controls, not a panel.
    let nav_btn =
        |title: &'static str, icon_handle: &'static iced::widget::svg::Handle, msg: Message| {
            button(
                row![
                    icons::render_svg(icon_handle, theme::COLOR_ACCENT)
                        .width(Length::Fixed(16.0))
                        .height(Length::Fixed(16.0)),
                    text(title)
                        .font(icons::FONT_BOLD)
                        .size(13)
                        .color(if is_dark {
                            theme::DARK_TEXT
                        } else {
                            theme::LIGHT_TEXT
                        })
                ]
                .spacing(10)
                .align_y(alignment::Alignment::Center),
            )
            .width(Length::Fill)
            .padding(10)
            .style(theme::neo_button(theme::NeoButton {
                is_accent: false,
                has_shadow: true,
                theme: app_theme,
            }))
            .on_press(msg)
        };

    let quick_links = column![
        nav_btn(
            "New Chat",
            &icons::HANDLE_ADD,
            Message::NavigateTo(crate::Screen::Chat {
                chat_id: "new_chat".into()
            })
        ),
        nav_btn(
            "Studio Screen",
            &icons::HANDLE_IMAGE,
            Message::NavigateTo(crate::Screen::Studio)
        ),
        nav_btn(
            "Media Gallery",
            &icons::HANDLE_GALLERY,
            Message::NavigateTo(crate::Screen::Gallery {
                selected_album_id: None
            })
        )
    ]
    .spacing(8);

    // --- 3. Chat Lists ---
    let filtered_chats: Vec<&HistoryChat> = state
        .chats
        .iter()
        .filter(|c| {
            state.search_query.is_empty()
                || c.name
                    .to_lowercase()
                    .contains(&state.search_query.to_lowercase())
        })
        .collect();

    let pinned_chats: Vec<&&HistoryChat> = filtered_chats.iter().filter(|c| c.is_pinned).collect();
    let recent_chats: Vec<&&HistoryChat> = filtered_chats.iter().filter(|c| !c.is_pinned).collect();

    let render_chat_row =
        |chat: &HistoryChat| {
            let chat_id = chat.id.clone();
            let is_pinned = chat.is_pinned;
            let is_hovered = state.hovered_chat_id.as_deref() == Some(chat_id.as_str());
            let muted_color = if is_dark {
                theme::DARK_TEXT_MUTED
            } else {
                theme::LIGHT_TEXT_MUTED
            };
            let title_color = if is_dark {
                theme::DARK_TEXT
            } else {
                theme::LIGHT_TEXT
            };

            let title_label = if is_hovered {
                truncate_with_ellipsis(&chat.name, hover_title_max_chars(state.sidebar_width))
            } else {
                chat.name.clone()
            };

            let title = text(title_label)
                .font(icons::FONT_BOLD)
                .size(13)
                .color(title_color)
                .width(Length::Fill);

            let timestamp = text(chat.timestamp.clone())
                .font(icons::FONT_REGULAR)
                .size(11)
                .color(muted_color);

            let chat_body = button(column![title, timestamp].spacing(2))
                .width(Length::Fill)
                .style(button::text)
                .on_press(Message::NavigateTo(crate::Screen::Chat {
                    chat_id: chat_id.clone(),
                }));

            let mut card_row = row![chat_body].align_y(alignment::Alignment::Center);

            if is_hovered {
                let pin_color = if is_pinned {
                    theme::COLOR_ACCENT
                } else {
                    muted_color
                };

                let pin_btn = button(
                    icons::render_svg(&icons::HANDLE_PIN, pin_color)
                        .width(Length::Fixed(16.0))
                        .height(Length::Fixed(16.0)),
                )
                .style(button::text)
                .on_press(if is_pinned {
                    Message::HistoryUnpinChat(chat_id.clone())
                } else {
                    Message::HistoryPinChat(chat_id.clone())
                });

                let rename_btn = button(
                    icons::render_svg(&icons::HANDLE_PENCIL, muted_color)
                        .width(Length::Fixed(16.0))
                        .height(Length::Fixed(16.0)),
                )
                .style(button::text)
                .on_press(Message::HistoryRenameChat(chat_id.clone()));

                let delete_btn = button(
                    icons::render_svg(&icons::HANDLE_DELETE, muted_color)
                        .width(Length::Fixed(16.0))
                        .height(Length::Fixed(16.0)),
                )
                .style(button::text)
                .on_press(Message::HistoryDeleteChat(chat_id.clone()));

                card_row = card_row.push(
                    row![pin_btn, rename_btn, delete_btn]
                        .spacing(4)
                        .align_y(alignment::Alignment::Center),
                );
            }

            mouse_area(container(card_row).padding(8).width(Length::Fill).style(
                theme::neo_container(theme::NeoContainer {
                    is_surface: true,
                    has_shadow: false,
                    theme: app_theme,
                }),
            ))
            .on_enter(Message::HistoryHoverChat(Some(chat_id)))
            .on_exit(Message::HistoryHoverChat(None))
        };

    let mut sidebar_list = Column::new().spacing(14);

    // Quick navigation section
    sidebar_list = sidebar_list.push(quick_links);

    // Pinned Section
    if !pinned_chats.is_empty() {
        let mut section = Column::new().spacing(6);
        section = section.push(
            text("PINNED")
                .font(icons::FONT_BOLD)
                .size(10)
                .color(if is_dark {
                    theme::DARK_TEXT_MUTED
                } else {
                    theme::LIGHT_TEXT_MUTED
                }),
        );
        for chat in pinned_chats {
            section = section.push(render_chat_row(chat));
        }
        sidebar_list = sidebar_list.push(section);
    }

    // Recent Section
    if !recent_chats.is_empty() {
        let mut section = Column::new().spacing(6);
        section = section.push(text("RECENT CHATS").font(icons::FONT_BOLD).size(10).color(
            if is_dark {
                theme::DARK_TEXT_MUTED
            } else {
                theme::LIGHT_TEXT_MUTED
            },
        ));
        for chat in recent_chats {
            section = section.push(render_chat_row(chat));
        }
        sidebar_list = sidebar_list.push(section);
    }

    let scroll_sidebar = scrollable(container(sidebar_list).padding(10).width(Length::Fill))
        .height(Length::Fill)
        .style(theme::neo_scrollable(theme::NeoScrollable {
            theme: app_theme,
        }));

    // --- 4. Footer: profile card + settings cog ---
    // The Google sign-in affordance migrated here from the settings screen. The
    // card is a `Popover` anchor: tapping it opens a single icon-led row that is
    // "Login" when `AuthState::Unauthenticated` or "Logout" when authenticated.
    // The settings cog moved out of the sidebar header to sit beside the card so
    // the two account-chrome controls group at the sidebar's foot.
    let profile_footer = profile_footer_card(state, app_theme, text_color);

    let sidebar_layout = column![header_row, scroll_sidebar, profile_footer]
        .width(Length::Fixed(state.sidebar_width))
        .height(Length::Fill);

    // Borderless surface fill — the draggable pane splitter draws the single
    // vertical rule between sidebar and chat so we do not double up with a
    // `NeoContainer` right edge. A full-width bottom rule in the chat screen
    // closes off the pane row under both columns.
    container(sidebar_layout)
        .width(Length::Fixed(state.sidebar_width))
        .height(Length::Fill)
        .style(theme::neo_pane_background(theme::NeoPaneBackground {
            is_surface: true,
            theme: app_theme,
        }))
        .into()
}

/// Build the sidebar footer: a profile/account card on the left (tapping it
/// opens the Login/Logout popover) and the settings cog on the right. The card
/// content adapts to `HistoryState::auth` — a signed-in user shows an avatar
/// well with their initials plus name/email, an unsigned user shows a generic
/// "Sign in" affordance with the user icon. The avatar is an initials well for
/// now (the desktop mock has no photo bytes); a future auth port would render
/// `photo_url` into an `iced::widget::image` avatar here.
fn profile_footer_card(
    state: &HistoryState,
    app_theme: AppTheme,
    text_color: Color,
) -> Element<'_, Message, Theme, iced::Renderer> {
    let is_dark = app_theme == AppTheme::Dark;
    let muted_color = if is_dark {
        theme::DARK_TEXT_MUTED
    } else {
        theme::LIGHT_TEXT_MUTED
    };

    // Avatar well — initials for an authenticated user, the user icon for an
    // unauthenticated one. The well is a Light-theme `NeoContainer` so the
    // initials (black) stay readable, mirroring the prior settings ACCOUNT
    // avatar.
    let avatar: Element<'_, Message, Theme, iced::Renderer> = match &state.auth {
        AuthState::Authenticated(user) => container(
            text(user.initials())
                .font(icons::FONT_BOLD)
                .size(16)
                .color(Color::BLACK),
        )
        .width(44)
        .height(44)
        .align_x(alignment::Horizontal::Center)
        .align_y(alignment::Vertical::Center)
        .style(theme::neo_container(theme::NeoContainer {
            is_surface: true,
            has_shadow: false,
            theme: AppTheme::Light,
        }))
        .into(),
        AuthState::Unauthenticated => container(
            icons::render_svg(&icons::HANDLE_USER, muted_color)
                .width(Length::Fixed(20.0))
                .height(Length::Fixed(20.0)),
        )
        .width(44)
        .height(44)
        .align_x(alignment::Horizontal::Center)
        .align_y(alignment::Vertical::Center)
        .style(theme::neo_container(theme::NeoContainer {
            is_surface: true,
            has_shadow: false,
            theme: app_theme,
        }))
        .into(),
    };

    // Card body text — name + email when authenticated, a "Sign in" label
    // otherwise.
    let card_body: Element<'_, Message, Theme, iced::Renderer> = match &state.auth {
        AuthState::Authenticated(user) => column![
            text(user.display_name.clone())
                .font(icons::FONT_BOLD)
                .size(14)
                .color(text_color),
            text(user.email.clone())
                .font(icons::FONT_REGULAR)
                .size(11)
                .color(muted_color)
        ]
        .spacing(2)
        .into(),
        AuthState::Unauthenticated => column![
            text("Sign in")
                .font(icons::FONT_BOLD)
                .size(14)
                .color(text_color),
            text("Tap to sign in with Google")
                .font(icons::FONT_REGULAR)
                .size(11)
                .color(muted_color)
        ]
        .spacing(2)
        .into(),
    };

    // The whole card is the tap target — a `button::text`-styled surface so it
    // reads as one selectable row (the `NeoContainer` below carries the chrome).
    let card_trigger = button(
        row![
            avatar,
            space::horizontal().width(Length::Fixed(12.0)),
            card_body
        ]
        .align_y(alignment::Alignment::Center),
    )
    .width(Length::Fill)
    .padding(0)
    .style(button::text)
    .on_press(Message::HistoryToggleProfileMenu);

    let card = container(card_trigger)
        .width(Length::Fill)
        .padding(10)
        .style(theme::neo_container(theme::NeoContainer {
            is_surface: true,
            has_shadow: false,
            theme: app_theme,
        }));

    // The popover floats the Login/Logout + Settings menu above the card.
    // `on_close` flips `profile_menu_open` on an outside-click so tapping the
    // card or another control dismisses it.
    let card_with_popover: Element<'_, Message, Theme, iced::Renderer> =
        Popover::new(card.into(), move || {
            if !state.profile_menu_open {
                return None;
            }
            Some(profile_popover_menu(&state.auth, app_theme).into())
        })
        .position(Position::Top)
        .on_close(Message::HistoryToggleProfileMenu)
        .into();

    // Pad the footer so the card has breathing room from the sidebar's bottom
    // and side edges.
    container(card_with_popover)
        .width(Length::Fill)
        .padding(10)
        .into()
}

/// The menu that floats over the profile card. Contains two icon-led rows:
/// the auth action (Login when unauthenticated, Logout when authenticated)
/// and a "Settings" row that navigates to the settings screen. One action per
/// row (DESIGN §7.1 hierarchy) — the card's own tap toggles the popover, each
/// row's tap performs its action.
fn profile_popover_menu(
    auth: &AuthState,
    app_theme: AppTheme,
) -> container::Container<'_, Message, Theme, iced::Renderer> {
    let is_dark = app_theme == AppTheme::Dark;
    let text_color = if is_dark {
        theme::DARK_TEXT
    } else {
        theme::LIGHT_TEXT
    };

    let (auth_label, auth_icon, auth_msg) = match auth {
        AuthState::Unauthenticated => ("Log in", &icons::HANDLE_LOG_IN, Message::HistorySignIn),
        AuthState::Authenticated(_) => ("Log out", &icons::HANDLE_LOG_OUT, Message::HistorySignOut),
    };

    let menu = column![
        menu_row(
            "Settings",
            &icons::HANDLE_SETTINGS,
            Message::NavigateTo(crate::Screen::Settings),
            text_color,
            app_theme
        ),
        menu_row(auth_label, auth_icon, auth_msg, text_color, app_theme)
    ]
    .spacing(2)
    .width(Length::Fill);

    container(menu)
        .padding(6)
        .width(Length::Fixed(180.0))
        .style(theme::neo_dropdown(theme::NeoDropdown { theme: app_theme }))
}

/// One icon-led row inside the profile popover menu. Leading icon in accent color
/// plus bold label, full-width non-accent `NeoButton` so the rows read as a
/// single stacked menu inside the `NeoDropdown` container.
fn menu_row<'a>(
    label: &'a str,
    icon_handle: &'a iced::widget::svg::Handle,
    on_press: Message,
    text_color: Color,
    app_theme: AppTheme,
) -> button::Button<'a, Message, Theme, iced::Renderer> {
    button(
        row![
            icons::render_svg(icon_handle, theme::COLOR_ACCENT)
                .width(Length::Fixed(16.0))
                .height(Length::Fixed(16.0)),
            text(label)
                .font(icons::FONT_BOLD)
                .size(13)
                .color(text_color)
        ]
        .spacing(10)
        .align_y(alignment::Alignment::Center),
    )
    .width(Length::Fill)
    .padding(10)
    .style(theme::neo_button(theme::NeoButton {
        is_accent: false,
        has_shadow: false,
        theme: app_theme,
    }))
    .on_press(on_press)
}

fn truncate_with_ellipsis(text: &str, max_chars: usize) -> String {
    let char_count = text.chars().count();
    if char_count <= max_chars {
        return text.to_owned();
    }

    let keep = max_chars.saturating_sub(1);
    format!("{}…", text.chars().take(keep).collect::<String>())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn truncate_with_ellipsis_leaves_short_text_unchanged() {
        assert_eq!(truncate_with_ellipsis("Short chat", 22), "Short chat");
    }

    #[test]
    fn truncate_with_ellipsis_adds_ellipsis_for_long_text() {
        let long = "Multimodal Gemini API prompt engineering";
        assert_eq!(truncate_with_ellipsis(long, 22), "Multimodal Gemini API…");
    }

    #[test]
    fn clamp_sidebar_width_respects_bounds() {
        assert_eq!(HistoryState::clamp_sidebar_width(100.0), MIN_SIDEBAR_WIDTH);
        assert_eq!(HistoryState::clamp_sidebar_width(900.0), MAX_SIDEBAR_WIDTH);
        assert_eq!(HistoryState::clamp_sidebar_width(300.0), 300.0);
    }

    #[test]
    fn apply_sidebar_resize_delta_clamps_result() {
        let mut state = HistoryState::new();
        assert_eq!(state.sidebar_width, MAX_SIDEBAR_WIDTH);
        state.apply_sidebar_resize_delta(-50.0);
        assert_eq!(state.sidebar_width, MAX_SIDEBAR_WIDTH - 50.0);
        state.apply_sidebar_resize_delta(500.0);
        assert_eq!(state.sidebar_width, MAX_SIDEBAR_WIDTH);
    }

    #[test]
    fn new_history_state_seeds_authenticated_with_seed_profile() {
        let state = HistoryState::new();
        match &state.auth {
            AuthState::Authenticated(user) => {
                assert_eq!(user.display_name, "Sean Dev");
                assert_eq!(user.email, "sean@browntowndev.com");
            }
            other => panic!("expected seeded Authenticated, got {other:?}"),
        }
        assert!(!state.profile_menu_open);
    }

    #[test]
    fn user_profile_initials_take_first_letter_of_first_two_words() {
        let user = UserProfile::seed();
        assert_eq!(user.initials(), "SD");
    }

    #[test]
    fn user_profile_initials_handle_single_word_name() {
        let user = UserProfile {
            uid: "u".into(),
            display_name: "Sean".into(),
            email: "s@b".into(),
            photo_url: None,
        };
        assert_eq!(user.initials(), "S");
    }

    #[test]
    fn user_profile_initials_uppercase_lowercase_input() {
        let user = UserProfile {
            uid: "u".into(),
            display_name: "sean dev".into(),
            email: "s@b".into(),
            photo_url: None,
        };
        assert_eq!(user.initials(), "SD");
    }
}

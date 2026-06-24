//! Model-download screen — desktop port of the Android `ModelDownloadScreen`.
//! Renders a status page showing which model files need to be downloaded,
//! overall progress, per-file progress cards, and controls (Skip / Continue).
//! Follows MVU: state lives in [`DownloadState`], side effects
//! are dispatched from `main.rs::update` via `Message`, and [`view`] is a pure
//! layout mapping.
//
// This screen is checked in as part of the BYOK checkpoint but is not yet wired
// into the app shell (`Screen::Download`, `DownloadState` ownership, and the
// download pipeline are pending). All items are `allow(dead_code)` until that
// routing lands so the `-D warnings` workspace lint does not fail the build on
// intentionally-unwired checkpoint code.
#![allow(dead_code)]

use crate::icons;
use crate::theme::{self, AppTheme};
use crate::Message;
use iced::widget::{button, column, container, progress_bar, row, scrollable, space, text, Column};
use iced::{alignment, Color, Element, Length, Theme};

/// Top-level download status (mirrors Android `DownloadStatus`).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DownloadStatus {
    /// Scanning the models directory to see what's missing.
    Checking,
    /// Actively downloading one or more files.
    Downloading,
    /// All downloads complete — ready to proceed.
    Ready,
    /// An error occurred.
    Error,
}

/// Per-file progress card state.
#[derive(Debug, Clone)]
pub struct FileProgressItem {
    pub display_name: String,
    pub downloaded: u64,
    pub total: u64,
    pub status: FileItemStatus,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FileItemStatus {
    Queued,
    Downloading,
    Complete,
    Failed,
}

/// State for the download screen. Owned by `EnclaveApp` when `Screen::Download`
/// is active.
#[derive(Debug, Clone)]
pub struct DownloadState {
    pub status: DownloadStatus,
    pub items: Vec<FileProgressItem>,
    pub error_message: Option<String>,
}

impl DownloadState {
    pub fn new() -> Self {
        Self {
            status: DownloadStatus::Checking,
            items: Vec::new(),
            error_message: None,
        }
    }

    /// Overall progress as (downloaded, total) across all items.
    pub fn overall_progress(&self) -> (u64, u64) {
        let downloaded: u64 = self.items.iter().map(|i| i.downloaded).sum();
        let total: u64 = self.items.iter().map(|i| i.total).sum();
        (downloaded, total)
    }

    /// Count of completed items.
    pub fn completed_count(&self) -> usize {
        self.items
            .iter()
            .filter(|i| i.status == FileItemStatus::Complete)
            .count()
    }

    /// Derive batch status after all download tasks finish — does not navigate.
    pub fn status_after_batch(&self) -> DownloadStatus {
        if self
            .items
            .iter()
            .any(|i| i.status == FileItemStatus::Failed)
        {
            DownloadStatus::Error
        } else if self.items.is_empty()
            || self
                .items
                .iter()
                .all(|i| i.status == FileItemStatus::Complete)
        {
            DownloadStatus::Ready
        } else {
            DownloadStatus::Downloading
        }
    }
}

impl Default for DownloadState {
    fn default() -> Self {
        Self::new()
    }
}

pub fn view(
    state: &DownloadState,
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

    // --- Header ---
    let header_title = match state.status {
        DownloadStatus::Checking => "Checking on the crew…",
        DownloadStatus::Downloading => "Downloading Models",
        DownloadStatus::Ready => "Models Ready",
        DownloadStatus::Error => "Download Error",
    };

    let header = text(header_title)
        .font(icons::FONT_BOLD)
        .size(24)
        .color(text_color);

    // --- Overall progress ---
    let (downloaded, total) = state.overall_progress();
    let pct = if total > 0 {
        (downloaded as f32 / total as f32).clamp(0.0, 1.0)
    } else {
        0.0
    };

    let progress_bar_widget =
        container(progress_bar(0.0..=1.0, pct).style(theme::neo_progress_bar(app_theme)))
            .width(Length::Fill)
            .height(Length::Fixed(8.0));

    let count_text = if state.items.is_empty() {
        text("No models to download")
            .font(icons::FONT_REGULAR)
            .size(13)
            .color(muted_color)
    } else {
        text(format!(
            "{} of {} models complete · {:.0}%",
            state.completed_count(),
            state.items.len(),
            pct * 100.0
        ))
        .font(icons::FONT_REGULAR)
        .size(13)
        .color(muted_color)
    };

    // --- Per-file cards ---
    let mut file_list = Column::new().spacing(8);

    for item in &state.items {
        let card = file_progress_card(item, app_theme);
        file_list = file_list.push(card);
    }

    let scroll = scrollable(file_list)
        .width(Length::Fill)
        .height(Length::Fill);

    // --- Error banner ---
    let error_banner: Option<Element<'_, Message, Theme, iced::Renderer>> =
        state.error_message.as_ref().map(|msg| {
            container(
                text(msg)
                    .font(icons::FONT_REGULAR)
                    .size(13)
                    .color(Color::from_rgb(0.85, 0.27, 0.27)),
            )
            .width(Length::Fill)
            .padding(12)
            .style(theme::neo_container(theme::NeoContainer {
                is_surface: true,
                has_shadow: false,
                theme: app_theme,
            }))
            .into()
        });

    // --- Controls ---
    let mut controls_row = row![]
        .spacing(12)
        .width(Length::Fill)
        .align_y(alignment::Alignment::Center);

    let continue_btn = button(
        text("Continue")
            .font(icons::FONT_BOLD)
            .size(14)
            .color(Color::BLACK),
    )
    .width(Length::Fixed(160.0))
    .padding(14)
    .style(theme::neo_button(theme::NeoButton {
        is_accent: true,
        has_shadow: true,
        theme: app_theme,
    }));

    let continue_btn = if state.status == DownloadStatus::Ready {
        continue_btn.on_press(Message::DownloadContinue)
    } else {
        continue_btn
    };

    let skip_btn = button(
        text("Skip for now")
            .font(icons::FONT_REGULAR)
            .size(14)
            .color(text_color),
    )
    .width(Length::Fixed(140.0))
    .padding(14)
    .style(theme::neo_button(theme::NeoButton {
        is_accent: false,
        has_shadow: false,
        theme: app_theme,
    }))
    .on_press(Message::DownloadContinue);

    controls_row = controls_row.push(space::horizontal().width(Length::Fill));
    controls_row = controls_row.push(skip_btn);
    controls_row = controls_row.push(continue_btn);

    // --- Compose body ---
    let mut body = column![
        header,
        space::vertical().height(Length::Fixed(16.0)),
        progress_bar_widget,
        space::vertical().height(Length::Fixed(8.0)),
        count_text,
        space::vertical().height(Length::Fixed(20.0)),
        scroll,
        space::vertical().height(Length::Fixed(12.0)),
    ]
    .spacing(0)
    .width(Length::Fill)
    .height(Length::Fill);

    if let Some(err) = error_banner {
        body = body
            .push(err)
            .push(space::vertical().height(Length::Fixed(12.0)));
    }

    body = body.push(controls_row);

    let card = container(body)
        .width(Length::Fixed(640.0))
        .height(Length::Fill)
        .padding(28)
        .style(theme::neo_container(theme::NeoContainer {
            is_surface: true,
            has_shadow: true,
            theme: app_theme,
        }));

    let centered = container(card)
        .width(Length::Fill)
        .height(Length::Fill)
        .align_x(alignment::Horizontal::Center);

    crate::screens::layout::screen_shell(centered, app_theme)
}

fn file_progress_card(
    item: &FileProgressItem,
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

    let status_icon = match item.status {
        FileItemStatus::Queued => "○",
        FileItemStatus::Downloading => "↓",
        FileItemStatus::Complete => "✓",
        FileItemStatus::Failed => "✗",
    };
    let status_color = match item.status {
        FileItemStatus::Complete => Color::from_rgb(0.2, 0.7, 0.3),
        FileItemStatus::Failed => Color::from_rgb(0.85, 0.27, 0.27),
        _ => theme::COLOR_ACCENT,
    };

    let name_row = row![
        text(status_icon)
            .font(icons::FONT_BOLD)
            .size(16)
            .color(status_color),
        space::horizontal().width(Length::Fixed(12.0)),
        text(&item.display_name)
            .font(icons::FONT_BOLD)
            .size(14)
            .color(text_color),
        space::horizontal().width(Length::Fill),
    ]
    .align_y(alignment::Alignment::Center);

    let pct = if item.total > 0 {
        (item.downloaded as f32 / item.total as f32).clamp(0.0, 1.0)
    } else {
        0.0
    };

    let file_progress =
        container(progress_bar(0.0..=1.0, pct).style(theme::neo_progress_bar(app_theme)))
            .width(Length::Fill)
            .height(Length::Fixed(6.0));

    let size_text = text(format!(
        "{} / {}",
        format_bytes(item.downloaded),
        format_bytes(item.total)
    ))
    .font(icons::FONT_REGULAR)
    .size(11)
    .color(muted_color);

    container(
        column![
            name_row,
            space::vertical().height(Length::Fixed(8.0)),
            file_progress,
            space::vertical().height(Length::Fixed(4.0)),
            size_text
        ]
        .spacing(0),
    )
    .width(Length::Fill)
    .padding(12)
    .style(theme::neo_container(theme::NeoContainer {
        is_surface: true,
        has_shadow: false,
        theme: app_theme,
    }))
    .into()
}

fn format_bytes(bytes: u64) -> String {
    if bytes >= 1_000_000_000 {
        format!("{:.1} GB", bytes as f64 / 1_073_741_824.0)
    } else if bytes >= 1_000_000 {
        format!("{:.1} MB", bytes as f64 / 1_048_576.0)
    } else if bytes >= 1_000 {
        format!("{:.1} KB", bytes as f64 / 1024.0)
    } else {
        format!("{bytes} B")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn new_state_starts_checking() {
        let state = DownloadState::new();
        assert_eq!(state.status, DownloadStatus::Checking);
        assert!(state.items.is_empty());
        assert!(state.error_message.is_none());
    }

    #[test]
    fn overall_progress_sums_items() {
        let mut state = DownloadState::new();
        state.items.push(FileProgressItem {
            display_name: "A".into(),
            downloaded: 100,
            total: 200,
            status: FileItemStatus::Downloading,
        });
        state.items.push(FileProgressItem {
            display_name: "B".into(),
            downloaded: 50,
            total: 100,
            status: FileItemStatus::Queued,
        });
        let (dl, total) = state.overall_progress();
        assert_eq!(dl, 150);
        assert_eq!(total, 300);
    }

    #[test]
    fn completed_count_counts_complete_only() {
        let mut state = DownloadState::new();
        state.items.push(FileProgressItem {
            display_name: "A".into(),
            downloaded: 200,
            total: 200,
            status: FileItemStatus::Complete,
        });
        state.items.push(FileProgressItem {
            display_name: "B".into(),
            downloaded: 0,
            total: 100,
            status: FileItemStatus::Queued,
        });
        assert_eq!(state.completed_count(), 1);
    }

    #[test]
    fn format_bytes_formats_correctly() {
        assert_eq!(format_bytes(500), "500 B");
        assert_eq!(format_bytes(1024), "1.0 KB");
        assert_eq!(format_bytes(1_048_576), "1.0 MB");
        assert_eq!(format_bytes(1_073_741_824), "1.0 GB");
    }

    #[test]
    fn view_builds_in_all_states() {
        let mut state = DownloadState::new();
        state.items.push(FileProgressItem {
            display_name: "Test Model".into(),
            downloaded: 50,
            total: 100,
            status: FileItemStatus::Downloading,
        });
        for status in [
            DownloadStatus::Checking,
            DownloadStatus::Downloading,
            DownloadStatus::Ready,
            DownloadStatus::Error,
        ] {
            state.status = status;
            let _ = view(&state, AppTheme::Dark);
            let _ = view(&state, AppTheme::Light);
        }
    }

    #[test]
    fn view_builds_with_error_message() {
        let mut state = DownloadState::new();
        state.status = DownloadStatus::Error;
        state.error_message = Some("Network timeout".into());
        let _ = view(&state, AppTheme::Dark);
    }

    #[test]
    fn status_after_batch_marks_error_when_any_file_failed() {
        let mut state = DownloadState::new();
        state.items.push(FileProgressItem {
            display_name: "A".into(),
            downloaded: 100,
            total: 100,
            status: FileItemStatus::Complete,
        });
        state.items.push(FileProgressItem {
            display_name: "B".into(),
            downloaded: 0,
            total: 100,
            status: FileItemStatus::Failed,
        });
        assert_eq!(state.status_after_batch(), DownloadStatus::Error);
    }

    #[test]
    fn status_after_batch_ready_when_all_complete() {
        let mut state = DownloadState::new();
        state.items.push(FileProgressItem {
            display_name: "A".into(),
            downloaded: 100,
            total: 100,
            status: FileItemStatus::Complete,
        });
        assert_eq!(state.status_after_batch(), DownloadStatus::Ready);
    }
}

//! Chat statistics bottom sheet for the desktop chat screen.
//!
//! Desktop analogue of the Android
//! `feature/chat/.../stats/ChatStatsBottomSheet.kt` (itself rendered via
//! `NeoTelemetryStatsSheet`): a modal bottom sheet surfaced from the input
//! bar's circular progress control. Sections mirror the Android layout —
//! Summary, By provider, By model — populated by [`sample_stats`] with seeded
//! demo telemetry (the desktop app is a UI prototype with mock data, matching
//! the seeded `chat_1` assistant demo in `state.rs`).

use iced::widget::{Column, button, column, container, row, scrollable, space, text};
use iced::{Color, Element, Length, Padding, Theme, alignment};

use crate::Message;
use crate::icons;
use crate::theme::{self, AppTheme};
use crate::widgets::BottomSheet;

/// A label/value telemetry metric row (Summary section). Mirrors the Android
/// `SummaryMetricRowUi`.
#[derive(Debug, Clone)]
pub struct SummaryMetricRow {
    pub label: String,
    pub value: String,
}

/// A per-provider telemetry breakdown row (By provider section). Mirrors the
/// Android `ProviderBreakdownRowUi`.
#[derive(Debug, Clone)]
pub struct ProviderBreakdownRow {
    pub label: String,
    pub cost_label: String,
    pub turn_count: u32,
    pub request_count: Option<u32>,
}

/// A per-model telemetry breakdown row (By model section). Mirrors the Android
/// `ModelBreakdownRowUi`.
#[derive(Debug, Clone)]
pub struct ModelBreakdownRow {
    pub label: String,
    pub cost_label: String,
    pub output_tokens: u32,
}

/// Aggregated chat telemetry backing the stats sheet. Mirrors the Android
/// `ChatStatsSheetUiState` slice (title + the three row sets).
#[derive(Debug, Clone)]
pub struct ChatStats {
    pub title: String,
    pub summary_rows: Vec<SummaryMetricRow>,
    pub provider_breakdown_rows: Vec<ProviderBreakdownRow>,
    pub model_breakdown_rows: Vec<ModelBreakdownRow>,
}

impl ChatStats {
    /// Seeded demo telemetry for a chat, mirroring the row set the Android
    /// `ChatTelemetryUiMapper.mapChatStats` produces. The desktop app is a UI
    /// prototype (no live inference pipeline wired in), so this is the same
    /// kind of seeded demo content as `chat_1_assistant_demo.md`.
    pub fn sample() -> Self {
        Self {
            title: "Chat statistics".to_string(),
            summary_rows: vec![
                SummaryMetricRow {
                    label: "Total cost".into(),
                    value: "$0.000000".into(),
                },
                SummaryMetricRow {
                    label: "Average TTFT".into(),
                    value: "Not available".into(),
                },
                SummaryMetricRow {
                    label: "Average output speed".into(),
                    value: "Not available".into(),
                },
                SummaryMetricRow {
                    label: "Input tokens".into(),
                    value: "0".into(),
                },
                SummaryMetricRow {
                    label: "Total prompt tokens (all API calls)".into(),
                    value: "0".into(),
                },
                SummaryMetricRow {
                    label: "Output tokens".into(),
                    value: "0".into(),
                },
                SummaryMetricRow {
                    label: "Reasoning tokens".into(),
                    value: "0".into(),
                },
                SummaryMetricRow {
                    label: "Cache read tokens".into(),
                    value: "0".into(),
                },
                SummaryMetricRow {
                    label: "Total cache read (all API calls)".into(),
                    value: "0".into(),
                },
                SummaryMetricRow {
                    label: "Cache write tokens".into(),
                    value: "0".into(),
                },
                SummaryMetricRow {
                    label: "Total cache write (all API calls)".into(),
                    value: "0".into(),
                },
                SummaryMetricRow {
                    label: "Cache hit rate".into(),
                    value: "Not available".into(),
                },
            ],
            provider_breakdown_rows: vec![ProviderBreakdownRow {
                label: "On-Device (LiteRT)".into(),
                cost_label: "$0.000000".into(),
                turn_count: 1,
                request_count: None,
            }],
            model_breakdown_rows: vec![ModelBreakdownRow {
                label: "Gemma 4 E4B".into(),
                cost_label: "$0.000000".into(),
                output_tokens: 0,
            }],
        }
    }
}

/// Wrap `inner` (the full chat view) in a [`BottomSheet`] overlay that mounts
/// the stats sheet when `stats_open` is true. The inline anchor is `inner`
/// itself — the sheet floats over it as a viewport-wide overlay, so this can
/// wrap the outermost chat element without changing the underlying layout.
pub fn wrap_stats_sheet<'a>(
    inner: Element<'a, Message, Theme, iced::Renderer>,
    stats: &'a ChatStats,
    stats_open: bool,
    app_theme: AppTheme,
) -> Element<'a, Message, Theme, iced::Renderer> {
    BottomSheet::new(inner, move || {
        if !stats_open {
            return None;
        }
        Some(view(stats, app_theme))
    })
    .on_close(Message::ChatToggleStatsSheet)
    .into()
}

/// Render the stats sheet content. A header row (title + close affordance)
/// followed by the Summary / By provider / By model sections inside a scrollable
/// body, all on a [`theme::NeoBottomSheet`] surface.
fn view(
    stats: &ChatStats,
    app_theme: AppTheme,
) -> Element<'static, Message, Theme, iced::Renderer> {
    let is_dark = app_theme == AppTheme::Dark;
    let muted = if is_dark {
        theme::DARK_TEXT_MUTED
    } else {
        theme::LIGHT_TEXT_MUTED
    };

    let header = row![
        text(stats.title.clone())
            .font(icons::FONT_BOLD)
            .size(16)
            .color(theme::COLOR_ACCENT),
        space::horizontal().width(Length::Fill),
        button(
            container(icons::render_svg(&icons::HANDLE_CLOSE, muted))
                .width(Length::Fill)
                .height(Length::Fill)
                .align_x(alignment::Horizontal::Center)
                .align_y(alignment::Vertical::Center),
        )
        .padding(4)
        .width(Length::Fixed(28.0))
        .height(Length::Fixed(28.0))
        .style(theme::neo_icon_button(theme::NeoIconButton {
            theme: app_theme
        }))
        .on_press(Message::ChatToggleStatsSheet),
    ]
    .align_y(alignment::Alignment::Center)
    .padding(Padding {
        top: 4.0,
        right: 16.0,
        bottom: 8.0,
        left: 16.0,
    })
    .width(Length::Fill);

    let mut sections: Vec<Element<Message, Theme, iced::Renderer>> = Vec::new();
    sections.push(section_title("Summary"));
    sections.push(metric_panel(
        app_theme,
        stats
            .summary_rows
            .iter()
            .map(|r| metric_row(&r.label, &r.value, muted)),
    ));

    if !stats.provider_breakdown_rows.is_empty() {
        sections.push(section_title("By provider"));
        sections.push(metric_panel(
            app_theme,
            stats
                .provider_breakdown_rows
                .iter()
                .map(|r| breakdown_row_provider(r, muted)),
        ));
    }

    if !stats.model_breakdown_rows.is_empty() {
        sections.push(section_title("By model"));
        sections.push(metric_panel(
            app_theme,
            stats
                .model_breakdown_rows
                .iter()
                .map(|r| breakdown_row_model(r, muted)),
        ));
    }

    let body = column_with(sections)
        .padding(Padding {
            top: 0.0,
            right: 16.0,
            bottom: 16.0,
            left: 16.0,
        })
        .spacing(4)
        .width(Length::Fill);

    let scroller = scrollable(body)
        .height(Length::Fixed(420.0))
        .style(theme::neo_scrollable(theme::NeoScrollable {
            theme: app_theme,
        }));

    container(column![header, scroller])
        .width(Length::Fill)
        .style(theme::neo_bottom_sheet(theme::NeoBottomSheet {
            theme: app_theme,
        }))
        .into()
}

fn section_title(label: &'static str) -> Element<'static, Message, Theme, iced::Renderer> {
    text(label).font(icons::FONT_SEMIBOLD).size(13).into()
}

/// A bordered panel grouping metric rows — the desktop analogue of the Android
/// `NeoTelemetryMetricPanel` (`NeoPanel` with a neo border). Built from an
/// iterator so the call sites read like the Compose `forEach` over the row sets.
fn metric_panel(
    app_theme: AppTheme,
    rows: impl Iterator<Item = Element<'static, Message, Theme, iced::Renderer>>,
) -> Element<'static, Message, Theme, iced::Renderer> {
    let mut col = Column::<Message, Theme, iced::Renderer>::new().width(Length::Fill);
    for row in rows {
        col = col.push(row);
    }
    container(col)
        .width(Length::Fill)
        .padding([4, 12])
        .style(theme::neo_container(theme::NeoContainer {
            is_surface: false,
            has_shadow: false,
            theme: app_theme,
        }))
        .into()
}

fn metric_row(
    label: &str,
    value: &str,
    muted: Color,
) -> Element<'static, Message, Theme, iced::Renderer> {
    row![
        text(label.to_string())
            .font(icons::FONT_REGULAR)
            .size(12)
            .color(muted)
            .width(Length::Fill),
        text(value.to_string()).font(icons::FONT_MEDIUM).size(12),
    ]
    .padding([6, 0])
    .align_y(alignment::Alignment::Center)
    .width(Length::Fill)
    .into()
}

fn breakdown_row_provider(
    row: &ProviderBreakdownRow,
    muted: Color,
) -> Element<'static, Message, Theme, iced::Renderer> {
    let request_suffix = row
        .request_count
        .map(|c| format!(" · {c} requests"))
        .unwrap_or_default();
    let secondary = format!(
        "{} turn{}{request_suffix}",
        row.turn_count,
        if row.turn_count == 1 { "" } else { "s" }
    );
    breakdown_row(row.label.clone(), row.cost_label.clone(), secondary, muted)
}

fn breakdown_row_model(
    row: &ModelBreakdownRow,
    muted: Color,
) -> Element<'static, Message, Theme, iced::Renderer> {
    let secondary = format!("{} output tokens", row.output_tokens);
    breakdown_row(row.label.clone(), row.cost_label.clone(), secondary, muted)
}

fn breakdown_row(
    primary: String,
    cost: String,
    secondary: String,
    muted: Color,
) -> Element<'static, Message, Theme, iced::Renderer> {
    column![
        row![
            text(primary)
                .font(icons::FONT_MEDIUM)
                .size(12)
                .width(Length::Fill),
            text(cost).font(icons::FONT_BOLD).size(12),
        ]
        .align_y(alignment::Alignment::Center)
        .width(Length::Fill),
        text(secondary)
            .font(icons::FONT_REGULAR)
            .size(11)
            .color(muted),
    ]
    .padding([6, 0])
    .spacing(2)
    .width(Length::Fill)
    .into()
}

/// Like `column!` but from a `Vec`, so `view` can conditionally accumulate
/// sections without borrow-checker churn on the macro's tuple expansion.
fn column_with(
    elements: Vec<Element<'static, Message, Theme, iced::Renderer>>,
) -> Column<'static, Message, Theme, iced::Renderer> {
    let mut col = Column::<Message, Theme, iced::Renderer>::new();
    for el in elements {
        col = col.push(el);
    }
    col
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sample_stats_has_summary_provider_and_model_sections() {
        let stats = ChatStats::sample();
        assert_eq!(stats.title, "Chat statistics");
        // Mirrors the Android mapper's fixed summary row set.
        assert!(stats.summary_rows.len() >= 12);
        assert!(
            stats.summary_rows.iter().any(|r| r.label == "Total cost"),
            "summary should include Total cost"
        );
        assert_eq!(stats.provider_breakdown_rows.len(), 1);
        assert_eq!(stats.model_breakdown_rows.len(), 1);
    }

    #[test]
    fn sample_provider_breakdown_request_suffix_is_optional() {
        let stats = ChatStats::sample();
        let provider = &stats.provider_breakdown_rows[0];
        // On-device LiteRT is a single local turn — no multi-request suffix.
        assert_eq!(provider.turn_count, 1);
        assert!(provider.request_count.is_none());
    }
}

use iced::widget::{Column, button, column, container, row, scrollable, space, text, text_editor};
use iced::{Element, Length, Padding, Theme, alignment};

use super::state::{ChatState, PermissionMode};
use crate::Message;
use crate::icons;
use crate::screens::layout::{dropdown_menu_row, icon_button};
use crate::theme::{self, AppTheme};

/// Build the shared input bar: a single solid `NeoInputBar` container holding
/// the auto-expanding text area on top and the action row below.
pub fn view(
    state: &ChatState,
    app_theme: AppTheme,
    text_color: iced::Color,
) -> Element<'_, Message, Theme, iced::Renderer> {
    let editor = text_editor(&state.input_content)
        .on_action(Message::ChatEditorAction)
        .font(icons::FONT_REGULAR)
        .padding(8)
        .height(Length::Shrink)
        .style(theme::neo_text_editor(theme::NeoTextEditor {
            theme: app_theme,
        }));

    let scroller = scrollable(editor).style(theme::neo_scrollable(theme::NeoScrollable {
        theme: app_theme,
    }));
    let editor_host = container(scroller).max_height(176.0);

    let plus_trigger = icon_button(
        &icons::HANDLE_ADD,
        text_color,
        app_theme,
        Message::ChatToggleMenu,
    );
    let plus_btn: Element<Message, Theme, iced::Renderer> =
        crate::widgets::Popover::new(plus_trigger.into(), move || {
            if !state.menu_open {
                return None;
            }
            let attach_item = button(
                row![
                    icons::render_svg(&icons::HANDLE_ATTACH, text_color)
                        .width(Length::Fixed(16.0))
                        .height(Length::Fixed(16.0)),
                    text("Attach File").font(icons::FONT_REGULAR).size(13),
                ]
                .spacing(8)
                .align_y(alignment::Alignment::Center),
            )
            .padding(8)
            .width(Length::Fill)
            .style(theme::neo_button(theme::NeoButton {
                is_accent: false,
                has_shadow: false,
                theme: app_theme,
            }))
            .on_press(Message::ChatFilePressed);

            Some(
                container(column![attach_item].spacing(2).width(Length::Fill))
                    .padding(6)
                    .width(Length::Fixed(160.0))
                    .style(theme::neo_dropdown(theme::NeoDropdown { theme: app_theme }))
                    .into(),
            )
        })
        .position(crate::widgets::Position::Top)
        .on_close(Message::ChatToggleMenu)
        .into();

    let perm_label = state.permission_mode.label();
    let perm_trigger = dropdown_trigger(
        perm_label,
        &icons::HANDLE_CHEVRON_DOWN,
        text_color,
        app_theme,
        state.perm_picker_open,
        Message::ChatTogglePermPicker,
    );
    let perm_btn: Element<Message, Theme, iced::Renderer> =
        crate::widgets::Popover::new(perm_trigger.into(), move || {
            if !state.perm_picker_open {
                return None;
            }
            let items: Column<Message, Theme, iced::Renderer> = PermissionMode::ALL.iter().fold(
                Column::new().spacing(2).width(Length::Fill),
                |col, mode| {
                    let is_selected = *mode == state.permission_mode;
                    let label = text(mode.label()).font(icons::FONT_REGULAR).size(13).color(
                        if is_selected {
                            theme::COLOR_ACCENT
                        } else {
                            text_color
                        },
                    );
                    let row_btn =
                        dropdown_menu_row(label, app_theme, Message::ChatSelectPermission(*mode));
                    col.push(row_btn)
                },
            );
            Some(
                container(items)
                    .padding(6)
                    .width(Length::Fixed(180.0))
                    .style(theme::neo_dropdown(theme::NeoDropdown { theme: app_theme }))
                    .into(),
            )
        })
        .position(crate::widgets::Position::Top)
        .on_close(Message::ChatTogglePermPicker)
        .into();

    // Circular progress ring bound to `context_pct`. Clicking it opens the
    // chat-stats bottom sheet (the desktop analogue of the Android top-bar
    // "Chat stats" button — same Summary / By provider / By model content).
    let context_btn = button(
        container(
            icons::render_circular_progress(state.context_pct as f32, theme::COLOR_ACCENT)
                .width(Length::Fixed(24.0))
                .height(Length::Fixed(24.0)),
        )
        .width(Length::Fill)
        .height(Length::Fill)
        .align_x(alignment::Horizontal::Center)
        .align_y(alignment::Vertical::Center),
    )
    .padding(8)
    .width(Length::Fixed(40.0))
    .height(Length::Fixed(40.0))
    .style(theme::neo_icon_button(theme::NeoIconButton {
        theme: app_theme,
    }))
    .on_press(Message::ChatToggleStatsSheet);

    let model_trigger = dropdown_trigger(
        &state.selected_model,
        &icons::HANDLE_CHEVRON_DOWN,
        text_color,
        app_theme,
        state.model_picker_open,
        Message::ChatToggleModelPicker,
    );
    let model_btn: Element<Message, Theme, iced::Renderer> =
        crate::widgets::Popover::new(model_trigger.into(), move || {
            if !state.model_picker_open {
                return None;
            }
            let mut col = Column::<Message, Theme, iced::Renderer>::new()
                .spacing(2)
                .width(Length::Fill);
            if state.available_models.is_empty() {
                // Empty state: show a single disabled hint row instead of a
                // blank pane (DESKTOP_RULES.md §7.3 "States: empty paths
                // rendered").
                col = col.push(
                    text("No models installed")
                        .font(icons::FONT_REGULAR)
                        .size(13)
                        .color(text_color),
                );
            } else {
                for entry in &state.available_models {
                    let is_selected = state.selected_model_id.as_deref() == Some(&entry.id);
                    let label = text(entry.display_name.as_str())
                        .font(icons::FONT_REGULAR)
                        .size(13)
                        .color(if is_selected {
                            theme::COLOR_ACCENT
                        } else {
                            text_color
                        });
                    let row_btn = dropdown_menu_row(
                        label,
                        app_theme,
                        Message::ChatSelectModel {
                            id: entry.id.clone(),
                            display_name: entry.display_name.clone(),
                        },
                    );
                    col = col.push(row_btn);
                }
            }
            Some(
                container(col)
                    .padding(6)
                    .width(Length::Fixed(200.0))
                    .style(theme::neo_dropdown(theme::NeoDropdown { theme: app_theme }))
                    .into(),
            )
        })
        .position(crate::widgets::Position::Top)
        .on_close(Message::ChatToggleModelPicker)
        .into();

    let send_btn = button(
        container(
            icons::render_svg(&icons::HANDLE_SEND, iced::Color::BLACK)
                .width(Length::Fixed(18.0))
                .height(Length::Fixed(18.0)),
        )
        .width(Length::Fill)
        .height(Length::Fill)
        .align_x(alignment::Horizontal::Center)
        .align_y(alignment::Vertical::Center),
    )
    .padding(10)
    .width(Length::Fixed(44.0))
    .height(Length::Fixed(44.0))
    .style(theme::neo_button(theme::NeoButton {
        is_accent: true,
        has_shadow: true,
        theme: app_theme,
    }))
    .on_press(Message::ChatSendMessage);

    let action_row = row![plus_btn, perm_btn]
        .spacing(8)
        .align_y(alignment::Alignment::Center)
        .push(space::horizontal().width(Length::Fill))
        .push(context_btn)
        .push(model_btn)
        .push(send_btn);

    let bar = column![
        container(editor_host).padding([0, 12]),
        action_row.padding([0, 12])
    ]
    .spacing(8)
    .width(Length::Fill);

    let styled_bar = container(bar)
        .width(Length::FillPortion(6))
        .padding(Padding {
            top: 10.0,
            right: 24.0,
            bottom: 14.0,
            left: 24.0,
        })
        .style(theme::neo_input_bar(theme::NeoInputBar {
            has_shadow: false,
            theme: app_theme,
        }));

    row![
        space::horizontal().width(Length::FillPortion(1)),
        styled_bar,
        space::horizontal().width(Length::FillPortion(1)),
    ]
    .width(Length::Fill)
    .align_y(alignment::Alignment::Center)
    .into()
}

fn dropdown_trigger<'a>(
    label: &str,
    chevron: &'a iced::widget::svg::Handle,
    color: iced::Color,
    app_theme: AppTheme,
    active: bool,
    on_press: Message,
) -> button::Button<'a, Message, Theme, iced::Renderer> {
    let trigger_color = if active { iced::Color::BLACK } else { color };
    button(
        row![
            text(label.to_string())
                .font(icons::FONT_REGULAR)
                .size(14)
                .color(trigger_color),
            icons::render_svg(chevron, trigger_color)
                .width(Length::Fixed(16.0))
                .height(Length::Fixed(16.0)),
        ]
        .spacing(8)
        .align_y(alignment::Alignment::Center),
    )
    .padding([10, 12])
    .height(Length::Fixed(40.0))
    .style(theme::neo_button(theme::NeoButton {
        is_accent: active,
        has_shadow: false,
        theme: app_theme,
    }))
    .on_press(on_press)
}

use iced::widget::{
    button, column, container, horizontal_space, row, scrollable, text, text_editor, Column,
};
use iced::{alignment, Element, Length, Theme};

use super::state::{ChatState, PermissionMode};
use crate::icons;
use crate::screens::layout::{dropdown_menu_row, icon_button};
use crate::theme::{self, AppTheme};
use crate::Message;

/// Build the shared input bar: a single solid `NeoInputBar` container holding
/// the auto-expanding text area on top and the action row below.
pub fn view(
    state: &ChatState,
    app_theme: AppTheme,
    text_color: iced::Color,
) -> Element<'_, Message, Theme, iced::Renderer> {
    let is_dark = app_theme == AppTheme::Dark;
    let muted_color = if is_dark {
        theme::DARK_TEXT_MUTED
    } else {
        theme::LIGHT_TEXT_MUTED
    };

    // --- Row 1: auto-expanding text area ---
    // The editor shrinks-to-content so it starts at a single line and grows as
    // the user types. The scrollable has no fixed height (it follows the
    // child), and the outer container clamps the visible region to ~8 lines
    // (176px). Once the content exceeds the clamp the scrollbar engages.
    let editor = text_editor(&state.input_content)
        .on_action(Message::ChatEditorAction)
        .font(icons::FONT_REGULAR)
        .padding(8)
        .height(Length::Shrink)
        .style(iced::theme::TextEditor::Custom(Box::new(
            theme::NeoTextEditor { theme: app_theme },
        )));

    // 8 lines * ~22px line height ≈ 176px before the scrollbar engages.
    let scroller = scrollable(editor).style(iced::theme::Scrollable::Custom(Box::new(
        theme::NeoScrollable { theme: app_theme },
    )));
    let editor_host = container(scroller).max_height(176.0);

    // --- Row 2: action row ---
    // Each menu trigger is wrapped in a floating `Popover` so the menu overlays
    // the UI instead of pushing the bar's layout. The closure returns the menu
    // when the flag is set, `None` otherwise (popover is hidden).

    // [+] button → attachment menu.
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
                .align_items(alignment::Alignment::Center),
            )
            .padding(8)
            .width(Length::Fill)
            .style(iced::theme::Button::Custom(Box::new(theme::NeoButton {
                is_accent: false,
                has_shadow: false,
                theme: app_theme,
            })))
            .on_press(Message::ChatFilePressed);

            Some(
                container(column![attach_item].spacing(2).width(Length::Fill))
                    .padding(6)
                    .width(Length::Fixed(160.0))
                    .style(iced::theme::Container::Custom(Box::new(
                        theme::NeoDropdown { theme: app_theme },
                    )))
                    .into(),
            )
        })
        .position(crate::widgets::Position::Top)
        .on_close(Message::ChatToggleMenu)
        .into();

    // Permission-mode dropdown.
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
                    let label = text(mode.label()).font(icons::FONT_REGULAR).size(13).style(
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
                    .style(iced::theme::Container::Custom(Box::new(
                        theme::NeoDropdown { theme: app_theme },
                    )))
                    .into(),
            )
        })
        .position(crate::widgets::Position::Top)
        .on_close(Message::ChatTogglePermPicker)
        .into();

    // Context ring scaffold: a ring icon + percent overlay. Click wired, popup
    // deferred to a later pass.
    let context_btn = button(
        container(
            row![
                icons::render_svg(&icons::HANDLE_IMAGE, theme::COLOR_ACCENT)
                    .width(Length::Fixed(16.0))
                    .height(Length::Fixed(16.0)),
                text(format!("{}%", state.context_pct))
                    .font(icons::FONT_REGULAR)
                    .size(12)
                    .style(muted_color),
            ]
            .spacing(4)
            .align_items(alignment::Alignment::Center),
        )
        .padding(6),
    )
    .padding(0)
    .style(iced::theme::Button::Custom(Box::new(theme::NeoButton {
        is_accent: false,
        has_shadow: false,
        theme: app_theme,
    })))
    .on_press(Message::ChatContextPressed);

    // Model picker dropdown.
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
            let models = ["On-Device (LiteRT)", "Claude (BYOK)", "GPT-4o (BYOK)"];
            let items = models.iter().fold(
                Column::<Message, Theme, iced::Renderer>::new()
                    .spacing(2)
                    .width(Length::Fill),
                |col, model| {
                    let is_selected = *model == state.selected_model;
                    let label =
                        text(*model)
                            .font(icons::FONT_REGULAR)
                            .size(13)
                            .style(if is_selected {
                                theme::COLOR_ACCENT
                            } else {
                                text_color
                            });
                    let row_btn = dropdown_menu_row(
                        label,
                        app_theme,
                        Message::ChatSelectModel((*model).to_string()),
                    );
                    col.push(row_btn)
                },
            );
            Some(
                container(items)
                    .padding(6)
                    .width(Length::Fixed(200.0))
                    .style(iced::theme::Container::Custom(Box::new(
                        theme::NeoDropdown { theme: app_theme },
                    )))
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
        .center_x()
        .center_y(),
    )
    .padding(10)
    .width(Length::Fixed(44.0))
    .height(Length::Fixed(44.0))
    .style(iced::theme::Button::Custom(Box::new(theme::NeoButton {
        is_accent: true,
        has_shadow: true,
        theme: app_theme,
    })))
    .on_press(Message::ChatSendMessage);

    // [+] [perm] <fill> [context] [model] [send]
    let action_row = row![plus_btn, perm_btn]
        .spacing(8)
        .align_items(alignment::Alignment::Center)
        .push(horizontal_space().width(Length::Fill))
        .push(context_btn)
        .push(model_btn)
        .push(send_btn);

    let bar = column![
        container(editor_host).padding([0, 12]),
        action_row.padding([0, 12])
    ]
    .spacing(8)
    .width(Length::Fill);

    // The styled solid block is 75% of the pane width (6 of 8 FillPortion),
    // centered between two bare fill spacers. Bare `horizontal_space` (matching
    // the working top-bar pattern) is required — wrapping it in a container
    // breaks the FillPortion distribution in iced 0.12. Bottom padding is
    // smaller than top so the bar lifts clear of the window's bottom edge.
    let styled_bar = container(bar)
        .width(Length::FillPortion(6))
        .padding([10, 24, 14, 24])
        .style(iced::theme::Container::Custom(Box::new(
            theme::NeoInputBar {
                has_shadow: false,
                theme: app_theme,
            },
        )));

    row![
        horizontal_space().width(Length::FillPortion(1)),
        styled_bar,
        horizontal_space().width(Length::FillPortion(1)),
    ]
    .width(Length::Fill)
    .align_items(alignment::Alignment::Center)
    .into()
}
/// border to the accent color so the open state is visible.
fn dropdown_trigger(
    label: &str,
    chevron: &iced::widget::svg::Handle,
    color: iced::Color,
    app_theme: AppTheme,
    active: bool,
    on_press: Message,
) -> button::Button<'static, Message, Theme, iced::Renderer> {
    // When the trigger is active (open), it switches to the peach accent
    // background, so its label + chevron must be BLACK to stay legible against
    // the brightened-on-hover peach fill.
    let trigger_color = if active { iced::Color::BLACK } else { color };
    button(
        container(
            row![
                text(label.to_string())
                    .font(icons::FONT_REGULAR)
                    .size(13)
                    .style(trigger_color),
                icons::render_svg(chevron, trigger_color)
                    .width(Length::Fixed(14.0))
                    .height(Length::Fixed(14.0)),
            ]
            .spacing(6)
            .align_items(alignment::Alignment::Center),
        )
        .padding([6, 10]),
    )
    .padding(0)
    .style(iced::theme::Button::Custom(Box::new(theme::NeoButton {
        is_accent: active,
        has_shadow: false,
        theme: app_theme,
    })))
    .on_press(on_press)
}

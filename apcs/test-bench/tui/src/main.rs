//! ESP32 Modbus Test-Bench TUI — главная точка входа.

mod app;
mod crc;
mod discover;
mod emulator;
mod firmware;
mod frames;
mod master;
mod tcp_master;
mod tcp_server;
mod ui;
mod worker;

use std::time::Duration;

use anyhow::Result;
use app::App;
use app::Tab as AppTab;
use crossterm::event::{
    self, EnableMouseCapture, DisableMouseCapture, Event, KeyCode, KeyEventKind, KeyModifiers, MouseButton,
};
use ratatui::DefaultTerminal;

fn main() -> Result<()> {
    let mut terminal = ratatui::init();
    crossterm::execute!(std::io::stdout(), EnableMouseCapture)?;
    let res = run(&mut terminal);
    crossterm::execute!(std::io::stdout(), DisableMouseCapture)?;
    ratatui::restore();
    res
}

fn run(terminal: &mut DefaultTerminal) -> Result<()> {
    let mut app = App::new();
    app.log(0, "ESP32 Modbus Test-Bench TUI started");
    app.log(
        0,
        "Start test server: [e]   |   connect ESP32: Ports → [c]   |   Modbus TCP server: Ports → [t]",
    );

    loop {
        terminal.draw(|frame| ui::draw(frame, &mut app))?;

        if event::poll(Duration::from_millis(50))? {
            match event::read()? {
                Event::Key(key) if key.kind == KeyEventKind::Press => handle_key(&mut app, key),
                Event::Mouse(m) => handle_mouse(&mut app, m),
                _ => {}
            }
        }

        // Обрабатываем фоновые события (включая тики — они нужны для
        // дренажа TCP-трафика вкладки Bus).
        while let Ok(ev) = app.runtime.events_rx.try_recv() {
            app.handle_event(ev);
        }

        if app.should_quit {
            break;
        }
    }
    Ok(())
}

fn handle_key(app: &mut App, key: crossterm::event::KeyEvent) {
    use KeyCode::*;

    // Режим редактирования поля формы: все клавиши идут в текст.
    if app.is_editing() {
        match key.code {
            Char(c) => app.edit_char(c),
            Backspace => app.edit_backspace(),
            Enter | Esc => app.cancel_edit(),
            _ => {}
        }
        return;
    }

    // Пока выполняется фоновая задача — блокируем только опасные операции
    // (connect/flash и т.п.), но навигация (Tab, прокрутка лога) и выход работают.
    if app.busy.is_some()
        && !matches!(
            key.code,
            Char('q') | Esc | Tab | BackTab | F(1) | Up | Down | Char('k') | Char('j')
        )
    {
        return;
    }

    match key.code {
        // Esc: сначала выходим из режима управления и редактирования.
        Esc if app.sensor_manage && app.tab == AppTab::Sensors => {
            app.sensor_manage = false;
        }
        Char('q') | Esc => {
            app.should_quit = true;
        }
        Tab => app.tab = app.tab.next(),
        BackTab => app.tab = app.tab.prev(),
        F(1) => app.tab = AppTab::Help,
        Char('e') => app.toggle_emulator(),
        Char('R') => {
            app.runtime.start_scanner();
            app.log(1, "Rescan requested");
        }
        Char('c') if app.tab == AppTab::Ports => app.connect_selected_port(),
        Char('d') if app.tab == AppTab::Ports => {
            let was = app.serial_port.clone().unwrap_or_default();
            app.disconnect_serial();
            app.log(2, format!("Disconnected {}", was));
        }
        Char('p') if app.tab == AppTab::Ports => app.probe_selected_port(),
        Char('t') if app.tab == AppTab::Ports => app.toggle_tcp_server(),
        // Modbus TCP master: подключение к внешнему устройству / отключение.
        Char('y') if app.tab == AppTab::Ports => app.connect_tcp(),
        Char('o') if app.tab == AppTab::Ports => app.disconnect_tcp(),
        Left | Char('h') if app.tab == AppTab::Ports => app.tcp_focus = 0,
        Right | Char('l') if app.tab == AppTab::Ports => app.tcp_focus = 1,
        Enter if app.tab == AppTab::Ports => app.begin_edit_focused(),
        Up | Char('k') if app.tab == AppTab::Ports => {
            if !app.ports.is_empty() {
                app.selected_port = app.selected_port.saturating_sub(1);
            }
        }
        Down | Char('j') if app.tab == AppTab::Ports => {
            if !app.ports.is_empty() {
                app.selected_port = (app.selected_port + 1).min(app.ports.len() - 1);
            }
        }
        // Дашборд: выбор устройства (насос выбранного устройства — через [space]).
        Up | Char('k') if app.tab == AppTab::Dashboard => app.select_dev_prev(),
        Down | Char('j') if app.tab == AppTab::Dashboard => app.select_dev_next(),
        PageUp if app.tab == AppTab::Dashboard => app.select_dev_paged(-1),
        PageDown if app.tab == AppTab::Dashboard => app.select_dev_paged(1),
        // Registres tab
        Char('r') if app.tab == AppTab::Registers => app.read_registers(),
        Char('w') if app.tab == AppTab::Registers => app.write_register(),
        Char('t') if app.tab == AppTab::Registers => {
            app.reg_form.reg_type = (app.reg_form.reg_type + 1) % 3;
        }
        Up | Char('k') if app.tab == AppTab::Registers => app.move_focus(-1),
        Down | Char('j') if app.tab == AppTab::Registers => app.move_focus(1),
        Enter if app.tab == AppTab::Registers => app.begin_edit_focused(),
        Char('1') if app.tab == AppTab::Registers || app.tab == AppTab::Sensors => {
            if app.tab == AppTab::Registers {
                app.reg_form.reg_type = 0;
            } else {
                app.sensor_form.kind = crate::emulator::DataType::TemperatureC;
            }
        }
        Char('2') if app.tab == AppTab::Registers || app.tab == AppTab::Sensors => {
            if app.tab == AppTab::Registers {
                app.reg_form.reg_type = 1;
            } else {
                app.sensor_form.kind = crate::emulator::DataType::PressureKPa;
            }
        }
        Char('3') if app.tab == AppTab::Registers || app.tab == AppTab::Sensors => {
            if app.tab == AppTab::Registers {
                app.reg_form.reg_type = 2;
            } else {
                app.sensor_form.kind = crate::emulator::DataType::Flow;
            }
        }
        Char('4') if app.tab == AppTab::Sensors => {
            app.sensor_form.kind = crate::emulator::DataType::Test;
        }
        Char('5') if app.tab == AppTab::Sensors => {
            app.sensor_form.kind = crate::emulator::DataType::LevelM;
        }
        Char('6') if app.tab == AppTab::Sensors => {
            app.sensor_form.kind = crate::emulator::DataType::HumidityPct;
        }
        Char('m') if app.tab == AppTab::Sensors => {
            app.toggle_sensor_manage();
        }
        Char('n') if app.tab == AppTab::Sensors => {
            app.add_device();
        }
        Char('d') | Delete if app.tab == AppTab::Sensors && app.sensor_manage => {
            app.remove_selected();
        }
        Up | Char('k') if app.tab == AppTab::Sensors && app.sensor_manage => {
            app.sensor_nav(-1);
        }
        Down | Char('j') if app.tab == AppTab::Sensors && app.sensor_manage => {
            app.sensor_nav(1);
        }
        Up | Char('k') if app.tab == AppTab::Sensors => app.move_focus(-1),
        Down | Char('j') if app.tab == AppTab::Sensors => app.move_focus(1),
        Enter if app.tab == AppTab::Sensors => app.begin_edit_focused(),
        Char('a') if app.tab == AppTab::Sensors => app.add_sensor(),
        Char(' ') if app.tab == AppTab::Dashboard => {
            // Переключение насоса выбранного устройства.
            if !app.snapshot.is_empty() {
                let di = app.selected_device();
                app.toggle_coil(di, 0);
            }
        }
        // Firmware tab
        Char('i') if app.tab == AppTab::Firmware => app.fw_board_info(),
        Char('b') | Char('B') if app.tab == AppTab::Firmware => app.fw_backup(),
        Char('f') if app.tab == AppTab::Firmware => app.fw_flash_test(),
        Char('o') if app.tab == AppTab::Firmware => app.fw_restore_selected(),
        Up | Char('k') if app.tab == AppTab::Firmware => {
            if !app.backups.is_empty() {
                app.backup_selected = app.backup_selected.saturating_sub(1);
            }
        }
        Down | Char('j') if app.tab == AppTab::Firmware => {
            if !app.backups.is_empty() {
                app.backup_selected = (app.backup_selected + 1).min(app.backups.len() - 1);
            }
        }
        Char('x') if app.tab == AppTab::Ports => {
            if let Some(name) = app.selected_port().map(|p| p.name.clone()) {
                app.log(2, format!("Manual probe of {}", name));
            }
        }
        Enter if app.tab == AppTab::Log => {
            app.logs.clear();
            app.log(0, "Log cleared");
        }
        Up | Char('k') if app.tab == AppTab::Log || app.tab == AppTab::Help || app.tab == AppTab::Bus => {
            app.mouse_scroll(-1);
        }
        Down | Char('j') if app.tab == AppTab::Log || app.tab == AppTab::Help || app.tab == AppTab::Bus => {
            app.mouse_scroll(1);
        }
        Char(c) if key.modifiers.contains(KeyModifiers::CONTROL) && c == 'c' => {
            app.should_quit = true;
        }
        _ => {}
    }
}

/// Обработка мыши: клик по вкладкам, по строкам списков, по полям форм, колесо.
fn handle_mouse(app: &mut App, m: crossterm::event::MouseEvent) {
    use crossterm::event::MouseEventKind::*;
    // Строка вкладок — переключение по клику.
    if m.row == 0 {
        if let Down(MouseButton::Left) = m.kind {
            if let Some(i) = ui::tab_at_col(m.column) {
                app.tab = AppTab::ALL[i];
            }
        }
        return;
    }
    match m.kind {
        ScrollUp => app.mouse_scroll(-1),
        ScrollDown => app.mouse_scroll(1),
        Down(MouseButton::Left) => left_click(app, m),
        _ => {}
    }
}

/// Клик левой кнопкой по телу: выбор строки списка / поля формы.
fn left_click(app: &mut App, m: crossterm::event::MouseEvent) {
    if app.is_editing() {
        return;
    }
    let y = m.row.saturating_sub(ui::HEADER_ROWS);
    let Ok((w, _)) = crossterm::terminal::size() else {
        return;
    };
    match app.tab {
        AppTab::Ports => {
            if let Some(i) = ui::list_index_at(y, app.ports.len()) {
                app.set_selected_port(i);
            }
        }
        AppTab::Dashboard => {
            let left_w = w * 3 / 5;
            if m.column < left_w && y > 0 {
                let map = ui::dashboard_rows_for_devices(app);
                if let Some(Some(di)) = map.get((y - 1) as usize).copied() {
                    app.set_selected_device(di);
                }
            }
        }
        AppTab::Sensors => {
            let left_w = w / 2;
            if m.column < left_w {
                if let Some(i) = ui::list_index_at(y, app.sensor_rows().len()) {
                    app.sensor_selected = i;
                }
            } else {
                let cr = y.saturating_sub(1);
                let f = match cr {
                    3 => Some(0),
                    4 => Some(1),
                    6 => Some(2),
                    7 => Some(3),
                    8 => Some(4),
                    _ => None,
                };
                if let Some(f) = f {
                    app.sensor_focus = f;
                }
            }
        }
        AppTab::Registers => {
            let left_w = w * 3 / 5;
            if m.column < left_w && y > 0 {
                let cr = y - 1;
                if (4..=8).contains(&cr) {
                    app.reg_focus = (cr - 4) as usize;
                }
            }
        }
        AppTab::Firmware => {
            let right_w = w * 2 / 5;
            if m.column >= w.saturating_sub(right_w) {
                if let Some(i) = ui::list_index_at(y, app.backups.len()) {
                    app.backup_selected = i;
                }
            }
        }
        _ => {}
    }
}
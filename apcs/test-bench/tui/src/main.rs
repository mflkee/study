//! ESP32 Modbus Test-Bench TUI — главная точка входа.

mod app;
mod crc;
mod discover;
mod emulator;
mod firmware;
mod frames;
mod master;
mod ui;
mod worker;

use std::time::Duration;

use anyhow::Result;
use app::App;
use app::Tab as AppTab;
use crossterm::event::{self, Event, KeyCode, KeyEventKind, KeyModifiers};
use ratatui::DefaultTerminal;
use worker::Event as WorkerEvent;

fn main() -> Result<()> {
    let mut terminal = ratatui::init();
    let res = run(&mut terminal);
    ratatui::restore();
    res
}

fn run(terminal: &mut DefaultTerminal) -> Result<()> {
    let mut app = App::new();
    app.log(0, "ESP32 Modbus Test-Bench TUI started");
    app.log(
        0,
        "Start test server: [e]   |   connect ESP32: Ports → [c]",
    );

    loop {
        terminal.draw(|frame| ui::draw(frame, &mut app))?;

        if event::poll(Duration::from_millis(50))? {
            if let Event::Key(key) = event::read()? {
                if key.kind == KeyEventKind::Press {
                    handle_key(&mut app, key);
                }
            }
        }

        // Обрабатываем фоновые события.
        loop {
            match app.runtime.events_rx.try_recv() {
                Ok(WorkerEvent::Ticked) => {}
                Ok(ev) => app.handle_event(ev),
                Err(_) => break,
            }
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
        Char('p') if app.tab == AppTab::Ports => {
            let name = app.selected_port().map(|p| p.name.clone());
            if let Some(name) = name {
                let slave = discover::probe_modbus(&name, 9600);
                let result = match slave {
                    Some(s) => {
                        app.log(1, format!("{} responds as slave {}", name, s));
                        "ok"
                    }
                    None => {
                        app.log(2, format!("{} no Modbus response", name));
                        "no response"
                    }
                };
                app.status_line = format!("Probe {}: {}", name, result);
            }
        }
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
        Char(c) if key.modifiers.contains(KeyModifiers::CONTROL) && c == 'c' => {
            app.should_quit = true;
        }
        _ => {}
    }
}
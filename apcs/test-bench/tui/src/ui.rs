//! Рендеринг интерфейса: все табы + footer.

use ratatui::layout::{Constraint, Layout, Rect};
use ratatui::style::{Color, Modifier, Style};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, List, ListItem, Paragraph, Tabs};
use ratatui::Frame;

use crate::app::{App, Tab};

pub fn draw(frame: &mut Frame, app: &mut App) {
    let area = frame.area();
    let [header, body, footer] = Layout::vertical([
        Constraint::Length(3),
        Constraint::Min(0),
        Constraint::Length(4),
    ])
    .areas(area);

    draw_header(frame, app, header);
    draw_footer(frame, app, footer);

    match app.tab {
        Tab::Dashboard => draw_dashboard(frame, app, body),
        Tab::Ports => draw_ports(frame, app, body),
        Tab::Registers => draw_registers(frame, app, body),
        Tab::Sensors => draw_sensors(frame, app, body),
        Tab::Firmware => draw_firmware(frame, app, body),
        Tab::Log => draw_log(frame, app, body),
        Tab::Help => draw_help(frame, app, body),
    }
}

fn draw_header(frame: &mut Frame, app: &App, area: Rect) {
    let [tabs_area, status_area] =
        Layout::vertical([Constraint::Length(1), Constraint::Length(2)]).areas(area);

    // Строка вкладок: всегда видна, текущая подсвечена.
    let titles: Vec<Line> = Tab::ALL.iter().map(|t| Line::from(t.title())).collect();
    let tabs = Tabs::new(titles)
        .select(Tab::ALL.iter().position(|t| t == &app.tab).unwrap_or(0))
        .divider("  ")
        .style(Style::default().fg(Color::DarkGray))
        .highlight_style(
            Style::default()
                .fg(Color::Black)
                .bg(Color::Cyan)
                .add_modifier(Modifier::BOLD),
        );
    frame.render_widget(tabs, tabs_area);

    // Статус-строка под табами.
    let status = format!(
        " {} — {} | tick={} | poll: {}",
        app.tab.title(),
        if app.is_emu() {
            "EMULATOR"
        } else if app.is_connected() {
            app.serial_port.as_deref().unwrap_or("SERIAL")
        } else {
            "NOT CONNECTED"
        },
        app.tick,
        app.last_poll_error
            .clone()
            .unwrap_or_else(|| format!("{} devices", app.snapshot.len()))
    );
    let block = Block::bordered().title(Span::styled(
        " ESP32 Modbus Test-Bench ",
        Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
    ));
    frame.render_widget(Paragraph::new(status).block(block), status_area);
}

fn draw_footer(frame: &mut Frame, app: &App, area: Rect) {
    let block = Block::bordered();
    let inner = block.inner(area);
    frame.render_widget(block, area);
    let [global_area, hint_area] =
        Layout::vertical([Constraint::Length(1), Constraint::Min(0)]).areas(inner);

    let global = vec![
        Span::styled(" [q] quit  ", Style::default().fg(Color::DarkGray)),
        Span::styled("[Tab] next  ", Style::default().fg(Color::DarkGray)),
        Span::styled("[e] toggle emu  ", Style::default().fg(Color::DarkGray)),
        Span::styled("[R] rescan  ", Style::default().fg(Color::DarkGray)),
        Span::styled("[F1] help", Style::default().fg(Color::DarkGray)),
    ];
    frame.render_widget(Paragraph::new(Line::from(global)), global_area);

    // Контекстная подсказка вкладки — отдельная строка, чтобы помещаться
    // целиком даже на узком терминале (раньше обрезалась).
    let hint = format!(
        "   {}",
        match app.tab {
            Tab::Dashboard => "[↑↓] select device  [space] toggle pump",
            Tab::Ports => "[↑↓] select  [c] connect  [d] disconnect  [p] probe",
            Tab::Registers => "[↑↓] focus  [enter] edit  [r] read  [w] write  [t] type",
            Tab::Sensors if app.sensor_manage => {
                "[↑↓] move  [d] delete device (◆) or sensor (●)  [esc] done"
            }
            Tab::Sensors => {
                "[↑↓] focus  [enter] edit  [1..6] type  [a] add  [n] new slave  [m] manage  [d] delete"
            }
            Tab::Firmware => "[i] board-info  [f] flash  [b] backup  [o] restore",
            Tab::Log => "[enter] clear",
            Tab::Help => "[↑↓] scroll",
        }
    );
    frame.render_widget(
        Paragraph::new(Line::from(vec![Span::styled(
            hint,
            Style::default().fg(Color::Yellow),
        )])),
        hint_area,
    );
}

// --- DASHBOARD ---

fn draw_dashboard(frame: &mut Frame, app: &mut App, area: Rect) {
    let [left, right] = Layout::horizontal([Constraint::Percentage(60), Constraint::Percentage(40)])
        .areas(area);

    // Левая колонка: устройства и датчики.
    let mut lines: Vec<Line> = Vec::new();

    if app.snapshot.is_empty() && !app.is_connected() {
        lines.push(Line::from(Span::styled(
            " ╭──────────────────────────────────────────╮",
            Style::default().fg(Color::DarkGray),
        )));
        lines.push(Line::from(Span::styled(
            " │  No data. Start the test server ([e])    │",
            Style::default().fg(Color::DarkGray),
        )));
        lines.push(Line::from(Span::styled(
            " │  or connect an ESP32 ([c] on Ports tab)   │",
            Style::default().fg(Color::DarkGray),
        )));
        lines.push(Line::from(Span::styled(
            " ╰──────────────────────────────────────────╯",
            Style::default().fg(Color::DarkGray),
        )));
    }

    let selected = app.selected_device();

    for (di, dev) in app.snapshot.iter().enumerate() {
        let selected_now = di == selected;
        let color = if dev.source == "EMU" {
            Color::Cyan
        } else {
            Color::Green
        };
        lines.push(Line::from(""));
        let leader = if selected_now { "▸" } else { " " };
        let line_style = if selected_now {
            Style::default().fg(Color::Yellow).add_modifier(Modifier::BOLD)
        } else {
            Style::default().fg(color).add_modifier(Modifier::BOLD)
        };
        lines.push(Line::from(vec![
            Span::styled(
                format!(" {} ◆ Slave {}: {}", leader, dev.slave_id, dev.name),
                line_style,
            ),
            Span::styled(
                format!("   [{}]{}", dev.source, if selected_now { "  ← active" } else { "" }),
                Style::default().fg(if selected_now { Color::Yellow } else { Color::DarkGray }),
            ),
        ]));

        // Каналы.
        for ch in dev.channels.iter() {
            let formatted = format!(
                "   {:6} {:>7.2} {:3}",
                ch.label, ch.value, ch.unit
            );
            lines.push(Line::from(Span::styled(
                formatted,
                Style::default().fg(Color::White),
            )));
        }

        // Насосы/coils.
        if !dev.coils.is_empty() {
            lines.push(Line::from(""));
            lines.push(Line::from(Span::styled(
                "   Pumps:",
                Style::default().fg(Color::Yellow),
            )));
            for (ci, c) in dev.coils.iter().enumerate() {
                let on = *c;
                let (sym, col) = if on {
                    ("● ON ", Color::Green)
                } else {
                    ("○ OFF", Color::Red)
                };
                let lbl = format!("   Pump {}: {}", ci, sym);
                lines.push(Line::from(vec![
                    Span::styled(lbl, Style::default().fg(col).add_modifier(Modifier::BOLD)),
                    Span::styled(
                        if selected_now && ci == 0 {
                            "  [space]  ◀ toggles this pump"
                        } else {
                            ""
                        },
                        Style::default().fg(Color::DarkGray),
                    ),
                ]));
            }
        }
    }

    let device_block = Block::bordered().title(Span::styled(
        " Devices ",
        Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
    ));
    frame.render_widget(Paragraph::new(lines).block(device_block), left);

    // Правая колонка: карта/статус пайплайна.
    draw_pipeline(frame, app, right);
}

fn draw_pipeline(frame: &mut Frame, app: &App, area: Rect) {
    let mut lines: Vec<Line> = vec![Line::from("")];

    // Статус источника.
    let (src, color) = if app.is_emu() {
        ("IN-PROCESS EMULATOR", Color::Cyan)
    } else if app.is_connected() {
        ("SERIAL ESP32", Color::Green)
    } else {
        ("NOT CONNECTED", Color::Red)
    };
    lines.push(Line::from(Span::styled(
        format!(" SOURCE: {}", src),
        Style::default().fg(color).add_modifier(Modifier::BOLD),
    )));
    lines.push(Line::from(" ──────────────────────────────────"));

    // Поток данных.
    lines.push(Line::from(""));
    lines.push(Line::from("  [sensors] ─▶ [Modbus RTU] ─▶ [UI]"));
    lines.push(Line::from(""));

    // Детали связи.
    let fw_state: String = if app.is_emu() {
        "emulated".to_string()
    } else if let Some(port) = &app.serial_port {
        if let Some(e) = &app.last_poll_error {
            format!("{} (error: {})", port, e)
        } else {
            format!("{} (ok)", port)
        }
    } else {
        "no serial link".into()
    };
    lines.push(Line::from(Span::styled(
        format!(" Serial link : {}", fw_state),
        if app.is_connected() {
            Style::default().fg(Color::Green)
        } else {
            Style::default().fg(Color::DarkGray)
        },
    )));

    let hw = if app.is_emu() {
        "n/a (in-memory)"
    } else {
        "ESP32 + MAX3485 + ZK-U485"
    };
    lines.push(Line::from(Span::styled(
        format!(" HW          : {}", hw),
        Style::default().fg(Color::DarkGray),
    )));

    lines.push(Line::from(""));
    lines.push(Line::from(Span::styled(
        " Ports found:",
        Style::default().fg(Color::Yellow),
    )));
    if app.ports.is_empty() {
        lines.push(Line::from(Span::styled(
            "   (none detected)",
            Style::default().fg(Color::DarkGray),
        )));
    } else {
        for p in app.ports.iter().take(6) {
            let tag = if p.is_esp_like {
                if p.has_firmware {
                    "[ESP32 F/W]"
                } else {
                    "[ESP32 ?]"
                }
            } else {
                "[port]"
            };
            let (col, mark) = if p.is_esp_like {
                (Color::Green, "●")
            } else {
                (Color::DarkGray, "·")
            };
            lines.push(Line::from(vec![
                Span::styled(mark, Style::default().fg(col)),
                Span::styled(format!(" {} {}", p.name, tag), Style::default().fg(col)),
            ]));
        }
    }

    lines.push(Line::from(""));
    lines.push(Line::from(Span::styled(
        format!(" Last good poll: {:.0}s ago", crate::emulator::now_secs() - app.last_poll_at),
        Style::default().fg(Color::DarkGray),
    )));

    let block = Block::bordered().title(Span::styled(
        " Pipeline ",
        Style::default().fg(Color::Magenta).add_modifier(Modifier::BOLD),
    ));
    frame.render_widget(Paragraph::new(lines).block(block), area);
}

// --- PORTS ---

fn draw_ports(frame: &mut Frame, app: &mut App, area: Rect) {
    let rows: Vec<ListItem> = app
        .ports
        .iter()
        .enumerate()
        .map(|(i, p)| {
            let tag = p.status_label();
            let color = if p.is_esp_like {
                if p.has_firmware {
                    Color::Green
                } else {
                    Color::Yellow
                }
            } else {
                Color::DarkGray
            };
            let marker = if i == app.selected_port { "›" } else { " " };
            let connected = app.serial_port.as_deref() == Some(p.name.as_str());
            ListItem::new(Line::from(vec![
                Span::styled(marker, Style::default().fg(Color::Cyan)),
                Span::styled(format!(" {} ", p.name), Style::default().fg(Color::White).add_modifier(Modifier::BOLD)),
                Span::styled(format!("[{}]", tag), Style::default().fg(color)),
                Span::styled(
                    format!("  {}", p.description),
                    Style::default().fg(Color::DarkGray),
                ),
                if connected {
                    Span::styled("  ◈ CONNECTED", Style::default().fg(Color::Green))
                } else {
                    Span::styled("", Style::default())
                },
            ]))
        })
        .collect();

    let block = Block::bordered().title(Span::styled(
        " Serial Ports (hotplug) ",
        Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
    ));
    let list = List::new(rows)
        .block(block)
        .highlight_style(Style::default().bg(Color::DarkGray).add_modifier(Modifier::BOLD));
    frame.render_stateful_widget(list, area, &mut app.port_state());
}

// --- REGISTERS ---

/// Строка редактируемого поля формы: подсветка фокуса и режима ввода.
fn field_line(label: &str, value: &str, focused: bool, editing: bool) -> Line<'static> {
    let body_style = if editing {
        Style::default().fg(Color::Black).bg(Color::Cyan)
    } else if focused {
        Style::default().fg(Color::Yellow).add_modifier(Modifier::BOLD)
    } else {
        Style::default().fg(Color::White)
    };
    let tail = if editing {
        " ■"
    } else if focused {
        "  ←"
    } else {
        ""
    };
    Line::from(vec![
        Span::styled(format!(" {:>14} : ", label), Style::default().fg(Color::DarkGray)),
        Span::styled(format!("{}{}", value, tail), body_style),
    ])
}

fn draw_registers(frame: &mut Frame, app: &mut App, area: Rect) {
    let [left, right] = Layout::horizontal([Constraint::Percentage(60), Constraint::Percentage(40)])
        .areas(area);

    // Форма.
    let type_label = match app.reg_form.reg_type {
        0 => "INPUT",
        1 => "HOLDING",
        _ => "COIL",
    };
    let editing = app.is_editing();
    let edit_hint = if editing {
        " typing… [enter] ok [esc] cancel"
    } else {
        " [↑↓] focus  [enter] edit"
    };
    let mut lines = vec![
        Line::from(""),
        Line::from(Span::styled(
            " Register editor",
            Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
        )),
        Line::from(""),
        Line::from(Span::styled(
            format!(" Reg type : {}   [t] change  {}", type_label, edit_hint),
            Style::default().fg(Color::Yellow),
        )),
        field_line(
            "Slave ID",
            &app.reg_form.slave_id,
            app.reg_focus == 0,
            editing && matches!(app.editing, Some(crate::app::FieldEdit::Reg(0))),
        ),
        field_line(
            "Start addr",
            &app.reg_form.start,
            app.reg_focus == 1,
            editing && matches!(app.editing, Some(crate::app::FieldEdit::Reg(1))),
        ),
        field_line(
            "Count",
            &app.reg_form.count,
            app.reg_focus == 2,
            editing && matches!(app.editing, Some(crate::app::FieldEdit::Reg(2))),
        ),
        field_line(
            "Write addr",
            &app.reg_form.addr,
            app.reg_focus == 3,
            editing && matches!(app.editing, Some(crate::app::FieldEdit::Reg(3))),
        ),
        field_line(
            "Write value",
            &app.reg_form.value,
            app.reg_focus == 4,
            editing && matches!(app.editing, Some(crate::app::FieldEdit::Reg(4))),
        ),
        Line::from(""),
        Line::from(Span::styled(
            " [r] read  [w] write value",
            Style::default().fg(Color::Yellow),
        )),
        Line::from(""),
        Line::from(format!(
            " Current result: {}",
            if app.status_line.is_empty() {
                "-"
            } else {
                &app.status_line
            }
        )),
    ];
    if !app.is_connected() {
        lines.push(Line::from(Span::styled(
            " ! no target: press [e] for emulator, or connect in Ports",
            Style::default().fg(Color::Red),
        )));
    }
    let is_emu = app.is_emu();
    lines.push(Line::from(""));
    lines.push(Line::from(Span::styled(
        if is_emu {
            " Target: in-process emulator"
        } else if app.is_connected() {
            " Target: serial ESP32"
        } else {
            " Target: (no connection — start emulator [e])"
        },
        Style::default().fg(if app.is_connected() { Color::Green } else { Color::DarkGray }),
    )));

    let block = Block::bordered().title(Span::styled(
        " Registers ",
        Style::default().fg(Color::Yellow).add_modifier(Modifier::BOLD),
    ));
    frame.render_widget(Paragraph::new(lines).block(block), left);

    // Справа: последний снимок.
    let mut rl = vec![Line::from("")];
    for (di, dev) in app.snapshot.iter().enumerate() {
        rl.push(Line::from(Span::styled(
            format!(" Slave {}: {}", dev.slave_id, dev.name),
            Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
        )));
        for pc in dev.channels.iter() {
            rl.push(Line::from(format!(
                "  {:5} @{} = {:>8.2} {}",
                pc.label, pc.reg, pc.value, pc.unit
            )));
        }
        if di + 1 < app.snapshot.len() {
            rl.push(Line::from(""));
        }
    }
    let block = Block::bordered().title(" Live snapshot ");
    frame.render_widget(Paragraph::new(rl).block(block), right);
}

// --- SENSORS ---

fn draw_sensors(frame: &mut Frame, app: &mut App, area: Rect) {
    let [_, right] = Layout::horizontal([Constraint::Percentage(50), Constraint::Percentage(50)])
        .areas(area);

    // Список устройств/датчиков (строки строиm app.sensor_rows — единый
    // источник для навигации и отрисовки).
    let rows = app.sensor_rows();
    let mut items: Vec<ListItem> = Vec::new();
    {
        let emu = app.emu.lock().unwrap();
        for row in &rows {
            match row {
                crate::app::SensorRow::Device(di) => {
                    if let Some(dev) = emu.device(*di) {
                        // Один ListItem = одна строка sensor_rows, чтобы индексы
                        // навигации (sensor_selected) и подсветки списка совпадали.
                        // Пустую строку-разделитель встраиваем в элемент заголовка.
                        let mut lines = Vec::new();
                        if !items.is_empty() {
                            lines.push(Line::from(""));
                        }
                        lines.push(Line::from(Span::styled(
                            format!("◆ Slave {} — {} ", dev.slave_id, dev.name),
                            Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
                        )));
                        items.push(ListItem::from(lines));
                    }
                }
                crate::app::SensorRow::Sensor(di, si) => {
                    if let Some(dev) = emu.device(*di) {
                        if let Some(s) = dev.sensors.get(*si) {
                            let (mark, col) = if s.enabled {
                                ("●", Color::Green)
                            } else {
                                ("○", Color::Red)
                            };
                            let line = Line::from(vec![
                                Span::styled(mark, Style::default().fg(col)),
                                Span::styled(format!("  {}", s.name), Style::default().fg(Color::White)),
                                Span::styled(
                                    format!(
                                        "  {}  base={:.1} amp={:.1} T={:.0}s => {:.2}",
                                        s.data_type.label(),
                                        s.base,
                                        s.amplitude,
                                        s.period_s,
                                        s.last_value
                                    ),
                                    Style::default().fg(Color::DarkGray),
                                ),
                            ]);
                            items.push(ListItem::from(line));
                        }
                    }
                }
            }
        }
    }
    let title = if app.sensor_manage {
        " Manage: [↑↓] move  [d] delete device/sensor  [esc] done "
    } else {
        " Virtual sensors (emulator)  [m] manage  [n] new slave "
    };
    let block = Block::bordered().title(Span::styled(
        title,
        Style::default()
            .fg(Color::Cyan)
            .add_modifier(Modifier::BOLD),
    ));
    let list = List::new(items).block(block).highlight_style(
        Style::default().bg(Color::DarkGray).add_modifier(Modifier::BOLD),
    );
    frame.render_stateful_widget(list, area, &mut app.sensor_list_state());

    // Форма добавления.
    let editing = app.is_editing();
    let edit_hint = if editing {
        " typing… [enter] ok [esc] cancel"
    } else {
        " [↑↓] focus  [enter] edit"
    };
    let lines = vec![
        Line::from(""),
        Line::from(Span::styled(
            " Add sensor to emulator:",
            Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
        )),
        Line::from(""),
        field_line(
            "Device idx",
            &app.sensor_form.device_idx,
            app.sensor_focus == 0,
            editing && matches!(app.editing, Some(crate::app::FieldEdit::Sensor(0))),
        ),
        field_line(
            "Name",
            &app.sensor_form.name,
            app.sensor_focus == 1,
            editing && matches!(app.editing, Some(crate::app::FieldEdit::Sensor(1))),
        ),
        Line::from(format!(
            " Type         : {}   [1..6] change",
            app.sensor_form.kind.label()
        )),
        field_line(
            "Base value",
            &app.sensor_form.base,
            app.sensor_focus == 2,
            editing && matches!(app.editing, Some(crate::app::FieldEdit::Sensor(2))),
        ),
        field_line(
            "Amplitude",
            &app.sensor_form.amplitude,
            app.sensor_focus == 3,
            editing && matches!(app.editing, Some(crate::app::FieldEdit::Sensor(3))),
        ),
        field_line(
            "Period (s)",
            &app.sensor_form.period,
            app.sensor_focus == 4,
            editing && matches!(app.editing, Some(crate::app::FieldEdit::Sensor(4))),
        ),
        Line::from(""),
        Line::from(Span::styled(
            format!(" [a] add  {}  [m] manage", edit_hint),
            Style::default().fg(Color::Yellow),
        )),
        Line::from(""),
        Line::from(Span::styled(
            " Sensor types: 1=T°C 2=Pressure 3=Flow 4=Test 5=Level 6=Humidity",
            Style::default().fg(Color::DarkGray),
        )),
    ];
    let block = Block::bordered().title(" Sensor factory ");
    frame.render_widget(Paragraph::new(lines).block(block), right);
}

// --- FIRMWARE ---

fn draw_firmware(frame: &mut Frame, app: &mut App, area: Rect) {
    let [left, right] = Layout::horizontal([Constraint::Percentage(60), Constraint::Percentage(40)])
        .areas(area);

    let port = app
        .fw_port
        .clone()
        .or_else(|| app.serial_port.clone())
        .unwrap_or_else(|| "(select in Ports tab)".into());

    let tool = crate::firmware::find_tool()
        .unwrap_or_else(|| "(no esptool — install espflash or esptool)".into());

    let lines = vec![
        Line::from(""),
        Line::from(Span::styled(
            " ESP32 Firmware Manager",
            Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
        )),
        Line::from(""),
        Line::from(format!(" Target port : {}", port)),
        Line::from(format!(" Tool        : {}", tool)),
        Line::from(""),
        Line::from(Span::styled(
            " [i] board-info     — read chip/board",
            Style::default().fg(Color::Yellow),
        )),
        Line::from(Span::styled(
            " [B] backup flash   — save to ~/esp32-backups",
            Style::default().fg(Color::Yellow),
        )),
        Line::from(Span::styled(
            " [f] flash test fw  — flash esp32-fw binary",
            Style::default().fg(Color::Yellow),
        )),
        Line::from(Span::styled(
            " [o] restore        — flash selected backup",
            Style::default().fg(Color::Yellow),
        )),
        Line::from(""),
        Line::from(format!(" Test firmware binary: {:?}", crate::firmware::test_firmware_bin())),
        Line::from(""),
        Line::from(Span::styled(
            " Note: requires `espflash` or `esptool.py` in PATH.",
            Style::default().fg(Color::DarkGray),
        )),
    ];
    let block = Block::bordered().title(Span::styled(
        " Firmware ",
        Style::default().fg(Color::Magenta).add_modifier(Modifier::BOLD),
    ));
    frame.render_widget(Paragraph::new(lines).block(block), left);

    // Backups.
    let rows: Vec<ListItem> = app
        .backups
        .iter()
        .enumerate()
        .map(|(i, b)| {
            ListItem::new(Line::from(if i == app.backup_selected {
                Span::styled(format!("› {}", b), Style::default().fg(Color::Yellow))
            } else {
                Span::styled(format!("  {}", b), Style::default().fg(Color::White))
            }))
        })
        .collect();
    let block = Block::bordered().title(format!(
        " Backups ({}) ",
        app.backups.len()
    ));
    let list = List::new(rows).block(block);
    frame.render_widget(list, right);
}

// --- LOG ---

fn draw_log(frame: &mut Frame, app: &mut App, area: Rect) {
    let rows: Vec<ListItem> = app
        .logs
        .iter()
        .map(|l| {
            let color = match l.level {
                0 => Color::Gray,
                1 => Color::Green,
                2 => Color::Yellow,
                _ => Color::Red,
            };
            ListItem::new(Line::from(Span::styled(&l.text, Style::default().fg(color))))
        })
        .collect();
    let block = Block::bordered().title(format!(" Log ({}) ", app.logs.len()));
    frame.render_widget(List::new(rows).block(block), area);
}

// --- HELP ---

fn draw_help(frame: &mut Frame, _app: &mut App, area: Rect) {
    let text = vec![
        Line::from(""),
        Line::from(Span::styled(
            " GLOBAL",
            Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
        )),
        Line::from("   [Tab]/[Shift-Tab]  — switch tab (powered by the tab bar above)"),
        Line::from("   [q]/[Esc]/[Ctrl-C] — quit"),
        Line::from("   [e]                — toggle in-process emulator (test server)"),
        Line::from("   [R]                — rescan ports (hotplug)"),
        Line::from("   [F1]               — this help"),
        Line::from(""),
        Line::from(Span::styled(
            " DASHBOARD",
            Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
        )),
        Line::from("   [↑/↓]  or [k/j]    — select device (▸ marker)"),
        Line::from("   [space]            — toggle pump 0 of the selected device"),
        Line::from(""),
        Line::from(Span::styled(
            " PORTS",
            Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
        )),
        Line::from("   [↑/↓]  or [k/j]    — select port"),
        Line::from("   [c]                — connect serial port"),
        Line::from("   [d]                — disconnect serial"),
        Line::from("   [p]                — probe Modbus on selected port"),
        Line::from(""),
        Line::from(Span::styled(
            " REGISTERS",
            Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
        )),
        Line::from("   [1]/[2]/[3]        — choose type: input / holding / coil"),
        Line::from("   [↑/↓]              — focus field (Slave/Start/Count/Write addr/Write value)"),
        Line::from("   [enter]            — start editing the focused field"),
        Line::from("   [r]                — read block"),
        Line::from("   [w]                — write single register/coil (uses Write addr/value)"),
        Line::from("   [t]                — cycle type (input/holding/coil)"),
        Line::from(""),
        Line::from(Span::styled(
            " SENSORS",
            Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
        )),
        Line::from("   [1..6]             — pick sensor type (1=T°C 2=Pressure 3=Flow 4=Test 5=Level 6=%RH)"),
        Line::from("   [↑/↓] + [enter]    — edit Device idx / Name / Base / Amplitude / Period"),
        Line::from("   [a]                — add sensor to emulator"),
        Line::from("   [n]                — add new (empty) slave device"),
        Line::from("   [m]                — manage devices & sensors (list becomes active)"),
        Line::from("   [↑/↓] in manage    — move cursor over device rows and sensors"),
        Line::from("   [d] in manage      — delete device (◆) or sensor (●) under cursor"),
        Line::from("   [esc] in manage    — leave manage mode"),
        Line::from("   (last device is never deleted — emulator must serve something)"),
        Line::from(""),
        Line::from(Span::styled(
            " EDITING a field",
            Style::default().fg(Color::Magenta).add_modifier(Modifier::BOLD),
        )),
        Line::from("   Type                — insert characters"),
        Line::from("   [Backspace]        — delete last character"),
        Line::from("   [enter] or [esc]   — finish / cancel editing"),
        Line::from("   (hint: 'registers' editor teaches you real Modbus addressing)"),
        Line::from(Span::styled(
            " LOG",
            Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
        )),
        Line::from("   [Enter]            — clear log"),
        Line::from(""),
        Line::from(Span::styled(
            " FIRMWARE",
            Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
        )),
        Line::from("   [i]  board-info    [f] flash test FW   [b] backup   [o] restore"),
        Line::from("   [↑/↓]              — select backup file"),
        Line::from(""),
        Line::from(Span::styled(
            " Idea: hot-plug any ESP32 — it appears in Ports, get probed,",
            Style::default().fg(Color::DarkGray),
        )),
        Line::from(Span::styled(
            " becomes connected, and you see its live registers.",
            Style::default().fg(Color::DarkGray),
        )),
    ];
    frame.render_widget(Paragraph::new(text), area);
}

// Состояния списков для render_stateful_widget.
impl App {
    pub(crate) fn port_state(&mut self) -> ratatui::widgets::ListState {
        let mut s = ratatui::widgets::ListState::default();
        if !self.ports.is_empty() {
            s.select(Some(self.selected_port));
        }
        s
    }
    pub(crate) fn sensor_list_state(&mut self) -> ratatui::widgets::ListState {
        let mut s = ratatui::widgets::ListState::default();
        if self.sensor_manage {
            s.select(Some(self.sensor_selected));
        }
        s
    }
}
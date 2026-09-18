//! Рендеринг интерфейса: все табы + footer.

use ratatui::layout::{Constraint, Layout, Rect};
use ratatui::style::{Color, Modifier, Style};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, List, ListItem, Paragraph, Tabs};
use ratatui::Frame;

use crate::app::{App, Tab};

/// Высота шапки: строка вкладок + 2 строки статуса.
pub const HEADER_ROWS: u16 = 3;
/// Высота подвала: 2 строки подсказок + рамка.
pub const FOOTER_ROWS: u16 = 4;

/// Вкладка под колонкой `col` (клик по строке вкладок). Повторяет раскладку
/// ratatui `Tabs` (0.30): `padding_left=" "` перед каждым заголовком,
/// `padding_right=" "` и делитель `"  "` после него. Итог между заголовками —
/// 4 пробела; заголовки идут с отступа 1.
pub fn tab_at_col(col: u16) -> Option<usize> {
    let mut x: u16 = 1; // padding_left первого заголовка
    for (i, t) in Tab::ALL.iter().enumerate() {
        let w = t.title().len() as u16;
        if col >= x && col < x + w {
            return Some(i);
        }
        x += w + 4; // padding_right + divider + padding_left
    }
    None
}

/// Для каждой строки левой колонки Dashboard: индекс устройства или None.
/// Зеркалит вёрстку draw_dashboard (заголовок устройства, каналы, насосы),
/// чтобы клик мыши попадал точно в устройство.
pub fn dashboard_rows_for_devices(app: &App) -> Vec<Option<usize>> {
    let mut map: Vec<Option<usize>> = Vec::new();
    if app.snapshot.is_empty() && !app.is_connected() {
        return map; // окошко "No data" — выбирать нечего
    }
    for (di, dev) in app.snapshot.iter().enumerate() {
        map.push(None); // пустая строка-разделитель
        map.push(Some(di)); // заголовок "◆ Slave N: name"
        for _ in dev.channels.iter() {
            map.push(None);
        }
        if !dev.coils.is_empty() {
            map.push(None); // ""
            map.push(None); // "Pumps:"
            for _ in dev.coils.iter() {
                map.push(None);
            }
        }
    }
    map
}

/// Какой строке простого списка (bordered List) соответствует тела-строка `y`.
pub fn list_index_at(y: u16, len: usize) -> Option<usize> {
    if y == 0 {
        return None; // верхняя рамка
    }
    let idx = (y - 1) as usize;
    if idx < len {
        Some(idx)
    } else {
        None
    }
}

pub fn draw(frame: &mut Frame, app: &mut App) {
    let area = frame.area();
    let [header, body, footer] = Layout::vertical([
        Constraint::Length(HEADER_ROWS),
        Constraint::Min(0),
        Constraint::Length(FOOTER_ROWS),
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
        Tab::Bus => draw_bus(frame, app, body),
        Tab::Log => draw_log(frame, app, body),
        Tab::Help => draw_help(frame, app, body),
    }
}

fn draw_header(frame: &mut Frame, app: &mut App, area: Rect) {
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
    let pipeline = if app.is_emu() {
        "EMULATOR".to_string()
    } else if let Some(addr) = &app.tcp_connected {
        format!("TCP master → {}", addr)
    } else if app.is_connected() {
        app.serial_port.clone().unwrap_or_else(|| "SERIAL".into())
    } else {
        "NOT CONNECTED".to_string()
    };
    let mut status = format!(
        " {} — {} | tick={} | poll: {}",
        app.tab.title(),
        pipeline,
        app.tick,
        app.last_poll_error
            .clone()
            .unwrap_or_else(|| format!("{} devices", app.snapshot.len()))
    );
    if let Some(st) = &app.tcp_server {
        status.push_str(&format!(" | TCP server:{}", st.addr));
    }

    // Индикатор фоновой задачи с анимированным спиннером.
    let busy_style = if let Some(label) = &app.busy {
        app.spin = app.spin.wrapping_add(1);
        const SPINNER: [char; 10] = ['⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏'];
        let c = SPINNER[app.spin % SPINNER.len()];
        status.push_str(&format!(" ⏳ {} {}…", c, label));
        Style::default().fg(Color::Yellow).add_modifier(Modifier::BOLD)
    } else {
        Style::default()
    };

    let block = Block::bordered().title(Span::styled(
        " ESP32 Modbus Test-Bench ",
        Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
    ));
    frame.render_widget(
        Paragraph::new(Line::from(Span::styled(status, busy_style))).block(block),
        status_area,
    );
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
            Tab::Dashboard => "[↑↓] select  [PgUp/PgDn ▾] page  [space] pump",
            Tab::Ports => "[↑↓] select  [c] connect  [d] disconnect  [p] probe  [t] TCP-server",
            Tab::Registers => "[↑↓] focus  [enter] edit  [r] read  [w] write  [t] type",
            Tab::Sensors if app.sensor_manage => {
                "[↑↓] move  [d] delete device (◆) or sensor (●)  [esc] done"
            }
            Tab::Sensors => {
                "[↑↓] focus  [enter] edit  [1..6] type  [a] add  [n] new slave  [m] manage  [d] delete"
            }
            Tab::Firmware => "[i] board-info  [f] flash  [b] backup  [o] restore",
            Tab::Bus => "[↑↓/wheel] scroll history (auto-follows newest)",
            Tab::Log => "[↑↓/wheel] scroll  [enter] clear",
            Tab::Help => "[↑↓/wheel] scroll",
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

        // Coils (дискретные выходы/включение каналов).
        if !dev.coils.is_empty() {
            lines.push(Line::from(""));
            lines.push(Line::from(Span::styled(
                "   Coils:",
                Style::default().fg(Color::Yellow),
            )));
            for (ci, c) in dev.coils.iter().enumerate() {
                let on = *c;
                let (sym, col) = if on {
                    ("● ON ", Color::Green)
                } else {
                    ("○ OFF", Color::Red)
                };
                let lbl = format!("   Coil {}: {}", ci, sym);
                lines.push(Line::from(vec![
                    Span::styled(lbl, Style::default().fg(col).add_modifier(Modifier::BOLD)),
                    Span::styled(
                        if selected_now && ci == 0 {
                            "  [space]  ◀ toggles this coil"
                        } else {
                            ""
                        },
                        Style::default().fg(Color::DarkGray),
                    ),
                ]));
            }
        }
    }

    // Автоскролл левой колонки. Ручная прокрутка (колесо/→ в app.mouse_scroll)
    // не перебивается: заголовок выбранного устройства подтягиваем на экран
    // только когда выбор сменился. Нижняя граница держится всегда,
    // чтобы выбранное устройство нельзя было «увести» вниз.
    let map = dashboard_rows_for_devices(app);
    let mut header_at = 0usize;
    for (i, r) in map.iter().enumerate() {
        if *r == Some(selected) {
            header_at = i;
            break;
        }
    }
    let page = left.height.saturating_sub(2) as usize;
    let max_scroll = lines.len().saturating_sub(page);
    let mut scroll = if map.is_empty() {
        0
    } else {
        app.dash_scroll.min(max_scroll)
    };
    if header_at >= scroll + page {
        scroll = header_at + 1 - page;
    }
    if app.dash_sel_seen != selected {
        if header_at < scroll {
            scroll = header_at;
        }
        app.dash_sel_seen = selected;
    }
    app.dash_scroll = scroll;
    let render: Vec<Line> = lines[scroll..(scroll + page).min(lines.len())].to_vec();
    let device_title = if scroll > 0 {
        format!(" Devices [scrolled {}..] ", scroll + page)
    } else {
        " Devices ".to_string()
    };
    let device_block = Block::bordered().title(Span::styled(
        device_title,
        Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
    ));
    frame.render_widget(Paragraph::new(render).block(device_block), left);

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
    let [serial_area, tcp_area] =
        Layout::horizontal([Constraint::Percentage(60), Constraint::Percentage(40)]).areas(area);

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
    frame.render_stateful_widget(list, serial_area, &mut app.port_state());

    draw_tcp_master(frame, app, tcp_area);
}

/// Правая панель вкладки Ports: Modbus TCP master (клиент).
/// Подключает TUI к внешнему устройству по Ethernet — второй путь к тем же
/// данным (рядом с RTU-опросом): входные float32, coils, holding.
fn draw_tcp_master(frame: &mut Frame, app: &mut App, area: Rect) {
    let editing = app.is_editing();
    let inner = area;
    let mut lines = vec![
        Line::from(Span::styled(
            " Modbus TCP master",
            Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
        )),
        Line::from(""),
    ];
    match &app.tcp_connected {
        Some(addr) => {
            let unit = app.reg_form.slave_id.parse::<u8>().ok();
            let unit_str = unit.map(|u| format!("unit {} ", u)).unwrap_or_default();
            lines.push(Line::from(vec![
                Span::styled(" ● ", Style::default().fg(Color::Green)),
                Span::styled(
                    format!("Connected to {} ({})", addr, unit_str.trim()),
                    Style::default().fg(Color::Green).add_modifier(Modifier::BOLD),
                ),
            ]));
            lines.push(Line::from(Span::styled(
                " [o] disconnect",
                Style::default().fg(Color::DarkGray),
            )));
        }
        None => {
            lines.push(Line::from(vec![
                Span::styled(" ○ ", Style::default().fg(Color::Red)),
                Span::styled(
                    "No TCP connection",
                    Style::default().fg(Color::Red),
                ),
            ]));
            lines.push(Line::from(Span::styled(
                " [y] connect after editing host/port below",
                Style::default().fg(Color::DarkGray),
            )));
        }
    };
    lines.push(Line::from(""));
    lines.push(Line::from(Span::styled(
        " Target device (host:port):",
        Style::default().fg(Color::Yellow),
    )));
    lines.push(field_line(
        "Host",
        &app.tcp_host,
        app.tcp_focus == 0,
        editing && matches!(app.editing, Some(crate::app::FieldEdit::Tcp(0))),
    ));
    lines.push(field_line(
        "Port",
        &app.tcp_port_str,
        app.tcp_focus == 1,
        editing && matches!(app.editing, Some(crate::app::FieldEdit::Tcp(1))),
    ));
    lines.push(Line::from(Span::styled(
        " ←/→ focus   [enter] edit   [y] connect   [o] disconnect",
        Style::default().fg(Color::DarkGray),
    )));
    lines.push(Line::from(""));
    let poll_style = if app.is_emu() {
        Color::DarkGray
    } else if app.tcp_connected.is_some() {
        Color::Green
    } else {
        Color::DarkGray
    };
    lines.push(Line::from(Span::styled(
        "Polled map: input 0..63 (32×float32), coils, holding.",
        Style::default().fg(poll_style),
    )));
    lines.push(Line::from(Span::styled(
        "Slave ID in MBAP header = register editor's 'Slave ID'.",
        Style::default().fg(Color::DarkGray),
    )));

    let block = Block::bordered().title(Span::styled(
        " Modbus TCP master ",
        Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
    ));
    frame.render_widget(Paragraph::new(lines).block(block), inner);
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

    let tool = app
        .fw_tool
        .clone()
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

// --- BUS ---

/// Обрезает длинную hex-строку до ширины терминала (кадр ответа читаемого
/// регистра — 125 байт → 374 hex-символа, в строку не влезает).
fn clip_hex(s: &str, max: usize) -> String {
    if s.len() <= max {
        s.to_string()
    } else if max <= 4 {
        format!("{}…", &s[..max.clamp(1, 2)])
    } else {
        format!("{}…", &s[..max - 1])
    }
}

fn draw_bus(frame: &mut Frame, app: &mut App, area: Rect) {
    let page_h = area.height.saturating_sub(2) as usize;
    let total = app.trace.len();
    let page = page_h.max(1);

    // 0 = живой хвост (последние кадры); N>0 = «на N экранов вглубь истории».
    let max_start = total.saturating_sub(page_h);
    let start = if app.bus_scroll == 0 {
        max_start
    } else {
        max_start.saturating_sub(app.bus_scroll * page).min(max_start)
    };

    let hex_max = (area.width.saturating_sub(6)) as usize;
    let mut lines: Vec<Line> = Vec::new();
    let transport_label = |t: &str| if t == "TCP" { "TCP (MBAP, no CRC)".to_string() } else { "CRC ok".to_string() };

    if total == 0 {
        lines.push(Line::from(Span::styled(
            " No Modbus traffic yet.",
            Style::default().fg(Color::DarkGray),
        )));
        let hint = if app.is_emu() {
            " Emulator: кадры появляются при каждом опросе (или после [e])."
        } else if app.is_connected() {
            " Ждём первый опрос — откройте вкладку Dashboard и подождите 0.5 сек."
        } else {
            " Запустите эмулятор ([e]) или подключите порт ([c] на вкладке Ports)."
        };
        lines.push(Line::from(Span::styled(hint, Style::default().fg(Color::DarkGray))));
        lines.push(Line::from(Span::styled(
            " Каждый опрос = 3 транзакции по шине: READ INPUT (T/P), READ COILS, READ HOLDING.",
            Style::default().fg(Color::DarkGray),
        )));
        lines.push(Line::from(Span::styled(
            " TCP-запросы от клиентов (вкладка Ports → [t]) тоже попадают сюда, помеченные TCP.",
            Style::default().fg(Color::DarkGray),
        )));
    }

    // Каждая транзакция занимает 2–3 строки (заголовок, request, response).
    let block_lines = (page_h.saturating_sub(2) / 2).min(total.max(1));
    for entry in app.trace.iter().skip(start).take(block_lines) {
        if entry.ok {
            lines.push(Line::from(vec![
                Span::styled(
                    format!(" ▸ {}", entry.fc),
                    Style::default().fg(Color::Green).add_modifier(Modifier::BOLD),
                ),
                Span::styled(
                    format!(
                        "   req {} B → resp {} B · {} ms · {}",
                        (entry.req.len() + 1) / 3,
                        (entry.resp.len() + 1) / 3,
                        entry.ms,
                        transport_label(entry.transport),
                    ),
                    Style::default().fg(Color::DarkGray),
                ),
            ]));
        } else {
            lines.push(Line::from(vec![
                Span::styled(
                    format!(" ✖ {}", entry.fc),
                    Style::default().fg(Color::Red).add_modifier(Modifier::BOLD),
                ),
                Span::styled(
                    format!(
                        "   req {} B → {} · {} ms",
                        (entry.req.len() + 1) / 3,
                        entry.err.as_deref().unwrap_or("no response"),
                        entry.ms
                    ),
                    Style::default().fg(Color::Red),
                ),
            ]));
        }

        lines.push(Line::from(vec![
            Span::styled("   → ", Style::default().fg(Color::DarkGray)),
            Span::styled(clip_hex(&entry.req, hex_max), Style::default().fg(Color::Cyan)),
        ]));
        if !entry.resp.is_empty() {
            lines.push(Line::from(vec![
                Span::styled("   ← ", Style::default().fg(Color::DarkGray)),
                Span::styled(clip_hex(&entry.resp, hex_max), Style::default().fg(Color::Yellow)),
            ]));
        }
    }

    let title = if total > 0 {
        format!(
            " Bus — Modbus frame log (RTU hex + TCP MBAP) ({}) (↑↓/wheel){} ",
            total,
            if app.bus_scroll > 0 {
                format!(" [scrolled {} screens]", app.bus_scroll)
            } else {
                String::new()
            }
        )
    } else {
        " Bus — Modbus frame log (RTU hex + TCP MBAP) ".to_string()
    };
    let block = Block::bordered().title(Span::styled(
        title,
        Style::default().fg(Color::Magenta).add_modifier(Modifier::BOLD),
    ));
    frame.render_widget(Paragraph::new(lines).block(block), area);
}

// --- LOG ---

fn draw_log(frame: &mut Frame, app: &mut App, area: Rect) {
    let page_h = area.height.saturating_sub(2) as usize;
    let total = app.logs.len();
    let start = app.log_scroll.min(total.saturating_sub(page_h));
    let rows: Vec<ListItem> = app
        .logs
        .iter()
        .skip(start)
        .take(page_h)
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
    let block = Block::bordered().title(format!(
        " Log ({}){} ",
        total,
        if start > 0 { format!(" [scrolled {}..{}]", start, start + rows.len()) } else { String::new() }
    ));
    frame.render_widget(List::new(rows).block(block), area);
}

// --- HELP ---

fn draw_help(frame: &mut Frame, app: &mut App, area: Rect) {
    let text = help_lines();
    let page_h = area.height.saturating_sub(2) as usize; // рамка блока
    let start = app.help_scroll.min(text.len().saturating_sub(page_h));
    let slice = &text[start..(start + page_h).min(text.len())];
    let block = if start > 0 {
        Block::bordered().title(" Help [scrolled] ↑↓/wheel ")
    } else {
        Block::bordered()
    };
    frame.render_widget(Paragraph::new(slice.to_vec()).block(block), area);
}

fn help_lines() -> Vec<Line<'static>> {
    vec![
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
        Line::from("   Mouse: click a tab to switch; click a list row/field to select;"),
        Line::from("          scroll wheel to move selection or scroll Log/Help"),
        Line::from(""),
        Line::from(Span::styled(
            " DASHBOARD",
            Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
        )),
        Line::from("   [↑/↓]  or [k/j]    — select device (▸ marker)"),
        Line::from("   [space]            — toggle coil 0 of the selected device"),
        Line::from("   Mouse wheel        — scroll the whole sensor list (line by line)"),
        Line::from(""),
        Line::from(Span::styled(
            " PORTS",
            Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
        )),
        Line::from("   [↑/↓]  or [k/j]    — select port"),
        Line::from("   [c]                — connect serial port"),
        Line::from("   [d]                — disconnect serial"),
        Line::from("   [p]                — probe Modbus on selected port"),
        Line::from("   [t]                — toggle Modbus TCP server on 127.0.0.1:1502"),
        Line::from("                      (serves the emulator register map — the future Zynq"),
        Line::from("                       can read the same data over Ethernet, no RS-485)"),
        Line::from("   Modbus TCP master (right pane):"),
        Line::from("   [←/→]              — focus Host / Port field"),
        Line::from("   [enter]            — edit focused field"),
        Line::from("   [y]                — connect to the host:port (unit = Slave ID of"),
        Line::from("                        register editor)"),
        Line::from("   [o]                — disconnect TCP master"),
        Line::from("   (master polls input 0..63 as 32×float32 channels, coils, holding —"),
        Line::from("    so you can watch your AI-32 over Ethernet instead of RS-485)"),
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
            " BUS (Modbus frame inspector)",
            Style::default().fg(Color::Magenta).add_modifier(Modifier::BOLD),
        )),
        Line::from("   Shows every request (→) and response (←) as raw bytes with the"),
        Line::from("   parsed function name and round-trip time. RTU frames carry the CRC;"),
        Line::from("   TCP frames (clients on Ports→[t], traffic of the TCP master too) are"),
        Line::from("   marked \"TCP\" and shown with their MBAP header instead (tid proto len unit)."),
        Line::from("   [↑/↓] or wheel     — walk history; scroll down to return to the live tail."),
        Line::from("   In emulator mode the frames are built from real device state, so the"),
        Line::from("   wire bytes look identical to the real bus. Read them like the real master"),
        Line::from("   (Zynq/PLC) will: [slave] [fc] [data...] [crc_lo crc_hi]."),
        Line::from(""),
        Line::from(Span::styled(
            " Idea: hot-plug any ESP32 — it appears in Ports, get probed,",
            Style::default().fg(Color::DarkGray),
        )),
        Line::from(Span::styled(
            " becomes connected, and you see its live registers.",
            Style::default().fg(Color::DarkGray),
        )),
    ]
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn tab_at_col_maps_tabs() {
        // Реальная раскладка Tabs(ratatui 0.30): padding_left(1) + титул + gap(4).
        // Dashboard[1,10) Ports[14,19) Registers[23,32) Sensors[36,43)
        // Firmware[47,55) Bus[59,62) Log[66,69) Help[73,77).
        assert_eq!(tab_at_col(1), Some(0)); // 'D'
        assert_eq!(tab_at_col(9), Some(0)); // последний символ Dashboard
        assert_eq!(tab_at_col(14), Some(1)); // 'P' Ports
        assert_eq!(tab_at_col(18), Some(1)); // последний символ Ports
        assert_eq!(tab_at_col(23), Some(2)); // Registers
        assert_eq!(tab_at_col(36), Some(3)); // Sensors
        assert_eq!(tab_at_col(47), Some(4)); // 'F' Firmware
        assert_eq!(tab_at_col(54), Some(4)); // последний символ Firmware
        assert_eq!(tab_at_col(59), Some(5)); // 'B' Bus
        assert_eq!(tab_at_col(61), Some(5)); // последний символ Bus
        assert_eq!(tab_at_col(66), Some(6)); // Log
        assert_eq!(tab_at_col(73), Some(7)); // Help
        // Пробелы-отступы/разделители вне заголовков — без вкладки.
        assert_eq!(tab_at_col(0), None);
        assert_eq!(tab_at_col(13), None);
        assert_eq!(tab_at_col(u16::MAX), None);
    }

    #[test]
    fn list_index_at_borders() {
        assert_eq!(list_index_at(0, 5), None); // рамка
        assert_eq!(list_index_at(1, 5), Some(0));
        assert_eq!(list_index_at(5, 5), Some(4));
        assert_eq!(list_index_at(6, 5), None); // ниже списка
    }

    #[test]
    fn dashboard_rows_map_has_device_headers() {
        let mut app = App::new();
        app.connect_emulator();
        let map = dashboard_rows_for_devices(&app);
        let headers: Vec<usize> = map.iter().flatten().copied().collect();
        assert_eq!(headers, vec![0, 1, 2, 3]);
        // Снимок паддит coils до 8 на каждое устройство, поэтому у каждого:
        // 1 разделитель + заголовок + 3 канала + 1 разделитель + "Pumps:" + 8 = 15.
        assert_eq!(map.len(), 4 * 15);
        // Первое устройство: разделитель(0), заголовок(1), каналы(2..4),
        // разделитель(5), "Pumps:"(6), насосы(7..14), разделитель(15).
        assert_eq!(map[0], None);
        assert_eq!(map[1], Some(0));
        assert_eq!(map[2], None);
        assert_eq!(map[6], None);
        assert_eq!(map[14], None);
        assert_eq!(map[15], None);
        assert_eq!(map[16], Some(1));
    }

    #[test]
    fn dashboard_rows_map_none_when_disconnected_empty() {
        let app = App::new();
        assert!(!app.is_connected());
        assert!(app.snapshot.is_empty());
        assert!(dashboard_rows_for_devices(&app).is_empty());
    }
}
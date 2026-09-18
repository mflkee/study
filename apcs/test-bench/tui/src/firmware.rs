//! Управление прошивкой ESP32: board-info, flash, backup, restore.
//!
//! Вызывает внешний инструмент `espflash` (или `esptool.py`), если он доступен.
//! Всё строго через subprocess — никакой прямой работы с чипом.

use std::path::{Path, PathBuf};
use std::process::Command;

/// Прогресс-колбэк: `(процент, строка из вывода esptool)`.
pub type ProgressCb<'a> = &'a mut dyn FnMut(u32, String);

/// Результат операции с прошивкой.
#[derive(Debug, Clone)]
pub struct FwResult {
    pub ok: bool,
    pub output: String,
    pub error: String,
}

impl FwResult {
    pub fn success(output: String) -> Self {
        Self {
            ok: true,
            output,
            error: String::new(),
        }
    }
    pub fn failure(error: String) -> Self {
        Self {
            ok: false,
            output: String::new(),
            error,
        }
    }
}

/// Пробует найти доступный CLI-инструмент для прошивки.
pub fn find_tool() -> Option<String> {
    for name in ["espflash", "esptool", "esptool.py"] {
        if command_exists(name) {
            return Some(name.to_string());
        }
    }
    None
}

/// esptool v5: `--port` идёт глобальным флагом ДО подкоманды, у него нет
/// команд `board-info`/`--save-image` (это синтаксис espflash).
fn is_esptool(tool: &str) -> bool {
    tool == "esptool" || tool == "esptool.py"
}

fn command_exists(name: &str) -> bool {
    // `--help` понятен и esptool v5 (опции `--version` у него нет),
    // и espflash, и esptool.py.
    Command::new(name)
        .arg("--help")
        .output()
        .map(|out| out.status.success())
        .unwrap_or(false)
}

/// Запускает команду и собирает stdout+stderr.
fn run(tool: &str, args: &[&str], cwd: Option<&Path>) -> FwResult {
    let mut cmd = Command::new(tool);
    cmd.args(args);
    if let Some(cwd) = cwd {
        cmd.current_dir(cwd);
    }
    match cmd.output() {
        Ok(out) => {
            let stdout = String::from_utf8_lossy(&out.stdout).to_string();
            let stderr = String::from_utf8_lossy(&out.stderr).to_string();
            if out.status.success() {
                FwResult::success(format!("{}{}", stdout, stderr))
            } else {
                FwResult::failure(format!("{}{}", stdout, stderr))
            }
        }
        Err(e) => FwResult::failure(e.to_string()),
    }
}

/// Достаёт процентаж из строки esptool («99.5%», «45.0 %»).
fn parse_pct(line: &str) -> Option<u32> {
    let idx = line.find('%')?;
    let mut end = idx;
    while end > 0 && line.as_bytes()[end - 1] == b' ' {
        end -= 1; // esptool местами пишет «45.0 %» (пробел перед %)
    }
    let head = &line[..end];
    let num: String = head
        .chars()
        .rev()
        .take_while(|c| c.is_ascii_digit() || *c == '.')
        .collect();
    if num.is_empty() {
        return None;
    }
    let f: f64 = num.chars().rev().collect::<String>().parse().ok()?;
    Some((f.round() as u32).min(100))
}

/// Запускает команду, стримя живые строки прогресса из вывода
/// (esptool пишет «Reading… 45.0 %», «Writing… 100.0 %»). Читает
/// параллельно и stdout, и stderr — чтобы не зависеть от того, куда
/// именно инструмент шлёт прогресс.
fn run_progress(
    tool: &str,
    args: &[&str],
    cwd: Option<&Path>,
    progress: ProgressCb,
) -> FwResult {
    use std::io::Read;
    use std::process::Stdio;
    use std::sync::mpsc::channel;
    use std::thread;

    let mut cmd = Command::new(tool);
    cmd.args(args);
    if let Some(cwd) = cwd {
        cmd.current_dir(cwd);
    }
    cmd.stdout(Stdio::piped()).stderr(Stdio::piped());
    let mut child = match cmd.spawn() {
        Ok(c) => c,
        Err(e) => return FwResult::failure(e.to_string()),
    };

    /// Читает поток, режет по '\n' и '\r', шлёт непустые строки в канал.
    fn pump<R: Read + Send + 'static>(mut r: R, tx: std::sync::mpsc::Sender<String>) {
        thread::spawn(move || {
            let mut buf = [0u8; 4096];
            let mut carry = String::new();
            loop {
                let n = match r.read(&mut buf) {
                    Ok(0) => break,
                    Ok(n) => n,
                    Err(_) => break,
                };
                carry.push_str(&String::from_utf8_lossy(&buf[..n]));
                while let Some(pos) = carry.find(['\n', '\r']) {
                    let chunk = carry[..pos].to_string();
                    carry = carry[pos + 1..].to_string();
                    let t = chunk.trim();
                    if !t.is_empty() {
                        let _ = tx.send(t.to_string());
                    }
                }
            }
            let t = carry.trim();
            if !t.is_empty() {
                let _ = tx.send(t.to_string());
            }
        });
    }

    let (tx, rx) = channel::<String>();
    let stderr = child.stderr.take().expect("stderr");
    let stdout = child.stdout.take().expect("stdout");
    pump(stderr, tx.clone());
    pump(stdout, tx.clone());
    drop(tx);

    let mut out_buf = String::new();
    let mut last_pct: Option<u32> = None;
    for chunk in rx {
        let t = chunk.trim();
        if t.is_empty() {
            continue;
        }
        if let Some(p) = parse_pct(t) {
            // Только смена процента — статус обновляет вызывающий.
            if last_pct != Some(p) {
                last_pct = Some(p);
                progress(p, t.to_string());
            }
        } else if out_buf.len() < 16384 {
            out_buf.push_str(t);
            out_buf.push('\n');
        }
    }

    let status = child.wait();
    match status {
        Ok(st) if st.success() => FwResult::success(out_buf),
        Ok(_) => FwResult::failure(out_buf),
        Err(e) => FwResult::failure(format!("{}{}", e, out_buf)),
    }
}

/// Получает информацию о чипе/плате.
pub fn board_info(port: &str) -> FwResult {
    match find_tool() {
        Some(tool) if is_esptool(&tool) => {
            run(&tool, &["--port", port, "chip-id"], None)
        }
        Some(tool) => run(&tool, &["board-info", "--port", port], None),
        None => FwResult::failure(
            "No esptool available. Install: pip install esptool OR cargo install espflash".into(),
        ),
    }
}

/// Прошивает бинарник в ESP32 по адресу 0x0.
pub fn flash(port: &str, bin_path: &Path, progress: ProgressCb) -> FwResult {
    let Some(tool) = find_tool() else {
        return FwResult::failure(
            "No esptool available. Install: pip install esptool OR cargo install espflash".into(),
        );
    };
    let bin = bin_path.to_string_lossy().to_string();
    if is_esptool(&tool) {
        run_progress(
            &tool,
            &["-b", "921600", "--port", port, "write-flash", "0x0", bin.as_str()],
            None,
            progress,
        )
    } else {
        run_progress(
            &tool,
            &["write-flash", "--port", port, "0x0", bin.as_str()],
            None,
            progress,
        )
    }
}

/// Определяет размер флеша чипа в МБ (esptool flash-id).
fn detect_flash_size_mb(tool: &str, port: &str) -> Option<usize> {
    let r = run(tool, &["--port", port, "flash-id"], None);
    if !r.ok {
        return None;
    }
    parse_flash_size_mb(&r.output)
}

/// Вытаскивает размер в МБ из вывода esptool («Detected flash size: 8MB»).
fn parse_flash_size_mb(output: &str) -> Option<usize> {
    output.lines().find_map(|l| {
        let l = l.trim();
        l.strip_prefix("Detected flash size:")
            .and_then(|s| s.trim().trim_end_matches("MB").trim().parse::<usize>().ok())
    })
}

/// Сохраняет дамп флеша в файл (backup). Размер определяется автоматически
/// (esptool flash-id), при неудаче — fallback `flash_size_mb` МБ.
pub fn backup(port: &str, out_path: &Path, flash_size_mb: u32, progress: ProgressCb) -> FwResult {
    match find_tool() {
        Some(tool) => {
            // Правильный пересчёт: 4MB = 0x400000 (а не 0x1000*1024*4 = 16MB!).
            let auto_mb = if is_esptool(&tool) {
                detect_flash_size_mb(&tool, port)
            } else {
                None
            };
            let mb = auto_mb.unwrap_or(flash_size_mb as usize);
            let size = 0x100000usize * mb;
            let out = out_path.to_string_lossy().to_string();
            let size_str = size.to_string();
            if is_esptool(&tool) {
                run_progress(
                    &tool,
                    &[
                        "-b",
                        "921600",
                        "--port",
                        port,
                        "read-flash",
                        "0x0",
                        size_str.as_str(),
                        out.as_str(),
                    ],
                    None,
                    progress,
                )
            } else {
                run(
                    &tool,
                    &[
                        "read-flash",
                        "--port",
                        port,
                        "--save-image",
                        "0x0",
                        size_str.as_str(),
                        out.as_str(),
                    ],
                    None,
                )
            }
        }
        None => FwResult::failure(
            "No esptool available. Install: pip install esptool OR cargo install espflash".into(),
        ),
    }
}

/// Восстанавливает дамп флеша из файла.
pub fn restore(port: &str, bin_path: &Path, progress: ProgressCb) -> FwResult {
    flash(port, bin_path, progress)
}

/// Папка для бэкапов (dirs::home_dir()/esp32-backups).
pub fn backup_dir() -> PathBuf {
    let home = dirs::home_dir().unwrap_or_else(|| PathBuf::from("."));
    let dir = home.join("esp32-backups");
    std::fs::create_dir_all(&dir).ok();
    dir
}

/// Путь к прошиваемому образу (merged .bin для offset 0x0).
pub fn test_firmware_bin() -> Option<PathBuf> {
    let bin = project_root().join("firmware").join("esp32-modbus-fw.bin");
    bin.exists().then_some(bin)
}

/// Корень проекта (test-bench): TUI лежит в test-bench/tui.
fn project_root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .parent()
        .map(|p| p.to_path_buf())
        .unwrap_or_else(|| PathBuf::from("."))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_flash_size_handles_esptool_output() {
        let out = "esptool v5.4.0\nSerial port /dev/ttyACM1:\nConnecting....\nDetected flash size: 8MB\n";
        assert_eq!(parse_flash_size_mb(out), Some(8));
        assert_eq!(parse_flash_size_mb("nothing here"), None);
        assert_eq!(parse_flash_size_mb("Detected flash frequency: 80MHz"), None);
    }

    #[test]
    fn backup_size_math_is_megabytes() {
        // Раньше 0x1000*1024*4 давал 16MB вместо 4MB.
        assert_eq!(0x100000usize * 4, 0x400000);
        assert_eq!(0x100000usize * 8, 0x800000);
    }

    #[test]
    fn parse_pct_reads_esptool_progress() {
        assert_eq!(
            parse_pct("Reading from 0x000fe000 ━━━━━━━━━╸ 99.5% 15.99MB/16.00MB [3:44]"),
            Some(100)
        );
        assert_eq!(parse_pct("Writing at 0x00003000... (45.0 %)"), Some(45));
        assert_eq!(parse_pct("Hard resetting via RTS pin..."), None);
        assert_eq!(parse_pct(""), None);
    }
}
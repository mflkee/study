//! Управление прошивкой ESP32: board-info, flash, backup, restore.
//!
//! Вызывает внешний инструмент `espflash` (или `esptool.py`), если он доступен.
//! Всё строго через subprocess — никакой прямой работы с чипом.

use std::path::{Path, PathBuf};
use std::process::Command;

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
    for name in ["espflash", "esptool.py", "esptool"] {
        if command_exists(name) {
            return Some(name.to_string());
        }
    }
    None
}

fn command_exists(name: &str) -> bool {
    Command::new(name)
        .arg("--version")
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

/// Получает информацию о чипе/плате.
pub fn board_info(port: &str) -> FwResult {
    match find_tool() {
        Some(tool) => run(&tool, &["board-info", "--port", port], None),
        None => FwResult::failure(
            "No esptool available. Install: pip install esptool OR cargo install espflash".into(),
        ),
    }
}

/// Прошивает бинарник в ESP32 по адресу 0x0.
pub fn flash(port: &str, bin_path: &Path) -> FwResult {
    let Some(tool) = find_tool() else {
        return FwResult::failure(
            "No esptool available. Install: pip install esptool OR cargo install espflash".into(),
        );
    };
    let bin = bin_path.to_string_lossy().to_string();
    let out = run(&tool, &["write-flash", "--port", port, "0x0", bin.as_str()], None);
    out
}

/// Сохраняет дамп флеша в файл (backup). Полный размер флеша 4MB.
pub fn backup(port: &str, out_path: &Path, flash_size_mb: u32) -> FwResult {
    match find_tool() {
        Some(tool) => {
            let size = 0x1000usize * 1024 * flash_size_mb as usize; // 0x400000 для 4MB
            let out = out_path.to_string_lossy().to_string();
            let size_str = size.to_string();
            let args = vec![
                "read-flash",
                "--port",
                port,
                "--save-image",
                "0x0",
                size_str.as_str(),
                out.as_str(),
            ];
            run(&tool, &args, None)
        }
        None => FwResult::failure(
            "No esptool available. Install: pip install esptool OR cargo install espflash".into(),
        ),
    }
}

/// Восстанавливает дамп флеша из файла.
pub fn restore(port: &str, bin_path: &Path) -> FwResult {
    flash(port, bin_path)
}

/// Папка для бэкапов (dirs::home_dir()/esp32-backups).
pub fn backup_dir() -> PathBuf {
    let home = dirs::home_dir().unwrap_or_else(|| PathBuf::from("."));
    let dir = home.join("esp32-backups");
    std::fs::create_dir_all(&dir).ok();
    dir
}

/// Собирает тестовую прошивку esp32-fw (cargo build --release).
pub fn build_test_firmware() -> FwResult {
    let fw_manifest = project_root().join("esp32-fw").join("Cargo.toml");
    if !fw_manifest.exists() {
        return FwResult::failure(format!(
            "esp32-fw/Cargo.toml not found at {} (build firmware first)",
            fw_manifest.display()
        ));
    }
    let out = run(
        "cargo",
        &["build", "--release", "--manifest-path", fw_manifest.to_str().unwrap()],
        None,
    );
    if !out.ok {
        return out;
    }
    FwResult::success(
        "Built test firmware. Flash it via Firmware → Flash test firmware.".to_string(),
    )
}

/// Путь к собранному бинарнику прошивки.
pub fn test_firmware_bin() -> Option<PathBuf> {
    let bin = project_root()
        .join("esp32-fw")
        .join("target")
        .join("xtensa-esp32s3-espidf")
        .join("release")
        .join("esp32-modbus-fw");
    bin.exists().then_some(bin)
}

/// Корень проекта (test-bench): TUI лежит в test-bench/tui.
fn project_root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .parent()
        .map(|p| p.to_path_buf())
        .unwrap_or_else(|| PathBuf::from("."))
}
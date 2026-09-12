//! Обнаружение последовательных портов и ESP32 (cross-platform).
//!
//! Работает через `serialport::available_ports()` — на Linux это /dev/tty*,
//! macOS /dev/cu.*, Windows COM*. Платформо-независимо.

use crate::emulator::now_secs;

/// Известные VID производителей чипов USB-UART, встречающихся на ESP dev-платах.
const ESP_USB_VENDORS: &[u16] = &[
    0x10C4, // Silicon Labs CP210x
    0x1A86, // WCH CH340/CH341
    0x0403, // FTDI FT232
    0x303A, // Espressif (native USB, ESP32-S3)
    0x2341, // Arduino (SAMD)
    0x21A8, // Espressif JTAG? (debug)
    0x2C64, // ?
    0x1A86, // CH9102
    0x10C4, // CP2102N
];

/// Описание одного найденного порта.
#[derive(Debug, Clone)]
pub struct PortInfo {
    pub name: String,
    pub description: String,
    /// TRUE если это похоже на ESP dev-плату (по VID или имени).
    pub is_esp_like: bool,
    /// TRUE если на порту найден отклик Modbus slave (прошивка загружена).
    pub has_firmware: bool,
    /// Время последней проверки.
    pub last_check: f64,
    /// Производитель от USB (если есть).
    pub manufacturer: Option<String>,
    pub product: Option<String>,
    pub serial: Option<String>,
}

impl PortInfo {
    pub fn status_label(&self) -> &'static str {
        if self.is_esp_like && self.has_firmware {
            "ESP32 + FW"
        } else if self.is_esp_like {
            "ESP32 (no FW)"
        } else if self.has_firmware {
            "Modbus"
        } else {
            "port"
        }
    }
}

/// Сканирует доступные порты.
pub fn list_ports() -> Vec<PortInfo> {
    serialport::available_ports()
        .map(|ports| ports.into_iter().map(|p| to_port_info(&p)).collect())
        .unwrap_or_default()
}

fn to_port_info(p: &serialport::SerialPortInfo) -> PortInfo {
    let (is_esp, manufacturer, product, serial) = match &p.port_type {
        serialport::SerialPortType::UsbPort(usb) => {
            let esp = ESP_USB_VENDORS.contains(&usb.vid);
            (
                esp,
                usb.manufacturer.clone(),
                Some(usb.product.clone().unwrap_or_default()).filter(|s| !s.is_empty()),
                Some(usb.serial_number.clone().unwrap_or_default())
                    .filter(|s| !s.is_empty()),
            )
        }
        _ => (false, None, None, None),
    };

    let name_lower = p.port_name.to_lowercase();
    let esp_by_name = name_lower.contains("esp") || name_lower.contains("usb");

    PortInfo {
        name: p.port_name.clone(),
        description: describe_port(p),
        is_esp_like: is_esp || esp_by_name,
        has_firmware: false,
        last_check: 0.0,
        manufacturer,
        product,
        serial,
    }
}

fn describe_port(p: &serialport::SerialPortInfo) -> String {
    match &p.port_type {
        serialport::SerialPortType::UsbPort(usb) => {
            let m = usb.manufacturer.clone().unwrap_or_default();
            let pr = usb.product.clone().unwrap_or_default();
            format!("USB: {} {}", m, pr).trim().to_string()
        }
        serialport::SerialPortType::BluetoothPort => "Bluetooth".to_string(),
        serialport::SerialPortType::PciPort => "PCI".to_string(),
        serialport::SerialPortType::Unknown => "Serial".to_string(),
    }
}

/// Проверяет, есть ли на порту отклик Modbus slave (ID от 1 до 15).
/// Возвращает (responded, first_slave_id).
pub fn probe_modbus(port_name: &str, baud: u32) -> Option<u8> {
    let mut m = crate::master::SerialMaster::open(port_name, baud, 1).ok()?;
    for slave in 1..=15u8 {
        m.set_slave_id(slave);
        // Пробуем прочитать 1 holding register по адресу 0.
        if let Ok(regs) = m.read_holding_registers(0, 1) {
            if !regs.is_empty() {
                return Some(slave);
            }
        }
    }
    None
}

/// Структура-держатель результата сканирования, посылаемого в UI-поток.
#[derive(Debug, Clone)]
pub struct ScanResult {
    pub ports: Vec<PortInfo>,
    pub checked_at: f64,
}

impl ScanResult {
    pub fn new(ports: Vec<PortInfo>) -> Self {
        Self {
            ports,
            checked_at: now_secs(),
        }
    }
}
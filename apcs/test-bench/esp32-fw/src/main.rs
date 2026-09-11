// ESP32 Modbus RTU Slave Firmware
//
// Прошивка ESP32 (HW678) как Modbus RTU Slave.
// Работает по RS-485 через MAX3485.
//
// Распиновка:
//   UART1_TX = GPIO17 → MAX3485 DI
//   UART1_RX = GPIO18 ← MAX3485 RO
//   GPIO4        → MAX3485 DE+RE (управление направлением)
//
// Порт:
//   USB-C (UART) — программирование + serial monitor

#![allow(clippy::single_component_path_imports)]

use esp_idf_hal::gpio::{Output, PinDriver, Level};
use esp_idf_hal::peripherals::Peripherals;
use esp_idf_hal::uart::{self, UartDriver, UartConfig};
use esp_idf_hal::delay::FreeRtos;
use esp_idf_hal::task::block_on;
use esp_idf_sys as _;

use embassy_executor::Spawner;
use embassy_time::{Duration, Timer};

mod crc;
mod register_map;
mod rtu_slave;

use register_map::RegisterMap;
use rtu_slave::handle_frame;
use std::sync::{Arc, RwLock};

// Конфигурация
const SLAVE_ID: u8 = 1;
const BAUD_RATE: u32 = 9600;

fn main() -> anyhow::Result<()> {
    // Настройка логирования
    esp_idf_sys::link_patches();
    esp_idf_svc::log::EspLogger::initialize_default();

    log::info!("ESP32 Modbus RTU Slave starting...");
    log::info!("Configuration:");
    log::info!("  Slave ID: {}", SLAVE_ID);
    log::info!("  Baud rate: {}", BAUD_RATE);
    log::info!("  UART1: GPIO17 (TX), GPIO18 (RX)");
    log::info!("  Direction: GPIO4 (DE/RE)");

    let peripherals = Peripherals::take().unwrap();

    // Настройка UART1
    let config = UartConfig::new()
        .baudrate(BAUD_RATE)
        .data_bits(uart::config::DataBits::DataBits8)
        .parity(uart::config::Parity::ParityNone)
        .stop_bits(uart::config::StopBits::StopBits1);
    
    let mut uart = UartDriver::new(
        peripherals.uart1,
        peripherals.pins.gpio17,   // TX → MAX3485 DI
        peripherals.pins.gpio18,   // RX ← MAX3485 RO
        Option::<esp_idf_hal::gpio::Gpio0>::None,
        Option::<esp_idf_hal::gpio::Gpio0>::None,
        &config,
    )?;
    
    // Настройка GPIO4 для управления направлением RS-485
    let mut dir_pin = PinDriver::output(peripherals.pins.gpio4)?;
    dir_pin.set_low(); // RX mode по умолчанию
    
    log::info!("Initialized UART1 @ {} baud", BAUD_RATE);
    
    // Создаём共享ный map регистров
    let register_map = Arc::new(RwLock::new(RegisterMap::new()));
    
    // Заполняем тестовыми данными (симуляция датчиков)
    init_test_data(&register_map);
    
    log::info!("Test data loaded (simulated sensors)");
    
    // Главный цикл: читаем запросы, отвечаем
    log::info!("Listening for Modbus RTU requests...");
    
    let mut buf = [0u8; 256];
    let mut bytes_collected: usize = 0;
    
    loop {
        // Читаем данные из UART (с таймаутом 100 мс)
        match uart.read(&mut buf[bytes_collected..], Duration::from_millis(100)) {
            Ok(n) if n > 0 => {
                bytes_collected += n;
                
                // Проверяем, есть ли полный кадр (минимум 4 байта)
                if bytes_collected >= 4 {
                    // Пробуем обработать кадр
                    match handle_frame(
                        &buf[..bytes_collected],
                        SLAVE_ID,
                        &register_map,
                        &mut uart,
                        &mut dir_pin,
                    ) {
                        Ok(processed) => {
                            if processed {
                                bytes_collected = 0; // Кадр обработан
                            } else {
                                // Не наш кадр или битый — сдвигаем
                                if bytes_collected > 0 {
                                    buf.copy_within(1.., 0);
                                    bytes_collected -= 1;
                                }
                            }
                        }
                        Err(_) => {
                            bytes_collected = 0;
                        }
                    }
                }
            }
            Ok(_) => {
                // Таймаут — сбрасываем буфер, ищем новый кадр
                if bytes_collected > 0 && buf[0] != SLAVE_ID && buf[0] != 0 {
                    buf.copy_within(1.., 0);
                    bytes_collected -= 1;
                } else if bytes_collected >= 4 {
                    // Проверяем CRC
                    let frame = &buf[..bytes_collected];
                    if crc::verify_crc(frame) {
                        // Это наш кадр, но мы его уже обработали?
                        // Нет — возможно, пришли дополнительные байты
                        let data_len = frame.len() - 2;
                        let mut search_len = bytes_collected;
                        while search_len > 4 {
                            if crc::verify_crc(&buf[..search_len]) {
                                break;
                            }
                            search_len -= 1;
                        }
                        if search_len >= 4 {
                            // Правильный кадр найден
                            match handle_frame(
                                &buf[..search_len],
                                SLAVE_ID,
                                &register_map,
                                &mut uart,
                                &mut dir_pin,
                            ) {
                                Ok(_) => {
                                    bytes_collected = 0;
                                }
                                Err(_) => {
                                    buf.copy_within(search_len.., 0);
                                    bytes_collected -= search_len;
                                }
                            }
                        } else {
                            bytes_collected = 0;
                        }
                    } else {
                        // CRC не совпал — мусор, сбрасываем
                        bytes_collected = 0;
                    }
                }
            }
            Err(e) => {
                log::error!("UART read error: {:?}", e);
                FreeRtos::delay_ms(10);
            }
        }
        
        // Обновляем данные датчиков каждую секунду
        // Небольшая пауза, чтобы не мешать UART
        FreeRtos::delay_ms(1);
    }
}

/// Инициализация тестовых данных (имитация 15+15+2 датчиков)
fn init_test_data(map: &Arc<RwLock<RegisterMap>>) {
    let mut m = map.write().unwrap();
    
    // Temperature sensors (15 штук, 0-29)
    for i in 0..15u16 {
        let temp = 20.0f32 + (i as f32) * 0.5;
        let bytes = temp.to_be_bytes();
        m.set_input_register(i * 2, u16::from_be_bytes([bytes[0], bytes[1]]));
        m.set_input_register(i * 2 + 1, u16::from_be_bytes([bytes[2], bytes[3]]));
    }
    
    // Pressure sensors (15 штук, 30-59)
    for i in 15..30u16 {
        let pressure = 100.0f32 + (i as f32 - 15.0) * 1.5;
        let bytes = pressure.to_be_bytes();
        m.set_input_register(i * 2, u16::from_be_bytes([bytes[0], bytes[1]]));
        m.set_input_register(i * 2 + 1, u16::from_be_bytes([bytes[2], bytes[3]]));
    }
    
    // Holding registers (конфигурация)
    for i in 0..32u16 {
        m.set_holding_register(i, i * 100);
    }
    
    // Coils (2 насоса)
    m.set_coil(0, true);   // Pump 1: ON
    m.set_coil(1, false);  // Pump 2: OFF
}
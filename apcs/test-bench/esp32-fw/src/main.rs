// ESP32 Modbus RTU Slave Firmware — режим «без трансивера»
//
// Прямое подключение к ПК через USB-C (UART0):
//   USB-C (UART/COM) ────► ПК
//
// TX = GPIO43 (U0TXD),  RX = GPIO44 (U0RXD)  ← идут на USB-мост платы
//
// Для работы по RS-485 (с MAX3485) замени содержимое main.rs на main_rs485.rs

#![allow(clippy::single_component_path_imports)]

use esp_idf_hal::delay::FreeRtos;
use esp_idf_hal::peripherals::Peripherals;
use esp_idf_hal::prelude::*;
use esp_idf_hal::uart::{self, UartDriver};

use esp_idf_sys as _;

use std::sync::{Arc, RwLock};

mod crc;
mod register_map;
mod rtu_slave;

use register_map::RegisterMap;
use rtu_slave::handle_frame;

const SLAVE_ID: u8 = 1;
const BAUD_RATE: u32 = 9600;

fn main() -> anyhow::Result<()> {
    esp_idf_sys::link_patches();
    unsafe {
        esp_idf_sys::esp_log_level_set(
            b"*\0".as_ptr().cast(),
            esp_idf_sys::esp_log_level_t_ESP_LOG_INFO,
        );
    }

    log::info!("{}", "=".repeat(60));
    log::info!("ESP32 Modbus RTU Slave (USB/COM mode)");
    log::info!("= no RS-485 transceiver =");
    log::info!("Slave ID: {}, baud: {}", SLAVE_ID, BAUD_RATE);
    log::info!("UART0: GPIO43 (TX), GPIO44 (RX) — USB-C COM port");
    log::info!("{}", "=".repeat(60));

    let peripherals = Peripherals::take().unwrap();

    let config = uart::config::Config::new()
        .baudrate(Hertz(BAUD_RATE))
        .data_bits(uart::config::DataBits::DataBits8)
        .parity_none()
        .stop_bits(uart::config::StopBits::STOP1);

    // UART0 — идёт на USB-мост платы (COM-порт в системе)
    let mut uart = UartDriver::new(
        peripherals.uart0,
        peripherals.pins.gpio43,
        peripherals.pins.gpio44,
        Option::<esp_idf_hal::gpio::Gpio0>::None,
        Option::<esp_idf_hal::gpio::Gpio0>::None,
        &config,
    )?;

    log::info!("UART0 initialized @ {} baud", BAUD_RATE);

    let register_map = Arc::new(RwLock::new(RegisterMap::new()));
    init_test_data(&register_map);
    log::info!("Test data loaded (simulated sensors)");

    let mut buf = [0u8; 256];
    let mut bytes_collected: usize = 0;
    let mut last_update_us = unsafe { esp_idf_sys::esp_timer_get_time() };

    log::info!("Waiting for Modbus RTU frames...");

    loop {
        let now_us = unsafe { esp_idf_sys::esp_timer_get_time() };
        if now_us - last_update_us >= 200_000 {
            update_simulated_data(&register_map, now_us as f64 / 1_000_000.0);
            last_update_us = now_us;
        }

        match uart.read(&mut buf[bytes_collected..], 0) {
            Ok(n) if n > 0 => {
                bytes_collected += n;

                if bytes_collected >= 4 {
                    match handle_frame(
                        &buf[..bytes_collected],
                        SLAVE_ID,
                        &register_map,
                        &mut uart,
                    ) {
                        Ok(processed) => {
                            if processed {
                                bytes_collected = 0;
                            } else {
                                buf.copy_within(1.., 0);
                                bytes_collected -= 1;
                            }
                        }
                        Err(_) => {
                            bytes_collected = 0;
                        }
                    }
                }
            }
            Ok(_) => {
                if bytes_collected > 0 {
                    buf.copy_within(1.., 0);
                    bytes_collected -= 1;
                } else {
                    FreeRtos::delay_ms(1);
                }
            }
            Err(e) => {
                log::error!("UART read error: {:?}", e);
                FreeRtos::delay_ms(10);
            }
        }
    }
}

fn init_test_data(map: &Arc<RwLock<RegisterMap>>) {
    let mut m = map.write().unwrap();

    for i in 0..15u16 {
        let temp = 20.0f32 + (i as f32) * 0.5;
        let bytes = temp.to_be_bytes();
        m.set_input_register(i * 2, u16::from_be_bytes([bytes[0], bytes[1]]));
        m.set_input_register(i * 2 + 1, u16::from_be_bytes([bytes[2], bytes[3]]));
    }

    for i in 15..30u16 {
        let pressure = 100.0f32 + (i as f32 - 15.0) * 1.5;
        let bytes = pressure.to_be_bytes();
        m.set_input_register(i * 2, u16::from_be_bytes([bytes[0], bytes[1]]));
        m.set_input_register(i * 2 + 1, u16::from_be_bytes([bytes[2], bytes[3]]));
    }

    for i in 0..32u16 {
        m.set_holding_register(i, i * 100);
    }

    m.set_coil(0, true);
    m.set_coil(1, false);
}

// «Живые» датчики: значения медленно плавают синусоидой (дрожат на доли),
// как у настоящих датчиков. rel_time_secs — секунды с момента старта.
fn update_simulated_data(map: &Arc<RwLock<RegisterMap>>, rel_time_secs: f64) {
    let mut m = map.write().unwrap();
    let t = rel_time_secs as f32;
    let two_pi = 2.0f32 * std::f32::consts::PI;

    for i in 0..15u16 {
        let idx = i * 2;
        let base = 20.0f32 + (i as f32) * 0.5;
        let period = 16.0f32 + (i as f32) * 1.3;
        let phase = (i as f32) * 0.7;
        let w = two_pi * t / period;
        let temp = base + 0.5 * (w + phase).sin() + 0.15 * (2.0 * w + phase).sin();
        let bytes = temp.to_be_bytes();
        m.set_input_register(idx, u16::from_be_bytes([bytes[0], bytes[1]]));
        m.set_input_register(idx + 1, u16::from_be_bytes([bytes[2], bytes[3]]));
    }

    for i in 15..30u16 {
        let idx = i * 2;
        let base = 100.0f32 + (i as f32 - 15.0) * 1.5;
        let period = 20.0f32 + (i as f32) * 1.5;
        let phase = (i as f32) * 0.9;
        let w = two_pi * t / period;
        let pressure = base + 1.0 * (w + phase).sin();
        let bytes = pressure.to_be_bytes();
        m.set_input_register(idx, u16::from_be_bytes([bytes[0], bytes[1]]));
        m.set_input_register(idx + 1, u16::from_be_bytes([bytes[2], bytes[3]]));
    }
}
// ESP32 Modbus RTU Slave Firmware — режим «RS-485 с трансивером MAX3485»
//
// Используй этот файл, когда подключишь MAX3485 по схеме:
//   UART1_TX = GPIO17 → MAX3485 DI
//   UART1_RX = GPIO18 ← MAX3485 RO
//   GPIO4        → MAX3485 DE+RE (управление направлением)
//
// Чтобы собрать эту версию, замени содержимое main.rs на этот файл.

#![allow(clippy::single_component_path_imports)]

use esp_idf_hal::delay::FreeRtos;
use esp_idf_hal::gpio::{Output, PinDriver};
use esp_idf_hal::peripherals::Peripherals;
use esp_idf_hal::uart::{self, UartDriver, UartConfig};

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
    esp_idf_svc::log::EspLogger::initialize_default();

    log::info!("=".repeat(60));
    log::info!("ESP32 Modbus RTU Slave (RS-485 + MAX3485 mode)");
    log::info!("Slave ID: {}, baud: {}", SLAVE_ID, BAUD_RATE);
    log::info!("UART1: GPIO17 (TX), GPIO18 (RX)");
    log::info!("Direction: GPIO4 (DE/RE)");
    log::info!("=".repeat(60));

    let peripherals = Peripherals::take().unwrap();

    let config = UartConfig::new()
        .baudrate(BAUD_RATE)
        .data_bits(uart::config::DataBits::DataBits8)
        .parity(uart::config::Parity::ParityNone)
        .stop_bits(uart::config::StopBits::StopBits1);

    let mut uart = UartDriver::new(
        peripherals.uart1,
        peripherals.pins.gpio17,
        peripherals.pins.gpio18,
        Option::<esp_idf_hal::gpio::Gpio0>::None,
        Option::<esp_idf_hal::gpio::Gpio0>::None,
        &config,
    )?;

    let mut dir_pin = PinDriver::output(peripherals.pins.gpio4)?;
    dir_pin.set_low();

    log::info!("UART1 initialized @ {} baud", BAUD_RATE);

    let register_map = Arc::new(RwLock::new(RegisterMap::new()));
    init_test_data(&register_map);
    log::info!("Test data loaded (simulated sensors)");

    let mut buf = [0u8; 256];
    let mut bytes_collected: usize = 0;

    log::info!("Waiting for Modbus RTU frames...");

    loop {
        match uart.read(&mut buf[bytes_collected..]) {
            Ok(n) if n > 0 => {
                bytes_collected += n;

                if bytes_collected >= 4 {
                    // Перед отправкой ответа переключаем направление на TX
                    match handle_frame_dir(
                        &buf[..bytes_collected],
                        SLAVE_ID,
                        &register_map,
                        &mut uart,
                        &mut dir_pin,
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
                }
            }
            Err(e) => {
                log::error!("UART read error: {:?}", e);
                FreeRtos::delay_ms(10);
            }
        }
    }
}

fn handle_frame_dir(
    frame: &[u8],
    slave_id: u8,
    map: &Arc<RwLock<RegisterMap>>,
    uart: &mut UartDriver,
    dir_pin: &mut PinDriver<impl Output, esp_idf_hal::gpio::Output>,
) -> Result<bool, ()> {
    // Включаем передатчик перед отправкой ответа
    dir_pin.set_high();
    FreeRtos::delay_ms(2); // время на переключение

    let result = handle_frame(frame, slave_id, map, uart);

    dir_pin.set_low();
    result
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
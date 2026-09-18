//! Modbus RTU slave по UART (через USB-UART мост платы).
//!
//! # RTU за 30 секунд
//!
//! RTU — транспортный уровень Modbus поверх последовательного порта. Кадр:
//!
//! ```text
//! [slave addr 1][fc][data …][CRC low][CRC high]
//! ```
//!
//! - **адрес**: кому кадр (наш `SLAVE_ID` = 1). Слушают все, отвечает один;
//! - **CRC16**: контрольная сумма (`core/src/crc.rs`) — отличаем «битый» кадр
//!   от целого;
//! - ответ — та же шапка (`[адрес][PDU][CRC]`), только PDU — ответ диспетчера.
//!
//! # Что здесь, а что в ядре
//!
//! Разбор PDU с регистрами — чистая логика и живёт в `mini-core`:
//! `registers::dispatch` + `registers::SLAVE_ID`. Этот файл — только тонкая
//! «обёртка»: поток, чтение байтов из UART, сборка кадра, CRC.
//!
//! # Почему UART, а не RS-485
//!
//! У мини-полигона трансивер не обязателен: USB-UART мост платы сам отвечает
//! на FTDI/CP210x — клиент с ПК видит его как `/dev/ttyUSB0`. RS-485 и DE/RE —
//! отдельная история (курс, модуль о шине).

use std::sync::{Arc, RwLock};
use std::thread;

use esp_idf_hal::delay::FreeRtos;
use esp_idf_hal::peripheral::Peripheral;
use esp_idf_hal::prelude::*;
use esp_idf_hal::uart::{self, Uart, UartDriver};

use esp_idf_sys::EspError;

use mini_core::crc;
use mini_core::registers::{build_response, dispatch, exception_pdu, MiniState, SLAVE_ID};

/// Скорость линии. 9600 — де-факто стандарт для RTU на длинных проводах.
pub const BAUD_RATE: u32 = 9600;

/// Поднимает фоновый поток RTU-slave на UART. Он живёт, пока живёт устройство.
pub fn spawn<UART: Uart>(
    state: Arc<RwLock<MiniState>>,
    uart: impl Peripheral<P = UART> + 'static,
    tx: impl Peripheral<P = impl esp_idf_hal::gpio::OutputPin> + 'static,
    rx: impl Peripheral<P = impl esp_idf_hal::gpio::InputPin> + 'static,
) -> Result<(), EspError> {
    // Конфиг линии: 8N1 — 8 бит, без контроля чётности, один стоп-бит.
    let config = uart::config::Config::new()
        .baudrate(Hertz(BAUD_RATE))
        .data_bits(uart::config::DataBits::DataBits8)
        .parity_none()
        .stop_bits(uart::config::StopBits::STOP1);

    // UartDriver — «владелец» UART-периферии. CTS/RTS не используем → None.
    let uart = UartDriver::new(
        uart,
        tx,
        rx,
        Option::<esp_idf_hal::gpio::Gpio0>::None,
        Option::<esp_idf_hal::gpio::Gpio0>::None,
        &config,
    )?;

    // Фоновый поток slave-цикла, крутится вечно.
    thread::Builder::new()
        .name("rtu-slave".into())
        .stack_size(32 * 1024)
        .spawn(move || slave_loop(state, uart))
        .map_err(|_| EspError::from_infallible::<1>())?; // 1 = ESP_FAIL
    Ok(())
}

/// Обрабатывает один RTU-кадр.
///
/// Возвращает:
/// - `Ok(true)` — кадр адресован нам и обработан (поток можно сбросить);
/// - `Ok(false)` — кадр **другой станции** или сбит CRC (не нам, но линия
///   занята — надо пересинхронизироваться);
/// - `Err(())` — повреждённый кадр (короткий).
pub fn handle_frame(
    frame: &[u8],
    slave_id: u8,
    state: &Arc<RwLock<MiniState>>,
    uart: &mut UartDriver,
) -> Result<bool, ()> {
    // Минимальный кадр: unit(1) + fc(1) + crc(2) = 4 байта.
    if frame.len() < 4 {
        return Err(());
    }
    // Адресат не мы — отвечать нельзя (иначе конфликт в сети против шины).
    if frame[0] != slave_id {
        return Ok(false);
    }
    // CRC подтверждает целостность: на «битый» кадр отвечать нельзя.
    if !crc::verify_crc(frame) {
        log::warn!("RTU: CRC ошибка: {:02X?}", frame);
        return Ok(false);
    }

    // PDU кадра — всё, что между адресом и CRC: [fc][data...].
    let pdu = &frame[1..frame.len() - 2];
    let resp = {
        let mut state = state.write().unwrap();
        dispatch(pdu[0], &pdu[1..], &mut state).unwrap_or_else(|code| exception_pdu(pdu[0], code))
    };

    // Собираем ответный кадр: [адрес][PDU][CRC lo][CRC hi].
    let response = build_response(&resp);

    log::debug!("RTU <<< {:02X?} >>> {:02X?}", pdu, &response[..response.len() - 2]);
    write_all(uart, &response)
}

/// Пишет данные в UART «кусками» — лимит порции может отличаться от длины
/// кадра. Плюс задержка в 1 мс — чтобы драйвер успел выдать байты.
fn write_all(uart: &mut UartDriver, data: &[u8]) -> Result<bool, ()> {
    for chunk in data.chunks(64) {
        uart.write(chunk).map_err(|_| ())?;
    }
    FreeRtos::delay_ms(1);
    Ok(true)
}

/// Цикл приёма кадров: читает байты, накапливает в буфер, обрабатывает.
///
/// RTU не сообщает длину кадра заранее (заголовка с длиной нет!), поэтому
/// «ловить» кадр приходится так:
///
/// 1. накапливаем байты, пока не наберётся ≥ 4 (минимум);
/// 2. пробуем обработать `handle_frame`;
/// 3. `Ok(true)` — кадр наш и обработан → сброс буфера;
/// 4. `Ok(false)` — «чужой»/битый → сдвигаем буфер **на 1 байт** и пробуем
///    снова (ищем, где начинается настоящий кадр);
/// 5. `Err(_)` — совсем плохой буфер → очищаем и ждём новых байтов;
/// 6. когда байты не приходят долго, а буфер непуст — тоже сдвигаем на 1,
///    «выкуривая» остатки из буфера.
fn slave_loop(state: Arc<RwLock<MiniState>>, mut uart: UartDriver) {
    let mut buf = [0u8; 256];
    let mut collected: usize = 0;

    loop {
        match uart.read(&mut buf[collected..], 0) {
            Ok(n) if n > 0 => {
                collected += n;
                if collected >= 4 {
                    match handle_frame(&buf[..collected], SLAVE_ID, &state, &mut uart) {
                        Ok(true) => collected = 0,
                        Ok(false) => {
                            buf.copy_within(1.., 0);
                            collected -= 1;
                        }
                        Err(_) => collected = 0,
                    }
                }
            }
            // Нет данных (timeout). Если накопили что-то «недокадр» —
            // сдвигаем и пробуем считывать дальше.
            Ok(_) => {
                if collected > 0 {
                    buf.copy_within(1.., 0);
                    collected -= 1;
                } else {
                    FreeRtos::delay_ms(1);
                }
            }
            Err(e) => {
                log::error!("RTU: read: {:?}", e);
                FreeRtos::delay_ms(10);
            }
        }
    }
}
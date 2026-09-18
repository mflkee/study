//! Modbus RTU Slave по RS-485 (max3485, UART c DE/RE-управлением).
//!
//! # RTU за 30 секунд
//!
//! RTU — «прадедушка» Modbus поверх последовательного порта. Кадр выглядит так:
//!
//! ```text
//! [slave addr][fc][data ...][CRC low][CRC high]
//! ```
//!
//! - **address**: кому предназначен кадр (у нас `SLAVE_ID = 1`). Все слушают,
//!   отвечает только тот, чей адрес совпал;
//! - **CRC16**: контрольная сумма по содержимому — отличаем «битый» кадр
//!   (помехи на проводе) от «чужого адреса»;
//! - ответ строится так же, только адрес тот же, а PDU — ответ диспетчера.
//!
//! # Чем этот модуль отличается от TCP-сервера (которого больше нет)
//!
//! Modbus TCP из модуля убран полностью: интерфейс — только RS-485 + RTU.
//! Это промышленная классика для приборки: одна витая пара, много устройств
//! на одной шине, помехоустойчивость (дифференциальный сигнал).
//! Данные идут **потоком байт** (UART), поэтому главная сложность — собрать
//! из потока ровно один кадр, а потом «пересинхронизироваться», если пришёл
//! мусор/чужой адрес. Разбор самого PDU — снова `ai32-core::register_map`.
//!
//! # К RS-485
//!
//! Линия RS-485 дифференциальная: A/B витой пары. Трансивер MAX3485
//! превращает UART в полудуплексную RS-485:
//!
//! - **TX** ESP32 → DI трансивера;
//! - **RX** ESP32 ← RO трансивера;
//! - **DE/RE** — управление направлением: `/RE` (приём) и `DE` (передача)
//!   объединяют на одну ногу. Перед отправкой — поднять (передача),
//!   после — опустить (приём).
//!
//! В этом каркасе DE/RE-переключение опущено для простоты (обсуждается в
//! лабораторной про шину); сама передача уже готова писать в UART. Подключение
//! GPIO DE/RE — TODO (есть в `ai32/docs/`, глава «Интерфейс RS-485»).

use std::sync::{Arc, RwLock};
use std::thread;

use ai32_core::crc;
use ai32_core::register_map;
use ai32_core::ChannelBank;

use esp_idf_hal::delay::FreeRtos;
use esp_idf_hal::peripheral::Peripheral;
use esp_idf_hal::prelude::*;
use esp_idf_hal::uart::{self, Uart, UartDriver};

use esp_idf_sys::EspError;

/// Адрес модуля в сети RTU (slave id). Модуль отвечает на кадры с `01`.
pub const SLAVE_ID: u8 = 1;
/// Скорость UART (бит/с). 9600 — классика для RTU на длинных линиях.
pub const BAUD_RATE: u32 = 9600;

/// Запускает поток RTU-slave на UART (TX/RX подключены к RS-485 трансиверу).
///
/// `Peripheral` — признак того, что периферию (UART, пины) можно «забрать»
/// себе; это гарантия от двойного использования пина другой периферией.
pub fn spawn<UART: Uart>(
    bank: Arc<RwLock<ChannelBank>>,
    uart: impl Peripheral<P = UART> + 'static,
    tx: impl Peripheral<P = impl esp_idf_hal::gpio::OutputPin> + 'static,
    rx: impl Peripheral<P = impl esp_idf_hal::gpio::InputPin> + 'static,
) -> Result<(), EspError> {
    // Конфигурация линии связи: 8N1 — 8 бит данных, без паритета, 1 стоп-бит.
    let config = uart::config::Config::new()
        .baudrate(Hertz(BAUD_RATE))
        .data_bits(uart::config::DataBits::DataBits8)
        .parity_none()
        .stop_bits(uart::config::StopBits::STOP1);

    // UartDriver — «владелец» UART-периферии на всё время работы.
    // Два `None` — CTS/RTS (мы их не используем).
    let uart = UartDriver::new(
        uart,
        tx,
        rx,
        Option::<esp_idf_hal::gpio::Gpio0>::None,
        Option::<esp_idf_hal::gpio::Gpio0>::None,
        &config,
    )?;

    // Фоновый поток slave-цикла: крутится вечно, читает и отвечает.
    thread::Builder::new()
        .name("rtu-slave".into())
        .stack_size(32 * 1024)
        .spawn(move || slave_loop(bank, uart))
        .map_err(|_| EspError::from_infallible::<1>())?; // 1 = ESP_FAIL
    Ok(())
}

/// Обрабатывает один RTU кадр.
///
/// Возвращает:
/// - `Ok(true)` — кадр адресован нам и обработан (поток можно сбросить);
/// - `Ok(false)` — кадр **другой станции** или сбит CRC (не нам, но линия
///   занята — надо пересинхронизироваться);
/// - `Err(())` — повреждённый кадр (короткий).
pub fn handle_frame(
    frame: &[u8],
    slave_id: u8,
    bank: &Arc<RwLock<ChannelBank>>,
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
    // Диспетчер (общий: и для RTU-запросов, и для будущих расширений) —
    // PDU ответа или код исключения.
    let resp_pdu =
        register_map::dispatch(pdu[0], &pdu[1..], &mut bank.write().unwrap()).unwrap_or_else(
            |code| exception_pdu(pdu[0], code),
        );

    // Собираем ответный кадр: [адрес][PDU][CRC lo][CRC hi].
    let mut response = vec![slave_id];
    response.extend_from_slice(&resp_pdu);
    let response = crc::append_crc(&response);

    log::debug!("RTU <<< {:02X?} >>> {:02X?}", pdu, &response[..response.len() - 2]);
    write_all(uart, &response)
}

/// [fc | 0x80] + код исключения — стандартный формат ошибки Modbus.
fn exception_pdu(fc: u8, code: u8) -> Vec<u8> {
    vec![fc | 0x80, code]
}

/// Пишет данные в UART «кусками» — лимит порции может отличаться от длины
/// кадра. Именаждный byte задержка в 1 мс — чтобы драйвер успел выдать байты.
fn write_all(uart: &mut UartDriver, data: &[u8]) -> Result<bool, ()> {
    for chunk in data.chunks(64) {
        uart.write(chunk).map_err(|_| ())?;
    }
    FreeRtos::delay_ms(1);
    Ok(true)
}

/// Цикл приёма кадров: читает байты, накапливает в буфер, обрабатывает.
///
/// # Синхронизация по потоку
///
/// RTU не сообщает заранее длину кадра — у него нет заголовка с длиной!
/// Поэтому алгоритм «лови кадр»:
///
/// 1. накапливаем байты в `buf`, пока их не наберётся ≥ 4 (минимум);
/// 2. пробуем обработать `handle_frame`;
/// 3. `Ok(true)` — кадр наш и обработан → буфер очищаем;
///    `Ok(false)` — кадр «не наш»/битый → сдвигаем буфер **на 1 байт**
///    влево и пытаемся снова (ищем, где начинается настоящий кадр);
///    `Err` — плохой буфер → просто очищаем и ждём новых байтов;
/// 4. когда байты не приходят, а буфер непуст, тоже сдвигаем на 1 —
///    «выкуриваем» мусор из буфера.
fn slave_loop(bank: Arc<RwLock<ChannelBank>>, mut uart: UartDriver) {
    let mut buf = [0u8; 256];
    let mut collected: usize = 0;

    loop {
        match uart.read(&mut buf[collected..], 0) {
            Ok(n) if n > 0 => {
                collected += n;
                if collected >= 4 {
                    match handle_frame(&buf[..collected], SLAVE_ID, &bank, &mut uart) {
                        Ok(true) => collected = 0,
                        Ok(false) => {
                            // Чужой адрес/битый CRC: сдвигаем на 1 байт и
                            // ищем новый кадр в оставшихся данных.
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
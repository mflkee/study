//! Карта Modbus-регистров модуля AI-32 (24 канала 4-20 мА).
//!
//! # Карта регистров
//!
//! | Функция | Тип      | Диапазон      | Что это                                |
//! |---------|----------|---------------|----------------------------------------|
//! | FC04    | INPUT    | 0..47 (×2)    | Ток по каналу как float32 (2 regs на канал) |
//! | FC03    | HOLDING  | 0..95 (×4)    | Калибровка: gain(2 regs) + offset(2 regs), read-only  |
//! | FC01    | COIL     | 0..23         | Включён ли канал (1 coil = 1 канал)    |
//! | FC02    | DISCR    | 0..23         | Копия coil (для совместимости)          |
//!
//! # Как это устроено «изнутри»
//!
//! - **INPUT** (FC04) формируются «на лету» из [`ChannelBank`]: каждый канал
//!   занимает два 16-битных регистра — старшее и младшее слово `f32` значения
//!   тока в мА (big-endian). 24 канала × 2 = 48 регистров. У выключенного
//!   канала читается `0.0` (см. `channels::current_of`).
//! - **HOLDING** (FC03) отражают калибровку: `gain` и `offset` как big-endian
//!   `f32` по 4 регистра на канал (2 слова × 2). Запись по Modbus **не
//!   предусмотрена** — калибровка задаётся процедурой через NVS, поэтому
//!   FC06 всегда отвечает exception `0x04` (server failure).
//! - **COILS** (FC01/FC02) — битовый массив включённости каналов: запись
//!   `true` через FC05 включает канал, `false` — выключает.
//!
//! # Соглашения Modbus, которые тут используются
//!
//! - все многобайтовые величины — **big-endian** (старший байт первым);
//! - `f32` пакованы как `u32::to_bits()` (битовое представление IEEE-754)
//!   и разбиты на два регистра: старшая половина в нечётный адрес;
//! - коды исключений стандартные: `0x01` функция, `0x02` адрес,
//!   `0x03` значение, `0x04` устройство.
//!
//! Этот модуль — **мост между моделью данных (`ChannelBank`) и протоколом**:
//! его функции не общаются с сетью, только читают/меняют структуры и
//! возвращают PDU или код ошибки. Вызов — из `dispatch` в `ai32/fw`
//! (RTU-сервер гонит сюда все запросы ИВК).

use crate::calib::Calibration;
use crate::channels::{ChannelBank, CHANNEL_COUNT};

/// Число INPUT-регистров (24 канала × 2 регистра float32 = 48).
pub const INPUT_REG_COUNT: u16 = (CHANNEL_COUNT * 2) as u16;

/// Число HOLDING-регистров (24 канала × 4 регистра = 96).
pub const HOLDING_REG_COUNT: u16 = (CHANNEL_COUNT * 4) as u16;

/// Число COILS (1 бит на канал = 24).
pub const COIL_COUNT: u16 = CHANNEL_COUNT as u16;

// ─── Вспомогательные функции ────────────────────────────────────────────

/// Упаковать `f32` в два регистра (big-endian).
///
/// `f32::to_bits()` даёт все 32 бита как `u32`; старшие 16 байт уходят в
/// первый регистр, младшие — во второй. Распаковка выполняется ровно
/// обратной операцией `f32::from_bits`.
#[inline]
fn f32_to_regs(v: f32) -> [u16; 2] {
    let bits = v.to_bits();
    [(bits >> 16) as u16, bits as u16]
}

/// Распаковать два регистра (big-endian) в `f32`.
///
/// Отмечено `dead_code` вне тестов: в прошивке float собирается прямо в
/// симуляторе интрументов, а распаковка нужна только в проверках чтения.
#[inline]
#[cfg_attr(not(test), allow(dead_code))]
fn regs_to_f32(msb: u16, lsb: u16) -> f32 {
    f32::from_bits(((msb as u32) << 16) | lsb as u32)
}

/// Прочитать пару `u16` из PDU по смещению (big-endian: старший, младший).
///
/// `get(offset)?` — чтение через `Option` без паники: если PDU короткий,
/// вернём `None`, а вызывающий превратит его в exception `0x02`.
#[inline]
fn pdu_u16(data: &[u8], offset: usize) -> Option<u16> {
    let hi = *data.get(offset)? as u16;
    let lo = *data.get(offset + 1)? as u16;
    Some((hi << 8) | lo)
}

// ─── Обработка Modbus-запросов ─────────────────────────────────────────

/// FC04 READ INPUT REGISTERS: вернуть `count` регистров, начиная с `start`.
///
/// Адрес `addr` раскладывается на канал и слово: `ch = addr/2`,
/// `is_msb = addr%2==0`. Выключенному каналу отвечаем `0.0` (`unwrap_or(0.0)`)
/// — это контракт «видимости» каналов.
pub fn read_input(bank: &ChannelBank, start: u16, count: u16) -> Result<Vec<u16>, u8> {
    if start + count > INPUT_REG_COUNT {
        return Err(0x02); // Illegal data address
    }
    let mut regs = Vec::with_capacity(count as usize);
    for i in 0..count {
        let addr = start + i;
        let ch = (addr / 2) as usize;
        let is_msb = addr % 2 == 0;
        let ma = bank.current_of(ch).unwrap_or(0.0);
        let pair = f32_to_regs(ma);
        regs.push(if is_msb { pair[0] } else { pair[1] });
    }
    Ok(regs)
}

/// FC03 READ HOLDING REGISTERS: калибровка (gain, offset как f32, read-only).
///
/// 4 регистра на канал: `addr/4` — канал, `addr%4` — слово (± регистры
/// `0=gain_hi, 1=gain_lo, 2=offset_hi, 3=offset_lo`). Если калибровки нет —
/// отдаём идеальную.
pub fn read_holding(bank: &ChannelBank, start: u16, count: u16) -> Result<Vec<u16>, u8> {
    if start + count > HOLDING_REG_COUNT {
        return Err(0x02);
    }
    let mut regs = Vec::with_capacity(count as usize);
    for i in 0..count {
        let addr = start + i;
        let ch = (addr / 4) as usize;
        let sub = addr % 4; // 0=gain_msb, 1=gain_lsb, 2=offset_msb, 3=offset_lsb
        let cal = bank.calibration(ch).unwrap_or(Calibration::default());
        let pair = match sub {
            0..=1 => f32_to_regs(cal.gain),
            _ => f32_to_regs(cal.offset),
        };
        regs.push(if sub % 2 == 0 { pair[0] } else { pair[1] });
    }
    Ok(regs)
}

/// FC01 READ COILS: битовый массив включённости 24 каналов.
pub fn read_coils(bank: &ChannelBank, start: u16, count: u16) -> Result<Vec<bool>, u8> {
    if start + count > COIL_COUNT {
        return Err(0x02);
    }
    let mut coils = Vec::with_capacity(count as usize);
    for i in 0..count {
        let ch = (start + i) as usize;
        coils.push(bank.channels.get(ch).map_or(false, |c| c.enabled));
    }
    Ok(coils)
}

/// FC02 READ DISCRETE INPUTS (копия COILS, для совместимости).
///
/// Формально «дискретные входы» — это физические входы, но у нас единственный
/// вариант битового представления — включённость канала, поэтому просто
/// делегируем FC01.
pub fn read_discrete(bank: &ChannelBank, start: u16, count: u16) -> Result<Vec<bool>, u8> {
    read_coils(bank, start, count)
}

/// FC05 WRITE SINGLE COIL: включение/выключение канала.
///
/// Единственный «пишущий» Modbus-запрос: ИВК может гасить канал (например,
/// вывести его из учета) прямо с диспетчерского поста.
pub fn write_coil(bank: &mut ChannelBank, addr: u16, value: bool) -> Result<(), u8> {
    if addr >= COIL_COUNT {
        return Err(0x02);
    }
    bank.set_enabled(addr as usize, value);
    Ok(())
}

/// FC06 WRITE SINGLE REGISTER (holding): калибровка read-only → exception 0x04.
pub fn write_holding(_bank: &mut ChannelBank, _addr: u16, _value: u16) -> Result<(), u8> {
    // Калибровка задаётся через NVS-процедуру, не через Modbus — иначе
    // любой узел в сети мог бы «подкрутить» коэффициенты канала.
    Err(0x04) // Server failure (read-only)
}

/// Парсинг PDU: извлечь пару (start, count) из запросов FC01..FC04.
///
/// Оба значения u16 big-endian; PDU короче 4 байт → `0x02`. Отдельно
/// проверяется `count`: 0 запрещён, больше 125 — слишком много регистров в
/// одном кадре (лимит Modbus) → `0x03` (illegal data value).
pub fn parse_read_params(data: &[u8]) -> Result<(u16, u16), u8> {
    if data.len() < 4 {
        return Err(0x02);
    }
    let start = pdu_u16(data, 0).ok_or(0x02u8)?;
    let count = pdu_u16(data, 2).ok_or(0x02u8)?;
    if count == 0 || count > 125 {
        return Err(0x03); // Illegal data value
    }
    Ok((start, count))
}

/// Парсинг PDU для FC05/FC06: адрес и значение u16 (4 байта).
pub fn parse_write_single(data: &[u8]) -> Result<(u16, u16), u8> {
    if data.len() < 4 {
        return Err(0x02);
    }
    let addr = pdu_u16(data, 0).ok_or(0x02u8)?;
    let value = pdu_u16(data, 2).ok_or(0x02u8)?;
    Ok((addr, value))
}

// ─── Диспетчер запросов ───────────────────────────────────────────────

/// Диспетчер: принимает FC + data из PDU, модифицирует `bank`, возвращает
/// ответный PDU (beginning with the FC byte) или код исключения.
///
/// Это **единая точка входа** для транспортного протокола модуля:
/// RTU-сервер разбирает свой кадр до PDU, а здесь уже обрабатывает
/// содержимое. Ответный PDU готов к упаковке обратно в RTU.
///
/// ```text
///   ИВК → RTU-кадр → разбор кадра → dispatch(fc, data, bank)
///                                   → PDU ответа → упаковка → ИВК
/// ```
pub fn dispatch(fc: u8, data: &[u8], bank: &mut ChannelBank) -> Result<Vec<u8>, u8> {
    let out = match fc {
        0x01 => {
            let (start, count) = parse_read_params(data)?;
            pdu_bitvec(&read_coils(bank, start, count)?)
        }
        0x02 => {
            let (start, count) = parse_read_params(data)?;
            pdu_bitvec(&read_discrete(bank, start, count)?)
        }
        0x03 => {
            let (start, count) = parse_read_params(data)?;
            pdu_regvec(&read_holding(bank, start, count)?)
        }
        0x04 => {
            let (start, count) = parse_read_params(data)?;
            pdu_regvec(&read_input(bank, start, count)?)
        }
        0x05 => {
            let (addr, value) = parse_write_single(data)?;
            write_coil(bank, addr, value != 0)?;
            // FC05 отвечает эхом запроса (полный кадр «что записали»).
            out_echo(0x05, addr, value)
        }
        0x06 => {
            let (addr, value) = parse_write_single(data)?;
            // Always Err(0x04): `?` сразу вернёт исключение и до эха
            // никогда не дойдёт (`.ok()` здесь не нужен — см. write_holding).
            let _ = write_holding(bank, addr, value)?;
            out_echo(0x06, addr, value)
        }
        _ => return Err(0x01), // Illegal function — нет такой функции
    };
    Ok(out)
}

/// Эхо для write-single запросов: FC + адрес + значение (всё big-endian).
fn out_echo(fc: u8, addr: u16, value: u16) -> Vec<u8> {
    vec![fc, (addr >> 8) as u8, addr as u8, (value >> 8) as u8, value as u8]
}

/// Упаковать вектор булевых значений в PDU: bytecount + биты.
///
/// Биты «линейно»: бит 0 канала 0 → младший бит первого байта, канал 8 →
/// первый бит второго байта (стандарт Modbus). Лишние биты последнего
/// байта — нулевые.
fn pdu_bitvec(bits: &[bool]) -> Vec<u8> {
    let byte_count = (bits.len() + 7) / 8;
    let mut out = Vec::with_capacity(1 + byte_count);
    out.push(byte_count as u8);
    for byte_idx in 0..byte_count {
        let mut byte = 0u8;
        for bit in 0..8 {
            let idx = byte_idx * 8 + bit;
            if idx < bits.len() && bits[idx] {
                byte |= 1 << bit;
            }
        }
        out.push(byte);
    }
    out
}

/// Упаковать вектор регистров в PDU: bytecount + 2 байта на регистр (BE).
fn pdu_regvec(regs: &[u16]) -> Vec<u8> {
    let byte_count = (regs.len() * 2) as u8;
    let mut out = Vec::with_capacity(1 + regs.len() * 2);
    out.push(byte_count);
    for &r in regs {
        out.push((r >> 8) as u8);
        out.push(r as u8);
    }
    out
}

// ─── Тесты ─────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;

    /// FC04: два регистра канала должны собираться обратно в мА.
    #[test]
    fn input_regs_roundtrip() {
        let mut bank = ChannelBank::default();
        bank.set_current(0, 4.0);
        bank.set_current(1, 20.0);
        // Ch0 regs: 0,1; Ch1 regs: 2,3.
        let regs = read_input(&bank, 0, 4).unwrap();
        assert_eq!(regs.len(), 4);
        let val0 = regs_to_f32(regs[0], regs[1]);
        let val1 = regs_to_f32(regs[2], regs[3]);
        assert!((val0 - 4.0).abs() < 1e-3);
        assert!((val1 - 20.0).abs() < 1e-3);
    }

    /// FC03: holding демонстрируют gain/offset заданной калибровки.
    #[test]
    fn holding_regs_show_gain_offset() {
        let mut bank = ChannelBank::default();
        let cal = Calibration::from_points(10_000.0, 40_000.0).unwrap();
        bank.set_calibration(0, cal);
        let regs = read_holding(&bank, 0, 4).unwrap();
        let gain = regs_to_f32(regs[0], regs[1]);
        let offset = regs_to_f32(regs[2], regs[3]);
        assert!((gain - cal.gain).abs() < 1e-9);
        assert!((offset - cal.offset).abs() < 1e-4);
    }

    /// FC05 включил/выключил канал — FC01 это должен отразить.
    #[test]
    fn coil_enable_disable() {
        let mut bank = ChannelBank::default();
        assert!(write_coil(&mut bank, 23, false).is_ok());
        let coils = read_coils(&bank, 0, 24).unwrap();
        assert!(!coils[23]);
        assert!(coils[0]);
    }

    /// Выход за границы карты → exception 0x02 (illegal data address).
    #[test]
    fn exception_out_of_bounds() {
        let bank = ChannelBank::default();
        assert_eq!(read_input(&bank, 46, 5), Err(0x02));
        assert_eq!(read_coils(&bank, 0, 30), Err(0x02));
    }

    /// Holding регистры нельзя записывать по Modbus.
    #[test]
    fn write_holding_is_read_only() {
        let mut bank = ChannelBank::default();
        assert_eq!(write_holding(&mut bank, 0, 0), Err(0x04));
    }
}
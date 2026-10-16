//! Тренажёр ступени 2 (биты на u8) — РАЗБОР. 19 упражнений, 4 блока.
//!
//!   Тесты:  cargo test --bin step2-drills-answer
//!   Демо:   cargo run  --bin step2-drills-answer
//!   Теория: step2-bits/LECTURE.md
//!
//! Те же упражнения, что в `drills/step2.rs`, но с готовыми решениями.
//!

#![allow(dead_code, unused_variables)]

// ===== Блок 1. Один бит ==================================================
// Здесь ты снова и снова пишешь приём «сдвиг + маска». Так он и запоминается.

/// Прочитать бит номер `i`: истина, если он равен 1.
fn get_bit(x: u8, i: u32) -> bool {
    (x >> i) & 1 == 1
}

/// Поставить бит номер `i` в 1, остальные не трогать.
fn set_bit(x: u8, i: u32) -> u8 {
    x | (1u8 << i)
}

/// Сбросить бит номер `i` в 0, остальные не трогать.
fn clear_bit(x: u8, i: u32) -> u8 {
    x & !(1u8 << i)
}

/// Инвертировать бит номер `i`.
fn flip_bit(x: u8, i: u32) -> u8 {
    x ^ (1u8 << i)
}

/// «Присвоить» биту значение: поставить, если `value`, иначе сбросить.
/// Подсказка: два уже известных приёма, выбираешь по `value`.
fn write_bit(x: u8, i: u32, value: bool) -> u8 {
    if value {
        set_bit(x, i)
    } else {
        clear_bit(x, i)
    }
}

// ===== Блок 2. Нибблы и маски ============================================
// Ниббл — это 4 бита. Старший ниббл: биты 4..8, младший: биты 0..4.

/// Младшие 4 бита (старшие обнулить).
fn low_nibble(x: u8) -> u8 {
    x & 0x0F
}

/// Старшие 4 бита, прижатые к младшим позициям (то есть значение 0..15).
fn high_nibble(x: u8) -> u8 {
    x >> 4
}

/// Поменять нибблы местами: `0xAB` → `0xBA`.
fn swap_nibbles(x: u8) -> u8 {
    (x << 4) | (x >> 4)
}

/// Собрать байт из двух нибблов: старший и младший.
/// Лишние биты во входах нужно отбросить: `pack_nibbles(0xFF, 0xFF) == 0xFF`.
fn pack_nibbles(hi: u8, lo: u8) -> u8 {
    ((hi & 0x0F) << 4) | (lo & 0x0F)
}

/// Разобрать байт на два ниббла: `(старший, младший)`.
fn unpack_nibbles(x: u8) -> (u8, u8) {
    (x >> 4, x & 0x0F)
}

// ===== Блок 3. Счёт и поиск ==============================================

/// Чётность: истина, если единиц в байте нечётное число. Свёртка XOR.
fn parity(x: u8) -> bool {
    let mut acc = false;
    for i in 0..8 {
        acc ^= get_bit(x, i);
    }
    acc
}

/// Сколько единичных битов в байте.
fn count_bits(x: u8) -> u32 {
    x.count_ones()
}

/// Является ли число степенью двойки (ровно одна единица в битах).
/// Ноль степенью двойки не считается.
fn is_power_of_two(x: u8) -> bool {
    x != 0 && (x & (x - 1)) == 0
}

/// Номер самого младшего единичного бита. Если единиц нет (`x == 0`) — `None`.
fn lowest_set_bit(x: u8) -> Option<u32> {
    if x == 0 {
        None
    } else {
        Some(x.trailing_zeros())
    }
}

/// Сбросить самый младший единичный бит, остальные не трогать.
/// Классический трюк: `x & (x - 1)`.
/// Грабля: при `x == 0` обычное вычитание переполнится — нужен `wrapping_sub`.
fn clear_lowest_set_bit(x: u8) -> u8 {
    x & x.wrapping_sub(1)
}

// ===== Блок 4. Байт целиком ==============================================

/// Один побитовый гейт работает сразу на 8 линиях.
/// Верни тройку: (AND, OR, XOR) для двух байтов.
fn byte_gates(a: u8, b: u8) -> (u8, u8, u8) {
    (a & b, a | b, a ^ b)
}

/// Развернуть порядок всех 8 бит: бит 0 меняется местами с битом 7 и т.д.
/// Собери руками из `get_bit`/`set_bit` в цикле — это и есть закрепление.
fn reverse_bits(x: u8) -> u8 {
    let mut out = 0u8;
    for i in 0..8 {
        if get_bit(x, i) {
            out = set_bit(out, 7 - i);
        }
    }
    out
}

/// Маска битов от `from` до `to` включительно (нумерация как везде: справа 0).
/// Пример: `mask_range(4, 7) == 0xF0`, `mask_range(0, 7) == 0xFF`.
/// Грабля: для ширины 8 выражение `1u8 << 8` переполнится — считай в `u16`.
fn mask_range(from: u32, to: u32) -> u8 {
    let width = to - from + 1;
    let ones = (1u16 << width) - 1;
    (ones as u8) << from
}

/// Сдвинуть байт влево на 1 и вдвинуть `bit` в младший разряд.
/// Именно так работает сдвиговый регистр из ступени 4.
fn shift_in(x: u8, bit: bool) -> u8 {
    (x << 1) | bit as u8
}

fn main() {
    let x = 0xABu8;
    println!("Разбор тренажёра ступени 2. x = {x:#010b}\n");
    println!("low_nibble(x)   = {:#010b}", low_nibble(x));
    println!("high_nibble(x)  = {:#010b}", high_nibble(x));
    println!("swap_nibbles(x) = {:#010b}", swap_nibbles(x));
    println!("reverse_bits(x) = {:#010b}", reverse_bits(x));
    println!("unpack_nibbles  = {:?}", unpack_nibbles(x));
    println!("parity(x)       = {}", parity(x));
    println!("count_bits(x)   = {}", count_bits(x));
    println!("mask_range(4, 7) = {:#010b}", mask_range(4, 7));
    println!(
        "shift_in(0b1010_0000, true) = {:#010b}",
        shift_in(0b1010_0000, true)
    );
    println!("\nпроверка: cargo test --bin step2-drills-answer");
}

#[cfg(test)]
mod block1_one_bit {
    use super::*;

    #[test]
    fn drill_get_bit() {
        let x = 0b1000_0001u8;
        assert!(get_bit(x, 0));
        assert!(get_bit(x, 7));
        assert!(!get_bit(x, 3));
        assert!(!get_bit(0, 5));
        assert!(get_bit(0xFF, 6));
    }

    #[test]
    fn drill_set_bit() {
        assert_eq!(set_bit(0, 3), 0b1000);
        assert_eq!(set_bit(0b1010_0000, 0), 0b1010_0001);
        assert_eq!(set_bit(0b1010_0000, 5), 0b1010_0000); // уже стоял — не изменился
        assert_eq!(set_bit(0, 7), 0b1000_0000);
    }

    #[test]
    fn drill_clear_bit() {
        assert_eq!(clear_bit(0b1111, 2), 0b1011);
        assert_eq!(clear_bit(0b1010_0000, 7), 0b0010_0000);
        assert_eq!(clear_bit(0b1010_0000, 0), 0b1010_0000); // уже 0 — не изменился
        assert_eq!(clear_bit(0xFF, 0), 0xFE);
    }

    #[test]
    fn drill_flip_bit() {
        assert_eq!(flip_bit(0b1010, 0), 0b1011);
        assert_eq!(flip_bit(0b1010_0000, 5), 0b1000_0000);
        assert_eq!(flip_bit(0b1010_0000, 4), 0b1011_0000);
        assert_eq!(flip_bit(0, 0), 1);
    }

    #[test]
    fn drill_write_bit() {
        assert_eq!(write_bit(0, 3, true), 0b1000);
        assert_eq!(write_bit(0b1111, 2, false), 0b1011);
        assert_eq!(write_bit(0b1000, 3, true), 0b1000); // уже так — не изменился
        assert_eq!(write_bit(0b1000, 3, false), 0b0000);
        assert_eq!(write_bit(0xFF, 0, false), 0xFE);
        assert_eq!(write_bit(0x00, 7, true), 0x80);
    }
}

#[cfg(test)]
mod block2_nibbles {
    use super::*;

    #[test]
    fn drill_low_nibble() {
        assert_eq!(low_nibble(0xAB), 0x0B);
        assert_eq!(low_nibble(0x0F), 0x0F);
        assert_eq!(low_nibble(0xF0), 0x00);
        assert_eq!(low_nibble(0x00), 0x00);
    }

    #[test]
    fn drill_high_nibble() {
        assert_eq!(high_nibble(0xAB), 0x0A);
        assert_eq!(high_nibble(0xF0), 0x0F);
        assert_eq!(high_nibble(0x05), 0x00);
        assert_eq!(high_nibble(0xFF), 0x0F);
    }

    #[test]
    fn drill_swap_nibbles() {
        assert_eq!(swap_nibbles(0xAB), 0xBA);
        assert_eq!(swap_nibbles(0x10), 0x01);
        assert_eq!(swap_nibbles(0x0F), 0xF0);
        assert_eq!(swap_nibbles(0xFF), 0xFF);
        assert_eq!(swap_nibbles(0x00), 0x00);
    }

    #[test]
    fn drill_pack_nibbles() {
        assert_eq!(pack_nibbles(0x0A, 0x0B), 0xAB);
        assert_eq!(pack_nibbles(0xFF, 0xFF), 0xFF); // лишние биты отброшены
        assert_eq!(pack_nibbles(0x00, 0x00), 0x00);
        assert_eq!(pack_nibbles(0x01, 0x02), 0x12);
        assert_eq!(pack_nibbles(0xF0, 0x0F), 0x0F); // старший ниббл был пуст
    }

    #[test]
    fn drill_unpack_nibbles() {
        assert_eq!(unpack_nibbles(0xAB), (0x0A, 0x0B));
        assert_eq!(unpack_nibbles(0x00), (0x00, 0x00));
        assert_eq!(unpack_nibbles(0xFF), (0x0F, 0x0F));
        assert_eq!(unpack_nibbles(0x12), (0x01, 0x02));
        // и обратно:
        for x in 0..=u8::MAX {
            let (hi, lo) = unpack_nibbles(x);
            assert_eq!(pack_nibbles(hi, lo), x);
        }
    }
}

#[cfg(test)]
mod block3_counting {
    use super::*;

    #[test]
    fn drill_parity() {
        assert!(!parity(0));
        assert!(parity(1));
        assert!(!parity(0b1010_0000)); // две единицы
        assert!(parity(0b0000_0111)); // три единицы
        assert!(!parity(0xFF)); // восемь единиц
        for x in 0..=u8::MAX {
            assert_eq!(parity(x), x.count_ones() % 2 == 1, "x={x}");
        }
    }

    #[test]
    fn drill_count_bits() {
        assert_eq!(count_bits(0), 0);
        assert_eq!(count_bits(1), 1);
        assert_eq!(count_bits(0b1010_0000), 2);
        assert_eq!(count_bits(0xFF), 8);
        assert_eq!(count_bits(0b0101_0101), 4);
    }

    #[test]
    fn drill_is_power_of_two() {
        assert!(!is_power_of_two(0));
        assert!(is_power_of_two(1));
        assert!(is_power_of_two(2));
        assert!(is_power_of_two(0x80));
        assert!(!is_power_of_two(3));
        assert!(!is_power_of_two(0xFF));
        assert!(is_power_of_two(0b0001_0000));
    }

    #[test]
    fn drill_lowest_set_bit() {
        assert_eq!(lowest_set_bit(0), None);
        assert_eq!(lowest_set_bit(1), Some(0));
        assert_eq!(lowest_set_bit(0b1100), Some(2));
        assert_eq!(lowest_set_bit(0b1010_0000), Some(5));
        assert_eq!(lowest_set_bit(0b1000_1000), Some(3));
        assert_eq!(lowest_set_bit(0xFF), Some(0));
    }

    #[test]
    fn drill_clear_lowest_set_bit() {
        assert_eq!(clear_lowest_set_bit(0), 0);
        assert_eq!(clear_lowest_set_bit(1), 0);
        assert_eq!(clear_lowest_set_bit(0b1011), 0b1010);
        assert_eq!(clear_lowest_set_bit(0b1010_0000), 0b1000_0000);
        assert_eq!(clear_lowest_set_bit(0xFF), 0xFE);
    }
}

#[cfg(test)]
mod block4_whole_byte {
    use super::*;

    #[test]
    fn drill_byte_gates() {
        let (a, b) = (0b1100_1010u8, 0b1010_0110u8);
        let (and, or, xor) = byte_gates(a, b);
        assert_eq!(and, 0b1000_0010);
        assert_eq!(or, 0b1110_1110);
        assert_eq!(xor, 0b0110_1100);
        assert_eq!(byte_gates(0, 0), (0, 0, 0));
        assert_eq!(byte_gates(0xFF, 0x0F), (0x0F, 0xFF, 0xF0));
    }

    #[test]
    fn drill_reverse_bits() {
        for x in 0..=u8::MAX {
            assert_eq!(reverse_bits(x), x.reverse_bits(), "x={x:#010b}");
        }
    }

    #[test]
    fn drill_mask_range() {
        assert_eq!(mask_range(0, 0), 0b0000_0001);
        assert_eq!(mask_range(0, 7), 0xFF);
        assert_eq!(mask_range(4, 7), 0xF0);
        assert_eq!(mask_range(0, 3), 0x0F);
        assert_eq!(mask_range(2, 3), 0b0000_1100);
        assert_eq!(mask_range(7, 7), 0x80);
        assert_eq!(mask_range(1, 1), 0b0000_0010);
    }

    #[test]
    fn drill_shift_in() {
        assert_eq!(shift_in(0b0000_0001, false), 0b0000_0010);
        assert_eq!(shift_in(0b0000_0010, true), 0b0000_0101);
        assert_eq!(shift_in(0b1000_0000, false), 0b0000_0000); // старший бит потерялся
        assert_eq!(shift_in(0b1010_0000, true), 0b0100_0001);
        assert_eq!(shift_in(0, true), 1);
    }
}

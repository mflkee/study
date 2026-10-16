//! Тренажёр ступени 1 (гейты). 17 упражнений, 4 блока. ЗАДАНИЕ.
//!
//!   Прогресс: cargo test --bin step1-drills
//!   Карта:    cargo run  --bin step1-drills
//!   Теория:   step1-gates/LECTURE.md
//!   Разбор:   drills/step1-notes.md (ход мысли по каждому упражнению)
//!
//! Как это работает: тесты уже написаны за тебя. Каждое упражнение — одна
//! функция и один тест. Пока в теле стоит `todo!()`, тест красный; как только
//! реализовал — тест зелёный. Делай по блоку за раз и перезапускай тесты.
//!
//! Тесты написаны так, чтобы НЕ зависеть от твоих других функций: можно делать
//! блоки в любом порядке, даже внутри блока — упражнения независимы.

#![allow(dead_code, unused_variables)]

// ===== Блок 1. Гейты и их смыслы =========================================
// Повторение ступени 1. Здесь нужен только `&`, `|`, `^`, `!`.

/// NOT: истина, когда вход ложен.
fn not(a: bool) -> bool {
    !a
}

/// NAND: «не-и». Ложь только в одном случае — когда оба входа истинны.
fn nand(a: bool, b: bool) -> bool {
    !(a & b)
}

/// NOR: «не-или». Истина только в одном случае — когда оба входа ложны.
fn nor(a: bool, b: bool) -> bool {
    !(a | b)
}

/// XNOR: «равенство» — истина, когда входы совпадают.
fn xnor(a: bool, b: bool) -> bool {
    !(a ^ b)
}

/// Импликация «если a, то b» (запись `a → b`, «a влечёт b»).
///
/// Это обещание: «когда a истинно, b тоже обязано быть истинно». Обещание
/// нарушено РОВНО в одном случае — когда обещали (`a = true`), а не сделали
/// (`b = false`). Если обещания не было (`a = false`), нарушить его
/// невозможно, поэтому результат истинен при любом `b`.
///
/// | a | b | a → b |
/// |---|---|-------|
/// | F | F |   T   |
/// | F | T |   T   |
/// | T | F |   F   |
/// | T | T |   T   |
///
/// Отсюда рабочее правило: импликация ложна только тогда, когда **из истины
/// вывели ложь**. Значит, она равна ОТРИЦАНИЮ единственной запрещённой
/// комбинации «a истинно, а b ложно». Собери выражение и проверь себя по
/// таблице; если хочешь короткую форму — упрости по закону де Моргана
/// (`!(x & y) == !x | !y`). Подробный разбор — в LECTURE.md: разделы
/// «Импликация» и «Как вывести выражение по таблице».
fn implies(a: bool, b: bool) -> bool {
    a & b
}

// ===== Блок 2. Собрать из NAND ===========================================
// Пользуйся только операцией NAND — то есть выражением вида `!(x & y)`.
// `|`, `^` и логические `&&`/`||` не использовать.

/// AND, собранный из NAND.
fn and_from_nand(a: bool, b: bool) -> bool {
    todo!("AND из NAND")
}

/// OR, собранный из NAND. Обходной путь: «не (не a и не b)».
fn or_from_nand(a: bool, b: bool) -> bool {
    todo!("OR из NAND")
}

/// NOT, собранный из NAND.
fn not_from_nand(a: bool) -> bool {
    todo!("NOT из NAND")
}

/// XOR, собранный из NAND. Классика: четырьмя NAND.
/// Схема-подсказка: n1 = NAND(a, b); дальше два NAND «против» n1 и один общий.
fn xor_from_nand(a: bool, b: bool) -> bool {
    todo!("XOR из NAND")
}

// ===== Блок 3. Три входа =================================================

/// Истина, если единиц (истин) во входах хотя бы две. «Голосование».
fn majority(a: bool, b: bool, c: bool) -> bool {
    todo!("мажоритарность")
}

/// Истина, если все три входа равны друг другу.
fn all_equal(a: bool, b: bool, c: bool) -> bool {
    todo!("все равны")
}

/// Истина, если единиц во входах нечётное число. Старое свойство XOR:
/// одинаковые входы взаимно уничтожаются.
fn odd_ones(a: bool, b: bool, c: bool) -> bool {
    todo!("нечётное число единиц")
}

/// Истина, если единица ровно одна.
/// (Если уже сделал `count_true` из этого блока — можно выразить через него.)
fn exactly_one(a: bool, b: bool, c: bool) -> bool {
    todo!("ровно одна единица")
}

/// Сколько входов истинны — числом. Пригодится приведение `bool as u8`.
fn count_true(a: bool, b: bool, c: bool) -> u8 {
    todo!("число истин")
}

// ===== Блок 4. Выбор и функции как значения ==============================

/// Мультиплексор: если `s` — вернуть `a`, иначе вернуть `b`.
fn mux(s: bool, a: bool, b: bool) -> bool {
    todo!("мультиплексор")
}

/// Совпадают ли два гейта **на всех четырёх** наборах входов.
/// Вспомни грабли про `return` внутри цикла: `true` возвращается только после
/// проверки всего перебора, а `false` — при первом расхождении.
fn gates_equal(g1: fn(bool, bool) -> bool, g2: fn(bool, bool) -> bool) -> bool {
    todo!("сравнение таблиц истинности")
}

/// Свернуть таблицу истинности гейта в одно число — «отпечаток».
/// Уговор: перебираем пары входов в порядке (F,F), (F,T), (T,F), (T,T) и
/// результат каждой строки кладём в бит с её номером:
///   (F,F) → бит 0, (F,T) → бит 1, (T,F) → бит 2, (T,T) → бит 3.
/// Например, у AND единица только на последней строке → ответ `0b1000`.
fn truth_table_rows(g: fn(bool, bool) -> bool) -> u8 {
    todo!("отпечаток таблицы истинности")
}

fn main() {
    println!("Тренажёр ступени 1. Проверка: cargo test --bin step1-drills\n");
    println!("Блок 1: not, nand, nor, xnor, implies");
    println!("Блок 2: and_from_nand, or_from_nand, not_from_nand, xor_from_nand");
    println!("Блок 3: majority, all_equal, odd_ones, exactly_one, count_true");
    println!("Блок 4: mux, gates_equal, truth_table_rows");
    println!("\nВсего 17 упражнений = 18 тестов.");
}

#[cfg(test)]
mod block1_gates {
    use super::*;

    #[test]
    fn drill_not() {
        assert!(not(false));
        assert!(!not(true));
    }

    #[test]
    fn drill_nand() {
        assert!(nand(false, false) && nand(false, true) && nand(true, false));
        assert!(!nand(true, true));
    }

    #[test]
    fn drill_nor() {
        assert!(nor(false, false));
        assert!(!nor(false, true) && !nor(true, false) && !nor(true, true));
    }

    #[test]
    fn drill_xnor() {
        assert!(xnor(false, false) && xnor(true, true));
        assert!(!xnor(false, true) && !xnor(true, false));
    }

    #[test]
    fn drill_implies() {
        assert!(implies(false, false) && implies(false, true) && implies(true, true));
        assert!(!implies(true, false));
    }
}

#[cfg(test)]
mod block2_from_nand {
    use super::*;

    #[test]
    fn drill_and_from_nand() {
        assert!(!and_from_nand(false, false));
        assert!(!and_from_nand(false, true));
        assert!(!and_from_nand(true, false));
        assert!(and_from_nand(true, true));
    }

    #[test]
    fn drill_or_from_nand() {
        assert!(!or_from_nand(false, false));
        assert!(or_from_nand(false, true));
        assert!(or_from_nand(true, false));
        assert!(or_from_nand(true, true));
    }

    #[test]
    fn drill_not_from_nand() {
        assert!(not_from_nand(false));
        assert!(!not_from_nand(true));
    }

    #[test]
    fn drill_xor_from_nand() {
        assert!(!xor_from_nand(false, false));
        assert!(xor_from_nand(false, true));
        assert!(xor_from_nand(true, false));
        assert!(!xor_from_nand(true, true));
    }
}

#[cfg(test)]
mod block3_three_inputs {
    use super::*;

    #[test]
    fn drill_majority() {
        assert!(!majority(false, false, false));
        assert!(!majority(true, false, false));
        assert!(!majority(false, true, false));
        assert!(!majority(false, false, true));
        assert!(majority(true, true, false));
        assert!(majority(true, false, true));
        assert!(majority(false, true, true));
        assert!(majority(true, true, true));
    }

    #[test]
    fn drill_all_equal() {
        assert!(all_equal(false, false, false));
        assert!(all_equal(true, true, true));
        assert!(!all_equal(false, false, true));
        assert!(!all_equal(true, false, true));
        assert!(!all_equal(false, true, true));
        assert!(!all_equal(true, true, false));
    }

    #[test]
    fn drill_odd_ones() {
        assert!(!odd_ones(false, false, false));
        assert!(odd_ones(true, false, false));
        assert!(odd_ones(false, true, false));
        assert!(odd_ones(false, false, true));
        assert!(!odd_ones(true, true, false));
        assert!(!odd_ones(true, false, true));
        assert!(!odd_ones(false, true, true));
        assert!(odd_ones(true, true, true));
    }

    #[test]
    fn drill_exactly_one() {
        assert!(!exactly_one(false, false, false));
        assert!(exactly_one(true, false, false));
        assert!(exactly_one(false, true, false));
        assert!(exactly_one(false, false, true));
        assert!(!exactly_one(true, true, false));
        assert!(!exactly_one(true, false, true));
        assert!(!exactly_one(false, true, true));
        assert!(!exactly_one(true, true, true));
    }

    #[test]
    fn drill_count_true() {
        assert_eq!(count_true(false, false, false), 0);
        assert_eq!(count_true(true, false, false), 1);
        assert_eq!(count_true(false, true, false), 1);
        assert_eq!(count_true(false, false, true), 1);
        assert_eq!(count_true(true, true, false), 2);
        assert_eq!(count_true(true, false, true), 2);
        assert_eq!(count_true(false, true, true), 2);
        assert_eq!(count_true(true, true, true), 3);
    }
}

#[cfg(test)]
mod block4_select_and_functions {
    use super::*;

    #[test]
    fn drill_mux() {
        assert!(!mux(false, true, false));
        assert!(mux(true, true, false));
        assert!(mux(false, false, true));
        assert!(!mux(true, false, true));
    }

    #[test]
    fn drill_gates_equal_same() {
        assert!(gates_equal(|a, b| a & b, |a, b| a & b));
        assert!(gates_equal(|a, b| !(a & b), |a, b| !(a & b)));
        assert!(gates_equal(|a, b| !(a ^ b), |a, b| (a & b) | (!a & !b)));
    }

    #[test]
    fn drill_gates_equal_differs() {
        assert!(!gates_equal(|a, b| a & b, |a, b| a | b));
        assert!(!gates_equal(|a, b| a ^ b, |a, b| a | b));
        assert!(!gates_equal(|a, b| a & b, |a, b| a ^ b));
    }

    #[test]
    fn drill_truth_table_rows() {
        assert_eq!(truth_table_rows(|a, b| a & b), 0b1000);
        assert_eq!(truth_table_rows(|a, b| a | b), 0b1110);
        assert_eq!(truth_table_rows(|a, b| a ^ b), 0b0110);
        assert_eq!(truth_table_rows(|a, b| !(a & b)), 0b0111);
        assert_eq!(truth_table_rows(|a, b| !(a | b)), 0b0001);
        assert_eq!(truth_table_rows(|a, b| !(a ^ b)), 0b1001);
    }
}

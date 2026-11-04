// Упражнение 11: склад и upsert
// Тема: entry API (and_modify + or_insert)
// "Обнови, если есть; вставь, если нет" — типовая операция склада:
// приход увеличивает остаток, расход уменьшает, ниже нуля не опускаемся.

use std::collections::HashMap;

/// Применяет операцию к остатку товара. delta > 0 — приход, < 0 — расход.
/// Возвращает остаток после операции (не меньше нуля).
fn apply_op(stock: &mut HashMap<String, i32>, item: &str, delta: i32) -> i32 {
    let remainder = stock
        .entry(item.to_string())
        .and_modify(|q| *q = (*q + delta).max(0)) // товар уже есть — обновляем
        .or_insert(delta.max(0)); // товара не было — вставляем
    *remainder
}

fn main() {
    println!("Упражнение 11: склад (upsert)");

    let mut stock: HashMap<String, i32> = HashMap::new();
    let ops = [
        ("Мука", 10),
        ("Сахар", 5),
        ("Мука", -3),
        ("Соль", 2),
        ("Сахар", -8), // расход больше остатка -> 0
        ("Мука", 4),
    ];

    for (item, delta) in ops {
        let left = apply_op(&mut stock, item, delta);
        println!("{delta:>4} {item:<6} -> остаток {left}");
    }

    let mut items: Vec<_> = stock.iter().collect();
    items.sort_by_key(|(name, _)| name.as_str());
    println!("\nИтог:");
    for (name, qty) in items {
        println!("  {name}: {qty}");
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn upsert_adds_and_updates() {
        let mut stock = HashMap::new();
        assert_eq!(apply_op(&mut stock, "Мука", 10), 10); // вставка
        assert_eq!(apply_op(&mut stock, "Мука", -3), 7); // обновление
        assert_eq!(stock["Мука"], 7);
    }

    #[test]
    fn never_goes_negative() {
        let mut stock = HashMap::new();
        apply_op(&mut stock, "Сахар", 5);
        assert_eq!(apply_op(&mut stock, "Сахар", -8), 0);
        assert_eq!(stock["Сахар"], 0);
    }

    #[test]
    fn negative_new_item_is_zero() {
        let mut stock = HashMap::new();
        assert_eq!(apply_op(&mut stock, "Соль", -4), 0);
    }
}

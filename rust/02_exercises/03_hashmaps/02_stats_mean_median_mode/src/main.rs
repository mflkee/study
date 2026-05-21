// Упражнение 09: среднее, медиана, мода
// Тема: сортировка, HashMap, математическая статистика
// Вычисляем три статистических показателя для набора чисел

use std::collections::HashMap;

fn main() {
    println!("Упражнение 09: среднее / медиана / мода");

    let data = vec![2, 4, 4, 4, 5, 7, 9];

    println!("Данные: {:?}", data);
    println!("Среднее: {:?}", mean(&data));    // Some(5.0)
    println!("Медиана: {:?}", median(&data));  // Some(4.0)
    println!("Мода: {:?}", mode(&data));       // Some(4)
}

/// Вычисляет среднее арифметическое.
/// Сумма всех элементов / количество элементов.
fn mean(data: &[i32]) -> Option<f64> {
    if data.is_empty() {
        return None;
    }
    let sum: i32 = data.iter().sum();
    Some(sum as f64 / data.len() as f64)
}

/// Вычисляет медиану — центральное значение отсортированного ряда.
/// Если количество элементов чётное — берём среднее двух центральных.
fn median(data: &[i32]) -> Option<f64> {
    if data.is_empty() {
        return None;
    }
    let mut sorted = data.to_vec();
    sorted.sort();
    let len = sorted.len();
    if len % 2 == 0 {
        // Чётное количество: среднее двух центральных
        let mid = len / 2;
        Some((sorted[mid - 1] + sorted[mid]) as f64 / 2.0)
    } else {
        // Нечётное количество: центральный элемент
        Some(sorted[len / 2] as f64)
    }
}

/// Находит моду — самое часто встречающееся значение.
/// Использует HashMap для подсчёта частотности.
fn mode(data: &[i32]) -> Option<i32> {
    if data.is_empty() {
        return None;
    }
    let mut freq = HashMap::new();
    for &val in data {
        *freq.entry(val).or_insert(0) += 1;
    }
    freq.into_iter()
        .max_by_key(|&(_, count)| count)
        .map(|(val, _)| val)
}

// Упражнение 03: изменяемое и неизменяемое заимствование
// Тема: mutable borrow (&mut), immutable borrow (&), срезы
// Демонстрируем мутацию всех элементов через &mut [i32]
// и поиск первого чётного через &[i32]

fn main() {
    println!("Упражнение 03: сдвиг элементов и поиск чётного");

    let mut values = vec![1, 2, 3, 4];

    // Мутируем вектор: передаём &mut [i32] (изменяемый срез)
    shift_all(&mut values, 10);
    println!("После сдвига: {:?}", values);

    // Ищем первое чётное: передаём &[i32] (неизменяемый срез)
    match first_even(&values) {
        Some(val) => println!("Первое чётное: {val}"),
        None => println!("Чётных чисел нет"),
    }
}

/// Прибавляет `shift` к каждому элементу среза.
/// Используем изменяемое заимствование: &mut [i32]
fn shift_all(data: &mut [i32], shift: i32) {
    for element in data.iter_mut() {
        *element += shift; // разыменовываем и изменяем
    }
}

/// Возвращает первое чётное число из среза (или None).
/// Используем неизменяемое заимствование: &[i32]
fn first_even(data: &[i32]) -> Option<i32> {
    for &val in data {
        if val % 2 == 0 {
            return Some(val);
        }
    }
    None
}

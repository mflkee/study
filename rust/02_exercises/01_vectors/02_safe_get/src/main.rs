// Упражнение 02: безопасный доступ к Vec
// Тема: Option, get(), match
// Вместо прямого индексирования (которое паникует при выходе за границы)
// используем get(), возвращающий Option<&T>

fn main() {
    println!("Упражнение 02: безопасный доступ к вектору");

    let data = vec![5, 10, 15];

    // get() возвращает Option<&i32> — None если индекс за пределами
    match safe_get(&data, 1) {
        Some(val) => println!("Элемент [1] = {val}"),
        None => println!("Нет элемента с индексом 1"),
    }

    match safe_get(&data, 10) {
        Some(val) => println!("Элемент [10] = {val}"),
        None => println!("Нет элемента с индексом 10"),
    }
}

/// Безопасно получает элемент по индексу без паники.
/// Использует get() и копирует значение через copied().
fn safe_get(data: &[i32], index: usize) -> Option<i32> {
    // get() возвращает Option<&i32>, copied() превращает в Option<i32>
    data.get(index).copied()
}

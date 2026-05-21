// Упражнение 04: смешанная строка таблицы через enum
// Тема: enum + Vec
// Храним разные типы данных в одном векторе с помощью перечисления

#[derive(Debug, Clone, PartialEq)]
enum SpreadsheetCell {
    Int(i32),
    Float(f64),
    Text(String),
}

fn main() {
    println!("Упражнение 04: строка таблицы с перечислением");

    // Создаём строку с данными разных типов
    let row = make_row();
    println!("Строка таблицы: {:?}", row);

    // Считаем количество текстовых ячеек
    let text_count = count_text_cells(&row);
    println!("Текстовых ячеек: {text_count}");
}

/// Создаёт строку (Vec<SpreadsheetCell>) с тремя разными типами:
/// Int(42), Float(3.14), Text("hello")
fn make_row() -> Vec<SpreadsheetCell> {
    vec![
        SpreadsheetCell::Int(42),
        SpreadsheetCell::Float(3.14),
        SpreadsheetCell::Text("hello".to_string()),
    ]
}

/// Считает количество ячеек с вариантом Text в срезе.
/// Использует pattern matching внутри итератора.
fn count_text_cells(row: &[SpreadsheetCell]) -> usize {
    row.iter()
        .filter(|cell| matches!(cell, SpreadsheetCell::Text(_)))
        .count()
}

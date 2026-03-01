// Exercise 04: Mixed Row with Enum
// Topic: enum + Vec

#[derive(Debug, Clone, PartialEq)]
enum SpreadsheetCell {
    Int(i32),
    Float(f64),
    Text(String),
}

fn main() {
    println!("Exercise 04: table row");
    println!("Complete TODO blocks in this file.");

    // Self-check (uncomment after implementation):
    // let row = make_row();
    // println!("{row:?}");
    // println!("Text cells: {}", count_text_cells(&row));
}

/// TODO:
/// Create one row with mixed types:
/// Int(42), Float(3.14), Text("hello")
fn make_row() -> Vec<SpreadsheetCell> {
    // Write your code below this line.
    Vec::new()
}

/// TODO:
/// Count how many `Text` cells are inside row.
fn count_text_cells(row: &[SpreadsheetCell]) -> usize {
    let _ = row;
    // Write your code below this line.
    0
}

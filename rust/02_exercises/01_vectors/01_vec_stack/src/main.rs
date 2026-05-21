// Упражнение 01: Vec как стек
// Тема: Vec::push, Vec::pop, while let
// Вектор можно использовать как стек: LIFO (Last In, First Out)

fn main() {
    println!("Упражнение 01: стек на Vec");

    // Строим стек из входных данных
    let stack = build_stack(vec![10, 20, 30, 40]);
    println!("Стек построен: {:?}", stack);

    // Извлекаем все элементы в обратном порядке
    let popped = pop_all(stack);
    println!("Извлечено: {:?}", popped);
    // Ожидаемый результат: [40, 30, 20, 10]
}

/// Добавляет все значения из input в стек (Vec как LIFO)
fn build_stack(input: Vec<i32>) -> Vec<i32> {
    let mut stack: Vec<i32> = Vec::new();
    for value in input {
        stack.push(value); // push — кладём на вершину стека
    }
    stack
}

/// Извлекает все значения из стека (pop — забираем с вершины)
/// while let — удобный паттерн для цикла, пока Option имеет значение Some
fn pop_all(mut stack: Vec<i32>) -> Vec<i32> {
    let mut popped: Vec<i32> = Vec::new();
    while let Some(value) = stack.pop() {
        popped.push(value);
    }
    popped
}

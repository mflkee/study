// Владение (Ownership) — ключевая концепция Rust
// https://doc.rust-lang.org/book/ch04-01-what-is-ownership.html
// Правила:
// 1) У каждого значения есть один владелец (owner)
// 2) Одновременно может быть только один владелец
// 3) Когда владелец выходит из области видимости — значение удаляется (drop)

mod references; // подключаем модуль со ссылками и заимствованием

fn main() {
    // Запускаем примеры из модуля references.rs
    references::demo();

    println!("\n--- Основы владения ---");

    // === Stack vs Heap ===
    // Стек: LIFO, фиксированный размер, быстрый доступ
    // Куча (Heap): динамический размер, медленнее, нужен аллокатор

    // String — heap-allocated (данные в куче)
    let s = String::from("hello");
    println!("{s}");

    // Move (перемещение) — при присваивании владение переходит
    let s1 = String::from("hello");
    let s2 = s1; // s1 moved -> больше невалиден
    // println!("{s1}"); // ОШИБКА: s1 больше не владеет значением
    println!("{s2}"); // OK

    // Clone (глубокое копирование) — clone() для heap-данных
    let s3 = String::from("hello");
    let s4 = s3.clone(); // копируем и кучу тоже
    println!("s3 = {s3}, s4 = {s4}");

    // Copy-типы (хранятся только на стеке) — копируются автоматически
    let x = 5;
    let y = x; // i32 реализует Copy, поэтому x остаётся валидным
    println!("x = {x}, y = {y}");

    // Владение и функции: передача значения = передача владения
    let s = String::from("hello");
    takes_ownership(s);
    // println!("{s}"); // ОШИБКА: значение перемещено в функцию

    let x = 5;
    makes_copy(x);
    println!("{x}"); // OK: i32 копируется, а не перемещается

    // Возврат значений и область видимости
    let s1 = gives_ownership();       // Функция возвращает владение
    let s2 = String::from("hello");
    let s2 = takes_and_gives_back(s2); // Перемещаем и возвращаем
    println!("{s2}");
}

fn takes_ownership(some_string: String) {
    println!("{some_string}");
} // Здесь some_string выходит из scope и drop() освобождает память

fn makes_copy(some_integer: i32) {
    println!("{some_integer}");
} // i32 — Copy, ничего не освобождается

fn gives_ownership() -> String {
    String::from("hello") // возвращает владение строкой
}

fn takes_and_gives_back(a_string: String) -> String {
    a_string // принимает и возвращает владение
}

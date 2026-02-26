// Ownership (Владение)
// https://doc.rust-lang.org/book/ch04-01-what-is-ownership.html

fn main() {
    // === Stack vs Heap ===
    // Stack: LIFO, фиксированный размер, быстрый доступ
    // Heap: динамический размер, медленнее, нужен allocator

    // === Ownership Rules ===
    // 1. Each value has a variable that's its 'owner'
    // 2. There can only be one owner at a time
    // 3. When the owner goes out of scope, the value will be dropped

    // String (heap-allocated)
    let s = String::from("hello");
    println!("{s}");

    // Move (перемещение)
    let s1 = String::from("hello");
    let s2 = s1; // s1 больше не валиден (move)
    // println!("{s1}"); // ERROR: value borrowed here after move
    println!("{s2}");

    // Clone (глубокое копирование)
    let s3 = String::from("hello");
    let s4 = s3.clone();
    println!("s3 = {s3}, s4 = {s4}");

    // Copy types (stack-only, автоматическое копирование)
    let x = 5;
    let y = x; // Copy trait
    println!("x = {x}, y = {y}");

    // Ownership и функции
    let s = String::from("hello");
    takes_ownership(s);
    // println!("{s}"); // ERROR: value moved

    let x = 5;
    makes_copy(x);
    println!("{x}"); // OK: Copy trait

    // Return values and scope
    let s1 = gives_ownership();
    let s2 = String::from("hello");
    let s2 = takes_and_gives_back(s2);
    println!("{s2}");
}

fn takes_ownership(some_string: String) {
    println!("{some_string}");
}

fn makes_copy(some_integer: i32) {
    println!("{some_integer}");
}

fn gives_ownership() -> String {
    String::from("hello")
}

fn takes_and_gives_back(a_string: String) -> String {
    a_string
}

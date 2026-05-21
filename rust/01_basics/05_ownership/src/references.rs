// Ссылки и заимствование (References and Borrowing)
// https://doc.rust-lang.org/book/ch04-02-references-and-borrowing.html
// Заимствование: передаём ссылку на значение без передачи владения
// &T — неизменяемая ссылка, &mut T — изменяемая ссылка

pub fn demo() {
    let s1 = String::from("hello");
    let len = calculate_length(&s1); // &s1 — заимствуем без перемещения
    println!("Длина '{s1}' равна {len}."); // s1 всё ещё доступна!

    // Изменяемые ссылки (&mut T)
    let mut s = String::from("hello");
    change(&mut s);
    println!("{s}");

    // Ограничение: только одна изменяемая ссылка в области видимости
    let mut s = String::from("hello");
    let r1 = &mut s;
    r1.push_str(", world");
    // let r2 = &mut s; // ОШИБКА: нельзя заимствовать как mutable более одного раза
    println!("{r1}");

    // Non-lexical lifetimes (NLL) — ссылка живёт до последнего использования
    let mut s = String::from("hello");
    {
        let r1 = &mut s;
        r1.push_str(", world");
    } // r1 выходит из области видимости
    let r2 = &mut s; // OK: r1 уже не используется
    println!("{r2}");

    // Несколько неизменяемых ссылок — разрешено
    let s = String::from("hello");
    let r1 = &s;
    let r2 = &s;
    println!("{r1} и {r2}");

    // Висячие ссылки (dangling references) — Rust не позволит
    // let reference = dangling();
}

fn calculate_length(s: &String) -> usize {
    s.len()
} // s выходит из scope, но т.к. это ссылка, значение не удаляется

fn change(some_string: &mut String) {
    some_string.push_str(", world");
}

// Пример висячей ссылки (не компилируется):
// fn dangling() -> &String {
//     let s = String::from("hello");
//     &s // ОШИБКА: s будет удалена при выходе из функции, ссылка повиснет
// }

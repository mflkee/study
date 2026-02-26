// References and Borrowing
// https://doc.rust-lang.org/book/ch04-02-references-and-borrowing.html

fn main() {
    let s1 = String::from("hello");
    let len = calculate_length(&s1);
    println!("The length of '{s1}' is {len}.");

    // Mutable references
    let mut s = String::from("hello");
    change(&mut s);
    println!("{s}");

    // Ограничения: одна mutable ссылка в области видимости
    let mut s = String::from("hello");
    let r1 = &mut s;
    r1.push_str(", world");
    // let r2 = &mut s; // ERROR: cannot borrow as mutable more than once
    println!("{r1}");

    // Non-lexical lifetimes (NLL)
    let mut s = String::from("hello");
    {
        let r1 = &mut s;
        r1.push_str(", world");
    } // r1 выходит из scope
    let r2 = &mut s; // OK
    println!("{r2}");

    // Multiple immutable references
    let s = String::from("hello");
    let r1 = &s;
    let r2 = &s;
    println!("{r1} and {r2}");

    // Dangling references (висячие ссылки) - не компилируется
    // let reference = dangling();
}

fn calculate_length(s: &String) -> usize {
    s.len()
}

fn change(some_string: &mut String) {
    some_string.push_str(", world");
}

// fn dangling() -> &String {
//     let s = String::from("hello");
//     &s // ERROR: borrowed value does not live long enough
// }

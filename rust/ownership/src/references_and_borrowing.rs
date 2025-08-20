pub fn main() {
    let s1 = String::from("hello");
    let len = calculate_lenght(&s1);
    println!("The lengt {s1} is {len}.");

    let mut s2 = &mut String::from("Makeev");
    println!("{s2}");
    str_up(s2);
    println!("{s2}");

    let r1 = &s2;
    let r2 = &s2;
    println!("{}{}", r1, r2);
    let r3 = &mut s2;
    println!("{}", r3);
}

fn calculate_lenght(s: &String) -> usize {
    s.len()
}

fn str_up(s: &mut String) {
    let a = s.push_str(" Gleb")e
    a
}

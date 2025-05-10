pub fn borrowing() {
    let s1 = String::from("Hello");
    let len = calculate_lenght(&s1);
    println!("i1: {}, len: {}", s1, len);

    let mut s2 = String::from("World");
    change(&mut s2);
    println!("{}",s2);
}

fn calculate_lenght(s: &str) -> usize {
    s.len()
}

fn change(s: &mut String){
    s.push('!');
}


pub fn match_operator(){
    let number = 1;
    match number {
        1 => println!("first"),
        2 => println!("second"),
        3 => println!("third"),
        _ => println!("else"),
    }
}

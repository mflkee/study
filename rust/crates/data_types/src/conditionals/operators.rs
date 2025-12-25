pub fn operators() {
    let num1 = 10;
    let digit = true;
    if num1 == 5 && digit {
        println!("{}>5", num1);
    } else if num1 == 10 || !digit {
        println!("{}", num1)
    } else {
        println!("{}<5", num1);
    }
}

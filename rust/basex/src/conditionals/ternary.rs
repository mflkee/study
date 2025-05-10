pub fn op_ternary() {
    let condition: bool = true;
    let num2 = if condition { 777 } else { 666 };
    println!("{}", num2)
}

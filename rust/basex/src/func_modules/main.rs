pub fn main_func() {
    let res_1 = add(150, 150);
    let res_2 = add(333, 333);
    let mut user = String::from("");
    greet_user(&mut user);
    println!("Res1:{res_1}\nRes2:{res_2}");

    let nums_1: (i32, i32) = (6, 6);
    println!("Res_mult: {}", mult(&nums_1));
}

fn mult(data: &(i32, i32)) -> i32 {
    data.0 * data.1
}

fn greet_user(name: &mut String) {
    *name = String::from("Elon Musk");
    println!("User: {name}");
}

fn add(a: i32, b: i32) -> i32 {
    let res = a + b;
    println!("Result: {res}");
    res
}

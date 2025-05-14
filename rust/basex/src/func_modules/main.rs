pub fn main_func() {
    add(150, 150);

    let user = "Albert Einstein";
    greet_useer(user);
}

fn greet_useer(user: &str){
    println!("User: {}", user);

}

fn add(a: i32, b: i32) {
    let res = a + b;
    println!("Result: {}", res);
}

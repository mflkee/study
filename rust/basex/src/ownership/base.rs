use std::io;

pub fn ownership_base() {
    // let mut user_data = String::new();
    // io::stdin().read_line(&mut user_data).expect("Fail to read information");
    // println!("Result: {}", user_data);

    let mut num1 = String::new();
    let mut num2 = String::new();
    println!("Enter num1: ");
    io::stdin()
        .read_line(&mut num1)
        .expect("Fail to read information");
    println!("Enter num2: ");
    io::stdin()
        .read_line(&mut num2)
        .expect("Fail to read information");
    let data_1: i16 = num1.trim().parse().expect("Please enter a valid number");
    let data_2: u8 = num2.trim().parse().expect("Please enter a valid number");
    println!("num1: {}\nnum2: {}", data_1, data_2);

    let mut sum: i16 = data_1 + data_2 as i16;
    sum += 10;
    println!("{}", sum);
}

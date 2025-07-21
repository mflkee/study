pub fn main(){
    if_else();
    check_for_division(20);
    conditions_in_let();
}

pub fn if_else(){
    let number = 25;

    if number < 5 {
        println!("condition was true");
    } else {
        println!("condition was false");
    }
}

pub fn check_for_division(number: i32){
    if number % 4 == 0{
        println!("number is divisible by 4");
    } else if number % 3 == 0{
        println!("number is divisible by 3");
    } else if number % 2 == 0{
        println!("number is divisible by 2");
    } else {
        println!("numver is not divisible by 4, 3, 2");
    }
}

pub fn conditions_in_let(){
    let condition = false;
    let number = if condition {"five"} else {"six"};
    println!("The value of number is: {number}")
}

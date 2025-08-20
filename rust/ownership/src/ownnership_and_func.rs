pub fn main(){
    let s = String::from("hello");
    println!("{s}: before");
    takes_ownership(s);
    // println!("{s}: after"); // E0382:  value borrowed here after move

    let x = 5;
    makes_copy(x);
}


fn takes_ownership(some_string: String){
    println!("{some_string}")
}

fn makes_copy(some_integer: i32){
    println!("{some_integer}");
}

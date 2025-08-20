pub fn main(){
    let s1 = gives_ownership();
    println!("{s1}");

    let s2 = String::from("hello");

    let s3 = takes_and_gives_back(s2);
    println!("{s3}");

    let s4 = String::from("hello_2");
    let (s5, len) = calculate_len(s4);
    println!("The lenght '{s5}' is {len}");

}

fn gives_ownership() -> String {
    let some_string = String::from("yours");
    some_string
}

fn takes_and_gives_back(a_string: String) -> String {
    a_string
}

fn calculate_len(s: String) -> (String, usize){
    let lenght = s.len();
    (s, lenght)
}

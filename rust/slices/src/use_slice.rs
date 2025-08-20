pub fn main(){
    let s = String::from("hello world");

    let hello = &s[0..=4];
    let world = &s[6..=10];
    println!("{hello} {world}")
}

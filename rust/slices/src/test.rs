pub fn main(){
    let s = String::from("hello world");
    println!("{s}");

    let new_s = &s[0..6];
    println!("{new_s}")
}

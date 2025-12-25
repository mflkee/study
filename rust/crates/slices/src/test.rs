pub fn main() {
    let s = String::from("hello world");
    println!("{s}");

    let len = s.len();

    let new_s = &s[0..len];
    println!("{new_s}")
}

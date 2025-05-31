fn main() {
    let x = "123 ";
    println!("{x}");
    let y: u32 = x.trim().parse().expect("this is not number");
    println!("{y}");
}

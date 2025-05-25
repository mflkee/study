fn main() {
    let x = "123";
    println!("{x}");
    {
        let x = 14;
        println!("{x}");
    }
    println!("{x}");
}

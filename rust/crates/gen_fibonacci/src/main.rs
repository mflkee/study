fn main() {
    let mut first_digital = 0;
    let mut second_digital = 1;

    while second_digital < 1000 {
        let result = first_digital + second_digital;
        println!("{}", result);
        first_digital = second_digital;
        second_digital = result;
    }
}

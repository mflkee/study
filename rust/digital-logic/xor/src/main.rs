fn main() {
    fn swap_arg(mut arg1: u8, mut arg2: u8) -> (u8, u8) {
        arg1 ^= arg2;
        arg2 ^= arg1;
        arg1 ^= arg2;

        (arg1, arg2)
    }

    println!("{:?}", swap_arg(1, 2));
}

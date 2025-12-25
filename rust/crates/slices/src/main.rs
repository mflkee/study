mod no_use_slice;
mod test;
mod use_slice;
mod string_slices;

fn main() {
    no_use_slice::main();
    use_slice::main();
    test::main();
    string_slices::main();
}

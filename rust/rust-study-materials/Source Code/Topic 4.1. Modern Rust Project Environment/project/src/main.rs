fn main() {
    let scores = [5, 4, 3, 5];
    let passed = rust_env_example::passing_students(&scores, 3);

    println!("Сдали: {passed}");
}

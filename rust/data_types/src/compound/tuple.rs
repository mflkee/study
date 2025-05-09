pub fn demo_tuple() {
    let mut python: (&str, &str, &str, &str, &str, &str, i8) = (
        "interpreted",       // интерпретируемый
        "dynamically typed", // динамичесская типизация
        "high-level",        // высокоуровневый
        "multi-paradigm",    // мультипарадигменный
        "garbage-collected", // сборщик мусора
        "cross-platform",    // кросс-платформенный
        7,
    );
    python.6 = 4;
    println!("{0}", python.6);
}

pub fn demo_for() {
    for i in 1..4 {
        println!("Iteration: {i}")
    }

    for i in (1..=4).rev() {
        println!("Итерация: {}", i)
    }

    for i in 1..21 {
        if i % 2 == 0 {
            continue;
        }
        if i >= 7 {
            break;
        }
        println!("Нечетное: {i}");
    }
}

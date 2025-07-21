pub fn main() {
    for i in 1..4 {
        println!("Iteration: {i}")
    }

    for i in (1..=4).rev() {
        println!("Итерация: {i}")
    }

    for i in 1..1000 {
        if i % 2 == 0 {
            continue;
        }
        if i >= 16 {
            break;
        }
        println!("Нечетное: {i}");
    }
    
    let a = [10, 20, 30, 40, 50];
    let mut index = 0;

    while index < 4 {
        println!("the value is: {}", a[index]);
        index += 1;
    }
}

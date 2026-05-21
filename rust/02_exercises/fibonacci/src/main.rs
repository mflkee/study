// Генератор чисел Фибоначчи
// Каждое следующее число равно сумме двух предыдущих
// 0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377, 610, 987...

fn main() {
    let mut first = 0;
    let mut second = 1;

    println!("Числа Фибоначчи (до 1000):");

    while second < 1000 {
        let result = first + second; // следующее число
        println!("{result}");
        first = second;
        second = result;
    }
}

// Альтернатива: подход на основе итератора
// struct Fibonacci {
//     current: u32,
//     next: u32,
// }
//
// impl Iterator for Fibonacci {
//     type Item = u32;
//
//     fn next(&mut self) -> Option<Self::Item> {
//         let new_next = self.current + self.next;
//         self.current = self.next;
//         self.next = new_next;
//         Some(self.current)
//     }
// }

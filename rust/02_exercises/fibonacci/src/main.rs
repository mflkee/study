// Fibonacci Sequence Generator
// Генерация чисел Фибоначчи

fn main() {
    let mut first = 0;
    let mut second = 1;

    println!("Fibonacci sequence (up to 1000):");

    while second < 1000 {
        let result = first + second;
        println!("{result}");
        first = second;
        second = result;
    }
}

// Alternative: iterator-based approach
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

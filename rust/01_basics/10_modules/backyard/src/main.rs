// Модули: структура проекта
// https://doc.rust-lang.org/book/ch07-00-managing-growing-projects-with-packages-crates-and-modules.html
// main.rs — корень бинарного крейта (crate root)
// Объявляем модуль garden, который находится в src/garden.rs

use crate::garden::vegetables::Asparagus;

pub mod garden; // подключаем модуль из файла garden.rs

fn main() {
    let plant = Asparagus {};
    println!("Я выращиваю {plant:?}");
}

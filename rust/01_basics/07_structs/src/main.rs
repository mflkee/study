// Структуры (Structs) — пользовательские типы данных
// https://doc.rust-lang.org/book/ch05-01-defining-structs.html
// Rust позволяет определять три вида структур

#[derive(Debug)]
struct Rectangle {
    width: u32,
    height: u32,
}

struct Square {
    side: u32,
}

struct User {
    active: bool,
    username: String,
    email: String,
    sign_in_count: u64,
}

// Кортежные структуры (Tuple structs) — поля без имён, только типы
struct Color(i32, i32, i32);
struct Point(i32, i32, i32);

// Unit-подобная структура — без полей
struct AlwaysEqual;

fn main() {
    // === Создание экземпляра структуры ===
    let rect1 = Rectangle {
        width: 30,
        height: 50,
    };

    println!("Ширина: {}, высота: {}", rect1.width, rect1.height);

    // Вывод структуры через {:?} (требует #[derive(Debug)])
    println!("rect1 = {rect1:?}");
    println!("rect1 = {rect1:#?}"); // pretty-print

    // === Методы ===
    let rect1 = Rectangle {
        width: 30,
        height: 50,
    };
    println!("Площадь: {}", rect1.area());

    if rect1.has_area() {
        println!("Прямоугольник имеет ненулевую площадь");
    }

    // === Square через Rectangle ===
    let sq = Square { side: 10 };
    println!("Площадь квадрата: {}", sq.area());

    // === Синтаксис обновления структуры (..other) ===
    let user1 = User {
        active: true,
        username: String::from("mflkee"),
        email: String::from("makeevgleb86rus@gmail.com"),
        sign_in_count: 1,
    };

    let user2 = User {
        email: String::from("another@example.com"),
        ..user1 // остальные поля из user1 (username moved!)
    };

    // === Кортежные структуры ===
    let black = Color(0, 0, 0);
    let origin = Point(0, 0, 0);

    // === Владение в структурах ===
    // Поля могут владеть данными (String) или заимствовать (&str — нужен lifetime)
}

// === Блоки impl — методы и ассоциированные функции ===
impl Rectangle {
    // Метод: &self — заимствует экземпляр
    fn area(&self) -> u32 {
        self.width * self.height
    }

    fn has_area(&self) -> bool {
        self.width > 0 && self.height > 0
    }

    // Метод с изменяемой ссылкой
    fn double_width(&mut self) {
        self.width *= 2;
    }

    // Ассоциированная функция (без self) — конструктор
    fn new(width: u32, height: u32) -> Self {
        Self { width, height }
    }
}

impl Square {
    fn area(&self) -> u32 {
        self.side * self.side
    }

    fn new(side: u32) -> Self {
        Self { side }
    }
}

// Structs (Структуры)
// https://doc.rust-lang.org/book/ch05-01-defining-structs.html

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

// Tuple structs
struct Color(i32, i32, i32);
struct Point(i32, i32, i32);

// Unit-like structs (без полей)
struct AlwaysEqual;

fn main() {
    // === Struct Instance ===
    let rect1 = Rectangle {
        width: 30,
        height: 50,
    };

    println!("The rectangle has a nonzero width; it is {} and height {}", 
             rect1.width, rect1.height);

    // Debug trait для println! с {:?}
    println!("rect1 = {rect1:?}");
    println!("rect1 = {rect1:#?}"); // pretty print

    // === Methods ===
    let rect1 = Rectangle {
        width: 30,
        height: 50,
    };
    println!("Area: {}", rect1.area());

    if rect1.has_area() {
        println!("The rectangle has area");
    }

    // === Square from Rectangle ===
    let sq = Square { side: 10 };
    println!("Square area: {}", sq.area());

    // === Struct Update Syntax ===
    let user1 = User {
        active: true,
        username: String::from("mflkee"),
        email: String::from("makeevgleb86rus@gmail.com"),
        sign_in_count: 1,
    };

    let user2 = User {
        email: String::from("another@example.com"),
        ..user1 // остальные поля из user1
    };

    // === Tuple Structs ===
    let black = Color(0, 0, 0);
    let origin = Point(0, 0, 0);

    // === Ownership in Structs ===
    // Поля могут владеть данными (String) или заимствовать (&str)
}

// === Impl Blocks ===
impl Rectangle {
    // Method (self)
    fn area(&self) -> u32 {
        self.width * self.height
    }

    fn has_area(&self) -> bool {
        self.width > 0 && self.height > 0
    }

    // Method with mut self
    fn double_width(&mut self) {
        self.width *= 2;
    }

    // Associated function (static)
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

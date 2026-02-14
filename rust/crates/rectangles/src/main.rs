#[derive(Debug)]
struct Rectangle{
    width: u32,
    height: u32,
}

struct Square{
    side: u32,
}

impl Rectangle {
    fn has_area(&self) -> bool {
        self.width > 0  && self.height > 0
    }
    fn area(&self) -> u32 {
        self.width * self.height
    }
}

impl Square{
    fn has_area(&self) -> bool {
        self.side > 0
    }

    fn area(&self) -> u32 {
        self.side * self.side
    }
}

fn main(){
    let rect1 = Rectangle {
        width: 30,
        height: 50,
    };

    if rect1.has_area(){
        println!("The rectangle has a nonzero width; it is {} and height {}", rect1.width, rect1.height)
    };

    println!("Area rectangle is: {}", rect1.area());

    let square1 = Square{
        side: 10
    };
    
    if square1.has_area(){
        println!("The square has a nonzero width; side is {}", square1.side)
    }
    
    println!("Area square is: {}", square1.area());
}

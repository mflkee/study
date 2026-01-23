#[derive(Debug)]
struct Rectangle{
    width: u32,
    height: u32,
}

impl Rectangle {
    fn has_area(&self) -> bool {
        self.width > 0  && self.height > 0
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
}

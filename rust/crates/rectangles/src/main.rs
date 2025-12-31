// fn main() {
//     let width1 = 30;
//     let hight1 = 50;
//     
//     println!("The area of the recrtangle {} square pixels",
//         area(width1, hight1));
//
// }


// fn main() {
//     let rec1 = (30,50);
//     
//     println!("The area of the recrtangle {} square pixels",
//         area(rec1));
//
// }

// fn area(demensions:(u32,u32)) -> u32{
//     demensions.0 * demensions.1
// }

// #[derive(Debug)]
// struct Rectangle {
//     width: u32,
//     hight: u32,
// }
//
// fn main(){
//     let scale = 2;
//     let rec1 = Rectangle{
//         width: dbg!(30 * scale),
//         hight: 50,
//     };
//     dbg!(&rec1);
// }
//
// fn area(rectangle: &Rectangle) -> u32 {
//         rectangle.width * rectangle.hight
// }

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

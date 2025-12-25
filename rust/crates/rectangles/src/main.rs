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
#[derive(Debug)]
struct Rectangle {
    width: u32,
    hight: u32,
}

fn main(){
    let rec1 = Rectangle{
        width: 30,
        hight: 50,
    };

    println!(
        "The area of the recrtangle {} square pixels",
        area(&rec1)
    );
    println!("rec1 is {rec1:?}");
}

fn area(rectangle: &Rectangle) -> u32 {
        rectangle.width * rectangle.hight
}

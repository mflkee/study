pub fn main(){
    let _reference_to_nothing = dangle();
    println!("{}", _reference_to_nothing);
}

fn dangle() -> String{
    let s = String::from("hello");
    s.to_string()
}

// dangle returns a reference to a String

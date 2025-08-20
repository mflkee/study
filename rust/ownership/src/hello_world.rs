pub fn main(){
    let mut s = String::from("hello");
    s.push_str(", world!");
    println!("{s} - print 's'");
    let s2 = s.clone();
    println!("{s2} - print 's2'");
    let s3 = &s2;
    println!("{s3} - print 's3'");
    println!("{s} - print 's'");
}

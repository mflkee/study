fn main() {
    // let value: f32 = 20.5;
    // println!("value = {value}");
    //
    // let bits: u32 = value.to_bits();
    // println!("value as bits = {bits}");
    //
    // let s = 345 % 10;
    // println!("{s}");
    //
    // let a: f64 = 0.1;
    // let b: f64 = 0.2;
    // let eq: bool = (a + b) == 0.3;
    // println!("{eq}");

    fn show_float(value: f32) {
        let bits: u32 = value.to_bits();
        println!("bits decimal: {bits}");
        println!("binary: {bits:032b}");
        println!("hex: 0x{bits:08X}");

        let sign = bits >> 31 & 1;
        if sign == 0 {
            println!("sign: +")
        } else {
            println!("sign: -")
        };

        let exponent = (bits >> 23) & 0xFF;
        println!("exponent: {exponent}");

        let mantisa = bits & 0x7FFFF;
        println!("mantisa: {mantisa}");

        let real_exp = exponent as i32 - 127;
        println!("real_exp: {real_exp}");
    }

    show_float(20.05);
    show_float(-404.567);
}

pub mod boolean;
pub mod char;
pub mod consts;
pub mod float;
pub mod scalar;

pub fn start() {
    scalar::integer();
    float::demo_float();
    boolean::demo_bool();
    char::demo_char();
    consts::demo_const();
}

pub mod integer;
pub mod float;
pub mod boolean;
pub mod char;
pub mod consts;

pub fn start(){
    integer::demo_int();
    float::demo_float();
    boolean::demo_bool();
    char::demo_char();
    consts::demo_const();
}

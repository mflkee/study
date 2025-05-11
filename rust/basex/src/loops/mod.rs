pub mod loop_for;
pub mod loop_while;
pub mod infinite_loop;
pub mod array_iterating;

pub fn start(){
    loop_for::demo_for();
    loop_while::demo_while();
    infinite_loop::demo_loop();
    array_iterating::demo_array_iterating();
}

pub mod loop_for;
pub mod loop_while;
pub mod infinite_loop;
pub mod array_iterating;
pub mod loops_labels;

pub fn start(){
    loop_for::main();
    loop_while::main();
    infinite_loop::main();
    array_iterating::main();
    loops_labels::main();
}

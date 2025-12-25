pub mod if_else;
pub mod if_expressions;
pub mod match_operator;
pub mod operators;

pub fn start() {
    operators::operators();
    if_expressions::if_expressions();
    match_operator::match_operator();
    if_else::main();
}

pub mod operators;
pub mod ternary;
pub mod op_match;

pub fn start(){
    operators::operators();
    ternary::op_ternary();
    op_match::op_match();
}


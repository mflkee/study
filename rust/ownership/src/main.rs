mod hello;
mod hello_world;
mod ownnership_and_func;
mod return_value_and_scope;
mod references_and_borrowing;
mod dangling_referece;

fn main() {
    hello::main();
    hello_world::main();
    ownnership_and_func::main();
    return_value_and_scope::main();
    references_and_borrowing::main();
    dangling_referece::main();
}

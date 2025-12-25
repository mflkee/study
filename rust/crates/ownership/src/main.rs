mod dangling_referece;
mod hello;
mod hello_world;
mod ownnership_and_func;
mod references_and_borrowing;
mod return_value_and_scope;

fn main() {
    hello::main();
    hello_world::main();
    ownnership_and_func::main();
    return_value_and_scope::main();
    references_and_borrowing::main();
    dangling_referece::main();
}

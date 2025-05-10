pub mod base;
pub mod borrowing;

pub fn start(){
    base::ownership_base();
    borrowing::borrowing();
}

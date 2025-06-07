pub mod func_1;
pub mod func_2;
pub mod func_3;
pub mod func_4;

pub mod main;

pub fn start() {
    main::main_func();
    func_1::func_1();
    func_2::func_2();
    func_3::func_3();
    func_4::func_4();
}

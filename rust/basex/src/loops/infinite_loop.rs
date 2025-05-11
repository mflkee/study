pub fn demo_loop() {
    let mut count = 0;

    loop {
        println!("Res_loop: {count}");
        count += 1;
        if count == 5 {
            println!("Finish");
            break;
        }
    }
}

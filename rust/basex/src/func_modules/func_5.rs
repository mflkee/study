pub fn func_5() {
    print_label_messuriment(5, 'h');
}

fn print_label_messuriment(value: i32, unit_label: char) {
    println!("The measurement is: {value}{unit_label}");
}

use std::fs::{File, OpenOptions};
use std::io::Write;  // ← нужно добавить для записи

fn main() {
    let path = "cache.txt";
    let mut file = create_file(path);  // ← добавил mut, т.к. будем менять файл
    
    write_file(&mut file, "Hello from Rust!");
}

fn create_file(path: &str) -> File {
    OpenOptions::new()
        .create(true)
        .truncate(true)
        .write(true)
        .open(path)
        .expect("Failed to create file")
}

fn write_file(file: &mut File, content: &str) {
    file.write_all(content.as_bytes())
        .expect("Failed to write to file");
}

use std::fs::File;
use std::io::ErrorKind;

fn main() {
    let path: &str = "hello.txt";
    let greeting_file_result = File::open(path);

    let greeting_file = match greeting_file_result {
        Ok(fo) => fo,
        Err(e) => match e.kind() {
            ErrorKind::NotFound => match File::create(path) {
                Ok(fc) => fc,
                Err(e) => panic!("Error creating file {e:?}"),
            },

            _ => {
                panic!("Problem opening file {e:?}")
            }
        },
    };
}

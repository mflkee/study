fn placeholder<T>() -> T {
    panic!("Замените placeholder() правильным значением");
}

#[test]
fn mutable_variables_can_change() {
    let mut value = 10;
    value += 5;

    assert_eq!(value, placeholder::<i32>());
}

#[test]
fn shadowing_creates_a_new_binding() {
    let items = 3;
    let items = items + 2;

    assert_eq!(items, placeholder::<i32>());
}

#[test]
fn tuples_can_be_destructured() {
    let student = ("Mira", 5_u8, true);
    let (name, grade, passed) = student;

    assert_eq!(name, placeholder::<&str>());
    assert_eq!(grade, placeholder::<u8>());
    assert_eq!(passed, placeholder::<bool>());
}

#[test]
fn if_is_an_expression() {
    let score = 78;
    let result = if score >= 60 { "pass" } else { "fail" };

    assert_eq!(result, placeholder::<&str>());
}

#[test]
fn loop_can_return_a_value() {
    let mut counter = 0;

    let answer = loop {
        counter += 1;
        if counter == 4 {
            break counter * 2;
        }
    };

    assert_eq!(answer, placeholder::<i32>());
}

#[test]
fn arrays_have_a_fixed_length() {
    let modules = ["ownership", "traits", "testing"];

    assert_eq!(modules.len(), placeholder::<usize>());
    assert_eq!(modules[1], placeholder::<&str>());
}

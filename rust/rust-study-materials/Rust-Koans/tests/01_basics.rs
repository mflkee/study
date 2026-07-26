#[test]
fn mutable_variables_can_change() {
    let mut value = 10;
    value += 5;

    assert_eq!(value, 15);
}

#[test]
fn shadowing_creates_a_new_binding() {
    let items = 3;
    let items = items + 2;

    assert_eq!(items, 5);
}

#[test]
fn tuples_can_be_destructured() {
    let student = ("Mira", 5_u8, true);
    let (name, grade, passed) = student;

    assert_eq!(name, "Mira");
    assert_eq!(grade, 5);
    assert_eq!(passed, true);
}

#[test]
fn if_is_an_expression() {
    let score = 78;
    let result = if score >= 60 { "pass" } else { "fail" };

    assert_eq!(result, "pass");
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

    assert_eq!(answer, 8);
}

#[test]
fn arrays_have_a_fixed_length() {
    let modules = ["ownership", "traits", "testing"];

    assert_eq!(modules.len(), 3);
    assert_eq!(modules[1], "traits");
}

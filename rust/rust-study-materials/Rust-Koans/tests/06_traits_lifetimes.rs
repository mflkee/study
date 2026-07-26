fn todo<T>() -> T {
    std::todo!("Замените todo() правильным значением");
}

trait Summary {
    fn summarize(&self) -> String;
}

#[derive(Debug)]
struct Note {
    title: String,
}

impl Summary for Note {
    fn summarize(&self) -> String {
        format!("note: {}", self.title)
    }
}

fn largest<T: Ord + Copy>(items: &[T]) -> T {
    let mut current = items[0];
    for item in items.iter().copied() {
        if item > current {
            current = item;
        }
    }
    current
}

fn longer<'a>(left: &'a str, right: &'a str) -> &'a str {
    if left.len() >= right.len() {
        left
    } else {
        right
    }
}

#[test]
fn traits_define_shared_behavior() {
    let note = Note {
        title: String::from("ownership"),
    };
    let expected_summary: String = todo();

    assert_eq!(note.summarize(), expected_summary);
}

#[test]
fn generics_allow_reuse_for_multiple_types() {
    let values = [3, 9, 4, 1];
    let expected_largest: i32 = todo();

    assert_eq!(largest(&values), expected_largest);
}

#[test]
fn lifetimes_describe_relation_between_references() {
    let title = "Rust";
    let subtitle = "borrow checker";
    let expected_longer: &str = todo();

    assert_eq!(longer(title, subtitle), expected_longer);
}

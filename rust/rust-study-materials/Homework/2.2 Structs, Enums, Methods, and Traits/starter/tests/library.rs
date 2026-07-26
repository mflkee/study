use starter::{Condition, Describable, ItemType, Library, LibraryItem};

#[test]
fn constructors_create_correct_item_types() {
    let book = LibraryItem::book("Klabnik", "Rust for Students", 2025, 320, Condition::Good);
    let magazine = LibraryItem::magazine("Rust Digest", 2026, 64, Condition::New);
    let notes = LibraryItem::lecture_notes("Borrow Checker", 2026, 28, Condition::Worn);

    assert_eq!(book.item_type, ItemType::Book);
    assert_eq!(magazine.item_type, ItemType::Magazine);
    assert_eq!(notes.item_type, ItemType::LectureNotes);
}

#[test]
fn critical_items_cannot_be_lent() {
    let item = LibraryItem::lecture_notes("Old Notes", 2020, 18, Condition::Critical);

    assert!(!item.can_be_lent());
}

#[test]
fn library_adds_only_valid_items() {
    let mut library = Library::new("Faculty Library");
    library.add_item(LibraryItem::magazine(
        "Rust Weekly",
        2026,
        42,
        Condition::Good,
    ));
    library.add_item(LibraryItem::lecture_notes(
        "Torn Notes",
        2021,
        12,
        Condition::Critical,
    ));

    assert_eq!(library.items.len(), 1);
}

#[test]
fn library_can_find_and_lend_item() {
    let mut library = Library::new("Faculty Library");
    library.add_item(LibraryItem::book(
        "Graydon",
        "Ownership",
        2024,
        210,
        Condition::Good,
    ));

    let found = library.find_by_title("Ownership");
    assert!(found.is_some());

    let given = library.lend("Ownership");
    assert_eq!(given.unwrap().title, "Ownership");
    assert!(library.find_by_title("Ownership").is_none());
}

#[test]
fn library_filters_items_by_type() {
    let mut library = Library::new("Faculty Library");
    library.add_item(LibraryItem::book("A", "Book A", 2023, 120, Condition::Good));
    library.add_item(LibraryItem::magazine("Mag", 2025, 40, Condition::New));
    library.add_item(LibraryItem::book("B", "Book B", 2024, 150, Condition::Worn));

    let books = library.items_by_type(ItemType::Book);
    let titles: Vec<&str> = books.iter().map(|item| item.title.as_str()).collect();

    assert_eq!(titles, vec!["Book A", "Book B"]);
}

#[test]
fn describe_contains_main_fields() {
    let item = LibraryItem::book("Ferris", "Rust Notes", 2026, 144, Condition::New);
    let description = item.describe();

    assert!(description.contains("Rust Notes"));
    assert!(description.contains("2026"));
}
